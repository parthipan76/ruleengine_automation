package com.sixdee.text2rule.agent;

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

        public String getCustomPrompt() {
            return (String) this.data().get("customPrompt");
        }

        public String getTraceId() {
            return (String) this.data().get("traceId");
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
        logger.info("UnifiedRuleAgent: Starting processing...");
        RuleTree<NodeData> tree = state.getTree();
        if (tree == null) {
            return CompletableFuture.completedFuture(Map.of("failed", true));
        }

        String customPrompt = state.getCustomPrompt();

        try {
            processTree(tree, customPrompt, state.getTraceId());
        } catch (Exception e) {
            logger.error("Error in Unified Rule Agent", e);
            throw new RuntimeException("Unified Rule Processing Failed", e);
        }
        return CompletableFuture.completedFuture(Map.of("tree", tree));
    }

    private void processTree(RuleTree<NodeData> tree, String customPrompt, String traceId) {
        if (tree == null || tree.getRoot() == null)
            return;
        traverseAndProcess(tree.getRoot(), customPrompt, traceId);
    }

    private void traverseAndProcess(RuleNode<NodeData> node, String customPrompt, String traceId) {
        if (node == null)
            return;

        // Find "segments" node (output of RuleConverterAgent)
        if ("segments".equalsIgnoreCase(node.getData().getType())) {
            processSegmentNode(node, customPrompt, traceId);
        }

        // Recursively process children
        // Use a copy to avoid ConcurrentModificationException if tree is modified
        if (node.getChildren() != null) {
            for (RuleNode<NodeData> child : new java.util.ArrayList<>(node.getChildren())) {
                traverseAndProcess(child, customPrompt, traceId);
            }
        }
    }

    private void processSegmentNode(RuleNode<NodeData> node, String customPrompt, String traceId) {
        String segmentsRaw = node.getData().getInput(); // Newline separated string
        logger.info("Processing segments: {}", segmentsRaw);

        // Fetch context
        String context = supabaseService.fetchDocument();

        // Step 1: KPI Matching
        List<String> matchedKpis = executeKpiMatching(segmentsRaw, context, traceId);

        // Step 2: IF Condition Generation
        String ifCondition = executeIfGeneration(node.getData().getInput(), context, matchedKpis, customPrompt,
                traceId); // Note:
        // Prompt
        // asks

        // Update Tree: Add IF Node and restructure
        updateTree(node, ifCondition, matchedKpis);
    }

    private List<String> executeKpiMatching(String segments, String context, String traceId) {
        String response = null;
        boolean success = false;
        String prompt = null;
        String jsonResponse = null;

        try {
            logger.info("UnifiedRuleAgent: Matching KPIs via LLM...");
            String promptTemplate = PromptRegistry.getInstance().get(KPI_PROMPT_KEY);
            prompt = promptTemplate.replace("{{ $json.segments }}", segments)
                    .replace("{{ $json.context }}", context);

            // Rate limit protection: 12-second delay
            // Rate limit
            com.sixdee.text2rule.util.RateLimiter.getInstance().apply();
            response = lang4jService.generate(prompt);
            jsonResponse = cleanJson(response);

            List<String> result = objectMapper.readValue(jsonResponse, new TypeReference<List<String>>() {
            });
            success = true;
            return result;
        } catch (Exception e) {
            logger.error("KPI Matching failed", e);
            response = "Error: " + e.getMessage();
            jsonResponse = response; // Ensure jsonResponse is set for finally block
            throw new RuntimeException("KPI Matching Failed", e);
        } finally {
            // Observability
            java.util.List<java.util.Map<String, String>> messages = new java.util.ArrayList<>();
            java.util.Map<String, String> userMessage = new java.util.HashMap<>();
            userMessage.put("role", "user");
            userMessage.put("content", prompt);
            messages.add(userMessage);

            com.sixdee.text2rule.observability.IntegrationFactory.getInstance().recordEvent(
                    traceId,
                    "UnifiedRuleAgent-KPI",
                    messages,
                    jsonResponse,
                    success ? "KPI Matching Successful" : "KPI Matching Failed",
                    "agent-model",
                    java.util.Collections.emptyMap(),
                    java.util.Collections.emptyMap());
        }
    }

    private String executeIfGeneration(String originalText, String context, List<String> matchedKpis,
            String customPrompt, String traceId) {
        String cleanedResponse = null;
        boolean success = false;
        String prompt = null;

        try {
            logger.info("UnifiedRuleAgent: Generating IF condition via LLM...");
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
            // I'll split by newline.

            String[] conditionsArray = originalText.split("\n");
            String conditionsJson = objectMapper.writeValueAsString(conditionsArray);

            String promptTemplate;
            if (customPrompt != null && !customPrompt.trim().isEmpty()) {
                promptTemplate = customPrompt;
            } else {
                promptTemplate = PromptRegistry.getInstance().get(IF_PROMPT_KEY);
            }

            prompt = promptTemplate.replace("{{ $json.conditions }}", conditionsJson)
                    .replace("{{ $json.context }}", context)
                    .replace("{{ $json.input_text }}", originalText);

            // Rate limit protection: 12-second delay
            // Rate limit
            com.sixdee.text2rule.util.RateLimiter.getInstance().apply();
            String response = lang4jService.generate(prompt);
            // Clean markdown if present, though prompt says "Return ONLY one line"
            cleanedResponse = cleanJson(response).replace("```", "").trim();

            success = true;
            return cleanedResponse;
        } catch (Exception e) {
            logger.error("IF Generation failed", e);
            cleanedResponse = "Error: " + e.getMessage();
            throw new RuntimeException("IF Generation Failed", e);
        } finally {
            // Observability
            java.util.List<java.util.Map<String, String>> messages = new java.util.ArrayList<>();
            java.util.Map<String, String> userMessage = new java.util.HashMap<>();
            userMessage.put("role", "user");
            userMessage.put("content", prompt); // Use 'prompt' here
            messages.add(userMessage);

            com.sixdee.text2rule.observability.IntegrationFactory.getInstance().recordEvent(
                    traceId,
                    "UnifiedRuleAgent-IfGen",
                    messages,
                    cleanedResponse, // Use 'cleanedResponse' here
                    success ? "If Generation Successful" : "If Generation Failed",
                    "agent-model",
                    java.util.Collections.emptyMap(),
                    java.util.Collections.emptyMap());
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
            // Find Action sibling
            for (RuleNode<NodeData> child : parent.getChildren()) {
                if ("Action".equalsIgnoreCase(child.getData().getType())) {
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
        return execute(tree, null);
    }

    public CompletableFuture<UnifiedState> execute(RuleTree<NodeData> tree, String customPrompt) {
        return execute(tree, customPrompt, null);
    }

    public CompletableFuture<UnifiedState> execute(RuleTree<NodeData> tree, String customPrompt, String traceId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Map<String, Object> input = new HashMap<>();
                input.put("tree", tree);
                input.put("traceId", traceId != null ? traceId : java.util.UUID.randomUUID().toString());
                if (customPrompt != null) {
                    input.put("customPrompt", customPrompt);
                }
                return compiledGraph.invoke(input).orElse(null);
            } catch (Exception e) {
                logger.error("Error executing UnifiedRuleAgent", e);
                throw new RuntimeException(e);
            }
        });
    }
}
