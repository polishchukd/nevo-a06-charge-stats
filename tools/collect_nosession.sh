#!/bin/bash
# Сбор данных с ГУ Changan Nevo A06 БЕЗ сессии зарядки (этап B, предварительный)
# Запускать на машине с adb, подключённой к ГУ (USB или adb connect <IP>:5555).
# Ходить по шагам: скрипт сам ждёт команд, в это время воткни/выкни пистолет.
set -u
command -v adb >/dev/null || { echo "adb не найден (apt install adb / platform-tools)"; exit 1; }

OUT=nevo_nosession_$(date +%Y%m%d_%H%M%S); mkdir -p "$OUT"
cd "$OUT" || exit 1

echo "== проверка подключения =="
adb devices | tee adb_devices.txt
adb shell getprop ro.build.type          | tee build_type.txt
adb shell getprop ro.build.version.release >> build_type.txt
adb shell getprop ro.product.model      >> build_type.txt
[ -s adb_devices.txt ] || true
if ! adb get-state >/dev/null 2>&1; then echo "ГУ не видна по adb — дальше нечего делать"; exit 1; fi

echo "== список dumpsys-сервисов =="
adb shell dumpsys -l | tee dumpsys_list.txt

echo "== car_service (полный конфиг VHAL: propId, типы, length) =="
adb shell dumpsys car_service > car_service.txt 2>&1
for s in $(grep -iE 'vehicle|IVehicle' dumpsys_list.txt | tr -d '[:space:]'); do
  echo "   + dump HAL: $s"; adb shell dumpsys "$s" > "vhal_${s//[:\/]/_}.txt" 2>&1
done

echo "== разовые чтения наших свойств (propId areaId) =="
# BCU_STATUS, BCU_PARAMS, PDU_STATUS, PDU_PARAMS, VCU_RUN_STATE, VCU_STATUS,
# PTLAMP(SOC), REMAINING_MILEAGE, CHARGE_PORT, GEAR, READY, SPEED, SOC_LIMIT, AC_CURRENT_LIMIT
for p in 0x41930600 0x41930700 0x41930800 0x41930900 0x41931000 0x41931100 \
         0x41978800 0x41205500 0x40117400 0x40801300 0x40127500 0x60800600 \
         0x40802600 0x40832000 0x41319400 0x41306C00; do
  echo "== $p =="
  adb shell cmd car_service get-property-value "$p" 0
done > props_static.txt 2>&1
grep -c "== 0x" props_static.txt

if grep -qi "permission denial" props_static.txt; then
  echo "!! cmd car_service: Permission Denial (user-сборка). Пробуем adb root:"
  adb root 2>&1 | tee adb_root.txt; sleep 2; adb wait-for-device
  # повтор
  for p in 0x41930600 0x41930700 0x41930800 0x41930900 0x41931000 0x41931100 0x41978800 0x41205500 0x40117400; do
    echo "== $p =="; adb shell cmd car_service get-property-value "$p" 0
  done > props_static2.txt 2>&1
  echo "   повторный результат: props_static2.txt"
fi

echo "== скриншоты Энергоцентра (открой Экран батареи/энергии на ГУ) =="
read -p "   Открой на ГУ экран зарядки Энергоцентра. Enter для снимка..." _
adb exec-out screencap -p > screen_energy_1.png
echo "== logcat: ПОКОЙ 60 сек (ничего не делаем) =="
timeout 60 adb logcat -v time > logcat_idle.txt
grep -aiE "DriveModel|EnergyRecycleModel|BatteryService|BatteryViewModel|CaSdk" logcat_idle.txt > logcat_energy_idle.txt; wc -l logcat_energy_idle.txt

echo "== logcat: ВОТКНУТЬ ПИСТОЛЕТ (зарядку НЕ запускать), 90 сек =="
read -p "   Воткни пистолет и нажми Enter (пись пойдёт 90 сек)..." _
timeout 90 adb logcat -v time > logcat_plug.txt
grep -aiE "DriveModel|EnergyRecycleModel|BatteryService|BatteryViewModel|CaSdk" logcat_plug.txt > logcat_energy_plug.txt; wc -l logcat_energy_plug.txt
read -p "   Сними скриншот экрана зарядки ГУ (шторка/кнопка или Enter для adb-снимка)..." _
adb exec-out screencap -p > screen_energy_plug.png

echo "== logcat: ВЫТЯНУТЬ ПИСТОЛЕТ, 60 сек =="
read -p "   Вытащи пистолет и нажми Enter..." _
timeout 60 adb logcat -v time > logcat_unplug.txt
grep -aiE "DriveModel|EnergyRecycleModel|BatteryService|BatteryViewModel|CaSdk" logcat_unplug.txt > logcat_energy_unplug.txt
adb exec-out screencap -p > screen_energy_unplug.png

# финальные статические чтения (чтобы увидеть, что залипло после отключения — stale-поведение §5.3)
for p in 0x41930600 0x41930700 0x41931000 0x41978800 0x40117400; do
  echo "== $p =="; adb shell cmd car_service get-property-value "$p" 0
done > props_after_unplug.txt 2>&1

echo "== settings (ищем ключи Energy) =="
adb shell settings list system > settings_system.txt 2>&1
adb shell settings list global > settings_global.txt 2>&1
adb shell settings list secure > settings_secure.txt 2>&1

echo "== упоминания car-разрешений в car_service =="
grep -aiE "permission|car\.permission" car_service.txt > car_permissions.txt || true

cd .. && tar czf "$OUT.tar.gz" "$OUT"
echo "ГОТОВО. Пришли мне: $OUT.tar.gz"
