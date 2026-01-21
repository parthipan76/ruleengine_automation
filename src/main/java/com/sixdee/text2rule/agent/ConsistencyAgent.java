package com.sixdee.text2rule.agent;

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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.bsc.langgraph4j.StateGraph.END;

public class ConsistencyAgent {
    private static final Logger logger = LoggerFactory.getLogger(ConsistencyAgent.class);
    private static final String PROMPT_KEY = "consistency_check_prompt";

    private final ChatLanguageModel lang4jService;

    private CompiledGraph<ConsistencyState> compiledGraph;

    public static class ConsistencyState extends AgentState {
        public ConsistencyState(Map<String, Object> initData) {
            super(new HashMap<>(initData));
        }

        @SuppressWarnings("unchecked")
        public RuleTree<NodeData> getTree() {
            return (RuleTree<NodeData>) this.data().get("tree");
        }

        public String getCheckType() {
            return (String) this.data().getOrDefault("checkType", "root");
        }

        public Double getConsistencyScore() {
            return (Double) this.data().get("consistencyScore");
        }

        public String getTraceId() {
            return (String) this.data().get("traceId");
        }
    }

    public ConsistencyAgent(ChatLanguageModel lang4jService) {
        this.lang4jService = lang4jService;
        compile();
    }

    private void compile() {
        try {
            StateGraph<ConsistencyState> graph = new StateGraph<>(ConsistencyState::new);
            graph.addNode("check_consistency", this::checkConsistencyNode);
            graph.addEdge(org.bsc.langgraph4j.StateGraph.START, "check_consistency");
            graph.addEdge("check_consistency", END);
            this.compiledGraph = graph.compile();
        } catch (Exception e) {
            logger.error("Failed to compile ConsistencyAgent graph", e);
            throw new RuntimeException(e);
        }
    }

    private CompletableFuture<Map<String, Object>> checkConsistencyNode(ConsistencyState state) {
        logger.info("ConsistencyAgent: Starting consistency check...");
        RuleTree<NodeData> tree = state.getTree();
        String checkType = state.getCheckType();
        String traceId = state.getTraceId();

        if (tree == null || tree.getRoot() == null) {
            logger.warn("Tree is empty. Skipping consistency check.");
            return CompletableFuture.completedFuture(Map.of("consistencyScore", 0.0));
        }

        try {
            Double score;
            if ("condition".equals(checkType)) {
                score = checkConditionConsistency(tree, traceId);
            } else if ("schedule".equals(checkType)) {
                score = checkScheduleConsistency(tree, traceId);
            } else if ("action".equals(checkType)) {
                score = checkActionConsistency(tree, traceId);
            } else if ("rule_converter".equals(checkType)) {
                score = checkRuleConverterConsistency(tree, traceId);
            } else if ("unified_rule".equals(checkType)) {
                score = checkUnifiedRuleConsistency(tree, traceId);
            } else {
                score = checkRootConsistency(tree, traceId);
            }

            if (score == null)
                score = 0.0;

            return CompletableFuture.completedFuture(Map.of("tree", tree, "consistencyScore", score));
        } catch (Exception e) {
            logger.error("Error during consistency check", e);
            return CompletableFuture.completedFuture(Map.of("consistencyScore", 0.0));
        }
    }

    private Double checkRootConsistency(RuleTree<NodeData> tree, String traceId) {
        RuleNode<NodeData> root = tree.getRoot();
        String originalText = root.getData().getInput();
        List<String> childrenTexts = collectChildrenTexts(root);
        String childrenCombined = String.join("\n", childrenTexts);

        Double score = calculateConsistencyScore(originalText, childrenCombined, traceId);

        if (score != null) {
            root.getData().setSimilarityScore(score);

            // Get threshold from config
            String thresholdStr = PromptRegistry.getInstance().getAttribute(PROMPT_KEY, "consistency_threshold");
            double threshold = (thresholdStr != null) ? Double.parseDouble(thresholdStr) : 0.8;

            if (score >= threshold) {
                logger.info("✓ Root Consistency Check: PASSED (score={})", score);
            } else {
                logger.warn("✗ Root Consistency Check: FAILED (score={}, threshold={})", score, threshold);
                logger.warn("  Original Text: {}", originalText);
                logger.warn("  Children Combined: {}", childrenCombined);
            }
            return score;
        } else {
            logger.warn("Failed to parse similarity_score from response.");
            return 0.0;
        }
    }

    private Double checkConditionConsistency(RuleTree<NodeData> tree, String traceId) {
        if (tree == null || tree.getRoot() == null)
            return 1.0;
        return checkConditionConsistencyRecursive(tree.getRoot(), traceId);
    }

