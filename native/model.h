// model.h - datový model harmonogramu (úkoly, zdroje, přiřazení, kalendáře), práce s datem, formátování hodnot
// a přenos modelu mezi knihovnou mppread.dll a aplikací (serializace).
// Copyright (c) 2026 Milan Knor (M3K Soft). Licence MIT (viz LICENSE-MIT.txt) - sdílí ho aplikace M3K Project View i knihovna mppread.dll.
#pragma once
#ifndef NOMINMAX
#define NOMINMAX
#endif
#ifndef WIN32_LEAN_AND_MEAN
#define WIN32_LEAN_AND_MEAN
#endif
#include <windows.h>
#include <algorithm>
#include <climits>
#include <cmath>
#include <cstdint>
#include <cstring>
#include <map>
#include <set>
#include <stdexcept>
#include <string>
#include <type_traits>
#include <vector>

namespace mpp {

typedef int64_t Time; // sekundy od 31. 12. 1983 00:00 (epocha MS Project); 0 = neuvedeno

// ---------- kalendářní data: algoritmy days_from_civil / civil_from_days, Howard Hinnant, public domain
// (https://howardhinnant.github.io/date_algorithms.html), přepsáno podle popisu na této stránce ----------
inline int64_t daysFromCivil(int y, unsigned m, unsigned d) {
    y -= m <= 2;
    const int era = (y >= 0 ? y : y - 399) / 400;
    const unsigned yoe = (unsigned)(y - era * 400);
    const unsigned doy = (153 * (m + (m > 2 ? -3 : 9)) + 2) / 5 + d - 1;
    const unsigned doe = yoe * 365 + yoe / 4 - yoe / 100 + doy;
    return (int64_t)era * 146097 + (int64_t)doe - 719468;
}
inline void civilFromDays(int64_t z, int& y, unsigned& m, unsigned& d) {
    z += 719468;
    const int64_t era = (z >= 0 ? z : z - 146096) / 146097;
    const unsigned doe = (unsigned)(z - era * 146097);
    const unsigned yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365;
    const int64_t yy = (int64_t)yoe + era * 400;
    const unsigned doy = doe - (365 * yoe + yoe / 4 - yoe / 100);
    const unsigned mp = (5 * doy + 2) / 153;
    d = doy - (153 * mp + 2) / 5 + 1;
    m = mp + (mp < 10 ? 3 : -9);
    y = (int)(yy + (m <= 2));
}
static const int64_t EPOCH_UNIX_DAYS = 5112; // 31. 12. 1983 jako počet dní od 1. 1. 1970
inline unsigned dowOfUnixDay(int64_t ud) { return (unsigned)(((ud % 7) + 7 + 3) % 7); } // 0 = pondělí (1.1.1970 byl čtvrtek)

struct Date { int y = 0; unsigned m = 0, d = 0, hh = 0, mm = 0; int64_t unixDays = 0; unsigned dow = 0; /* 0 = pondělí */ };
inline Date toDate(Time t) {
    Date r;
    int64_t days = t / 86400, sec = t % 86400;
    r.unixDays = days + EPOCH_UNIX_DAYS;
    civilFromDays(r.unixDays, r.y, r.m, r.d);
    r.hh = (unsigned)(sec / 3600);
    r.mm = (unsigned)((sec % 3600) / 60);
    r.dow = dowOfUnixDay(r.unixDays);
    return r;
}
inline Time fromUnixDays(int64_t unixDays) { return (unixDays - EPOCH_UNIX_DAYS) * 86400; }
inline int64_t toUnixDays(Time t) { return t / 86400 + EPOCH_UNIX_DAYS; }
inline Time makeTime(int y, unsigned m, unsigned d, unsigned hh = 0, unsigned mm = 0, unsigned ss = 0) { return fromUnixDays(daysFromCivil(y, m, d)) + hh * 3600 + mm * 60 + ss; }

inline std::wstring fmtDate(Time t, bool withTime = false) {
    if (t == 0) return L"";
    Date d = toDate(t);
    wchar_t b[40];
    if (withTime) swprintf(b, 40, L"%u.%u.%d %u:%02u", d.d, d.m, d.y, d.hh, d.mm);
    else swprintf(b, 40, L"%u.%u.%d", d.d, d.m, d.y);
    return b;
}

inline std::wstring ansiToWide(const std::string& s, int cp) {
    if (s.empty()) return L"";
    int n = MultiByteToWideChar((UINT)cp, 0, s.data(), (int)s.size(), nullptr, 0);
    if (n <= 0 && cp != 1252) n = MultiByteToWideChar(1252, 0, s.data(), (int)s.size(), nullptr, 0), cp = 1252;
    std::wstring w((size_t)std::max(0, n), 0);
    if (n > 0) MultiByteToWideChar((UINT)cp, 0, s.data(), (int)s.size(), &w[0], n);
    return w;
}
inline std::string wideToUtf8(const std::wstring& w) {
    if (w.empty()) return "";
    int n = WideCharToMultiByte(CP_UTF8, 0, w.data(), (int)w.size(), nullptr, 0, nullptr, nullptr);
    std::string s((size_t)std::max(0, n), 0);
    if (n > 0) WideCharToMultiByte(CP_UTF8, 0, w.data(), (int)w.size(), &s[0], n, nullptr, nullptr);
    return s;
}

// ---------- doby trvání ----------
// Jednotky (kód MPP): 3 min, 4 upl. min, 5 hod, 6 upl. hod, 7 dny, 8 upl. dny, 9 týdny, 10 upl. týdny, 11 měs, 12 upl. měs, 21 = výchozí projektu
// Surová hodnota je vždy v desetinách minuty; jednotky ovlivňují jen zobrazení.
struct Duration { int raw = -1; int units = 7; bool valid() const { return raw != -1; } };
inline int unitsOf(int v, int def) {
    v &= 0x1F;
    switch (v) {
    case 3: case 4: case 5: case 6: case 7: case 8: case 9: case 10: case 11: case 12: case 19: case 20: return v;
    case 21: return def;
    default: return 7;
    }
}

struct Project;
double durationValue(const Duration& d, const Project& p);
std::wstring fmtDuration(const Duration& d, const Project& p, bool estimated = false);

enum CustomKind { CK_TEXT, CK_NUMBER, CK_FLAG, CK_DATE, CK_DURATION, CK_COST, CK_START, CK_FINISH, CK_OUTLINE_CODE };
inline const wchar_t* customKindName(CustomKind k) {
    static const wchar_t* n[] = { L"Text", L"Číslo", L"Příznak", L"Datum", L"Doba trvání", L"Náklady", L"Zahájení", L"Dokončení", L"Kód osnovy" };
    return n[k];
}

// ---------- datový model ----------
struct Relation { int predUid = 0; int type = 1; /* 0 FF, 1 FS, 2 SF, 3 SS */ Duration lag; };
struct Assignment { int uid = 0, taskUid = 0, rscUid = 0; double units = 1; /* 1.0 = 100 % */ double workHours = 0, actualWorkHours = 0, cost = 0; Time start = 0, finish = 0; };
struct Resource {
    int uid = 0, id = 0; std::wstring name, initials, group, email; int type = 0; /* 0 práce, 1 materiál, 2 náklady */
    double maxUnits = 1, stdRate = 0, ovtRate = 0, costPerUse = 0, workHours = 0, cost = 0;
    std::vector<int> assignments;
};
struct Baseline { Time start = 0, finish = 0; Duration duration; double workHours = 0, cost = 0; bool any() const { return start || finish || duration.valid() || workHours != 0 || cost != 0; } };
struct CustomValue { std::wstring text; double num = 0; Time date = 0; bool flag = false; Duration dur; bool set = false; };
struct CustomField { CustomKind kind; int n; int id; std::wstring alias; std::wstring title() const { std::wstring base = std::wstring(customKindName(kind)) + std::to_wstring(n); return alias.empty() ? base : alias + L" (" + base + L")"; } };
struct Task {
    int uid = 0, id = 0;
    std::wstring name, wbs, notes, resourceNames, outlineNumber, contact, calendarName, subproject;
    bool nameSet = false;
    int level = 1;
    Time start = 0, finish = 0, actualStart = 0, actualFinish = 0, deadline = 0, created = 0,
         constraintDate = 0, earlyStart = 0, earlyFinish = 0, lateStart = 0, lateFinish = 0;
    Time& baselineStart() { return baselines[0].start; } Time& baselineFinish() { return baselines[0].finish; }
    Time baselineStartV() const { return baselines[0].start; } Time baselineFinishV() const { return baselines[0].finish; }
    Duration duration, actualDuration, remainingDuration;
    Baseline baselines[11];
    int pctComplete = -1, pctWork = -1;
    int constraintType = 0; // 0 ASAP,1 ALAP,2 MSO,3 MFO,4 SNET,5 SNLT,6 FNET,7 FNLT
    int priority = 500, taskType = 0; // 0 pevné jednotky, 1 pevná doba, 2 pevná práce
    double workMinutes = 0, actualWorkMinutes = 0, remainingWorkMinutes = 0, cost = 0, actualCost = 0, remainingCost = 0, fixedCost = 0;
    int startSlackRaw = INT32_MIN, finishSlackRaw = INT32_MIN, freeSlackRaw = INT32_MIN, totalSlackRaw = INT32_MIN; // desetiny minuty; INT32_MIN = neuvedeno
    int calendarUid = -1;
    bool milestone = false, summary = false, manual = false, active = true, expanded = true, isNull = false, estimated = false, critical = false, effortDriven = false, criticalFromFile = false;
    int parentUid = -1;
    int parent = -1;              // index v Project::tasks
    std::vector<int> children;    // indexy
    std::vector<Relation> preds;
    std::vector<int> assignments; // indexy v Project::assignments
    std::map<int, CustomValue> custom; // id pole -> hodnota
    const CustomValue* customValue(int id) const { auto it = custom.find(id); return it == custom.end() ? nullptr : &it->second; }
};

// Kalendář: pracovní dny v týdnu + výjimky (svátky, dovolené, mimořádné směny).
// Opakované výjimky vyhodnocuje knihovna mppread.dll (expandCalendar v mpp.h); aplikace dostane hotový seznam dnů.
struct CalException {
    int64_t from = 0, to = 0;   // unixové dny, včetně
    bool working = false;
    int rec = 0;                // 0 = prostý rozsah, jinak kód opakování MPP (1/7 denně, 6 týdně, 4/5 měsíčně, 2/3 ročně)
    int freq = 1, dayNumber = 0, dayOfWeek = 0 /* 1 = neděle */, month = 0, weekBits = 0;
    int minutes = 0;            // pracovní minuty (u pracovních výjimek)
    std::wstring name;
};
struct DayException { int64_t day = 0; int ex = -1; }; // den, na který připadá výjimka -> index v Calendar::exceptions
struct Calendar {
    int uid = 0; std::wstring name; int baseUid = -1;
    bool working[7] = { false, true, true, true, true, true, false }; // index 0 = neděle (konvence MPXJ)
    int minutes[7] = { 0, 480, 480, 480, 480, 480, 0 };
    std::wstring hoursText[7];
    std::vector<CalException> exceptions;

