// gantt.h - kreslení Ganttova diagramu (obrazovka, tisk, PNG/PDF), úvodní obrazovka, tisková sestava
#pragma once
#include "filters.h"

static const wchar_t* MONTHS[12] = { L"leden", L"únor", L"březen", L"duben", L"květen", L"červen", L"červenec", L"srpen", L"září", L"říjen", L"listopad", L"prosinec" };
static const wchar_t* MONTHS_S[12] = { L"led", L"úno", L"bře", L"dub", L"kvě", L"čvn", L"čvc", L"srp", L"zář", L"říj", L"lis", L"pro" };
static const wchar_t* DOW_S[7] = { L"Po", L"Út", L"St", L"Čt", L"Pá", L"So", L"Ne" };
static std::vector<std::pair<RECT, int>> g_startRects; // úvodní obrazovka: obdélník -> index v g_recent (-1 = otevřít)

struct GanttPaint {
    HDC hdc; RECT area;
    int headerH, rowH, firstRow, rowCount;
    double pxPerDay; int64_t day0; int hscroll; int scale;
    bool progressLine, links, highlightSel, weekends, legend;
    int selRow; std::vector<RECT>* barRects;
    int S(int px) const { return MulDiv(px, scale, 96); }
    int xOfDay(int64_t ud) const { return area.left + (int)std::floor((double)(ud - day0) * pxPerDay) - hscroll; }
    int xOf(Time t) const { return area.left + (int)std::floor((t - mpp::fromUnixDays(day0)) / 86400.0 * pxPerDay) - hscroll; }
    int yOf(int row) const { return area.top + headerH + (row - firstRow) * rowH; }
    void run() {
        int w = area.right, h = area.bottom;
        int legendH = (legend && !g_legend.empty()) ? S(24) : 0;
        int bottom = imin(h - legendH, yOf(firstRow + rowCount) + rowH);
        HRGN clip = CreateRectRgn(area.left, area.top, area.right, area.bottom); SelectClipRgn(hdc, clip); DeleteObject(clip);
        SetBkMode(hdc, TRANSPARENT);
        const int tier = headerH / 2;
        int mode = pxPerDay >= 16 ? 0 : (pxPerDay >= 3.5 ? 1 : 2);
        int64_t dFrom = day0 + (int64_t)std::floor(hscroll / pxPerDay) - 1;
        int64_t dTo = day0 + (int64_t)std::ceil((hscroll + (area.right - area.left)) / pxPerDay) + 1;
        HBRUSH bWeekend = CreateSolidBrush(T.weekend), bHoliday = CreateSolidBrush(T.holiday), bHeader = CreateSolidBrush(T.headerBg), bSel = CreateSolidBrush(T.sel);
        HPEN pGrid = CreatePen(PS_SOLID, 1, T.grid), pGridDark = CreatePen(PS_SOLID, 1, T.gridStrong);
        const mpp::Calendar& cal = g_prj.calendar();
        if (mode < 2 && weekends) for (int64_t d = dFrom; d <= dTo; d++) {
            bool wk = mpp::dowOfUnixDay(d) >= 5; const mpp::CalException* e = g_holidays ? cal.exceptionAt(d) : nullptr;
            bool nonWork = g_holidays ? !cal.isWorking(d) : wk;
            if (nonWork || e) { RECT r = { xOfDay(d), area.top + headerH, xOfDay(d + 1), bottom }; FillRect(hdc, &r, e ? bHoliday : bWeekend); }
        }
        if (highlightSel && selRow >= firstRow && selRow < firstRow + rowCount) { RECT r = { area.left, yOf(selRow), w, yOf(selRow) + rowH }; FillRect(hdc, &r, bSel); }
        SelectObject(hdc, pGrid);
        for (int i = 0; i <= rowCount; i++) { int y = yOf(firstRow + i); if (y > bottom) break; MoveToEx(hdc, area.left, y, nullptr); LineTo(hdc, w, y); }
        RECT hr = { area.left, area.top, w, area.top + headerH }; FillRect(hdc, &hr, bHeader);
        SetTextColor(hdc, T.ink);
        int y0; unsigned mm0, dd0; mpp::civilFromDays(dFrom, y0, mm0, dd0); int mo0 = (int)mm0;
        { int y = y0, m = mode == 2 ? 1 : mo0;
          while (true) {
              int64_t s = mpp::daysFromCivil(y, (unsigned)m, 1);
              int ny = y, nm = m + (mode == 2 ? 12 : 1); if (nm > 12) { nm -= 12; ny++; }
              int64_t e = mpp::daysFromCivil(ny, (unsigned)nm, 1);
              if (s > dTo) break;
              int x0 = xOfDay(s), x1 = xOfDay(e);
              SelectObject(hdc, pGridDark); MoveToEx(hdc, x1, area.top, nullptr); LineTo(hdc, x1, mode == 2 ? bottom : area.top + tier);
              RECT r = { imax(x0, area.left) + 2, area.top, imin(x1, w) - 2, area.top + tier };
              std::wstring lab = mode == 2 ? std::to_wstring(y) : std::wstring(MONTHS[m - 1]) + L" " + std::to_wstring(y);
              if (r.right - r.left > S(30)) drawText(hdc, lab, r);
              y = ny; m = nm;
          } }
        if (mode == 0) {
            for (int64_t d = dFrom; d <= dTo; d++) {
                int x0 = xOfDay(d), x1 = xOfDay(d + 1);
                SelectObject(hdc, pGrid); MoveToEx(hdc, x1, area.top + tier, nullptr); LineTo(hdc, x1, bottom);
                int y; unsigned m, dd; mpp::civilFromDays(d, y, m, dd);
                RECT r = { x0, area.top + tier, x1, area.top + headerH };
                SetTextColor(hdc, mpp::dowOfUnixDay(d) >= 5 ? T.muted : T.ink);
                drawText(hdc, pxPerDay >= 34 ? std::wstring(DOW_S[mpp::dowOfUnixDay(d)]) + L" " + std::to_wstring(dd) : std::to_wstring(dd), r);
            }
        } else if (mode == 1) {
            for (int64_t d = dFrom - mpp::dowOfUnixDay(dFrom); d <= dTo; d += 7) {
                int x0 = xOfDay(d), x1 = xOfDay(d + 7);
                SelectObject(hdc, pGrid); MoveToEx(hdc, x0, area.top + tier, nullptr); LineTo(hdc, x0, bottom);
                int y; unsigned m, dd; mpp::civilFromDays(d, y, m, dd);
                RECT r = { x0, area.top + tier, x1, area.top + headerH };
                SetTextColor(hdc, T.ink);
                if (x1 - x0 > S(24)) drawText(hdc, std::to_wstring(dd) + L"." + std::to_wstring(m) + L".", r);
            }
        } else {
            int y = y0, m = mo0;
            while (true) {
                int64_t s = mpp::daysFromCivil(y, (unsigned)m, 1);
                int ny = y, nm = m + 1; if (nm > 12) { nm = 1; ny++; }
                int64_t e = mpp::daysFromCivil(ny, (unsigned)nm, 1);
                if (s > dTo) break;
                int x0 = xOfDay(s), x1 = xOfDay(e);
                SelectObject(hdc, pGrid); MoveToEx(hdc, x0, area.top + tier, nullptr); LineTo(hdc, x0, bottom);
                RECT r = { x0, area.top + tier, x1, area.top + headerH };
                SetTextColor(hdc, T.ink);
                if (x1 - x0 > S(18)) drawText(hdc, MONTHS_S[m - 1], r);
                y = ny; m = nm;
            }
        }
        SelectObject(hdc, pGridDark); MoveToEx(hdc, area.left, area.top + headerH - 1, nullptr); LineTo(hdc, w, area.top + headerH - 1); MoveToEx(hdc, area.left, area.top + tier, nullptr); LineTo(hdc, w, area.top + tier);

        HBRUSH bTask = CreateSolidBrush(T.task), bDone = CreateSolidBrush(T.done), bCrit = CreateSolidBrush(T.crit), bCritDone = CreateSolidBrush(T.critDone), bSum = CreateSolidBrush(T.sum),
               bBase = CreateSolidBrush(T.base), bMile = CreateSolidBrush(T.mile), bInactive = CreateSolidBrush(T.inactive), bDeadline = CreateSolidBrush(T.ok), bConstraint = CreateSolidBrush(T.lateStart);
        HPEN pLate = CreatePen(PS_SOLID, S(2), T.late), pNull = (HPEN)GetStockObject(NULL_PEN), pBar = CreatePen(PS_SOLID, 1, T.done), pCrit = CreatePen(PS_SOLID, 1, T.critDone);
        const int barH = imax(6, rowH - S(8));
        if (barRects) barRects->assign(g_rows.size(), RECT{ 0, 0, 0, 0 });
        int bi = g_baselineIdx < 0 || g_baselineIdx > 10 ? 0 : g_baselineIdx;
        for (int r = firstRow; r < firstRow + rowCount && r < (int)g_rows.size(); r++) {
            const mpp::Task& t = g_prj.tasks[g_rows[r]];
            if (t.isNull || !t.start) continue;
            int yc = yOf(r) + rowH / 2;
            if (yc > bottom) break;
            int x0 = xOf(t.start), x1 = xOf(t.finish ? t.finish : t.start);
            if (x1 < x0) x1 = x0;
            if (barRects) (*barRects)[r] = RECT{ x0 - S(4), yc - barH / 2, x1 + S(4), yc + barH / 2 };
            const mpp::Baseline& bl = t.baselines[bi];
            if (g_showBaseline && bl.start && bl.finish && !t.summary) { RECT br = { xOf(bl.start), yc + barH / 2 - 1, imax(xOf(bl.finish), xOf(bl.start) + 1), yc + barH / 2 + S(2) }; FillRect(hdc, &br, bBase); }
            COLORREF cc; bool colored = barColor(t, g_rows[r], cc);
            HBRUSH bCustom = colored ? CreateSolidBrush(cc) : nullptr, bCustomLight = colored ? CreateSolidBrush(lighter(cc)) : nullptr;
            if (t.milestone) {
                int s = imax(4, barH / 2);
                POINT pts[4] = { { x0, yc - s }, { x0 + s, yc }, { x0, yc + s }, { x0 - s, yc } };
                SelectObject(hdc, isLate(t) ? pLate : (t.critical ? pCrit : pNull)); SelectObject(hdc, t.pctComplete >= 100 ? bMile : (colored ? bCustom : (t.critical ? bCrit : (t.active ? bTask : bInactive))));
                Polygon(hdc, pts, 4);
            } else if (t.summary) {
                int hh = imax(3, barH / 3);
                RECT sr = { x0, yc - hh / 2 - 1, imax(x1, x0 + 2), yc + hh / 2 };
                FillRect(hdc, &sr, bSum);
                POINT a[3] = { { x0, yc + hh / 2 - 1 }, { x0 + hh + 2, yc + hh / 2 - 1 }, { x0, yc + hh + 2 } };
                POINT b[3] = { { x1, yc + hh / 2 - 1 }, { x1 - hh - 2, yc + hh / 2 - 1 }, { x1, yc + hh + 2 } };
                SelectObject(hdc, pNull); SelectObject(hdc, bSum); Polygon(hdc, a, 3); Polygon(hdc, b, 3);
            } else {
                RECT tr = { x0, yc - barH / 2, imax(x1, x0 + 2), yc + barH / 2 };
                FillRect(hdc, &tr, !t.active ? bInactive : colored ? bCustomLight : (t.critical ? bCrit : bTask));
                if (t.pctComplete > 0 && t.active) {
                    RECT pr = tr; pr.right = tr.left + (int)((tr.right - tr.left) * imin(100, t.pctComplete) / 100.0);
                    pr.top += barH / 3; pr.bottom -= barH / 3;
                    FillRect(hdc, &pr, colored ? bCustom : (t.critical ? bCritDone : bDone));
                }
                SelectObject(hdc, isLate(t) ? pLate : (t.critical ? pCrit : pBar)); SelectObject(hdc, GetStockObject(NULL_BRUSH));
                Rectangle(hdc, tr.left, tr.top, tr.right + 1, tr.bottom + 1);
                if (t.deadline) { int xd = xOf(t.deadline); POINT dp[3] = { { xd - S(5), tr.top - 1 }, { xd + S(5), tr.top - 1 }, { xd, tr.top + S(5) } }; SelectObject(hdc, pNull); SelectObject(hdc, bDeadline); Polygon(hdc, dp, 3); }
                if (t.constraintDate && t.constraintType >= 2) { int xd = xOf(t.constraintDate); POINT cp[3] = { { xd - S(4), tr.bottom + 1 }, { xd + S(4), tr.bottom + 1 }, { xd, tr.bottom - S(4) } }; SelectObject(hdc, pNull); SelectObject(hdc, bConstraint); Polygon(hdc, cp, 3); }
            }
            if (bCustom) DeleteObject(bCustom); if (bCustomLight) DeleteObject(bCustomLight);
        }
        if (links) {
            int selUid = (selRow >= 0 && selRow < (int)g_rows.size()) ? g_prj.tasks[g_rows[selRow]].uid : -1;
            HPEN pLink = CreatePen(PS_SOLID, 1, T.link), pLinkSel = CreatePen(PS_SOLID, S(2), T.linkSel);
            HBRUSH bLink = CreateSolidBrush(T.link), bLinkSel = CreateSolidBrush(T.linkSel);
            for (int pass = 0; pass < 2; pass++)
            for (int r = firstRow; r < firstRow + rowCount && r < (int)g_rows.size(); r++) {
                const mpp::Task& s = g_prj.tasks[g_rows[r]];
                if (s.isNull || !s.start) continue;
                int ys = yOf(r) + rowH / 2;
                if (ys > bottom + rowH * 50) break;
                for (const mpp::Relation& rel : s.preds) {
                    auto it = g_prj.uidIndex.find(rel.predUid);
                    if (it == g_prj.uidIndex.end()) continue;
                    int pr = g_rowOfTask[it->second];
                    if (pr < 0) continue;
                    const mpp::Task& p = g_prj.tasks[it->second];
                    if (!p.start) continue;
                    bool sel = highlightSel && (s.uid == selUid || p.uid == selUid);
                    if ((pass == 0) == sel) continue;
                    int yp = yOf(pr) + rowH / 2;
                    if (yp < area.top - rowH * 50 || yp > h + rowH * 50) continue;
                    bool fromFinish = rel.type == 1 || rel.type == 0;
                    bool toStart = rel.type == 1 || rel.type == 3;
                    int xa = fromFinish ? xOf(p.finish ? p.finish : p.start) : xOf(p.start);
                    int xb = toStart ? xOf(s.start) : xOf(s.finish ? s.finish : s.start);
                    int xm = toStart ? xb - S(6) : xb + S(6);
                    if (fromFinish && toStart && xa + S(4) > xm) xm = xa + S(4);
                    SelectObject(hdc, sel ? pLinkSel : pLink); SelectObject(hdc, sel ? bLinkSel : bLink);
                    POINT line[4] = { { xa, yp }, { xm, yp }, { xm, ys }, { xb, ys } };
                    Polyline(hdc, line, 4);
                    int dir = xb >= xm ? 1 : -1;
                    POINT ah[3] = { { xb, ys }, { xb - dir * S(5), ys - S(3) }, { xb - dir * S(5), ys + S(3) } };
                    Polygon(hdc, ah, 3);
                }
            }
            SelectObject(hdc, GetStockObject(WHITE_BRUSH)); DeleteObject(pLink); DeleteObject(pLinkSel); DeleteObject(bLink); DeleteObject(bLinkSel);
        }
        if (progressLine && g_refDate && g_refDate >= mpp::fromUnixDays(day0) && g_refDate <= g_end) {
            std::vector<POINT> pts;
            int xr = xOf(g_refDate);
            pts.push_back({ xr, area.top + headerH });
            for (int r = firstRow; r < firstRow + rowCount && r < (int)g_rows.size(); r++) {
                const mpp::Task& t = g_prj.tasks[g_rows[r]];
                int yc = yOf(r) + rowH / 2; if (yc > bottom) break;
                int x = xr;
                if (!t.isNull && !t.summary && t.start && t.finish && t.start < g_refDate && t.pctComplete < 100) x = xOf(t.start + (Time)((t.finish - t.start) * (t.pctComplete < 0 ? 0 : t.pctComplete) / 100.0));
                pts.push_back({ x, yc });
            }
            pts.push_back({ xr, bottom });
            HPEN pProg = CreatePen(PS_SOLID, S(2), T.late); SelectObject(hdc, pProg);
            Polyline(hdc, pts.data(), (int)pts.size());
            SelectObject(hdc, pGrid); DeleteObject(pProg);
        }
        auto vline = [&](Time t, COLORREF c, const wchar_t* label) {
            int x = xOf(t);
            if (x < area.left || x > w) return;
            HPEN p = CreatePen(PS_DOT, 1, c); SelectObject(hdc, p);
            MoveToEx(hdc, x, area.top + headerH, nullptr); LineTo(hdc, x, bottom);
            SetTextColor(hdc, c); RECT r = { x + 3, area.top + headerH + 1, x + S(120), area.top + headerH + S(15) }; drawText(hdc, label, r, DT_LEFT);
            SelectObject(hdc, pGrid); DeleteObject(p);
        };
        if (g_prj.statusDate) vline(g_prj.statusDate, T.status, L"stav k");
        vline(g_today, T.today, L"dnes");
        if (legendH) { // legenda obarvení
            RECT lr = { area.left, h - legendH, w, h }; FillRect(hdc, &lr, bHeader);
            SelectObject(hdc, pGridDark); MoveToEx(hdc, area.left, h - legendH, nullptr); LineTo(hdc, w, h - legendH);
            int x = area.left + S(8); int ci = colIndex(g_colorKey);
            std::wstring title = ci >= 0 ? g_cols[ci].title + L":" : L"";
            SetTextColor(hdc, T.muted); RECT tr = { x, h - legendH, x + S(300), h }; drawText(hdc, title, tr, DT_LEFT);
            SIZE sz; GetTextExtentPoint32W(hdc, title.c_str(), (int)title.size(), &sz); x += sz.cx + S(10);
            SetTextColor(hdc, T.ink);
            for (auto& e : g_legend) {
                HBRUSH b = CreateSolidBrush(e.second); RECT sw = { x, h - legendH + S(6), x + S(12), h - S(6) }; FillRect(hdc, &sw, b); DeleteObject(b);
                x += S(16); GetTextExtentPoint32W(hdc, e.first.c_str(), (int)e.first.size(), &sz);
                RECT r = { x, h - legendH, x + sz.cx + 2, h }; drawText(hdc, e.first, r, DT_LEFT); x += sz.cx + S(14);
                if (x > w) break;
            }
        }
        HGDIOBJ objs[] = { bWeekend, bHoliday, bHeader, bSel, pGrid, pGridDark, bTask, bDone, bCrit, bCritDone, bSum, bBase, bMile, bInactive, bDeadline, bConstraint, pLate, pBar, pCrit };
        for (HGDIOBJ o : objs) DeleteObject(o);
        SelectClipRgn(hdc, nullptr);
    }
};

