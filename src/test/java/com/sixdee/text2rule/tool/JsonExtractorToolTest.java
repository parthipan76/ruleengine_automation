package com.sixdee.text2rule.tool;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class JsonExtractorToolTest {

    // Simple POJO for testing
    static class TestDto {
        @JsonProperty("name")
        public String name;
        @JsonProperty("value")
        public int value;
    }

    @Test
    public void testCleanJson() {
        String input = "{\"name\":\"test\",\"value\":123}";
        TestDto result = JsonExtractorTool.extractAndParse(input, TestDto.class);
        Assertions.assertNotNull(result);
        Assertions.assertEquals("test", result.name);
        Assertions.assertEquals(123, result.value);
    }

    @Test
    public void testJsonInMarkdown() {
        String input = "Here is the result:\n```json\n{\"name\":\"markdown\",\"value\":456}\n```";
        TestDto result = JsonExtractorTool.extractAndParse(input, TestDto.class);
        Assertions.assertNotNull(result);
        Assertions.assertEquals("markdown", result.name);
        Assertions.assertEquals(456, result.value);
    }

    @Test
    public void testJsonInGenericCodeBlock() {
        String input = "Some text before\n```\n{\"name\":\"generic\",\"value\":789}\n```\nSome text after";
        TestDto result = JsonExtractorTool.extractAndParse(input, TestDto.class);
        Assertions.assertNotNull(result);
        Assertions.assertEquals("generic", result.name);
        Assertions.assertEquals(789, result.value);
    }

    @Test
    public void testJsonEmbeddedInText() {
        String input = "Sure, here is the JSON: {\"name\":\"embedded\",\"value\":101} hope this helps.";
        TestDto result = JsonExtractorTool.extractAndParse(input, TestDto.class);
        Assertions.assertNotNull(result);
        Assertions.assertEquals("embedded", result.name);
        Assertions.assertEquals(101, result.value);
    }

    @Test
    public void testMalformedJson() {
        String input = "{\"name\":\"broken\",";
        TestDto result = JsonExtractorTool.extractAndParse(input, TestDto.class);
        Assertions.assertNull(result);
    }

    @Test
    public void testNoJson() {
        String input = "Just some text without brackets.";
        TestDto result = JsonExtractorTool.extractAndParse(input, TestDto.class);
        Assertions.assertNull(result);
    }

    @Test
    public void testEmptyInput() {
        Assertions.assertNull(JsonExtractorTool.extractAndParse(null, TestDto.class));
        Assertions.assertNull(JsonExtractorTool.extractAndParse("", TestDto.class));
    }
}
