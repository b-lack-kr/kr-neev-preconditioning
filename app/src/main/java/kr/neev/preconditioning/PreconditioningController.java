package kr.neev.preconditioning;

/** Single-writer controller. Time updates the display; it never implies activation. */
final class PreconditioningController {
    static final long ACTIVATION_TIMEOUT_MS = 5 * 60 * 1000L;
    static final long WARM_NOTICE_MS = 10 * 1000L;
    interface Journal { void requested(boolean value); }
    private final VehiclePort vehicle;
    private final Journal journal;
    private VehiclePort.Reading reading;
    private boolean requested, activeSeen, stopping, released, commandFailed, activationTimedOut;
    private long startedAt, endedAt, nextMaintenance;
    private long warmUntil;
    private int warmTemperatureC;
    private PreconditioningSnapshot.State state = PreconditioningSnapshot.State.CHECKING;
    private String detail = "차량 서비스를 확인하고 있습니다";
    private String commandError = "";

    PreconditioningController(VehiclePort vehicle, Journal journal) {
        this.vehicle = vehicle; this.journal = journal;
    }
    void recover(boolean pending, long now) {
        if (pending) {
            requested = true; startedAt = now;
            // A recovered process must cancel uncertain ownership, never send Start.
            stop(now);
        }
    }
    void refresh(long now) {
        if (warmUntil != 0 && now >= warmUntil) {
            warmUntil = 0;
            state = PreconditioningSnapshot.State.CHECKING;
        }
        try { reading = vehicle.read(); }
        catch (Exception e) {
            reading = null;
            if (cancelExpiredRequest(now)) return;
            state = PreconditioningSnapshot.State.UNKNOWN;
            detail = "차량 상태를 확인할 수 없습니다. " + message(e);
            return;
        }
        // A fresh Active indication wins at the deadline; active sessions have no
        // five-minute cutoff. Unknown/missing feedback must not prolong a pending
        // request forever, but cancellation never implies confirmed Off.
        if (requested && !stopping && reading.indicator == 1) activeSeen = true;
        if (cancelExpiredRequest(now)) return;
        if (reading.indicator != 0 && reading.indicator != 1) {
            state = PreconditioningSnapshot.State.UNKNOWN;
            detail = "차량에서 확인 가능한 배터리 컨디셔닝 상태를 보내지 않았습니다.";
            return;
        }
        if (stopping) {
            if (released && reading.indicator == 0) {
                try { journal.requested(false); }
                catch (RuntimeException e) { fail("취소 요청을 보냈지만 작동 종료를 기록하지 못했습니다. ", e); return; }
                requested = false; stopping = false;
                endedAt = now;
                state = activationTimedOut ? PreconditioningSnapshot.State.TIMED_OUT : PreconditioningSnapshot.State.ENDED;
                detail = activationTimedOut
                    ? "작동이 확인되지 않았습니다. 대기 시간: " + timeoutText() + ". 취소가 확인되었으며 차량이 꺼짐 상태를 보고했습니다."
                    : "취소 요청을 보냈으며 차량이 배터리 컨디셔닝 꺼짐을 보고했습니다.";
            } else if (released) {
                state = PreconditioningSnapshot.State.STOPPING;
                detail = "취소 요청을 보냈습니다. 차량의 꺼짐 응답을 기다리고 있습니다.";
            }
            return;
        }
        if (requested) {
            if (reading.ready == 0 || reading.enabled == 1 || (activeSeen && reading.indicator == 0)) {
                stop(now);
                return;
            }
            if (commandFailed) {
                state = PreconditioningSnapshot.State.ERROR; detail = commandError; return;
            }
            if (reading.ready != 1 || reading.enabled != 2) {
                state = PreconditioningSnapshot.State.UNKNOWN;
                detail = "차량 작동 상태를 알 수 없습니다. 중지는 계속 사용할 수 있습니다.";
                return;
            }
            if (reading.indicator == 1) {
                activeSeen = true; state = PreconditioningSnapshot.State.ACTIVE;
                detail = "차량이 배터리 컨디셔닝 작동 중 상태를 보고했습니다.";
            } else {
                state = PreconditioningSnapshot.State.WAITING;
                detail = waitingDetail();
            }
            // Refresh the OEM request metadata. Never resend the Start pulse.
            long interval = vehicle.maintenanceIntervalMs();
            if (interval > 0 && now >= nextMaintenance) {
                try { vehicle.maintainManual(); nextMaintenance = now + interval; }
                catch (Exception e) { fail("수동 요청 유지에 실패했습니다. ", e); }
            }
            return;
        }
        if (warmUntil > now && reading.indicator == 0 && reading.ready == 1
                && reading.enabled == 2 && vehicle.unavailableReason().isEmpty()) {
            state = PreconditioningSnapshot.State.WARM;
            return;
        }
        warmUntil = 0;
        if (state == PreconditioningSnapshot.State.ENDED || state == PreconditioningSnapshot.State.TIMED_OUT) return;
        String unavailable = vehicle.unavailableReason();
        if (!unavailable.isEmpty()) {
            state = PreconditioningSnapshot.State.UNAVAILABLE; detail = unavailable;
        } else if (reading.enabled != 2) {
            state = PreconditioningSnapshot.State.UNAVAILABLE;
            detail = "먼저 차량의 EV 설정에서 배터리 컨디셔닝을 켜세요.";
        } else if (reading.ready != 1) {
            state = PreconditioningSnapshot.State.UNAVAILABLE;
            detail = "차량이 주행 준비(READY) 상태를 보고하지 않았습니다.";
        } else {
            state = PreconditioningSnapshot.State.IDLE;
            detail = reading.indicator == 1 ? "차량이 배터리 컨디셔닝을 제어하고 있습니다."
                : "배터리 컨디셔닝 시작을 차량에 요청합니다.";
        }
    }
    boolean start(long now) {
        return start(now, null);
    }
    boolean start(long now, Integer minimumTemperatureC) {
        return start(now, minimumTemperatureC, false);
    }
    boolean startAnyway(long now) {
        if (state != PreconditioningSnapshot.State.WARM || warmUntil <= now) return false;
        return start(now, null, true);
    }
    private boolean start(long now, Integer minimumTemperatureC, boolean requestAnyway) {
        if (!snapshot(now).canStart) return false;
        // A queued duplicate Start must never become consent to override a warning.
        if (warmUntil > now && !requestAnyway) return false;
        warmUntil = 0;
        if (!requestAnyway && BatteryTemperature.isWarm(minimumTemperatureC, vehicle.warmThresholdC())) {
            warmUntil = now + WARM_NOTICE_MS;
            warmTemperatureC = minimumTemperatureC;
            endedAt = 0;
            state = PreconditioningSnapshot.State.WARM;
            // This is a local advisory: do not journal, Start, maintain or cancel.
            return false;
        }
        requested = true; stopping = false; released = false; activeSeen = false; commandFailed = false; activationTimedOut = false;
        startedAt = now; endedAt = 0;
        try {
            journal.requested(true); // Record uncertain ownership before any transmission.
        } catch (RuntimeException e) {
            requested = false; fail("작동 상태를 기록하지 못해 시작 요청을 보내지 않았습니다. ", e);
            return false;
        }
        try {
            vehicle.beginManual();
            nextMaintenance = now + vehicle.maintenanceIntervalMs();
            state = PreconditioningSnapshot.State.WAITING;
            detail = waitingDetail();
            return true;
        } catch (Exception e) { fail("시작 요청을 확인하지 못했습니다. 중지는 계속 사용할 수 있습니다. ", e); return false; }
    }
    private String waitingDetail() {
        return "요청을 보냈지만 아직 작동하지 않습니다. 차량 조건에 따라 작동하지 않을 수 있으며, "
            + timeoutText() + " 동안 작동이 확인되지 않으면 취소합니다.";
    }
    private String timeoutText() {
        long minutes = vehicle.activationTimeoutMs() / 60000;
        return minutes + "분";
    }
    private boolean cancelExpiredRequest(long now) {
        if (requested && !stopping && !activeSeen && now - startedAt >= vehicle.activationTimeoutMs()) {
            activationTimedOut = true;
            stop(now);
            return true;
        }
        return false;
    }
    void stop(long now) {
        if (!requested || (stopping && released)) return;
        stopping = true;
        state = PreconditioningSnapshot.State.STOPPING;
        detail = activationTimedOut
            ? "작동이 확인되지 않았습니다. 대기 시간: " + timeoutText() + ". 취소 중이며 차량의 꺼짐 응답을 기다리고 있습니다."
            : "수동 요청을 해제하고 차량의 꺼짐 응답을 기다리고 있습니다.";
        try { vehicle.stopAndRelease(); released = true; }
        catch (Exception e) { fail("취소가 확인되지 않았습니다. 중지를 다시 시도할 수 있습니다. ", e); }
    }
    PreconditioningSnapshot snapshot(long now) {
        int indicator = reading == null ? -1 : reading.indicator;
        boolean known = reading != null && (indicator == 0 || indicator == 1);
        boolean canStart = !requested && known && indicator == 0 && reading.enabled == 2
            && reading.ready == 1 && vehicle.unavailableReason().isEmpty();
        long resetSeconds = state == PreconditioningSnapshot.State.WARM && warmUntil > now
            ? (warmUntil - now + 999) / 1000 : 0;
        String displayDetail = resetSeconds > 0
            ? "배터리 최저 온도 " + warmTemperatureC + "°C가 예상 가열 기준("
                + vehicle.warmThresholdC() + "°C) 이상입니다. 요청을 보내지 않았습니다. "
                + resetSeconds + "초 후 버튼이 초기화됩니다. ‘그래도 시작’을 누르면 수동 요청을 보내며 가열 여부는 차량이 결정합니다."
            : detail;
        // A successful read passed the firmware gate; read errors only name firmware when it failed.
        int supported = !vehicle.unavailableReason().isEmpty() ? PreconditioningSnapshot.FAIL
            : reading != null ? PreconditioningSnapshot.PASS
            : state == PreconditioningSnapshot.State.UNKNOWN && (detail.contains("firmware") || detail.contains("펌웨어")) ? PreconditioningSnapshot.FAIL
            : PreconditioningSnapshot.UNKNOWN;
        return new PreconditioningSnapshot(state, requested, canStart,
            requested && !(stopping && released), indicator,
            reading == null ? -1 : reading.failureRaw,
            requested ? Math.max(0, now - startedAt) : endedAt > 0 ? Math.max(0, endedAt - startedAt) : 0,
            displayDetail, resetSeconds, reading == null ? -1 : reading.ready,
            reading == null ? -1 : reading.enabled, supported);
    }
    private void fail(String prefix, Exception e) {
        commandFailed = true;
        state = PreconditioningSnapshot.State.ERROR; detail = prefix + message(e); commandError = detail;
    }
    private static String message(Exception e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }
}
