package com.lingm.vibekey;

import org.junit.Test;
import static org.junit.Assert.*;

public class ButtonSequenceTest {
    @Test public void actionExecutesOnceOnOwnedUpAndNeverOnRepeat() {
        ButtonSequence sequence = new ButtonSequence(); sequence.down(11, 28, 66, "click", 7);
        assertTrue(sequence.repeatedDown(11, 28, 66, 1)); assertTrue(sequence.repeatedDown(11, 28, 66, 20));
        ButtonSequence.Release release = sequence.up(11, 28, 66, 7, false);
        assertTrue(release.owned()); assertEquals("click", release.action());
        assertFalse(sequence.up(11, 28, 66, 7, false).owned());
    }
    @Test public void unownedRepeatAndOtherKeyboardCannotTakeOwnership() {
        ButtonSequence sequence = new ButtonSequence();
        assertFalse(sequence.repeatedDown(11, 28, 66, 3)); assertFalse(sequence.up(11, 28, 66, 7, false).owned());
        sequence.down(11, 28, 66, "click", 7);
        assertFalse(sequence.up(12, 28, 66, 7, false).owned()); assertFalse(sequence.up(11, 29, 66, 7, false).owned());
        assertEquals("click", sequence.up(11, 28, 66, 7, false).action());
    }
    @Test public void stoppedSessionKeepsOwnedLongPressButCancelsItsAction() {
        assertTrue(ButtonSequence.RELEASE_GRACE_MS > 2_000);
        ButtonSequence sequence = new ButtonSequence(); sequence.down(11, 28, 66, "dictation", 7);
        assertTrue(sequence.repeatedDown(11, 28, 66, 40));
        ButtonSequence.Release afterStop = sequence.up(11, 28, 66, 8, false);
        assertTrue(afterStop.owned()); assertEquals("", afterStop.action());
    }
    @Test public void cancellationCapabilityChangeAndFreshDownDiscardOldAction() {
        ButtonSequence sequence = new ButtonSequence(); sequence.down(11, 28, 66, "click", 7);
        assertEquals("", sequence.up(11, 28, 66, 7, true).action());
        sequence.down(11, 28, 66, "click", 7); assertTrue(sequence.clearDevice(11)); assertTrue(sequence.isEmpty());
        sequence.down(11, 28, 66, "click", 7); assertFalse(sequence.repeatedDown(11, 28, 66, 0));
        assertFalse(sequence.up(11, 28, 66, 7, false).owned());
    }
}
