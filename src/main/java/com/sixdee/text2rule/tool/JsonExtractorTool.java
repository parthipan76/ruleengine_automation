package com.sixdee.text2rule.tool;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility tool for extracting and parsing JSON from text content.
 * Handles markdown code blocks and raw JSON strings.
 */
public class JsonExtractorTool {
    private static final Logger logger = LoggerFactory.getLogger(JsonExtractorTool.class);
    private static final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    /**
     * Extracts JSON from the content and parses it into the specified class.
     * Tries to find JSON in markdown blocks first, then looks for raw JSON
     * structure.
     *
     * @param content The text content containing JSON
     * @param clazz   The class to parse the JSON into
     * @param <T>     The type of the class
     * @return The parsed object, or null if extraction/parsing fails
     */
    public static <T> T extractAndParse(String content, Class<T> clazz) {
        if (content == null || content.trim().isEmpty()) {
            logger.warn("Content is empty, cannot extract JSON");
            return null;
        }

        String jsonString = extractJsonString(content);
        if (jsonString == null) {
            logger.warn("No JSON found in content");
            return null;
        }

        try {
            return objectMapper.readValue(jsonString, clazz);
        } catch (Exception e) {
            logger.error("Failed to parse JSON content: {}", jsonString, e);
            return null;
        }
    }

    private static String extractJsonString(String content) {
        // 1. Try to extract from markdown code blocks (```json ... ```)
        Pattern pattern = Pattern.compile("```json\\s*(\\{.*?\\})\\s*```", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
            return matcher.group(1);
        }

        // 2. Try to extract from generic code blocks (``` ... ```)
        pattern = Pattern.compile("```\\s*(\\{.*?\\})\\s*```", Pattern.DOTALL);
        matcher = pattern.matcher(content);
        if (matcher.find()) {
            return matcher.group(1);
        }

        // 3. Fallback: Find the first '{' and last '}'
        int start = content.indexOf("{");
        int end = content.lastIndexOf("}");
        if (start >= 0 && end > start) {
            return content.substring(start, end + 1);
        }

        return null;
    }
}
