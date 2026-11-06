package com.nevo.chargestats;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.widget.Toast;

import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Foreground-сервис: 1 Гц опрос VHAL → ChargeEngine → SQLite; сырые тики — в CSV
 * (папка из SAF persistable grant, fallback — Download/ChargeStats).
 */
public class LoggerService extends Service {
    public static final String ACTION_TICK = "com.nevo.chargestats.TICK";
    public static final String EXTRA_STATUS = "status";
    public static final String CHANNEL = "chargestats";
    public static volatile String lastStatus = "-";
    private static final AtomicBoolean running = new AtomicBoolean(false);

    private HandlerThread thread;
    private Handler work;
    private Vhal vhal;
    private Db db;
    private ChargeEngine engine;
    private Writer csv;
    private String csvName;
    private final SimpleDateFormat ts = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);

    public static boolean isRunning() { return running.get(); }

    @Override public IBinder onBind(Intent i) { return null; }

    @Override public void onCreate() {
        super.onCreate();
        db = new Db(this);
        db.backfillCosts();
        engine = new ChargeEngine();
        thread = new HandlerThread("logger");
        thread.start();
        work = new Handler(thread.getLooper());
        startNotif("запуск…");
        work.post(new Runnable(){public void run(){loop();}});
        work.postDelayed(new Runnable(){public void run(){sweepStale();}}, 10000);
    }

    /** Убрать «зависшие» открытые сессии после перезапуска сервиса. */
    private void sweepStale() {
        try {
            android.database.Cursor c = db.getReadableDatabase().rawQuery(
                    "SELECT id, start_ts FROM sessions WHERE state='open'", null);
            long now = System.currentTimeMillis();
            java.util.ArrayList<long[]> drop = new java.util.ArrayList<>();
            while (c.moveToNext()) {
                if (now - c.getLong(1) > 300000) drop.add(new long[]{c.getLong(0)});
            }
            c.close();
            for (long[] d : drop) db.deleteSession(d[0]);
        } catch (Exception ignored) { }
    }

    @Override public int onStartCommand(Intent i, int f, int s) {
        startNotif(lastStatus);
        return START_STICKY;
    }

    @Override public void onDestroy() {
        running.set(false);
        if (thread != null) thread.quitSafely();
        closeCsv();
        super.onDestroy();
    }

    private void loop() {
        running.set(true);
        try { vhal = new Vhal(); vhal.connect(this); }
        catch (Throwable t) { setStatus("VHAL недоступен: " + t.getMessage()); return; }
        openCsv();
        long sinceUi = 0;
        while (running.get()) {
            long t0 = System.currentTimeMillis();
            try {
                Signals.Snap s = Signals.read(vhal);
                if (s.valid) {
                    String ev = engine.onTick(db, s);
                    writeCsv(s);
                    if (ev != null) {
                        setStatus(ev);
                        db.backfillCosts();
                    } else if (System.currentTimeMillis() - sinceUi > 5000) {
                        sinceUi = System.currentTimeMillis();
                        setStatus(engine.active()
                                ? String.format(Locale.US, "зарядка: %.1f кВт, SOC %.2f%%", s.pKw, s.socPct)
                                : String.format(Locale.US, "ожидание, SOC %.2f%%", s.socPct));
                    }
                }
            } catch (Throwable t) {
                setStatus("tick err " + t.getClass().getSimpleName());
            }
            long dt = System.currentTimeMillis() - t0;
            try { Thread.sleep(Math.max(50, 1000 - dt)); } catch (InterruptedException e) { break; }
        }
        closeCsv();
        running.set(false);
    }

    private void setStatus(final String s) {
        lastStatus = s;
        startNotif(s);
        Intent i = new Intent(ACTION_TICK).putExtra(EXTRA_STATUS, s);
        sendBroadcast(i);
    }

    private void startNotif(String text) {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "ChargeStats",
                    NotificationManager.IMPORTANCE_LOW);
            nm.createNotificationChannel(ch);
        }
        Intent i = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, i,
                Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
        Notification n = new Notification.Builder(this, CHANNEL)
                .setContentTitle("ChargeStats")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_menu_manage)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
        startForeground(1, n);
    }

    // ---- CSV: SAF tree → Download/ChargeStats fallback
    private void openCsv() {
        try {
            String tree = db.get("csv_tree", "");
            if (!tree.isEmpty()) {
                Uri uri = Uri.parse(tree);
                Uri doc = DocumentsContract.buildDocumentUriUsingTree(uri,
                        DocumentsContract.getTreeDocumentId(uri));
                try (ParcelFileDescriptor p = getContentResolver()
                        .openFileDescriptor(doc, "wt")) { /* проверка записи */ }
                Uri file = DocumentsContract.createDocument(getContentResolver(), doc,
                        "text/csv", csvName());
                if (file != null) { csv = new OutputStreamWriter(
                        getContentResolver().openOutputStream(file), "UTF-8"); return; }
            }
        } catch (Throwable ignored) { }
        try {
            java.io.File dir = new java.io.File(
                    android.os.Environment.getExternalStoragePublicDirectory(
                            android.os.Environment.DIRECTORY_DOWNLOADS), "ChargeStats");
            if (!dir.exists()) dir.mkdirs();
            java.io.File f = new java.io.File(dir, csvName());
            csv = new OutputStreamWriter(new FileOutputStream(f), "UTF-8");
        } catch (Throwable t) { csv = null; }
    }

    private String csvName() {
        csvName = "raw_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
                .format(new Date()) + ".csv";
        return csvName;
    }

    private void writeCsv(Signals.Snap s) {
        if (csv == null) return;
        try {
            csv.write(String.format(Locale.US,
                    "%s,%d,%d,%d,%d,%.4f,%.2f,%.2f,%.2f,%.2f%n",
                    ts.format(new Date(s.ts)), s.mode, s.chgStatus, s.acFlag, s.dcFlag,
                    s.socPct, s.pKw, s.uPack, s.iBat, s.addedKm));
            csv.flush();
        } catch (Throwable ignored) { }
    }

    private void closeCsv() {
        if (csv != null) { try { csv.close(); } catch (Throwable ignored) { } csv = null; }
    }
}
