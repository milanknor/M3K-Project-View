// exports.h - CSV, kopírování řádků, XLSX (vlastní zapisovač ZIP bez komprese), PNG, PDF (vlastní zapisovač, obrázek Flate z PNG), tisk
#pragma once
#include "usage.h"

// ---------------- CSV / schránka ----------------
static std::wstring rowsAsText(const std::vector<int>& rows, wchar_t sep, bool csv) {
    std::wstring o;
    auto cell = [&](const std::wstring& s) { return csv ? csvCell(s) : tsvCell(s); };
    for (size_t i = 0; i < g_visible.size(); i++) { if (i) o += sep; o += cell(g_cols[g_visible[i]].title); }
    if (csv) o += L";Úroveň;Kritický;Poznámky";
    o += L"\r\n";
    for (int r : rows) {
        const mpp::Task& t = g_prj.tasks[r];
        for (size_t i = 0; i < g_visible.size(); i++) { if (i) o += sep; o += cell(cellText(t, r, g_visible[i])); }
        if (csv) o += L";" + std::to_wstring(t.level) + L";" + (t.critical ? L"ano" : L"") + L";" + cell(t.notes);
        o += L"\r\n";
    }
    return o;
}
static bool exportCsv(const wchar_t* path = nullptr) {
    if (!g_loaded) return false;
    wchar_t buf[MAX_PATH]; if (!fillPath(buf, path, baseName() + L".csv")) return false;
    if (!path && !saveDialog(buf, L"CSV (oddělovač ;)\0*.csv\0", L"csv", L"Export tabulky do CSV (zobrazené řádky a sloupce)")) return false;
    try { writeUtf8(buf, rowsAsText(g_rows, L';', true)); } catch (const std::exception&) { if (!path) msgError(L"Soubor nelze zapsat."); return false; }
    return true;
}
static void copySelectedRows() {
    std::vector<int> rows; int i = -1;
    while ((i = ListView_GetNextItem(g_list, i, LVNI_SELECTED)) >= 0) if (i < (int)g_rows.size()) rows.push_back(g_rows[i]);
    if (rows.empty()) return;
    clipboardText(rowsAsText(rows, L'\t', false));
}

