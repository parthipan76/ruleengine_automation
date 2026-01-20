package com.sixdee.text2rule.view;

import com.sixdee.text2rule.model.RuleTree;
import com.sixdee.text2rule.workflow.DecompositionWorkflow;
import com.sixdee.text2rule.workflow.WorkflowState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WorkflowGraphRenderer implements TreeRenderer {
    private static final Logger logger = LoggerFactory.getLogger(WorkflowGraphRenderer.class);

    @Override
    public void render(RuleTree<?> tree) {
        // No-op
    }

    @Override
    public void render(RuleTree<?> tree, WorkflowState state, DecompositionWorkflow workflow) {
        if (workflow == null) {
            logger.warn("Cannot render workflow graph: workflow object is null");
            return;
        }

        try {
            String graphOutput = workflow.print();
            System.out.println(graphOutput);
            logger.debug("Workflow graph displayed successfully");
        } catch (Exception e) {
            logger.error("Failed to display workflow graph", e);
        }
    }

    // Default render(tree, state) delegates to render(tree), which does nothing.
    // This renderer ONLY works if workflow is provided.
}
