package com.lingm.vibekey;

import org.junit.Test;
import static org.junit.Assert.*;

public class InputRolesTest {
    @Test public void benignChangeKeepsCurrentIdentityButRemovalCannotRestoreIt() {
        InputRoles roles = new InputRoles();
        roles.selectWheel(11, "wheel", 10);
        assertTrue(roles.stillSelected(11, "wheel", 10));
        assertFalse(roles.stillSelected(11, "wheel", 30));
        assertFalse(roles.stillSelected(12, "wheel", 10));
        roles.invalidate(11);
        assertFalse(roles.stillSelected(11, "wheel", 10));
    }
    @Test public void separateKeyboardNeedsExplicitSelection() {
        InputRoles roles = new InputRoles(); roles.selectWheel(11, "fixture-wheel", 10);
        assertTrue(roles.matchesWheel(11, "fixture-wheel", 10));
        assertFalse(roles.matchesButtons(11, "fixture-wheel", 10));
        roles.selectButtons(12, "fixture-keyboard", 20);
        assertTrue(roles.matchesButtons(12, "fixture-keyboard", 20));
        assertFalse(roles.matchesButtons(13, "fixture-keyboard", 20));
        assertFalse(roles.matchesButtons(11, "fixture-wheel", 10));
    }
    @Test public void sameCompositeSourceCanBeSelectedForBothRoles() {
        InputRoles roles = new InputRoles(); roles.selectWheel(11, "fixture-composite", 30); roles.selectButtons(11, "fixture-composite", 30);
        assertTrue(roles.matchesWheel(11, "fixture-composite", 30)); assertTrue(roles.matchesButtons(11, "fixture-composite", 30));
        roles.invalidate(11); assertEquals(-1, roles.wheelId()); assertEquals(-1, roles.buttonsId());
    }
    @Test public void connectionAndCapabilitiesNeverExpandImplicitly() {
        InputRoles roles = new InputRoles(); roles.selectWheel(11, "fixture-wheel", 10); roles.selectButtons(12, "fixture-keyboard", 20);
        assertFalse(roles.matchesWheel(11, "fixture-wheel", 30));
        assertFalse(roles.matchesWheel(11, "replacement", 10));
        assertFalse(roles.matchesWheel(14, "fixture-wheel", 10));
        assertFalse(roles.invalidate(99)); assertTrue(roles.invalidate(12));
        assertTrue(roles.matchesWheel(11, "fixture-wheel", 10)); assertEquals(-1, roles.buttonsId());
    }
}
