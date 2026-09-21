// filters.h - rychlé filtry, rozšířený filtr (uložené pojmenované), uložené pohledy
#pragma once
#include "columns.h"

enum FilterMode { FM_ALL, FM_LATE, FM_INPROGRESS, FM_NOTSTARTED, FM_INCOMPLETE, FM_DONE, FM_CRITICAL, FM_THISWEEK, FM_NEXT14, FM_NEXT3W, FM_MILESTONES, FM_SUMMARY, FM_WITHRES, FM_INACTIVE, FM_NOTES, FM_COUNT };
static const wchar_t* FILTER_NAMES[FM_COUNT] = { L"Všechny úkoly", L"Zpožděné", L"Probíhající", L"Nezahájené", L"Nedokončené", L"Dokončené", L"Kritické", L"Tento týden", L"Příštích 14 dní", L"Nejbližší tři týdny", L"Milníky", L"Souhrnné", L"Se zdroji", L"Neaktivní", L"S poznámkou" };
static int g_filterMode = FM_ALL, g_filterRsc = -1;
static std::wstring g_filterText;

// Rozšířený filtr (port TaskFilter.java)
struct AdvFilter {
    std::wstring name, resource, colKey, text;
    int64_t from = 0, to = 0;     // unixové dny; 0 = nezadáno
    int pctMin = -1, pctMax = -1; // -1 = nezadáno
    bool leafOnly = false;
    bool empty() const { return resource.empty() && !from && !to && pctMin < 0 && pctMax < 0 && text.empty() && !leafOnly; }
    std::wstring describe() const {
        std::wstring s;
        auto add = [&](const std::wstring& x) { if (!s.empty()) s += L", "; s += x; };
        if (!resource.empty()) add(L"zdroj „" + resource + L"“");
        if (from || to) add(L"období " + (from ? mpp::fmtDate(mpp::fromUnixDays(from)) : L"…") + L" – " + (to ? mpp::fmtDate(mpp::fromUnixDays(to)) : L"…"));
        if (pctMin >= 0 || pctMax >= 0) add(L"dokončeno " + std::to_wstring(pctMin < 0 ? 0 : pctMin) + L"–" + std::to_wstring(pctMax < 0 ? 100 : pctMax) + L" %");
        if (!text.empty()) add(L"text „" + text + L"“");
        if (leafOnly) add(L"bez souhrnů");
        return s;
    }
    bool matches(const mpp::Task& t, int idx) const {
        if (leafOnly && t.summary) return false;
        if (!resource.empty()) { std::wstring r = lower(resource); bool f = false; for (int ai : t.assignments) { const mpp::Resource* rs = g_prj.rscByUid(g_prj.assignments[ai].rscUid); if (rs && lower(rs->name).find(r) != std::wstring::npos) { f = true; break; } } if (!f) return false; }
        if (from || to) {
            Time s = t.start, f = t.finish; if (!s && !f) return false; if (!s) s = f; if (!f) f = s;
            if (to && mpp::toUnixDays(s) > to) return false;
            if (from && mpp::toUnixDays(f) < from) return false;
        }
        if (pctMin >= 0 && imax(0, t.pctComplete) < pctMin) return false;
        if (pctMax >= 0 && imax(0, t.pctComplete) > pctMax) return false;
        if (!text.empty()) {
            std::wstring hay; int ci = colKey.empty() ? -1 : colIndex(colKey);
            hay = ci >= 0 ? cellText(t, idx, ci) : t.name + L" " + t.notes + L" " + t.wbs;
            if (lower(hay).find(lower(text)) == std::wstring::npos) return false;
        }
        return true;
    }
    std::wstring serialize() const {
        auto esc = [](std::wstring s) { for (auto& c : s) if (c == L'\t' || c == L'\n') c = L' '; return s; };
        return esc(name) + L"\t" + esc(resource) + L"\t" + std::to_wstring(from) + L"\t" + std::to_wstring(to) + L"\t" + std::to_wstring(pctMin) + L"\t" + std::to_wstring(pctMax) + L"\t" + esc(colKey) + L"\t" + esc(text) + L"\t" + (leafOnly ? L"1" : L"0");
    }
    static bool deserialize(const std::wstring& line, AdvFilter& f) {
        std::vector<std::wstring> p = split(line, L'\t'); if (p.size() < 9) return false;
        f.name = p[0]; f.resource = p[1]; f.from = _wtoi64(p[2].c_str()); f.to = _wtoi64(p[3].c_str()); f.pctMin = _wtoi(p[4].c_str()); f.pctMax = _wtoi(p[5].c_str()); f.colKey = p[6]; f.text = p[7]; f.leafOnly = p[8] == L"1";
        return true;
    }
};
static AdvFilter g_adv;
static std::vector<AdvFilter> g_savedFilters;
static void loadSavedFilters() { g_savedFilters.clear(); for (auto& kv : iniSection(L"Filters")) { AdvFilter f; if (AdvFilter::deserialize(kv.second, f) && !f.name.empty()) g_savedFilters.push_back(f); } }
static void storeSavedFilters() { WritePrivateProfileStringW(L"Filters", nullptr, nullptr, g_iniPath.c_str()); for (size_t i = 0; i < g_savedFilters.size(); i++) iniSetSection(L"Filters", (L"F" + std::to_wstring(i)).c_str(), g_savedFilters[i].serialize()); }

