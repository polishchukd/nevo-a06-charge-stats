package com.nevo.chargestats;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.List;

/**
 * SQLite: sessions / samples / tariffs(историчные) / settings.
 * Стоимость закрытой сессии — снапшот тарифа на момент окончания (roadmap C1).
 */
public class Db extends SQLiteOpenHelper implements ChargeEngine.Store {
    public static final String DB = "chargestats.db";
    private static final int V = 1;

    public Db(Context c) { super(c, DB, null, V); }

    @Override public void onCreate(SQLiteDatabase d) {
        d.execSQL("CREATE TABLE sessions(" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "type TEXT NOT NULL," +               // AC|DC
                "start_ts INTEGER NOT NULL," +
                "end_ts INTEGER," +
                "energy_kwh REAL DEFAULT 0," +
                "avg_kw REAL, max_kw REAL," +
                "soc_start REAL, soc_end REAL," +
                "added_km REAL DEFAULT 0," +
                "cost REAL, cost_filled INTEGER DEFAULT 0," + // 1 = стоимость проставлена (снапшот)
                "note TEXT, state TEXT DEFAULT 'open')");
        d.execSQL("CREATE TABLE samples(" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "session_id INTEGER NOT NULL," +
                "ts INTEGER NOT NULL, p_kw REAL, u REAL, i REAL, soc REAL," +
                "FOREIGN KEY(session_id) REFERENCES sessions(id))");
        d.execSQL("CREATE TABLE tariffs(" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "type TEXT NOT NULL," +               // AC|DC
                "price REAL NOT NULL," +              // за кВт·ч
                "valid_from INTEGER NOT NULL)");
        d.execSQL("CREATE TABLE settings(key TEXT PRIMARY KEY, value TEXT)");
        d.execSQL("CREATE INDEX idx_samples_s ON samples(session_id)");
    }
    @Override public void onUpgrade(SQLiteDatabase d, int a, int b) { }
    @Override public void onOpen(SQLiteDatabase d) {
        super.onOpen(d);
        try { d.execSQL("UPDATE sessions SET end_ts=NULL WHERE state='open' AND end_ts IS NOT NULL"); }
        catch (Exception ignored) { }
    }

