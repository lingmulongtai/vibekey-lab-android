package com.lingm.vibekey;

import org.junit.Test;
import static org.junit.Assert.*;

public class KeyPressTrackerTest {
    @Test public void disconnectRemovesOnlyItsOwnPendingKeys() {
        KeyPressTracker tracker = new KeyPressTracker(); tracker.down(4, 28, 66); tracker.down(5, 28, 66);
        assertTrue(tracker.clearDevice(4)); assertFalse(tracker.up(4, 28, 66)); assertTrue(tracker.up(5, 28, 66));
    }
    @Test public void repeatsArePairedAndFreshDownRecoversAfterMissingUp() {
        KeyPressTracker tracker = new KeyPressTracker(); tracker.down(4, 28, 66);
        assertTrue(tracker.consumeRepeatedDown(4, 28, 66, 1));
        assertTrue(tracker.consumeRepeatedDown(4, 28, 66, 4));
        assertFalse(tracker.consumeRepeatedDown(5, 28, 66, 2));
        assertFalse(tracker.consumeRepeatedDown(4, 28, 66, 0));
        assertFalse(tracker.up(4, 28, 66));
        tracker.down(4, 28, 66); assertTrue(tracker.up(4, 28, 66));
    }
    @Test public void isolatesDeviceAndScanCode() {
        KeyPressTracker tracker = new KeyPressTracker(); tracker.down(4, 28, 66);
        assertFalse(tracker.up(5, 28, 66)); assertFalse(tracker.up(4, 29, 66));
        assertTrue(tracker.up(4, 28, 66)); assertTrue(tracker.isEmpty());
    }
    @Test public void pairsOnceAndClearsOnTimeout() {
        KeyPressTracker tracker = new KeyPressTracker(); tracker.down(4, 28, 66); tracker.down(4, 28, 66);
        assertTrue(tracker.up(4, 28, 66)); assertFalse(tracker.up(4, 28, 66));
        tracker.down(4, 28, 66); tracker.clear(); assertTrue(tracker.isEmpty());
    }
}
