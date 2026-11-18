package com.lingm.vibekey;

import org.junit.Test;
import static org.junit.Assert.*;

public class ExperimentSafetyTest {
    @Test public void requiresExactlyOwnServiceAndOneActiveDisplay() {
        assertTrue(ExperimentSafety.environment(1, true, false, 1, true));
        assertFalse(ExperimentSafety.environment(0, true, false, 1, true));
        assertFalse(ExperimentSafety.environment(2, true, false, 1, true));
        assertFalse(ExperimentSafety.environment(1, false, false, 1, true));
        assertFalse(ExperimentSafety.environment(1, true, true, 1, true));
        assertFalse(ExperimentSafety.environment(1, true, false, 2, true));
        assertFalse(ExperimentSafety.environment(1, true, false, 1, false));
    }
    @Test public void refusesUnknownFocusAndExternalDisplay() {
        assertTrue(ExperimentSafety.focus(true, true, true, false, false, false));
        assertFalse(ExperimentSafety.focus(false, true, true, false, false, false));
        assertFalse(ExperimentSafety.focus(true, false, true, false, false, false));
        assertFalse(ExperimentSafety.focus(true, true, false, false, false, false));
    }
    @Test public void refusesSensitiveAndAllEditableFocus() {
        assertFalse(ExperimentSafety.focus(true, true, true, true, false, false));
        assertFalse(ExperimentSafety.focus(true, true, true, false, true, false));
        assertFalse(ExperimentSafety.focus(true, true, true, false, false, true));
    }
}
