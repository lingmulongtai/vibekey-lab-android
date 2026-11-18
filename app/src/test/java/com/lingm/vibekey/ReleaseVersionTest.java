package com.lingm.vibekey;

import org.junit.Test;

import static org.junit.Assert.*;

public final class ReleaseVersionTest {
    @Test public void ordersDeveloperAndStableVersions() {
        assertTrue(ReleaseVersion.parse("0.1.10-dev").compareTo(ReleaseVersion.parse("0.1.9")) > 0);
        assertTrue(ReleaseVersion.parse("0.1.4-dev.2").compareTo(ReleaseVersion.parse("0.1.4-dev")) > 0);
        assertTrue(ReleaseVersion.parse("0.1.4").compareTo(ReleaseVersion.parse("0.1.4-dev.2")) > 0);
        assertEquals(0, ReleaseVersion.parse("0.1.4-dev").compareTo(ReleaseVersion.parse("0.1.4-dev")));
    }

    @Test public void rejectsAmbiguousAndMalformedVersions() {
        for (String name : new String[]{"v0.1.4-dev", "0.01.4-dev", "0.1.4-rc", "0.1.4-dev.0",
                "0.1.999999999999-dev", "0.1.4-dev\n", "0.1.4-dev/extra"}) {
            assertNull(name, ReleaseVersion.parse(name));
        }
    }
}
