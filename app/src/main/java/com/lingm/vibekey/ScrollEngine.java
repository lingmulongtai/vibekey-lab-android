package com.lingm.vibekey;

/** Frame-rate independent low-pass integration; units are pixels, never HID report bytes. */
public final class ScrollEngine {
    private double pending;
    private final double maxPending;
    private final double timeConstantMs;

    public ScrollEngine(double maxPending, double timeConstantMs) {
        if (!Double.isFinite(maxPending) || !Double.isFinite(timeConstantMs)
                || maxPending <= 0 || timeConstantMs <= 0) throw new IllegalArgumentException();
        this.maxPending = maxPending;
        this.timeConstantMs = timeConstantMs;
    }

    public void add(double pixels) {
        if (!Double.isFinite(pixels) || pixels == 0) return;
        // Reversal must feel immediate instead of waiting for old inertia to drain.
        if (Math.signum(pending) != Math.signum(pixels)) pending = 0;
        pending = Math.max(-maxPending, Math.min(maxPending, pending + pixels));
    }

    public double step(double elapsedMs) {
        if (!Double.isFinite(elapsedMs) || elapsedMs <= 0) return 0;
        double moved = pending * -Math.expm1(-Math.min(elapsedMs, 50) / timeConstantMs);
        pending -= moved;
        if (Math.abs(pending) < 0.05) { moved += pending; pending = 0; }
        return moved;
    }

    public double take(double maximum) {
        if (!Double.isFinite(maximum) || maximum <= 0) return 0;
        double moved = Math.max(-maximum, Math.min(maximum, pending));
        pending -= moved;
        return moved;
    }

    public boolean isIdle() { return pending == 0; }
    public double remaining() { return pending; }
    public void clear() { pending = 0; }
}

