package com.sixdee.text2rule.agent;

import com.sixdee.text2rule.config.PromptRegistry;
import com.sixdee.text2rule.dto.ValidationResult;

import com.fasterxml.jackson.databind.ObjectMapper;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.state.AgentState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.bsc.langgraph4j.StateGraph.END;

public class ValidationAgent {
    private static final Logger logger = LoggerFactory.getLogger(ValidationAgent.class);
    private static final String PROMPT_KEY = "basic_validator_agent_prompt";
    private static final String JSON_INSTRUCTIONS = "\nValidate the rule and return a valid JSON object matching the format. Output ONLY the JSON.";
    private static final String ERROR_INPUT_NULL = "ValidationAgent: Input cannot be null";
    private static final String ERROR_PROMPT_NOT_FOUND = "ValidationAgent: Prompt '" + PROMPT_KEY
            + "' not found in registry.";

    private final ChatLanguageModel lang4jService;
    private final ObjectMapper objectMapper;
    private CompiledGraph<ValidationState> compiledGraph;

    public static class ValidationState extends AgentState {
        public ValidationState(Map<String, Object> initData) {
            super(new HashMap<>(initData));
        }

        public String getInput() {
            return (String) this.data().get("input");
        }

        public String getTraceId() {
            return (String) this.data().get("traceId");
        }

        public ValidationResult getValidationResult() {
            return (ValidationResult) this.data().get("validationResult");
        }

        public boolean isValid() {
            return "true".equalsIgnoreCase((String) this.data().get("valid"));
        }
    }

    public ValidationAgent(ChatLanguageModel lang4jService) {
        this.lang4jService = lang4jService;
        this.objectMapper = new ObjectMapper();
        // Configure to accept both camelCase and snake_case
        this.objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                false);
        compile();
    }

    private void compile() {
        try {
            StateGraph<ValidationState> graph = new StateGraph<>(ValidationState::new);
            graph.addNode("validate", this::validateNode);
            graph.addEdge(org.bsc.langgraph4j.StateGraph.START, "validate");
            graph.addEdge("validate", END);
            this.compiledGraph = graph.compile();
        } catch (Exception e) {
            logger.error("Failed to compile ValidationAgent graph", e);
            throw new RuntimeException(e);
        }
    }

    private CompletableFuture<Map<String, Object>> validateNode(ValidationState state) {
        String validationPromptForLog = null;
        AiMessage aiMessage = null;
        ValidationResult result = null;
        String content = null;
        String json = null;

        try {
            logger.info("ValidationAgent: Starting validation...");
            String input = state.getInput();
            if (input == null) {
                logger.error("ValidationAgent: Input is null, cannot proceed.");
                throw new IllegalArgumentException(ERROR_INPUT_NULL);
            }

            validationPromptForLog = generatePrompt(input);
            List<ChatMessage> messages = Collections.singletonList(new SystemMessage(validationPromptForLog));

            applyRateLimiting();

            logger.info("ValidationAgent: Sending prompt to LLM...");
            Response<AiMessage> response = lang4jService.generate(messages);
            logger.info("ValidationAgent: Received response from LLM.");

            aiMessage = response.content();
            content = aiMessage.text();

            result = parseValidationResult(content);
            if (result != null && result.isValid()) {
                try {
                    json = objectMapper.writeValueAsString(result);
                } catch (Exception e) {
                    logger.warn("Failed to serialize validation result for logging", e);
                }
            }

            return CompletableFuture
                    .completedFuture(Map.of("validationResult", result, "valid", String.valueOf(result.isValid())));

        } catch (Exception e) {
            logger.error("Validation failed with exception", e);
            // Re-throw exception as requested by user - stop workflow on error
            throw e instanceof RuntimeException ? (RuntimeException) e : new RuntimeException(e);
        } finally {
            String finalTraceId = state.getTraceId();
            String finalValidationPromptForLog = validationPromptForLog;
            String finalInput = state.getInput();
            AiMessage finalAiMessage = aiMessage;
            ValidationResult finalResult = result;
            String finalContent = content;
            String finalJson = json;

            // Execute in background or just run safely to ensure main logic isn't affected
            // by observability errors
            try {
                recordObservability(finalTraceId,
                        finalValidationPromptForLog != null ? finalValidationPromptForLog : finalInput,
                        finalAiMessage, finalResult, finalContent, finalJson);
            } catch (Exception observationError) {
                logger.error("Error recording observability event", observationError);
            }
        }
    }

    private String generatePrompt(String input) {
        String promptTemplate = PromptRegistry.getInstance().get(PROMPT_KEY);
        if (promptTemplate == null) {
            logger.error("ValidationAgent: Prompt '{}' not found in registry.", PROMPT_KEY);
            throw new com.sixdee.text2rule.exception.ConfigurationException(ERROR_PROMPT_NOT_FOUND);
        }
        return promptTemplate.replace("{{ $json.ruletext }}", input) + JSON_INSTRUCTIONS;
    }

    private void applyRateLimiting() {
        com.sixdee.text2rule.util.RateLimiter.getInstance().apply();
    }

    private ValidationResult parseValidationResult(String content) {
        ValidationResult result = null;
        try {
            result = com.sixdee.text2rule.tool.JsonExtractorTool.extractAndParse(content, ValidationResult.class);
            if (result != null) {
                logger.info("ValidationAgent: Parsed result - isValid: {}, issuesDetected: {}",
                        result.isValid(), result.getIssuesDetected());
            } else {
                logger.warn("No JSON found or parsing failed in response: {}", content);
            }
        } catch (Exception e) {
            logger.error("Failed to parse validation result", e);
        }

        if (result == null) {
            result = new ValidationResult();
            result.setValid(false);
            result.setIssuesDetected(Collections
                    .singletonList("Validation failed: Could not parse JSON response. Response: " + content));
        }
        return result;
    }

    private void recordObservability(String traceId, String prompt, AiMessage aiMessage, ValidationResult result,
            String content, String json) {
        List<Map<String, String>> messageListForEvent = new ArrayList<>();

        Map<String, String> userMessage = new HashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", prompt);
        messageListForEvent.add(userMessage);

        if (aiMessage != null) {
            Map<String, String> aiMessageMap = new HashMap<>();
            aiMessageMap.put("role", "assistant");
            aiMessageMap.put("content", aiMessage.text());
            messageListForEvent.add(aiMessageMap);
        }

        String validationJson = (json != null) ? json : (content != null ? content : "No JSON/Content");
        String status = (result != null && result.isValid()) ? "Validation Passed" : "Validation Failed";

        com.sixdee.text2rule.observability.IntegrationFactory.getInstance().recordEvent(
                traceId,
                "ValidationAgent",
                messageListForEvent,
                validationJson,
                status,
                "agent-model",
                Collections.emptyMap(),
                Collections.emptyMap());
    }

    public CompletableFuture<ValidationState> execute(String input, String traceId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return compiledGraph.invoke(Map.of("input", input, "traceId",
                        traceId != null ? traceId : java.util.UUID.randomUUID().toString())).orElse(null);
            } catch (Exception e) {
                logger.error("Error executing validation agent", e);
                // Unwrap RuntimeException if it wraps a known exception, or just throw
                if (e.getCause() instanceof RuntimeException) {
                    throw (RuntimeException) e.getCause();
                }
                throw new RuntimeException(e);
            }
        });
    }
}
