// mppview.cpp - nativní Win32 prohlížeč harmonogramů MS Project (.mpp / .xml) pro techniky. Hlavní okno, tabulka, Gantt, záložky, nastavení.
#include "panels.h"
#include "detail.h"
#include <memory>

enum { IDM_OPEN = 101, IDM_OPEN_NEW, IDM_RELOAD, IDM_EXIT, IDM_ZOOM_IN, IDM_ZOOM_OUT, IDM_ZOOM_FIT, IDM_TODAY, IDM_GOTO_SEL, IDM_EXPAND_ALL, IDM_COLLAPSE_ALL, IDM_ABOUT,
       IDM_EXPORT_CSV, IDM_EXPORT_XLSX, IDM_EXPORT_PNG, IDM_EXPORT_PDF, IDM_PRINT, IDM_COPY, IDM_ASSOC, IDM_UNASSOC, IDM_DETAIL, IDM_PROGRESS, IDM_HOLIDAYS, IDM_DARK, IDM_FONT_UP, IDM_FONT_DOWN,
       IDM_SCALE_DAYS, IDM_SCALE_WEEKS, IDM_SCALE_MONTHS, IDM_FIND, IDM_CLEAR_FILTER, IDM_ADV_FILTER, IDM_CHECK, IDM_COMPARE, IDM_VIEW_SAVE, IDM_VIEW_DELETE, IDM_COLS_RESET, IDM_COLS_FIT,
       IDM_LEVEL1 = 140, IDM_RECENT0 = 150, IDM_TAB0 = 170, IDM_LICENSE = 180, IDM_LICENSE_DIR, IDM_GOTO_BASE = 200, IDM_COLOR_BASE = 3000 /* + 1 + index sloupce, nesmí zasahovat do dalších rozsahů */, IDM_BASELINE_BASE = 400, IDM_VIEW_BASE = 420, IDM_COL_BASE = 500,
       IDC_SEARCH = 901, IDC_FILTER, IDC_RSC, IDC_SCALE, IDC_SUMMARY, IDC_DETAIL, IDC_TABS, IDC_ADVBTN, IDC_LEVEL, IDC_ZOOMOUT, IDC_ZOOMIN, IDC_LINKS, IDC_BASELINE, IDC_TODAY };
static HWND g_lblSearch, g_lblFilter, g_lblLevel, g_lblScale, g_advBtn, g_level, g_zoomOut, g_zoomIn, g_chkLinks, g_chkBase, g_todayBtn;
static bool g_dragDetail = false;
static HWND g_lastFocus = nullptr; // prvek s fokusem při deaktivaci okna
static int g_splitPref = -1; // šířka tabulky zvolená uživatelem; g_splitX je skutečná, omezená šířkou okna
static bool g_placing = false; // obnovení polohy okna při startu (WM_DPICHANGED pak nemění velikost okna)
static bool g_statBusy = false; // kontrola změny souboru běží ve vlákně na pozadí
enum { WM_APP_FILESTAT = WM_APP + 3 };
static const wchar_t* TAB_NAMES[] = { L"Ganttův diagram", L"Časová osa", L"Zdroje", L"Vytížení", L"Kalendář", L"Projekt" };
static HWND g_panels[6];
static void rebuildRows(int keepUid = -1, bool openMatches = false);
static void updateSelectionStatus();
static void loadFile(const std::wstring& path, int keepUid = -1);
static void layout();
static void applyTheme();

// ---------------- řádky tabulky (filtr, řazení, sbalení) ----------------
static void updateSummary();
static void sortChildren(std::vector<int>& v) { int col = g_sortDir ? colIndex(g_sortKey) : -1; if (col < 0) return; std::stable_sort(v.begin(), v.end(), [&](int a, int b) { return compareTasks(a, b, col, g_sortDir) < 0; }); }
static void collect(int idx) {
    const mpp::Task& t = g_prj.tasks[idx];
    g_rowOfTask[idx] = (int)g_rows.size();
    g_rows.push_back(idx);
    if (!t.expanded) return;
    std::vector<int> ch; for (int c : t.children) if (g_keep[c]) ch.push_back(c);
    sortChildren(ch);
    for (int c : ch) collect(c);
}
static void fillListItems() {
    SendMessage(g_list, WM_SETREDRAW, FALSE, 0);
    ListView_DeleteAllItems(g_list);
    ListView_SetItemCountEx(g_list, (int)g_rows.size(), 0);
    std::wstring buf;
    for (size_t i = 0; i < g_rows.size(); i++) {
        const mpp::Task& t = g_prj.tasks[g_rows[i]];
        LVITEMW lvi = {}; lvi.mask = LVIF_TEXT; lvi.iItem = (int)i;
        buf = g_visible.empty() ? L"" : (g_cols[g_visible[0]].key == L"name" ? nameCell(t) : cellText(t, g_rows[i], g_visible[0])); lvi.pszText = &buf[0];
        ListView_InsertItem(g_list, &lvi);
        for (size_t c = 1; c < g_visible.size(); c++) { buf = g_cols[g_visible[c]].key == L"name" ? nameCell(t) : cellText(t, g_rows[i], g_visible[c]); ListView_SetItemText(g_list, (int)i, (int)c, &buf[0]); }
    }
    SendMessage(g_list, WM_SETREDRAW, TRUE, 0);
}
// openMatches: rozbalit souhrny se shodou filtru – jen při změně filtru, jinak by nešly sbalit a po zrušení filtru zůstaly rozbalené
static void rebuildRows(int keepUid, bool openMatches) {
    size_t n = g_prj.tasks.size();
    g_rows.clear(); g_rowOfTask.assign(n, -1); g_keep.assign(n, 0);
    bool fa = filterActive();
    for (size_t i = n; i-- > 0;) {
        mpp::Task& t = g_prj.tasks[i];
        bool m = taskMatches(t, (int)i);
        if (fa && t.summary) { bool anyChild = false; for (int c : t.children) if (g_keep[c]) { anyChild = true; break; } m = m || anyChild; if (anyChild && openMatches) t.expanded = true; }
        g_keep[i] = m;
    }
    std::vector<int> roots; for (int r : g_prj.roots) if (g_keep[r]) roots.push_back(r);
    sortChildren(roots);
    for (int r : roots) collect(r);
    buildBarColors();
    fillListItems();
    g_selRow = -1;
    if (keepUid >= 0) {
        auto it = g_prj.uidIndex.find(keepUid);
        if (it != g_prj.uidIndex.end() && g_rowOfTask[it->second] >= 0) {
            g_selRow = g_rowOfTask[it->second];
            ListView_SetItemState(g_list, g_selRow, LVIS_SELECTED | LVIS_FOCUSED, LVIS_SELECTED | LVIS_FOCUSED);
            ListView_EnsureVisible(g_list, g_selRow, FALSE);
        }
        if (g_selRow < 0) { updateDetail(); updateSelectionStatus(); } // vybraný úkol skryl filtr – detail nesmí ukazovat jeho údaje
    }
    RECT rc;
    if (!g_rows.empty() && ListView_GetItemRect(g_list, 0, &rc, LVIR_BOUNDS)) g_rowH = rc.bottom - rc.top;
    HWND hdr = ListView_GetHeader(g_list);
    if (hdr && GetWindowRect(hdr, &rc)) g_headerH = rc.bottom - rc.top;
    InvalidateRect(g_list, nullptr, TRUE); InvalidateRect(g_gantt, nullptr, FALSE);
    updateSummary();
}
static int selectedUid() { return g_selRow >= 0 && g_selRow < (int)g_rows.size() ? g_prj.tasks[g_rows[g_selRow]].uid : -1; }
static void toggleRow(int row, int force = -1) {
    if (row < 0 || row >= (int)g_rows.size()) return;
    mpp::Task& t = g_prj.tasks[g_rows[row]];
    if (!t.summary) return;
    bool nv = force < 0 ? !t.expanded : force != 0;
    if (nv == t.expanded) return;
    t.expanded = nv; rebuildRows(t.uid);
}
static void expandAll(bool expand) { for (auto& t : g_prj.tasks) if (t.summary) t.expanded = expand || t.level <= 0; rebuildRows(selectedUid()); }
static void expandToLevel(int level) { for (auto& t : g_prj.tasks) if (t.summary) t.expanded = t.level < level; rebuildRows(selectedUid()); }
static void setTab(int tab);
static void selectTaskByUid(int uid) {
    auto it = g_prj.uidIndex.find(uid);
    if (it == g_prj.uidIndex.end()) return;
    for (int q = g_prj.tasks[it->second].parent; q >= 0; q = g_prj.tasks[q].parent) g_prj.tasks[q].expanded = true;
    if (!g_keep.empty() && !g_keep[it->second] && filterActive()) { g_filterMode = FM_ALL; g_filterRsc = -1; g_filterText.clear(); g_adv = AdvFilter(); SetWindowTextW(g_search, L""); SendMessage(g_filter, CB_SETCURSEL, 0, 0); SendMessage(g_rscCombo, CB_SETCURSEL, 0, 0); }
    if (g_tab != 0) setTab(0);
    rebuildRows(uid);
    SetFocus(g_list);
}

// ---------------- časová osa Ganttu ----------------
static void updateScroll();
static void computeRange() {
    Time mn = 0, mx = 0;
    for (const mpp::Task& t : g_prj.tasks) {
        if (t.isNull) continue;
        auto add = [&](Time v) { if (!v) return; if (!mn || v < mn) mn = v; if (!mx || v > mx) mx = v; };
        for (Time v : { t.start, t.finish, t.baselineStartV(), t.baselineFinishV(), t.deadline }) add(v);
        for (int n : g_prj.baselinesUsed) if (n > 0 && n <= 10) { add(t.baselines[n].start); add(t.baselines[n].finish); } // i směrné plány 1–10 (lze je přepnout)
    }
    if (!mn) { mn = g_today - 30 * 86400; mx = g_today + 60 * 86400; }
    int64_t d0 = mpp::toUnixDays(mn) - 7; d0 -= mpp::dowOfUnixDay(d0);
    g_origin = mpp::fromUnixDays(d0); g_end = mpp::fromUnixDays(mpp::toUnixDays(mx) + 21); g_hscroll = 0;
}
static int X(Time t) { return (int)std::floor((t - g_origin) / 86400.0 * g_pxPerDay) - g_hscroll; }
static int totalWidth() { return (int)((g_end - g_origin) / 86400.0 * g_pxPerDay); }
static void syncScaleCombo() { int sel = -1; if (std::fabs(g_pxPerDay - 24) < 0.01) sel = 0; else if (std::fabs(g_pxPerDay - 6) < 0.01) sel = 1; else if (std::fabs(g_pxPerDay - 1.5) < 0.01) sel = 2; SendMessage(g_scale, CB_SETCURSEL, sel, 0); }
static void updateScroll() {
    RECT rc; GetClientRect(g_gantt, &rc);
    SCROLLINFO si = { sizeof(si), SIF_RANGE | SIF_PAGE | SIF_POS }; si.nMin = 0; si.nMax = imax(0, totalWidth() - 1); si.nPage = rc.right; si.nPos = g_hscroll;
    SetScrollInfo(g_gantt, SB_HORZ, &si, TRUE); GetScrollInfo(g_gantt, SB_HORZ, &si); g_hscroll = si.nPos;
}
static void setZoom(double px, int anchorX) {
    RECT rc; GetClientRect(g_gantt, &rc);
    if (anchorX < 0) anchorX = rc.right / 3;
    double dayAtAnchor = (g_hscroll + anchorX) / g_pxPerDay;
    g_pxPerDay = std::max(0.4, std::min(160.0, px));
    g_hscroll = imax(0, (int)(dayAtAnchor * g_pxPerDay) - anchorX);
    updateScroll(); syncScaleCombo(); InvalidateRect(g_gantt, nullptr, FALSE);
}
static void zoomFit() {
    RECT rc; GetClientRect(g_gantt, &rc);
    double days = (g_end - g_origin) / 86400.0;
    g_pxPerDay = days > 0 ? std::max(0.4, std::min(160.0, (rc.right - 20) / days)) : 20;
    g_hscroll = 0; updateScroll(); syncScaleCombo();
    if (SendMessage(g_scale, CB_GETCURSEL, 0, 0) < 0) SendMessage(g_scale, CB_SETCURSEL, 3, 0);
    InvalidateRect(g_gantt, nullptr, FALSE);
}
static void scrollTo(Time t) { RECT rc; GetClientRect(g_gantt, &rc); g_hscroll = imax(0, X(t) + g_hscroll - (int)rc.right / 3); updateScroll(); InvalidateRect(g_gantt, nullptr, FALSE); }
static void gotoToday() { scrollTo(g_today); }
static void gotoSelected() { int u = selectedUid(); const mpp::Task* t = u >= 0 ? g_prj.byUid(u) : nullptr; if (t && t->start) scrollTo(t->start); }

// ---------------- sloupce tabulky ----------------
static std::map<std::wstring, int> g_colWidths; // uložené šířky podle klíče
static void rebuildListColumns() {
    SendMessage(g_list, WM_SETREDRAW, FALSE, 0);
    while (ListView_DeleteColumn(g_list, 0)) {}
    for (size_t i = 0; i < g_visible.size(); i++) {
        const Col& c = g_cols[g_visible[i]];
        LVCOLUMNW col = {}; col.mask = LVCF_TEXT | LVCF_WIDTH | LVCF_FMT;
        std::wstring title = c.title; if (g_sortDir && c.key == g_sortKey) title += g_sortDir > 0 ? L" ▲" : L" ▼";
        col.pszText = &title[0]; auto w = g_colWidths.find(c.key); col.cx = S(w != g_colWidths.end() ? w->second : c.width);
        col.fmt = c.right ? LVCFMT_RIGHT : LVCFMT_LEFT;
        ListView_InsertColumn(g_list, (int)i, &col);
    }
    SendMessage(g_list, WM_SETREDRAW, TRUE, 0);
}
static void rememberColumnWidths() { for (size_t i = 0; i < g_visible.size(); i++) g_colWidths[g_cols[g_visible[i]].key] = MulDiv(ListView_GetColumnWidth(g_list, (int)i), 96, g_dpi); }
static void setVisibleColumns(const std::vector<std::wstring>& keys) {
    g_visible.clear();
    for (auto& k : keys) { int i = colIndex(k); if (i >= 0) g_visible.push_back(i); }
    if (g_visible.empty()) for (auto& k : DEFAULT_COLS) { int i = colIndex(k); if (i >= 0) g_visible.push_back(i); }
    rebuildListColumns();
}
static std::wstring visibleKeys() { std::vector<std::wstring> k; for (int i : g_visible) k.push_back(g_cols[i].key); return join(k, L","); }
static void syncColumnOrder() { // po přetažení hlavičky
    int n = (int)g_visible.size(); if (n <= 0) return; std::vector<int> order(n); ListView_GetColumnOrderArray(g_list, n, order.data());
    std::vector<int> nv; for (int o : order) if (o >= 0 && o < n) nv.push_back(g_visible[o]);
    if ((int)nv.size() == n) { rememberColumnWidths(); g_visible = nv; rebuildListColumns(); fillListItems(); }
}
static void autoFitColumns() { for (size_t i = 0; i < g_visible.size(); i++) { ListView_SetColumnWidth(g_list, (int)i, LVSCW_AUTOSIZE_USEHEADER); int w = ListView_GetColumnWidth(g_list, (int)i); if (w > S(400)) ListView_SetColumnWidth(g_list, (int)i, S(400)); } rememberColumnWidths(); }
static void columnChooser(POINT screen) {
    HMENU m = CreatePopupMenu(); HMENU cats[CAT_COUNT];
    for (int c = 0; c < CAT_COUNT; c++) cats[c] = CreatePopupMenu();
    for (size_t i = 0; i < g_cols.size(); i++) { bool vis = std::find(g_visible.begin(), g_visible.end(), (int)i) != g_visible.end(); AppendMenuW(cats[g_cols[i].cat], MF_STRING | (vis ? MF_CHECKED : 0), IDM_COL_BASE + i, g_cols[i].title.c_str()); }
    for (int c = 0; c < CAT_COUNT; c++) { if (GetMenuItemCount(cats[c])) AppendMenuW(m, MF_POPUP, (UINT_PTR)cats[c], CAT_NAMES[c]); else DestroyMenu(cats[c]); }
    AppendMenuW(m, MF_SEPARATOR, 0, nullptr); AppendMenuW(m, MF_STRING, IDM_COLS_FIT, L"Přizpůsobit šířky obsahu"); AppendMenuW(m, MF_STRING, IDM_COLS_RESET, L"Výchozí sloupce");
    int cmd = TrackPopupMenu(m, TPM_RETURNCMD | TPM_RIGHTBUTTON, screen.x, screen.y, 0, g_main, nullptr); DestroyMenu(m);
    if (cmd >= IDM_COL_BASE && cmd < IDM_COL_BASE + (int)g_cols.size()) {
        int ci = cmd - IDM_COL_BASE; rememberColumnWidths();
        auto it = std::find(g_visible.begin(), g_visible.end(), ci);
        if (it != g_visible.end()) { if (g_visible.size() > 1) g_visible.erase(it); } else g_visible.push_back(ci);
        rebuildListColumns(); fillListItems();
    } else if (cmd == IDM_COLS_RESET) { g_colWidths.clear(); setVisibleColumns(DEFAULT_COLS); fillListItems(); }
    else if (cmd == IDM_COLS_FIT) autoFitColumns();
}
static void sortByColumn(int listCol) {
    if (listCol < 0 || listCol >= (int)g_visible.size()) return;
    const std::wstring& key = g_cols[g_visible[listCol]].key;
    if (g_sortKey != key) { g_sortKey = key; g_sortDir = 1; } else if (g_sortDir == 1) g_sortDir = -1; else if (g_sortDir == -1) { g_sortDir = 0; g_sortKey.clear(); } else g_sortDir = 1;
    rememberColumnWidths(); rebuildListColumns(); rebuildRows(selectedUid());
}

