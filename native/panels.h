// panels.h - záložky: Časová osa, Zdroje, Vytížení, Kalendář, Projekt (každá = child okno hlavního okna)
#pragma once
#include "tools.h"

static void updateDetail(); // mppview.cpp

// ---------------- Časová osa ----------------
struct TlItem { int idx; Time start, finish; bool milestone; int lane, color; };
static HWND g_tlPanel, g_tlLevel, g_tlMs, g_tlCrit, g_tlPng, g_tlSummary;
static std::vector<TlItem> g_tl; static int64_t g_tlOrigin = 0, g_tlEnd = 0; static int g_tlLanes = 1, g_tlMsRows = 0;
static const COLORREF TL_PALETTE[8] = { RGB(59,130,246), RGB(16,185,129), RGB(249,115,22), RGB(139,92,246), RGB(236,72,153), RGB(20,184,166), RGB(234,179,8), RGB(100,116,139) };
static std::wstring msLabel(const mpp::Task& t) { mpp::Date dd = mpp::toDate(t.start); std::wstring s = std::to_wstring(dd.d) + L"." + std::to_wstring(dd.m) + L". " + t.name; if (s.size() > 40) s = s.substr(0, 38) + L"…"; return s; }
static int msLabelX(int cx, int w, int width) { return imax(2, imin(width - w - 2, cx - w / 2)); }
// popisky milníků do řádků tak, aby se nepřekrývaly (závisí na šířce plochy -> volat i před exportem)
static void layoutMsLabels(HDC hdc, int width) {
    HGDIOBJ of = SelectObject(hdc, g_fontSmall);
    double ppd = (width - S(24)) / (double)imax(1, (int)(g_tlEnd - g_tlOrigin));
    std::vector<int> rowEnd;
    for (TlItem& it : g_tl) {
        if (!it.milestone) continue;
        std::wstring label = msLabel(g_prj.tasks[it.idx]); SIZE sz; GetTextExtentPoint32W(hdc, label.c_str(), (int)label.size(), &sz);
        int cx = S(12) + (int)std::lround((it.start - mpp::fromUnixDays(g_tlOrigin)) / 86400.0 * ppd), lx = msLabelX(cx, sz.cx, width);
        int r = 0; while (r < (int)rowEnd.size() && rowEnd[r] + S(8) > lx) r++;
        if (r == (int)rowEnd.size()) { if (r < 40) rowEnd.push_back(0); else r = 39; } // ponytail: nad 40 řádků se popisky překrývají
        rowEnd[r] = lx + sz.cx; it.lane = r;
    }
    g_tlMsRows = (int)rowEnd.size();
    SelectObject(hdc, of);
}
static void rebuildTimeline() {
    g_tl.clear(); g_tlLanes = 1; g_tlMsRows = 0;
    if (!g_loaded) { SetWindowTextW(g_tlSummary, L""); InvalidateRect(g_tlPanel, nullptr, TRUE); return; }
    int lvl = (int)SendMessage(g_tlLevel, CB_GETCURSEL, 0, 0); int maxLevel = lvl == 3 ? 1000 : lvl + 1;
    bool ms = SendMessage(g_tlMs, BM_GETCHECK, 0, 0) == BST_CHECKED, onlyCrit = SendMessage(g_tlCrit, BM_GETCHECK, 0, 0) == BST_CHECKED;
    int64_t mn = 0, mx = 0; std::vector<TlItem> blocks, miles; int color = 0;
    for (size_t i = 0; i < g_prj.tasks.size(); i++) {
        const mpp::Task& t = g_prj.tasks[i];
        if (t.isNull || !t.start || !t.finish || !t.active) continue;
        if (onlyCrit && !t.critical) continue;
        int depth = imax(0, t.level - 1);
        bool isMs = t.milestone && !t.summary, isBlock = t.summary && depth < maxLevel && t.level > 0;
        if (!isMs && !isBlock) continue; if (isMs && !ms) continue;
        int64_t s = mpp::toUnixDays(t.start), f = mpp::toUnixDays(t.finish);
        if (!mn || s < mn) mn = s; if (!mx || f > mx) mx = f;
        if (isMs) miles.push_back({ (int)i, t.start, t.finish, true, 0, 0 });
        else blocks.push_back({ (int)i, t.start, t.finish, false, 0, depth == 0 ? color++ % 8 : (color + depth) % 8 });
    }
    if (!mn) { SetWindowTextW(g_tlSummary, L"Žádné souhrnné úkoly ani milníky k zobrazení."); InvalidateRect(g_tlPanel, nullptr, TRUE); return; }
    int64_t days = imax(1, (int)(mx - mn)); g_tlOrigin = mn - imax(2, (int)(days / 30)); g_tlEnd = mx + imax(2, (int)(days / 30)) + 1;
    std::vector<Time> laneEnd;
    for (TlItem& b : blocks) { int lane = -1; for (size_t i = 0; i < laneEnd.size(); i++) if (b.start >= laneEnd[i]) { lane = (int)i; break; } if (lane < 0) { lane = (int)laneEnd.size(); laneEnd.push_back(b.finish); } else laneEnd[lane] = b.finish; b.lane = lane; g_tl.push_back(b); }
    g_tlLanes = imax(1, (int)laneEnd.size());
    std::sort(miles.begin(), miles.end(), [](const TlItem& a, const TlItem& b) { return a.start < b.start; });
    RECT rc; GetClientRect(g_tlPanel, &rc); double ppd = (imax(200, (int)rc.right) - S(24)) / (double)imax(1, (int)(g_tlEnd - g_tlOrigin));
    (void)ppd; for (TlItem& m : miles) g_tl.push_back(m);
    g_tlMsRows = 0; if (!miles.empty()) { HDC dc = GetDC(g_tlPanel); layoutMsLabels(dc, imax(200, (int)rc.right)); ReleaseDC(g_tlPanel, dc); }
    SetWindowTextW(g_tlSummary, (std::to_wstring(blocks.size()) + L" bloků · " + std::to_wstring(miles.size()) + L" milníků · " + mpp::fmtDate(mpp::fromUnixDays(mn)) + L" – " + mpp::fmtDate(mpp::fromUnixDays(mx))).c_str());
    InvalidateRect(g_tlPanel, nullptr, TRUE);
}
static void paintTimeline(HDC hdc, int width, int height, int top) {
    const int AXIS = S(34), LANE = S(40), MS_ROW = S(22);
    RECT full = { 0, top, width, height }; HBRUSH bg = CreateSolidBrush(T.bg); FillRect(hdc, &full, bg); DeleteObject(bg);
    SetBkMode(hdc, TRANSPARENT); SelectObject(hdc, g_font);
    if (g_tl.empty()) { SetTextColor(hdc, T.muted); RECT r = { S(20), top + S(20), width, top + S(44) }; drawText(hdc, g_loaded ? L"Nic k zobrazení – zvolte jinou úroveň nebo zapněte milníky." : L"Otevřete soubor Microsoft Project.", r, DT_LEFT); return; }
    double ppd = (width - S(24)) / (double)imax(1, (int)(g_tlEnd - g_tlOrigin));
    auto xOf = [&](Time t) { return S(12) + (int)std::lround((t - mpp::fromUnixDays(g_tlOrigin)) / 86400.0 * ppd); };
    int y0 = top;
    SelectObject(hdc, g_fontSmall);
    int y; unsigned m, d; mpp::civilFromDays(g_tlOrigin, y, m, d); int64_t cur = mpp::daysFromCivil(y, m, 1); bool years = g_tlEnd - g_tlOrigin > 900;
    HPEN pStrong = CreatePen(PS_SOLID, 1, T.gridStrong); HBRUSH hb = CreateSolidBrush(T.headerBg);
    while (cur <= g_tlEnd) {
        int yy; unsigned mm, dd; mpp::civilFromDays(cur, yy, mm, dd);
        int64_t nxt = years ? mpp::daysFromCivil(yy + 1, 1, 1) : mpp::daysFromCivil(mm == 12 ? yy + 1 : yy, mm == 12 ? 1 : mm + 1, 1);
        int x0 = xOf(mpp::fromUnixDays(cur)), x1 = xOf(mpp::fromUnixDays(nxt));
        SelectObject(hdc, pStrong); MoveToEx(hdc, x0, y0, nullptr); LineTo(hdc, x0, height);
        RECT hr = { x0 + 1, y0, imax(x0 + 1, x1), y0 + AXIS - 6 }; FillRect(hdc, &hr, hb);
        std::wstring label = years ? std::to_wstring(yy) : (x1 - x0 > S(70) ? std::wstring(MONTHS[mm - 1]) + L" " + std::to_wstring(yy) : MONTHS_S[mm - 1]);
        SIZE sz; GetTextExtentPoint32W(hdc, label.c_str(), (int)label.size(), &sz);
        if (sz.cx + 6 < x1 - x0) { SetTextColor(hdc, T.ink); RECT lr = { x0 + 4, y0, x1, y0 + AXIS - 6 }; drawText(hdc, label, lr, DT_LEFT); }
        cur = nxt;
    }
    SelectObject(hdc, pStrong); MoveToEx(hdc, 0, y0 + AXIS - 6, nullptr); LineTo(hdc, width, y0 + AXIS - 6);
    int64_t td = mpp::toUnixDays(g_today);
    if (td >= g_tlOrigin && td <= g_tlEnd) { HPEN pt = CreatePen(PS_DOT, 1, T.today); SelectObject(hdc, pt); int tx = xOf(g_today); MoveToEx(hdc, tx, y0 + AXIS - 6, nullptr); LineTo(hdc, tx, height); SelectObject(hdc, pStrong); DeleteObject(pt); }
    SelectObject(hdc, g_font);
    for (const TlItem& it : g_tl) {
        if (it.milestone) continue;
        int a = xOf(it.start), b = imax(a + 4, xOf(it.finish)), yb = y0 + AXIS + it.lane * LANE + 6;
        const mpp::Task& t = g_prj.tasks[it.idx];
        COLORREF c = t.critical ? T.crit : TL_PALETTE[it.color % 8];
        HBRUSH br = CreateSolidBrush(c); HPEN pn = CreatePen(PS_SOLID, 1, c); SelectObject(hdc, br); SelectObject(hdc, pn);
        RoundRect(hdc, a, yb, b, yb + LANE - 12, S(8), S(8));
        int pct = imax(0, imin(100, t.pctComplete));
        if (pct > 0) { HBRUSH dk = CreateSolidBrush(RGB(GetRValue(c) * 3 / 4, GetGValue(c) * 3 / 4, GetBValue(c) * 3 / 4)); SelectObject(hdc, dk); RoundRect(hdc, a, yb, a + (int)std::lround((b - a) * pct / 100.0), yb + LANE - 12, S(8), S(8)); DeleteObject(dk); }
        SelectObject(hdc, GetStockObject(NULL_BRUSH)); DeleteObject(br); DeleteObject(pn);
        if (b - a > S(30)) { SetTextColor(hdc, RGB(255, 255, 255)); RECT tr = { a + S(6), yb, b - S(4), yb + LANE - 12 }; drawText(hdc, t.name, tr, DT_LEFT | DT_END_ELLIPSIS); }
    }
    int msTop = y0 + AXIS + g_tlLanes * LANE + 16;
    SelectObject(hdc, g_fontSmall);
    for (const TlItem& it : g_tl) {
        if (!it.milestone) continue;
        int cx = xOf(it.start), cy = msTop + 8; const mpp::Task& t = g_prj.tasks[it.idx];
        HBRUSH br = CreateSolidBrush(t.critical ? T.crit : T.mile); SelectObject(hdc, br); SelectObject(hdc, GetStockObject(NULL_PEN));
        POINT pts[4] = { { cx, cy - 7 }, { cx + 7, cy }, { cx, cy + 7 }, { cx - 7, cy } }; Polygon(hdc, pts, 4); DeleteObject(br);
        int ly = cy + 10 + it.lane * MS_ROW;
        SelectObject(hdc, pStrong); MoveToEx(hdc, cx, cy + 7, nullptr); LineTo(hdc, cx, ly);
        std::wstring label = msLabel(t);
        SIZE sz; GetTextExtentPoint32W(hdc, label.c_str(), (int)label.size(), &sz);
        int lx = msLabelX(cx, sz.cx, width);
        SetTextColor(hdc, T.ink); RECT lr = { lx, ly, lx + sz.cx + 2, ly + MS_ROW }; drawText(hdc, label, lr, DT_LEFT);
    }
    DeleteObject(pStrong); DeleteObject(hb);
}
static int tlItemAt(int x, int y, int width, int top) {
    const int AXIS = S(34), LANE = S(40);
    double ppd = (width - S(24)) / (double)imax(1, (int)(g_tlEnd - g_tlOrigin));
    auto xOf = [&](Time t) { return S(12) + (int)std::lround((t - mpp::fromUnixDays(g_tlOrigin)) / 86400.0 * ppd); };
    for (const TlItem& it : g_tl) {
        if (it.milestone) { int cx = xOf(it.start), cy = top + AXIS + g_tlLanes * LANE + 16 + 8; if (abs(x - cx) <= 8 && abs(y - cy) <= 8) return it.idx; }
        else { int a = xOf(it.start), b = xOf(it.finish), yb = top + AXIS + it.lane * LANE + 6; if (x >= a && x <= b && y >= yb && y <= yb + LANE - 12) return it.idx; }
    }
    return -1;
}
static LRESULT CALLBACK TimelineProc(HWND hwnd, UINT msg, WPARAM wp, LPARAM lp) {
    const int BAR = S(36);
    switch (msg) {
    case WM_CREATE: {
        g_tlPanel = hwnd;
        HWND l = dlgCtl(hwnd, L"STATIC", L"Souhrnné úkoly:", SS_LEFT, 8, 10, 100, 18, -1);
        g_tlLevel = dlgCombo(hwnd, 110, 7, 130, 201); for (const wchar_t* s : { L"Úroveň 1", L"Úrovně 1–2", L"Úrovně 1–3", L"Všechny souhrny" }) SendMessage(g_tlLevel, CB_ADDSTRING, 0, (LPARAM)s); SendMessage(g_tlLevel, CB_SETCURSEL, 1, 0);
        g_tlMs = dlgCheck(hwnd, L"Milníky", 250, 8, 80, 202); SendMessage(g_tlMs, BM_SETCHECK, BST_CHECKED, 0);
        g_tlCrit = dlgCheck(hwnd, L"Jen kritické", 335, 8, 110, 203);
        g_tlPng = dlgButton(hwnd, L"Uložit jako PNG…", 450, 6, 130, 204);
        g_tlSummary = dlgCtl(hwnd, L"STATIC", L"", SS_LEFT, 590, 10, 500, 18, -1);
        (void)l; return 0;
    }
    case WM_COMMAND: {
        int id = LOWORD(wp);
        if (id == 201 && HIWORD(wp) == CBN_SELCHANGE) rebuildTimeline(); else if (id == 202 || id == 203) rebuildTimeline();
        else if (id == 204 && g_loaded) {
            wchar_t buf[MAX_PATH]; wcscpy(buf, (baseName() + L"-osa.png").c_str());
            if (saveDialog(buf, L"Obrázek PNG\0*.png\0", L"png", L"Uložit časovou osu do PNG")) {
                int w = 2400; { HDC dc = GetDC(hwnd); layoutMsLabels(dc, w); ReleaseDC(hwnd, dc); }
                int h = S(34) + g_tlLanes * S(40) + 16 + g_tlMsRows * S(22) + 40;
                HBITMAP bmp = renderPageBitmap(w, h, 96, [&](HDC hdc, RECT) { paintTimeline(hdc, w, h, 0); });
                Gdiplus::Bitmap gb(bmp, nullptr); CLSID clsid; bool ok = pngEncoderClsid(&clsid) == 0 && gb.Save(buf, &clsid, nullptr) == Gdiplus::Ok; DeleteObject(bmp);
                rebuildTimeline(); // zpět rozložení pro obrazovku
                if (ok) ShellExecuteW(nullptr, L"open", buf, nullptr, nullptr, SW_SHOWNORMAL); else msgError(L"Obrázek se nepodařilo uložit.");
            }
        }
        return 0;
    }
    case WM_CTLCOLORSTATIC: { SetTextColor((HDC)wp, T.muted); SetBkColor((HDC)wp, T.panel); static HBRUSH b; if (b) DeleteObject(b); b = CreateSolidBrush(T.panel); return (LRESULT)b; }
    case WM_SIZE: rebuildTimeline(); return 0;
    case WM_ERASEBKGND: { RECT rc; GetClientRect(hwnd, &rc); rc.bottom = BAR; HBRUSH b = CreateSolidBrush(T.panel); FillRect((HDC)wp, &rc, b); DeleteObject(b); return 1; }
    case WM_PAINT: {
        PAINTSTRUCT ps; HDC hdc = BeginPaint(hwnd, &ps); RECT rc; GetClientRect(hwnd, &rc);
        HDC mem = CreateCompatibleDC(hdc); HBITMAP bmp = CreateCompatibleBitmap(hdc, rc.right, rc.bottom); HGDIOBJ old = SelectObject(mem, bmp);
        RECT bar = { 0, 0, rc.right, BAR }; HBRUSH b = CreateSolidBrush(T.panel); FillRect(mem, &bar, b); DeleteObject(b);
        paintTimeline(mem, rc.right, rc.bottom, BAR);
        BitBlt(hdc, 0, BAR, rc.right, rc.bottom - BAR, mem, 0, BAR, SRCCOPY);
        SelectObject(mem, old); DeleteObject(bmp); DeleteDC(mem); EndPaint(hwnd, &ps); return 0;
    }
    case WM_LBUTTONDBLCLK: { RECT rc; GetClientRect(hwnd, &rc); int i = tlItemAt(GET_X_LPARAM(lp), GET_Y_LPARAM(lp), rc.right, BAR); if (i >= 0) selectTaskByUid(g_prj.tasks[i].uid); return 0; }
    }
    return DefWindowProcW(hwnd, msg, wp, lp);
}