    private Double checkConditionConsistencyRecursive(RuleNode<NodeData> node, String traceId) {
        Double minScore = 1.0;

        // Check NormalStatements nodes that have Condition children
        if ("NormalStatements".equalsIgnoreCase(node.getData().getType()) && !node.getChildren().isEmpty()) {
            // ... (check logic)
            logger.info("Checking consistency for Segments of NormalStatements node...");
            String originalText = node.getData().getInput();

            // Collect Condition texts
            List<String> segmentTexts = new ArrayList<>();
            for (RuleNode<NodeData> child : node.getChildren()) {
                if ("Segment".equalsIgnoreCase(child.getData().getType())) {
                    segmentTexts.add(child.getData().getInput());
                }
            }

            if (!segmentTexts.isEmpty()) {
                String childrenCombined = String.join("\n", segmentTexts);
                Double score = performConsistencyCheck(node, originalText, childrenCombined, traceId);
                if (score != null && score < minScore) {
                    minScore = score;
                }
            } else {
                logger.warn("No Condition children found for NormalStatements node");
            }
        }

        for (RuleNode<NodeData> child : node.getChildren()) {
            Double childScore = checkConditionConsistencyRecursive(child, traceId);
            if (childScore < minScore) {
                minScore = childScore;
            }
        }
        return minScore;
    }

    private Double checkScheduleConsistency(RuleTree<NodeData> tree, String traceId) {
        if (tree == null || tree.getRoot() == null)
            return 1.0;

        RuleNode<NodeData> root = tree.getRoot();
        // originalText should be the Text of the Schedule Node(s), NOT the Root Input
        List<String> originalScheduleTexts = new ArrayList<>();
        List<String> derivedScheduleDetails = new ArrayList<>();

        // Find Schedule nodes and their extraction results
        for (RuleNode<NodeData> child : root.getChildren()) {
            if ("Schedule".equalsIgnoreCase(child.getData().getType())) {
                originalScheduleTexts.add(child.getData().getInput());

                // The extraction agent adds a child node to the Schedule node with results
                if (child.getChildren() != null) {
                    for (RuleNode<NodeData> grandChild : child.getChildren()) {
                        // In ScheduleExtractionAgent.java: NodeData extractedData = new
                        // NodeData("ScheduleDetails", ...)
                        if ("ScheduleDetails".equalsIgnoreCase(grandChild.getData().getType())) {
                            derivedScheduleDetails.add(grandChild.getData().getInput());
                        }
                    }
                }
            }
        }

        if (!originalScheduleTexts.isEmpty()) {
            logger.info("Checking Schedule Consistency...");
            String originalCombined = String.join("\n", originalScheduleTexts);

            String derivedCombined;
            if (derivedScheduleDetails.isEmpty()) {
                logger.warn(
                        "Schedule node found but no extracted details (children) found. Using empty string for derived.");
                derivedCombined = "";
            } else {
                derivedCombined = String.join("\n", derivedScheduleDetails);
            }

            Double score = calculateConsistencyScore(originalCombined, derivedCombined, traceId);
            if (score != null) {
                // Set on first schedule node for reference
                for (RuleNode<NodeData> child : root.getChildren()) {
                    if ("Schedule".equalsIgnoreCase(child.getData().getType())) {
                        child.getData().setSimilarityScore(score);
                    }
                }
                handleScoreLogging(score, originalCombined, derivedCombined, "Schedule");
                return score;
            }
        } else {
            logger.warn("No Schedule nodes found to check.");
        }
        return 1.0;
    }

    private Double checkActionConsistency(RuleTree<NodeData> tree, String traceId) {
        if (tree == null || tree.getRoot() == null)
            return 1.0;

        // Find Action nodes and their ActionDetails children
        RuleNode<NodeData> root = tree.getRoot();
        return checkActionConsistencyRecursive(root, traceId);
    }

    private Double checkActionConsistencyRecursive(RuleNode<NodeData> node, String traceId) {
        Double minScore = 1.0;

        if ("Action".equalsIgnoreCase(node.getData().getType())) {
            String originalText = node.getData().getInput();
            String derivedText = "";

            if (node.getChildren() != null) {
                for (RuleNode<NodeData> child : node.getChildren()) {
                    if ("ActionDetails".equalsIgnoreCase(child.getData().getType())) {
                        derivedText = child.getData().getInput();
                        break;
                    }
                }
            }

            if (!derivedText.isEmpty()) {
                logger.info("Checking Action Consistency...");
                Double score = calculateConsistencyScore(originalText, derivedText, traceId);
                if (score != null) {
                    node.getData().setSimilarityScore(score);
                    handleScoreLogging(score, originalText, derivedText, "Action");
                    if (score < minScore)
                        minScore = score;
                }
            } else {
                logger.warn("Action node found but no ActionDetails child. Skipping check.");
            }
        }

        if (node.getChildren() != null) {
            for (RuleNode<NodeData> child : node.getChildren()) {
                Double childScore = checkActionConsistencyRecursive(child, traceId);
                if (childScore < minScore)
                    minScore = childScore;
            }
        }
        return minScore;
    }

