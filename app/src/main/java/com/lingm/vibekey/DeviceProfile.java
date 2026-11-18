package com.lingm.vibekey;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.InputDevice;
import android.view.KeyEvent;

final class DeviceProfile {
    // Connection-scoped selection. Identifiers never enter preferences, diagnostics, or backup.
    private static final InputRoles inputs = new InputRoles();
    private static final ControlLayers panelInputs = new ControlLayers();
    private final SharedPreferences preferences;
    DeviceProfile(Context context) {
        preferences = context.getSharedPreferences("profile", Context.MODE_PRIVATE);
        preferences.edit().remove("descriptor").remove("vid").remove("pid").apply();
    }
    boolean select(InputDevice device) {
        if (!selectable(device) || !device.supportsSource(InputDevice.SOURCE_MOUSE)) return false;
        inputs.selectWheel(device.getId(), device.getDescriptor(), device.getSources()); return true;
    }
    boolean selectButtons(InputDevice device) {
        if (!selectable(device) || !device.supportsSource(InputDevice.SOURCE_KEYBOARD)) return false;
        clearMappings(); panelInputs.clearLearned();
        inputs.selectButtons(device.getId(), device.getDescriptor(), device.getSources()); return true;
    }
    private boolean selectable(InputDevice device) {
        if (device == null || device.isVirtual() || !device.isExternal() || device.getId() < 0 || device.getDescriptor().isEmpty()) return false;
        InputDevice current = InputDevice.getDevice(device.getId());
        return current != null && current.getDescriptor().equals(device.getDescriptor()) && current.getSources() == device.getSources();
    }
    boolean matches(InputDevice device) {
        return device != null && !device.isVirtual() && device.isExternal() && selected()
                && inputs.matchesWheel(device.getId(), device.getDescriptor(), device.getSources());
    }
    boolean matchesButtons(InputDevice device) {
        return device != null && !device.isVirtual() && device.isExternal() && buttonsSelected()
                && inputs.matchesButtons(device.getId(), device.getDescriptor(), device.getSources());
    }
    boolean selected() { return current(false) != null; }
    boolean buttonsSelected() { return current(true) != null; }
    InputDevice buttonDevice() { return current(true); }
    int wheelSelectionId() { return inputs.wheelId(); }
    int buttonSelectionId() { return inputs.buttonsId(); }
    ControlLayers panelInputs() { return panelInputs; }
    boolean references(int id) { return inputs.contains(id); }
    boolean revalidateChanged(int id) {
        if (!references(id)) return false;
        InputDevice device = InputDevice.getDevice(id);
        if (selectable(device) && inputs.stillSelected(id, device.getDescriptor(), device.getSources())) return false;
        return invalidate(id);
    }
    private InputDevice current(boolean buttons) {
        int id = buttons ? inputs.buttonsId() : inputs.wheelId(); if (id < 0) return null;
        InputDevice device = InputDevice.getDevice(id);
        if (device == null || device.isVirtual() || !device.isExternal()
                || !(buttons ? inputs.matchesButtons(id, device.getDescriptor(), device.getSources()) : inputs.matchesWheel(id, device.getDescriptor(), device.getSources()))) {
            invalidate(id); return null;
        }
        return device;
    }
    boolean invalidate(int inputId) {
        if (inputId >= 0 && inputs.buttonsId() == inputId) { clearMappings(); panelInputs.clearLearned(); }
        return inputs.invalidate(inputId);
    }
    enum LearnStatus { STORED, CONFLICT, UNSUPPORTED }
    record LearnResult(LearnStatus status, String conflict) { }
    LearnResult learn(String action, KeyEvent event) {
        if (action.equals("send") || java.util.Arrays.stream(ACTIONS).noneMatch(action::equals)
                || (event.getKeyCode() == KeyEvent.KEYCODE_UNKNOWN && event.getScanCode() == 0)
                || KeyEvent.isModifierKey(event.getKeyCode())) return new LearnResult(LearnStatus.UNSUPPORTED, "");
        String signature = signature(event);
        for (String candidate : ACTIONS) {
            if (!candidate.equals(action) && signature.equals(preferences.getString(candidate, "")))
                return new LearnResult(LearnStatus.CONFLICT, candidate);
        }
        preferences.edit().putString(action, signature).apply();
        return new LearnResult(LearnStatus.STORED, "");
    }
    String action(KeyEvent event) {
        if (!matchesButtons(event.getDevice())) return "";
        for (String candidate : ACTIONS) if (signature(event).equals(preferences.getString(candidate, ""))) return candidate;
        return "";
    }
    boolean hasActiveBindings() {
        for (String action : ACTIONS) if (!action.equals("send") && !preferences.getString(action, "").isEmpty()) return true;
        return false;
    }
    void clearMapping(String action) { preferences.edit().remove(action).apply(); }
    void clearMappings() { SharedPreferences.Editor edit = preferences.edit(); for (String action : ACTIONS) edit.remove(action); edit.apply(); }
    String mapping(String action) { return preferences.getString(action, "未登録"); }
    String target() { return preferences.getString("target", ""); }
    void target(String value) { preferences.edit().putString("target", value.trim()).apply(); }
    int distanceDp() { return preferences.getInt("distance", 80); }
    void distanceDp(int value) { preferences.edit().putInt("distance", Math.max(16, Math.min(200, value))).apply(); }
    static String signature(KeyEvent event) {
        return event.getKeyCode() + ":" + event.getScanCode() + ":"
                + (KeyEvent.normalizeMetaState(event.getMetaState()) & KeyEvent.getModifierMetaStateMask());
    }
    static final String[] ACTIONS = { "dictation", "click", "send", "back" };
}

