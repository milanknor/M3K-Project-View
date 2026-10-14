// ui.h - společný stav aplikace, motiv (světlý/tmavý), písma, nastavení (INI), jednoduché modální dialogy, pomocné funkce
#pragma once
#define NOMINMAX
#define _CRT_SECURE_NO_WARNINGS
#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <commctrl.h>
#include <commdlg.h>
#include <shellapi.h>
#include <shlobj.h>
#include <windowsx.h>
#include <objidl.h>
#include <gdiplus.h>
#include <dwmapi.h>
#include <uxtheme.h>
#include <functional>
#include <climits>
#include "mppload.h" // model + načítání přes mppread.dll (čtečka je samostatná knihovna pod LGPL)

#pragma comment(lib, "comctl32.lib")
#pragma comment(lib, "user32.lib")
#pragma comment(lib, "gdi32.lib")
#pragma comment(lib, "comdlg32.lib")
#pragma comment(lib, "shell32.lib")
#pragma comment(lib, "advapi32.lib")
#pragma comment(lib, "ole32.lib")
#pragma comment(lib, "gdiplus.lib")
#pragma comment(lib, "dwmapi.lib")
#pragma comment(lib, "uxtheme.lib")
#pragma comment(linker, "\"/manifestdependency:type='win32' name='Microsoft.Windows.Common-Controls' version='6.0.0.0' processorArchitecture='*' publicKeyToken='6595b64144ccf1df' language='*'\"")

using mpp::Time;
static int imin(int a, int b) { return a < b ? a : b; }
static int imax(int a, int b) { return a > b ? a : b; }
static const wchar_t* APP_TITLE = L"M3K Project View";
static const wchar_t* APP_AUTHOR = L"Milan Knor (M3K Soft), https://m3ksoft.cz";
static const wchar_t* APP_VERSION = L"1.0.8";

// ---------------- globální stav ----------------
static HINSTANCE g_inst;
static HWND g_main, g_list, g_gantt, g_status, g_search, g_filter, g_rscCombo, g_scale, g_summary, g_detail, g_tip, g_tabs;
static HMENU g_menuFile, g_menuView, g_menuViews, g_menuColor, g_menuBaseline, g_menuCols;
static HFONT g_font, g_fontBold, g_fontSmall;
static int g_fontPt = 9;
static mpp::Project g_prj;
static bool g_loaded = false;
static std::wstring g_file;
static FILETIME g_fileTime = {};
static std::vector<int> g_rows, g_rowOfTask, g_var;
static std::vector<char> g_keep;
static std::map<int, std::vector<int>> g_succ;
static int g_splitX = -1, g_dpi = 96, g_headerH = 24, g_rowH = 20, g_hscroll = 0, g_selRow = -1, g_tipTask = -1, g_tab = 0, g_baselineIdx = 0;
static bool g_dragSplit = false, g_detailVisible = true, g_progressLine = true, g_tracking = false, g_dark = false, g_holidays = true, g_reloadAsked = false;
static bool g_showLinks = true, g_showBaseline = true; // lišta: Vazby / Základní plán
static double g_pxPerDay = 20;
static Time g_origin = 0, g_end = 0, g_today = 0, g_refDate = 0;
static std::vector<RECT> g_barRects;
static std::vector<std::wstring> g_recent;
static std::wstring g_iniPath;
static std::wstring g_sortKey; static int g_sortDir = 0; // 1 vzestupně, -1 sestupně, 0 podle souboru
static std::wstring g_colorKey; // klíč sloupce pro obarvení pruhů ("" = vypnuto)
static std::vector<std::pair<std::wstring, COLORREF>> g_legend;
static std::map<std::wstring, COLORREF> g_colorMap;
static const int SPLIT_W = 6, MAX_RECENT = 8;

static int S(int px) { return MulDiv(px, g_dpi, 96); }

