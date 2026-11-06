package com.nevo.chargestats;

import android.app.Activity;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Диалог редактирования сессии 1:1 по design/edit_light.png:
 * белая карточка-модалка, заголовок "Сессия ДД.ММ.ГГГГ ЧЧ:ММ", 2x4 поля,
 * пустые поля = без изменений, кнопки Отмена/Сохранить.
 */
public class EditSessionActivity extends Activity {
    private Db db;
    private long sid;
    private EditText type, vAvg, dateStart, maxKw, durMin, energy, soc, cost;
    private long origStart, origEnd;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        sid = getIntent().getLongExtra("id", -1);
        if (sid < 0) { finish(); return; }
        db = new Db(this);
        Theme T = Theme.of(this);
        I18n L = I18n.of(this);
        float d = getResources().getDisplayMetrics().density;

        // читаем сессию
        String[] s = db.getSession(sid);
        if (s == null) { finish(); return; }
        origStart = Long.parseLong(s[2]); origEnd = Long.parseLong(s[3]);
        long durMin0 = origEnd > 0 ? (origEnd - origStart) / 60000 : 0;

        SimpleDateFormat df = new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.US);

        // оверлей
        ScrollView outer = new ScrollView(this);
        outer.setBackgroundColor(0xCC000000);
        outer.setPadding((int)(48*d), (int)(32*d), (int)(48*d), (int)(32*d));

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundColor(T.card);
        // rounded corners programmatically
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(T.card);
        bg.setCornerRadius(24 * d);
        card.setBackground(bg);
        int cp = (int)(32*d);
        card.setPadding(cp, cp, cp, cp);
        android.widget.FrameLayout.LayoutParams clp = new android.widget.FrameLayout.LayoutParams(
                -1, -2, android.view.Gravity.CENTER);
        outer.addView(card, clp);

        // заголовок
        TextView title = new TextView(this);
        title.setText(L.t("s_title") + df.format(new Date(origStart)));
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        title.setTextColor(T.main);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        card.addView(title, mb(d, 10));

        card.addView(divider(T, d));

        TextView hint = new TextView(this);
        hint.setText(L.t("s_hint"));
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        hint.setTextColor(T.muted);
        card.addView(hint, mb(d, 18));

        // 2×4 grid
        type = field(T, L.t("f_type"), s[1] != null ? s[1] : "");
        vAvg = field(T, L.t("f_volt"), "");
        dateStart = field(T, L.t("f_date"), "");
        maxKw = field(T, L.t("f_max"), d1(s[5]));
        durMin = field(T, L.t("f_dur"), durMin0 > 0 ? String.valueOf(durMin0) : "");
        energy = field(T, L.t("f_energy"), d2(s[4]));
        soc = field(T, L.t("f_soc"), d0(s[6]) + "→" + d0(s[7]));
        cost = field(T, L.t("f_cost"), d2(s[8].equals("-1") ? "0" : s[8]));

        addGrid(card, new EditText[]{type, vAvg, dateStart, maxKw, durMin, energy, soc, cost}, d);

        card.addView(divider(T, d), mbTop(d, 18));

        // кнопки
        LinearLayout btns = new LinearLayout(this);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        btns.setGravity(Gravity.END);
        TextView cancel = textBtn(L.t("cancel"), T.muted, d, 18);
        cancel.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { finish(); } });
        TextView save = textBtn(L.t("save"), T.accent, d, 18);
        save.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { save(); } });
        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(-2, -2);
        gp.leftMargin = (int)(32*d);
        btns.addView(cancel);
        btns.addView(save, gp);
        card.addView(btns);

        setContentView(outer);
    }

    private void save() {
        android.content.ContentValues cv = new android.content.ContentValues();
        if (type.getText().length() > 0) cv.put("type", type.getText().toString().trim().toUpperCase(java.util.Locale.US));
        if (maxKw.getText().length() > 0) cv.put("max_kw", d(maxKw));
        if (durMin.getText().length() > 0) cv.put("end_ts", origStart + (long)(d(durMin) * 60000));
        if (energy.getText().length() > 0) cv.put("energy_kwh", d(energy));
        if (soc.getText().length() > 0) {
            String[] p = soc.getText().toString().split("→");
            if (p.length == 2) {
                try {
                    cv.put("soc_start", Double.parseDouble(p[0].trim().replace("%","")));
                    cv.put("soc_end", Double.parseDouble(p[1].trim().replace("%","")));
                } catch (Exception ignored) { }
            }
        }
        if (cost.getText().length() > 0) {
            cv.put("cost", d(cost)); cv.put("cost_filled", 1);
        }
        if (dateStart.getText().length() > 0) {
            try {
                java.util.Date dt = new java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.US)
                        .parse(dateStart.getText().toString());
                long delta = dt.getTime() - origStart;
                cv.put("start_ts", dt.getTime());
                if (origEnd > 0) cv.put("end_ts", origEnd + delta);
            } catch (Exception ignored) { }
        }
        if (cv.size() > 0) db.updateSession(sid, cv);
        sendBroadcast(new android.content.Intent(LoggerService.ACTION_TICK));
        finish();
    }

    // ---- helpers ----
    private double d(EditText e) { try { return Double.parseDouble(e.getText().toString().trim()); } catch (Exception e2) { return 0; } }
    private String d1(String s) { try { return String.format(Locale.US, "%.1f", Double.parseDouble(s)); } catch (Exception e) { return ""; } }
    private String d2(String s) { try { return String.format(Locale.US, "%.2f", Double.parseDouble(s)); } catch (Exception e) { return ""; } }
    private String d0(String s) { try { return String.format(Locale.US, "%.0f", Double.parseDouble(s)); } catch (Exception e) { return ""; } }

    private EditText field(Theme T, String label, String value) {
        // wrap: label + input — caller arranges in grid
        EditText e = new EditText(this);
        e.setText(value);
        e.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL | android.text.InputType.TYPE_NUMBER_FLAG_SIGNED);
        e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        e.setTextColor(T.main);
        e.setHint(label);
        e.setHintTextColor(T.muted);
        float d = getResources().getDisplayMetrics().density;
        e.setSingleLine(true);
        e.setPadding((int)(14*d), (int)(12*d), (int)(14*d), (int)(12*d));
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(T.fieldBg); bg.setCornerRadius(10*d);
        bg.setStroke((int)(1*d), T.fieldBorder);
        e.setBackground(bg);
        e.setTag(label);
        return e;
    }

    private void addGrid(LinearLayout card, EditText[] fields, float d) {
        for (int i = 0; i < fields.length; i += 2) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            int gap = (int)(20*d);
            row.addView(fields[i], gp(d));
            if (i + 1 < fields.length) {
                LinearLayout.LayoutParams lp = gp(d); lp.leftMargin = gap;
                row.addView(fields[i+1], lp);
            }
            LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-1, -2);
            rl.bottomMargin = (int)(16*d);
            card.addView(row, rl);
        }
    }
    private LinearLayout.LayoutParams gp(float d) {
        return new LinearLayout.LayoutParams(0, -2, 1f);
    }
    private LinearLayout.LayoutParams mb(float d, int dp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = (int)(dp*d); return lp;
    }
    private LinearLayout.LayoutParams mbTop(float d, int dp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = (int)(dp*d); return lp;
    }
    private View divider(Theme T, float d) {
        View v = new View(this);
        v.setBackgroundColor(T.divider);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, (int)(1*d));
        lp.bottomMargin = (int)(10*d);
        return v;
    }
    private TextView textBtn(String label, int color, float d, float sp) {
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        t.setPadding((int)(12*d), (int)(8*d), (int)(12*d), (int)(8*d));
        return t;
    }
}
