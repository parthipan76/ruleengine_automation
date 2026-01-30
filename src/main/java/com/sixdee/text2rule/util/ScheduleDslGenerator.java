package com.sixdee.text2rule.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Utility class for generating consistent Schedule DSL strings from parsed schedule JSON.
 * 
 * Generates output format like:
 * schedule(ScheduleName="Daily", ScheduleType="Daily", StartTime="ALL:10:00", Repeat="Yes", Day="ALL", SelectDays="ALL")
 */
public class ScheduleDslGenerator {
    private static final Logger logger = LoggerFactory.getLogger(ScheduleDslGenerator.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Generate DSL string from schedule JSON.
     * 
     * @param scheduleJson The parsed schedule JSON object
     * @return DSL string in format: schedule(key="value", ...)
     */
    public static String generateDsl(String scheduleJson) {
        if (scheduleJson == null || scheduleJson.trim().isEmpty() || "{}".equals(scheduleJson.trim())) {
            logger.debug("Empty schedule JSON, returning empty DSL");
            return "";
        }

        try {
            JsonNode root = objectMapper.readTree(scheduleJson);
            
            // Handle array wrapper (if present)
            if (root.isArray() && root.size() > 0) {
                root = root.get(0);
            }
            
            if (root == null || root.isEmpty()) {
                return "";
            }

            String scheduleType = getTextValue(root, "schedule_type");
            if (scheduleType == null || scheduleType.isEmpty()) {
                logger.warn("Missing schedule_type in JSON");
                return "";
            }

            StringBuilder dsl = new StringBuilder("schedule(");
            List<String> params = new ArrayList<>();

            // Build parameters based on schedule type
            switch (scheduleType) {
                case "ScheduleNow":
                    params.add(formatParam("ScheduleName", scheduleType));
                    params.add(formatParam("ScheduleType", scheduleType));
                    break;

                case "Daily":
                    params.add(formatParam("ScheduleName", scheduleType));
                    params.add(formatParam("ScheduleType", scheduleType));
                    addDateParams(root, params);
                    addStartTimeParam(root, params);
                    addRepeatParam(root, params);
                    params.add(formatParam("Day", "ALL"));
                    params.add(formatParam("SelectDays", "ALL"));
                    break;

                case "DailyWithInterval":
                    params.add(formatParam("ScheduleName", scheduleType));
                    params.add(formatParam("ScheduleType", scheduleType));
                    addDateParams(root, params);
                    params.add(formatParam("Interval", "Yes"));
                    addFrequencyParam(root, params);
                    addStartTimeParam(root, params);
                    addEndTimeParam(root, params);
                    addRepeatParam(root, params);
                    addHoursMinutesParams(root, params);
                    params.add(formatParam("Day", "ALL"));
                    params.add(formatParam("SelectDays", "ALL"));
                    break;

                case "Weekly":
                    params.add(formatParam("ScheduleName", scheduleType));
                    params.add(formatParam("ScheduleType", scheduleType));
                    addDateParams(root, params);
                    addStartTimeParam(root, params);
                    addRepeatParam(root, params);
                    addDayParam(root, params);
                    addSelectDaysParam(root, params);
                    break;

                case "WeeklyWithInterval":
                    params.add(formatParam("ScheduleName", scheduleType));
                    params.add(formatParam("ScheduleType", scheduleType));
                    addDateParams(root, params);
                    params.add(formatParam("Interval", "Yes"));
                    addFrequencyParam(root, params);
                    addStartTimeParam(root, params);
                    addEndTimeParam(root, params);
                    addRepeatParam(root, params);
                    addHoursMinutesParams(root, params);
                    addDayParam(root, params);
                    addSelectDaysParam(root, params);
                    break;

                case "Monthly":
                    params.add(formatParam("ScheduleName", scheduleType));
                    params.add(formatParam("ScheduleType", scheduleType));
                    addDateParams(root, params);
                    addStartTimeParam(root, params);
                    addRepeatParam(root, params);
                    addDayParam(root, params);
                    addSelectDaysParam(root, params);
                    break;

                case "MonthlyWithInterval":
                    params.add(formatParam("ScheduleName", scheduleType));
                    params.add(formatParam("ScheduleType", scheduleType));
                    addDateParams(root, params);
                    params.add(formatParam("Interval", "Yes"));
                    addFrequencyParam(root, params);
                    addStartTimeParam(root, params);
                    addEndTimeParam(root, params);
                    addRepeatParam(root, params);
                    addHoursMinutesParams(root, params);
                    addDayParam(root, params);
                    addSelectDaysParam(root, params);
                    break;

                case "MonthlyWithSpecifics":
                    params.add(formatParam("ScheduleName", scheduleType));
                    params.add(formatParam("ScheduleType", scheduleType));
                    addDateParams(root, params);
                    addStartTimeParam(root, params);
                    addRepeatParam(root, params);
                    addTypeParam(root, params);
                    addPeriodParam(root, params);
                    addWeekParam(root, params);
                    addDayParam(root, params);
                    addSelectDaysParam(root, params);
                    break;

                case "Interval":
                    params.add(formatParam("ScheduleName", scheduleType));
                    params.add(formatParam("ScheduleType", scheduleType));
                    addDateParams(root, params);
                    addIntervalDescParam(root, params);
                    addStartTimeParam(root, params);
                    addRepeatParam(root, params);
                    addHoursMinutesParams(root, params);
                    addSelectDaysParam(root, params);
                    break;

                default:
                    logger.warn("Unknown schedule type: {}", scheduleType);
                    params.add(formatParam("ScheduleName", scheduleType));
                    params.add(formatParam("ScheduleType", scheduleType));
            }

            dsl.append(String.join(", ", params));
            dsl.append(")");

            String result = dsl.toString();
            logger.info("Generated schedule DSL: {}", result);
            return result;

        } catch (Exception e) {
            logger.error("Failed to generate schedule DSL from JSON: {}", scheduleJson, e);
            return "";
        }
    }

    /**
     * Generate DSL directly from a parsed ScheduleParserResult DTO.
     */
    public static String generateDsl(com.sixdee.text2rule.dto.ScheduleParserResult result) {
        if (result == null || result.getScheduleType() == null || result.getScheduleType().isEmpty()) {
            return "";
        }

        try {
            String json = objectMapper.writeValueAsString(result);
            return generateDsl(json);
        } catch (Exception e) {
            logger.error("Failed to convert ScheduleParserResult to DSL", e);
            return "";
        }
    }

    // Helper methods for building parameters

    private static String formatParam(String key, String value) {
        return key + "=\"" + (value != null ? value : "") + "\"";
    }

    private static String getTextValue(JsonNode node, String field) {
        JsonNode fieldNode = node.get(field);
        if (fieldNode == null || fieldNode.isNull()) {
            return null;
        }
        return fieldNode.asText();
    }

    private static void addDateParams(JsonNode root, List<String> params) {
        String startDate = getTextValue(root, "segment_rule_start_date");
        String endDate = getTextValue(root, "segment_rule_end_date");
        
        if (startDate != null && !startDate.isEmpty() && !"null".equals(startDate)) {
            params.add(formatParam("StartDate", startDate));
        }
        if (endDate != null && !endDate.isEmpty() && !"null".equals(endDate)) {
            params.add(formatParam("ExpiryDate", endDate));
        }
    }

    private static void addRepeatParam(JsonNode root, List<String> params) {
        String repeat = getTextValue(root, "repeat");
        if (repeat != null && !repeat.isEmpty()) {
            params.add(formatParam("Repeat", repeat));
        }
    }

    private static void addFrequencyParam(JsonNode root, List<String> params) {
        String frequency = getTextValue(root, "frequency");
        if (frequency != null && !frequency.isEmpty()) {
            params.add(formatParam("Frequency", frequency));
        }
    }

    private static void addHoursMinutesParams(JsonNode root, List<String> params) {
        String hours = getTextValue(root, "hours");
        String minutes = getTextValue(root, "minutes");
        
        if (hours != null && !hours.isEmpty()) {
            params.add(formatParam("Hours", hours));
        }
        if (minutes != null && !minutes.isEmpty()) {
            params.add(formatParam("Minutes", minutes));
        }
    }

    private static void addDayParam(JsonNode root, List<String> params) {
        String day = getTextValue(root, "day");
        if (day != null && !day.isEmpty()) {
            params.add(formatParam("Day", day));
        }
    }

    private static void addSelectDaysParam(JsonNode root, List<String> params) {
        JsonNode selectDays = root.get("select_days");
        if (selectDays != null && selectDays.isArray() && selectDays.size() > 0) {
            List<String> days = new ArrayList<>();
            for (JsonNode dayNode : selectDays) {
                if (dayNode.isTextual()) {
                    days.add(dayNode.asText());
                } else if (dayNode.isNumber()) {
                    days.add(String.valueOf(dayNode.asInt()));
                }
            }
            params.add(formatParam("SelectDays", String.join(",", days)));
        }
    }

    private static void addStartTimeParam(JsonNode root, List<String> params) {
        JsonNode startTime = root.get("start_time");
        if (startTime != null && startTime.isObject() && startTime.size() > 0) {
            List<String> timeParts = new ArrayList<>();
            Iterator<Map.Entry<String, JsonNode>> fields = startTime.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                timeParts.add(entry.getKey() + ":" + entry.getValue().asText());
            }
            params.add(formatParam("StartTime", String.join("|", timeParts)));
        }
    }

