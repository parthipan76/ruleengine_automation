package com.sixdee.text2rule.workflow;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphRepresentation;
import org.bsc.langgraph4j.StateGraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sixdee.text2rule.agent.ActionExtractionAgent;
import com.sixdee.text2rule.agent.ConditionExtractionAgent;
import com.sixdee.text2rule.agent.ConsistencyAgent;
import com.sixdee.text2rule.agent.DecompositionAgent;
import com.sixdee.text2rule.agent.PromptRefinementAgent;
import com.sixdee.text2rule.agent.ValidationAgent;
import com.sixdee.text2rule.config.PromptRegistry;
import com.sixdee.text2rule.dto.DecompositionResult;
import com.sixdee.text2rule.model.NodeData;
import com.sixdee.text2rule.model.RuleNode;
import com.sixdee.text2rule.model.RuleTree;
import com.sixdee.text2rule.view.AsciiRenderer;

import dev.langchain4j.model.chat.ChatLanguageModel;

public class AgenticConversionWorkflow {
    private static final Logger logger = LoggerFactory.getLogger(AgenticConversionWorkflow.class);
    private static final String CONSISTENCY_PROMPT_KEY = "consistency_check_prompt";
    private static final String DECOMPOSITION_PROMPT_KEY = "statement_decompostion_agent_prompt";

    private final ValidationAgent validationAgent;
    private final DecompositionAgent decompositionAgent;
    private final ConsistencyAgent consistencyAgent;
    private final PromptRefinementAgent promptRefinementAgent;
    private final ConditionExtractionAgent conditionAgent;
    private final ActionExtractionAgent actionAgent;
    private final AsciiRenderer asciiRenderer;
    private final ObjectMapper objectMapper;
    private final double consistencyThreshold;
    private final int maxRetries;
    private CompiledGraph<WorkflowState> compiledGraph;

    public AgenticConversionWorkflow(ChatLanguageModel lang4jService) {
        this.validationAgent = new ValidationAgent(lang4jService);
        this.decompositionAgent = new DecompositionAgent(lang4jService);
        this.consistencyAgent = new ConsistencyAgent(lang4jService);
        this.promptRefinementAgent = new PromptRefinementAgent(lang4jService);
        this.conditionAgent = new ConditionExtractionAgent(lang4jService);
        this.actionAgent = new ActionExtractionAgent(lang4jService);
        this.asciiRenderer = new AsciiRenderer();
        this.objectMapper = new ObjectMapper();

        // Read configuration from prompts.xml
        PromptRegistry registry = PromptRegistry.getInstance();
        String thresholdStr = registry.getAttribute(CONSISTENCY_PROMPT_KEY, "consistency_threshold");
        String maxRetriesStr = registry.getAttribute(CONSISTENCY_PROMPT_KEY, "max_retries");

        this.consistencyThreshold = thresholdStr != null ? Double.parseDouble(thresholdStr) : 0.8;
        this.maxRetries = maxRetriesStr != null ? Integer.parseInt(maxRetriesStr) : 3;

        logger.info("AgenticConversionWorkflow initialized with consistency_threshold={}, max_retries={}",
                consistencyThreshold, maxRetries);
    }

