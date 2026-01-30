package com.sixdee.text2rule.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.sixdee.text2rule.util.ScheduleDslGenerator;

/**
 * COMPLETE Test cases for Schedule Parsing and DSL Generation.
 * Covers ALL 9 schedule types from the reference test sheet.
 * JDK 11 compatible (no text blocks).
 */
public class ScheduleParsingTestComplete {

    private String json(String s) {
        return s.replace("'", "\"");
    }

    // ==========================================
    // TEST CASE 1: Daily Schedule (No Interval)
    // Input: "Every day at 10 AM"
    // ==========================================
    @Test
    @DisplayName("Daily - Every day at 10 AM")
    void testDailySchedule() {
        String scheduleJson = json(
            "{\n" +
            "  'schedule_type': 'Daily',\n" +
            "  'repeat': 'Yes',\n" +
            "  'last_fetch': 'No',\n" +
            "  'segment_rule_start_date': null,\n" +
            "  'segment_rule_end_date': null,\n" +
            "  'interval': 'No',\n" +
            "  'frequency': '',\n" +
            "  'hours': '',\n" +
            "  'minutes': '',\n" +
            "  'type': null,\n" +
            "  'period': '',\n" +
            "  'week': '',\n" +
            "  'day': 'ALL',\n" +
            "  'select_days': ['ALL'],\n" +
            "  'start_time': {'ALL': '10:00'},\n" +
            "  'end_time': {}\n" +
            "}"
        );

        String actualDsl = ScheduleDslGenerator.generateDsl(scheduleJson);
        System.out.println("TEST 1 - Daily: " + actualDsl);

        assertTrue(actualDsl.contains("ScheduleName=\"Daily\""), "Should have ScheduleName=Daily");
        assertTrue(actualDsl.contains("ScheduleType=\"Daily\""), "Should have ScheduleType=Daily");
        assertTrue(actualDsl.contains("StartTime=\"ALL:10:00\""), "Should have StartTime=ALL:10:00");
        assertTrue(actualDsl.contains("Repeat=\"Yes\""), "Should have Repeat=Yes");
        assertTrue(actualDsl.contains("Day=\"ALL\""), "Should have Day=ALL");
        assertTrue(actualDsl.contains("SelectDays=\"ALL\""), "Should have SelectDays=ALL");
    }

    // ==========================================
    // TEST CASE 2: Weekly Schedule (No Interval)
    // Input: "Every Monday and Thursday at 6 PM"
    // ==========================================
    @Test
    @DisplayName("Weekly - Every Monday and Thursday at 6 PM")
    void testWeeklySchedule() {
        String scheduleJson = json(
            "{\n" +
            "  'schedule_type': 'Weekly',\n" +
            "  'repeat': 'Yes',\n" +
            "  'last_fetch': 'No',\n" +
            "  'segment_rule_start_date': null,\n" +
            "  'segment_rule_end_date': null,\n" +
            "  'interval': 'No',\n" +
            "  'frequency': '',\n" +
            "  'hours': '',\n" +
            "  'minutes': '',\n" +
            "  'type': null,\n" +
            "  'period': '',\n" +
            "  'week': '',\n" +
            "  'day': 'Monday, Thursday',\n" +
            "  'select_days': ['MON','THU'],\n" +
            "  'start_time': {'MON': '18:00','THU': '18:00'},\n" +
            "  'end_time': {}\n" +
            "}"
        );

        String actualDsl = ScheduleDslGenerator.generateDsl(scheduleJson);
        System.out.println("TEST 2 - Weekly: " + actualDsl);

        assertTrue(actualDsl.contains("ScheduleType=\"Weekly\""), "Should have ScheduleType=Weekly");
        assertTrue(actualDsl.contains("Day=\"Monday, Thursday\""), "Should have Day=Monday, Thursday");
        assertTrue(actualDsl.contains("SelectDays=\"MON,THU\""), "Should have SelectDays=MON,THU");
        // Check time format - should contain both days with times
        assertTrue(actualDsl.contains("StartTime="), "Should have StartTime");
    }

