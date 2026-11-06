package com.nevo.chargestats;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Настройки по design/settings_light.png: тарифы, колонки-чипы, тема, язык, служебное. */
public class SettingsActivity extends Activity {
    private Db db;
    private EditText dcEdit, acEdit;
    private Theme T;
    private I18n L;
    private float d;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        db = new Db(this);
        T = Theme.of(this);
        L = I18n.of(this);
        d = getResources().getDisplayMetrics().density;

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(T.page);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = (int)(24*d);
        root.setPadding(pad, pad, pad, pad);

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        android.widget.ImageView back = iconBox("ic_back", null, (int)(56*d), (int)(28*d), T.iconStrong);
        back.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { finish(); } });
        head.addView(back, sq(56));
        TextView h1 = tv(26, true, T.main);
        h1.setText(L.t("settings"));
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(0, -2, 1f);
        hp.leftMargin = (int)(18*d);
        head.addView(h1, hp);
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));
        root.addView(head, mbTop(12, 0));

        // ── 1. Тарифы ──
        LinearLayout c1 = card("zap", L.t("cost_t"), L.t("cost_s"));
        LinearLayout fields = new LinearLayout(this);
        fields.setOrientation(LinearLayout.HORIZONTAL);
        final EditText dcF = priceField(fields, L.t("dc_l"), T.dc, db.price("DC"));
        final EditText acF = priceField(fields, L.t("ac_l"), T.ac, db.price("AC"));
        c1.addView(fields);
        dcEdit = dcF; acEdit = acF;
        root.addView(c1, mbTop(12, 0));

        // ── 2. Колонки: чипы [иконка название свитч] ──
        LinearLayout c2 = card("grid", L.t("col_t"), L.t("col_s"));
        String[] icons = { "cal", "clock", "bolt", "batt", "rub" };
        LinearLayout chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < 5; i++) {
            final String key = "col_" + i;
            boolean on = !"0".equals(db.get(key, "1"));
            LinearLayout chip = new LinearLayout(this);
            chip.setOrientation(LinearLayout.HORIZONTAL);
            chip.setGravity(Gravity.CENTER_VERTICAL);
            chip.setPadding((int)(10*d), 0, (int)(10*d), 0);
            chip.setBackground(on
                    ? roundRect(T.dark ? 0xFF1E3050 : 0xFFE8F0FE, 12*d, (int)(2*d), T.accent)
                    : roundRect(T.chipBg, 12*d, (int)(1*d), T.fieldBorder));
            TextView name = tv(12, true, on ? T.accent : T.main);
            name.setText(L.t("colL" + i));
            name.setGravity(Gravity.CENTER);
            name.setSingleLine(true);
            name.setAutoSizeTextTypeUniformWithConfiguration(
                    8, 12, 1, TypedValue.COMPLEX_UNIT_SP);
            LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(0, -2, 1f);
            nlp.leftMargin = (int)(6*d); nlp.rightMargin = (int)(6*d);
            chip.addView(name, nlp);
            chip.setClickable(true);
            chip.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    boolean isOn = "0".equals(db.get(key, "1"));
                    db.put(key, isOn ? "1" : "0");
                    recreate();
                } });
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0, (int)(49.2f*d), 1f);
            chips.addView(chip, clp);
            if (i < 4) {
                View gap = new View(this);
                LinearLayout.LayoutParams gl = new LinearLayout.LayoutParams((int)(8*d), 1);
                chips.addView(gap, gl);
            }
        }
        c2.addView(chips);
        root.addView(c2, mbTop(12, 0));

        // ── 3. Тема: Светлая | Тёмная | Системная ──
        String theme = db.get("theme", "light");
        LinearLayout c3 = card("theme", L.t("theme_t"), L.t("theme_s"));
        c3.addView(seg3(new String[]{ L.t("th_light"), L.t("th_dark"), L.t("th_sys") },
                new String[]{"light", "dark", "system"}, theme, "theme"));
        root.addView(c3, mbTop(12, 0));

        // ── 4. Язык: Русский | English | Системный ──
        String lang = db.get("lang", "ru");
        LinearLayout c4 = card("lang", L.t("lang_t"), L.t("lang_s"));
        c4.addView(seg3(new String[]{ L.t("lang_ru"), L.t("lang_en"), L.t("lang_sys") },
                new String[]{"ru", "en", "system"}, lang, "lang"));
        root.addView(c4, mbTop(12, 0));

        // ── 5. Служебное ──
        LinearLayout c6 = card("adv", L.t("adv_t"), L.t("adv_s"));
        TextView imp = textBtn(L.t("imp"), T.accent, 16);
        imp.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("*/*");
                startActivityForResult(i, 43);
            } });
        c6.addView(imp, mb(10, 0));
        TextView folder = textBtn(L.t("folder"), T.main, 16);
        folder.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), 42);
            } });
        c6.addView(folder, mbTop(0, 0));
        root.addView(c6, mbTop(12, 0));

        setContentView(scroll);
    }

    private LinearLayout seg3(String[] labels, final String[] vals, String cur, final String setting) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < labels.length; i++) {
            boolean active = vals[i].equals(cur);
            TextView b = segBtn(labels[i], active);
            final String v = vals[i];
            b.setOnClickListener(new View.OnClickListener() {
                public void onClick(View x) {
                    if (!v.equals(db.get(setting, ""))) {
                        db.put(setting, v);
                        recreate();
                    }
                } });
            LinearLayout.LayoutParams lp = g();
            if (i > 0) lp.leftMargin = (int)(16*d);
            row.addView(b, lp);
        }
        return row;
    }

    private int di(String name) {
        return getResources().getIdentifier(name, "drawable", getPackageName());
    }
    private android.graphics.drawable.Drawable roundRect(int color, float r, int sw, int sc) {
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setColor(color); g.setCornerRadius(r);
        if (sw > 0) g.setStroke(sw, sc);
        return g;
    }

    @Override protected void onPause() {
        super.onPause();
        if (dcEdit != null) {
            double dc = num(dcEdit), ac = num(acEdit);
            if (dc > 0) db.setPrice("DC", dc);
            if (ac > 0) db.setPrice("AC", ac);
            db.backfillCosts();
        }
    }

    @Override protected void onActivityResult(int rq, int res, Intent data) {
        super.onActivityResult(rq, res, data);
        if (rq == 42 && res == RESULT_OK && data != null && data.getData() != null) {
            Uri tree = data.getData();
            int flags = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
            getContentResolver().takePersistableUriPermission(tree, flags);
            db.put("csv_tree", tree.toString());
            toast(L.t("t_folder"));
        } else if (rq == 43 && res == RESULT_OK && data != null && data.getData() != null) {
            final Uri uri = data.getData();
            new Thread(new Runnable() { public void run() {
                try {
                    java.io.InputStream in = getContentResolver().openInputStream(uri);
                    java.io.BufferedReader br = new java.io.BufferedReader(
                            new java.io.InputStreamReader(in, "UTF-8"));
                    String line; int n = 0;
                    ChargeEngine eng = new ChargeEngine();
                    java.text.SimpleDateFormat fmt = new java.text.SimpleDateFormat(
                            "yyyy-MM-dd HH:mm:ss", java.util.Locale.US);
                    br.readLine();
                    while ((line = br.readLine()) != null) {
                        if (line.trim().isEmpty()) continue;
                        try {
                            long ts = fmt.parse(line.substring(0, 19)).getTime();
                            java.util.List<String> cells = new java.util.ArrayList<String>();
                            StringBuilder cur = new StringBuilder(); boolean q = false;
                            for (char ch : line.toCharArray()) {
                                if (ch=='"') q=!q;
                                else if (ch==',' && !q) { cells.add(cur.toString()); cur.setLength(0); }
                                else cur.append(ch);
                            }
                            cells.add(cur.toString());
                            Signals.Snap s = Signals.parse(
                                    arr(cells,1), arr(cells,2), arr(cells,4),
                                    arr(cells,7), arr(cells,8), ts);
                            if (eng.onTick(db, s) != null) n++;
                        } catch (Exception ignored) { }
                    }
                    br.close(); in.close();
                    if (eng.active()) eng.forceClose(db);
                    db.backfillCosts();
                    final int cnt = n;
                    runOnUiThread(new Runnable(){public void run(){ toast(L.t("t_imp") + cnt); }});
                } catch (final Exception e) {
                    runOnUiThread(new Runnable(){public void run(){ toast("err " + e.getMessage()); }});
                }
            }}).start();
        }
    }
    private static int[] arr(java.util.List<String> c, int i) {
        if (c.size() <= i) return null;
        String v = c.get(i).trim();
        if (v.isEmpty() || !v.startsWith("[")) return null;
        v = v.substring(1, v.length() - 1);
        String[] p = v.split(",\\s*");
        int[] a = new int[p.length];
        for (int k = 0; k < p.length; k++) a[k] = Integer.parseInt(p[k].trim());
        return a;
    }

    // ─── helpers ────────────────────────────────
    private void toast(String s) {
        android.widget.Toast.makeText(this, s, android.widget.Toast.LENGTH_SHORT).show();
    }
    private LinearLayout card(String icon, String title, String sub) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(roundRect(T.card, 18*d, (int)(1*d), T.divider));
        int p = (int)(24*d);
        c.setPadding(p, p, p, p);
        LinearLayout h = new LinearLayout(this);
        h.setOrientation(LinearLayout.HORIZONTAL);
        TextView ic = new TextView(this);
        android.graphics.drawable.Drawable drw =
                getResources().getDrawable(di("ic_card_" + icon), null);
        int isz = (int)(22*d); drw.setBounds(0, 0, isz, isz);
        drw.setTintList(android.content.res.ColorStateList.valueOf(T.iconStrong));
        ic.setCompoundDrawables(drw, null, null, null);
        ic.setWidth(isz);
        ic.setHeight(isz);
        LinearLayout.LayoutParams iLP = new LinearLayout.LayoutParams(-2, -2);
        iLP.gravity = Gravity.TOP;
        h.addView(ic, iLP);
        LinearLayout tb = new LinearLayout(this);
        tb.setOrientation(LinearLayout.HORIZONTAL);
        tb.setBaselineAligned(true);
        TextView t = tv(19, true, T.main);
        t.setText(title);
        tb.addView(t, new LinearLayout.LayoutParams(-2, -2));
        TextView s = tv(14, false, T.muted);
        s.setText(sub);
        s.setSingleLine(true);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-2, -2);
        slp.leftMargin = (int)(12*d);
        tb.addView(s, slp);
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(-2, -2);
        hl.leftMargin = (int)(14*d);
        h.addView(tb, hl);
        c.addView(h, mb(12, 0));
        return c;
    }
    private EditText priceField(LinearLayout parent, String label, int iconColor, Double cur) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        TextView l = tv(14, false, iconColor);
        l.setText(label);
        l.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        col.addView(l, mb(0, 6));
        EditText e = new EditText(this);
        e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        e.setImeOptions(android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        e.setTextColor(T.main);
        e.setHint("—");
        e.setHintTextColor(T.muted);
        if (cur != null) e.setText(String.format(java.util.Locale.US, "%.2f", cur));
        e.setSingleLine(true);
        e.setPadding((int)(14*d), (int)(12*d), (int)(14*d), (int)(12*d));
        e.setBackground(roundRect(T.fieldBg, 10*d, (int)(1*d), T.fieldBorder));
        col.addView(e);
        parent.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));
        return e;
    }
    private TextView segBtn(String label, boolean active) {
        TextView t = tv(16, true, active ? T.accent : T.main);
        t.setText(label);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, (int)(14*d), 0, (int)(14*d));
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(active ? (T.dark ? 0xFF1E3050 : 0xFFE8F0FE) : T.fieldBg);
        bg.setCornerRadius(12*d);
        bg.setStroke((int)(active ? 2*d : 1*d), active ? T.accent : T.fieldBorder);
        t.setBackground(bg);
        return t;
    }
    private double num(EditText e) {
        try { return Double.parseDouble(e.getText().toString().trim()); } catch (Exception x) { return 0; }
    }
    private TextView box(String icon, String bg) {
        TextView t = new TextView(this);
        t.setGravity(Gravity.CENTER);
        t.setCompoundDrawablesWithIntrinsicBounds(0, di(icon), 0, 0);
        t.setBackground(getResources().getDrawable(di(bg), null));
        return t;
    }
    private android.widget.ImageView iconBox(String icon, String bg, int sidePx, int iconPx, int tint) {
        android.widget.ImageView iv = new android.widget.ImageView(this);
        iv.setScaleType(android.widget.ImageView.ScaleType.CENTER_INSIDE);
        int pad = (sidePx - iconPx) / 2;
        iv.setPadding(pad, pad, pad, pad);
        android.graphics.drawable.Drawable drw =
                getResources().getDrawable(di(icon), null);
        if (tint != 0) drw.setTintList(android.content.res.ColorStateList.valueOf(tint));
        iv.setImageDrawable(drw);
        if (bg != null) iv.setBackground(getResources().getDrawable(di(bg), null));
        return iv;
    }
    private LinearLayout.LayoutParams sq(int dp) {
        return new LinearLayout.LayoutParams((int)(dp*d), (int)(dp*d));
    }
    private LinearLayout.LayoutParams g() {
        return new LinearLayout.LayoutParams(0, -2, 1f);
    }
    private LinearLayout.LayoutParams mb(int dp, int x) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = (int)(dp*d); return lp;
    }
    private LinearLayout.LayoutParams mbTop(int dp, int x) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = (int)(dp*d); return lp;
    }
    private TextView textBtn(String label, int color, float sp) {
        TextView t = tv(sp, true, color);
        t.setText(label);
        t.setPadding((int)(12*d), (int)(8*d), (int)(12*d), (int)(8*d));
        return t;
    }
    private TextView tv(float sp, boolean bold, int color) {
        TextView t = new TextView(this);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        return t;
    }
}