static void paintStart(HDC hdc, int w, int h) {
    g_startRects.clear();
    SetTextColor(hdc, T.ink); SelectObject(hdc, g_fontBold);
    RECT r = { S(40), S(30), w - S(40), S(60) }; drawText(hdc, std::wstring(APP_TITLE) + L" " + APP_VERSION, r, DT_LEFT);
    SelectObject(hdc, g_font); SetTextColor(hdc, T.muted);
    r = { S(40), S(60), w - S(40), S(84) }; drawText(hdc, L"Otevřete soubor .mpp / .xml (Ctrl+O) nebo jej přetáhněte do okna.", r, DT_LEFT);
    r = { S(40), S(84), w - S(40), S(108) }; SetTextColor(hdc, T.linkSel); drawText(hdc, L"Otevřít soubor…", r, DT_LEFT); g_startRects.push_back({ r, -1 });
    if (!g_recent.empty()) {
        SetTextColor(hdc, T.muted); r = { S(40), S(130), w - S(40), S(154) }; drawText(hdc, L"Naposledy otevřené:", r, DT_LEFT);
        int y = S(156);
        for (size_t i = 0; i < g_recent.size(); i++) {
            RECT rr = { S(40), y, w - S(40), y + S(24) };
            SetTextColor(hdc, T.linkSel); drawText(hdc, fileName(g_recent[i]), rr, DT_LEFT);
            SIZE sz; std::wstring fn = fileName(g_recent[i]); GetTextExtentPoint32W(hdc, fn.c_str(), (int)fn.size(), &sz);
            RECT rp = { S(40) + sz.cx + S(12), y, w - S(40), y + S(24) }; SetTextColor(hdc, T.muted); SelectObject(hdc, g_fontSmall); drawText(hdc, g_recent[i], rp, DT_LEFT | DT_END_ELLIPSIS); SelectObject(hdc, g_font);
            g_startRects.push_back({ rr, (int)i }); y += S(24);
        }
    }
}
static void paintGantt(HDC hdc, int w, int h) {
    RECT full = { 0, 0, w, h };
    HBRUSH bg = CreateSolidBrush(T.bg); FillRect(hdc, &full, bg); DeleteObject(bg);
    SelectObject(hdc, g_font); SetBkMode(hdc, TRANSPARENT);
    if (!g_loaded) { paintStart(hdc, w, h); return; }
    GanttPaint gp;
    gp.hdc = hdc; gp.area = full; gp.headerH = g_headerH; gp.rowH = imax(1, g_rowH);
    gp.firstRow = ListView_GetTopIndex(g_list); gp.rowCount = (h - g_headerH) / gp.rowH + 2;
    gp.pxPerDay = g_pxPerDay; gp.day0 = mpp::toUnixDays(g_origin); gp.hscroll = g_hscroll; gp.scale = g_dpi;
    gp.progressLine = g_progressLine; gp.links = g_showLinks; gp.highlightSel = true; gp.weekends = true; gp.legend = true; gp.selRow = g_selRow; gp.barRects = &g_barRects;
    gp.run();
}

