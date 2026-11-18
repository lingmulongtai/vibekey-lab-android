package com.lingm.vibekey;

import org.junit.Test;
import static org.junit.Assert.*;

public class SessionGateTest {
    @Test public void neverStartsImplicitlyAndScopesToExactPackage() {
        SessionGate gate = new SessionGate(); assertFalse(gate.active(0));
        gate.arm("com.android.chrome", 100);
        assertTrue(gate.allows("com.android.chrome", 101));
        assertFalse(gate.allows("com.android.chrome.fake", 101));
        assertFalse(gate.allows(null, 101));
        assertFalse(gate.allows("com.android.systemui", 101));
    }
    @Test public void expiresAtTenMinutesAndStopIsImmediate() {
        SessionGate gate = new SessionGate(); gate.arm("example.reader", 100);
        assertTrue(gate.active(600099)); assertFalse(gate.active(600100));
        gate.arm("example.reader", 700000); gate.stop(); assertFalse(gate.active(700001));
    }
    @Test(expected = IllegalArgumentException.class) public void rejectsInvalidPackage() { new SessionGate().arm("", 0); }
}

