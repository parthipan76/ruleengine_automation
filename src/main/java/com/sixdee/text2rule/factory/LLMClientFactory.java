package com.sixdee.text2rule.factory;

import com.sixdee.text2rule.config.ConfigurationManager;
import com.sixdee.text2rule.exception.ConfigurationException;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

/**
 * Factory for creating ChatLanguageModel instances based on configuration.
 * Supports multiple LLM providers via LangChain4j.
 * Follows Factory Pattern for provider abstraction.
 */
public class LLMClientFactory {
    private static final Logger logger = LoggerFactory.getLogger(LLMClientFactory.class);

    /**
     * Create ChatLanguageModel based on active provider in configuration.
     * 
     * @param config ConfigurationManager instance
     * @return Configured ChatLanguageModel instance
     * @throws ConfigurationException if provider is unsupported or configuration is
     *                                invalid
     */
    public static ChatLanguageModel createChatModel(ConfigurationManager config) {
        String activeProvider = null;

        try {
            activeProvider = config.getActiveProvider();
            logger.info("Creating ChatLanguageModel [provider={}]", activeProvider);

            switch (activeProvider.toLowerCase()) {
                case "openai":
                    return createOpenAIModel(config);
                case "groq":
                    return createGroqModel(config);
                case "anthropic":
                    logger.warn("Anthropic provider requires langchain4j-anthropic dependency");
                    throw new ConfigurationException(
                            "Anthropic provider not yet implemented. Add langchain4j-anthropic dependency.");
                case "azure":
                    logger.warn("Azure OpenAI provider requires langchain4j-azure-open-ai dependency");
                    throw new ConfigurationException(
                            "Azure provider not yet implemented. Add langchain4j-azure-open-ai dependency.");
                case "google":
                    logger.warn("Google Gemini provider requires langchain4j-google-ai-gemini dependency");
                    throw new ConfigurationException(
                            "Google provider not yet implemented. Add langchain4j-google-ai-gemini dependency.");
                case "ollama":
                    logger.warn("Ollama provider requires langchain4j-ollama dependency");
                    throw new ConfigurationException(
                            "Ollama provider not yet implemented. Add langchain4j-ollama dependency.");
                case "huggingface":
                    logger.warn("HuggingFace provider requires langchain4j-hugging-face dependency");
                    throw new ConfigurationException(
                            "HuggingFace provider not yet implemented. Add langchain4j-hugging-face dependency.");
                default:
                    logger.error("Unsupported LLM provider [provider={}]", activeProvider);
                    throw new ConfigurationException("Unsupported LLM provider: " + activeProvider);
            }
        } catch (ConfigurationException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Failed to create ChatLanguageModel [error={}]", e.getMessage(), e);
            throw new ConfigurationException("Failed to create ChatLanguageModel", e);
        } finally {
            // Cleanup resources
            activeProvider = null;
        }
    }

    /**
     * Create OpenAI ChatLanguageModel.
     */
    private static ChatLanguageModel createOpenAIModel(ConfigurationManager config) {
        String apiKey = null;
        String baseUrl = null;
        String modelName = null;
        Duration timeout = null;
        ChatLanguageModel model = null;

        try {
            apiKey = config.getApiKey("openai");
            baseUrl = config.getProviderBaseUrl("openai");
            modelName = config.getProviderModelName("openai");
            timeout = config.getTimeout();

            logger.info("Creating OpenAI model [model={}, base_url={}]", modelName, baseUrl);

            model = OpenAiChatModel.builder()
                    .apiKey(apiKey)
                    .baseUrl(baseUrl)
                    .modelName(modelName)
                    .timeout(timeout)
                    .temperature(config.getTemperature())
                    .maxTokens(config.getMaxTokens())
                    .maxRetries(1)
                    .build();
            return model;
        } catch (Exception e) {
            logger.error("Failed to create OpenAI model [error={}]", e.getMessage(), e);
            throw new ConfigurationException("Failed to create OpenAI model", e);
        } finally {
            // Cleanup resources
            apiKey = null;
            baseUrl = null;
            modelName = null;
            timeout = null;
        }
    }

    /**
     * Create Groq ChatLanguageModel (uses OpenAI-compatible API).
     */
    private static ChatLanguageModel createGroqModel(ConfigurationManager config) {
        String apiKey = null;
        String baseUrl = null;
        String modelName = null;
        Duration timeout = null;
        ChatLanguageModel model = null;

        try {
            apiKey = config.getApiKey("groq");
            baseUrl = config.getProviderBaseUrl("groq");
            modelName = config.getProviderModelName("groq");
            timeout = config.getTimeout();

            logger.info("Creating Groq model [model={}, base_url={}]", modelName, baseUrl);

            // Groq uses OpenAI-compatible API
            model = OpenAiChatModel.builder()
                    .apiKey(apiKey)
                    .baseUrl(baseUrl)
                    .modelName(modelName)
                    .timeout(timeout)
                    .temperature(config.getTemperature())
                    .maxTokens(config.getMaxTokens())
                    .maxRetries(1)
                    .build();
            return model;
        } catch (Exception e) {
            logger.error("Failed to create Groq model [error={}]", e.getMessage(), e);
            throw new ConfigurationException("Failed to create Groq model", e);
        } finally {
            // Cleanup resources
            apiKey = null;
            baseUrl = null;
            modelName = null;
            timeout = null;
        }
    }
}
