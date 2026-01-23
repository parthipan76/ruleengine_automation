package com.sixdee.text2rule.observability;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IntegrationFactory {

    private static final Logger logger = LoggerFactory.getLogger(IntegrationFactory.class);

    private static volatile IntegrationFactory instance;
    private static final Object lock = new Object();

    private IntegrationFactory() {
        // Private constructor
    }

    public static IntegrationFactory getInstance() {
        if (instance == null) {
            synchronized (lock) {
                if (instance == null) {
                    instance = new IntegrationFactory();
                }
            }
        }
        return instance;
    }

    public AsyncQueueManager getQueueManager() {
        return AsyncQueueManager.getInstance();
    }

    public LangfuseWorker createWorker(String endpointUrl, String apiKey, String secretKey) {
        return new LangfuseWorker(endpointUrl, AsyncQueueManager.getInstance(), apiKey, secretKey);
    }

    public void startWorkerThread(com.sixdee.text2rule.config.ConfigurationManager config) {
        String endpointUrl = config.getObservabilityUrl();
        String apiKey = config.getObservabilityApiKey();
        String secretKey = config.getObservabilitySecretKey();

        if (endpointUrl != null && !endpointUrl.isEmpty()) {
            startWorkerThread(endpointUrl, apiKey, secretKey);
        } else {
            logger.warn("OBSERVABILITY_WARN: URL not configured, worker not started.");
        }
    }

    private volatile boolean enabled = false;

    public void startWorkerThread(String endpointUrl, String apiKey, String secretKey) {
        LangfuseWorker worker = createWorker(endpointUrl, apiKey, secretKey);
        Thread t = new Thread(worker);
        t.setName("Langfuse-Worker-Thread");
        t.setDaemon(true);
        t.start();
        this.enabled = true;
    }

    public void recordEvent(String traceId, String agentName, java.util.List<java.util.Map<String, String>> messages,
            Object output,
            String state, String model, java.util.Map<String, Object> modelParameters,
            java.util.Map<String, Object> metadata) {
        if (!enabled) {
            return;
        }
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            String outputJson = output instanceof String ? (String) output : mapper.writeValueAsString(output);

            AsyncQueueManager.getInstance().offer(
                    new ObservabilityEvent(traceId, agentName, messages, outputJson, state, model, modelParameters,
                            metadata));
        } catch (Exception e) {
            // Squelch or simple log as per "Not needed" strictness preference, but keeping
            // minimal safety
            logger.error("OBSERVABILITY_ERR: " + e.getMessage(), e);
        }
    }
}
