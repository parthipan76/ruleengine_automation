package com.sixdee.text2rule.agent;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sixdee.text2rule.config.ConfigurationManager;
import com.sixdee.text2rule.config.MySQLConnectionManager;
import com.sixdee.text2rule.factory.LLMClientFactory;

/**
 * UPDATED ActionExtractionAgent - N8N Compatible Output
 * 
 * This agent now produces output matching N8N format with full BONUS_CRITERIA structure.
 * 
 * Key Changes from Original:
 * 1. Extracts full bonus details (action_type, value, maxValue, aggregation, noOfDays)
 * 2. Queries PROD_PRODUCT_MASTER for product_id matching
 * 3. Generates promotional messages with emojis
 * 4. Builds complete benefit_details structure
 * 
 * Output format:
 * {
 *   "ActionName": "Send Promotion",
 *   "ActionKey": "recharge_10gb_bonus",
 *   "Channel": "SMS",
 *   "Message": "Recharge between 6 RO and 10 RO and get a 10GB data pack...",
 *   "ProductId": "1934114",
 *   "BONUS_CRITERIA": {
 *     "action_type": "Recharge",
 *     "value": 6,
 *     "aggregation": "Range",
 *     "noOfDays": 15,
 *     "bonusThreshold": 1,
 *     "maxValue": 10,
 *     "conditions": " ",
 *     "benefit_details": {
 *       "product_id": "1934114",
 *       "Bonus_type": "telco_product",
 *       "success_message": "Congratulations! 10GB data...",
 *       "error_message": "Sorry, we couldn't add..."
 *     }
 *   }
 * }
 */
public class ActionExtractionAgent implements IAgent {
    private static final Logger logger = LoggerFactory.getLogger(ActionExtractionAgent.class);

    private final LLMClientFactory llmClient;
    private final ObjectMapper objectMapper;
    private final ConfigurationManager configManager;
    private final MySQLConnectionManager dbManager;

    public ActionExtractionAgent(LLMClientFactory llmClient) {
        this.llmClient = llmClient;
        this.objectMapper = new ObjectMapper();
        this.configManager = ConfigurationManager.getInstance();
        this.dbManager = MySQLConnectionManager.getInstance();
    }

    @Override
    public String execute(String input) {
        if (input == null || input.trim().isEmpty()) {
            logger.warn("Empty input received");
            return createEmptyResult();
        }

        logger.info("Extracting action from: {}", input);

        try {
            // Step 1: Extract basic action structure using LLM
            ActionDetails actionDetails = extractActionWithLLM(input);
            
            // Step 2: Extract bonus criteria
            BonusCriteria bonus = extractBonusCriteria(input, actionDetails);
            
            // Step 3: Match product from database
            ProductMatch product = matchProduct(bonus);
            
            // Step 4: Generate messages
            GeneratedMessages messages = generateMessages(input, bonus, product);
            
            // Step 5: Build complete result
            return buildCompleteResult(actionDetails, bonus, product, messages);

        } catch (Exception e) {
            logger.error("Error extracting action", e);
            return createErrorResult(e.getMessage());
        }
    }

    /**
     * Extract basic action details using LLM.
     */
    private ActionDetails extractActionWithLLM(String input) {
        ActionDetails details = new ActionDetails();

        try {
            String prompt = buildActionExtractionPrompt(input);
            String response = llmClient.chat(prompt);
            
            String jsonStr = extractJsonFromResponse(response);
            JsonNode json = objectMapper.readTree(jsonStr);

            details.actionName = getTextValue(json, "action_name", "Send Promotion");
            details.actionKey = getTextValue(json, "action_key", "");
            details.channel = getTextValue(json, "channel", "SMS");
            details.userType = getTextValue(json, "user_type", "");
            details.trigger = getTextValue(json, "trigger", "");

            logger.debug("Extracted action: name={}, channel={}", details.actionName, details.channel);

        } catch (Exception e) {
            logger.warn("LLM action extraction failed, using defaults", e);
            details.actionName = determineActionName(input);
            details.channel = determineChannel(input);
        }

        return details;
    }