// ---------------- motiv ----------------
struct Theme {
    COLORREF bg, ink, muted, grid, gridStrong, headerBg, sel, weekend, holiday, task, done, crit, critDone, sum, base, mile, inactive, late, link, linkSel, today, status, tipBg, panel, lateStart, ok, over, overInk, workLight;
};
static Theme T;
static void applyThemeColors() {
    if (!g_dark) T = { RGB(255,255,255), RGB(0,0,0), RGB(110,110,110), RGB(228,228,228), RGB(200,200,200), RGB(248,248,248), RGB(214,230,255), RGB(242,242,242), RGB(255,236,214),
        RGB(84,140,214), RGB(24,70,150), RGB(225,70,70), RGB(150,20,20), RGB(60,60,60), RGB(170,170,170), RGB(20,20,20), RGB(200,200,200), RGB(210,30,30), RGB(90,90,90), RGB(0,90,220), RGB(210,30,30), RGB(0,90,200), RGB(255,255,225), RGB(240,240,240), RGB(200,110,0), RGB(0,130,0), RGB(254,202,202), RGB(153,27,27), RGB(255,237,213) };
    else T = { RGB(17,17,21), RGB(230,230,230), RGB(150,150,160), RGB(45,45,52), RGB(70,70,80), RGB(30,30,36), RGB(50,70,110), RGB(26,26,32), RGB(60,45,30),
        RGB(255,121,10), RGB(190,80,0), RGB(230,80,80), RGB(160,30,30), RGB(200,200,200), RGB(110,110,120), RGB(240,240,240), RGB(90,90,100), RGB(255,90,90), RGB(160,160,170), RGB(100,170,255), RGB(255,90,90), RGB(100,170,255), RGB(45,45,55), RGB(24,24,30), RGB(255,170,60), RGB(80,200,120), RGB(120,40,40), RGB(255,180,180), RGB(50,45,35) };
}
static void drawText(HDC hdc, const std::wstring& s, RECT rc, UINT fmt = DT_CENTER) { DrawTextW(hdc, s.c_str(), (int)s.size(), &rc, fmt | DT_VCENTER | DT_SINGLELINE | DT_NOPREFIX); }
static std::wstring lower(std::wstring s) { for (auto& c : s) c = (wchar_t)towlower(c); return s; }
static int collate(const std::wstring& a, const std::wstring& b) { return CompareStringEx(LOCALE_NAME_USER_DEFAULT, LINGUISTIC_IGNORECASE, a.c_str(), (int)a.size(), b.c_str(), (int)b.size(), nullptr, nullptr, 0) - 2; }
static std::wstring baseName() { std::wstring n = g_file.substr(g_file.find_last_of(L"\\/") + 1); size_t d = n.find_last_of(L'.'); return d == std::wstring::npos ? n : n.substr(0, d); }
static std::wstring fileName(const std::wstring& p) { return p.substr(p.find_last_of(L"\\/") + 1); }
static bool g_batch = false; // --csv/--png/...: žádná okna, jen exit kód
static void msgError(const std::wstring& s) { if (g_batch) { fputs((mpp::wideToUtf8(s) + "\n").c_str(), stderr); return; } MessageBoxW(g_main, s.c_str(), APP_TITLE, MB_ICONERROR); }

// C++ výjimka nesmí projít přes rámce Windows (nedefinované chování = pád programu), proto každou
// proceduru okna obalí safeProc: chybu ohlásí a zprávu dokončí výchozí obsluha. Hlášení se nevnořuje,
// aby chyba při kreslení nevyvolala řetěz oken.
static void reportUnexpected(const char* what) {
    static bool showing = false;
    if (showing) return;
    showing = true;
    msgError(L"Neočekávaná chyba programu" + (what ? L":\n" + mpp::ansiToWide(what, CP_UTF8) : std::wstring(L".")) +
             L"\n\nOtevřený soubor se nezměnil; pokud se chyba opakuje, napište prosím na info@m3ksound.cz.");
    showing = false;
}
template<WNDPROC P> LRESULT CALLBACK safeProc(HWND h, UINT m, WPARAM w, LPARAM l) {
    try { return P(h, m, w, l); }
    catch (const std::exception& e) { reportUnexpected(e.what()); }
    catch (...) { reportUnexpected(nullptr); }
    return DefWindowProcW(h, m, w, l);
}
static std::vector<std::wstring> split(const std::wstring& s, wchar_t sep) { std::vector<std::wstring> v; size_t p = 0; while (true) { size_t e = s.find(sep, p); v.push_back(s.substr(p, e == std::wstring::npos ? std::wstring::npos : e - p)); if (e == std::wstring::npos) break; p = e + 1; } return v; }
static std::wstring join(const std::vector<std::wstring>& v, const wchar_t* sep) { std::wstring s; for (size_t i = 0; i < v.size(); i++) { if (i) s += sep; s += v[i]; } return s; }

