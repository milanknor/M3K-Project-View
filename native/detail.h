// detail.h - spodní panel detailu vybraného úkolu: záložky Obecné / Zdroje / Vazby / Poznámky (podle M3K Project View)
#pragma once

static HWND g_dtTabs, g_dtGen, g_dtAsg, g_dtLinks, g_dtNotes;
static int g_dtPage = 0, g_dtScroll = 0, g_detailH = 0;
static void selectTaskByUid(int uid);

// Obsah záložky Obecné (kreslí se sám -> skupiny s nadpisy, popisky šedě, hodnoty tučně)
struct DtItem { std::wstring label, value; COLORREF color = CLR_INVALID; int bar = -1; };
struct DtGroup { std::wstring title; std::vector<DtItem> items; };
static struct { std::wstring title, subtitle, empty; std::vector<DtGroup> groups; std::vector<DtItem> wide; DtGroup custom, baselines; } g_dg;

static std::wstring orDash(const std::wstring& s) { return s.empty() ? L"—" : s; }
static const wchar_t* relTypeLong(int t) { return t == 0 ? L"FF – konec – konec" : t == 2 ? L"SF – začátek – konec" : t == 3 ? L"SS – začátek – začátek" : L"FS – konec – začátek"; }
static std::wstring lagText(const mpp::Duration& d) {
    if (!d.valid() || d.raw == 0) return L"—";
    mpp::Duration a = d; a.raw = std::abs(a.raw); return (d.raw < 0 ? L"-" : L"+") + mpp::fmtDuration(a, g_prj);
}
static std::wstring hoursOrZero(double minutes) { return mpp::fmtNumber(minutes / 60.0) + L" h"; }

