package com.sixdee.text2rule.agent;

import java.util.HashMap;
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
 * Agent for extracting full BONUS_CRITERIA structure from action statements.
 * 
 * Produces N8N-compatible output:
 * BONUS_CRITERIA = {
 *   action_type=Recharge, 
 *   value=6, 
 *   aggregation=Range, 
 *   noOfDays=15, 
 *   bonusThreshold=1, 
 *   maxValue=10, 
 *   conditions=" ", 
 *   benefit_details={
 *     product_id=1934114, 
 *     Bonus_type="telco_product", 
 *     success_message="...", 
 *     error_message="..."
 *   }
 * }
 */
public class BonusExtractionAgent implements IAgent {
    private static final Logger logger = LoggerFactory.getLogger(BonusExtractionAgent.class);

    private final LLMClientFactory llmClient;
    private final ObjectMapper objectMapper;
    private final MySQLConnectionManager dbManager;
    private final ConfigurationManager configManager;

    public BonusExtractionAgent(LLMClientFactory llmClient) {
        this.llmClient = llmClient;
        this.objectMapper = new ObjectMapper();
        this.dbManager = MySQLConnectionManager.getInstance();
        this.configManager = ConfigurationManager.getInstance();
    }

    @Override
    public String execute(String input) {
        if (input == null || input.trim().isEmpty()) {
            return "{}";
        }

        logger.info("Extracting bonus criteria from: {}", input);

        try {
            // Step 1: Extract bonus details using LLM
            BonusDetails bonusDetails = extractBonusDetailsWithLLM(input);
            
            // Step 2: Match product from database
            Map<String, Object> product = matchProduct(bonusDetails);
            
            // Step 3: Generate messages
            MessageDetails messages = generateMessages(bonusDetails, product);
            
            // Step 4: Build final BONUS_CRITERIA structure
            return buildBonusCriteriaJson(bonusDetails, product, messages);

        } catch (Exception e) {
            logger.error("Error extracting bonus criteria", e);
            return buildDefaultBonusCriteria();
        }
    }

    /**
     * Extract bonus details using LLM.
     */
    private BonusDetails extractBonusDetailsWithLLM(String input) {
        BonusDetails details = new BonusDetails();

        try {
            String prompt = buildExtractionPrompt(input);
            String response = llmClient.chat(prompt);
            
            // Parse JSON response
            String jsonStr = extractJsonFromResponse(response);
            JsonNode json = objectMapper.readTree(jsonStr);

            details.actionType = getTextValue(json, "action_type", "Recharge");
            details.value = getTextValue(json, "value", "0");
            details.maxValue = getTextValue(json, "max_value", "0");
            details.aggregation = getTextValue(json, "aggregation", "Sum");
            details.noOfDays = getTextValue(json, "validity_days", "0");
            details.bonusThreshold = getTextValue(json, "bonus_threshold", "1");
            details.conditions = getTextValue(json, "extra_condition", " ");
            details.productDescription = getTextValue(json, "product_description", "");
            details.dataSize = getTextValue(json, "data_size", "");
            details.bonusType = getTextValue(json, "bonus_type", "telco_product");

            logger.debug("Extracted bonus details: actionType={}, value={}, maxValue={}, noOfDays={}",
                    details.actionType, details.value, details.maxValue, details.noOfDays);

        } catch (Exception e) {
            logger.warn("LLM extraction failed, using regex fallback", e);
            details = extractBonusDetailsWithRegex(input);
        }

        return details;
    }

    /**
     * Fallback regex-based extraction.
     */
    private BonusDetails extractBonusDetailsWithRegex(String input) {
        BonusDetails details = new BonusDetails();
        String lowerInput = input.toLowerCase();

        // Extract action type
        if (lowerInput.contains("recharge")) {
            details.actionType = "Recharge";
        } else if (lowerInput.contains("purchase")) {
            details.actionType = "Purchase";
        } else if (lowerInput.contains("topup") || lowerInput.contains("top-up")) {
            details.actionType = "TopUp";
        } else {
            details.actionType = "Recharge";
        }

        // Extract range values: "between X and Y", "from X to Y"
        Pattern rangePattern = Pattern.compile(
            "(?:between|from)\\s+(\\d+(?:\\.\\d+)?)\\s*(?:RO|OMR|\\$|₹)?\\s*(?:and|to)\\s*(\\d+(?:\\.\\d+)?)\\s*(?:RO|OMR|\\$|₹)?",
            Pattern.CASE_INSENSITIVE
        );
        Matcher rangeMatcher = rangePattern.matcher(input);
        if (rangeMatcher.find()) {
            details.value = rangeMatcher.group(1);
            details.maxValue = rangeMatcher.group(2);
            details.aggregation = "Range";
        }

        // Extract data size: "10 GB", "5GB"
        Pattern dataPattern = Pattern.compile("(\\d+)\\s*(?:GB|G|MB|M)", Pattern.CASE_INSENSITIVE);
        Matcher dataMatcher = dataPattern.matcher(input);
        if (dataMatcher.find()) {
            details.dataSize = dataMatcher.group(1);
            details.productDescription = details.dataSize + " GB data pack";
        }

        // Extract validity: "valid for X days", "X days validity"
        Pattern validityPattern = Pattern.compile(
            "(?:valid(?:ity)?\\s+(?:for\\s+)?|for\\s+)(\\d+)\\s*days?",
            Pattern.CASE_INSENSITIVE
        );
        Matcher validityMatcher = validityPattern.matcher(input);
        if (validityMatcher.find()) {
            details.noOfDays = validityMatcher.group(1);
        }

        details.bonusThreshold = "1";
        details.conditions = " ";
        details.bonusType = "telco_product";

        return details;
    }

