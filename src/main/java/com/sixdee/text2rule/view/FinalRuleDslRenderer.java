package com.sixdee.text2rule.view;

import com.sixdee.text2rule.model.NodeData;
import com.sixdee.text2rule.model.RuleNode;
import com.sixdee.text2rule.model.RuleTree;
import com.sixdee.text2rule.workflow.DecompositionWorkflow;
import com.sixdee.text2rule.workflow.WorkflowState;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Renderer that generates DSL (Domain Specific Language) rule strings.
 * Produces output in the format:
 * {if (conditions) { then action(...) } else if (...) }schedule(...)
 * 
 * Implements TreeRenderer interface for integration with RendererFactory.
 */
public class FinalRuleDslRenderer implements TreeRenderer {
    private static final Logger logger = LoggerFactory.getLogger(FinalRuleDslRenderer.class);

    private final ObjectMapper objectMapper;

    // Day name to abbreviation mapping
    private static final Map<String, String> DAY_ABBREVIATIONS = new HashMap<>();
    static {
        DAY_ABBREVIATIONS.put("monday", "MON");
        DAY_ABBREVIATIONS.put("tuesday", "TUE");
        DAY_ABBREVIATIONS.put("wednesday", "WED");
        DAY_ABBREVIATIONS.put("thursday", "THU");
        DAY_ABBREVIATIONS.put("friday", "FRI");
        DAY_ABBREVIATIONS.put("saturday", "SAT");
        DAY_ABBREVIATIONS.put("sunday", "SUN");
    }

    public FinalRuleDslRenderer() {
        this.objectMapper = new ObjectMapper();
        logger.info("FinalRuleDslRenderer initialized");
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
    public void render(RuleTree<?> tree, WorkflowState state, DecompositionWorkflow workflow) {
        if (tree == null || tree.getRoot() == null) {
            logger.warn("Cannot render DSL rule: tree or root is null");
            return;
        }

        try {
            @SuppressWarnings("unchecked")
            RuleNode<NodeData> root = (RuleNode<NodeData>) tree.getRoot();
            
            // Debug: Print tree structure
            logger.debug("Starting DSL rendering from root type: {}", root.getData().getType());
            printTreeStructure(root, 0);
            
            String dslRule = generateDslRule(root);
            
            // Create final output JSON
            List<Map<String, String>> result = new ArrayList<>();
            Map<String, String> ruleMap = new HashMap<>();
            ruleMap.put("final_rule", dslRule);
            result.add(ruleMap);

            String jsonOutput = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result);

            logger.info("DSL Rule Generated:\n{}", jsonOutput);
            
            // Print the clean DSL rule
            System.out.println("\n================================================================================");
            System.out.println("FINAL DSL RULE OUTPUT");
            System.out.println("================================================================================");
            System.out.println(dslRule);  // Print raw DSL instead of JSON
            System.out.println("================================================================================\n");
            
            // Optionally also print the JSON version
            System.out.println("JSON FORMAT:");
            System.out.println(jsonOutput);
            System.out.println("================================================================================\n");

        } catch (Exception e) {
            logger.error("Failed to render DSL rule [error={}]", e.getMessage(), e);
        }
    }
    /**
     * Debug method to print tree structure
     */
    private void printTreeStructure(RuleNode<NodeData> node, int depth) {
        if (node == null) return;
        
        String indent = "  ".repeat(depth);
        String type = node.getData().getType();
        String input = node.getData().getInput();
        String inputPreview = (input != null && input.length() > 50) ? input.substring(0, 50) + "..." : input;
        
        logger.debug("{}[{}] {}", indent, type, inputPreview);
        
        if (node.getChildren() != null) {
            for (RuleNode<NodeData> child : node.getChildren()) {
                printTreeStructure(child, depth + 1);
            }
        }
    }