    // ==========================================
    // TEST CASE 3: Monthly Schedule (No Interval)
    // Input: "On the 1st and 15th of every month at 9:30 AM"
    // ==========================================
    @Test
    @DisplayName("Monthly - On the 1st and 15th at 9:30 AM")
    void testMonthlySchedule() {
        String scheduleJson = json(
            "{\n" +
            "  'schedule_type': 'Monthly',\n" +
            "  'repeat': 'Yes',\n" +
            "  'last_fetch': 'No',\n" +
            "  'segment_rule_start_date': null,\n" +
            "  'segment_rule_end_date': null,\n" +
            "  'interval': 'No',\n" +
            "  'frequency': '',\n" +
            "  'hours': '',\n" +
            "  'minutes': '',\n" +
            "  'type': null,\n" +
            "  'period': '',\n" +
            "  'week': '',\n" +
            "  'day': '1 , 15',\n" +
            "  'select_days': [1,15],\n" +
            "  'start_time': {'1': '09:30','15': '09:30'},\n" +
            "  'end_time': {}\n" +
            "}"
        );

        String actualDsl = ScheduleDslGenerator.generateDsl(scheduleJson);
        System.out.println("TEST 3 - Monthly: " + actualDsl);

        assertTrue(actualDsl.contains("ScheduleType=\"Monthly\""), "Should have ScheduleType=Monthly");
        assertTrue(actualDsl.contains("Day=\"1 , 15\""), "Should have Day=1 , 15");
        assertTrue(actualDsl.contains("SelectDays=\"1,15\""), "Should have SelectDays=1,15");
    }

    // ==========================================
    // TEST CASE 4: ScheduleNow
    // Input: "Run campaign now"
    // ==========================================
    @Test
    @DisplayName("ScheduleNow - Run campaign now")
    void testScheduleNow() {
        String scheduleJson = json(
            "{\n" +
            "  'schedule_type': 'ScheduleNow',\n" +
            "  'repeat': '',\n" +
            "  'last_fetch': null,\n" +
            "  'segment_rule_start_date': null,\n" +
            "  'segment_rule_end_date': null,\n" +
            "  'interval': '',\n" +
            "  'frequency': '',\n" +
            "  'hours': '',\n" +
            "  'minutes': '',\n" +
            "  'type': null,\n" +
            "  'period': '',\n" +
            "  'week': '',\n" +
            "  'day': '',\n" +
            "  'select_days': [],\n" +
            "  'start_time': {},\n" +
            "  'end_time': {}\n" +
            "}"
        );

        String actualDsl = ScheduleDslGenerator.generateDsl(scheduleJson);
        System.out.println("TEST 4 - ScheduleNow: " + actualDsl);

        assertTrue(actualDsl.contains("ScheduleType=\"ScheduleNow\""), "Should have ScheduleType=ScheduleNow");
        assertFalse(actualDsl.contains("Repeat=\""), "Should NOT have Repeat for ScheduleNow");
        assertFalse(actualDsl.contains("StartTime"), "Should NOT have StartTime for ScheduleNow");
    }

    // ==========================================
    // TEST CASE 5: Interval
    // Input: "Repeat every 2 days 4 hours 30 minutes starting at 08:00"
    // ==========================================
    @Test
    @DisplayName("Interval - Repeat every 2 days 4 hours 30 minutes")
    void testIntervalSchedule() {
        String scheduleJson = json(
            "{\n" +
            "  'schedule_type': 'Interval',\n" +
            "  'repeat': 'Yes',\n" +
            "  'last_fetch': null,\n" +
            "  'segment_rule_start_date': null,\n" +
            "  'segment_rule_end_date': null,\n" +
            "  'interval': '2 days 4 hours 30 minutes',\n" +
            "  'frequency': '',\n" +
            "  'hours': '4',\n" +
            "  'minutes': '30',\n" +
            "  'type': null,\n" +
            "  'period': '',\n" +
            "  'week': '',\n" +
            "  'day': '',\n" +
            "  'select_days': ['ALL'],\n" +
            "  'start_time': {'ALL': '08:00'},\n" +
            "  'end_time': {}\n" +
            "}"
        );

        String actualDsl = ScheduleDslGenerator.generateDsl(scheduleJson);
        System.out.println("TEST 5 - Interval: " + actualDsl);

        assertTrue(actualDsl.contains("ScheduleType=\"Interval\""), "Should have ScheduleType=Interval");
        assertTrue(actualDsl.contains("Interval=\"2 days 4 hours 30 minutes\""), "Should have Interval description");
        assertTrue(actualDsl.contains("Hours=\"4\""), "Should have Hours=4");
        assertTrue(actualDsl.contains("Minutes=\"30\""), "Should have Minutes=30");
        assertTrue(actualDsl.contains("StartTime=\"ALL:08:00\""), "Should have StartTime=ALL:08:00");
    }

