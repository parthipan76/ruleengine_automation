package com.sixdee.text2rule.observability;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class ObservabilityEvent {
    private final String traceId;
    private final String agentName;
    private final List<Map<String, String>> messages;
    private final String agentOutput;
    private final String agentState;
    private final String model;
    private final Map<String, Object> modelParameters;
    private final Map<String, Object> metadata;

    public ObservabilityEvent(String traceId, String agentName, List<Map<String, String>> messages, String agentOutput,
            String agentState, String model, Map<String, Object> modelParameters, Map<String, Object> metadata) {
        this.traceId = traceId;
        this.agentName = agentName;
        this.messages = messages != null ? messages : Collections.emptyList();
        this.agentOutput = agentOutput;
        this.agentState = agentState;
        this.model = model;
        this.modelParameters = modelParameters != null ? modelParameters : Collections.emptyMap();
        this.metadata = metadata != null ? metadata : Collections.emptyMap();
    }

    public String getTraceId() {
        return traceId;
    }

    public String getAgentName() {
        return agentName;
    }

    public List<Map<String, String>> getMessages() {
        return messages;
    }

    public String getAgentOutput() {
        return agentOutput;
    }

    public String getAgentState() {
        return agentState;
    }

    public String getModel() {
        return model;
    }

    public Map<String, Object> getModelParameters() {
        return modelParameters;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    @Override
    public String toString() {
        return "ObservabilityEvent{" +
                "traceId='" + traceId + '\'' +
                ", agentName='" + agentName + '\'' +
                ", messages=" + messages +
                ", agentOutput='" + agentOutput + '\'' +
                ", agentState='" + agentState + '\'' +
                ", model='" + model + '\'' +
                '}';
    }
}