// ---------------- přehled, detail, tooltip ----------------
static void updateSummary() {
    if (!g_loaded) { SetWindowTextW(g_summary, L""); return; }
    int total = 0, done = 0, prog = 0, late = 0, crit = 0, notStarted = 0; double workAll = 0, workDone = 0;
    const mpp::Task* nextMs = nullptr;
    for (const mpp::Task& t : g_prj.tasks) {
        if (t.isNull || t.summary) continue;
        total++;
        if (t.pctComplete >= 100) done++; else if (t.pctComplete > 0) prog++; else notStarted++;
        if (isLate(t) || isLateStart(t)) late++;
        if (t.critical) crit++;
        double w = t.workMinutes > 0 ? t.workMinutes : (t.duration.valid() ? t.duration.raw / 10.0 : 0);
        workAll += w; workDone += w * imax(0, t.pctComplete) / 100.0;
        if (t.milestone && t.pctComplete < 100 && t.start >= g_refDate && (!nextMs || t.start < nextMs->start)) nextMs = &t;
    }
    int pct = workAll > 0 ? (int)std::round(workDone * 100 / workAll) : 0;
    std::wstring s = L"Úkolů: " + std::to_wstring(total) + L"   ·   Hotovo: " + std::to_wstring(done) + L"   ·   Probíhá: " + std::to_wstring(prog) + L"   ·   Nezahájeno: " + std::to_wstring(notStarted) + L"   ·   Zpožděno: " + std::to_wstring(late) + L"   ·   Kritických: " + std::to_wstring(crit) + L"   ·   Projekt hotov: " + std::to_wstring(pct) + L" %";
    if (nextMs) s += L"   ·   Nejbližší milník: " + nextMs->name + L" (" + mpp::fmtDate(nextMs->start) + L")";
    if (filterActive()) { s += L"   ·   [filtr: " + std::to_wstring(g_rows.size()) + L" řádků"; std::wstring d = g_adv.describe(); if (!d.empty()) s += L"; " + d; s += L"]"; }
    SetWindowTextW(g_summary, s.c_str());
}
static std::wstring slackText(const mpp::Task& t) { return mpp::fmtSlackRaw(t.totalSlackRaw, g_prj); }
static std::wstring taskDetail(const mpp::Task& t, int idx) {
    if (t.isNull) return L"";
    std::wstring s = t.name + L"   [" + mpp::taskKindName(t) + L"]\r\n";
    s += L"Zahájení: " + mpp::fmtDate(t.start, true) + L"    Dokončení: " + mpp::fmtDate(t.finish, true) + L"    Doba: " + cellText(t, idx, colIndex(L"duration")) + L"    Dokončeno: " + cellText(t, idx, colIndex(L"percent")) + L"    Stav: " + stateText(t) + (t.critical ? L"    KRITICKÝ" : L"") + L"\r\n";
    if (t.actualStart || t.actualFinish) s += L"Skutečné zahájení: " + mpp::fmtDate(t.actualStart) + L"    Skutečné dokončení: " + mpp::fmtDate(t.actualFinish) + L"\r\n";
    for (int n : g_prj.baselinesUsed) { const mpp::Baseline& b = t.baselines[n]; if (!b.start && !b.finish) continue; s += (n == 0 ? L"Směrný plán: " : L"Směrný plán " + std::to_wstring(n) + L": ") + mpp::fmtDate(b.start) + L" – " + mpp::fmtDate(b.finish) + (b.duration.valid() ? L"  (" + mpp::fmtDuration(b.duration, g_prj) + L")" : L"") + (n == 0 ? L"    Skluz dokončení: " + varianceText(t, idx) + L" prac. dní" : L"") + (b.workHours ? L"    práce " + mpp::fmtHours(b.workHours) : L"") + (b.cost ? L"    náklady " + mpp::fmtMoney(b.cost, g_prj) : L"") + L"\r\n"; }
    if (t.deadline) s += L"Konečný termín: " + mpp::fmtDate(t.deadline) + L"\r\n";
    if (t.constraintType) s += L"Omezení: " + std::wstring(mpp::constraintName(t.constraintType)) + (t.constraintDate ? L" (" + mpp::fmtDate(t.constraintDate) + L")" : L"") + L"\r\n";
    std::wstring sl = slackText(t); if (!sl.empty() && !t.summary) s += L"Celková rezerva: " + sl + L"    Volná rezerva: " + mpp::fmtSlackRaw(t.freeSlackRaw, g_prj) + L"\r\n";
    if (t.workMinutes || t.cost) s += L"Práce: " + mpp::fmtHours(t.workMinutes / 60.0) + (t.actualWorkMinutes ? L" (skutečná " + mpp::fmtHours(t.actualWorkMinutes / 60.0) + L")" : L"") + L"    Náklady: " + mpp::fmtMoney(t.cost, g_prj) + L"    Priorita: " + mpp::priorityName(t.priority) + L"    Typ: " + mpp::taskTypeName(t.taskType) + L"\r\n";
    if (!t.assignments.empty()) { s += L"Zdroje:"; for (int ai : t.assignments) { const mpp::Assignment& a = g_prj.assignments[ai]; const mpp::Resource* r = g_prj.rscByUid(a.rscUid); if (!r) continue; s += L"  " + r->name + L" " + mpp::fmtNumber(a.units * 100) + L" %" + (a.workHours ? L" / " + mpp::fmtHours(a.workHours) : L"") + L";"; } s += L"\r\n"; }
    std::wstring pr = mpp::fmtPredecessors(t, g_prj); if (!pr.empty()) s += L"Předchůdci: " + pr + L"\r\n";
    std::wstring su = successorsText(t); if (!su.empty()) s += L"Následníci: " + su + L"\r\n";
    if (!t.wbs.empty() || !t.outlineNumber.empty()) s += L"WBS: " + t.wbs + L"    Osnova: " + t.outlineNumber + L"    Jedinečné ID: " + std::to_wstring(t.uid) + (t.calendarName.empty() ? L"" : L"    Kalendář: " + t.calendarName) + L"\r\n";
    if (!t.custom.empty()) { s += L"Vlastní pole:"; for (auto& cf : g_prj.customFields) { std::wstring v = mpp::fmtCustom(cf, t.customValue(cf.id), g_prj); if (!v.empty()) s += L"  " + cf.title() + L" = " + v + L";"; } s += L"\r\n"; }
    if (!t.subproject.empty()) s += L"Vložený podprojekt: " + t.subproject + L"\r\n";
    if (!t.contact.empty()) s += L"Kontakt: " + t.contact + L"\r\n";
    if (!t.notes.empty()) { s += L"\r\nPoznámky:\r\n"; std::wstring n = t.notes; size_t p = 0; while ((p = n.find(L'\n', p)) != std::wstring::npos) { n.replace(p, 1, L"\r\n"); p += 2; } s += n; }
    return s;
}
static void updateDetail() {
    if (!g_detailVisible) return;
    bool sel = g_selRow >= 0 && g_selRow < (int)g_rows.size();
    fillDetail(sel ? g_rows[g_selRow] : -1);
    // text okna = celý detail jako prostý text (čtou ho testy přes WM_GETTEXT)
    SetWindowTextW(g_detail, sel ? taskDetail(g_prj.tasks[g_rows[g_selRow]], g_rows[g_selRow]).c_str() : L"");
}
static void updateSelectionStatus() {
    std::vector<int> sel; int i = -1; while ((i = ListView_GetNextItem(g_list, i, LVNI_SELECTED)) >= 0) if (i < (int)g_rows.size()) sel.push_back(g_rows[i]);
    if (sel.size() < 2) { SendMessage(g_status, SB_SETTEXT, 1, (LPARAM)(g_loaded ? (L"Project " + std::to_wstring(g_prj.appVersion >= 16 ? 2016 : g_prj.appVersion == 15 ? 2013 : g_prj.appVersion == 14 ? 2010 : 2007) + (g_prj.appVersion > 16 ? L"+" : L"") + L"   Kalendář: " + (g_prj.defaultCalendar >= 0 ? g_prj.calendar().name : L"(vestavěný Po–Pá)") + L"   Zdrojů: " + std::to_wstring(g_prj.resources.size())).c_str() : L"")); return; }
    double work = 0, cost = 0; Time mn = 0, mx = 0; int n = 0;
    for (int r : sel) { const mpp::Task& t = g_prj.tasks[r]; if (t.isNull || t.summary) continue; n++; work += t.workMinutes; cost += t.cost; if (t.start && (!mn || t.start < mn)) mn = t.start; if (t.finish && (!mx || t.finish > mx)) mx = t.finish; }
    std::wstring s = L"Vybráno " + std::to_wstring(n) + L" úkolů · práce " + mpp::fmtHours(work / 60.0) + (cost ? L" · náklady " + mpp::fmtMoney(cost, g_prj) : L"") + L" · termíny " + mpp::fmtDate(mn) + L" – " + mpp::fmtDate(mx);
    SendMessage(g_status, SB_SETTEXT, 1, (LPARAM)s.c_str());
}
static std::wstring tipText(const mpp::Task& t, int idx) {
    std::wstring s = t.name + L"\n" + mpp::fmtDate(t.start) + L" – " + mpp::fmtDate(t.finish) + L"   " + cellText(t, idx, colIndex(L"duration")) + L"   " + cellText(t, idx, colIndex(L"percent"));
    s += L"\nStav: " + stateText(t) + (t.critical ? L" · kritický" : L"");
    if (t.baselineFinishV()) s += L"\nSměrný plán: " + mpp::fmtDate(t.baselineStartV()) + L" – " + mpp::fmtDate(t.baselineFinishV()) + L"  (skluz " + varianceText(t, idx) + L" prac. dní)";
    if (!t.summary && t.totalSlackRaw != INT32_MIN) s += L"\nCelková rezerva: " + slackText(t);
    if (t.constraintType) s += L"\nOmezení: " + std::wstring(mpp::constraintName(t.constraintType)) + (t.constraintDate ? L" " + mpp::fmtDate(t.constraintDate) : L"");
    if (!t.resourceNames.empty()) s += L"\nZdroje: " + t.resourceNames;
    std::wstring pr = mpp::fmtPredecessors(t, g_prj); if (!pr.empty()) s += L"\nPředchůdci: " + pr;
    if (!t.notes.empty()) { std::wstring n = t.notes.substr(0, 200); if (t.notes.size() > 200) n += L"…"; s += L"\n" + n; }
    return s;
}
static LRESULT CALLBACK TipProc(HWND hwnd, UINT msg, WPARAM wp, LPARAM lp) {
    if (msg == WM_PAINT) {
        PAINTSTRUCT ps; HDC hdc = BeginPaint(hwnd, &ps); RECT rc; GetClientRect(hwnd, &rc);
        HBRUSH b = CreateSolidBrush(T.tipBg); FillRect(hdc, &rc, b); DeleteObject(b);
        SelectObject(hdc, g_font); SetBkMode(hdc, TRANSPARENT); SetTextColor(hdc, T.ink);
        std::wstring t = ctlText(hwnd); RECT tr = { S(6), S(4), rc.right - S(6), rc.bottom - S(4) };
        DrawTextW(hdc, t.c_str(), (int)t.size(), &tr, DT_LEFT | DT_WORDBREAK | DT_NOPREFIX);
        EndPaint(hwnd, &ps); return 0;
    }
    if (msg == WM_MOUSEACTIVATE) return MA_NOACTIVATE;
    return DefWindowProcW(hwnd, msg, wp, lp);
}
static void showTip(int row, POINT screen) {
    if (row < 0 || row >= (int)g_rows.size()) { if (g_tipTask >= 0) { ShowWindow(g_tip, SW_HIDE); g_tipTask = -1; } return; }
    int idx = g_rows[row]; std::wstring text = tipText(g_prj.tasks[idx], idx);
    HDC hdc = GetDC(g_tip); SelectObject(hdc, g_font); RECT m = { 0, 0, S(420), 0 }; DrawTextW(hdc, text.c_str(), (int)text.size(), &m, DT_LEFT | DT_WORDBREAK | DT_NOPREFIX | DT_CALCRECT); ReleaseDC(g_tip, hdc);
    int w = m.right + S(12) + 2, h = m.bottom + S(8) + 2, x = screen.x + S(16), y = screen.y + S(18);
    MONITORINFO mi = { sizeof(mi) }; GetMonitorInfoW(MonitorFromPoint(screen, MONITOR_DEFAULTTONEAREST), &mi);
    if (x + w > mi.rcWork.right) x = screen.x - w - S(8); if (y + h > mi.rcWork.bottom) y = screen.y - h - S(8);
    if (g_tipTask != idx) { SetWindowTextW(g_tip, text.c_str()); InvalidateRect(g_tip, nullptr, TRUE); g_tipTask = idx; }
    SetWindowPos(g_tip, HWND_TOPMOST, x, y, w, h, SWP_NOACTIVATE | SWP_SHOWWINDOW);
}

