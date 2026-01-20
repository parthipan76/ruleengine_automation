package com.sixdee.text2rule.view;

import com.sixdee.text2rule.model.RuleTree;

public interface TreeRenderer {
    /**
     * Renders the tree structure.
     * 
     * @param tree the tree to render
     */
    void render(RuleTree<?> tree);

    /**
     * Renders the tree structure with additional workflow state context.
     * Default implementation delegates to render(tree).
     * 
     * @param tree  the tree to render
     * @param state the workflow state containing metadata (scores, etc.)
     */
    default void render(RuleTree<?> tree, com.sixdee.text2rule.workflow.WorkflowState state) {
        render(tree);
    }

    /**
     * Renders with full context including workflow definition.
     * Default delegates to render(tree, state).
     */
    default void render(RuleTree<?> tree, com.sixdee.text2rule.workflow.WorkflowState state,
            com.sixdee.text2rule.workflow.DecompositionWorkflow workflow) {
        render(tree, state);
    }
}
