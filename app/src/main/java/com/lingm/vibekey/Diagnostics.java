package com.lingm.vibekey;

import android.view.InputDevice;
import android.view.MotionEvent;
import org.json.JSONObject;
import org.json.JSONException;
import java.util.ArrayDeque;

/** Only selected-source vertical wheel deltas and relative times can enter the export buffer. */
final class Diagnostics {
    private final ArrayDeque<JSONObject> events = new ArrayDeque<>();
    private int dropped;
    private boolean recording;
    private int selectedInput = -1;
    private long startedAt;
    String last = "入力待ち";
    void clear() { events.clear(); dropped = 0; last = "入力待ち"; }
    int count() { return events.size(); }
    boolean isRecording() { return recording; }
    void start(int selectedInput, long uptime) {
        if (selectedInput < 0) throw new IllegalArgumentException("入力元を選択してください");
        clear(); this.selectedInput = selectedInput; startedAt = uptime; recording = true;
    }
    void stop() { recording = false; selectedInput = -1; }
    void motion(MotionEvent event) {
        if (!recording || event.getDeviceId() != selectedInput || !event.isFromSource(InputDevice.SOURCE_MOUSE)
                || event.getActionMasked() != MotionEvent.ACTION_SCROLL || event.getEventTime() < startedAt) return;
        float delta = event.getAxisValue(MotionEvent.AXIS_VSCROLL);
        if (!Float.isFinite(delta) || delta == 0) return;
        JSONObject row = new JSONObject(); put(row, "type", "wheel");
        put(row, "elapsedMs", event.getEventTime() - startedAt); put(row, "vscroll", delta);
        add(row); last = "縦回転=" + delta;
    }
    private void add(JSONObject value) { if (events.size() == 2000) { events.removeFirst(); dropped++; } events.add(value); }
    String export() {
        JSONObject header = new JSONObject(); put(header, "schemaVersion", 2);
        put(header, "kind", "selected-vertical-wheel-only"); put(header, "hardwareVerified", false);
        put(header, "appVersion", BuildConfig.VERSION_NAME); put(header, "droppedEvents", dropped);
        StringBuilder result = new StringBuilder(header.toString()).append('\n');
        for (JSONObject event : events) result.append(event).append('\n'); return result.toString();
    }
    static void put(JSONObject value, String key, Object content) {
        try { value.put(key, content); } catch (JSONException error) { throw new IllegalArgumentException(error); }
    }
}
