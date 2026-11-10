package kr.neev.preconditioning;

import java.util.Locale;

/** Immutable UI state. A pending request and the vehicle indicator are independent. */
final class PreconditioningSnapshot {
    enum State { CHECKING, UNAVAILABLE, IDLE, WARM, WAITING, ACTIVE, STOPPING, ENDED, TIMED_OUT, UNKNOWN, ERROR }
    final State state;
    final boolean requested, canStart, canStop;
    static final int PASS = 1, FAIL = 0, UNKNOWN = -1;
    final int indicator, failureRaw;
    /** Raw vehicle values, or -1 before a successful read. {@code supported} is a check result. */
    final int ready, enabled, supported;
    final long elapsedMs;
    final long resetSeconds;
    final String detail;
    PreconditioningSnapshot(State state, boolean requested, boolean canStart, boolean canStop,
                            int indicator, int failureRaw, long elapsedMs, String detail) {
        this(state, requested, canStart, canStop, indicator, failureRaw, elapsedMs, detail, 0);
    }
    PreconditioningSnapshot(State state, boolean requested, boolean canStart, boolean canStop,
                            int indicator, int failureRaw, long elapsedMs, String detail, long resetSeconds) {
        this(state, requested, canStart, canStop, indicator, failureRaw, elapsedMs, detail, resetSeconds, -1, -1, UNKNOWN);
    }
    PreconditioningSnapshot(State state, boolean requested, boolean canStart, boolean canStop,
                            int indicator, int failureRaw, long elapsedMs, String detail, long resetSeconds,
                            int ready, int enabled, int supported) {
        this.state = state; this.requested = requested; this.canStart = canStart;
        this.canStop = canStop; this.indicator = indicator; this.failureRaw = failureRaw;
        this.elapsedMs = elapsedMs; this.detail = detail;
        this.resetSeconds = resetSeconds;
        this.ready = ready; this.enabled = enabled; this.supported = supported;
    }
    static PreconditioningSnapshot checking() {
        return new PreconditioningSnapshot(State.CHECKING, false, false, false, -1, -1, 0,
            "차량 서비스를 확인하고 있습니다");
    }
    String title() {
        switch (state) {
            case CHECKING: return "확인 중";
            case UNAVAILABLE: return "사용 불가";
            case IDLE: return indicator == 1 ? "작동 중 · 차량 제어" : "준비됨";
            case WARM: return "배터리 충분히 따뜻함 · " + resetSeconds + "초 후 초기화";
            case WAITING: return "요청 전송됨";
            case ACTIVE: return "작동 중";
            case STOPPING: return "중지 요청됨";
            case ENDED: return "작동 종료";
            case TIMED_OUT: return "작동 미확인";
            case UNKNOWN: return "상태 확인 불가";
            default: return "요청 오류";
        }
    }
    String elapsed() {
        long seconds = Math.max(0, elapsedMs) / 1000;
        return String.format(Locale.US, "%d:%02d", seconds / 60, seconds % 60);
    }
    String indicatorLabel() {
        return indicator == 1 ? "차량 표시: 작동 중" : indicator == 0
            ? "차량 표시: 꺼짐" : "차량 표시: 확인 불가";
    }
    int readyCheck() { return ready == 1 ? PASS : ready == 0 ? FAIL : UNKNOWN; }
    int enabledCheck() { return enabled == 2 ? PASS : enabled >= 0 ? FAIL : UNKNOWN; }
    int indicatorCheck() { return indicator == 0 || indicator == 1 ? PASS : UNKNOWN; }
    String readyValue() { return ready == 1 ? "주행 준비(READY) 상태" : ready == 0 ? "주행 준비 안 됨" : unknown(ready); }
    String enabledValue() {
        return enabled == 2 ? "EV 설정에서 켜짐" : enabled >= 0 ? "EV 설정에서 꺼짐 (원시값 " + enabled + ")" : "알 수 없음";
    }
    String indicatorValue() { return indicator == 1 ? "작동 중" : indicator == 0 ? "꺼짐" : unknown(indicator); }
    String supportedValue() {
        return supported == PASS ? "지원됨" : supported == FAIL ? "지원 안 됨" : state == State.CHECKING ? "확인 중" : "알 수 없음";
    }
    private static String unknown(int raw) { return raw < 0 ? "알 수 없음" : "알 수 없음 (원시값 " + raw + ")"; }
}
