# ANALYSIS.md — Реверс штатного «Энергоцентра» (com.incall.apps.energy)


Источник: jadx-декомпиляция `in/com.incall.apps.energy.apk` (4 dex, ~12k классов) + apktool (smali/res).

---

## 1. Вывод об архитектуре доступа (главный вопрос этапа A)

**Energy читает бортовые данные стандартным AOSP VHAL API — `android.car.hardware.property.CarPropertyManager`.** Никакого вендорского прокси-сервиса или собственного AIDL для ЧТЕНИЯ сигналов нет.

Доказательства:

- Инициализация: `com/incall/apps/casdkmanager/CaSdkManager.java:622-633`
  ```java
  Car.createCar(context, new Handler(handlerThread.getLooper()), -1L, mCarServiceLifecycleListener);
  // в CarServiceLifecycleListener:
  CarPropertyService.INSTANCE.init((CarPropertyManager) car.getCarManager("property"));
  ```
- Обёртка: `com/incall/apps/vehiclesetting_service/base/CarPropertyService.kt` — синглтон, хранит
  `CarPropertyManager`, методы `getIntArrayProperty(id, areaId)`, `registerCallback(property, cb, rateHz)`
  (默认 1.0 Hz), `setPropertyRes(int.class, id, areaId, v)` (пишет только в установочные свойства).
- Манифест подтверждает: `android:sharedUserId="android.uid.system"` (AndroidManifest.xml, apktool),
  разрешение `android.car.permission.ACCESS_CAR_PROJECTION_STATUS` (прочие car-разрешения не объявлены —
  доступ к propertyGiving обеспечивается системным UID).

### Что это значит для ChargeStats
- Механизм: `Car.createCar()` → `car.getCarManager(Car.PROPERTY_SERVICE)` → `CarPropertyManager`
  (`getProperty/getIntArrayProperty` + `registerCallback` со скоростью 1 Гц).