static void buildGeneral(const mpp::Task& t, int idx) {
    const mpp::Project& p = g_prj; g_dg = {};
    g_dg.title = std::to_wstring(t.id) + L" · " + t.name;
    g_dg.subtitle = std::wstring(mpp::taskKindName(t)) + L"   ·   WBS " + orDash(t.wbs) + L"   ·   Osnova " + orDash(t.outlineNumber) + L"   ·   Jedinečné ID " + std::to_wstring(t.uid);
    auto dur = [&](const mpp::Duration& d, bool est = false) { return d.valid() ? mpp::fmtDuration(d, p, est) : std::wstring(L"—"); };
    auto dt = [&](Time v, bool tm = false) { return v ? mpp::fmtDate(v, tm) : std::wstring(L"—"); };
    auto slack = [&](int raw) { std::wstring s = mpp::fmtSlackRaw(raw, p); return s.empty() ? std::wstring(L"—") : s; };
    auto money = [&](double v) { return orDash(mpp::fmtMoney(v, p)); };

    DtGroup g1{ L"Termíny" };
    g1.items = { { L"Zahájení", dt(t.start, true) }, { L"Dokončení", dt(t.finish, true) }, { L"Trvání", dur(t.duration, t.estimated) },
                 { L"Skutečné zahájení", dt(t.actualStart) }, { L"Skutečné dokončení", dt(t.actualFinish) },
                 { L"Termín (deadline)", dt(t.deadline), t.deadline && t.finish > t.deadline ? T.late : CLR_INVALID } };

    DtGroup g2{ L"Průběh a směrný plán" };
    int pct = imax(0, t.pctComplete);
    COLORREF stc = isLate(t) ? T.late : isLateStart(t) ? T.lateStart : pct >= 100 ? T.ok : CLR_INVALID;
    std::wstring var = varianceText(t, idx); COLORREF varc = !var.empty() && var[0] == L'+' ? T.late : !var.empty() && var[0] == L'-' ? T.ok : CLR_INVALID;
    const mpp::Baseline& b0 = t.baselines[0];
    g2.items = { { L"Dokončeno", std::to_wstring(pct) + L" %", CLR_INVALID, pct }, { L"Práce dokončena", t.pctWork >= 0 ? std::to_wstring(t.pctWork) + L" %" : L"—" },
                 { L"Stav", stateText(t), stc },
                 { L"Základní plán", b0.start || b0.finish ? dt(b0.start) + L" – " + dt(b0.finish) : L"—" }, { L"Trvání (zákl. plán)", b0.start || b0.finish ? dur(b0.duration) : L"—" },
                 { L"Skluz dokončení", var.empty() ? L"—" : var + L" prac. dní", varc } };

    DtGroup g3{ L"Práce a náklady" };
    g3.items = { { L"Práce", hoursOrZero(t.workMinutes) }, { L"Skutečná práce", hoursOrZero(t.actualWorkMinutes) }, { L"Zbývající práce", hoursOrZero(t.remainingWorkMinutes) },
                 { L"Náklady", money(t.cost) }, { L"Skutečné náklady", money(t.actualCost) },
                 { L"Práce / náklady (ZP)", b0.any() ? mpp::fmtNumber(b0.workHours) + L" h / " + money(b0.cost) : L"—" } };

    DtGroup g4{ L"Plánování" };
    bool negSlack = t.totalSlackRaw != INT32_MIN && t.totalSlackRaw < 0;
    g4.items = { { L"Omezení", std::wstring(mpp::constraintName(t.constraintType)) + (t.constraintDate && t.constraintType >= 2 ? L" (" + mpp::fmtDate(t.constraintDate) + L")" : L"") },
                 { L"Celková rezerva", slack(t.totalSlackRaw), negSlack ? T.late : CLR_INVALID }, { L"Volná rezerva", slack(t.freeSlackRaw) },
                 { L"Kritický", t.critical ? L"ano" : L"ne", t.critical ? T.late : CLR_INVALID },
                 { L"Priorita", mpp::priorityName(t.priority) }, { L"Kalendář", orDash(t.calendarName) } };
    g4.items.push_back({ L"Typ úkolu", std::wstring(mpp::taskTypeName(t.taskType)) + (t.effortDriven ? L", řízený úsilím" : L"") });
    g4.items.push_back({ L"Plánování", t.manual ? L"ručně" : L"automaticky" });
    g_dg.groups = { g1, g2, g3, g4 };

    g_dg.wide.push_back({ L"Předchůdci", orDash(mpp::fmtPredecessors(t, p)) });
    g_dg.wide.push_back({ L"Následníci", orDash(successorsText(t)) });
    g_dg.wide.push_back({ L"Zdroje", orDash(t.resourceNames) });
    if (!t.contact.empty()) g_dg.wide.push_back({ L"Kontakt", t.contact });
    if (!t.subproject.empty()) g_dg.wide.push_back({ L"Podprojekt", t.subproject });

    g_dg.custom.title = L"Vlastní pole";
    for (auto& cf : p.customFields) { std::wstring v = mpp::fmtCustom(cf, t.customValue(cf.id), p); if (!v.empty()) g_dg.custom.items.push_back({ cf.title(), v }); }
    g_dg.baselines.title = L"Další směrné plány";
    for (int n : p.baselinesUsed) {
        if (n == 0) continue; const mpp::Baseline& b = t.baselines[n]; if (!b.any()) continue;
        std::wstring v = dt(b.start) + L" – " + dt(b.finish) + (b.duration.valid() ? L" (" + mpp::fmtDuration(b.duration, p) + L")" : L"");
        if (b.workHours || b.cost) v += L", " + mpp::fmtNumber(b.workHours) + L" h / " + money(b.cost);
        g_dg.baselines.items.push_back({ L"Směrný plán " + std::to_wstring(n), v });
    }
}

