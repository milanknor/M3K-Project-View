// columns.h - sloupce tabulky úkolů: vestavěné v kategoriích, vlastní pole a směrné plány 1-10 ze souboru; text buňky, klíč řazení
#pragma once
#include "ui.h"

enum ColCat { CAT_GENERAL, CAT_DATES, CAT_WORK, CAT_BASELINE, CAT_SLACK, CAT_OTHER, CAT_CUSTOM, CAT_COUNT };
static const wchar_t* CAT_NAMES[CAT_COUNT] = { L"Obecné", L"Termíny", L"Práce a náklady", L"Směrný plán", L"Rezervy a kritická cesta", L"Ostatní", L"Vlastní pole" };
struct Col {
    std::wstring key, title; int cat; int width; bool right;
    std::function<std::wstring(const mpp::Task&, int)> text;
    std::function<double(const mpp::Task&, int)> num; // klíč řazení; NAN = řadit podle textu
};
static std::vector<Col> g_cols;           // všechny dostupné sloupce
static std::vector<int> g_visible;        // indexy do g_cols v pořadí zobrazení
static const std::vector<std::wstring> DEFAULT_COLS = { L"id", L"name", L"duration", L"start", L"finish", L"percent", L"resources", L"predecessors", L"baselineFinish", L"variance", L"state" };
static bool isLate(const mpp::Task& t) { return !t.isNull && !t.summary && t.pctComplete < 100 && t.finish && t.finish < g_refDate; }
static bool isLateStart(const mpp::Task& t) { return !t.isNull && !t.summary && t.pctComplete <= 0 && t.start && t.start < g_refDate && !isLate(t); }
static std::wstring stateText(const mpp::Task& t) {
    if (t.isNull) return L"";
    if (!t.active) return L"neaktivní";
    if (t.pctComplete >= 100) return t.milestone ? L"splněn" : L"hotovo";
    if (isLate(t)) return L"ZPOŽDĚNO";
    if (t.pctComplete > 0) return t.critical ? L"probíhá (krit.)" : L"probíhá";
    if (isLateStart(t)) return L"nezahájeno!";
    return t.critical ? L"čeká (krit.)" : L"čeká";
}
static std::wstring varianceText(const mpp::Task& t, int idx) {
    if (t.isNull || !t.finish || !t.baselineFinishV() || idx < 0 || idx >= (int)g_var.size() || g_var[idx] == INT_MIN) return L"";
    int d = g_var[idx];
    return d == 0 ? L"0" : (d > 0 ? L"+" : L"") + std::to_wstring(d);
}
static std::wstring successorsText(const mpp::Task& t) {
    auto it = g_succ.find(t.uid); if (it == g_succ.end()) return L"";
    std::wstring s; for (int u : it->second) { const mpp::Task* x = g_prj.byUid(u); if (!x) continue; if (!s.empty()) s += L";"; s += std::to_wstring(x->id); }
    return s;
}
static std::wstring firstLine(const std::wstring& n) { size_t nl = n.find(L'\n'); std::wstring s = nl == std::wstring::npos ? n : n.substr(0, nl) + L" …"; return s.size() > 120 ? s.substr(0, 117) + L"…" : s; }
static const double NONUM = std::numeric_limits<double>::quiet_NaN();
static double tnum(Time t) { return t ? (double)t : NONUM; }
static double dnum(const mpp::Duration& d) { return d.valid() ? (double)d.raw : NONUM; }
static double snum(int raw) { return raw == INT32_MIN ? NONUM : (double)raw; }
static double zero0(double v) { return v == 0 ? NONUM : v; }
static std::wstring workText(double minutes) { return minutes == 0 ? L"" : mpp::fmtHours(minutes / 60.0); }

