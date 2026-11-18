package com.lingm.vibekey;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.accessibilityservice.GestureDescription;
import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.hardware.input.InputManager;
import android.hardware.display.DisplayManager;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.view.Display;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityWindowInfo;
import android.text.InputType;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import java.lang.ref.WeakReference;
import java.util.List;

/** Experimental Android 14+ adapter. Nothing is captured until the user explicitly arms a session. */
public final class ScrollService extends AccessibilityService implements InputManager.InputDeviceListener {
    private static WeakReference<ScrollService> connection = new WeakReference<>(null);
    static ScrollService current() { return connection.get(); }
    String status = "停止中";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final SessionGate gate = new SessionGate();
    private final ScrollEngine queue = new ScrollEngine(400, 65);
    private final ButtonSequence heldKeys = new ButtonSequence();
    private DeviceProfile profile;
    private boolean capturing, inFlight, enteredTarget, releaseUncertain, buttonsEnabled, configurationUncertain = true;
    private int generation;
    private long lastInput;
    private Button stopButton;
    private final Runnable releaseKeys = () -> {
        if (heldKeys.isEmpty()) { setCapture(capturing); return; }
        heldKeys.clear();
        if (!setCapture(false)) return;
        status = "キー解放を確認できないため、捕捉終了とサービス停止を要求しました。ボタンを離してください";
        try { disableSelf(); } catch (RuntimeException failure) { captureFailure(); }
    };
    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            if (!gate.active(now())) { stopSession("期限切れ"); return; }
            if (!inputsAvailable() || locked() || !environmentSafe()) { stopSession("安全条件の変更・端末切断・ロック"); return; }
            updateCapture(); if (gate.active(now())) handler.postDelayed(this, 200);
        }
    };
    private final BroadcastReceiver screenOff = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { stopSession("画面消灯"); }
    };
    private long now() { return SystemClock.elapsedRealtime(); }
    @Override protected void onServiceConnected() {
        connection = new WeakReference<>(this); profile = new DeviceProfile(this);
        setCapture(false);
        ((InputManager) getSystemService(INPUT_SERVICE)).registerInputDeviceListener(this, handler);
        registerReceiver(screenOff, new IntentFilter(Intent.ACTION_SCREEN_OFF), Context.RECEIVER_NOT_EXPORTED);
    }
    String startSession() { return startSession(false); }
    String startSession(boolean includeButtons) {
        if (!gate.active(now()) && !heldKeys.isEmpty()) return "ボタンを離してから再開始してください。キー解放を待機しています";
        stopSession("開始準備");
        if (releaseUncertain) return status;
        if (!heldKeys.isEmpty()) return "ボタンを離してから再開始してください。キー解放を待機しています";
        if (!profile.selected() || !deviceAvailable()) return "選択端末のマウス入力がありません。他のマウスも外してください";
        if (includeButtons && (!profile.buttonsSelected() || !profile.hasActiveBindings())) return "ボタンの入力元と割当を先に登録してください";
        if (locked()) return "画面ロックを解除してください";
        if (!environmentSafe()) return "他の Accessibility サービス・画面構成があるか、安全条件を確認できないため、この環境では開始しません";
        String target = profile.target();
        if (target.equals(getPackageName()) || target.equals("com.android.systemui") || target.equals("android")) return "通常の対象アプリを選んでください";
        try { gate.arm(target, now()); } catch (IllegalArgumentException error) { return error.getMessage(); }
        buttonsEnabled = includeButtons;
        handler.removeCallbacks(releaseKeys);
        if (!showStop()) { stopSession("STOP 表示失敗"); return "STOP を表示できないため開始しません"; }
        if (!setCapture(false)) { stopSession("入力経路を確認できません"); return status; }
        enteredTarget = false; status = "待機中: 対象アプリを開いてください"; handler.post(refresh); return status;
    }
    void stopSession(String reason) {
        gate.stop(); handler.removeCallbacks(refresh); queue.clear(); buttonsEnabled = false;
        generation++;
        if (!setCapture(false)) return;
        hideStop();
        status = "停止: " + reason + (inFlight ? "（送出済みの最大 120 ms が完了後に停止）" : "");
        // Keep the matching key-up consumed when a key itself ended the session.
        handler.removeCallbacks(releaseKeys);
        if (!heldKeys.isEmpty()) {
            status += "（ボタンを離してください・解放待ち）";
            handler.postDelayed(releaseKeys, ButtonSequence.RELEASE_GRACE_MS);
        }
    }
    private boolean deviceAvailable() {
        int matching = 0;
        for (int id : InputDevice.getDeviceIds()) {
            InputDevice device = InputDevice.getDevice(id);
            if (device == null) continue;
            if (device.supportsSource(InputDevice.SOURCE_MOUSE)) {
                if (profile.matches(device)) matching++; else return false;
            }
        }
        return matching == 1;
    }
    private boolean inputsAvailable() { return deviceAvailable() && (!buttonsEnabled || (profile.buttonsSelected() && profile.hasActiveBindings())); }
    private boolean locked() { return ((KeyguardManager) getSystemService(KEYGUARD_SERVICE)).isKeyguardLocked(); }
    private boolean environmentSafe() {
        try {
            AccessibilityManager manager = (AccessibilityManager) getSystemService(ACCESSIBILITY_SERVICE);
            List<AccessibilityServiceInfo> services = manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
            boolean own = services.size() == 1 && services.get(0).getResolveInfo() != null
                    && services.get(0).getResolveInfo().serviceInfo != null
                    && getPackageName().equals(services.get(0).getResolveInfo().serviceInfo.packageName)
                    && ScrollService.class.getName().equals(services.get(0).getResolveInfo().serviceInfo.name);
            DisplayManager displays = (DisplayManager) getSystemService(DISPLAY_SERVICE);
            Display primary = displays.getDisplay(Display.DEFAULT_DISPLAY);
            return ExperimentSafety.environment(services.size(), own, manager.isTouchExplorationEnabled(),
                    displays.getDisplays().length, primary != null && primary.getState() == Display.STATE_ON);
        } catch (RuntimeException error) { return false; }
    }
    private String packageOf(AccessibilityNodeInfo root) {
        return root == null || root.getPackageName() == null ? "" : root.getPackageName().toString();
    }
    private boolean safeRoot(AccessibilityNodeInfo root) {
        if (root == null || !gate.allows(packageOf(root), now())) return false;
        AccessibilityWindowInfo window = root.getWindow();
        AccessibilityNodeInfo focus = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        if (focus == null) return false;
        int input = focus.getInputType();
        int variation = input & InputType.TYPE_MASK_VARIATION;
        boolean passwordType = ((input & InputType.TYPE_MASK_CLASS) == InputType.TYPE_CLASS_TEXT
                && (variation == InputType.TYPE_TEXT_VARIATION_PASSWORD || variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD || variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD))
                || ((input & InputType.TYPE_MASK_CLASS) == InputType.TYPE_CLASS_NUMBER && variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        return ExperimentSafety.focus(true, true, window != null && window.getDisplayId() == Display.DEFAULT_DISPLAY,
                root.isPassword() || focus.isPassword() || passwordType, focus.isEditable(),
                root.isAccessibilityDataSensitive() || focus.isAccessibilityDataSensitive());
    }
    private boolean allowed() { return gate.active(now()) && inputsAvailable() && !locked() && environmentSafe() && safeRoot(getRootInActiveWindow()); }
    private void updateCapture() {
        if (!gate.active(now()) || !inputsAvailable() || locked() || !environmentSafe()) { stopSession("安全条件が確認できません"); return; }
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (!gate.target().equals(packageOf(root))) {
            if (enteredTarget || capturing) stopSession("対象外へ移動・前面不明");
            else { if (!setCapture(false)) return; status = "待機中: 対象アプリを開いてください"; }
            return;
        }
        if (!safeRoot(root)) { stopSession("編集欄・機密欄またはフォーカス／画面が未確認"); return; }
        enteredTarget = true;
        if (!setCapture(true)) { stopSession("入力捕捉の要求に失敗"); return; }
        status = "対象アプリで補正中";
    }
    private boolean setCapture(boolean enabled) {
        try {
            AccessibilityServiceInfo info = getServiceInfo();
            if (info == null) { captureFailure(); return false; }
            int previousFlags = info.flags, previousSources = info.getMotionEventSources();
            info.setMotionEventSources(enabled ? InputDevice.SOURCE_MOUSE : 0);
            if (gate.active(now())) info.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
            else info.flags &= ~AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
            if (InputRouting.keyFilter(enabled, buttonsEnabled, profile != null && profile.hasActiveBindings(), profile != null && profile.buttonsSelected(), !heldKeys.isEmpty())) info.flags |= AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
            else info.flags &= ~AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
            if (configurationUncertain || previousFlags != info.flags || previousSources != info.getMotionEventSources()) setServiceInfo(info);
            configurationUncertain = false; releaseUncertain = false; capturing = enabled; return true;
        } catch (RuntimeException failure) { captureFailure(); return false; }
    }
    private void captureFailure() {
        // Establish local safety before best-effort remote shutdown. Never recurse via setCapture.
        gate.stop(); queue.clear(); heldKeys.clear(); handler.removeCallbacks(refresh); handler.removeCallbacks(releaseKeys);
        generation++; capturing = false; buttonsEnabled = false; enteredTarget = false; releaseUncertain = true; configurationUncertain = true;
        status = "入力解除は未確認です。Android の Accessibility 設定で VibeKey Lab を OFF にしてください";
        if (stopButton != null) {
            stopButton.setText("設定で OFF");
            stopButton.setOnClickListener(v -> startActivity(new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));
        }
        try { disableSelf(); } catch (RuntimeException ignored) { /* Unknown remote outcome is kept visible above. */ }
    }
    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (capturing && event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                && (event.getPackageName() == null || !gate.target().contentEquals(event.getPackageName()))) {
            stopSession("対象外のウィンドウへ移動"); return;
        }
        if (gate.active(now())) updateCapture();
    }
    @Override public void onMotionEvent(MotionEvent event) {
        if (!capturing) return;
        InputRouting.Motion route = InputRouting.motion(profile.matches(event.getDevice()), event.getActionMasked() == MotionEvent.ACTION_SCROLL, this::inputsAvailable, this::allowed);
        if (route == InputRouting.Motion.STOP) { stopSession("入力元または安全条件が変化"); return; }
        if (route != InputRouting.Motion.SCROLL) return;
        float axis = event.getAxisValue(MotionEvent.AXIS_VSCROLL);
        if (!Float.isFinite(axis) || axis == 0) return;
        float density = getResources().getDisplayMetrics().density;
        queue.add(-axis * profile.distanceDp()); lastInput = now();
        drain(density);
    }
    private void drain(float density) {
        if (inFlight || queue.isIdle()) return;
        if (!allowed()) { stopSession("出力前の安全条件が変化"); return; }
        if (now() - lastInput > 500) { queue.clear(); return; }
        AccessibilityNodeInfo root = getRootInActiveWindow(); Rect region = new Rect();
        if (!safeRoot(root)) { stopSession("前面を確認できません"); return; }
        int windowId = root.getWindowId();
        findScrollable(root, region, new int[] { 0 });
        if (region.height() < 120 * density || region.width() < 80 * density) {
            stopSession("対象アプリのスクロール領域が見つかりません"); return;
        }
        double amount = queue.take(Math.min(160, region.height() / density * 0.4));
        float pixels = (float) amount * density;
        float center = region.exactCenterY();
        Path path = new Path(); path.moveTo(region.exactCenterX(), center + pixels / 2);
        path.lineTo(region.exactCenterX(), center - pixels / 2);
        AccessibilityNodeInfo latest = getRootInActiveWindow();
        if (!allowed() || !safeRoot(latest) || latest.getWindowId() != windowId) { stopSession("出力直前にウィンドウが変化"); return; }
        int current = generation; inFlight = true;
        boolean accepted = dispatchGesture(new GestureDescription.Builder().setDisplayId(Display.DEFAULT_DISPLAY).addStroke(
                new GestureDescription.StrokeDescription(path, 0, 120)).build(), new GestureResultCallback() {
            @Override public void onCompleted(GestureDescription description) {
                inFlight = false;
                if (gate.active(now())) handler.postDelayed(() -> drain(density), 8);
            }
            @Override public void onCancelled(GestureDescription description) {
                inFlight = false;
                if (current == generation) stopSession("ジェスチャーが中断されました");
                else if (gate.active(now())) handler.postDelayed(() -> drain(density), 8);
            }
        }, handler);
        if (!accepted) { inFlight = false; stopSession("Android がジェスチャーを受理しませんでした"); }
    }
    private void findScrollable(AccessibilityNodeInfo node, Rect best, int[] visited) {
        if (node == null || ++visited[0] > 250) return;
        boolean vertical = node.getActionList().contains(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_UP)
                || node.getActionList().contains(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_DOWN);
        if (node.isVisibleToUser() && node.isScrollable() && vertical) {
            Rect candidate = new Rect(); node.getBoundsInScreen(candidate);
            Rect visible = new Rect(); AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root == null) return; root.getBoundsInScreen(visible);
            if (!candidate.intersect(visible)) return;
            if ((long) candidate.width() * candidate.height() > (long) best.width() * best.height()) best.set(candidate);
        }
        for (int i = 0; i < node.getChildCount() && visited[0] < 250; i++) findScrollable(node.getChild(i), best, visited);
    }
    @Override protected boolean onKeyEvent(KeyEvent event) {
        int device = event.getDeviceId(), scan = event.getScanCode(), key = event.getKeyCode();
        if (!heldKeys.isEmpty() && !profile.matchesButtons(event.getDevice()) && heldKeys.clearDevice(device)) stopSession("押下中の入力元が変更されました");
        if (event.getAction() == KeyEvent.ACTION_UP) {
            ButtonSequence.Release release = heldKeys.up(device, scan, key, generation, event.isCanceled());
            if (!release.owned()) return false;
            setCapture(capturing);
            if (!release.action().isEmpty()) {
                if (gate.active(now()) && allowed()) performButtonAction(release.action());
                else stopSession("ボタン解放時の安全条件が変化");
            }
            return true;
        }
        if (event.getAction() == KeyEvent.ACTION_DOWN && heldKeys.repeatedDown(device, scan, key, event.getRepeatCount())) return true;
        if (!gate.active(now())) return false;
        if (!buttonsEnabled) return false;
        InputRouting.Key route = InputRouting.key(true, profile.matchesButtons(event.getDevice()), profile.hasActiveBindings(),
                event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0, this::allowed);
        if (route == InputRouting.Key.BYPASS) return false;
        if (route == InputRouting.Key.STOP) { stopSession("キー操作前の安全条件が変化"); return false; }
        String action = profile.action(event); if (action.isEmpty()) return false;
        if (action.equals("send")) return false; // Editing/IME operations are deliberately unavailable in this build.
        heldKeys.down(device, scan, key, action, generation);
        return true;
    }
    private void performButtonAction(String action) {
        switch (action) {
                case "back": performGlobalAction(GLOBAL_ACTION_BACK); break;
                case "dictation":
                    stopSession("音声入力画面へ移動");
                    startActivity(new Intent(this, DictationActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); break;
                default:
                    AccessibilityNodeInfo root = getRootInActiveWindow();
                    AccessibilityNodeInfo focus = root == null ? null : root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
                    if (!safeRoot(root) || focus == null || !allowed()) { stopSession("入力フォーカスが未確認"); break; }
                    AccessibilityNodeInfo check = getRootInActiveWindow();
                    if (!safeRoot(check) || check.getWindowId() != root.getWindowId()) { stopSession("操作直前にウィンドウが変化"); break; }
                    if (!focus.performAction(AccessibilityNodeInfo.ACTION_CLICK)) status = "対象アプリはこの操作に未対応";
        }
    }
    private boolean showStop() {
        stopButton = new Button(this); stopButton.setText("STOP"); stopButton.setTextColor(0xffffffff); stopButton.setBackgroundColor(0xff9c2333);
        stopButton.setContentDescription("VibeKey の補正を停止"); stopButton.setOnClickListener(v -> stopSession("STOP"));
        float density = getResources().getDisplayMetrics().density;
        WindowManager.LayoutParams params = new WindowManager.LayoutParams((int)(96*density), (int)(48*density),
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.END; params.y = (int) (48*density);
        try { ((WindowManager) getSystemService(WINDOW_SERVICE)).addView(stopButton, params); return true; }
        catch (RuntimeException error) { stopButton = null; return false; }
    }
    private void hideStop() {
        if (stopButton != null) {
            try { ((WindowManager) getSystemService(WINDOW_SERVICE)).removeView(stopButton); } catch (IllegalArgumentException ignored) { }
            stopButton = null;
        }
    }
    private void deviceChanged() { if (gate.active(now()) && !inputsAvailable()) stopSession("入力端末の構成が変更されました"); }
    @Override public void onInputDeviceAdded(int id) { deviceChanged(); }
    @Override public void onInputDeviceRemoved(int id) {
        boolean hadPress = heldKeys.clearDevice(id);
        if (profile.invalidate(id) || hadPress) stopSession("選択端末の切断"); else deviceChanged();
        if (!gate.active(now())) setCapture(false);
    }
    @Override public void onInputDeviceChanged(int id) {
        boolean hadPress = heldKeys.clearDevice(id);
        boolean selected = profile.references(id);
        boolean changed = profile.revalidateChanged(id);
        if ((selected || hadPress) && gate.active(now())) {
            stopSession(changed ? "入力元の構成が変更されました。再選択してください" : "Android の入力元変更通知により実験を停止しました");
        } else deviceChanged();
        if (!gate.active(now())) setCapture(false);
    }
    @Override public void onInterrupt() { stopSession("サービス中断"); }
    @Override public void onDestroy() {
        stopSession("サービス終了"); hideStop(); connection.clear(); handler.removeCallbacksAndMessages(null);
        ((InputManager) getSystemService(INPUT_SERVICE)).unregisterInputDeviceListener(this);
        try { unregisterReceiver(screenOff); } catch (IllegalArgumentException ignored) { }
        super.onDestroy();
    }
}
