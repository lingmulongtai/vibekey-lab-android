package com.lingm.vibekey;

import java.util.EnumMap;
import java.util.List;

final class ControlLayers {
    enum Layer {
        PRIMARY("基本"), ALTERNATE("レイヤー B");
        final String label;
        Layer(String label) { this.label = label; }
        Layer other() { return this == PRIMARY ? ALTERNATE : PRIMARY; }
    }

    enum Control {
        ROTATE("ノブを回す"), PRESS("ノブを押す"), K1("K1 · マイク"), K2("K2 · チェック"), K3("K3 · ×");
        final String label;
        Control(String label) { this.label = label; }
    }

    enum Action {
        NONE("何もしない"), SMOOTH("滑らかにスクロール"), DIRECT("補間なしでスクロール"),
        REVERSE("逆方向にスクロール"), LAYER("レイヤーを切り替え"), VOICE("音声入力画面を開く"),
        UP("上へスクロール"), DOWN("下へスクロール");
        final String label;
        Action(String label) { this.label = label; }
    }

    private final EnumMap<Control, String> learned = new EnumMap<>(Control.class);

    static List<Action> options(Control control) {
        return control == Control.ROTATE
                ? List.of(Action.SMOOTH, Action.DIRECT, Action.REVERSE, Action.NONE)
                : List.of(Action.NONE, Action.LAYER, Action.VOICE, Action.UP, Action.DOWN);
    }

    static Action restore(Control control, String saved) {
        for (Action action : options(control)) if (action.name().equals(saved)) return action;
        return control == Control.ROTATE ? Action.SMOOTH : Action.NONE;
    }

    static String preferenceKey(Layer layer, Control control) {
        return "panel." + layer.name() + "." + control.name();
    }

    Control lookup(String signature) {
        for (var entry : learned.entrySet()) if (entry.getValue().equals(signature)) return entry.getKey();
        return null;
    }

    boolean learn(Control control, String signature) {
        if (control == Control.ROTATE || signature.isEmpty()) return false;
        Control previous = lookup(signature);
        if (previous != null && previous != control) return false;
        learned.put(control, signature);
        return true;
    }

    boolean learned(Control control) { return learned.containsKey(control); }
    void forget(Control control) { learned.remove(control); }
    void clearLearned() { learned.clear(); }
}
