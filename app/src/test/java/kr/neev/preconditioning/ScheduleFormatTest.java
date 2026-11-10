package kr.neev.preconditioning;

import org.junit.Test;
import java.util.TimeZone;
import java.util.Locale;
import static org.junit.Assert.*;

public class ScheduleFormatTest {
    static final TimeZone UTC = TimeZone.getTimeZone("UTC");
    static final long TEN = 1793613600000L;   // 2026-11-02 10:00 UTC (a Monday)
    static final long MIN = 60_000L;

    @Test public void formatsTwelveAndTwentyFourHourTimes() {
        long t = TEN + 3 * 60 * MIN + 30 * MIN;   // 13:30
        assertEquals("오후 1:30", ScheduleFormat.time(t, false, UTC));
        assertEquals("13:30", ScheduleFormat.time(t, true, UTC));
        assertEquals("오후 1:28:50", ScheduleFormat.timeWithSeconds(t - 70_000, false, UTC));
        assertEquals("오전 12:00", ScheduleFormat.time(TEN - 10 * 60 * MIN, false, UTC));
    }
    @Test public void koreanTimeAndCelsiusDoNotDependOnHeadUnitLanguage() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.US);
            assertEquals("오전 10:00", ScheduleFormat.time(TEN, false, UTC));
            assertEquals(" 내일", ScheduleFormat.dayHint(TEN + 24 * 60 * MIN, TEN, UTC));
            assertEquals("-10°C", ScheduleFormat.temperature(-10));
            assertEquals("0°C", ScheduleFormat.temperature(0));
        } finally { Locale.setDefault(previous); }
    }
    @Test public void countdownRoundsUp() {
        assertEquals("0:00", ScheduleFormat.countdown(0));
        assertEquals("0:00", ScheduleFormat.countdown(-5000));
        assertEquals("0:01", ScheduleFormat.countdown(1));
        assertEquals("42:17", ScheduleFormat.countdown(42 * MIN + 17_000));
        assertEquals("1:02:03", ScheduleFormat.countdown(62 * MIN + 3_000));
    }
    @Test public void nextOccurrenceIsTodayOrTomorrow() {
        assertEquals(TEN + 30 * MIN, ScheduleFormat.nextOccurrence(10 * 60 + 30, TEN, UTC));
        assertEquals(TEN + 23 * 60 * MIN, ScheduleFormat.nextOccurrence(9 * 60, TEN, UTC));
        assertEquals(TEN + 24 * 60 * MIN, ScheduleFormat.nextOccurrence(10 * 60, TEN, UTC));   // strictly after now
        assertEquals(TEN + 30 * MIN, ScheduleFormat.nextOccurrence(10 * 60 + 30 + 24 * 60, TEN, UTC));
    }
    @Test public void minutesWrapAndRoundToFive() {
        assertEquals(605, ScheduleFormat.roundUpToFive(601));
        assertEquals(600, ScheduleFormat.roundUpToFive(600));
        assertEquals(0, ScheduleFormat.roundUpToFive(1437));
        assertEquals(1435, ScheduleFormat.wrapDay(-5));
        assertEquals(30, ScheduleFormat.wrapDay(24 * 60 + 30));
        assertEquals(10 * 60, ScheduleFormat.minuteOfDay(TEN, UTC));
    }
    @Test public void dayHintNamesTomorrowAndLaterDays() {
        assertEquals("", ScheduleFormat.dayHint(TEN + 60 * MIN, TEN, UTC));
        assertEquals(" 내일", ScheduleFormat.dayHint(TEN + 20 * 60 * MIN, TEN, UTC));
        assertEquals(" 수", ScheduleFormat.dayHint(TEN + 48 * 60 * MIN, TEN, UTC));
    }
    @Test public void sendLineCountsDownThenWaits() {
        PreconditioningSchedule s = PreconditioningSchedule.startBy(TEN + 3 * 60 * MIN + 30 * MIN, TEN);
        assertEquals("3:28:50 후 시작", ScheduleFormat.sendLine(s, TEN));
        assertEquals("차량 응답 대기 중", ScheduleFormat.sendLine(s, s.plannedSendAt));
        PreconditioningSchedule warm = PreconditioningSchedule.arriveBy(TEN + 60 * MIN, TEN, 25, 10, 21);
        assertEquals("배터리 충분히 따뜻함", ScheduleFormat.sendLine(warm, TEN));
    }
    @Test public void eventLineLeadsWithTimeAndKind() {
        ScheduleLog.Event e = ScheduleLog.EMPTY.add(TEN, ScheduleLog.Kind.ARMED, "시작 시각 오전 10:30").latest();
        assertEquals("오전 10:00  예약됨 · 시작 시각 오전 10:30", ScheduleFormat.event(e, TEN + MIN, false, UTC));
    }
    @Test public void temperatureShowsCelsiusOnly() {
        assertEquals("9°C", ScheduleFormat.temperature(9));
        assertEquals("—", ScheduleFormat.temperature(null));
    }
    @Test public void hourWheelKeepsMinutesAndWrapsTheDay() {
        assertEquals(8 * 60 + 45, ScheduleFormat.stepHour(7 * 60 + 45, 1));
        assertEquals(23 * 60 + 45, ScheduleFormat.stepHour(45, -1));
        assertEquals(10, ScheduleFormat.stepHour(23 * 60 + 10, 1));
    }
    @Test public void minuteWheelStepsFiveMinuteMarksWithinTheHour() {
        assertEquals(7 * 60 + 50, ScheduleFormat.stepMinute(7 * 60 + 45, 1));
        assertEquals(7 * 60, ScheduleFormat.stepMinute(7 * 60 + 55, 1));
        assertEquals(7 * 60 + 55, ScheduleFormat.stepMinute(7 * 60, -1));
        // An armed time off the five-minute grid snaps to the next mark down, then steps.
        assertEquals(7 * 60 + 50, ScheduleFormat.stepMinute(7 * 60 + 47, 1));
        assertEquals(7 * 60 + 45, ScheduleFormat.stepMinute(7 * 60 + 47, 0));
    }
    @Test public void morningAndAfternoonKeepTheClockTime() {
        assertEquals(19 * 60 + 45, ScheduleFormat.withAfternoon(7 * 60 + 45, true));
        assertEquals(7 * 60 + 45, ScheduleFormat.withAfternoon(19 * 60 + 45, false));
        assertEquals(12 * 60 + 5, ScheduleFormat.withAfternoon(5, true));
        assertEquals(7 * 60 + 45, ScheduleFormat.withAfternoon(7 * 60 + 45, false));
    }
    @Test public void wheelLabelsFollowTheClockFormat() {
        assertEquals("12", ScheduleFormat.hourLabel(5, false));
        assertEquals("7", ScheduleFormat.hourLabel(19 * 60, false));
        assertEquals("12", ScheduleFormat.hourLabel(12 * 60, false));
        assertEquals("00", ScheduleFormat.hourLabel(5, true));
        assertEquals("19", ScheduleFormat.hourLabel(19 * 60, true));
        assertEquals("05", ScheduleFormat.minuteLabel(7 * 60 + 5));
    }
}
