package com.nevo.chargestats;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

/** Мини-переключатель (трек+ползунок) — без appcompat, source 8. */
public class SwitchView extends View {
    private boolean on = true;
    private int trackOn, trackOff, thumb = 0xFFFFFFFF;
    public Runnable onChange;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float w_, h_;

    public SwitchView(Context c, int accent, int off) {
        super(c);
        trackOn = accent; trackOff = off;
        float d = c.getResources().getDisplayMetrics().density;
        setMinimumWidth((int)(46*d)); setMinimumHeight((int)(26*d));
    }
    public void setOn(boolean v) { on = v; invalidate(); }
    public boolean isOn() { return on; }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) { w_ = w; h_ = h; }
    @Override protected void onMeasure(int ws, int hs) {
        float d = getResources().getDisplayMetrics().density;
        setMeasuredDimension((int)(52*d), (int)(28*d));
    }

    @Override protected void onDraw(Canvas cv) {
        float r = h_ / 2f;
        p.setColor(on ? trackOn : trackOff);
        cv.drawRoundRect(new RectF(0, 0, w_, h_), r, r, p);
        float tr = r - h_ * 0.14f;
        float cx = on ? w_ - r : r;
        p.setColor(thumb);
        cv.drawCircle(cx, r, tr, p);
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() == MotionEvent.ACTION_UP) {
            on = !on; invalidate();
            if (onChange != null) onChange.run();
            performClick();
            return true;
        }
        return true;
    }
    @Override public boolean performClick() { return super.performClick(); }
}
