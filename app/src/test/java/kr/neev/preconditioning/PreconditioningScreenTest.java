package kr.neev.preconditioning;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class PreconditioningScreenTest {
    private static List<TemperatureHistory.Sample> samples(long... times) {
        List<TemperatureHistory.Sample> list = new ArrayList<>();
        for (long at : times) list.add(new TemperatureHistory.Sample(at, 10, null));
        return list;
    }
    @Test public void gaugeClampsToItsRangeAndMarksTheThreshold() {
        assertEquals(0f, TemperatureGauge.fraction(-40), 0f);
        assertEquals(0f, TemperatureGauge.fraction(-10), 0f);
        assertEquals(30f / 45f, TemperatureGauge.fraction(20), 1e-6f);
        assertEquals(1f, TemperatureGauge.fraction(35), 0f);
        assertEquals(1f, TemperatureGauge.fraction(60), 0f);
    }
    @Test public void chartRangeAlwaysIncludesTheThresholdAndTenDegrees() {
        assertArrayEquals(new float[]{15, 25}, TemperatureChart.range(Collections.<TemperatureHistory.Sample>emptyList(), 20), 0f);
        assertArrayEquals(new float[]{16, 26}, TemperatureChart.range(Collections.<TemperatureHistory.Sample>emptyList(), 21), 0f);
        assertArrayEquals(new float[]{-7, 22}, TemperatureChart.range(Arrays.asList(
            new TemperatureHistory.Sample(1, -5, null), new TemperatureHistory.Sample(2, 3, 6)), 20), 0f);
        assertArrayEquals(new float[]{18, 33}, TemperatureChart.range(Arrays.asList(
            new TemperatureHistory.Sample(1, 22, 31)), 20), 0f);
        float[] narrow = TemperatureChart.range(Arrays.asList(new TemperatureHistory.Sample(1, 19, 21)), 20);
        assertEquals(10f, narrow[1] - narrow[0], 0f); assertTrue(narrow[0] <= 19 && narrow[1] >= 21);
    }
    @Test public void chartSpanIsBetweenFiveAndThirtyMinutes() {
        long now = 10_000_000;
        assertEquals(now - TemperatureChart.MIN_SPAN_MS, TemperatureChart.start(samples(), now));
        assertEquals(now - TemperatureChart.MIN_SPAN_MS, TemperatureChart.start(samples(now - 60_000), now));
        assertEquals(now - 600_000, TemperatureChart.start(samples(now - 600_000, now - 1000), now));
        assertEquals(now - TemperatureHistory.WINDOW_MS, TemperatureChart.start(samples(now - 4_000_000), now));
    }
    @Test public void chartMappingIsLinearAndClamped() {
        assertEquals(100f, TemperatureChart.x(0, 0, 1000, 100, 500), 0f);
        assertEquals(350f, TemperatureChart.x(500, 0, 1000, 100, 500), 0f);
        assertEquals(600f, TemperatureChart.x(2000, 0, 1000, 100, 500), 0f);
        assertEquals(100f, TemperatureChart.x(-5, 0, 1000, 100, 500), 0f);
        assertEquals(600f, TemperatureChart.x(5, 5, 5, 100, 500), 0f);
        assertEquals(210f, TemperatureChart.y(10, 10, 20, 10, 200), 0f);
        assertEquals(10f, TemperatureChart.y(20, 10, 20, 10, 200), 0f);
        assertEquals(110f, TemperatureChart.y(15, 10, 20, 10, 200), 0f);
        assertEquals(10f, TemperatureChart.y(99, 10, 20, 10, 200), 0f);
    }
    @Test public void countdownDrainsOverNinetySecondsAndClamps() {
        assertEquals(1f, CountdownBar.remaining(1000, 1000), 0f);
        assertEquals(1f, CountdownBar.remaining(1000, 0), 0f);
        assertEquals(0.5f, CountdownBar.remaining(1000, 46_000), 1e-6f);
        assertEquals(0f, CountdownBar.remaining(1000, 91_000), 0f);
        assertEquals(0f, CountdownBar.remaining(1000, 500_000), 0f);
    }
    @Test public void temperaturesKeepTheirCelsiusValuesAcrossDisplaySurfaces() {
        for (int value : new int[]{-40, -10, 0, 20, 21, 35, 60}) {
            String expected = value + "°C";
            assertEquals(expected, ScheduleFormat.temperature(value));
            BatteryTemperatureReading reading = BatteryTemperatureReading.checking().update(value, 1000);
            assertEquals("배터리 온도: " + expected, reading.label(1000, true));
            assertEquals("배터리 팩 최저 온도: " + expected + " · 수신 0초 전", reading.label(1000, false));
        }
        assertEquals("—", ScheduleFormat.temperature(null));
    }
    @Test public void temperatureAgeSeparatesValueFromReceipt() {
        assertEquals("확인 중…", BatteryTemperatureReading.checking().age(0));
        assertEquals("확인 불가", BatteryTemperatureReading.checking().update(null, 1000).age(1000));
        BatteryTemperatureReading reading = BatteryTemperatureReading.checking().update(17, 1000);
        assertEquals("수신 9초 전", reading.age(10_000));
        assertEquals("오래된 값 · 마지막 수신 30초 전", reading.age(31_000));
    }
    @Test public void vehicleChecksReadRawValuesAndNeverGuess() {
        PreconditioningSnapshot unknown = PreconditioningSnapshot.checking();
        assertEquals(PreconditioningSnapshot.UNKNOWN, unknown.readyCheck());
        assertEquals(PreconditioningSnapshot.UNKNOWN, unknown.enabledCheck());
        assertEquals(PreconditioningSnapshot.UNKNOWN, unknown.indicatorCheck());
        assertEquals("확인 중", unknown.supportedValue());
        PreconditioningSnapshot ready = snapshot(1, 2, 0);
        assertEquals(PreconditioningSnapshot.PASS, ready.readyCheck());
        assertEquals(PreconditioningSnapshot.PASS, ready.enabledCheck());
        assertEquals(PreconditioningSnapshot.PASS, ready.indicatorCheck());
        assertEquals("꺼짐", ready.indicatorValue());
        PreconditioningSnapshot off = snapshot(0, 1, 7);
        assertEquals(PreconditioningSnapshot.FAIL, off.readyCheck());
        assertEquals(PreconditioningSnapshot.FAIL, off.enabledCheck());
        assertEquals(PreconditioningSnapshot.UNKNOWN, off.indicatorCheck());
        assertEquals("EV 설정에서 꺼짐 (원시값 1)", off.enabledValue());
        assertEquals("알 수 없음 (원시값 7)", off.indicatorValue());
        assertEquals("알 수 없음 (원시값 3)", snapshot(3, 2, 1).readyValue());
    }
    @Test public void controllerSnapshotCarriesReadingAndFirmwareChecks() {
        PreconditioningControllerTest.FakeVehicle car = new PreconditioningControllerTest.FakeVehicle();
        PreconditioningController controller = new PreconditioningController(car, new PreconditioningControllerTest.Journal());
        PreconditioningSnapshot before = controller.snapshot(0);
        assertEquals(-1, before.ready); assertEquals(-1, before.enabled);
        assertEquals(PreconditioningSnapshot.UNKNOWN, before.supported);
        controller.refresh(1000);
        PreconditioningSnapshot read = controller.snapshot(1000);
        assertEquals(1, read.ready); assertEquals(2, read.enabled); assertEquals(PreconditioningSnapshot.PASS, read.supported);
        car.readFails = true; controller.refresh(2000);
        assertEquals(-1, controller.snapshot(2000).ready);
        assertEquals(PreconditioningSnapshot.UNKNOWN, controller.snapshot(2000).supported);
        car.readFails = false; car.unavailable = "Unsupported head-unit firmware"; controller.refresh(3000);
        assertEquals(PreconditioningSnapshot.FAIL, controller.snapshot(3000).supported);
    }
    @Test public void translatedFirmwareErrorsKeepUnsupportedStatusAndBlockStart() {
        for (String message : new String[]{"지원하지 않는 헤드유닛 펌웨어입니다", "Unsupported head-unit firmware"}) {
            PreconditioningControllerTest.FakeVehicle car = new PreconditioningControllerTest.FakeVehicle();
            car.readFails = true; car.readError = message;
            PreconditioningController controller = new PreconditioningController(car, new PreconditioningControllerTest.Journal());
            controller.refresh(1000);
            assertEquals(PreconditioningSnapshot.FAIL, controller.snapshot(1000).supported);
            assertFalse(controller.snapshot(1000).canStart);
            assertFalse(controller.start(1000));
            assertEquals(0, car.starts);
        }
    }
    private static PreconditioningSnapshot snapshot(int ready, int enabled, int indicator) {
        return new PreconditioningSnapshot(PreconditioningSnapshot.State.IDLE, false, false, false, indicator, -1, 0, "",
            0, ready, enabled, PreconditioningSnapshot.PASS);
    }
}