- Требования к нашему приложению: системная подпись (platform key) и `sharedUserId="android.uid.system"`
  (либо иной путь выдачи car permissions на ГУ — проверяется на этапе B: у приложения в системном UID
  права inherit'ятся, отдельный CarPermission не нужен).
- Только чтение (см. §7 роадмапа): вызываем `getProperty`/`registerCallback`, НЕ трогаем `setProperty*`.

## 2. Манифест Energy (для понимания, что копировать НЕ надо)

- `sharedUserId="android.uid.system"`, package `com.incall.apps.energy`, compileSdk 31.
- Разрешения (полный список в apktool-out/AndroidManifest.xml): WRITE/READ_EXTERNAL_STORAGE (легаси —
  нам ЗАПРЕЩЕНО), INTERACT_ACROSS_USERS, FORCE_STOP_PACKAGES, INJECT_EVENTS и пр. — избыточно для нас.
- Компоненты: `energy_view.EnergyService` (persistent-сервис-хранилище), `energy_view.activity.MainActivity`,
  `module_energy.ui.home.HomeActivity`, widget `SDA_Widget_EnergyProvider`, `BackupDataReceiver`.
- Settings.* читает (Settings.System/Global — в `EnergyService.java`, `ConfigureModel.java`);
  SystemProperties.set — НЕ найден (grep по SystemProperties.set в energy-view/module/virtualvehicle пуст).
  Писать он умеет только в VHAL через `setPropertyRes` (установки зарядки: ток, уставки SOC, расписание).

## 3. Карта propId (зарядка/батарея)

Пропerty объявлены в двух источниках (одинаковый механический разбор):
- `com/incall/apps/energy_service/page/battery/BatteryCarProperty.kt` (см. d2-метаданные, 46 свойств)
- `com/incall/apps/energy_service/page/energy/EnergyProperty.kt`

Тип почти всех массивных свойств: `Integer[]` (получают через `getIntArrayProperty(id, areaId)`).

### 3.1 Ключевые свойства (пройдены кодом чтения → доказано)

| Свойство (имя) | propId | areaId | Что в value[] | Доказательство |
|---|---|---|---|---|
| GET_ENERGY_BCU_STATUS (батарея BCU) | 0x41930600 | 0 | [3] batteryHotState, [4] batChargeMode, [5] batteryChargeStatus, [6] acChargeStatus, [7] dcChargeStatus, [8] heatActivityStatus, [11] batteryHeatMode, [21/22] bcuLostComFltSts, [28] dcOutDischrgSt | DriveModel.java:930-965 |
| GET_ENERGY_BCU_PARAMS_STATUS | 0x41930700 | 0 | [0] totalVoltage (0.1 В), [1] totalCurrent (сырой), [5] chargeRemainTime (мин), [6] appointmentChargeRemainTime, [9] resDischargeAllTime, [14/15/16] max/min/avgTemp, [28] bcuBattRatedAh (А·ч) | DriveModel.java:952-997 |
| GET_ENERGY_PDU_STATUS | 0x41930800 | 0 | [0] dcDcFaultLevel, [1] alarmSts, [2] overTempSts, [3] dcDcStatus, [4] **chargeMode** (0/1), [8] dcAcStatus | DriveModel.java:886-920 |
| GET_ENERGY_PDU_PARAMS_STATUS | 0x41930900 | 0 | [0..2] AC-напряжения (3 фазы/канала), [3..5] токи — сумма = realTimeCurrent (0.1 А); по PDU-массиву: [9] outPutCurrent, [10] dcAcOutPutU | DriveModel.java:860-885 |
| GET_ENERGY_VCU_RUN_STATE (VCU) | 0x41931000 | 0 | [1] chargeConnectStatus (сырой), [2] sysEnergyFlow, [4]==1 → снапшот BMS в БД, [13] vcuChrglockSts (1=заблокирован), [15..19] REEV-потоки | EnergyRecycleModel.java:263-307 |
| GET_ENERGY_AC_SIGNAL_CHARGE_STATUS | 0x41307400 | 0 | int[] AC-сигналы (BatteryService.java:75) | BatteryService.java:75 |
| GET_EV_CHARGE_PORT_OPEN | 0x40117400 | 0 | состояние крышки/порта charge.chargePortStatus | BatteryCarProperty + BatteryBean |
| GET_CURRENT_GEAR | 0x40801300 | 0 | drive.gearLevel | soaValueToUIValue |
| GET_VEHICLE_READY_STATE | 0x40127500 | 0 | drive.vehicleReadyState | BatteryBean |
| GET_PERF_VEHICLE_SPEED | 0x60800600 | 0 | speedValue | DriveModel.java:998+ |
| GET_ENERGY_REMAINING_MILEAGE_STATE | 0x41205500 | 0 | energyRecycle.chargeAddKm (прибавка запаса хода, масштаб ×0.01 км) | soaValueToUIValue: chargeAddKm → ×0.01 |
| GET_ENERGY_PTLAMP_STATUS | 0x41978800 | 0 | [2] heatStatusFeedBack, [4..5] chrgTipInfo, [12] dcAcCharTip, [25] remainingElectric (SOC %), [31] vcuTmsPtcLimTextInfo | DriveModel.java:820-855 |
| GET_ENERGY_VCU_STATUS (energyIndicate) | 0x41931100 | 0 | энергия потока, UI = raw−100 | soaValueToUIValue (EnergyRecycleModel:2690+) |
| SET/GET_ENERGY_SOC_LIMIT_VALUE | 0x40802600 | 0 | уставка SOC (control_soc_limit_target) | BatteryCarProperty |
| GET/SET_CONTROL_AC_CHARGE_LIMIT | 0x40832000 (1082139136) | 0 | предел AC-тока (control_ac_charge_limit) | BatteryCarProperty |
| GET_ENERGY_PACK_CHARGE_MODE | 0x40128900 | — | LockCarMod | BatteryCarProperty |

Полный механический список propId (из dex):

```
BatteryCarProperty:
  0x40216100 GET_ENERGY_OPERATION_MODE      0x41319400 GET/SET_CHARGE_SWITCH
  0x41306C00 GET/SET_PARK_CHARGE_SWITCH     0x41930800 GET/SET_ENERGY_PDU_STATUS
  0x41318700 SET/UPDATE_ENERGY_HEAT_HELD    0x41930900 GET_ENERGY_PDU_PARAMS_STATUS
  0x41307400 GET_ENERGY_AC_SIGNAL_CHARGE    0x40117400 GET_EV_CHARGE_PORT_OPEN
  0x40128100 SET_ENERGY_DCAC_SWITCH         0x40802600 GET/SET_SOC_LIMIT
  0x41978800 GET_ENERGY_PTLAMP_STATUS       0x41930600 GET_ENERGY_BCU_STATUS
  0x41930700 GET_ENERGY_BCU_PARAMS_STATUS   0x41931000 GET_ENERGY_VCU_RUN_STATE
  0x60800600 GET_PERF_VEHICLE_SPEED         0x40801300 GET_CURRENT_GEAR
  0x40127500 GET_VEHICLE_READY_STATE        0x40801900 GET/SET_CHARGE_CURRENT_DRAW_LIMIT
  0x40904400 SET_CHARGE_AUTO_UNLOCK         0x40205600 SET_CONTROL_CHARGE_AUTO_UNLOCK
  0x40832000 GET/SET_CONTROL_AC_CHARGE_LIMIT(1082139136)  0x41205500 GET_ENERGY_REMAINING_MILEAGE(chargeAddKm)
EnergyProperty:
  0x41312400 GET_ENERGY_VEHICLE_FUEL_STATUS / GET_ENERGY_CNS_ARRAY
  0x41307200 GET_ENERGY_REEV_OPER_STATUS_TOTAL  0x41307000 GET_ENERGY_REEV_OPERATE_STATUS
  0x41931100 GET_ENERGY_VCU_STATUS          0x41205000 GET_ENERGY_CNS_INFO
  0x50100000 GET_TOTAL_MILE_VALUE           0x51100100 GET_REEV_TOTAL_MILE_VALUE
  0x40216500 SET_ENERGY_RESET_MILEAGE_ONE(запрещено нам) 0x41320300 GET_ENERGY_DISTRIBUTION_INFO
```

Ключи-«двойники» «0x12345» у SET_OTHER_BLE_CHRG_SWITCH/ID_DOOR_KEY_SET_STATUS — заглушки вендора (realf
propId подставляется динамически), для нас не нужны (write-функции).

## 4. Формулы и масштабы (главный источник ошибок — собрано из кода)

Функция-конвертер UI: `EnergyRecycleModel.soaValueToUIValue(key, raw)` (EnergyRecycleModel.java:2687-2757):

| Сигнал | Формула UI | Ед. |
|---|---|---|
| drive.totalVoltage | raw × 0.1 | В |
| drive.realTimeCurrent | raw × 0.1 | А |
| drive.totalCurrent | abs(raw × 0.1 − 1000); если raw×0.1−1000 < 0 → |...|, иначе 0 (`getHmiTotalCurrent`, строка 1571) | А |
| dcOutDischrgPwr | raw × 0.1 | кВт (для V2L) |
| energyIndicate | raw − 100 | у.е. поток |
| chargeConnectStatus | raw − 1 | 0=не подключён |
| batChargeStatus | см. §5 (switch i−1 → 0/2/…) | статус |
| chargeAddKm / disChargeReduceKm | raw × 0.01 | км |
| dcDCTemperature | raw − 40 | °C |
| tenKmInstantUse и пр. мгновенный расход | raw × 0.1 − 300 | кВт·ч/100км |
| tenKmAvgUse/twFifKm/fiftyKm | raw × 0.1 − 50 | кВт·ч/100км |
| totalFuelCnse, Vcu*FuelCnse* | raw × 0.1 | л/100км |
| chargeRemainTime | raw==8191 → "−255" (инвалид) | мин |
| batDisChargeMaxLong/ShortTime | raw × 0.5 | мин |
| batHeatRemainTime | ceil(raw/60) | мин |
| remainingElectric (из PTLAMP [25]) | raw напрямую | % SOC |
| bcuBattRatedAh (BCU_PARAMS [28]) | raw | А·ч (судя по сравнению в case 2: power/Ah→3×) |

### 4.1 Мощность на экране (экран батареи) — доказано

`BatteryFragment.chargePower(voltageStr, currentStr)` (BatteryFragment.java:291-330):

```
P[кВт] = U × I / 1000      (U и I — уже отмасштабированные В и А из LiveData)
```

- Для AC (состояние 1,5): U = `realTimeVoltage` (из PDU_PARAMS [0..2]: chargeMode==0 → max, chargeMode==1 → min),
  I = `realTimeCurrent` (= сумма PDU_PARAMS[3..5] × 0.1). Если I < 1 А — показывают U×1/1000 (заглушка).
- Для DC (состояние 2,6): U = `totalVoltage` (BCU_PARAMS[0]×0.1), I = `totalCurrent` (|BCU_PARAMS[1]×0.1−1000|).
- DC-out (V2L): `getdcOutPwr = rawU×0.1 × (rawI×0.1 − 1000) × 0.001` (EnergyRecycleModel.java:1579).
- Формат вывода: 1 знак после точки.

### 4.2 Энергия

Готового «кВт·ч за сессию» в VHAL нет — Energy НЕ отображает полученную энергию прямой величиной;
он показывает chargeAddKm (км) и время. **Вывод: нашу энергию интегрируем сами: E = Σ P(t)·Δt по
мощности из §4.1** (проверить ±5% против «Энергоцентр косвенно»/внешним счётчиком на этапе B).

### 4.3 SOC / запас хода

SOC = PTLAMP[25] (raw напрямую, %). Запас хода — готовый remainingDriverMile изEnergy-репозитория.

## 5. Жизненный цикл зарядки (статусы) — полностью расшифрован

Источник статуса: BCU_STATUS[5] → `batteryChargeStatus` (SOA raw) → UI-статус через
`soaValueToUIValue` и `BatteryUserCase` константы
(`energy_view/usercase/BatteryUserCase.java:21-28`):

| UI chargingStatus | Константа | Расшифровка (по BatteryFragment.updateChargeStatusView, строки 3945-4029) |
|---|---|---|
| −1 | CHARGE_STATUS_NONE | нет данных/пусто — экран скрывает зарядку |
| 0 | CHARGE_STATUS_CQWQ | «充电枪已连接» — пистолет подключён, зарядки нет (ожидание) |
| 1 | CHARGE_STATUS_SLOW | «慢充充电中» — AC/медленная |
| 2 | CHARGE_STATUS_FAST | «快充充电中» — DC/быстрая |
| 3 | CHARGE_STATUS_FULL | «已充满» (показывают текст «枪已连接») |
| 4 | CHARGE_STATUS_END | «充电结束» |
| 5 | CHARGE_STATUS_SLOW_WARM | AC +保温 (подогрев/термоподдержка) |
| 6 | CHARGE_STATUS_FAST_WARM | DC +保温 |
| 7 | (в BatteryUserCase нет) | «等待预约充电» — ожидание резервации (строка 4022) |

SOA-raw → UI (soaValueToUIValue, batChargeStatus): `k = raw − 1; if (k==1) k=2; UI = (k==2)?2:k` —
т.е. сырые 2 и 3 оба дают UI=2 (зона DC/AC разграничивается отдельно через acChargeStatus/dcChargeStatus).
Точное соответствие raw↔физика — проверить logcat'ом на этапе B (кандидат raw=2 = AC→UI-1 идёт другим
путём, см. ниже).

