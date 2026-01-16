package com.sixdee.text2rule.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sixdee.text2rule.agent.ConditionExtractionAgent;
import com.sixdee.text2rule.agent.ConsistencyAgent;
import com.sixdee.text2rule.agent.DecompositionAgent;
import com.sixdee.text2rule.agent.PromptRefinementAgent;
import com.sixdee.text2rule.agent.ScheduleExtractionAgent;
import com.sixdee.text2rule.agent.ValidationAgent;
import com.sixdee.text2rule.config.PromptRegistry;
import com.sixdee.text2rule.dto.DecompositionResult;
import com.sixdee.text2rule.model.NodeData;
import com.sixdee.text2rule.model.RuleTree;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphRepresentation;
import org.bsc.langgraph4j.StateGraph;
import com.sixdee.text2rule.view.AsciiRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;

public class DecompositionWorkflow {
    private static final Logger logger = LoggerFactory.getLogger(DecompositionWorkflow.class);
    private static final String CONSISTENCY_PROMPT_KEY = "consistency_check_prompt";
    private static final String DECOMPOSITION_PROMPT_KEY = "statement_decompostion_agent_prompt";

    private final ValidationAgent validationAgent;
    private final DecompositionAgent decompositionAgent;
    private final ConsistencyAgent consistencyAgent;
    private final PromptRefinementAgent promptRefinementAgent;
    private final ConditionExtractionAgent conditionExtractionAgent;
    private final ScheduleExtractionAgent scheduleExtractionAgent;
    private final AsciiRenderer asciiRenderer;
    private final ObjectMapper objectMapper;
    private final double consistencyThreshold;
    private final int maxRetries;
    private CompiledGraph<WorkflowState> compiledGraph;

    public DecompositionWorkflow(ChatLanguageModel lang4jService) {
        this.validationAgent = new ValidationAgent(lang4jService);
        this.decompositionAgent = new DecompositionAgent(lang4jService);
        this.consistencyAgent = new ConsistencyAgent(lang4jService);
        this.promptRefinementAgent = new PromptRefinementAgent(lang4jService);
        this.conditionExtractionAgent = new ConditionExtractionAgent(lang4jService);
        this.scheduleExtractionAgent = new ScheduleExtractionAgent(lang4jService);
        this.asciiRenderer = new AsciiRenderer();
        this.objectMapper = new ObjectMapper();

        // Read configuration from prompts.xml
        PromptRegistry registry = PromptRegistry.getInstance();
        String thresholdStr = registry.getAttribute(CONSISTENCY_PROMPT_KEY, "consistency_threshold");
        String maxRetriesStr = registry.getAttribute(CONSISTENCY_PROMPT_KEY, "max_retries");

        this.consistencyThreshold = thresholdStr != null ? Double.parseDouble(thresholdStr) : 0.8;
        this.maxRetries = maxRetriesStr != null ? Integer.parseInt(maxRetriesStr) : 3;

        logger.info("DecompositionWorkflow initialized with consistency_threshold={}, max_retries={}",
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

        // Extraction nodes
        workflow.addNode("condition_extract_agent", this::conditionExtractionNode);
        workflow.addNode("consistency_check_condition", this::consistencyCheckConditionNode);
        workflow.addNode("refine_condition_prompt", this::refineConditionPromptNode);
        workflow.addNode("schedule_extract_agent", this::scheduleExtractionNode);

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
                                "✓ Decomposition Consistency PASSED (score={}, threshold={}). Proceeding to Extraction.",
                                score, consistencyThreshold);
                        // MOVED TO NEXT STEP: Condition Extraction
                        return CompletableFuture.completedFuture("condition_extract_agent");
                    }

                    if (retryCount >= maxRetries) {
                        logger.warn(
                                "✗ Decomposition Max retries ({}) reached with score={}. Ending workflow.",
                                maxRetries, score);
                        return CompletableFuture.completedFuture(END);
                    }

