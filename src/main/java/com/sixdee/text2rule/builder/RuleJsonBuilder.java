package com.sixdee.text2rule.builder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builder for constructing rule JSON following Builder Pattern.
 * Provides fluent API for building complex JSON structures.
 * Follows Single Responsibility Principle - only handles JSON construction.
 */
public class RuleJsonBuilder {
    private static final Logger logger = LoggerFactory.getLogger(RuleJsonBuilder.class);

    private final ObjectMapper objectMapper;
    private List<Map<String, Object>> conditions;
    private List<Map<String, Object>> actions;
    private Map<String, Object> schedule;

    public RuleJsonBuilder() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        this.conditions = new ArrayList<>();
        this.actions = new ArrayList<>();
    }

    /**
     * Set conditions for the rule.
     */
    public RuleJsonBuilder withConditions(List<Map<String, Object>> conditions) {
        try {
            this.conditions = conditions != null ? conditions : new ArrayList<>();
            logger.debug("Set conditions [count={}]", this.conditions.size());
            return this;
        } catch (Exception e) {
            logger.error("Failed to set conditions [error={}]", e.getMessage(), e);
            this.conditions = new ArrayList<>();
            return this;
        }
    }

    /**
     * Set actions for the rule.
     */
    public RuleJsonBuilder withActions(List<Map<String, Object>> actions) {
        try {
            this.actions = actions != null ? actions : new ArrayList<>();
            logger.debug("Set actions [count={}]", this.actions.size());
            return this;
        } catch (Exception e) {
            logger.error("Failed to set actions [error={}]", e.getMessage(), e);
            this.actions = new ArrayList<>();
            return this;
        }
    }

    /**
     * Set schedule for the rule.
     */
    public RuleJsonBuilder withSchedule(Map<String, Object> schedule) {
        try {
            this.schedule = schedule;
            logger.debug("Set schedule [has_data={}]", schedule != null);
            return this;
        } catch (Exception e) {
            logger.error("Failed to set schedule [error={}]", e.getMessage(), e);
            this.schedule = null;
            return this;
        }
    }

    /**
     * Build JSON from condition groups directly (preserves grouping).
     */
    public String buildFromGroups(List<com.sixdee.text2rule.util.ConditionActionGrouper.ConditionGroup> groups,
            com.sixdee.text2rule.parser.ConditionParser conditionParser,
            com.sixdee.text2rule.parser.ActionParser actionParser) {
        try {
            List<Map<String, Object>> result = new ArrayList<>();
            Map<String, Object> detail = new HashMap<>();
            Map<String, Object> rules = new HashMap<>();

            rules.put("id", "0");
            rules.put("pid", "#");

            // Build from groups
            List<Map<String, Object>> children = buildFromConditionGroups(groups, conditionParser, actionParser);
            rules.put("childrens", children);

            // Add schedule if present
            if (schedule != null) {
                rules.put("schedule", schedule);
            }

            detail.put("rules", rules);
            result.add(Map.of("detail", detail));

            String json = objectMapper.writeValueAsString(result);
            logger.info("Built rule JSON from groups [output_size={}, groups={}]",
                    json.length(), groups.size());
            return json;
        } catch (Exception e) {
            logger.error("Failed to build rule JSON from groups [error={}]", e.getMessage(), e);
            return "[]";
        }
    }

    /**
     * Build the final JSON string.
     */
    public String build() {
        try {
            List<Map<String, Object>> result = new ArrayList<>();
            Map<String, Object> detail = new HashMap<>();
            Map<String, Object> rules = new HashMap<>();

            rules.put("id", "0");
            rules.put("pid", "#");

            // Build conditions with their child actions
            List<Map<String, Object>> children = buildConditionsWithActions();
            rules.put("childrens", children);

            // Add schedule if present
            if (schedule != null) {
                rules.put("schedule", schedule);
            }

            detail.put("rules", rules);
            result.add(Map.of("detail", detail));

            String json = objectMapper.writeValueAsString(result);
            logger.info("Built rule JSON [output_size={}, conditions={}, actions={}]",
                    json.length(), conditions.size(), actions.size());
            return json;
        } catch (Exception e) {
            logger.error("Failed to build rule JSON [error={}]", e.getMessage(), e);
            return "[]";
        }
    }

    /**
     * Build conditions with actions as siblings in proper hierarchy.
     * Structure: Outer "Any" group -> Inner "All" groups -> Conditions + Action as
     * siblings
     */
    private List<Map<String, Object>> buildConditionsWithActions() {
        try {
            List<Map<String, Object>> result = new ArrayList<>();

            if (conditions.isEmpty()) {
                logger.debug("No conditions to build");
                return result;
            }

            // Create outer "Any" conditions group (id="0_0", pid="0")
            Map<String, Object> outerGroup = new HashMap<>();
            outerGroup.put("id", "0_0");
            outerGroup.put("pid", "0");
            outerGroup.put("type", "conditions");
            outerGroup.put("option", "Any");

            List<Map<String, Object>> innerGroups = new ArrayList<>();

            // Calculate how many conditions per action
            int numActions = actions.size();
            int numConditions = conditions.size();

            if (numActions == 0) {
                // No actions - create single inner group with all conditions
                Map<String, Object> innerGroup = createInnerGroup(conditions, null, 0);
                innerGroups.add(innerGroup);
            } else {
                // Calculate conditions per group
                int conditionsWithoutAction = Math.max(0, numConditions - numActions);

                int groupIndex = 0;

                // First group: conditions without action (if any)
                if (conditionsWithoutAction > 0) {
                    List<Map<String, Object>> firstGroupConditions = conditions.subList(0, conditionsWithoutAction);
                    Map<String, Object> innerGroup = createInnerGroup(firstGroupConditions, null, groupIndex);
                    innerGroups.add(innerGroup);
                    groupIndex++;
                }

                // Remaining groups: each with conditions + action
                for (int i = 0; i < numActions; i++) {
                    int conditionIndex = conditionsWithoutAction + i;
                    if (conditionIndex < numConditions) {
                        List<Map<String, Object>> singleCondition = new ArrayList<>();
                        singleCondition.add(conditions.get(conditionIndex));
                        Map<String, Object> innerGroup = createInnerGroup(singleCondition, actions.get(i), groupIndex);
                        innerGroups.add(innerGroup);
                        groupIndex++;
                    }
                }
            }

            outerGroup.put("childrens", innerGroups);
            result.add(outerGroup);

            logger.info(
                    "Built proper JSON structure [outer_groups=1, inner_groups={}, total_conditions={}, actions={}]",
                    innerGroups.size(), conditions.size(), actions.size());
            return result;
        } catch (Exception e) {
            logger.error("Failed to build conditions with actions [error={}]", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    /**
     * Create an inner "All" conditions group with conditions and optional action as
     * siblings.
     */
    private Map<String, Object> createInnerGroup(List<Map<String, Object>> groupConditions,
            Map<String, Object> action, int groupIndex) {
        Map<String, Object> innerGroup = new HashMap<>();
        String groupId = "0_0_" + groupIndex;
        innerGroup.put("id", groupId);
        innerGroup.put("pid", "0_0");
        innerGroup.put("type", "conditions");
        innerGroup.put("option", "All");

        List<Map<String, Object>> children = new ArrayList<>();

        // Add conditions as siblings
        for (int i = 0; i < groupConditions.size(); i++) {
            Map<String, Object> condition = new HashMap<>(groupConditions.get(i));
            String conditionId = groupId + "_" + i;
            condition.put("id", conditionId);
            condition.put("pid", groupId);
            children.add(condition);
        }

        // Add action as sibling (not child of last condition)
        if (action != null) {
            Map<String, Object> actionCopy = new HashMap<>(action);
            String actionId = groupId + "_" + groupConditions.size();
            actionCopy.put("id", actionId);
            actionCopy.put("pid", groupId);
            children.add(actionCopy);
            logger.debug("Added action as sibling [group={}, action_id={}]", groupIndex, actionId);
        }

        innerGroup.put("childrens", children);
        return innerGroup;
    }

    /**
     * Build JSON structure from ConditionGroup list (preserves grouping).
     */
    private List<Map<String, Object>> buildFromConditionGroups(
            List<com.sixdee.text2rule.util.ConditionActionGrouper.ConditionGroup> groups,
            com.sixdee.text2rule.parser.ConditionParser conditionParser,
            com.sixdee.text2rule.parser.ActionParser actionParser) {
        try {
            List<Map<String, Object>> result = new ArrayList<>();

            if (groups.isEmpty()) {
                logger.debug("No groups to build");
                return result;
            }

            // Create outer "Any" conditions group (id="0_0", pid="0")
            Map<String, Object> outerGroup = new HashMap<>();
            outerGroup.put("id", "0_0");
            outerGroup.put("pid", "0");
            outerGroup.put("type", "conditions");
            outerGroup.put("option", "Any");

            List<Map<String, Object>> innerGroups = new ArrayList<>();

            // Build each group
            for (int groupIndex = 0; groupIndex < groups.size(); groupIndex++) {
                com.sixdee.text2rule.util.ConditionActionGrouper.ConditionGroup group = groups.get(groupIndex);

                // Create inner "All" group
                Map<String, Object> innerGroup = new HashMap<>();
                String groupId = "0_0_" + groupIndex;
                innerGroup.put("id", groupId);
                innerGroup.put("pid", "0_0");
                innerGroup.put("type", "conditions");
                innerGroup.put("option", "All");

                List<Map<String, Object>> children = new ArrayList<>();

                // Add all conditions from this group
                int childIndex = 0;
                for (com.sixdee.text2rule.model.RuleNode<com.sixdee.text2rule.model.NodeData> conditionNode : group
                        .getConditions()) {
                    List<Map<String, Object>> extractedConditions = conditionParser.extractConditions(conditionNode);
                    for (Map<String, Object> condition : extractedConditions) {
                        Map<String, Object> conditionCopy = new HashMap<>(condition);
                        String conditionId = groupId + "_" + childIndex;
                        conditionCopy.put("id", conditionId);
                        conditionCopy.put("pid", groupId);
                        children.add(conditionCopy);
                        childIndex++;
                    }
                }

                // Add action as sibling if this group has one
                if (group.hasAction()) {
                    List<Map<String, Object>> extractedActions = actionParser.extractActions(group.getAction());
                    for (Map<String, Object> action : extractedActions) {
                        Map<String, Object> actionCopy = new HashMap<>(action);
                        String actionId = groupId + "_" + childIndex;
                        actionCopy.put("id", actionId);
                        actionCopy.put("pid", groupId);
                        children.add(actionCopy);
                        childIndex++;
                        logger.debug("Added action as sibling [group={}, action_id={}]", groupIndex, actionId);
                    }
                }

                innerGroup.put("childrens", children);
                innerGroups.add(innerGroup);
            }

            outerGroup.put("childrens", innerGroups);
            result.add(outerGroup);

            logger.info("Built JSON from groups [outer_groups=1, inner_groups={}, total_groups={}]",
                    innerGroups.size(), groups.size());
            return result;
        } catch (Exception e) {
            logger.error("Failed to build from condition groups [error={}]", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    /**
     * Reset builder to initial state.
     */
    public RuleJsonBuilder reset() {
        this.conditions = new ArrayList<>();
        this.actions = new ArrayList<>();
        this.schedule = null;
        logger.debug("Builder reset to initial state");
        return this;
    }
}
