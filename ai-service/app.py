import os
import json
import numpy as np
import onnxruntime as ort
from flask import Flask, request, jsonify
from PIL import Image, ImageDraw, ImageFont
import io
import base64
import cv2
import random

app = Flask(__name__)

# 配置
MODEL_PATH = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "best.onnx")
CLASSES_PATH = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "classes.txt")

# 加载类别
classes = []
if os.path.exists(CLASSES_PATH):
    with open(CLASSES_PATH, 'r', encoding='utf-8') as f:
        # 兼容两种格式：1. 纯名称 2. 数字→名称
        for line in f:
            line = line.strip()
            if not line: continue
            if '→' in line:
                classes.append(line.split('→')[-1].strip())
            else:
                classes.append(line)
else:
    # 备用方案：如果文件不存在，使用 feature_map.json 中的特征名
    # 注意：这需要确保顺序一致
    FEATURE_MAP_PATH_ALT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "feature_map.json")
    if os.path.exists(FEATURE_MAP_PATH_ALT):
        with open(FEATURE_MAP_PATH_ALT, 'r') as f:
            temp_map = json.load(f)
            classes = temp_map.get('features', [])
            print("Warning: classes.txt not found, using features from feature_map.json")

# 生成随机颜色
np.random.seed(42)
colors = [[random.randint(0, 255) for _ in range(3)] for _ in range(len(classes) + 10)]

# 加载模型
session = None
try:
    session = ort.InferenceSession(MODEL_PATH)
    print(f"Model loaded from {MODEL_PATH}")
except Exception as e:
    print(f"Error loading model: {e}")

def load_annotation_font(font_size=20):
    """
    优先加载支持中文的字体；若不可用则返回默认字体并标记不支持中文。
    """
    font_paths = [
        "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc",
        "/usr/share/fonts/truetype/wqy/wqy-microhei.ttc",
        "/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc",
        "/usr/share/fonts/truetype/droid/DroidSansFallbackFull.ttf",
        "C:/Windows/Fonts/msyh.ttc",
        "C:/Windows/Fonts/simhei.ttf"
    ]

    for fp in font_paths:
        if os.path.exists(fp):
            try:
                font = ImageFont.truetype(fp, font_size)
                print(f"Loaded annotation font: {fp}")
                return font, True
            except Exception:
                continue

    print("No CJK font found, fallback to default font")
    return ImageFont.load_default(), False

def letterbox(im, new_shape=(640, 640), color=(114, 114, 114), auto=True, scaleFill=False, scaleup=True, stride=32):
    shape = im.shape[:2]  # current shape [height, width]
    if isinstance(new_shape, int):
        new_shape = (new_shape, new_shape)

    r = min(new_shape[0] / shape[0], new_shape[1] / shape[1])
    if not scaleup:  # only scale down, do not scale up (for better test mAP)
        r = min(r, 1.0)

    ratio = r, r  # width, height ratios
    new_unpad = int(round(shape[1] * r)), int(round(shape[0] * r))
    dw, dh = new_shape[1] - new_unpad[0], new_shape[0] - new_unpad[1]  # wh padding
    if auto:  # minimum rectangle
        dw, dh = np.mod(dw, stride), np.mod(dh, stride)  # wh padding
    elif scaleFill:  # stretch
        dw, dh = 0.0, 0.0
        new_unpad = (new_shape[1], new_shape[0])
        ratio = new_shape[1] / shape[1], new_shape[0] / shape[0]  # width, height ratios

    dw /= 2  # divide padding into 2 sides
    dh /= 2

    if shape[::-1] != new_unpad:  # resize
        im = cv2.resize(im, new_unpad, interpolation=cv2.INTER_LINEAR)
    
    top, bottom = int(round(dh - 0.1)), int(round(dh + 0.1))
    left, right = int(round(dw - 0.1)), int(round(dw + 0.1))
    im = cv2.copyMakeBorder(im, top, bottom, left, right, cv2.BORDER_CONSTANT, value=color)  # add border
    return im, ratio, (dw, dh)