    std::vector<DayException> days; // rozvinuté dny výjimek, seřazené podle dne (vypočte mppread.dll)

    const CalException* exceptionAt(int64_t ud) const {
        auto it = std::lower_bound(days.begin(), days.end(), ud, [](const DayException& x, int64_t v) { return x.day < v; });
        return it != days.end() && it->day == ud && it->ex >= 0 && it->ex < (int)exceptions.size() ? &exceptions[it->ex] : nullptr;
    }
    bool isWorking(int64_t ud) const {
        if (const CalException* e = exceptionAt(ud)) return e->working;
        return working[(dowOfUnixDay(ud) + 1) % 7];
    }
    int minutesOn(int64_t ud) const {
        if (const CalException* e = exceptionAt(ud)) return e->working ? (e->minutes > 0 ? e->minutes : 480) : 0;
        return working[(dowOfUnixDay(ud) + 1) % 7] ? minutes[(dowOfUnixDay(ud) + 1) % 7] : 0;
    }
    int64_t workingDaysBetween(int64_t a, int64_t b) const {
        if (b < a) return -workingDaysBetween(b, a);
        if (b - a > 36600) b = a + 36600; // ponytail: strop 100 let, ochrana proti nesmyslným datům
        int64_t n = 0;
        for (int64_t d = a; d < b; d++) if (isWorking(d)) n++;
        return n;
    }
    double workingHoursBetween(int64_t a, int64_t b) const { // [a, b)
        if (b <= a) return 0; if (b - a > 36600) b = a + 36600;
        double h = 0; for (int64_t d = a; d < b; d++) h += minutesOn(d) / 60.0; return h;
    }
};

struct Project {
    int mppType = 14;             // 12 = Project 2007, 14 = Project 2010+, 0 = XML (MSPDI)
    std::wstring title, filePath, author, manager, company, subject, comments, keywords, category, currency, format, appName;
    int appVersion = 14;
    Time startDate = 0, finishDate = 0, statusDate = 0, currentDate = 0, creationDate = 0, lastSaved = 0, baselineDate = 0;
    int minutesPerDay = 480, minutesPerWeek = 2400, daysPerMonth = 20, defaultUnits = 7;
    int criticalSlackLimitDays = 0;
    bool encrypted = false, scheduleFromStart = true;
    std::vector<Task> tasks;      // seřazeno podle ID
    std::vector<int> roots;
    std::map<int, int> uidIndex;
    std::vector<Resource> resources;
    std::map<int, int> rscIndex;
    std::vector<Assignment> assignments;
    std::vector<Calendar> calendars;
    int defaultCalendar = -1;     // index v calendars; -1 = vestavěný Po–Pá
    Calendar builtin;
    std::vector<CustomField> customFields; // vlastní pole, která mají alias nebo hodnotu
    std::vector<int> baselinesUsed;        // 0..10 přítomné v souboru
    std::map<int, std::wstring> aliases;   // id pole -> alias (jen úkoly)