### 5.1 Явный признак AC vs DC — ЕСТЬ

Не эвристика: `Vehicle.drive.batChargeMode` = BCU_STATUS[4], плюс дубли:
`acChargeStatus` = BCU_STATUS[6] (валидный диапазон raw 0..5, EnergyRecycleModel.java:2405),
`dcChargeStatus` = BCU_STATUS[7] (raw 0..4, строка 2414), и `chargeMode` = PDU_STATUS[4].
Порог «быстрая/очень быстрая» в UI: DC ток ≥ 3×Ah-рейтинг (bcuBattRatedAh) → «极速充电中» (строки 3969-3988).

### 5.2 Подключение/отключение пистолета

- `chargeConnectStatus` = VCU_RUN_STATE[1] − 1 (0/1), либо `charge.chargePortStatus` (свойство 0x40117400).
- `vcuChrglockSts` = VCU_RUN_STATE[13] (1 = замок портa активен).
- События NTF_START_CHARGE / NTF_STOP_CHARGE постятся внутренним SOA-слоем (EnergyRecycleModel.java:561)
  — внешний мир может их не видеть; для нас первичны значения VHAL, а не широковещания.

### 5.3 Протухание данных

- Колбэки VHAL приходят на registerCallback(..., 1.0 Hz) при изменении значения. При отваливании VHAL
  ServiceManager getter вернёт null (все геттеры обёрнуты `manager != null ? ... : null`).
