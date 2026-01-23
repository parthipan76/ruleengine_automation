package com.sixdee.text2rule.observability;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LangfuseWorker implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(LangfuseWorker.class);

    private final String endpointUrl;
    private final AsyncQueueManager queueManager;
    private final String publicKey;
    private final String secretKey;
    private volatile boolean running = true;

    public LangfuseWorker(String endpointUrl, AsyncQueueManager queueManager, String publicKey, String secretKey) {
        this.endpointUrl = endpointUrl;
        this.queueManager = queueManager;
        this.publicKey = publicKey;
        this.secretKey = secretKey;
    }

    public void stop() {
        this.running = false;
    }

    @Override
    public void run() {
        logger.info("LangfuseWorker started [endpoint={}]", endpointUrl);
        while (running) {
            try {
                ObservabilityEvent event = queueManager.take();
                if (event != null) {
                    String jsonPayload = toJson(event);
                    sendData(jsonPayload);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.warn("LangfuseWorker interrupted, stopping", e);
                break;
            } catch (Exception e) {
                logger.error("Unexpected error in LangfuseWorker loop", e);
            }
        }
        logger.info("LangfuseWorker stopped");
    }

    private com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();

    private String toJson(ObservabilityEvent event) {
        try {
            String transactionId = java.util.UUID.randomUUID().toString();
            // Use traceId from event or fallback to new UUID if null
            String traceId = event.getTraceId() != null ? event.getTraceId() : java.util.UUID.randomUUID().toString();
            String generationId = java.util.UUID.randomUUID().toString();
            String timestamp = java.time.Instant.now().toString();

            com.fasterxml.jackson.databind.node.ObjectNode root = objectMapper.createObjectNode();
            com.fasterxml.jackson.databind.node.ArrayNode batch = root.putArray("batch");

            // 1. Trace Create Event
            com.fasterxml.jackson.databind.node.ObjectNode traceEvent = batch.addObject();
            traceEvent.put("id", java.util.UUID.randomUUID().toString());
            traceEvent.put("type", "trace-create");
            traceEvent.put("timestamp", timestamp);

            com.fasterxml.jackson.databind.node.ObjectNode traceBody = traceEvent.putObject("body");
            traceBody.put("id", traceId);
            traceBody.put("timestamp", timestamp);
            traceBody.put("name", event.getAgentName() != null ? event.getAgentName() : "ChatGroq"); // Default to
                                                                                                     // ChatGroq if
                                                                                                     // null, or use
                                                                                                     // specific agent
                                                                                                     // name
            traceBody.put("sessionId", transactionId);

            // Trace Input (Construct as array of messages)
            com.fasterxml.jackson.databind.node.ArrayNode traceInputArray = traceBody.putArray("input");
            if (event.getMessages() != null) {
                for (java.util.Map<String, String> msg : event.getMessages()) {
                    com.fasterxml.jackson.databind.node.ObjectNode msgNode = traceInputArray.addObject();
                    msgNode.put("content", msg.getOrDefault("content", ""));
                    msgNode.put("role", msg.getOrDefault("role", "user"));
                }
            }

            // Add metadata to trace if present
            if (event.getMetadata() != null && !event.getMetadata().isEmpty()) {
                com.fasterxml.jackson.databind.node.ObjectNode traceMeta = traceBody.putObject("metadata");
                event.getMetadata().forEach((k, v) -> traceMeta.put(k, String.valueOf(v)));
            }

            // 2. Generation Create Event
            com.fasterxml.jackson.databind.node.ObjectNode generationEvent = batch.addObject();
            generationEvent.put("id", java.util.UUID.randomUUID().toString());
            generationEvent.put("type", "generation-create");
            generationEvent.put("timestamp", timestamp);

            com.fasterxml.jackson.databind.node.ObjectNode generationBody = generationEvent.putObject("body");
            generationBody.put("id", generationId);
            generationBody.put("traceId", traceId);
            generationBody.put("name", event.getAgentName() != null ? event.getAgentName() : "ChatGroq");
            generationBody.put("startTime", timestamp);

            // Generation Input
            com.fasterxml.jackson.databind.node.ArrayNode genInputArray = generationBody.putArray("input");
            if (event.getMessages() != null) {
                for (java.util.Map<String, String> msg : event.getMessages()) {
                    com.fasterxml.jackson.databind.node.ObjectNode msgNode = genInputArray.addObject();
                    msgNode.put("content", msg.getOrDefault("content", ""));
                    msgNode.put("role", msg.getOrDefault("role", "user"));
                }
            }

            // Generation Output
            // Ensure output is always JSON Object or wrapped properly
            if (event.getAgentOutput() != null && (event.getAgentOutput().trim().startsWith("{")
                    || event.getAgentOutput().trim().startsWith("["))) {
                try {
                    com.fasterxml.jackson.databind.JsonNode jsonOutput = objectMapper.readTree(event.getAgentOutput());
                    if (jsonOutput.isObject()) {
                        generationBody.set("output", jsonOutput);
                    } else {
                        // Array or primitive, wrap it
                        com.fasterxml.jackson.databind.node.ObjectNode outObj = generationBody.putObject("output");
                        outObj.set("value", jsonOutput);
                    }
                } catch (Exception e) {
                    com.fasterxml.jackson.databind.node.ObjectNode outObj = generationBody.putObject("output");
                    outObj.put("value", event.getAgentOutput());
                }
            } else {
                com.fasterxml.jackson.databind.node.ObjectNode outObj = generationBody.putObject("output");
                outObj.put("value", (event.getAgentOutput() != null ? event.getAgentOutput() : ""));
            }

            // Model Information
            generationBody.put("model", event.getModel() != null ? event.getModel() : "llama-3.3-70b-versatile"); // Default
                                                                                                                  // as
                                                                                                                  // per
                                                                                                                  // sample
            com.fasterxml.jackson.databind.node.ObjectNode modelParams = generationBody.putObject("modelParameters");
            if (event.getModelParameters() != null) {
                event.getModelParameters().forEach((k, v) -> modelParams.put(k, String.valueOf(v)));
            }

            // Metadata
            com.fasterxml.jackson.databind.node.ObjectNode metadata = generationBody.putObject("metadata");
            metadata.put("state", event.getAgentState());
            if (event.getMetadata() != null) {
                event.getMetadata().forEach((k, v) -> metadata.put(k, String.valueOf(v)));
            }

            // 3. Root Metadata
            com.fasterxml.jackson.databind.node.ObjectNode rootMetadata = root.putObject("metadata");
            rootMetadata.put("batch_size", 2);
            rootMetadata.put("sdk_integration", "LANGCHAIN");
            rootMetadata.put("sdk_version", "proxy-1.0.0");
            rootMetadata.put("sdk_variant", "langsmith-proxy");
            rootMetadata.put("public_key", publicKey);
            rootMetadata.put("sdk_name", "langsmith-langfuse-proxy");

            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            logger.error("Failed to serialize observability event", e);
            return "{}";
        }
    }

    public void sendData(String payload) {
        HttpURLConnection conn = null;
        OutputStream os = null;
        try {
            conn = (HttpURLConnection) new URL(endpointUrl).openConnection();
            conn.setRequestMethod("POST");
            // Standard JSON content type without extra params to avoid parser issues
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("User-Agent", "SixDee-Text2Rule-Agent/1.0");

            // Basic Auth
            if (publicKey != null && secretKey != null) {
                String auth = publicKey + ":" + secretKey;
                String encodedAuth = java.util.Base64.getEncoder()
                        .encodeToString(auth.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                conn.setRequestProperty("Authorization", "Basic " + encodedAuth);
            }

            conn.setDoOutput(true);
            os = conn.getOutputStream();
            os.write(payload.getBytes("UTF-8"));

            int responseCode = conn.getResponseCode();
            if (responseCode >= 200 && responseCode < 300) {
                logger.debug("Successfully sent event [code={}]", responseCode);
            } else {
                java.io.InputStream errorStream = conn.getErrorStream();
                String errorMsg = "No error details";
                if (errorStream != null) {
                    try (java.util.Scanner scanner = new java.util.Scanner(errorStream,
                            java.nio.charset.StandardCharsets.UTF_8.name())) {
                        errorMsg = scanner.useDelimiter("\\A").next();
                    }
                }
                logger.warn("Failed to send event [code={}, endpoint={}, error={}]", responseCode, endpointUrl,
                        errorMsg);
                logger.warn("Failed Payload: {}", payload);
            }

        } catch (Exception e) {
            logger.error("Failed to send observability payload", e);
        } finally {
            if (os != null) {
                try {
                    os.close();
                } catch (Exception ignored) {
                }
            }
            if (conn != null) {
                try {
                    conn.disconnect();
                } catch (Exception ignored) {
                }
            }
        }
    }
}
