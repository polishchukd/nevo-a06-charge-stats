# CollectNoSession.ps1 — сбор данных с ГУ Changan Nevo A06 БЕЗ сессии зарядки (этап B, предварительный)
# Запуск: PowerShell, из папки где лежит скрипт:  powershell -ExecutionPolicy Bypass -File .\CollectNoSession.ps1
# Требуется adb (Android platform-tools). Поставить: winget install --id Google.PlatformTools
# ГУ должна быть видна: USB (с драйвером и включённой отладкой по USB) или сеть: adb connect <IP-ГУ>:5555
param([string]$Adb = "adb")

$out = "nevo_nosession_" + (Get-Date -Format "yyyyMMdd_HHmmss")
New-Item -ItemType Directory -Path $out -Force | Out-Null
Write-Host "== проверка подключения =="
& $Adb devices | Tee-Object "$out\adb_devices.txt"
& $Adb shell getprop ro.build.type            | Out-File "$out\build_type.txt"
& $Adb shell getprop ro.build.version.release | Out-File -Append "$out\build_type.txt"
& $Adb shell getprop ro.product.model         | Out-File -Append "$out\build_type.txt"
$state = (& $Adb get-state) 2>&1
if ($LASTEXITCODE -ne 0) { Write-Host "ГУ не видна по adb ($state). Подключи USB/adb connect и повтори."; exit 1 }

Write-Host "== список dumpsys-сервисов =="
& $Adb shell dumpsys -l | Out-File "$out\dumpsys_list.txt"

Write-Host "== car_service (конфиг VHAL: propId, типы, length) =="
& $Adb shell dumpsys car_service | Out-File "$out\car_service.txt"
foreach ($s in (Get-Content "$out\dumpsys_list.txt" | Select-String -Pattern "vehicle" | ForEach-Object { $_.ToString().Trim() })) {
    Write-Host "   + dump HAL: $s"
    & $Adb shell dumpsys $s | Out-File "$out\vhal_$($s -replace '[^A-Za-z0-9]','_').txt"
}

$props = "0x41930600","0x41930700","0x41930800","0x41930900","0x41931000","0x41931100",
         "0x41978800","0x41205500","0x40117400","0x40801300","0x40127500","0x60800600",
         "0x40802600","0x40832000","0x41319400","0x41306C00"
Write-Host "== разовые чтения свойств VHAL =="
$props | ForEach-Object { "== $_"; & $Adb shell cmd car_service get-property-value $_ 0 } |
    Out-File "$out\props_static.txt"
if (Select-String -Quiet -Pattern "permission denial" "$out\props_static.txt") {
    Write-Host "!! Permission Denial — пробуем adb root..."
    & $Adb root 2>&1 | Out-File "$out\adb_root.txt"; Start-Sleep 3; & $Adb wait-for-device
    $props | ForEach-Object { "== $_"; & $Adb shell cmd car_service get-property-value $_ 0 } |
        Out-File "$out\props_static2.txt"
}

function Grab-Logcat([string]$file, [int]$sec) {
    & $Adb logcat -c 2>$null
    $p = Start-Process -FilePath $Adb -ArgumentList "logcat","-v","time" -RedirectStandardOutput $file -NoNewWindow -PassThru
    Start-Sleep -Seconds $sec
    Stop-Process -Id $p.Id -Force -ErrorAction SilentlyContinue
    Select-String -Path $file -Pattern "DriveModel|EnergyRecycleModel|BatteryService|BatteryViewModel|CaSdk" |
        ForEach-Object { $_.Line } | Out-File "$($file -replace '\.txt$','')_energy.txt"
}
function Shot([string]$name) {
    # скриншот через /sdcard (exec-out в PowerShell бинарник испортит кодировкой)
    & $Adb shell screencap -p /sdcard/_shot.png
    & $Adb pull /sdcard/_shot.png "$out\$name" | Out-Null
    & $Adb shell rm /sdcard/_shot.png
}

Write-Host "== скриншот Энергоцентра: открой экран зарядки/энергии на ГУ =="
Read-Host "   Открой экран и нажми Enter для снимка"
Shot "screen_energy_1.png"

Write-Host "== logcat: ПОКОЙ 60 сек (ничего не делаем) =="
Grab-Logcat "$out\logcat_idle.txt" 60

Write-Host "== logcat: ВОТКНУТЬ ПИСТОЛЕТ (зарядку НЕ запускать) =="
Read-Host "   Воткни пистолет и нажми Enter (пись пойдёт 90 сек)"
Grab-Logcat "$out\logcat_plug.txt" 90
Shot "screen_energy_plug.png"

Write-Host "== logcat: ВЫТЯНУТЬ ПИСТОЛЕТ =="
Read-Host "   Вытащи пистолет и нажми Enter (пись пойдёт 60 сек)"
Grab-Logcat "$out\logcat_unplug.txt" 60
Shot "screen_energy_unplug.png"

Write-Host "== повторные чтения (stale-поведение после отключения) =="
"0x41930600","0x41930700","0x41931000","0x41978800","0x40117400" | ForEach-Object { "== $_"; & $Adb shell cmd car_service get-property-value $_ 0 } |
    Out-File "$out\props_after_unplug.txt"

Write-Host "== settings (ключи Energy) =="
& $Adb shell settings list system | Out-File "$out\settings_system.txt"
& $Adb shell settings list global | Out-File "$out\settings_global.txt"
& $Adb shell settings list secure | Out-File "$out\settings_secure.txt"

Select-String -Path "$out\car_service.txt" -Pattern "permission|car\.permission" |
    ForEach-Object { $_.Line } | Out-File "$out\car_permissions.txt"

Compress-Archive -Path $out -DestinationPath "$out.zip"
Write-Host "ГОТОВО. Пришли мне архив: $((Get-Item "$out.zip").FullName)"