def color_correct(img):
    """
    Experiment 1: Standardized Preprocessing - Grey World Color Correction
    """
    b, g, r = cv2.split(img)
    b_avg = cv2.mean(b)[0]
    g_avg = cv2.mean(g)[0]
    r_avg = cv2.mean(r)[0]
    avg = (b_avg + g_avg + r_avg) / 3
    b = cv2.convertScaleAbs(b, alpha=avg/b_avg if b_avg > 0 else 1.0)
    g = cv2.convertScaleAbs(g, alpha=avg/g_avg if g_avg > 0 else 1.0)
    r = cv2.convertScaleAbs(r, alpha=avg/r_avg if r_avg > 0 else 1.0)
    return cv2.merge([b, g, r])

def preprocess(image):
    # Convert PIL to OpenCV
    img_cv = cv2.cvtColor(np.array(image), cv2.COLOR_RGB2BGR)
    
    # Experiment 1: Standardized Preprocessing
    img_cv = color_correct(img_cv)
    img_cv = cv2.bilateralFilter(img_cv, 9, 75, 75) # Denoising
    
    # Letterbox 缩放至 640x640
    # 注意：YOLOv8 训练时通常使用 letterbox 保持长宽比
    # auto=False 确保输出尺寸严格为 640x640，不留多余黑边
    img_letterbox, ratio, (dw, dh) = letterbox(img_cv, new_shape=(640, 640), auto=False)
    
    # 维度转换: HWC -> CHW, BGR -> RGB, 归一化并增加 Batch 维度
    img_data = img_letterbox.transpose((2, 0, 1))[::-1]
    img_data = np.ascontiguousarray(img_data)
    
    img_data = img_data.astype('float32') / 255.0
    img_data = np.expand_dims(img_data, axis=0)
    
    # 调试：打印预处理后的数据统计
    # print(f"Preprocessed Input Shape: {img_data.shape}, Min: {np.min(img_data)}, Max: {np.max(img_data)}")
    
    return img_data, ratio, (dw, dh), img_cv

def xywh2xyxy(x):
    # Convert nx4 boxes from [x, y, w, h] to [x1, y1, x2, y2]
    y = np.copy(x)
    y[:, 0] = x[:, 0] - x[:, 2] / 2  # top left x
    y[:, 1] = x[:, 1] - x[:, 3] / 2  # top left y
    y[:, 2] = x[:, 0] + x[:, 2] / 2  # bottom right x
    y[:, 3] = x[:, 1] + x[:, 3] / 2  # bottom right y
    return y

def nms(boxes, scores, class_ids, iou_threshold=0.45):
    """
    Multi-class NMS
    """
    if boxes.size == 0: return []
    # Strategy: Offset boxes by class_id * max_coordinate to separate classes
    max_coordinate = boxes.max() + 1000
    offsets = class_ids * max_coordinate
    boxes_offset = boxes + offsets[:, None]
    
    x1 = boxes_offset[:, 0]
    y1 = boxes_offset[:, 1]
    x2 = boxes_offset[:, 2]
    y2 = boxes_offset[:, 3]

    areas = (x2 - x1 + 1) * (y2 - y1 + 1)
    order = scores.argsort()[::-1]

    keep = []
    while order.size > 0:
        i = order[0]
        keep.append(i)
        xx1 = np.maximum(x1[i], x1[order[1:]])
        yy1 = np.maximum(y1[i], y1[order[1:]])
        xx2 = np.minimum(x2[i], x2[order[1:]])
        yy2 = np.minimum(y2[i], y2[order[1:]])

        w = np.maximum(0.0, xx2 - xx1 + 1)
        h = np.maximum(0.0, yy2 - yy1 + 1)
        inter = w * h
        ovr = inter / (areas[i] + areas[order[1:]] - inter)

        inds = np.where(ovr <= iou_threshold)[0]
        order = order[inds + 1]

    return keep

import joblib
import json

# 加载新训练的分类器模型
# 注意：确保文件路径正确，这里假设 app.py 和模型文件在同一目录
# 修改：使用绝对路径查找上一级目录下的文件 (根据之前 Copy-Item 的目标路径)
CLASSIFIER_PATH = os.path.join(os.path.dirname(os.path.abspath(__file__)), "constitution_classifier.pkl")
FEATURE_MAP_PATH = os.path.join(os.path.dirname(os.path.abspath(__file__)), "feature_map.json")

