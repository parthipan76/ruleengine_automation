package com.sixdee.text2rule.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sixdee.text2rule.config.PromptRegistry;
import com.sixdee.text2rule.config.SupabaseService;
import com.sixdee.text2rule.model.NodeData;
import com.sixdee.text2rule.model.RuleNode;
import com.sixdee.text2rule.model.RuleTree;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.state.AgentState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;

public class UnifiedRuleAgent {
    private static final Logger logger = LoggerFactory.getLogger(UnifiedRuleAgent.class);
    private static final String KPI_PROMPT_KEY = "unified_kpi_matching_prompt";
    private static final String IF_PROMPT_KEY = "unified_if_condition_prompt";

    private final ChatLanguageModel lang4jService;
    private final SupabaseService supabaseService;
    private final ObjectMapper objectMapper;
    private CompiledGraph<UnifiedState> compiledGraph;

    public static class UnifiedState extends AgentState {
        public UnifiedState(Map<String, Object> initData) {
            super(new HashMap<>(initData));
        }

        @SuppressWarnings("unchecked")
        public RuleTree<NodeData> getTree() {
            return (RuleTree<NodeData>) this.data().get("tree");
        }

        public boolean isFailed() {
            return (boolean) this.data().getOrDefault("failed", false);
        }
    }

    public UnifiedRuleAgent(ChatLanguageModel lang4jService) {
        this.lang4jService = lang4jService;
        this.supabaseService = new SupabaseService();
        this.objectMapper = new ObjectMapper();
        compile();
    }

    private void compile() {
        try {
            StateGraph<UnifiedState> graph = new StateGraph<>(UnifiedState::new);
            graph.addNode("unified_process", this::unifiedProcessNode);
            graph.addEdge(START, "unified_process");
            graph.addEdge("unified_process", END);
            this.compiledGraph = graph.compile();
        } catch (Exception e) {
            logger.error("Failed to compile UnifiedRuleAgent", e);
            throw new RuntimeException(e);
        }
    }

    private CompletableFuture<Map<String, Object>> unifiedProcessNode(UnifiedState state) {
        logger.info("UnifiedRuleAgent: Processing node...");
        RuleTree<NodeData> tree = state.getTree();
        if (tree == null) {
            return CompletableFuture.completedFuture(Map.of("failed", true));
        }

        try {
            processTree(tree);
        } catch (Exception e) {
            logger.error("Error in Unified Rule Agent", e);
            return CompletableFuture.completedFuture(Map.of("failed", true));
        }
        return CompletableFuture.completedFuture(Map.of("tree", tree));
    }

    private void processTree(RuleTree<NodeData> tree) {
        if (tree == null || tree.getRoot() == null)
            return;
        traverseAndProcess(tree.getRoot());
    }

    private void traverseAndProcess(RuleNode<NodeData> node) {
        if (node == null)
            return;

        // Find "segments" node (output of RuleConverterAgent)
        if ("segments".equalsIgnoreCase(node.getData().getType())) {
            processSegmentNode(node);
        }

        // Recursively process children
        // Use a copy to avoid ConcurrentModificationException if tree is modified
        if (node.getChildren() != null) {
            for (RuleNode<NodeData> child : new java.util.ArrayList<>(node.getChildren())) {
                traverseAndProcess(child);
            }
        }
    }

    private void processSegmentNode(RuleNode<NodeData> node) {
        String segmentsRaw = node.getData().getInput(); // Newline separated string
        logger.info("Processing segments: {}", segmentsRaw);

        // Fetch context
        String context = supabaseService.fetchDocument();

        // Step 1: KPI Matching
        List<String> matchedKpis = executeKpiMatching(segmentsRaw, context);

        // Step 2: IF Condition Generation
        String ifCondition = executeIfGeneration(node.getData().getInput(), context, matchedKpis); // Note: Prompt asks
                                                                                                   // for
                                                                                                   // CONDITION_JSON,
                                                                                                   // using raw text for
                                                                                                   // now or split

        // Update Tree: Add IF Node and restructure
        updateTree(node, ifCondition, matchedKpis);
    }