    private static void addEndTimeParam(JsonNode root, List<String> params) {
        JsonNode endTime = root.get("end_time");
        if (endTime != null && endTime.isObject() && endTime.size() > 0) {
            List<String> timeParts = new ArrayList<>();
            Iterator<Map.Entry<String, JsonNode>> fields = endTime.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                timeParts.add(entry.getKey() + ":" + entry.getValue().asText());
            }
            params.add(formatParam("EndTime", String.join("|", timeParts)));
        }
    }

    private static void addTypeParam(JsonNode root, List<String> params) {
        String type = getTextValue(root, "type");
        if (type != null && !type.isEmpty() && !"null".equals(type)) {
            params.add(formatParam("Type", type));
        }
    }

    private static void addPeriodParam(JsonNode root, List<String> params) {
        String period = getTextValue(root, "period");
        if (period != null && !period.isEmpty()) {
            params.add(formatParam("Period", period));
        }
    }

    private static void addWeekParam(JsonNode root, List<String> params) {
        String week = getTextValue(root, "week");
        if (week != null && !week.isEmpty()) {
            params.add(formatParam("Week", week));
        }
    }

    private static void addIntervalDescParam(JsonNode root, List<String> params) {
        String interval = getTextValue(root, "interval");
        if (interval != null && !interval.isEmpty() && !"Yes".equals(interval) && !"No".equals(interval)) {
            params.add(formatParam("Interval", interval));
        }
    }
}