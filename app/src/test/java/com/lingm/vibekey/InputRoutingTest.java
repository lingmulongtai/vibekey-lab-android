package com.lingm.vibekey;

import org.junit.Test;
import static org.junit.Assert.*;

public class InputRoutingTest {
    @Test public void foreignMouseStopsEvenBeforeItsAddNotificationAndWithoutScroll() {
        for (boolean scroll : new boolean[] { false, true })
            assertEquals(InputRouting.Motion.STOP, InputRouting.motion(false, scroll, () -> { throw new AssertionError(); }, () -> { throw new AssertionError(); }));
    }
    @Test public void selectedNonScrollStillChecksSafetyAndNeverCreatesScroll() {
        assertEquals(InputRouting.Motion.IGNORE, InputRouting.motion(true, false, () -> true, () -> true));
        assertEquals(InputRouting.Motion.STOP, InputRouting.motion(true, false, () -> true, () -> false));
        assertEquals(InputRouting.Motion.STOP, InputRouting.motion(true, true, () -> false, () -> { throw new AssertionError(); }));
        assertEquals(InputRouting.Motion.SCROLL, InputRouting.motion(true, true, () -> true, () -> true));
    }
    @Test public void otherKeyboardAndWheelOnlySessionsNeverReadAccessibilityContext() {
        assertEquals(InputRouting.Key.BYPASS, InputRouting.key(false, true, true, true, () -> { throw new AssertionError(); }));
        assertEquals(InputRouting.Key.BYPASS, InputRouting.key(true, false, true, true, () -> { throw new AssertionError(); }));
        assertEquals(InputRouting.Key.BYPASS, InputRouting.key(true, true, false, true, () -> { throw new AssertionError(); }));
        assertEquals(InputRouting.Key.BYPASS, InputRouting.key(true, true, true, false, () -> { throw new AssertionError(); }));
        assertEquals(InputRouting.Key.STOP, InputRouting.key(true, true, true, true, () -> false));
        assertEquals(InputRouting.Key.PROCESS, InputRouting.key(true, true, true, true, () -> true));
    }
    @Test public void filteringRequiresOptInActualBindingsAndAnInputSource() {
        assertFalse(InputRouting.keyFilter(true, false, true, true, false));
        assertFalse(InputRouting.keyFilter(true, true, false, true, false));
        assertFalse(InputRouting.keyFilter(true, true, true, false, false));
        assertFalse(InputRouting.keyFilter(false, true, true, true, false));
        assertTrue(InputRouting.keyFilter(true, true, true, true, false));
        assertTrue(InputRouting.keyFilter(false, false, false, false, true));
    }
}
