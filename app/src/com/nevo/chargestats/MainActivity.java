package com.nevo.chargestats;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Главный экран 1:1 по design/main_light.png / main_dark.png. */
public class MainActivity extends Activity {
    private Db db;
    private Theme T;
    private I18n L;
    private String stateSig;
    private float d;
    private LinearLayout list;
    private TextView totals;
    private TextView[] segs = new TextView[3];
    private int period = 0;
    private static final long[] PERIOD_MS = { 31L*86400000L, 183L*86400000L, 366L*86400000L };
    private final SimpleDateFormat dt = new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.US);
    private float[] W;
    private boolean[] vis;
    private SwipeRow openRow;

    private final BroadcastReceiver tick = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { render(); }
    };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        db = new Db(this);
        T = Theme.of(this);
        L = I18n.of(this);
        stateSig = Theme.sig(this) + "|" + I18n.langCode(this) + "|" + colsSig();
        d = getResources().getDisplayMetrics().density;
        int pad = (int)(20 * d);

        if (!LoggerService.isRunning()) {
            Intent s = new Intent(this, LoggerService.class);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(s); else startService(s);
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(T.page);
        root.setPadding(pad, (int)(10*d), pad, (int)(14*d));

        // ── шапка ──
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);

        android.widget.ImageView logo = new android.widget.ImageView(this);
        logo.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
        logo.setImageResource(di(T.dark ? "logo_dark" : "logo_light"));
        head.addView(logo, sq(64));

        TextView h1 = tv(26, true, T.main);
        h1.setText(L.t("h1"));
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(0, -2, 1f);
        hp.leftMargin = (int)(18*d);
        head.addView(h1, hp);

        LinearLayout seg = new LinearLayout(this);
        seg.setOrientation(LinearLayout.HORIZONTAL);
        seg.setPadding((int)(4*d), (int)(4*d), (int)(4*d), (int)(4*d));
        seg.setBackground(roundRect(T.segC, 12*d, 0, 0));
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            segs[i] = tv(16, true, T.main);
            segs[i].setText(L.t("m" + i));
            segs[i].setGravity(Gravity.CENTER);
            segs[i].setPadding((int)(26*d), (int)(10*d), (int)(26*d), (int)(10*d));
            segs[i].setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { period = idx; styleSegs(); render(); } });
            seg.addView(segs[i]);
        }
        head.addView(seg);

        // кнопка настроек: квадрат со стороной = высота сегмента, шестерёнка x1.5
        float segPx = (16 * d * 1.15f + 20 * d + 8 * d); // textSize*1.15 + vertical paddings + container padding
        int gearSide = (int) segPx;
        int gearIcon = (int)(39 * d); // 26dp * 1.5
        android.widget.ImageView gear = new android.widget.ImageView(this);
        gear.setScaleType(android.widget.ImageView.ScaleType.CENTER_INSIDE);
        int gp2 = (gearSide - gearIcon) / 2;
        gear.setPadding(gp2, gp2, gp2, gp2);
        android.graphics.drawable.Drawable gdr =
                getResources().getDrawable(di("ic_gear"), null);
        gdr.setTintList(android.content.res.ColorStateList.valueOf(T.iconStrong));
        gear.setImageDrawable(gdr);
        gear.setBackground(roundRect(T.segC, 12*d, 0, 0));
        gear.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { startActivity(new Intent(MainActivity.this, SettingsActivity.class)); } });
        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(gearSide, gearSide);
        gp.leftMargin = (int)(14*d);
        head.addView(gear, gp);

        root.addView(head, new LinearLayout.LayoutParams(-1, -2));

        // ── карточка ──
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(roundRect(T.card, 16*d, (int)(1*d), T.divider));
        int cp = (int)(26*d);
        card.setPadding(cp, cp, cp, cp);
        LinearLayout.LayoutParams cpLp = new LinearLayout.LayoutParams(-1, 0, 1f);
        cpLp.topMargin = (int)(16*d);

        TextView ct = tv(20, true, T.main);
        ct.setText(L.t("history"));
        card.addView(ct, mb(14));

        vis = new boolean[7];
        vis[0] = true;
        for (int i = 0; i < 5; i++) vis[i+1] = !"0".equals(db.get("col_" + i, "1"));
        vis[6] = true;
        String[] ckeys = { "c_type","c_date","c_dur","c_pwr","c_energy","c_cost","c_soc" };
        W = new float[]{ 1.1f, 2.4f, 1.7f, 1.7f, 1.8f, 1.7f, 1.7f };

        LinearLayout th = new LinearLayout(this);
        th.setOrientation(LinearLayout.HORIZONTAL);
        th.setPadding((int)(12*d), (int)(14*d), (int)(12*d), (int)(14*d));
        th.setBackground(roundRect(T.thead, 10*d, 0, 0));
        for (int i = 0; i < ckeys.length; i++) {
            if (!vis[i]) continue;
            TextView t = tv(14, false, T.muted);
            t.setText(L.t(ckeys[i]));
            th.addView(t, wgt(W[i]));
        }
        card.addView(th, mb(2));

        ScrollView sv = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        sv.addView(list);
        card.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));

        totals = tv(15, false, T.muted);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-1, -2);
        tp.topMargin = (int)(14*d);
        card.addView(totals, tp);

        root.addView(card, cpLp);
        setContentView(root);
        styleSegs();
        render();
    }

    private void styleSegs() {
        for (int i = 0; i < 3; i++) {
            segs[i].setBackground(i == period ? roundRect(T.segA, 10*d, 0, 0) : null);
        }
    }
    private android.graphics.drawable.Drawable roundRect(int color, float r, int sw, int sc) {
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setColor(color); g.setCornerRadius(r);
        if (sw > 0) g.setStroke(sw, sc);
        return g;
    }

    private void render() {
        openRow = null;
        list.removeAllViews();
        List<String> rows = db.listSessions(500);
        long cutoff = System.currentTimeMillis() - PERIOD_MS[period];
        double totE = 0, totC = 0; int n = 0;

        for (String r : rows) {
            final String[] p = r.split("\\|");
            long st = Long.parseLong(p[2]), en = Long.parseLong(p[3]);
            boolean live = "open".equals(p.length > 9 ? p[9] : "closed");
            if (st < cutoff) continue;
            boolean done = en > 0 && !live;
            double e = Double.parseDouble(p[4]), mx = Double.parseDouble(p[5]);
            double s0 = Double.parseDouble(p[6]), s1 = Double.parseDouble(p[7]);
            double cost = Double.parseDouble(p[8]);
            if (done) { totE += e; if (cost >= 0) totC += cost; n++; }

            if (list.getChildCount() > 0) list.addView(divider());

            LinearLayout tr = new LinearLayout(this);
            tr.setOrientation(LinearLayout.HORIZONTAL);
            tr.setGravity(Gravity.CENTER_VERTICAL);
            tr.setPadding((int)(12*d), (int)(14*d), (int)(12*d), (int)(14*d));

            // 0: тип — только бейдж
            if (vis[0]) {
                TextView badge = tv(12, true, 0xFFFFFFFF);
                badge.setText(p[1]);
                badge.setGravity(Gravity.CENTER);
                badge.setBackground(dr("AC".equals(p[1]) ? "badge_ac" : "badge_dc"));
                LinearLayout tt = new LinearLayout(this);
                tt.setOrientation(LinearLayout.HORIZONTAL);
                tt.setGravity(Gravity.CENTER_VERTICAL);
                tt.addView(badge, new LinearLayout.LayoutParams((int)(34*d), (int)(34*d)));
                tr.addView(tt, wgt(W[0]));
            }
            if (vis[1]) tr.addView(cell(dt.format(new Date(st))), wgt(W[1]));
            if (vis[2]) {
                LinearLayout dc = new LinearLayout(this);
                dc.setOrientation(LinearLayout.VERTICAL);
                dc.setGravity(Gravity.CENTER_VERTICAL);
                String dur = "—";
                if (done || live) {
                    long endRef = live ? System.currentTimeMillis() : en;
                    long m = Math.max(0, (endRef - st) / 60000);
                    dur = live ? String.format(Locale.US, "%d:%02d", m/60, m%60)
                               : String.format(Locale.US, "%d:%02d:00", m/60, m%60);
                }
                dc.addView(cell(dur));
                if (live) {
                    TextView gl = tv(13, false, T.live);
                    gl.setText(L.t("live"));
                    dc.addView(gl);
                }
                tr.addView(dc, wgt(W[2]));
            }
            if (vis[3]) tr.addView(cell(done || live || mx > 0 ? String.format(Locale.US, "%.1f", mx) : "—"), wgt(W[3]));
            if (vis[4]) tr.addView(cell(String.format(Locale.US, "%.2f", e)), wgt(W[4]));
            if (vis[5]) tr.addView(cell((cost < 0 || live) ? "—" : String.format(Locale.US, "%.2f", cost)), wgt(W[5]));
            if (vis[6]) tr.addView(cell(String.format(Locale.US, "%.0f%%→%.0f%%", s0, s1 > 0 ? s1 : s0)), wgt(W[6]));

            final long sid = Long.parseLong(p[0]);
            SwipeRow swr = new SwipeRow(this, tr, L.t("del"), L.t("edit"), T.accent, T.danger, T.card, (int)(34*d), d);
            swr.onGrab = new Runnable() { public void run() { closeOther(swr); } };
            swr.onEdit = new Runnable() { public void run() {
                Intent i = new Intent(MainActivity.this, EditSessionActivity.class);
                i.putExtra("id", sid); startActivity(i); } };
            swr.onDelete = new Runnable() { public void run() {
                db.deleteSession(sid); render(); } };
            list.addView(swr);
        }
        if (list.getChildCount() == 0) {
            TextView empty = tv(16, false, T.muted);
            empty.setText(L.t("empty"));
            empty.setPadding((int)(12*d), (int)(24*d), (int)(12*d), (int)(24*d));
            list.addView(empty);
        }
        totals.setText(String.format(Locale.US, L.t("totals"), n, totE, totC));
    }

    private void closeOther(SwipeRow cur) {
        if (openRow != null && openRow != cur) openRow.snapClosed();
        openRow = cur;
    }

    // ── helpers ──
    private TextView cell(String s) {
        TextView t = tv(16, false, T.main);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setText(s);
        return t;
    }
    private LinearLayout.LayoutParams wgt(float w) {
        return new LinearLayout.LayoutParams(0, -2, w);
    }
    private View divider() {
        View v = new View(this);
        v.setBackgroundColor(T.divider);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, Math.max(2, (int)d));
        lp.leftMargin = (int)(12*d); lp.rightMargin = (int)(12*d);
        v.setLayoutParams(lp);
        return v;
    }
    private LinearLayout.LayoutParams sq(int dp) {
        return new LinearLayout.LayoutParams((int)(dp*d), (int)(dp*d));
    }
    private LinearLayout.LayoutParams mb(int dp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = (int)(dp*d); return lp;
    }
    private int di(String name) {
        return getResources().getIdentifier(name, "drawable", getPackageName());
    }
    private android.graphics.drawable.Drawable dr(String name) {
        return getResources().getDrawable(di(name), null);
    }
    private TextView tv(float sp, boolean bold, int color) {
        TextView t = new TextView(this);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        return t;
    }

    @Override protected void onResume() {
        super.onResume();
        registerReceiver(tick, new IntentFilter(LoggerService.ACTION_TICK));
        String sig = Theme.sig(this) + "|" + I18n.langCode(this) + "|" + colsSig();
        if (!sig.equals(stateSig)) recreate();
        else render();
    }
    private String colsSig() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 5; i++) sb.append(db.get("col_" + i, "1")).append(',');
        return sb.toString();
    }

    @Override protected void onPause() {
        super.onPause();
        try { unregisterReceiver(tick); } catch (Exception ignored) { }
    }
}
