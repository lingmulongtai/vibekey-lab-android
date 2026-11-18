package com.lingm.vibekey;

import java.util.HashMap;

/** A mapped action belongs to one DOWN and executes only on its valid, uncancelled UP. */
final class ButtonSequence {
    static final long RELEASE_GRACE_MS = 5_000;
    private record Key(int device, int scan, int code) { }
    private record Press(String action, int generation) { }
    record Release(boolean owned, String action) { }
    private final HashMap<Key, Press> held = new HashMap<>();
    void down(int device, int scan, int code, String action, int generation) {
        held.put(new Key(device, scan, code), new Press(action, generation));
    }
    boolean repeatedDown(int device, int scan, int code, int repeats) {
        Key key = new Key(device, scan, code);
        if (repeats == 0) { held.remove(key); return false; }
        return held.containsKey(key);
    }
    Release up(int device, int scan, int code, int generation, boolean cancelled) {
        Press press = held.remove(new Key(device, scan, code));
        if (press == null) return new Release(false, "");
        return new Release(true, !cancelled && press.generation == generation ? press.action : "");
    }
    boolean clearDevice(int device) { return held.keySet().removeIf(key -> key.device == device); }
    boolean isEmpty() { return held.isEmpty(); }
    void clear() { held.clear(); }
}
