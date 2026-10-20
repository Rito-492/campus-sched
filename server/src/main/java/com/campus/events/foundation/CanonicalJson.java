package com.campus.events.foundation;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.TreeMap;
import org.springframework.stereotype.Component;

@Component
public class CanonicalJson {
    private final ObjectMapper mapper;
    public CanonicalJson(ObjectMapper mapper) {
        this.mapper = mapper.copy().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                .enable(com.fasterxml.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    }
    public String normalize(String json) {
        try {
            JsonNode tree = mapper.readTree(json);
            if (tree == null || tree.isMissingNode()) throw ApiException.invalid("请求体不能为空");
            return mapper.writeValueAsString(sort(tree));
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) { throw ApiException.invalid("JSON格式不正确"); }
    }
    private JsonNode sort(JsonNode node) {
        if (node.isObject()) {
            var sorted = new TreeMap<String, JsonNode>();
            node.fields().forEachRemaining(e -> sorted.put(e.getKey(), sort(e.getValue())));
            return mapper.valueToTree(sorted);
        }
        if (node.isArray()) { var array = mapper.createArrayNode(); node.forEach(n -> array.add(sort(n))); return array; }
        if (node.isNumber()) return com.fasterxml.jackson.databind.node.DecimalNode.valueOf(node.decimalValue().stripTrailingZeros());
        return node;
    }
    public static String sha256(String text) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
    public String write(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (com.fasterxml.jackson.core.JsonProcessingException ex) { throw new IllegalStateException("响应序列化失败", ex); }
    }
    public JsonNode read(String value) {
        try { return mapper.readTree(value); }
        catch (com.fasterxml.jackson.core.JsonProcessingException ex) { throw new IllegalStateException("回执解析失败", ex); }
    }
}
