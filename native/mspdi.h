// mspdi.h - čtečka MS Project XML (MSPDI) přes XmlLite (součást Windows). Plní stejný model jako mpp.h.
// Součást knihovny mppread.dll - licence GNU LGPL 2.1 nebo pozdější (viz COPYING.LIB). Copyright (c) 2026 Milan Knor (M3K Soft).
// Několik převodních pravidel (měna v setinách, nákladové zdroje) odpovídá chování MPXJ (Packwood Software, LGPL 2.1+).
#pragma once
#include <objbase.h>
#include <xmllite.h>
#include <shlwapi.h>
#pragma comment(lib, "xmllite.lib")
#pragma comment(lib, "shlwapi.lib")

namespace mpp {

// Sekvenční průchod XML: událost 1 = začátek elementu, 2 = konec elementu (text = obsah listu), 0 = konec souboru
struct XmlWalker {
    IXmlReader* rd = nullptr; IStream* st = nullptr;
    std::vector<std::wstring> path; std::wstring text; bool leafClosed = false;
    ~XmlWalker() { if (rd) rd->Release(); if (st) st->Release(); }
    void open(const std::wstring& file) {
        if (FAILED(SHCreateStreamOnFileW(file.c_str(), STGM_READ | STGM_SHARE_DENY_WRITE, &st))) throw std::runtime_error("Soubor nelze otevrit");
        if (FAILED(CreateXmlReader(__uuidof(IXmlReader), (void**)&rd, nullptr))) throw std::runtime_error("XmlLite neni k dispozici");
        rd->SetProperty(XmlReaderProperty_DtdProcessing, DtdProcessing_Prohibit);
        if (FAILED(rd->SetInput(st))) throw std::runtime_error("XML nelze cist");
    }
    int next() {
        XmlNodeType nt;
        while (S_OK == rd->Read(&nt)) {
            if (nt == XmlNodeType_Element) {
                const wchar_t* ln = nullptr; rd->GetLocalName(&ln, nullptr);
                path.push_back(ln ? ln : L""); text.clear();
                leafClosed = rd->IsEmptyElement() != FALSE;
                return 1;
            }
            if (nt == XmlNodeType_Text || nt == XmlNodeType_CDATA) { const wchar_t* v = nullptr; UINT n = 0; rd->GetValue(&v, &n); if (v) text.append(v, n); }
            else if (nt == XmlNodeType_EndElement) return 2;
        }
        return 0;
    }
    void pop() { if (!path.empty()) path.pop_back(); }
    bool at(std::initializer_list<const wchar_t*> tail) const {
        if (tail.size() > path.size()) return false;
        size_t i = path.size() - tail.size();
        for (const wchar_t* t : tail) if (path[i++] != t) return false;
        return true;
    }
    const std::wstring& name() const { static std::wstring e; return path.empty() ? e : path.back(); }
    size_t depth() const { return path.size(); }
};

inline Time parseXmlDate(const std::wstring& s) { // 2006-08-23T08:00:00
    int y = 0, mo = 0, d = 0, hh = 0, mm = 0, ss = 0;
    if (swscanf(s.c_str(), L"%d-%d-%dT%d:%d:%d", &y, &mo, &d, &hh, &mm, &ss) < 3 || y < 1985 || mo < 1 || mo > 12 || d < 1 || d > 31) return 0;
    return makeTime(y, (unsigned)mo, (unsigned)d, (unsigned)hh, (unsigned)mm, (unsigned)ss);
}
inline int parseXmlDurationRaw(const std::wstring& s) { // PT8H0M0S -> desetiny minuty
    if (s.empty()) return -1;
    double h = 0, m = 0, sec = 0, cur = 0; bool num = false;
    for (const wchar_t* c = s.c_str(); *c; c++) {
        if (iswdigit(*c)) { cur = cur * 10 + (*c - L'0'); num = true; }
        else if (*c == L'.') { /* zlomky ignorujeme */ }
        else { if (num) { if (*c == L'H') h = cur; else if (*c == L'M') m = cur; else if (*c == L'S') sec = cur; } cur = 0; num = false; }
    }
    return (int)std::lround((h * 60 + m + sec / 60.0) * 10);
}
inline int xmlInt(const std::wstring& s) { return _wtoi(s.c_str()); }
inline double xmlDbl(const std::wstring& s) { return _wtof(s.c_str()); }
inline double xmlCur(const std::wstring& s) { return _wtof(s.c_str()) / 100.0; } // MSPDI ukládá měnu v setinách (jako MPXJ parseCurrency)
inline bool xmlBool(const std::wstring& s) { return s == L"1" || s == L"true"; }
inline int xmlClock(const std::wstring& s) { int hh = 0, mm = 0; swscanf(s.c_str(), L"%d:%d", &hh, &mm); return hh * 60 + mm; }

inline Project readMspdi(const std::wstring& path) {
    Project p; p.mppType = 0; p.format = L"XML (MSPDI)"; p.appName = L"Microsoft Project XML";
    XmlWalker x; x.open(path);
    Task t; t.uid = -1; Resource r; Assignment a; Calendar cal; CalException ex; Relation rel; int baselineN = -1; Baseline bl;
    int weekDayType = 0; bool weekDayWorking = false; int weekMinutes = 0; std::wstring weekText; int wtFrom = 0;
    int64_t exFrom = -1, exTo = -1; int exFieldId = 0; std::wstring exValue; int exDurFormat = 7;
    bool isBase = true; int calBase = -1; int calDefaultUid = -1; std::vector<CalException> recEx;

    auto onStart = [&]() {
        size_t d = x.depth();
        if (d == 3 && x.at({ L"Tasks", L"Task" })) { t = Task(); t.uid = -1; t.level = 1; return; }
        if (d == 3 && x.at({ L"Resources", L"Resource" })) { r = Resource(); r.uid = -1; return; }
        if (d == 3 && x.at({ L"Assignments", L"Assignment" })) { a = Assignment(); a.uid = -1; a.taskUid = -1; a.rscUid = -1; return; }
        if (d == 3 && x.at({ L"Calendars", L"Calendar" })) { cal = Calendar(); cal.uid = -1; isBase = true; calBase = -1; recEx.clear(); for (int i = 0; i < 7; i++) { cal.working[i] = false; cal.minutes[i] = 0; cal.hoursText[i].clear(); } return; }
        if (x.at({ L"WeekDays", L"WeekDay" })) { weekDayType = 0; weekDayWorking = false; weekMinutes = 0; weekText.clear(); exFrom = exTo = -1; return; }
        if (x.at({ L"Exceptions", L"Exception" })) { ex = CalException(); return; }
        if (x.at({ L"Task", L"PredecessorLink" })) { rel = Relation(); return; }
        if (x.at({ L"Task", L"Baseline" })) { baselineN = 0; bl = Baseline(); return; }
        if (x.at({ L"ExtendedAttribute" })) { exFieldId = 0; exValue.clear(); exDurFormat = 7; return; }
    };
    auto project = [&](const std::wstring& n, const std::wstring& v) {
        if (n == L"Name" || n == L"Title") { if (p.title.empty() || n == L"Title") p.title = v; return; }
        if (n == L"Subject") { p.subject = v; return; }
        if (n == L"Author") { p.author = v; return; }
        if (n == L"Manager") { p.manager = v; return; }
        if (n == L"Company") { p.company = v; return; }
        if (n == L"Category") { p.category = v; return; }
        if (n == L"CreationDate") { p.creationDate = parseXmlDate(v); return; }
        if (n == L"LastSaved") { p.lastSaved = parseXmlDate(v); return; }
        if (n == L"StartDate") { p.startDate = parseXmlDate(v); return; }
        if (n == L"FinishDate") { p.finishDate = parseXmlDate(v); return; }
        if (n == L"StatusDate") { p.statusDate = parseXmlDate(v); return; }
        if (n == L"CurrentDate") { p.currentDate = parseXmlDate(v); return; }
        if (n == L"ScheduleFromStart") { p.scheduleFromStart = xmlBool(v); return; }
        if (n == L"CriticalSlackLimit") { p.criticalSlackLimitDays = xmlInt(v); return; }
        if (n == L"CurrencySymbol") { p.currency = v; return; }
        if (n == L"MinutesPerDay") { p.minutesPerDay = xmlInt(v); if (p.minutesPerDay <= 0) p.minutesPerDay = 480; return; }
        if (n == L"MinutesPerWeek") { p.minutesPerWeek = xmlInt(v); if (p.minutesPerWeek <= 0) p.minutesPerWeek = 2400; return; }
        if (n == L"DaysPerMonth") { p.daysPerMonth = xmlInt(v); if (p.daysPerMonth <= 0) p.daysPerMonth = 20; return; }
        if (n == L"DurationFormat") { p.defaultUnits = unitsOf(xmlInt(v), 7); return; }
        if (n == L"CalendarUID") { calDefaultUid = xmlInt(v); return; }
    };
    auto calendar = [&](const std::wstring& n, const std::wstring& v) {
        if (x.at({ L"Calendar", L"UID" })) { cal.uid = xmlInt(v); return; }
        if (x.at({ L"Calendar", L"Name" })) { cal.name = v; return; }
        if (x.at({ L"Calendar", L"IsBaseCalendar" })) { isBase = xmlBool(v); return; }
        if (x.at({ L"Calendar", L"BaseCalendarUID" })) { calBase = xmlInt(v); return; }
        if (x.at({ L"WeekDay", L"DayType" })) { weekDayType = xmlInt(v); return; }
        if (x.at({ L"WeekDay", L"DayWorking" })) { weekDayWorking = xmlBool(v); return; }
        if (x.at({ L"WorkingTime", L"FromTime" })) { wtFrom = xmlClock(v); return; }
        if (x.at({ L"WeekDay", L"WorkingTimes", L"WorkingTime", L"ToTime" })) {
            int to = xmlClock(v); if (to == 0) to = 1440;
            if (to > wtFrom) { weekMinutes += to - wtFrom; wchar_t b[24]; swprintf(b, 24, L"%d:%02d–%d:%02d", wtFrom / 60, wtFrom % 60, to / 60, to % 60); if (!weekText.empty()) weekText += L", "; weekText += b; }
            return;
        }
        if (x.at({ L"WeekDay", L"TimePeriod", L"FromDate" })) { Time tt = parseXmlDate(v); if (tt) exFrom = toUnixDays(tt); return; }
        if (x.at({ L"WeekDay", L"TimePeriod", L"ToDate" })) { Time tt = parseXmlDate(v); if (tt) exTo = toUnixDays(tt); return; }
        if (x.at({ L"WeekDays", L"WeekDay" })) {
            if (weekDayType >= 1 && weekDayType <= 7) { int i = weekDayType - 1; cal.working[i] = weekDayWorking; cal.minutes[i] = weekDayWorking ? (weekMinutes > 0 ? weekMinutes : 480) : 0; cal.hoursText[i] = weekText; }
            else if (weekDayType == 0 && exFrom >= 0) { CalException e; e.from = exFrom; e.to = exTo >= 0 ? exTo : exFrom; e.working = weekDayWorking; e.minutes = weekMinutes; cal.exceptions.push_back(e); }
            return;
        }
        if (x.at({ L"Exception", L"TimePeriod", L"FromDate" })) { Time tt = parseXmlDate(v); if (tt) ex.from = toUnixDays(tt); return; }
        if (x.at({ L"Exception", L"TimePeriod", L"ToDate" })) { Time tt = parseXmlDate(v); if (tt) ex.to = toUnixDays(tt); return; }
        if (x.at({ L"Exception", L"Name" })) { ex.name = v; return; }
        if (x.at({ L"Exception", L"DayWorking" })) { ex.working = xmlBool(v); return; }
        if (x.at({ L"Exception", L"Type" })) { int ty = xmlInt(v); ex.rec = (ty >= 1 && ty <= 6) ? ty : 0; return; }
        if (x.at({ L"Exception", L"Period" })) { ex.freq = xmlInt(v); return; }
        if (x.at({ L"Exception", L"DaysOfWeek" })) { ex.weekBits = xmlInt(v); return; }
        if (x.at({ L"Exception", L"MonthDay" })) { ex.dayNumber = xmlInt(v); return; }
        if (x.at({ L"Exception", L"Month" })) { ex.month = xmlInt(v) + 1; return; }
        if (x.at({ L"Exception", L"MonthItem" })) { ex.dayOfWeek = xmlInt(v) - 2; return; }
        if (x.at({ L"Exception", L"MonthPosition" })) { ex.dayNumber = xmlInt(v) + 1; return; }
        if (x.at({ L"Exception", L"WorkingTimes", L"WorkingTime", L"ToTime" })) { int to = xmlClock(v); if (to > wtFrom) ex.minutes += to - wtFrom; return; }
        if (x.at({ L"Exceptions", L"Exception" })) { if (ex.rec == 1) ex.rec = ex.freq > 1 ? 7 : 0; if (ex.to < ex.from) ex.to = ex.from; if (ex.from > 0) recEx.push_back(ex); return; }
        if (x.depth() == 3 && x.at({ L"Calendars", L"Calendar" })) {
            // Project píše opakované výjimky i rozvinuté do WeekDay DayType=0 -> ty jsou přesné, z <Exceptions> bereme jen názvy.
            // ponytail: bez rozvinutých dnů se pravidla <Exceptions> mapují jen přibližně (denní/týdenní/měsíční)
            if (cal.exceptions.empty()) cal.exceptions = recEx;
            else for (CalException& e : cal.exceptions) for (const CalException& r : recEx) if (r.from <= e.from && e.to <= r.to) { e.name = r.name; break; }
            if (cal.uid >= 0 && (isBase || calBase <= 0)) p.calendars.push_back(cal); return;
        }
    };
    auto task = [&](const std::wstring& n, const std::wstring& v) {
        size_t d = x.depth();
        if (d == 4) {
            if (n == L"UID") { t.uid = xmlInt(v); return; }
            if (n == L"ID") { t.id = xmlInt(v); return; }
            if (n == L"Name") { t.name = v; t.nameSet = true; return; }
            if (n == L"IsNull") { if (xmlBool(v)) t.nameSet = false; return; }
            if (n == L"Type") { t.taskType = xmlInt(v); return; }
            if (n == L"Priority") { t.priority = xmlInt(v); return; }
            if (n == L"WBS") { t.wbs = v; return; }
            if (n == L"OutlineNumber") { t.outlineNumber = v; return; }
            if (n == L"OutlineLevel") { t.level = xmlInt(v); return; }
            if (n == L"Contact") { t.contact = v; return; }
            if (n == L"Start") { t.start = parseXmlDate(v); return; }
            if (n == L"Finish") { t.finish = parseXmlDate(v); return; }
            if (n == L"ActualStart") { t.actualStart = parseXmlDate(v); return; }
            if (n == L"ActualFinish") { t.actualFinish = parseXmlDate(v); return; }
            if (n == L"EarlyStart") { t.earlyStart = parseXmlDate(v); return; }
            if (n == L"EarlyFinish") { t.earlyFinish = parseXmlDate(v); return; }
            if (n == L"LateStart") { t.lateStart = parseXmlDate(v); return; }
            if (n == L"LateFinish") { t.lateFinish = parseXmlDate(v); return; }
            if (n == L"Deadline") { t.deadline = parseXmlDate(v); return; }
            if (n == L"CreateDate") { t.created = parseXmlDate(v); return; }
            if (n == L"ConstraintType") { t.constraintType = xmlInt(v); return; }
            if (n == L"ConstraintDate") { t.constraintDate = parseXmlDate(v); return; }
            if (n == L"Duration") { t.duration.raw = parseXmlDurationRaw(v); return; }
            if (n == L"DurationFormat") { t.duration.units = unitsOf(xmlInt(v), p.defaultUnits); t.actualDuration.units = t.remainingDuration.units = t.duration.units; return; }
            if (n == L"ActualDuration") { t.actualDuration.raw = parseXmlDurationRaw(v); return; }
            if (n == L"RemainingDuration") { t.remainingDuration.raw = parseXmlDurationRaw(v); return; }
            if (n == L"Work") { t.workMinutes = parseXmlDurationRaw(v) / 10.0; return; }
            if (n == L"ActualWork") { t.actualWorkMinutes = parseXmlDurationRaw(v) / 10.0; return; }
            if (n == L"RemainingWork") { t.remainingWorkMinutes = parseXmlDurationRaw(v) / 10.0; return; }
            if (n == L"Cost") { t.cost = xmlCur(v); return; }
            if (n == L"ActualCost") { t.actualCost = xmlCur(v); return; }
            if (n == L"RemainingCost") { t.remainingCost = xmlCur(v); return; }
            if (n == L"FixedCost") { t.fixedCost = xmlCur(v); return; }
            if (n == L"PercentComplete") { t.pctComplete = xmlInt(v); return; }
            if (n == L"PercentWorkComplete") { t.pctWork = xmlInt(v); return; }
            if (n == L"Milestone") { t.milestone = xmlBool(v); return; }
            if (n == L"Critical") { t.critical = xmlBool(v); t.criticalFromFile = true; return; }
            if (n == L"Active") { t.active = xmlBool(v); return; }
            if (n == L"Manual") { t.manual = xmlBool(v); return; }
            if (n == L"Estimated") { t.estimated = xmlBool(v); return; }
            if (n == L"EffortDriven") { t.effortDriven = xmlBool(v); return; }
            if (n == L"StartSlack") { t.startSlackRaw = xmlInt(v); return; }
            if (n == L"FinishSlack") { t.finishSlackRaw = xmlInt(v); return; }
            if (n == L"TotalSlack") { t.totalSlackRaw = xmlInt(v); return; }
            if (n == L"FreeSlack") { t.freeSlackRaw = xmlInt(v); return; }
            if (n == L"CalendarUID") { t.calendarUid = xmlInt(v); return; }
            if (n == L"Notes") { t.notes = v; return; }
            if (n == L"SubprojectName") { t.subproject = v; return; }
            if (n == L"PredecessorLink") { t.preds.push_back(rel); return; }
            if (n == L"Baseline") { if (baselineN >= 0 && baselineN <= 10) t.baselines[baselineN] = bl; return; }
            if (n == L"ExtendedAttribute") {
                if ((exFieldId & 0xFFFF0000) != 0x0B400000 || exValue.empty()) return;
                int id = exFieldId & 0xFFFF;
                for (const CustomDef& cd : customDefs()) if (cd.id == id) {
                    CustomValue cv; cv.set = true;
                    switch (cd.kind) {
                    case CK_TEXT: case CK_OUTLINE_CODE: cv.text = exValue; break;
                    case CK_NUMBER: case CK_COST: cv.num = cd.kind == CK_COST ? xmlCur(exValue) : xmlDbl(exValue); cv.set = std::fabs(cv.num) > 1e-9; break;
                    case CK_FLAG: cv.flag = xmlBool(exValue); cv.set = cv.flag; break;
                    case CK_DATE: case CK_START: case CK_FINISH: cv.date = parseXmlDate(exValue); cv.set = cv.date != 0; break;
                    case CK_DURATION: cv.dur.raw = parseXmlDurationRaw(exValue); cv.dur.units = unitsOf(exDurFormat, p.defaultUnits); cv.set = cv.dur.raw > 0; break;
                    }
                    if (cv.set) t.custom[id] = cv;
                    break;
                }
                return;
            }
            return;
        }
        if (x.at({ L"PredecessorLink", L"PredecessorUID" })) { rel.predUid = xmlInt(v); return; }
        if (x.at({ L"PredecessorLink", L"Type" })) { rel.type = xmlInt(v); return; }
        if (x.at({ L"PredecessorLink", L"LinkLag" })) { rel.lag.raw = xmlInt(v); rel.lag.units = 7; return; }
        if (x.at({ L"PredecessorLink", L"LagFormat" })) { rel.lag.units = unitsOf(xmlInt(v), 7); return; }
        if (x.at({ L"Baseline", L"Number" })) { baselineN = xmlInt(v); return; }
        if (x.at({ L"Baseline", L"Start" })) { bl.start = parseXmlDate(v); return; }
        if (x.at({ L"Baseline", L"Finish" })) { bl.finish = parseXmlDate(v); return; }
        if (x.at({ L"Baseline", L"Duration" })) { bl.duration.raw = parseXmlDurationRaw(v); return; }
        if (x.at({ L"Baseline", L"DurationFormat" })) { bl.duration.units = unitsOf(xmlInt(v), p.defaultUnits); return; }
        if (x.at({ L"Baseline", L"Work" })) { bl.workHours = parseXmlDurationRaw(v) / 600.0; return; }
        if (x.at({ L"Baseline", L"Cost" })) { bl.cost = xmlCur(v); return; }
        if (x.at({ L"ExtendedAttribute", L"FieldID" })) { exFieldId = xmlInt(v); return; }
        if (x.at({ L"ExtendedAttribute", L"Value" })) { exValue = v; return; }
        if (x.at({ L"ExtendedAttribute", L"DurationFormat" })) { exDurFormat = xmlInt(v); return; }
    };
    auto resource = [&](const std::wstring& n, const std::wstring& v) {
        if (x.depth() != 4) return;
        if (n == L"UID") { r.uid = xmlInt(v); return; }
        if (n == L"ID") { r.id = xmlInt(v); return; }
        if (n == L"Name") { r.name = v; return; }
        if (n == L"Type") { int ty = xmlInt(v); r.type = ty == 1 ? 0 : ty == 0 ? (r.type == 2 ? 2 : 1) : 2; return; }
        if (n == L"IsCostResource") { if (xmlBool(v)) r.type = 2; return; } // materiálový + IsCostResource = nákladový (jako MPXJ)
        if (n == L"Initials") { r.initials = v; return; }
        if (n == L"Group") { r.group = v; return; }
        if (n == L"EmailAddress") { r.email = v; return; }
        if (n == L"MaxUnits") { r.maxUnits = xmlDbl(v); return; }
        if (n == L"StandardRate") { r.stdRate = xmlDbl(v); return; }
        if (n == L"OvertimeRate") { r.ovtRate = xmlDbl(v); return; }
        if (n == L"CostPerUse") { r.costPerUse = xmlCur(v); return; }
        if (n == L"Work") { r.workHours = parseXmlDurationRaw(v) / 600.0; return; }
        if (n == L"Cost") { r.cost = xmlCur(v); return; }
    };
    auto assignment = [&](const std::wstring& n, const std::wstring& v) {
        if (x.depth() != 4) return;
        if (n == L"UID") { a.uid = xmlInt(v); return; }
        if (n == L"TaskUID") { a.taskUid = xmlInt(v); return; }
        if (n == L"ResourceUID") { a.rscUid = xmlInt(v); return; }
        if (n == L"Units") { a.units = xmlDbl(v); return; }
        if (n == L"Work") { a.workHours = parseXmlDurationRaw(v) / 600.0; return; }
        if (n == L"ActualWork") { a.actualWorkHours = parseXmlDurationRaw(v) / 600.0; return; }
        if (n == L"Cost") { a.cost = xmlCur(v); return; }
        if (n == L"Start") { a.start = parseXmlDate(v); return; }
        if (n == L"Finish") { a.finish = parseXmlDate(v); return; }
    };
    auto onEnd = [&]() {
        const std::wstring& n = x.name(); const std::wstring& v = x.text;
        size_t d = x.depth();
        if (d == 2) { project(n, v); return; }
        if (d >= 2 && x.path[1] == L"Tasks") {
            if (d == 3) { if (t.uid >= 0) { if (!t.nameSet) t.isNull = true; p.tasks.push_back(t); } t.uid = -1; return; }
            task(n, v); return;
        }
        if (d >= 2 && x.path[1] == L"Resources") {
            if (d == 3) { if (r.uid > 0 && !r.name.empty()) { p.rscIndex[r.uid] = (int)p.resources.size(); p.resources.push_back(r); } return; }
            resource(n, v); return;
        }
        if (d >= 2 && x.path[1] == L"Assignments") {
            if (d == 3) { if (a.taskUid >= 0 && a.rscUid > 0) p.assignments.push_back(a); return; }
            assignment(n, v); return;
        }
        if (d >= 2 && x.path[1] == L"Calendars") { calendar(n, v); return; }
        if (d >= 2 && x.path[1] == L"ExtendedAttributes") {
            if (d == 4 && n == L"FieldID") { exFieldId = xmlInt(v); return; }
            if (d == 4 && n == L"Alias") { exValue = v; return; }
            if (d == 3 && n == L"ExtendedAttribute") { if ((exFieldId & 0xFFFF0000) == 0x0B400000 && !exValue.empty()) p.aliases[exFieldId & 0xFFFF] = exValue; return; }
        }
    };

    int ev;
    while ((ev = x.next()) != 0) {
        if (ev == 1) { onStart(); if (x.leafClosed) { x.leafClosed = false; x.pop(); } continue; }
        onEnd();
        x.pop();
    }
    if (calDefaultUid >= 0) for (size_t i = 0; i < p.calendars.size(); i++) if (p.calendars[i].uid == calDefaultUid) p.defaultCalendar = (int)i;
    if (p.defaultCalendar < 0 && !p.calendars.empty()) p.defaultCalendar = 0;
    finalizeProject(p);
    return p;
}

} // namespace mpp