    public CompiledGraph<WorkflowState> build() throws Exception {
        StateGraph<WorkflowState> workflow = new StateGraph<>(WorkflowState::new);

        // Add all nodes
        workflow.addNode("validate_agent", this::validateNode);

        // Decomposition nodes
        workflow.addNode("decompose_agent", this::decomposeNode);
        workflow.addNode("consistency_check_decompose", this::consistencyCheckDecomposeNode);
        workflow.addNode("refine_decompose_prompt", this::refineDecomposePromptNode);

        // Condition extraction nodes
        workflow.addNode("condition_agent", this::conditionNode);
        workflow.addNode("consistency_check_condition", this::consistencyCheckConditionNode);
        workflow.addNode("refine_condition_prompt", this::refineConditionPromptNode);

        // Action extraction nodes
        workflow.addNode("action_agent", this::actionNode);
        workflow.addNode("consistency_check_action", this::consistencyCheckActionNode);
        workflow.addNode("refine_action_prompt", this::refineActionPromptNode);

        // Start with validation
        workflow.addEdge(START, "validate_agent");

        // After validation, decide whether to proceed or end
        workflow.addConditionalEdges(
                "validate_agent",
                state -> {
                    boolean valid = "true".equalsIgnoreCase((String) state.data().getOrDefault("valid", "false"));
                    if (valid) {
                        return CompletableFuture.completedFuture("decompose_agent");
                    } else {
                        return CompletableFuture.completedFuture(END);
                    }
                },
                Map.of("decompose_agent", "decompose_agent", END, END));

        // Decomposition flow
        workflow.addConditionalEdges(
                "decompose_agent",
                state -> {
                    if (state.isWorkflowFailed())
                        return CompletableFuture.completedFuture(END);
                    return CompletableFuture.completedFuture("consistency_check_decompose");
                },
                Map.of("consistency_check_decompose", "consistency_check_decompose", END, END));

        workflow.addConditionalEdges(
                "consistency_check_decompose",
                state -> {
                    if (state.isWorkflowFailed()) {
                        return CompletableFuture.completedFuture(END);
                    }

                    Double score = state.getConsistencyScore();
                    int retryCount = state.getRetryCount();

                    if (score != null && score >= consistencyThreshold) {
                        logger.info(
                                "✓ Decomposition Consistency PASSED (score={}, threshold={}). Proceeding to condition extraction.",
                                score, consistencyThreshold);
                        return CompletableFuture.completedFuture("condition_agent");
                    }

                    if (retryCount >= maxRetries) {
                        logger.warn(
                                "✗ Decomposition Max retries ({}) reached with score={}. Proceeding despite failure.",
                                maxRetries, score);
                        return CompletableFuture.completedFuture("condition_agent");
                    }

                    logger.info(
                            "✗ Decomposition Consistency FAILED (score={}, threshold={}). Retry {}/{}. Refining prompt...",
                            score, consistencyThreshold, retryCount + 1, maxRetries);
                    return CompletableFuture.completedFuture("refine_decompose_prompt");
                },
                Map.of("condition_agent", "condition_agent", "refine_decompose_prompt", "refine_decompose_prompt", END,
                        END));

        workflow.addEdge("refine_decompose_prompt", "decompose_agent");

        // Condition extraction flow
        workflow.addConditionalEdges(
                "condition_agent",
                state -> {
                    if (state.isWorkflowFailed())
                        return CompletableFuture.completedFuture(END);
                    return CompletableFuture.completedFuture("consistency_check_condition");
                },
                Map.of("consistency_check_condition", "consistency_check_condition", END, END));

        workflow.addConditionalEdges(
                "consistency_check_condition",
                state -> {
                    if (state.isWorkflowFailed()) {
                        return CompletableFuture.completedFuture(END);
                    }

                    Double score = state.getConditionConsistencyScore();
                    int retryCount = state.getConditionRetryCount();

                    if (score != null && score >= consistencyThreshold) {
                        logger.info(
                                "✓ Condition Consistency PASSED (score={}, threshold={}). Proceeding to action extraction.",
                                score, consistencyThreshold);
                        return CompletableFuture.completedFuture("action_agent");
                    }

                    if (retryCount >= maxRetries) {
                        logger.warn("✗ Condition Max retries ({}) reached with score={}. Proceeding despite failure.",
                                maxRetries, score);
                        return CompletableFuture.completedFuture("action_agent");
                    }

                    logger.info(
                            "✗ Condition Consistency FAILED (score={}, threshold={}). Retry {}/{}. Refining prompt...",
                            score, consistencyThreshold, retryCount + 1, maxRetries);
                    return CompletableFuture.completedFuture("refine_condition_prompt");
                },
                Map.of("action_agent", "action_agent", "refine_condition_prompt", "refine_condition_prompt", END, END));

        workflow.addEdge("refine_condition_prompt", "condition_agent");

        // Action extraction flow
        workflow.addConditionalEdges(
                "action_agent",
                state -> {
                    if (state.isWorkflowFailed())
                        return CompletableFuture.completedFuture(END);
                    return CompletableFuture.completedFuture("consistency_check_action");
                },
                Map.of("consistency_check_action", "consistency_check_action", END, END));

        workflow.addConditionalEdges(
                "consistency_check_action",
                state -> {
                    if (state.isWorkflowFailed()) {
                        return CompletableFuture.completedFuture(END);
                    }

                    Double score = state.getActionConsistencyScore();
                    int retryCount = state.getActionRetryCount();

                    if (score != null && score >= consistencyThreshold) {
                        logger.info("✓ Action Consistency PASSED (score={}, threshold={}). Workflow complete.",
                                score, consistencyThreshold);
                        return CompletableFuture.completedFuture(END);
                    }

                    if (retryCount >= maxRetries) {
                        logger.warn("✗ Action Max retries ({}) reached with score={}. Ending workflow.",
                                maxRetries, score);
                        return CompletableFuture.completedFuture(END);
                    }

                    logger.info("✗ Action Consistency FAILED (score={}, threshold={}). Retry {}/{}. Refining prompt...",
                            score, consistencyThreshold, retryCount + 1, maxRetries);
                    return CompletableFuture.completedFuture("refine_action_prompt");
                },
                Map.of("refine_action_prompt", "refine_action_prompt", END, END));

        workflow.addEdge("refine_action_prompt", "action_agent");

        this.compiledGraph = workflow.compile();
        return this.compiledGraph;
    }

