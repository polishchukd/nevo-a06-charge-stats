package com.nevo.chargestats;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Отладочный broadcast: am broadcast -n com.nevo.chargestats/.DebugReceiver
 *   --es ac 4.4 --es dc 9          — тарифы
 *   --ez demo_live true            — открыть демо live-сессию AC
 *   --ez demo_live false           — удалить все open-сессии
 * Для автотестов без UI.
 */
public class DebugReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent i) {
        Db db = new Db(c);
        String ac = i.getStringExtra("ac"), dc = i.getStringExtra("dc");
        boolean did = false;
        if (ac != null && ac.length() > 0) { db.setPrice("AC", Double.parseDouble(ac)); did = true; }
        if (dc != null && dc.length() > 0) { db.setPrice("DC", Double.parseDouble(dc)); did = true; }
        if (did) db.backfillCosts();
        if (i.hasExtra("demo_live")) {
            boolean on = i.getBooleanExtra("demo_live", false);
            if (on) {
                long sid = db.openSession("AC", System.currentTimeMillis() - 3 * 60000, 55.0);
                android.content.ContentValues cv = new android.content.ContentValues();
                cv.put("energy_kwh", 1.24); cv.put("max_kw", 2.9); cv.put("soc_end", 56.8);
                db.updateSession(sid, cv);
            } else {
                db.getWritableDatabase().delete("sessions", "state='open'", null);
            }
            c.sendBroadcast(new Intent(LoggerService.ACTION_TICK));
        }
    }
}
