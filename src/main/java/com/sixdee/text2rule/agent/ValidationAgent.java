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
        String input = null;
        List<ChatMessage> messages = null;
        String promptTemplate = null;
        String detailedInstructions = null;
        Response<AiMessage> response = null;
        AiMessage aiMessage = null;
        ValidationResult result = null;
        String content = null;
        String json = null;
        String isValid = null;
        String traceId = state.getTraceId();
        String validationPromptForLog = null;

        try {
            logger.info("ValidationAgent: Starting validation...");
            input = state.getInput();
            messages = new ArrayList<>();

            promptTemplate = PromptRegistry.getInstance().get("basic_validator_agent_prompt");
            // Fallback or use template
            if (promptTemplate == null)
                promptTemplate = "You are a validation agent. Validate the following rule: {{ $json.ruletext }}";

            // Append instructions to ensure strict JSON output
            detailedInstructions = "\nValidate the rule and return a valid JSON object matching the format. Output ONLY the JSON.";

            validationPromptForLog = promptTemplate.replace("{{ $json.ruletext }}", input) + detailedInstructions;

            messages.add(
                    new SystemMessage(validationPromptForLog));

            // Rate limit protection: 12-second delay
            try {
                Thread.sleep(12000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            logger.info("ValidationAgent: Sending prompt to LLM...");
            response = lang4jService.generate(messages);
            logger.info("ValidationAgent: Received response from LLM.");
            aiMessage = response.content();

            result = null;
            content = aiMessage.text();

            try {
                // Use JsonExtractorTool for robust JSON extraction
                result = com.sixdee.text2rule.tool.JsonExtractorTool.extractAndParse(content, ValidationResult.class);

                if (result != null) {
                    json = objectMapper.writeValueAsString(result); // Re-serialize for logging/observability if needed
                    logger.info("ValidationAgent: Parsed result - isValid: {}, issuesDetected: {}",
                            result.isValid(), result.getIssuesDetected());
                } else {
                    logger.warn("No JSON found or parsing failed in response: {}", content);
                }
            } catch (Exception e) {
                logger.error("Failed to parse validation result", e);
            }

            // Safety check if LLM didn't return proper structure
            if (result == null) {
                result = new ValidationResult();
                result.setValid(false);
                result.setIssuesDetected(
                        Collections
                                .singletonList(
                                        "Validation failed: Could not parse JSON response. Response: " + content));
            }

            isValid = String.valueOf(result.isValid());

            return CompletableFuture.completedFuture(Map.of("validationResult", result, "valid", isValid));
        } catch (Exception e) {
            logger.error("Validation failed with exception", e);
            // Create a dummy result for the state if needed, or rethrow
            // But for observability, we handle it in finally
            throw e;
        } finally {
            // Observability: Capture Event in Finally
            java.util.List<java.util.Map<String, String>> messageListForEvent = new java.util.ArrayList<>();
            java.util.Map<String, String> userMessage = new java.util.HashMap<>();
            userMessage.put("role", "user");
            userMessage.put("content", validationPromptForLog != null ? validationPromptForLog : state.getInput());
            // validationPrompt
            messageListForEvent.add(userMessage);

            // Add AI message if available
            if (aiMessage != null) {
                java.util.Map<String, String> aiMessageMap = new java.util.HashMap<>();
                aiMessageMap.put("role", "assistant");
                aiMessageMap.put("content", aiMessage.text());
                messageListForEvent.add(aiMessageMap);
            }

            String validationJson = (json != null) ? json : (content != null ? content : "No JSON/Content");
            String status = (result != null && result.isValid()) ? "Validation Passed" : "Validation Failed";

            com.sixdee.text2rule.observability.IntegrationFactory.getInstance().recordEvent(
                    traceId,
                    "ValidationAgent",
                    messageListForEvent, // Use the new message list
                    validationJson, // Use the extracted JSON or content
                    status, // Use the derived status
                    "agent-model",
                    java.util.Collections.emptyMap(),
                    java.util.Collections.emptyMap());

            // Cleanup resources
            input = null;
            messages = null;
            promptTemplate = null;
            detailedInstructions = null;
            response = null;
            aiMessage = null;
            content = null;
            json = null;
        }
    }

    public CompletableFuture<ValidationState> execute(String input, String traceId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return compiledGraph.invoke(Map.of("input", input, "traceId",
                        traceId != null ? traceId : java.util.UUID.randomUUID().toString())).orElse(null);
            } catch (Exception e) {
                logger.error("Error executing validation agent", e);
                throw new RuntimeException(e);
            }
        });
    }
}
