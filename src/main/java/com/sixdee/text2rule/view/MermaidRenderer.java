package com.sixdee.text2rule.view;

import com.sixdee.text2rule.model.RuleTree;
import com.sixdee.text2rule.model.RuleNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MermaidRenderer implements TreeRenderer {
    private static final Logger logger = LoggerFactory.getLogger(MermaidRenderer.class);

    @Override
    public void render(RuleTree<?> tree) {
        // Initialize all variables before try block
        StringBuilder sb = null;

        try {
            if (tree.getRoot() == null) {
                logger.warn("Cannot render Mermaid graph: tree root is null");
                return;
            }

            sb = new StringBuilder();
            sb.append("--- Mermaid Graph ---\n");
            sb.append("graph TD\n");

            // Define styles
            sb.append("    classDef root fill:#f9f,stroke:#333,stroke-width:2px;\n");
            sb.append("    classDef condition fill:#e1f5fe,stroke:#01579b,stroke-width:2px;\n");
            sb.append("    classDef logic fill:#fff9c4,stroke:#fbc02d,stroke-width:2px;\n");
            sb.append("    classDef schedule fill:#e8f5e9,stroke:#2e7d32,stroke-width:2px;\n");
            sb.append("    classDef action fill:#fff3e0,stroke:#ef6c00,stroke-width:2px;\n");
            sb.append("    classDef policy fill:#f3e5f5,stroke:#7b1fa2,stroke-width:2px;\n");
            sb.append("    classDef defaultNode fill:#fff,stroke:#333,stroke-width:1px;\n");

            printGraphRecursive(tree.getRoot(), sb);
            sb.append("---------------------");

            logger.info("Mermaid Graph:\n{}", sb.toString());

        } catch (Exception e) {
            logger.error("Error rendering Mermaid graph", e);
        } finally {
            // Cleanup
            sb = null;
            logger.debug("Mermaid rendering completed");
        }
    }

    private void printGraphRecursive(RuleNode<?> node, StringBuilder sb) {
        // Initialize all variables before try block
        String parentId = null;
        String parentLabel = null;
        String styleClass = null;
        com.sixdee.text2rule.model.NodeData data = null;
        String type = null;
        String childId = null;

        try {
            if (node == null || sb == null) {
                return;
            }

            parentId = "N" + Math.abs(node.hashCode());
            parentLabel = node.getData().toString().replace("\"", "'");

            // Determine style class based on type
            styleClass = "defaultNode";
            if (node.getData() instanceof com.sixdee.text2rule.model.NodeData) {
                data = (com.sixdee.text2rule.model.NodeData) node.getData();
                type = data.getType();
                if (type != null) {
                    switch (type.toLowerCase()) {
                        case "root":
                            styleClass = "root";
                            break;
                        case "condition":
                            styleClass = "condition";
                            break;
                        case "logic":
                            styleClass = "logic";
                            break;
                        case "schedule":
                            styleClass = "schedule";
                            break;
                        case "action":
                            styleClass = "action";
                            break;
                        case "policy":
                            styleClass = "policy";
                            break;
                    }
                }
            }

            sb.append(String.format("    %s[\"%s\"]:::%s\n", parentId, parentLabel, styleClass));

            for (RuleNode<?> child : node.getChildren()) {
                childId = "N" + Math.abs(child.hashCode());
                sb.append(String.format("    %s --> %s\n", parentId, childId));
                printGraphRecursive(child, sb);
            }

        } catch (Exception e) {
            logger.error("Error in recursive Mermaid graph printing", e);
        } finally {
            // Cleanup
            parentId = null;
            parentLabel = null;
            styleClass = null;
            data = null;
            type = null;
            childId = null;
        }
    }
}
