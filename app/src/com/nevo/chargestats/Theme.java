package com.nevo.chargestats;

import android.content.Context;
import android.content.res.Configuration;

/** Палитры light/dark по референсам design/*.png. settings("theme"): light|dark|system. */
public class Theme {
    public int page, card, thead, divider, main, muted, segC, segA, ac, dc, live,
               fieldBg, fieldBorder, accent, iconOnDark, chipBg, danger,
               iconStrong, iconWeak;
    public boolean dark;

    public static boolean isNight(Context c) {
        int m = c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return m == Configuration.UI_MODE_NIGHT_YES;
    }

    /** Сигнатура текущего вида (для пересоздания активностей). */
    public static String sig(Context c) {
        return new Db(c).get("theme", "light") + (isNight(c) ? "-n" : "-d");
    }

    public static Theme of(Context c) {
        String t = new Db(c).get("theme", "light");
        boolean dark;
        if ("system".equals(t)) dark = isNight(c);
        else dark = "dark".equals(t);
        Theme p = new Theme();
        p.ac = 0xFF17A65B; p.dc = 0xFFFA7A06; p.live = 0xFFF59E0B;
        p.iconOnDark = 0xFFFFFFFF; p.danger = 0xFFD93025;
        if (dark) {
            p.dark = true;
            p.page = 0xFF0F1520; p.card = 0xFF171F2E; p.thead = 0xFF1C2536;
            p.divider = 0xFF232C3D; p.main = 0xFFE6EDF7; p.muted = 0xFF8CA0BC;
            p.segC = 0xFF243049; p.segA = 0xFF34435E;
            p.fieldBg = 0xFF0F1520; p.fieldBorder = 0xFF33415A; p.accent = 0xFF5B8CFF;
            p.chipBg = 0xFF10182A;
            p.iconStrong = 0xFFB8C2CF; p.iconWeak = 0xFF7E8894;
        } else {
            p.dark = false;
            p.page = 0xFFF4F7FB; p.card = 0xFFFFFFFF; p.thead = 0xFFF3F7FD;
            p.divider = 0xFFE6EBF2; p.main = 0xFF16264A; p.muted = 0xFF6B7A90;
            p.segC = 0xFFDCE4F0; p.segA = 0xFFC9D5E6;
            p.fieldBg = 0xFFF8FAFC; p.fieldBorder = 0xFFCBD5E1; p.accent = 0xFF2563EB;
            p.chipBg = 0xFFF3F6FB;
            p.iconStrong = 0xFF4B5563; p.iconWeak = 0xFF6B7280;
        }
        return p;
    }
}
