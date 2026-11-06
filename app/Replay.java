import com.nevo.chargestats.ChargeEngine;
import com.nevo.chargestats.Signals;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Replay полевых логов ChargeProbe через ChargeEngine без Android.
 * Store — in-memory. Ожидаем: AC-лог → 1 сессия AC (с учётом перетыка — 2),
 * DC-лог → 1 сессия DC, энергия DC ~8.2 кВт·ч, макс ~19-20 кВт.
 */
public class Replay {
    static class Mem implements ChargeEngine.Store {
        class S { String type; long st, en; double e, avg, max, s0, s1, km; boolean open; }
        List<S> list = new ArrayList<>();
        public long openSession(String type, long ts, double soc) {
            S s = new S(); s.type = type; s.st = ts; s.s0 = soc; s.open = true;
            list.add(s); return list.size();
        }
        public void addSample(long id, Signals.Snap s) { }
        public void closeSession(long id, long en, double e, double avg, double max,
                                 double s1, double km) {
            S s = list.get((int) id - 1); s.en = en; s.e = e; s.avg = avg;
            s.max = max; s.s1 = s1; s.km = km; s.open = false;
        }
        public void deleteSession(long id) { list.set((int) id - 1, null); }
        void dump(String tag) {
            System.out.println("== " + tag);
            for (S s : list) if (s != null) System.out.printf(
                "  %s %s->%s  %.2f kWh  avg %.1f  max %.1f kW  SOC %.2f->%.2f  +%.2f km%n",
                s.type, new java.util.Date(s.st), s.en == 0 ? "OPEN" : new java.util.Date(s.en),
                s.e, s.avg, s.max, s.s0, s.s1, s.km);
        }
    }

    // парсер CSV ChargeProbe v2.0: ts,BCU_STATUS,BCU_PARAMS,PDU_STATUS,PDU_PARAMS,...
    static int[][] parseArrays(String line) {
        List<String> cells = new ArrayList<>();
        StringBuilder cur = new StringBuilder(); boolean q = false;
        for (char c : line.toCharArray()) {
            if (c == '"') q = !q;
            else if (c == ',' && !q) { cells.add(cur.toString()); cur.setLength(0); }
            else cur.append(c);
        }
        cells.add(cur.toString());
        int[][] out = new int[5][];
        for (int k = 0; k < 5; k++) {
            String v = cells.get(k + 1).trim();
            if (v.isEmpty() || !v.startsWith("[")) { out[k] = null; continue; }
            v = v.substring(1, v.length() - 1);
            String[] p = v.split(",\\s*");
            int[] a = new int[p.length];
            for (int i = 0; i < p.length; i++) a[i] = Integer.parseInt(p[i].trim());
            out[k] = a;
        }
        return out; // [bcu, bcp, pduStatus, pdu, rest]
    }

    public static void main(String[] args) throws Exception {
        java.text.SimpleDateFormat fmt = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        for (String path : args) {
            ChargeEngine eng = new ChargeEngine();
            Mem mem = new Mem();
            try (BufferedReader br = new BufferedReader(new FileReader(path))) {
                br.readLine();
                String line;
                while ((line = br.readLine()) != null) {
                    if (line.trim().isEmpty()) continue;
                    try {
                        long ts = fmt.parse(line.substring(0, 19)).getTime();
                        int[][] a = parseArrays(line);
                        // колонки: BCU_STATUS, BCU_PARAMS, PDU_STATUS, PDU_PARAMS, VCU_RUN_STATE,
                        // VCU_STATUS, PTLAMP, REMAINING_MILEAGE (индексы 1,2,4,7)
                        Signals.Snap s = Signals.parse(a[0], a[1], a[3],
                                arr(line, 7), arr(line, 8), ts);
                        eng.onTick(mem, s);
                    } catch (Exception e) { /* строка-исключение */ }
                }
            }
            mem.dump(path);
        }
    }

    static int[] arr(String line, int col) {
        List<String> cells = new ArrayList<>();
        StringBuilder cur = new StringBuilder(); boolean q = false;
        for (char c : line.toCharArray()) {
            if (c == '"') q = !q;
            else if (c == ',' && !q) { cells.add(cur.toString()); cur.setLength(0); }
            else cur.append(c);
        }
        cells.add(cur.toString());
        String v = cells.get(col).trim();
        if (v.isEmpty() || !v.startsWith("[")) return null;
        v = v.substring(1, v.length() - 1);
        String[] p = v.split(",\\s*");
        int[] a = new int[p.length];
        for (int i = 0; i < p.length; i++) a[i] = Integer.parseInt(p[i].trim());
        return a;
    }
}