    // ==========================================
    // TEST CASE 6: DailyWithInterval
    // Input: "Send notifications every day from 09:00 to 17:00 every 2 hours"
    // ==========================================
    @Test
    @DisplayName("DailyWithInterval - Every day 09:00 to 17:00 every 2 hours")
    void testDailyWithInterval() {
        String scheduleJson = json(
            "{\n" +
            "  'schedule_type': 'DailyWithInterval',\n" +
            "  'repeat': 'Yes',\n" +
            "  'last_fetch': 'No',\n" +
            "  'segment_rule_start_date': null,\n" +
            "  'segment_rule_end_date': null,\n" +
            "  'interval': 'Yes',\n" +
            "  'frequency': '2 hours',\n" +
            "  'hours': '2',\n" +
            "  'minutes': '',\n" +
            "  'type': null,\n" +
            "  'period': '',\n" +
            "  'week': '',\n" +
            "  'day': 'ALL',\n" +
            "  'select_days': ['ALL'],\n" +
            "  'start_time': {'ALL': '09:00'},\n" +
            "  'end_time': {'ALL': '17:00'}\n" +
            "}"
        );

        String actualDsl = ScheduleDslGenerator.generateDsl(scheduleJson);
        System.out.println("TEST 6 - DailyWithInterval: " + actualDsl);

        assertTrue(actualDsl.contains("ScheduleType=\"DailyWithInterval\""), "Should have ScheduleType=DailyWithInterval");
        assertTrue(actualDsl.contains("Interval=\"Yes\""), "Should have Interval=Yes");
        assertTrue(actualDsl.contains("Frequency=\"2 hours\""), "Should have Frequency=2 hours");
        assertTrue(actualDsl.contains("StartTime=\"ALL:09:00\""), "Should have StartTime=ALL:09:00");
        assertTrue(actualDsl.contains("EndTime=\"ALL:17:00\""), "Should have EndTime=ALL:17:00");
        assertTrue(actualDsl.contains("Hours=\"2\""), "Should have Hours=2");
    }

    // ==========================================
    // TEST CASE 7: WeeklyWithInterval
    // Input: "Run every Monday and Thursday every 2 hours from 09:00 to 17:00"
    // ==========================================
    @Test
    @DisplayName("WeeklyWithInterval - Monday and Thursday every 2 hours 09:00-17:00")
    void testWeeklyWithInterval() {
        String scheduleJson = json(
            "{\n" +
            "  'schedule_type': 'WeeklyWithInterval',\n" +
            "  'repeat': 'Yes',\n" +
            "  'last_fetch': 'No',\n" +
            "  'segment_rule_start_date': null,\n" +
            "  'segment_rule_end_date': null,\n" +
            "  'interval': 'Yes',\n" +
            "  'frequency': '2 hours',\n" +
            "  'hours': '2',\n" +
            "  'minutes': '',\n" +
            "  'type': null,\n" +
            "  'period': '',\n" +
            "  'week': '',\n" +
            "  'day': 'Monday, Thursday',\n" +
            "  'select_days': ['MON','THU'],\n" +
            "  'start_time': {'MON': '09:00','THU': '09:00'},\n" +
            "  'end_time': {'MON': '17:00','THU': '17:00'}\n" +
            "}"
        );

        String actualDsl = ScheduleDslGenerator.generateDsl(scheduleJson);
        System.out.println("TEST 7 - WeeklyWithInterval: " + actualDsl);

        assertTrue(actualDsl.contains("ScheduleType=\"WeeklyWithInterval\""), "Should have ScheduleType=WeeklyWithInterval");
        assertTrue(actualDsl.contains("Interval=\"Yes\""), "Should have Interval=Yes");
        assertTrue(actualDsl.contains("Frequency=\"2 hours\""), "Should have Frequency=2 hours");
        assertTrue(actualDsl.contains("SelectDays=\"MON,THU\""), "Should have SelectDays=MON,THU");
        assertTrue(actualDsl.contains("Day=\"Monday, Thursday\""), "Should have Day=Monday, Thursday");
        assertTrue(actualDsl.contains("Hours=\"2\""), "Should have Hours=2");
    }