static bool filterActive() { return g_filterMode != FM_ALL || g_filterRsc >= 0 || !g_filterText.empty() || !g_adv.empty(); }
static bool taskMatches(const mpp::Task& t, int idx) {
    if (t.isNull) return !filterActive();
    if (!g_filterText.empty() && lower(t.name).find(g_filterText) == std::wstring::npos && lower(t.resourceNames).find(g_filterText) == std::wstring::npos && lower(t.wbs).find(g_filterText) == std::wstring::npos && std::to_wstring(t.id) != g_filterText && lower(t.notes).find(g_filterText) == std::wstring::npos) return false;
    if (g_filterRsc >= 0) { bool has = false; for (int ai : t.assignments) if (g_prj.assignments[ai].rscUid == g_filterRsc) { has = true; break; } if (!has) return false; }
    if (!g_adv.empty() && !g_adv.matches(t, idx)) return false;
    int64_t td = mpp::toUnixDays(g_today);
    switch (g_filterMode) {
    case FM_LATE: return isLate(t) || isLateStart(t);
    case FM_INPROGRESS: return !t.summary && t.pctComplete > 0 && t.pctComplete < 100;
    case FM_NOTSTARTED: return !t.summary && t.pctComplete <= 0;
    case FM_INCOMPLETE: return !t.summary && t.pctComplete < 100;
    case FM_DONE: return !t.summary && t.pctComplete >= 100;
    case FM_CRITICAL: return t.critical;
    case FM_MILESTONES: return t.milestone;
    case FM_SUMMARY: return t.summary;
    case FM_WITHRES: return !t.assignments.empty();
    case FM_INACTIVE: return !t.active;
    case FM_NOTES: return !t.notes.empty();
    case FM_THISWEEK: case FM_NEXT14: case FM_NEXT3W: {
        if (t.summary || !t.start) return false;
        int64_t a = g_filterMode == FM_THISWEEK ? td - (int64_t)mpp::dowOfUnixDay(td) : td;
        int64_t b = g_filterMode == FM_THISWEEK ? a + 7 : td + (g_filterMode == FM_NEXT14 ? 14 : 21);
        int64_t s = mpp::toUnixDays(t.start), f = mpp::toUnixDays(t.finish ? t.finish : t.start);
        return s < b && f >= a && t.pctComplete < 100;
    }
    default: return true;
    }
}

// Uložené pohledy: sloupce, filtr, úroveň osnovy, měřítko, obarvení
struct SavedView { std::wstring name, cols, colorKey; int filterMode = 0, level = 0; double pxPerDay = 0; AdvFilter adv; };
static std::vector<SavedView> g_views;
static void loadViews() {
    g_views.clear();
    for (auto& kv : iniSection(L"Views")) {
        std::vector<std::wstring> p = split(kv.second, L'\x1F'); if (p.size() < 6) continue;
        SavedView v; v.name = p[0]; v.cols = p[1]; v.filterMode = _wtoi(p[2].c_str()); v.level = _wtoi(p[3].c_str()); v.pxPerDay = _wtof(p[4].c_str()); v.colorKey = p[5];
        if (p.size() > 6) AdvFilter::deserialize(p[6], v.adv);
        if (!v.name.empty()) g_views.push_back(v);
    }
}
static void storeViews() {
    WritePrivateProfileStringW(L"Views", nullptr, nullptr, g_iniPath.c_str());
    for (size_t i = 0; i < g_views.size(); i++) { const SavedView& v = g_views[i]; wchar_t z[32]; swprintf(z, 32, L"%.3f", v.pxPerDay); iniSetSection(L"Views", (L"V" + std::to_wstring(i)).c_str(), v.name + L"\x1F" + v.cols + L"\x1F" + std::to_wstring(v.filterMode) + L"\x1F" + std::to_wstring(v.level) + L"\x1F" + z + L"\x1F" + v.colorKey + L"\x1F" + v.adv.serialize()); }
}
