error id: file://<WORKSPACE>/src/main/java/com/sixdee/text2rule/observability/LangfuseWorker.java:_empty_/AsyncQueueManager#
file://<WORKSPACE>/src/main/java/com/sixdee/text2rule/observability/LangfuseWorker.java
empty definition using pc, found symbol in pc: _empty_/AsyncQueueManager#
empty definition using semanticdb
empty definition using fallback
non-local guesses:

offset: 247
uri: file://<WORKSPACE>/src/main/java/com/sixdee/text2rule/observability/LangfuseWorker.java
text:
```scala
package com.sixdee.text2rule.observability;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class LangfuseWorker implements Runnable {
    private final String endpointUrl;
    private final AsyncQueue@@Manager queueManager;
    private final String apiKey;
    private volatile boolean running = true;

    public LangfuseWorker(String endpointUrl, AsyncQueueManager queueManager, String apiKey) {
        this.endpointUrl = endpointUrl;
        this.queueManager = queueManager;
        this.apiKey = apiKey;
    }

    public void stop() {
        this.running = false;
    }

    @Override
    public void run() {
        while (running) {
            try {
                ObservabilityEvent event = queueManager.take();
                if (event != null) {
                    String jsonPayload = toJson(event);
                    sendData(jsonPayload);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println("ERR_QUEUE: Worker interrupted: " + e.getMessage());
                break;
            } catch (Exception e) {
                System.err.println("ERR_WORKER: Unexpected error: " + e.getMessage());
            }
        }
    }

    private String toJson(ObservabilityEvent event) {
        return String.format("{\"input\": \"%s\", \"output\": \"%s\", \"state\": \"%s\"}",
                escape(event.getAgentInput()),
                escape(event.getAgentOutput()),
                escape(event.getAgentState()));
    }

    private String escape(String s) {
        if (s == null)
            return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    // EXAMPLE STYLE - APPLY THIS PATTERN TO ALL CODE
    public void sendData(String payload) {
        HttpURLConnection conn = null;
        OutputStream os = null;
        try {
            // 1. Logic inside Try
            conn = (HttpURLConnection) new URL(endpointUrl).openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; utf-8");
            conn.setRequestProperty("Accept", "application/json");

            // Basic Auth with API Key as username
            if (apiKey != null && !apiKey.isEmpty()) {
                String auth = apiKey + ":";
                String encodedAuth = java.util.Base64.getEncoder()
                        .encodeToString(auth.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                conn.setRequestProperty("Authorization", "Basic " + encodedAuth);
            }

            conn.setDoOutput(true);
            os = conn.getOutputStream();
            os.write(payload.getBytes("UTF-8"));

            int responseCode = conn.getResponseCode();
            if (responseCode >= 400) {
                System.err.println("ERR_NETWORK: Server returned error code: " + responseCode);
            }

        } catch (Exception e) {
            // 2. Single line logging only
            System.err.println("ERR_NETWORK: Failed to send payload due to: " + e.getMessage());
        } finally {
            // 3. Clean up in Finally
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

```


#### Short summary: 

empty definition using pc, found symbol in pc: _empty_/AsyncQueueManager#