    // ==========================================
    // TEST CASE 8: MonthlyWithInterval
    // Input: "Send campaign on the 1st and 15th every 2 hours from 09:00 to 17:00"
    // ==========================================
    @Test
    @DisplayName("MonthlyWithInterval - 1st and 15th every 2 hours 09:00-17:00")
    void testMonthlyWithInterval() {
        String scheduleJson = json(
            "{\n" +
            "  'schedule_type': 'MonthlyWithInterval',\n" +
            "  'repeat': 'Yes',\n" +
            "  'last_fetch': 'No',\n" +
            "  'segment_rule_start_date': null,\n" +
            "  'segment_rule_end_date': null,\n" +
            "  'interval': 'Yes',\n" +
            "  'frequency': '2 hours',\n" +
            "  'hours': '2',\n" +
            "  'minutes': '',\n" +
            "  'type': null,\n" +
            "  'period': '',\n" +
            "  'week': '',\n" +
            "  'day': '1 , 15',\n" +
            "  'select_days': [1,15],\n" +
            "  'start_time': {'1': '09:00','15': '09:00'},\n" +
            "  'end_time': {'1': '17:00','15': '17:00'}\n" +
            "}"
        );

        String actualDsl = ScheduleDslGenerator.generateDsl(scheduleJson);
        System.out.println("TEST 8 - MonthlyWithInterval: " + actualDsl);

        assertTrue(actualDsl.contains("ScheduleType=\"MonthlyWithInterval\""), "Should have ScheduleType=MonthlyWithInterval");
        assertTrue(actualDsl.contains("Interval=\"Yes\""), "Should have Interval=Yes");
        assertTrue(actualDsl.contains("Frequency=\"2 hours\""), "Should have Frequency=2 hours");
        assertTrue(actualDsl.contains("Day=\"1 , 15\""), "Should have Day=1 , 15");
        assertTrue(actualDsl.contains("SelectDays=\"1,15\""), "Should have SelectDays=1,15");
        assertTrue(actualDsl.contains("Hours=\"2\""), "Should have Hours=2");
    }

    // ==========================================
    // TEST CASE 9: MonthlyWithSpecifics
    // Input: "Run a campaign on the first Monday of every month at 09:15"
    // ==========================================
    @Test
    @DisplayName("MonthlyWithSpecifics - First Monday of every month at 09:15")
    void testMonthlyWithSpecifics() {
        String scheduleJson = json(
            "{\n" +
            "  'schedule_type': 'MonthlyWithSpecifics',\n" +
            "  'repeat': 'Monthly',\n" +
            "  'last_fetch': null,\n" +
            "  'segment_rule_start_date': '2025-11-01',\n" +
            "  'segment_rule_end_date': '2025-12-31',\n" +
            "  'interval': '',\n" +
            "  'frequency': '',\n" +
            "  'hours': '',\n" +
            "  'minutes': '',\n" +
            "  'type': 'First',\n" +
            "  'period': 'Week',\n" +
            "  'week': '1',\n" +
            "  'day': 'Monday',\n" +
            "  'select_days': ['MON'],\n" +
            "  'start_time': {'MON': '09:15'},\n" +
            "  'end_time': {}\n" +
            "}"
        );

        String actualDsl = ScheduleDslGenerator.generateDsl(scheduleJson);
        System.out.println("TEST 9 - MonthlyWithSpecifics: " + actualDsl);

        assertTrue(actualDsl.contains("ScheduleType=\"MonthlyWithSpecifics\""), "Should have ScheduleType=MonthlyWithSpecifics");
        assertTrue(actualDsl.contains("StartDate=\"2025-11-01\""), "Should have StartDate=2025-11-01");
        assertTrue(actualDsl.contains("ExpiryDate=\"2025-12-31\""), "Should have ExpiryDate=2025-12-31");
        assertTrue(actualDsl.contains("Type=\"First\""), "Should have Type=First");
        assertTrue(actualDsl.contains("Period=\"Week\""), "Should have Period=Week");
        assertTrue(actualDsl.contains("Week=\"1\""), "Should have Week=1");
        assertTrue(actualDsl.contains("Day=\"Monday\""), "Should have Day=Monday");
        assertTrue(actualDsl.contains("SelectDays=\"MON\""), "Should have SelectDays=MON");
        assertTrue(actualDsl.contains("Repeat=\"Monthly\""), "Should have Repeat=Monthly");
    }

