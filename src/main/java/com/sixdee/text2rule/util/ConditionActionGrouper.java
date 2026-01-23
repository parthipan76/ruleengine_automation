package com.sixdee.text2rule.util;

import com.sixdee.text2rule.model.NodeData;
import com.sixdee.text2rule.model.RuleNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Groups conditions by their associated actions based on tree hierarchy.
 */
public class ConditionActionGrouper {
    private static final Logger logger = LoggerFactory.getLogger(ConditionActionGrouper.class);
    private final TreeTraverser traverser;

    public ConditionActionGrouper() {
        this.traverser = new TreeTraverser();
    }

    /**
     * Group conditions by their associated actions.
     * Returns a list of groups, where each group contains conditions and optionally
     * an action.
     */
    public List<ConditionGroup> groupConditionsByAction(RuleNode<NodeData> root) {
        List<ConditionGroup> groups = new ArrayList<>();
        List<RuleNode<NodeData>> allConditions = findAllConditions(root);

        logger.info("Found {} total IF_Condition nodes", allConditions.size());

        // Group conditions by their matching action
        Map<RuleNode<NodeData>, List<RuleNode<NodeData>>> actionToConditions = new HashMap<>();
        List<RuleNode<NodeData>> conditionsWithoutAction = new ArrayList<>();

        for (RuleNode<NodeData> condition : allConditions) {
            RuleNode<NodeData> action = traverser.findMatchingAction(condition);

            if (action != null) {
                actionToConditions.computeIfAbsent(action, k -> new ArrayList<>()).add(condition);
                logger.debug("Grouped condition with action [action_type={}]", action.getData().getType());
            } else {
                conditionsWithoutAction.add(condition);
                logger.debug("Condition has no matching action");
            }
        }

        // Create group for conditions without actions
        if (!conditionsWithoutAction.isEmpty()) {
            ConditionGroup group = new ConditionGroup(conditionsWithoutAction, null);
            groups.add(group);
            logger.info("Created group without action [conditions={}]", conditionsWithoutAction.size());
        }

        // Create groups for each action
        for (Map.Entry<RuleNode<NodeData>, List<RuleNode<NodeData>>> entry : actionToConditions.entrySet()) {
            ConditionGroup group = new ConditionGroup(entry.getValue(), entry.getKey());
            groups.add(group);
            logger.info("Created group with action [conditions={}, action_type={}]",
                    entry.getValue().size(), entry.getKey().getData().getType());
        }

        return groups;
    }

    /**
     * Find all IF_Condition nodes in the tree.
     */
    private List<RuleNode<NodeData>> findAllConditions(RuleNode<NodeData> root) {
        List<RuleNode<NodeData>> conditions = new ArrayList<>();
        findConditionsRecursive(root, conditions);
        return conditions;
    }

    private void findConditionsRecursive(RuleNode<NodeData> node, List<RuleNode<NodeData>> conditions) {
        if (node == null) {
            return;
        }

        if ("IF_Condition".equalsIgnoreCase(node.getData().getType())) {
            conditions.add(node);
        }

        if (node.getChildren() != null) {
            for (RuleNode<NodeData> child : node.getChildren()) {
                findConditionsRecursive(child, conditions);
            }
        }
    }

    /**
     * Represents a group of conditions with an optional action.
     */
    public static class ConditionGroup {
        private final List<RuleNode<NodeData>> conditions;
        private final RuleNode<NodeData> action;

        public ConditionGroup(List<RuleNode<NodeData>> conditions, RuleNode<NodeData> action) {
            this.conditions = conditions;
            this.action = action;
        }

        public List<RuleNode<NodeData>> getConditions() {
            return conditions;
        }

        public RuleNode<NodeData> getAction() {
            return action;
        }

        public boolean hasAction() {
            return action != null;
        }
    }
}