// ---------------- Gantt okno ----------------
static int rowAtY(int y) { if (y < g_headerH || g_rowH <= 0) return -1; int r = ListView_GetTopIndex(g_list) + (y - g_headerH) / g_rowH; return r < (int)g_rows.size() ? r : -1; }
static void openDialog() { wchar_t buf[MAX_PATH]; if (openDialogPath(buf, L"Otevřít harmonogram")) loadFile(buf); }
static LRESULT CALLBACK GanttProc(HWND hwnd, UINT msg, WPARAM wp, LPARAM lp) {
    switch (msg) {
    case WM_ERASEBKGND: return 1;
    case WM_PAINT: {
        PAINTSTRUCT ps; HDC hdc = BeginPaint(hwnd, &ps); RECT rc; GetClientRect(hwnd, &rc);
        HDC mem = CreateCompatibleDC(hdc); HBITMAP bmp = CreateCompatibleBitmap(hdc, rc.right, rc.bottom); HGDIOBJ old = SelectObject(mem, bmp);
        paintGantt(mem, rc.right, rc.bottom);
        BitBlt(hdc, 0, 0, rc.right, rc.bottom, mem, 0, 0, SRCCOPY);
        SelectObject(mem, old); DeleteObject(bmp); DeleteDC(mem); EndPaint(hwnd, &ps); return 0;
    }
    case WM_SIZE: updateScroll(); InvalidateRect(hwnd, nullptr, FALSE); return 0;
    case WM_HSCROLL: {
        SCROLLINFO si = { sizeof(si), SIF_ALL }; GetScrollInfo(hwnd, SB_HORZ, &si); int pos = si.nPos;
        switch (LOWORD(wp)) { case SB_LINELEFT: pos -= S(20); break; case SB_LINERIGHT: pos += S(20); break; case SB_PAGELEFT: pos -= (int)si.nPage; break; case SB_PAGERIGHT: pos += (int)si.nPage; break; case SB_THUMBTRACK: case SB_THUMBPOSITION: pos = si.nTrackPos; break; case SB_LEFT: pos = 0; break; case SB_RIGHT: pos = si.nMax; break; }
        g_hscroll = imax(0, imin(pos, imax(0, si.nMax - (int)si.nPage + 1))); updateScroll(); InvalidateRect(hwnd, nullptr, FALSE); return 0;
    }
    case WM_MOUSEWHEEL: {
        int delta = GET_WHEEL_DELTA_WPARAM(wp);
        if (GET_KEYSTATE_WPARAM(wp) & MK_CONTROL) { POINT pt = { GET_X_LPARAM(lp), GET_Y_LPARAM(lp) }; ScreenToClient(hwnd, &pt); setZoom(g_pxPerDay * (delta > 0 ? 1.25 : 0.8), pt.x); }
        else if (GET_KEYSTATE_WPARAM(wp) & MK_SHIFT) { SendMessage(hwnd, WM_HSCROLL, delta > 0 ? SB_LINELEFT : SB_LINERIGHT, 0); SendMessage(hwnd, WM_HSCROLL, delta > 0 ? SB_LINELEFT : SB_LINERIGHT, 0); }
        else { SendMessage(g_list, WM_MOUSEWHEEL, wp, lp); InvalidateRect(hwnd, nullptr, FALSE); }
        return 0;
    }
    case WM_MOUSEMOVE: {
        static const bool testMode = GetEnvironmentVariableW(L"MPPVIEW_TEST", nullptr, 0) != 0;
        if (!g_tracking && !testMode) { TRACKMOUSEEVENT tme = { sizeof(tme), TME_LEAVE, hwnd, 0 }; TrackMouseEvent(&tme); g_tracking = true; }
        if (!g_loaded) return 0;
        int x = GET_X_LPARAM(lp), y = GET_Y_LPARAM(lp); int row = rowAtY(y);
        bool over = row >= 0 && row < (int)g_barRects.size() && x >= g_barRects[row].left && x <= g_barRects[row].right && g_barRects[row].right > g_barRects[row].left;
        POINT sp = { x, y }; ClientToScreen(hwnd, &sp); showTip(over ? row : -1, sp); return 0;
    }
    case WM_MOUSELEAVE: g_tracking = false; showTip(-1, POINT{ 0, 0 }); return 0;
    case WM_LBUTTONDOWN: {
        if (!g_loaded) { POINT pt = { GET_X_LPARAM(lp), GET_Y_LPARAM(lp) }; for (auto& r : g_startRects) if (PtInRect(&r.first, pt)) { if (r.second < 0) openDialog(); else if (r.second < (int)g_recent.size()) loadFile(g_recent[r.second]); break; } return 0; }
        int row = rowAtY(GET_Y_LPARAM(lp));
        if (row >= 0) { ListView_SetItemState(g_list, -1, 0, LVIS_SELECTED); ListView_SetItemState(g_list, row, LVIS_SELECTED | LVIS_FOCUSED, LVIS_SELECTED | LVIS_FOCUSED); SetFocus(g_list); }
        return 0;
    }
    case WM_SETCURSOR: { if (!g_loaded) { POINT pt; GetCursorPos(&pt); ScreenToClient(hwnd, &pt); for (auto& r : g_startRects) if (PtInRect(&r.first, pt)) { SetCursor(LoadCursor(nullptr, IDC_HAND)); return TRUE; } } break; }
    case WM_LBUTTONDBLCLK: if (g_loaded) toggleRow(rowAtY(GET_Y_LPARAM(lp))); return 0;
    case WM_RBUTTONUP: { int row = rowAtY(GET_Y_LPARAM(lp)); if (row >= 0) { ListView_SetItemState(g_list, row, LVIS_SELECTED | LVIS_FOCUSED, LVIS_SELECTED | LVIS_FOCUSED); POINT pt = { GET_X_LPARAM(lp), GET_Y_LPARAM(lp) }; ClientToScreen(hwnd, &pt); SendMessage(g_main, WM_CONTEXTMENU, (WPARAM)g_list, MAKELPARAM(pt.x, pt.y)); } return 0; }
    }
    return DefWindowProc(hwnd, msg, wp, lp);
}
static LRESULT CALLBACK ListSub(HWND hwnd, UINT msg, WPARAM wp, LPARAM lp, UINT_PTR, DWORD_PTR) {
    if (msg == WM_KEYDOWN && wp == 'C' && (GetKeyState(VK_CONTROL) & 0x8000)) { copySelectedRows(); return 0; }
    LRESULT r = DefSubclassProc(hwnd, msg, wp, lp);
    if (msg == WM_VSCROLL || msg == WM_MOUSEWHEEL || msg == WM_KEYDOWN || msg == WM_SIZE) InvalidateRect(g_gantt, nullptr, FALSE);
    return r;
}
static void applyFilterFromControls();
static LRESULT CALLBACK SearchSub(HWND hwnd, UINT msg, WPARAM wp, LPARAM lp, UINT_PTR, DWORD_PTR) {
    if (msg == WM_KEYDOWN && wp == VK_ESCAPE) { SetWindowTextW(hwnd, L""); applyFilterFromControls(); return 0; }
    if (msg == WM_KEYDOWN && wp == VK_RETURN) { applyFilterFromControls(); SetFocus(g_list); return 0; }
    if (msg == WM_GETDLGCODE) return DLGC_WANTALLKEYS;
    return DefSubclassProc(hwnd, msg, wp, lp);
}
static void applyFilterFromControls() {
    wchar_t b[256]; GetWindowTextW(g_search, b, 256); g_filterText = lower(b);
    g_filterMode = (int)SendMessage(g_filter, CB_GETCURSEL, 0, 0); if (g_filterMode < 0) g_filterMode = FM_ALL;
    int rs = (int)SendMessage(g_rscCombo, CB_GETCURSEL, 0, 0); g_filterRsc = rs > 0 ? (int)SendMessage(g_rscCombo, CB_GETITEMDATA, rs, 0) : -1;
    if (g_loaded) rebuildRows(selectedUid(), true);
}
static void clearFilters() { SetWindowTextW(g_search, L""); SendMessage(g_filter, CB_SETCURSEL, 0, 0); SendMessage(g_rscCombo, CB_SETCURSEL, 0, 0); g_adv = AdvFilter(); applyFilterFromControls(); }

// ---------------- rozšířený filtr ----------------
static int64_t parseDay(const std::wstring& s) { int d = 0, m = 0, y = 0; if (swscanf(s.c_str(), L"%d.%d.%d", &d, &m, &y) == 3 && y > 1900 && m >= 1 && m <= 12 && d >= 1 && d <= 31) return mpp::daysFromCivil(y, (unsigned)m, (unsigned)d); return 0; }
static void advancedFilterDialog() {
    if (!g_loaded) return;
    Dlg d; dlgCreate(d, L"Rozšířený filtr", 520, 330);
    dlgLabel(d.h, L"Uložený filtr:", 12, 14, 100); HWND saved = dlgCombo(d.h, 120, 11, 250, 110); HWND bLoad = dlgButton(d.h, L"Použít", 378, 10, 60, 111); HWND bDel = dlgButton(d.h, L"Smazat", 444, 10, 62, 112);
    auto fillSaved = [&]() { SendMessage(saved, CB_RESETCONTENT, 0, 0); for (auto& f : g_savedFilters) SendMessage(saved, CB_ADDSTRING, 0, (LPARAM)f.name.c_str()); }; fillSaved();
    dlgLabel(d.h, L"Zdroj obsahuje:", 12, 50, 100); HWND eRes = dlgEdit(d.h, g_adv.resource.c_str(), 120, 48, 250, 101);
    dlgLabel(d.h, L"Období od – do:", 12, 80, 100); HWND eFrom = dlgEdit(d.h, g_adv.from ? mpp::fmtDate(mpp::fromUnixDays(g_adv.from)).c_str() : L"", 120, 78, 115, 102); HWND eTo = dlgEdit(d.h, g_adv.to ? mpp::fmtDate(mpp::fromUnixDays(g_adv.to)).c_str() : L"", 255, 78, 115, 103); dlgLabel(d.h, L"(d.m.rrrr)", 378, 82, 100);
    dlgLabel(d.h, L"Dokončeno % od – do:", 12, 110, 105); HWND ePMin = dlgEdit(d.h, g_adv.pctMin >= 0 ? std::to_wstring(g_adv.pctMin).c_str() : L"", 120, 108, 60, 104); HWND ePMax = dlgEdit(d.h, g_adv.pctMax >= 0 ? std::to_wstring(g_adv.pctMax).c_str() : L"", 200, 108, 60, 105);
    dlgLabel(d.h, L"Text ve sloupci:", 12, 140, 100); HWND cCol = dlgCombo(d.h, 120, 137, 180, 106); SendMessage(cCol, CB_ADDSTRING, 0, (LPARAM)L"(název, poznámky, WBS)"); int selCol = 0; for (size_t i = 0; i < g_cols.size(); i++) { SendMessage(cCol, CB_ADDSTRING, 0, (LPARAM)g_cols[i].title.c_str()); if (g_cols[i].key == g_adv.colKey) selCol = (int)i + 1; } SendMessage(cCol, CB_SETCURSEL, selCol, 0);
    HWND eText = dlgEdit(d.h, g_adv.text.c_str(), 310, 137, 196, 107);
    HWND cLeaf = dlgCheck(d.h, L"Bez souhrnných úkolů", 120, 168, 200, 108); SendMessage(cLeaf, BM_SETCHECK, g_adv.leafOnly ? BST_CHECKED : BST_UNCHECKED, 0);
    dlgLabel(d.h, L"Prázdné položky se neuplatní. Nadřazené souhrny zůstávají vidět.", 12, 200, 490);
    dlgButton(d.h, L"Uložit jako…", 12, 290, 100, 113); dlgButton(d.h, L"Vymazat", 120, 290, 80, 114);
    dlgButton(d.h, L"Použít", 340, 290, 80, IDOK, true); dlgButton(d.h, L"Zrušit", 428, 290, 78, IDCANCEL);
    auto read = [&]() { AdvFilter f; f.resource = ctlText(eRes); f.from = parseDay(ctlText(eFrom)); f.to = parseDay(ctlText(eTo)); std::wstring a = ctlText(ePMin), b = ctlText(ePMax); f.pctMin = a.empty() ? -1 : _wtoi(a.c_str()); f.pctMax = b.empty() ? -1 : _wtoi(b.c_str()); int c = (int)SendMessage(cCol, CB_GETCURSEL, 0, 0); f.colKey = c > 0 && c <= (int)g_cols.size() ? g_cols[c - 1].key : L""; f.text = ctlText(eText); f.leafOnly = SendMessage(cLeaf, BM_GETCHECK, 0, 0) == BST_CHECKED; return f; };
    auto write = [&](const AdvFilter& f) { SetWindowTextW(eRes, f.resource.c_str()); SetWindowTextW(eFrom, f.from ? mpp::fmtDate(mpp::fromUnixDays(f.from)).c_str() : L""); SetWindowTextW(eTo, f.to ? mpp::fmtDate(mpp::fromUnixDays(f.to)).c_str() : L""); SetWindowTextW(ePMin, f.pctMin >= 0 ? std::to_wstring(f.pctMin).c_str() : L""); SetWindowTextW(ePMax, f.pctMax >= 0 ? std::to_wstring(f.pctMax).c_str() : L""); int sc = 0; for (size_t i = 0; i < g_cols.size(); i++) if (g_cols[i].key == f.colKey) sc = (int)i + 1; SendMessage(cCol, CB_SETCURSEL, sc, 0); SetWindowTextW(eText, f.text.c_str()); SendMessage(cLeaf, BM_SETCHECK, f.leafOnly ? BST_CHECKED : BST_UNCHECKED, 0); };
    d.onCmd = [&](int id, int, HWND) {
        if (id == IDOK) { g_adv = read(); g_adv.name.clear(); d.result = 1; d.done = true; }
        else if (id == IDCANCEL) d.done = true;
        else if (id == 114) write(AdvFilter());
        else if (id == 111) { int i = (int)SendMessage(saved, CB_GETCURSEL, 0, 0); if (i >= 0 && i < (int)g_savedFilters.size()) write(g_savedFilters[i]); }
        else if (id == 112) { int i = (int)SendMessage(saved, CB_GETCURSEL, 0, 0); if (i >= 0 && i < (int)g_savedFilters.size()) { g_savedFilters.erase(g_savedFilters.begin() + i); storeSavedFilters(); fillSaved(); } }
        else if (id == 113) { std::wstring name; if (askText(L"Uložit filtr", L"Název filtru:", name) && !name.empty()) { AdvFilter f = read(); f.name = name; g_savedFilters.erase(std::remove_if(g_savedFilters.begin(), g_savedFilters.end(), [&](const AdvFilter& x) { return x.name == name; }), g_savedFilters.end()); g_savedFilters.push_back(f); storeSavedFilters(); fillSaved(); } }
    };
    (void)bLoad; (void)bDel;
    if (dlgRun(d) == 1) rebuildRows(selectedUid(), true);
}

// ---------------- pohledy, obarvení, směrný plán (menu) ----------------
static void rebuildViewsMenu() {
    while (GetMenuItemCount(g_menuViews) > 0) DeleteMenu(g_menuViews, 0, MF_BYPOSITION);
    for (size_t i = 0; i < g_views.size() && i < 20; i++) AppendMenuW(g_menuViews, MF_STRING, IDM_VIEW_BASE + i, g_views[i].name.c_str());
    if (!g_views.empty()) AppendMenuW(g_menuViews, MF_SEPARATOR, 0, nullptr);
    AppendMenuW(g_menuViews, MF_STRING, IDM_VIEW_SAVE, L"Uložit aktuální pohled…"); AppendMenuW(g_menuViews, MF_STRING, IDM_VIEW_DELETE, L"Smazat pohled…");
}
static void applyView(const SavedView& v) {
    rememberColumnWidths(); setVisibleColumns(split(v.cols, L','));
    g_filterMode = imax(0, imin(FM_COUNT - 1, v.filterMode)); SendMessage(g_filter, CB_SETCURSEL, g_filterMode, 0); g_adv = v.adv; g_colorKey = v.colorKey;
    if (v.level > 0) for (auto& t : g_prj.tasks) if (t.summary) t.expanded = t.level < v.level;
    if (v.pxPerDay > 0) g_pxPerDay = v.pxPerDay;
    rebuildRows(selectedUid(), true); updateScroll(); syncScaleCombo();
}
static void rebuildColorMenu() {
    while (GetMenuItemCount(g_menuColor) > 0) DeleteMenu(g_menuColor, 0, MF_BYPOSITION);
    AppendMenuW(g_menuColor, MF_STRING | (g_colorKey.empty() ? MF_CHECKED : 0), IDM_COLOR_BASE, L"(vypnuto)");
    const wchar_t* keys[] = { L"resources", L"level", L"kind", L"taskType", L"priority", L"calendar", L"wbs", L"state", L"critical" };
    for (const wchar_t* k : keys) { int ci = colIndex(k); if (ci >= 0) AppendMenuW(g_menuColor, MF_STRING | (g_colorKey == k ? MF_CHECKED : 0), IDM_COLOR_BASE + 1 + ci, g_cols[ci].title.c_str()); }
    bool sep = false;
    for (size_t i = 0; i < g_cols.size(); i++) if (g_cols[i].cat == CAT_CUSTOM) { if (!sep) { AppendMenuW(g_menuColor, MF_SEPARATOR, 0, nullptr); sep = true; } AppendMenuW(g_menuColor, MF_STRING | (g_colorKey == g_cols[i].key ? MF_CHECKED : 0), IDM_COLOR_BASE + 1 + i, g_cols[i].title.c_str()); }
}
static void rebuildBaselineMenu() {
    while (GetMenuItemCount(g_menuBaseline) > 0) DeleteMenu(g_menuBaseline, 0, MF_BYPOSITION);
    for (int n = 0; n <= 10; n++) { bool used = std::find(g_prj.baselinesUsed.begin(), g_prj.baselinesUsed.end(), n) != g_prj.baselinesUsed.end(); if (!used && n) continue; AppendMenuW(g_menuBaseline, MF_STRING | (g_baselineIdx == n ? MF_CHECKED : 0) | (used ? 0 : MF_GRAYED), IDM_BASELINE_BASE + n, n == 0 ? L"Směrný plán" : (L"Směrný plán " + std::to_wstring(n)).c_str()); }
}

