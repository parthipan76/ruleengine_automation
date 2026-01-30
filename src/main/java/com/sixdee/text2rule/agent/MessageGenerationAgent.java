package com.sixdee.text2rule.agent;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sixdee.text2rule.factory.LLMClientFactory;

/**
 * Agent for generating promotional messages like N8N.
 * 
 * Generates three types of messages:
 * 1. campaign_message - Main promotional text for SMS/notification
 * 2. success_message - Confirmation message when bonus is applied
 * 3. error_message - Error message when bonus fails
 * 
 * Example N8N output:
 * MESSAGE = "🎉 Great news! Recharge ₹6‑₹10 now and snag 10 GB data free for 15 days!"
 * success_message = "Congratulations! 10GB data for 15 days has been added to your account. Enjoy fast surfing! 🎉"
 * error_message = "Sorry, we couldn't add the 10GB data pack to your account right now. Please try again later."
 */
public class MessageGenerationAgent implements IAgent {
    private static final Logger logger = LoggerFactory.getLogger(MessageGenerationAgent.class);

    private final LLMClientFactory llmClient;
    private final ObjectMapper objectMapper;

    public MessageGenerationAgent(LLMClientFactory llmClient) {
        this.llmClient = llmClient;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public String execute(String input) {
        if (input == null || input.trim().isEmpty()) {
            return "{}";
        }

        logger.info("Generating messages for: {}", input);

        try {
            // Extract context from input
            MessageContext context = extractContext(input);
            
            // Generate messages
            GeneratedMessages messages = generateMessages(context);
            
            // Build response
            return buildResponseJson(messages);

        } catch (Exception e) {
            logger.error("Error generating messages", e);
            return buildDefaultMessages();
        }
    }

    /**
     * Generate messages with full context (bonus details + product info).
     */
    public GeneratedMessages generateWithContext(String actionText, Map<String, Object> bonusDetails, Map<String, Object> productInfo) {
        MessageContext context = new MessageContext();
        
        // Extract from action text
        context.actionDescription = actionText;
        
        // Extract from bonus details
        if (bonusDetails != null) {
            context.minValue = getStringValue(bonusDetails, "value");
            context.maxValue = getStringValue(bonusDetails, "maxValue");
            context.aggregation = getStringValue(bonusDetails, "aggregation");
            context.validityDays = getStringValue(bonusDetails, "noOfDays");
        }
        
        // Extract from product info
        if (productInfo != null) {
            context.productName = getStringValue(productInfo, "product_name");
            context.dataSize = extractDataSize(context.productName);
        }
        
        // Fallback extraction from text
        if (context.dataSize == null || context.dataSize.isEmpty()) {
            context.dataSize = extractDataSizeFromText(actionText);
        }
        
        return generateMessages(context);
    }

    /**
     * Extract context from input text.
     */
    private MessageContext extractContext(String input) {
        MessageContext context = new MessageContext();
        context.actionDescription = input;
        
        // Extract data size: "10 GB", "5GB"
        context.dataSize = extractDataSizeFromText(input);
        
        // Extract validity: "valid for 15 days"
        Pattern validityPattern = Pattern.compile("(?:valid(?:ity)?\\s+(?:for\\s+)?|for\\s+)(\\d+)\\s*days?", Pattern.CASE_INSENSITIVE);
        Matcher validityMatcher = validityPattern.matcher(input);
        if (validityMatcher.find()) {
            context.validityDays = validityMatcher.group(1);
        }
        
        // Extract amount range: "between 6 RO and 10 RO"
        Pattern rangePattern = Pattern.compile("(?:between|from)\\s+(\\d+(?:\\.\\d+)?)\\s*(?:RO|OMR|\\$|₹)?\\s*(?:and|to)\\s*(\\d+(?:\\.\\d+)?)\\s*(?:RO|OMR|\\$|₹)?", Pattern.CASE_INSENSITIVE);
        Matcher rangeMatcher = rangePattern.matcher(input);
        if (rangeMatcher.find()) {
            context.minValue = rangeMatcher.group(1);
            context.maxValue = rangeMatcher.group(2);
            context.aggregation = "Range";
        }
        
        // Extract channel
        if (input.toLowerCase().contains("sms")) {
            context.channel = "SMS";
        } else if (input.toLowerCase().contains("notification") || input.toLowerCase().contains("push")) {
            context.channel = "Push";
        } else if (input.toLowerCase().contains("email")) {
            context.channel = "Email";
        }
        
        return context;
    }

    /**
     * Extract data size from text.
     */
    private String extractDataSizeFromText(String text) {
        Pattern dataPattern = Pattern.compile("(\\d+)\\s*(?:GB|G|MB|M)", Pattern.CASE_INSENSITIVE);
        Matcher dataMatcher = dataPattern.matcher(text);
        if (dataMatcher.find()) {
            return dataMatcher.group(1);
        }
        return null;
    }

    /**
     * Extract data size from product name.
     */
    private String extractDataSize(String productName) {
        if (productName == null) return null;
        return extractDataSizeFromText(productName);
    }

    /**
     * Generate all three message types.
     */
    private GeneratedMessages generateMessages(MessageContext context) {
        GeneratedMessages messages = new GeneratedMessages();
        
        // Build product description
        String productDesc = buildProductDescription(context);
        
        // Generate campaign message (promotional SMS)
        messages.campaignMessage = generateCampaignMessage(context, productDesc);
        
        // Generate success message
        messages.successMessage = generateSuccessMessage(context, productDesc);
        
        // Generate error message
        messages.errorMessage = generateErrorMessage(productDesc);
        
        return messages;
    }

    /**
     * Build product description from context.
     */
    private String buildProductDescription(MessageContext context) {
        StringBuilder desc = new StringBuilder();
        
        if (context.dataSize != null && !context.dataSize.isEmpty()) {
            desc.append(context.dataSize).append("GB data");
        } else if (context.productName != null && !context.productName.isEmpty()) {
            desc.append(context.productName);
        } else {
            desc.append("bonus");
        }
        
        if (context.validityDays != null && !context.validityDays.isEmpty() && !"0".equals(context.validityDays)) {
            desc.append(" for ").append(context.validityDays).append(" days");
        }
        
        return desc.toString();
    }

    /**
     * Generate campaign/promotional message.
     */
    private String generateCampaignMessage(MessageContext context, String productDesc) {
        StringBuilder msg = new StringBuilder();
        
        // Add promotional text based on context
        if ("Range".equals(context.aggregation) && context.minValue != null && context.maxValue != null) {
            msg.append("Recharge between ")
               .append(context.minValue).append(" RO and ")
               .append(context.maxValue).append(" RO and get a ")
               .append(productDesc).append(".");
        } else if (context.minValue != null && !context.minValue.isEmpty()) {
            msg.append("Recharge ₹").append(context.minValue)
               .append("+ and get ").append(productDesc).append(" free!");
        } else {
            msg.append("Get ").append(productDesc).append(" now!");
        }
        
        return msg.toString();
    }

    /**
     * Generate success message with emoji.
     */
    private String generateSuccessMessage(MessageContext context, String productDesc) {
        return "Congratulations! " + productDesc + " has been added to your account. Enjoy fast surfing! 🎉";
    }

    /**
     * Generate error message.
     */
    private String generateErrorMessage(String productDesc) {
        return "Sorry, we couldn't add the " + productDesc + " to your account right now. Please try again later.";
    }

    /**
     * Use LLM to generate more creative messages.
     */
    public GeneratedMessages generateWithLLM(MessageContext context) {
        try {
            String prompt = """
                Generate promotional messages for a telecom campaign.
                
                Context:
                - Product: %s GB data pack
                - Validity: %s days
                - Trigger: Recharge between %s and %s
                
                Generate exactly 3 messages in JSON format:
                {
                  "campaign_message": "Promotional SMS text with emojis, under 160 characters",
                  "success_message": "Congratulation message when bonus is applied, with 🎉 emoji",
                  "error_message": "Apology message when bonus fails to apply"
                }
                
                Return ONLY valid JSON:
                """.formatted(
                    context.dataSize != null ? context.dataSize : "data",
                    context.validityDays != null ? context.validityDays : "validity",
                    context.minValue != null ? context.minValue : "min",
                    context.maxValue != null ? context.maxValue : "max"
                );

            String response = llmClient.chat(prompt);
            
            // Parse response
            String jsonStr = extractJsonFromResponse(response);
            JsonNode json = objectMapper.readTree(jsonStr);
            
            GeneratedMessages messages = new GeneratedMessages();
            messages.campaignMessage = json.has("campaign_message") ? json.get("campaign_message").asText() : "";
            messages.successMessage = json.has("success_message") ? json.get("success_message").asText() : "";
            messages.errorMessage = json.has("error_message") ? json.get("error_message").asText() : "";
            
            return messages;

        } catch (Exception e) {
            logger.warn("LLM message generation failed, using templates", e);
            return generateMessages(context);
        }
    }

    /**
     * Build response JSON.
     */
    private String buildResponseJson(GeneratedMessages messages) {
        try {
            ObjectNode json = objectMapper.createObjectNode();
            json.put("campaign_message", messages.campaignMessage);
            json.put("success_message", messages.successMessage);
            json.put("error_message", messages.errorMessage);
            return objectMapper.writeValueAsString(json);
        } catch (Exception e) {
            return buildDefaultMessages();
        }
    }

    /**
     * Build default messages.
     */
    private String buildDefaultMessages() {
        return "{\"campaign_message\":\"\",\"success_message\":\"\",\"error_message\":\"\"}";
    }

    /**
     * Extract JSON from LLM response.
     */
    private String extractJsonFromResponse(String response) {
        if (response == null) return "{}";
        
        int start = response.indexOf('{');
        int end = response.lastIndexOf('}');
        
        if (start >= 0 && end > start) {
            return response.substring(start, end + 1);
        }
        
        return "{}";
    }

    /**
     * Get string value from map.
     */
    private String getStringValue(Map<String, Object> map, String key) {
        if (map == null || !map.containsKey(key)) return null;
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }

    // Inner classes

    public static class MessageContext {
        public String actionDescription;
        public String productName;
        public String dataSize;
        public String validityDays;
        public String minValue;
        public String maxValue;
        public String aggregation;
        public String channel = "SMS";
    }

    public static class GeneratedMessages {
        public String campaignMessage = "";
        public String successMessage = "";
        public String errorMessage = "";
    }
}