// ---------------- INI ----------------
static void iniSet(const wchar_t* key, const std::wstring& v) { WritePrivateProfileStringW(L"MppView", key, v.c_str(), g_iniPath.c_str()); }
static std::wstring iniGet(const wchar_t* key, const wchar_t* def = L"") { wchar_t b[4096]; GetPrivateProfileStringW(L"MppView", key, def, b, 4096, g_iniPath.c_str()); return b; }
static int iniInt(const wchar_t* key, int def) { return (int)GetPrivateProfileIntW(L"MppView", key, def, g_iniPath.c_str()); }
static void iniSetSection(const wchar_t* section, const wchar_t* key, const std::wstring& v) { WritePrivateProfileStringW(section, key, v.empty() ? nullptr : v.c_str(), g_iniPath.c_str()); }
static std::vector<std::pair<std::wstring, std::wstring>> iniSection(const wchar_t* section) {
    std::vector<std::pair<std::wstring, std::wstring>> out; std::vector<wchar_t> buf(65536);
    DWORD n = GetPrivateProfileSectionW(section, buf.data(), 65536, g_iniPath.c_str());
    for (size_t i = 0; i < n;) { std::wstring line = buf.data() + i; i += line.size() + 1; if (line.empty()) break; size_t eq = line.find(L'='); if (eq != std::wstring::npos) out.push_back({ line.substr(0, eq), line.substr(eq + 1) }); }
    return out;
}

// ---------------- písma ----------------
static void createFonts() {
    if (g_font) DeleteObject(g_font); if (g_fontBold) DeleteObject(g_fontBold); if (g_fontSmall) DeleteObject(g_fontSmall);
    NONCLIENTMETRICSW ncm = { sizeof(ncm) }; SystemParametersInfoW(SPI_GETNONCLIENTMETRICS, sizeof(ncm), &ncm, 0);
    LOGFONTW lf = ncm.lfMessageFont;
    lf.lfHeight = -MulDiv(g_fontPt, g_dpi, 72); g_font = CreateFontIndirectW(&lf);
    lf.lfWeight = FW_BOLD; g_fontBold = CreateFontIndirectW(&lf);
    lf.lfWeight = FW_NORMAL; lf.lfHeight = -MulDiv(imax(7, g_fontPt - 1), g_dpi, 72); g_fontSmall = CreateFontIndirectW(&lf);
}