    /**
     * Extract bonus criteria from input.
     */
    private BonusCriteria extractBonusCriteria(String input, ActionDetails action) {
        BonusCriteria bonus = new BonusCriteria();

        try {
            // Try LLM extraction first
            String prompt = buildBonusExtractionPrompt(input);
            String response = llmClient.chat(prompt);
            
            String jsonStr = extractJsonFromResponse(response);
            JsonNode json = objectMapper.readTree(jsonStr);

            bonus.actionType = getTextValue(json, "action_type", "Recharge");
            bonus.value = parseIntSafe(getTextValue(json, "value", "0"));
            bonus.maxValue = parseIntSafe(getTextValue(json, "max_value", "0"));
            bonus.aggregation = getTextValue(json, "aggregation", "Sum");
            bonus.noOfDays = parseIntSafe(getTextValue(json, "validity_days", "0"));
            bonus.bonusThreshold = parseIntSafe(getTextValue(json, "bonus_threshold", "1"));
            bonus.conditions = getTextValue(json, "extra_condition", " ");
            bonus.productDescription = getTextValue(json, "product_description", "");
            bonus.dataSize = getTextValue(json, "data_size", "");
            bonus.bonusType = getTextValue(json, "bonus_type", "telco_product");

        } catch (Exception e) {
            logger.warn("LLM bonus extraction failed, using regex", e);
            bonus = extractBonusWithRegex(input);
        }

        return bonus;
    }

    /**
     * Extract bonus using regex patterns (fallback).
     */
    private BonusCriteria extractBonusWithRegex(String input) {
        BonusCriteria bonus = new BonusCriteria();
        String lowerInput = input.toLowerCase();

        // Determine action type
        if (lowerInput.contains("recharge")) {
            bonus.actionType = "Recharge";
        } else if (lowerInput.contains("purchase")) {
            bonus.actionType = "Purchase";
        } else if (lowerInput.contains("topup") || lowerInput.contains("top-up")) {
            bonus.actionType = "TopUp";
        } else {
            bonus.actionType = "Recharge";
        }

        // Extract range: "between 6 and 10"
        Pattern rangePattern = Pattern.compile(
            "(?:between|from)\\s+(\\d+(?:\\.\\d+)?)\\s*(?:RO|OMR|\\$|₹)?\\s*(?:and|to)\\s*(\\d+(?:\\.\\d+)?)\\s*(?:RO|OMR|\\$|₹)?",
            Pattern.CASE_INSENSITIVE
        );
        Matcher rangeMatcher = rangePattern.matcher(input);
        if (rangeMatcher.find()) {
            bonus.value = parseIntSafe(rangeMatcher.group(1));
            bonus.maxValue = parseIntSafe(rangeMatcher.group(2));
            bonus.aggregation = "Range";
        }

        // Extract data size: "10 GB"
        Pattern dataPattern = Pattern.compile("(\\d+)\\s*(?:GB|G|MB|M)", Pattern.CASE_INSENSITIVE);
        Matcher dataMatcher = dataPattern.matcher(input);
        if (dataMatcher.find()) {
            bonus.dataSize = dataMatcher.group(1);
            bonus.productDescription = bonus.dataSize + " GB data pack";
        }

        // Extract validity: "valid for 15 days"
        Pattern validityPattern = Pattern.compile(
            "(?:valid(?:ity)?\\s+(?:for\\s+)?|for\\s+)(\\d+)\\s*days?",
            Pattern.CASE_INSENSITIVE
        );
        Matcher validityMatcher = validityPattern.matcher(input);
        if (validityMatcher.find()) {
            bonus.noOfDays = parseIntSafe(validityMatcher.group(1));
        }

        bonus.bonusThreshold = 1;
        bonus.conditions = " ";
        bonus.bonusType = "telco_product";

        return bonus;
    }

    /**
     * Match product from database.
     */
    private ProductMatch matchProduct(BonusCriteria bonus) {
        ProductMatch product = new ProductMatch();

        try {
            // Try by product description
            if (bonus.productDescription != null && !bonus.productDescription.isEmpty()) {
                Map<String, Object> dbProduct = dbManager.findProductByName(bonus.productDescription);
                if (dbProduct != null) {
                    product.productId = String.valueOf(dbProduct.get("product_id"));
                    product.productName = String.valueOf(dbProduct.get("product_name"));
                    product.validity = bonus.noOfDays;
                    product.found = true;
                    return product;
                }
            }

            // Try by specs (data size + validity)
            if (bonus.dataSize != null && !bonus.dataSize.isEmpty()) {
                Map<String, Object> dbProduct = dbManager.findProductBySpecs(
                    bonus.dataSize, 
                    String.valueOf(bonus.noOfDays)
                );
                if (dbProduct != null) {
                    product.productId = String.valueOf(dbProduct.get("product_id"));
                    product.productName = String.valueOf(dbProduct.get("product_name"));
                    product.validity = (Integer) dbProduct.get("validity");
                    product.found = true;
                    return product;
                }
            }

        } catch (Exception e) {
            logger.warn("Product matching failed", e);
        }

        // Fallback - product not found
        product.found = false;
        product.productName = bonus.productDescription;
        product.validity = bonus.noOfDays;

        return product;
    }