- Признаков «stale-флагов» в самих массивах не найдено → наш детектор свежести: собственный watchdog
  (нет onChange N сек → считать данные устаревшими; пороги — на этапе B).
- chargeRemainTime == 8191 — встроенный invalid-маркер вендора (пример того, что отдельные поля имеют
  явные «мусорные» значения; учитывать при фильтрации).

## 6. Схема хранения Energy (для сравнения архитектуры)

- SOA-кэш значений: Room `VehicleDb` (БД "vehicle", `com/incall/vehiclemodel/room/VehicleDb.java:322`,
  ~120 миграций) + in-memory `BaseModel.cachedIntMap`. Energy-сессий как строк истории НЕ хранит —
  хранит текущие значения + статистику энергопотребления (`PersistentDb energyCurve`, 500 точек).
- Вывод для нас: история сессий — целиком наша ответственность (SQLite: sessions/samples/tariffs, §C1).

## 7. Итоговая таблица «экран → источник → формула» (метрики экрана батареи)

| Что видит пользователь | Источник propId/индекс | Формула | Ед. |
|---|---|---|---|
| Статус зарядки (текст) | 0x41930600[5] (+[6],[7],[4]) | §5 | — |
| Напряжение (AC) | 0x41930900[0..2] | ×0.1, max/min по chargeMode(0x41930800[4]) | В |
| Ток (AC) | 0x41930900[3..5] | (Σ)×0.1 | А |
| Напряжение (DC) | 0x41930700[0] | ×0.1 | В |
| Ток (DC) | 0x41930700[1] | abs(×0.1−1000) | А |
| Мощность | произведение U×I | /1000 | кВт |
| SOC | 0x41978800[25] | raw | % |
| Осталось времени | 0x41930700[5] | raw, 8191=invalid | мин |
| Добавленный запас | 0x41205500 | ×0.01 | км |
| AC/DC тип | 0x41930600[4]/[6]/[7] | явный признак | — |
| Пистолет подключён | 0x41931000[1]−1 | | 0/1 |

