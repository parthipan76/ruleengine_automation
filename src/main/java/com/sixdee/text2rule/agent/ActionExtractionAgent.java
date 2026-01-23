package com.sixdee.text2rule.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sixdee.text2rule.config.PromptRegistry;
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
 * ActionExtractionAgent extracts action details from Segment nodes.
 * Simplified version - no internal retry logic (handled by workflow).
 */
public class ActionExtractionAgent {
    private static final Logger logger = LoggerFactory.getLogger(ActionExtractionAgent.class);
    private static final String DEFAULT_PROMPT_KEY = "action_extraction_prompt";

    private final ChatLanguageModel lang4jService;
    private final ObjectMapper objectMapper;
    private CompiledGraph<ActionState> compiledGraph;

    public static class ActionState extends AgentState {
        public ActionState(Map<String, Object> initData) {
            super(new HashMap<>(initData));
        }

        @SuppressWarnings("unchecked")
        public RuleTree<NodeData> getTree() {
            return (RuleTree<NodeData>) this.data().get("tree");
        }

        public boolean isFailed() {
            return (boolean) this.data().getOrDefault("failed", false);
        }

        public String getTraceId() {
            return (String) this.data().get("traceId");
        }
    }

    public ActionExtractionAgent(ChatLanguageModel lang4jService) {
        this.lang4jService = lang4jService;
        this.objectMapper = new ObjectMapper();
        // Configure ObjectMapper to handle flexible JSON parsing
        this.objectMapper.configure(com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_COMMENTS, true);
        this.objectMapper.configure(com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, true);
        this.objectMapper.configure(com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_SINGLE_QUOTES, true);
        compile();
    }

    private void compile() {
        try {
            StateGraph<ActionState> graph = new StateGraph<>(ActionState::new);
            graph.addNode("extract_action", this::extractNode);
            graph.addEdge(START, "extract_action");
            graph.addEdge("extract_action", END);
            this.compiledGraph = graph.compile();
        } catch (Exception e) {
            logger.error("Failed to compile ActionExtractionAgent", e);
            throw new RuntimeException(e);
        }
    }

    private CompletableFuture<Map<String, Object>> extractNode(ActionState state) {
        logger.info("ActionExtractionAgent: Starting extraction...");
        RuleTree<NodeData> tree = state.getTree();
        if (tree == null)
            return CompletableFuture.completedFuture(Map.of("failed", true));

        String customPromptKey = (String) state.data().get("customPromptKey");

        try {
            extractActions(tree, customPromptKey, state.getTraceId());
        } catch (Exception e) {
            logger.error("Error extracting actions", e);
            throw new RuntimeException("Action Extraction Failed", e);
        }

        return CompletableFuture.completedFuture(Map.of("tree", tree));
    }

    private void extractActions(RuleTree<NodeData> tree, String customPromptKey, String traceId) {
        if (tree == null || tree.getRoot() == null)
            return;
        extractActionsRecursive(tree.getRoot(), customPromptKey, traceId);
    }

