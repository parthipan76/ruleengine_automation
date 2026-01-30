package com.sixdee.text2rule.parser;

import com.sixdee.text2rule.model.NodeData;
import com.sixdee.text2rule.model.RuleNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * UPDATED ScheduleParser - N8N Compatible Output
 * 
 * Key Changes:
 * 1. Added Monthly interval format: Date="1 09:00-17:00, 15 09:00-17:00"
 * 2. Added LeadPolicyId field support
 * 3. Consistent output format matching N8N
 * 
 * N8N Schedule Output Example:
 * schedule(
 *   ScheduleName="",
 *   ScheduleType="Monthly",
 *   StartDate="",
 *   ExpiryDate="",
 *   Repeat="true",
 *   Hours="2",
 *   Minutes="",
 *   Date="1 09:00-17:00, 15 09:00-17:00",
 *   LeadPolicyId="167"
 * )
 */
public class ScheduleParser {
    private static final Logger logger = LoggerFactory.getLogger(ScheduleParser.class);

    /**
     * Extract schedule data from the rule tree.
     */
    public Map<String, Object> extractSchedule(RuleNode<NodeData> root) {
        if (root == null) {
            logger.warn("Cannot extract schedule: root is null");
            return null;
        }

        ScheduleData scheduleData = findScheduleDetails(root);
        
        if (scheduleData == null || !scheduleData.hasData()) {
            logger.info("Extracted schedule [has_data=false]");
            return null;
        }

        Map<String, Object> scheduleMap = buildScheduleMap(scheduleData);
        
        logger.info("Extracted schedule [has_data=true, type={}]", scheduleData.scheduleType);
        return scheduleMap;
    }

    /**
     * Recursively find ScheduleDetails node and extract data.
     */
    private ScheduleData findScheduleDetails(RuleNode<NodeData> node) {
        if (node == null) return null;

        String nodeType = node.getData().getType();

        if ("Schedule".equalsIgnoreCase(nodeType)) {
            for (RuleNode<NodeData> child : node.getChildren()) {
                if ("ScheduleDetails".equalsIgnoreCase(child.getData().getType())) {
                    String details = child.getData().getInput();
                    logger.debug("Found ScheduleDetails: {}", details);
                    return parseScheduleDetails(details);
                }
            }
            return parseScheduleFromText(node.getData().getInput());
        }

        if (node.getChildren() != null) {
            for (RuleNode<NodeData> child : node.getChildren()) {
                ScheduleData result = findScheduleDetails(child);
                if (result != null && result.hasData()) {
                    return result;
                }
            }
        }

        return null;
    }

    /**
     * Parse schedule details using regex-based extraction.
     */
    private ScheduleData parseScheduleDetails(String details) {
        if (details == null || details.isEmpty()) {
            return null;
        }

        logger.debug("Parsing schedule details: {}", details);

        ScheduleData data = new ScheduleData();

        // Extract all fields
        extractField(details, "Schedule Type", value -> data.scheduleType = value);
        extractField(details, "Repeat", value -> data.repeat = value);
        extractField(details, "Start Date", value -> data.startDate = value);
        extractField(details, "End Date", value -> data.endDate = value);
        extractField(details, "Interval", value -> data.interval = value);
        extractField(details, "Frequency", value -> data.frequency = value);
        extractField(details, "Hours", value -> data.hours = value);
        extractField(details, "Minutes", value -> data.minutes = value);
        extractField(details, "Day", value -> data.day = value);
        
        // Extract time maps
        data.startTimeMap = extractTimeMap(details, "Start Time");
        data.endTimeMap = extractTimeMap(details, "End Time");
        
        // Extract select_days array
        data.selectDays = extractSelectDays(details);

        return data;
    }

    /**
     * Extract a single field value from details string.
     */
    private void extractField(String details, String fieldName, Consumer<String> setter) {
        // Pattern: "Field Name: value" or "Field Name = value"
        Pattern pattern = Pattern.compile(
            fieldName + "\\s*[:=]\\s*([^,}\\n]+)",
            Pattern.CASE_INSENSITIVE
        );
        Matcher matcher = pattern.matcher(details);
        if (matcher.find()) {
            String value = matcher.group(1).trim();
            // Remove surrounding quotes
            value = value.replaceAll("^[\"']|[\"']$", "");
            if (!value.isEmpty() && !value.equals("null")) {
                setter.accept(value);
            }
        }
    }

