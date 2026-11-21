package com.velthon.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.view.View;

/** The raised gold circle that sits in the dock's notch, holding the selected app. */
class Bubble extends View {
    private final Paint disc = new Paint(Paint.ANTI_ALIAS_FLAG), shade = new Paint(Paint.ANTI_ALIAS_FLAG),
            rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Drawable icon;
    final float r;
    private int lw = -1;

    Bubble(Context c) {
        super(c);
        r = U.dp(c, 26);
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(U.dp(c, 1.2f));
    }

    void setIcon(Drawable d) { icon = d; invalidate(); }

    @Override protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        float cx = w / 2f, cy = r + U.dp(getContext(), 1);
        if (w != lw) {
            lw = w;
            disc.setShader(new LinearGradient(0, cy - r, 0, cy + r, 0xFFFFDA5C, 0xFFE0A200, Shader.TileMode.CLAMP));
            rim.setShader(new LinearGradient(0, cy - r, 0, cy + r, 0xE6FFF1C2, 0x66FFC400, Shader.TileMode.CLAMP));
            shade.setShader(new RadialGradient(cx, cy + r * .25f, r * 1.35f, new int[]{0x66000000, 0x26000000, 0x00000000},
                    new float[]{0f, .7f, 1f}, Shader.TileMode.CLAMP));
        }
        c.drawCircle(cx, cy + r * .25f, r * 1.35f, shade);
        c.drawCircle(cx, cy, r, disc);
        if (icon != null) {
            int s = (int) (r * 1.2f);
            icon.setBounds((int) (cx - s / 2f), (int) (cy - s / 2f), (int) (cx + s / 2f), (int) (cy + s / 2f));
            icon.draw(c);
        }
    }
}