## 8. Открытые вопросы → план этапа B (живая ГУ)

1. Точное соответствие raw batChargeStatus ↔ AC/DC (гипотеза: 1=connected,2=AC,3=DC,4=full,5=end,6=warm).
   Метод: логировать 0x41930600 в реальных AC- и DC-сессиях (adb logcat теги DriveModel/EnergyRecycleModel).
2. Реальные unit/масштабы PDU_PARAMS[0..2]: 3 канала — это фазы AC (220В×3?) или DC-шина+бортовая сеть.
   Метод: реальная AC-сессия (220В/32А ≈ 7.0кВт) — зафиксировать сырые значения, сравнить с экраном.
3. Подтвердить, что приложению в sharedUserId=system доступны те же 8 propId (dumpsys car_service | grep prop).
4. Частота/дрожание realTimeCurrent при 7 кВт AC — выбрать окно сглаживания (10 с среднее?).
5. Поведение totalCurrent («0 при разряде») — убедиться, что знак-смещение −1000 стабильно.
6. Проверить «контрольный вопрос этапа A»: предсказать экранные цифры через минуту после старта
   сессии: P=U×I/1000, SOC рост на Δ≈P·t/(Ah·U_nom/100)… записать и сверить на машине.

## 9. Требования к ChargeStats, следующие из реверса (минимум по §C1)

