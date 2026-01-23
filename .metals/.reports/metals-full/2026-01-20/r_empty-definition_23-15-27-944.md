error id: file://<WORKSPACE>/src/main/java/com/sixdee/text2rule/observability/IntegrationFactory.java:java/io/PrintStream#println(+8).
file://<WORKSPACE>/src/main/java/com/sixdee/text2rule/observability/IntegrationFactory.java
empty definition using pc, found symbol in pc: java/io/PrintStream#println(+8).
empty definition using semanticdb
empty definition using fallback
non-local guesses:

offset: 1200
uri: file://<WORKSPACE>/src/main/java/com/sixdee/text2rule/observability/IntegrationFactory.java
text:
```scala
package com.sixdee.text2rule.observability;

public class IntegrationFactory {

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

    public LangfuseWorker createWorker(String endpointUrl, String apiKey) {
        return new LangfuseWorker(endpointUrl, AsyncQueueManager.getInstance(), apiKey);
    }

    public void startWorkerThread(com.sixdee.text2rule.config.ConfigurationManager config) {
        String endpointUrl = config.getObservabilityUrl();
        String apiKey = config.getObservabilityApiKey();

        if (endpointUrl != null && !endpointUrl.isEmpty()) {
            startWorkerThread(endpointUrl, apiKey);
        } else {
            System.err.println@@("OBSERVABILITY_WARN: URL not configured, worker not started.");
        }
    }

    private volatile boolean enabled = false;

    public void startWorkerThread(String endpointUrl, String apiKey) {
        LangfuseWorker worker = createWorker(endpointUrl, apiKey);
        Thread t = new Thread(worker);
        t.setName("Langfuse-Worker-Thread");
        t.setDaemon(true);
        t.start();
        this.enabled = true;
    }

    public void recordEvent(String input, Object output, String state) {
        if (!enabled) {
            return;
        }
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

empty definition using pc, found symbol in pc: java/io/PrintStream#println(+8).