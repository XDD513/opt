package com.hospital.service;

import com.hospital.util.RedisUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class CaptchaService {

    private static final String REDIS_PREFIX = "hospital:captcha:";
    private static final int TTL_MINUTES = 5;
    private static final String CHARS = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
    /** 5 位降低纯枚举成功率，配合干扰线/噪点提高 OCR 成本 */
    private static final int CODE_LEN = 5;
    private static final int WIDTH = 132;
    private static final int HEIGHT = 44;

    private final SecureRandom random = new SecureRandom();

    @Autowired
    private RedisUtil redisUtil;

    public Map<String, String> generateCaptchaImage() {
        String captchaId = UUID.randomUUID().toString().replace("-", "");
        String code = randomCode();
        redisUtil.set(REDIS_PREFIX + captchaId, code, TTL_MINUTES, TimeUnit.MINUTES);
        String imageBase64;
        try {
            imageBase64 = Base64.getEncoder().encodeToString(renderPng(code));
        } catch (IOException e) {
            log.error("验证码显示错误", e);
            redisUtil.delete(REDIS_PREFIX + captchaId);
            throw new RuntimeException("验证码创建失败", e);
        }
        Map<String, String> out = new HashMap<>();
        out.put("captchaId", captchaId);
        out.put("imageBase64", imageBase64);
        return out;
    }

    public boolean validateAndConsume(String captchaId, String captchaCode) {
        if (!StringUtils.hasText(captchaId) || !StringUtils.hasText(captchaCode)) {
            return false;
        }
        String key = REDIS_PREFIX + captchaId.trim();
        Object stored = redisUtil.get(key);
        redisUtil.delete(key);
        if (stored == null) {
            return false;
        }
        String expected = String.valueOf(stored).trim();
        return expected.equalsIgnoreCase(captchaCode.trim());
    }

    private String randomCode() {
        StringBuilder sb = new StringBuilder(CODE_LEN);
        for (int i = 0; i < CODE_LEN; i++) {
            sb.append(CHARS.charAt(random.nextInt(CHARS.length())));
        }
        return sb.toString();
    }

    private byte[] renderPng(String code) throws IOException {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(new Color(245, 247, 250));
            g.fillRect(0, 0, WIDTH, HEIGHT);
            for (int i = 0; i < 10; i++) {
                g.setColor(new Color(160 + random.nextInt(80), 160 + random.nextInt(80), 160 + random.nextInt(80)));
                g.setStroke(new BasicStroke(1f + random.nextFloat()));
                g.drawLine(random.nextInt(WIDTH), random.nextInt(HEIGHT), random.nextInt(WIDTH), random.nextInt(HEIGHT));
            }
            for (int i = 0; i < 45; i++) {
                g.setColor(new Color(random.nextInt(200), random.nextInt(200), random.nextInt(200)));
                int px = random.nextInt(WIDTH);
                int py = random.nextInt(HEIGHT);
                g.fillOval(px, py, 2, 2);
            }
            int step = (WIDTH - 28) / Math.max(code.length(), 1);
            for (int i = 0; i < code.length(); i++) {
                int fontSize = 22 + random.nextInt(5);
                g.setFont(new Font("Arial", Font.BOLD, fontSize));
                g.setColor(new Color(20 + random.nextInt(60), 20 + random.nextInt(60), 20 + random.nextInt(60)));
                int x = 14 + i * step + random.nextInt(3);
                int y = 30 + random.nextInt(5) - 2;
                double angle = (random.nextDouble() - 0.5) * 0.45;
                g.rotate(angle, x, y);
                g.drawString(String.valueOf(code.charAt(i)), x, y);
                g.rotate(-angle, x, y);
            }
        } finally {
            g.dispose();
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "png", baos);
        return baos.toByteArray();
    }
}
