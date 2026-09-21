// mppdump.cpp - konzolový výpis obsahu .mpp / .xml (pro ověření čtečky a hromadné testy). Licence: GNU LGPL 2.1 nebo pozdější (obsahuje čtečku mpp.h).
// Použití: mppdump [-q] [-n] [-r] [-c] soubor [dalsi ...]
//   -q  jen jeden řádek na soubor   -n  poznámky   -r  zdroje a kalendář   -c  vlastní pole a směrné plány
//   -dll  číst přes mppread.dll (jako aplikace) místo přímo   -rt  kontrola serializace (zápis -> čtení -> zápis = stejné bajty)
#define NOMINMAX
#define _CRT_SECURE_NO_WARNINGS
#include "mpp.h"
#include "mppload.h"
#include <cstdio>

static void out(const std::wstring& s) { fputs(mpp::wideToUtf8(s).c_str(), stdout); }

#define CHECK(x) do { if (!(x)) { fputs("SELFCHECK FAILED: " #x "\n", stderr); exit(3); } } while (0)
static void selfCheck() {
    CHECK(mpp::daysFromCivil(1970, 1, 1) == 0);
    CHECK(mpp::daysFromCivil(1983, 12, 31) == mpp::EPOCH_UNIX_DAYS);
    int y; unsigned m, d;
    mpp::civilFromDays(mpp::EPOCH_UNIX_DAYS, y, m, d);
    CHECK(y == 1983 && m == 12 && d == 31);
    mpp::Time t = mpp::fromUnixDays(mpp::daysFromCivil(2006, 8, 23)) + 8 * 3600;
    mpp::Date dd = mpp::toDate(t);
    CHECK(dd.y == 2006 && dd.m == 8 && dd.d == 23 && dd.hh == 8 && dd.dow == 2);
    CHECK(mpp::parseXmlDate(L"2006-08-23T08:00:00") == t);
    CHECK(mpp::parseXmlDurationRaw(L"PT8H0M0S") == 4800);
    CHECK(mpp::parseXmlDurationRaw(L"PT1H30M0S") == 900);

    mpp::Calendar c;
    mpp::CalException h; h.rec = 2; h.month = 10; h.dayNumber = 28; h.from = mpp::daysFromCivil(2000, 1, 1); h.to = mpp::daysFromCivil(2100, 1, 1); c.exceptions.push_back(h);
    mpp::CalException v; v.from = mpp::daysFromCivil(2024, 7, 1); v.to = mpp::daysFromCivil(2024, 7, 3); c.exceptions.push_back(v);
    mpp::CalException s; s.from = s.to = mpp::daysFromCivil(2024, 7, 6); s.working = true; c.exceptions.push_back(s);
    mpp::expandCalendar(c);
    CHECK(!c.isWorking(mpp::daysFromCivil(2024, 10, 28)));
    CHECK(c.isWorking(mpp::daysFromCivil(2024, 10, 29)));
    CHECK(!c.isWorking(mpp::daysFromCivil(2024, 7, 2)));
    CHECK(c.isWorking(mpp::daysFromCivil(2024, 7, 6)));
    CHECK(!c.isWorking(mpp::daysFromCivil(2024, 7, 7)));
    CHECK(c.workingDaysBetween(mpp::daysFromCivil(2024, 7, 1), mpp::daysFromCivil(2024, 7, 8)) == 3);
    CHECK(c.workingDaysBetween(mpp::daysFromCivil(2024, 7, 8), mpp::daysFromCivil(2024, 7, 1)) == -3);
    mpp::CalException lastFri; lastFri.rec = 5; lastFri.dayOfWeek = 6; lastFri.dayNumber = 5; lastFri.from = mpp::daysFromCivil(2024, 1, 1); lastFri.to = mpp::daysFromCivil(2024, 12, 31);
    mpp::Calendar c2; c2.exceptions.push_back(lastFri); mpp::expandCalendar(c2);
    CHECK(!c2.isWorking(mpp::daysFromCivil(2024, 8, 30)));
    CHECK(c2.isWorking(mpp::daysFromCivil(2024, 8, 23)));

    std::wstring r = mpp::rtfToText("{\\rtf1\\ansi\\ansicpg1250\\deff0{\\fonttbl{\\f0 Arial;}}{\\colortbl;\\red0\\green0\\blue0;}\\f0\\fs20 Zkou\\'9aka \\u268?ty\\'f8i\\par druh\\'fd \\{\\}\\line}");
    CHECK(r == L"Zkouška Čtyři\ndruhý {}");
    CHECK(mpp::rtfToText("plain") == L"plain");
    mpp::Project pp; pp.currency = L"Kč";
    CHECK(mpp::fmtMoney(1234567.5, pp) == L"1 234 567,50 Kč");
}