// ---------------- Zdroje ----------------
static HWND g_rsPanel, g_rsList, g_rsAsg, g_rsSummary, g_rsTitle;
static void fillResourcesPanel() {
    ListView_DeleteAllItems(g_rsList); ListView_DeleteAllItems(g_rsAsg);
    if (!g_loaded) { SetWindowTextW(g_rsSummary, L""); return; }
    const mpp::Project& p = g_prj;
    for (size_t i = 0; i < p.resources.size(); i++) { const mpp::Resource& r = p.resources[i]; lvAdd(g_rsList, { std::to_wstring(r.id), r.name, mpp::resourceTypeName(r.type), r.initials, r.group, mpp::fmtNumber(r.maxUnits * 100) + L" %", r.stdRate ? mpp::fmtMoney(r.stdRate, p) + L"/h" : L"", r.ovtRate ? mpp::fmtMoney(r.ovtRate, p) + L"/h" : L"", mpp::fmtMoney(r.costPerUse, p), mpp::fmtHours(r.workHours), mpp::fmtMoney(r.cost, p), std::to_wstring(r.assignments.size()), r.email }, (LPARAM)i); }
    SetWindowTextW(g_rsSummary, p.resources.empty() ? L"Projekt neobsahuje žádné zdroje." : (std::to_wstring(p.resources.size()) + L" zdrojů   •   Dvojklik na přiřazení přejde na úkol v Ganttově diagramu.").c_str());
    if (!p.resources.empty()) ListView_SetItemState(g_rsList, 0, LVIS_SELECTED | LVIS_FOCUSED, LVIS_SELECTED | LVIS_FOCUSED);
}
static void fillResourceAssignments() {
    ListView_DeleteAllItems(g_rsAsg);
    int i = ListView_GetNextItem(g_rsList, -1, LVNI_SELECTED); if (i < 0) { SetWindowTextW(g_rsTitle, L"Přiřazení vybraného zdroje"); return; }
    const mpp::Resource& r = g_prj.resources[(size_t)lvParam(g_rsList, i)];
    SetWindowTextW(g_rsTitle, (L"Přiřazení zdroje „" + r.name + L"“").c_str());
    for (int ai : r.assignments) { const mpp::Assignment& a = g_prj.assignments[ai]; const mpp::Task* t = g_prj.byUid(a.taskUid); if (!t) continue; lvAdd(g_rsAsg, { std::to_wstring(t->id), t->name, mpp::fmtNumber(a.units * 100) + L" %", mpp::fmtHours(a.workHours), mpp::fmtDate(a.start ? a.start : t->start), mpp::fmtDate(a.finish ? a.finish : t->finish), a.workHours > 0 ? mpp::fmtNumber(a.actualWorkHours / a.workHours * 100) + L" %" : L"", mpp::fmtMoney(a.cost, g_prj) }, t->uid); }
}
static LRESULT CALLBACK ResourcesProc(HWND hwnd, UINT msg, WPARAM wp, LPARAM lp) {
    switch (msg) {
    case WM_CREATE: {
        g_rsPanel = hwnd;
        g_rsSummary = dlgCtl(hwnd, L"STATIC", L"", SS_LEFT, 8, 8, 900, 18, -1);
        g_rsList = dlgList(hwnd, 0, 30, 100, 100, 301, LVS_SINGLESEL);
        const wchar_t* c[] = { L"ID", L"Název zdroje", L"Typ", L"Iniciály", L"Skupina", L"Max. jednotky", L"Standardní sazba", L"Přesčasová sazba", L"Náklady na použití", L"Práce", L"Náklady", L"Přiřazení", L"E-mail" }; int w[] = { 40, 200, 90, 60, 110, 90, 110, 110, 110, 80, 100, 70, 180 };
        for (int i = 0; i < 13; i++) lvColumn(g_rsList, i, c[i], w[i], i == 0 || i == 5 || i == 9 || i == 10 || i == 11);
        g_rsTitle = dlgCtl(hwnd, L"STATIC", L"Přiřazení vybraného zdroje", SS_LEFT, 8, 0, 600, 18, -1); SendMessage(g_rsTitle, WM_SETFONT, (WPARAM)g_fontBold, TRUE);
        g_rsAsg = dlgList(hwnd, 0, 0, 100, 100, 302, LVS_SINGLESEL);
        const wchar_t* c2[] = { L"ID úkolu", L"Úkol", L"Jednotky", L"Práce", L"Zahájení", L"Dokončení", L"% práce", L"Náklady" }; int w2[] = { 60, 320, 70, 80, 90, 90, 70, 100 };
        for (int i = 0; i < 8; i++) lvColumn(g_rsAsg, i, c2[i], w2[i], i == 0 || i == 2 || i == 3 || i == 6 || i == 7);
        return 0;
    }
    case WM_SIZE: { int w = LOWORD(lp), h = HIWORD(lp); int h1 = (h - S(60)) * 6 / 10; MoveWindow(g_rsSummary, S(8), S(8), w - S(16), S(18), TRUE); MoveWindow(g_rsList, 0, S(30), w, h1, TRUE); MoveWindow(g_rsTitle, S(8), S(30) + h1 + S(6), w - S(16), S(18), TRUE); MoveWindow(g_rsAsg, 0, S(30) + h1 + S(28), w, h - S(30) - h1 - S(28), TRUE); return 0; }
    case WM_NOTIFY: { NMHDR* nm = (NMHDR*)lp; if (nm->hwndFrom == g_rsList && nm->code == LVN_ITEMCHANGED) { NMLISTVIEW* lv = (NMLISTVIEW*)lp; if ((lv->uNewState & LVIS_SELECTED) && !(lv->uOldState & LVIS_SELECTED)) fillResourceAssignments(); } if (nm->hwndFrom == g_rsAsg && nm->code == NM_DBLCLK) { int i = ListView_GetNextItem(g_rsAsg, -1, LVNI_SELECTED); if (i >= 0) selectTaskByUid((int)lvParam(g_rsAsg, i)); } return 0; }
    case WM_CTLCOLORSTATIC: { SetTextColor((HDC)wp, T.ink); SetBkColor((HDC)wp, T.panel); static HBRUSH b; if (b) DeleteObject(b); b = CreateSolidBrush(T.panel); return (LRESULT)b; }
    case WM_ERASEBKGND: { RECT rc; GetClientRect(hwnd, &rc); HBRUSH b = CreateSolidBrush(T.panel); FillRect((HDC)wp, &rc, b); DeleteObject(b); return 1; }
    }
    return DefWindowProcW(hwnd, msg, wp, lp);
}