    private Double checkRuleConverterConsistency(RuleTree<NodeData> tree, String traceId) {
        if (tree == null || tree.getRoot() == null)
            return 1.0;
        return checkRuleConverterRecursive(tree.getRoot(), traceId);
    }

    private Double checkRuleConverterRecursive(RuleNode<NodeData> node, String traceId) {
        Double minScore = 1.0;

        // Rule Converter operates on "Segment" nodes (from ConditionExtraction)
        // It produce children: segments, Action, Policy, Schedule, Sampling
        if ("Segment".equalsIgnoreCase(node.getData().getType())) {
            String originalText = node.getData().getInput();
            List<String> childOutputs = new ArrayList<>();

            if (node.getChildren() != null) {
                for (RuleNode<NodeData> child : node.getChildren()) {
                    // Collect content from the converter outputs
                    String type = child.getData().getType();
                    if ("segments".equalsIgnoreCase(type) || "Action".equalsIgnoreCase(type) ||
                            "Policy".equalsIgnoreCase(type) || "Schedule".equalsIgnoreCase(type) ||
                            "Sampling".equalsIgnoreCase(type)) {
                        childOutputs.add(type + ": " + child.getData().getInput());
                    }
                }
            }

            if (!childOutputs.isEmpty()) {
                String derivedText = String.join("\n", childOutputs);
                logger.info("Checking Rule Converter Consistency...");
                Double score = calculateConsistencyScore(originalText, derivedText, traceId);
                if (score != null) {
                    node.getData().setSimilarityScore(score);
                    handleScoreLogging(score, originalText, derivedText, "RuleConverter");
                    if (score < minScore)
                        minScore = score;
                }
            }
        }

        if (node.getChildren() != null) {
            for (RuleNode<NodeData> child : node.getChildren()) {
                Double childScore = checkRuleConverterRecursive(child, traceId);
                if (childScore < minScore)
                    minScore = childScore;
            }
        }
        return minScore;
    }

    private Double checkUnifiedRuleConsistency(RuleTree<NodeData> tree, String traceId) {
        if (tree == null || tree.getRoot() == null)
            return 1.0;
        return checkUnifiedRuleRecursive(tree.getRoot(), traceId);
    }

    private Double checkUnifiedRuleRecursive(RuleNode<NodeData> node, String traceId) {
        Double minScore = 1.0;

        // Unified Rule operates on "segments" node (output of RuleConverter)
        // It adds "IF_Condition" child
        if ("segments".equalsIgnoreCase(node.getData().getType())) {
            String originalText = node.getData().getInput(); // The list of conditions
            String derivedText = "";

            if (node.getChildren() != null) {
                for (RuleNode<NodeData> child : node.getChildren()) {
                    if ("IF_Condition".equalsIgnoreCase(child.getData().getType())) {
                        derivedText = child.getData().getInput();
                        break;
                    }
                }
            }

            if (!derivedText.isEmpty()) {
                logger.info("Checking Unified Rule Consistency...");
                Double score = calculateConsistencyScore(originalText, derivedText, traceId);
                if (score != null) {
                    node.getData().setSimilarityScore(score);
                    handleScoreLogging(score, originalText, derivedText, "UnifiedRule");
                    if (score < minScore)
                        minScore = score;
                }
            }
        }

        if (node.getChildren() != null) {
            for (RuleNode<NodeData> child : node.getChildren()) {
                Double childScore = checkUnifiedRuleRecursive(child, traceId);
                if (childScore < minScore)
                    minScore = childScore;
            }
        }
        return minScore;
    }

    private Double performConsistencyCheck(RuleNode<NodeData> node, String original, String derived, String traceId) {
        Double score = calculateConsistencyScore(original, derived, traceId);
        if (score != null) {
            node.getData().setSimilarityScore(score);
            // We don't have threshold here easily, just log
            logger.info("  > Consistency Score: {}", score);
            return score;
        }
        return null; // Or 0.0?
    }

