package com.sixdee.text2rule.view;

import com.sixdee.text2rule.agent.BonusExtractionAgent;
import com.sixdee.text2rule.agent.PolicyExtractionAgent;
import com.sixdee.text2rule.model.NodeData;
import com.sixdee.text2rule.model.RuleNode;
import com.sixdee.text2rule.model.RuleTree;
import com.sixdee.text2rule.workflow.DecompositionWorkflow;
import com.sixdee.text2rule.workflow.WorkflowState;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * UPDATED FinalRuleDslRenderer - N8N Compatible Output
 * 
 * Key Changes:
 * 1. FIXED: Removed double "if" syntax issue
 * 2. ADDED: Full BONUS_CRITERIA structure with product_id, benefit_details
 * 3. ADDED: LeadPolicyId support in schedule
 * 4. ADDED: Proper MESSAGE field from MessageGenerationAgent
 * 5. UPDATED: Schedule format for Monthly with interval (Date="1 09:00-17:00, 15 09:00-17:00")
 * 
 * Output format matches N8N:
 * {if (condition) { then action(ActionName = "Send Promotion", CHANNEL = "SMS", MESSAGE = "...", BONUS_CRITERIA = {...}) }}
 * schedule(ScheduleType="Monthly", ..., Date="1 09:00-17:00, 15 09:00-17:00", LeadPolicyId="167")
 */
public class FinalRuleDslRenderer implements TreeRenderer {
    private static final Logger logger = LoggerFactory.getLogger(FinalRuleDslRenderer.class);

    private final ObjectMapper objectMapper;

    public FinalRuleDslRenderer() {
        this.objectMapper = new ObjectMapper();
        logger.info("FinalRuleDslRenderer initialized (N8N-compatible version)");
    }

    @Override
    public void render(RuleTree<?> tree) {
        render(tree, null, null);
    }