// ---------------- načtení souboru, sledování změn ----------------
static void addRecent(std::wstring path);
static void refreshFileTime() { WIN32_FILE_ATTRIBUTE_DATA fa = {}; if (GetFileAttributesExW(g_file.c_str(), GetFileExInfoStandard, &fa)) g_fileTime = fa.ftLastWriteTime; }
static void loadFile(const std::wstring& path, int keepUid) {
    std::map<int, bool> expandedState; if (keepUid >= 0 && g_loaded) for (auto& t : g_prj.tasks) if (t.summary) expandedState[t.uid] = t.expanded;
    mpp::Project p;
    try { p = mpp::loadProject(path); }
    catch (const std::exception& e) { msgError(L"Soubor nelze otevřít:\n" + path + L"\n\n" + mpp::ansiToWide(e.what(), CP_UTF8)); return; }
    rememberColumnWidths();
    g_prj = std::move(p); g_loaded = true; g_file = path; g_reloadAsked = false; refreshFileTime();
    for (auto& kv : expandedState) { mpp::Task* t = g_prj.byUid(kv.first); if (t && t->summary) t->expanded = kv.second; }
    SYSTEMTIME st; GetLocalTime(&st);
    g_today = mpp::fromUnixDays(mpp::daysFromCivil(st.wYear, st.wMonth, st.wDay));
    g_refDate = g_prj.statusDate ? g_prj.statusDate : g_today;
    const mpp::Calendar& cal = g_prj.calendar();
    g_var.assign(g_prj.tasks.size(), INT_MIN); g_succ.clear();
    for (size_t i = 0; i < g_prj.tasks.size(); i++) { const mpp::Task& t = g_prj.tasks[i]; if (t.finish && t.baselineFinishV()) g_var[i] = (int)cal.workingDaysBetween(mpp::toUnixDays(t.baselineFinishV()), mpp::toUnixDays(t.finish)); for (const mpp::Relation& r : t.preds) g_succ[r.predUid].push_back(t.uid); }
    std::vector<std::wstring> keys; for (int i : g_visible) if (i < (int)g_cols.size()) keys.push_back(g_cols[i].key); // klíče předchozího souboru – před buildColumns, ten mění indexy
    buildColumns();
    if (keys.empty()) { std::wstring saved = iniGet(L"Cols"); keys = saved.empty() ? DEFAULT_COLS : split(saved, L','); }
    setVisibleColumns(keys);
    if (colIndex(g_colorKey) < 0) g_colorKey.clear();
    if (std::find(g_prj.baselinesUsed.begin(), g_prj.baselinesUsed.end(), g_baselineIdx) == g_prj.baselinesUsed.end()) g_baselineIdx = 0;
    SendMessage(g_rscCombo, CB_RESETCONTENT, 0, 0); SendMessage(g_rscCombo, CB_ADDSTRING, 0, (LPARAM)L"(všechny zdroje)");
    std::vector<const mpp::Resource*> rs; for (auto& r : g_prj.resources) if (!r.assignments.empty()) rs.push_back(&r);
    std::sort(rs.begin(), rs.end(), [](const mpp::Resource* a, const mpp::Resource* b) { return collate(a->name, b->name) < 0; });
    for (auto* r : rs) { int i = (int)SendMessage(g_rscCombo, CB_ADDSTRING, 0, (LPARAM)r->name.c_str()); SendMessage(g_rscCombo, CB_SETITEMDATA, i, r->uid); }
    SendMessage(g_rscCombo, CB_SETCURSEL, 0, 0); g_filterRsc = -1;
    showTip(-1, {}); // bublina drží index úkolu z předchozích dat
    Time leftEdge = keepUid >= 0 && g_origin ? g_origin + (Time)(g_hscroll / g_pxPerDay * 86400) : 0; // F5: graf zůstane na stejném datu
    computeRange(); layout();
    if (leftEdge) g_hscroll = imax(0, X(leftEdge));
    rebuildRows(keepUid);
    if (keepUid < 0) { zoomFit(); if (g_pxPerDay < 6) setZoom(6, 0); if (g_today >= g_origin && g_today <= g_end) gotoToday(); } else updateScroll();
    SetWindowTextW(g_main, (fileName(path) + L" – " + APP_TITLE).c_str());
    SendMessage(g_status, SB_SETTEXT, 0, (LPARAM)(fileName(path) + (g_prj.title.empty() ? L"" : L"  (" + g_prj.title + L")")).c_str());
    updateSelectionStatus();
    SendMessage(g_status, SB_SETTEXT, 2, (LPARAM)(L"Projekt: " + mpp::fmtDate(g_prj.startDate) + L" – " + mpp::fmtDate(g_prj.finishDate) + (g_prj.statusDate ? L"   Stav k: " + mpp::fmtDate(g_prj.statusDate) : L"   (stav k dnešku)")).c_str());
    if (!g_batch) addRecent(path); // dávkový export (--csv/--pdf…) nemá měnit seznam nedávných souborů
    updateDetail(); rebuildColorMenu(); rebuildBaselineMenu(); refreshPanels();
}
static void reloadFile() { if (g_loaded) loadFile(g_file, selectedUid() >= 0 ? selectedUid() : INT_MAX); } // INT_MAX: nic nevybírat (uid 0 je projektový souhrn)
// Čas změny souboru se zjišťuje ve vlákně na pozadí: soubor na nedostupném síťovém disku by jinak každé 2 s zasekl okno.
struct FileStat { std::wstring path; FILETIME time; bool ok; };
static void checkFileChanged() {
    if (!g_loaded || g_statBusy) return;
    FileStat* fs = new FileStat{ g_file, {}, false };
    g_statBusy = true;
    if (!TrySubmitThreadpoolCallback([](PTP_CALLBACK_INSTANCE, void* p) {
            FileStat* fs = (FileStat*)p; WIN32_FILE_ATTRIBUTE_DATA fa = {};
            fs->ok = GetFileAttributesExW(fs->path.c_str(), GetFileExInfoStandard, &fa) != 0; fs->time = fa.ftLastWriteTime;
            if (!PostMessageW(g_main, WM_APP_FILESTAT, 0, (LPARAM)fs)) delete fs; // okno už neexistuje
        }, fs, nullptr)) { delete fs; g_statBusy = false; }
}
static void onFileStat(const FileStat& fs) {
    g_statBusy = false;
    // !IsWindowEnabled: otevřený dialog (kontrola, porovnání…) drží indexy úkolů – nenačítat pod ním, zeptat se až po zavření
    if (!fs.ok || !g_loaded || fs.path != g_file || g_reloadAsked || !IsWindowEnabled(g_main)) return;
    if (CompareFileTime(&fs.time, &g_fileTime) == 0) return;
    g_reloadAsked = true;
    if (MessageBoxW(g_main, (L"Soubor " + fileName(g_file) + L" byl na disku změněn.\nZnovu načíst?").c_str(), APP_TITLE, MB_ICONQUESTION | MB_YESNO) == IDYES) reloadFile();
    refreshFileTime(); g_reloadAsked = false; // i po nepovedeném načtení (soubor se ještě zapisoval) – další uložení se znovu ohlásí
}

// ---------------- nastavení ----------------
static void rebuildRecentMenu() {
    for (int i = 0; i < MAX_RECENT; i++) DeleteMenu(g_menuFile, IDM_RECENT0 + i, MF_BYCOMMAND);
    int pos = GetMenuItemCount(g_menuFile) - 2;
    for (size_t i = 0; i < g_recent.size(); i++) {
        std::wstring f = fileName(g_recent[i]); for (size_t p = 0; (p = f.find(L'&', p)) != std::wstring::npos; p += 2) f.insert(p, 1, L'&'); // & v názvu není zkratková klávesa
        InsertMenuW(g_menuFile, pos + (UINT)i, MF_BYPOSITION | MF_STRING, IDM_RECENT0 + i, (L"&" + std::to_wstring(i + 1) + L"  " + f).c_str());
    }
}
static void addRecent(std::wstring path) { // kopie: path bývá přímo prvek g_recent (otevření z nabídky) a erase by ho přepsal
    g_recent.erase(std::remove_if(g_recent.begin(), g_recent.end(), [&](const std::wstring& s) { return _wcsicmp(s.c_str(), path.c_str()) == 0; }), g_recent.end());
    g_recent.insert(g_recent.begin(), path); if ((int)g_recent.size() > MAX_RECENT) g_recent.resize(MAX_RECENT);
    for (int i = 0; i < MAX_RECENT; i++) iniSet((L"Recent" + std::to_wstring(i)).c_str(), i < (int)g_recent.size() ? g_recent[i] : L"");
    rebuildRecentMenu();
}
static void saveSettings() {
    WINDOWPLACEMENT wp = { sizeof(wp) }; GetWindowPlacement(g_main, &wp);
    iniSet(L"Window", std::to_wstring(wp.rcNormalPosition.left) + L"," + std::to_wstring(wp.rcNormalPosition.top) + L"," + std::to_wstring(wp.rcNormalPosition.right) + L"," + std::to_wstring(wp.rcNormalPosition.bottom) + L"," + std::to_wstring(wp.showCmd == SW_SHOWMAXIMIZED || (wp.showCmd == SW_SHOWMINIMIZED && (wp.flags & WPF_RESTORETOMAXIMIZED)) ? 1 : 0));
    iniSet(L"Split", std::to_wstring(MulDiv(g_splitPref > 0 ? g_splitPref : g_splitX, 96, g_dpi))); iniSet(L"Zoom", std::to_wstring((int)(g_pxPerDay * 100)));
    iniSet(L"Detail", g_detailVisible ? L"1" : L"0"); iniSet(L"ProgressLine", g_progressLine ? L"1" : L"0"); iniSet(L"Holidays", g_holidays ? L"1" : L"0"); iniSet(L"Dark", g_dark ? L"1" : L"0"); iniSet(L"FontPt", std::to_wstring(g_fontPt));
    iniSet(L"ColorKey", g_colorKey); iniSet(L"Baseline", std::to_wstring(g_baselineIdx));
    iniSet(L"ShowLinks", g_showLinks ? L"1" : L"0"); iniSet(L"ShowBaseline", g_showBaseline ? L"1" : L"0"); if (g_detailH > 0) iniSet(L"DetailH", std::to_wstring(MulDiv(g_detailH, 96, g_dpi)));
    if (g_loaded) { rememberColumnWidths(); iniSet(L"Cols", visibleKeys()); }
    std::wstring cw; for (auto& kv : g_colWidths) cw += kv.first + L"=" + std::to_wstring(kv.second) + L","; iniSet(L"ColWidths", cw);
}
static void loadSettings() {
    wchar_t dir[MAX_PATH]; SHGetFolderPathW(nullptr, CSIDL_APPDATA, nullptr, 0, dir);
    std::wstring d = std::wstring(dir) + L"\\MppView"; CreateDirectoryW(d.c_str(), nullptr); g_iniPath = d + L"\\mppview.ini";
    // nový ini jako UTF-16 (BOM): WritePrivateProfileStringW jinak zapisuje ANSI a znaky mimo kódovou stránku (cesty, názvy) se ztratí
    HANDLE f = CreateFileW(g_iniPath.c_str(), GENERIC_WRITE, 0, nullptr, CREATE_NEW, FILE_ATTRIBUTE_NORMAL, nullptr);
    if (f != INVALID_HANDLE_VALUE) { DWORD wr; WriteFile(f, "\xFF\xFE", 2, &wr, nullptr); CloseHandle(f); }
    g_detailVisible = iniInt(L"Detail", 1) != 0; g_progressLine = iniInt(L"ProgressLine", 1) != 0; g_holidays = iniInt(L"Holidays", 1) != 0; g_dark = iniInt(L"Dark", 0) != 0; g_fontPt = imax(7, imin(16, iniInt(L"FontPt", 9)));
    int sp = iniInt(L"Split", -1); if (sp > 0) g_splitPref = S(sp);
    int z = iniInt(L"Zoom", 0); if (z > 0) g_pxPerDay = z / 100.0;
    g_colorKey = iniGet(L"ColorKey"); g_baselineIdx = iniInt(L"Baseline", 0);
    g_showLinks = iniInt(L"ShowLinks", 1) != 0; g_showBaseline = iniInt(L"ShowBaseline", 1) != 0; g_detailH = S(iniInt(L"DetailH", 270));
    for (auto& kv : split(iniGet(L"ColWidths"), L',')) { size_t e = kv.find(L'='); if (e != std::wstring::npos) { int w = _wtoi(kv.substr(e + 1).c_str()); if (w >= 10 && w <= 2000) g_colWidths[kv.substr(0, e)] = w; } }
    for (int i = 0; i < MAX_RECENT; i++) {
        std::wstring r = iniGet((L"Recent" + std::to_wstring(i)).c_str()); if (r.empty()) continue;
        // síťové cesty se při startu neověřují: bez VPN by start visel desítky sekund a soubory by ze seznamu zmizely
        bool remote = r.compare(0, 2, L"\\\\") == 0 || (r.size() > 2 && r[1] == L':' && GetDriveTypeW(r.substr(0, 3).c_str()) == DRIVE_REMOTE);
        if (remote || GetFileAttributesW(r.c_str()) != INVALID_FILE_ATTRIBUTES) g_recent.push_back(r);
    }
    loadSavedFilters(); loadViews();
}

// ---------------- asociace .mpp + Průzkumník ----------------
static void setAssoc(bool on) {
    if (on) {
        if (MessageBoxW(g_main, L"Soubory .mpp se budou otevírat v tomto prohlížeči a v Průzkumníku přibude „Otevřít v M3K Project View“ (jen pro tento účet Windows).\nPokračovat?", APP_TITLE, MB_ICONQUESTION | MB_YESNO) != IDYES) return;
        wchar_t exe[MAX_PATH]; GetModuleFileNameW(nullptr, exe, MAX_PATH);
        std::wstring cmd = L"\"" + std::wstring(exe) + L"\" \"%1\"", icon = std::wstring(exe) + L",0", docIcon = std::wstring(exe) + L",1";
        auto put = [](const wchar_t* key, const wchar_t* name, const std::wstring& v) { HKEY h; if (RegCreateKeyExW(HKEY_CURRENT_USER, key, 0, nullptr, 0, KEY_WRITE, nullptr, &h, nullptr) == ERROR_SUCCESS) { RegSetValueExW(h, name, 0, REG_SZ, (const BYTE*)v.c_str(), (DWORD)((v.size() + 1) * 2)); RegCloseKey(h); } };
        put(L"Software\\Classes\\MppView.Project", nullptr, L"Harmonogram MS Project"); put(L"Software\\Classes\\MppView.Project\\DefaultIcon", nullptr, docIcon); put(L"Software\\Classes\\MppView.Project\\shell\\open\\command", nullptr, cmd);
        for (const wchar_t* ext : { L".mpp", L".mpt" }) { put((L"Software\\Classes\\" + std::wstring(ext)).c_str(), nullptr, L"MppView.Project"); put((L"Software\\Classes\\" + std::wstring(ext) + L"\\OpenWithProgids").c_str(), L"MppView.Project", L""); put((L"Software\\Classes\\SystemFileAssociations\\" + std::wstring(ext) + L"\\shell\\MppView").c_str(), nullptr, L"Otevřít v M3K Project View"); put((L"Software\\Classes\\SystemFileAssociations\\" + std::wstring(ext) + L"\\shell\\MppView").c_str(), L"Icon", icon); put((L"Software\\Classes\\SystemFileAssociations\\" + std::wstring(ext) + L"\\shell\\MppView\\command").c_str(), nullptr, cmd); }
    } else {
        if (MessageBoxW(g_main, L"Zrušit přidružení souborů .mpp k tomuto prohlížeči?", APP_TITLE, MB_ICONQUESTION | MB_YESNO) != IDYES) return;
        RegDeleteTreeW(HKEY_CURRENT_USER, L"Software\\Classes\\MppView.Project"); RegDeleteTreeW(HKEY_CURRENT_USER, L"Software\\Classes\\.mpp"); RegDeleteTreeW(HKEY_CURRENT_USER, L"Software\\Classes\\.mpt");
        RegDeleteTreeW(HKEY_CURRENT_USER, L"Software\\Classes\\SystemFileAssociations\\.mpp\\shell\\MppView"); RegDeleteTreeW(HKEY_CURRENT_USER, L"Software\\Classes\\SystemFileAssociations\\.mpt\\shell\\MppView");
    }
    SHChangeNotify(SHCNE_ASSOCCHANGED, SHCNF_IDLIST, nullptr, nullptr);
}