// ---------------- Vytížení ----------------
static HWND g_usPanel, g_usPeriod, g_usMode, g_usOver, g_usSummary, g_usList, g_usTitle, g_usTasks;
static Usage g_usage; static std::vector<int> g_usRows; static std::vector<std::vector<double>> g_usTaskWork; static std::vector<int> g_usTaskUids;
static void fillUsage() {
    ListView_DeleteAllItems(g_usList); ListView_DeleteAllItems(g_usTasks);
    while (ListView_DeleteColumn(g_usList, 0)) {} while (ListView_DeleteColumn(g_usTasks, 0)) {}
    if (!g_loaded) { SetWindowTextW(g_usSummary, L""); return; }
    int per = (int)SendMessage(g_usPeriod, CB_GETCURSEL, 0, 0); if (per < 0) per = 1;
    g_usage = computeUsage((UsagePeriod)per);
    SendMessage(g_usPeriod, CB_SETCURSEL, (int)g_usage.period, 0);
    bool pct = SendMessage(g_usMode, CB_GETCURSEL, 0, 0) == 1, onlyOver = SendMessage(g_usOver, BM_GETCHECK, 0, 0) == BST_CHECKED;
    lvColumn(g_usList, 0, L"Zdroj", 200); lvColumn(g_usList, 1, L"Celkem h", 70, true); lvColumn(g_usList, 2, L"Detail", 120);
    lvColumn(g_usTasks, 0, L"Úkol", 300); lvColumn(g_usTasks, 1, L"Celkem h", 70, true); lvColumn(g_usTasks, 2, L"Detail", 120);
    for (size_t i = 0; i < g_usage.headers.size(); i++) { lvColumn(g_usList, (int)i + 3, g_usage.headers[i].c_str(), 58, true); lvColumn(g_usTasks, (int)i + 3, g_usage.headers[i].c_str(), 58, true); }
    g_usRows.clear(); int overRes = 0;
    for (size_t i = 0; i < g_usage.rscIdx.size(); i++) {
        bool over = g_usage.rowOver((int)i); if (over) overRes++;
        if (onlyOver && !over) continue;
        const mpp::Resource& r = g_prj.resources[g_usage.rscIdx[i]];
        std::vector<std::wstring> cells = { r.name, L"", std::wstring(mpp::resourceTypeName(r.type)) + L", " + mpp::fmtNumber(r.maxUnits * 100) + L" %" };
        double sum = 0; for (size_t c = 0; c < g_usage.work[i].size(); c++) { double v = g_usage.work[i][c]; sum += v; if (v < 0.005) cells.push_back(L""); else if (pct && !g_usage.cap[i].empty() && g_usage.cap[i][c] >= 0.005) cells.push_back(std::to_wstring((int)std::lround(v / g_usage.cap[i][c] * 100)) + L" %"); else cells.push_back(mpp::fmtNumber(v)); }
        cells[1] = mpp::fmtNumber(sum);
        lvAdd(g_usList, cells, (LPARAM)i); g_usRows.push_back((int)i);
    }
    SetWindowTextW(g_usSummary, (std::to_wstring(g_usage.rscIdx.size()) + L" zdrojů · " + std::to_wstring(g_usage.headers.size()) + L" období · " + (overRes == 0 ? L"bez přetížení" : std::to_wstring(overRes) + L" přetížených zdrojů, " + std::to_wstring(g_usage.overCells) + L" období") + L"   (práce rozložená rovnoměrně na pracovní dny přiřazení)").c_str());
    if (ListView_GetItemCount(g_usList)) ListView_SetItemState(g_usList, 0, LVIS_SELECTED | LVIS_FOCUSED, LVIS_SELECTED | LVIS_FOCUSED);
}
static void fillUsageTasks() {
    ListView_DeleteAllItems(g_usTasks); g_usTaskWork.clear(); g_usTaskUids.clear();
    int i = ListView_GetNextItem(g_usList, -1, LVNI_SELECTED); if (i < 0 || g_usage.bucketStart.empty()) { SetWindowTextW(g_usTitle, L"Práce vybraného zdroje po úkolech"); return; }
    int ui = (int)lvParam(g_usList, i); const mpp::Resource& r = g_prj.resources[g_usage.rscIdx[ui]];
    SetWindowTextW(g_usTitle, (L"Práce zdroje „" + r.name + L"“ po úkolech (dvojklik přejde na úkol)").c_str());
    for (int ai : r.assignments) {
        const mpp::Assignment& a = g_prj.assignments[ai]; const mpp::Task* t = g_prj.byUid(a.taskUid); if (!t) continue;
        std::vector<double> w = spreadWork(a, g_usage.bucketStart); double sum = 0; for (double v : w) sum += v;
        std::vector<std::wstring> cells = { std::to_wstring(t->id) + L" · " + t->name, mpp::fmtNumber(sum), mpp::fmtNumber(a.units * 100) + L" %" + (a.start ? L", " + mpp::fmtDate(a.start) + L" – " + mpp::fmtDate(a.finish) : L"") };
        for (double v : w) cells.push_back(v < 0.005 ? L"" : mpp::fmtNumber(v));
        lvAdd(g_usTasks, cells, t->uid);
    }
}
static LRESULT CALLBACK UsageProc(HWND hwnd, UINT msg, WPARAM wp, LPARAM lp) {
    switch (msg) {
    case WM_CREATE: {
        g_usPanel = hwnd;
        dlgCtl(hwnd, L"STATIC", L"Období:", SS_LEFT, 8, 10, 50, 18, -1);
        g_usPeriod = dlgCombo(hwnd, 60, 7, 90, 401); for (const wchar_t* s : { L"Dny", L"Týdny", L"Měsíce" }) SendMessage(g_usPeriod, CB_ADDSTRING, 0, (LPARAM)s); SendMessage(g_usPeriod, CB_SETCURSEL, 1, 0);
        dlgCtl(hwnd, L"STATIC", L"Zobrazit:", SS_LEFT, 160, 10, 60, 18, -1);
        g_usMode = dlgCombo(hwnd, 222, 7, 120, 402); for (const wchar_t* s : { L"Hodiny práce", L"% vytížení" }) SendMessage(g_usMode, CB_ADDSTRING, 0, (LPARAM)s); SendMessage(g_usMode, CB_SETCURSEL, 0, 0);
        g_usOver = dlgCheck(hwnd, L"Jen přetížené zdroje", 352, 8, 160, 403);
        g_usSummary = dlgCtl(hwnd, L"STATIC", L"", SS_LEFT, 520, 10, 800, 18, -1);
        g_usList = dlgList(hwnd, 0, 34, 100, 100, 404, LVS_SINGLESEL);
        g_usTitle = dlgCtl(hwnd, L"STATIC", L"Práce vybraného zdroje po úkolech", SS_LEFT, 8, 0, 800, 18, -1); SendMessage(g_usTitle, WM_SETFONT, (WPARAM)g_fontBold, TRUE);
        g_usTasks = dlgList(hwnd, 0, 0, 100, 100, 405, LVS_SINGLESEL);
        return 0;
    }
    case WM_COMMAND: { int id = LOWORD(wp); if ((id == 401 || id == 402) && HIWORD(wp) == CBN_SELCHANGE) fillUsage(); else if (id == 403) fillUsage(); return 0; }
    case WM_SIZE: { int w = LOWORD(lp), h = HIWORD(lp); int h1 = (h - S(64)) * 6 / 10; MoveWindow(g_usList, 0, S(34), w, h1, TRUE); MoveWindow(g_usTitle, S(8), S(34) + h1 + S(6), w - S(16), S(18), TRUE); MoveWindow(g_usTasks, 0, S(34) + h1 + S(28), w, h - S(34) - h1 - S(28), TRUE); MoveWindow(g_usSummary, S(520), S(10), imax(100, w - S(530)), S(18), TRUE); return 0; }
    case WM_NOTIFY: {
        NMHDR* nm = (NMHDR*)lp;
        if (nm->hwndFrom == g_usList && nm->code == LVN_ITEMCHANGED) { NMLISTVIEW* lv = (NMLISTVIEW*)lp; if ((lv->uNewState & LVIS_SELECTED) && !(lv->uOldState & LVIS_SELECTED)) fillUsageTasks(); }
        if (nm->hwndFrom == g_usTasks && nm->code == NM_DBLCLK) { int i = ListView_GetNextItem(g_usTasks, -1, LVNI_SELECTED); if (i >= 0) selectTaskByUid((int)lvParam(g_usTasks, i)); }
        if (nm->hwndFrom == g_usList && nm->code == NM_CUSTOMDRAW) {
            NMLVCUSTOMDRAW* cd = (NMLVCUSTOMDRAW*)lp;
            if (cd->nmcd.dwDrawStage == CDDS_PREPAINT) return CDRF_NOTIFYITEMDRAW;
            if (cd->nmcd.dwDrawStage == CDDS_ITEMPREPAINT) return CDRF_NOTIFYSUBITEMDRAW;
            if (cd->nmcd.dwDrawStage == (CDDS_ITEMPREPAINT | CDDS_SUBITEM)) {
                int ui = (int)cd->nmcd.lItemlParam; int c = cd->iSubItem - 3;
                cd->clrText = T.ink; cd->clrTextBk = T.bg;
                if (c >= 0 && ui >= 0 && ui < (int)g_usage.work.size() && c < (int)g_usage.work[ui].size()) { if (g_usage.isOver(ui, c)) { cd->clrText = T.overInk; cd->clrTextBk = T.over; } else if (g_usage.work[ui][c] >= 0.005) cd->clrTextBk = T.workLight; }
                return CDRF_NEWFONT;
            }
        }
        return 0;
    }
    case WM_CTLCOLORSTATIC: { SetTextColor((HDC)wp, T.ink); SetBkColor((HDC)wp, T.panel); static HBRUSH b; if (b) DeleteObject(b); b = CreateSolidBrush(T.panel); return (LRESULT)b; }
    case WM_ERASEBKGND: { RECT rc; GetClientRect(hwnd, &rc); HBRUSH b = CreateSolidBrush(T.panel); FillRect((HDC)wp, &rc, b); DeleteObject(b); return 1; }
    }
    return DefWindowProcW(hwnd, msg, wp, lp);
}