    // Helper to consolidate logging
    private void handleScoreLogging(Double score, String original, String derived, String type) {
        String thresholdStr = PromptRegistry.getInstance().getAttribute(PROMPT_KEY, "consistency_threshold");
        double threshold = (thresholdStr != null) ? Double.parseDouble(thresholdStr) : 0.8;

        if (score >= threshold) {
            logger.info("✓ {} Consistency Check: PASSED (score={})", type, score);
        } else {
            logger.warn("✗ {} Consistency Check: FAILED (score={}, threshold={})", type, score, threshold);
        }
    }

    private Double calculateConsistencyScore(String originalText, String childrenCombined, String traceId) {
        logger.info("Calculating consistency score...");
        String responseJson = null;
        String populatedPrompt = null;
        boolean success = false;
        try {
            String promptTemplate = PromptRegistry.getInstance().get(PROMPT_KEY);
            if (promptTemplate == null) {
                logger.error("Consistency prompt not found.");
                return null;
            }

            populatedPrompt = promptTemplate
                    .replace("{{original}}", originalText)
                    .replace("{{children}}", childrenCombined);

            logger.info("ConsistencyAgent: Sending prompt to LLM...");
            // Rate limit protection: 12-second delay
            try {
                Thread.sleep(12000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            responseJson = lang4jService.generate(populatedPrompt);
            logger.info("ConsistencyAgent: Received response from LLM.");

            // Use JsonExtractorTool for robust JSON extraction
            @SuppressWarnings("unchecked")
            Map<String, Object> responseMap = com.sixdee.text2rule.tool.JsonExtractorTool.extractAndParse(responseJson,
                    Map.class);

            if (responseMap == null) {
                logger.warn("No JSON found or parsing failed in consistency response: {}", responseJson);
                return null;
            }
            Object scoreObj = responseMap.get("similarity_score");
            if (scoreObj instanceof Number) {
                Double score = ((Number) scoreObj).doubleValue();
                logger.info("Calculated consistency score: {}", score);
                success = true;

                // Comprehensive Logging on Failure
                String thresholdStr = PromptRegistry.getInstance().getAttribute(PROMPT_KEY, "consistency_threshold");
                double threshold = (thresholdStr != null) ? Double.parseDouble(thresholdStr) : 0.8;

                if (score < threshold) {
                    logger.error("!!! CONSISTENCY CHECK FAILED !!! (Score: {} < Threshold: {})", score, threshold);
                    logger.error("--- PARENT (Original) TEXT ---\n{}\n-----------------------------", originalText);
                    logger.error("--- CHILD (Derived) TEXT ---\n{}\n----------------------------", childrenCombined);
                    logger.error("--- FULL PROMPT USED ---\n{}\n------------------------", populatedPrompt);
                }

                return score;
            }
        } catch (Exception e) {
            logger.error("Error calculating consistency score", e);
        } finally {
            // Observability: Capture Event in Finally
            java.util.List<java.util.Map<String, String>> messages = new java.util.ArrayList<>();
            java.util.Map<String, String> userMessage = new java.util.HashMap<>();
            userMessage.put("role", "user");
            userMessage.put("content", populatedPrompt);
            messages.add(userMessage);

            com.sixdee.text2rule.observability.IntegrationFactory.getInstance().recordEvent(
                    traceId,
                    "ConsistencyAgent",
                    messages,
                    responseJson,
                    "Consistency Check Performed",
                    "agent-model",
                    java.util.Collections.emptyMap(),
                    java.util.Collections.emptyMap());
        }
        return null;
    }

    private List<String> collectChildrenTexts(RuleNode<NodeData> node) {
        List<String> texts = new ArrayList<>();
        if (node.getChildren().isEmpty()) {
            return texts;
        }

        for (RuleNode<NodeData> child : node.getChildren()) {
            texts.add(child.getData().getInput());
        }
        return texts;
    }

    public CompletableFuture<ConsistencyState> execute(RuleTree<NodeData> tree, String checkType, String traceId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Map<String, Object> input = new HashMap<>();
                input.put("tree", tree);
                input.put("checkType", checkType);
                input.put("traceId", traceId != null ? traceId : java.util.UUID.randomUUID().toString());
                return compiledGraph.invoke(input).orElse(null);
            } catch (Exception e) {
                logger.error("Error executing ConsistencyAgent", e);
                throw new RuntimeException(e);
            }
        });
    }

    public CompletableFuture<ConsistencyState> execute(RuleTree<NodeData> tree, String checkType) {
        return execute(tree, checkType, null);
    }

    public CompletableFuture<ConsistencyState> execute(RuleTree<NodeData> tree) {
        return execute(tree, "root", null);
    }
}