    // ==========================================
    // TEST CASE 10: Weekly with Date Range
    // Input: "Weekly on Mondays and Tuesdays from 26th March 2025 at 6:15 AM until 31st December 2026"
    // ==========================================
    @Test
    @DisplayName("Weekly with Date Range - March 2025 to December 2026")
    void testWeeklyWithDateRange() {
        String scheduleJson = json(
            "{\n" +
            "  'schedule_type': 'Weekly',\n" +
            "  'repeat': 'Yes',\n" +
            "  'last_fetch': 'No',\n" +
            "  'segment_rule_start_date': '2025-03-26',\n" +
            "  'segment_rule_end_date': '2026-12-31',\n" +
            "  'interval': 'No',\n" +
            "  'frequency': '',\n" +
            "  'hours': '',\n" +
            "  'minutes': '',\n" +
            "  'type': null,\n" +
            "  'period': '',\n" +
            "  'week': '',\n" +
            "  'day': 'Monday, Tuesday',\n" +
            "  'select_days': ['MON','TUE'],\n" +
            "  'start_time': {'MON': '06:15','TUE': '06:15'},\n" +
            "  'end_time': {}\n" +
            "}"
        );

        String actualDsl = ScheduleDslGenerator.generateDsl(scheduleJson);
        System.out.println("TEST 10 - Weekly with Date Range: " + actualDsl);

        assertTrue(actualDsl.contains("ScheduleType=\"Weekly\""), "Should have ScheduleType=Weekly");
        assertTrue(actualDsl.contains("StartDate=\"2025-03-26\""), "Should have StartDate=2025-03-26");
        assertTrue(actualDsl.contains("ExpiryDate=\"2026-12-31\""), "Should have ExpiryDate=2026-12-31");
        assertTrue(actualDsl.contains("Day=\"Monday, Tuesday\""), "Should have Day=Monday, Tuesday");
        assertTrue(actualDsl.contains("SelectDays=\"MON,TUE\""), "Should have SelectDays=MON,TUE");
    }

    // ==========================================
    // TEST CASE 11: Daily with Date Range
    // Input: "Run from 1st November 2024 to 30th November 2024"
    // ==========================================
    @Test
    @DisplayName("Daily with Date Range - November 2024")
    void testDailyWithDateRange() {
        String scheduleJson = json(
            "{\n" +
            "  'schedule_type': 'Daily',\n" +
            "  'repeat': 'Yes',\n" +
            "  'last_fetch': 'No',\n" +
            "  'segment_rule_start_date': '2024-11-01',\n" +
            "  'segment_rule_end_date': '2024-11-30',\n" +
            "  'interval': 'No',\n" +
            "  'frequency': '',\n" +
            "  'hours': '',\n" +
            "  'minutes': '',\n" +
            "  'type': null,\n" +
            "  'period': '',\n" +
            "  'week': '',\n" +
            "  'day': 'ALL',\n" +
            "  'select_days': ['ALL'],\n" +
            "  'start_time': {'ALL': '14:00'},\n" +
            "  'end_time': {}\n" +
            "}"
        );

        String actualDsl = ScheduleDslGenerator.generateDsl(scheduleJson);
        System.out.println("TEST 11 - Daily with Date Range: " + actualDsl);

        assertTrue(actualDsl.contains("ScheduleType=\"Daily\""), "Should have ScheduleType=Daily");
        assertTrue(actualDsl.contains("StartDate=\"2024-11-01\""), "Should have StartDate=2024-11-01");
        assertTrue(actualDsl.contains("ExpiryDate=\"2024-11-30\""), "Should have ExpiryDate=2024-11-30");
        assertTrue(actualDsl.contains("StartTime=\"ALL:14:00\""), "Should have StartTime=ALL:14:00");
    }