// ---------------- ZIP (bez komprese) + XLSX ----------------
static uint32_t crc32buf(const std::string& d) {
    static uint32_t tab[256]; static bool init = false;
    if (!init) { for (uint32_t i = 0; i < 256; i++) { uint32_t c = i; for (int k = 0; k < 8; k++) c = (c & 1) ? 0xEDB88320u ^ (c >> 1) : c >> 1; tab[i] = c; } init = true; }
    uint32_t c = 0xFFFFFFFFu; for (unsigned char b : d) c = tab[(c ^ b) & 0xFF] ^ (c >> 8); return c ^ 0xFFFFFFFFu;
}
struct ZipWriter {
    FILE* f; struct E { std::string name; uint32_t crc, size, off; }; std::vector<E> entries;
    ZipWriter(const wchar_t* path) { f = _wfopen(path, L"wb"); if (!f) throw std::runtime_error("Soubor nelze zapsat"); }
    ~ZipWriter() { if (f) fclose(f); } // při výjimce uprostřed zápisu soubor aspoň zavřít
    ZipWriter(const ZipWriter&) = delete; ZipWriter& operator=(const ZipWriter&) = delete;
    void put32(uint32_t v) { fputc(v & 255, f); fputc((v >> 8) & 255, f); fputc((v >> 16) & 255, f); fputc((v >> 24) & 255, f); }
    void put16(uint32_t v) { fputc(v & 255, f); fputc((v >> 8) & 255, f); }
    void add(const std::string& name, const std::string& data) {
        E e; e.name = name; e.crc = crc32buf(data); e.size = (uint32_t)data.size(); e.off = (uint32_t)ftell(f);
        put32(0x04034b50); put16(20); put16(0x0800); put16(0); put16(0); put16(0x21); put32(e.crc); put32(e.size); put32(e.size); put16((uint32_t)name.size()); put16(0);
        fwrite(name.data(), 1, name.size(), f); fwrite(data.data(), 1, data.size(), f);
        entries.push_back(e);
    }
    void close() {
        uint32_t cd = (uint32_t)ftell(f);
        for (E& e : entries) { put32(0x02014b50); put16(20); put16(20); put16(0x0800); put16(0); put16(0); put16(0x21); put32(e.crc); put32(e.size); put32(e.size); put16((uint32_t)e.name.size()); put16(0); put16(0); put16(0); put16(0); put32(0); put32(e.off); fwrite(e.name.data(), 1, e.name.size(), f); }
        uint32_t cdSize = (uint32_t)ftell(f) - cd;
        put32(0x06054b50); put16(0); put16(0); put16((uint32_t)entries.size()); put16((uint32_t)entries.size()); put32(cdSize); put32(cd); put16(0);
        bool ok = !ferror(f); if (fclose(f) != 0) ok = false; f = nullptr;
        if (!ok) throw std::runtime_error("Soubor nelze zapsat (plny disk?)");
    }
};
static std::string xmlEsc(const std::wstring& w) {
    std::string s = mpp::wideToUtf8(w), o; o.reserve(s.size() + 16);
    for (unsigned char c : s) { switch (c) { case '&': o += "&amp;"; break; case '<': o += "&lt;"; break; case '>': o += "&gt;"; break; case '"': o += "&quot;"; break; default: if (c >= 0x20 || c == '\t' || c == '\n' || c == '\r') o += (char)c; } }
    return o;
}
static std::string colName(int index) { std::string s; int n = index; while (n > 0) { int rem = (n - 1) % 26; s.insert(s.begin(), (char)('A' + rem)); n = (n - 1) / 26; } return s; }
struct Xlsx { // port XlsxWriter.java
    enum Style { TEXT = 0, HEADER = 1, BOLD = 2, DATE = 3, NUMBER = 4, MONEY = 5, PERCENT = 6 };
    ZipWriter zip; std::vector<std::string> sheets; std::string cur; int row = 0, col = 0, maxCol = 0; bool open = false;
    Xlsx(const wchar_t* path) : zip(path) {}
    void sheet(const std::wstring& name, const std::vector<int>& widths) {
        endSheet(); std::wstring n = name; for (auto& c : n) if (wcschr(L":\\/?*[]", c)) c = L' '; if (n.size() > 31) n.resize(31);
        sheets.push_back(xmlEsc(n)); cur = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetViews><sheetView workbookViewId=\"0\"><pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\" state=\"frozen\"/></sheetView></sheetViews>";
        if (!widths.empty()) { cur += "<cols>"; for (size_t i = 0; i < widths.size(); i++) cur += "<col min=\"" + std::to_string(i + 1) + "\" max=\"" + std::to_string(i + 1) + "\" width=\"" + std::to_string(imax(6, imin(80, widths[i] <= 0 ? 12 : widths[i]))) + "\" customWidth=\"1\"/>"; cur += "</cols>"; }
        cur += "<sheetData>"; open = true; row = 0; maxCol = 0;
    }
    void newRow() { if (row > 0) cur += "</row>"; row++; col = 0; cur += "<row r=\"" + std::to_string(row) + "\">"; }
    void cellOpen(Style s) { col++; maxCol = imax(maxCol, col); cur += "<c r=\"" + colName(col) + std::to_string(row) + "\" s=\"" + std::to_string((int)s) + "\""; }
    void text(const std::wstring& v, Style s = TEXT) { cellOpen(s); if (v.empty()) { cur += "/>"; return; } cur += " t=\"inlineStr\"><is><t xml:space=\"preserve\">" + xmlEsc(v.size() > 32767 ? v.substr(0, 32767) : v) + "</t></is></c>"; } // delší buňku Excel „opravuje“
    void number(double v, Style s = NUMBER) { cellOpen(s); if (std::isnan(v)) { cur += "/>"; return; } char b[40]; if (v == std::round(v) && std::fabs(v) < 1e15) snprintf(b, 40, "%lld", (long long)v); else snprintf(b, 40, "%.6f", v); cur += std::string("><v>") + b + "</v></c>"; }
    void date(Time t) { cellOpen(DATE); if (!t) { cur += "/>"; return; } double serial = (double)(mpp::toUnixDays(t) + 25569) + (t % 86400) / 86400.0; char b[40]; snprintf(b, 40, "%.6f", serial); cur += std::string("><v>") + b + "</v></c>"; }
    void header(const std::vector<std::wstring>& titles) { newRow(); for (auto& t : titles) text(t, HEADER); }
    void endSheet() {
        if (!open) return;
        if (row > 0) cur += "</row>";
        cur += "</sheetData>"; if (row > 1 && maxCol > 0) cur += "<autoFilter ref=\"A1:" + colName(maxCol) + std::to_string(row) + "\"/>"; cur += "</worksheet>";
        zip.add("xl/worksheets/sheet" + std::to_string(sheets.size()) + ".xml", cur); cur.clear(); open = false;
    }
    void close(const std::wstring& currency) {
        endSheet();
        std::string money = currency.empty() ? "#,##0.00" : "#,##0.00\\ &quot;" + xmlEsc(currency) + "&quot;";
        zip.add("xl/styles.xml", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><numFmts count=\"3\"><numFmt numFmtId=\"164\" formatCode=\"d\\.\\ m\\.\\ yyyy\"/><numFmt numFmtId=\"166\" formatCode=\"0\\ &quot;%&quot;\"/><numFmt numFmtId=\"167\" formatCode=\"" + money + "\"/></numFmts><fonts count=\"3\"><font><sz val=\"11\"/><name val=\"Calibri\"/></font><font><b/><sz val=\"11\"/><color rgb=\"FFFFFFFF\"/><name val=\"Calibri\"/></font><font><b/><sz val=\"11\"/><name val=\"Calibri\"/></font></fonts><fills count=\"3\"><fill><patternFill patternType=\"none\"/></fill><fill><patternFill patternType=\"gray125\"/></fill><fill><patternFill patternType=\"solid\"><fgColor rgb=\"FF2E5A8C\"/><bgColor indexed=\"64\"/></patternFill></fill></fills><borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders><cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs><cellXfs count=\"7\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/><xf numFmtId=\"0\" fontId=\"1\" fillId=\"2\" borderId=\"0\" xfId=\"0\" applyFont=\"1\" applyFill=\"1\"/><xf numFmtId=\"0\" fontId=\"2\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyFont=\"1\"/><xf numFmtId=\"164\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\"/><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/><xf numFmtId=\"167\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\"/><xf numFmtId=\"166\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\"/></cellXfs><cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles></styleSheet>");
        std::string wb = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets>", rels = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">", ct = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/><Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>";
        for (size_t i = 0; i < sheets.size(); i++) { std::string n = std::to_string(i + 1); wb += "<sheet name=\"" + sheets[i] + "\" sheetId=\"" + n + "\" r:id=\"rId" + n + "\"/>"; rels += "<Relationship Id=\"rId" + n + "\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet" + n + ".xml\"/>"; ct += "<Override PartName=\"/xl/worksheets/sheet" + n + ".xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>"; }
        wb += "</sheets></workbook>"; rels += "<Relationship Id=\"rIdStyles\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/></Relationships>"; ct += "</Types>";
        zip.add("xl/workbook.xml", wb); zip.add("xl/_rels/workbook.xml.rels", rels); zip.add("[Content_Types].xml", ct);
        zip.add("_rels/.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>");
        zip.close();
    }
};
static bool exportXlsx(const wchar_t* path = nullptr) {
    if (!g_loaded) return false;
    wchar_t buf[MAX_PATH]; if (!fillPath(buf, path, baseName() + L".xlsx")) return false;
    if (!path && !saveDialog(buf, L"Sešit Excel\0*.xlsx\0", L"xlsx", L"Export do Excelu (listy Úkoly, Zdroje, Přiřazení, Vytížení, Projekt)")) return false;
    try {
        Xlsx x(buf); const mpp::Project& p = g_prj;
        std::vector<int> w; std::vector<std::wstring> h;
        for (int ci : g_visible) { w.push_back(imax(8, g_cols[ci].width / 7)); h.push_back(g_cols[ci].title); }
        h.push_back(L"Úroveň"); h.push_back(L"Kritický"); h.push_back(L"Poznámky"); w.push_back(8); w.push_back(9); w.push_back(40);
        x.sheet(L"Úkoly", w); x.header(h);
        for (int r : g_rows) {
            const mpp::Task& t = p.tasks[r]; x.newRow();
            for (int ci : g_visible) {
                const Col& c = g_cols[ci]; const std::wstring& k = c.key;
                if (t.isNull) { x.text(cellText(t, r, ci)); continue; }
                if (k == L"start" || k == L"finish" || k == L"actualStart" || k == L"actualFinish" || k == L"deadline" || k == L"created" || k == L"earlyStart" || k == L"earlyFinish" || k == L"lateStart" || k == L"lateFinish" || k == L"baselineStart" || k == L"baselineFinish" || k.rfind(L"baseline", 0) == 0 && (k.find(L"Start") != std::wstring::npos || k.find(L"Finish") != std::wstring::npos)) { double v = c.num(t, r); x.date(std::isnan(v) ? 0 : (Time)v); }
                else if (k == L"percent" || k == L"percentWork") { double v = c.num(t, r); x.number(v, Xlsx::PERCENT); }
                else if (k == L"cost" || k == L"actualCost" || k == L"remainingCost" || k == L"fixedCost" || k == L"baselineCost" || k.find(L"Cost") != std::wstring::npos) { double v = c.num(t, r); x.number(v, Xlsx::MONEY); }
                else if (k == L"work" || k == L"actualWork" || k == L"remainingWork" || k == L"baselineWork" || k.find(L"Work") != std::wstring::npos) { double v = c.num(t, r); x.number(std::isnan(v) ? v : (k.find(L"baseline") == 0 ? v : v / 60.0), Xlsx::NUMBER); }
                else if (k == L"id" || k == L"uniqueId" || k == L"level" || k == L"variance" || k == L"startVariance") { double v = c.num(t, r); x.number(v, Xlsx::NUMBER); }
                else x.text(t.summary && k == L"name" ? t.name : cellText(t, r, ci), t.summary && k == L"name" ? Xlsx::BOLD : Xlsx::TEXT);
            }
            x.number(t.level); x.text(t.critical ? L"ano" : L""); x.text(t.notes);
        }
        x.sheet(L"Zdroje", { 6, 30, 12, 10, 16, 12, 12, 12, 12, 14, 8, 24 }); x.header({ L"ID", L"Název zdroje", L"Typ", L"Iniciály", L"Skupina", L"Max. jednotky", L"Standardní sazba", L"Práce (h)", L"Náklady", L"Přiřazení", L"", L"E-mail" });
        for (auto& r : p.resources) { x.newRow(); x.number(r.id); x.text(r.name); x.text(mpp::resourceTypeName(r.type)); x.text(r.initials); x.text(r.group); x.number(r.maxUnits * 100, Xlsx::PERCENT); x.number(r.stdRate, Xlsx::MONEY); x.number(r.workHours); x.number(r.cost, Xlsx::MONEY); x.number((double)r.assignments.size()); x.text(L""); x.text(r.email); }
        x.sheet(L"Přiřazení", { 8, 36, 30, 10, 10, 12, 12, 12 }); x.header({ L"ID úkolu", L"Úkol", L"Zdroj", L"Jednotky", L"Práce (h)", L"Zahájení", L"Dokončení", L"Náklady" });
        for (auto& a : p.assignments) { const mpp::Task* t = p.byUid(a.taskUid); const mpp::Resource* r = p.rscByUid(a.rscUid); if (!t || !r) continue; x.newRow(); x.number(t->id); x.text(t->name); x.text(r->name); x.number(a.units * 100, Xlsx::PERCENT); x.number(a.workHours); x.date(a.start ? a.start : t->start); x.date(a.finish ? a.finish : t->finish); x.number(a.cost, Xlsx::MONEY); }
        Usage u = computeUsage(UP_WEEKS);
        std::vector<int> uw = { 30, 12 }; std::vector<std::wstring> uh = { L"Zdroj", L"Celkem h" }; for (auto& hh : u.headers) { uw.push_back(9); uh.push_back(hh); }
        x.sheet(L"Vytížení (týdny, h)", uw); x.header(uh);
        for (size_t i = 0; i < u.rscIdx.size(); i++) { x.newRow(); x.text(p.resources[u.rscIdx[i]].name, Xlsx::BOLD); double sum = 0; for (double v : u.work[i]) sum += v; x.number(sum); for (double v : u.work[i]) x.number(v < 0.005 ? NONUM : v); }
        x.sheet(L"Projekt", { 28, 60 }); x.header({ L"Vlastnost", L"Hodnota" });
        auto kv = [&](const wchar_t* k, const std::wstring& v) { x.newRow(); x.text(k); x.text(v); };
        kv(L"Soubor", g_file); kv(L"Formát", p.format); kv(L"Aplikace", p.appName); kv(L"Název", p.title); kv(L"Autor", p.author); kv(L"Manažer", p.manager); kv(L"Společnost", p.company);
        kv(L"Zahájení projektu", mpp::fmtDate(p.startDate, true)); kv(L"Dokončení projektu", mpp::fmtDate(p.finishDate, true)); kv(L"Datum stavu", mpp::fmtDate(p.statusDate, true));
        kv(L"Výchozí kalendář", p.calendar().name); kv(L"Hodin denně / týdně", mpp::fmtNumber(p.minutesPerDay / 60.0) + L" / " + mpp::fmtNumber(p.minutesPerWeek / 60.0)); kv(L"Měna", p.currency);
        kv(L"Úkolů", std::to_wstring(p.tasks.size())); kv(L"Zdrojů", std::to_wstring(p.resources.size())); kv(L"Přiřazení", std::to_wstring(p.assignments.size()));
        x.close(p.currency);
    } catch (const std::exception& e) { if (!path) msgError(L"Export se nezdařil: " + mpp::ansiToWide(e.what(), CP_UTF8)); return false; }
    return true;
}