// Vykreslení (draw=false jen spočítá výšku obsahu kvůli posuvníku)
static int drawGeneral(HDC hdc, int W, int yOff, bool draw) {
    SetBkMode(hdc, TRANSPARENT);
    TEXTMETRICW tm; SelectObject(hdc, g_font); GetTextMetricsW(hdc, &tm);
    const int pad = S(12), rowH = tm.tmHeight + S(5), gap = S(22);
    int y = S(8) - yOff;
    auto text = [&](const std::wstring& s, int x, int yy, int w, HFONT f, COLORREF c) {
        if (!draw || w <= 0) return; SelectObject(hdc, f); SetTextColor(hdc, c); RECT r = { x, yy, x + w, yy + rowH }; drawText(hdc, s, r, DT_LEFT | DT_END_ELLIPSIS);
    };
    auto width = [&](const std::wstring& s, HFONT f) { SelectObject(hdc, f); SIZE sz; GetTextExtentPoint32W(hdc, s.c_str(), (int)s.size(), &sz); return (int)sz.cx; };
    if (!g_dg.empty.empty()) { RECT r = { pad, y, W - pad, y + rowH * 3 }; if (draw) { SelectObject(hdc, g_font); SetTextColor(hdc, T.muted); DrawTextW(hdc, g_dg.empty.c_str(), -1, &r, DT_LEFT | DT_WORDBREAK | DT_NOPREFIX); } return rowH * 3; }

    text(g_dg.title, pad, y, W - 2 * pad, g_fontBold, T.ink); y += rowH;
    text(g_dg.subtitle, pad, y, W - 2 * pad, g_font, T.muted); y += rowH + S(4);

    // skupina = nadpis s linkou + řádky "popisek  hodnota"; sloupců podle šířky panelu
    auto drawGroup = [&](const DtGroup& g, int x, int yy, int w) {
        text(g.title, x, yy, w, g_fontBold, T.linkSel);
        if (draw) { HPEN pn = CreatePen(PS_SOLID, 1, T.gridStrong); HGDIOBJ o = SelectObject(hdc, pn); MoveToEx(hdc, x, yy + rowH - S(1), nullptr); LineTo(hdc, x + w, yy + rowH - S(1)); SelectObject(hdc, o); DeleteObject(pn); }
        yy += rowH + S(2);
        int lw = 0; for (auto& it : g.items) lw = imax(lw, width(it.label, g_font)); lw = imin(lw + S(10), w / 2);
        for (auto& it : g.items) {
            text(it.label, x, yy, lw, g_font, T.muted);
            int vx = x + lw;
            if (it.bar >= 0 && draw) {
                int bw = S(56), bh = rowH - S(10); RECT br = { vx, yy + S(5), vx + bw, yy + S(5) + bh };
                HBRUSH b1 = CreateSolidBrush(T.grid); FillRect(hdc, &br, b1); DeleteObject(b1);
                RECT fr = br; fr.right = br.left + bw * imin(100, it.bar) / 100; HBRUSH b2 = CreateSolidBrush(it.bar >= 100 ? T.ok : T.task); FillRect(hdc, &fr, b2); DeleteObject(b2);
            }
            if (it.bar >= 0) vx += S(62);
            text(it.value, vx, yy, x + w - vx, g_fontBold, it.color == CLR_INVALID ? T.ink : it.color);
            yy += rowH;
        }
        return yy;
    };
    int avail = W - 2 * pad;
    int ncols = avail >= S(1040) ? 4 : avail >= S(500) ? 2 : 1;
    int colW = (avail - (ncols - 1) * gap) / ncols;
    for (size_t i = 0; i < g_dg.groups.size(); i += ncols) {
        int rowEnd = y;
        for (int c = 0; c < ncols && i + c < g_dg.groups.size(); c++) rowEnd = imax(rowEnd, drawGroup(g_dg.groups[i + c], pad + c * (colW + gap), y, colW));
        y = rowEnd + S(8);
    }
    // široké řádky (vazby, zdroje)
    int lw = 0; for (auto& it : g_dg.wide) lw = imax(lw, width(it.label, g_font)); lw += S(10);
    for (auto& it : g_dg.wide) { text(it.label, pad, y, lw, g_font, T.muted); text(it.value, pad + lw, y, avail - lw, g_fontBold, T.ink); y += rowH; }
    // vlastní pole a další směrné plány jako mřížka popisek: hodnota
    for (const DtGroup* g : { &g_dg.custom, &g_dg.baselines }) {
        if (g->items.empty()) continue;
        y += S(8);
        text(g->title, pad, y, avail, g_fontBold, T.linkSel);
        if (draw) { HPEN pn = CreatePen(PS_SOLID, 1, T.gridStrong); HGDIOBJ o = SelectObject(hdc, pn); MoveToEx(hdc, pad, y + rowH - S(1), nullptr); LineTo(hdc, pad + avail, y + rowH - S(1)); SelectObject(hdc, o); DeleteObject(pn); }
        y += rowH + S(2);
        int n = g == &g_dg.baselines ? imax(1, ncols / 2) : ncols, cw = (avail - (n - 1) * gap) / n;
        for (size_t i = 0; i < g->items.size(); i++) {
            int c = (int)(i % n), x = pad + c * (cw + gap);
            std::wstring lab = g->items[i].label + L":"; int l = imin(width(lab, g_font) + S(6), cw / 2);
            text(lab, x, y, l, g_font, T.muted); text(g->items[i].value, x + l, y, cw - l, g_fontBold, T.ink);
            if (c == n - 1 || i + 1 == g->items.size()) y += rowH;
        }
    }
    return y + yOff + S(8);
}
static int g_dtContentH = 0;
static void dtUpdateScroll() {
    RECT rc; GetClientRect(g_dtGen, &rc);
    SCROLLINFO si = { sizeof(si), SIF_RANGE | SIF_PAGE | SIF_POS }; si.nMax = imax(0, g_dtContentH - 1); si.nPage = rc.bottom; si.nPos = g_dtScroll;
    SetScrollInfo(g_dtGen, SB_VERT, &si, TRUE); GetScrollInfo(g_dtGen, SB_VERT, &si); g_dtScroll = si.nPos;
}
static LRESULT CALLBACK DetailGenProc(HWND hwnd, UINT msg, WPARAM wp, LPARAM lp) {
    switch (msg) {
    case WM_ERASEBKGND: return 1;
    case WM_PAINT: {
        PAINTSTRUCT ps; HDC hdc = BeginPaint(hwnd, &ps); RECT rc; GetClientRect(hwnd, &rc);
        HDC mem = CreateCompatibleDC(hdc); HBITMAP bmp = CreateCompatibleBitmap(hdc, imax(1, rc.right), imax(1, rc.bottom)); HGDIOBJ ob = SelectObject(mem, bmp);
        HBRUSH bg = CreateSolidBrush(T.bg); FillRect(mem, &rc, bg); DeleteObject(bg);
        int h = drawGeneral(mem, rc.right, 0, false);
        if (h != g_dtContentH) { g_dtContentH = h; dtUpdateScroll(); GetClientRect(hwnd, &rc); }
        drawGeneral(mem, rc.right, g_dtScroll, true);
        BitBlt(hdc, 0, 0, rc.right, rc.bottom, mem, 0, 0, SRCCOPY);
        SelectObject(mem, ob); DeleteObject(bmp); DeleteDC(mem); EndPaint(hwnd, &ps); return 0;
    }
    case WM_SIZE: g_dtContentH = -1; InvalidateRect(hwnd, nullptr, FALSE); return 0;
    case WM_VSCROLL: {
        SCROLLINFO si = { sizeof(si), SIF_ALL }; GetScrollInfo(hwnd, SB_VERT, &si); int pos = si.nPos;
        switch (LOWORD(wp)) { case SB_LINEUP: pos -= S(20); break; case SB_LINEDOWN: pos += S(20); break; case SB_PAGEUP: pos -= (int)si.nPage; break; case SB_PAGEDOWN: pos += (int)si.nPage; break; case SB_THUMBTRACK: case SB_THUMBPOSITION: pos = si.nTrackPos; break; }
        g_dtScroll = imax(0, imin(pos, si.nMax - (int)si.nPage + 1)); dtUpdateScroll(); InvalidateRect(hwnd, nullptr, FALSE); return 0;
    }
    case WM_MOUSEWHEEL: SendMessage(hwnd, WM_VSCROLL, GET_WHEEL_DELTA_WPARAM(wp) > 0 ? SB_LINEUP : SB_LINEDOWN, 0); SendMessage(hwnd, WM_VSCROLL, GET_WHEEL_DELTA_WPARAM(wp) > 0 ? SB_LINEUP : SB_LINEDOWN, 0); return 0;
    case WM_LBUTTONDOWN: SetFocus(hwnd); return 0;
    }
    return DefWindowProcW(hwnd, msg, wp, lp);
}