// ---------------- vzhled ----------------
static void applyTheme() {
    applyThemeColors();
    BOOL on = g_dark; DwmSetWindowAttribute(g_main, 20, &on, sizeof(on));
    for (HWND l : { g_list, g_rsList, g_rsAsg, g_usList, g_usTasks, g_caWeek, g_caExc, g_prList, g_dtAsg, g_dtLinks }) { if (!l) continue; ListView_SetBkColor(l, T.bg); ListView_SetTextBkColor(l, T.bg); ListView_SetTextColor(l, T.ink); SetWindowTheme(l, g_dark ? L"DarkMode_Explorer" : L"Explorer", nullptr); HWND hd = ListView_GetHeader(l); if (hd) SetWindowTheme(hd, g_dark ? L"DarkMode_ItemsView" : L"Explorer", nullptr); }
    for (HWND e : { g_search, g_dtNotes, g_dtGen }) if (e) SetWindowTheme(e, g_dark ? L"DarkMode_Explorer" : L"Explorer", nullptr);
    for (HWND c : { g_filter, g_rscCombo, g_scale, g_level, g_tlLevel, g_usPeriod, g_usMode, g_caCombo }) if (c) SetWindowTheme(c, g_dark ? L"DarkMode_CFD" : L"Explorer", nullptr);
    for (HWND b : { g_tlPng, g_caPrev, g_caNext, g_caToday, g_tlMs, g_tlCrit, g_usOver, g_advBtn, g_zoomOut, g_zoomIn, g_chkLinks, g_chkBase, g_todayBtn }) if (b) SetWindowTheme(b, g_dark ? L"DarkMode_Explorer" : L"Explorer", nullptr);
    for (HWND t : { g_tabs, g_dtTabs }) if (t) SetWindowTheme(t, g_dark ? L"DarkMode_Explorer" : L"Explorer", nullptr);
    CheckMenuItem(GetMenu(g_main), IDM_DARK, g_dark ? MF_CHECKED : MF_UNCHECKED);
    RedrawWindow(g_main, nullptr, nullptr, RDW_INVALIDATE | RDW_ERASE | RDW_ALLCHILDREN | RDW_FRAME);
}
static void applyFonts() {
    struct FontMap { HFONT from[3], to[3]; } fm = { { g_font, g_fontBold, g_fontSmall }, {} };
    createFonts();
    fm.to[0] = g_font; fm.to[1] = g_fontBold; fm.to[2] = g_fontSmall;
    // i popisky panelů, které nemají vlastní proměnnou – jinak by zůstaly se smazaným písmem (systémové písmo)
    EnumChildWindows(g_main, [](HWND c, LPARAM lp) -> BOOL { const FontMap* m = (const FontMap*)lp; HFONT f = (HFONT)SendMessage(c, WM_GETFONT, 0, 0); for (int i = 0; i < 3; i++) if (f && f == m->from[i]) { SendMessage(c, WM_SETFONT, (WPARAM)m->to[i], FALSE); break; } return TRUE; }, (LPARAM)&fm);
    for (HWND c : { g_search, g_filter, g_rscCombo, g_scale, g_summary, g_list, g_status, g_tabs, g_rsList, g_rsAsg, g_rsSummary, g_usList, g_usTasks, g_usSummary, g_usPeriod, g_usMode, g_usOver, g_caCombo, g_caWeek, g_caExc, g_caTitle, g_caPrev, g_caNext, g_caToday, g_prList, g_tlLevel, g_tlMs, g_tlCrit, g_tlPng, g_tlSummary,
                    g_lblSearch, g_lblFilter, g_lblLevel, g_lblScale, g_advBtn, g_level, g_zoomOut, g_zoomIn, g_chkLinks, g_chkBase, g_todayBtn, g_dtTabs, g_dtAsg, g_dtLinks, g_dtNotes }) if (c) SendMessage(c, WM_SETFONT, (WPARAM)g_font, TRUE);
    for (HWND c : { g_rsTitle, g_usTitle }) if (c) SendMessage(c, WM_SETFONT, (WPARAM)g_fontBold, TRUE);
    layout(); if (g_loaded) rebuildRows(selectedUid());
    updateDetail();
}
// Okno je na monitoru s jiným měřítkem (přesun mezi monitory, start na druhém monitoru): přepočítat písma, šířky sloupců,
// prvky panelů umístěné jen při vytvoření a rozložení. Bez toho zůstal text po přesunu z 150 % na 100 % obří (a naopak).
static void onDpiChanged(int dpi) {
    int old = g_dpi; if (dpi <= 0 || dpi == old) return;
    g_dpi = dpi;
    if (g_splitPref > 0) g_splitPref = MulDiv(g_splitPref, dpi, old);
    if (g_detailH > 0) g_detailH = MulDiv(g_detailH, dpi, old);
    struct Scale { int n, d; HWND parent; } sc = { dpi, old, nullptr };
    EnumChildWindows(g_main, [](HWND w, LPARAM lp) -> BOOL { // sloupce všech tabulek (hlavní, panely, detail)
        const Scale* s = (const Scale*)lp; wchar_t cls[32] = {}; GetClassNameW(w, cls, 32);
        if (_wcsicmp(cls, WC_LISTVIEWW) == 0) for (int i = 0, n = Header_GetItemCount(ListView_GetHeader(w)); i < n; i++) ListView_SetColumnWidth(w, i, MulDiv(ListView_GetColumnWidth(w, i), s->n, s->d));
        return TRUE; }, (LPARAM)&sc);
    for (HWND p : g_panels) if (p) { // přímé prvky panelů (vnořené, např. záhlaví tabulky, si umístí jejich rodič)
        sc.parent = p;
        EnumChildWindows(p, [](HWND w, LPARAM lp) -> BOOL {
            const Scale* s = (const Scale*)lp; if (GetParent(w) != s->parent) return TRUE;
            RECT r; GetWindowRect(w, &r); MapWindowPoints(HWND_DESKTOP, s->parent, (POINT*)&r, 2);
            wchar_t cls[32] = {}; GetClassNameW(w, cls, 32);
            if (_wcsicmp(cls, L"ComboBox") == 0) { RECT d; if (SendMessage(w, CB_GETDROPPEDCONTROLRECT, 0, (LPARAM)&d)) r.bottom = r.top + (d.bottom - d.top); } // výška i s rozbalovacím seznamem
            SetWindowPos(w, nullptr, MulDiv(r.left, s->n, s->d), MulDiv(r.top, s->n, s->d), MulDiv(r.right - r.left, s->n, s->d), MulDiv(r.bottom - r.top, s->n, s->d), SWP_NOZORDER | SWP_NOACTIVATE);
            return TRUE; }, (LPARAM)&sc);
    }
    applyFonts(); // písma pro nové DPI, rozložení hlavního okna a tabulka
    for (HWND p : g_panels) if (p) { RECT r; GetClientRect(p, &r); SendMessage(p, WM_SIZE, SIZE_RESTORED, MAKELPARAM(r.right, r.bottom)); }
    if (g_detail) { RECT r; GetClientRect(g_detail, &r); SendMessage(g_detail, WM_SIZE, SIZE_RESTORED, MAKELPARAM(r.right, r.bottom)); }
    RedrawWindow(g_main, nullptr, nullptr, RDW_INVALIDATE | RDW_ERASE | RDW_ALLCHILDREN | RDW_FRAME);
}

