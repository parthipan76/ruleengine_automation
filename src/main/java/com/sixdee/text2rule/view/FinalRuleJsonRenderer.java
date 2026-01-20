package com.sixdee.text2rule.view;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.sixdee.text2rule.model.NodeData;
import com.sixdee.text2rule.model.RuleNode;
import com.sixdee.text2rule.model.RuleTree;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Renders RuleTree into hierarchical rule JSON format with nested conditions
 * and actions.
 */
public class FinalRuleJsonRenderer {
    private static final Logger logger = LoggerFactory.getLogger(FinalRuleJsonRenderer.class);
    private final ObjectMapper objectMapper;
    private int idCounter = 0;

    public FinalRuleJsonRenderer() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    /**
     * Renders the tree into hierarchical rule JSON format.
     * Conditions are direct children of rules, with actions nested under
     * conditions.
     */
    public String render(RuleTree<NodeData> tree) {
        if (tree == null || tree.getRoot() == null) {
            return "[]";
        }

        List<Map<String, Object>> result = new ArrayList<>();
        Map<String, Object> detail = new HashMap<>();
        Map<String, Object> rules = new HashMap<>();

        idCounter = 0;
        rules.put("id", "0");
        rules.put("pid", "#");

        // Build conditions with their child actions directly
        List<Map<String, Object>> children = buildConditionsWithActions(tree.getRoot());
        rules.put("childrens", children);

        // Add schedule
        Map<String, Object> schedule = buildSchedule(tree.getRoot());
        if (schedule != null) {
            rules.put("schedule", schedule);
        }

        detail.put("rules", rules);
        result.add(Map.of("detail", detail));

        try {
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            logger.error("Failed to serialize rules to JSON", e);
            return "[]";
        }
    }

    /**
     * Builds conditions as direct children of rules, with actions nested under each
     * condition.
     */
    private List<Map<String, Object>> buildConditionsWithActions(RuleNode<NodeData> root) {
        List<Map<String, Object>> conditionsWithActions = new ArrayList<>();

        // Extract all conditions
        List<Map<String, Object>> conditions = extractConditions(root);

        // Extract all actions
        List<Map<String, Object>> actions = extractActions(root);

        // Associate actions with their parent conditions
        // For now, we'll nest all actions under each condition
        // This assumes each condition can have multiple actions
        int conditionIndex = 0;
        for (Map<String, Object> condition : conditions) {
            // Update condition ID to be direct child of root
            condition.put("id", "0_" + conditionIndex);
            condition.put("pid", "0");

            // Create children list for this condition
            List<Map<String, Object>> conditionChildren = new ArrayList<>();

            // Add actions as children of this condition
            int actionIndex = 0;
            for (Map<String, Object> action : actions) {
                // Clone the action and update its IDs
                Map<String, Object> actionCopy = new HashMap<>(action);
                actionCopy.put("id", "0_" + conditionIndex + "_" + actionIndex);
                actionCopy.put("pid", "0_" + conditionIndex);
                conditionChildren.add(actionCopy);
                actionIndex++;
            }

            // Add children to condition if any exist
            if (!conditionChildren.isEmpty()) {
                condition.put("childrens", conditionChildren);
            }

            conditionsWithActions.add(condition);
            conditionIndex++;
        }

        return conditionsWithActions;
    }

    private List<Map<String, Object>> extractConditions(RuleNode<NodeData> node) {
        List<Map<String, Object>> conditions = new ArrayList<>();
        extractConditionsRecursive(node, conditions, "0");
        return conditions;
    }

    private void extractConditionsRecursive(RuleNode<NodeData> node, List<Map<String, Object>> conditions,
            String parentId) {
        if (node == null) {
            return;
        }

        String nodeType = node.getData().getType();
        String nodeInput = node.getData().getInput();
        logger.info("Traversing node - Type: {}, Input: {}", nodeType,
                nodeInput != null && nodeInput.length() > 50 ? nodeInput.substring(0, 50) + "..." : nodeInput);

        // Look for IF_Condition nodes
        if ("IF_Condition".equalsIgnoreCase(nodeType)) {
            logger.info("*** FOUND IF_Condition NODE with input: {}", nodeInput);
            if (nodeInput != null) {
                // Parse the IF condition - may contain multiple conditions with AND
                List<Map<String, Object>> parsedConditions = parseComplexIfCondition(nodeInput, parentId,
                        conditions.size());
                logger.info("Parsed {} conditions from IF_Condition node", parsedConditions.size());
                conditions.addAll(parsedConditions);
            }
        }

        // Traverse children
        if (node.getChildren() != null) {
            logger.info("Node {} has {} children", nodeType, node.getChildren().size());
            for (RuleNode<NodeData> child : node.getChildren()) {
                extractConditionsRecursive(child, conditions, parentId);
            }
        } else {
            logger.info("Node {} has no children", nodeType);
        }
    }

