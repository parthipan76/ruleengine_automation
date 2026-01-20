package com.sixdee.text2rule.factory;

import com.sixdee.text2rule.config.ConfigurationManager;
import com.sixdee.text2rule.model.NodeData;
import com.sixdee.text2rule.model.RuleTree;
import com.sixdee.text2rule.view.AsciiRenderer;
import com.sixdee.text2rule.view.ConsistencyRenderer;
import com.sixdee.text2rule.view.FinalRuleJsonRenderer;
import com.sixdee.text2rule.view.MermaidRenderer;
import com.sixdee.text2rule.view.TreeRenderer;
import com.sixdee.text2rule.view.WorkflowGraphRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Factory for creating TreeRenderer instances based on configuration.
 * Follows Factory Pattern and Single Responsibility Principle.
 * Separates renderer creation logic from Main class.
 */
public class RendererFactory {
    private static final Logger logger = LoggerFactory.getLogger(RendererFactory.class);

    private final ConfigurationManager config;

    /**
     * Constructor with dependency injection.
     * 
     * @param config Configuration manager instance
     */
    public RendererFactory(ConfigurationManager config) {
        this.config = config;
        logger.debug("RendererFactory initialized");
    }

    /**
     * Create list of enabled renderers based on configuration.
     * 
     * @return List of active TreeRenderer instances
     */
    public List<TreeRenderer> createEnabledRenderers() {
        List<TreeRenderer> renderers = null;

        try {
            renderers = new ArrayList<>();

            // Add ASCII renderer if enabled
            if (config.isAsciiRendererEnabled()) {
                renderers.add(new AsciiRenderer());
                logger.debug("ASCII renderer enabled");
            }

            // Add Mermaid renderer if enabled
            if (config.isMermaidRendererEnabled()) {
                renderers.add(new MermaidRenderer());
                logger.debug("Mermaid renderer enabled");
            }

            // Add JSON renderer if enabled
            if (config.isJsonRendererEnabled()) {
                renderers.add(createJsonRendererWrapper());
                logger.debug("JSON renderer enabled");
            }

            // Add Consistency renderer if enabled
            if (config.isConsistencyRendererEnabled()) {
                renderers.add(new ConsistencyRenderer());
                logger.debug("Consistency renderer enabled");
            }

            // Add Workflow Graph renderer if enabled
            if (config.isWorkflowGraphRendererEnabled()) {
                renderers.add(new WorkflowGraphRenderer());
                logger.debug("Workflow Graph renderer enabled");
            }

            if (renderers.isEmpty()) {
                logger.warn("No renderers enabled in configuration");
            } else {
                logger.info("Created renderers [count={}]", renderers.size());
            }

            return renderers;
        } finally {
            // Cleanup handled by return
        }
    }

    /**
     * Create a TreeRenderer wrapper for FinalRuleJsonRenderer.
     * Wraps the JSON renderer to implement TreeRenderer interface.
     * 
     * @return TreeRenderer instance that wraps FinalRuleJsonRenderer
     */
    private TreeRenderer createJsonRendererWrapper() {
        return new TreeRenderer() {
            private final FinalRuleJsonRenderer jsonRenderer = new FinalRuleJsonRenderer();

            @Override
            @SuppressWarnings("unchecked")
            public void render(RuleTree<?> tree) {
                if (tree == null) {
                    logger.warn("Cannot render JSON: tree is null");
                    return;
                }
                String jsonOutput = null;

                try {
                    jsonOutput = jsonRenderer.render((RuleTree<NodeData>) tree);

                    System.out.println("\n" + "=".repeat(80));
                    System.out.println("FINAL RULE JSON OUTPUT");
                    System.out.println("=".repeat(80));
                    System.out.println(jsonOutput);
                    System.out.println("=".repeat(80) + "\n");
                } finally {
                    jsonOutput = null;
                }
            }
        };
    }
}