    private void extractActionsRecursive(RuleNode<NodeData> node, String customPromptKey, String traceId) {
        // Process "Action" nodes (not "Segment" nodes)
        if ("Action".equalsIgnoreCase(node.getData().getType())) {
            String actionText = node.getData().getInput();
            logger.info("Extracting campaign details for Action: {}", actionText);

            String jsonResponse = null;
            boolean success = false;
            try {
                // Use custom prompt key if provided, otherwise use default
                String promptKey = (customPromptKey != null && !customPromptKey.trim().isEmpty())
                        ? customPromptKey
                        : DEFAULT_PROMPT_KEY;

                String promptTemplate = PromptRegistry.getInstance().get(promptKey);
                String prompt = promptTemplate.replace("{{ $json.action_text }}", actionText);

                logger.info("ActionExtractionAgent: Sending prompt to LLM...");
                // Rate limit
                com.sixdee.text2rule.util.RateLimiter.getInstance().apply();
                jsonResponse = lang4jService.generate(prompt);
                logger.info("ActionExtractionAgent: Received response from LLM.");

                // Robust JSON extraction: Find first '{' and last '}'
                jsonResponse = cleanJson(jsonResponse);

                logger.info("Parsed JSON String: {}", jsonResponse);

                // Parse into ActionExtractionResult DTO
                com.sixdee.text2rule.dto.ActionExtractionResult actionResult = objectMapper.readValue(jsonResponse,
                        com.sixdee.text2rule.dto.ActionExtractionResult.class);

                // Format the extracted data
                String formattedAction = formatActionDetails(actionResult);

                NodeData actionDetailsNode = new NodeData("ActionDetails", "", "", node.getData().getModelName(), "",
                        formattedAction);
                node.addChild(new RuleNode<>(actionDetailsNode));
                logger.info("Added ActionDetails child node");

                success = true;

                // Observability: Capture Event (Success)
                java.util.List<java.util.Map<String, String>> messages = new java.util.ArrayList<>();
                java.util.Map<String, String> userMessage = new java.util.HashMap<>();
                userMessage.put("role", "user");
                userMessage.put("content", prompt);
                messages.add(userMessage);

                com.sixdee.text2rule.observability.IntegrationFactory.getInstance().recordEvent(
                        traceId,
                        "ActionExtractionAgent",
                        messages,
                        jsonResponse,
                        "Action Extracted",
                        "agent-model",
                        java.util.Collections.emptyMap(),
                        java.util.Collections.emptyMap());

            } catch (Exception e) {
                logger.error("Error extracting action details. Using fallback.", e);
                jsonResponse = "Error: " + e.getMessage(); // For observability

                // Observability: Capture Event (Error)
                java.util.List<java.util.Map<String, String>> messages = new java.util.ArrayList<>();
                java.util.Map<String, String> userMessage = new java.util.HashMap<>();
                userMessage.put("role", "user");
                userMessage.put("content", actionText); // Fallback
                messages.add(userMessage);

                com.sixdee.text2rule.observability.IntegrationFactory.getInstance().recordEvent(
                        traceId,
                        "ActionExtractionAgent",
                        messages,
                        jsonResponse,
                        "Action Extraction Failed",
                        "agent-model",
                        java.util.Collections.emptyMap(),
                        java.util.Collections.emptyMap());

                throw new RuntimeException("Action Extraction Failed for node: " + actionText, e);
            }
        }

        if (node.getChildren() != null) {
            for (RuleNode<NodeData> child : node.getChildren()) {
                extractActionsRecursive(child, customPromptKey, traceId);
            }
        }
    }

    private String cleanJson(String response) {
        int startIndex = response.indexOf("{");
        int endIndex = response.lastIndexOf("}");

        if (startIndex != -1 && endIndex != -1 && endIndex > startIndex) {
            return response.substring(startIndex, endIndex + 1);
        } else {
            // Fallback cleanup if braces not found
            if (response.startsWith("```json")) {
                response = response.replace("```json", "").replace("```", "").trim();
            }
            return response;
        }
    }

    private String formatActionDetails(com.sixdee.text2rule.dto.ActionExtractionResult result) {
        if (result == null) {
            return "No action information available";
        }

        StringBuilder sb = new StringBuilder();

        if (result.getActionName() != null) {
            sb.append("Action: ").append(result.getActionName());
        }

        if (result.getChannel() != null) {
            sb.append(", Channel: ").append(result.getChannel());
        }

        if (result.getMessageId() != null) {
            sb.append(", Message ID: ").append(result.getMessageId());
        }

        if (result.getUserType() != null) {
            sb.append(", User Type: ").append(result.getUserType());
        }

        if (result.getTriggerType() != null) {
            sb.append(", Trigger: ").append(result.getTriggerType());
        }

        if (result.getConditions() != null && !result.getConditions().isEmpty()) {
            sb.append(", Conditions: ").append(result.getConditions().size());
            for (com.sixdee.text2rule.dto.ActionExtractionResult.ActionCondition condition : result.getConditions()) {
                if (condition.getBenefits() != null && !condition.getBenefits().isEmpty()) {
                    sb.append(", Benefits: ");
                    for (com.sixdee.text2rule.dto.ActionExtractionResult.Benefit benefit : condition.getBenefits()) {
                        sb.append(benefit.getProduct()).append(" (")
                                .append(benefit.getProductType()).append(")");
                    }
                }
            }
        }

        return sb.toString();
    }

    public CompletableFuture<ActionState> execute(RuleTree<NodeData> tree) {
        return execute(tree, null);
    }

    public CompletableFuture<ActionState> execute(RuleTree<NodeData> tree, String customPromptKey) {
        return execute(tree, customPromptKey, null);
    }

    public CompletableFuture<ActionState> execute(RuleTree<NodeData> tree, String customPromptKey, String traceId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Map<String, Object> input = new HashMap<>();
                input.put("tree", tree);
                input.put("traceId", traceId != null ? traceId : java.util.UUID.randomUUID().toString());
                if (customPromptKey != null && !customPromptKey.trim().isEmpty()) {
                    input.put("customPromptKey", customPromptKey);
                    logger.info("Using custom prompt key for action extraction");
                }
                return compiledGraph.invoke(input).orElse(null);
            } catch (Exception e) {
                logger.error("Error executing ActionExtractionAgent", e);
                throw new RuntimeException(e);
            }
        });
    }
}
