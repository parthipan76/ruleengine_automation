package com.sixdee.text2rule.workflow;

import com.sixdee.text2rule.model.RuleTree;
import com.sixdee.text2rule.model.NodeData;
import com.sixdee.text2rule.dto.ValidationResult;
import com.sixdee.text2rule.dto.DecompositionResult;
import org.bsc.langgraph4j.state.AgentState;
import java.util.Map;
import java.util.HashMap;

public class WorkflowState extends AgentState {

    public WorkflowState(Map<String, Object> initData) {
        super(new HashMap<>(initData));
    }

    public String getInput() {
        return (String) this.data().get("input");
    }

    public ValidationResult getValidationResponse() {
        return (ValidationResult) this.data().get("validationResponse");
    }

    public void setValidationResponse(ValidationResult response) {
        this.data().put("validationResponse", response);
    }

    public RuleTree<NodeData> getTree() {
        return (RuleTree<NodeData>) this.data().get("tree");
    }

    public void setTree(RuleTree<NodeData> tree) {
        this.data().put("tree", tree);
    }

    public DecompositionResult getDecompositionResponse() {
        return (DecompositionResult) this.data().get("decompositionResponse");
    }

    public void setDecompositionResponse(DecompositionResult response) {
        this.data().put("decompositionResponse", response);
    }

    public int getRetryCount() {
        return (int) this.data().getOrDefault("retryCount", 0);
    }

    public void incrementRetryCount() {
        int count = getRetryCount();
        this.data().put("retryCount", count + 1);
    }

    public String getFeedback() {
        return (String) this.data().get("feedback");
    }

    public void setFeedback(String feedback) {
        this.data().put("feedback", feedback);
    }

    public int getConditionRetryCount() {
        return (int) this.data().getOrDefault("conditionRetryCount", 0);
    }

    public void incrementConditionRetryCount() {
        int count = getConditionRetryCount();
        this.data().put("conditionRetryCount", count + 1);
    }

    public String getCurrentDecompositionPrompt() {
        return (String) this.data().get("currentDecompositionPrompt");
    }

    public void setCurrentDecompositionPrompt(String prompt) {
        this.data().put("currentDecompositionPrompt", prompt);
    }

    public Double getConsistencyScore() {
        return (Double) this.data().get("consistencyScore");
    }

    public void setConsistencyScore(Double score) {
        this.data().put("consistencyScore", score);
    }

    public String getPreviousOutput() {
        return (String) this.data().get("previousOutput");
    }

    public void setPreviousOutput(String output) {
        this.data().put("previousOutput", output);
    }

    // Condition extraction retry fields
    public String getCurrentConditionPromptKey() {
        return (String) this.data().get("currentConditionPromptKey");
    }

    public void setCurrentConditionPromptKey(String promptKey) {
        this.data().put("currentConditionPromptKey", promptKey);
    }

    public String getCurrentConditionPromptString() {
        return (String) this.data().get("currentConditionPromptString");
    }

    public void setCurrentConditionPromptString(String promptString) {
        this.data().put("currentConditionPromptString", promptString);
    }

    public Double getConditionConsistencyScore() {
        return (Double) this.data().get("conditionConsistencyScore");
    }

    public void setConditionConsistencyScore(Double score) {
        this.data().put("conditionConsistencyScore", score);
    }

    public String getConditionFeedback() {
        return (String) this.data().get("conditionFeedback");
    }

    public void setConditionFeedback(String feedback) {
        this.data().put("conditionFeedback", feedback);
    }

    public String getConditionPreviousOutput() {
        return (String) this.data().get("conditionPreviousOutput");
    }

    public void setConditionPreviousOutput(String output) {
        this.data().put("conditionPreviousOutput", output);
    }

    // Action extraction retry fields
    public String getCurrentActionPromptKey() {
        return (String) this.data().get("currentActionPromptKey");
    }

    public void setCurrentActionPromptKey(String promptKey) {
        this.data().put("currentActionPromptKey", promptKey);
    }

    public Double getActionConsistencyScore() {
        return (Double) this.data().get("actionConsistencyScore");
    }

    public void setActionConsistencyScore(Double score) {
        this.data().put("actionConsistencyScore", score);
    }

    public int getActionRetryCount() {
        return (int) this.data().getOrDefault("actionRetryCount", 0);
    }

    public void incrementActionRetryCount() {
        int count = getActionRetryCount();
        this.data().put("actionRetryCount", count + 1);
    }

    public String getActionFeedback() {
        return (String) this.data().get("actionFeedback");
    }

    public void setActionFeedback(String feedback) {
        this.data().put("actionFeedback", feedback);
    }

    public String getActionPreviousOutput() {
        return (String) this.data().get("actionPreviousOutput");
    }

    public void setActionPreviousOutput(String output) {
        this.data().put("actionPreviousOutput", output);
    }

    public boolean isWorkflowFailed() {
        return (boolean) this.data().getOrDefault("workflowFailed", false);
    }

    public void setWorkflowFailed(boolean failed) {
        this.data().put("workflowFailed", failed);
    }

    public String getFailureReason() {
        return (String) this.data().get("failureReason");
    }

    public void setFailureReason(String reason) {
        this.data().put("failureReason", reason);
    }
}