    // ==========================================
    // TEST CASE 12: Empty / Invalid Schedule
    // ==========================================
    @Test
    @DisplayName("Empty schedule JSON returns empty string")
    void testEmptySchedule() {
        assertEquals("", ScheduleDslGenerator.generateDsl("{}"), "Empty JSON should return empty string");
        assertEquals("", ScheduleDslGenerator.generateDsl(""), "Empty string should return empty string");
        assertEquals("", ScheduleDslGenerator.generateDsl("[]"), "Empty array should return empty string");
    }

    // ==========================================
    // TEST CASE 13: MonthlyWithSpecifics - Last Friday
    // Input: "Run on the last Friday of every month at 16:00"
    // ==========================================
    @Test
    @DisplayName("MonthlyWithSpecifics - Last Friday of every month")
    void testMonthlyWithSpecificsLastFriday() {
        String scheduleJson = json(
            "{\n" +
            "  'schedule_type': 'MonthlyWithSpecifics',\n" +
            "  'repeat': 'Monthly',\n" +
            "  'last_fetch': null,\n" +
            "  'segment_rule_start_date': null,\n" +
            "  'segment_rule_end_date': null,\n" +
            "  'interval': '',\n" +
            "  'frequency': '',\n" +
            "  'hours': '',\n" +
            "  'minutes': '',\n" +
            "  'type': 'Last',\n" +
            "  'period': 'Week',\n" +
            "  'week': '5',\n" +
            "  'day': 'Friday',\n" +
            "  'select_days': ['FRI'],\n" +
            "  'start_time': {'FRI': '16:00'},\n" +
            "  'end_time': {}\n" +
            "}"
        );

        String actualDsl = ScheduleDslGenerator.generateDsl(scheduleJson);
        System.out.println("TEST 13 - MonthlyWithSpecifics Last Friday: " + actualDsl);

        assertTrue(actualDsl.contains("ScheduleType=\"MonthlyWithSpecifics\""), "Should have ScheduleType=MonthlyWithSpecifics");
        assertTrue(actualDsl.contains("Type=\"Last\""), "Should have Type=Last");
        assertTrue(actualDsl.contains("Week=\"5\""), "Should have Week=5");
        assertTrue(actualDsl.contains("Day=\"Friday\""), "Should have Day=Friday");
        assertTrue(actualDsl.contains("SelectDays=\"FRI\""), "Should have SelectDays=FRI");
    }

    // ==========================================
    // TEST CASE 14: Single Day Weekly
    // Input: "Every Sunday at 8 AM"
    // ==========================================
    @Test
    @DisplayName("Weekly - Single day (Sunday at 8 AM)")
    void testWeeklySingleDay() {
        String scheduleJson = json(
            "{\n" +
            "  'schedule_type': 'Weekly',\n" +
            "  'repeat': 'Yes',\n" +
            "  'last_fetch': 'No',\n" +
            "  'segment_rule_start_date': null,\n" +
            "  'segment_rule_end_date': null,\n" +
            "  'interval': 'No',\n" +
            "  'frequency': '',\n" +
            "  'hours': '',\n" +
            "  'minutes': '',\n" +
            "  'type': null,\n" +
            "  'period': '',\n" +
            "  'week': '',\n" +
            "  'day': 'Sunday',\n" +
            "  'select_days': ['SUN'],\n" +
            "  'start_time': {'SUN': '08:00'},\n" +
            "  'end_time': {}\n" +
            "}"
        );

        String actualDsl = ScheduleDslGenerator.generateDsl(scheduleJson);
        System.out.println("TEST 14 - Weekly Single Day: " + actualDsl);

        assertTrue(actualDsl.contains("ScheduleType=\"Weekly\""), "Should have ScheduleType=Weekly");
        assertTrue(actualDsl.contains("Day=\"Sunday\""), "Should have Day=Sunday");
        assertTrue(actualDsl.contains("SelectDays=\"SUN\""), "Should have SelectDays=SUN");
        assertTrue(actualDsl.contains("StartTime=\"SUN:08:00\""), "Should have StartTime=SUN:08:00");
    }

