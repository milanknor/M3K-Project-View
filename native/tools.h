// tools.h - Kontrola kvality plánu (14 kontrol ve stylu DCMA) a Porovnání dvou verzí harmonogramu; port ScheduleCheck.java / ScheduleDiff.java
#pragma once
#include "exports.h"

static void selectTaskByUid(int uid); // mppview.cpp

// ---------------- kontrola kvality plánu ----------------
enum CheckLevel { CL_OK, CL_NOTE, CL_WARNING };
struct Finding { std::wstring rule, detail; int count, total; std::vector<int> tasks; CheckLevel level; double share() const { return total <= 0 ? 0 : count * 100.0 / total; } };
static const double LONG_DAYS = 44;
static std::vector<Finding> runScheduleCheck() {
    const mpp::Project& p = g_prj;
    std::vector<int> tasks; for (size_t i = 0; i < p.tasks.size(); i++) { const mpp::Task& t = p.tasks[i]; if (t.isNull || !t.nameSet || t.summary || !t.active) continue; tasks.push_back((int)i); }
    std::vector<Finding> out; int total = (int)tasks.size(); if (!total) return out;
    int64_t statusDay = mpp::toUnixDays(p.statusDate ? p.statusDate : g_today);
    std::vector<int> noPred, noSucc, hardC, negSlack, highSlack, longDur, withLag, withLead, nonFs, late, futureAct, startedNoActual, noRes, msNoLink;
    auto days = [&](int raw) { return raw == INT32_MIN ? 0.0 : mpp::durationDays(raw, p); };
    for (int i : tasks) {
        const mpp::Task& t = p.tasks[i];
        auto it = g_succ.find(t.uid); bool hasSucc = it != g_succ.end() && !it->second.empty();
        if (t.preds.empty()) noPred.push_back(i);
        if (!hasSucc) noSucc.push_back(i);
        if (t.milestone && t.preds.empty() && !hasSucc) msNoLink.push_back(i);
        if (t.constraintType == 2 || t.constraintType == 3 || t.constraintType == 5 || t.constraintType == 7) hardC.push_back(i);
        double slack = days(t.totalSlackRaw);
        if (t.totalSlackRaw != INT32_MIN) { if (slack < -0.01) negSlack.push_back(i); else if (slack > LONG_DAYS) highSlack.push_back(i); }
        if (!t.milestone && t.duration.valid() && days(t.duration.raw) > LONG_DAYS) longDur.push_back(i);
        bool lag = false, lead = false, nfs = false;
        for (const mpp::Relation& r : t.preds) { double l = r.lag.valid() ? mpp::durationDays(r.lag.raw, p) : 0; if (l > 0.01) lag = true; else if (l < -0.01) lead = true; if (r.type != 1) nfs = true; }
        if (lag) withLag.push_back(i); if (lead) withLead.push_back(i); if (nfs) nonFs.push_back(i);
        int pct = imax(0, t.pctComplete);
        if (pct < 100 && t.finish && mpp::toUnixDays(t.finish) < statusDay) late.push_back(i);
        if ((t.actualStart && mpp::toUnixDays(t.actualStart) > statusDay) || (t.actualFinish && mpp::toUnixDays(t.actualFinish) > statusDay)) futureAct.push_back(i);
        if (pct > 0 && !t.actualStart) startedNoActual.push_back(i);
        if (!t.milestone && t.assignments.empty() && t.duration.valid() && t.duration.raw > 0) noRes.push_back(i);
    }
    auto add = [&](const wchar_t* rule, const wchar_t* detail, const std::vector<int>& v, double tol) { double share = total ? v.size() * 100.0 / total : 0; out.push_back({ rule, detail, (int)v.size(), total, v, v.empty() ? CL_OK : share <= tol ? CL_NOTE : CL_WARNING }); };
    add(L"Úkoly bez předchůdce", L"Nemají na co navazovat – při posunu začátku se samy neposunou.", noPred, 5);
    add(L"Úkoly bez následníka", L"Nic z nich nevychází, takže jejich zpoždění se v plánu neprojeví.", noSucc, 5);
    add(L"Tvrdá omezení", L"„Musí začít/skončit“ a „nejpozději“ přebíjejí vazby a zakrývají skutečnou kritickou cestu.", hardC, 0);
    add(L"Záporná celková rezerva", L"Plán je v konfliktu – termín nelze splnit bez změny vazeb nebo omezení.", negSlack, 0);
    add(L"Rezerva nad 44 dní", L"Vysoká rezerva bývá známkou chybějící nebo volné vazby.", highSlack, 5);
    add(L"Trvání nad 44 dní", L"Dlouhé úkoly se špatně řídí; obvykle jde o nerozpadlý balík práce.", longDur, 5);
    add(L"Vazby s prodlevou", L"Prodleva ve vazbě skrývá činnost, která v plánu není vidět.", withLag, 5);
    add(L"Vazby s předstihem", L"Záporná prodleva (překryv) podle DCMA v plánu být nemá.", withLead, 0);
    add(L"Jiné vazby než konec–začátek", L"SS/FF/SF vazby jsou v pořádku, pokud je jich málo a jsou zdůvodněné.", nonFs, 10);
    add(L"Zpožděné proti datu stavu", L"Dokončení je před datem stavu, ale úkol není hotový.", late, 5);
    add(L"Skutečné termíny v budoucnosti", L"Skutečné zahájení nebo dokončení je po datu stavu – chyba ve výkazu.", futureAct, 0);
    add(L"Rozpracované bez skutečného zahájení", L"Procento dokončení je vyplněné, ale skutečné zahájení chybí.", startedNoActual, 5);
    add(L"Úkoly bez zdroje", L"Bez přiřazení nejde plánovat kapacity ani náklady.", noRes, 20);
    add(L"Milníky bez vazeb", L"Milník bez vazby nemá v plánu žádnou oporu.", msNoLink, 0);
    return out;
}
static std::wstring checkSummary(const std::vector<Finding>& f) {
    int w = 0, n = 0; for (auto& x : f) { if (x.level == CL_WARNING) w++; else if (x.level == CL_NOTE) n++; }
    if (!w && !n) return L"Žádná z kontrol nenašla problém.";
    return L"Varování: " + std::to_wstring(w) + L", k zamyšlení: " + std::to_wstring(n) + L" (kontrol celkem: " + std::to_wstring(f.size()) + L")";
}
static void scheduleCheckDialog() {
    if (!g_loaded) return;
    std::vector<Finding> f = runScheduleCheck();
    Dlg d; dlgCreate(d, L"Kontrola kvality plánu (DCMA 14)", 900, 600, true);
    HWND sum = dlgLabel(d.h, checkSummary(f).c_str(), 12, 10, 860);
    HWND lf = dlgList(d.h, 12, 34, 876, 260, 101, LVS_SINGLESEL); lvColumn(lf, 0, L"Kontrola", 220); lvColumn(lf, 1, L"Stav", 90); lvColumn(lf, 2, L"Úkolů", 60, true); lvColumn(lf, 3, L"Podíl", 60, true); lvColumn(lf, 4, L"Proč to vadí", 430);
    for (size_t i = 0; i < f.size(); i++) { wchar_t sh[16]; swprintf(sh, 16, L"%.0f %%", f[i].share()); lvAdd(lf, { f[i].rule, f[i].level == CL_OK ? L"v pořádku" : f[i].level == CL_NOTE ? L"k zamyšlení" : L"VAROVÁNÍ", std::to_wstring(f[i].count), sh, f[i].detail }, (LPARAM)i); }
    HWND lbl = dlgLabel(d.h, L"Dotčené úkoly (dvojklik přejde na úkol):", 12, 302, 500);
    HWND lt = dlgList(d.h, 12, 322, 876, 230, 102, LVS_SINGLESEL); lvColumn(lt, 0, L"ID", 50, true); lvColumn(lt, 1, L"Úkol", 360); lvColumn(lt, 2, L"Zahájení", 90); lvColumn(lt, 3, L"Dokončení", 90); lvColumn(lt, 4, L"% dok.", 60, true); lvColumn(lt, 5, L"Rezerva", 90); lvColumn(lt, 6, L"Zdroje", 120);
    HWND bGo = dlgButton(d.h, L"Přejít na úkol", 12, 562, 110, 103), bCopy = dlgButton(d.h, L"Kopírovat přehled", 130, 562, 130, 104), bCsv = dlgButton(d.h, L"Uložit CSV…", 268, 562, 110, 105), bClose = dlgButton(d.h, L"Zavřít", 800, 562, 88, IDCANCEL, true);
    auto fillTasks = [&](int fi) { ListView_DeleteAllItems(lt); if (fi < 0 || fi >= (int)f.size()) return; for (int ti : f[fi].tasks) { const mpp::Task& t = g_prj.tasks[ti]; lvAdd(lt, { std::to_wstring(t.id), t.name, mpp::fmtDate(t.start), mpp::fmtDate(t.finish), std::to_wstring(imax(0, t.pctComplete)) + L" %", mpp::fmtSlackRaw(t.totalSlackRaw, g_prj), t.resourceNames }, t.uid); } };
    auto report = [&]() { std::wstring s = L"Kontrola kvality plánu – " + fileName(g_file) + L"\r\n" + checkSummary(f) + L"\r\n\r\n"; for (auto& x : f) { s += x.rule + L": " + std::to_wstring(x.count) + L" (" + mpp::fmtNumber(x.share()) + L" %) – " + (x.level == CL_OK ? L"v pořádku" : x.level == CL_NOTE ? L"k zamyšlení" : L"VAROVÁNÍ") + L"\r\n"; for (int ti : x.tasks) s += L"    " + std::to_wstring(g_prj.tasks[ti].id) + L"  " + g_prj.tasks[ti].name + L"\r\n"; } return s; };
    d.onSize = [&](int w, int h) { MoveWindow(lf, S(12), S(34), w - S(24), (h - S(120)) / 2, TRUE); int y2 = S(34) + (h - S(120)) / 2 + S(8); MoveWindow(lbl, S(12), y2, S(500), S(18), TRUE); MoveWindow(lt, S(12), y2 + S(20), w - S(24), h - y2 - S(20) - S(44), TRUE); int by = h - S(34); MoveWindow(bGo, S(12), by, S(110), S(24), TRUE); MoveWindow(bCopy, S(130), by, S(130), S(24), TRUE); MoveWindow(bCsv, S(268), by, S(110), S(24), TRUE); MoveWindow(bClose, w - S(100), by, S(88), S(24), TRUE); MoveWindow(sum, S(12), S(10), w - S(24), S(18), TRUE); };
    d.onNotify = [&](NMHDR* nm) -> LRESULT {
        if (nm->hwndFrom == lf && nm->code == LVN_ITEMCHANGED) { NMLISTVIEW* lv = (NMLISTVIEW*)nm; if ((lv->uNewState & LVIS_SELECTED) && !(lv->uOldState & LVIS_SELECTED)) fillTasks((int)lvParam(lf, lv->iItem)); }
        if (nm->hwndFrom == lt && nm->code == NM_DBLCLK) { int i = ListView_GetNextItem(lt, -1, LVNI_SELECTED); if (i >= 0) { d.result = (int)lvParam(lt, i) + 1; d.done = true; } }
        return 0;
    };
    d.onCmd = [&](int id, int, HWND) {
        if (id == IDCANCEL) d.done = true;
        else if (id == 103) { int i = ListView_GetNextItem(lt, -1, LVNI_SELECTED); if (i >= 0) { d.result = (int)lvParam(lt, i) + 1; d.done = true; } }
        else if (id == 104) clipboardText(report());
        else if (id == 105) { wchar_t buf[MAX_PATH]; wcscpy(buf, (baseName() + L"-kontrola.csv").c_str()); if (saveDialog(buf, L"CSV\0*.csv\0", L"csv", L"Uložit kontrolu do CSV")) { std::wstring s = L"Kontrola;Stav;Úkolů;Podíl %;ID;Úkol;Zahájení;Dokončení\r\n"; for (auto& x : f) { if (x.tasks.empty()) s += csvCell(x.rule) + L";v pořádku;0;0;;;;\r\n"; for (int ti : x.tasks) { const mpp::Task& t = g_prj.tasks[ti]; s += csvCell(x.rule) + L";" + (x.level == CL_NOTE ? L"k zamyšlení" : L"varování") + L";" + std::to_wstring(x.count) + L";" + mpp::fmtNumber(x.share()) + L";" + std::to_wstring(t.id) + L";" + csvCell(t.name) + L";" + mpp::fmtDate(t.start) + L";" + mpp::fmtDate(t.finish) + L"\r\n"; } } try { writeUtf8(buf, s); } catch (...) { msgError(L"Soubor nelze zapsat."); } } }
    };
    RECT rc; GetClientRect(d.h, &rc); d.onSize(rc.right, rc.bottom);
    if (ListView_GetItemCount(lf)) ListView_SetItemState(lf, 0, LVIS_SELECTED | LVIS_FOCUSED, LVIS_SELECTED | LVIS_FOCUSED);
    int r = dlgRun(d);
    if (r > 0) selectTaskByUid(r - 1);
}

