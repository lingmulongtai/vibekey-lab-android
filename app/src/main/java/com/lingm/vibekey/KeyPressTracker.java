package com.lingm.vibekey;

import java.util.HashSet;

/** Pairs consumed downs and ups without mixing devices or depending on modifier release order. */
final class KeyPressTracker {
    private final HashSet<String> held = new HashSet<>();
    private String token(int device, int scan, int key) { return device + ":" + scan + ":" + key; }
    void down(int device, int scan, int key) { held.add(token(device, scan, key)); }
    boolean contains(int device, int scan, int key) { return held.contains(token(device, scan, key)); }
    boolean consumeRepeatedDown(int device, int scan, int key, int repeats) {
        if (repeats == 0) { held.remove(token(device, scan, key)); return false; }
        return contains(device, scan, key);
    }
    boolean up(int device, int scan, int key) { return held.remove(token(device, scan, key)); }
    boolean isEmpty() { return held.isEmpty(); }
    boolean clearDevice(int device) { return held.removeIf(token -> token.startsWith(device + ":")); }
    void clear() { held.clear(); }
}
