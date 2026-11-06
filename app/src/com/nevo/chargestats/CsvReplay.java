package com.nevo.chargestats;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Replay CSV-логов ChargeProbe v2.0 (ts,BCU_STATUS,BCU_PARAMS,PDU_STATUS,PDU_PARAMS,...)
 * через ChargeEngine. Debug-импорт сессий без реальной зарядки.
 */
public final class CsvReplay {

    public static int importFile(Db db, File f) throws Exception {
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        ChargeEngine eng = new ChargeEngine();
        int n = 0;
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String line = br.readLine(); // header
            if (line == null || !line.startsWith("ts,")) return 0;
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                try {
                    long ts = fmt.parse(line.substring(0, 19)).getTime();
                    List<String> c = cells(line);
                    Signals.Snap s = Signals.parse(
                            arr(c, 1), arr(c, 2), arr(c, 4), arr(c, 7), arr(c, 8), ts);
                    if (eng.onTick(db, s) != null) n++;
                } catch (Exception ignored) { }
            }
        }
        return n;
    }

    private static List<String> cells(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder(); boolean q = false;
        for (char ch : line.toCharArray()) {
            if (ch == '"') q = !q;
            else if (ch == ',' && !q) { out.add(cur.toString()); cur.setLength(0); }
            else cur.append(ch);
        }
        out.add(cur.toString());
        return out;
    }

    private static int[] arr(List<String> c, int i) {
        if (c.size() <= i) return null;
        String v = c.get(i).trim();
        if (v.isEmpty() || !v.startsWith("[")) return null;
        v = v.substring(1, v.length() - 1);
        String[] p = v.split(",\\s*");
        int[] a = new int[p.length];
        for (int k = 0; k < p.length; k++) a[k] = Integer.parseInt(p[k].trim());
        return a;
    }

    private CsvReplay() {}
}