static void dtShowPage(int page) {
    g_dtPage = imax(0, imin(3, page)); TabCtrl_SetCurSel(g_dtTabs, g_dtPage);
    HWND pages[4] = { g_dtGen, g_dtAsg, g_dtLinks, g_dtNotes };
    for (int i = 0; i < 4; i++) ShowWindow(pages[i], i == g_dtPage ? SW_SHOW : SW_HIDE);
}
static void dtSetTabTitle(int i, const std::wstring& s) { TCITEMW ti = {}; ti.mask = TCIF_TEXT; ti.pszText = (LPWSTR)s.c_str(); TabCtrl_SetItem(g_dtTabs, i, &ti); }

// Naplní všechny záložky pro úkol (idx < 0 = nic nevybráno)
static void fillDetail(int idx) {
    SendMessage(g_dtAsg, WM_SETREDRAW, FALSE, 0); SendMessage(g_dtLinks, WM_SETREDRAW, FALSE, 0);
    ListView_DeleteAllItems(g_dtAsg); ListView_DeleteAllItems(g_dtLinks);
    g_dtScroll = 0; g_dtContentH = -1;
    int nAsg = 0, nLinks = 0; std::wstring notes;
    if (idx < 0) {
        g_dg = {}; g_dg.empty = g_loaded ? L"Vyberte úkol v tabulce nebo v diagramu – zde se zobrazí termíny, průběh, práce a náklady, omezení, zdroje, vazby a poznámky. Souhrnné úkoly sbalíte a rozbalíte kliknutím na šipku, dvojklikem nebo mezerníkem." : L"";
    } else {
        const mpp::Task& t = g_prj.tasks[idx];
        buildGeneral(t, idx);
        for (int ai : t.assignments) {
            const mpp::Assignment& a = g_prj.assignments[ai]; const mpp::Resource* r = g_prj.rscByUid(a.rscUid); if (!r) continue;
            std::wstring pw = a.workHours > 0 ? std::to_wstring((int)std::lround(a.actualWorkHours * 100 / a.workHours)) + L" %" : L"—";
            lvAdd(g_dtAsg, { r->name, mpp::resourceTypeName(r->type), mpp::fmtNumber(a.units * 100) + L" %", mpp::fmtNumber(a.workHours) + L" h", orDash(mpp::fmtDate(a.start)), orDash(mpp::fmtDate(a.finish)), pw, mpp::fmtMoney(a.cost, g_prj) }, r->uid);
            nAsg++;
        }
        for (const mpp::Relation& rel : t.preds) {
            const mpp::Task* o = g_prj.byUid(rel.predUid); if (!o) continue;
            lvAdd(g_dtLinks, { L"Předchůdce", std::to_wstring(o->id), o->name, relTypeLong(rel.type), lagText(rel.lag), orDash(mpp::fmtDate(o->start)), orDash(mpp::fmtDate(o->finish)) }, o->uid); nLinks++;
        }
        auto it = g_succ.find(t.uid);
        if (it != g_succ.end()) for (int su : it->second) {
            const mpp::Task* o = g_prj.byUid(su); if (!o) continue;
            const mpp::Relation* rel = nullptr; for (const mpp::Relation& r : o->preds) if (r.predUid == t.uid) { rel = &r; break; }
            lvAdd(g_dtLinks, { L"Následník", std::to_wstring(o->id), o->name, rel ? relTypeLong(rel->type) : L"", rel ? lagText(rel->lag) : L"—", orDash(mpp::fmtDate(o->start)), orDash(mpp::fmtDate(o->finish)) }, o->uid); nLinks++;
        }
        notes = t.notes; size_t p = 0; while ((p = notes.find(L'\n', p)) != std::wstring::npos) { if (p == 0 || notes[p - 1] != L'\r') { notes.insert(p, 1, L'\r'); p++; } p++; }
    }
    SetWindowTextW(g_dtNotes, notes.c_str());
    dtSetTabTitle(1, nAsg ? L"Zdroje (" + std::to_wstring(nAsg) + L")" : L"Zdroje");
    dtSetTabTitle(2, nLinks ? L"Vazby (" + std::to_wstring(nLinks) + L")" : L"Vazby");
    dtSetTabTitle(3, notes.empty() ? L"Poznámky" : L"Poznámky ●");
    SendMessage(g_dtAsg, WM_SETREDRAW, TRUE, 0); SendMessage(g_dtLinks, WM_SETREDRAW, TRUE, 0);
    InvalidateRect(g_dtAsg, nullptr, TRUE); InvalidateRect(g_dtLinks, nullptr, TRUE); InvalidateRect(g_dtGen, nullptr, FALSE);
}