    // ---- sessions
    public long openSession(String type, long ts, double soc) {
        ContentValues cv = new ContentValues();
        cv.put("type", type); cv.put("start_ts", ts); cv.put("soc_start", soc);
        return getWritableDatabase().insert("sessions", null, cv);
    }
    public void updateSession(long id, ContentValues cv) {
        getWritableDatabase().update("sessions", cv, "id=?", new String[]{"" + id});
    }
    public void updateLive(long id, double energy, double max, double socEnd) {
        ContentValues cv = new ContentValues();
        cv.put("energy_kwh", energy); cv.put("max_kw", max); cv.put("soc_end", socEnd);
        getWritableDatabase().update("sessions", cv, "id=?", new String[]{"" + id});
    }
    public void closeSession(long id, long endTs, double energy, double avg, double max,
                             double socEnd, double addedKm) {
        ContentValues cv = new ContentValues();
        cv.put("end_ts", endTs); cv.put("energy_kwh", energy);
        cv.put("avg_kw", avg); cv.put("max_kw", max);
        cv.put("soc_end", socEnd); cv.put("added_km", addedKm);
        cv.put("state", "closed");
        Double price = priceAt(getWritableDatabase(), typeOf(id), endTs);
        if (price != null) { cv.put("cost", energy * price); cv.put("cost_filled", 1); }
        getWritableDatabase().update("sessions", cv, "id=?", new String[]{"" + id});
    }
    public void deleteSession(long id) {
        SQLiteDatabase w = getWritableDatabase();
        w.delete("samples", "session_id=?", new String[]{"" + id});
        w.delete("sessions", "id=?", new String[]{"" + id});
    }
    public String typeOf(long id) {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT type FROM sessions WHERE id=?", new String[]{"" + id});
        try { return c.moveToFirst() ? c.getString(0) : null; } finally { c.close(); }
    }
    /** поля сессии строкой через '|' (null → "0") */
    public String[] getSession(long id) {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT id,type,start_ts,end_ts,energy_kwh,max_kw,soc_start,soc_end,cost " +
                "FROM sessions WHERE id=?", new String[]{"" + id});
        try {
            if (!c.moveToFirst()) return null;
            return new String[]{ c.getLong(0)+"", c.getString(1), c.getLong(2)+"",
                (c.isNull(3) ? 0 : c.getLong(3))+"", c.getDouble(4)+"", c.getDouble(5)+"",
                c.getDouble(6)+"", (c.isNull(7) ? 0 : c.getDouble(7))+"",
                (c.isNull(8) ? -1 : c.getDouble(8))+"" };
        } finally { c.close(); }
    }
    public List<String> listSessions(int limit) {
        List<String> out = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT id,type,start_ts,end_ts,energy_kwh,max_kw,soc_start,soc_end,cost,state " +
                "FROM sessions ORDER BY start_ts DESC LIMIT ?",
                new String[]{"" + limit});
        while (c.moveToNext()) {
            out.add(c.getLong(0) + "|" + c.getString(1) + "|" + c.getLong(2) + "|" +
                    (c.isNull(3) ? 0 : c.getLong(3)) + "|" + c.getDouble(4) + "|" +
                    c.getDouble(5) + "|" + c.getDouble(6) + "|" +
                    (c.isNull(7) ? 0 : c.getDouble(7)) + "|" +
                    (c.isNull(8) ? -1 : c.getDouble(8)) + "|" +
                    (c.isNull(9) ? "closed" : c.getString(9)));
        }
        c.close();
        return out;
    }
    /** Дописать цену только там, где её никогда не было (cost_filled=0). */
    public void backfillCosts() {
        SQLiteDatabase w = getWritableDatabase();
        Cursor c = w.rawQuery("SELECT id,type,end_ts,energy_kwh FROM sessions " +
                "WHERE end_ts IS NOT NULL AND cost_filled=0", null);
        ArrayList<long[]> ids = new ArrayList<>(); ArrayList<double[]> args = new ArrayList<>();
        ArrayList<String> types = new ArrayList<>();
        while (c.moveToNext()) {
            ids.add(new long[]{c.getLong(0)});
            args.add(new double[]{c.getLong(2), c.getDouble(3)});
            types.add(c.getString(1));
        }
        c.close();
        for (int i = 0; i < ids.size(); i++) {
            // по roadmap: добор «текущей ценой» при первом появлении тарифа
            Double p = priceAt(w, types.get(i), System.currentTimeMillis());
            if (p != null) {
                ContentValues cv = new ContentValues();
                cv.put("cost", args.get(i)[1] * p); cv.put("cost_filled", 1);
                w.update("sessions", cv, "id=" + ids.get(i)[0], null);
            }
        }
    }

    // ---- samples
    public void addSample(long sid, Signals.Snap s) {
        ContentValues cv = new ContentValues();
        cv.put("session_id", sid); cv.put("ts", s.ts); cv.put("p_kw", s.pKw);
        cv.put("u", s.uPack); cv.put("i", s.iBat); cv.put("soc", s.socPct);
        getWritableDatabase().insert("samples", null, cv);
    }

    // ---- tariffs (историчные: вставка новой цены не меняет старые цены)
    public void setPrice(String type, double price) {
        ContentValues cv = new ContentValues();
        cv.put("type", type); cv.put("price", price); cv.put("valid_from", System.currentTimeMillis());
        getWritableDatabase().insert("tariffs", null, cv);
    }
    public Double price(String type) {
        return priceAt(getReadableDatabase(), type, System.currentTimeMillis());
    }
    private static Double priceAt(SQLiteDatabase d, String type, long ts) {
        if (type == null) return null;
        Cursor c = d.rawQuery("SELECT price FROM tariffs WHERE type=? AND valid_from<=? " +
                "ORDER BY valid_from DESC LIMIT 1", new String[]{type, "" + ts});
        try { return c.moveToFirst() ? c.getDouble(0) : null; } finally { c.close(); }
    }

    // ---- settings
    public String get(String k, String def) {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT value FROM settings WHERE key=?", new String[]{k});
        try { return c.moveToFirst() ? c.getString(0) : def; } finally { c.close(); }
    }
    public void put(String k, String v) {
        ContentValues cv = new ContentValues(); cv.put("key", k); cv.put("value", v);
        getWritableDatabase().insertWithOnConflict("settings", null, cv,
                SQLiteDatabase.CONFLICT_REPLACE);
    }
}
