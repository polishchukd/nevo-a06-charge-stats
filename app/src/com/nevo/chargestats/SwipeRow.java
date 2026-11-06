package com.nevo.chargestats;

import android.content.Context;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;
import android.widget.TextView;

/**
 * Строка со свайпом: влево — справа открывается «Удалить», вправо — слева «Редактировать».
 */
public class SwipeRow extends FrameLayout {
    public Runnable onEdit, onDelete, onClick, onGrab;
    private View content;
    private final int reveal, slop;
    private float sx, sy;
    private boolean dragging, decided;
    private int openDir; // -1 = delete показан, +1 = edit, 0 закрыт

    public SwipeRow(Context c, View row, String delLabel, String editLabel,
                    int accent, int danger, int bgColor, int btnH, float d) {
        super(c);
        slop = ViewConfiguration.get(c).getScaledTouchSlop();
        reveal = (int)(140 * d);

        TextView del = btn(delLabel, danger, d);
        TextView ed  = btn(editLabel, accent, d);
        del.setOnClickListener(new OnClickListener() {
            public void onClick(View v) { if (onDelete != null) onDelete.run(); } });
        ed.setOnClickListener(new OnClickListener() {
            public void onClick(View v) { if (onEdit != null) onEdit.run(); } });
        LayoutParams dlp = new LayoutParams(reveal, btnH, Gravity.END | Gravity.CENTER_VERTICAL);
        LayoutParams elp = new LayoutParams(reveal, btnH, Gravity.START | Gravity.CENTER_VERTICAL);
        dlp.rightMargin = (int)(6 * d); elp.leftMargin = (int)(6 * d);
        addView(del, dlp);
        addView(ed, elp);

        content = row;
        if (content.getBackground() == null) {
            content.setBackgroundColor(bgColor);
        }
        addView(content, new LayoutParams(-1, -1));
    }

    private TextView btn(String label, int color, float d) {
        TextView t = new TextView(getContext());
        t.setText(label);
        t.setTextColor(0xFFFFFFFF);
        t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13f);
        t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        t.setGravity(Gravity.CENTER);
        t.setSingleLine(true);
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(8 * d);
        t.setBackground(g);
        return t;
    }

    public boolean isOpen() { return openDir != 0; }
    public void snapClosed() {
        openDir = 0;
        content.animate().translationX(0f).setDuration(150).start();
    }

    @Override public boolean onInterceptTouchEvent(MotionEvent e) {
        int a = e.getActionMasked();
        if (a == MotionEvent.ACTION_DOWN) {
            sx = e.getRawX(); sy = e.getRawY();
            dragging = false; decided = false;
            if (openDir != 0 && onGrab != null) onGrab.run();
        } else if (a == MotionEvent.ACTION_MOVE) {
            float dx = e.getRawX() - sx, dy = e.getRawY() - sy;
            if (!decided && (Math.abs(dx) > slop || Math.abs(dy) > slop)) {
                decided = true;
                dragging = Math.abs(dx) > Math.abs(dy) * 1.2f;
            }
        }
        return dragging;
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        float tx = content.getTranslationX();
        int a = e.getActionMasked();
        if (a == MotionEvent.ACTION_MOVE) {
            float nx = tx + (e.getRawX() - sx);
            sx = e.getRawX();
            if (nx > reveal) nx = reveal + (nx - reveal) * 0.25f;
            if (nx < -reveal) nx = -reveal + (nx + reveal) * 0.25f;
            content.setTranslationX(nx);
            return true;
        }
        if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
            int target = 0;
            if (tx < -reveal / 2f) target = -reveal;
            else if (tx > reveal / 2f) target = reveal;
            openDir = target > 0 ? 1 : target < 0 ? -1 : 0;
            content.animate().translationX((float) target).setDuration(150).start();
            dragging = false;
            return true;
        }
        return true;
    }
}
