package com.sixdee.text2rule.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sixdee.text2rule.agent.ActionExtractionAgent;
import com.sixdee.text2rule.agent.ConditionExtractionAgent;
import com.sixdee.text2rule.agent.ConsistencyAgent;
import com.sixdee.text2rule.agent.DecompositionAgent;
import com.sixdee.text2rule.agent.PromptRefinementAgent;
import com.sixdee.text2rule.agent.ScheduleExtractionAgent;

import com.sixdee.text2rule.agent.RuleConverterAgent;
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

import com.sixdee.text2rule.agent.RuleConverterAgent;
import com.sixdee.text2rule.agent.UnifiedRuleAgent;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;

public class DecompositionWorkflow {
    private static final Logger logger = LoggerFactory.getLogger(DecompositionWorkflow.class);

    private static final String DECOMPOSITION_PROMPT_KEY = "statement_decompostion_agent_prompt";
    private static final String SCHEDULE_EXTRACTION_PROMPT_KEY = "schedule_parser_prompt";
    private static final String ACTION_EXTRACTION_PROMPT_KEY = "action_extraction_prompt";
    private static final String RULE_CONVERTER_PROMPT_KEY = "rule_converter_prompt";
    private static final String UNIFIED_RULE_PROMPT_KEY = "unified_rule_prompt";

    private final ValidationAgent validationAgent;
    private final DecompositionAgent decompositionAgent;
    private final ConsistencyAgent consistencyAgent;
    private final PromptRefinementAgent promptRefinementAgent;
    private final ConditionExtractionAgent conditionExtractionAgent;
    private final ScheduleExtractionAgent scheduleExtractionAgent;
    private final RuleConverterAgent ruleConverterAgent;
    private final UnifiedRuleAgent unifiedRuleAgent;
    private final ActionExtractionAgent actionExtractionAgent;

    private final AsciiRenderer asciiRenderer;
    private final ObjectMapper objectMapper;
    private CompiledGraph<WorkflowState> compiledGraph;

