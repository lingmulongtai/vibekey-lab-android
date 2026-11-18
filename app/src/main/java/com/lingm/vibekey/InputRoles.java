package com.lingm.vibekey;

/** Explicit connection-scoped identities. Never merges devices by name, VID or PID. */
final class InputRoles {
    private record Connection(int id, String descriptor, int sources) {
        Connection {
            if (id < 0 || descriptor == null || descriptor.isEmpty() || sources == 0) throw new IllegalArgumentException();
        }
        boolean matches(int id, String descriptor, int sources) {
            return this.id == id && this.descriptor.equals(descriptor) && this.sources == sources;
        }
    }
    private Connection wheel, buttons;
    void selectWheel(int id, String descriptor, int sources) { wheel = new Connection(id, descriptor, sources); }
    void selectButtons(int id, String descriptor, int sources) { buttons = new Connection(id, descriptor, sources); }
    int wheelId() { return wheel == null ? -1 : wheel.id; }
    int buttonsId() { return buttons == null ? -1 : buttons.id; }
    boolean matchesWheel(int id, String descriptor, int sources) { return wheel != null && wheel.matches(id, descriptor, sources); }
    boolean matchesButtons(int id, String descriptor, int sources) { return buttons != null && buttons.matches(id, descriptor, sources); }
    boolean contains(int id) { return id >= 0 && (wheelId() == id || buttonsId() == id); }
    boolean stillSelected(int id, String descriptor, int sources) {
        return matchesWheel(id, descriptor, sources) || matchesButtons(id, descriptor, sources);
    }
    boolean invalidate(int id) {
        boolean changed = false;
        if (wheel != null && wheel.id == id) { wheel = null; changed = true; }
        if (buttons != null && buttons.id == id) { buttons = null; changed = true; }
        return changed;
    }
}