    /**
     * Generate the complete DSL rule string from the tree.
     */
    private String generateDslRule(RuleNode<NodeData> root) {
        StringBuilder dslBuilder = new StringBuilder();

        try {
            List<SegmentData> segments = extractSegments(root);
            ScheduleData scheduleData = extractScheduleData(root);

            dslBuilder.append("{");

            for (int i = 0; i < segments.size(); i++) {
                SegmentData segment = segments.get(i);

                if (segment.condition == null || segment.condition.isEmpty()) {
                    continue;
                }

                if (i == 0) {
                    dslBuilder.append("if (");
                } else {
                    dslBuilder.append(" else if (");
                }

                dslBuilder.append(segment.condition);
                dslBuilder.append(") { then ");
                dslBuilder.append(buildActionString(segment.actionData));
                dslBuilder.append(" }");
            }

            // ✅ CLOSE RULE BLOCK
            dslBuilder.append("}");
            dslBuilder.append("\n");

            // ✅ SCHEDULE OUTSIDE
            if (scheduleData != null) {
                dslBuilder.append(buildScheduleString(scheduleData));
            }

            return dslBuilder.toString();

        } catch (Exception e) {
            logger.error("Failed to generate DSL rule", e);
            return "{}";
        }
    }

    /**
     * Extract all segments (condition + action pairs) from the tree.
     * Based on actual tree structure:
     * Root → NormalStatements → Segment → Conditions → IF_Condition
     *                                   → Action → ActionDetails
     */
    private List<SegmentData> extractSegments(RuleNode<NodeData> root) {
        List<SegmentData> segments = new ArrayList<>();
        
        try {
            // Find all Segment nodes anywhere in the tree
            List<RuleNode<NodeData>> segmentNodes = findAllNodesByType(root, "Segment");
            
            logger.info("Found {} Segment nodes in tree", segmentNodes.size());
            
            for (RuleNode<NodeData> segmentNode : segmentNodes) {
                SegmentData segment = extractSingleSegment(segmentNode);
                if (segment != null && segment.condition != null && !segment.condition.isEmpty()) {
                    segments.add(segment);
                    logger.debug("Added segment with condition length: {}", segment.condition.length());
                }
            }
            
            logger.info("Extracted {} valid segments", segments.size());
        } catch (Exception e) {
            logger.error("Failed to extract segments [error={}]", e.getMessage(), e);
        }
        
        return segments;
    }

    /**
     * Find all nodes of a specific type in the tree.
     */
    private List<RuleNode<NodeData>> findAllNodesByType(RuleNode<NodeData> node, String type) {
        List<RuleNode<NodeData>> result = new ArrayList<>();
        findAllNodesByTypeRecursive(node, type, result);
        return result;
    }

    private void findAllNodesByTypeRecursive(RuleNode<NodeData> node, String type, List<RuleNode<NodeData>> result) {
        if (node == null) {
            return;
        }

        if (type.equalsIgnoreCase(node.getData().getType())) {
            result.add(node);
        }

        if (node.getChildren() != null) {
            for (RuleNode<NodeData> child : node.getChildren()) {
                findAllNodesByTypeRecursive(child, type, result);
            }
        }
    }