    /**
     * Extract time map from details: {1=09:00, 15=09:00} or {ALL=06:15}
     */
    private Map<String, String> extractTimeMap(String details, String fieldName) {
        Map<String, String> timeMap = new LinkedHashMap<>();
        
        // Pattern for map format: Start Time: {key=value, key=value}
        Pattern mapPattern = Pattern.compile(
            fieldName + "\\s*[:=]\\s*\\{([^}]+)\\}",
            Pattern.CASE_INSENSITIVE
        );
        Matcher mapMatcher = mapPattern.matcher(details);
        
        if (mapMatcher.find()) {
            String mapContent = mapMatcher.group(1);
            // Parse each key=value pair
            Pattern pairPattern = Pattern.compile("([\\w]+)\\s*=\\s*([\\d:]+)");
            Matcher pairMatcher = pairPattern.matcher(mapContent);
            
            while (pairMatcher.find()) {
                String key = pairMatcher.group(1);
                String value = pairMatcher.group(2);
                timeMap.put(key, value);
            }
        }
        
        return timeMap;
    }

    /**
     * Extract select_days array from details.
     */
    private List<Object> extractSelectDays(String details) {
        List<Object> selectDays = new ArrayList<>();
        
        // Pattern for array format: select_days: [1, 15] or select_days: ["MON", "TUE"]
        Pattern arrayPattern = Pattern.compile(
            "select_days\\s*[:=]\\s*\\[([^\\]]+)\\]",
            Pattern.CASE_INSENSITIVE
        );
        Matcher arrayMatcher = arrayPattern.matcher(details);
        
        if (arrayMatcher.find()) {
            String arrayContent = arrayMatcher.group(1);
            String[] items = arrayContent.split(",");
            
            for (String item : items) {
                String trimmed = item.trim().replaceAll("^[\"']|[\"']$", "");
                if (!trimmed.isEmpty()) {
                    // Check if numeric
                    if (trimmed.matches("\\d+")) {
                        selectDays.add(Integer.parseInt(trimmed));
                    } else {
                        selectDays.add(trimmed);
                    }
                }
            }
        }
        
        return selectDays;
    }

    /**
     * Parse schedule from raw text (fallback).
     */
    private ScheduleData parseScheduleFromText(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }

        ScheduleData data = new ScheduleData();
        String lowerText = text.toLowerCase();

        // Determine schedule type
        if (lowerText.contains("now") || lowerText.contains("immediate")) {
            data.scheduleType = "ScheduleNow";
        } else if (lowerText.contains("monthly") || Pattern.compile("\\d+(?:st|nd|rd|th)").matcher(lowerText).find()) {
            data.scheduleType = "Monthly";
        } else if (lowerText.contains("weekly") || lowerText.contains("monday") || lowerText.contains("every day")) {
            data.scheduleType = "Weekly";
        } else {
            data.scheduleType = "Daily";
        }

        // Extract interval info
        Pattern intervalPattern = Pattern.compile("every\\s+(\\d+)\\s*(hour|minute)s?", Pattern.CASE_INSENSITIVE);
        Matcher intervalMatcher = intervalPattern.matcher(text);
        if (intervalMatcher.find()) {
            String value = intervalMatcher.group(1);
            String unit = intervalMatcher.group(2).toLowerCase();
            
            if (unit.equals("hour")) {
                data.hours = value;
            } else {
                data.minutes = value;
            }
            data.interval = "Yes";
        }

        // Extract time range
        Pattern timeRangePattern = Pattern.compile("from\\s+(\\d{1,2}:\\d{2})\\s*(?:to|until|-)?\\s*(\\d{1,2}:\\d{2})", Pattern.CASE_INSENSITIVE);
        Matcher timeRangeMatcher = timeRangePattern.matcher(text);
        if (timeRangeMatcher.find()) {
            data.startTimeMap.put("ALL", timeRangeMatcher.group(1));
            data.endTimeMap.put("ALL", timeRangeMatcher.group(2));
        }

