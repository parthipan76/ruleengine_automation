package com.sixdee.text2rule.util;

import com.sixdee.text2rule.model.NodeData;
import com.sixdee.text2rule.model.RuleNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for tree traversal operations.
 * Provides methods for finding leftmost leaf nodes and matching actions.
 */
public class TreeTraverser {
    private static final Logger logger = LoggerFactory.getLogger(TreeTraverser.class);

    /**
     * Find the leftmost leaf node in the tree.
     * Traverses down the leftmost path until reaching a leaf.
     * 
     * @param root The root node to start from
     * @return The leftmost leaf node, or null if tree is empty
     */
    public RuleNode<NodeData> findLeftmostLeaf(RuleNode<NodeData> root) {
        if (root == null) {
            logger.warn("findLeftmostLeaf called with null root");
            return null;
        }

        RuleNode<NodeData> current = root;

        try {
            // Traverse down the leftmost path
            while (current.getChildren() != null && !current.getChildren().isEmpty()) {
                current = current.getChildren().get(0); // Always take first (leftmost) child
                logger.debug("Traversing to leftmost child [type={}]", current.getData().getType());
            }

            logger.info("Found leftmost leaf [type={}, input_length={}]",
                    current.getData().getType(),
                    current.getData().getInput() != null ? current.getData().getInput().length() : 0);

            return current;
        } catch (Exception e) {
            logger.error("Error finding leftmost leaf [error={}]", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Find matching action for a condition node by searching upward through parent
     * nodes.
     * Algorithm:
     * 1. Start at condition node
     * 2. Get parent
     * 3. Check parent's children for Action node
     * 4. If not found, go up to parent's parent and repeat
     * 5. Continue until Action found or reach root
     * 
     * @param conditionNode The condition node to find an action for
     * @return The matching Action node, or null if not found
     */
    public RuleNode<NodeData> findMatchingAction(RuleNode<NodeData> conditionNode) {
        if (conditionNode == null) {
            logger.warn("findMatchingAction called with null conditionNode");
            return null;
        }

        RuleNode<NodeData> current = conditionNode;
        RuleNode<NodeData> parent = null;
        RuleNode<NodeData> actionNode = null;

        try {
            logger.debug("Searching for matching action [starting_from={}]", conditionNode.getData().getType());

            // Search upward through parent nodes
            while (current != null) {
                parent = current.getParent();

                if (parent == null) {
                    logger.debug("Reached root without finding action");
                    break;
                }

                logger.debug("Checking parent [type={}] for action siblings", parent.getData().getType());

                // Check parent's children for Action node
                actionNode = findActionInChildren(parent);

                if (actionNode != null) {
                    logger.info("Found matching action [action_type={}, parent_type={}]",
                            actionNode.getData().getType(), parent.getData().getType());
                    return actionNode;
                }

                // Move up to parent and continue search
                current = parent;
            }

            logger.warn("No matching action found for condition [condition_type={}]",
                    conditionNode.getData().getType());
            return null;
        } catch (Exception e) {
            logger.error("Error finding matching action [error={}]", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Find an Action node among the children of a parent node.
     * 
     * @param parent The parent node whose children to search
     * @return The first Action node found, or null if none found
     */
    private RuleNode<NodeData> findActionInChildren(RuleNode<NodeData> parent) {
        if (parent == null || parent.getChildren() == null) {
            return null;
        }

        for (RuleNode<NodeData> child : parent.getChildren()) {
            if ("Action".equalsIgnoreCase(child.getData().getType())) {
                logger.debug("Found Action node in children [parent_type={}]", parent.getData().getType());
                return child;
            }
        }

        return null;
    }

    /**
     * Get all siblings of a node (including the node itself).
     * 
     * @param node The node whose siblings to retrieve
     * @return List of sibling nodes, or empty list if no parent
     */
    public List<RuleNode<NodeData>> getSiblings(RuleNode<NodeData> node) {
        if (node == null || node.getParent() == null) {
            logger.debug("getSiblings called with null node or no parent");
            return new ArrayList<>();
        }

        RuleNode<NodeData> parent = node.getParent();

        if (parent.getChildren() == null) {
            return new ArrayList<>();
        }

        logger.debug("Retrieved siblings [count={}, parent_type={}]",
                parent.getChildren().size(), parent.getData().getType());

        return new ArrayList<>(parent.getChildren());
    }
}
