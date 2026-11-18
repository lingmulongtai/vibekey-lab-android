package com.lingm.vibekey;

/** Immutable session selection, expires even if the companion UI goes away. */
final class SessionGate {
    private String target = "";
    private long deadline;
    void arm(String packageName, long now) {
        if (packageName == null || !packageName.matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+"))
            throw new IllegalArgumentException("対象のパッケージ名を確認してください");
        target = packageName; deadline = now + 600_000;
    }
    boolean active(long now) { return !target.isEmpty() && now < deadline; }
    boolean allows(String foreground, long now) { return active(now) && target.equals(foreground); }
    void stop() { target = ""; deadline = 0; }
    String target() { return target; }
}

