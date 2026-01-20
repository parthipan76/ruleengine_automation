package com.sixdee.text2rule.view;

import com.sixdee.text2rule.model.RuleTree;
import com.sixdee.text2rule.model.RuleNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AsciiRenderer implements TreeRenderer {
    private static final Logger logger = LoggerFactory.getLogger(AsciiRenderer.class);

    @Override
    public void render(RuleTree<?> tree, com.sixdee.text2rule.workflow.WorkflowState state) {
        if (state != null) {
            Double score = state.getConsistencyScore();
            // String feedback = state.getFeedback(); // Unused
            StringBuilder info = new StringBuilder();
            info.append("\n--- Consistency Info ---\n");
            info.append("Score: ").append(score != null ? score : "N/A").append("\n");
            // info.append("Feedback: ").append(feedback != null ? feedback :
            // "N/A").append("\n"); // Optional: Feedback might be long
            info.append("------------------------");
            logger.debug(info.toString());
        }
        render(tree);
    }

    @Override
    public void render(RuleTree<?> tree) {
        // Initialize all variables before try block
        StringBuilder buffer = null;

        try {
            if (tree == null || tree.getRoot() == null) {
                logger.warn("Cannot render ASCII tree: tree or root is null");
                return;
            }

            buffer = new StringBuilder();
            buffer.append("--- ASCII Tree Visualization ---\n");
            printTreeRecursive(tree.getRoot(), "", true, buffer);
            buffer.append("--------------------------------");

            logger.debug("ASCII Tree:\n{}", buffer.toString());

        } catch (Exception e) {
            logger.error("Error rendering ASCII tree", e);
        } finally {
            // Cleanup
            buffer = null;
            logger.debug("ASCII rendering completed");
        }
    }

    private void printTreeRecursive(RuleNode<?> node, String prefix, boolean isTail, StringBuilder sb) {
        // Initialize all variables before try block
        int childCount = 0;

        try {
            if (node == null || sb == null) {
                return;
            }

            sb.append(prefix).append(isTail ? "└── " : "├── ").append(node.getData()).append("\n");

            childCount = node.getChildren().size();
            for (int i = 0; i < childCount - 1; i++) {
                printTreeRecursive(node.getChildren().get(i), prefix + (isTail ? "    " : "│   "), false, sb);
            }
            if (childCount > 0) {
                printTreeRecursive(node.getChildren().get(childCount - 1), prefix + (isTail ? "    " : "│   "), true,
                        sb);
            }

        } catch (Exception e) {
            logger.error("Error in recursive ASCII tree printing", e);
        } finally {
            // Cleanup - primitives don't need nulling
            logger.debug("Recursive print completed for node");
        }
    }
}