    /**
     * Extract a single segment (condition + action) from a Segment node.
     * Tree structure:
     * Segment → Conditions (contains text like "SMS revenue of exactly 15 RO...")
     *        → IF_Condition (nested inside Conditions, contains "if ((SMS_REVENUE_30D = 15)...)")
     *        → Action → ActionDetails
     */
    private SegmentData extractSingleSegment(RuleNode<NodeData> segmentNode) {
        SegmentData segment = new SegmentData();

        try {
            if (segmentNode == null || segmentNode.getChildren() == null) {
                logger.debug("Segment node is null or has no children");
                return null;
            }

            logger.debug("Extracting segment from node with {} children", segmentNode.getChildren().size());

            // Traverse all children to find IF_Condition and Action
            for (RuleNode<NodeData> child : segmentNode.getChildren()) {
                String childType = child.getData().getType();
                logger.debug("Processing child type: {}", childType);

                if ("Conditions".equalsIgnoreCase(childType)) {
                    // Look for IF_Condition inside Conditions node
                    RuleNode<NodeData> ifConditionNode = findNodeByTypeRecursive(child, "IF_Condition");
                    if (ifConditionNode != null) {
                        segment.condition = extractConditionFromIfNode(ifConditionNode);
                        logger.debug("Found IF_Condition inside Conditions node");
                    }
                }
                else if ("IF_Condition".equalsIgnoreCase(childType)) {
                    // Direct IF_Condition child
                    segment.condition = extractConditionFromIfNode(child);
                    logger.debug("Found direct IF_Condition child");
                }
                else if ("Action".equalsIgnoreCase(childType)) {
                    // Extract action data
                    segment.actionData = extractActionData(child);
                    logger.debug("Found Action node");
                }
            }

            // If still no condition found, search deeper in the tree
            if (segment.condition == null || segment.condition.isEmpty()) {
                RuleNode<NodeData> ifConditionNode = findNodeByTypeRecursive(segmentNode, "IF_Condition");
                if (ifConditionNode != null) {
                    segment.condition = extractConditionFromIfNode(ifConditionNode);
                    logger.debug("Found IF_Condition via deep search");
                }
            }

            // If still no action found, search deeper
            if (segment.actionData == null || segment.actionData.actionName == null) {
                RuleNode<NodeData> actionNode = findNodeByTypeRecursive(segmentNode, "Action");
                if (actionNode != null) {
                    segment.actionData = extractActionData(actionNode);
                    logger.debug("Found Action via deep search");
                }
            }

            logger.debug("Extracted segment [has_condition={}, has_action={}]", 
                    segment.condition != null && !segment.condition.isEmpty(), 
                    segment.actionData != null && segment.actionData.actionName != null);
            
            return segment;

        } catch (Exception e) {
            logger.error("Failed to extract single segment [error={}]", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Find node by type recursively (depth-first search).
     */
    private RuleNode<NodeData> findNodeByTypeRecursive(RuleNode<NodeData> node, String type) {
        if (node == null) {
            return null;
        }

        if (type.equalsIgnoreCase(node.getData().getType())) {
            return node;
        }

        if (node.getChildren() != null) {
            for (RuleNode<NodeData> child : node.getChildren()) {
                RuleNode<NodeData> found = findNodeByTypeRecursive(child, type);
                if (found != null) {
                    return found;
                }
            }
        }

        return null;
    }

    /**
     * Extract condition string from IF_Condition node.
     * Cleans and formats the condition for DSL output.
     */
    private String extractConditionFromIfNode(RuleNode<NodeData> ifConditionNode) {
        try {
            String input = ifConditionNode.getData().getInput();
            if (input == null || input.isEmpty()) {
                logger.debug("IF_Condition node has empty input");
                return null;
            }

            logger.debug("Raw IF_Condition input: {}", input);

            // Clean the condition string
            String condition = input.trim();
            
            // Remove "if" prefix
            if (condition.toLowerCase().startsWith("if ")) {
                condition = condition.substring(3).trim();
            } else if (condition.toLowerCase().startsWith("if(")) {
                condition = condition.substring(2).trim();
            }
            
            // Remove outer parentheses if they wrap the entire condition
            if (condition.startsWith("(") && condition.endsWith(")")) {
                // Check if these are matching outer parentheses
                int depth = 0;
                boolean isOuterPair = true;
                for (int i = 0; i < condition.length() - 1; i++) {
                    if (condition.charAt(i) == '(') depth++;
                    else if (condition.charAt(i) == ')') depth--;
                    if (depth == 0 && i < condition.length() - 1) {
                        isOuterPair = false;
                        break;
                    }
                }
                if (isOuterPair) {
                    condition = condition.substring(1, condition.length() - 1).trim();
                }
            }

            // Format values with double quotes
            condition = formatConditionValues(condition);

            logger.debug("Processed condition: {}", condition);
            return condition;

        } catch (Exception e) {
            logger.error("Failed to extract condition from IF node [error={}]", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Format condition values to use double quotes consistently.
     */
    private String formatConditionValues(String condition) {
        if (condition == null) {
            return null;
        }

        // Replace single quotes with double quotes
        String formatted = condition.replace("'", "\"");
        
        // Handle numeric values that should be quoted
        // Match patterns like "= 15" or ">= 200" and add quotes around the number
        // But don't double-quote already quoted values
        formatted = formatted.replaceAll("(=|>|<|>=|<=|!=)\\s*(\\d+)(?!\")", "$1 \"$2\"");
        
        return formatted;
    }

    /**
     * Extract action data from Action node.
     */
    private ActionData extractActionData(RuleNode<NodeData> actionNode) {
        ActionData actionData = new ActionData();

        try {
            if (actionNode == null) {
                return actionData;
            }

            // Find ActionDetails child
            RuleNode<NodeData> actionDetailsNode = findDirectChild(actionNode, "ActionDetails");
            if (actionDetailsNode != null && actionDetailsNode.getData().getInput() != null) {
                String details = actionDetailsNode.getData().getInput();
                logger.debug("ActionDetails input: {}", details);
                parseActionDetails(details, actionData);
            } else {
                logger.debug("No ActionDetails found, searching recursively");
                // Try to find ActionDetails recursively
                actionDetailsNode = findNodeByTypeRecursive(actionNode, "ActionDetails");
                if (actionDetailsNode != null && actionDetailsNode.getData().getInput() != null) {
                    parseActionDetails(actionDetailsNode.getData().getInput(), actionData);
                }
            }

            logger.debug("Extracted action data [name={}, channel={}, messageId={}]", 
                    actionData.actionName, actionData.channel, actionData.messageId);
            return actionData;

        } catch (Exception e) {
            logger.error("Failed to extract action data [error={}]", e.getMessage(), e);
            return actionData;
        }
    }

    /**
     * Parse action details string into ActionData object.
     * Expected format: "Action: Send Promotion, Channel: SMS, Message ID: 24"
     */
    private void parseActionDetails(String details, ActionData actionData) {
        if (details == null || details.isEmpty()) {
            return;
        }

        try {
            logger.debug("Parsing action details: {}", details);
            
            String[] parts = details.split(",");
            for (String part : parts) {
                part = part.trim();
                if (part.contains(":")) {
                    String[] keyValue = part.split(":", 2);
                    String key = keyValue[0].trim().toLowerCase().replace(" ", "_");
                    String value = keyValue[1].trim();

                    logger.debug("Action field: {} = {}", key, value);

                    switch (key) {
                        case "action":
                        case "actionname":
                            actionData.actionName = value;
                            break;
                        case "channel":
                            actionData.channel = value;
                            break;
                        case "message_id":
                            actionData.messageId = value;
                            break;
                        case "message":
                            actionData.message = value;
                            break;
                        case "product_id":
                            actionData.productId = value;
                            break;
                        case "action_key":
                        case "actionkey":
                            actionData.actionKey = value;
                            break;
                        case "action_type":
                        case "actiontype":
                            actionData.bonusActionType = value;
                            break;
                        case "value":
                            actionData.bonusValue = value;
                            break;
                        case "aggregation":
                            actionData.bonusAggregation = value;
                            break;
                        case "no_of_days":
                        case "noofdays":
                            actionData.bonusNoOfDays = value;
                            break;
                        case "bonus_threshold":
                        case "bonusthreshold":
                            actionData.bonusThreshold = value;
                            break;
                        case "max_value":
                        case "maxvalue":
                            actionData.bonusMaxValue = value;
                            break;
                        case "no_of_times":
                        case "nooftimes":
                            actionData.bonusNoOfTimes = value;
                            break;
                        case "conditions":
                            actionData.bonusConditions = value;
                            break;
                        case "product_type":
                        case "bonus_type":
                            actionData.benefitBonusType = value;
                            break;
                        case "success_message":
                            actionData.benefitSuccessMessage = value;
                            break;
                        case "error_message":
                            actionData.benefitErrorMessage = value;
                            break;
                        case "benefit_product_id":
                            actionData.benefitProductId = value;
                            break;
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Failed to parse action details [details={}, error={}]", details, e.getMessage(), e);
        }
    }

    /**
     * Build action string for DSL output.
     * Only includes fields that have values.
     */
    private String buildActionString(ActionData actionData) {
        if (actionData == null) {
            return "action()";
        }

        StringBuilder actionBuilder = new StringBuilder();
        actionBuilder.append("action(");

        List<String> actionParts = new ArrayList<>();

        // Add action name
        if (actionData.actionName != null && !actionData.actionName.isEmpty()) {
            actionParts.add("ActionName = \"" + actionData.actionName + "\"");
        }

        // Add channel
        if (actionData.channel != null && !actionData.channel.isEmpty()) {
            actionParts.add("CHANNEL = \"" + actionData.channel + "\"");
        }

        // Add message (use messageId if message is not available)
        if (actionData.message != null && !actionData.message.isEmpty()) {
            actionParts.add("MESSAGE = \"" + actionData.message + "\"");
        } else if (actionData.messageId != null && !actionData.messageId.isEmpty()) {
            actionParts.add("MESSAGE = \"" + actionData.messageId + "\"");
        }

        // Add product ID if available
        if (actionData.productId != null && !actionData.productId.isEmpty()) {
            actionParts.add("PRODUCT_ID = \"" + actionData.productId + "\"");
        }

        // Build BONUS_CRITERIA if any bonus field is present
        String bonusCriteria = buildBonusCriteria(actionData);
        if (bonusCriteria != null && !bonusCriteria.isEmpty()) {
            actionParts.add("BONUS_CRITERIA = " + bonusCriteria);
        }

        actionBuilder.append(String.join(", ", actionParts));
        actionBuilder.append(")");

        return actionBuilder.toString();
    }

    /**
     * Build BONUS_CRITERIA object string.
     * Only includes fields that have values.
     */
    private String buildBonusCriteria(ActionData actionData) {
        if (actionData == null) {
            return null;
        }

        // Check if any bonus field is present
        boolean hasBonusFields = (actionData.bonusActionType != null && !actionData.bonusActionType.isEmpty()) ||
                (actionData.bonusValue != null && !actionData.bonusValue.isEmpty()) ||
                (actionData.bonusAggregation != null && !actionData.bonusAggregation.isEmpty()) ||
                (actionData.bonusConditions != null && !actionData.bonusConditions.isEmpty()) ||
                (actionData.benefitProductId != null && !actionData.benefitProductId.isEmpty()) ||
                (actionData.benefitBonusType != null && !actionData.benefitBonusType.isEmpty());

        if (!hasBonusFields) {
            return null;
        }

        StringBuilder bonusBuilder = new StringBuilder();
        bonusBuilder.append("{");

        List<String> bonusParts = new ArrayList<>();

        if (actionData.bonusActionType != null && !actionData.bonusActionType.isEmpty()) {
            bonusParts.add("action_type=" + actionData.bonusActionType);
        }
        if (actionData.bonusValue != null && !actionData.bonusValue.isEmpty()) {
            bonusParts.add("value=" + actionData.bonusValue);
        }
        if (actionData.bonusAggregation != null && !actionData.bonusAggregation.isEmpty()) {
            bonusParts.add("aggregation=" + actionData.bonusAggregation);
        }
        if (actionData.bonusNoOfDays != null && !actionData.bonusNoOfDays.isEmpty()) {
            bonusParts.add("noOfDays=" + actionData.bonusNoOfDays);
        }
        if (actionData.bonusThreshold != null && !actionData.bonusThreshold.isEmpty()) {
            bonusParts.add("bonusThreshold=" + actionData.bonusThreshold);
        }
        if (actionData.bonusMaxValue != null && !actionData.bonusMaxValue.isEmpty()) {
            bonusParts.add("maxValue=" + actionData.bonusMaxValue);
        }
        if (actionData.bonusNoOfTimes != null && !actionData.bonusNoOfTimes.isEmpty()) {
            bonusParts.add("noOfTimes=" + actionData.bonusNoOfTimes);
        }
        if (actionData.bonusConditions != null && !actionData.bonusConditions.isEmpty()) {
            bonusParts.add("conditions=\"" + actionData.bonusConditions + "\"");
        }

        // Build benefit_details if any benefit field is present
        String benefitDetails = buildBenefitDetails(actionData);
        if (benefitDetails != null && !benefitDetails.isEmpty()) {
            bonusParts.add("benefit_details=" + benefitDetails);
        }

        bonusBuilder.append(String.join(", ", bonusParts));
        bonusBuilder.append("}");

        return bonusBuilder.toString();
    }

    /**
     * Build benefit_details object string.
     */
    private String buildBenefitDetails(ActionData actionData) {
        if (actionData == null) {
            return null;
        }

        boolean hasBenefitFields = (actionData.benefitProductId != null && !actionData.benefitProductId.isEmpty()) ||
                (actionData.benefitBonusType != null && !actionData.benefitBonusType.isEmpty()) ||
                (actionData.benefitSuccessMessage != null && !actionData.benefitSuccessMessage.isEmpty()) ||
                (actionData.benefitErrorMessage != null && !actionData.benefitErrorMessage.isEmpty());

        if (!hasBenefitFields) {
            return null;
        }

        StringBuilder benefitBuilder = new StringBuilder();
        benefitBuilder.append("{");

        List<String> benefitParts = new ArrayList<>();

        if (actionData.benefitProductId != null && !actionData.benefitProductId.isEmpty()) {
            benefitParts.add("product_id=\"" + actionData.benefitProductId + "\"");
        }
        if (actionData.benefitBonusType != null && !actionData.benefitBonusType.isEmpty()) {
            benefitParts.add("Bonus_type=\"" + actionData.benefitBonusType + "\"");
        }
        if (actionData.benefitSuccessMessage != null && !actionData.benefitSuccessMessage.isEmpty()) {
            benefitParts.add("success_message=\"" + actionData.benefitSuccessMessage + "\"");
        }
        if (actionData.benefitErrorMessage != null && !actionData.benefitErrorMessage.isEmpty()) {
            benefitParts.add("error_message=\"" + actionData.benefitErrorMessage + "\"");
        }

        benefitBuilder.append(String.join(", ", benefitParts));
        benefitBuilder.append("}");

        return benefitBuilder.toString();
    }

    /**
     * Extract schedule data from the tree.
     */
    private ScheduleData extractScheduleData(RuleNode<NodeData> root) {
        try {
            RuleNode<NodeData> scheduleNode = findNodeByTypeRecursive(root, "Schedule");
            if (scheduleNode == null) {
                logger.debug("No Schedule node found");
                return null;
            }

            RuleNode<NodeData> scheduleDetailsNode = findNodeByTypeRecursive(scheduleNode, "ScheduleDetails");
            if (scheduleDetailsNode == null || scheduleDetailsNode.getData().getInput() == null) {
                logger.debug("No ScheduleDetails node found");
                return null;
            }

            String details = scheduleDetailsNode.getData().getInput();
            logger.debug("Schedule details: {}", details);
            return parseScheduleDetails(details);

        } catch (Exception e) {
            logger.error("Failed to extract schedule data [error={}]", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Parse schedule details string into ScheduleData object.
     * Expected format: "Schedule Type: Weekly, Day(s): Monday, Tuesday, Start Time: {...}, Start Date: 2024-10-05, End Date: 2026-10-05"
     */
    private ScheduleData parseScheduleDetails(String details) {
        ScheduleData scheduleData = new ScheduleData();

        try {
            logger.debug("Parsing schedule: {}", details);

            // Extract Schedule Type
            scheduleData.scheduleType = extractValueBetween(details, "Schedule Type:", ",");
            if (scheduleData.scheduleType == null || scheduleData.scheduleType.isEmpty()) {
                scheduleData.scheduleType = extractValueBetween(details, "ScheduleType:", ",");
            }

            // Extract Days - handle "Day(s):" format
            String days = extractDaysFromSchedule(details);
            if (days != null && !days.isEmpty()) {
                scheduleData.day = days.trim();
                scheduleData.selectDays = convertDaysToAbbreviations(days);
            }

            // Extract Start Date
            scheduleData.startDate = extractValueBetween(details, "Start Date:", ",");
            if (scheduleData.startDate == null || scheduleData.startDate.isEmpty()) {
                scheduleData.startDate = extractValueBetween(details, "StartDate:", ",");
            }

            // Extract End Date
            scheduleData.endDate = extractValueAfter(details, "End Date:");
            if (scheduleData.endDate == null || scheduleData.endDate.isEmpty()) {
                scheduleData.endDate = extractValueAfter(details, "ExpiryDate:");
            }
            if (scheduleData.endDate == null || scheduleData.endDate.isEmpty()) {
                scheduleData.endDate = extractValueBetween(details, "End Date:", ",");
            }

            // Extract Start Time
            String startTimeStr = extractStartTime(details);
            if (startTimeStr != null && !startTimeStr.isEmpty()) {
                scheduleData.startTime = startTimeStr;
            }

            // Extract Interval if present
            scheduleData.interval = extractValueBetween(details, "Interval:", ",");

            // Determine Repeat based on schedule existence
            scheduleData.repeat = "Yes";

            // Extract Schedule Name (use type if not available)
            scheduleData.scheduleName = extractValueBetween(details, "Schedule Name:", ",");
            if (scheduleData.scheduleName == null || scheduleData.scheduleName.isEmpty()) {
                scheduleData.scheduleName = scheduleData.scheduleType;
            }

            logger.debug("Parsed schedule [type={}, days={}, selectDays={}, start={}, end={}]",
                    scheduleData.scheduleType, scheduleData.day, scheduleData.selectDays, 
                    scheduleData.startDate, scheduleData.endDate);
            return scheduleData;

        } catch (Exception e) {
            logger.error("Failed to parse schedule details [details={}, error={}]", details, e.getMessage(), e);
            return scheduleData;
        }
    }

    /**
     * Extract days from schedule string.
     * Handles format: "Day(s): Monday, Tuesday, Start Time: ..."
     */
    private String extractDaysFromSchedule(String details) {
        try {
            // Try "Day(s):" first
            int startIdx = details.indexOf("Day(s):");
            if (startIdx == -1) {
                startIdx = details.indexOf("Day:");
            }
            if (startIdx == -1) {
                return null;
            }

            startIdx = details.indexOf(":", startIdx) + 1;
            
            // Find the end - look for "Start Time" or next major section
            int endIdx = details.indexOf("Start Time", startIdx);
            if (endIdx == -1) {
                endIdx = details.indexOf(",", startIdx);
                // But we need to handle "Monday, Tuesday" - find the right comma
                // Look for a comma followed by a non-day word
                String remaining = details.substring(startIdx);
                endIdx = findDaysEndIndex(remaining);
                if (endIdx > 0) {
                    endIdx += startIdx;
                }
            }
            
            if (endIdx == -1) {
                endIdx = details.length();
            }

            String days = details.substring(startIdx, endIdx).trim();
            // Remove trailing comma if present
            if (days.endsWith(",")) {
                days = days.substring(0, days.length() - 1).trim();
            }
            
            return days;
        } catch (Exception e) {
            logger.debug("Failed to extract days: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Find where the days list ends in a string.
     */
    private int findDaysEndIndex(String text) {
        String[] dayNames = {"monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday"};
        String lower = text.toLowerCase();
        
        int lastDayEnd = 0;
        for (String day : dayNames) {
            int idx = lower.indexOf(day);
            if (idx >= 0) {
                int end = idx + day.length();
                if (end > lastDayEnd) {
                    lastDayEnd = end;
                }
            }
        }
        
        // Find next comma after last day
        int commaIdx = text.indexOf(",", lastDayEnd);
        return commaIdx > 0 ? commaIdx : text.length();
    }

    /**
     * Extract start time from schedule details.
     * Handles format: "Start Time: {MON=00:00, TUE=00:00}"
     */
    private String extractStartTime(String details) {
        try {
            int startIdx = details.indexOf("Start Time:");
            if (startIdx == -1) {
                return null;
            }

            startIdx = details.indexOf(":", startIdx) + 1;
            String remaining = details.substring(startIdx).trim();

            // Check if it's a map format
            if (remaining.startsWith("{")) {
                int endIdx = remaining.indexOf("}");
                if (endIdx > 0) {
                    String mapStr = remaining.substring(1, endIdx);
                    // Get first time value
                    String[] entries = mapStr.split(",");
                    if (entries.length > 0) {
                        String firstEntry = entries[0].trim();
                        String[] kv = firstEntry.split("=");
                        if (kv.length > 1) {
                            return kv[1].trim();
                        }
                    }
                }
            } else {
                // Simple time format
                int endIdx = remaining.indexOf(",");
                if (endIdx == -1) endIdx = remaining.length();
                return remaining.substring(0, endIdx).trim();
            }
        } catch (Exception e) {
            logger.debug("Failed to extract start time: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Extract value between a prefix and delimiter.
     */
    private String extractValueBetween(String text, String prefix, String delimiter) {
        if (text == null || prefix == null) {
            return null;
        }

        int startIdx = text.indexOf(prefix);
        if (startIdx == -1) {
            return null;
        }

        startIdx += prefix.length();
        
        // Skip leading whitespace
        while (startIdx < text.length() && text.charAt(startIdx) == ' ') {
            startIdx++;
        }

        // Handle special case for maps/objects
        if (startIdx < text.length() && text.charAt(startIdx) == '{') {
            int endIdx = text.indexOf("}", startIdx) + 1;
            if (endIdx > startIdx) {
                return text.substring(startIdx, endIdx).trim();
            }
        }
        
        int endIdx = text.indexOf(delimiter, startIdx);
        if (endIdx == -1 || endIdx <= startIdx) {
            endIdx = text.length();
        }

        return text.substring(startIdx, endIdx).trim();
    }

    /**
     * Extract value after a prefix (to end of string or next comma).
     */
    private String extractValueAfter(String text, String prefix) {
        if (text == null || prefix == null) {
            return null;
        }

        int startIdx = text.indexOf(prefix);
        if (startIdx == -1) {
            return null;
        }

        startIdx += prefix.length();
        String remaining = text.substring(startIdx).trim();
        
        // Find next comma or end
        int endIdx = remaining.indexOf(",");
        if (endIdx == -1) {
            endIdx = remaining.length();
        }

        return remaining.substring(0, endIdx).trim();
    }

    /**
     * Convert full day names to abbreviations.
     * "Monday, Tuesday" -> "MON,TUE"
     */
    private String convertDaysToAbbreviations(String days) {
        if (days == null || days.isEmpty()) {
            return "";
        }

        String[] dayArray = days.split(",");
        List<String> abbreviations = new ArrayList<>();

        for (String day : dayArray) {
            String trimmed = day.trim().toLowerCase();
            String abbr = DAY_ABBREVIATIONS.get(trimmed);
            if (abbr != null) {
                abbreviations.add(abbr);
            }
        }

        return String.join(",", abbreviations);
    }

    /**
     * Build schedule string for DSL output.
     */
    private String buildScheduleString(ScheduleData scheduleData) {
        if (scheduleData == null) {
            return "";
        }

        StringBuilder scheduleBuilder = new StringBuilder();
        scheduleBuilder.append("schedule(");

        List<String> scheduleParts = new ArrayList<>();

        // Add schedule name
        if (scheduleData.scheduleName != null && !scheduleData.scheduleName.isEmpty()) {
            scheduleParts.add("ScheduleName = \"" + scheduleData.scheduleName + "\"");
        }

        // Add schedule type
        if (scheduleData.scheduleType != null && !scheduleData.scheduleType.isEmpty()) {
            scheduleParts.add("ScheduleType = \"" + scheduleData.scheduleType + "\"");
        }

        // Add start date
        if (scheduleData.startDate != null && !scheduleData.startDate.isEmpty()) {
            scheduleParts.add("StartDate = \"" + scheduleData.startDate + "\"");
        }

        // Add expiry/end date
        if (scheduleData.endDate != null && !scheduleData.endDate.isEmpty()) {
            scheduleParts.add("ExpiryDate = \"" + scheduleData.endDate + "\"");
        }

        // Add repeat
        if (scheduleData.repeat != null && !scheduleData.repeat.isEmpty()) {
            scheduleParts.add("Repeat = \"" + scheduleData.repeat + "\"");
        }

        // Add day (full names)
        if (scheduleData.day != null && !scheduleData.day.isEmpty()) {
            scheduleParts.add("Day = \"" + scheduleData.day + "\"");
        }

        // Add select days (abbreviations)
        if (scheduleData.selectDays != null && !scheduleData.selectDays.isEmpty()) {
            scheduleParts.add("SelectDays = \"" + scheduleData.selectDays + "\"");
        }

        // Add interval if present
        if (scheduleData.interval != null && !scheduleData.interval.isEmpty()) {
            scheduleParts.add("Interval = \"" + scheduleData.interval + "\"");
        }

        // Add start time if present
        if (scheduleData.startTime != null && !scheduleData.startTime.isEmpty()) {
            scheduleParts.add("StartTime = \"" + scheduleData.startTime + "\"");
        }

        scheduleBuilder.append(String.join(", ", scheduleParts));
        scheduleBuilder.append(")");

        return scheduleBuilder.toString();
    }

    /**
     * Find direct child node with specified type.
     */
    private RuleNode<NodeData> findDirectChild(RuleNode<NodeData> parent, String type) {
        if (parent == null || parent.getChildren() == null) {
            return null;
        }

        for (RuleNode<NodeData> child : parent.getChildren()) {
            if (type.equalsIgnoreCase(child.getData().getType())) {
                return child;
            }
        }
        return null;
    }

    // ==================== Inner Data Classes ====================

    /**
     * Data class for segment (condition + action pair).
     */
    private static class SegmentData {
        String condition;
        ActionData actionData = new ActionData();
    }

    /**
     * Data class for action information.
     */
    private static class ActionData {
        String actionName;
        String actionKey;
        String channel;
        String messageId;
        String message;
        String productId;

        // Bonus criteria fields
        String bonusActionType;
        String bonusValue;
        String bonusAggregation;
        String bonusNoOfDays;
        String bonusThreshold;
        String bonusMaxValue;
        String bonusNoOfTimes;
        String bonusConditions;

        // Benefit details fields
        String benefitProductId;
        String benefitBonusType;
        String benefitSuccessMessage;
        String benefitErrorMessage;
    }

    /**
     * Data class for schedule information.
     */
    private static class ScheduleData {
        String scheduleName;
        String scheduleType;
        String startDate;
        String endDate;
        String repeat;
        String day;
        String selectDays;
        String interval;
        String startTime;
    }
}