# 如果在当前目录找不到，尝试在上一级目录查找 (兼容开发环境结构)
if not os.path.exists(CLASSIFIER_PATH):
    CLASSIFIER_PATH = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "constitution_classifier.pkl")

if not os.path.exists(FEATURE_MAP_PATH):
    FEATURE_MAP_PATH = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "feature_map.json")

classifier = None
feature_mapping = None

try:
    if os.path.exists(CLASSIFIER_PATH):
        classifier = joblib.load(CLASSIFIER_PATH)
        print(f"Classifier loaded from {CLASSIFIER_PATH}")
    else:
        print(f"Classifier not found at {CLASSIFIER_PATH}")
        
    if os.path.exists(FEATURE_MAP_PATH):
        with open(FEATURE_MAP_PATH, 'r') as f:
            feature_mapping = json.load(f)
        print(f"Feature mapping loaded from {FEATURE_MAP_PATH}")
    else:
        print(f"Feature mapping not found at {FEATURE_MAP_PATH}")
except Exception as e:
    print(f"Error loading classifier: {e}")

@app.route('/predict_v2', methods=['POST'])
def predict_v2():
    """
    Stage 2 Cascade Inference: ONNX (Visual) -> PKL (Cognitive)
    """
    if 'file' not in request.files:
        return jsonify({"error": "No file part"}), 400
    
    file = request.files['file']
    try:
        # 检查 feature_mapping 是否加载成功
        if feature_mapping is None:
            return jsonify({"error": "Feature mapping configuration not loaded"}), 500
            
        image = Image.open(file.stream).convert('RGB')
        
        # --- Stage 1: Visual Perception (ONNX) ---
        input_data, ratio, (dw, dh), img_cv = preprocess(image)
        
        if not session:
            return jsonify({"error": "Visual model not loaded"}), 500
            
        input_name = session.get_inputs()[0].name
        outputs = session.run(None, {input_name: input_data})
        output = outputs[0].transpose(0, 2, 1)
        prediction = output[0]
        
        # 调试：打印预测张量的形状和数值统计
        print(f"ONNX Output Shape: {output.shape}")
        # print(f"First 5 predictions (raw): {prediction[:5]}")
        
        # Extract features vector
        # YOLOv8 输出格式: [x, y, w, h, conf1, conf2, ...]
        # 这里的切片 [:, 4:] 假设前4个是坐标，后面是类别置信度
        # 请确认这与您训练的模型版本一致
        scores = prediction[:, 4:] 
        
        # 调试：打印分数矩阵的统计信息
        print(f"Scores Matrix Shape: {scores.shape}")
        print(f"Scores Max: {np.max(scores)}, Min: {np.min(scores)}, Mean: {np.mean(scores)}")
        
        max_scores = np.max(scores, axis=0) # [80] -> [num_classes]
        
        # 打印一下最大分数，方便调试
        print(f"Max scores from ONNX: {max_scores}")
        
        # Map to 21-dim feature vector expected by Classifier
        # YOLO classes might differ from Classifier features, need mapping
        # Assuming YOLO classes are a superset or ordered differently
        
        # Create feature vector for classifier
        clf_features = np.zeros(len(feature_mapping['features']))
        
        # Mapping logic: YOLO class name -> Classifier feature index
        yolo_classes = classes # defined globally
        # feature_map.json 中使用的是 "features" 键
        clf_feature_names = feature_mapping['features']
        
        # 调试：打印两个列表的内容，确认是否匹配
        print(f"YOLO Classes: {yolo_classes}")
        print(f"CLF Features: {clf_feature_names}")
        
        for i, cls_name in enumerate(yolo_classes):
            # 注意：feature_map.json 中的特征名可能与 classes.txt 中的略有不同
            # 例如 classes.txt 可能是 'chihenshe'，而 feature_map 也是 'chihenshe'
            # 这里做严格匹配，如果需要映射可以在这里加逻辑
            
            # 清洗字符串，去除可能的空白字符
            clean_cls_name = cls_name.strip()
            
            if clean_cls_name in clf_feature_names:
                idx = clf_feature_names.index(clean_cls_name)
                # Normalize confidence to 0-1 if not already
                clf_features[idx] = float(max_scores[i])
            else:
                # 尝试模糊匹配或打印未匹配的项
                print(f"Warning: YOLO class '{clean_cls_name}' not found in classifier features")
        
        # 调试：打印构建好的特征向量
        # print(f"Built CLF Features: {clf_features}")
        
        # --- Stage 2: Cognitive Decision (PKL) ---
        constitution_result = {}
        if classifier:
            # Predict probabilities
            probas = classifier.predict_proba([clf_features])[0]
            labels = feature_mapping['labels']
            
            # Convert to dictionary
            for i, label in enumerate(labels):
                constitution_result[label] = round(float(probas[i]), 4)
                
            # Get primary constitution
            primary_idx = np.argmax(probas)
            primary_const = labels[primary_idx]
            primary_score = probas[primary_idx]
        else:
            return jsonify({"error": "Classifier model not loaded"}), 500

        # --- Visual Feedback (Drawing) ---
        # Generate detections for drawing
        boxes = prediction[:, :4]
        box_scores = prediction[:, 4:]
        class_ids = np.argmax(box_scores, axis=1)
        confidences = np.max(box_scores, axis=1)
        
        mask = confidences > 0.10
        boxes = boxes[mask]
        confidences = confidences[mask]
        class_ids = class_ids[mask]
        
        boxes = xywh2xyxy(boxes)
        indices = nms(boxes, confidences, class_ids, iou_threshold=0.65)
        
        detections = []
        for i in indices:
            box = boxes[i]
            # Rescale boxes from img_size to im0 size
            box[0] = (box[0] - dw) / ratio[0]
            box[1] = (box[1] - dh) / ratio[1]
            box[2] = (box[2] - dw) / ratio[0]
            box[3] = (box[3] - dh) / ratio[1]
            
            detections.append({
                "box": box,
                "label": classes[class_ids[i]],
                "conf": float(confidences[i])
            })

        # 绘制检测框
        img_draw = img_cv.copy()
        
        # 特征名映射字典 (英文 -> 中文) 用于绘制
        feature_name_map = {
            "jiankangshe": "健康舌", "botaishe": "薄白苔", "hongshe": "红舌", "zishe": "紫舌",
            "pangdashe": "胖大舌", "shoushe": "瘦舌", "hongdianshe": "红点舌", "liewenshe": "裂纹舌",
            "chihenshe": "齿痕舌", "baitaishe": "白苔", "huangtaishe": "黄苔", "heitaishe": "黑苔",
            "huataishe": "滑苔", "shenquao": "肾区凹陷", "shenqutu": "肾区凸起", "gandanao": "肝胆凹陷",
            "gandantu": "肝胆凸起", "piweiao": "脾胃凹陷", "piweitu": "脾胃凸起", "xinfeiao": "心肺凹陷",
            "xinfeitu": "心肺凸起"
        }
        
        # 加载字体：有中文字体则输出中文，否则自动回退英文避免乱码
        font, supports_chinese = load_annotation_font(font_size=20)

        # 使用 clf_feature_names 作为键，clf_features 作为值
        raw_features = {}
        for name, val in zip(clf_feature_names, clf_features):
            if val > 0.001:
                display_name = feature_name_map.get(name, name) if supports_chinese else name
                raw_features[display_name] = round(float(val), 4)

        # 使用 PIL 绘制 (支持中文)
        img_pil = Image.fromarray(cv2.cvtColor(img_draw, cv2.COLOR_BGR2RGB))
        draw = ImageDraw.Draw(img_pil)
        
        # 预定义的颜色列表 (中医风格配色)
        # 绿色 (Green), 蓝色 (Blue), 橙色 (Orange), 红色 (Red), 紫色 (Purple), 青色 (Cyan)
        # 每个元组是 (R, G, B)
        colors = [
            (74, 144, 226),   # Blue
            (126, 211, 33),   # Green
            (245, 166, 35),   # Orange
            (208, 2, 27),     # Red
            (144, 19, 254),   # Purple
            (80, 227, 194),   # Cyan
            (255, 64, 129),   # Pink
            (155, 155, 155)   # Grey
        ]
        
        # 为每个类别分配固定的颜色
        # 使用类别名称的哈希值来选择颜色，保证同一类别的颜色在不同图片中一致
        def get_color(label):
            idx = abs(hash(label)) % len(colors)
            return colors[idx]

        # 记录已绘制的标签位置，用于简单的避障
        # 每个元素是 (x1, y1, x2, y2)
        placed_labels = []

        for det in detections:
            x1, y1, x2, y2 = map(int, det["box"])
            label_en = det["label"]
            conf = det["conf"]
            label_cn = feature_name_map.get(label_en, label_en)
            
            # 获取该类别的颜色
            color = get_color(label_en)
            
            label_name = label_cn if supports_chinese else label_en
            label_text = f"{label_name} {conf:.2f}"
            
            # 绘制矩形框
            draw.rectangle([x1, y1, x2, y2], outline=color, width=3)
            
            # 计算文本尺寸
            try:
                # Pillow >= 9.2.0
                left, top, right, bottom = draw.textbbox((x1, y1), label_text, font=font)
                w, h = right - left, bottom - top
            except AttributeError:
                # Pillow < 9.2.0
                w, h = draw.textsize(label_text, font=font)
            
            # 标签位置逻辑 (简单避障)
            # 默认位置：框的左上角上方
            label_x = x1
            label_y = y1 - h - 4
            
            # 如果上方超出边界，放框内
            if label_y < 0:
                label_y = y1 + 4
            
            # 简单的垂直堆叠避障：检查是否与已有标签重叠
            # 如果重叠，向下移动
            current_label_box = [label_x, label_y, label_x + w, label_y + h]
            
            # 尝试最多 5 次偏移
            for _ in range(5):
                overlap = False
                for placed in placed_labels:
                    # 计算 IoU 或简单重叠
                    # 这里检查 y 轴重叠
                    if (abs(current_label_box[1] - placed[1]) < h + 2) and (abs(current_label_box[0] - placed[0]) < w + 2):
                        overlap = True
                        break
                
                if overlap:
                    # 向下移动一行
                    label_y += h + 4
                    current_label_box = [label_x, label_y, label_x + w, label_y + h]
                else:
                    break
            
            # 记录位置
            placed_labels.append(current_label_box)
                
            # 绘制标签背景和文本
            draw.rectangle([label_x, label_y, label_x + w + 4, label_y + h + 4], fill=color)
            draw.text((label_x + 2, label_y), label_text, fill=(255, 255, 255), font=font) # 白色文字

        # Convert back to OpenCV for encoding
        img_draw = cv2.cvtColor(np.array(img_pil), cv2.COLOR_RGB2BGR)

        # Convert to Base64
        _, buffer = cv2.imencode('.jpg', img_draw)
        image_base64 = base64.b64encode(buffer).decode('utf-8')
        image_base64_str = f"data:image/jpeg;base64,{image_base64}"
        
        print(f"Returning visual_features (CN): {raw_features}")
        
        # Prepare features_detail and features_list for backward compatibility
        features_detail = []
        features_list = []
        for name, val in raw_features.items():
            features_detail.append({"name": name, "confidence": float(val)})
        
        # Sort by confidence and get top 5 for features_list
        sorted_features = sorted(features_detail, key=lambda x: x["confidence"], reverse=True)
        features_list = [f["name"] for f in sorted_features[:5]]

        return jsonify({
            "code": 200,
            "message": "success",
            "data": {
                "primary_constitution": primary_const,
                "constitution_scores": constitution_result,
                "visual_features": raw_features,
                "features_detail": features_detail,
                "features_list": features_list,
                "image_base64": image_base64_str
            }
        })

    except Exception as e:
        import traceback
        traceback.print_exc()
        return jsonify({"error": str(e)}), 500