// ---------------- rastr: PNG, PDF, tisk ----------------
static int pngEncoderClsid(CLSID* out) {
    UINT n = 0, size = 0; Gdiplus::GetImageEncodersSize(&n, &size); if (!size) return -1;
    std::vector<char> buf(size); auto* info = (Gdiplus::ImageCodecInfo*)buf.data();
    Gdiplus::GetImageEncoders(n, size, info);
    for (UINT i = 0; i < n; i++) if (wcscmp(info[i].MimeType, L"image/png") == 0) { *out = info[i].Clsid; return 0; }
    return -1;
}
// Vykreslí stránku sestavy do 24bpp DIB; vrací HBITMAP (volající uvolní)
// Kreslí se 1:1 v pixelech obrázku; velikost písma a řádků určuje DPI předané do renderReport, ne obrazovka.
// (Dřívější přepočet MM_ANISOTROPIC podle DPI obrazovky při 125–150 % ořezával graf a v PDF dělal nerovné mezery v textu.)
static HBITMAP renderPageBitmap(int w, int h, std::function<void(HDC, RECT)> draw) {
    HDC sdc = GetDC(nullptr); HDC mem = CreateCompatibleDC(sdc);
    BITMAPINFO bi = {}; bi.bmiHeader.biSize = sizeof(bi.bmiHeader); bi.bmiHeader.biWidth = w; bi.bmiHeader.biHeight = -h; bi.bmiHeader.biPlanes = 1; bi.bmiHeader.biBitCount = 24; bi.bmiHeader.biCompression = BI_RGB;
    void* bits = nullptr; HBITMAP bmp = CreateDIBSection(mem, &bi, DIB_RGB_COLORS, &bits, nullptr, 0);
    if (!bmp) { DeleteDC(mem); ReleaseDC(nullptr, sdc); return nullptr; } // málo paměti: volající ohlásí chybu exportu
    HGDIOBJ old = SelectObject(mem, bmp);
    RECT full = { 0, 0, w, h }; FillRect(mem, &full, (HBRUSH)GetStockObject(WHITE_BRUSH));
    draw(mem, full);
    SelectObject(mem, old); DeleteDC(mem); ReleaseDC(nullptr, sdc);
    return bmp;
}
// Prázdný výsledek = převod se nepovedl (málo paměti); writePdf to ohlásí jako chybu.
static std::string pngBytes(HBITMAP bmp) {
    IStream* st = nullptr; std::string out;
    if (FAILED(CreateStreamOnHGlobal(nullptr, TRUE, &st)) || !st) return out;
    Gdiplus::Bitmap gb(bmp, nullptr); CLSID clsid; HGLOBAL hg = nullptr; STATSTG ss = {};
    if (pngEncoderClsid(&clsid) == 0 && gb.Save(st, &clsid, nullptr) == Gdiplus::Ok
        && SUCCEEDED(GetHGlobalFromStream(st, &hg)) && SUCCEEDED(st->Stat(&ss, STATFLAG_NONAME))) {
        if (const char* p = (const char*)GlobalLock(hg)) { out.assign(p, (size_t)ss.cbSize.QuadPart); GlobalUnlock(hg); }
    }
    st->Release();
    return out;
}
// Filtr nebo hledání skryly všechny úkoly: PNG, PDF a tisk by jinak tiše neudělaly nic
static bool hasRowsToExport(const wchar_t* path) {
    if (g_loaded && g_rows.empty() && !path && !g_batch) MessageBoxW(g_main, L"Nejsou zobrazené žádné úkoly – zkontrolujte filtr nebo hledání.", APP_TITLE, MB_ICONINFORMATION);
    return g_loaded && !g_rows.empty();
}
static bool exportPng(const wchar_t* path = nullptr) {
    if (!hasRowsToExport(path)) return false;
    wchar_t buf[MAX_PATH]; if (!fillPath(buf, path, baseName() + L".png")) return false;
    if (!path && !saveDialog(buf, L"Obrázek PNG\0*.png\0", L"png", L"Export harmonogramu do PNG (zobrazené řádky, celý projekt)")) return false;
    int rows = imin((int)g_rows.size(), 4000);
    int w = 2400, h = 22 + 4 + 30 + rows * 18 + 34;
    HBITMAP bmp = renderPageBitmap(w, h, [&](HDC hdc, RECT a) { RECT area = { 10, 6, a.right - 10, a.bottom - 4 }; renderReport(hdc, area, 0, rows, 1, 1, 96); }); // 96: rozměry výše (18 px na řádek)
    Gdiplus::Bitmap gb(bmp, nullptr); CLSID clsid;
    bool ok = pngEncoderClsid(&clsid) == 0 && gb.Save(buf, &clsid, nullptr) == Gdiplus::Ok;
    DeleteObject(bmp);
    if (path) return ok;
    if (!ok) { msgError(L"Obrázek se nepodařilo uložit."); return ok; }
    if ((int)g_rows.size() > rows) // víc řádků by dalo obrázek přes půl GB
        MessageBoxW(g_main, (L"Obrázek obsahuje prvních " + std::to_wstring(rows) + L" z " + std::to_wstring(g_rows.size()) + L" zobrazených řádků. Celý harmonogram uložte do PDF.").c_str(), APP_TITLE, MB_ICONINFORMATION);
    ShellExecuteW(nullptr, L"open", buf, nullptr, nullptr, SW_SHOWNORMAL);
    return ok;
}
// PDF: každá stránka = obrázek; data přebíráme z PNG (IDAT = zlib deflate s PNG prediktory) -> /FlateDecode s /Predictor 15
static bool writePdf(const wchar_t* path, int pages, double wpt, double hpt, std::function<HBITMAP(int)> render, const std::wstring& title) {
    FILE* f = _wfopen(path, L"wb"); if (!f) return false;
    try { // při výjimce (málo paměti) soubor zavřít, jinak by zůstal zamčený až do konce programu
    std::vector<long> offs; auto tell = [&]() { return ftell(f); };
    auto puts_ = [&](const std::string& s) { fwrite(s.data(), 1, s.size(), f); };
    puts_("%PDF-1.4\n%\xE2\xE3\xCF\xD3\n");
    std::string kids; for (int i = 0; i < pages; i++) kids += std::to_string(4 + i * 3) + " 0 R ";
    offs.push_back(tell()); puts_("1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n");
    offs.push_back(tell()); puts_("2 0 obj\n<< /Type /Pages /Kids [" + kids + "] /Count " + std::to_string(pages) + " >>\nendobj\n");
    std::string t; for (wchar_t c : title) t += (c == L'(' || c == L')' || c == L'\\') ? std::string("\\") + (char)c : (c < 128 ? std::string(1, (char)c) : "?");
    offs.push_back(tell()); puts_("3 0 obj\n<< /Producer (M3K Project View " + mpp::wideToUtf8(APP_VERSION) + ") /Title (" + t + ") >>\nendobj\n");
    char wb[32], hb[32]; snprintf(wb, 32, "%.2f", wpt); snprintf(hb, 32, "%.2f", hpt);
    for (int i = 0; i < pages; i++) {
        HBITMAP bmp = render(i); BITMAP bm = {};
        if (!bmp || !GetObject(bmp, sizeof(bm), &bm)) { if (bmp) DeleteObject(bmp); fclose(f); return false; } // málo paměti na stránku
        std::string png = pngBytes(bmp); DeleteObject(bmp);
        std::string idat; int colorType = -1;
        for (size_t p = 8; p + 8 <= png.size();) { uint32_t len = ((uint8_t)png[p] << 24) | ((uint8_t)png[p + 1] << 16) | ((uint8_t)png[p + 2] << 8) | (uint8_t)png[p + 3]; std::string type = png.substr(p + 4, 4); if (type == "IHDR" && len >= 13) colorType = (uint8_t)png[p + 8 + 9]; if (type == "IDAT") idat += png.substr(p + 8, len); p += 12 + len; }
        if (idat.empty() || colorType < 0) { fclose(f); return false; } // stránku se nepodařilo převést do PNG
        int po = 4 + i * 3, co = po + 1, io = po + 2;
        offs.push_back(tell()); puts_(std::to_string(po) + " 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 " + wb + " " + hb + "] /Contents " + std::to_string(co) + " 0 R /Resources << /XObject << /Im" + std::to_string(i) + " " + std::to_string(io) + " 0 R >> >> >>\nendobj\n");
        std::string content = std::string("q ") + wb + " 0 0 " + hb + " 0 0 cm /Im" + std::to_string(i) + " Do Q\n";
        offs.push_back(tell()); puts_(std::to_string(co) + " 0 obj\n<< /Length " + std::to_string(content.size()) + " >>\nstream\n" + content + "\nendstream\nendobj\n");
        std::string data, filter;
        if (colorType == 2 && !idat.empty()) { data = idat; filter = "/Filter /FlateDecode /DecodeParms << /Predictor 15 /Colors 3 /BitsPerComponent 8 /Columns " + std::to_string(bm.bmWidth) + " >>"; }
        else { // záloha: nekomprimované RGB
            BITMAPINFO bi = {}; bi.bmiHeader.biSize = sizeof(bi.bmiHeader); bi.bmiHeader.biWidth = bm.bmWidth; bi.bmiHeader.biHeight = -bm.bmHeight; bi.bmiHeader.biPlanes = 1; bi.bmiHeader.biBitCount = 24; bi.bmiHeader.biCompression = BI_RGB;
            int stride = ((bm.bmWidth * 3 + 3) / 4) * 4; std::vector<uint8_t> px((size_t)stride * bm.bmHeight); HDC dc = GetDC(nullptr); HBITMAP b2 = render(i); GetDIBits(dc, b2, 0, bm.bmHeight, px.data(), &bi, DIB_RGB_COLORS); ReleaseDC(nullptr, dc); DeleteObject(b2);
            data.reserve((size_t)bm.bmWidth * bm.bmHeight * 3);
            for (int y = 0; y < bm.bmHeight; y++) for (int xx = 0; xx < bm.bmWidth; xx++) { const uint8_t* q = &px[(size_t)y * stride + xx * 3]; data += (char)q[2]; data += (char)q[1]; data += (char)q[0]; }
        }
        offs.push_back(tell()); puts_(std::to_string(io) + " 0 obj\n<< /Type /XObject /Subtype /Image /Width " + std::to_string(bm.bmWidth) + " /Height " + std::to_string(bm.bmHeight) + " /ColorSpace /DeviceRGB /BitsPerComponent 8 " + filter + " /Length " + std::to_string(data.size()) + " >>\nstream\n"); puts_(data); puts_("\nendstream\nendobj\n");
    }
    long xref = tell();
    puts_("xref\n0 " + std::to_string(offs.size() + 1) + "\n0000000000 65535 f \n");
    for (long o : offs) { char b[32]; snprintf(b, 32, "%010ld 00000 n \n", o); puts_(b); }
    puts_("trailer\n<< /Size " + std::to_string(offs.size() + 1) + " /Root 1 0 R /Info 3 0 R >>\nstartxref\n" + std::to_string(xref) + "\n%%EOF\n");
    } catch (...) { fclose(f); throw; }
    bool ok = !ferror(f); if (fclose(f) != 0) ok = false;
    return ok;
}
// Řádků na stránku a počet stránek tisku i PDF – rozměry odpovídají rozvržení v renderReport (gantt.h).
static void pageSplit(int ph, int dpi, int margin, int& perPage, int& pages) {
    int rowH = MulDiv(18, dpi, 96), headerH = MulDiv(30, dpi, 96), titleH = MulDiv(26, dpi, 96), legendH = MulDiv(24, dpi, 96);
    perPage = imax(1, (ph - 2 * margin - headerH - titleH - legendH) / rowH);
    pages = ((int)g_rows.size() + perPage - 1) / perPage;
}
static bool exportPdf(const wchar_t* path = nullptr, bool a3 = false) {
    if (!hasRowsToExport(path)) return false;
    if (!path) { // volba formátu
        Dlg d; dlgCreate(d, L"Uložit do PDF", 300, 120);
        dlgLabel(d.h, L"Formát stránky (na šířku):", 12, 12, 270);
        HWND c = dlgCombo(d.h, 12, 34, 120, 100); SendMessage(c, CB_ADDSTRING, 0, (LPARAM)L"A4"); SendMessage(c, CB_ADDSTRING, 0, (LPARAM)L"A3"); SendMessage(c, CB_SETCURSEL, 0, 0);
        dlgButton(d.h, L"Uložit…", 130, 80, 76, IDOK, true); dlgButton(d.h, L"Zrušit", 212, 80, 74, IDCANCEL);
        d.onCmd = [&](int id, int, HWND) { if (id == IDOK) { a3 = SendMessage(c, CB_GETCURSEL, 0, 0) == 1; d.result = 1; d.done = true; } else if (id == IDCANCEL) d.done = true; };
        if (dlgRun(d) != 1) return false;
    }
    wchar_t buf[MAX_PATH]; if (!fillPath(buf, path, baseName() + L".pdf")) return false;
    if (!path && !saveDialog(buf, L"PDF\0*.pdf\0", L"pdf", L"Uložit harmonogram do PDF")) return false;
    double wpt = a3 ? 1190.55 : 841.89, hpt = a3 ? 841.89 : 595.28; int dpi = 150;
    int pw = (int)(wpt / 72 * dpi), ph = (int)(hpt / 72 * dpi);
    int margin = MulDiv(30, dpi, 96), perPage = 1, pages = 1; pageSplit(ph, dpi, margin, perPage, pages);
    bool ok = false;
    try {
        ok = writePdf(buf, pages, wpt, hpt, [&](int pg) {
            // sestava v DPI stránky (150) – stejně jako pageSplit; dřív v 96 DPI, takže stránka byla zaplněná jen ze 60 % a písmo malé
            return renderPageBitmap(pw, ph, [&](HDC hdc, RECT) { RECT area = { margin, margin, pw - margin, ph - margin }; renderReport(hdc, area, pg * perPage, imin((int)g_rows.size(), (pg + 1) * perPage), pg + 1, pages, dpi); });
        }, baseName());
    } catch (const std::exception&) { ok = false; } // např. málo paměti – hlášení níže
    if (path) return ok;
    if (!ok) msgError(L"PDF se nepodařilo uložit."); else ShellExecuteW(nullptr, L"open", buf, nullptr, nullptr, SW_SHOWNORMAL);
    return ok;
}
static void printReport() {
    if (!hasRowsToExport(nullptr)) return;
    PRINTDLGW pd = { sizeof(pd) };
    pd.hwndOwner = g_main; pd.Flags = PD_RETURNDEFAULT;
    if (PrintDlgW(&pd) && pd.hDevMode) { DEVMODEW* dm = (DEVMODEW*)GlobalLock(pd.hDevMode); if (dm) { dm->dmFields |= DM_ORIENTATION; dm->dmOrientation = DMORIENT_LANDSCAPE; GlobalUnlock(pd.hDevMode); } }
    pd.Flags = PD_RETURNDC | PD_NOSELECTION | PD_NOPAGENUMS | PD_USEDEVMODECOPIESANDCOLLATE;
    if (!PrintDlgW(&pd) || !pd.hDC) { if (pd.hDevMode) GlobalFree(pd.hDevMode); if (pd.hDevNames) GlobalFree(pd.hDevNames); return; } // Zrušit
    HDC hdc = pd.hDC;
    int pw = GetDeviceCaps(hdc, HORZRES), ph = GetDeviceCaps(hdc, VERTRES), dpi = GetDeviceCaps(hdc, LOGPIXELSX);
    int margin = MulDiv(40, dpi, 96), perPage = 1, pages = 1; pageSplit(ph, dpi, margin, perPage, pages);
    DOCINFOW di = { sizeof(di) }; std::wstring dn = std::wstring(APP_TITLE) + L" – " + baseName(); di.lpszDocName = dn.c_str();
    if (StartDocW(hdc, &di) > 0) {
        for (int p = 0; p < pages; p++) { StartPage(hdc); RECT area = { margin, margin, pw - margin, ph - margin }; renderReport(hdc, area, p * perPage, imin((int)g_rows.size(), (p + 1) * perPage), p + 1, pages); EndPage(hdc); }
        EndDoc(hdc);
    }
    DeleteDC(hdc);
    if (pd.hDevMode) GlobalFree(pd.hDevMode); if (pd.hDevNames) GlobalFree(pd.hDevNames);
}