// ---------------- Kalendář ----------------
static HWND g_caPanel, g_caCombo, g_caWeek, g_caExc, g_caMonth, g_caPrev, g_caNext, g_caToday, g_caTitle;
static int g_caYear = 2026, g_caMonthN = 1;
static const mpp::Calendar* currentCalendar() { if (!g_loaded) return nullptr; int i = (int)SendMessage(g_caCombo, CB_GETCURSEL, 0, 0); if (i < 0 || i >= (int)g_prj.calendars.size()) return &g_prj.calendar(); return &g_prj.calendars[i]; }
static void fillCalendarPanel() {
    SendMessage(g_caCombo, CB_RESETCONTENT, 0, 0); ListView_DeleteAllItems(g_caWeek); ListView_DeleteAllItems(g_caExc);
    if (!g_loaded) return;
    for (size_t i = 0; i < g_prj.calendars.size(); i++) SendMessage(g_caCombo, CB_ADDSTRING, 0, (LPARAM)(g_prj.calendars[i].name + ((int)i == g_prj.defaultCalendar ? L"   (výchozí)" : L"")).c_str());
    if (g_prj.calendars.empty()) SendMessage(g_caCombo, CB_ADDSTRING, 0, (LPARAM)L"(vestavěný Po–Pá 8 h)");
    SendMessage(g_caCombo, CB_SETCURSEL, imax(0, g_prj.defaultCalendar), 0);
    mpp::Date d = mpp::toDate(g_today); g_caYear = d.y; g_caMonthN = (int)d.m;
    if (g_prj.startDate && (g_today < g_prj.startDate || g_today > g_prj.finishDate + 30 * 86400)) { mpp::Date s = mpp::toDate(g_prj.startDate); g_caYear = s.y; g_caMonthN = (int)s.m; }
    const mpp::Calendar* c = currentCalendar(); if (!c) return;
    static const wchar_t* DAYS[7] = { L"Neděle", L"Pondělí", L"Úterý", L"Středa", L"Čtvrtek", L"Pátek", L"Sobota" };
    double weekH = 0;
    for (int i = 1; i <= 7; i++) { int k = i % 7; lvAdd(g_caWeek, { DAYS[k], c->working[k] ? L"pracovní" : L"nepracovní", c->working[k] ? c->hoursText[k] : L"—", c->working[k] ? mpp::fmtNumber(c->minutes[k] / 60.0) : L"" }); weekH += c->working[k] ? c->minutes[k] / 60.0 : 0; }
    lvAdd(g_caWeek, { L"Týden celkem", L"", L"", mpp::fmtNumber(weekH) });
    static const wchar_t* REC[] = { L"", L"denně", L"ročně", L"ročně (pozice)", L"měsíčně", L"měsíčně (pozice)", L"týdně", L"denně" };
    for (auto& e : c->exceptions) lvAdd(g_caExc, { e.name.empty() ? L"(bez názvu)" : e.name, mpp::fmtDate(mpp::fromUnixDays(e.from)), mpp::fmtDate(mpp::fromUnixDays(e.to)), e.working ? L"ano" : L"ne", e.rec ? REC[e.rec] : L"", e.working && e.minutes ? mpp::fmtNumber(e.minutes / 60.0) + L" h" : L"" });
    InvalidateRect(g_caMonth, nullptr, TRUE);
}
static void paintMonth(HDC hdc, int w, int h) {
    RECT full = { 0, 0, w, h }; HBRUSH bg = CreateSolidBrush(T.bg); FillRect(hdc, &full, bg); DeleteObject(bg);
    SetBkMode(hdc, TRANSPARENT); SelectObject(hdc, g_font);
    const mpp::Calendar* c = currentCalendar(); if (!c) return;
    int cellW = (w - 2) / 7, headH = S(24), cellH = (h - headH - 2) / 6;
    SelectObject(hdc, g_fontBold); SetTextColor(hdc, T.ink);
    for (int i = 0; i < 7; i++) { RECT r = { 1 + i * cellW, 0, 1 + (i + 1) * cellW, headH }; drawText(hdc, DOW_S[i], r); }
    SelectObject(hdc, g_font);
    int64_t first = mpp::daysFromCivil(g_caYear, (unsigned)g_caMonthN, 1); int startDow = (int)mpp::dowOfUnixDay(first);
    int64_t td = mpp::toUnixDays(g_today);
    HPEN pg = CreatePen(PS_SOLID, 1, T.grid); SelectObject(hdc, pg);
    HBRUSH bNon = CreateSolidBrush(T.weekend), bHol = CreateSolidBrush(T.holiday), bWorkEx = CreateSolidBrush(T.workLight);
    for (int cell = 0; cell < 42; cell++) {
        int64_t day = first - startDow + cell; int y; unsigned m, d; mpp::civilFromDays(day, y, m, d);
        RECT r = { 1 + (cell % 7) * cellW, headH + (cell / 7) * cellH, 1 + (cell % 7 + 1) * cellW, headH + (cell / 7 + 1) * cellH };
        bool inMonth = (int)m == g_caMonthN; const mpp::CalException* e = c->exceptionAt(day); bool work = c->isWorking(day);
        if (inMonth) { if (e) FillRect(hdc, &r, e->working ? bWorkEx : bHol); else if (!work) FillRect(hdc, &r, bNon); }
        Rectangle(hdc, r.left, r.top, r.right + 1, r.bottom + 1);
        if (day == td) { HPEN pt = CreatePen(PS_SOLID, 2, T.linkSel); SelectObject(hdc, pt); SelectObject(hdc, GetStockObject(NULL_BRUSH)); Rectangle(hdc, r.left + 1, r.top + 1, r.right, r.bottom); SelectObject(hdc, pg); DeleteObject(pt); }
        SetTextColor(hdc, inMonth ? (work ? T.ink : T.muted) : T.grid);
        RECT tr = { r.left + 4, r.top + 2, r.right - 4, r.top + S(18) }; drawText(hdc, std::to_wstring(d), tr, DT_LEFT);
        if (inMonth && e && !e->name.empty()) { SelectObject(hdc, g_fontSmall); RECT nr = { r.left + 4, r.top + S(18), r.right - 4, r.bottom - 2 }; DrawTextW(hdc, e->name.c_str(), (int)e->name.size(), &nr, DT_LEFT | DT_WORDBREAK | DT_NOPREFIX | DT_END_ELLIPSIS); SelectObject(hdc, g_font); }
    }
    DeleteObject(pg); DeleteObject(bNon); DeleteObject(bHol); DeleteObject(bWorkEx);
}
static LRESULT CALLBACK MonthProc(HWND hwnd, UINT msg, WPARAM wp, LPARAM lp) {
    if (msg == WM_PAINT) { PAINTSTRUCT ps; HDC hdc = BeginPaint(hwnd, &ps); RECT rc; GetClientRect(hwnd, &rc); HDC mem = CreateCompatibleDC(hdc); HBITMAP bmp = CreateCompatibleBitmap(hdc, rc.right, rc.bottom); HGDIOBJ old = SelectObject(mem, bmp); paintMonth(mem, rc.right, rc.bottom); BitBlt(hdc, 0, 0, rc.right, rc.bottom, mem, 0, 0, SRCCOPY); SelectObject(mem, old); DeleteObject(bmp); DeleteDC(mem); EndPaint(hwnd, &ps); return 0; }
    if (msg == WM_ERASEBKGND) return 1;
    return DefWindowProcW(hwnd, msg, wp, lp);
}
static void updateMonthTitle() { SetWindowTextW(g_caTitle, (std::wstring(MONTHS[g_caMonthN - 1]) + L" " + std::to_wstring(g_caYear) + L"   —   šedě nepracovní dny, oranžově výjimky (volno), světle pracovní výjimky, modře dnešek").c_str()); InvalidateRect(g_caMonth, nullptr, TRUE); }
static LRESULT CALLBACK CalendarProc(HWND hwnd, UINT msg, WPARAM wp, LPARAM lp) {
    switch (msg) {
    case WM_CREATE: {
        g_caPanel = hwnd;
        dlgCtl(hwnd, L"STATIC", L"Kalendář:", SS_LEFT, 8, 10, 60, 18, -1);
        g_caCombo = dlgCombo(hwnd, 70, 7, 260, 501);
        HWND l1 = dlgCtl(hwnd, L"STATIC", L"Pracovní týden", SS_LEFT, 8, 40, 300, 18, 502); SendMessage(l1, WM_SETFONT, (WPARAM)g_fontBold, TRUE);
        g_caWeek = dlgList(hwnd, 8, 60, 400, 190, 503); lvColumn(g_caWeek, 0, L"Den", 100); lvColumn(g_caWeek, 1, L"Typ", 90); lvColumn(g_caWeek, 2, L"Pracovní doba", 150); lvColumn(g_caWeek, 3, L"Hodin", 55, true);
        HWND l2 = dlgCtl(hwnd, L"STATIC", L"Výjimky (svátky, mimořádné dny)", SS_LEFT, 8, 260, 300, 18, 504); SendMessage(l2, WM_SETFONT, (WPARAM)g_fontBold, TRUE);
        g_caExc = dlgList(hwnd, 8, 280, 400, 300, 505); lvColumn(g_caExc, 0, L"Název", 150); lvColumn(g_caExc, 1, L"Od", 80); lvColumn(g_caExc, 2, L"Do", 80); lvColumn(g_caExc, 3, L"Pracovní", 60); lvColumn(g_caExc, 4, L"Opakování", 90); lvColumn(g_caExc, 5, L"Hodin", 50, true);
        g_caPrev = dlgButton(hwnd, L"◀", 430, 6, 32, 506); g_caNext = dlgButton(hwnd, L"▶", 466, 6, 32, 507); g_caToday = dlgButton(hwnd, L"Dnes", 502, 6, 60, 508);
        g_caTitle = dlgCtl(hwnd, L"STATIC", L"", SS_LEFT, 570, 10, 800, 18, -1);
        WNDCLASSW mc = {}; mc.lpfnWndProc = safeProc<MonthProc>; mc.hInstance = g_inst; mc.lpszClassName = L"MppMonth"; mc.hCursor = LoadCursor(nullptr, IDC_ARROW); RegisterClassW(&mc);
        g_caMonth = CreateWindowExW(WS_EX_CLIENTEDGE, L"MppMonth", L"", WS_CHILD | WS_VISIBLE, S(430), S(40), 100, 100, hwnd, nullptr, g_inst, nullptr);
        return 0;
    }
    case WM_COMMAND: {
        int id = LOWORD(wp);
        if (id == 501 && HIWORD(wp) == CBN_SELCHANGE) { int sel = (int)SendMessage(g_caCombo, CB_GETCURSEL, 0, 0); ListView_DeleteAllItems(g_caWeek); ListView_DeleteAllItems(g_caExc); const mpp::Calendar* c = currentCalendar(); if (c) { static const wchar_t* DAYS[7] = { L"Neděle", L"Pondělí", L"Úterý", L"Středa", L"Čtvrtek", L"Pátek", L"Sobota" }; double wh = 0; for (int i = 1; i <= 7; i++) { int k = i % 7; lvAdd(g_caWeek, { DAYS[k], c->working[k] ? L"pracovní" : L"nepracovní", c->working[k] ? c->hoursText[k] : L"—", c->working[k] ? mpp::fmtNumber(c->minutes[k] / 60.0) : L"" }); wh += c->working[k] ? c->minutes[k] / 60.0 : 0; } lvAdd(g_caWeek, { L"Týden celkem", L"", L"", mpp::fmtNumber(wh) }); static const wchar_t* REC[] = { L"", L"denně", L"ročně", L"ročně (pozice)", L"měsíčně", L"měsíčně (pozice)", L"týdně", L"denně" }; for (auto& e : c->exceptions) lvAdd(g_caExc, { e.name.empty() ? L"(bez názvu)" : e.name, mpp::fmtDate(mpp::fromUnixDays(e.from)), mpp::fmtDate(mpp::fromUnixDays(e.to)), e.working ? L"ano" : L"ne", e.rec ? REC[e.rec] : L"", e.working && e.minutes ? mpp::fmtNumber(e.minutes / 60.0) + L" h" : L"" }); } (void)sel; InvalidateRect(g_caMonth, nullptr, TRUE); }
        else if (id == 506) { if (--g_caMonthN < 1) { g_caMonthN = 12; g_caYear--; } updateMonthTitle(); }
        else if (id == 507) { if (++g_caMonthN > 12) { g_caMonthN = 1; g_caYear++; } updateMonthTitle(); }
        else if (id == 508) { mpp::Date d = mpp::toDate(g_today); g_caYear = d.y; g_caMonthN = (int)d.m; updateMonthTitle(); }
        return 0;
    }
    case WM_NOTIFY: { NMHDR* nm = (NMHDR*)lp; if (nm->hwndFrom == g_caExc && nm->code == LVN_ITEMCHANGED) { int i = ListView_GetNextItem(g_caExc, -1, LVNI_SELECTED); const mpp::Calendar* c = currentCalendar(); if (i >= 0 && c && i < (int)c->exceptions.size()) { mpp::Date d = mpp::toDate(mpp::fromUnixDays(c->exceptions[i].from)); g_caYear = d.y; g_caMonthN = (int)d.m; updateMonthTitle(); } } return 0; }
    case WM_SIZE: { int w = LOWORD(lp), h = HIWORD(lp); int lw = imin(S(420), w / 2); MoveWindow(g_caWeek, S(8), S(60), lw - S(16), S(190), TRUE); MoveWindow(g_caExc, S(8), S(280), lw - S(16), h - S(290), TRUE); MoveWindow(g_caPrev, lw + S(10), S(6), S(32), S(24), TRUE); MoveWindow(g_caNext, lw + S(46), S(6), S(32), S(24), TRUE); MoveWindow(g_caToday, lw + S(82), S(6), S(60), S(24), TRUE); MoveWindow(g_caTitle, lw + S(150), S(10), imax(100, w - lw - S(160)), S(18), TRUE); MoveWindow(g_caMonth, lw + S(10), S(40), w - lw - S(20), h - S(50), TRUE); return 0; }
    case WM_CTLCOLORSTATIC: { SetTextColor((HDC)wp, T.ink); SetBkColor((HDC)wp, T.panel); static HBRUSH b; if (b) DeleteObject(b); b = CreateSolidBrush(T.panel); return (LRESULT)b; }
    case WM_ERASEBKGND: { RECT rc; GetClientRect(hwnd, &rc); HBRUSH b = CreateSolidBrush(T.panel); FillRect((HDC)wp, &rc, b); DeleteObject(b); return 1; }
    }
    return DefWindowProcW(hwnd, msg, wp, lp);
}

