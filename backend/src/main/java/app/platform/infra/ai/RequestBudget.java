package app.platform.infra.ai;

import java.time.Clock;

/**
 * Global cap on model calls per minute, on top of the per-visitor rate limit: keeps the bill bounded even under a
 * flood of requests. Over the cap, callers fall back to the deterministic rules.
 */
public final class RequestBudget {

    private static final long WINDOW_MILLIS = 60_000;

    private final int perMinute;
    private final Clock clock;
    private long windowStart;
    private int used;

    public RequestBudget(int perMinute, Clock clock) {
        this.perMinute = perMinute;
        this.clock = clock;
    }

    public synchronized boolean tryAcquire() {
        long now = clock.millis();
        if (now - windowStart >= WINDOW_MILLIS) {
            windowStart = now;
            used = 0;
        }
        if (used >= perMinute) {
            return false;
        }
        used++;
        return true;
    }
}
