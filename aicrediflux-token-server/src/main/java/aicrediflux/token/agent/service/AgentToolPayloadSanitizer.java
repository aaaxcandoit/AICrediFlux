package aicrediflux.token.agent.service;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;

final class AgentToolPayloadSanitizer {
    private static final int MAX_JSON_LENGTH = 2000;
    private static final int MAX_TEXT_LENGTH = 2000;
    private static final int SUMMARY_LENGTH = 900;
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Pattern SECRET_FIELD_NAME = Pattern.compile(
            "(?i).*(api[_ -]?key|channel[_ -]?key|provider[_ -]?key|secret|access[_ -]?key|authorization|password|token|key).*");
    private static final Pattern SECRET_JSON_FIELD = Pattern.compile(
            "(?i)(\"(?:apiKey|api_key|api-key|channelKey|channel_key|providerKey|provider_key|secret|secretKey|secret_key|accessKey|access_key|token|authorization|password|key)\"\\s*:\\s*\")([^\"]*)(\")");
    private static final Pattern BEARER_TOKEN = Pattern.compile("(?i)Bearer\\s+[A-Za-z0-9._\\-+/=]{8,}");
    private static final Pattern SK_TOKEN = Pattern.compile("(?i)sk-[A-Za-z0-9._\\-]{6,}");

    private AgentToolPayloadSanitizer() {
    }

    static String sanitizeText(String payload) {
        if (payload == null || payload.isBlank()) {
            return "";
        }
        String sanitized = redactSecrets(payload);
        if (sanitized.length() <= MAX_TEXT_LENGTH) {
            return sanitized;
        }
        return sanitized.substring(0, MAX_TEXT_LENGTH) + "...";
    }

    static String sanitizeJson(String payload) {
        if (payload == null || payload.isBlank()) {
            return "{}";
        }
        try {
            JsonNode node = JSON.readTree(payload);
            JsonNode sanitized = sanitizeNode(node);
            String json = JSON.writeValueAsString(sanitized);
            return shrinkJsonIfNeeded(json, sanitized);
        } catch (Exception ignored) {
            Map<String, Object> wrapped = new LinkedHashMap<>();
            wrapped.put("text", redactSecrets(payload));
            return shrinkJsonIfNeeded(writeJson(wrapped), JSON.valueToTree(wrapped));
        }
    }

    static String sanitize(String payload) {
        return sanitizeText(payload);
    }

    private static JsonNode sanitizeNode(JsonNode node) {
        if (node == null || node.isNull()) {
            return JSON.nullNode();
        }
        if (node.isObject()) {
            ObjectNode object = JSON.createObjectNode();
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                if (SECRET_FIELD_NAME.matcher(field.getKey()).matches()) {
                    object.put(field.getKey(), "***REDACTED***");
                } else {
                    object.set(field.getKey(), sanitizeNode(field.getValue()));
                }
            }
            return object;
        }
        if (node.isArray()) {
            ArrayNode array = JSON.createArrayNode();
            for (JsonNode item : node) {
                array.add(sanitizeNode(item));
            }
            return array;
        }
        if (node.isTextual()) {
            return new TextNode(redactSecrets(node.asText()));
        }
        return node;
    }

    private static String shrinkJsonIfNeeded(String json, JsonNode sanitizedNode) {
        if (json.length() <= MAX_JSON_LENGTH) {
            return json;
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("truncated", true);
        summary.put("rawLength", json.length());
        summary.put("summary", truncate(redactSecrets(sanitizedNode.toString()), SUMMARY_LENGTH));
        return writeJson(summary);
    }

    private static String redactSecrets(String payload) {
        String sanitized = SECRET_JSON_FIELD.matcher(payload).replaceAll("$1***REDACTED***$3");
        sanitized = BEARER_TOKEN.matcher(sanitized).replaceAll("Bearer ***REDACTED***");
        return SK_TOKEN.matcher(sanitized).replaceAll("sk-***REDACTED***");
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "...";
    }

    private static String writeJson(Object value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (Exception e) {
            return "{\"error\":\"json serialization failed\"}";
        }
    }
}