    // ==========================================
    // TEST CASE 15: Monthly Single Day
    // Input: "On the 5th of every month at 12:00"
    // ==========================================
    @Test
    @DisplayName("Monthly - Single day (5th at 12:00)")
    void testMonthlySingleDay() {
        String scheduleJson = json(
            "{\n" +
            "  'schedule_type': 'Monthly',\n" +
            "  'repeat': 'Yes',\n" +
            "  'last_fetch': 'No',\n" +
            "  'segment_rule_start_date': null,\n" +
            "  'segment_rule_end_date': null,\n" +
            "  'interval': 'No',\n" +
            "  'frequency': '',\n" +
            "  'hours': '',\n" +
            "  'minutes': '',\n" +
            "  'type': null,\n" +
            "  'period': '',\n" +
            "  'week': '',\n" +
            "  'day': '5',\n" +
            "  'select_days': [5],\n" +
            "  'start_time': {'5': '12:00'},\n" +
            "  'end_time': {}\n" +
            "}"
        );

        String actualDsl = ScheduleDslGenerator.generateDsl(scheduleJson);
        System.out.println("TEST 15 - Monthly Single Day: " + actualDsl);

        assertTrue(actualDsl.contains("ScheduleType=\"Monthly\""), "Should have ScheduleType=Monthly");
        assertTrue(actualDsl.contains("Day=\"5\""), "Should have Day=5");
        assertTrue(actualDsl.contains("SelectDays=\"5\""), "Should have SelectDays=5");
        assertTrue(actualDsl.contains("StartTime=\"5:12:00\""), "Should have StartTime=5:12:00");
    }

    // ==========================================
    // TEST CASE 16: User's actual input test
    // Input: "Schedule from 26th March 2025 at 6:15 AM until 31st December 2026 at 11:59 PM"
    // ==========================================
    @Test
    @DisplayName("User Input - March 2025 to December 2026 with specific times")
    void testUserActualInput() {
        String scheduleJson = json(
            "{\n" +
            "  'schedule_type': 'Daily',\n" +
            "  'repeat': 'Yes',\n" +
            "  'last_fetch': 'No',\n" +
            "  'segment_rule_start_date': '2025-03-26',\n" +
            "  'segment_rule_end_date': '2026-12-31',\n" +
            "  'interval': 'No',\n" +
            "  'frequency': '',\n" +
            "  'hours': '',\n" +
            "  'minutes': '',\n" +
            "  'type': null,\n" +
            "  'period': '',\n" +
            "  'week': '',\n" +
            "  'day': 'ALL',\n" +
            "  'select_days': ['ALL'],\n" +
            "  'start_time': {'ALL': '06:15'},\n" +
            "  'end_time': {}\n" +
            "}"
        );

        String actualDsl = ScheduleDslGenerator.generateDsl(scheduleJson);
        System.out.println("TEST 16 - User Actual Input: " + actualDsl);

        assertTrue(actualDsl.contains("ScheduleType=\"Daily\""), "Should have ScheduleType=Daily");
        assertTrue(actualDsl.contains("StartDate=\"2025-03-26\""), "Should have StartDate=2025-03-26");
        assertTrue(actualDsl.contains("ExpiryDate=\"2026-12-31\""), "Should have ExpiryDate=2026-12-31");
        assertTrue(actualDsl.contains("StartTime=\"ALL:06:15\""), "Should have StartTime=ALL:06:15");
    }