int wmain(int argc, wchar_t** argv) {
    SetConsoleOutputCP(CP_UTF8);
    selfCheck();
    bool quiet = false, showNotes = false, showRsc = false, showCustom = false, workBitmap = false, viaDll = false, roundTrip = false;
    std::vector<std::wstring> files;
    for (int i = 1; i < argc; i++) {
        if (wcscmp(argv[i], L"-q") == 0) quiet = true;
        else if (wcscmp(argv[i], L"-n") == 0) showNotes = true;
        else if (wcscmp(argv[i], L"-r") == 0) showRsc = true;
        else if (wcscmp(argv[i], L"-c") == 0) showCustom = true;
        else if (wcscmp(argv[i], L"-w") == 0) workBitmap = true;
        else if (wcscmp(argv[i], L"-dll") == 0) viaDll = true;
        else if (wcscmp(argv[i], L"-rt") == 0) roundTrip = true;
        else files.push_back(argv[i]);
    }
    if (files.empty()) { out(L"Pouziti: mppdump [-q] [-n] [-r] [-c] soubor ...\n"); return 1; }

    int failed = 0;
    for (const std::wstring& f : files) {
        try {
            mpp::Project p = viaDll ? mpp::loadProject(f) : mpp::open(f);
            if (roundTrip) {
                std::vector<uint8_t> a = mpp::serializeProject(p); mpp::Project q = mpp::deserializeProject(a.data(), a.size()); std::vector<uint8_t> b = mpp::serializeProject(q);
                if (a != b) throw std::runtime_error("serializace neni stabilni");
                p = std::move(q);
            }
            if (workBitmap) { // -w: pracovní dny výchozího kalendáře 1998–2030 (porovnání MPP x XML)
                const mpp::Calendar& c = p.calendar(); std::wstring s;
                for (int64_t d = mpp::daysFromCivil(1998, 1, 1); d < mpp::daysFromCivil(2031, 1, 1); d++) s += c.isWorking(d) ? L'1' : L'0';
                out(c.name + L" " + s + L"\n"); continue;
            }
            if (quiet) {
                int named = 0, crit = 0, notes = 0; for (auto& t : p.tasks) { if (!t.isNull && t.nameSet) named++; if (t.critical) crit++; if (!t.notes.empty()) notes++; }
                wchar_t b[400]; swprintf(b, 400, L"OK   %-45s %-10s v%d ukolu=%d pojm=%d krit=%d pozn=%d zdroju=%d prirazeni=%d kal=%d vlastni=%d smerne=%d start=%s%s\n", f.c_str(), p.format.c_str(), p.appVersion, (int)p.tasks.size(), named, crit, notes, (int)p.resources.size(), (int)p.assignments.size(), (int)p.calendars.size(), (int)p.customFields.size(), (int)p.baselinesUsed.size(), mpp::fmtDate(p.startDate).c_str(), p.encrypted ? L" [xor]" : L"");
                out(b);
                continue;
            }
            out(L"Soubor: " + f + L"\n");
            out(L"Titul: " + p.title + L"  | " + p.format + L" / Project " + std::to_wstring(p.appVersion) + L"  | zahajeni " + mpp::fmtDate(p.startDate) + L"  dokonceni " + mpp::fmtDate(p.finishDate) + L"  stav k " + mpp::fmtDate(p.statusDate) + L"  mena " + p.currency + L"\n");
            out(L"min/den=" + std::to_wstring(p.minutesPerDay) + L" min/tyden=" + std::to_wstring(p.minutesPerWeek) + L" dni/mesic=" + std::to_wstring(p.daysPerMonth) + L" krit.limit=" + std::to_wstring(p.criticalSlackLimitDays) + L"d ukolu=" + std::to_wstring(p.tasks.size()) + L" zdroju=" + std::to_wstring(p.resources.size()) + L" prirazeni=" + std::to_wstring(p.assignments.size()) + L" kalendaru=" + std::to_wstring(p.calendars.size()) + L"\n");
            if (showCustom) {
                for (auto& cf : p.customFields) out(L"  vlastni pole id=" + std::to_wstring(cf.id) + L" " + cf.title() + L"\n");
                std::wstring bs; for (int n : p.baselinesUsed) bs += std::to_wstring(n) + L" ";
                out(L"  smerne plany: " + bs + L"\n");
            }
            if (showRsc) {
                for (auto& r : p.resources) out(L"  zdroj uid=" + std::to_wstring(r.uid) + L" id=" + std::to_wstring(r.id) + L" [" + mpp::resourceTypeName(r.type) + L"] " + r.name + (r.group.empty() ? L"" : L" (" + r.group + L")") + L" max=" + mpp::fmtNumber(r.maxUnits * 100) + L"% sazba=" + mpp::fmtNumber(r.stdRate) + L" prace=" + mpp::fmtHours(r.workHours) + L" naklady=" + mpp::fmtMoney(r.cost, p) + L" prirazeni=" + std::to_wstring(r.assignments.size()) + L"\n");
                for (auto& c : p.calendars) {
                    std::wstring w; for (int i = 0; i < 7; i++) w += c.working[i] ? L"P" : L"-";
                    out(L"  kalendar uid=" + std::to_wstring(c.uid) + L" " + c.name + L" [Ne..So " + w + L"] Po=" + c.hoursText[1] + L" vyjimek=" + std::to_wstring(c.exceptions.size()) + (p.defaultCalendar >= 0 && &c == &p.calendars[p.defaultCalendar] ? L" (vychozi)" : L"") + L"\n");
                    for (auto& e : c.exceptions) out(L"      " + mpp::fmtDate(mpp::fromUnixDays(e.from)) + L" - " + mpp::fmtDate(mpp::fromUnixDays(e.to)) + (e.working ? L" pracovni" : L" volno") + L" rec=" + std::to_wstring(e.rec) + L" " + e.name + L"\n");
                }
            }
            out(L"  ID  UID Lvl Osn.   Nazev                                    Zahajeni         Dokonceni        Trvani      %   Sm.zah.    Sm.dok.    Predch.   Pozn.  Rezerva  Zdroje\n");
            for (const mpp::Task& t : p.tasks) {
                wchar_t b[1024];
                if (t.isNull) { swprintf(b, 1024, L"%4d %4d     (prazdny radek)\n", t.id, t.uid); out(b); continue; }
                std::wstring name = std::wstring((size_t)std::max(0, t.level - 1) * 2, L' ') + t.name;
                if (name.size() > 40) name = name.substr(0, 39) + L"~";
                std::wstring flags;
                if (t.summary) flags += L"S"; if (t.milestone) flags += L"M"; if (t.manual) flags += L"R"; if (!t.active) flags += L"X"; if (!t.expanded) flags += L"-"; if (t.critical) flags += L"K"; if (!t.notes.empty()) flags += L"n"; if (!t.custom.empty()) flags += L"c";
                swprintf(b, 1024, L"%4d %4d %3d %-6s %-40s %-16s %-16s %-11s %3s %-10s %-10s %-9s %-6s %-8s %s\n", t.id, t.uid, t.level, t.outlineNumber.c_str(), name.c_str(),
                    mpp::fmtDate(t.start, true).c_str(), mpp::fmtDate(t.finish, true).c_str(), mpp::fmtDuration(t.duration, p, t.estimated).c_str(),
                    t.pctComplete < 0 ? L"" : std::to_wstring(t.pctComplete).c_str(),
                    mpp::fmtDate(t.baselineStartV()).c_str(), mpp::fmtDate(t.baselineFinishV()).c_str(), mpp::fmtPredecessors(t, p).c_str(), flags.c_str(), mpp::fmtSlackRaw(t.totalSlackRaw, p).c_str(), t.resourceNames.c_str());
                out(b);
                if (showNotes && !t.notes.empty()) out(L"        POZN: " + t.notes + L"\n");
                if (showCustom) for (auto& cf : p.customFields) { std::wstring v = mpp::fmtCustom(cf, t.customValue(cf.id), p); if (!v.empty()) out(L"        " + cf.title() + L" = " + v + L"\n"); }
                if (showCustom) for (int n : p.baselinesUsed) if (n > 0 && t.baselines[n].any()) out(L"        ZP" + std::to_wstring(n) + L": " + mpp::fmtDate(t.baselines[n].start) + L" - " + mpp::fmtDate(t.baselines[n].finish) + L" " + mpp::fmtDuration(t.baselines[n].duration, p) + L" prace=" + mpp::fmtHours(t.baselines[n].workHours) + L"\n");
            }
        } catch (const std::exception& e) {
            failed++;
            out(L"ERR  " + f + L": " + mpp::ansiToWide(e.what(), CP_UTF8) + L"\n");
        }
    }
    return failed ? 2 : 0;
}
