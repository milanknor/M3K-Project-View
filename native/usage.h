// usage.h - vytížení zdrojů: tabulka zdroj × období. Bez časově rozložených dat (nečteme je) se práce přiřazení
// rozkládá rovnoměrně na pracovní dny mezi zahájením a dokončením přiřazení podle kalendáře projektu.
#pragma once
#include "gantt.h"

enum UsagePeriod { UP_DAYS, UP_WEEKS, UP_MONTHS };
struct Usage {
    UsagePeriod period = UP_WEEKS;
    std::vector<int64_t> bucketStart; // unixové dny, poslední = konec
    std::vector<std::wstring> headers;
    std::vector<int> rscIdx;                 // řádky = zdroje (index v g_prj.resources)
    std::vector<std::vector<double>> work;   // hodiny
    std::vector<std::vector<double>> cap;    // kapacita v hodinách (prázdné = nákladový/materiálový zdroj)
    int overCells = 0;
    bool isOver(int r, int c) const { return !cap[r].empty() && work[r][c] > cap[r][c] + 0.01; }
    bool rowOver(int r) const { for (size_t c = 0; c + 1 < bucketStart.size(); c++) if (isOver(r, (int)c)) return true; return false; }
};
static const int MAX_BUCKETS = 420;
// Rozloží hodiny přiřazení do kbelíků; vrací vektor délky buckets
static std::vector<double> spreadWork(const mpp::Assignment& a, const std::vector<int64_t>& b) {
    size_t n = b.size() - 1; std::vector<double> out(n, 0.0);
    const mpp::Task* t = g_prj.byUid(a.taskUid);
    Time s = a.start ? a.start : (t ? t->start : 0), f = a.finish ? a.finish : (t ? t->finish : 0);
    if (!s || !f || a.workHours <= 0) return out;
    int64_t d0 = mpp::toUnixDays(s), d1 = mpp::toUnixDays(f); if (d1 < d0) d1 = d0;
    if (d1 - d0 > 36600) d1 = d0 + 36600;
    const mpp::Calendar& cal = g_prj.calendar();
    int wd = 0; for (int64_t d = d0; d <= d1; d++) if (cal.isWorking(d)) wd++;
    bool useCal = wd > 0; double per = a.workHours / (useCal ? wd : (double)(d1 - d0 + 1));
    for (int64_t d = d0; d <= d1; d++) {
        if (useCal && !cal.isWorking(d)) continue;
        if (d < b.front() || d >= b.back()) continue;
        size_t k = (size_t)(std::upper_bound(b.begin(), b.end(), d) - b.begin()) - 1;
        if (k < n) out[k] += per;
    }
    return out;
}
static Usage computeUsage(UsagePeriod period) {
    Usage u; u.period = period;
    int64_t mn = 0, mx = 0;
    for (const mpp::Assignment& a : g_prj.assignments) {
        const mpp::Task* t = g_prj.byUid(a.taskUid); if (!t || !g_prj.rscByUid(a.rscUid)) continue;
        Time s = a.start ? a.start : t->start, f = a.finish ? a.finish : t->finish; if (!s || !f) continue;
        int64_t sd = mpp::toUnixDays(s), fd = mpp::toUnixDays(f);
        if (!mn || sd < mn) mn = sd; if (!mx || fd > mx) mx = fd;
    }
    if (!mn) return u;
    auto build = [&](UsagePeriod per) {
        std::vector<int64_t> b; int64_t d = mn;
        if (per == UP_WEEKS) d -= mpp::dowOfUnixDay(d); else if (per == UP_MONTHS) { int y; unsigned m, dd; mpp::civilFromDays(d, y, m, dd); d = mpp::daysFromCivil(y, m, 1); }
        int guard = 0;
        while (d <= mx && guard++ < 100000) {
            b.push_back(d);
            if (per == UP_DAYS) d += 1; else if (per == UP_WEEKS) d += 7; else { int y; unsigned m, dd; mpp::civilFromDays(d, y, m, dd); if (m == 12) { y++; m = 1; } else m++; d = mpp::daysFromCivil(y, m, 1); }
        }
        b.push_back(d); return b;
    };
    std::vector<int64_t> b = build(period);
    while (b.size() - 1 > MAX_BUCKETS && period != UP_MONTHS) { period = period == UP_DAYS ? UP_WEEKS : UP_MONTHS; b = build(period); }
    if (b.size() - 1 > MAX_BUCKETS) b.resize(MAX_BUCKETS + 1);
    u.period = period; u.bucketStart = b;
    for (size_t i = 0; i + 1 < b.size(); i++) { int y; unsigned m, dd; mpp::civilFromDays(b[i], y, m, dd); wchar_t h[24]; if (period == UP_MONTHS) swprintf(h, 24, L"%s %02d", MONTHS_S[m - 1], y % 100); else swprintf(h, 24, L"%u.%u.", dd, m); u.headers.push_back(h); }
    size_t n = b.size() - 1;
    const mpp::Calendar& cal = g_prj.calendar();
    for (size_t ri = 0; ri < g_prj.resources.size(); ri++) {
        const mpp::Resource& r = g_prj.resources[ri];
        std::vector<double> w(n, 0.0);
        for (int ai : r.assignments) { std::vector<double> s = spreadWork(g_prj.assignments[ai], b); for (size_t k = 0; k < n; k++) w[k] += s[k]; }
        std::vector<double> c;
        if (r.type == 0) { c.resize(n); for (size_t k = 0; k < n; k++) c[k] = cal.workingHoursBetween(b[k], b[k + 1]) * r.maxUnits; }
        u.rscIdx.push_back((int)ri); u.work.push_back(w); u.cap.push_back(c);
        int row = (int)u.work.size() - 1;
        for (size_t k = 0; k < n; k++) if (u.isOver(row, (int)k)) u.overCells++;
    }
    return u;
}