    private CompletableFuture<Map<String, Object>> validateNode(WorkflowState state) {
        logger.info("Calling Validation Agent...");
        return validationAgent.execute(state.getInput(), state.getTraceId())
                .thenApply(agentState -> Map.of(
                        "validationResponse", agentState.getValidationResult(),
                        "valid", String.valueOf(agentState.isValid())));
    }

    // ===== DECOMPOSITION NODES =====

    private CompletableFuture<Map<String, Object>> decomposeNode(WorkflowState state) {
        int retryCount = state.getRetryCount();
        logger.info("═══ DECOMPOSITION AGENT (Attempt {}/{}) ═══", retryCount + 1, maxRetries + 1);

        String systemPrompt = state.getCurrentDecompositionPrompt();
        if (systemPrompt == null || systemPrompt.trim().isEmpty()) {
            // Load default from registry
            systemPrompt = PromptRegistry.getInstance().get(DECOMPOSITION_PROMPT_KEY);
            logger.info("Using default system prompt from registry");

            if (systemPrompt == null || systemPrompt.trim().isEmpty()) {
                throw new IllegalArgumentException("System prompt is empty and not found in registry");
            }
        } else {
            logger.info("Using refined system prompt from previous iteration");
        }

        return decompositionAgent.execute(state.getInput(), systemPrompt, state.getTraceId())
                .thenApply(agentState -> {
                    if (agentState.isFailed()) {
                        return Map.of("workflowFailed", true, "failureReason", "Decomposition Agent Failed");
                    }
                    DecompositionResult result = agentState.getDecompositionResult();
                    RuleTree<NodeData> tree = agentState.getTree();

                    if (result == null || tree == null) {
                        return Map.of("workflowFailed", true, "failureReason",
                                "Decomposition Agent produced null result or tree");
                    }

                    logger.info("Decomposition completed successfully");
                    asciiRenderer.render(tree);

                    String previousOutput = "";
                    try {
                        previousOutput = objectMapper.writeValueAsString(result);
                    } catch (Exception e) {
                        logger.warn("Failed to serialize decomposition result", e);
                    }

                    return Map.of(
                            "decompositionResponse", result,
                            "tree", tree,
                            "previousOutput", previousOutput);
                });
    }

    private CompletableFuture<Map<String, Object>> consistencyCheckDecomposeNode(WorkflowState state) {
        logger.info("═══ CONSISTENCY CHECK (Decomposition) ═══");
        RuleTree<NodeData> tree = state.getTree();

        if (tree == null) {
            logger.error("Tree is null, cannot check consistency");
            return CompletableFuture.completedFuture(Map.of("workflowFailed", true));
        }

        return consistencyAgent.execute(tree, "root", state.getTraceId())
                .thenApply(consistencyState -> {
                    Double score = consistencyState.getConsistencyScore();

                    if (score == null) {
                        logger.warn("Consistency check returned null score, defaulting to 0.0");
                        score = 0.0;
                    }

                    logger.info("Decomposition Consistency score: {}", score);

                    String feedback = generateFeedback(tree, score, "decomposition");

                    return Map.of(
                            "consistencyScore", score,
                            "feedback", feedback,
                            "tree", consistencyState.getTree() != null ? consistencyState.getTree() : tree);
                });
    }

    private CompletableFuture<Map<String, Object>> refineDecomposePromptNode(WorkflowState state) {
        int currentRetry = state.getRetryCount();
        logger.info("═══ PROMPT REFINEMENT (Decomposition - Retry {}/{}) ═══", currentRetry + 1, maxRetries);

        String originalPrompt = state.getCurrentDecompositionPrompt();
        if (originalPrompt == null || originalPrompt.trim().isEmpty()) {
            originalPrompt = PromptRegistry.getInstance().get(DECOMPOSITION_PROMPT_KEY);
        }

        String inputText = state.getInput();
        String previousOutput = state.getPreviousOutput();
        String feedback = state.getFeedback();

        String refinedPrompt = promptRefinementAgent.refinePrompt(originalPrompt, inputText, previousOutput, feedback,
                currentRetry + 1, state.getTraceId());

        if (refinedPrompt == null || refinedPrompt.trim().isEmpty()) {
            logger.warn("Prompt refinement failed, keeping original prompt");
            refinedPrompt = originalPrompt;
        } else {
            logger.info("Successfully generated refined decomposition prompt");
        }

        return CompletableFuture.completedFuture(Map.of(
                "currentDecompositionPrompt", refinedPrompt,
                "retryCount", currentRetry + 1));
    }