- minSdk 29, `sharedUserId="android.uid.system"`, platform-подпись; никаких WRITE_EXTERNAL_STORAGE/legacy.
- Разрешения в манифесте: только `android.car.permission.*` при необходимости + FOREGROUND_SERVICE;
  на ГУ (Android 14) обязательна foreground-service type + POST_NOTIFICATIONS.
- Foreground-сервис: Car.createCar в onCreate сервиса; подписка на 0x41930600, 0x41930700, 0x41930800,
  0x41930900, 0x41931000, 0x41978800 (1 Hz); SQLite (sessions/samples/tariffs); SAF для CSV.
- FSM: NONE → (chargeConnectStatus=1) WAITING → (chargingStatus∈{1,2,5,6}) CHARGING → (стоп-пороги)
  closing; DC/AC из явного признака §5.1; защита от stale/слияний по §5.3.
- После каждой сборки с изменённым манифестом — проверка п.10 DoD (ГУ/кластер/Энергоцентр живы).

## 10. Дизайн (главная) — замеры из референсов

main_light.png: фон #eef2f7, карточка #fff r20, шапка колонок #f3f6fa/#8a94a6, акцент #16213e/#1e2a4a,
AC-чип #16a34a, DC-чип #f97316, «идёт…» #f97316, разделители #e6ebf2. Шрифт Inter/Roboto-подобный,
RU. Тёмная тема — по main_dark.png (снять замеры при вёрстке). Итоговая строка: «Сессий: N · всего X кВт·ч · Y ₽».
Колонки: Тип | Дата и время | Длительность | Мощность, кВт | Получено, кВт·ч | Стоимость, ₽ | Заряд, % (было→стало).

## 11. Полевые данные (этап B, лог log2_20261104_162837.csv, ChargeProbe v2.0)

Сессия 3.5 мин: AC-зарядка 2.78 кВт (220В/13А, одна фаза) → остановка (пистолет воткнут)
→ извлечение → повторное подключение → возобновление. Все фазы жизненного цикла в одном логе.

Подтверждено эмпирически (важнее статического реверса при противоречии):
- PDU_PARAMS: [0..2]=U фаз A/B/C в ВОЛЬТАХ (raw×1! не ×0.1); [3..5]=I ×0.1 А;
  [7]=называется в реверсе «DC U ×0.1» (3405→340.5В, близко к U пака — трактуем осторожно,
  при DC не проверялось); [8]=«DC I ×0.1» (63→6.3, аналогично). Пилот: [0]=232 при
  «воткнут, стойку держит», =0 без пистолета.
  Однофазная зарядка: активен только канал A. P_ac = Σ U_i·I_i/1000 = 219·12.7/1000 = 2.78 кВт.
- BCU_PARAMS: [0]=U пака ×0.1 (340.8В); [1]=I батареи, значение=|raw×0.1−1000| (9932→6.8А,
  в простое 10009→~0 — offset-формула наблюдалась ТОЛЬКО на AC-сессии, сторона батареи);
  [5]=8191(invalid) без зарядки; [6]=t пака ×0.1 (50.9°C зарядка → 28 после); [22]=SOH% (10000→100).
  ВАЖНО: DC-сессий ещё НЕ БЫЛО — поведение всех сигналов при DC-зарядке (BCU[4]/[7],
  PDU-каналы, масштабы, статус-коды) НЕ ПРОВЕРЕНО, выводов не делаем.
- BCU_STATUS: зарядка AC = [4]=3,[5]=1,[6]=1,[7]=0; «остановлена, пистолет воткнут» = [5]=3
  (НЕ «полностью заряжен»! SOC был 47%); без пистолета = [4]=0,[6]=0.
