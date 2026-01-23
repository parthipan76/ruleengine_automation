package com.sixdee.text2rule.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sixdee.text2rule.config.PromptRegistry;
import com.sixdee.text2rule.dto.RuleConverterResult;
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

public class RuleConverterAgent {
    private static final Logger logger = LoggerFactory.getLogger(RuleConverterAgent.class);
    private static final String DEFAULT_PROMPT_KEY = "rule_converter_prompt";

    private final ChatLanguageModel lang4jService;
    private final ObjectMapper objectMapper;
    private CompiledGraph<ConverterState> compiledGraph;

    public static class ConverterState extends AgentState {
        public ConverterState(Map<String, Object> initData) {
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

    public RuleConverterAgent(ChatLanguageModel lang4jService) {
        this.lang4jService = lang4jService;
        this.objectMapper = new ObjectMapper();
        compile();
    }

    private void compile() {
        try {
            StateGraph<ConverterState> graph = new StateGraph<>(ConverterState::new);
            graph.addNode("convert", this::convertNode);
            graph.addEdge(START, "convert");
            graph.addEdge("convert", END);
            this.compiledGraph = graph.compile();
        } catch (Exception e) {
            logger.error("Failed to compile RuleConverterAgent", e);
            throw new RuntimeException(e);
        }
    }

    private CompletableFuture<Map<String, Object>> convertNode(ConverterState state) {
        logger.info("RuleConverterAgent: Starting conversion...");
        RuleTree<NodeData> tree = state.getTree();
        if (tree == null) {
            return CompletableFuture.completedFuture(Map.of("failed", true));
        }

        String customPrompt = state.getCustomPrompt();

        try {
            convertRules(tree, customPrompt, state.getTraceId());
        } catch (Exception e) {
            logger.error("Error in rule conversion node", e);
            throw new RuntimeException("Rule Conversion Failed", e);
        }
        return CompletableFuture.completedFuture(Map.of("tree", tree));
    }

    private void convertRules(RuleTree<NodeData> tree, String customPrompt, String traceId) {
        if (tree == null || tree.getRoot() == null)
            return;
        processNode(tree.getRoot(), customPrompt, traceId);
    }

    private void processNode(RuleNode<NodeData> node, String customPrompt, String traceId) {
        if (node == null)
            return;

        // Check if this is a "Segment" node which holds the Rule text
        if ("Segment".equalsIgnoreCase(node.getData().getType())) {
            // Check if we already processed it (e.g. if it has children, maybe skip? or
            // clear?)
            // For now, assuming we process "Segment" nodes.
            node.getChildren().clear(); // Clear existing children if any

            String ruleText = node.getData().getInput();
            logger.info("Converting rule for Segment node: {}", ruleText);

            String jsonResponse = null;
            boolean success = false;
            try {
                String promptTemplate;
                if (customPrompt != null && !customPrompt.trim().isEmpty()) {
                    promptTemplate = customPrompt;
                } else {
                    promptTemplate = PromptRegistry.getInstance().get(DEFAULT_PROMPT_KEY);
                }

                String prompt = promptTemplate.replace("{{ $json['output.normal_statements'] }}", ruleText);
                logger.info("RuleConverterAgent: Sending prompt to LLM...");
                // Rate limit protection: 12-second delay
                // Rate limit
                com.sixdee.text2rule.util.RateLimiter.getInstance().apply();
                jsonResponse = lang4jService.generate(prompt);
                logger.info("RuleConverterAgent: Received response from LLM.");

                // Clean JSON
                int startIndex = jsonResponse.indexOf("{");
                int endIndex = jsonResponse.lastIndexOf("}");
                if (startIndex != -1 && endIndex != -1) {
                    jsonResponse = jsonResponse.substring(startIndex, endIndex + 1);
                } else if (jsonResponse.contains("```json")) {
                    jsonResponse = jsonResponse.replaceAll("```json", "").replaceAll("```", "").trim();
                }

                RuleConverterResult result = objectMapper.readValue(jsonResponse, RuleConverterResult.class);

                if (result != null) {
                    addChildrenToNode(node, result);
                } else {
                    logger.warn("Rule conversion returned null result");
                }

                success = true;

                // Observability: Capture Event (Success)
                java.util.List<java.util.Map<String, String>> messages = new java.util.ArrayList<>();
                java.util.Map<String, String> userMessage = new java.util.HashMap<>();
                userMessage.put("role", "user");
                userMessage.put("content", prompt);
                messages.add(userMessage);

                com.sixdee.text2rule.observability.IntegrationFactory.getInstance().recordEvent(
                        traceId,
                        "RuleConverterAgent",
                        messages,
                        jsonResponse,
                        "Rule Extended",
                        "agent-model",
                        java.util.Collections.emptyMap(),
                        java.util.Collections.emptyMap());

            } catch (JsonProcessingException e) {
                logger.error("Failed to parse rule conversion response", e);
                jsonResponse = "JSON Error: " + e.getMessage();

                // Observability: Capture Event (JSON Error)
                java.util.List<java.util.Map<String, String>> messages = new java.util.ArrayList<>();
                java.util.Map<String, String> userMessage = new java.util.HashMap<>();
                userMessage.put("role", "user");
                userMessage.put("content", ruleText); // Fallback
                messages.add(userMessage);

                com.sixdee.text2rule.observability.IntegrationFactory.getInstance().recordEvent(
                        traceId,
                        "RuleConverterAgent",
                        messages,
                        jsonResponse,
                        "Rule Extension Failed",
                        "agent-model",
                        java.util.Collections.emptyMap(),
                        java.util.Collections.emptyMap());

                throw new RuntimeException("Rule Conversion JSON Parsing Failed", e);

            } catch (Exception e) {
                logger.error("Error during rule conversion", e);
                jsonResponse = "Error: " + e.getMessage();

                // Observability: Capture Event (General Error)
                java.util.List<java.util.Map<String, String>> messages = new java.util.ArrayList<>();
                java.util.Map<String, String> userMessage = new java.util.HashMap<>();
                userMessage.put("role", "user");
                userMessage.put("content", ruleText); // Fallback
                messages.add(userMessage);

                com.sixdee.text2rule.observability.IntegrationFactory.getInstance().recordEvent(
                        traceId,
                        "RuleConverterAgent",
                        messages,
                        jsonResponse,
                        "Rule Extension Failed",
                        "agent-model",
                        java.util.Collections.emptyMap(),
                        java.util.Collections.emptyMap());

                throw new RuntimeException("Rule Conversion Process Failed", e);
            }
        }

        // Recursively process children
        // Note: we just added children. Should we process them? No, we decended from
        // Segment.
        // But if the tree structure was different, we might need to.
        // Since we are iterating the tree, we should be careful about concurrent
        // modification if we use a simple iterator.
        // But here we are recursing. `processNode` calls `processNode` on children.
        // We added children to `node`.
        // DO NOT recurse into the just-added children to avoid infinite loops or
        // reprocessing if they happened to be named "Segment" (unlikely).
        // So we should iterate over original children?
        // Wait, "Segment" nodes were leaf nodes (created by ConditionExtractionAgent).
        // So they shouldn't have children before we start, or if they do (Schedule?),
        // we cleared them.
        // However, we need to traverse down to find the "Segment" nodes.
        // If the node is NOT a Segment, we recurse.
        if (!"Segment".equalsIgnoreCase(node.getData().getType()) && node.getChildren() != null) {
            // Create a copy of the list to iterate if needed, but standard loop is fine if
            // we don't modify the *current* node's children list while iterating it.
            // We are modifying `node`'s children only if `node` is "Segment".
            // If `node` is NOT "Segment", we iterate its children.
            // If `node` is NOT "Segment", we iterate its children.
            for (RuleNode<NodeData> child : node.getChildren()) {
                processNode(child, customPrompt, traceId);
            }
        }
    }

    private void addChildrenToNode(RuleNode<NodeData> parent, RuleConverterResult result) {
        String modelName = parent.getData().getModelName();

        // 1. Add Segments (Conditions)
        // 1. Add Segments (Conditions) as a single node
        if (result.getSegments() != null && !result.getSegments().isEmpty()) {
            String joinedSegments = String.join("\n", result.getSegments());
            NodeData n = new NodeData("segments", "", "", modelName, "", joinedSegments);
            parent.addChild(new RuleNode<>(n));
        }

        // 2. Add Actions
        if (result.getActions() != null && !result.getActions().isEmpty()) {
            NodeData n = new NodeData("Action", "", "", modelName, "", result.getActions());
            parent.addChild(new RuleNode<>(n));
        }

        // 3. Add Policy
        if (result.getPolicy() != null && !result.getPolicy().isEmpty()) {
            NodeData n = new NodeData("Policy", "", "", modelName, "", result.getPolicy());
            parent.addChild(new RuleNode<>(n));
        }

        // 4. Add Schedule
        if (result.getSchedule() != null && !result.getSchedule().isEmpty()) {
            NodeData n = new NodeData("Schedule", "", "", modelName, "", result.getSchedule());
            parent.addChild(new RuleNode<>(n));
        }

        // 5. Add Sampling
        if (result.getSampling() != null && !result.getSampling().isEmpty()) {
            NodeData n = new NodeData("Sampling", "", "", modelName, "", result.getSampling());
            parent.addChild(new RuleNode<>(n));
        }
    }

    public CompletableFuture<ConverterState> execute(RuleTree<NodeData> tree) {
        return execute(tree, null);
    }

    public CompletableFuture<ConverterState> execute(RuleTree<NodeData> tree, String customPrompt) {
        return execute(tree, customPrompt, null);
    }

    public CompletableFuture<ConverterState> execute(RuleTree<NodeData> tree, String customPrompt, String traceId) {
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
                logger.error("Error executing RuleConverterAgent", e);
                throw new RuntimeException(e);
            }
        });
    }
}