    // ===== CONDITION EXTRACTION NODES =====

    private CompletableFuture<Map<String, Object>> conditionNode(WorkflowState state) {
        int retryCount = state.getConditionRetryCount();
        logger.info("═══ CONDITION EXTRACTION AGENT (Attempt {}/{}) ═══", retryCount + 1, maxRetries + 1);

        RuleTree<NodeData> tree = state.getTree();
        String customPromptKey = state.getCurrentConditionPromptKey();

        return conditionAgent.execute(tree, customPromptKey, null, state.getTraceId())
                .thenApply(agentState -> {
                    if (agentState.isFailed()) {
                        return Map.of("workflowFailed", true, "failureReason", "Condition Agent Failed");
                    }

                    RuleTree<NodeData> updatedTree = agentState.getTree();
                    logger.info("Condition Extraction completed");
                    asciiRenderer.render(updatedTree);

                    // Serialize tree for feedback
                    String previousOutput = serializeTreeForFeedback(updatedTree);

                    return Map.of(
                            "tree", updatedTree,
                            "conditionPreviousOutput", previousOutput);
                });
    }

    private CompletableFuture<Map<String, Object>> consistencyCheckConditionNode(WorkflowState state) {
        logger.info("═══ CONSISTENCY CHECK (Condition) ═══");
        RuleTree<NodeData> tree = state.getTree();

        if (tree == null) {
            logger.error("Tree is null, cannot check condition consistency");
            return CompletableFuture.completedFuture(Map.of("workflowFailed", true));
        }

        return consistencyAgent.execute(tree, "condition", state.getTraceId())
                .thenApply(consistencyState -> {
                    Double score = consistencyState.getConsistencyScore();

                    if (score == null) {
                        logger.warn("Condition consistency check returned null score, defaulting to 0.0");
                        score = 0.0;
                    }

                    logger.info("Condition Consistency score: {}", score);

                    String feedback = generateFeedback(tree, score, "condition");

                    return Map.of(
                            "conditionConsistencyScore", score,
                            "conditionFeedback", feedback,
                            "tree", consistencyState.getTree() != null ? consistencyState.getTree() : tree);
                });
    }

    private CompletableFuture<Map<String, Object>> refineConditionPromptNode(WorkflowState state) {
        int currentRetry = state.getConditionRetryCount();
        logger.info("═══ PROMPT REFINEMENT (Condition - Retry {}/{}) ═══", currentRetry + 1, maxRetries);

        // For condition extraction, we refine the prompt template key
        // This is a simplified approach - in production you might want to refine the
        // actual prompt content
        String feedback = state.getConditionFeedback();

        logger.info("Condition refinement feedback: {}", feedback);
        logger.warn("Note: Condition prompt refinement is simplified - using default prompt with retry");

        return CompletableFuture.completedFuture(Map.of(
                "conditionRetryCount", currentRetry + 1));
    }

    // ===== ACTION EXTRACTION NODES =====

    private CompletableFuture<Map<String, Object>> actionNode(WorkflowState state) {
        int retryCount = state.getActionRetryCount();
        logger.info("═══ ACTION EXTRACTION AGENT (Attempt {}/{}) ═══", retryCount + 1, maxRetries + 1);

        RuleTree<NodeData> tree = state.getTree();
        String customPromptKey = state.getCurrentActionPromptKey();

        return actionAgent.execute(tree, customPromptKey, state.getTraceId())
                .thenApply(agentState -> {
                    if (agentState.isFailed()) {
                        return Map.of("workflowFailed", true);
                    }

                    RuleTree<NodeData> updatedTree = agentState.getTree();
                    logger.info("Action Extraction completed");
                    asciiRenderer.render(updatedTree);

                    // Serialize tree for feedback
                    String previousOutput = serializeTreeForFeedback(updatedTree);

                    return Map.of(
                            "tree", updatedTree,
                            "actionPreviousOutput", previousOutput);
                });
    }

