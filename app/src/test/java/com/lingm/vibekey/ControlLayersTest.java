package com.lingm.vibekey;

import org.junit.Test;
import static org.junit.Assert.*;
import static com.lingm.vibekey.ControlLayers.*;

public class ControlLayersTest {
    @Test public void layerActionsStaySeparateAndInvalidStoredActionsFallBackSafely() {
        assertNotEquals(preferenceKey(Layer.PRIMARY, Control.K1), preferenceKey(Layer.ALTERNATE, Control.K1));
        assertEquals(Layer.PRIMARY, Layer.PRIMARY.other().other());
        assertEquals(Action.SMOOTH, restore(Control.ROTATE, "VOICE"));
        assertEquals(Action.NONE, restore(Control.K1, "SMOOTH"));
        assertEquals(Action.LAYER, restore(Control.PRESS, "LAYER"));
    }

    @Test public void duplicateSignalCannotOverwriteAPhysicalControl() {
        ControlLayers model = new ControlLayers();
        assertTrue(model.learn(Control.K1, "synthetic-press"));
        assertFalse(model.learn(Control.K2, "synthetic-press"));
        assertEquals(Control.K1, model.lookup("synthetic-press"));
        assertTrue(model.learn(Control.K1, "different-press"));
        assertNull(model.lookup("synthetic-press"));
        assertFalse(model.learn(Control.ROTATE, "keyboard-signal"));
        model.clearLearned();
        assertNull(model.lookup("different-press"));
    }
}