@app.route('/predict', methods=['POST'])
def predict():
    if 'file' not in request.files:
        return jsonify({"error": "No file part"}), 400
    
    file = request.files['file']
    if file.filename == '':
        return jsonify({"error": "No selected file"}), 400

    try:
        image = Image.open(file.stream).convert('RGB')
        input_data, ratio, (dw, dh), img_cv = preprocess(image)
        
        if session:
            input_name = session.get_inputs()[0].name
            outputs = session.run(None, {input_name: input_data})
            
            # Output shape: [1, 25, 8400] -> Transpose to [1, 8400, 25]
            output = outputs[0].transpose(0, 2, 1)
            
            # Squeeze batch dimension: [8400, 25]
            prediction = output[0]
            
            # Split boxes and scores
            boxes = prediction[:, :4] # cx, cy, w, h
            scores = prediction[:, 4:] # classes scores
            
            # Get max confidence and class index
            class_ids = np.argmax(scores, axis=1)
            confidences = np.max(scores, axis=1)
            
            # Filter by threshold
            CONF_THRESHOLD = 0.10 # Lowered even more to catch everything
            mask = confidences > CONF_THRESHOLD
            boxes = boxes[mask]
            confidences = confidences[mask]
            class_ids = class_ids[mask]
            
            print(f"Detected {len(boxes)} raw boxes above threshold {CONF_THRESHOLD}")
            
            if len(boxes) == 0:
                # 即使没有检测到特征，也返回原图
                _, buffer = cv2.imencode('.jpg', img_cv)
                img_base64 = base64.b64encode(buffer).decode('utf-8')
                return jsonify({
                    "code": 200,
                    "message": "success",
                    "data": {
                        "feature": "Unknown",
                        "image_base64": "data:image/jpeg;base64," + img_base64,
                        "features_list": []
                    }
                })

            # Convert boxes to xyxy
            boxes = xywh2xyxy(boxes)
            
            # Class-Aware NMS
            # Increased iou_threshold from 0.45 to 0.65 to allow overlapping features (color/coating)
            indices = nms(boxes, confidences, class_ids, iou_threshold=0.65)
            print(f"Selected {len(indices)} boxes after NMS")
            
            # Result holders
            detected_features = []
            
            # --- 1. 图像留白处理 (防止遮挡) --- 
            pad = 80 # 留白像素 
            img_padded = cv2.copyMakeBorder(img_cv, pad, pad, pad, pad, cv2.BORDER_CONSTANT, value=(255, 255, 255)) 
            img_h, img_w = img_padded.shape[:2] 
            
            # 记录填充后的宽高比例，用于前端展示优化 (可选)
            # print(f"Padded Image Size: {img_w}x{img_h}")

            # --- 2. 准备专业色系 (中医视觉风格) --- 
            TCM_COLORS = [(74, 144, 226), (126, 211, 33), (245, 166, 35), (208, 2, 27), (144, 19, 254), (80, 227, 194)] 
            
            # --- 3. 转换为 PIL 进行高质量渲染 --- 
            img_pil = Image.fromarray(cv2.cvtColor(img_padded, cv2.COLOR_BGR2RGB)).convert('RGBA') 
            overlay = Image.new('RGBA', img_pil.size, (255, 255, 255, 0)) 
            draw = ImageDraw.Draw(img_pil) 
            ol_draw = ImageDraw.Draw(overlay) 
            
            # 字体加载 (增加字号提升质感) 
            font = None 
            for fp in ["msyh.ttc", "simhei.ttf", "arial.ttf", "C:/Windows/Fonts/msyh.ttc", "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"]: 
                try: 
                    font = ImageFont.truetype(fp, 22)
                    break 
                except: continue 
            if font is None: font = ImageFont.load_default() 

            placed_rects = [] # 用于避障 

            # 排序：置信度高的优先画标签 
            sorted_indices = sorted(indices, key=lambda i: confidences[i], reverse=True) 

            for i in sorted_indices: 
                box = boxes[i] 
                score = float(confidences[i]) 
                class_id = class_ids[i]
                class_name = classes[class_id] if class_id < len(classes) else str(class_id)
                color = TCM_COLORS[class_id % len(TCM_COLORS)] 
                
                # 坐标映射到带 Padding 的图像 
                bx1 = (box[0] - dw) / ratio[0] + pad 
                by1 = (box[1] - dh) / ratio[1] + pad 
                bx2 = (box[2] - dw) / ratio[0] + pad 
                by2 = (box[3] - dh) / ratio[1] + pad 
                
                # 绘制半透明蒙版 (Mask) 
                ol_draw.rectangle([bx1, by1, bx2, by2], fill=color + (40,)) 
                # 绘制细边框 
                draw.rectangle([bx1, by1, bx2, by2], outline=color + (200,), width=2) 

                # --- 4. 智能标签排布 (避障逻辑) --- 
                label = f"{class_name} {score:.2f}" 
                try:
                    left, top, right, bottom = draw.textbbox((0, 0), label, font=font)
                    tw, th = right - left, bottom - top
                except:
                    tw, th = draw.textsize(label, font=font)
                
                # 候选位置尝试：1.框上方 2.框下方 3.多层级垂直偏移
                lx, ly = bx1, by1 - th - 10 
                found_pos = False 
                for offset in [0, -40, 40, -80, 80, -120, 120]: # 垂直偏移尝试 
                    curr_rect = (bx1, by1 - th - 10 + offset, bx1 + tw + 10, by1 + offset) 
                    # 检查是否重叠 
                    overlap = False 
                    for pr in placed_rects: 
                        if not (curr_rect[2] < pr[0] or curr_rect[0] > pr[2] or curr_rect[3] < pr[1] or curr_rect[1] > pr[3]): 
                            overlap = True; break 
                    if not overlap and curr_rect[1] > 0 and curr_rect[3] < img_h: 
                        lx, ly = curr_rect[0], curr_rect[1] 
                        placed_rects.append(curr_rect) 
                        found_pos = True; break 
                
                if not found_pos: # 实在没地方放了，强行放一个位置 
                    ly = by1 - th - 10 
                    placed_rects.append((lx, ly, lx+tw, ly+th)) 

                # 绘制引线 (如果标签离框较远) 
                if abs(ly - (by1 - th - 10)) > 5: 
                    draw.line([(bx1, by1), (lx, ly + th)], fill=color + (150,), width=1) 

                # 绘制标签背景 (胶囊形状更专业) 
                draw.rounded_rectangle([lx-4, ly-2, lx+tw+6, ly+th+2], radius=4, fill=color + (220,)) 
                draw.text((lx+2, ly-2), label, font=font, fill=(255, 255, 255, 255)) 
                
                detected_features.append({"name": class_name, "confidence": round(score, 4)}) 

            # 合并图层 
            img_final = Image.alpha_composite(img_pil, overlay).convert('RGB') 
            draw_img = cv2.cvtColor(np.array(img_final), cv2.COLOR_RGB2BGR) 

            # Convert result image to base64
            _, buffer = cv2.imencode('.jpg', draw_img)
            img_base64 = base64.b64encode(buffer).decode('utf-8')
            
            main_feature = detected_features[0]["name"] if detected_features else "Unknown"

            return jsonify({
                "code": 200,
                "message": "success",
                "data": {
                    "feature": main_feature,
                    "features_list": [f["name"] for f in detected_features],
                    "features_detail": detected_features,
                    "image_base64": "data:image/jpeg;base64," + img_base64
                }
            })
        else:
            return jsonify({"error": "Model not loaded"}), 500
    except Exception as e:
        import traceback
        traceback.print_exc()
        return jsonify({"error": str(e)}), 500