    // ==========================================
    // SUMMARY: Run all tests and print results
    // ==========================================
    public static void main(String[] args) {
        ScheduleParsingTestComplete test = new ScheduleParsingTestComplete();
        
        System.out.println("\n========== SCHEDULE PARSING TEST SUITE ==========\n");
        
        try { test.testDailySchedule(); System.out.println("✓ TEST 1 PASSED: Daily"); } 
        catch (AssertionError e) { System.out.println("✗ TEST 1 FAILED: Daily - " + e.getMessage()); }
        
        try { test.testWeeklySchedule(); System.out.println("✓ TEST 2 PASSED: Weekly"); } 
        catch (AssertionError e) { System.out.println("✗ TEST 2 FAILED: Weekly - " + e.getMessage()); }
        
        try { test.testMonthlySchedule(); System.out.println("✓ TEST 3 PASSED: Monthly"); } 
        catch (AssertionError e) { System.out.println("✗ TEST 3 FAILED: Monthly - " + e.getMessage()); }
        
        try { test.testScheduleNow(); System.out.println("✓ TEST 4 PASSED: ScheduleNow"); } 
        catch (AssertionError e) { System.out.println("✗ TEST 4 FAILED: ScheduleNow - " + e.getMessage()); }
        
        try { test.testIntervalSchedule(); System.out.println("✓ TEST 5 PASSED: Interval"); } 
        catch (AssertionError e) { System.out.println("✗ TEST 5 FAILED: Interval - " + e.getMessage()); }
        
        try { test.testDailyWithInterval(); System.out.println("✓ TEST 6 PASSED: DailyWithInterval"); } 
        catch (AssertionError e) { System.out.println("✗ TEST 6 FAILED: DailyWithInterval - " + e.getMessage()); }
        
        try { test.testWeeklyWithInterval(); System.out.println("✓ TEST 7 PASSED: WeeklyWithInterval"); } 
        catch (AssertionError e) { System.out.println("✗ TEST 7 FAILED: WeeklyWithInterval - " + e.getMessage()); }
        
        try { test.testMonthlyWithInterval(); System.out.println("✓ TEST 8 PASSED: MonthlyWithInterval"); } 
        catch (AssertionError e) { System.out.println("✗ TEST 8 FAILED: MonthlyWithInterval - " + e.getMessage()); }
        
        try { test.testMonthlyWithSpecifics(); System.out.println("✓ TEST 9 PASSED: MonthlyWithSpecifics"); } 
        catch (AssertionError e) { System.out.println("✗ TEST 9 FAILED: MonthlyWithSpecifics - " + e.getMessage()); }
        
        try { test.testWeeklyWithDateRange(); System.out.println("✓ TEST 10 PASSED: Weekly with Date Range"); } 
        catch (AssertionError e) { System.out.println("✗ TEST 10 FAILED: Weekly with Date Range - " + e.getMessage()); }
        
        try { test.testDailyWithDateRange(); System.out.println("✓ TEST 11 PASSED: Daily with Date Range"); } 
        catch (AssertionError e) { System.out.println("✗ TEST 11 FAILED: Daily with Date Range - " + e.getMessage()); }
        
        try { test.testEmptySchedule(); System.out.println("✓ TEST 12 PASSED: Empty Schedule"); } 
        catch (AssertionError e) { System.out.println("✗ TEST 12 FAILED: Empty Schedule - " + e.getMessage()); }
        
        try { test.testMonthlyWithSpecificsLastFriday(); System.out.println("✓ TEST 13 PASSED: Last Friday"); } 
        catch (AssertionError e) { System.out.println("✗ TEST 13 FAILED: Last Friday - " + e.getMessage()); }
        
        try { test.testWeeklySingleDay(); System.out.println("✓ TEST 14 PASSED: Weekly Single Day"); } 
        catch (AssertionError e) { System.out.println("✗ TEST 14 FAILED: Weekly Single Day - " + e.getMessage()); }
        
        try { test.testMonthlySingleDay(); System.out.println("✓ TEST 15 PASSED: Monthly Single Day"); } 
        catch (AssertionError e) { System.out.println("✗ TEST 15 FAILED: Monthly Single Day - " + e.getMessage()); }
        
        try { test.testUserActualInput(); System.out.println("✓ TEST 16 PASSED: User Actual Input"); } 
        catch (AssertionError e) { System.out.println("✗ TEST 16 FAILED: User Actual Input - " + e.getMessage()); }
        
        System.out.println("\n========== TEST SUITE COMPLETE ==========\n");
    }
}