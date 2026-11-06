package com.nevo.chargestats;

/**
 * Карта сигналов VHAL и парсинг среза. Все адреса/масштабы верифицированы
 * живыми AC- и DC-сессиями (ANALYSIS.md §11/§12). Только чтение.
 */
public final class Signals {
    // propId
    public static final int BCU_STATUS   = 0x41930600; // [4]mode [5]chgStatus [6]AC [7]DC | [25]heartbeat 0..2
    public static final int BCU_PARAMS   = 0x41930700; // [0]U pack ×0.1 [1]I |x0.1-1000| [5]remMin [6]t℃+(-40? no ×0.1)
    public static final int PDU_PARAMS   = 0x41930900; // [0..2]U фаза×1 [3..5]I ×0.1
    public static final int PTLAMP       = 0x41978800; // [8]plug [34]SOC×100
    public static final int REMAIN_MILE  = 0x41205500; // [7]доп.запас км [12]накопл. ×0.01

    public static final class Snap {
        public long ts;
        public int mode;          // BCU[4]: 3=AC, 6=DC, 0/8=нет
        public int chgStatus;     // BCU[5]: 1=идёт, 3=подключено/остановлена
        public int acFlag, dcFlag;
        public int heartbeat;     // 0..2, цикл ~10с — признак живости VHAL
        public double uPack, iBat, pDcKw;
        public double pAcKw;      // Σ Uф×Iф /1000
        public double pKw;        // активная сторона: DC→батарея, AC→сеть (как UI)
        public double socPct;     // PTLAMP[34]/100.0
        public boolean plug;      // PTLAMP[8]==1
        public double addedKm;    // REMAIN[12]×0.01
        public int remMin;        // BCU[5 params], 8191=invalid
        public boolean valid;
    }

    public static Snap read(Vhal v) throws Exception {
        return parse(v.readIntArray(BCU_STATUS), v.readIntArray(BCU_PARAMS),
                v.readIntArray(PDU_PARAMS), v.readIntArray(PTLAMP),
                v.readIntArray(REMAIN_MILE), System.currentTimeMillis());
    }

    /** Парсинг из сырых массивов (тестируется на JVM replay-ем полевых логов). */
    public static Snap parse(int[] bcu, int[] bcp, int[] pdu, int[] ptl, int[] rem, long ts) {
        Snap s = new Snap();
        s.ts = ts;
        if (bcu == null || bcu.length < 26) return s;
        s.mode = bcu[4]; s.chgStatus = bcu[5]; s.acFlag = bcu[6]; s.dcFlag = bcu[7];
        s.heartbeat = bcu[25];
        if (ptl != null && ptl.length > 34) { s.plug = ptl[8] == 1; s.socPct = ptl[34] / 100.0; }
        if (bcp != null && bcp.length > 6) {
            s.uPack = bcp[0] * 0.1;
            s.iBat = Math.abs(bcp[1] * 0.1 - 1000.0);
            s.remMin = bcp[5] == 8191 ? -1 : bcp[5];
        }
        if (pdu != null && pdu.length > 5) {
            double p = 0;
            for (int i = 0; i < 3; i++) p += pdu[i] * pdu[3 + i] * 0.1;
            s.pAcKw = p / 1000.0;
        }
        s.pDcKw = s.uPack * s.iBat / 1000.0;
        s.pKw = (s.dcFlag == 1) ? s.pDcKw : s.pAcKw;
        if (rem != null && rem.length > 12) s.addedKm = rem[12] * 0.01;
        s.valid = true;
        return s;
    }

    private Signals() {}
}
