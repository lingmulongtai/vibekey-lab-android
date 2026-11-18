package com.lingm.vibekey;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public final class UpdateActivity extends Activity {
    private AppUpdates updates;
    private Button recheck, download, install, restart;
    private TextView status;
    private final Runnable listener = this::render;

    @Override public void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        updates = AppUpdates.get(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int padding = Math.round(20 * getResources().getDisplayMetrics().density);
        content.setPadding(padding, padding, padding, padding);
        ScrollView root = new ScrollView(this);
        root.addView(content);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            var paddingInsets = insets.getInsets(android.view.WindowInsets.Type.systemBars());
            view.setPadding(paddingInsets.left, paddingInsets.top, paddingInsets.right, paddingInsets.bottom);
            return insets;
        });
        setContentView(root);

        TextView title = new TextView(this);
        title.setText("アプリの更新");
        title.setTextSize(27);
        content.addView(title);
        TextView installed = new TextView(this);
        installed.setText("インストール済み " + BuildConfig.VERSION_NAME);
        content.addView(installed);
        status = new TextView(this);
        status.setTextSize(16);
        status.setPadding(0, padding, 0, padding);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        content.addView(status);

        recheck = button(content, "更新を確認", () -> updates.check(true));
        download = button(content, "APK をダウンロード", updates::download);
        install = button(content, "更新をインストール", this::install);
        restart = button(content, "インストールをやり直す", updates::restartInstall);
        button(content, "閉じる", this::finish);
    }

    @Override protected void onResume() {
        super.onResume();
        updates.observe(listener);
        updates.check(false);
    }

    @Override protected void onPause() {
        updates.remove(listener);
        super.onPause();
    }

    private Button button(LinearLayout content, String text, Runnable run) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setOnClickListener(view -> run.run());
        content.addView(button);
        return button;
    }

    private void render() {
        AppUpdates.State state = updates.state();
        String version = updates.availableVersion();
        boolean busy = state == AppUpdates.State.CHECKING || state == AppUpdates.State.DOWNLOADING
                || state == AppUpdates.State.INSTALLING;
        status.setText(switch (state) {
            case IDLE -> "GitHub の公開ビルドを確認できます。";
            case CHECKING -> "更新を確認中…";
            case CURRENT -> "更新はありません。";
            case AVAILABLE -> "更新 " + version + " を利用できます。";
            case CHECK_FAILED -> "更新を確認できませんでした。通信を確認して、もう一度試してください。";
            case DOWNLOADING -> version + " をダウンロードして検証中…";
            case DOWNLOAD_FAILED -> "ダウンロードまたは APK の検証に失敗しました。もう一度試してください。";
            case READY -> version + " の検証が完了しました。";
            case INSTALLING -> "Android のインストール確認を待っています。";
            case INSTALL_CANCELED -> "インストールをキャンセルしました。";
            case INSTALL_FAILED -> "インストールできませんでした。端末の設定を確認して、もう一度試してください。";
            case COMPLETE -> "更新をインストールしました。";
        });
        recheck.setEnabled(!busy);
        restart.setVisibility(updates.canRestartInstall() ? View.VISIBLE : View.GONE);
        download.setVisibility(state == AppUpdates.State.AVAILABLE || state == AppUpdates.State.DOWNLOAD_FAILED
                ? View.VISIBLE : View.GONE);
        install.setVisibility(updates.hasPackage() && !busy ? View.VISIBLE : View.GONE);
        install.setText(getPackageManager().canRequestPackageInstalls()
                ? "更新をインストール" : "Android のインストール元設定を開く");

        Intent confirmation = updates.takeConfirmation();
        if (confirmation != null) {
            try {
                startActivity(confirmation);
            } catch (ActivityNotFoundException | SecurityException failure) {
                updates.failConfirmation();
            }
        }
    }

    private void install() {
        if (getPackageManager().canRequestPackageInstalls()) {
            updates.install();
            return;
        }
        Intent settings = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.fromParts("package", getPackageName(), null));
        try {
            startActivity(settings);
        } catch (ActivityNotFoundException | SecurityException failure) {
            Toast.makeText(this, "この端末ではインストール元の設定を開けません。", Toast.LENGTH_LONG).show();
        }
    }
}
