package com.lingm.vibekey;

import java.util.function.BooleanSupplier;

/** Routing order is intentional: unrelated input never reaches expensive accessibility context reads. */
final class InputRouting {
    enum Motion { STOP, IGNORE, SCROLL }
    enum Key { BYPASS, STOP, PROCESS }
    static Motion motion(boolean selected, boolean scroll, BooleanSupplier topology, BooleanSupplier context) {
        if (!selected || !topology.getAsBoolean() || !context.getAsBoolean()) return Motion.STOP;
        return scroll ? Motion.SCROLL : Motion.IGNORE;
    }
    static Key key(boolean optedIn, boolean selected, boolean hasBindings, boolean freshDown, BooleanSupplier context) {
        if (!optedIn || !selected || !hasBindings || !freshDown) return Key.BYPASS;
        return context.getAsBoolean() ? Key.PROCESS : Key.STOP;
    }
    static boolean keyFilter(boolean capturing, boolean optedIn, boolean hasBindings, boolean buttonSourceReady, boolean held) {
        return held || (capturing && optedIn && hasBindings && buttonSourceReady);
    }
}