// ---------------- jednoduché modální dialogy ----------------
struct Dlg {
    HWND h = nullptr; bool done = false; int result = 0;
    std::function<void(int id, int code, HWND ctl)> onCmd;
    std::function<LRESULT(NMHDR*)> onNotify;
    std::function<void(int w, int h)> onSize;
    std::function<void()> onDestroy;
};
static std::map<HWND, Dlg*> g_dlgs;
static LRESULT CALLBACK DlgProc(HWND hwnd, UINT msg, WPARAM wp, LPARAM lp) {
    auto it = g_dlgs.find(hwnd); Dlg* d = it == g_dlgs.end() ? nullptr : it->second;
    switch (msg) {
    case WM_COMMAND: if (d && d->onCmd) d->onCmd(LOWORD(wp), HIWORD(wp), (HWND)lp); return 0;
    case WM_NOTIFY: if (d && d->onNotify) return d->onNotify((NMHDR*)lp); return 0;
    case WM_SIZE: if (d && d->onSize) d->onSize(LOWORD(lp), HIWORD(lp)); return 0;
    case WM_CLOSE: if (d) { d->done = true; d->result = 0; } return 0;
    case WM_CTLCOLORSTATIC: case WM_CTLCOLORDLG: case WM_CTLCOLOREDIT: case WM_CTLCOLORLISTBOX: {
        HDC hdc = (HDC)wp; SetTextColor(hdc, T.ink); SetBkColor(hdc, msg == WM_CTLCOLOREDIT || msg == WM_CTLCOLORLISTBOX ? T.bg : T.panel);
        static HBRUSH bp = nullptr, be = nullptr; if (bp) DeleteObject(bp); if (be) DeleteObject(be); bp = CreateSolidBrush(T.panel); be = CreateSolidBrush(T.bg);
        return (LRESULT)(msg == WM_CTLCOLOREDIT || msg == WM_CTLCOLORLISTBOX ? be : bp);
    }
    case WM_ERASEBKGND: { RECT rc; GetClientRect(hwnd, &rc); HBRUSH b = CreateSolidBrush(T.panel); FillRect((HDC)wp, &rc, b); DeleteObject(b); return 1; }
    }
    return DefWindowProcW(hwnd, msg, wp, lp);
}
static void registerDlgClass() { WNDCLASSW wc = {}; wc.lpfnWndProc = safeProc<DlgProc>; wc.hInstance = g_inst; wc.lpszClassName = L"MppDlg"; wc.hCursor = LoadCursor(nullptr, IDC_ARROW); RegisterClassW(&wc); }
static HWND dlgCreate(Dlg& d, const wchar_t* title, int w, int h, bool resizable = false) {
    RECT mr; GetWindowRect(g_main, &mr);
    int W = S(w), H = S(h);
    DWORD style = WS_POPUP | WS_CAPTION | WS_SYSMENU | (resizable ? WS_THICKFRAME | WS_MAXIMIZEBOX : 0);
    RECT r = { 0, 0, W, H }; AdjustWindowRect(&r, style, FALSE);
    d.h = CreateWindowExW(WS_EX_DLGMODALFRAME, L"MppDlg", title, style, mr.left + ((mr.right - mr.left) - (r.right - r.left)) / 2, mr.top + ((mr.bottom - mr.top) - (r.bottom - r.top)) / 2, r.right - r.left, r.bottom - r.top, g_main, nullptr, g_inst, nullptr);
    g_dlgs[d.h] = &d;
    if (g_dark) { BOOL on = TRUE; DwmSetWindowAttribute(d.h, 20, &on, sizeof(on)); }
    return d.h;
}
static HWND dlgCtl(HWND parent, const wchar_t* cls, const wchar_t* text, DWORD style, int x, int y, int w, int h, int id, DWORD ex = 0) {
    HWND c = CreateWindowExW(ex, cls, text, WS_CHILD | WS_VISIBLE | style, S(x), S(y), S(w), S(h), parent, (HMENU)(INT_PTR)id, g_inst, nullptr);
    SendMessage(c, WM_SETFONT, (WPARAM)g_font, TRUE);
    return c;
}
static HWND dlgLabel(HWND p, const wchar_t* t, int x, int y, int w, int h = 18) { return dlgCtl(p, L"STATIC", t, SS_LEFT, x, y, w, h, -1); }
static HWND dlgEdit(HWND p, const wchar_t* t, int x, int y, int w, int id, DWORD extra = 0) { return dlgCtl(p, L"EDIT", t, WS_TABSTOP | ES_AUTOHSCROLL | extra, x, y, w, 22, id, WS_EX_CLIENTEDGE); }
static HWND dlgButton(HWND p, const wchar_t* t, int x, int y, int w, int id, bool def = false) { return dlgCtl(p, L"BUTTON", t, WS_TABSTOP | (def ? BS_DEFPUSHBUTTON : BS_PUSHBUTTON), x, y, w, 24, id); }
static HWND dlgCheck(HWND p, const wchar_t* t, int x, int y, int w, int id) { return dlgCtl(p, L"BUTTON", t, WS_TABSTOP | BS_AUTOCHECKBOX, x, y, w, 20, id); }
static HWND dlgCombo(HWND p, int x, int y, int w, int id, bool editable = false) { return dlgCtl(p, L"COMBOBOX", L"", WS_TABSTOP | WS_VSCROLL | (editable ? CBS_DROPDOWN : CBS_DROPDOWNLIST), x, y, w, 300, id); }
static HWND dlgList(HWND p, int x, int y, int w, int h, int id, DWORD extra = 0) {
    HWND l = dlgCtl(p, WC_LISTVIEWW, L"", WS_TABSTOP | LVS_REPORT | LVS_SHOWSELALWAYS | extra, x, y, w, h, id, WS_EX_CLIENTEDGE);
    ListView_SetExtendedListViewStyle(l, LVS_EX_FULLROWSELECT | LVS_EX_GRIDLINES | LVS_EX_DOUBLEBUFFER);
    ListView_SetBkColor(l, T.bg); ListView_SetTextBkColor(l, T.bg); ListView_SetTextColor(l, T.ink);
    if (g_dark) SetWindowTheme(l, L"DarkMode_Explorer", nullptr);
    return l;
}
static void lvColumn(HWND l, int i, const wchar_t* title, int w, bool right = false) { LVCOLUMNW c = {}; c.mask = LVCF_TEXT | LVCF_WIDTH | LVCF_FMT; c.pszText = (LPWSTR)title; c.cx = S(w); c.fmt = right ? LVCFMT_RIGHT : LVCFMT_LEFT; ListView_InsertColumn(l, i, &c); }
static int lvAdd(HWND l, const std::vector<std::wstring>& cells, LPARAM data = 0) {
    LVITEMW it = {}; it.mask = LVIF_TEXT | LVIF_PARAM; it.iItem = ListView_GetItemCount(l); std::wstring s0 = cells.empty() ? L"" : cells[0]; it.pszText = &s0[0]; it.lParam = data;
    int idx = ListView_InsertItem(l, &it);
    for (size_t c = 1; c < cells.size(); c++) { std::wstring s = cells[c]; ListView_SetItemText(l, idx, (int)c, &s[0]); }
    return idx;
}
static LPARAM lvParam(HWND l, int i) { LVITEMW it = {}; it.mask = LVIF_PARAM; it.iItem = i; ListView_GetItem(l, &it); return it.lParam; }
static std::wstring ctlText(HWND c) { int n = GetWindowTextLengthW(c); std::wstring s((size_t)n + 1, 0); GetWindowTextW(c, &s[0], n + 1); s.resize((size_t)n); return s; }
static int dlgRun(Dlg& d) {
    ShowWindow(d.h, SW_SHOW); EnableWindow(g_main, FALSE);
    MSG m;
    while (!d.done && GetMessageW(&m, nullptr, 0, 0) > 0) {
        if (m.message == WM_KEYDOWN && m.wParam == VK_ESCAPE && (m.hwnd == d.h || IsChild(d.h, m.hwnd))) { d.done = true; d.result = 0; break; }
        if (IsDialogMessageW(d.h, &m)) continue;
        TranslateMessage(&m); DispatchMessageW(&m);
    }
    EnableWindow(g_main, TRUE); SetForegroundWindow(g_main);
    if (d.onDestroy) d.onDestroy();
    g_dlgs.erase(d.h); DestroyWindow(d.h); d.h = nullptr;
    return d.result;
}
// Jednoduchý dotaz na text (název pohledu / filtru)
static bool askText(const wchar_t* title, const wchar_t* label, std::wstring& value) {
    Dlg d; dlgCreate(d, title, 360, 110);
    dlgLabel(d.h, label, 12, 12, 330);
    HWND e = dlgEdit(d.h, value.c_str(), 12, 34, 330, 100);
    dlgButton(d.h, L"OK", 190, 70, 70, IDOK, true); dlgButton(d.h, L"Zrušit", 268, 70, 74, IDCANCEL);
    d.onCmd = [&](int id, int, HWND) { if (id == IDOK) { value = ctlText(e); d.result = 1; d.done = true; } else if (id == IDCANCEL) { d.done = true; } };
    SetFocus(e); SendMessage(e, EM_SETSEL, 0, -1);
    return dlgRun(d) == 1;
}
// Naplní buffer dialogu Uložit: zadanou cestu (dávkový režim) nebo navržený název souboru.
// Zadanou cestu delší než MAX_PATH odmítne, navržený název zkrátí (dřív wcscpy mohl buffer přetéct).
static bool fillPath(wchar_t (&buf)[MAX_PATH], const wchar_t* path, const std::wstring& suggested) {
    if (path && wcslen(path) >= MAX_PATH) return false;
    wcsncpy_s(buf, MAX_PATH, path ? path : suggested.c_str(), _TRUNCATE);
    return true;
}
static bool saveDialog(wchar_t* buf, const wchar_t* filter, const wchar_t* defExt, const wchar_t* title) {
    OPENFILENAMEW ofn = { sizeof(ofn) };
    ofn.hwndOwner = g_main; ofn.lpstrFile = buf; ofn.nMaxFile = MAX_PATH; ofn.lpstrFilter = filter; ofn.lpstrDefExt = defExt; ofn.lpstrTitle = title;
    ofn.Flags = OFN_OVERWRITEPROMPT | OFN_PATHMUSTEXIST;
    return GetSaveFileNameW(&ofn) != 0;
}
static bool openDialogPath(wchar_t* buf, const wchar_t* title) {
    buf[0] = 0;
    OPENFILENAMEW ofn = { sizeof(ofn) };
    ofn.hwndOwner = g_main; ofn.lpstrFile = buf; ofn.nMaxFile = MAX_PATH;
    ofn.lpstrFilter = L"MS Project (*.mpp;*.mpt;*.xml)\0*.mpp;*.mpt;*.xml\0Všechny soubory\0*.*\0";
    ofn.lpstrTitle = title; ofn.Flags = OFN_FILEMUSTEXIST | OFN_HIDEREADONLY;
    return GetOpenFileNameW(&ofn) != 0;
}
static void clipboardText(const std::wstring& s) {
    if (!OpenClipboard(g_main)) return;
    EmptyClipboard(); HGLOBAL h = GlobalAlloc(GMEM_MOVEABLE, (s.size() + 1) * 2);
    if (h) { // bez paměti nic nezkopíruje, ale nespadne; paměť uvolní, když ji schránka nepřevezme
        void* dst = GlobalLock(h);
        if (dst) { memcpy(dst, s.c_str(), (s.size() + 1) * 2); GlobalUnlock(h); }
        if (!dst || !SetClipboardData(CF_UNICODETEXT, h)) GlobalFree(h);
    }
    CloseClipboard();
}
static void writeUtf8(const wchar_t* path, const std::wstring& content, bool bom = true) {
    FILE* f = _wfopen(path, L"wb"); if (!f) throw std::runtime_error("Soubor nelze zapsat");
    if (bom) fwrite("\xEF\xBB\xBF", 1, 3, f);
    std::string u = mpp::wideToUtf8(content); bool ok = fwrite(u.data(), 1, u.size(), f) == u.size() && !ferror(f);
    if (fclose(f) != 0 || !ok) throw std::runtime_error("Soubor nelze zapsat (plny disk?)");
}
// Text začínající =, +, -, @ (nebo tabulátorem) by Excel po otevření CSV či vložení ze schránky mohl
// vyhodnotit jako vzorec – u harmonogramu od cizí firmy nebezpečné (CSV injection). Takový text dostane
// na začátek apostrof; čísla se znaménkem („+5“, „-3“) a hodnoty jako „+2 prac. dní“ zůstanou beze změny.
static std::wstring noFormula(const std::wstring& s) {
    if (s.empty() || !wcschr(L"=+-@\t\r", s[0])) return s;
    if ((s[0] == L'+' || s[0] == L'-') && s.size() > 1 && iswdigit(s[1])) {
        bool plain = true;
        for (size_t i = 1; i < s.size() && plain; i++) plain = iswdigit(s[i]) || IsCharAlphaW(s[i]) || wcschr(L" ,.% ", s[i]);
        if (plain) return s;
    }
    return L"'" + s;
}
// Buňka do schránky (oddělovač tabulátor): tabulátor ani konec řádku uvnitř textu nesmí rozbít tabulku.
static std::wstring tsvCell(std::wstring s) { for (wchar_t& c : s) if (c == L'\t' || c == L'\r' || c == L'\n') c = L' '; return noFormula(s); }
static std::wstring csvCell(std::wstring s) { s = noFormula(s); bool q =s.find_first_of(L";\"\n\r") != std::wstring::npos; if (!q) return s; std::wstring o = L"\""; for (wchar_t c : s) { if (c == L'"') o += L"\"\""; else o += c; } return o + L"\""; }