// Tisková / exportní sestava: tabulka (zobrazené sloupce) + Gantt pro řádky [r0, r1), celý projekt na šířku
static void renderReport(HDC hdc, RECT area, int r0, int r1, int page, int pages) {
    int dpi = GetDeviceCaps(hdc, LOGPIXELSX);
    auto P = [&](int px) { return MulDiv(px, dpi, 96); };
    LOGFONTW lf = {}; lf.lfHeight = -MulDiv(8, dpi, 72); wcscpy(lf.lfFaceName, L"Segoe UI"); lf.lfCharSet = DEFAULT_CHARSET;
    HFONT f = CreateFontIndirectW(&lf); lf.lfWeight = FW_BOLD; HFONT fb = CreateFontIndirectW(&lf);
    HGDIOBJ oldF = SelectObject(hdc, f);
    SetBkMode(hdc, TRANSPARENT); SetTextColor(hdc, RGB(0, 0, 0));
    Theme saved = T; bool savedDark = g_dark; g_dark = false; applyThemeColors(); // tisk vždy světle
    int titleH = P(22), rowH = P(18), headerH = P(30);
    std::wstring title = fileName(g_file) + (g_prj.title.empty() ? L"" : L" – " + g_prj.title) + L"   |   " + mpp::fmtDate(g_prj.startDate) + L" – " + mpp::fmtDate(g_prj.finishDate) + L"   |   stav k " + mpp::fmtDate(g_refDate) + (pages > 1 ? L"   |   strana " + std::to_wstring(page) + L"/" + std::to_wstring(pages) : L"");
    RECT tr = { area.left, area.top, area.right, area.top + titleH }; SelectObject(hdc, fb); drawText(hdc, title, tr, DT_LEFT); SelectObject(hdc, f);
    int top = area.top + titleH + P(4);
    // sloupce: zobrazené, celkem max 45 % šířky
    std::vector<std::pair<int, int>> cols; int tableW = 0, maxW = (area.right - area.left) * 45 / 100;
    for (int ci : g_visible) { int w = P(imin(g_cols[ci].width, 220)); if (tableW + w + P(4) > maxW) break; cols.push_back({ ci, w }); tableW += w + P(4); }
    HPEN pGrid = CreatePen(PS_SOLID, 1, RGB(200, 200, 200)); SelectObject(hdc, pGrid);
    { int x = area.left; SelectObject(hdc, fb); for (auto& c : cols) { RECT r = { x + P(2), top, x + c.second, top + headerH }; drawText(hdc, g_cols[c.first].title, r, DT_LEFT | DT_END_ELLIPSIS); x += c.second + P(4); } SelectObject(hdc, f); }
    for (int r = r0; r < r1; r++) {
        const mpp::Task& t = g_prj.tasks[g_rows[r]];
        int y = top + headerH + (r - r0) * rowH, x = area.left;
        MoveToEx(hdc, area.left, y + rowH, nullptr); LineTo(hdc, area.left + tableW, y + rowH);
        SelectObject(hdc, t.summary ? fb : f);
        SetTextColor(hdc, isLate(t) ? RGB(200, 0, 0) : RGB(0, 0, 0));
        for (auto& c : cols) {
            std::wstring s = g_cols[c.first].key == L"name" ? std::wstring((size_t)imax(0, t.level - 1) * 2, L' ') + (t.milestone ? L"◆ " : L"") + t.name : cellText(t, g_rows[r], c.first);
            RECT rc = { x + P(2), y, x + c.second, y + rowH }; drawText(hdc, s, rc, (g_cols[c.first].right ? DT_RIGHT : DT_LEFT) | DT_END_ELLIPSIS); x += c.second + P(4);
        }
    }
    SetTextColor(hdc, RGB(0, 0, 0));
    GanttPaint gp;
    gp.hdc = hdc; gp.area = { area.left + tableW + P(6), top, area.right, area.bottom };
    gp.headerH = headerH; gp.rowH = rowH; gp.firstRow = r0; gp.rowCount = r1 - r0;
    double days = (double)(mpp::toUnixDays(g_end) - mpp::toUnixDays(g_origin));
    gp.pxPerDay = days > 0 ? (gp.area.right - gp.area.left - P(4)) / days : 10; gp.day0 = mpp::toUnixDays(g_origin); gp.hscroll = 0; gp.scale = dpi;
    gp.progressLine = g_progressLine; gp.links = g_showLinks; gp.highlightSel = false; gp.weekends = gp.pxPerDay >= 3.5; gp.legend = true; gp.selRow = -1; gp.barRects = nullptr;
    gp.run();
    g_dark = savedDark; T = saved;
    SelectObject(hdc, oldF); DeleteObject(f); DeleteObject(fb); DeleteObject(pGrid);
}
