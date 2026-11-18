package com.lingm.vibekey;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;

/** Non-exported entry point: a launcher Intent extra must never activate external speech UI. */
public final class DictationActivity extends Activity {
    private EditText resultText;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout panel = new LinearLayout(this); panel.setOrientation(LinearLayout.VERTICAL);
        int padding = Math.round(20 * getResources().getDisplayMetrics().density);
        panel.setPadding(padding, padding * 3, padding, padding);
        TextView note = new TextView(this); note.setText("音声入力は選択された外部アプリが処理します。マイクとデータの扱いはそのアプリに依存します。結果は自動送信・診断保存しません。"); panel.addView(note);
        Button start = new Button(this); start.setText("音声入力を開始"); panel.addView(start); start.setOnClickListener(v -> recognize());
        resultText = new EditText(this); resultText.setSaveEnabled(false); panel.addView(resultText);
        Button copy = new Button(this); copy.setText("結果をコピー"); panel.addView(copy);
        copy.setOnClickListener(v -> ((ClipboardManager) getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("音声入力", resultText.getText())));
        Button close = new Button(this); close.setText("閉じる"); panel.addView(close); close.setOnClickListener(v -> finish()); setContentView(panel);
    }
    private void recognize() {
        try { startActivityForResult(new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM), 1);
        } catch (android.content.ActivityNotFoundException missing) { resultText.setText("音声入力アプリが見つかりません"); }
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != 1 || result != RESULT_OK || data == null) return;
        ArrayList<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
        if (results != null && !results.isEmpty()) resultText.setText(results.get(0));
    }
}
