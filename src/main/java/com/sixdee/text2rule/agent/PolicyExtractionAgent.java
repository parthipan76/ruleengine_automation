package com.sixdee.text2rule.agent;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sixdee.text2rule.config.MySQLConnectionManager;
import com.sixdee.text2rule.factory.LLMClientFactory;

/**
 * Agent for extracting Policy information from rule text.
 * 
 * Identifies and extracts:
 * 1. LeadPolicy - Campaign suppression policies (e.g., "suppressed from Baby Care Program")
 * 2. ContactPolicy - Contact frequency policies
 * 
 * Output matches N8N format:
 * - LeadPolicyId="167"
 * - NewContactPolicyId="234"
 */
public class PolicyExtractionAgent implements IAgent {
    private static final Logger logger = LoggerFactory.getLogger(PolicyExtractionAgent.class);

    private final LLMClientFactory llmClient;
    private final ObjectMapper objectMapper;
    private final MySQLConnectionManager dbManager;

    // Patterns for policy detection
    private static final Pattern SUPPRESSION_PATTERN = Pattern.compile(
        "(?:suppress(?:ed|ion)?|exclude(?:d)?|block(?:ed)?|remove(?:d)?)\\s+(?:from|in)\\s+([\\w\\s]+?)\\s+(?:for|during|within)\\s+(\\d+)\\s*(day|week|month|hour)s?",
        Pattern.CASE_INSENSITIVE
    );

    private static final Pattern CAMPAIGN_NAME_PATTERN = Pattern.compile(
        "(?:from|in)\\s+([A-Z][\\w\\s]+(?:Program|Campaign|Offer|Promotion|Plan))",
        Pattern.CASE_INSENSITIVE
    );

    private static final Pattern ALL_CAMPAIGNS_PATTERN = Pattern.compile(
        "(?:all\\s+campaigns?|any\\s+campaigns?|every\\s+campaigns?)",
        Pattern.CASE_INSENSITIVE
    );

    private static final Pattern CONTACT_POLICY_PATTERN = Pattern.compile(
        "(?:contact|communicate|reach)\\s+(?:no\\s+more\\s+than|at\\s+most|maximum)\\s+(\\d+)\\s+times?\\s+(?:per|every|in)\\s+(day|week|month)",
        Pattern.CASE_INSENSITIVE
    );

    public PolicyExtractionAgent(LLMClientFactory llmClient) {
        this.llmClient = llmClient;
        this.objectMapper = new ObjectMapper();
        this.dbManager = MySQLConnectionManager.getInstance();
    }

    @Override
    public String execute(String input) {
        if (input == null || input.trim().isEmpty()) {
            return "{}";
        }

        logger.info("Extracting policy from: {}", input);

        try {
            PolicyResult result = new PolicyResult();

            // Step 1: Classify policy type
            PolicyType type = classifyPolicyType(input);
            result.policyType = type.name();

            // Step 2: Extract based on type
            switch (type) {
                case LEAD_POLICY:
                    extractLeadPolicy(input, result);
                    break;
                case CONTACT_POLICY:
                    extractContactPolicy(input, result);
                    break;
                case NONE:
                default:
                    logger.info("No policy detected in input");
                    break;
            }

            return objectMapper.writeValueAsString(result);

        } catch (Exception e) {
            logger.error("Error extracting policy", e);
            return "{}";
        }
    }

    /**
     * Classify the policy type from input text.
     */
    private PolicyType classifyPolicyType(String input) {
        String lowerInput = input.toLowerCase();

        // Check for suppression keywords (Lead Policy)
        if (lowerInput.contains("suppress") || 
            lowerInput.contains("exclude") ||
            lowerInput.contains("block from") ||
            (lowerInput.contains("program") && lowerInput.contains("period"))) {
            return PolicyType.LEAD_POLICY;
        }

        // Check for contact frequency keywords (Contact Policy)
        if (lowerInput.contains("contact no more than") ||
            lowerInput.contains("maximum contacts") ||
            lowerInput.contains("contact frequency")) {
            return PolicyType.CONTACT_POLICY;
        }

        // Use LLM for ambiguous cases
        return classifyWithLLM(input);
    }

    /**
     * Use LLM to classify policy type.
     */
    private PolicyType classifyWithLLM(String input) {
        try {
            String prompt = """
                Classify the following text into one of these categories:
                - LEAD_POLICY: If it mentions campaign suppression, excluding from campaigns, blocking from programs
                - CONTACT_POLICY: If it mentions contact frequency limits, maximum number of communications
                - NONE: If no policy is mentioned
                
                TEXT: %s
                
                Return ONLY one word: LEAD_POLICY, CONTACT_POLICY, or NONE
                """.formatted(input);

            String response = llmClient.chat(prompt);
            String trimmed = response.trim().toUpperCase();

            if (trimmed.contains("LEAD_POLICY")) return PolicyType.LEAD_POLICY;
            if (trimmed.contains("CONTACT_POLICY")) return PolicyType.CONTACT_POLICY;
            return PolicyType.NONE;

        } catch (Exception e) {
            logger.warn("LLM classification failed", e);
            return PolicyType.NONE;
        }
    }

