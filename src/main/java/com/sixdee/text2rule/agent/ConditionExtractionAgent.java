package com.sixdee.text2rule.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sixdee.text2rule.config.PromptRegistry;
import com.sixdee.text2rule.dto.ExtractionResult;
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

/**
 * ConditionExtractionAgent extracts conditions from NormalStatements nodes.
 * Simplified version - no internal retry logic (handled by workflow).
 */
public class ConditionExtractionAgent {
    private static final Logger logger = LoggerFactory.getLogger(ConditionExtractionAgent.class);
    private static final String DEFAULT_PROMPT_KEY = "condition_extraction_prompt";

    private final ChatLanguageModel lang4jService;
    private final ObjectMapper objectMapper;
    private CompiledGraph<ConditionState> compiledGraph;

    public static class ConditionState extends AgentState {
        public ConditionState(Map<String, Object> initData) {
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

    public ConditionExtractionAgent(ChatLanguageModel lang4jService) {
        this.lang4jService = lang4jService;
        this.objectMapper = new ObjectMapper();
        compile();
    }

    private void compile() {
        try {
            StateGraph<ConditionState> graph = new StateGraph<>(ConditionState::new);
            graph.addNode("extract", this::extractNode);
            graph.addEdge(START, "extract");
            graph.addEdge("extract", END);
            this.compiledGraph = graph.compile();
        } catch (Exception e) {
            logger.error("Failed to compile ConditionExtractionAgent", e);
            throw new RuntimeException(e);
        }
    }

    private CompletableFuture<Map<String, Object>> extractNode(ConditionState state) {
        logger.info("ConditionExtractionAgent: Executing Extract Node...");
        RuleTree<NodeData> tree = state.getTree();
        if (tree == null) {
            return CompletableFuture.completedFuture(Map.of("failed", true));
        }

        String customPromptKey = (String) state.data().get("customPromptKey");
        String customPromptString = (String) state.data().get("customPromptString");

        try {
            extractConditions(tree, customPromptKey, customPromptString);
        } catch (Exception e) {
            logger.error("Error in extraction node", e);
            return CompletableFuture.completedFuture(Map.of("failed", true));
        }
        return CompletableFuture.completedFuture(Map.of("tree", tree));
    }

    private void extractConditions(RuleTree<NodeData> tree, String customPromptKey, String customPromptString) {
        if (tree == null || tree.getRoot() == null)
            return;
        processNode(tree.getRoot(), customPromptKey, customPromptString);
    }

    private void processNode(RuleNode<NodeData> node, String customPromptKey, String customPromptString) {
        if (node == null)
            return;

        // Check if this is a NormalStatements node
        if ("NormalStatements".equalsIgnoreCase(node.getData().getType())) {
            // Clear existing children to support retry mechanism
            node.getChildren().clear();

            String conditionText = node.getData().getInput();
            logger.info("Extracting conditions for NormalStatements node: {}", conditionText);

            try {
                String prompt;
                if (customPromptString != null && !customPromptString.trim().isEmpty()) {
                    prompt = customPromptString
                            .replace("{{ $json['output.normal_statements'] }}", conditionText)
                            .replace("{{ $json.input_text }}", conditionText);
                    logger.info("Using provided custom prompt string");
                } else {
                    // Use custom prompt key if provided, otherwise use default
                    String promptKey = (customPromptKey != null && !customPromptKey.trim().isEmpty())
                            ? customPromptKey
                            : DEFAULT_PROMPT_KEY;

                    String promptTemplate = PromptRegistry.getInstance().get(promptKey);
                    prompt = promptTemplate
                            .replace("{{ $json['output.normal_statements'] }}", conditionText)
                            .replace("{{ $json.input_text }}", conditionText);
                }

                String jsonResponse = lang4jService.generate(prompt);

                // Robust JSON List extraction
                int startIndex = jsonResponse.indexOf("[");
                int endIndex = jsonResponse.lastIndexOf("]");

                if (startIndex != -1 && endIndex != -1 && endIndex > startIndex) {
                    jsonResponse = jsonResponse.substring(startIndex, endIndex + 1);
                } else {
                    if (jsonResponse.startsWith("```json")) {
                        jsonResponse = jsonResponse.replace("```json", "").replace("```", "").trim();
                    }
                }

                List<ExtractionResult> conditions = objectMapper.readValue(jsonResponse,
                        new TypeReference<List<ExtractionResult>>() {
                        });

                if (conditions != null && !conditions.isEmpty()) {
                    logger.info("Found {} conditions. Adding as children.", conditions.size());

                    for (ExtractionResult segment : conditions) {
                        String childInput;
                        if (segment.getRule() != null && !segment.getRule().trim().isEmpty()) {
                            childInput = segment.getRule();
                        } else {
                            // Fallback for backward compatibility or if rule is missing
                            childInput = "Condition: " + segment.getCondition() + " -> Action: "
                                    + segment.getActions();
                        }

                        NodeData conditionNode = new NodeData("Segment", "", "", node.getData().getModelName(), "",
                                childInput);
                        node.addChild(new RuleNode<>(conditionNode));
                    }

                } else {
                    logger.info("No conditions extracted from NormalStatements node.");
                }

            } catch (JsonProcessingException e) {
                logger.error("Failed to parse extraction response", e);
            } catch (Exception e) {
                logger.error("Error during condition extraction", e);
            }
        }

        // Recursively process children
        if (node.getChildren() != null) {
            for (RuleNode<NodeData> child : node.getChildren()) {
                processNode(child, customPromptKey, customPromptString);
            }
        }
    }

    public CompletableFuture<ConditionState> execute(RuleTree<NodeData> tree) {
        return execute(tree, null, null);
    }

    public CompletableFuture<ConditionState> execute(RuleTree<NodeData> tree, String customPromptKey) {
        return execute(tree, customPromptKey, null);
    }

    public CompletableFuture<ConditionState> execute(RuleTree<NodeData> tree, String customPromptKey,
            String customPromptString) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Map<String, Object> input = new HashMap<>();
                input.put("tree", tree);
                if (customPromptKey != null && !customPromptKey.trim().isEmpty()) {
                    input.put("customPromptKey", customPromptKey);
                }
                if (customPromptString != null && !customPromptString.trim().isEmpty()) {
                    input.put("customPromptString", customPromptString);
                }
                return compiledGraph.invoke(input).orElse(null);
            } catch (Exception e) {
                logger.error("Error executing ConditionExtractionAgent", e);
                throw new RuntimeException(e);
            }
        });
    }
}
