error id: file://<WORKSPACE>/src/main/java/com/sixdee/text2rule/view/ResultPresenter.java:java/lang/Exception#
file://<WORKSPACE>/src/main/java/com/sixdee/text2rule/view/ResultPresenter.java
empty definition using pc, found symbol in pc: java/lang/Exception#
empty definition using semanticdb
empty definition using fallback
non-local guesses:

offset: 2185
uri: file://<WORKSPACE>/src/main/java/com/sixdee/text2rule/view/ResultPresenter.java
text:
```scala
package com.sixdee.text2rule.view;

import com.sixdee.text2rule.config.ConfigurationManager;
import com.sixdee.text2rule.factory.RendererFactory;
import com.sixdee.text2rule.model.NodeData;
import com.sixdee.text2rule.model.RuleTree;
import com.sixdee.text2rule.view.TreeRenderer;
import com.sixdee.text2rule.workflow.DecompositionWorkflow;
import com.sixdee.text2rule.workflow.WorkflowState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

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
     * Present all execution results: Graph, Consistency, and Rule Tree.
     * 
     * @param graphBuilder The workflow definition
     * @param state        The final execution state
     */
    public void present(DecompositionWorkflow graphBuilder, WorkflowState state) {
        try {
            logger.info("Presenting execution results...");

            // 1. Display workflow graph
            displayWorkflowGraph(graphBuilder);

            // 2. Display consistency check results
            displayConsistencyResults(state);

            // 3. Render tree using factory-created renderers
            renderTree(state);

            logger.info("Results presented successfully");
        } catch (Exception e) {
            logger.error("Failed to present results [error={}]", e.getMessage(), e);
        }
    }

    /**
     * Display workflow graph using Mermaid.
     */
    private void displayWorkflowGraph(DecompositionWorkflow graphBuilder) {
        String graphOutput = null;
        try {
            graphOutput = graphBuilder.print();
            System.out.println(graphOutput);
            logger.info("Workflow graph displayed successfully");
        } catch (Excepti@@on e) {
            logger.error("Failed to display workflow graph [error={}]", e.getMessage(), e);
        }
    }

    /**
     * Display consistency check results.
     */
    private void displayConsistencyResults(WorkflowState state) {
        Double consistencyScore = null;
        String consistencyFeedback = null;
        try {
            consistencyScore = state.getConsistencyScore();
            consistencyFeedback = state.getConsistencyFeedback();

            if (consistencyScore != null) {
                System.out.println("\n" + "=".repeat(50));
                System.out.println("CONSISTENCY CHECK RESULTS");
                System.out.println("=".repeat(50));
                System.out.println("Score: " + consistencyScore);
                System.out.println("Feedback: " + (consistencyFeedback != null ? consistencyFeedback : "N/A"));
                System.out.println("=".repeat(50) + "\n");
                logger.info("Consistency results displayed [score={}]", consistencyScore);
            } else {
                logger.info("No consistency score available to display");
            }
        } catch (Exception e) {
            logger.error("Error logging consistency results [error={}]", e.getMessage(), e);
        }
    }

    /**
     * Render the rule tree using configured renderers.
     */
    private void renderTree(WorkflowState state) {
        RuleTree<NodeData> tree = null;
        RendererFactory rendererFactory = null;
        List<TreeRenderer> renderers = null;

        try {
            tree = state.getTree();
            if (tree == null) {
                logger.warn("No tree available to render");
                return;
            }

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
                    renderer.render(tree);
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

empty definition using pc, found symbol in pc: java/lang/Exception#