// ---------------- hlavní okno ----------------
static std::vector<HWND> toolbarCtls() { return { g_lblSearch, g_search, g_lblFilter, g_filter, g_advBtn, g_rscCombo, g_lblLevel, g_level, g_lblScale, g_scale, g_zoomOut, g_zoomIn, g_chkLinks, g_chkBase, g_todayBtn }; }
static void setTab(int tab) {
    g_tab = imax(0, imin(5, tab)); TabCtrl_SetCurSel(g_tabs, g_tab);
    for (int i = 0; i < 6; i++) if (g_panels[i]) ShowWindow(g_panels[i], i == g_tab ? SW_SHOW : SW_HIDE);
    for (HWND c : { g_list, g_gantt, g_detail, g_summary }) ShowWindow(c, g_tab == 0 ? SW_SHOW : SW_HIDE);
    for (HWND c : toolbarCtls()) ShowWindow(c, g_tab == 0 ? SW_SHOW : SW_HIDE);
    layout();
    if (g_tab == 0) { ShowWindow(g_list, g_loaded ? SW_SHOW : SW_HIDE); ShowWindow(g_detail, g_loaded && g_detailVisible ? SW_SHOW : SW_HIDE); }
}
static int g_detailTop = 0; // y horní hrany panelu detailu (kvůli tažení dělicí čáry)
static const int DETAIL_GRIP = 6;
static void layoutPanes();
static const int TAB_H = 26, BAR_H = 32, SUM_H = 22; // záložky, lišta, souhrnný řádek (při 96 DPI)
static int panesTop() { return S(TAB_H) + S(BAR_H) + S(SUM_H); }
static void layout() {
    RECT rc; GetClientRect(g_main, &rc);
    SendMessage(g_status, WM_SIZE, 0, 0); RECT sr; GetWindowRect(g_status, &sr);
    int parts[3] = { S(460), S(900), -1 }; SendMessage(g_status, SB_SETPARTS, 3, (LPARAM)parts);
    int tabH = S(TAB_H); MoveWindow(g_tabs, 0, 0, rc.right, tabH, TRUE);
    int bottom = rc.bottom - (sr.bottom - sr.top);
    if (g_tab != 0) { if (g_panels[g_tab]) MoveWindow(g_panels[g_tab], 0, tabH, rc.right, bottom - tabH, TRUE); return; }
    int barH = S(BAR_H), sumH = S(SUM_H);
    // lišta: popisky podle šířky textu, hledací pole dostane zbylé místo (110–260 px)
    HDC dc = GetDC(g_main); HGDIOBJ of = SelectObject(dc, g_font);
    auto tw = [&](HWND c) { std::wstring s = ctlText(c); SIZE sz; GetTextExtentPoint32W(dc, s.c_str(), (int)s.size(), &sz); return (int)sz.cx; };
    struct Item { HWND h; int w; int gapAfter; };
    std::vector<Item> items = { { g_lblSearch, tw(g_lblSearch) + S(4), 0 }, { g_search, 0, S(12) }, { g_lblFilter, tw(g_lblFilter) + S(4), 0 }, { g_filter, S(160), S(4) }, { g_advBtn, tw(g_advBtn) + S(20), S(8) },
                                { g_rscCombo, S(150), S(12) }, { g_lblLevel, tw(g_lblLevel) + S(4), 0 }, { g_level, S(100), S(12) }, { g_lblScale, tw(g_lblScale) + S(4), 0 }, { g_scale, S(130), S(4) },
                                { g_zoomOut, S(26), S(2) }, { g_zoomIn, S(26), S(12) }, { g_chkLinks, tw(g_chkLinks) + S(26), S(6) }, { g_chkBase, tw(g_chkBase) + S(26), S(10) }, { g_todayBtn, tw(g_todayBtn) + S(20), 0 } };
    SelectObject(dc, of); ReleaseDC(g_main, dc);
    int fixed = S(6) * 2; for (auto& it : items) fixed += it.w + it.gapAfter;
    items[1].w = imax(S(110), imin(S(260), (int)rc.right - fixed));
    int x = S(6), y = tabH + S(5);
    for (auto& it : items) {
        bool combo = it.h == g_filter || it.h == g_rscCombo || it.h == g_level || it.h == g_scale;
        bool label = it.h == g_lblSearch || it.h == g_lblFilter || it.h == g_lblLevel || it.h == g_lblScale;
        MoveWindow(it.h, x, label ? y + S(5) : y, it.w, combo ? S(300) : label ? S(18) : S(23), TRUE);
        x += it.w + it.gapAfter;
    }
    MoveWindow(g_summary, S(8), tabH + barH + S(3), rc.right - S(16), sumH - S(4), TRUE);
    layoutPanes();
    InvalidateRect(g_main, nullptr, TRUE);
}
// Tabulka, graf a detail pod lištou. Při tažení dělicích čar se volá samostatně: lišta a stavový řádek se nemění
// a jejich přepočet trval ~30 ms na pohyb myši, takže Windows nestíhaly kreslit a mezi tabulkou a grafem zůstávaly staré pixely.
static void layoutPanes() {
    RECT rc; GetClientRect(g_main, &rc); RECT sr; GetWindowRect(g_status, &sr);
    int bottom = rc.bottom - (sr.bottom - sr.top), top = panesTop();
    int detH = 0;
    if (g_detailVisible && g_loaded) { if (g_detailH <= 0) g_detailH = S(270); detH = imax(S(90), imin(g_detailH, bottom - top - S(120))); }
    int h = bottom - top - detH;
    // omezení jen do g_splitX: úzké okno při startu (1280 px před maximalizací) ani zúžení nesmí trvale zmenšit zvolenou šířku
    g_splitX = imax(S(150), imin(g_splitPref > 0 ? g_splitPref : (int)rc.right * 52 / 100, (int)rc.right - S(150)));
    if (g_loaded) { ShowWindow(g_list, SW_SHOW); MoveWindow(g_list, 0, top, g_splitX, h, TRUE); MoveWindow(g_gantt, g_splitX + SPLIT_W, top, rc.right - g_splitX - SPLIT_W, h, TRUE); }
    else { ShowWindow(g_list, SW_HIDE); MoveWindow(g_gantt, 0, top, rc.right, h, TRUE); }
    ShowWindow(g_detail, detH ? SW_SHOW : SW_HIDE);
    g_detailTop = top + h;
    if (detH) MoveWindow(g_detail, 0, top + h + S(DETAIL_GRIP), rc.right, detH - S(DETAIL_GRIP), TRUE);
    RECT r; if (!g_rows.empty() && ListView_GetItemRect(g_list, 0, &r, LVIR_BOUNDS)) g_rowH = r.bottom - r.top;
    HWND hdr = ListView_GetHeader(g_list); if (hdr && GetWindowRect(hdr, &r)) g_headerH = r.bottom - r.top;
    updateScroll();
}
static LRESULT onCustomDraw(NMLVCUSTOMDRAW* cd) {
    switch (cd->nmcd.dwDrawStage) {
    case CDDS_PREPAINT: return CDRF_NOTIFYITEMDRAW;
    case CDDS_ITEMPREPAINT: { int row = (int)cd->nmcd.dwItemSpec; if (row < 0 || row >= (int)g_rows.size()) return CDRF_DODEFAULT; if (g_prj.tasks[g_rows[row]].summary) { SelectObject(cd->nmcd.hdc, g_fontBold); return CDRF_NEWFONT | CDRF_NOTIFYSUBITEMDRAW; } return CDRF_NOTIFYSUBITEMDRAW; }
    case CDDS_ITEMPREPAINT | CDDS_SUBITEM: {
        int row = (int)cd->nmcd.dwItemSpec; if (row < 0 || row >= (int)g_rows.size() || cd->iSubItem >= (int)g_visible.size()) return CDRF_DODEFAULT;
        const mpp::Task& t = g_prj.tasks[g_rows[row]]; const std::wstring& k = g_cols[g_visible[cd->iSubItem]].key;
        cd->clrText = !t.active ? T.muted : T.ink; cd->clrTextBk = T.bg;
        if (k == L"name" && t.critical && !t.summary) cd->clrText = T.late;
        if (k == L"state" || k == L"finish") { if (isLate(t)) cd->clrText = T.late; else if (isLateStart(t) && k == L"state") cd->clrText = T.lateStart; else if (t.pctComplete >= 100 && k == L"state") cd->clrText = T.ok; }
        if (k == L"variance" || k == L"startVariance") { std::wstring v = cellText(t, g_rows[row], g_visible[cd->iSubItem]); if (!v.empty() && v[0] == L'+') cd->clrText = T.late; else if (!v.empty() && v[0] == L'-') cd->clrText = T.ok; }
        if (k == L"totalSlack" && t.totalSlackRaw != INT32_MIN && t.totalSlackRaw < 0 && !t.summary) cd->clrText = T.late;
        return CDRF_NEWFONT;
    }
    }
    return CDRF_DODEFAULT;
}
static void contextMenu(int row, POINT screen) {
    if (row < 0 || row >= (int)g_rows.size()) return;
    const mpp::Task& t = g_prj.tasks[g_rows[row]];
    HMENU m = CreatePopupMenu(); std::vector<int> targets;
    for (const mpp::Relation& r : t.preds) { const mpp::Task* p = g_prj.byUid(r.predUid); if (!p) continue; AppendMenuW(m, MF_STRING, IDM_GOTO_BASE + targets.size(), (L"Předchůdce  " + std::to_wstring(p->id) + L"  " + p->name).c_str()); targets.push_back(p->uid); }
    auto it = g_succ.find(t.uid);
    if (it != g_succ.end()) for (int u : it->second) { const mpp::Task* s = g_prj.byUid(u); if (!s) continue; AppendMenuW(m, MF_STRING, IDM_GOTO_BASE + targets.size(), (L"Následník  " + std::to_wstring(s->id) + L"  " + s->name).c_str()); targets.push_back(s->uid); }
    if (t.parent >= 0) { const mpp::Task& p = g_prj.tasks[t.parent]; AppendMenuW(m, MF_STRING, IDM_GOTO_BASE + targets.size(), (L"Nadřazený  " + std::to_wstring(p.id) + L"  " + p.name).c_str()); targets.push_back(p.uid); }
    if (!targets.empty()) AppendMenuW(m, MF_SEPARATOR, 0, nullptr);
    if (t.summary) AppendMenuW(m, MF_STRING, IDM_EXPAND_ALL + 1000, t.expanded ? L"Sbalit" : L"Rozbalit");
    AppendMenuW(m, MF_STRING, IDM_GOTO_SEL, L"Zobrazit v diagramu\tCtrl+G"); AppendMenuW(m, MF_STRING, IDM_COPY, L"Kopírovat řádky\tCtrl+C");
    int cmd = TrackPopupMenu(m, TPM_RETURNCMD | TPM_RIGHTBUTTON, screen.x, screen.y, 0, g_main, nullptr); DestroyMenu(m);
    if (cmd >= IDM_GOTO_BASE && cmd < IDM_GOTO_BASE + (int)targets.size()) selectTaskByUid(targets[cmd - IDM_GOTO_BASE]);
    else if (cmd == IDM_EXPAND_ALL + 1000) toggleRow(row); else if (cmd == IDM_GOTO_SEL) gotoSelected(); else if (cmd == IDM_COPY) copySelectedRows();
}
static void saveCurrentView() {
    std::wstring name; if (!askText(L"Uložit pohled", L"Název pohledu (sloupce, filtr, rozbalení, měřítko, obarvení):", name) || name.empty()) return;
    SavedView v; v.name = name; v.cols = visibleKeys(); v.filterMode = g_filterMode; v.adv = g_adv; v.pxPerDay = g_pxPerDay; v.colorKey = g_colorKey;
    int maxExp = 0; for (auto& t : g_prj.tasks) if (t.summary && t.expanded) maxExp = imax(maxExp, t.level + 1); v.level = maxExp;
    g_views.erase(std::remove_if(g_views.begin(), g_views.end(), [&](const SavedView& x) { return x.name == name; }), g_views.end()); g_views.push_back(v); storeViews(); rebuildViewsMenu();
}
static void deleteView() {
    if (g_views.empty()) return;
    Dlg d; dlgCreate(d, L"Smazat pohled", 320, 110); dlgLabel(d.h, L"Pohled:", 12, 14, 60); HWND c = dlgCombo(d.h, 70, 11, 236, 100); for (auto& v : g_views) SendMessage(c, CB_ADDSTRING, 0, (LPARAM)v.name.c_str()); SendMessage(c, CB_SETCURSEL, 0, 0);
    dlgButton(d.h, L"Smazat", 150, 70, 70, IDOK, true); dlgButton(d.h, L"Zrušit", 228, 70, 78, IDCANCEL);
    d.onCmd = [&](int id, int, HWND) { if (id == IDOK) { int i = (int)SendMessage(c, CB_GETCURSEL, 0, 0); if (i >= 0 && i < (int)g_views.size()) g_views.erase(g_views.begin() + i); storeViews(); rebuildViewsMenu(); d.done = true; } else if (id == IDCANCEL) d.done = true; };
    dlgRun(d);
}
// Licenční podmínky jsou vložené do exe (mppview.rc: LICENSE RCDATA), aby šly zobrazit i bez souboru ve složce programu.
static std::wstring embeddedLicense() {
    HRSRC r = FindResourceW(nullptr, L"LICENSE", RT_RCDATA); HGLOBAL g = r ? LoadResource(nullptr, r) : nullptr;
    const unsigned char* p = g ? (const unsigned char*)LockResource(g) : nullptr; int n = p ? (int)SizeofResource(nullptr, r) : 0;
    if (n >= 3 && p[0] == 0xEF && p[1] == 0xBB && p[2] == 0xBF) { p += 3; n -= 3; }
    int wn = n > 0 ? MultiByteToWideChar(CP_UTF8, 0, (const char*)p, n, nullptr, 0) : 0;
    std::wstring w((size_t)imax(0, wn), L' '); if (wn > 0) MultiByteToWideChar(CP_UTF8, 0, (const char*)p, n, &w[0], wn);
    std::wstring s; for (wchar_t c : w) { if (c == L'\r') continue; if (c == L'\n') s += L'\r'; s += c; } // EDIT láme řádky jen na CRLF
    return s;
}
static void licenseDialog() {
    Dlg d; dlgCreate(d, L"Licenční podmínky", 760, 560, true);
    HWND e = dlgCtl(d.h, L"EDIT", embeddedLicense().c_str(), WS_TABSTOP | WS_VSCROLL | ES_MULTILINE | ES_READONLY | ES_AUTOVSCROLL, 12, 12, 736, 500, 100, WS_EX_CLIENTEDGE);
    SendMessage(e, EM_SETMARGINS, EC_LEFTMARGIN | EC_RIGHTMARGIN, MAKELPARAM(S(8), S(8)));
    if (g_dark) SetWindowTheme(e, L"DarkMode_Explorer", nullptr);
    HWND b = dlgButton(d.h, L"Zavřít", 660, 524, 88, IDCANCEL, true);
    d.onSize = [&](int w, int h) { MoveWindow(e, S(12), S(12), w - S(24), h - S(60), TRUE); MoveWindow(b, w - S(100), h - S(36), S(88), S(24), TRUE); };
    d.onCmd = [&](int id, int, HWND) { if (id == IDCANCEL) d.done = true; };
    RECT rc; GetClientRect(d.h, &rc); d.onSize(rc.right, rc.bottom);
    SetFocus(e);
    dlgRun(d);
}
static LRESULT CALLBACK MainProc(HWND hwnd, UINT msg, WPARAM wp, LPARAM lp) {
    switch (msg) {
    case WM_CREATE: {
        g_main = hwnd;
        g_tabs = CreateWindowExW(0, WC_TABCONTROLW, L"", WS_CHILD | WS_VISIBLE | WS_CLIPSIBLINGS | TCS_FOCUSNEVER, 0, 0, 10, 10, hwnd, (HMENU)IDC_TABS, g_inst, nullptr);
        SendMessage(g_tabs, WM_SETFONT, (WPARAM)g_font, TRUE);
        for (int i = 0; i < 6; i++) { TCITEMW ti = {}; ti.mask = TCIF_TEXT; ti.pszText = (LPWSTR)TAB_NAMES[i]; TabCtrl_InsertItem(g_tabs, i, &ti); }
        g_search = CreateWindowExW(WS_EX_CLIENTEDGE, L"EDIT", L"", WS_CHILD | WS_VISIBLE | WS_TABSTOP | ES_AUTOHSCROLL, 0, 0, 10, 10, hwnd, (HMENU)IDC_SEARCH, g_inst, nullptr);
        SendMessage(g_search, EM_SETCUEBANNER, TRUE, (LPARAM)L"Hledat úkol, zdroj, ID, WBS… (Ctrl+F)"); SetWindowSubclass(g_search, SearchSub, 2, 0);
        g_filter = CreateWindowExW(0, L"COMBOBOX", L"", WS_CHILD | WS_VISIBLE | WS_TABSTOP | CBS_DROPDOWNLIST | WS_VSCROLL, 0, 0, 10, 10, hwnd, (HMENU)IDC_FILTER, g_inst, nullptr);
        for (int i = 0; i < FM_COUNT; i++) SendMessage(g_filter, CB_ADDSTRING, 0, (LPARAM)FILTER_NAMES[i]); SendMessage(g_filter, CB_SETCURSEL, 0, 0);
        g_rscCombo = CreateWindowExW(0, L"COMBOBOX", L"", WS_CHILD | WS_VISIBLE | WS_TABSTOP | CBS_DROPDOWNLIST | WS_VSCROLL, 0, 0, 10, 10, hwnd, (HMENU)IDC_RSC, g_inst, nullptr);
        SendMessage(g_rscCombo, CB_ADDSTRING, 0, (LPARAM)L"(všechny zdroje)"); SendMessage(g_rscCombo, CB_SETCURSEL, 0, 0);
        g_scale = CreateWindowExW(0, L"COMBOBOX", L"", WS_CHILD | WS_VISIBLE | WS_TABSTOP | CBS_DROPDOWNLIST, 0, 0, 10, 10, hwnd, (HMENU)IDC_SCALE, g_inst, nullptr);
        for (const wchar_t* s : { L"Dny", L"Týdny", L"Měsíce", L"Přizpůsobit oknu" }) SendMessage(g_scale, CB_ADDSTRING, 0, (LPARAM)s);
        auto label = [&](const wchar_t* t) { HWND l = CreateWindowExW(0, L"STATIC", t, WS_CHILD | WS_VISIBLE | SS_LEFT, 0, 0, 10, 10, hwnd, nullptr, g_inst, nullptr); SendMessage(l, WM_SETFONT, (WPARAM)g_font, TRUE); return l; };
        auto button = [&](const wchar_t* t, int id, DWORD style) { HWND b = CreateWindowExW(0, L"BUTTON", t, WS_CHILD | WS_VISIBLE | WS_TABSTOP | style, 0, 0, 10, 10, hwnd, (HMENU)(INT_PTR)id, g_inst, nullptr); SendMessage(b, WM_SETFONT, (WPARAM)g_font, TRUE); return b; };
        g_lblSearch = label(L"Hledat:"); g_lblFilter = label(L"Filtr:"); g_lblLevel = label(L"Osnova:"); g_lblScale = label(L"Měřítko:");
        g_advBtn = button(L"Filtr…", IDC_ADVBTN, BS_PUSHBUTTON); g_zoomOut = button(L"−", IDC_ZOOMOUT, BS_PUSHBUTTON); g_zoomIn = button(L"+", IDC_ZOOMIN, BS_PUSHBUTTON);
        g_chkLinks = button(L"Vazby", IDC_LINKS, BS_AUTOCHECKBOX); g_chkBase = button(L"Základní plán", IDC_BASELINE, BS_AUTOCHECKBOX); g_todayBtn = button(L"Dnes", IDC_TODAY, BS_PUSHBUTTON);
        SendMessage(g_chkLinks, BM_SETCHECK, g_showLinks ? BST_CHECKED : BST_UNCHECKED, 0); SendMessage(g_chkBase, BM_SETCHECK, g_showBaseline ? BST_CHECKED : BST_UNCHECKED, 0);
        g_level = CreateWindowExW(0, L"COMBOBOX", L"", WS_CHILD | WS_VISIBLE | WS_TABSTOP | CBS_DROPDOWNLIST | WS_VSCROLL, 0, 0, 10, 10, hwnd, (HMENU)IDC_LEVEL, g_inst, nullptr);
        SendMessage(g_level, WM_SETFONT, (WPARAM)g_font, TRUE);
        SendMessage(g_level, CB_ADDSTRING, 0, (LPARAM)L"Vše"); for (int i = 1; i <= 9; i++) SendMessage(g_level, CB_ADDSTRING, 0, (LPARAM)(L"Úroveň " + std::to_wstring(i)).c_str());
        SendMessage(g_level, CB_SETCURSEL, 0, 0);
        g_summary = CreateWindowExW(0, L"STATIC", L"", WS_CHILD | WS_VISIBLE | SS_LEFTNOWORDWRAP | SS_ENDELLIPSIS, 0, 0, 10, 10, hwnd, (HMENU)IDC_SUMMARY, g_inst, nullptr);
        for (HWND c : { g_search, g_filter, g_rscCombo, g_scale, g_summary }) SendMessage(c, WM_SETFONT, (WPARAM)g_font, TRUE);
        g_list = CreateWindowExW(0, WC_LISTVIEWW, L"", WS_CHILD | WS_VISIBLE | WS_TABSTOP | LVS_REPORT | LVS_SHOWSELALWAYS, 0, 0, 10, 10, hwnd, (HMENU)1, g_inst, nullptr);
        ListView_SetExtendedListViewStyle(g_list, LVS_EX_FULLROWSELECT | LVS_EX_GRIDLINES | LVS_EX_DOUBLEBUFFER | LVS_EX_HEADERDRAGDROP);
        SendMessage(g_list, WM_SETFONT, (WPARAM)g_font, TRUE); SetWindowSubclass(g_list, ListSub, 1, 0);
        g_gantt = CreateWindowExW(0, L"MppGantt", L"", WS_CHILD | WS_VISIBLE | WS_HSCROLL, 0, 0, 10, 10, hwnd, (HMENU)2, g_inst, nullptr);
        g_detail = CreateWindowExW(0, L"MppDetail", L"", WS_CHILD | WS_VISIBLE | WS_CLIPCHILDREN, 0, 0, 10, 10, hwnd, (HMENU)IDC_DETAIL, g_inst, nullptr);
        g_status = CreateWindowExW(0, STATUSCLASSNAMEW, L"", WS_CHILD | WS_VISIBLE | SBARS_SIZEGRIP, 0, 0, 0, 0, hwnd, (HMENU)3, g_inst, nullptr);
        SendMessage(g_status, WM_SETFONT, (WPARAM)g_font, TRUE);
        g_tip = CreateWindowExW(WS_EX_TOOLWINDOW | WS_EX_TOPMOST | WS_EX_NOACTIVATE, L"MppTip", L"", WS_POPUP | WS_BORDER, 0, 0, 10, 10, hwnd, nullptr, g_inst, nullptr);
        const wchar_t* pc[] = { nullptr, L"MppTimeline", L"MppResources", L"MppUsage", L"MppCalendar", L"MppProject" };
        for (int i = 1; i < 6; i++) g_panels[i] = CreateWindowExW(0, pc[i], L"", WS_CHILD | WS_CLIPCHILDREN, 0, 0, 10, 10, hwnd, nullptr, g_inst, nullptr);
        DragAcceptFiles(hwnd, TRUE); SetTimer(hwnd, 2, 2000, nullptr);
        return 0;
    }
    case WM_SIZE: if (wp != SIZE_MINIMIZED) layout(); return 0; // minimalizované okno má nulovou šířku – příčka by se srazila na minimum
    case WM_GETMINMAXINFO: ((MINMAXINFO*)lp)->ptMinTrackSize = { S(760), S(400) }; return 0;
    case WM_CTLCOLORSTATIC: case WM_CTLCOLOREDIT: { HDC hdc = (HDC)wp; SetTextColor(hdc, T.ink); SetBkColor(hdc, msg == WM_CTLCOLOREDIT ? T.bg : T.panel); static HBRUSH bp, be; if (bp) DeleteObject(bp); if (be) DeleteObject(be); bp = CreateSolidBrush(T.panel); be = CreateSolidBrush(T.bg); return (LRESULT)(msg == WM_CTLCOLOREDIT ? be : bp); }
    case WM_ERASEBKGND: {
        RECT rc; GetClientRect(hwnd, &rc); HBRUSH b = CreateSolidBrush(T.panel); FillRect((HDC)wp, &rc, b); DeleteObject(b);
        if (g_tab == 0 && g_loaded && g_detailVisible) { // úchyt dělicí čáry nad detailem („•••“)
            HBRUSH d = CreateSolidBrush(T.muted); int cx = rc.right / 2, cy = g_detailTop + S(DETAIL_GRIP) / 2;
            for (int i = -1; i <= 1; i++) { RECT r = { cx + i * S(5) - S(1), cy - S(1), cx + i * S(5) + S(1), cy + S(1) }; FillRect((HDC)wp, &r, d); }
            DeleteObject(d);
        }
        return 1;
    }
    case WM_SETCURSOR: {
        POINT pt; GetCursorPos(&pt); ScreenToClient(hwnd, &pt); RECT lr; GetWindowRect(g_list, &lr); MapWindowPoints(nullptr, hwnd, (POINT*)&lr, 2);
        if (g_tab == 0 && g_loaded && LOWORD(lp) == HTCLIENT && pt.x >= g_splitX && pt.x < g_splitX + SPLIT_W && pt.y >= lr.top && pt.y < lr.bottom) { SetCursor(LoadCursor(nullptr, IDC_SIZEWE)); return TRUE; }
        if (g_tab == 0 && g_loaded && g_detailVisible && LOWORD(lp) == HTCLIENT && pt.y >= g_detailTop && pt.y < g_detailTop + S(DETAIL_GRIP)) { SetCursor(LoadCursor(nullptr, IDC_SIZENS)); return TRUE; }
        break;
    }
    case WM_LBUTTONDOWN: {
        int x = GET_X_LPARAM(lp), y = GET_Y_LPARAM(lp); RECT lr; GetWindowRect(g_list, &lr); MapWindowPoints(nullptr, hwnd, (POINT*)&lr, 2);
        if (g_tab == 0 && g_loaded && g_detailVisible && y >= g_detailTop && y < g_detailTop + S(DETAIL_GRIP)) { g_dragDetail = true; SetCapture(hwnd); }
        else if (g_tab == 0 && g_loaded && x >= g_splitX && x < g_splitX + SPLIT_W && y >= lr.top) { g_dragSplit = true; SetCapture(hwnd); }
        return 0;
    }
    case WM_MOUSEMOVE:
        if (g_dragSplit || g_dragDetail) {
            int oldSplit = g_splitPref, oldDetH = g_detailH;
            if (g_dragSplit) g_splitPref = GET_X_LPARAM(lp);
            else { RECT rc, sr; GetClientRect(hwnd, &rc); GetWindowRect(g_status, &sr); g_detailH = imax(S(90), (int)rc.bottom - (sr.bottom - sr.top) - GET_Y_LPARAM(lp)); }
            if (g_splitPref == oldSplit && g_detailH == oldDetH) return 0; // posun oken pod kurzorem pošle další WM_MOUSEMOVE se stejnou pozicí
            layoutPanes();
            // nakreslit hned, co posun odkryl – jinak při rychlém tažení Windows nestíhají kreslit a zůstávají staré pixely
            RedrawWindow(hwnd, nullptr, nullptr, RDW_UPDATENOW | RDW_ALLCHILDREN);
        }
        return 0;
    case WM_LBUTTONUP: if (g_dragSplit || g_dragDetail) { g_dragSplit = g_dragDetail = false; ReleaseCapture(); } return 0;
    case WM_CAPTURECHANGED: g_dragSplit = g_dragDetail = false; return 0; // myš sebral dialog nebo Alt+Tab – tažení skončilo
    case WM_CONTEXTMENU: {
        if ((HWND)wp != g_list) break;
        POINT pt = { GET_X_LPARAM(lp), GET_Y_LPARAM(lp) };
        HWND hdr = ListView_GetHeader(g_list); RECT hr; GetWindowRect(hdr, &hr);
        if (pt.x != -1 && PtInRect(&hr, pt)) { columnChooser(pt); return 0; }
        int row = ListView_GetNextItem(g_list, -1, LVNI_SELECTED);
        if (pt.x == -1) { RECT r; ListView_GetItemRect(g_list, imax(0, row), &r, LVIR_BOUNDS); pt = { r.left + S(40), r.bottom }; ClientToScreen(g_list, &pt); }
        contextMenu(row, pt); return 0;
    }
    case WM_NOTIFY: {
        NMHDR* nm = (NMHDR*)lp;
        if (nm->hwndFrom == g_tabs && nm->code == TCN_SELCHANGE) { setTab(TabCtrl_GetCurSel(g_tabs)); return 0; }
        if (nm->hwndFrom == ListView_GetHeader(g_list)) {
            if (nm->code == HDN_ENDDRAG) { PostMessage(hwnd, WM_APP + 1, 0, 0); return 0; }
            if (nm->code == HDN_ENDTRACK || nm->code == HDN_DIVIDERDBLCLICK) PostMessage(hwnd, WM_APP + 2, 0, 0);
            if (nm->code == HDN_ITEMCLICK) { sortByColumn(((NMHEADERW*)lp)->iItem); return 0; }
            if (nm->code == NM_RCLICK) { POINT pt; GetCursorPos(&pt); columnChooser(pt); return 1; }
        }
        if (nm->hwndFrom == g_list) {
            switch (nm->code) {
            case LVN_ITEMCHANGED: { NMLISTVIEW* lv = (NMLISTVIEW*)lp; if ((lv->uNewState ^ lv->uOldState) & LVIS_SELECTED) { if (lv->uNewState & LVIS_SELECTED) g_selRow = lv->iItem; else if (g_selRow == lv->iItem) g_selRow = ListView_GetNextItem(g_list, -1, LVNI_SELECTED); InvalidateRect(g_gantt, nullptr, FALSE); updateDetail(); updateSelectionStatus(); } break; }
            case NM_CLICK: { NMITEMACTIVATE* ia = (NMITEMACTIVATE*)lp; LVHITTESTINFO ht = {}; ht.pt = ia->ptAction; ListView_SubItemHitTest(g_list, &ht); if (ht.iItem >= 0 && ht.iSubItem < (int)g_visible.size() && ht.iSubItem >= 0 && g_cols[g_visible[ht.iSubItem]].key == L"name") { RECT rc; ListView_GetSubItemRect(g_list, ht.iItem, ht.iSubItem, LVIR_LABEL, &rc); const mpp::Task& t = g_prj.tasks[g_rows[ht.iItem]]; int glyphEnd = rc.left + S(6) + (imax(0, t.level - 1) * 3 + 2) * S(5); if (t.summary && ia->ptAction.x < glyphEnd) toggleRow(ht.iItem); } break; }
            case NM_DBLCLK: { NMITEMACTIVATE* ia = (NMITEMACTIVATE*)lp; if (ia->iItem < 0) break; LVHITTESTINFO ht = {}; ht.pt = ia->ptAction; ListView_SubItemHitTest(g_list, &ht); const mpp::Task& t = g_prj.tasks[g_rows[ia->iItem]]; std::wstring k = ht.iSubItem >= 0 && ht.iSubItem < (int)g_visible.size() ? g_cols[g_visible[ht.iSubItem]].key : L""; if (k == L"predecessors" && !t.preds.empty()) selectTaskByUid(t.preds[0].predUid); else if (k == L"successors") { auto it = g_succ.find(t.uid); if (it != g_succ.end() && !it->second.empty()) selectTaskByUid(it->second[0]); } else toggleRow(ia->iItem); break; }
            case LVN_KEYDOWN: { NMLVKEYDOWN* kd = (NMLVKEYDOWN*)lp; int row = ListView_GetNextItem(g_list, -1, LVNI_SELECTED); if (kd->wVKey == VK_ADD || kd->wVKey == VK_RIGHT) toggleRow(row, 1); else if (kd->wVKey == VK_SUBTRACT) toggleRow(row, 0); else if (kd->wVKey == VK_SPACE) toggleRow(row); else if (kd->wVKey == VK_LEFT && row >= 0) { const mpp::Task& t = g_prj.tasks[g_rows[row]]; if (t.summary && t.expanded) toggleRow(row, 0); else if (t.parent >= 0 && g_rowOfTask[t.parent] >= 0) { ListView_SetItemState(g_list, -1, 0, LVIS_SELECTED); ListView_SetItemState(g_list, g_rowOfTask[t.parent], LVIS_SELECTED | LVIS_FOCUSED, LVIS_SELECTED | LVIS_FOCUSED); } } else if (kd->wVKey == VK_APPS && row >= 0) SendMessage(hwnd, WM_CONTEXTMENU, (WPARAM)g_list, MAKELPARAM(-1, -1)); break; }
            case NM_CUSTOMDRAW: return onCustomDraw((NMLVCUSTOMDRAW*)lp);
            }
        }
        return 0;
    }
    case WM_APP + 1: syncColumnOrder(); return 0;
    case WM_APP + 2: rememberColumnWidths(); return 0;
    case WM_APP_FILESTAT: { std::unique_ptr<FileStat> fs((FileStat*)lp); onFileStat(*fs); return 0; }
    case WM_TIMER: if (wp == 1) { KillTimer(hwnd, 1); applyFilterFromControls(); } else if (wp == 2) checkFileChanged(); return 0;
    case WM_DROPFILES: { wchar_t path[MAX_PATH]; if (DragQueryFileW((HDROP)wp, 0, path, MAX_PATH)) loadFile(path); DragFinish((HDROP)wp); return 0; }
    case WM_COMMAND: {
        int id = LOWORD(wp), code = HIWORD(wp);
        if (id == IDC_SEARCH && code == EN_CHANGE) { SetTimer(hwnd, 1, 300, nullptr); return 0; }
        if ((id == IDC_FILTER || id == IDC_RSC) && code == CBN_SELCHANGE) { applyFilterFromControls(); return 0; }
        if (id == IDC_ADVBTN) { advancedFilterDialog(); return 0; }
        if (id == IDC_LEVEL && code == CBN_SELCHANGE) { int l = (int)SendMessage(g_level, CB_GETCURSEL, 0, 0); if (g_loaded) { if (l <= 0) expandAll(true); else expandToLevel(l); } SetFocus(g_list); return 0; }
        if (id == IDC_ZOOMOUT) { setZoom(g_pxPerDay / 1.5, -1); return 0; }
        if (id == IDC_ZOOMIN) { setZoom(g_pxPerDay * 1.5, -1); return 0; }
        if (id == IDC_TODAY) { gotoToday(); return 0; }
        if (id == IDC_LINKS) { g_showLinks = SendMessage(g_chkLinks, BM_GETCHECK, 0, 0) == BST_CHECKED; InvalidateRect(g_gantt, nullptr, FALSE); return 0; }
        if (id == IDC_BASELINE) { g_showBaseline = SendMessage(g_chkBase, BM_GETCHECK, 0, 0) == BST_CHECKED; InvalidateRect(g_gantt, nullptr, FALSE); return 0; }
        if (id == IDC_SCALE && code == CBN_SELCHANGE) { int s = (int)SendMessage(g_scale, CB_GETCURSEL, 0, 0); if (s == 0) setZoom(24, -1); else if (s == 1) setZoom(6, -1); else if (s == 2) setZoom(1.5, -1); else zoomFit(); return 0; }
        if (id >= IDM_RECENT0 && id < IDM_RECENT0 + MAX_RECENT) { int i = id - IDM_RECENT0; if (i < (int)g_recent.size()) loadFile(g_recent[i]); return 0; }
        if (id >= IDM_TAB0 && id < IDM_TAB0 + 6) { setTab(id - IDM_TAB0); return 0; }
        if (id >= IDM_LEVEL1 && id < IDM_LEVEL1 + 6) { expandToLevel(id - IDM_LEVEL1 + 1); return 0; }
        if (id >= IDM_COLOR_BASE && id <= IDM_COLOR_BASE + (int)g_cols.size()) { int ci = id - IDM_COLOR_BASE - 1; g_colorKey = ci < 0 ? L"" : g_cols[ci].key; rebuildColorMenu(); buildBarColors(); InvalidateRect(g_gantt, nullptr, FALSE); return 0; }
        if (id >= IDM_BASELINE_BASE && id <= IDM_BASELINE_BASE + 10) { g_baselineIdx = id - IDM_BASELINE_BASE; rebuildBaselineMenu(); InvalidateRect(g_gantt, nullptr, FALSE); return 0; }
        if (id >= IDM_VIEW_BASE && id < IDM_VIEW_BASE + 20) { int i = id - IDM_VIEW_BASE; if (i < (int)g_views.size() && g_loaded) applyView(g_views[i]); return 0; }
        switch (id) {
        case IDM_OPEN: openDialog(); return 0;
        case IDM_OPEN_NEW: { wchar_t exe[MAX_PATH]; GetModuleFileNameW(nullptr, exe, MAX_PATH); wchar_t buf[MAX_PATH]; if (openDialogPath(buf, L"Otevřít v novém okně")) ShellExecuteW(nullptr, L"open", exe, (L"\"" + std::wstring(buf) + L"\"").c_str(), nullptr, SW_SHOWNORMAL); return 0; }
        case IDM_RELOAD: reloadFile(); return 0;
        case IDM_EXIT: DestroyWindow(hwnd); return 0;
        case IDM_EXPORT_CSV: exportCsv(); return 0;
        case IDM_EXPORT_XLSX: exportXlsx(); return 0;
        case IDM_EXPORT_PNG: exportPng(); return 0;
        case IDM_EXPORT_PDF: exportPdf(); return 0;
        case IDM_PRINT: printReport(); return 0;
        case IDM_COPY: copySelectedRows(); return 0;
        case IDM_ASSOC: setAssoc(true); return 0;
        case IDM_UNASSOC: setAssoc(false); return 0;
        case IDM_ZOOM_IN: setZoom(g_pxPerDay * 1.5, -1); return 0;
        case IDM_ZOOM_OUT: setZoom(g_pxPerDay / 1.5, -1); return 0;
        case IDM_ZOOM_FIT: zoomFit(); return 0;
        case IDM_SCALE_DAYS: setZoom(24, -1); return 0;
        case IDM_SCALE_WEEKS: setZoom(6, -1); return 0;
        case IDM_SCALE_MONTHS: setZoom(1.5, -1); return 0;
        case IDM_TODAY: gotoToday(); return 0;
        case IDM_GOTO_SEL: gotoSelected(); return 0;
        case IDM_EXPAND_ALL: expandAll(true); return 0;
        case IDM_COLLAPSE_ALL: expandAll(false); return 0;
        case IDM_DETAIL: g_detailVisible = !g_detailVisible; CheckMenuItem(GetMenu(hwnd), IDM_DETAIL, g_detailVisible ? MF_CHECKED : MF_UNCHECKED); layout(); updateDetail(); return 0;
        case IDM_PROGRESS: g_progressLine = !g_progressLine; CheckMenuItem(GetMenu(hwnd), IDM_PROGRESS, g_progressLine ? MF_CHECKED : MF_UNCHECKED); InvalidateRect(g_gantt, nullptr, FALSE); return 0;
        case IDM_HOLIDAYS: g_holidays = !g_holidays; CheckMenuItem(GetMenu(hwnd), IDM_HOLIDAYS, g_holidays ? MF_CHECKED : MF_UNCHECKED); InvalidateRect(g_gantt, nullptr, FALSE); return 0;
        case IDM_DARK: g_dark = !g_dark; applyTheme(); return 0;
        case IDM_FONT_UP: if (g_fontPt < 16) { g_fontPt++; applyFonts(); } return 0;
        case IDM_FONT_DOWN: if (g_fontPt > 7) { g_fontPt--; applyFonts(); } return 0;
        case IDM_FIND: setTab(0); SetFocus(g_search); SendMessage(g_search, EM_SETSEL, 0, -1); return 0;
        case IDM_CLEAR_FILTER: clearFilters(); return 0;
        case IDM_ADV_FILTER: setTab(0); advancedFilterDialog(); return 0;
        case IDM_CHECK: scheduleCheckDialog(); return 0;
        case IDM_COMPARE: compareDialog(); return 0;
        case IDM_VIEW_SAVE: if (g_loaded) saveCurrentView(); return 0;
        case IDM_VIEW_DELETE: deleteView(); return 0;
        case IDM_COLS_RESET: g_colWidths.clear(); setVisibleColumns(DEFAULT_COLS); fillListItems(); return 0;
        case IDM_COLS_FIT: autoFitColumns(); return 0;
        case IDM_LICENSE: licenseDialog(); return 0;
        case IDM_LICENSE_DIR: { wchar_t exe[MAX_PATH] = {}; GetModuleFileNameW(nullptr, exe, MAX_PATH); ShellExecuteW(hwnd, nullptr, L"explorer.exe", (L"/select,\"" + std::wstring(exe) + L"\"").c_str(), nullptr, SW_SHOWNORMAL); return 0; }
        case IDM_ABOUT: {
            std::wstring text = (std::wstring(APP_TITLE) + L" " + APP_VERSION + L"\nAutor: " + APP_AUTHOR + L"\n\nProhlížeč harmonogramů .mpp / .xml (Project 2007 a novější) pro kontrolu průběhu zakázek – funguje i bez instalace.\n"
                L"Freeware pro soukromé i komerční použití (viz Nápověda → Licenční podmínky).\n"
                L"Copyright (c) 2026 Milan Knor (M3K Soft).\n\n"
                L"Soubory čte knihovna mppread.dll, licence GNU LGPL 2.1 nebo pozdější:\n"
                L"MPXJ: Copyright (c) Packwood Software 2002-2022 (Jon Iles), https://github.com/joniles/mpxj\n"
                L"Port do C++ a úpravy: Copyright (c) 2026 Milan Knor (M3K Soft).\n"
                L"Zdrojový kód knihovny je přiložen (mppread-src.zip), knihovnu lze nahradit vlastní verzí.\n\n"
                L"Ctrl+O otevřít · F5 znovu načíst · Ctrl+F hledat · Ctrl+Shift+F rozšířený filtr · pravé tlačítko na hlavičku = sloupce · klik na hlavičku = řazení\n"
                L"dvojklik / mezerník / +/- rozbalit · dvojklik na Předchůdce = skok · pravé tlačítko na úkol = vazby · Ctrl+G zobrazit v diagramu\n"
                L"Ctrl+kolečko lupa · Shift+kolečko posun · Ctrl+T dnešek · Ctrl+0 celý projekt · F4 detail · Ctrl+E CSV · Ctrl+Shift+S PDF · Ctrl+P tisk · Ctrl+C kopírovat");
            MSGBOXPARAMSW mb = { sizeof(mb), hwnd, g_inst, text.c_str(), APP_TITLE, MB_OK | MB_USERICON, MAKEINTRESOURCEW(1) }; // ikona 1 = logo programu
            MessageBoxIndirectW(&mb);
            return 0;
        }
        }
        break;
    }
    case WM_CLOSE: saveSettings(); DestroyWindow(hwnd); return 0;
    case WM_ENDSESSION: if (wp) saveSettings(); return 0; // odhlášení / vypnutí Windows bez WM_CLOSE
    case WM_DPICHANGED: {
        onDpiChanged(HIWORD(wp));
        const RECT* r = (const RECT*)lp; // navržená velikost pro nový monitor; při obnovení polohy ze startu platí uložená
        if (!g_placing) SetWindowPos(hwnd, nullptr, r->left, r->top, r->right - r->left, r->bottom - r->top, SWP_NOZORDER | SWP_NOACTIVATE);
        return 0;
    }
    case WM_DESTROY: PostQuitMessage(0); return 0;
    // po Alt+Tab nebo zavření dialogu vrátit fokus tam, kde byl (jinak zůstane na hlavním okně a klávesy v tabulce nefungují)
    case WM_ACTIVATE: if (LOWORD(wp) == WA_INACTIVE) { HWND f = GetFocus(); if (f && IsChild(hwnd, f)) g_lastFocus = f; } break;
    case WM_SETFOCUS:
        if (g_lastFocus && IsWindow(g_lastFocus) && IsWindowVisible(g_lastFocus) && IsWindowEnabled(g_lastFocus)) SetFocus(g_lastFocus);
        else if (g_loaded && g_tab == 0) SetFocus(g_list);
        return 0;
    }
    return DefWindowProcW(hwnd, msg, wp, lp);
}

