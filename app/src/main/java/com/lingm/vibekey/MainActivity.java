package com.lingm.vibekey;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Choreographer;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.hardware.input.InputManager;
import android.widget.*;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

public final class MainActivity extends Activity implements InputManager.InputDeviceListener {
    private final Diagnostics diagnostics = new Diagnostics();
    private final ScrollEngine engine = new ScrollEngine(1600, 65);
    private final KeyPressTracker learningKeys = new KeyPressTracker();
    private ControlLayers controls;
    private final ButtonSequence panelKeys = new ButtonSequence();
    private SharedPreferences panelSettings;
    private ControlLayers.Layer layer = ControlLayers.Layer.PRIMARY;
    private ControlLayers.Control selectedControl = ControlLayers.Control.ROTATE, learningControl;
    private int panelGeneration;
    private boolean boardVisible, connectionVisible;
    private Runnable refreshPanel;
    private TextView panelFeedback;
    private AppUpdates updates;
    private Button updateButton;
    private final Runnable updateListener = this::renderUpdateButton;
    private DeviceProfile profile;
    private LinearLayout page;
    private TextView status;
    private ScrollView preview;
    private boolean previewVisible, framePending, smoothing;
    private long lastFrame;
    private long lastWheelAt = -1, lastButtonAt = -1;
    private int visibleWheelSelection = -1, visibleButtonSelection = -1;
    private String topologyNote = "変更通知なし";
    private long learningExpires;
    private double fractional;
    private String learning, pendingExport;
    private static final int EXPORT = 10;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        profile = new DeviceProfile(this);
        controls = profile.panelInputs();
        updateSelectionSnapshot();
        panelSettings = getSharedPreferences("control_panel", MODE_PRIVATE);
        updates = AppUpdates.get(this);
        home();
        getSystemService(InputManager.class).registerInputDeviceListener(this, new android.os.Handler(getMainLooper()));
        getWindow().getDecorView().setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom()); return insets;
        });
    }
    @Override protected void onStart() {
        super.onStart();
        updates.observe(updateListener);
    }

    @Override protected void onResume() {
        super.onResume();
        updates.check(false);
    }

    @Override protected void onStop() {
        updates.remove(updateListener);
        super.onStop();
    }

    private void renderUpdateButton() {
        if (updateButton == null) return;
        String version = updates.availableVersion();
        updateButton.setText(version == null ? "アプリの更新" : "アプリの更新 • " + version);
    }

    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private void screen(String title) {
        previewVisible = false; engine.clear(); fractional = 0; learning = null; status = null;
        boardVisible = false; connectionVisible = false; learningControl = null;
        refreshPanel = null; panelFeedback = null; updateButton = null; panelGeneration++;
        page = new LinearLayout(this); page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(20), dp(16), dp(20), dp(24)); page.setBackgroundColor(0xfff5f7fa);
        ScrollView outer = new ScrollView(this); outer.setFillViewport(true); outer.addView(page); setContentView(outer);
        text(title, 27); text("AU05 開発版 " + BuildConfig.VERSION_NAME + " · 実機の動作は未確認", 13);
        if (!title.equals("VibeKey Lab")) button("← ホーム", this::home);
    }
    private TextView text(String value, int size) {
        TextView view = new TextView(this); view.setText(value); view.setTextSize(size);
        view.setTextColor(0xff152132); view.setPadding(0, dp(8), 0, dp(8)); page.addView(view); return view;
    }
    private Button button(String label, Runnable action) {
        Button button = new Button(this); button.setText(label); button.setAllCaps(false);
        page.addView(button, new LinearLayout.LayoutParams(-1, -2)); button.setOnClickListener(v -> action.run());
        return button;
    }
    private void message(String value) { Toast.makeText(this, value, Toast.LENGTH_LONG).show(); }
    private void home() {
        diagnostics.stop(); screen("VibeKey Lab");
        text("はじめはこのアプリ内だけで試します。Accessibility は有効にせず、ドングルを接続して入力元を選んでください。", 16);
        button("本体の割当とレイヤー", this::boardScreen);
        button("1  AU05 の回転入力元を選ぶ", this::chooseDevice);
        button("2  スクロールを比較", this::scrollScreen);
        button("3  回転だけを診断・保存", this::diagnosticScreen);
        button("接続状態を確認", this::connectionScreen);
        button("詳細設定（任意）", this::advancedScreen);
        updateButton = button("アプリの更新", () -> startActivity(new Intent(this, UpdateActivity.class)));
        renderUpdateButton();
        text("診断ファイルは縦回転量と相対時間のみ。キー入力・識別子・画面内容は含めません。自動的な権限要求や他アプリの操作は行いません。", 14);
    }
    private void connectionScreen() {
        diagnostics.stop(); screen("接続状態"); connectionVisible = true;
        text("ドングル・入力元・最後の入力を確認できます。入力が止まっても、USB 機器が残っていれば無線待機などの可能性があります。", 15);
        button("回転入力元を選ぶ", this::chooseDevice);
        button("ボタン入力元を選ぶ", this::chooseButtonDevice);
        TextView summary = text("", 16);
        Runnable refresh = new Runnable() {
            @Override public void run() {
                if (!summary.isAttachedToWindow()) return;
                summary.postDelayed(this, 1_000);
                if (!hasWindowFocus()) return;
                var usb = getSystemService(android.hardware.usb.UsbManager.class);
                var audio = getSystemService(android.media.AudioManager.class);
                int usbInputs = 0;
                if (audio != null) for (var device : audio.getDevices(android.media.AudioManager.GET_DEVICES_INPUTS)) {
                    int type = device.getType();
                    if (type == android.media.AudioDeviceInfo.TYPE_USB_DEVICE || type == android.media.AudioDeviceInfo.TYPE_USB_HEADSET
                            || type == android.media.AudioDeviceInfo.TYPE_USB_ACCESSORY) usbInputs++;
                }
                ScrollService service = ScrollService.current();
                summary.setText("Android が列挙した USB 機器  " + (usb == null ? "不明" : usb.getDeviceList().size() + " 台")
                        + "\n利用可能な USB 音声入力  " + (audio == null ? "不明" : usbInputs + " 件")
                        + "\n回転入力元  " + (profile.selected() ? "選択・認識中" : "未選択／再選択が必要")
                        + "\nボタン入力元  " + (profile.buttonsSelected() ? "選択・認識中" : "未選択／再選択が必要")
                        + "\n最後の回転  " + elapsedInput(lastWheelAt) + "\n最後のキー  " + elapsedInput(lastButtonAt)
                        + "\n入力元の通知  " + topologyNote
                        + "\n他アプリ実験  " + (service == null ? "サービス未接続" : service.status));
            }
        };
        summary.post(refresh);
        text("本体が消灯してドングルが点滅したら、キーを一度押し、ランプが安定してからノブを回してください。USB と音声の件数は端末全体の情報です。無線リンクや、録音アプリが実際に使うマイクは判定できません。", 14);
    }
    private String elapsedInput(long timestamp) {
        if (timestamp < 0) return "アプリ内で未観測";
        return Math.max(0, (android.os.SystemClock.elapsedRealtime() - timestamp) / 1000) + " 秒前";
    }
    private void advancedScreen() {
        diagnostics.stop(); screen("詳細設定");
        text("初回の比較には不要です。他アプリの補正は未検証で、明示開始と手動の Accessibility 許可が必要です。", 15);
        button("ボタンを実入力から登録", this::mappingScreen);
        button("他アプリで使う（未検証の実験）", this::externalScreen);
        button("音声入力を開く（外部アプリ）", this::dictate);
    }
    private void diagnosticScreen() {
        screen("入力診断");
        text("選択した入力元の縦回転量と開始からの時間だけを最大 2,000 件記録します。キー入力・端末識別子・画面内容は保存しません。", 15);
        button("接続端末から AU05 を選ぶ", this::chooseDevice);
        button("回転の記録を開始／停止", () -> {
            if (diagnostics.isRecording()) diagnostics.stop();
            else {
                InputDevice selected = selectedMouse();
                if (selected == null) { message("先に AU05 の回転入力元を選択してください"); return; }
                diagnostics.start(selected.getId(), android.os.SystemClock.uptimeMillis());
            }
            updateStatus();
        });
        status = text("", 16); updateStatus();
        text("上下に低速／高速で回して保存してください。ボタンのコードや入力文字は記録しません。アプリを離れると記録は停止します。", 14);
        button("診断を JSONL で保存", this::exportCapture);
        button("記録を消去", () -> { diagnostics.clear(); updateStatus(); });
        text(deviceSummary(), 12);
    }
    private void updateStatus() {
        if (status != null) status.setText((diagnostics.isRecording() ? "● 記録中" : "記録停止")
                + " · " + diagnostics.count() + " 件\n" + diagnostics.last);
    }
    private void chooseDevice() { chooseDevice(false); }
    private void chooseButtonDevice() { chooseDevice(true); }
    private void chooseDevice(boolean buttons) {
        ArrayList<InputDevice> devices = new ArrayList<>();
        for (int id : InputDevice.getDeviceIds()) {
            InputDevice d = InputDevice.getDevice(id);
            if (d != null && !d.isVirtual() && d.isExternal() && d.supportsSource(buttons ? InputDevice.SOURCE_KEYBOARD : InputDevice.SOURCE_MOUSE)) devices.add(d);
        }
        if (devices.isEmpty()) { message("外付け入力端末がありません。USB 接続を確認してください"); return; }
        String[] labels = new String[devices.size()];
        for (int i = 0; i < labels.length; i++) labels[i] = devices.get(i).getName();
        new AlertDialog.Builder(this).setTitle(buttons ? "AU05 のボタン入力元を選択" : "AU05 の回転入力元を選択")
                .setItems(labels, (dialog, which) -> selectDevice(devices.get(which), buttons)).setNegativeButton("キャンセル", null).show();
    }
    private void selectDevice(InputDevice device, boolean buttons) {
        new AlertDialog.Builder(this).setTitle(device.getName()).setMessage("この入力元を" + (buttons ? "ボタン" : "回転") + "用に、今回の接続中だけ使用します。切断や構成変更後は再選択してください。" + (buttons ? "既存のボタン割当は消去されます。" : "") + "端末の識別情報は保存しません。")
                .setPositiveButton("選択", (dialog, which) -> {
                    if (ScrollService.current() != null) ScrollService.current().stopSession("端末変更");
                    diagnostics.stop(); engine.clear(); learning = null; learningKeys.clear();
                    boolean selected = buttons ? profile.selectButtons(device) : profile.select(device);
                    if (!selected) { message("接続状態が変わりました。入力元を選び直してください"); return; }
                    updateSelectionSnapshot();
                    if (!buttons) { diagnostics.clear(); lastWheelAt = -1; }
                    message("入力元を選択しました");
                    if (buttons) {
                        panelKeys.clear(); lastButtonAt = -1;
                    }
                    if (boardVisible && refreshPanel != null) refreshPanel.run();
                    else if (buttons && !connectionVisible) mappingScreen();
                }).setNegativeButton("キャンセル", null).show();
    }
    private InputDevice selectedMouse() {
        for (int id : InputDevice.getDeviceIds()) {
            InputDevice device = InputDevice.getDevice(id);
            if (profile.matches(device) && device.supportsSource(InputDevice.SOURCE_MOUSE)) return device;
        }
        return null;
    }
    private String deviceSummary() {
        StringBuilder value = new StringBuilder("この画面の端末名は保存・出力しません。\n");
        for (int id : InputDevice.getDeviceIds()) {
            InputDevice d = InputDevice.getDevice(id);
            if (profile.matches(d)) value.append("選択中: ").append(d.getName()).append('\n');
        }
        return value.toString();
    }
    private void exportCapture() {
        diagnostics.stop(); updateStatus();
        try {
            pendingExport = diagnostics.export();
            startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/x-ndjson")
                    .addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE, "vibekey-wheel-" + BuildConfig.VERSION_NAME + ".jsonl"), EXPORT);
        } catch (Exception error) { message("保存準備に失敗: " + error.getMessage()); }
    }
    private void scrollScreen() {
        diagnostics.stop(); screen("スクロール Lab");
        text("この画面内だけで、同じ距離の「補間なし／あり」を比較します。OS 標準の動作そのものとの比較ではありません。タッチすると残りの移動を止めます。", 14);
        RadioGroup modes = new RadioGroup(this);
        RadioButton direct = new RadioButton(this); direct.setId(android.view.View.generateViewId()); direct.setText("補間なし（同じ距離）");
        RadioButton smooth = new RadioButton(this); smooth.setId(android.view.View.generateViewId()); smooth.setText("補間あり（65 ms）");
        modes.addView(direct); modes.addView(smooth); page.addView(modes); modes.check(smoothing ? smooth.getId() : direct.getId());
        modes.setOnCheckedChangeListener((group, checked) -> { engine.clear(); fractional = 0; smoothing = checked == smooth.getId(); });
        text("1 ステップの距離: " + profile.distanceDp() + " dp", 15);
        SeekBar distance = new SeekBar(this); distance.setMax(184); distance.setProgress(profile.distanceDp() - 16); page.addView(distance);
        distance.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar bar, int progress, boolean user) { if (user) profile.distanceDp(progress + 16); }
            public void onStartTrackingTouch(SeekBar bar) { engine.clear(); }
            public void onStopTrackingTouch(SeekBar bar) { message("距離 " + profile.distanceDp() + " dp"); }
        });
        button("合成テスト ↓ 3 ステップ", () -> move(dp(profile.distanceDp()) * 3.0));
        button("合成テスト ↑ 3 ステップ", () -> move(-dp(profile.distanceDp()) * 3.0));
        appendPreview(360);
    }
    private void appendPreview(int heightDp) {
        preview = new ScrollView(this); preview.setBackgroundColor(0xffffffff);
        LinearLayout rows = new LinearLayout(this); rows.setOrientation(LinearLayout.VERTICAL);
        for (int i = 1; i <= 60; i++) {
            TextView row = new TextView(this); row.setText(i + "   Scroll feels better in motion\n縦スクロールの連続性と方向を確認");
            row.setTextSize(17); row.setTextColor(0xff192d41); row.setPadding(dp(16), dp(22), dp(16), dp(22)); rows.addView(row);
        }
        preview.addView(rows); page.addView(preview, new LinearLayout.LayoutParams(-1, dp(heightDp))); previewVisible = true;
    }
    private void move(double pixels) { move(pixels, smoothing); }
    private void move(double pixels, boolean interpolate) {
        if (!Double.isFinite(pixels) || preview == null) return;
        if (!interpolate) {
            fractional += Math.max(-1600, Math.min(1600, pixels)); int amount = (int) fractional;
            fractional -= amount; preview.scrollBy(0, amount); return;
        }
        engine.add(pixels); if (!framePending) { lastFrame = 0; scheduleFrame(); }
    }
    private void scheduleFrame() { framePending = true; Choreographer.getInstance().postFrameCallback(this::frame); }
    private void frame(long time) {
        framePending = false; if (!previewVisible || preview == null) { engine.clear(); return; }
        double elapsed = lastFrame == 0 ? 1000.0 / 60 : (time - lastFrame) / 1e6; lastFrame = time;
        fractional += engine.step(elapsed); int pixels = (int) fractional; fractional -= pixels;
        int before = preview.getScrollY(); preview.scrollBy(0, pixels);
        if (pixels != 0 && preview.getScrollY() == before) { engine.clear(); fractional = 0; }
        if (!engine.isIdle()) scheduleFrame();
    }
    private void boardScreen() {
        diagnostics.stop(); screen("AU05 の割当"); boardVisible = true;
        text("正面図をタップして動作を割り当てます。レイヤーはこの画面で使えます。", 15);

        RadioGroup layerOptions = new RadioGroup(this);
        layerOptions.setOrientation(RadioGroup.HORIZONTAL);
        RadioButton primary = layerButton(layerOptions, ControlLayers.Layer.PRIMARY);
        RadioButton alternate = layerButton(layerOptions, ControlLayers.Layer.ALTERNATE);
        page.addView(layerOptions);

        LinearLayout layout = new LinearLayout(this);
        layout.setGravity(android.view.Gravity.TOP);
        page.addView(layout, new LinearLayout.LayoutParams(-1, -2));
        FrontPanel front = new FrontPanel(this, this::selectControl);
        layout.addView(front, new LinearLayout.LayoutParams(dp(104), -2));

        LinearLayout controlsColumn = new LinearLayout(this);
        controlsColumn.setOrientation(LinearLayout.VERTICAL);
        controlsColumn.setPadding(dp(12), 0, 0, 0);
        layout.addView(controlsColumn, new LinearLayout.LayoutParams(0, -2, 1));
        Button rotate = panelButton(controlsColumn, "ノブ回転を選ぶ", () -> selectControl(ControlLayers.Control.ROTATE));
        rotate.setContentDescription("ノブ回転を選ぶ");
        TextView controlTitle = panelText(controlsColumn, "", 19);
        Button assignment = panelButton(controlsColumn, "割当を変更", this::choosePanelAction);
        Button learn = panelButton(controlsColumn, "実機ボタンを登録", this::editPanelBinding);
        Button test = panelButton(controlsColumn, "動作を試す", this::simulatePanelControl);
        TextView signal = panelText(controlsColumn, "", 13);
        panelButton(controlsColumn, "入力元を選ぶ", () -> {
            if (selectedControl == ControlLayers.Control.ROTATE) chooseDevice();
            else chooseButtonDevice();
        });
        panelButton(controlsColumn, "側面の電源ボタン", () -> new AlertDialog.Builder(this)
                .setTitle("側面の電源ボタン")
                .setMessage("本体の起動・停止に使うボタンです。Android に割当可能な入力が届くか未確認のため、割当は保留しています。")
                .setPositiveButton("閉じる", null).show());

        panelFeedback = text("回転やキーの反応は下のプレビューで確認できます。", 14);
        appendPreview(220);
        refreshPanel = () -> {
            layerOptions.check(layer == ControlLayers.Layer.PRIMARY ? primary.getId() : alternate.getId());
            front.highlight(selectedControl);
            controlTitle.setText(selectedControl.label);
            assignment.setText("割当 · " + panelAction(selectedControl).label);
            boolean isWheel = selectedControl == ControlLayers.Control.ROTATE;
            learn.setVisibility(isWheel ? android.view.View.GONE : android.view.View.VISIBLE);
            learn.setText(controls.learned(selectedControl) ? "実機ボタンの登録を編集" : "実機ボタンを登録");
            test.setText(isWheel ? "回転を試す ↓" : "動作を試す");
            signal.setText(isWheel ? (profile.selected() ? "回転入力元 選択済み" : "回転入力元 未選択")
                    : (controls.learned(selectedControl) ? "実機入力 登録済み" : "実機入力 未登録"));
        };
        refreshPanel.run();
        layerOptions.setOnCheckedChangeListener((group, id) -> switchPanelLayer(
                id == primary.getId() ? ControlLayers.Layer.PRIMARY : ControlLayers.Layer.ALTERNATE));
    }

    private RadioButton layerButton(RadioGroup group, ControlLayers.Layer option) {
        RadioButton radio = new RadioButton(this);
        radio.setId(android.view.View.generateViewId()); radio.setText(option.label);
        group.addView(radio, new RadioGroup.LayoutParams(0, dp(48), 1));
        return radio;
    }
    private TextView panelText(LinearLayout parent, String label, int size) {
        TextView view = new TextView(this);
        view.setText(label); view.setTextSize(size); view.setTextColor(0xff152132);
        parent.addView(view, new LinearLayout.LayoutParams(-1, -2));
        return view;
    }
    private Button panelButton(LinearLayout parent, String label, Runnable action) {
        Button button = new Button(this);
        button.setText(label); button.setAllCaps(false); button.setMinHeight(dp(48));
        button.setOnClickListener(v -> action.run());
        parent.addView(button, new LinearLayout.LayoutParams(-1, -2));
        return button;
    }
    private void selectControl(ControlLayers.Control control) {
        selectedControl = control; learningControl = null; refreshPanel.run();
        panelFeedback.setText(control.label + " を選択しました");
    }
    private ControlLayers.Action panelAction(ControlLayers.Control control) {
        return ControlLayers.restore(control, panelSettings.getString(ControlLayers.preferenceKey(layer, control), ""));
    }
    private void choosePanelAction() {
        learningControl = null;
        ControlLayers.Control control = selectedControl;
        ControlLayers.Layer editedLayer = layer;
        var options = ControlLayers.options(control);
        String[] labels = options.stream().map(action -> action.label).toArray(String[]::new);
        new AlertDialog.Builder(this).setTitle(editedLayer.label + " · " + control.label)
                .setSingleChoiceItems(labels, options.indexOf(panelAction(control)), (dialog, which) -> {
                    panelSettings.edit().putString(ControlLayers.preferenceKey(editedLayer, control), options.get(which).name()).apply();
                    resetPanelMotion(); dialog.dismiss(); refreshPanel.run();
                    panelFeedback.setText("この画面用の割当を保存しました");
                }).setNegativeButton("キャンセル", null).show();
    }
    private void switchPanelLayer(ControlLayers.Layer next) {
        if (next == layer) return;
        layer = next; learningControl = null; resetPanelMotion();
        if (refreshPanel != null) refreshPanel.run();
        if (panelFeedback != null) panelFeedback.setText(next.label + " に切り替えました");
    }
    private void resetPanelMotion() { panelGeneration++; engine.clear(); fractional = 0; }
    private void editPanelBinding() {
        if (!controls.learned(selectedControl)) { armPanelLearning(); return; }
        ControlLayers.Control control = selectedControl;
        new AlertDialog.Builder(this).setTitle(control.label)
                .setItems(new String[] {"登録し直す", "このキーの登録を解除"}, (dialog, choice) -> {
                    if (choice == 0) { armPanelLearning(); return; }
                    learningControl = null; controls.forget(control); resetPanelMotion();
                    refreshPanel.run(); panelFeedback.setText(control.label + " の紐付けを解除しました");
                }).setNegativeButton("キャンセル", null).show();
    }
    private void armPanelLearning() {
        if (!profile.buttonsSelected()) { message("先に実機のボタン入力元を選んでください"); chooseButtonDevice(); return; }
        learningControl = selectedControl;
        learningExpires = android.os.SystemClock.uptimeMillis() + 10_000;
        panelFeedback.setText("10秒以内に「" + selectedControl.label + "」を一度押してください");
        final long deadline = learningExpires;
        panelFeedback.postDelayed(() -> {
            if (boardVisible && learningControl != null && learningExpires == deadline) {
                learningControl = null;
                panelFeedback.setText("入力が届きませんでした。入力元や Android のショートカットを確認してください");
            }
        }, 10_000);
    }
    private void simulatePanelControl() {
        learningControl = null;
        if (selectedControl == ControlLayers.Control.ROTATE) {
            applyPanelWheel(-3);
            panelFeedback.setText("テスト回転 · " + panelAction(selectedControl).label);
        } else performPanelAction(panelAction(selectedControl), "画面テスト");
    }
    private void applyPanelWheel(float axis) {
        ControlLayers.Action action = panelAction(ControlLayers.Control.ROTATE);
        if (action == ControlLayers.Action.NONE) return;
        double pixels = -axis * (double) dp(profile.distanceDp());
        if (action == ControlLayers.Action.REVERSE) pixels = -pixels;
        move(pixels, action != ControlLayers.Action.DIRECT);
    }
    private void performPanelAction(ControlLayers.Action action, String source) {
        if (panelFeedback != null) panelFeedback.setText(source + " · " + action.label);
        switch (action) {
            case LAYER -> switchPanelLayer(layer.other());
            case VOICE -> dictate();
            case UP -> move(-dp(profile.distanceDp()), true);
            case DOWN -> move(dp(profile.distanceDp()), true);
            default -> { }
        }
    }
    private void mappingScreen() {
        diagnostics.stop(); screen("ボタン登録");
        text("回転とは別に、ボタンの入力元を明示選択します。同じ機器の別入力元でも自動的には許可しません。既定コードの仮定はありません。", 14);
        button("ボタンの入力元を選ぶ", this::chooseButtonDevice);
        InputDevice buttons = profile.buttonDevice();
        text(buttons == null ? "ボタン入力元: 未選択" : "ボタン入力元: " + buttons.getName() + "（画面内表示のみ）", 14);
        String[] labels = { "マイク → このアプリの音声入力画面", "OK → 編集欄以外のフォーカスをクリック", "送信 → この版では無効", "キャンセル → Android の戻る" };
        for (int i = 0; i < labels.length; i++) {
            String action = DeviceProfile.ACTIONS[i];
            button(labels[i] + "\n" + (profile.mapping(action).equals("未登録") ? "未登録" : "登録済み（端末内のみ）"), () -> {
                if (action.equals("send")) { message("編集欄では実験を停止するため、この版の送信操作は使えません"); return; }
                if (!profile.buttonsSelected()) { message("先にボタンの入力元を選択してください"); return; }
                learning = action; learningExpires = android.os.SystemClock.uptimeMillis() + 10_000;
                message("10 秒以内に物理ボタンを 1 回押してください。画面移動で中止します。");
            });
            if (!action.equals("send")) button(actionLabel(action) + " の割当を解除", () -> { profile.clearMapping(action); mappingScreen(); });
        }
        button("全割当を消去", () -> { profile.clearMappings(); mappingScreen(); });
        text("キーを読むのは登録中と、登録済みボタンを明示的に有効にした実験中です。ボタン操作はキーを離した時に一度だけ実行します。コードは端末内設定に保持し、診断には含めません。編集欄・パスワード欄・不明なフォーカスでは実験を終了します。", 14);
    }
    private String actionLabel(String action) {
        switch (action) { case "dictation": return "マイク"; case "click": return "OK"; case "back": return "キャンセル"; default: return "送信"; }
    }
    private void externalScreen() {
        diagnostics.stop(); screen("他アプリで使う · 実験");
        text("Android 14+ の未検証機能です。他の Accessibility サービス、複数画面、編集欄、フォーカス不明では動かしません。パスワードや金融・認証アプリでは試さないでください。送信機能は無効です。", 14);
        text("対象アプリのパッケージ名（例: com.android.chrome）", 14);
        EditText target = new EditText(this); target.setSingleLine(true); target.setText(profile.target()); page.addView(target);
        CheckBox useButtons = new CheckBox(this); useButtons.setText("登録済みボタンも使う（今回だけ・既定 OFF）"); useButtons.setChecked(false); page.addView(useButtons);
        text("チェックしないスクロール実験ではキー捕捉を要求しません。有効時は他のキーボード入力もサービスの判定を経由するため、遅延ゼロは保証できません。選択外の入力は追加操作せず通します。停止時にボタンを押したままなら最大 5 秒解放を待ち、未確認ならこのサービスの停止を要求します。", 14);
        button("Accessibility の説明と設定", () -> new AlertDialog.Builder(this).setTitle("権限の説明")
                .setMessage("他アプリの前面パッケージ・スクロール領域・入力フォーカスを読み、登録キーと回転を操作に変換します。画面本文や入力内容を保存・送信しません。許可だけでは捕捉を開始しません。設定画面で利用者自身が有効にしてください。")
                .setPositiveButton("設定を開く", (d, w) -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)))
                .setNegativeButton("キャンセル", null).show());
        button("10 分間の実験を開始", () -> {
            profile.target(target.getText().toString());
            new AlertDialog.Builder(this).setTitle("マウス入力を実験モードに切り替え")
                    .setMessage("Android はマウス source 全体を捕捉するため、クリック・移動も影響します。他サービスが後から有効になる場合などは元のホイールと追加スワイプが重なる可能性があります。対象外への移動や入力欄の検出でセッションを終了しますが、画面切替直後の短い入力抑止まで完全には防げません。補正とタッチの同時操作は競合します。STOP 後も送出済みの最大 120 ms は完了し得ます。実機は未確認です。初回はアプリ内の比較だけを推奨します。")
                    .setPositiveButton("開始", (d, w) -> {
                        if (ScrollService.current() == null) { message("サービスを先に手動で許可してください"); return; }
                        message(ScrollService.current().startSession(useButtons.isChecked()));
                    }).setNegativeButton("キャンセル", null).show();
        });
        button("実験を停止", () -> { if (ScrollService.current() != null) ScrollService.current().stopSession("利用者が停止"); message("停止しました"); });
        text(ScrollService.current() == null ? "サービス: 未接続" : "サービス: 接続済み · " + ScrollService.current().status, 14);
    }
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (profile.matchesButtons(event.getDevice())) {
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0)
                lastButtonAt = android.os.SystemClock.elapsedRealtime();
            if (handlePanelKey(event)) return true;
        } else if (!panelKeys.isEmpty()) panelKeys.clearDevice(event.getDeviceId());
        if (learning == null && learningKeys.isEmpty()) return super.dispatchKeyEvent(event);
        int deviceId = event.getDeviceId(), scan = event.getScanCode(), key = event.getKeyCode();
        if (!learningKeys.isEmpty() && !profile.matchesButtons(event.getDevice())) learningKeys.clearDevice(deviceId);
        if (event.getAction() == KeyEvent.ACTION_UP && learningKeys.up(deviceId, scan, key)) return true;
        if (event.getAction() == KeyEvent.ACTION_DOWN && learningKeys.consumeRepeatedDown(deviceId, scan, key, event.getRepeatCount())) return true;
        if (learning != null && android.os.SystemClock.uptimeMillis() > learningExpires) learning = null;
        if (learning != null) {
            InputDevice device = event.getDevice();
            if (profile.matchesButtons(device) && event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0 && !KeyEvent.isModifierKey(event.getKeyCode())) {
                learningKeys.down(deviceId, scan, key);
                DeviceProfile.LearnResult result = profile.learn(learning, event); learning = null; mappingScreen();
                if (result.status() == DeviceProfile.LearnStatus.STORED) message("観測したキーを登録しました");
                else if (result.status() == DeviceProfile.LearnStatus.CONFLICT) message("このボタンは「" + actionLabel(result.conflict()) + "」に登録済みです。既存の割当を保持しました");
                else message("この入力ではボタンを識別できません。割当は変更しませんでした");
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }
    private boolean handlePanelKey(KeyEvent event) {
        if (!boardVisible && panelKeys.isEmpty()) return false;
        int device = event.getDeviceId(), scan = event.getScanCode(), key = event.getKeyCode();
        if (event.getAction() == KeyEvent.ACTION_UP) {
            ButtonSequence.Release release = panelKeys.up(device, scan, key, panelGeneration, event.isCanceled());
            if (boardVisible && !release.action().isEmpty()) performPanelAction(ControlLayers.Action.valueOf(release.action()), "実機キー");
            return release.owned();
        }
        if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
        if (panelKeys.repeatedDown(device, scan, key, event.getRepeatCount())) return true;
        if (!boardVisible || event.getRepeatCount() != 0 || !canLearnPanelKey(event)) return false;
        String signature = DeviceProfile.signature(event);
        if (learningControl != null && android.os.SystemClock.uptimeMillis() <= learningExpires) {
            ControlLayers.Control requested = learningControl;
            learningControl = null;
            panelKeys.down(device, scan, key, "", panelGeneration);
            if (controls.learn(requested, signature)) {
                panelFeedback.setText(requested.label + " に " + KeyEvent.keyCodeToString(key) + " を登録しました");
            } else {
                ControlLayers.Control previous = controls.lookup(signature);
                panelFeedback.setText(previous == null ? "この入力は登録できません" : "同じ入力は「" + previous.label + "」に登録済みです");
            }
            refreshPanel.run();
            return true;
        }
        ControlLayers.Control control = controls.lookup(signature);
        if (control == null) return false;
        panelKeys.down(device, scan, key, panelAction(control).name(), panelGeneration);
        return true;
    }
    private boolean canLearnPanelKey(KeyEvent event) {
        int key = event.getKeyCode();
        if (KeyEvent.isModifierKey(key) || (key == KeyEvent.KEYCODE_UNKNOWN && event.getScanCode() == 0)) return false;
        return switch (key) {
            case KeyEvent.KEYCODE_HOME, KeyEvent.KEYCODE_POWER, KeyEvent.KEYCODE_SLEEP, KeyEvent.KEYCODE_WAKEUP -> false;
            case KeyEvent.KEYCODE_H -> !event.isMetaPressed();
            default -> true;
        };
    }
    @Override public boolean dispatchGenericMotionEvent(MotionEvent event) {
        InputDevice device = event.getDevice();
        if (device != null && device.isExternal() && !device.isVirtual()) {
            if (profile.matches(device)) {
                if (event.getActionMasked() == MotionEvent.ACTION_SCROLL && event.isFromSource(InputDevice.SOURCE_MOUSE)
                        && event.getAxisValue(MotionEvent.AXIS_VSCROLL) != 0) lastWheelAt = android.os.SystemClock.elapsedRealtime();
                diagnostics.motion(event); updateStatus();
            }
            if (previewVisible && profile.matches(device) && event.isFromSource(InputDevice.SOURCE_MOUSE)
                    && event.getActionMasked() == MotionEvent.ACTION_SCROLL) {
                float axis = event.getAxisValue(MotionEvent.AXIS_VSCROLL);
                if (Float.isFinite(axis) && axis != 0) {
                    if (boardVisible) applyPanelWheel(axis);
                    else move(-axis * dp(profile.distanceDp()));
                    return true;
                }
            }
        }
        return super.dispatchGenericMotionEvent(event);
    }
    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) { engine.clear(); fractional = 0; }
        return super.dispatchTouchEvent(event);
    }
    private void dictate() {
        diagnostics.stop();
        startActivity(new Intent(this, DictationActivity.class));
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == EXPORT) {
            if (result == RESULT_OK && data != null && data.getData() != null && pendingExport != null) {
                try (OutputStream out = getContentResolver().openOutputStream(data.getData())) {
                    if (out == null) throw new java.io.IOException("保存先を開けません");
                    out.write(pendingExport.getBytes(StandardCharsets.UTF_8)); message("診断を保存しました");
                } catch (Exception error) { message("保存失敗: " + error.getMessage()); }
            }
            pendingExport = null;
        }
    }
    @Override protected void onPause() {
        if (learningControl != null && panelFeedback != null)
            panelFeedback.setText("画面を離れたため登録を中止しました。ホームに移動した場合は Android のショートカットの可能性があります");
        diagnostics.stop(); learning = null; learningControl = null; learningKeys.clear(); panelKeys.clear();
        resetPanelMotion(); super.onPause();
    }
    @Override public void onInputDeviceAdded(int id) { topologyNote = "Android が入力機器を追加"; }
    @Override public void onInputDeviceChanged(int id) {
        if (!wasSelected(id) && !profile.references(id)) return;
        learningKeys.clearDevice(id);
        panelKeys.clearDevice(id);
        if (profile.revalidateChanged(id) || !profile.references(id)) onSelectedInputLost("選択した入力元の構成が変更");
        else topologyNote = "構成変更通知あり・入力元は維持";
    }
    @Override public void onInputDeviceRemoved(int id) {
        learningKeys.clearDevice(id);
        panelKeys.clearDevice(id);
        if (profile.invalidate(id) || wasSelected(id)) onSelectedInputLost("選択した入力元が取り外された");
        else {
            topologyNote = "Android が入力機器を削除";
            if (!profile.buttonsSelected()) lastButtonAt = -1;
            if (!profile.selected()) lastWheelAt = -1;
            if (refreshPanel != null) refreshPanel.run();
        }
    }
    private boolean wasSelected(int id) { return id >= 0 && (id == visibleWheelSelection || id == visibleButtonSelection); }
    private void updateSelectionSnapshot() {
        visibleWheelSelection = profile.wheelSelectionId();
        visibleButtonSelection = profile.buttonSelectionId();
    }
    private void onSelectedInputLost(String reason) {
        topologyNote = reason;
        diagnostics.stop(); learning = null; learningKeys.clear(); engine.clear(); updateStatus();
        learningControl = null; panelKeys.clear(); resetPanelMotion();
        if (!profile.buttonsSelected()) lastButtonAt = -1;
        if (!profile.selected()) lastWheelAt = -1;
        updateSelectionSnapshot();
        if (refreshPanel != null) refreshPanel.run();
    }
    @Override protected void onDestroy() { engine.clear(); getSystemService(InputManager.class).unregisterInputDeviceListener(this); super.onDestroy(); }
}