    @Override
    public void render(RuleTree<?> tree, WorkflowState state) {
        render(tree, state, null);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void render(RuleTree<?> tree, WorkflowState state, DecompositionWorkflow workflow) {
        if (tree == null || tree.getRoot() == null) {
            logger.warn("Cannot render DSL: tree or root is null");
            return;
        }

        try {
            String dslOutput = generateDsl((RuleTree<NodeData>) tree, state);

            System.out.println("\n" + "=".repeat(80));
            System.out.println("FINAL RULE DSL OUTPUT (N8N-Compatible)");
            System.out.println("=".repeat(80));
            System.out.println(dslOutput);
            System.out.println("=".repeat(80) + "\n");

            logger.info("DSL rendering completed [output_length={}]", dslOutput.length());
        } catch (Exception e) {
            logger.error("Error rendering DSL", e);
        }
    }

    /**
     * Generate DSL string from the rule tree.
     */
    public String generateDsl(RuleTree<NodeData> tree, WorkflowState state) {
        if (tree == null || tree.getRoot() == null) {
            return "";
        }

        StringBuilder dsl = new StringBuilder();
        RuleNode<NodeData> root = tree.getRoot();

        // Collect all rule branches
        List<RuleBranch> branches = new ArrayList<>();
        String scheduleDsl = "";
        String policyDsl = "";

        // Traverse tree
        collectBranches(root, branches, state);
        scheduleDsl = collectScheduleDsl(root, state);
        policyDsl = extractPolicyDsl(state);

        // Build the DSL output - FIXED: No double "if"
        if (!branches.isEmpty()) {
            dsl.append("{");

            for (int i = 0; i < branches.size(); i++) {
                RuleBranch branch = branches.get(i);

                if (i == 0) {
                    // FIXED: Single "if" only
                    dsl.append("if ");
                } else {
                    dsl.append(" else if ");
                }

                // Add condition - FIXED: Clean format (condition)
                dsl.append(formatCondition(branch.getCondition()));

                // Add action block
                dsl.append(" { then ");
                dsl.append(branch.getAction());
                dsl.append(" }");
            }

            dsl.append("}");
        }

        // Append schedule with policy
        if (scheduleDsl != null && !scheduleDsl.isEmpty()) {
            if (dsl.length() > 0) {
                dsl.append("\n");
            }
            
            // Append LeadPolicyId to schedule if present
            if (policyDsl != null && !policyDsl.isEmpty()) {
                scheduleDsl = appendPolicyToSchedule(scheduleDsl, policyDsl);
            }
            
            dsl.append(scheduleDsl);
        }

        return dsl.toString();
    }

    /**
     * Format condition - ensure single parentheses and clean syntax.
     * 
     * FIXED: Removes extra parentheses and ensures clean format:
     * Input: "((Fav_Recharge_Channel_M1 = \"mobile\"))"
     * Output: "(Fav_Recharge_Channel_M1 = \"mobile\")"
     */
    private String formatCondition(String condition) {
        if (condition == null || condition.isEmpty()) {
            return "()";
        }

        String cleaned = condition.trim();
        
        // Remove extra outer parentheses
        while (cleaned.startsWith("((") && cleaned.endsWith("))")) {
            cleaned = cleaned.substring(1, cleaned.length() - 1);
        }
        
        // Ensure single parentheses
        if (!cleaned.startsWith("(")) {
            cleaned = "(" + cleaned;
        }
        if (!cleaned.endsWith(")")) {
            cleaned = cleaned + ")";
        }
        
        // Remove "if" if it appears at start (from previous incorrect generation)
        cleaned = cleaned.replaceAll("^\\(\\s*if\\s+", "(");
        
        return cleaned;
    }

    /**
     * Recursively collect rule branches from the tree.
     */
    private void collectBranches(RuleNode<NodeData> node, List<RuleBranch> branches, WorkflowState state) {
        if (node == null) return;

        String nodeType = node.getData().getType();

        if ("Segment".equalsIgnoreCase(nodeType)) {
            String condition = "";
            String action = "";

            for (RuleNode<NodeData> child : node.getChildren()) {
                String childType = child.getData().getType();

                if ("segments".equalsIgnoreCase(childType)) {
                    for (RuleNode<NodeData> grandChild : child.getChildren()) {
                        if ("IF_Condition".equalsIgnoreCase(grandChild.getData().getType())) {
                            condition = grandChild.getData().getInput();
                            break;
                        }
                    }
                } else if ("Action".equalsIgnoreCase(childType)) {
                    action = buildActionDsl(child, state);
                }
            }

            if (!condition.isEmpty() && !action.isEmpty()) {
                branches.add(new RuleBranch(condition, action));
                logger.debug("Added branch [condition_length={}, action_length={}]", 
                        condition.length(), action.length());
            }
        }

        // Recurse
        if (node.getChildren() != null) {
            for (RuleNode<NodeData> child : node.getChildren()) {
                collectBranches(child, branches, state);
            }
        }
    }

    /**
     * Build action DSL with full BONUS_CRITERIA structure.
     * 
     * N8N Format:
     * action(ActionName = "Send Promotion", CHANNEL = "SMS", MESSAGE = "...", 
     *        BONUS_CRITERIA = {action_type=Recharge, value=6, aggregation=Range, noOfDays=15, 
     *        bonusThreshold=1, maxValue=10, conditions=" ", 
     *        benefit_details={product_id=1934114, Bonus_type="telco_product", success_message="...", error_message="..."}})
     */
    private String buildActionDsl(RuleNode<NodeData> actionNode, WorkflowState state) {
        StringBuilder action = new StringBuilder("action(");
        List<String> params = new ArrayList<>();

        String actionName = "Send Promotion";
        String channel = "SMS";
        String message = "";
        String bonusCriteria = "";

        // Find ActionDetails child
        for (RuleNode<NodeData> child : actionNode.getChildren()) {
            if ("ActionDetails".equalsIgnoreCase(child.getData().getType())) {
                String details = child.getData().getInput();
                
                if (details != null && !details.isEmpty()) {
                    // Parse action details
                    Map<String, String> parsed = parseActionDetails(details);
                    
                    if (parsed.containsKey("ACTION")) {
                        actionName = mapActionName(parsed.get("ACTION"));
                    }
                    if (parsed.containsKey("CHANNEL")) {
                        channel = parsed.get("CHANNEL").toUpperCase();
                    }
                    if (parsed.containsKey("MESSAGE")) {
                        message = parsed.get("MESSAGE");
                    }
                    
                    // Build full BONUS_CRITERIA from parsed details and state
                    bonusCriteria = buildFullBonusCriteria(parsed, state, details);
                }
                break;
            }
        }

        // Build params
        params.add("ActionName = \"" + actionName + "\"");
        params.add("CHANNEL = \"" + channel + "\"");
        
        if (!message.isEmpty()) {
            params.add("MESSAGE = \"" + escapeQuotes(message) + "\"");
        }
        
        if (!bonusCriteria.isEmpty()) {
            params.add(bonusCriteria);
        }

        action.append(String.join(", ", params));
        action.append(")");

        return action.toString();
    }

    /**
     * Parse action details string into key-value map.
     */
    private Map<String, String> parseActionDetails(String details) {
        Map<String, String> map = new java.util.HashMap<>();
        
        String[] parts = details.split(",");
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.contains(":")) {
                String[] keyValue = trimmed.split(":", 2);
                String key = keyValue[0].trim().replace(" ", "_").toUpperCase();
                String value = keyValue[1].trim();
                map.put(key, value);
            }
        }
        
