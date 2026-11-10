package kr.neev.preconditioning;

/** Display-only sample. Receipt age is not the vehicle sensor's own timestamp. */
final class BatteryTemperatureReading {
    static final long REFRESH_MS = 10_000;
    static final long STALE_MS = 30_000;
    final Integer minimumC;
    final long receivedAt;
    final boolean attempted, failed;
    private BatteryTemperatureReading(Integer minimumC, long receivedAt, boolean attempted, boolean failed) {
        this.minimumC = minimumC; this.receivedAt = receivedAt;
        this.attempted = attempted; this.failed = failed;
    }
    static BatteryTemperatureReading checking() {
        return new BatteryTemperatureReading(null, 0, false, false);
    }
    BatteryTemperatureReading update(Integer minimum, long now) {
        if (minimum == null || minimum < -40 || minimum > 60)
            return new BatteryTemperatureReading(minimumC, receivedAt, true, true);
        return new BatteryTemperatureReading(minimum, now, true, false);
    }
    boolean stale(long now) {
        return minimumC != null && (failed || now < receivedAt || now - receivedAt >= STALE_MS);
    }
    String age(long now) {
        if (minimumC == null) return attempted ? "확인 불가" : "확인 중…";
        long seconds = Math.max(0, now - receivedAt) / 1000;
        return (stale(now) ? "오래된 값 · 마지막 수신 " : "수신 ") + seconds + "초 전";
    }
    String label(long now, boolean compact) {
        String prefix = compact ? "배터리 온도: " : "배터리 팩 최저 온도: ";
        if (minimumC == null) return prefix + (attempted ? "확인 불가" : "확인 중…");
        String value = minimumC + "°C";
        // Keep stale visible in the narrow Home row; Advanced retains last value.
        if (compact) return prefix + (stale(now) ? "오래된 값" : value);
        long seconds = Math.max(0, now - receivedAt) / 1000;
        return prefix + value + (stale(now) ? " · 오래된 값; 마지막 수신 " : " · 수신 ") + seconds + "초 전";
    }
}