    private CompletableFuture<Map<String, Object>> consistencyCheckActionNode(WorkflowState state) {
        logger.info("═══ CONSISTENCY CHECK (Action) ═══");
        RuleTree<NodeData> tree = state.getTree();

        if (tree == null) {
            logger.error("Tree is null, cannot check action consistency");
            return CompletableFuture.completedFuture(Map.of("workflowFailed", true));
        }

        return consistencyAgent.execute(tree, "action", state.getTraceId())
                .thenApply(consistencyState -> {
                    Double score = consistencyState.getConsistencyScore();

                    if (score == null) {
                        logger.warn("Action consistency check returned null score, defaulting to 0.0");
                        score = 0.0;
                    }

                    logger.info("Action Consistency score: {}", score);

                    String feedback = generateFeedback(tree, score, "action");

                    return Map.of(
                            "actionConsistencyScore", score,
                            "actionFeedback", feedback,
                            "tree", consistencyState.getTree() != null ? consistencyState.getTree() : tree);
                });
    }

    private CompletableFuture<Map<String, Object>> refineActionPromptNode(WorkflowState state) {
        int currentRetry = state.getActionRetryCount();
        logger.info("═══ PROMPT REFINEMENT (Action - Retry {}/{}) ═══", currentRetry + 1, maxRetries);

        String feedback = state.getActionFeedback();

        logger.info("Action refinement feedback: {}", feedback);
        logger.warn("Note: Action prompt refinement is simplified - using default prompt with retry");

        return CompletableFuture.completedFuture(Map.of(
                "actionRetryCount", currentRetry + 1));
    }

    // ===== HELPER METHODS =====

    private String generateFeedback(RuleTree<NodeData> tree, Double score, String stage) {
        StringBuilder feedback = new StringBuilder();
        feedback.append("Stage: ").append(stage.toUpperCase()).append("\n");
        feedback.append("Consistency Score: ").append(String.format("%.2f", score));
        feedback.append(" (Threshold: ").append(consistencyThreshold).append(")\n\n");

        if (score < consistencyThreshold) {
            feedback.append("The ").append(stage)
                    .append(" does not adequately preserve the original statement's meaning.\n\n");

            RuleNode<NodeData> root = tree.getRoot();
            String originalText = root.getData().getInput();
            List<String> childrenTexts = new ArrayList<>();
            for (RuleNode<NodeData> child : root.getChildren()) {
                childrenTexts.add(child.getData().getInput());
            }

            feedback.append("Original Text:\n").append(originalText).append("\n\n");
            feedback.append("Extracted Children:\n");
            for (int i = 0; i < childrenTexts.size(); i++) {
                feedback.append((i + 1)).append(". ").append(childrenTexts.get(i)).append("\n");
            }
            feedback.append("\n");

            feedback.append("Issues Detected:\n");
            if (score < 0.5) {
                feedback.append("- Major semantic differences between original and extracted content\n");
                feedback.append("- Critical information may be missing or incorrectly split\n");
            } else if (score < 0.7) {
                feedback.append("- Moderate semantic differences detected\n");
                feedback.append("- Some details may be lost or incorrectly categorized\n");
            } else {
                feedback.append("- Minor inconsistencies in extraction\n");
                feedback.append("- Fine-tune the extraction logic to better preserve context\n");
            }

            feedback.append("\nSuggestions:\n");
            feedback.append("- Ensure all relevant information is fully captured\n");
            feedback.append("- Verify that the extraction maintains logical coherence\n");
            feedback.append("- Check that no critical details are lost in the process\n");
        }

        return feedback.toString();
    }

    private String serializeTreeForFeedback(RuleTree<NodeData> tree) {
        if (tree == null || tree.getRoot() == null) {
            return "{}";
        }

        try {
            // Simple serialization - just capture the structure
            StringBuilder sb = new StringBuilder();
            sb.append("{\n");
            sb.append("  \"root\": \"").append(tree.getRoot().getData().getInput()).append("\",\n");
            sb.append("  \"children_count\": ").append(tree.getRoot().getChildren().size()).append("\n");
            sb.append("}");
            return sb.toString();
        } catch (Exception e) {
            logger.warn("Failed to serialize tree for feedback", e);
            return "{}";
        }
    }

    public String print() {
        try {
            if (this.compiledGraph == null) {
                throw new IllegalStateException("Workflow graph must be built before printing.");
            }
            GraphRepresentation graphRep = this.compiledGraph.getGraph(GraphRepresentation.Type.MERMAID);
            return graphRep.getContent();
        } catch (Exception e) {
            logger.error("Error printing graph", e);
            throw new RuntimeException(e);
        }
    }
}