    const Task* byUid(int uid) const { auto it = uidIndex.find(uid); return it == uidIndex.end() ? nullptr : &tasks[it->second]; }
    Task* byUid(int uid) { auto it = uidIndex.find(uid); return it == uidIndex.end() ? nullptr : &tasks[it->second]; }
    const Resource* rscByUid(int uid) const { auto it = rscIndex.find(uid); return it == rscIndex.end() ? nullptr : &resources[it->second]; }
    const Calendar& calendar() const { return defaultCalendar >= 0 ? calendars[defaultCalendar] : builtin; }
    const Calendar* calendarByUid(int uid) const { for (auto& c : calendars) if (c.uid == uid) return &c; return nullptr; }
    void reindex() { uidIndex.clear(); for (size_t i = 0; i < tasks.size(); i++) uidIndex[tasks[i].uid] = (int)i; }
};

inline double durationValue(const Duration& d, const Project& p) {
    double v = d.raw;
    switch (d.units) {
    case 3: case 4: return v / 10;
    case 5: case 6: return v / 600;
    case 7: return p.minutesPerDay ? v / (p.minutesPerDay * 10.0) : 0;
    case 8: return v / 14400;
    case 9: return p.minutesPerWeek ? v / (p.minutesPerWeek * 10.0) : 0;
    case 10: return v / 100800;
    case 11: return (p.minutesPerDay && p.daysPerMonth) ? v / (p.minutesPerDay * (double)p.daysPerMonth * 10.0) : 0;
    case 12: return v / 432000;
    default: return v;
    }
}
inline double durationDays(int raw, const Project& p) { return p.minutesPerDay ? raw / (p.minutesPerDay * 10.0) : 0; }
inline std::wstring fmtNumber(double v) {
    wchar_t b[32];
    if (std::fabs(v - std::round(v)) < 0.005) swprintf(b, 32, L"%.0f", v);
    else { swprintf(b, 32, L"%.2f", v); size_t n = wcslen(b); while (n > 0 && b[n - 1] == L'0') b[--n] = 0; if (n > 0 && b[n - 1] == L'.') b[--n] = 0; }
    for (wchar_t* c = b; *c; ++c) if (*c == L'.') *c = L',';
    return b;
}
inline std::wstring fmtMoney(double v, const Project& p) {
    if (v == 0) return L"";
    wchar_t b[48]; swprintf(b, 48, L"%.2f", v);
    std::wstring s = b; size_t dot = s.find(L'.'); std::wstring ip = s.substr(0, dot), fp = s.substr(dot + 1);
    std::wstring g; int cnt = 0; for (size_t i = ip.size(); i-- > 0;) { g.insert(g.begin(), ip[i]); if (++cnt % 3 == 0 && i > 0 && ip[i - 1] != L'-') g.insert(g.begin(), L' '); }
    return g + L"," + fp + (p.currency.empty() ? L"" : L" " + p.currency);
}
inline std::wstring fmtDuration(const Duration& d, const Project& p, bool estimated) {
    if (!d.valid()) return L"";
    static const wchar_t* names[] = { L"", L"", L"", L"min", L"kal. min", L"h", L"kal. h", L"d", L"kal. d", L"týd", L"kal. týd", L"měs", L"kal. měs" };
    std::wstring u = (d.units >= 3 && d.units <= 12) ? names[d.units] : (d.units == 19 || d.units == 20 ? L"%" : L"");
    return fmtNumber(durationValue(d, p)) + L" " + u + (estimated ? L"?" : L"");
}
inline std::wstring fmtHours(double h) { return h == 0 ? L"" : fmtNumber(h) + L" h"; }
inline std::wstring fmtSlackRaw(int raw, const Project& p) { if (raw == INT32_MIN) return L""; Duration d; d.raw = raw; d.units = 7; return fmtDuration(d, p); }
inline const wchar_t* constraintName(int c) {
    static const wchar_t* n[] = { L"Co nejdříve", L"Co nejpozději", L"Musí začít", L"Musí skončit", L"Zahájit nejdříve", L"Zahájit nejpozději", L"Dokončit nejdříve", L"Dokončit nejpozději" };
    return c >= 0 && c < 8 ? n[c] : L"—";
}
inline std::wstring priorityName(int v) {
    const wchar_t* n = v >= 1000 ? L"Nevyrovnávat" : v >= 900 ? L"Nejvyšší" : v >= 800 ? L"Velmi vysoká" : v >= 700 ? L"Vyšší" : v >= 600 ? L"Vysoká" : v >= 500 ? L"Střední" : v >= 400 ? L"Nízká" : v >= 300 ? L"Nižší" : v >= 200 ? L"Velmi nízká" : L"Nejnižší";
    return std::wstring(n) + L" (" + std::to_wstring(v) + L")";
}
inline const wchar_t* taskTypeName(int t) { return t == 1 ? L"Pevná doba trvání" : t == 2 ? L"Pevná práce" : L"Pevné jednotky"; }
inline const wchar_t* resourceTypeName(int t) { return t == 1 ? L"Materiálový" : t == 2 ? L"Nákladový" : L"Pracovní"; }
inline const wchar_t* taskKindName(const Task& t) { return !t.active ? L"Neaktivní úkol" : !t.subproject.empty() ? L"Vložený podprojekt" : t.summary ? L"Souhrnný úkol" : t.milestone ? L"Milník" : L"Úkol"; }
inline std::wstring fmtCustom(const CustomField& f, const CustomValue* v, const Project& p) {
    if (!v || !v->set) return L"";
    switch (f.kind) {
    case CK_TEXT: case CK_OUTLINE_CODE: return v->text;
    case CK_NUMBER: return fmtNumber(v->num);
    case CK_FLAG: return v->flag ? L"ano" : L"";
    case CK_DATE: case CK_START: case CK_FINISH: return fmtDate(v->date);
    case CK_DURATION: return fmtDuration(v->dur, p);
    case CK_COST: return fmtMoney(v->num, p);
    }
    return L"";
}

// Text předchůdců ve stylu MS Project: "3", "5SS", "7FS+2 d"
inline std::wstring fmtPredecessors(const Task& t, const Project& p) {
    static const wchar_t* types[] = { L"FF", L"FS", L"SF", L"SS" };
    std::wstring s;
    for (const Relation& r : t.preds) {
        const Task* pr = p.byUid(r.predUid);
        if (!pr) continue;
        if (!s.empty()) s += L";";
        s += std::to_wstring(pr->id);
        bool lag = r.lag.valid() && r.lag.raw != 0;
        if (r.type != 1 || lag) s += (r.type >= 0 && r.type < 4) ? types[r.type] : L"";
        if (lag) { double v = durationValue(r.lag, p); Duration a = r.lag; a.raw = std::abs(a.raw); s += (v < 0 ? L"-" : L"+") + fmtDuration(a, p); }
    }
    return s;
}

#include "serial.h"
} // namespace mpp