    public DecompositionWorkflow(ChatLanguageModel lang4jService) {
        this.validationAgent = new ValidationAgent(lang4jService);
        this.decompositionAgent = new DecompositionAgent(lang4jService);
        this.consistencyAgent = new ConsistencyAgent(lang4jService);
        this.promptRefinementAgent = new PromptRefinementAgent(lang4jService);
        this.conditionExtractionAgent = new ConditionExtractionAgent(lang4jService);
        this.scheduleExtractionAgent = new ScheduleExtractionAgent(lang4jService);
        this.ruleConverterAgent = new RuleConverterAgent(lang4jService);
        this.unifiedRuleAgent = new UnifiedRuleAgent(lang4jService);
        this.actionExtractionAgent = new ActionExtractionAgent(lang4jService);

        this.asciiRenderer = new AsciiRenderer();
        this.objectMapper = new ObjectMapper();

        // Read configuration from prompts.xml
        logger.info("DecompositionWorkflow initialized");
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
        workflow.addNode("consistency_check_schedule", this::consistencyCheckScheduleNode);
        workflow.addNode("refine_schedule_prompt", this::refineSchedulePromptNode);

        // Unified Rule Node & Consistency
        workflow.addNode("rule_converter_agent", this::ruleConverterNode);
        workflow.addNode("consistency_check_converter", this::consistencyCheckConverterNode);
        workflow.addNode("refine_converter_prompt", this::refineConverterPromptNode);

        workflow.addNode("unified_rule_agent", this::unifiedRuleNode);
        workflow.addNode("consistency_check_unified", this::consistencyCheckUnifiedNode);
        workflow.addNode("refine_unified_prompt", this::refineUnifiedPromptNode);

        workflow.addNode("action_extract_agent", this::actionExtractionNode);
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
                        return CompletableFuture.completedFuture("Success");
                    } else {
                        return CompletableFuture.completedFuture("Failure");
                    }
                },
                Map.of("Success", "decompose_agent", "Failure", END));

        // Decomposition flow
        workflow.addConditionalEdges(
                "decompose_agent",
                state -> {
                    if (state.isWorkflowFailed())
                        return CompletableFuture.completedFuture("Failure");
                    return CompletableFuture.completedFuture("Success");
                },
                Map.of("Success", "consistency_check_decompose", "Failure", END));

        workflow.addConditionalEdges(
                "consistency_check_decompose",
                state -> {
                    if (state.isWorkflowFailed()) {
                        return CompletableFuture.completedFuture(END);
                    }

                    Double score = state.getConsistencyScore();
                    int retryCount = state.getRetryCount();
                    double threshold = getThreshold(DECOMPOSITION_PROMPT_KEY);
                    int maxRetries = getMaxRetries(DECOMPOSITION_PROMPT_KEY);

                    if (score != null && score >= threshold) {
                        logger.info(
                                "✓ Decomposition Consistency PASSED (score={}, threshold={}). Proceeding to Schedule Extraction.",
                                score, threshold);
                        return CompletableFuture.completedFuture("Success");
                    }

                    if (retryCount >= maxRetries) {
                        logger.warn(
                                "✗ Decomposition Max retries ({}) reached with score={}. Ending workflow.",
                                maxRetries, score);
                        return CompletableFuture.completedFuture("Failure");
                    }

                    logger.info(
                            "✗ Decomposition Consistency FAILED (score={}, threshold={}). Retry {}/{}. Refining prompt...",
                            score, threshold, retryCount + 1, maxRetries);
                    return CompletableFuture.completedFuture("Retry");
                },
                Map.of("Retry", "refine_decompose_prompt",
                        "Success", "schedule_extract_agent",
                        "Failure", END));

        workflow.addEdge("refine_decompose_prompt", "decompose_agent");

        // Schedule -> Condition
        // Schedule Logic (Agent -> Consistency -> Refine loop or Next)
        workflow.addConditionalEdges(
                "schedule_extract_agent",
                state -> state.isWorkflowFailed() ? CompletableFuture.completedFuture(END)
                        : CompletableFuture.completedFuture("audit"),
                Map.of("audit", "consistency_check_schedule", END, END));

        workflow.addConditionalEdges(
                "consistency_check_schedule",
                state -> {
                    if (state.isWorkflowFailed())
                        return CompletableFuture.completedFuture(END);
                    Double score = state.getScheduleConsistencyScore();
                    int retry = state.getScheduleRetryCount();
                    if (score != null && score >= getThreshold(SCHEDULE_EXTRACTION_PROMPT_KEY))
                        return CompletableFuture.completedFuture("Success");
                    if (retry >= getMaxRetries(SCHEDULE_EXTRACTION_PROMPT_KEY))
                        return CompletableFuture.completedFuture("Failure");
                    return CompletableFuture.completedFuture("Retry");
                },
                Map.of("Retry", "refine_schedule_prompt", "Success", "condition_extract_agent", "Failure",
                        "condition_extract_agent"));

        workflow.addEdge("refine_schedule_prompt", "schedule_extract_agent");

        // Extraction flow: Condition -> Consistency
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
                    double threshold = getThreshold(CONDITION_EXTRACTION_PROMPT_KEY);
                    int maxRetries = getMaxRetries(CONDITION_EXTRACTION_PROMPT_KEY);

                    if (score == null)
                        score = 0.0;

                    if (score >= threshold) {
                        logger.info(
                                "✓ Condition Consistency PASSED (score={}, threshold={}). Proceeding to Unified Rule Agent.",
                                score, threshold);
                        return CompletableFuture.completedFuture("Success");
                    }

                    if (retryCount >= maxRetries) {
                        logger.warn("✗ Condition Max retries ({}) reached with score={}. Proceeding with best effort.",
                                maxRetries, score);
                        return CompletableFuture.completedFuture("Failure");
                    }

                    logger.info(
                            "✗ Condition Consistency FAILED (score={}, threshold={}). Retry {}/{}. Refining prompt...",
                            score, threshold, retryCount + 1, maxRetries);
                    return CompletableFuture.completedFuture("Retry");
                },
                Map.of("Retry", "refine_condition_prompt",
                        "Success", "rule_converter_agent",
                        "Failure", "rule_converter_agent"));

        workflow.addEdge("refine_condition_prompt", "condition_extract_agent");

        // Rule Converter Logic
        workflow.addConditionalEdges(
                "rule_converter_agent",
                state -> state.isWorkflowFailed() ? CompletableFuture.completedFuture(END)
                        : CompletableFuture.completedFuture("audit"),
                Map.of("audit", "consistency_check_converter", END, END));

        workflow.addConditionalEdges(
                "consistency_check_converter",
                state -> {
                    if (state.isWorkflowFailed())
                        return CompletableFuture.completedFuture(END);
                    Double score = state.getRuleConverterConsistencyScore();
                    int retry = state.getRuleConverterRetryCount();
                    if (score != null && score >= getThreshold(RULE_CONVERTER_PROMPT_KEY))
                        return CompletableFuture.completedFuture("Success");
                    if (retry >= getMaxRetries(RULE_CONVERTER_PROMPT_KEY))
                        return CompletableFuture.completedFuture("Failure");
                    return CompletableFuture.completedFuture("Retry");
                },
                Map.of("Retry", "refine_converter_prompt", "Success", "unified_rule_agent", "Failure",
                        "unified_rule_agent"));

        workflow.addEdge("refine_converter_prompt", "rule_converter_agent");

        // Unified Rule Logic
        workflow.addConditionalEdges(
                "unified_rule_agent",
                state -> state.isWorkflowFailed() ? CompletableFuture.completedFuture(END)
                        : CompletableFuture.completedFuture("audit"),
                Map.of("audit", "consistency_check_unified", END, END));

        workflow.addConditionalEdges(
                "consistency_check_unified",
                state -> {
                    if (state.isWorkflowFailed())
                        return CompletableFuture.completedFuture(END);
                    Double score = state.getUnifiedRuleConsistencyScore();
                    int retry = state.getUnifiedRuleRetryCount();
                    if (score != null && score >= getThreshold(UNIFIED_RULE_PROMPT_KEY))
                        return CompletableFuture.completedFuture("Success");
                    if (retry >= getMaxRetries(UNIFIED_RULE_PROMPT_KEY))
                        return CompletableFuture.completedFuture("Failure");
                    return CompletableFuture.completedFuture("Retry");
                },
                Map.of("Retry", "refine_unified_prompt", "Success", "action_extract_agent", "Failure",
                        "action_extract_agent"));

        workflow.addEdge("refine_unified_prompt", "unified_rule_agent");

        // Action Logic
        workflow.addConditionalEdges(
                "action_extract_agent",
                state -> state.isWorkflowFailed() ? CompletableFuture.completedFuture(END)
                        : CompletableFuture.completedFuture("audit"),
                Map.of("audit", "consistency_check_action", END, END));

        workflow.addConditionalEdges(
                "consistency_check_action",
                state -> {
                    if (state.isWorkflowFailed())
                        return CompletableFuture.completedFuture(END);
                    Double score = state.getActionConsistencyScore();
                    int retry = state.getActionRetryCount();
                    if (score != null && score >= getThreshold(ACTION_EXTRACTION_PROMPT_KEY))
                        return CompletableFuture.completedFuture("Success");
                    if (retry >= getMaxRetries(ACTION_EXTRACTION_PROMPT_KEY))
                        return CompletableFuture.completedFuture("Failure");
                    return CompletableFuture.completedFuture("Retry");
                },
                Map.of("Retry", "refine_action_prompt", "Success", END, "Failure", END));

        workflow.addEdge("refine_action_prompt", "action_extract_agent");

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
        int maxRetries = getMaxRetries(DECOMPOSITION_PROMPT_KEY);
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
                .exceptionally(ex -> {
                    logger.error("Decomposition Agent failed with exception", ex);
                    return null; // Return null to trigger failure handling in thenApply
                })
                .thenApply(agentState -> {
                    if (agentState == null) {
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
                .exceptionally(ex -> {
                    logger.error("Consistency check failed with exception", ex);
                    return null;
                })
                .thenApply(consistencyState -> {
                    if (consistencyState == null) {
                        return Map.of("workflowFailed", true);
                    }

                    Double score = consistencyState.getConsistencyScore();

                    if (score == null) {
                        logger.warn("Consistency check returned null score, defaulting to 0.0");
                        score = 0.0;
                    }

                    logger.info("Decomposition Consistency score: {}", score);

                    String feedback = generateFeedback(tree, score, "decomposition",
                            getThreshold(DECOMPOSITION_PROMPT_KEY));

                    return Map.of(
                            "consistencyScore", score,
                            "feedback", feedback,
                            "tree", consistencyState.getTree() != null ? consistencyState.getTree() : tree);
                });
    }

    private CompletableFuture<Map<String, Object>> refineDecomposePromptNode(WorkflowState state) {
        int currentRetry = state.getRetryCount();
        int maxRetries = getMaxRetries(DECOMPOSITION_PROMPT_KEY);
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

    // ===== EXTRACTION NODES =====

    private static final String CONDITION_EXTRACTION_PROMPT_KEY = "condition_extraction_prompt";

    private CompletableFuture<Map<String, Object>> conditionExtractionNode(WorkflowState state) {
        int retryCount = state.getConditionRetryCount();
        int maxRetries = getMaxRetries(CONDITION_EXTRACTION_PROMPT_KEY);
        logger.info("═══ CONDITION EXTRACTION AGENT (Attempt {}/{}) ═══", retryCount + 1, maxRetries + 1);
        RuleTree<NodeData> tree = state.getTree();

        String customPromptKey = state.getCurrentConditionPromptKey();
        String customPromptString = state.getCurrentConditionPromptString();

        if (customPromptString != null) {
            logger.info("Using refined condition prompt string.");
        }

        return conditionExtractionAgent.execute(tree, customPromptKey, customPromptString, state.getTraceId())
                .thenApply(conditionState -> {
                    if (conditionState.isFailed()) {
                        logger.warn("Condition Extraction failed or produced no updates.");
                    } else {
                        logger.info("Condition Extraction completed.");
                    }

                    return Map.of("tree", conditionState.getTree() != null ? conditionState.getTree() : tree);
                });
    }

    private CompletableFuture<Map<String, Object>> consistencyCheckConditionNode(WorkflowState state) {
        logger.info("═══ CONSISTENCY CHECK (Condition) ═══");
        RuleTree<NodeData> tree = state.getTree();

        return consistencyAgent.execute(tree, "condition", state.getTraceId())
                .thenApply(consistencyState -> {
                    Double score = consistencyState.getConsistencyScore();
                    if (score == null) {
                        logger.warn("Condition consistency check returned null score, defaulting to 0.0");
                        score = 0.0;
                    }

                    logger.info("Condition Consistency score: {}", score);
                    String feedback = generateFeedback(tree, score, "condition",
                            getThreshold(CONDITION_EXTRACTION_PROMPT_KEY));

                    return Map.of(
                            "conditionConsistencyScore", score,
                            "conditionFeedback", feedback,
                            "tree", consistencyState.getTree() != null ? consistencyState.getTree() : tree);
                });
    }

    private CompletableFuture<Map<String, Object>> refineConditionPromptNode(WorkflowState state) {
        int currentRetry = state.getConditionRetryCount();
        int maxRetries = getMaxRetries(CONDITION_EXTRACTION_PROMPT_KEY);
        logger.info("═══ PROMPT REFINEMENT (Condition - Retry {}/{}) ═══", currentRetry + 1, maxRetries);

        String originalPrompt = state.getCurrentConditionPromptString();
        if (originalPrompt == null || originalPrompt.trim().isEmpty()) {
            // Fallback to registry if we don't have a refined one yet
            String key = state.getCurrentConditionPromptKey();
            if (key == null)
                key = CONDITION_EXTRACTION_PROMPT_KEY;
            originalPrompt = PromptRegistry.getInstance().get(key);
        }

        String inputText = state.getInput();
        String feedback = state.getConditionFeedback();
        String previousOutput = "";

        String refinedPrompt = promptRefinementAgent.refinePrompt(originalPrompt, inputText, previousOutput, feedback,
                currentRetry + 1, state.getTraceId());

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
        logger.info("═══ SCHEDULE EXTRACTION AGENT (Retry {}/{}) ═══", state.getScheduleRetryCount() + 1,
                getMaxRetries(SCHEDULE_EXTRACTION_PROMPT_KEY) + 1);
        RuleTree<NodeData> tree = state.getTree();

        return scheduleExtractionAgent.execute(tree, state.getTraceId())
                .thenApply(scheduleState -> {
                    logger.info("Schedule Extraction completed.");
                    return Map.of("tree", scheduleState.getTree() != null ? scheduleState.getTree() : tree);
                });
    }

    private CompletableFuture<Map<String, Object>> consistencyCheckScheduleNode(WorkflowState state) {
        logger.info("═══ CONSISTENCY CHECK (Schedule) ═══");
        RuleTree<NodeData> tree = state.getTree();

        return consistencyAgent.execute(tree, "schedule", state.getTraceId())
                .thenApply(consistencyState -> {
                    Double score = consistencyState.getConsistencyScore();
                    if (score == null)
                        score = 0.0;
                    logger.info("Schedule Consistency score: {}", score);
                    String feedback = generateFeedback(tree, score, "schedule",
                            getThreshold(SCHEDULE_EXTRACTION_PROMPT_KEY));
                    return Map.of(
                            "scheduleConsistencyScore", score,
                            "scheduleFeedback", feedback,
                            "tree", consistencyState.getTree() != null ? consistencyState.getTree() : tree);
                });
    }

    private CompletableFuture<Map<String, Object>> refineSchedulePromptNode(WorkflowState state) {
        // ... (Logic calls PromptRefinementAgent)
        return refinePromptGeneric(state, SCHEDULE_EXTRACTION_PROMPT_KEY,
                state.getScheduleRetryCount(), state.getScheduleFeedback(),
                state.getCurrentSchedulePromptString(), "Schedule")
                .thenApply(map -> {
                    Map<String, Object> result = new java.util.HashMap<>(map);
                    result.put("currentSchedulePromptString", map.get("refinedPrompt"));
                    result.put("scheduleRetryCount", ((Integer) map.get("retryCount")));
                    return result;
                });
    }

    private CompletableFuture<Map<String, Object>> ruleConverterNode(WorkflowState state) {
        logger.info("═══ RULE CONVERTER AGENT ═══");
        RuleTree<NodeData> tree = state.getTree();

        return ruleConverterAgent.execute(tree, state.getCurrentRuleConverterPromptString(), state.getTraceId())
                .thenApply(converterState -> {
                    logger.info("Rule conversion completed.");
                    asciiRenderer.render(converterState.getTree());
                    return Map.of("tree", converterState.getTree() != null ? converterState.getTree() : tree);
                });
    }

    private CompletableFuture<Map<String, Object>> actionExtractionNode(WorkflowState state) {
        logger.info("═══ ACTION EXTRACTION AGENT ═══");
        RuleTree<NodeData> tree = state.getTree();

        return actionExtractionAgent.execute(tree, state.getCurrentActionPromptString(), state.getTraceId())
                .thenApply(actionState -> {
                    logger.info("Action extraction completed.");
                    return Map.of("tree", actionState.getTree() != null ? actionState.getTree() : tree);
                });
    }

    private String generateFeedback(RuleTree<NodeData> tree, Double score, String stage, double threshold) {
        StringBuilder feedback = new StringBuilder();
        feedback.append("Stage: ").append(stage.toUpperCase()).append("\n");
        feedback.append("Consistency Score: ").append(String.format("%.2f", score));
        feedback.append(" (Threshold: ").append(threshold).append(")\n\n");

        if (score < threshold) {
            feedback.append("The ").append(stage)
                    .append(" does not adequately preserve the original statement's meaning.\n\n");
            feedback.append("Score is below threshold. Please improve consistency.");
        }
        return feedback.toString();
    }

    private double getThreshold(String key) {
        String val = PromptRegistry.getInstance().getAttribute(key, "consistency_threshold");
        return val != null ? Double.parseDouble(val) : 0.8;
    }

    private int getMaxRetries(String key) {
        String val = PromptRegistry.getInstance().getAttribute(key, "max_retries");
        return val != null ? Integer.parseInt(val) : 3;
    }

    // Generic Helper for Prompt Refinement to reduce boilerplate
    private CompletableFuture<Map<String, Object>> refinePromptGeneric(WorkflowState state, String promptKey,
            int currentRetry, String feedback,
            String currentPrompt, String stageName) {
        int maxRetries = getMaxRetries(promptKey);
        logger.info("═══ PROMPT REFINEMENT ({} - Retry {}/{}) ═══", stageName, currentRetry + 1, maxRetries);

        String originalPrompt = currentPrompt;
        if (originalPrompt == null || originalPrompt.trim().isEmpty()) {
            originalPrompt = PromptRegistry.getInstance().get(promptKey);
        }

        String inputText = state.getInput();
        String previousOutput = ""; // Ideally get previous output from state

        String refinedPrompt = promptRefinementAgent.refinePrompt(originalPrompt, inputText, previousOutput, feedback,
                currentRetry + 1, state.getTraceId());

        if (refinedPrompt == null || refinedPrompt.trim().isEmpty()) {
            logger.warn("{} prompt refinement failed, keeping original.", stageName);
            refinedPrompt = originalPrompt;
        } else {
            logger.info("Successfully generated refined {} prompt", stageName);
        }

        return CompletableFuture
                .completedFuture(Map.of("refinedPrompt", refinedPrompt, "retryCount", currentRetry + 1));
    }

    // --- Action Nodes ---
    private CompletableFuture<Map<String, Object>> consistencyCheckActionNode(WorkflowState state) {
        logger.info("═══ CONSISTENCY CHECK (Action) ═══");
        RuleTree<NodeData> tree = state.getTree();

        return consistencyAgent.execute(tree, "action", state.getTraceId())
                .thenApply(consistencyState -> {
                    Double score = consistencyState.getConsistencyScore();
                    if (score == null)
                        score = 0.0;
                    logger.info("Action Consistency score: {}", score);
                    String feedback = generateFeedback(tree, score, "action",
                            getThreshold(ACTION_EXTRACTION_PROMPT_KEY));
                    return Map.of(
                            "actionConsistencyScore", score,
                            "actionFeedback", feedback,
                            "tree", consistencyState.getTree() != null ? consistencyState.getTree() : tree);
                });
    }

    private CompletableFuture<Map<String, Object>> refineActionPromptNode(WorkflowState state) {
        return refinePromptGeneric(state, ACTION_EXTRACTION_PROMPT_KEY,
                state.getActionRetryCount(), state.getActionFeedback(),
                state.getCurrentActionPromptString(), "Action")
                .thenApply(map -> {
                    Map<String, Object> result = new java.util.HashMap<>(map);
                    result.put("currentActionPromptString", map.get("refinedPrompt"));
                    result.put("actionRetryCount", ((Integer) map.get("retryCount")));
                    return result;
                });
    }

    // --- Rule Converter Nodes ---
    private CompletableFuture<Map<String, Object>> unifiedRuleNode(WorkflowState state) {
        logger.info("═══ UNIFIED RULE AGENT ═══");
        RuleTree<NodeData> tree = state.getTree();

        return unifiedRuleAgent.execute(tree, state.getCurrentUnifiedRulePromptString(), state.getTraceId())
                .thenApply(agentState -> {
                    asciiRenderer.render(agentState.getTree());
                    return Map.of("tree", agentState.getTree() != null ? agentState.getTree() : tree);
                });
    }

    private CompletableFuture<Map<String, Object>> consistencyCheckConverterNode(WorkflowState state) {
        logger.info("═══ CONSISTENCY CHECK (RuleConverter) ═══");
        RuleTree<NodeData> tree = state.getTree();

        return consistencyAgent.execute(tree, "rule_converter", state.getTraceId())
                .thenApply(consistencyState -> {
                    Double score = consistencyState.getConsistencyScore();
                    if (score == null)
                        score = 0.0;
                    logger.info("Rule Converter Consistency score: {}", score);
                    String feedback = generateFeedback(tree, score, "rule_converter",
                            getThreshold(RULE_CONVERTER_PROMPT_KEY));
                    return Map.of(
                            "ruleConverterConsistencyScore", score,
                            "ruleConverterFeedback", feedback,
                            "tree", consistencyState.getTree() != null ? consistencyState.getTree() : tree);
                });
    }

    private CompletableFuture<Map<String, Object>> refineConverterPromptNode(WorkflowState state) {
        return refinePromptGeneric(state, RULE_CONVERTER_PROMPT_KEY,
                state.getRuleConverterRetryCount(), state.getRuleConverterFeedback(),
                state.getCurrentRuleConverterPromptString(), "RuleConverter")
                .thenApply(map -> {
                    Map<String, Object> result = new java.util.HashMap<>(map);
                    result.put("currentRuleConverterPromptString", map.get("refinedPrompt"));
                    result.put("ruleConverterRetryCount", ((Integer) map.get("retryCount")));
                    return result;
                });
    }

    // --- Unified Rule Nodes ---
    private CompletableFuture<Map<String, Object>> consistencyCheckUnifiedNode(WorkflowState state) {
        logger.info("═══ CONSISTENCY CHECK (UnifiedRule) ═══");
        RuleTree<NodeData> tree = state.getTree();

        return consistencyAgent.execute(tree, "unified_rule", state.getTraceId())
                .thenApply(consistencyState -> {
                    Double score = consistencyState.getConsistencyScore();
                    if (score == null)
                        score = 0.0;
                    logger.info("Unified Rule Consistency score: {}", score);
                    String feedback = generateFeedback(tree, score, "unified_rule",
                            getThreshold(UNIFIED_RULE_PROMPT_KEY));
                    return Map.of(
                            "unifiedRuleConsistencyScore", score,
                            "unifiedRuleFeedback", feedback,
                            "tree", consistencyState.getTree() != null ? consistencyState.getTree() : tree);
                });
    }

    private CompletableFuture<Map<String, Object>> refineUnifiedPromptNode(WorkflowState state) {
        return refinePromptGeneric(state, UNIFIED_RULE_PROMPT_KEY,
                state.getUnifiedRuleRetryCount(), state.getUnifiedRuleFeedback(),
                state.getCurrentUnifiedRulePromptString(), "UnifiedRule")

                .thenApply(map -> {
                    Map<String, Object> result = new java.util.HashMap<>(map);
                    result.put("currentUnifiedRulePromptString", map.get("refinedPrompt"));
                    result.put("unifiedRuleRetryCount", ((Integer) map.get("retryCount")));
                    return result;
                });
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
