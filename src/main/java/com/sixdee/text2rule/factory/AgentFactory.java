package com.sixdee.text2rule.factory;

import com.sixdee.text2rule.agent.*;
import com.sixdee.text2rule.config.ConfigurationManager;
import com.sixdee.text2rule.exception.ConfigurationException;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Factory for creating agent instances following Factory Pattern.
 * Centralizes agent creation and dependency injection.
 * Supports Dependency Inversion Principle by abstracting agent instantiation.
 */
public class AgentFactory {
    private static final Logger logger = LoggerFactory.getLogger(AgentFactory.class);

    private final ChatLanguageModel llmClient;
    private final ConfigurationManager config;

    /**
     * Constructor with dependency injection.
     * 
     * @param llmClient The LLM client to inject into agents
     * @param config    The configuration manager
     */
    public AgentFactory(ChatLanguageModel llmClient, ConfigurationManager config) {
        if (llmClient == null) {
            throw new IllegalArgumentException("ChatLanguageModel cannot be null");
        }
        if (config == null) {
            throw new IllegalArgumentException("ConfigurationManager cannot be null");
        }

        this.llmClient = llmClient;
        this.config = config;

        logger.info("AgentFactory initialized [llm_client={}, config={}]",
                llmClient.getClass().getSimpleName(), config.getClass().getSimpleName());
    }

    /**
     * Create ValidationAgent instance.
     */
    public ValidationAgent createValidationAgent() {
        try {
            logger.debug("Creating ValidationAgent");
            return new ValidationAgent(llmClient);
        } catch (Exception e) {
            logger.error("Failed to create ValidationAgent [error={}]", e.getMessage(), e);
            throw new ConfigurationException("Failed to create ValidationAgent", e);
        }
    }

    /**
     * Create DecompositionAgent instance.
     */
    public DecompositionAgent createDecompositionAgent() {
        try {
            logger.debug("Creating DecompositionAgent");
            return new DecompositionAgent(llmClient);
        } catch (Exception e) {
            logger.error("Failed to create DecompositionAgent [error={}]", e.getMessage(), e);
            throw new ConfigurationException("Failed to create DecompositionAgent", e);
        }
    }

    /**
     * Create ConsistencyAgent instance.
     */
    public ConsistencyAgent createConsistencyAgent() {
        try {
            logger.debug("Creating ConsistencyAgent");
            return new ConsistencyAgent(llmClient);
        } catch (Exception e) {
            logger.error("Failed to create ConsistencyAgent [error={}]", e.getMessage(), e);
            throw new ConfigurationException("Failed to create ConsistencyAgent", e);
        }
    }
    
    
 // In AgentFactory.java constructor or init method
    public IAgent createAgent(String agentType, LLMClientFactory llmClient) {
        switch (agentType.toLowerCase()) {
            // Existing agents...
            case "action_extraction":
                return new ActionExtractionAgent(llmClient);
            
            // NEW AGENTS
            case "bonus_extraction":
                return new BonusExtractionAgent(llmClient);
            case "policy_extraction":
                return new PolicyExtractionAgent(llmClient);
            case "message_generation":
                return new MessageGenerationAgent(llmClient);
            
            default:
                throw new IllegalArgumentException("Unknown agent type: " + agentType);
        }
    }

    /**
     * Create PromptRefinementAgent instance.
     */
    public PromptRefinementAgent createPromptRefinementAgent() {
        try {
            logger.debug("Creating PromptRefinementAgent");
            return new PromptRefinementAgent(llmClient);
        } catch (Exception e) {
            logger.error("Failed to create PromptRefinementAgent [error={}]", e.getMessage(), e);
            throw new ConfigurationException("Failed to create PromptRefinementAgent", e);
        }
    }

    /**
     * Create ConditionExtractionAgent instance.
     */
    public ConditionExtractionAgent createConditionExtractionAgent() {
        try {
            logger.debug("Creating ConditionExtractionAgent");
            return new ConditionExtractionAgent(llmClient);
        } catch (Exception e) {
            logger.error("Failed to create ConditionExtractionAgent [error={}]", e.getMessage(), e);
            throw new ConfigurationException("Failed to create ConditionExtractionAgent", e);
        }
    }

    /**
     * Create ScheduleExtractionAgent instance.
     */
    public ScheduleExtractionAgent createScheduleExtractionAgent() {
        try {
            logger.debug("Creating ScheduleExtractionAgent");
            return new ScheduleExtractionAgent(llmClient);
        } catch (Exception e) {
            logger.error("Failed to create ScheduleExtractionAgent [error={}]", e.getMessage(), e);
            throw new ConfigurationException("Failed to create ScheduleExtractionAgent", e);
        }
    }

    /**
     * Create ActionExtractionAgent instance.
     */
    public ActionExtractionAgent createActionExtractionAgent() {
        try {
            logger.debug("Creating ActionExtractionAgent");
            return new ActionExtractionAgent(llmClient);
        } catch (Exception e) {
            logger.error("Failed to create ActionExtractionAgent [error={}]", e.getMessage(), e);
            throw new ConfigurationException("Failed to create ActionExtractionAgent", e);
        }
    }

    /**
     * Create RuleConverterAgent instance.
     */
    public RuleConverterAgent createRuleConverterAgent() {
        try {
            logger.debug("Creating RuleConverterAgent");
            return new RuleConverterAgent(llmClient);
        } catch (Exception e) {
            logger.error("Failed to create RuleConverterAgent [error={}]", e.getMessage(), e);
            throw new ConfigurationException("Failed to create RuleConverterAgent", e);
        }
    }

    /**
     * Create UnifiedRuleAgent instance.
     */
    public UnifiedRuleAgent createUnifiedRuleAgent() {
        try {
            logger.debug("Creating UnifiedRuleAgent");
            return new UnifiedRuleAgent(llmClient);
        } catch (Exception e) {
            logger.error("Failed to create UnifiedRuleAgent [error={}]", e.getMessage(), e);
            throw new ConfigurationException("Failed to create UnifiedRuleAgent", e);
        }
    }

    /**
     * Get the injected LLM client (for testing purposes).
     */
    public ChatLanguageModel getLlmClient() {
        return llmClient;
    }

    /**
     * Get the configuration manager (for testing purposes).
     */
    public ConfigurationManager getConfig() {
        return config;
    }
}