@app.route('/vectorize', methods=['POST'])
def vectorize():
    """
    Experiment 2: Feature Vectorization for Recommendation Engine
    Returns a normalized feature vector based on detection results.
    """
    if 'file' not in request.files:
        return jsonify({"error": "No file part"}), 400
    
    file = request.files['file']
    try:
        image = Image.open(file.stream).convert('RGB')
        input_data, _, _, _ = preprocess(image)
        
        if session:
            input_name = session.get_inputs()[0].name
            outputs = session.run(None, {input_name: input_data})
            output = outputs[0].transpose(0, 2, 1)
            prediction = output[0]
            
            # Split boxes and scores
            # prediction shape: [8400, 85] (4 box coords + 81 classes) or similar
            # scores start from index 4
            scores = prediction[:, 4:] 
            
            # --- Fix: Get max confidence for EACH class across all boxes ---
            # scores shape: [8400, num_classes]
            # We want a vector of shape [num_classes], where each element is the max score for that class
            
            # 1. Filter out low confidence detections globally to reduce noise
            # But here we want a global vector, so we can just take max over axis 0
            max_scores = np.max(scores, axis=0)
            
            # 2. Normalize or Scale (Optional, but good for consistent scoring)
            # Ensure scores are between 0 and 1 (they usually are from sigmoid/softmax)
            vector = max_scores.tolist()
            
            # 3. Map to specific constitution types if needed
            # Assuming 'classes' list corresponds to the vector indices
            
            return jsonify({
                "code": 200,
                "data": {
                    "vector": vector,
                    "classes": classes
                }
            })
    except Exception as e:
        return jsonify({"error": str(e)}), 500

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5000)