    /**
     * Extract Lead Policy details.
     */
    private void extractLeadPolicy(String input, PolicyResult result) {
        // Check for "all campaigns" pattern
        Matcher allCampaignsMatcher = ALL_CAMPAIGNS_PATTERN.matcher(input);
        if (allCampaignsMatcher.find()) {
            result.isAllCampaigns = true;
            result.campaignName = "ALL";
        }

        // Extract campaign name
        Matcher campaignMatcher = CAMPAIGN_NAME_PATTERN.matcher(input);
        if (campaignMatcher.find() && !result.isAllCampaigns) {
            result.campaignName = campaignMatcher.group(1).trim();
        }

        // Extract suppression period
        Matcher suppressionMatcher = SUPPRESSION_PATTERN.matcher(input);
        if (suppressionMatcher.find()) {
            if (result.campaignName == null || result.campaignName.isEmpty()) {
                result.campaignName = suppressionMatcher.group(1).trim();
            }
            result.suppressionDays = suppressionMatcher.group(2);
            result.suppressionUnit = suppressionMatcher.group(3).toLowerCase();
            
            // Convert to days
            result.expiryDays = calculateDays(result.suppressionDays, result.suppressionUnit);
        }

        // Query database for LeadPolicyId
        if (result.campaignName != null && !result.campaignName.isEmpty()) {
            String policyId = dbManager.findLeadPolicyId(result.campaignName);
            if (policyId != null) {
                result.leadPolicyId = policyId;
            } else {
                // Use LLM extraction as fallback
                result.leadPolicyId = extractPolicyIdWithLLM(input, "LEAD_POLICY");
            }
        }

        logger.info("Extracted LeadPolicy: campaignName={}, leadPolicyId={}, expiryDays={}",
                result.campaignName, result.leadPolicyId, result.expiryDays);
    }

    /**
     * Extract Contact Policy details.
     */
    private void extractContactPolicy(String input, PolicyResult result) {
        Matcher contactMatcher = CONTACT_POLICY_PATTERN.matcher(input);
        
        if (contactMatcher.find()) {
            result.frequency = contactMatcher.group(1);
            result.frequencyUnit = contactMatcher.group(2).toLowerCase();
        }

        // Query database for ContactPolicyId
        if (result.frequency != null) {
            String policyId = dbManager.findContactPolicyId("campaign", result.frequency, result.frequencyUnit);
            if (policyId != null) {
                result.contactPolicyId = policyId;
            }
        }

        logger.info("Extracted ContactPolicy: frequency={}/{}, contactPolicyId={}",
                result.frequency, result.frequencyUnit, result.contactPolicyId);
    }

    /**
     * Use LLM to extract policy ID.
     */
    private String extractPolicyIdWithLLM(String input, String policyType) {
        try {
            String prompt = """
                Extract the policy ID for the following %s from this text.
                If no specific ID is mentioned, return "null".
                
                TEXT: %s
                
                Return ONLY the policy ID number or "null":
                """.formatted(policyType, input);

            String response = llmClient.chat(prompt);
            String trimmed = response.trim();

            // Check if response is a valid number
            if (trimmed.matches("\\d+")) {
                return trimmed;
            }

        } catch (Exception e) {
            logger.warn("LLM policy extraction failed", e);
        }

        return null;
    }

    /**
     * Calculate days from value and unit.
     */
    private String calculateDays(String value, String unit) {
        try {
            int numValue = Integer.parseInt(value);
            
            switch (unit) {
                case "hour":
                    return String.valueOf(Math.max(1, numValue / 24));
                case "day":
                    return String.valueOf(numValue);
                case "week":
                    return String.valueOf(numValue * 7);
                case "month":
                    return String.valueOf(numValue * 30);
                default:
                    return value;
            }
        } catch (NumberFormatException e) {
            return value;
        }
    }

    /**
     * Get policy DSL string for schedule.
     * Returns: LeadPolicyId="167" or NewContactPolicyId="234"
     */
    public static String toDslFormat(String jsonString) {
        if (jsonString == null || jsonString.isEmpty() || "{}".equals(jsonString)) {
            return "";
        }

        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode json = mapper.readTree(jsonString);

            StringBuilder dsl = new StringBuilder();

            if (json.has("leadPolicyId") && !json.get("leadPolicyId").isNull()) {
                dsl.append("LeadPolicyId=\"").append(json.get("leadPolicyId").asText()).append("\"");
            } else if (json.has("contactPolicyId") && !json.get("contactPolicyId").isNull()) {
                dsl.append("NewContactPolicyId=\"").append(json.get("contactPolicyId").asText()).append("\"");
            }

            return dsl.toString();

        } catch (Exception e) {
            logger.error("Error converting policy to DSL", e);
            return "";
        }
    }

    // Inner classes

    private enum PolicyType {
        LEAD_POLICY,
        CONTACT_POLICY,
        NONE
    }

    private static class PolicyResult {
        String policyType = "NONE";
        
        // Lead Policy fields
        String leadPolicyId = null;
        String campaignName = null;
        boolean isAllCampaigns = false;
        String suppressionDays = null;
        String suppressionUnit = null;
        String expiryDays = null;
        
        // Contact Policy fields
        String contactPolicyId = null;
        String frequency = null;
        String frequencyUnit = null;
    }
}