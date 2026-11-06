package com.nevo.chargestats;

import android.content.Context;
import java.util.HashMap;
import java.util.Locale;

/** ru/en словари. settings("lang"): ru | en | system. */
public class I18n {
    private final HashMap<String, String> m;
    public final boolean ru;

    public static String langCode(Context c) {
        String l = new Db(c).get("lang", "ru");
        if ("ru".equals(l) || "en".equals(l)) return l;
        return "ru".equals(Locale.getDefault().getLanguage()) ? "ru" : "en";
    }

    public static I18n of(Context c) { return new I18n(langCode(c)); }

    private I18n(String lang) {
        ru = "ru".equals(lang);
        m = new HashMap<>();
        if (ru) {
            p("h1","Статистика зарядки автомобиля"); p("history","История зарядок");
            p("m0","Месяц"); p("m1","Полгода"); p("m2","Год");
            p("c_type","Тип"); p("c_date","Дата и время"); p("c_dur","Длительность");
            p("c_pwr","Мощность, кВт"); p("c_energy","Получено, кВт·ч");
            p("c_cost","Стоимость, ₽"); p("c_soc","Заряд, %");
            p("live","· идёт…");
            p("totals","Сессий: %1$d · всего %2$.2f кВт·ч · %3$.2f ₽");
            p("empty","Нет записей. Поставь зарядку или импортируй CSV в настройках.");
            p("del","Удалить"); p("edit","Редактировать");
            p("settings","Настройки");
            p("cost_t","Стоимость электроэнергии");
            p("cost_s","Укажите стоимость электроэнергии для разных типов зарядки");
            p("dc_l","DC зарядка"); p("ac_l","AC зарядка"); p("save","Сохранить");
            p("col_t","Отображаемые колонки");
            p("col_s","Выберите, какие колонки показывать на главном экране");
            p("theme_t","Тема оформления"); p("theme_s","Выберите светлую или тёмную тему");
            p("th_light","Светлая"); p("th_dark","Тёмная"); p("th_sys","Системная");
            p("lang_t","Язык интерфейса"); p("lang_s","Выберите язык приложения");
            p("lang_ru","Русский"); p("lang_en","English"); p("lang_sys","Системный");
            p("adv_t","Служебное"); p("adv_s","Отладка: папка выгрузки CSV и импорт логов ChargeProbe");
            p("imp","Импорт CSV-лога…"); p("folder","Папка CSV…");
            p("t_prices","Тарифы сохранены"); p("t_folder","Папка CSV сохранена");
            p("t_imp","Импортировано сессий: ");
            p("s_title","Сессия "); p("s_hint","Пустые поля остаются без изменений");
            p("f_type","Тип (AC / DC)"); p("f_volt","Напряжение среднее, В");
            p("f_date","Дата и время начала"); p("f_max","Мощность макс., кВт");
            p("f_dur","Длительность, мин"); p("f_energy","Энергия, кВт·ч");
            p("f_soc","Заряд, % (начало→конец)"); p("f_cost","Стоимость, ₽");
            p("cancel","Отмена");
            p("colL0","Дата и время начала"); p("colL1","Длительность");
            p("colS0","Дата"); p("colS1","Длит."); p("colS2","Мощн."); p("colS3","кВт·ч"); p("colS4","Стоим.");
            p("colL2","Мощность (кВт)"); p("colL3","Получено (кВт·ч)"); p("colL4","Стоимость (₽)");
        } else {
            p("h1","Charging statistics"); p("history","Charging history");
            p("m0","Month"); p("m1","6 months"); p("m2","Year");
            p("c_type","Type"); p("c_date","Date & time"); p("c_dur","Duration");
            p("c_pwr","Power, kW"); p("c_energy","Received, kWh");
            p("c_cost","Cost, ₽"); p("c_soc","Charge, %");
            p("live","· live…");
            p("totals","Sessions: %1$d · total %2$.2f kWh · %3$.2f ₽");
            p("empty","No records. Start charging or import a CSV log in Settings.");
            p("del","Delete"); p("edit","Edit");
            p("settings","Settings");
            p("cost_t","Electricity cost");
            p("cost_s","Set the electricity price for each charging type");
            p("dc_l","DC charging"); p("ac_l","AC charging"); p("save","Save");
            p("col_t","Visible columns");
            p("col_s","Choose which columns to show on the main screen");
            p("theme_t","Theme"); p("theme_s","Choose light or dark theme");
            p("th_light","Light"); p("th_dark","Dark"); p("th_sys","System");
            p("lang_t","Language"); p("lang_s","Choose the app language");
            p("lang_ru","Русский"); p("lang_en","English"); p("lang_sys","System");
            p("adv_t","Advanced"); p("adv_s","Debug: CSV output folder and ChargeProbe log import");
            p("imp","Import CSV log…"); p("folder","CSV folder…");
            p("t_prices","Tariffs saved"); p("t_folder","CSV folder saved");
            p("t_imp","Imported sessions: ");
            p("s_title","Session "); p("s_hint","Empty fields are left unchanged");
            p("f_type","Type (AC / DC)"); p("f_volt","Avg voltage, V");
            p("f_date","Start date & time"); p("f_max","Max power, kW");
            p("f_dur","Duration, min"); p("f_energy","Energy, kWh");
            p("f_soc","Charge, % (start→end)"); p("f_cost","Cost, ₽");
            p("cancel","Cancel");
            p("colL0","Start date & time"); p("colL1","Duration");
            p("colS0","Date"); p("colS1","Dur."); p("colS2","Power"); p("colS3","kWh"); p("colS4","Cost");
            p("colL2","Power (kW)"); p("colL3","Received (kWh)"); p("colL4","Cost (₽)");
        }
    }
    private void p(String k, String v) { m.put(k, v); }
    public String t(String k) { String v = m.get(k); return v != null ? v : k; }
}
