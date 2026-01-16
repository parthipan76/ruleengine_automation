package com.sixdee.text2rule.agent;

import com.sixdee.text2rule.model.NodeData;
import com.sixdee.text2rule.model.RuleNode;
import com.sixdee.text2rule.model.RuleTree;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.state.AgentState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;

/**
 * ScheduleExtractionAgent extracts schedule details from Schedule nodes.
 * Dummy implementation as requested.
 */
public class ScheduleExtractionAgent {
    private static final Logger logger = LoggerFactory.getLogger(ScheduleExtractionAgent.class);

    private final ChatLanguageModel lang4jService;
    private CompiledGraph<ScheduleState> compiledGraph;

    public static class ScheduleState extends AgentState {
        public ScheduleState(Map<String, Object> initData) {
            super(new HashMap<>(initData));
        }

        @SuppressWarnings("unchecked")
        public RuleTree<NodeData> getTree() {
            return (RuleTree<NodeData>) this.data().get("tree");
        }
    }

    public ScheduleExtractionAgent(ChatLanguageModel lang4jService) {
        this.lang4jService = lang4jService;
        compile();
    }

    private void compile() {
        try {
            StateGraph<ScheduleState> graph = new StateGraph<>(ScheduleState::new);
            graph.addNode("extract_schedule", this::extractNode);
            graph.addEdge(START, "extract_schedule");
            graph.addEdge("extract_schedule", END);
            this.compiledGraph = graph.compile();
        } catch (Exception e) {
            logger.error("Failed to compile ScheduleExtractionAgent", e);
            throw new RuntimeException(e);
        }
    }

    private CompletableFuture<Map<String, Object>> extractNode(ScheduleState state) {
        logger.info("ScheduleExtractionAgent: Executing Extract Node...");
        RuleTree<NodeData> tree = state.getTree();
        if (tree == null) {
            return CompletableFuture.completedFuture(Map.of("failed", true));
        }

        processNode(tree.getRoot());
        return CompletableFuture.completedFuture(Map.of("tree", tree));
    }

    private void processNode(RuleNode<NodeData> node) {
        if (node == null)
            return;

        // Check if this is a Schedule node
        if ("Schedule".equalsIgnoreCase(node.getData().getType())) {
            String scheduleText = node.getData().getInput();
            logger.info("Found Schedule node: {}", scheduleText);

            // Dummy Extraction Logic
            logger.info("Dummy Extraction: Parsing schedule details...");

            // Add a child node representing extracted info
            NodeData extractedData = new NodeData("ScheduleDetails", "", "", node.getData().getModelName(), "",
                    "Extracted Schedule: Valid for weekly execution on Mon, Tue.");

            node.addChild(new RuleNode<>(extractedData));
            logger.info("Added extracted schedule details to tree.");
        }

        // Recursively process children
        if (node.getChildren() != null) {
            for (RuleNode<NodeData> child : node.getChildren()) {
                processNode(child);
            }
        }
    }

    public CompletableFuture<ScheduleState> execute(RuleTree<NodeData> tree) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return compiledGraph.invoke(Map.of("tree", tree)).orElse(null);
            } catch (Exception e) {
                logger.error("Error executing ScheduleExtractionAgent", e);
                throw new RuntimeException(e);
            }
        });
    }
}
