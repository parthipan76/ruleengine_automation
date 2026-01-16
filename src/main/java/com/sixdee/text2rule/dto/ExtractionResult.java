package com.sixdee.text2rule.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serializable;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ExtractionResult implements Serializable {
    @JsonProperty("condition")
    private String condition;

    @JsonProperty("actions")
    private String actions;

    @JsonProperty("input_text")
    private String inputText;

    @JsonProperty("sampling")
    private String sampling;

    @JsonProperty("policy")
    private String policy;

    @JsonProperty("schedule")
    private String schedule;

    public String getCondition() {
        return condition;
    }

    public void setCondition(String condition) {
        this.condition = condition;
    }

    public String getActions() {
        return actions;
    }

    public void setActions(String actions) {
        this.actions = actions;
    }

    public String getInputText() {
        return inputText;
    }

    public void setInputText(String inputText) {
        this.inputText = inputText;
    }

    public String getSampling() {
        return sampling;
    }

    public void setSampling(String sampling) {
        this.sampling = sampling;
    }

    public String getPolicy() {
        return policy;
    }

    public void setPolicy(String policy) {
        this.policy = policy;
    }

    public String getSchedule() {
        return schedule;
    }

    public void setSchedule(String schedule) {
        this.schedule = schedule;
    }

    @JsonProperty("rule")
    private String rule;

    public String getRule() {
        return rule;
    }

    public void setRule(String rule) {
        this.rule = rule;
    }

    @Override
    public String toString() {
        return "ExtractionResult{rule='" + rule + "', condition='" + condition + "', actions='" + actions +
                "', sampling='" + sampling + "', policy='" + policy + "', schedule='" + schedule + "'}";
    }
}