    /**
     * Match product from database.
     */
    private Map<String, Object> matchProduct(BonusDetails details) {
        Map<String, Object> product = null;

        // Try by product description first
        if (details.productDescription != null && !details.productDescription.isEmpty()) {
            product = dbManager.findProductByName(details.productDescription);
        }

        // Try by specs (data size + validity)
        if (product == null && details.dataSize != null && !details.dataSize.isEmpty()) {
            product = dbManager.findProductBySpecs(details.dataSize, details.noOfDays);
        }

        // Return default if not found
        if (product == null) {
            product = new HashMap<>();
            product.put("product_id", null);
            product.put("product_name", details.productDescription);
            logger.warn("Product not found in database, using placeholder");
        }

        return product;
    }

    /**
     * Generate promotional messages.
     */
    private MessageDetails generateMessages(BonusDetails bonus, Map<String, Object> product) {
        MessageDetails messages = new MessageDetails();

        String productName = product.get("product_name") != null ? 
                product.get("product_name").toString() : 
                bonus.dataSize + "GB data for " + bonus.noOfDays + " days";

        // Generate success message
        messages.successMessage = String.format(
            "Congratulations! %s has been added to your account. Enjoy fast surfing! 🎉",
            productName
        );

        // Generate error message
        messages.errorMessage = String.format(
            "Sorry, we couldn't add the %s to your account right now. Please try again later.",
            productName
        );

        // Generate campaign message
        if (bonus.aggregation.equals("Range") && bonus.value != null && bonus.maxValue != null) {
            messages.campaignMessage = String.format(
                "Recharge between %s RO and %s RO and get a %s valid for %s days.",
                bonus.value, bonus.maxValue, productName, bonus.noOfDays
            );
        } else {
            messages.campaignMessage = String.format(
                "Get %s valid for %s days!",
                productName, bonus.noOfDays
            );
        }

        return messages;
    }

    /**
     * Build final BONUS_CRITERIA JSON structure.
     */
    private String buildBonusCriteriaJson(BonusDetails bonus, Map<String, Object> product, MessageDetails messages) {
        try {
            ObjectNode root = objectMapper.createObjectNode();
            
            root.put("action_type", bonus.actionType);
            root.put("value", Integer.parseInt(bonus.value.isEmpty() ? "0" : bonus.value));
            root.put("aggregation", bonus.aggregation);
            root.put("noOfDays", Integer.parseInt(bonus.noOfDays.isEmpty() ? "0" : bonus.noOfDays));
            root.put("bonusThreshold", Integer.parseInt(bonus.bonusThreshold));
            
            if (bonus.maxValue != null && !bonus.maxValue.isEmpty()) {
                root.put("maxValue", Integer.parseInt(bonus.maxValue));
            }
            
            root.put("conditions", bonus.conditions);

            // Build benefit_details
            ObjectNode benefitDetails = objectMapper.createObjectNode();
            
            Object productId = product.get("product_id");
            if (productId != null) {
                benefitDetails.put("product_id", productId.toString());
            }
            
            benefitDetails.put("Bonus_type", bonus.bonusType);
            benefitDetails.put("success_message", messages.successMessage);
            benefitDetails.put("error_message", messages.errorMessage);

            root.set("benefit_details", benefitDetails);

            return objectMapper.writeValueAsString(root);

        } catch (Exception e) {
            logger.error("Error building bonus criteria JSON", e);
            return buildDefaultBonusCriteria();
        }
    }

    /**
     * Build default BONUS_CRITERIA when extraction fails.
     */
    private String buildDefaultBonusCriteria() {
        return "{\"conditions\":\" \"}";
    }

