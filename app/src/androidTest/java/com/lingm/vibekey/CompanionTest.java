package com.lingm.vibekey;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageInfo;
import org.junit.Before;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import static org.junit.Assert.*;
import android.view.MotionEvent;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ScrollView;
import org.json.JSONObject;
import java.util.concurrent.atomic.AtomicReference;

/** Emulator-only integration checks. Synthetic inputs are never AU05 acceptance evidence. */
@SuppressWarnings("deprecation")
@RunWith(JUnit4.class)
public class CompanionTest {
    private android.app.Instrumentation getInstrumentation() {
        return androidx.test.platform.app.InstrumentationRegistry.getInstrumentation();
    }
    private Activity activity;
    @Before public void setUp() throws Exception {
        Intent intent = new Intent(getInstrumentation().getTargetContext(), MainActivity.class).setAction(Intent.ACTION_MAIN)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        activity = getInstrumentation().startActivitySync(intent); getInstrumentation().waitForIdleSync();
    }
    @After public void tearDown() throws Exception {
        if (activity != null) new DeviceProfile(activity).clearMappings();
        if (activity != null) getInstrumentation().runOnMainSync(() -> activity.finish());
        getInstrumentation().waitForIdleSync();
    }
    private Button button(View node, String contains) {
        if (node instanceof Button && ((Button)node).getText().toString().contains(contains)) return (Button)node;
        if (node instanceof ViewGroup) for (int i = 0; i < ((ViewGroup)node).getChildCount(); i++) {
            Button found = button(((ViewGroup)node).getChildAt(i), contains); if (found != null) return found;
        }
        return null;
    }
    private void click(String contains) {
        getInstrumentation().runOnMainSync(() -> {
            Button found = button(activity.getWindow().getDecorView(), contains); assertNotNull(contains, found); found.performClick();
        });
        getInstrumentation().waitForIdleSync();
    }
    private ScrollView nestedScroll(View node) {
        if (node instanceof ScrollView && ((ScrollView)node).getChildAt(0) instanceof android.widget.LinearLayout
                && ((ViewGroup)((ScrollView)node).getChildAt(0)).getChildCount() == 60) return (ScrollView)node;
        if (node instanceof ViewGroup) for (int i = 0; i < ((ViewGroup)node).getChildCount(); i++) {
            ScrollView found = nestedScroll(((ViewGroup)node).getChildAt(i)); if (found != null) return found;
        }
        return null;
    }
    @Test public void testSyntheticScrollMovesAndReverses() throws Exception {
        click("スクロールを比較"); click("補間あり"); click("合成テスト ↓");
        Thread.sleep(800); getInstrumentation().waitForIdleSync();
        AtomicReference<Integer> down = new AtomicReference<>();
        getInstrumentation().runOnMainSync(() -> down.set(nestedScroll(activity.getWindow().getDecorView()).getScrollY()));
        assertTrue("Synthetic down must move preview", down.get() > 0);
        click("合成テスト ↑"); Thread.sleep(800); getInstrumentation().waitForIdleSync();
        getInstrumentation().runOnMainSync(() -> assertTrue(nestedScroll(activity.getWindow().getDecorView()).getScrollY() < down.get()));
    }
    @Test public void testDiagnosticsRemainLocalBoundedAndExplicit() throws Exception {
        Diagnostics capture = new Diagnostics();
        MotionEvent wheel = wheel(41, 1, MotionEvent.ACTION_SCROLL);
        capture.motion(wheel); assertEquals(0, capture.count()); capture.start(41, 100);
        for (int i = 0; i < 2005; i++) capture.motion(wheel);
        String[] lines = capture.export().trim().split("\n");
        assertEquals(2001, lines.length); JSONObject header = new JSONObject(lines[0]);
        assertEquals(5, header.getInt("droppedEvents")); assertFalse(header.getBoolean("hardwareVerified"));
        assertEquals(2, header.getInt("schemaVersion"));
        assertEquals("wheel", new JSONObject(lines[1]).getString("type"));
        // Exact schema allowlists prohibit accidental identifiers/content in future additions.
        assertEquals(5, header.length());
        for (String field : new String[] { "schemaVersion", "kind", "hardwareVerified", "appVersion", "droppedEvents" }) assertTrue(header.has(field));
        JSONObject row = new JSONObject(lines[1]); assertEquals(3, row.length());
        for (String field : new String[] { "type", "elapsedMs", "vscroll" }) assertTrue(row.has(field));
        wheel.recycle();
        capture.clear(); assertEquals(0, capture.count());
    }
    private MotionEvent wheel(int deviceId, float delta, int action) {
        MotionEvent.PointerProperties properties = new MotionEvent.PointerProperties(); properties.id = 0; properties.toolType = MotionEvent.TOOL_TYPE_MOUSE;
        MotionEvent.PointerCoords coordinates = new MotionEvent.PointerCoords(); coordinates.setAxisValue(MotionEvent.AXIS_VSCROLL, delta);
        return MotionEvent.obtain(100, 110, action, 1, new MotionEvent.PointerProperties[] { properties },
                new MotionEvent.PointerCoords[] { coordinates }, 0, 0, 1, 1, deviceId, 0, InputDevice.SOURCE_MOUSE, 0);
    }
    @Test public void testDiagnosticRejectsOtherSourcesMotionAndStoppedCapture() {
        Diagnostics capture = new Diagnostics(); capture.start(41, 100);
        for (MotionEvent event : new MotionEvent[] { wheel(42, 1, MotionEvent.ACTION_SCROLL), wheel(41, 1, MotionEvent.ACTION_HOVER_MOVE), wheel(41, Float.NaN, MotionEvent.ACTION_SCROLL) }) {
            capture.motion(event); event.recycle();
        }
        assertEquals(0, capture.count()); capture.stop();
        MotionEvent event = wheel(41, 1, MotionEvent.ACTION_SCROLL); capture.motion(event); event.recycle(); assertEquals(0, capture.count());
    }
    @Test public void testDiagnosticRejectsKeyboardSourceAndExportsRelativeTimeOnly() throws Exception {
        Diagnostics capture = new Diagnostics(); capture.start(41, 100);
        MotionEvent event = wheel(41, 1, MotionEvent.ACTION_SCROLL); event.setSource(InputDevice.SOURCE_KEYBOARD);
        capture.motion(event); event.recycle(); assertEquals(0, capture.count());
        event = wheel(41, 1, MotionEvent.ACTION_SCROLL); capture.motion(event); event.recycle();
        JSONObject row = new JSONObject(capture.export().trim().split("\n")[1]);
        assertEquals(10, row.getLong("elapsedMs")); assertEquals(3, row.length());
    }
    @Test public void testBothComparisonModesApplyOneDistanceWithoutService() throws Exception {
        click("スクロールを比較"); click("補間なし"); click("合成テスト ↓");
        AtomicReference<Integer> direct = new AtomicReference<>();
        getInstrumentation().runOnMainSync(() -> direct.set(nestedScroll(activity.getWindow().getDecorView()).getScrollY()));
        int expected = Math.round(new DeviceProfile(activity).distanceDp() * activity.getResources().getDisplayMetrics().density) * 3;
        assertEquals(expected, direct.get().intValue());
        click("補間あり"); click("合成テスト ↓"); Thread.sleep(800); getInstrumentation().waitForIdleSync();
        getInstrumentation().runOnMainSync(() -> assertEquals(expected, nestedScroll(activity.getWindow().getDecorView()).getScrollY() - direct.get(), 1));
        assertNull(ScrollService.current());
    }
    @Test public void testLaunchOnlyDeclaresUpdatePermissionsAndLeavesServiceDisabled() throws Exception {
        PackageInfo info = activity.getPackageManager().getPackageInfo(activity.getPackageName(), android.content.pm.PackageManager.GET_PERMISSIONS);
        assertNotNull(info.requestedPermissions);
        assertEquals(java.util.Set.of(android.Manifest.permission.INTERNET,
                        android.Manifest.permission.REQUEST_INSTALL_PACKAGES),
                new java.util.HashSet<>(java.util.Arrays.asList(info.requestedPermissions)));
        assertNull(ScrollService.current());
        getInstrumentation().runOnMainSync(() -> assertNull(button(activity.getWindow().getDecorView(), "他アプリで使う")));
        click("詳細設定"); click("他アプリで使う");
        assertNull("Navigation must not start/enable accessibility", ScrollService.current());
        click("実験を停止"); assertNull(ScrollService.current());
    }
    @Test public void testProfileStartsWithoutInventedButtonCodes() {
        DeviceProfile profile = new DeviceProfile(activity);
        assertFalse(profile.selected());
        for (String action : DeviceProfile.ACTIONS) assertEquals("未登録", profile.mapping(action));
    }
    private KeyEvent fixtureKey(int code, int scan, int meta) {
        return new KeyEvent(100, 110, KeyEvent.ACTION_DOWN, code, 0, meta, 41, scan, 0, InputDevice.SOURCE_KEYBOARD);
    }
    @Test public void testBindingConflictPreservesExistingAndUnsupportedInputDoesNotBind() {
        DeviceProfile profile = new DeviceProfile(activity); profile.clearMappings();
        KeyEvent observed = fixtureKey(KeyEvent.KEYCODE_ENTER, 28, 0); // Synthetic Android fixture, not an AU05 code.
        assertEquals(DeviceProfile.LearnStatus.STORED, profile.learn("dictation", observed).status());
        String before = profile.mapping("dictation");
        DeviceProfile.LearnResult collision = profile.learn("click", observed);
        assertEquals(DeviceProfile.LearnStatus.CONFLICT, collision.status()); assertEquals("dictation", collision.conflict());
        assertEquals(before, profile.mapping("dictation")); assertEquals("未登録", profile.mapping("click"));
        assertEquals(DeviceProfile.LearnStatus.STORED, profile.learn("dictation", observed).status());
        assertEquals(DeviceProfile.LearnStatus.UNSUPPORTED, profile.learn("back", fixtureKey(KeyEvent.KEYCODE_UNKNOWN, 0, 0)).status());
        assertEquals(DeviceProfile.LearnStatus.UNSUPPORTED, profile.learn("send", observed).status());
        profile.clearMapping("dictation"); assertFalse(profile.hasActiveBindings());
        assertEquals(DeviceProfile.LearnStatus.STORED, profile.learn("click", observed).status()); assertTrue(profile.hasActiveBindings());
    }
    @Test public void testChordIgnoresLockKeysButPreservesObservedModifierSides() {
        String left = DeviceProfile.signature(fixtureKey(KeyEvent.KEYCODE_H, 35, KeyEvent.META_CTRL_LEFT_ON));
        assertEquals(left, DeviceProfile.signature(fixtureKey(KeyEvent.KEYCODE_H, 35,
                KeyEvent.META_CTRL_LEFT_ON | KeyEvent.META_CAPS_LOCK_ON | KeyEvent.META_NUM_LOCK_ON | KeyEvent.META_SCROLL_LOCK_ON)));
        assertNotEquals(left, DeviceProfile.signature(fixtureKey(KeyEvent.KEYCODE_H, 35, KeyEvent.META_CTRL_RIGHT_ON)));
    }
    @Test public void testButtonInputCaptureIsAnUncheckedSessionOption() {
        click("詳細設定"); click("他アプリで使う");
        getInstrumentation().runOnMainSync(() -> {
            Button option = button(activity.getWindow().getDecorView(), "登録済みボタンも使う");
            assertTrue(option instanceof android.widget.CheckBox); assertFalse(((android.widget.CheckBox) option).isChecked());
        });
        assertNull(ScrollService.current());
    }
    @Test public void testFrontPanelChangesLayerAndUsesThatLayersWheelAction() {
        var settings = activity.getSharedPreferences("control_panel", android.content.Context.MODE_PRIVATE);
        String key = ControlLayers.preferenceKey(ControlLayers.Layer.PRIMARY, ControlLayers.Control.K1);
        String alternateWheel = ControlLayers.preferenceKey(ControlLayers.Layer.ALTERNATE, ControlLayers.Control.ROTATE);
        String original = settings.getString(key, null);
        String originalWheel = settings.getString(alternateWheel, null);
        settings.edit().putString(key, "LAYER").putString(alternateWheel, "DIRECT").apply();
        try {
            click("本体の割当とレイヤー"); click("K1"); click("動作を試す");
            click("ノブ回転を選ぶ");
            getInstrumentation().runOnMainSync(() -> {
                View root = activity.getWindow().getDecorView();
                assertNotNull(button(root, "補間なしでスクロール"));
            });
            click("回転を試す");
            getInstrumentation().runOnMainSync(() -> assertTrue(nestedScroll(activity.getWindow().getDecorView()).getScrollY() > 0));
            assertNull(ScrollService.current());
        } finally {
            settings.edit().putString(key, original).putString(alternateWheel, originalWheel).apply();
        }
    }
    @Test public void testLegacyDeviceIdentifiersAreRemovedFromPreferences() {
        android.content.SharedPreferences preferences = activity.getSharedPreferences("profile", android.content.Context.MODE_PRIVATE);
        preferences.edit().putString("descriptor", "synthetic-test-identifier").putInt("vid", 123).putInt("pid", 456).commit();
        new DeviceProfile(activity);
        assertFalse(preferences.contains("descriptor")); assertFalse(preferences.contains("vid")); assertFalse(preferences.contains("pid"));
    }
    @Test public void testUnavailableServiceReleaseNeverClaimsSuccessOrEnablesService() {
        getInstrumentation().runOnMainSync(() -> {
            ScrollService unbound = new ScrollService(); unbound.onInterrupt();
            assertTrue(unbound.status.contains("解除は未確認")); assertNull(ScrollService.current());
        });
    }
    @Test public void testDictationEntryIsPrivateAndRequiresAnExplicitStart() throws Exception {
        android.content.pm.ActivityInfo entry = activity.getPackageManager().getActivityInfo(new android.content.ComponentName(activity, DictationActivity.class), 0);
        assertFalse(entry.exported);
        android.app.Instrumentation.ActivityMonitor monitor = getInstrumentation().addMonitor(new android.content.IntentFilter(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH), null, true);
        Activity dictation = null;
        try {
            // An explicit MAIN action keeps a speech IntentFilter from matching a null action.
            dictation = getInstrumentation().startActivitySync(new Intent(activity, DictationActivity.class)
                    .setAction(Intent.ACTION_MAIN).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            getInstrumentation().waitForIdleSync(); assertEquals(0, monitor.getHits());
        } finally {
            getInstrumentation().removeMonitor(monitor);
            if (dictation != null) { Activity opened = dictation; getInstrumentation().runOnMainSync(opened::finish); }
            getInstrumentation().waitForIdleSync();
        }
    }
}