int WINAPI wWinMain(_In_ HINSTANCE inst, _In_opt_ HINSTANCE, _In_ LPWSTR, _In_ int show) {
    g_inst = inst;
    typedef BOOL(WINAPI* PSetCtx)(HANDLE);
    if (HMODULE u = GetModuleHandleW(L"user32.dll")) if (PSetCtx f = (PSetCtx)GetProcAddress(u, "SetProcessDpiAwarenessContext")) f((HANDLE)-4);
    INITCOMMONCONTROLSEX icc = { sizeof(icc), ICC_LISTVIEW_CLASSES | ICC_BAR_CLASSES | ICC_STANDARD_CLASSES | ICC_TAB_CLASSES }; InitCommonControlsEx(&icc);
    Gdiplus::GdiplusStartupInput gsi; ULONG_PTR gdiToken = 0; Gdiplus::GdiplusStartup(&gdiToken, &gsi, nullptr);
    HDC sdc = GetDC(nullptr); g_dpi = GetDeviceCaps(sdc, LOGPIXELSX); ReleaseDC(nullptr, sdc);
    loadSettings(); createFonts(); applyThemeColors(); registerDlgClass(); registerPanelClasses(); registerDetailClasses();
    WNDCLASSW gc = {}; gc.lpfnWndProc = safeProc<GanttProc>; gc.hInstance = inst; gc.lpszClassName = L"MppGantt"; gc.hCursor = LoadCursor(nullptr, IDC_ARROW); gc.style = CS_DBLCLKS; RegisterClassW(&gc);
    WNDCLASSW tc = {}; tc.lpfnWndProc = safeProc<TipProc>; tc.hInstance = inst; tc.lpszClassName = L"MppTip"; tc.hCursor = LoadCursor(nullptr, IDC_ARROW); RegisterClassW(&tc);
    WNDCLASSW wc = {}; wc.lpfnWndProc = safeProc<MainProc>; wc.hInstance = inst; wc.lpszClassName = L"MppViewMain"; wc.hCursor = LoadCursor(nullptr, IDC_ARROW); wc.hbrBackground = nullptr; wc.hIcon = LoadIconW(inst, MAKEINTRESOURCEW(1)); RegisterClassW(&wc);

    HMENU menu = CreateMenu(), mHelp = CreatePopupMenu(), mTools = CreatePopupMenu();
    g_menuFile = CreatePopupMenu(); g_menuView = CreatePopupMenu(); g_menuViews = CreatePopupMenu(); g_menuColor = CreatePopupMenu(); g_menuBaseline = CreatePopupMenu(); g_menuCols = CreatePopupMenu();
    AppendMenuW(g_menuFile, MF_STRING, IDM_OPEN, L"&Otevřít...\tCtrl+O"); AppendMenuW(g_menuFile, MF_STRING, IDM_OPEN_NEW, L"Otevřít v &novém okně...\tCtrl+Shift+O"); AppendMenuW(g_menuFile, MF_STRING, IDM_RELOAD, L"&Znovu načíst\tF5");
    AppendMenuW(g_menuFile, MF_SEPARATOR, 0, nullptr);
    AppendMenuW(g_menuFile, MF_STRING, IDM_EXPORT_CSV, L"Export tabulky do &CSV...\tCtrl+E"); AppendMenuW(g_menuFile, MF_STRING, IDM_EXPORT_XLSX, L"Export do &Excelu (XLSX)...\tCtrl+Shift+E"); AppendMenuW(g_menuFile, MF_STRING, IDM_EXPORT_PNG, L"Export harmonogramu do &PNG...\tCtrl+Shift+P"); AppendMenuW(g_menuFile, MF_STRING, IDM_EXPORT_PDF, L"Uložit do P&DF...\tCtrl+Shift+S"); AppendMenuW(g_menuFile, MF_STRING, IDM_PRINT, L"&Tisk...\tCtrl+P");
    AppendMenuW(g_menuFile, MF_SEPARATOR, 0, nullptr); AppendMenuW(g_menuFile, MF_STRING, IDM_ASSOC, L"Otevírat soubory .mpp tímto programem"); AppendMenuW(g_menuFile, MF_STRING, IDM_UNASSOC, L"Zrušit přidružení .mpp");
    AppendMenuW(g_menuFile, MF_SEPARATOR, 0, nullptr); AppendMenuW(g_menuFile, MF_SEPARATOR, 0, nullptr); AppendMenuW(g_menuFile, MF_STRING, IDM_EXIT, L"&Konec");
    AppendMenuW(g_menuView, MF_STRING, IDM_FIND, L"&Hledat\tCtrl+F"); AppendMenuW(g_menuView, MF_STRING, IDM_ADV_FILTER, L"&Rozšířený filtr...\tCtrl+Shift+F"); AppendMenuW(g_menuView, MF_STRING, IDM_CLEAR_FILTER, L"Zrušit &filtr\tEsc");
    AppendMenuW(g_menuView, MF_SEPARATOR, 0, nullptr); AppendMenuW(g_menuView, MF_POPUP, (UINT_PTR)g_menuViews, L"&Pohledy"); AppendMenuW(g_menuView, MF_POPUP, (UINT_PTR)g_menuColor, L"O&barvit pruhy podle"); AppendMenuW(g_menuView, MF_POPUP, (UINT_PTR)g_menuBaseline, L"Zobrazený &směrný plán");
    AppendMenuW(g_menuView, MF_SEPARATOR, 0, nullptr);
    AppendMenuW(g_menuView, MF_STRING, IDM_SCALE_DAYS, L"Měřítko: &dny"); AppendMenuW(g_menuView, MF_STRING, IDM_SCALE_WEEKS, L"Měřítko: &týdny"); AppendMenuW(g_menuView, MF_STRING, IDM_SCALE_MONTHS, L"Měřítko: &měsíce");
    AppendMenuW(g_menuView, MF_STRING, IDM_ZOOM_IN, L"Přiblížit\tCtrl++"); AppendMenuW(g_menuView, MF_STRING, IDM_ZOOM_OUT, L"Oddálit\tCtrl+-"); AppendMenuW(g_menuView, MF_STRING, IDM_ZOOM_FIT, L"&Celý projekt\tCtrl+0"); AppendMenuW(g_menuView, MF_STRING, IDM_TODAY, L"Přejít na d&nešek\tCtrl+T"); AppendMenuW(g_menuView, MF_STRING, IDM_GOTO_SEL, L"Přejít na vybraný úkol\tCtrl+G");
    AppendMenuW(g_menuView, MF_SEPARATOR, 0, nullptr);
    HMENU mLevel = CreatePopupMenu(); for (int i = 1; i <= 6; i++) AppendMenuW(mLevel, MF_STRING, IDM_LEVEL1 + i - 1, (L"Úroveň " + std::to_wstring(i)).c_str());
    AppendMenuW(g_menuView, MF_POPUP, (UINT_PTR)mLevel, L"Rozbalit do ú&rovně"); AppendMenuW(g_menuView, MF_STRING, IDM_EXPAND_ALL, L"Rozbalit vše"); AppendMenuW(g_menuView, MF_STRING, IDM_COLLAPSE_ALL, L"Sbalit vše");
    AppendMenuW(g_menuView, MF_SEPARATOR, 0, nullptr);
    AppendMenuW(g_menuView, MF_STRING | (g_detailVisible ? MF_CHECKED : 0), IDM_DETAIL, L"Panel detailu úkolu\tF4"); AppendMenuW(g_menuView, MF_STRING | (g_progressLine ? MF_CHECKED : 0), IDM_PROGRESS, L"Průběhová čára"); AppendMenuW(g_menuView, MF_STRING | (g_holidays ? MF_CHECKED : 0), IDM_HOLIDAYS, L"Svátky a nepracovní dny podle kalendáře");
    AppendMenuW(g_menuView, MF_SEPARATOR, 0, nullptr); AppendMenuW(g_menuView, MF_STRING | (g_dark ? MF_CHECKED : 0), IDM_DARK, L"Tmavý režim"); AppendMenuW(g_menuView, MF_STRING, IDM_FONT_UP, L"Větší písmo\tCtrl+Shift++"); AppendMenuW(g_menuView, MF_STRING, IDM_FONT_DOWN, L"Menší písmo\tCtrl+Shift+-");
    AppendMenuW(mTools, MF_STRING, IDM_CHECK, L"&Kontrola kvality plánu..."); AppendMenuW(mTools, MF_STRING, IDM_COMPARE, L"&Porovnat se starší verzí...");
    AppendMenuW(mHelp, MF_STRING, IDM_LICENSE, L"&Licenční podmínky…"); AppendMenuW(mHelp, MF_STRING, IDM_LICENSE_DIR, L"Složka programu (licence, zdroje mppread)…");
    AppendMenuW(mHelp, MF_SEPARATOR, 0, nullptr); AppendMenuW(mHelp, MF_STRING, IDM_ABOUT, L"&O programu");
    AppendMenuW(menu, MF_POPUP, (UINT_PTR)g_menuFile, L"&Soubor"); AppendMenuW(menu, MF_POPUP, (UINT_PTR)g_menuView, L"&Zobrazit"); AppendMenuW(menu, MF_POPUP, (UINT_PTR)mTools, L"&Nástroje"); AppendMenuW(menu, MF_POPUP, (UINT_PTR)mHelp, L"Náp&ověda");
    rebuildRecentMenu(); rebuildViewsMenu(); rebuildColorMenu(); rebuildBaselineMenu();
    ACCEL acc[] = { { FCONTROL | FVIRTKEY, 'O', IDM_OPEN }, { FCONTROL | FSHIFT | FVIRTKEY, 'O', IDM_OPEN_NEW }, { FVIRTKEY, VK_F5, IDM_RELOAD }, { FCONTROL | FVIRTKEY, 'E', IDM_EXPORT_CSV }, { FCONTROL | FSHIFT | FVIRTKEY, 'E', IDM_EXPORT_XLSX }, { FCONTROL | FSHIFT | FVIRTKEY, 'P', IDM_EXPORT_PNG }, { FCONTROL | FSHIFT | FVIRTKEY, 'S', IDM_EXPORT_PDF }, { FCONTROL | FVIRTKEY, 'P', IDM_PRINT },
                    { FCONTROL | FVIRTKEY, 'F', IDM_FIND }, { FCONTROL | FSHIFT | FVIRTKEY, 'F', IDM_ADV_FILTER }, { FVIRTKEY, VK_F4, IDM_DETAIL }, { FCONTROL | FVIRTKEY, 'G', IDM_GOTO_SEL },
                    { FCONTROL | FVIRTKEY, VK_ADD, IDM_ZOOM_IN }, { FCONTROL | FVIRTKEY, VK_OEM_PLUS, IDM_ZOOM_IN }, { FCONTROL | FVIRTKEY, VK_SUBTRACT, IDM_ZOOM_OUT }, { FCONTROL | FVIRTKEY, VK_OEM_MINUS, IDM_ZOOM_OUT }, { FCONTROL | FVIRTKEY, '0', IDM_ZOOM_FIT }, { FCONTROL | FVIRTKEY, VK_NUMPAD0, IDM_ZOOM_FIT }, { FCONTROL | FVIRTKEY, 'T', IDM_TODAY },
                    { FCONTROL | FVIRTKEY, '1', IDM_TAB0 }, { FCONTROL | FVIRTKEY, '2', IDM_TAB0 + 1 }, { FCONTROL | FVIRTKEY, '3', IDM_TAB0 + 2 }, { FCONTROL | FVIRTKEY, '4', IDM_TAB0 + 3 }, { FCONTROL | FVIRTKEY, '5', IDM_TAB0 + 4 }, { FCONTROL | FVIRTKEY, '6', IDM_TAB0 + 5 },
                    { FCONTROL | FSHIFT | FVIRTKEY, VK_ADD, IDM_FONT_UP }, { FCONTROL | FSHIFT | FVIRTKEY, VK_OEM_PLUS, IDM_FONT_UP }, { FCONTROL | FSHIFT | FVIRTKEY, VK_SUBTRACT, IDM_FONT_DOWN }, { FCONTROL | FSHIFT | FVIRTKEY, VK_OEM_MINUS, IDM_FONT_DOWN } };
    HACCEL hacc = CreateAcceleratorTableW(acc, (int)(sizeof(acc) / sizeof(acc[0])));

    HWND hwnd = CreateWindowExW(0, L"MppViewMain", APP_TITLE, WS_OVERLAPPEDWINDOW | WS_CLIPCHILDREN, CW_USEDEFAULT, CW_USEDEFAULT, S(1280), S(760), nullptr, menu, inst, nullptr);
    applyTheme();

    // Argumenty: soubor [--csv x] [--xlsx x] [--png x] [--pdf x] -> bez okna, jen export (okno se proto ukazuje až potom)
    int argc = 0; LPWSTR* argv = CommandLineToArgvW(GetCommandLineW(), &argc);
    std::wstring arg, csvOut, pngOut, pdfOut, xlsxOut;
    for (int i = 1; argv && i < argc; i++) {
        if (wcscmp(argv[i], L"--csv") == 0 && i + 1 < argc) csvOut = argv[++i]; else if (wcscmp(argv[i], L"--png") == 0 && i + 1 < argc) pngOut = argv[++i];
        else if (wcscmp(argv[i], L"--pdf") == 0 && i + 1 < argc) pdfOut = argv[++i]; else if (wcscmp(argv[i], L"--xlsx") == 0 && i + 1 < argc) xlsxOut = argv[++i]; else arg = argv[i];
    }
    if (argv) LocalFree(argv);
    g_batch = !csvOut.empty() || !pngOut.empty() || !pdfOut.empty() || !xlsxOut.empty();

    if (!g_batch) {
        std::wstring w = iniGet(L"Window"); int v[5] = { 0, 0, 0, 0, 1 };
        if (swscanf(w.c_str(), L"%d,%d,%d,%d,%d", &v[0], &v[1], &v[2], &v[3], &v[4]) == 5 && v[2] - v[0] > 300 && v[3] - v[1] > 200) {
            WINDOWPLACEMENT wp = { sizeof(wp) }; wp.rcNormalPosition = { v[0], v[1], v[2], v[3] }; wp.showCmd = v[4] ? SW_SHOWMAXIMIZED : SW_SHOWNORMAL;
            g_placing = true; SetWindowPlacement(hwnd, &wp); g_placing = false;
        }
        else ShowWindow(hwnd, show == SW_SHOWMINIMIZED ? SW_SHOW : SW_SHOWMAXIMIZED);
        // okno může ležet na monitoru s jiným měřítkem, než mělo Windows při přihlášení (z toho je g_dpi)
        typedef UINT(WINAPI* PGetDpi)(HWND);
        if (PGetDpi f = (PGetDpi)GetProcAddress(GetModuleHandleW(L"user32.dll"), "GetDpiForWindow")) onDpiChanged((int)f(hwnd));
    }
    setTab(0); syncScaleCombo();
    if (!arg.empty()) loadFile(arg);
    if (!csvOut.empty() || !pngOut.empty() || !pdfOut.empty() || !xlsxOut.empty()) {
        int rc = g_loaded ? 0 : 2;
        if (g_loaded && !csvOut.empty() && !exportCsv(csvOut.c_str())) rc = 3;
        if (g_loaded && !pngOut.empty() && !exportPng(pngOut.c_str())) rc = 4;
        if (g_loaded && !pdfOut.empty() && !exportPdf(pdfOut.c_str())) rc = 5;
        if (g_loaded && !xlsxOut.empty() && !exportXlsx(xlsxOut.c_str())) rc = 6;
        DestroyWindow(hwnd); Gdiplus::GdiplusShutdown(gdiToken); return rc;
    }
    MSG m;
    while (GetMessageW(&m, nullptr, 0, 0) > 0) {
        if (TranslateAcceleratorW(hwnd, hacc, &m)) continue;
        if (m.message == WM_KEYDOWN && m.wParam == VK_ESCAPE && GetFocus() == g_list && filterActive()) { clearFilters(); continue; }
        TranslateMessage(&m); DispatchMessageW(&m);
    }
    Gdiplus::GdiplusShutdown(gdiToken);
    return 0;
}
