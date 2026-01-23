error id: file://<WORKSPACE>/src/main/java/com/sixdee/text2rule/view/FinalRuleJsonRenderer.java:_empty_/RuleJsonBuilder#
file://<WORKSPACE>/src/main/java/com/sixdee/text2rule/view/FinalRuleJsonRenderer.java
empty definition using pc, found symbol in pc: _empty_/RuleJsonBuilder#
empty definition using semanticdb
empty definition using fallback
non-local guesses:

offset: 1072
uri: file://<WORKSPACE>/src/main/java/com/sixdee/text2rule/view/FinalRuleJsonRenderer.java
text:
```scala
package com.sixdee.text2rule.view;

import com.sixdee.text2rule.builder.RuleJsonBuilder;
import com.sixdee.text2rule.model.NodeData;
import com.sixdee.text2rule.model.RuleNode;
import com.sixdee.text2rule.model.RuleTree;
import com.sixdee.text2rule.parser.ActionParser;
import com.sixdee.text2rule.parser.ConditionParser;
import com.sixdee.text2rule.parser.ScheduleParser;
import com.sixdee.text2rule.util.TreeTraverser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * Refactored FinalRuleJsonRenderer following SOLID principles.
 * Delegates parsing to specialized parser classes.
 * Uses Builder pattern for JSON construction.
 * Follows Single Responsibility Principle - only orchestrates rendering.
 */
public class FinalRuleJsonRenderer {
    private static final Logger logger = LoggerFactory.getLogger(FinalRuleJsonRenderer.class);

    private final ConditionParser conditionParser;
    private final ActionParser actionParser;
    private final ScheduleParser scheduleParser;
    private final RuleJs@@onBuilder jsonBuilder;
    private final TreeTraverser treeTraverser;

    /**
     * Constructor with dependency injection of parsers and builder.
     */
    public FinalRuleJsonRenderer() {
        try {
            this.conditionParser = new ConditionParser();
            this.actionParser = new ActionParser();
            this.scheduleParser = new ScheduleParser();
            this.jsonBuilder = new RuleJsonBuilder();
            this.treeTraverser = new TreeTraverser();
            logger.info(
                    "FinalRuleJsonRenderer initialized [parsers=3, builder=RuleJsonBuilder, traverser=TreeTraverser]");
        } catch (Exception e) {
            logger.error("Failed to initialize FinalRuleJsonRenderer [error={}]", e.getMessage(), e);
            throw new RuntimeException("Renderer initialization failed", e);
        }
    }

    /**
     * Render the tree into hierarchical rule JSON format.
     * Orchestrates parsing and building process.
     * 
     * @param tree The RuleTree to render
     * @return JSON string representation of the rule
     */
    public String render(RuleTree<NodeData> tree) {
        List<Map<String, Object>> conditions = null;
        List<Map<String, Object>> actions = null;
        Map<String, Object> schedule = null;
        String result = null;

        try {
            if (tree == null || tree.getRoot() == null) {
                logger.warn("Render called with null tree or root");
                return "[]";
            }

            logger.info("Starting rule JSON rendering [tree_has_root=true]");

            // Extract components using specialized parsers
            conditions = conditionParser.extractConditions(tree.getRoot());
            actions = actionParser.extractActions(tree.getRoot());
            schedule = scheduleParser.extractSchedule(tree.getRoot());

            // Build JSON using builder pattern
            result = jsonBuilder
                    .withConditions(conditions)
                    .withActions(actions)
                    .withSchedule(schedule)
                    .build();

            logger.info("Rule JSON rendering completed [output_size={}, conditions={}, actions={}, has_schedule={}]",
                    result.length(), conditions.size(), actions.size(), schedule != null);

            return result;
        } catch (Exception e) {
            logger.error("Failed to render rule JSON [error={}]", e.getMessage(), e);
            return "[]";
        } finally {
            // Cleanup resources
            conditions = null;
            actions = null;
            schedule = null;
            // Note: result is returned, so not nullified here
        }
    }

    /**
     * Render the tree using leftmost-leaf-first traversal with condition-action
     * pairing.
     * This method:
     * 1. Finds the leftmost leaf node
     * 2. If it's a condition, creates a condition group
     * 3. Searches upward for matching action
     * 4. Pairs them in the output
     * 
     * @param tree The RuleTree to render
     * @return JSON string representation with paired condition-action structure
     */
    public String renderWithPairing(RuleTree<NodeData> tree) {
        RuleNode<NodeData> leftmostLeaf = null;
        RuleNode<NodeData> matchingAction = null;
        List<Map<String, Object>> conditions = null;
        List<Map<String, Object>> actions = null;
        Map<String, Object> schedule = null;
        String result = null;

        try {
            if (tree == null || tree.getRoot() == null) {
                logger.warn("renderWithPairing called with null tree or root");
                return "[]";
            }

            logger.info("Starting paired rule JSON rendering [tree_has_root=true]");

            // Find leftmost leaf
            leftmostLeaf = treeTraverser.findLeftmostLeaf(tree.getRoot());

            if (leftmostLeaf == null) {
                logger.warn("No leftmost leaf found");
                return "[]";
            }

            logger.info("Found leftmost leaf [type={}]", leftmostLeaf.getData().getType());

            // Check if leftmost leaf is a condition
            if ("IF_Condition".equalsIgnoreCase(leftmostLeaf.getData().getType())) {
                logger.info("Leftmost leaf is a condition, searching for matching action");

                // Find matching action
                matchingAction = treeTraverser.findMatchingAction(leftmostLeaf);

                if (matchingAction != null) {
                    logger.info("Found matching action [action_type={}]", matchingAction.getData().getType());
                } else {
                    logger.warn("No matching action found for condition");
                }
            }

            // Extract components using specialized parsers
            // For now, use existing extraction logic
            // TODO: Modify to use paired structure
            conditions = conditionParser.extractConditions(tree.getRoot());
            actions = actionParser.extractActions(tree.getRoot());
            schedule = scheduleParser.extractSchedule(tree.getRoot());

            // Build JSON using builder pattern
            result = jsonBuilder
                    .withConditions(conditions)
                    .withActions(actions)
                    .withSchedule(schedule)
                    .build();

            logger.info(
                    "Paired rule JSON rendering completed [output_size={}, leftmost_leaf={}, has_matching_action={}]",
                    result.length(), leftmostLeaf.getData().getType(), matchingAction != null);

            return result;
        } catch (Exception e) {
            logger.error("Failed to render paired rule JSON [error={}]", e.getMessage(), e);
            return "[]";
        } finally {
            // Cleanup resources
            leftmostLeaf = null;
            matchingAction = null;
            conditions = null;
            actions = null;
            schedule = null;
        }
    }
}

```


#### Short summary: 

empty definition using pc, found symbol in pc: _empty_/RuleJsonBuilder#