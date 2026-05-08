package com.hospital.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;

import java.util.Locale;
import java.util.Set;

/**
 * 操作日志序列化前脱敏：按字段名屏蔽密码、令牌与部分个人信息。
 */
public final class LogSanitizer {

    private static final String REDACTED = "***";
    private static final Set<String> SENSITIVE_KEYS = Set.of(
            "password",
            "oldpassword",
            "newpassword",
            "confirmpassword",
            "token",
            "refreshtoken",
            "accesstoken",
            "captchacode",
            "idcard",
            "idnumber",
            "phone"
    );

    private LogSanitizer() {
    }

    private static boolean isSensitiveKey(String fieldName) {
        if (fieldName == null) {
            return false;
        }
        return SENSITIVE_KEYS.contains(fieldName.toLowerCase(Locale.ROOT));
    }

    /**
     * 将对象转为 JSON 树并递归脱敏后返回根节点（不修改原对象）。
     */
    public static JsonNode redact(ObjectMapper mapper, Object value) {
        if (value == null) {
            return NullNode.getInstance();
        }
        JsonNode tree = mapper.valueToTree(value);
        return redactNode(tree);
    }

    private static JsonNode redactNode(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return node;
        }
        if (node.isObject()) {
            ObjectNode src = (ObjectNode) node;
            ObjectNode out = JsonNodeFactory.instance.objectNode();
            src.fields().forEachRemaining(entry -> {
                String name = entry.getKey();
                JsonNode child = entry.getValue();
                if (isSensitiveKey(name)) {
                    out.set(name, TextNode.valueOf(REDACTED));
                } else {
                    out.set(name, redactNode(child));
                }
            });
            return out;
        }
        if (node.isArray()) {
            ArrayNode src = (ArrayNode) node;
            ArrayNode out = JsonNodeFactory.instance.arrayNode();
            for (JsonNode item : src) {
                out.add(redactNode(item));
            }
            return out;
        }
        return node;
    }
}
