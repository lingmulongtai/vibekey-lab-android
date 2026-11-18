package com.lingm.vibekey;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import java.util.EnumMap;
import java.util.function.Consumer;

/** Original front illustration. Each physical control is a real focusable Android button. */
final class FrontPanel extends LinearLayout {
    private final EnumMap<ControlLayers.Control, Button> keys = new EnumMap<>(ControlLayers.Control.class);

    FrontPanel(Context context, Consumer<ControlLayers.Control> choose) {
        super(context);
        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER_HORIZONTAL);
        setPadding(dp(10), dp(9), dp(10), dp(10));
        GradientDrawable housing = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[] {0xff7f909d, 0xffbac6cb, 0xffe6ebed, 0xff9eafb8});
        housing.setCornerRadius(dp(14));
        housing.setStroke(dp(1), 0xff83939e);
        setBackground(housing);
        addView(new View(context) {
            private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override protected void onDraw(Canvas canvas) {
                paint.setColor(0xff344852);
                float slot = getWidth() / 17f;
                for (int i = 0; i < 6; i++) {
                    float x = getWidth() / 2f + (i - 2.5f) * slot * 1.75f;
                    canvas.drawRoundRect(x - slot / 2, dp(3), x + slot / 2, getHeight() - dp(3), dp(2), dp(2), paint);
                }
            }
        }, new LayoutParams(dp(65), dp(17)));
        FrameLayout dial = new FrameLayout(context);
        Button ring = makeControl(ControlLayers.Control.ROTATE, "", choose);
        dial.addView(ring, new FrameLayout.LayoutParams(-1, -1));
        Button center = makeControl(ControlLayers.Control.PRESS, "●", choose);
        dial.addView(center, new FrameLayout.LayoutParams(dp(48), dp(48), Gravity.CENTER));
        LayoutParams dialBox = new LayoutParams(dp(76), dp(76)); dialBox.topMargin = dp(5); dialBox.bottomMargin = dp(4);
        addView(dial, dialBox);
        addControl(ControlLayers.Control.K1, "K1", 56, 48, choose);
        addControl(ControlLayers.Control.K2, "✓", 56, 48, choose);
        addControl(ControlLayers.Control.K3, "×", 56, 48, choose);
    }

    private void addControl(ControlLayers.Control control, String glyph, int width, int height,
                            Consumer<ControlLayers.Control> choose) {
        LayoutParams box = new LayoutParams(dp(width), dp(height)); box.topMargin = dp(5);
        addView(makeControl(control, glyph, choose), box);
    }

    private Button makeControl(ControlLayers.Control control, String glyph, Consumer<ControlLayers.Control> choose) {
        Button key = new Button(getContext());
        key.setText(glyph); key.setTextSize(19); key.setAllCaps(false);
        key.setContentDescription(control.label);
        key.setPadding(0, 0, 0, 0);
        key.setMinWidth(0); key.setMinHeight(0);
        key.setOnClickListener(v -> choose.accept(control));
        keys.put(control, key);
        return key;
    }

    void highlight(ControlLayers.Control selected) {
        for (var entry : keys.entrySet()) {
            boolean active = entry.getKey() == selected;
            boolean dial = entry.getKey() == ControlLayers.Control.ROTATE || entry.getKey() == ControlLayers.Control.PRESS;
            GradientDrawable face = new GradientDrawable();
            face.setColor(active ? 0xff133d3a : (dial ? 0xff25343e : 0xfff4f7f8));
            face.setCornerRadius(dp(dial ? 40 : 8));
            face.setStroke(dp(active ? 2 : 1), active ? 0xff64d3c0 : 0xff627981);
            Button key = entry.getValue();
            key.setBackground(new android.graphics.drawable.RippleDrawable(ColorStateList.valueOf(0x6677c7bc), face, null));
            key.setTextColor(active || dial ? 0xffedf9f7 : 0xff16343f);
            key.setSelected(active);
        }
    }

    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
