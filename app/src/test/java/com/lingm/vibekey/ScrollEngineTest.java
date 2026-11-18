package com.lingm.vibekey;

import static org.junit.Assert.*;
import org.junit.Test;

public class ScrollEngineTest {
    @Test public void preservesDistanceAtSixtyAndOneTwentyHz() {
        for (double interval : new double[] { 1000.0 / 60, 1000.0 / 120 }) {
            ScrollEngine engine = new ScrollEngine(1000, 65);
            engine.add(240);
            double total = 0;
            for (int i = 0; i < 300; i++) total += engine.step(interval);
            assertEquals(240, total, 0.0001);
            assertTrue(engine.isIdle());
        }
    }
    @Test public void reversalDropsOldBacklog() {
        ScrollEngine engine = new ScrollEngine(400, 65);
        engine.add(300); engine.add(-40);
        assertEquals(-40, engine.remaining(), 0);
        assertTrue(engine.step(16) < 0);
    }
    @Test public void boundsBurstsAndRejectsInvalidInput() {
        ScrollEngine engine = new ScrollEngine(400, 65);
        engine.add(1e10); engine.add(Double.NaN); engine.add(Double.POSITIVE_INFINITY);
        assertEquals(400, engine.remaining(), 0);
        assertEquals(0, engine.step(Double.NaN), 0);
        assertEquals(0, engine.step(-10), 0);
        assertEquals(0, engine.take(-1), 0);
        assertEquals(120, engine.take(120), 0);
        engine.clear(); assertTrue(engine.isIdle());
    }
    @Test public void stallDoesNotProduceHugeJump() {
        ScrollEngine engine = new ScrollEngine(400, 65);
        engine.add(400);
        assertTrue(engine.step(10000) < 220);
    }
}