- Дублирующие признаки: PTLAMP[6]=1 и [8]=1 при зарядке/пистолете; CHARGE_SWITCH[2]=1 при
  зарядке (снимается и при пользовательской остановке); REMAIN[7]=доп. запас км (0 без пистолета);
  REMAIN[12]=накопитель доп. запаса при зарядке; PTLAMP[25]=SOC (47 const) ✓; PTLAMP[34]=одометр.
- P_ac (2.78 кВт) vs сторона батареи (340.8В×6.8А=2.32 кВт): разница ~17% — для ChargeStats
  энергию AC-сессии считаем по AC-стороне (PDU), как делает Energy. Для DC-сессий источник
  энергии ПОКА НЕ ВЫБРАН — ждём реальную DC-сессию.
- VCU_RUN_STATE[0]=1(D1)/3/4(ACC) — режим зажигания; [1]=2 константа при пистолете — НЕ индикатор
  пистолета (англиш предыдущей гипотезы). Индикатор пистолета: PTLAMP[8] + BCU_STATUS[6/7].

Осталось добрать (не блокирует C): DC-сессия (масштабы/поведение при DC), длинная AC-сессия
для сверки ∫P dt с экраном Energy (±5%), поведение BCU_PARAMS[5]/[1] при平衡-зарядке.

## 12. Полевые данные DC (fast_chrge_log2_20261104_180237.csv + фото «Fast Charging»)

DC-сессия 18:02–18:33 (стойка, батарея холодная 5.6°C, улица 4°C):
- BCU_STATUS при DC-зарядке = [4]=6,[5]=1,[6]=0,[7]=1. Режим-код AC=3 vs DC=6 подтверждён;
  [5]=1 «идёт» — ЕДИНЫЙ для AC/DC; явные флаги [6]=AC, [7]=DC. 18:03–18:05 рукопожатие=(6,0)/(6,3).
- PDU_PARAMS при DC: [0..6]=0; [7]=raw напряжения пака ×0.1 (3489≈BCU[0]=3490) — НЕ «выход стойки»;
  [8]=61-63 = dcDCTemperature raw−40 ≈ 21-23°C (температура, НЕ ток). Ток/мощность стойки
  через VHAL НЕДОСТУПНЫ.
- UI Power на DC = U_пака × I_батареи: фото 17.6 кВт ≡ 348.5В×50.5А из лога, секунга в секунгу.
  → ChargeStats для DC считает энергию по BCU (U×I), как и UI.
- Фото → лог, точные совпадения: 50.5 А = |9495×0.1−1000|; «1h 7 min» = BCU[5]=67 мин;
  «54.12%» = PTLAMP[34]=5412; «+1.38 km» = REMAIN[12]×0.01.
- PTLAMP[34] = SOC×100 (ДВЕ цифры после запятой), НЕ одометр (поправка к §11!).
  В простом стойке дрифтует ±0.1% (кулон-счётчик). Одометр в наблюдаемом наборе отсутствует.
- Итог DC-сессии: SOC 53.59→81.01%, ∫U·I dt = 8.24 кВт·ч за 26.3 мин (ср. 18.9 кВт),
  REMAIN[12] = 55.16 км прироста, BCU[5] remaining 68→39 мин.
- Фото медленной (42.74% / 2.7 кВт / 12.6 А / 15:59) — AC-сессия ДО лога 16:28 (там SOC=47).
  Консистентна: 2.7 кВт ≡ 219В×12.6А, «Remaining 6h9» и «+47.26 km» — формулы те же.

ВЫВОД для C: движок ChargeStats: тип = BCU_STATUS[4]∈{3,6} (+дубли [6]/[7]); старт/стоп по [5];
AC-мощность PDU ΣUφIφ/1000; DC-мощность BCU[0]×|BCU[1]×0.1−1000|/1000; SOC = PTLAMP[34]/100;
прирост запаса = REMAIN[12]×0.01; оставшееся время = BCU[5] мин.