    /**
     * Build LLM prompt for bonus extraction.
     */
    private String buildExtractionPrompt(String input) {
        return """
            Extract bonus/offer details from the following text and return ONLY a JSON object.
            
            TEXT: %s
            
            Extract these fields:
            - action_type: The trigger action (Recharge, Purchase, TopUp, etc.)
            - value: The minimum amount/value
            - max_value: The maximum amount (if range specified)
            - aggregation: "Range" if between X and Y, "Sum" if total, "Count" if count-based
            - validity_days: Number of days the bonus is valid
            - bonus_threshold: Minimum number of times action needed (default 1)
            - extra_condition: Any additional conditions (or empty string)
            - product_description: Description of the product/bonus (e.g., "10 GB data pack")
            - data_size: Data size in GB if applicable
            - bonus_type: "telco_product", "cashback", "discount", etc.
            
            Return ONLY valid JSON, no explanations:
            {
              "action_type": "",
              "value": "",
              "max_value": "",
              "aggregation": "",
              "validity_days": "",
              "bonus_threshold": "1",
              "extra_condition": "",
              "product_description": "",
              "data_size": "",
              "bonus_type": "telco_product"
            }
            """.formatted(input);
    }

    /**
     * Extract JSON from LLM response.
     */
    private String extractJsonFromResponse(String response) {
        if (response == null) return "{}";
        
        // Find JSON object in response
        int start = response.indexOf('{');
        int end = response.lastIndexOf('}');
        
        if (start >= 0 && end > start) {
            return response.substring(start, end + 1);
        }
        
        return "{}";
    }

    /**
     * Get text value from JSON node with default.
     */
    private String getTextValue(JsonNode node, String field, String defaultValue) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return defaultValue;
        }
        return node.get(field).asText(defaultValue);
    }

    // Inner classes for data structures
    
    private static class BonusDetails {
        String actionType = "Recharge";
        String value = "0";
        String maxValue = "";
        String aggregation = "Sum";
        String noOfDays = "0";
        String bonusThreshold = "1";
        String conditions = " ";
        String productDescription = "";
        String dataSize = "";
        String bonusType = "telco_product";
    }

    private static class MessageDetails {
        String successMessage = "";
        String errorMessage = "";
        String campaignMessage = "";
    }
    
    /**
     * Convert BONUS_CRITERIA to DSL string format (for FinalRuleDslRenderer).
     * 
     * Output format:
     * BONUS_CRITERIA = {action_type=Recharge, value=6, aggregation=Range, noOfDays=15, bonusThreshold=1, maxValue=10, conditions=" ", benefit_details={product_id=1934114, Bonus_type="telco_product", success_message="...", error_message="..."}}
     */
    public static String toDslFormat(String jsonString) {
        if (jsonString == null || jsonString.isEmpty() || "{}".equals(jsonString)) {
            return "BONUS_CRITERIA = {conditions=\" \"}";
        }

        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode json = mapper.readTree(jsonString);
            
            StringBuilder dsl = new StringBuilder("BONUS_CRITERIA = {");
            
            // Main fields
            appendDslField(dsl, "action_type", json, false);
            appendDslField(dsl, "value", json, true);
            appendDslField(dsl, "aggregation", json, true);
            appendDslField(dsl, "noOfDays", json, true);
            appendDslField(dsl, "bonusThreshold", json, true);
            
            if (json.has("maxValue")) {
                appendDslField(dsl, "maxValue", json, true);
            }
            
            appendDslField(dsl, "conditions", json, true);
            
            // Benefit details
            if (json.has("benefit_details")) {
                dsl.append(", benefit_details={");
                JsonNode benefits = json.get("benefit_details");
                boolean firstBenefit = true;
                
                if (benefits.has("product_id") && !benefits.get("product_id").isNull()) {
                    dsl.append("product_id=").append(benefits.get("product_id").asText());
                    firstBenefit = false;
                }
                
                if (benefits.has("Bonus_type")) {
                    if (!firstBenefit) dsl.append(", ");
                    dsl.append("Bonus_type=\"").append(benefits.get("Bonus_type").asText()).append("\"");
                    firstBenefit = false;
                }
                
                if (benefits.has("success_message")) {
                    if (!firstBenefit) dsl.append(", ");
                    dsl.append("success_message=\"").append(escapeQuotes(benefits.get("success_message").asText())).append("\"");
                    firstBenefit = false;
                }
                
                if (benefits.has("error_message")) {
                    if (!firstBenefit) dsl.append(", ");
                    dsl.append("error_message=\"").append(escapeQuotes(benefits.get("error_message").asText())).append("\"");
                }
                
                dsl.append("}");
            }
            
            dsl.append("}");
            
            return dsl.toString();

        } catch (Exception e) {
            logger.error("Error converting bonus JSON to DSL", e);
            return "BONUS_CRITERIA = {conditions=\" \"}";
        }
    }

    private static void appendDslField(StringBuilder dsl, String fieldName, JsonNode json, boolean addComma) {
        if (json.has(fieldName)) {
            if (addComma) dsl.append(", ");
            JsonNode value = json.get(fieldName);
            
            if (value.isNumber()) {
                dsl.append(fieldName).append("=").append(value.asInt());
            } else {
                String text = value.asText();
                if (text.matches("\\d+")) {
                    dsl.append(fieldName).append("=").append(text);
                } else {
                    dsl.append(fieldName).append("=").append(text);
                }
            }
        }
    }

    private static String escapeQuotes(String input) {
        return input.replace("\"", "\\\"");
    }
}