// ---------------- Projekt ----------------
static HWND g_prPanel, g_prList;
static void fillProjectPanel() {
    ListView_DeleteAllItems(g_prList); if (!g_loaded) return;
    const mpp::Project& p = g_prj;
    auto sec = [&](const wchar_t* s) { lvAdd(g_prList, { s, L"" }, 1); };
    auto row = [&](const wchar_t* k, const std::wstring& v) { lvAdd(g_prList, { k, v.empty() ? L"—" : v }); };
    WIN32_FILE_ATTRIBUTE_DATA fa = {}; GetFileAttributesExW(g_file.c_str(), GetFileExInfoStandard, &fa);
    sec(L"SOUBOR"); row(L"Cesta", g_file); row(L"Velikost", mpp::fmtNumber(((double)fa.nFileSizeHigh * 4294967296.0 + fa.nFileSizeLow) / 1024) + L" KB"); row(L"Formát", p.format + (p.mppType == 14 ? L" – Project 2010 a novější" : p.mppType == 12 ? L" – Project 2007" : L""));
    row(L"Aplikace", p.appName + (p.mppType ? L" · verze " + std::to_wstring(p.appVersion) : L"")); row(L"Vytvořeno", mpp::fmtDate(p.creationDate, true)); row(L"Naposledy uloženo", mpp::fmtDate(p.lastSaved, true));
    sec(L"PROJEKT"); row(L"Název / titul", p.title); row(L"Předmět", p.subject); row(L"Autor", p.author); row(L"Manažer", p.manager); row(L"Společnost", p.company); row(L"Kategorie", p.category); row(L"Klíčová slova", p.keywords); row(L"Komentář", p.comments);
    sec(L"HARMONOGRAM"); row(L"Zahájení projektu", mpp::fmtDate(p.startDate, true)); row(L"Dokončení projektu", mpp::fmtDate(p.finishDate, true)); row(L"Datum stavu", mpp::fmtDate(p.statusDate, true)); row(L"Aktuální datum", mpp::fmtDate(p.currentDate, true));
    row(L"Plánovat od", p.scheduleFromStart ? L"data zahájení" : L"data dokončení"); row(L"Směrný plán uložen", mpp::fmtDate(p.baselineDate, true));
    double work = 0, cost = 0; int tasks = 0, sums = 0, ms = 0, crit = 0, done = 0, inact = 0, late = 0;
    for (auto& t : p.tasks) { if (t.isNull) continue; tasks++; if (t.summary) { sums++; continue; } if (t.milestone) ms++; if (t.critical) crit++; if (t.pctComplete >= 100) done++; if (!t.active) inact++; if (isLate(t)) late++; work += t.workMinutes; cost += t.cost; }
    row(L"Výchozí kalendář", p.calendar().name.empty() ? L"(vestavěný Po–Pá)" : p.calendar().name); row(L"Hodin denně / týdně", mpp::fmtNumber(p.minutesPerDay / 60.0) + L" / " + mpp::fmtNumber(p.minutesPerWeek / 60.0)); row(L"Dnů v měsíci", std::to_wstring(p.daysPerMonth)); row(L"Měna", p.currency); row(L"Limit kritické rezervy", std::to_wstring(p.criticalSlackLimitDays) + L" d");
    sec(L"STATISTIKA"); row(L"Úkolů celkem", std::to_wstring(tasks)); row(L"z toho souhrnných", std::to_wstring(sums)); row(L"z toho milníků", std::to_wstring(ms)); row(L"Kritických", std::to_wstring(crit)); row(L"Dokončených", std::to_wstring(done)); row(L"Zpožděných", std::to_wstring(late)); row(L"Neaktivních", std::to_wstring(inact));
    row(L"Práce celkem", mpp::fmtHours(work / 60.0)); row(L"Náklady celkem", mpp::fmtMoney(cost, p)); row(L"Zdrojů", std::to_wstring(p.resources.size())); row(L"Přiřazení", std::to_wstring(p.assignments.size()));
    std::wstring cals; for (auto& c : p.calendars) { if (!cals.empty()) cals += L", "; cals += c.name; } row(L"Kalendáře", cals);
    std::wstring cf; for (auto& c : p.customFields) { if (!cf.empty()) cf += L", "; cf += c.title(); } row(L"Vlastní pole", cf);
    std::wstring bl; for (int n : p.baselinesUsed) { if (!bl.empty()) bl += L", "; bl += n == 0 ? L"Směrný plán" : L"Směrný plán " + std::to_wstring(n); } row(L"Směrné plány", bl);
    std::wstring sp; for (auto& t : p.tasks) if (!t.subproject.empty()) { if (!sp.empty()) sp += L"; "; sp += t.subproject; } if (!sp.empty()) { sec(L"VLOŽENÉ PODPROJEKTY"); row(L"Soubory", sp); }
}
static LRESULT CALLBACK ProjectProc(HWND hwnd, UINT msg, WPARAM wp, LPARAM lp) {
    switch (msg) {
    case WM_CREATE: g_prPanel = hwnd; g_prList = dlgList(hwnd, 0, 0, 100, 100, 601, LVS_SINGLESEL); lvColumn(g_prList, 0, L"Vlastnost", 220); lvColumn(g_prList, 1, L"Hodnota", 900); return 0;
    case WM_SIZE: MoveWindow(g_prList, 0, 0, LOWORD(lp), HIWORD(lp), TRUE); return 0;
    case WM_NOTIFY: { NMHDR* nm = (NMHDR*)lp; if (nm->hwndFrom == g_prList && nm->code == NM_CUSTOMDRAW) { NMLVCUSTOMDRAW* cd = (NMLVCUSTOMDRAW*)lp; if (cd->nmcd.dwDrawStage == CDDS_PREPAINT) return CDRF_NOTIFYITEMDRAW; if (cd->nmcd.dwDrawStage == CDDS_ITEMPREPAINT) { cd->clrText = T.ink; if (cd->nmcd.lItemlParam == 1) { SelectObject(cd->nmcd.hdc, g_fontBold); cd->clrText = T.linkSel; return CDRF_NEWFONT; } } } return 0; }
    }
    return DefWindowProcW(hwnd, msg, wp, lp);
}

static void registerPanelClasses() {
    struct { const wchar_t* name; WNDPROC proc; } cls[] = { { L"MppTimeline", safeProc<TimelineProc> }, { L"MppResources", safeProc<ResourcesProc> }, { L"MppUsage", safeProc<UsageProc> }, { L"MppCalendar", safeProc<CalendarProc> }, { L"MppProject", safeProc<ProjectProc> } };
    for (auto& c : cls) { WNDCLASSW wc = {}; wc.lpfnWndProc = c.proc; wc.hInstance = g_inst; wc.lpszClassName = c.name; wc.hCursor = LoadCursor(nullptr, IDC_ARROW); wc.style = CS_DBLCLKS; RegisterClassW(&wc); }
}
static void refreshPanels() { rebuildTimeline(); fillResourcesPanel(); fillUsage(); fillCalendarPanel(); updateMonthTitle(); fillProjectPanel(); }
