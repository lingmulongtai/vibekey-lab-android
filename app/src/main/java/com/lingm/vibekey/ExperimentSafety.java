package com.lingm.vibekey;

/** Fail-closed prerequisites; these checks reduce risk but do not make separate Android IPC atomic. */
final class ExperimentSafety {
    static boolean environment(int enabledServices, boolean onlyOwnService, boolean touchExploration,
                               int displays, boolean primaryOn) {
        return enabledServices == 1 && onlyOwnService && !touchExploration && displays == 1 && primaryOn;
    }
    static boolean focus(boolean rootKnown, boolean focusKnown, boolean primaryDisplay,
                         boolean password, boolean editable, boolean sensitive) {
        return rootKnown && focusKnown && primaryDisplay && !password && !editable && !sensitive;
    }
}
