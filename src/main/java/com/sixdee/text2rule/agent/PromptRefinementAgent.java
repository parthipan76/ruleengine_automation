package com.sixdee.text2rule.agent;

import com.sixdee.text2rule.config.PromptRegistry;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * PromptRefinementAgent is responsible for generating improved prompts
 * based on failure feedback from consistency checks.
 * 
 * This is a simple utility agent - it does NOT handle retry logic or
 * orchestration.
 * The workflow itself manages retries.
 */
public class PromptRefinementAgent {
    private static final Logger logger = LoggerFactory.getLogger(PromptRefinementAgent.class);
    private static final String REFINEMENT_PROMPT_KEY = "prompt_refinement_prompt";

    private final ChatLanguageModel lang4jService;

    public PromptRefinementAgent(ChatLanguageModel lang4jService) {
        this.lang4jService = lang4jService;
        logger.info("PromptRefinementAgent initialized");
    }

    /**
     * Generates a refined system prompt based on failure feedback.
     * 
     * @param originalPrompt The original system prompt that was used
     * @param inputText      The input text being processed
     * @param previousOutput The previous LLM output (decomposition result JSON)
     * @param feedback       Detailed feedback about why the consistency check
     *                       failed
     * @return A refined system prompt, or null if refinement fails
     */
    public String refinePrompt(String originalPrompt, String inputText,
            String previousOutput, String feedback, int retryCount, String traceId) {

        String response = null;
        boolean success = false;
        String populatedPrompt = null;
        String refinedPrompt = null;

        try {
            String promptTemplate = PromptRegistry.getInstance().get(REFINEMENT_PROMPT_KEY);

            if (promptTemplate == null || promptTemplate.startsWith("Prompt not found")) {
                logger.error("Prompt refinement template not found in prompts.xml");
                return null;
            }

            // Populate the template with context
            populatedPrompt = promptTemplate
                    .replace("{{ $json.original_prompt }}", originalPrompt != null ? originalPrompt : "")
                    .replace("{{ $json.input_text }}", inputText != null ? inputText : "")
                    .replace("{{ $json.previous_output }}", previousOutput != null ? previousOutput : "")
                    .replace("{{ $json.feedback }}", feedback != null ? feedback : "");

            logger.info("PromptRefinementAgent: Sending prompt to LLM...");
            // Rate limit protection: 12-second delay
            // Rate limit
            com.sixdee.text2rule.util.RateLimiter.getInstance().apply();
            response = lang4jService.generate(populatedPrompt);
            logger.info("PromptRefinementAgent: Received response from LLM.");

            // Clean up the response (remove markdown code blocks if present)
            refinedPrompt = cleanResponse(response);

            if (refinedPrompt == null || refinedPrompt.trim().isEmpty()) {
                logger.warn("LLM returned empty refined prompt");
                return null;
            }

            logger.debug("Refined Prompt Generated (Retry {}):\n{}", retryCount, refinedPrompt);

            success = true;
            return refinedPrompt;

        } catch (Exception e) {
            logger.error("Error generating refined prompt", e);
            response = "Error: " + e.getMessage();
            return null;
        } finally {
            // Observability: Capture Event in Finally
            java.util.List<java.util.Map<String, String>> messages = new java.util.ArrayList<>();
            java.util.Map<String, String> userMessage = new java.util.HashMap<>();
            userMessage.put("role", "user");
            userMessage.put("content", populatedPrompt != null ? populatedPrompt : ""); // Using populatedPrompt as
                                                                                        // input
            messages.add(userMessage);

            com.sixdee.text2rule.observability.IntegrationFactory.getInstance().recordEvent(
                    traceId,
                    "PromptRefinementAgent",
                    messages,
                    refinedPrompt,
                    "Prompt Refinement Attempted",
                    "agent-model",
                    java.util.Collections.emptyMap(),
                    java.util.Collections.emptyMap());
        }
    }

    /**
     * Cleans up LLM response by removing markdown code blocks
     */
    private String cleanResponse(String response) {
        if (response == null) {
            return null;
        }

        String cleaned = response.trim();

        // Remove markdown code blocks
        if (cleaned.contains("```")) {
            int start = cleaned.indexOf("```");
            int end = cleaned.lastIndexOf("```");
            if (start != -1 && end != -1 && end > start) {
                cleaned = cleaned.substring(start + 3, end).trim();

                // Remove language identifier if present (e.g., ```text or ```markdown)
                if (cleaned.startsWith("text") || cleaned.startsWith("markdown")) {
                    int newlineIdx = cleaned.indexOf('\n');
                    if (newlineIdx != -1) {
                        cleaned = cleaned.substring(newlineIdx + 1).trim();
                    }
                }
            }
        }

        return cleaned;
    }
}