        return map;
    }

    /**
     * Build full BONUS_CRITERIA structure matching N8N output.
     */
    private String buildFullBonusCriteria(Map<String, String> parsed, WorkflowState state, String originalText) {
        StringBuilder bonus = new StringBuilder("BONUS_CRITERIA = {");
        List<String> parts = new ArrayList<>();

        // Extract values from parsed data or state
        String actionType = "Recharge";
        String value = "0";
        String maxValue = null;
        String aggregation = "Sum";
        String noOfDays = "0";
        String bonusThreshold = "1";
        String conditions = " ";
        String productId = null;
        String bonusType = "telco_product";
        String successMessage = "";
        String errorMessage = "";

        // Try to get from state (if BonusExtractionAgent was run)
        if (state != null) {
            Object bonusData = state.get("bonus_extraction_result");
            if (bonusData != null) {
                try {
                    JsonNode json = objectMapper.readTree(bonusData.toString());
                    actionType = getJsonText(json, "action_type", actionType);
                    value = getJsonText(json, "value", value);
                    maxValue = getJsonText(json, "maxValue", maxValue);
                    aggregation = getJsonText(json, "aggregation", aggregation);
                    noOfDays = getJsonText(json, "noOfDays", noOfDays);
                    bonusThreshold = getJsonText(json, "bonusThreshold", bonusThreshold);
                    conditions = getJsonText(json, "conditions", conditions);
                    
                    if (json.has("benefit_details")) {
                        JsonNode benefits = json.get("benefit_details");
                        productId = getJsonText(benefits, "product_id", null);
                        bonusType = getJsonText(benefits, "Bonus_type", bonusType);
                        successMessage = getJsonText(benefits, "success_message", "");
                        errorMessage = getJsonText(benefits, "error_message", "");
                    }
                } catch (Exception e) {
                    logger.warn("Error parsing bonus state", e);
                }
            }
        }

        // Fallback: Extract from original text using regex
        if ("0".equals(value) || value.isEmpty()) {
            ExtractedBonusValues extracted = extractBonusValuesFromText(originalText);
            if (extracted.value != null) value = extracted.value;
            if (extracted.maxValue != null) maxValue = extracted.maxValue;
            if (extracted.aggregation != null) aggregation = extracted.aggregation;
            if (extracted.noOfDays != null) noOfDays = extracted.noOfDays;
            if (extracted.dataSize != null) {
                // Generate messages
                String productDesc = extracted.dataSize + "GB data for " + noOfDays + " days";
                successMessage = "Congratulations! " + productDesc + " has been added to your account. Enjoy fast surfing! 🎉";
                errorMessage = "Sorry, we couldn't add the " + productDesc + " to your account right now. Please try again later.";
            }
        }

        // Build BONUS_CRITERIA structure
        parts.add("action_type=" + actionType);
        parts.add("value=" + value);
        parts.add("aggregation=" + aggregation);
        parts.add("noOfDays=" + noOfDays);
        parts.add("bonusThreshold=" + bonusThreshold);
        
        if (maxValue != null && !maxValue.isEmpty()) {
            parts.add("maxValue=" + maxValue);
        }
        
        parts.add("conditions=\"" + conditions + "\"");

        // Build benefit_details
        StringBuilder benefitDetails = new StringBuilder("benefit_details={");
        List<String> benefitParts = new ArrayList<>();
        
        if (productId != null && !productId.isEmpty()) {
            benefitParts.add("product_id=" + productId);
        }
        benefitParts.add("Bonus_type=\"" + bonusType + "\"");
        
        if (!successMessage.isEmpty()) {
            benefitParts.add("success_message=\"" + escapeQuotes(successMessage) + "\"");
        }
        if (!errorMessage.isEmpty()) {
            benefitParts.add("error_message=\"" + escapeQuotes(errorMessage) + "\"");
        }
        
        benefitDetails.append(String.join(", ", benefitParts));
        benefitDetails.append("}");
        
        parts.add(benefitDetails.toString());

        bonus.append(String.join(", ", parts));
        bonus.append("}");

        return bonus.toString();
    }

    /**
     * Extract bonus values from text using regex.
     */
    private ExtractedBonusValues extractBonusValuesFromText(String text) {
        ExtractedBonusValues values = new ExtractedBonusValues();
        
        // Range pattern: "between 6 and 10"
        Pattern rangePattern = Pattern.compile(
            "(?:between|from)\\s+(\\d+(?:\\.\\d+)?)\\s*(?:RO|OMR|\\$|₹)?\\s*(?:and|to)\\s*(\\d+(?:\\.\\d+)?)\\s*(?:RO|OMR|\\$|₹)?",
            Pattern.CASE_INSENSITIVE
        );
        Matcher rangeMatcher = rangePattern.matcher(text);
        if (rangeMatcher.find()) {
            values.value = rangeMatcher.group(1);
            values.maxValue = rangeMatcher.group(2);
            values.aggregation = "Range";
        }

        // Data size: "10 GB"
        Pattern dataPattern = Pattern.compile("(\\d+)\\s*(?:GB|G)", Pattern.CASE_INSENSITIVE);
        Matcher dataMatcher = dataPattern.matcher(text);
        if (dataMatcher.find()) {
            values.dataSize = dataMatcher.group(1);
        }

        // Validity: "valid for 15 days"
        Pattern validityPattern = Pattern.compile(
            "(?:valid(?:ity)?\\s+(?:for\\s+)?|for\\s+)(\\d+)\\s*days?",
            Pattern.CASE_INSENSITIVE
        );
        Matcher validityMatcher = validityPattern.matcher(text);
        if (validityMatcher.find()) {
            values.noOfDays = validityMatcher.group(1);
        }

        return values;
    }

    /**
     * Map action name to standard format.
     */
    private String mapActionName(String actionName) {
        if (actionName == null) return "Send Promotion";
        
        String lower = actionName.toLowerCase();
        if (lower.contains("promotion") || lower.contains("promo")) {
            return "Send Promotion";
        } else if (lower.contains("reminder")) {
            return "Send Reminder";
        } else if (lower.contains("upgrade")) {
            return "Upgrade Offer";
        } else if (lower.contains("notification")) {
            return "Send Notification";
        }
        return actionName;
    }

    /**
     * Collect schedule DSL from the tree.
     */
    private String collectScheduleDsl(RuleNode<NodeData> node, WorkflowState state) {
        if (node == null) return "";

        String nodeType = node.getData().getType();

        if ("Schedule".equalsIgnoreCase(nodeType)) {
            for (RuleNode<NodeData> child : node.getChildren()) {
                if ("ScheduleDetails".equalsIgnoreCase(child.getData().getType())) {
                    String details = child.getData().getInput();
                    return buildScheduleDslFromDetails(details, state);
                }
            }
            return buildScheduleDslFromText(node.getData().getInput(), state);
        }

        // Recurse
        if (node.getChildren() != null) {
            for (RuleNode<NodeData> child : node.getChildren()) {
                String result = collectScheduleDsl(child, state);
                if (!result.isEmpty()) {
                    return result;
                }
            }
        }

        return "";
    }

    /**
     * Build schedule DSL with N8N-compatible format.
     * 
     * For Monthly with interval:
     * schedule(ScheduleType="Monthly", Repeat="true", Hours="2", Date="1 09:00-17:00, 15 09:00-17:00")
     */
    private String buildScheduleDslFromDetails(String details, WorkflowState state) {
        if (details == null || details.isEmpty()) {
            return "";
        }

        StringBuilder dsl = new StringBuilder("schedule(");
        List<String> params = new ArrayList<>();

        // Parse schedule type
        String scheduleType = extractField(details, "Schedule Type");
        String repeat = extractField(details, "Repeat");
        String startDate = extractField(details, "Start Date");
        String endDate = extractField(details, "End Date");
        String interval = extractField(details, "Interval");
        String hours = extractField(details, "Hours");
        String minutes = extractField(details, "Minutes");
        String day = extractField(details, "Day");
        String startTime = extractTimeField(details, "Start Time");
        String endTime = extractTimeField(details, "End Time");

        // Basic params
        params.add("ScheduleName=\"\"");
        params.add("ScheduleType=\"" + (scheduleType != null ? scheduleType : "Daily") + "\"");
        
        if (startDate != null && !startDate.isEmpty()) {
            params.add("StartDate=\"" + startDate + "\"");
        }
        if (endDate != null && !endDate.isEmpty()) {
            params.add("ExpiryDate=\"" + endDate + "\"");
        }
        
        params.add("Repeat=\"" + (repeat != null && repeat.toLowerCase().contains("yes") ? "true" : "false") + "\"");
        
        if (hours != null && !hours.isEmpty()) {
            params.add("Hours=\"" + hours + "\"");
        }
        if (minutes != null && !minutes.isEmpty()) {
            params.add("Minutes=\"" + minutes + "\"");
        }

        // Handle Monthly with interval format: Date="1 09:00-17:00, 15 09:00-17:00"
        if (scheduleType != null && scheduleType.contains("Monthly") && day != null) {
            String dateField = buildMonthlyDateField(day, startTime, endTime);
            if (!dateField.isEmpty()) {
                params.add("Date=\"" + dateField + "\"");
            }
        }

        dsl.append(String.join(", ", params));
        dsl.append(")");

        return dsl.toString();
    }

    /**
     * Build Monthly Date field: "1 09:00-17:00, 15 09:00-17:00"
     */
    private String buildMonthlyDateField(String day, String startTime, String endTime) {
        if (day == null || day.isEmpty()) return "";

        // Parse day numbers: "1 , 15" or "1,15"
        String[] days = day.split("[,\\s]+");
        List<String> dateEntries = new ArrayList<>();

        for (String d : days) {
            String trimmed = d.trim();
            if (!trimmed.isEmpty() && trimmed.matches("\\d+")) {
                StringBuilder entry = new StringBuilder(trimmed);
                
                if (startTime != null && !startTime.isEmpty()) {
                    entry.append(" ").append(startTime);
                    
                    if (endTime != null && !endTime.isEmpty()) {
                        entry.append("-").append(endTime);
                    }
                }
                
                dateEntries.add(entry.toString());
            }
        }

        return String.join(", ", dateEntries);
    }

    /**
     * Extract policy DSL from state.
     */
    private String extractPolicyDsl(WorkflowState state) {
        if (state == null) return "";

        Object policyData = state.get("policy_extraction_result");
        if (policyData != null) {
            return PolicyExtractionAgent.toDslFormat(policyData.toString());
        }

        return "";
    }

    /**
     * Append policy to schedule DSL.
     * 
     * schedule(...) -> schedule(..., LeadPolicyId="167")
     */
    private String appendPolicyToSchedule(String scheduleDsl, String policyDsl) {
        if (scheduleDsl.endsWith(")")) {
            return scheduleDsl.substring(0, scheduleDsl.length() - 1) + ", " + policyDsl + ")";
        }
        return scheduleDsl;
    }

    /**
     * Build schedule DSL from raw text.
     */
    private String buildScheduleDslFromText(String text, WorkflowState state) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        // Use ScheduleParser for parsing
        return "schedule(ScheduleName=\"\", ScheduleType=\"Daily\", Repeat=\"true\")";
    }

    /**
     * Extract field from details string.
     */
    private String extractField(String details, String fieldName) {
        Pattern pattern = Pattern.compile(fieldName + "\\s*[:=]\\s*([^,}]+)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(details);
        if (matcher.find()) {
            return matcher.group(1).trim().replaceAll("^[\"']|[\"']$", "");
        }
        return null;
    }

    /**
     * Extract time field from details (handles map format).
     */
    private String extractTimeField(String details, String fieldName) {
        // Try map format: Start Time: {1=09:00, 15=09:00}
        Pattern mapPattern = Pattern.compile(fieldName + "\\s*[:=]\\s*\\{([^}]+)\\}", Pattern.CASE_INSENSITIVE);
        Matcher mapMatcher = mapPattern.matcher(details);
        if (mapMatcher.find()) {
            String mapContent = mapMatcher.group(1);
            // Extract first time value
            Pattern timePattern = Pattern.compile("=\\s*(\\d{2}:\\d{2})");
            Matcher timeMatcher = timePattern.matcher(mapContent);
            if (timeMatcher.find()) {
                return timeMatcher.group(1);
            }
        }
        
        // Fallback: simple format
        return extractField(details, fieldName);
    }

    /**
     * Get text from JSON node with default.
     */
    private String getJsonText(JsonNode node, String field, String defaultValue) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return defaultValue;
        }
        return node.get(field).asText(defaultValue);
    }

    /**
     * Escape quotes in string.
     */
    private String escapeQuotes(String input) {
        if (input == null) return "";
        return input.replace("\"", "\\\"");
    }

    // Inner classes

    private static class RuleBranch {
        private final String condition;
        private final String action;

        public RuleBranch(String condition, String action) {
            this.condition = condition;
            this.action = action;
        }

        public String getCondition() {
            return condition;
        }

        public String getAction() {
            return action;
        }
    }

    private static class ExtractedBonusValues {
        String value;
        String maxValue;
        String aggregation;
        String noOfDays;
        String dataSize;
    }
}