    private List<String> executeKpiMatching(String segments, String context) {
        try {
            String promptTemplate = PromptRegistry.getInstance().get(KPI_PROMPT_KEY);
            String prompt = promptTemplate.replace("{{ $json.segments }}", segments)
                    .replace("{{ $json.context }}", context);

            // Rate limit protection: 12-second delay
            try {
                Thread.sleep(12000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            String response = lang4jService.generate(prompt);
            response = cleanJson(response);

            return objectMapper.readValue(response, new TypeReference<List<String>>() {
            });
        } catch (Exception e) {
            logger.error("KPI Matching failed", e);
            return Collections.emptyList();
        }
    }

    private String executeIfGeneration(String originalText, String context, List<String> matchedKpis) {
        try {
            // Prompt inputs: CONDITION_JSON, CONTEXT_STR, ORIGINAL_STATEMENT
            // The prompt says "Read CONDITION_JSON".
            // We pass the list of extracted segments as JSON array to CONDITION_JSON
            // But wait, segmentsRaw is a string. RuleConverter outputs a single string for
            // "segments" node input?
            // RuleConverter output: "segments": [ "...", "..." ] -> Joined by "\n" in
            // addChildrenToNode.
            // So node.getData().getInput() is a newline separated string.
            // I should convert it back to a JSON list or just pass it as is if the prompt
            // handles it?
            // The prompt {{ $json.conditions }} expects a list. I'll split by newline.

            String[] conditionsArray = originalText.split("\n");
            String conditionsJson = objectMapper.writeValueAsString(conditionsArray);

            String promptTemplate = PromptRegistry.getInstance().get(IF_PROMPT_KEY);
            String prompt = promptTemplate.replace("{{ $json.conditions }}", conditionsJson)
                    .replace("{{ $json.context }}", context)
                    .replace("{{ $json.input_text }}", originalText);

            // Rate limit protection: 12-second delay
            try {
                Thread.sleep(12000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            String response = lang4jService.generate(prompt);
            // Clean markdown if present, though prompt says "Return ONLY one line"
            return cleanJson(response).replace("```", "").trim();
        } catch (Exception e) {
            logger.error("IF Generation failed", e);
            return "if (error)";
        }
    }

    private void updateTree(RuleNode<NodeData> segmentNode, String ifCondition, List<String> matchedKpis) {
        // Create IF Node
        NodeData ifNodeData = new NodeData("IF_Condition", "", "", segmentNode.getData().getModelName(), "",
                ifCondition);
        RuleNode<NodeData> ifNode = new RuleNode<>(ifNodeData);

        // Add Matched KPIs as metadata or separate node?
        // User didn't specify, but helpful for debugging.
        // Let's add them as a "KPIs" node under IF node? Or just logs?
        // Let's stick to IF node as the main logic.

        // Restructure: Move Actions from Segment node (siblings?) No, Actions were
        // children of Segment?
        // Wait, RuleConverter adds "segments" and "Action" as children of ROOT (or
        // parent).
        // Let's check RuleConverterAgent again.
        // RuleConverterAgent.java:168: parent.addChild(new RuleNode<>(segments));
        // RuleConverterAgent.java:174: parent.addChild(new RuleNode<>(Action));
        // So they are SIBLINGS. `segmentNode` is the "segments" node.
        // The "Action" node is a sibling of `segmentNode`.
        // I need to find the Action node and move it under the IF node.

        RuleNode<NodeData> parent = segmentNode.getParent();
        if (parent != null) {
            RuleNode<NodeData> actionNode = null;
            // Find Action sibling
            for (RuleNode<NodeData> child : parent.getChildren()) {
                if ("Action".equalsIgnoreCase(child.getData().getType())) {
                    actionNode = child;
                    break;
                }
            }

            // Modify structure:
            // Parent -> Segments Node -> IF Node
            // Parent -> Action Node (Unchanged)

            // Attach IF Node to Segment Node
            segmentNode.addChild(ifNode);

            // Do NOT remove Segment Node. It stays.
            // Action Node stays (Sibling of Segments)
            // No changes needed for actionNode.
        }
    }

    private String cleanJson(String response) {
        // Basic cleanup
        if (response.contains("```json")) {
            response = response.replace("```json", "").replace("```", "");
        } else if (response.contains("```")) {
            response = response.replace("```", "");
        }
        return response.trim();
    }

    public CompletableFuture<UnifiedState> execute(RuleTree<NodeData> tree) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Map<String, Object> input = new HashMap<>();
                input.put("tree", tree);
                return compiledGraph.invoke(input).orElse(null);
            } catch (Exception e) {
                logger.error("Error executing UnifiedRuleAgent", e);
                throw new RuntimeException(e);
            }
        });
    }
}
