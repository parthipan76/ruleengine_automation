package com.sixdee.text2rule.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class KpiMatchingResult {
    private List<KpiMatch> matches;

    public List<KpiMatch> getMatches() {
        return matches;
    }

    public void setMatches(List<KpiMatch> matches) {
        this.matches = matches;
    }
}