                    logger.info(
                            "✗ Decomposition Consistency FAILED (score={}, threshold={}). Retry {}/{}. Refining prompt...",
                            score, consistencyThreshold, retryCount + 1, maxRetries);
                    return CompletableFuture.completedFuture("refine_decompose_prompt");
                },
                Map.of("refine_decompose_prompt", "refine_decompose_prompt",
                        "condition_extract_agent", "condition_extract_agent",
                        END, END));

        workflow.addEdge("refine_decompose_prompt", "decompose_agent");

        // Extraction flow: Condition -> Consistency -> [Refine -> Condition] OR
        // [Schedule]
        // Condition -> Consistency
        workflow.addConditionalEdges(
                "condition_extract_agent",
                state -> {
                    if (state.isWorkflowFailed())
                        return CompletableFuture.completedFuture(END);
                    return CompletableFuture.completedFuture("consistency_check_condition");
                },
                Map.of("consistency_check_condition", "consistency_check_condition", END, END));

        // Consistency Check logic
        workflow.addConditionalEdges(
                "consistency_check_condition",
                state -> {
                    if (state.isWorkflowFailed()) {
                        return CompletableFuture.completedFuture(END);
                    }

                    Double score = state.getConditionConsistencyScore();
                    int retryCount = state.getConditionRetryCount();
                    if (score == null)
                        score = 0.0;

                    if (score >= consistencyThreshold) {
                        logger.info(
                                "✓ Condition Consistency PASSED (score={}, threshold={}). Proceeding to Schedule Extraction.",
                                score, consistencyThreshold);
                        return CompletableFuture.completedFuture("schedule_extract_agent");
                    }

                    if (retryCount >= maxRetries) {
                        logger.warn("✗ Condition Max retries ({}) reached with score={}. Proceeding with best effort.",
                                maxRetries, score);
                        // Proceeding instead of ending, as we might have partial results
                        return CompletableFuture.completedFuture("schedule_extract_agent");
                    }

                    logger.info(
                            "✗ Condition Consistency FAILED (score={}, threshold={}). Retry {}/{}. Refining prompt...",
                            score, consistencyThreshold, retryCount + 1, maxRetries);
                    return CompletableFuture.completedFuture("refine_condition_prompt");
                },
                Map.of("refine_condition_prompt", "refine_condition_prompt",
                        "schedule_extract_agent", "schedule_extract_agent",
                        END, END));

        workflow.addEdge("refine_condition_prompt", "condition_extract_agent");
        workflow.addEdge("schedule_extract_agent", END);

        this.compiledGraph = workflow.compile();
        return this.compiledGraph;
    }

    private CompletableFuture<Map<String, Object>> validateNode(WorkflowState state) {
        logger.info("Calling Validation Agent...");
        return validationAgent.execute(state.getInput())
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

        return decompositionAgent.execute(state.getInput(), systemPrompt)
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

        return consistencyAgent.execute(tree, "root")
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
                currentRetry + 1);

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

    // ===== EXTRACTION NODES =====

    private static final String CONDITION_EXTRACTION_PROMPT_KEY = "condition_extraction_prompt";

    // ... (fields remain same)

    private CompletableFuture<Map<String, Object>> conditionExtractionNode(WorkflowState state) {
        int retryCount = state.getConditionRetryCount();
        logger.info("═══ CONDITION EXTRACTION AGENT (Attempt {}/{}) ═══", retryCount + 1, maxRetries + 1);
        RuleTree<NodeData> tree = state.getTree();

        String customPromptKey = state.getCurrentConditionPromptKey();
        String customPromptString = state.getCurrentConditionPromptString();

        if (customPromptString != null) {
            logger.info("Using refined condition prompt string.");
        }

        return conditionExtractionAgent.execute(tree, customPromptKey, customPromptString)
                .thenApply(conditionState -> {
                    if (conditionState.isFailed()) {
                        logger.warn("Condition Extraction failed or produced no updates.");
                    } else {
                        logger.info("Condition Extraction completed.");
                    }

                    // Capture output for consistency check/refinement (approximation since agent
                    // updates tree in-place)
                    // We might need to serialize children of NormalStatements nodes to get
                    // "previousOutput" effectively
                    // For now, we rely on tree state.

                    return Map.of("tree", conditionState.getTree() != null ? conditionState.getTree() : tree);
                });
    }

    private CompletableFuture<Map<String, Object>> consistencyCheckConditionNode(WorkflowState state) {
        logger.info("═══ CONSISTENCY CHECK (Condition) ═══");
        RuleTree<NodeData> tree = state.getTree();

        if (tree == null) {
            return CompletableFuture.completedFuture(Map.of("workflowFailed", true));
        }

        return consistencyAgent.execute(tree, "condition")
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

        String originalPrompt = state.getCurrentConditionPromptString();
        if (originalPrompt == null || originalPrompt.trim().isEmpty()) {
            // Fallback to registry if we don't have a refined one yet
            String key = state.getCurrentConditionPromptKey();
            if (key == null)
                key = CONDITION_EXTRACTION_PROMPT_KEY;
            originalPrompt = PromptRegistry.getInstance().get(key);
        }

        // We need input text for refinement. For conditions, the input is technically
        // the text of NormalStatements nodes.
        // However, prompt refinement agent typically takes one "input text".
        // Using the root input is a reasonable proxy or we can aggregate
        // NormalStatements inputs.
        String inputText = state.getInput();
        String previousOutput = ""; // We don't easily have the strict JSON output here without serialization, might
                                    // skip or approximate
        String feedback = state.getConditionFeedback();

        String refinedPrompt = promptRefinementAgent.refinePrompt(originalPrompt, inputText, previousOutput, feedback,
                currentRetry + 1);

        if (refinedPrompt == null || refinedPrompt.trim().isEmpty()) {
            logger.warn("Condition prompt refinement failed, keeping original prompt");
            refinedPrompt = originalPrompt;
        } else {
            logger.info("Successfully generated refined condition prompt");
        }

        return CompletableFuture.completedFuture(Map.of(
                "currentConditionPromptString", refinedPrompt,
                "conditionRetryCount", currentRetry + 1));
    }

    private CompletableFuture<Map<String, Object>> scheduleExtractionNode(WorkflowState state) {
        logger.info("═══ SCHEDULE EXTRACTION AGENT (Dummy) ═══");
        RuleTree<NodeData> tree = state.getTree();

        return scheduleExtractionAgent.execute(tree)
                .thenApply(scheduleState -> {
                    logger.info("Schedule Extraction completed.");
                    return Map.of("tree", scheduleState.getTree() != null ? scheduleState.getTree() : tree);
                });
    }

    private String generateFeedback(RuleTree<NodeData> tree, Double score, String stage) {
        StringBuilder feedback = new StringBuilder();
        feedback.append("Stage: ").append(stage.toUpperCase()).append("\n");
        feedback.append("Consistency Score: ").append(String.format("%.2f", score));
        feedback.append(" (Threshold: ").append(consistencyThreshold).append(")\n\n");

        if (score < consistencyThreshold) {
            feedback.append("The ").append(stage)
                    .append(" does not adequately preserve the original statement's meaning.\n\n");
            // Simplified feedback generation compared to original which pulled children
            // texts
            feedback.append("Score is below threshold. Please improve consistency.");
        }
        return feedback.toString();
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
