error id: file://<WORKSPACE>/src/main/java/com/sixdee/text2rule/view/ResultPresenter.java:_empty_/NodeData#
file://<WORKSPACE>/src/main/java/com/sixdee/text2rule/view/ResultPresenter.java
empty definition using pc, found symbol in pc: _empty_/NodeData#
empty definition using semanticdb
empty definition using fallback
non-local guesses:

offset: 1929
uri: file://<WORKSPACE>/src/main/java/com/sixdee/text2rule/view/ResultPresenter.java
text:
```scala
package com.sixdee.text2rule.view;

import com.sixdee.text2rule.config.ConfigurationManager;
import com.sixdee.text2rule.factory.RendererFactory;
import com.sixdee.text2rule.model.NodeData;
import com.sixdee.text2rule.model.RuleTree;
import com.sixdee.text2rule.workflow.DecompositionWorkflow;
import com.sixdee.text2rule.workflow.WorkflowState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Facade for handling all application output presentation.
 * Encapsulates the logic for displaying workflow graphs, consistency results,
 * and rule trees.
 * Keeps Main class clean of view/presentation logic.
 */
public class ResultPresenter {
    private static final Logger logger = LoggerFactory.getLogger(ResultPresenter.class);

    private final ConfigurationManager config;

    public ResultPresenter(ConfigurationManager config) {
        this.config = config;
    }

    /**
     * Render all execution results: Graph, Consistency, and Rule Tree.
     * 
     * @param graphBuilder The workflow definition
     * @param state        The final execution state
     */
    public void renderTree(DecompositionWorkflow graphBuilder, WorkflowState state) {
        try {
            logger.info("Presenting execution results...");

            // Render all results using factory-created renderers (Chain of Responsibility)
            renderTreeNodes(state, graphBuilder);

            logger.info("Results presented successfully");
        } catch (Exception e) {
            logger.error("Failed to present results [error={}]", e.getMessage(), e);
        }
    }

    /**
     * Display workflow graph using Mermaid.
     */
    // Obsolete display methods removed in favor of Renderer implementations

    /**
     * Render the rule tree using configured renderers.
     */
    private void renderTreeNodes(WorkflowState state, DecompositionWorkflow workflow) {
        RuleTree<Node@@Data> tree = null;
        RendererFactory rendererFactory = null;
        List<TreeRenderer> renderers = null;

        try {
            tree = state.getTree();
            // Ensure tree is not null for renderers that depend on it
            // However, WorkflowGraphRenderer might strictly depend on workflow, not tree.
            // But strict TreeRenderer interface requires tree.
            // We proceed even if tree is null, passing it as null? No, State.getTree()
            // might be null.

            // use Factory to create renderers (Separation of Concern)
            rendererFactory = new RendererFactory(config);
            renderers = rendererFactory.createEnabledRenderers();

            if (renderers.isEmpty()) {
                logger.warn("No renderers enabled in configuration");
                return;
            }

            // Execute rendering
            for (TreeRenderer renderer : renderers) {
                try {
                    renderer.render(tree, state, workflow);
                } catch (Exception e) {
                    logger.error("Renderer failed [renderer={}, error={}]",
                            renderer.getClass().getSimpleName(), e.getMessage(), e);
                }
            }
        } catch (Exception e) {
            logger.error("Error processing tree rendering [error={}]", e.getMessage(), e);
        } finally {
            tree = null;
            rendererFactory = null;
            renderers = null;
        }
    }
}

```


#### Short summary: 

empty definition using pc, found symbol in pc: _empty_/NodeData#