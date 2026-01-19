package com.sixdee.text2rule.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class UnifiedRuleResult {
    private List<Condition> conditions;
    private List<Action> actions;
    private List<KpiMatch> kpis;
    @JsonProperty("if_instruction")
    private String ifInstruction;

    public List<Condition> getConditions() {
        return conditions;
    }

    public void setConditions(List<Condition> conditions) {
        this.conditions = conditions;
    }

    public List<Action> getActions() {
        return actions;
    }

    public void setActions(List<Action> actions) {
        this.actions = actions;
    }

    public List<KpiMatch> getKpis() {
        return kpis;
    }

    public void setKpis(List<KpiMatch> kpis) {
        this.kpis = kpis;
    }

    public String getIfInstruction() {
        return ifInstruction;
    }

    public void setIfInstruction(String ifInstruction) {
        this.ifInstruction = ifInstruction;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Condition {
        private String field;
        private String operator;
        private String value;

        public String getField() {
            return field;
        }

        public void setField(String field) {
            this.field = field;
        }

        public String getOperator() {
            return operator;
        }

        public void setOperator(String operator) {
            this.operator = operator;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Action {
        private String type;
        private String value;

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }
    }
}