static void addCol(const wchar_t* key, const wchar_t* title, int cat, int width, bool right, std::function<std::wstring(const mpp::Task&, int)> text, std::function<double(const mpp::Task&, int)> num) {
    g_cols.push_back({ key, title, cat, width, right, text, num });
}
static void buildColumns() {
    g_cols.clear();
    const mpp::Project& p = g_prj;
    addCol(L"id", L"ID", CAT_GENERAL, 40, true, [](const mpp::Task& t, int) { return std::to_wstring(t.id); }, [](const mpp::Task& t, int) { return (double)t.id; });
    addCol(L"name", L"Název úkolu", CAT_GENERAL, 260, false, [](const mpp::Task& t, int) { return t.name; }, [](const mpp::Task&, int) { return NONUM; });
    addCol(L"duration", L"Doba trvání", CAT_GENERAL, 80, true, [&p](const mpp::Task& t, int) { return t.milestone && !t.duration.valid() ? L"0 d" : mpp::fmtDuration(t.duration, p, t.estimated); }, [](const mpp::Task& t, int) { return dnum(t.duration); });
    addCol(L"start", L"Zahájení", CAT_GENERAL, 90, false, [](const mpp::Task& t, int) { return mpp::fmtDate(t.start); }, [](const mpp::Task& t, int) { return tnum(t.start); });
    addCol(L"finish", L"Dokončení", CAT_GENERAL, 90, false, [](const mpp::Task& t, int) { return mpp::fmtDate(t.finish); }, [](const mpp::Task& t, int) { return tnum(t.finish); });
    addCol(L"percent", L"% dok.", CAT_GENERAL, 55, true, [](const mpp::Task& t, int) { return t.pctComplete < 0 ? L"" : std::to_wstring(t.pctComplete) + L" %"; }, [](const mpp::Task& t, int) { return t.pctComplete < 0 ? NONUM : (double)t.pctComplete; });
    addCol(L"resources", L"Zdroje", CAT_GENERAL, 150, false, [](const mpp::Task& t, int) { return t.resourceNames; }, [](const mpp::Task&, int) { return NONUM; });
    addCol(L"predecessors", L"Předchůdci", CAT_GENERAL, 90, false, [&p](const mpp::Task& t, int) { return mpp::fmtPredecessors(t, p); }, [](const mpp::Task&, int) { return NONUM; });
    addCol(L"baselineFinish", L"Směrný plán dok.", CAT_BASELINE, 110, false, [](const mpp::Task& t, int) { return mpp::fmtDate(t.baselineFinishV()); }, [](const mpp::Task& t, int) { return tnum(t.baselineFinishV()); });
    addCol(L"variance", L"Skluz (prac. dny)", CAT_BASELINE, 95, true, [](const mpp::Task& t, int i) { return varianceText(t, i); }, [](const mpp::Task&, int i) { return i >= 0 && i < (int)g_var.size() && g_var[i] != INT_MIN ? (double)g_var[i] : NONUM; });
    addCol(L"state", L"Stav", CAT_GENERAL, 95, false, [](const mpp::Task& t, int) { return stateText(t); }, [](const mpp::Task&, int) { return NONUM; });
    addCol(L"work", L"Práce", CAT_WORK, 80, true, [](const mpp::Task& t, int) { return workText(t.workMinutes); }, [](const mpp::Task& t, int) { return zero0(t.workMinutes); });
    addCol(L"cost", L"Náklady", CAT_WORK, 100, true, [&p](const mpp::Task& t, int) { return mpp::fmtMoney(t.cost, p); }, [](const mpp::Task& t, int) { return zero0(t.cost); });
    addCol(L"totalSlack", L"Celková rezerva", CAT_SLACK, 100, true, [&p](const mpp::Task& t, int) { return t.summary ? L"" : mpp::fmtSlackRaw(t.totalSlackRaw, p); }, [](const mpp::Task& t, int) { return snum(t.totalSlackRaw); });
    addCol(L"wbs", L"WBS", CAT_GENERAL, 70, false, [](const mpp::Task& t, int) { return t.wbs; }, [](const mpp::Task&, int) { return NONUM; });
    addCol(L"uniqueId", L"Jedinečné ID", CAT_GENERAL, 80, true, [](const mpp::Task& t, int) { return std::to_wstring(t.uid); }, [](const mpp::Task& t, int) { return (double)t.uid; });
    addCol(L"outlineNumber", L"Číslo osnovy", CAT_GENERAL, 80, false, [](const mpp::Task& t, int) { return t.outlineNumber; }, [](const mpp::Task&, int) { return NONUM; });
    addCol(L"level", L"Úroveň", CAT_GENERAL, 55, true, [](const mpp::Task& t, int) { return std::to_wstring(t.level); }, [](const mpp::Task& t, int) { return (double)t.level; });
    addCol(L"kind", L"Typ", CAT_GENERAL, 100, false, [](const mpp::Task& t, int) { return std::wstring(mpp::taskKindName(t)); }, [](const mpp::Task&, int) { return NONUM; });
    addCol(L"successors", L"Následníci", CAT_GENERAL, 90, false, [](const mpp::Task& t, int) { return successorsText(t); }, [](const mpp::Task&, int) { return NONUM; });
    addCol(L"priority", L"Priorita", CAT_GENERAL, 110, false, [](const mpp::Task& t, int) { return mpp::priorityName(t.priority); }, [](const mpp::Task& t, int) { return (double)t.priority; });
    addCol(L"taskType", L"Typ úkolu", CAT_GENERAL, 120, false, [](const mpp::Task& t, int) { return std::wstring(mpp::taskTypeName(t.taskType)); }, [](const mpp::Task& t, int) { return (double)t.taskType; });
    addCol(L"calendar", L"Kalendář", CAT_GENERAL, 110, false, [](const mpp::Task& t, int) { return t.calendarName; }, [](const mpp::Task&, int) { return NONUM; });
    addCol(L"notes", L"Poznámky", CAT_GENERAL, 220, false, [](const mpp::Task& t, int) { return firstLine(t.notes); }, [](const mpp::Task&, int) { return NONUM; });
    addCol(L"contact", L"Kontakt", CAT_OTHER, 110, false, [](const mpp::Task& t, int) { return t.contact; }, [](const mpp::Task&, int) { return NONUM; });
    addCol(L"actualStart", L"Skutečné zahájení", CAT_DATES, 105, false, [](const mpp::Task& t, int) { return mpp::fmtDate(t.actualStart); }, [](const mpp::Task& t, int) { return tnum(t.actualStart); });
    addCol(L"actualFinish", L"Skutečné dokončení", CAT_DATES, 105, false, [](const mpp::Task& t, int) { return mpp::fmtDate(t.actualFinish); }, [](const mpp::Task& t, int) { return tnum(t.actualFinish); });
    addCol(L"deadline", L"Konečný termín", CAT_DATES, 95, false, [](const mpp::Task& t, int) { return mpp::fmtDate(t.deadline); }, [](const mpp::Task& t, int) { return tnum(t.deadline); });
    addCol(L"constraint", L"Omezení", CAT_DATES, 160, false, [](const mpp::Task& t, int) { return t.isNull ? L"" : std::wstring(mpp::constraintName(t.constraintType)) + (t.constraintDate ? L" " + mpp::fmtDate(t.constraintDate) : L""); }, [](const mpp::Task& t, int) { return (double)t.constraintType; });
    addCol(L"earlyStart", L"Nejdřívější zahájení", CAT_DATES, 115, false, [](const mpp::Task& t, int) { return mpp::fmtDate(t.earlyStart); }, [](const mpp::Task& t, int) { return tnum(t.earlyStart); });
    addCol(L"earlyFinish", L"Nejdřívější dokončení", CAT_DATES, 115, false, [](const mpp::Task& t, int) { return mpp::fmtDate(t.earlyFinish); }, [](const mpp::Task& t, int) { return tnum(t.earlyFinish); });
    addCol(L"lateStart", L"Nejzazší zahájení", CAT_DATES, 115, false, [](const mpp::Task& t, int) { return mpp::fmtDate(t.lateStart); }, [](const mpp::Task& t, int) { return tnum(t.lateStart); });
    addCol(L"lateFinish", L"Nejzazší dokončení", CAT_DATES, 115, false, [](const mpp::Task& t, int) { return mpp::fmtDate(t.lateFinish); }, [](const mpp::Task& t, int) { return tnum(t.lateFinish); });
    addCol(L"created", L"Vytvořeno", CAT_DATES, 90, false, [](const mpp::Task& t, int) { return mpp::fmtDate(t.created); }, [](const mpp::Task& t, int) { return tnum(t.created); });
    addCol(L"actualDuration", L"Skutečná doba", CAT_DATES, 90, true, [&p](const mpp::Task& t, int) { return t.actualDuration.valid() && t.actualDuration.raw ? mpp::fmtDuration(t.actualDuration, p) : L""; }, [](const mpp::Task& t, int) { return dnum(t.actualDuration); });
    addCol(L"remainingDuration", L"Zbývající doba", CAT_DATES, 90, true, [&p](const mpp::Task& t, int) { return t.remainingDuration.valid() ? mpp::fmtDuration(t.remainingDuration, p) : L""; }, [](const mpp::Task& t, int) { return dnum(t.remainingDuration); });
    addCol(L"actualWork", L"Skutečná práce", CAT_WORK, 95, true, [](const mpp::Task& t, int) { return workText(t.actualWorkMinutes); }, [](const mpp::Task& t, int) { return zero0(t.actualWorkMinutes); });
    addCol(L"remainingWork", L"Zbývající práce", CAT_WORK, 95, true, [](const mpp::Task& t, int) { return workText(t.remainingWorkMinutes); }, [](const mpp::Task& t, int) { return zero0(t.remainingWorkMinutes); });
    addCol(L"percentWork", L"% práce", CAT_WORK, 65, true, [](const mpp::Task& t, int) { return t.pctWork < 0 ? L"" : std::to_wstring(t.pctWork) + L" %"; }, [](const mpp::Task& t, int) { return t.pctWork < 0 ? NONUM : (double)t.pctWork; });
    addCol(L"actualCost", L"Skutečné náklady", CAT_WORK, 105, true, [&p](const mpp::Task& t, int) { return mpp::fmtMoney(t.actualCost, p); }, [](const mpp::Task& t, int) { return zero0(t.actualCost); });
    addCol(L"remainingCost", L"Zbývající náklady", CAT_WORK, 105, true, [&p](const mpp::Task& t, int) { return mpp::fmtMoney(t.remainingCost, p); }, [](const mpp::Task& t, int) { return zero0(t.remainingCost); });
    addCol(L"fixedCost", L"Pevné náklady", CAT_WORK, 95, true, [&p](const mpp::Task& t, int) { return mpp::fmtMoney(t.fixedCost, p); }, [](const mpp::Task& t, int) { return zero0(t.fixedCost); });
    addCol(L"baselineStart", L"Zahájení (směrný plán)", CAT_BASELINE, 115, false, [](const mpp::Task& t, int) { return mpp::fmtDate(t.baselineStartV()); }, [](const mpp::Task& t, int) { return tnum(t.baselineStartV()); });
    addCol(L"baselineDuration", L"Doba (směrný plán)", CAT_BASELINE, 105, true, [&p](const mpp::Task& t, int) { return mpp::fmtDuration(t.baselines[0].duration, p); }, [](const mpp::Task& t, int) { return dnum(t.baselines[0].duration); });
    addCol(L"baselineWork", L"Práce (směrný plán)", CAT_BASELINE, 105, true, [](const mpp::Task& t, int) { return mpp::fmtHours(t.baselines[0].workHours); }, [](const mpp::Task& t, int) { return zero0(t.baselines[0].workHours); });
    addCol(L"baselineCost", L"Náklady (směrný plán)", CAT_BASELINE, 115, true, [&p](const mpp::Task& t, int) { return mpp::fmtMoney(t.baselines[0].cost, p); }, [](const mpp::Task& t, int) { return zero0(t.baselines[0].cost); });
    addCol(L"startVariance", L"Odchylka zahájení", CAT_BASELINE, 105, true, [](const mpp::Task& t, int) { if (!t.start || !t.baselineStartV()) return std::wstring(); int64_t d = g_prj.calendar().workingDaysBetween(mpp::toUnixDays(t.baselineStartV()), mpp::toUnixDays(t.start)); return (d > 0 ? L"+" : L"") + std::to_wstring(d); }, [](const mpp::Task& t, int) { return t.start && t.baselineStartV() ? (double)g_prj.calendar().workingDaysBetween(mpp::toUnixDays(t.baselineStartV()), mpp::toUnixDays(t.start)) : NONUM; }); // řadit stejně, jak se zobrazuje (pracovní dny)
    addCol(L"freeSlack", L"Volná rezerva", CAT_SLACK, 95, true, [&p](const mpp::Task& t, int) { return t.summary ? L"" : mpp::fmtSlackRaw(t.freeSlackRaw, p); }, [](const mpp::Task& t, int) { return snum(t.freeSlackRaw); });
    addCol(L"critical", L"Kritický", CAT_SLACK, 65, false, [](const mpp::Task& t, int) { return t.critical ? L"ano" : L""; }, [](const mpp::Task& t, int) { return t.critical ? 1.0 : 0.0; });
    addCol(L"milestone", L"Milník", CAT_OTHER, 60, false, [](const mpp::Task& t, int) { return t.milestone ? L"ano" : L""; }, [](const mpp::Task& t, int) { return t.milestone ? 1.0 : 0.0; });
    addCol(L"summary", L"Souhrnný", CAT_OTHER, 70, false, [](const mpp::Task& t, int) { return t.summary ? L"ano" : L""; }, [](const mpp::Task& t, int) { return t.summary ? 1.0 : 0.0; });
    addCol(L"active", L"Aktivní", CAT_OTHER, 60, false, [](const mpp::Task& t, int) { return t.isNull ? L"" : t.active ? L"ano" : L"ne"; }, [](const mpp::Task& t, int) { return t.active ? 1.0 : 0.0; });
    addCol(L"manual", L"Ručně plánovaný", CAT_OTHER, 95, false, [](const mpp::Task& t, int) { return t.manual ? L"ano" : L""; }, [](const mpp::Task& t, int) { return t.manual ? 1.0 : 0.0; });
    addCol(L"estimated", L"Odhad", CAT_OTHER, 60, false, [](const mpp::Task& t, int) { return t.estimated ? L"ano" : L""; }, [](const mpp::Task& t, int) { return t.estimated ? 1.0 : 0.0; });
    addCol(L"effortDriven", L"Řízený úsilím", CAT_OTHER, 90, false, [](const mpp::Task& t, int) { return t.effortDriven ? L"ano" : L""; }, [](const mpp::Task& t, int) { return t.effortDriven ? 1.0 : 0.0; });
    addCol(L"subproject", L"Podprojekt", CAT_OTHER, 150, false, [](const mpp::Task& t, int) { return t.subproject; }, [](const mpp::Task&, int) { return NONUM; });
    for (int n : p.baselinesUsed) {
        if (n == 0) continue;
        std::wstring suf = L" (SP " + std::to_wstring(n) + L")";
        addCol((L"baseline" + std::to_wstring(n) + L"Start").c_str(), (L"Zahájení" + suf).c_str(), CAT_BASELINE, 105, false, [n](const mpp::Task& t, int) { return mpp::fmtDate(t.baselines[n].start); }, [n](const mpp::Task& t, int) { return tnum(t.baselines[n].start); });
        addCol((L"baseline" + std::to_wstring(n) + L"Finish").c_str(), (L"Dokončení" + suf).c_str(), CAT_BASELINE, 105, false, [n](const mpp::Task& t, int) { return mpp::fmtDate(t.baselines[n].finish); }, [n](const mpp::Task& t, int) { return tnum(t.baselines[n].finish); });
        addCol((L"baseline" + std::to_wstring(n) + L"Duration").c_str(), (L"Doba" + suf).c_str(), CAT_BASELINE, 95, true, [n, &p](const mpp::Task& t, int) { return mpp::fmtDuration(t.baselines[n].duration, p); }, [n](const mpp::Task& t, int) { return dnum(t.baselines[n].duration); });
        addCol((L"baseline" + std::to_wstring(n) + L"Work").c_str(), (L"Práce" + suf).c_str(), CAT_BASELINE, 95, true, [n](const mpp::Task& t, int) { return mpp::fmtHours(t.baselines[n].workHours); }, [n](const mpp::Task& t, int) { return zero0(t.baselines[n].workHours); });
        addCol((L"baseline" + std::to_wstring(n) + L"Cost").c_str(), (L"Náklady" + suf).c_str(), CAT_BASELINE, 105, true, [n, &p](const mpp::Task& t, int) { return mpp::fmtMoney(t.baselines[n].cost, p); }, [n](const mpp::Task& t, int) { return zero0(t.baselines[n].cost); });
    }
    for (const mpp::CustomField& cf : p.customFields) {
        mpp::CustomField f = cf;
        bool right = f.kind == mpp::CK_NUMBER || f.kind == mpp::CK_COST || f.kind == mpp::CK_DURATION;
        int w = f.kind == mpp::CK_TEXT ? 150 : f.kind == mpp::CK_FLAG ? 70 : 100;
        addCol((L"custom:" + std::to_wstring(f.id)).c_str(), f.title().c_str(), CAT_CUSTOM, w, right,
            [f, &p](const mpp::Task& t, int) { return mpp::fmtCustom(f, t.customValue(f.id), p); },
            [f](const mpp::Task& t, int) { const mpp::CustomValue* v = t.customValue(f.id); if (!v || !v->set) return NONUM; switch (f.kind) { case mpp::CK_NUMBER: case mpp::CK_COST: return v->num; case mpp::CK_FLAG: return 1.0; case mpp::CK_DATE: case mpp::CK_START: case mpp::CK_FINISH: return (double)v->date; case mpp::CK_DURATION: return (double)v->dur.raw; default: return NONUM; } });
    }
}
static int colIndex(const std::wstring& key) { for (size_t i = 0; i < g_cols.size(); i++) if (g_cols[i].key == key) return (int)i; return -1; }
static std::wstring cellText(const mpp::Task& t, int idx, int col) {
    if (col < 0 || col >= (int)g_cols.size()) return L"";
    if (t.isNull) return g_cols[col].key == L"id" ? std::to_wstring(t.id) : L"";
    return g_cols[col].text(t, idx);
}
static std::wstring nameCell(const mpp::Task& t) {
    if (t.isNull) return L"";
    std::wstring s((size_t)imax(0, t.level - 1) * 3, L' ');
    if (t.summary) s += t.expanded ? L"▾ " : L"▸ ";
    else if (t.milestone) s += L"◆ ";
    return s + t.name;
}
// Řazení dvou úkolů podle sloupce (prázdné hodnoty vždy na konci)
static int compareTasks(int ia, int ib, int col, int dir) {
    const mpp::Task& a = g_prj.tasks[ia]; const mpp::Task& b = g_prj.tasks[ib];
    if (a.isNull || b.isNull) return a.isNull == b.isNull ? 0 : (a.isNull ? 1 : -1);
    double na = g_cols[col].num(a, ia), nb = g_cols[col].num(b, ib);
    bool ea, eb; int c;
    if (!std::isnan(na) || !std::isnan(nb)) { ea = std::isnan(na); eb = std::isnan(nb); c = (ea || eb) ? 0 : (na < nb ? -1 : na > nb ? 1 : 0); }
    else { std::wstring sa = g_cols[col].text(a, ia), sb = g_cols[col].text(b, ib); ea = sa.empty(); eb = sb.empty(); c = (ea || eb) ? 0 : collate(sa, sb); }
    if (ea != eb) return ea ? 1 : -1;
    return c * dir;
}
// Hodnota pro obarvení pruhů: první položka před ; nebo , a bez jednotek v hranatých závorkách
static std::wstring colorValue(const mpp::Task& t, int idx, int col) {
    std::wstring s = cellText(t, idx, col);
    size_t cut = s.find_first_of(L";,"); if (cut != std::wstring::npos) s = s.substr(0, cut);
    size_t br = s.find(L'['); if (br != std::wstring::npos && br > 0) s = s.substr(0, br);
    while (!s.empty() && s.back() == L' ') s.pop_back();
    return s;
}
static const COLORREF PALETTE[12] = { RGB(0xFF,0x79,0x0A), RGB(0x2E,0x7D,0xD7), RGB(0x2F,0xA3,0x4F), RGB(0x8E,0x5B,0xD0), RGB(0xD2,0x48,0x7F), RGB(0x0E,0x9A,0x9A), RGB(0xB0,0x7B,0x12), RGB(0x5C,0x78,0x96), RGB(0xC1,0x53,0x3C), RGB(0x4F,0x7A,0x28), RGB(0x7A,0x4F,0xBF), RGB(0x1F,0x6F,0x8B) };
static const COLORREF C_OTHERS = RGB(0x8A, 0x8A, 0x96);
static COLORREF lighter(COLORREF c) { auto mix = [](int v) { return imin(255, (int)std::lround(v + (255 - v) * 0.68)); }; return g_dark ? RGB(GetRValue(c) / 2, GetGValue(c) / 2, GetBValue(c) / 2) : RGB(mix(GetRValue(c)), mix(GetGValue(c)), mix(GetBValue(c))); }
static void buildBarColors() {
    g_colorMap.clear(); g_legend.clear();
    int col = colIndex(g_colorKey); if (col < 0) return;
    std::map<std::wstring, int> counts;
    // pořadí barev podle všech úkolů vyhovujících filtru, ne jen viditelných řádků – jinak se barvy přehazovaly při sbalení souhrnu
    for (size_t r = 0; r < g_prj.tasks.size() && r < g_keep.size(); r++) { const mpp::Task& t = g_prj.tasks[r]; if (!g_keep[r] || t.isNull || t.summary) continue; std::wstring v = colorValue(t, (int)r, col); if (!v.empty()) counts[v]++; }
    std::vector<std::pair<std::wstring, int>> sorted(counts.begin(), counts.end());
    std::sort(sorted.begin(), sorted.end(), [](const auto& a, const auto& b) { return a.second != b.second ? a.second > b.second : collate(a.first, b.first) < 0; });
    bool others = false;
    for (size_t i = 0; i < sorted.size(); i++) { if (i < 12) { g_colorMap[sorted[i].first] = PALETTE[i]; g_legend.push_back({ sorted[i].first, PALETTE[i] }); } else { others = true; break; } }
    if (others) g_legend.push_back({ L"ostatní", C_OTHERS });
}
static bool barColor(const mpp::Task& t, int idx, COLORREF& out) {
    if (g_colorKey.empty() || t.summary || t.isNull) return false;
    int col = colIndex(g_colorKey); if (col < 0) return false;
    std::wstring v = colorValue(t, idx, col); if (v.empty()) return false;
    auto it = g_colorMap.find(v); out = it == g_colorMap.end() ? C_OTHERS : it->second; return true;
}