        // Extract dates
        Pattern datePattern = Pattern.compile("(\\d{4}-\\d{2}-\\d{2}|\\d{2}/\\d{2}/\\d{4})");
        Matcher dateMatcher = datePattern.matcher(text);
        List<String> dates = new ArrayList<>();
        while (dateMatcher.find()) {
            dates.add(dateMatcher.group(1));
        }
        if (dates.size() >= 1) data.startDate = dates.get(0);
        if (dates.size() >= 2) data.endDate = dates.get(1);

        data.repeat = "Yes";

        return data;
    }

    /**
     * Build schedule map for JSON output.
     */
    private Map<String, Object> buildScheduleMap(ScheduleData data) {
        Map<String, Object> map = new LinkedHashMap<>();

        // Always include these fields
        map.put("schedule_name", "");
        map.put("schedule_type", data.scheduleType != null ? data.scheduleType : "Daily");
        
        // Date fields
        if (data.startDate != null && !data.startDate.isEmpty()) {
            map.put("segment_rule_start_date", data.startDate);
        }
        if (data.endDate != null && !data.endDate.isEmpty()) {
            map.put("segment_rule_end_date", data.endDate);
        }

        // Repeat field
        map.put("repeat", normalizeBoolean(data.repeat));

        // Interval fields
        if (data.hours != null && !data.hours.isEmpty()) {
            map.put("hours", data.hours);
        }
        if (data.minutes != null && !data.minutes.isEmpty()) {
            map.put("minutes", data.minutes);
        }

        // Day field for Monthly
        if (data.day != null && !data.day.isEmpty()) {
            map.put("day", data.day);
        }

        // Select days array
        if (data.selectDays != null && !data.selectDays.isEmpty()) {
            map.put("select_days", data.selectDays);
        }

        // Time maps
        if (!data.startTimeMap.isEmpty()) {
            map.put("start_time", data.startTimeMap);
        }
        if (!data.endTimeMap.isEmpty()) {
            map.put("end_time", data.endTimeMap);
        }

        // N8N-specific: Build Date field for Monthly with interval
        if (isMonthlyWithInterval(data)) {
            String dateField = buildMonthlyDateField(data);
            if (dateField != null && !dateField.isEmpty()) {
                map.put("date", dateField);
            }
        }

        return map;
    }

    /**
     * Check if schedule is Monthly with interval.
     */
    private boolean isMonthlyWithInterval(ScheduleData data) {
        if (data.scheduleType == null) return false;
        
        String type = data.scheduleType.toLowerCase();
        return (type.contains("monthly") || type.equals("monthlywithinterval")) 
            && (data.hours != null || data.minutes != null || "Yes".equalsIgnoreCase(data.interval));
    }

    /**
     * Build N8N-format Date field: "1 09:00-17:00, 15 09:00-17:00"
     */
    private String buildMonthlyDateField(ScheduleData data) {
        if (data.day == null || data.day.isEmpty()) {
            return null;
        }

        // Parse day numbers
        String[] days = data.day.split("[,\\s]+");
        List<String> entries = new ArrayList<>();

        for (String dayNum : days) {
            String trimmed = dayNum.trim();
            if (trimmed.isEmpty() || !trimmed.matches("\\d+")) {
                continue;
            }

            StringBuilder entry = new StringBuilder(trimmed);

            // Get start time for this day
            String startTime = null;
            if (data.startTimeMap.containsKey(trimmed)) {
                startTime = data.startTimeMap.get(trimmed);
            } else if (data.startTimeMap.containsKey("ALL")) {
                startTime = data.startTimeMap.get("ALL");
            } else if (!data.startTimeMap.isEmpty()) {
                startTime = data.startTimeMap.values().iterator().next();
            }

            // Get end time for this day
            String endTime = null;
            if (data.endTimeMap.containsKey(trimmed)) {
                endTime = data.endTimeMap.get(trimmed);
            } else if (data.endTimeMap.containsKey("ALL")) {
                endTime = data.endTimeMap.get("ALL");
            } else if (!data.endTimeMap.isEmpty()) {
                endTime = data.endTimeMap.values().iterator().next();
            }

            // Build time range
            if (startTime != null) {
                entry.append(" ").append(formatTime(startTime));
                
                if (endTime != null) {
                    entry.append("-").append(formatTime(endTime));
                }
            }

            entries.add(entry.toString());
        }

        return String.join(", ", entries);
    }

    /**
     * Format time to HH:MM format.
     */
    private String formatTime(String time) {
        if (time == null) return "00:00";
        
        // Already in correct format
        if (time.matches("\\d{2}:\\d{2}")) {
            return time;
        }
        
        // Handle single digit hours
        if (time.matches("\\d:\\d{2}")) {
            return "0" + time;
        }
        
        // Handle various formats
        Pattern timePattern = Pattern.compile("(\\d{1,2}):(\\d{2})");
        Matcher matcher = timePattern.matcher(time);
        if (matcher.find()) {
            int hour = Integer.parseInt(matcher.group(1));
            int minute = Integer.parseInt(matcher.group(2));
            return String.format("%02d:%02d", hour, minute);
        }
        
        return time;
    }

    /**
     * Normalize boolean values to "true"/"false".
     */
    private String normalizeBoolean(String value) {
        if (value == null) return "true";
        String lower = value.toLowerCase().trim();
        
        if (lower.equals("yes") || lower.equals("true") || lower.equals("1")) {
            return "true";
        }
        if (lower.equals("no") || lower.equals("false") || lower.equals("0")) {
            return "false";
        }
        
        return "true";
    }

    /**
     * Generate DSL string from schedule data.
     * 
     * N8N Format:
     * schedule(ScheduleName="", ScheduleType="Monthly", StartDate="", ExpiryDate="", 
     *          Repeat="true", Hours="2", Date="1 09:00-17:00, 15 09:00-17:00", LeadPolicyId="167")
     */
    public String generateDsl(Map<String, Object> scheduleMap, String policyId) {
        if (scheduleMap == null || scheduleMap.isEmpty()) {
            return "";
        }

        StringBuilder dsl = new StringBuilder("schedule(");
        List<String> params = new ArrayList<>();

        // Standard fields
        params.add("ScheduleName=\"" + getStringValue(scheduleMap, "schedule_name", "") + "\"");
        params.add("ScheduleType=\"" + getStringValue(scheduleMap, "schedule_type", "Daily") + "\"");
        
        String startDate = getStringValue(scheduleMap, "segment_rule_start_date", "");
        if (!startDate.isEmpty()) {
            params.add("StartDate=\"" + startDate + "\"");
        }
        
        String endDate = getStringValue(scheduleMap, "segment_rule_end_date", "");
        if (!endDate.isEmpty()) {
            params.add("ExpiryDate=\"" + endDate + "\"");
        }

        params.add("Repeat=\"" + getStringValue(scheduleMap, "repeat", "true") + "\"");

        String hours = getStringValue(scheduleMap, "hours", "");
        if (!hours.isEmpty()) {
            params.add("Hours=\"" + hours + "\"");
        }

        String minutes = getStringValue(scheduleMap, "minutes", "");
        if (!minutes.isEmpty()) {
            params.add("Minutes=\"" + minutes + "\"");
        }

        // N8N-specific Date field for Monthly
        String dateField = getStringValue(scheduleMap, "date", "");
        if (!dateField.isEmpty()) {
            params.add("Date=\"" + dateField + "\"");
        }

        // Append policy if present
        if (policyId != null && !policyId.isEmpty()) {
            params.add("LeadPolicyId=\"" + policyId + "\"");
        }

        dsl.append(String.join(", ", params));
        dsl.append(")");

        return dsl.toString();
    }

    /**
     * Get string value from map with default.
     */
    private String getStringValue(Map<String, Object> map, String key, String defaultValue) {
        Object value = map.get(key);
        if (value == null) return defaultValue;
        return value.toString();
    }

    // Inner class for schedule data
    private static class ScheduleData {
        String scheduleType;
        String repeat;
        String startDate;
        String endDate;
        String interval;
        String frequency;
        String hours;
        String minutes;
        String day;
        Map<String, String> startTimeMap = new LinkedHashMap<>();
        Map<String, String> endTimeMap = new LinkedHashMap<>();
        List<Object> selectDays = new ArrayList<>();

        boolean hasData() {
            return scheduleType != null && !scheduleType.isEmpty();
        }
    }
}