    private List<Map<String, Object>> parseComplexIfCondition(String input, String parentId, int startIndex) {
        List<Map<String, Object>> conditions = new ArrayList<>();

        // Clean the condition
        String cleaned = input.trim();
        if (cleaned.startsWith("if (")) {
            cleaned = cleaned.substring(4);
        }
        if (cleaned.startsWith("if(")) {
            cleaned = cleaned.substring(3);
        }
        while (cleaned.endsWith(")")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1);
        }

        // Split by AND to get individual conditions
        String[] parts = cleaned.split(" AND ");

        for (int i = 0; i < parts.length; i++) {
            String part = parts[i].trim();
            // Remove parentheses
            part = part.replace("(", "").replace(")", "").trim();

            Map<String, Object> condition = parseSingleCondition(part, parentId, startIndex + i);
            if (condition != null) {
                conditions.add(condition);
            }
        }

        return conditions;
    }

    private Map<String, Object> parseSingleCondition(String conditionStr, String parentId, int index) {
        Map<String, Object> condition = new HashMap<>();
        condition.put("id", parentId + "_" + index);
        condition.put("pid", parentId);
        condition.put("type", "condition");

        // Parse condition: "PROFILE_NAME OPERATOR VALUE"
        String operator = null;
        String[] parts = null;

        if (conditionStr.contains(">=")) {
            operator = ">=";
            parts = conditionStr.split(">=", 2);
        } else if (conditionStr.contains("<=")) {
            operator = "<=";
            parts = conditionStr.split("<=", 2);
        } else if (conditionStr.contains("!=")) {
            operator = "!=";
            parts = conditionStr.split("!=", 2);
        } else if (conditionStr.contains(">")) {
            operator = ">";
            parts = conditionStr.split(">", 2);
        } else if (conditionStr.contains("<")) {
            operator = "<";
            parts = conditionStr.split("<", 2);
        } else if (conditionStr.contains("=")) {
            operator = "=";
            parts = conditionStr.split("=", 2);
        }

        if (parts != null && parts.length == 2) {
            String profileName = parts[0].trim();
            String value = parts[1].trim().replace("'", "").replace("\"", "");

            Map<String, Object> profile = new HashMap<>();
            profile.put("id", 1000 + index);
            profile.put("name", profileName);
            condition.put("profile", profile);
            condition.put("operator", operator);

            Map<String, Object> values = new HashMap<>();
            try {
                // Try to parse as number
                if (value.contains(".")) {
                    values.put("value", Double.parseDouble(value));
                } else {
                    values.put("value", Integer.parseInt(value));
                }
            } catch (NumberFormatException e) {
                values.put("value", value);
            }
            condition.put("values", values);
        }

        return condition;
    }

    private Map<String, Object> parseIfCondition(String input, String parentId, int index) {
        // Clean the condition
        String cleaned = input.trim();
        if (cleaned.startsWith("if (")) {
            cleaned = cleaned.substring(4);
        }
        if (cleaned.startsWith("if(")) {
            cleaned = cleaned.substring(3);
        }
        while (cleaned.endsWith(")")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1);
        }

        // Parse condition parts (simplified - you may need more sophisticated parsing)
        Map<String, Object> condition = new HashMap<>();
        condition.put("id", parentId + "_" + index);
        condition.put("pid", parentId);
        condition.put("type", "condition");

        // Try to extract profile, operator, and value
        // Example: "TOTAL_RECHARGE_30D > 200"
        if (cleaned.contains(">") || cleaned.contains("<") || cleaned.contains("=")) {
            String[] parts = cleaned.split("(>|<|=|>=|<=|!=)", 2);
            if (parts.length == 2) {
                String profileName = parts[0].trim();
                String value = parts[1].trim().replace("'", "").replace("\"", "");

                String operator = "=";
                if (cleaned.contains(">="))
                    operator = ">=";
                else if (cleaned.contains("<="))
                    operator = "<=";
                else if (cleaned.contains(">"))
                    operator = ">";
                else if (cleaned.contains("<"))
                    operator = "<";
                else if (cleaned.contains("!="))
                    operator = "!=";

                Map<String, Object> profile = new HashMap<>();
                profile.put("id", 1000 + index); // Placeholder ID
                profile.put("name", profileName);
                condition.put("profile", profile);
                condition.put("operator", operator);

                Map<String, Object> values = new HashMap<>();
                try {
                    values.put("value", Integer.parseInt(value));
                } catch (NumberFormatException e) {
                    values.put("value", value);
                }
                condition.put("values", values);
            }
        }

        return condition;
    }

    private List<Map<String, Object>> extractActions(RuleNode<NodeData> node) {
        List<Map<String, Object>> actions = new ArrayList<>();
        extractActionsRecursive(node, actions, "0");
        return actions;
    }

    private void extractActionsRecursive(RuleNode<NodeData> node, List<Map<String, Object>> actions, String parentId) {
        if (node == null) {
            return;
        }

        // Look for Action nodes with ActionDetails
        if ("Action".equalsIgnoreCase(node.getData().getType())) {
            RuleNode<NodeData> actionDetailsNode = findDirectChild(node, "ActionDetails");
            if (actionDetailsNode != null && actionDetailsNode.getData().getInput() != null) {
                Map<String, Object> action = buildActionObject(actionDetailsNode.getData().getInput(), parentId,
                        actions.size());
                if (action != null) {
                    actions.add(action);
                }
            }
        }

        // Traverse children
        if (node.getChildren() != null) {
            for (RuleNode<NodeData> child : node.getChildren()) {
                extractActionsRecursive(child, actions, parentId);
            }
        }
    }

    private Map<String, Object> buildActionObject(String details, String parentId, int index) {
        Map<String, String> fields = parseActionDetails(details);

        Map<String, Object> action = new HashMap<>();
        action.put("id", parentId + "_" + index);
        action.put("pid", parentId);
        action.put("type", "action");

        // Action info
        Map<String, Object> actionInfo = new HashMap<>();
        actionInfo.put("id", 5);
        actionInfo.put("name", fields.getOrDefault("Action", "Send Promotion"));
        action.put("action", actionInfo);

        // Action fields
        List<Map<String, String>> fieldList = new ArrayList<>();
        fieldList.add(Map.of("name", "ActionCall", "value", "EXTERNAL"));
        fieldList.add(Map.of("name", "ActionName", "value", "UPLOADER_MAIN"));
        fieldList.add(Map.of("name", "ActionURL", "value", "UPLOADER_CALL"));
        fieldList.add(Map.of("name", "ActionType", "value", "ASYNCH"));
        action.put("field", fieldList);

        // Request fields
        Map<String, Object> request = new HashMap<>();
        List<Map<String, String>> requestFields = new ArrayList<>();
        requestFields.add(Map.of("name", "ActionKey", "value", "campaign_action"));
        requestFields.add(Map.of("name", "CHANNEL", "value", fields.getOrDefault("Channel", "SMS")));
        requestFields.add(Map.of("name", "MESSAGE_ID", "value", fields.getOrDefault("Message_ID", "")));
        request.put("field", requestFields);
        action.put("request", request);

        return action;
    }

    private Map<String, Object> buildSchedule(RuleNode<NodeData> root) {
        RuleNode<NodeData> scheduleNode = findNodeByType(root, "Schedule");
        if (scheduleNode == null) {
            return null;
        }

        RuleNode<NodeData> scheduleDetailsNode = findNodeByType(scheduleNode, "ScheduleDetails");
        if (scheduleDetailsNode == null || scheduleDetailsNode.getData().getInput() == null) {
            return null;
        }

        String details = scheduleDetailsNode.getData().getInput();
        Map<String, Object> schedule = new HashMap<>();
        List<Map<String, String>> fields = new ArrayList<>();

        String scheduleType = extractValue(details, "Schedule Type:");
        String days = extractValue(details, "Day(s):");

        fields.add(Map.of("name", "ScheduleId", "value", ""));
        fields.add(Map.of("name", "ScheduleName", "value", scheduleType));
        fields.add(Map.of("name", "ScheduleType", "value", scheduleType));
        fields.add(Map.of("name", "StartDate", "value", "2024-11-01"));
        fields.add(Map.of("name", "ExpiryDate", "value", "2024-11-30"));
        fields.add(Map.of("name", "Repeat", "value", "Yes"));

        schedule.put("field", fields);
        return schedule;
    }

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

    private Map<String, String> parseActionDetails(String details) {
        Map<String, String> fields = new HashMap<>();

        String[] parts = details.split(",");
        for (String part : parts) {
            part = part.trim();
            if (part.contains(":")) {
                String[] keyValue = part.split(":", 2);
                String key = keyValue[0].trim().replace(" ", "_");
                String value = keyValue[1].trim();
                fields.put(key, value);
            }
        }

        return fields;
    }

    private String extractValue(String text, String prefix) {
        int startIdx = text.indexOf(prefix);
        if (startIdx == -1) {
            return "";
        }

        startIdx += prefix.length();
        int endIdx = text.indexOf(",", startIdx);
        if (endIdx == -1) {
            endIdx = text.length();
        }

        return text.substring(startIdx, endIdx).trim();
    }

    private RuleNode<NodeData> findNodeByType(RuleNode<NodeData> node, String type) {
        if (node == null) {
            return null;
        }

        if (type.equalsIgnoreCase(node.getData().getType())) {
            return node;
        }

        if (node.getChildren() != null) {
            for (RuleNode<NodeData> child : node.getChildren()) {
                RuleNode<NodeData> found = findNodeByType(child, type);
                if (found != null) {
                    return found;
                }
            }
        }

        return null;
    }
}