    /**
     * Generate promotional messages.
     */
    private GeneratedMessages generateMessages(String input, BonusCriteria bonus, ProductMatch product) {
        GeneratedMessages messages = new GeneratedMessages();

        String productDesc = product.productName != null && !product.productName.isEmpty() 
            ? product.productName 
            : (bonus.dataSize != null ? bonus.dataSize + "GB data for " + bonus.noOfDays + " days" : "bonus");

        // Campaign message
        if ("Range".equals(bonus.aggregation) && bonus.value > 0 && bonus.maxValue > 0) {
            messages.campaignMessage = String.format(
                "Recharge between %d RO and %d RO and get a %s.",
                bonus.value, bonus.maxValue, productDesc
            );
        } else if (bonus.value > 0) {
            messages.campaignMessage = String.format(
                "Recharge %d+ RO and get %s free!",
                bonus.value, productDesc
            );
        } else {
            messages.campaignMessage = String.format("Get %s now!", productDesc);
        }

        // Success message with emoji
        messages.successMessage = String.format(
            "Congratulations! %s has been added to your account. Enjoy fast surfing! 🎉",
            productDesc
        );

        // Error message
        messages.errorMessage = String.format(
            "Sorry, we couldn't add the %s to your account right now. Please try again later.",
            productDesc
        );

        return messages;
    }

    /**
     * Build complete result JSON.
     */
    private String buildCompleteResult(ActionDetails action, BonusCriteria bonus, 
                                       ProductMatch product, GeneratedMessages messages) {
        try {
            ObjectNode root = objectMapper.createObjectNode();
            
            // Basic action fields
            root.put("ActionName", action.actionName);
            root.put("ActionKey", generateActionKey(action, bonus));
            root.put("Channel", action.channel);
            root.put("Message", messages.campaignMessage);
            
            if (product.found && product.productId != null) {
                root.put("ProductId", product.productId);
            }
            
            root.put("Conditions", bonus.conditions.trim().isEmpty() ? "" : bonus.conditions);

            // Build BONUS_CRITERIA
            ObjectNode bonusCriteria = objectMapper.createObjectNode();
            bonusCriteria.put("action_type", bonus.actionType);
            bonusCriteria.put("value", bonus.value);
            bonusCriteria.put("aggregation", bonus.aggregation);
            bonusCriteria.put("noOfDays", bonus.noOfDays);
            bonusCriteria.put("bonusThreshold", bonus.bonusThreshold);
            
            if (bonus.maxValue > 0) {
                bonusCriteria.put("maxValue", bonus.maxValue);
            }
            
            bonusCriteria.put("conditions", bonus.conditions);

            // Build benefit_details
            ObjectNode benefitDetails = objectMapper.createObjectNode();
            if (product.found && product.productId != null) {
                benefitDetails.put("product_id", product.productId);
            }
            benefitDetails.put("Bonus_type", bonus.bonusType);
            benefitDetails.put("success_message", messages.successMessage);
            benefitDetails.put("error_message", messages.errorMessage);

            bonusCriteria.set("benefit_details", benefitDetails);
            root.set("BONUS_CRITERIA", bonusCriteria);

            // Also store in DSL format for FinalRuleDslRenderer
            root.put("ActionDetails_DSL", buildActionDetailsDsl(action, bonus, product, messages));

            return objectMapper.writeValueAsString(root);

        } catch (Exception e) {
            logger.error("Error building result", e);
            return createErrorResult(e.getMessage());
        }
    }

    /**
     * Build action details string for legacy compatibility.
     */
    private String buildActionDetailsDsl(ActionDetails action, BonusCriteria bonus, 
                                         ProductMatch product, GeneratedMessages messages) {
        StringBuilder dsl = new StringBuilder();
        
        dsl.append("Action: ").append(action.actionName);
        dsl.append(", Channel: ").append(action.channel);
        dsl.append(", Message: ").append(messages.campaignMessage);
        
        if (product.found && product.productId != null) {
            dsl.append(", ProductId: ").append(product.productId);
        }

        // Include bonus info for FinalRuleDslRenderer to extract
        dsl.append(", BonusType: ").append(bonus.bonusType);
        dsl.append(", ActionType: ").append(bonus.actionType);
        dsl.append(", Value: ").append(bonus.value);
        dsl.append(", MaxValue: ").append(bonus.maxValue);
        dsl.append(", Aggregation: ").append(bonus.aggregation);
        dsl.append(", NoOfDays: ").append(bonus.noOfDays);
        dsl.append(", SuccessMessage: ").append(messages.successMessage);
        dsl.append(", ErrorMessage: ").append(messages.errorMessage);

        return dsl.toString();
    }