static LRESULT CALLBACK DetailProc(HWND hwnd, UINT msg, WPARAM wp, LPARAM lp) {
    switch (msg) {
    case WM_CREATE: {
        g_dtTabs = CreateWindowExW(0, WC_TABCONTROLW, L"", WS_CHILD | WS_VISIBLE | WS_CLIPSIBLINGS | TCS_FOCUSNEVER, 0, 0, 10, 10, hwnd, (HMENU)1, g_inst, nullptr);
        SendMessage(g_dtTabs, WM_SETFONT, (WPARAM)g_font, TRUE);
        for (const wchar_t* s : { L"Obecné", L"Zdroje", L"Vazby", L"Poznámky" }) { TCITEMW ti = {}; ti.mask = TCIF_TEXT; ti.pszText = (LPWSTR)s; TabCtrl_InsertItem(g_dtTabs, TabCtrl_GetItemCount(g_dtTabs), &ti); }
        g_dtGen = CreateWindowExW(0, L"MppDetailGen", L"", WS_CHILD | WS_VSCROLL, 0, 0, 10, 10, hwnd, (HMENU)2, g_inst, nullptr);
        g_dtAsg = dlgList(hwnd, 0, 0, 10, 10, 3); g_dtLinks = dlgList(hwnd, 0, 0, 10, 10, 4);
        for (HWND l : { g_dtAsg, g_dtLinks }) { SetWindowLongPtrW(l, GWL_EXSTYLE, GetWindowLongPtrW(l, GWL_EXSTYLE) & ~WS_EX_CLIENTEDGE); ShowWindow(l, SW_HIDE); }
        int i = 0; for (auto c : { std::make_pair(L"Zdroj", 220), { L"Typ", 90 }, { L"Jednotky", 80 }, { L"Práce", 80 }, { L"Zahájení", 90 }, { L"Dokončení", 90 }, { L"% práce", 70 }, { L"Náklady", 110 } }) { bool right = i == 2 || i == 3 || i == 6 || i == 7; lvColumn(g_dtAsg, i, c.first, c.second, right); i++; }
        i = 0; for (auto c : { std::make_pair(L"Směr", 100), { L"ID", 60 }, { L"Název úkolu", 320 }, { L"Typ vazby", 170 }, { L"Prodleva", 80 }, { L"Zahájení", 90 }, { L"Dokončení", 90 } }) lvColumn(g_dtLinks, i++, c.first, c.second);
        g_dtNotes = CreateWindowExW(0, L"EDIT", L"", WS_CHILD | ES_MULTILINE | ES_READONLY | WS_VSCROLL | ES_AUTOVSCROLL, 0, 0, 10, 10, hwnd, (HMENU)5, g_inst, nullptr);
        SendMessage(g_dtNotes, WM_SETFONT, (WPARAM)g_font, TRUE); SendMessage(g_dtNotes, EM_SETMARGINS, EC_LEFTMARGIN | EC_RIGHTMARGIN, MAKELPARAM(S(10), S(10)));
        dtShowPage(iniInt(L"DetailTab", 0));
        return 0;
    }
    case WM_SIZE: {
        int w = LOWORD(lp), h = HIWORD(lp), th = S(26);
        MoveWindow(g_dtTabs, 0, 0, w, th, TRUE);
        for (HWND c : { g_dtGen, g_dtAsg, g_dtLinks, g_dtNotes }) MoveWindow(c, 0, th, w, imax(0, h - th), TRUE);
        return 0;
    }
    case WM_NOTIFY: {
        NMHDR* nm = (NMHDR*)lp;
        if (nm->hwndFrom == g_dtTabs && nm->code == TCN_SELCHANGE) { dtShowPage(TabCtrl_GetCurSel(g_dtTabs)); iniSet(L"DetailTab", std::to_wstring(g_dtPage)); return 0; }
        if (nm->hwndFrom == g_dtLinks && nm->code == NM_DBLCLK) { int i = ((NMITEMACTIVATE*)lp)->iItem; if (i >= 0) selectTaskByUid((int)lvParam(g_dtLinks, i)); return 0; }
        return 0;
    }
    case WM_CTLCOLORSTATIC: case WM_CTLCOLOREDIT: { HDC hdc = (HDC)wp; SetTextColor(hdc, T.ink); SetBkColor(hdc, T.bg); static HBRUSH b; if (b) DeleteObject(b); b = CreateSolidBrush(T.bg); return (LRESULT)b; }
    case WM_ERASEBKGND: { RECT rc; GetClientRect(hwnd, &rc); HBRUSH b = CreateSolidBrush(T.panel); FillRect((HDC)wp, &rc, b); DeleteObject(b); return 1; }
    }
    return DefWindowProcW(hwnd, msg, wp, lp);
}
static void registerDetailClasses() {
    WNDCLASSW c = {}; c.hInstance = g_inst; c.hCursor = LoadCursor(nullptr, IDC_ARROW);
    c.lpfnWndProc = safeProc<DetailProc>; c.lpszClassName = L"MppDetail"; RegisterClassW(&c);
    c.lpfnWndProc = safeProc<DetailGenProc>; c.lpszClassName = L"MppDetailGen"; RegisterClassW(&c);
}