// ---------------- porovnání dvou verzí ----------------
enum DiffKind { DK_ADDED, DK_REMOVED, DK_CHANGED, DK_SAME };
struct DiffRow { DiffKind kind; int oldIdx, newIdx; std::wstring name, wbs; Time oldStart, newStart, oldFinish, newFinish; int64_t startShift, finishShift; std::vector<std::wstring> changes; };
static std::vector<DiffRow> compareProjects(const mpp::Project& older, const mpp::Project& newer) {
    std::map<std::wstring, int> byUid, byWbsName, byName; std::vector<int> oldTasks;
    auto key = [](const std::wstring& wbs, const std::wstring& name) { std::wstring n = lower(name); while (!n.empty() && n.back() == L' ') n.pop_back(); return (wbs.empty() ? L"" : wbs + L"|") + n; };
    for (size_t i = 0; i < older.tasks.size(); i++) { const mpp::Task& t = older.tasks[i]; if (t.isNull || t.name.empty()) continue; oldTasks.push_back((int)i); byUid.emplace(L"u" + std::to_wstring(t.uid), (int)i); byWbsName.emplace(key(t.wbs, t.name), (int)i); byName.emplace(key(L"", t.name), (int)i); }
    std::set<int> matched; std::vector<DiffRow> out;
    auto find = [&](std::map<std::wstring, int>& m, const std::wstring& k) { if (k.empty()) return -1; auto it = m.find(k); return it == m.end() || matched.count(it->second) ? -1 : it->second; };
    auto days = [](const mpp::Duration& d, const mpp::Project& p) { return d.valid() ? mpp::durationDays(d.raw, p) : 0.0; };
    for (size_t i = 0; i < newer.tasks.size(); i++) {
        const mpp::Task& b = newer.tasks[i]; if (b.isNull || b.name.empty()) continue;
        int m = find(byUid, L"u" + std::to_wstring(b.uid)); if (m < 0) m = find(byWbsName, key(b.wbs, b.name)); if (m < 0) m = find(byName, key(L"", b.name));
        if (m < 0) { out.push_back({ DK_ADDED, -1, (int)i, b.name, b.wbs, 0, b.start, 0, b.finish, 0, 0, { L"nový úkol" } }); continue; }
        matched.insert(m); const mpp::Task& a = older.tasks[m];
        DiffRow r{ DK_SAME, m, (int)i, b.name, b.wbs, a.start, b.start, a.finish, b.finish, 0, 0, {} };
        auto sign = [](int64_t v) { return (v > 0 ? L"+" : L"") + std::to_wstring(v); };
        if (a.start && b.start) r.startShift = mpp::toUnixDays(b.start) - mpp::toUnixDays(a.start);
        if (a.finish && b.finish) r.finishShift = mpp::toUnixDays(b.finish) - mpp::toUnixDays(a.finish);
        if (r.startShift) r.changes.push_back(L"začátek " + sign(r.startShift) + L" dní");
        if (r.finishShift) r.changes.push_back(L"konec " + sign(r.finishShift) + L" dní");
        double da = days(a.duration, older), db = days(b.duration, newer); if (std::fabs(db - da) > 0.01) r.changes.push_back(L"trvání " + mpp::fmtNumber(da) + L" → " + mpp::fmtNumber(db) + L" dní");
        int pa = imax(0, a.pctComplete), pb = imax(0, b.pctComplete); if (pa != pb) r.changes.push_back(L"dokončeno " + std::to_wstring(pa) + L" → " + std::to_wstring(pb) + L" %");
        if (lower(a.name) != lower(b.name)) r.changes.push_back(L"přejmenován z „" + a.name + L"“");
        if (a.critical != b.critical) r.changes.push_back(b.critical ? L"nově kritický" : L"už není kritický");
        double wd = (b.workMinutes - a.workMinutes) / 60.0; if (std::fabs(wd) > 0.01) r.changes.push_back(L"práce " + sign((int64_t)std::lround(wd)) + L" h");
        if (std::fabs(b.cost - a.cost) > 0.5) r.changes.push_back(L"náklady " + sign((int64_t)std::lround(b.cost - a.cost)));
        if (!r.changes.empty()) r.kind = DK_CHANGED;
        out.push_back(r);
    }
    for (int m : oldTasks) if (!matched.count(m)) { const mpp::Task& a = older.tasks[m]; out.push_back({ DK_REMOVED, m, -1, a.name, a.wbs, a.start, 0, a.finish, 0, 0, 0, { L"v nové verzi chybí" } }); }
    return out;
}
static void compareDialog() {
    if (!g_loaded) return;
    wchar_t path[MAX_PATH]; if (!openDialogPath(path, L"Vyberte starší verzi téhož harmonogramu")) return;
    mpp::Project older;
    try { older = mpp::loadProject(path); } catch (const std::exception& e) { msgError(L"Soubor nelze otevřít: " + mpp::ansiToWide(e.what(), CP_UTF8)); return; }
    std::vector<DiffRow> rows = compareProjects(older, g_prj);
    int added = 0, removed = 0, changed = 0; int64_t maxShift = 0;
    for (auto& r : rows) { if (r.kind == DK_ADDED) added++; else if (r.kind == DK_REMOVED) removed++; else if (r.kind == DK_CHANGED) { changed++; if (std::llabs(r.finishShift) > std::llabs(maxShift)) maxShift = r.finishShift; } }
    std::wstring summary = L"Starší: " + fileName(path) + L"   ·   Změněno: " + std::to_wstring(changed) + L", přidáno: " + std::to_wstring(added) + L", odebráno: " + std::to_wstring(removed) + (maxShift ? L"   ·   největší posun dokončení: " + std::wstring(maxShift > 0 ? L"+" : L"") + std::to_wstring(maxShift) + L" dní" : L"");
    Dlg d; dlgCreate(d, L"Porovnání dvou verzí", 1000, 600, true);
    HWND sum = dlgLabel(d.h, summary.c_str(), 12, 10, 960);
    HWND only = dlgCheck(d.h, L"Jen změny", 12, 32, 120, 106); SendMessage(only, BM_SETCHECK, BST_CHECKED, 0);
    HWND lv = dlgList(d.h, 12, 56, 976, 490, 101, LVS_SINGLESEL);
    const wchar_t* cols[] = { L"Změna", L"ID", L"Úkol", L"WBS", L"Zahájení dříve", L"Zahájení nyní", L"Dokončení dříve", L"Dokončení nyní", L"Posun zač.", L"Posun dok.", L"Co se změnilo" };
    int cw[] = { 70, 45, 240, 60, 90, 90, 90, 90, 70, 70, 300 };
    for (int i = 0; i < 11; i++) lvColumn(lv, i, cols[i], cw[i], i == 1 || i == 8 || i == 9);
    static const wchar_t* KINDS[] = { L"přidán", L"odebrán", L"změněn", L"beze změny" };
    auto fill = [&]() {
        ListView_DeleteAllItems(lv); bool oc = SendMessage(only, BM_GETCHECK, 0, 0) == BST_CHECKED;
        for (size_t i = 0; i < rows.size(); i++) { const DiffRow& r = rows[i]; if (oc && r.kind == DK_SAME) continue; int id = r.newIdx >= 0 ? g_prj.tasks[r.newIdx].id : older.tasks[r.oldIdx].id;
            lvAdd(lv, { KINDS[r.kind], std::to_wstring(id), r.name, r.wbs, mpp::fmtDate(r.oldStart), mpp::fmtDate(r.newStart), mpp::fmtDate(r.oldFinish), mpp::fmtDate(r.newFinish), r.startShift ? std::to_wstring(r.startShift) : L"", r.finishShift ? std::to_wstring(r.finishShift) : L"", join(r.changes, L", ") }, (LPARAM)i); }
    };
    fill();
    HWND bGo = dlgButton(d.h, L"Přejít na úkol", 12, 556, 110, 103), bCsv = dlgButton(d.h, L"Uložit CSV…", 130, 556, 110, 105), bClose = dlgButton(d.h, L"Zavřít", 900, 556, 88, IDCANCEL, true);
    d.onSize = [&](int w, int h) { MoveWindow(sum, S(12), S(10), w - S(24), S(18), TRUE); MoveWindow(lv, S(12), S(56), w - S(24), h - S(56) - S(44), TRUE); int by = h - S(34); MoveWindow(bGo, S(12), by, S(110), S(24), TRUE); MoveWindow(bCsv, S(130), by, S(110), S(24), TRUE); MoveWindow(bClose, w - S(100), by, S(88), S(24), TRUE); };
    auto go = [&]() { int i = ListView_GetNextItem(lv, -1, LVNI_SELECTED); if (i < 0) return; const DiffRow& r = rows[(size_t)lvParam(lv, i)]; if (r.newIdx >= 0) { d.result = g_prj.tasks[r.newIdx].uid + 1; d.done = true; } };
    d.onNotify = [&](NMHDR* nm) -> LRESULT { if (nm->hwndFrom == lv && nm->code == NM_DBLCLK) go(); return 0; };
    d.onCmd = [&](int id, int, HWND) {
        if (id == IDCANCEL) d.done = true; else if (id == 106) fill(); else if (id == 103) go();
        else if (id == 105) { wchar_t buf[MAX_PATH]; wcscpy(buf, (baseName() + L"-porovnani.csv").c_str()); if (saveDialog(buf, L"CSV\0*.csv\0", L"csv", L"Uložit porovnání do CSV")) { std::wstring s = L"Změna;ID;Úkol;WBS;Zahájení dříve;Zahájení nyní;Dokončení dříve;Dokončení nyní;Posun zač.;Posun dok.;Co se změnilo\r\n"; for (auto& r : rows) { int id2 = r.newIdx >= 0 ? g_prj.tasks[r.newIdx].id : older.tasks[r.oldIdx].id; s += std::wstring(KINDS[r.kind]) + L";" + std::to_wstring(id2) + L";" + csvCell(r.name) + L";" + r.wbs + L";" + mpp::fmtDate(r.oldStart) + L";" + mpp::fmtDate(r.newStart) + L";" + mpp::fmtDate(r.oldFinish) + L";" + mpp::fmtDate(r.newFinish) + L";" + std::to_wstring(r.startShift) + L";" + std::to_wstring(r.finishShift) + L";" + csvCell(join(r.changes, L", ")) + L"\r\n"; } try { writeUtf8(buf, s); } catch (...) { msgError(L"Soubor nelze zapsat."); } } }
    };
    RECT rc; GetClientRect(d.h, &rc); d.onSize(rc.right, rc.bottom);
    int r = dlgRun(d);
    if (r > 0) selectTaskByUid(r - 1);
}