    /**
     * Generate action key.
     */
    private String generateActionKey(ActionDetails action, BonusCriteria bonus) {
        StringBuilder key = new StringBuilder();
        
        key.append(bonus.actionType.toLowerCase());
        
        if (bonus.dataSize != null && !bonus.dataSize.isEmpty()) {
            key.append("_").append(bonus.dataSize).append("gb");
        }
        
        key.append("_bonus");
        
        return key.toString();
    }

    /**
     * Build action extraction prompt.
     */
    private String buildActionExtractionPrompt(String input) {
        return """
            Extract action details from the following text.
            
            TEXT: %s
            
            Extract:
            - action_name: The action to perform (Send Promotion, Send Reminder, etc.)
            - action_key: A unique key for this action
            - channel: Communication channel (SMS, Email, Push, etc.)
            - user_type: Target user type if mentioned
            - trigger: What triggers this action
            
            Return ONLY valid JSON:
            {
              "action_name": "",
              "action_key": "",
              "channel": "SMS",
              "user_type": "",
              "trigger": ""
            }
            """.formatted(input);
    }

    /**
     * Build bonus extraction prompt.
     */
    private String buildBonusExtractionPrompt(String input) {
        return """
            Extract bonus/offer details from the following text.
            
            TEXT: %s
            
            Extract:
            - action_type: Trigger action (Recharge, Purchase, TopUp)
            - value: Minimum amount (integer)
            - max_value: Maximum amount if range (integer, 0 if not range)
            - aggregation: "Range" if between X and Y, "Sum" if total, "Count" if count-based
            - validity_days: Number of days the bonus is valid (integer)
            - bonus_threshold: Minimum times action needed (default 1)
            - extra_condition: Any additional conditions
            - product_description: Description of bonus product
            - data_size: Data size in GB if applicable
            - bonus_type: "telco_product", "cashback", "discount"
            
            Return ONLY valid JSON:
            {
              "action_type": "Recharge",
              "value": "0",
              "max_value": "0",
              "aggregation": "Sum",
              "validity_days": "0",
              "bonus_threshold": "1",
              "extra_condition": "",
              "product_description": "",
              "data_size": "",
              "bonus_type": "telco_product"
            }
            """.formatted(input);
    }

    /**
     * Determine action name from input.
     */
    private String determineActionName(String input) {
        String lower = input.toLowerCase();
        
        if (lower.contains("reminder")) return "Send Reminder";
        if (lower.contains("upgrade")) return "Upgrade Offer";
        if (lower.contains("notification")) return "Send Notification";
        if (lower.contains("alert")) return "Send Alert";
        
        return "Send Promotion";
    }

    /**
     * Determine channel from input.
     */
    private String determineChannel(String input) {
        String lower = input.toLowerCase();
        
        if (lower.contains("email")) return "Email";
        if (lower.contains("push") || lower.contains("notification")) return "Push";
        if (lower.contains("call") || lower.contains("ivr")) return "IVR";
        
        return "SMS";
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
     * Get text value from JSON node.
     */
    private String getTextValue(JsonNode node, String field, String defaultValue) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return defaultValue;
        }
        return node.get(field).asText(defaultValue);
    }

    /**
     * Parse integer safely.
     */
    private int parseIntSafe(String value) {
        try {
            if (value == null || value.isEmpty()) return 0;
            // Handle decimal values
            if (value.contains(".")) {
                return (int) Double.parseDouble(value);
            }
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Create empty result.
     */
    private String createEmptyResult() {
        return "{\"ActionName\":\"Send Promotion\",\"Channel\":\"SMS\",\"BONUS_CRITERIA\":{\"conditions\":\" \"}}";
    }

    /**
     * Create error result.
     */
    private String createErrorResult(String message) {
        try {
            ObjectNode error = objectMapper.createObjectNode();
            error.put("error", message);
            error.put("ActionName", "Send Promotion");
            error.put("Channel", "SMS");
            return objectMapper.writeValueAsString(error);
        } catch (Exception e) {
            return createEmptyResult();
        }
    }

    // Inner classes

    private static class ActionDetails {
        String actionName = "Send Promotion";
        String actionKey = "";
        String channel = "SMS";
        String userType = "";
        String trigger = "";
    }

    private static class BonusCriteria {
        String actionType = "Recharge";
        int value = 0;
        int maxValue = 0;
        String aggregation = "Sum";
        int noOfDays = 0;
        int bonusThreshold = 1;
        String conditions = " ";
        String productDescription = "";
        String dataSize = "";
        String bonusType = "telco_product";
    }

    private static class ProductMatch {
        String productId = null;
        String productName = "";
        int validity = 0;
        boolean found = false;
    }

    private static class GeneratedMessages {
        String campaignMessage = "";
        String successMessage = "";
        String errorMessage = "";
    }
}