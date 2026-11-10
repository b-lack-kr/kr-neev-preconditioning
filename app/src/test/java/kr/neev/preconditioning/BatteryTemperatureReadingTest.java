package kr.neev.preconditioning;

import org.junit.Test;
import static org.junit.Assert.*;

public class BatteryTemperatureReadingTest {
    @Test public void initialAndUnavailableStatesNeverInventZeroDegrees() {
        BatteryTemperatureReading reading = BatteryTemperatureReading.checking();
        assertEquals("배터리 온도: 확인 중…", reading.label(1000, true));
        assertEquals("배터리 팩 최저 온도: 확인 불가", reading.update(null, 1000).label(1000, false));
    }
    @Test public void validTemperatureShowsCelsiusAndReceiptAge() {
        BatteryTemperatureReading reading = BatteryTemperatureReading.checking().update(17, 1000);
        assertEquals("배터리 온도: 17°C", reading.label(1000, true));
        assertEquals("배터리 팩 최저 온도: 17°C · 수신 9초 전", reading.label(10000, false));
        assertFalse(reading.stale(10000));
    }
    @Test public void latestFailedReadMarksPreviousValueStaleImmediately() {
        BatteryTemperatureReading old = BatteryTemperatureReading.checking().update(20, 1000);
        BatteryTemperatureReading failed = old.update(null, 11000);
        assertEquals("배터리 온도: 오래된 값", failed.label(11000, true));
        assertTrue(failed.label(11000, false).contains("마지막 수신 10초 전"));
        assertEquals(1000, failed.receivedAt);
        assertFalse(old.stale(11000)); // Published snapshots stay immutable.
    }
    @Test public void ageAloneMarksStaleAtThirtySeconds() {
        BatteryTemperatureReading reading = BatteryTemperatureReading.checking().update(20, 1000);
        assertFalse(reading.stale(30999));
        assertTrue(reading.stale(31000));
        assertTrue(reading.label(31000, false).contains("오래된 값"));
    }
    @Test public void newSuccessfulReadRecoversFromStaleAndReplacesValue() {
        BatteryTemperatureReading reading = BatteryTemperatureReading.checking().update(17, 1000).update(null, 11000);
        reading = reading.update(18, 21000);
        assertEquals("배터리 온도: 18°C", reading.label(21000, true));
        assertFalse(reading.stale(21000));
    }
    @Test public void malformedTemperatureCannotReplacePreviouslyValidValue() {
        for (Integer value : new Integer[]{null, -128, 61, 127, 255}) {
            BatteryTemperatureReading reading = BatteryTemperatureReading.checking().update(20, 1000).update(value, 11000);
            assertEquals(Integer.valueOf(20), reading.minimumC);
            assertTrue(reading.stale(11000));
        }
    }
    @Test public void zeroAndNegativeTemperaturesAreRealValues() {
        assertEquals("배터리 온도: 0°C", BatteryTemperatureReading.checking().update(0,1000).label(1000,true));
        assertEquals("배터리 온도: -40°C", BatteryTemperatureReading.checking().update(-40,1000).label(1000,true));
    }
    @Test public void clockResetCannotPresentAnOldValueAsFresh() {
        BatteryTemperatureReading reading = BatteryTemperatureReading.checking().update(20, 5000);
        assertTrue(reading.stale(1000));
        assertTrue(reading.label(1000,false).contains("0초 전"));
    }
    @Test public void unchangedValueStillUpdatesResponseAgeThroughoutActiveLengthInterval() {
        BatteryTemperatureReading reading = BatteryTemperatureReading.checking().update(16, 1000);
        for (long now = 11000; now <= 241000; now += 10000) {
            reading = reading.update(16, now);
            assertEquals("배터리 온도: 16°C", reading.label(now, true));
            assertTrue(reading.label(now, false).endsWith("수신 0초 전"));
            assertTrue(reading.label(now + 9000, false).endsWith("수신 9초 전"));
            assertFalse(reading.stale(now + 9000));
        }
        assertEquals(241000, reading.receivedAt);
    }
}
