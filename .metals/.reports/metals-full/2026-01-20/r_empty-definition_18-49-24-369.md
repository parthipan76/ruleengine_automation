error id: file://<WORKSPACE>/src/main/java/com/sixdee/text2rule/observability/IntegrationFactory.java:_empty_/AsyncQueueManager#
file://<WORKSPACE>/src/main/java/com/sixdee/text2rule/observability/IntegrationFactory.java
empty definition using pc, found symbol in pc: _empty_/AsyncQueueManager#
empty definition using semanticdb
empty definition using fallback
non-local guesses:

offset: 160
uri: file://<WORKSPACE>/src/main/java/com/sixdee/text2rule/observability/IntegrationFactory.java
text:
```scala
package com.sixdee.text2rule.observability;

public class IntegrationFactory {

    public static AsyncQueueManager getQueueManager() {
        return AsyncQueu@@eManager.getInstance();
    }

    public static LangfuseWorker createWorker(String endpointUrl, String apiKey) {
        return new LangfuseWorker(endpointUrl, AsyncQueueManager.getInstance(), apiKey);
    }

    public static void startWorkerThread(com.sixdee.text2rule.config.ConfigurationManager config) {
        String endpointUrl = config.getObservabilityUrl();
        String apiKey = config.getObservabilityApiKey();

        if (endpointUrl != null && !endpointUrl.isEmpty()) {
            startWorkerThread(endpointUrl, apiKey);
        } else {
            System.err.println("OBSERVABILITY_WARN: URL not configured, worker not started.");
        }
    }

    public static void startWorkerThread(String endpointUrl, String apiKey) {
        LangfuseWorker worker = createWorker(endpointUrl, apiKey);
        Thread t = new Thread(worker);
        t.setName("Langfuse-Worker-Thread");
        t.setDaemon(true);
        t.start();
    }

    public static void recordEvent(String input, Object output, String state) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            String outputJson = output instanceof String ? (String) output : mapper.writeValueAsString(output);

            AsyncQueueManager.getInstance().offer(
                    new ObservabilityEvent(input, outputJson, state));
        } catch (Exception e) {
            // Squelch or simple log as per "Not needed" strictness preference, but keeping
            // minimal safety
            System.err.println("OBSERVABILITY_ERR: " + e.getMessage());
        }
    }
}

```


#### Short summary: 

empty definition using pc, found symbol in pc: _empty_/AsyncQueueManager#