package com.sixdee.text2rule.view;

import com.sixdee.text2rule.model.RuleTree;
import com.sixdee.text2rule.workflow.DecompositionWorkflow;
import com.sixdee.text2rule.workflow.WorkflowState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConsistencyRenderer implements TreeRenderer {
    private static final Logger logger = LoggerFactory.getLogger(ConsistencyRenderer.class);

    @Override
    public void render(RuleTree<?> tree) {
        // No-op or delegate
    }

    @Override
    public void render(RuleTree<?> tree, WorkflowState state, DecompositionWorkflow workflow) {
        renderWithState(state);
    }

    @Override
    public void render(RuleTree<?> tree, WorkflowState state) {
        renderWithState(state);
    }

    private void renderWithState(WorkflowState state) {
        if (state == null)
            return;

        Double consistencyScore = null;
        String consistencyFeedback = null;
        try {
            consistencyScore = state.getConsistencyScore();
            consistencyFeedback = state.getFeedback();

            if (consistencyScore != null) {
                System.out.println("\n" + "=".repeat(50));
                System.out.println("CONSISTENCY CHECK RESULTS");
                System.out.println("=".repeat(50));
                System.out.println("Score: " + consistencyScore);
                System.out.println("Feedback: " + (consistencyFeedback != null ? consistencyFeedback : "N/A"));
                System.out.println("=".repeat(50) + "\n");
                logger.debug("Consistency results displayed [score={}]", consistencyScore);
            } else {
                logger.debug("No consistency score available to display");
            }
        } catch (Exception e) {
            logger.error("Error displaying consistency results", e);
        }
    }
}
