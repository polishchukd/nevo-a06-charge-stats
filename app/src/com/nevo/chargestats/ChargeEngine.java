package com.nevo.chargestats;


/**
 * State machine сессий (roadmap C1) на 1 Гц-тикахах Signals.Snap.
 *
 * Правила (верифицированы полями §11/§12):
 *  - старт: chgStatus==1 (BCU[5]) при mode∈{3,6}; подтверждение 2 тика (~2 c);
 *  - старт-условие «plug» не обязателен: chgStatus==1 уже подразумевает пистолет;
 *  - закрытие: chgStatus!=1 дольше CLOSE_S (5 c — DoD ≤5 с);
 *  - stale: heartbeat BCU[25] не меняется STALE_S → закрытие без слияния;
 *  - разрыв тиков > GAP_S (60 c) → закрыть и открыть новую (не сливать);
 *  - сессия с энергией < MIN_E (0.01 кВт·ч) удаляется при закрытии.
 * Энергия: ∫p dt (p — активная сторона: AC→сеть PDU, DC→батарея BCU, как UI).
 */
public class ChargeEngine {

    /** Минимальный интерфейс хранилища для движка (реализует Db). */
    public interface Store {
        long openSession(String type, long ts, double soc);
        void addSample(long sid, Signals.Snap s);
        void updateLive(long sid, double energy, double max, double socEnd);
        void closeSession(long id, long endTs, double energy, double avg, double max,
                          double socEnd, double addedKm);
        void deleteSession(long id);
    }

    public static final int CLOSE_S = 5, STALE_S = 30, GAP_S = 60, MIN_E_KWH_CENTI = 1;
    public static final int CONFIRM_S = 2;

    private long sid = -1;
    private String type;
    private long startTs, lastTs, lastTickWall;
    private double energy, maxKw, sumKw;
    private int ticks, socStartX100, socEndX100;
    private double addedKm;        // дельта REMAIN[12] внутри сессии
    private double baseKm;         // REMAIN[12] на старте (счётчик сбрасывается при подключении)
    private int pendingStart;      // тиков подряд с chgStatus=1
    private long condSince;        // когда chgStatus!=1 (0 = сейчас идёт)
    private int lastHeartbeat = -1; private long hbChangeTs;
    private long lastVhalTick;

    public long sessionId() { return sid; }
    public boolean active() { return sid != -1; }

    /** Принудительно закрыть текущую сессию (конец CSV-импорта). */
    public void forceClose(ChargeEngine.Store db) { close(db, false); }

    /** Тик 1 Гц. Возвращает описание перехода или null. */
    public String onTick(ChargeEngine.Store db, Signals.Snap s) {
        String ev = null;
        long now = s.ts;

        // stale-защита: heartbeat VHAL
        if (s.heartbeat != lastHeartbeat) { lastHeartbeat = s.heartbeat; hbChangeTs = now; }
        boolean stale = (now - hbChangeTs) > STALE_S * 1000L;

        // разрыв тиков логгера
        if (sid != -1 && lastTickWall > 0 && (now - lastTickWall) > GAP_S * 1000L) {
            ev = "gap>60s → разрыв сессии";
            close(db, true);
            if (chargingNow(s)) { start(db, s); pendingStart = CONFIRM_S - 1; }
        }
        lastTickWall = now;

        boolean charging = chargingNow(s);

        if (sid == -1) {
            if (stale) { pendingStart = 0; return ev; }
            if (charging) {
                pendingStart++;
                if (pendingStart >= CONFIRM_S) {
                    start(db, s);
                    ev = "start " + type;
                }
            } else pendingStart = 0;
            condSince = 0;
        } else {
            if (stale) { ev = "stale → close"; close(db, true); return ev; }
            if (charging) {
                condSince = 0;
                accumulate(db, s, now);
            } else {
                if (condSince == 0) condSince = now;
                accumulate(db, s, now); // последние тики тоже в积分 (U,I≈0)
                if (now - condSince >= CLOSE_S * 1000L) {
                    ev = "close " + type;
                    close(db, false);
                }
            }
        }
        return ev;
    }

    private static boolean chargingNow(Signals.Snap s) {
        return s.chgStatus == 1 && (s.mode == 3 || s.mode == 6);
    }

    private void start(ChargeEngine.Store db, Signals.Snap s) {
        type = s.mode == 6 ? "DC" : "AC";
        sid = db.openSession(type, s.ts, s.socPct);
        startTs = s.ts; lastTs = s.ts;
        energy = 0; maxKw = 0; sumKw = 0; ticks = 0;
        socStartX100 = (int) Math.round(s.socPct * 100); socEndX100 = socStartX100;
        addedKm = 0; baseKm = s.addedKm; condSince = 0; pendingStart = 0;
    }

    private void accumulate(ChargeEngine.Store db, Signals.Snap s, long now) {
        long dtMs = now - lastTs;
        if (dtMs > 0 && dtMs < GAP_S * 1000L) energy += s.pKw * dtMs / 3_600_000.0;
        if (s.pKw > maxKw) maxKw = s.pKw;
        sumKw += s.pKw; ticks++;
        lastTs = now;
        socEndX100 = (int) Math.round(s.socPct * 100);
        double km = s.addedKm - baseKm; if (km > addedKm) addedKm = Math.max(0, km);
        db.addSample(sid, s);
        if (ticks % 5 == 0) db.updateLive(sid, energy, maxKw, socEndX100 / 100.0);
    }

    private void close(ChargeEngine.Store db, boolean byProblem) {
        if (sid == -1) return;
        if (energy * 100 < MIN_E_KWH_CENTI) {
            db.deleteSession(sid);   // нулевая сессия удаляется
        } else {
            double avg = ticks > 0 ? sumKw / ticks : 0;
            db.closeSession(sid, lastTs, energy, avg, maxKw, socEndX100 / 100.0, addedKm);
        }
        sid = -1; type = null;
    }
}
