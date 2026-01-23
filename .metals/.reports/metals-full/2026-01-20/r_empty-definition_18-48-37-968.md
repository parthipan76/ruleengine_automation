error id: file://<WORKSPACE>/src/main/java/com/sixdee/text2rule/Main.java:com/sixdee/text2rule/observability/IntegrationFactory#
file://<WORKSPACE>/src/main/java/com/sixdee/text2rule/Main.java
empty definition using pc, found symbol in pc: com/sixdee/text2rule/observability/IntegrationFactory#
empty definition using semanticdb
empty definition using fallback
non-local guesses:

offset: 1543
uri: file://<WORKSPACE>/src/main/java/com/sixdee/text2rule/Main.java
text:
```scala
package com.sixdee.text2rule;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sixdee.text2rule.config.ConfigurationManager;
import com.sixdee.text2rule.exception.ConfigurationException;
import com.sixdee.text2rule.exception.Text2RuleException;
import com.sixdee.text2rule.workflow.DecompositionWorkflow;
import com.sixdee.text2rule.workflow.WorkflowState;
import com.sixdee.text2rule.dto.ValidationResult;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.bsc.langgraph4j.CompiledGraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Main application class.
 * Uses ConfigurationManager for configuration.
 * Uses AgentFactory for agent creation.
 * Implements comprehensive error handling with custom exceptions.
 * Follows consolidated logging pattern.
 */
public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        ChatLanguageModel chatLanguageModel = null;
        CompiledGraph<WorkflowState> app = null;
        DecompositionWorkflow graphBuilder = null;
        WorkflowState finalState = null;

        try {
            logger.info("Application starting [version=1.0, timestamp={}]", System.currentTimeMillis());

            // Initialize LLM client using configuration
            chatLanguageModel = initializeLLMClient(ConfigurationManager.getInstance());

            // Initialize Observability Worker
     
            com.sixdee.text2rule.observability.Integrat@@ionFactory.startWorkerThread(ConfigurationManager.getInstance(););
            logger.info("Observability worker initialized");

            // Build workflow
            graphBuilder = new DecompositionWorkflow(chatLanguageModel);
            app = graphBuilder.build();

            // Prepare input
            Map<String, Object> inputs = Map.of("input",
                    "Run this campaign weekly on Mondays and Tuesdays from 5 October 2024 to 5 October 2026, targeting subscribers based on their SMS revenue, preferred location, and recharge behavior. Subscribers whose SMS revenue in the last 30 days is exactly 15 RO, whose favorite location is Adimali, and whose total recharge in the last 30 days is at least 200 RO should receive a promotional SMS with Message ID 24, while subscribers whose SMS revenue in the last 30 days is greater than 15 RO, whose favorite location is Bengaluru, and whose total recharge in the last 30 days is at least 150 RO should receive a promotional SMS with Message ID 25. Subscribers who do not meet either of these criteria should be excluded from the campaign.");

            logger.info("Invoking Decomposition Workflow [input_length={}]",
                    ((String) inputs.get("input")).length());

            // Execute workflow
            finalState = app.invoke(inputs)
                    .orElseThrow(() -> new Text2RuleException("Graph execution failed to return state"));

            // Process validation results
            processValidationResults(finalState);

            // Check workflow failure status
            if (!finalState.isWorkflowFailed()) {
                logger.info("Workflow execution completed [state={}]", finalState != null ? "available" : "null");

                // Present all results using ResultPresenter (Facade)
                new com.sixdee.text2rule.view.ResultPresenter(ConfigurationManager.getInstance()).renderTree(
                        graphBuilder,
                        finalState);
            }

            logger.info("Application completed successfully");

        } catch (ConfigurationException e) {
            logger.error("Configuration error [message={}]", e.getMessage(), e);
            System.exit(1);
        } catch (Text2RuleException e) {
            logger.error("Application error [type={}, message={}]",
                    e.getClass().getSimpleName(), e.getMessage(), e);
            System.exit(1);
        } catch (Exception e) {
            logger.error("Unexpected error [message={}]", e.getMessage(), e);
            System.exit(1);
        } finally {
            logger.info("Cleaning up resources");
            if (app != null) {
                logger.debug("Workflow graph cleanup completed");
                // Nullify the reference
                app = null;
            }
            finalState = null;
            logger.info("Application shutdown complete");
        }
    }

    /**
     * Initialize LLM client using LLMClientFactory.
     * Supports multiple providers based on configuration.
     */
    private static ChatLanguageModel initializeLLMClient(ConfigurationManager config) {
        ChatLanguageModel client = null;

        try {
            client = com.sixdee.text2rule.factory.LLMClientFactory.createChatModel(config);

            logger.info("LLM client initialized [provider={}, model={}]",
                    config.getActiveProvider(),
                    config.getProviderModelName(config.getActiveProvider()));

        } catch (Exception e) {
            logger.error("Failed to initialize LLM client [error={}]", e.getMessage(), e);
            throw new ConfigurationException("LLM client initialization failed", e);
        } finally {
            // Resource cleanup handled by caller
            logger.debug("LLM client initialization completed");
        }
        return client;
    }

    /**
     * Process and log validation results.
     */
    private static void processValidationResults(WorkflowState finalState) {
        ValidationResult validationResponse = null;
        ObjectMapper mapper = null;

        try {
            validationResponse = finalState.getValidationResponse();
            if (validationResponse != null) {
                if (!validationResponse.isValid()) {
                    mapper = new ObjectMapper();
                    logger.error("Validation failed [details={}]",
                            mapper.writerWithDefaultPrettyPrinter().writeValueAsString(validationResponse));
                } else {
                    logger.info("Validation passed [is_valid={}, issues={}]",
                            validationResponse.isValid(),
                            validationResponse.getIssuesDetected());
                }
            }
        } catch (Exception e) {
            logger.error("Error processing validation results [error={}]", e.getMessage(), e);
        } finally {
            // Cleanup resources
            validationResponse = null;
            mapper = null;
        }
    }

}

```


#### Short summary: 

empty definition using pc, found symbol in pc: com/sixdee/text2rule/observability/IntegrationFactory#