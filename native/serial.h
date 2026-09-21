// serial.h - binární přenos modelu Project mezi mppread.dll a aplikací (obě strany mají vlastní CRT/haldu,
// proto se přes hranici DLL nepředávají C++ objekty, jen bajty). Vkládá se do namespace mpp na konci model.h.
// Copyright (c) 2026 Milan Knor (M3K Soft). Licence MIT (viz LICENSE-MIT.txt).
// POZOR: při přidání pole do modelu ho doplň i sem a zvyš SERIAL_VERSION.

static const uint32_t SERIAL_MAGIC = 0x504B334D; // "M3KP"
static const uint32_t SERIAL_VERSION = 2; // 2: Calendar::days

struct SerOut { std::vector<uint8_t> b; static const bool reading = false; void raw(void* p, size_t n) { const uint8_t* c = (const uint8_t*)p; b.insert(b.end(), c, c + n); } };
struct SerIn {
    const uint8_t* p; size_t n, pos = 0; static const bool reading = true;
    SerIn(const uint8_t* data, size_t size) : p(data), n(size) {}
    void raw(void* d, size_t k) { if (k > n - pos) throw std::runtime_error("Poskozena data z mppread.dll"); memcpy(d, p + pos, k); pos += k; }
    size_t left() const { return n - pos; }
};

template<class A, class T> typename std::enable_if<std::is_arithmetic<T>::value || std::is_enum<T>::value>::type io(A& a, T& v) { a.raw(&v, sizeof v); }
template<class A> uint32_t ioCount(A& a, size_t cur, size_t minItemBytes) {
    uint32_t c = (uint32_t)cur; a.raw(&c, 4);
    if constexpr (A::reading) { if ((size_t)c * minItemBytes > a.left()) throw std::runtime_error("Poskozena data z mppread.dll"); }
    return c;
}
inline void io(SerOut& a, std::wstring& s) { uint32_t c = ioCount(a, s.size(), 2); if (c) a.raw(&s[0], c * 2); }
inline void io(SerIn& a, std::wstring& s) { uint32_t c = ioCount(a, 0, 2); s.assign(c, L'\0'); if (c) a.raw(&s[0], c * 2); }
template<class A, class T, size_t N> void io(A& a, T (&arr)[N]) { for (T& x : arr) io(a, x); }
template<class A, class T> void io(A& a, std::vector<T>& v) {
    uint32_t c = ioCount(a, v.size(), 1);
    if constexpr (A::reading) v.resize(c);
    for (T& x : v) io(a, x);
}
template<class A, class K, class V> void io(A& a, std::map<K, V>& m) {
    uint32_t c = ioCount(a, m.size(), sizeof(K));
    if constexpr (A::reading) { m.clear(); for (uint32_t i = 0; i < c; i++) { K k{}; V v{}; io(a, k); io(a, v); m.emplace(k, std::move(v)); } }
    else for (auto& kv : m) { K k = kv.first; io(a, k); io(a, kv.second); }
}

template<class A> void io(A& a, Duration& d) { io(a, d.raw); io(a, d.units); }
template<class A> void io(A& a, Relation& r) { io(a, r.predUid); io(a, r.type); io(a, r.lag); }
template<class A> void io(A& a, Assignment& x) { io(a, x.uid); io(a, x.taskUid); io(a, x.rscUid); io(a, x.units); io(a, x.workHours); io(a, x.actualWorkHours); io(a, x.cost); io(a, x.start); io(a, x.finish); }
template<class A> void io(A& a, Resource& r) {
    io(a, r.uid); io(a, r.id); io(a, r.name); io(a, r.initials); io(a, r.group); io(a, r.email); io(a, r.type);
    io(a, r.maxUnits); io(a, r.stdRate); io(a, r.ovtRate); io(a, r.costPerUse); io(a, r.workHours); io(a, r.cost); io(a, r.assignments);
}
template<class A> void io(A& a, Baseline& b) { io(a, b.start); io(a, b.finish); io(a, b.duration); io(a, b.workHours); io(a, b.cost); }
template<class A> void io(A& a, CustomValue& v) { io(a, v.text); io(a, v.num); io(a, v.date); io(a, v.flag); io(a, v.dur); io(a, v.set); }
template<class A> void io(A& a, CustomField& f) { io(a, f.kind); io(a, f.n); io(a, f.id); io(a, f.alias); }
template<class A> void io(A& a, Task& t) {
    io(a, t.uid); io(a, t.id); io(a, t.name); io(a, t.wbs); io(a, t.notes); io(a, t.resourceNames); io(a, t.outlineNumber); io(a, t.contact); io(a, t.calendarName); io(a, t.subproject);
    io(a, t.nameSet); io(a, t.level);
    io(a, t.start); io(a, t.finish); io(a, t.actualStart); io(a, t.actualFinish); io(a, t.deadline); io(a, t.created); io(a, t.constraintDate);
    io(a, t.earlyStart); io(a, t.earlyFinish); io(a, t.lateStart); io(a, t.lateFinish);
    io(a, t.duration); io(a, t.actualDuration); io(a, t.remainingDuration); io(a, t.baselines);
    io(a, t.pctComplete); io(a, t.pctWork); io(a, t.constraintType); io(a, t.priority); io(a, t.taskType);
    io(a, t.workMinutes); io(a, t.actualWorkMinutes); io(a, t.remainingWorkMinutes); io(a, t.cost); io(a, t.actualCost); io(a, t.remainingCost); io(a, t.fixedCost);
    io(a, t.startSlackRaw); io(a, t.finishSlackRaw); io(a, t.freeSlackRaw); io(a, t.totalSlackRaw); io(a, t.calendarUid);
    io(a, t.milestone); io(a, t.summary); io(a, t.manual); io(a, t.active); io(a, t.expanded); io(a, t.isNull); io(a, t.estimated); io(a, t.critical); io(a, t.effortDriven); io(a, t.criticalFromFile);
    io(a, t.parentUid); io(a, t.parent); io(a, t.children); io(a, t.preds); io(a, t.assignments); io(a, t.custom);
}
template<class A> void io(A& a, CalException& e) { io(a, e.from); io(a, e.to); io(a, e.working); io(a, e.rec); io(a, e.freq); io(a, e.dayNumber); io(a, e.dayOfWeek); io(a, e.month); io(a, e.weekBits); io(a, e.minutes); io(a, e.name); }
template<class A> void io(A& a, DayException& x) { io(a, x.day); io(a, x.ex); }
template<class A> void io(A& a, Calendar& c) { io(a, c.uid); io(a, c.name); io(a, c.baseUid); io(a, c.working); io(a, c.minutes); io(a, c.hoursText); io(a, c.exceptions); io(a, c.days); }
template<class A> void io(A& a, Project& p) {
    io(a, p.mppType); io(a, p.title); io(a, p.filePath); io(a, p.author); io(a, p.manager); io(a, p.company); io(a, p.subject); io(a, p.comments); io(a, p.keywords); io(a, p.category); io(a, p.currency); io(a, p.format); io(a, p.appName);
    io(a, p.appVersion);
    io(a, p.startDate); io(a, p.finishDate); io(a, p.statusDate); io(a, p.currentDate); io(a, p.creationDate); io(a, p.lastSaved); io(a, p.baselineDate);
    io(a, p.minutesPerDay); io(a, p.minutesPerWeek); io(a, p.daysPerMonth); io(a, p.defaultUnits); io(a, p.criticalSlackLimitDays);
    io(a, p.encrypted); io(a, p.scheduleFromStart);
    io(a, p.tasks); io(a, p.roots); io(a, p.uidIndex); io(a, p.resources); io(a, p.rscIndex); io(a, p.assignments); io(a, p.calendars);
    io(a, p.defaultCalendar); io(a, p.builtin); io(a, p.customFields); io(a, p.baselinesUsed); io(a, p.aliases);
}

inline std::vector<uint8_t> serializeProject(Project& p) {
    SerOut o; uint32_t magic = SERIAL_MAGIC, ver = SERIAL_VERSION; io(o, magic); io(o, ver); io(o, p); return std::move(o.b);
}
inline Project deserializeProject(const uint8_t* data, size_t size) {
    SerIn in(data, size); uint32_t magic = 0, ver = 0; io(in, magic); io(in, ver);
    if (magic != SERIAL_MAGIC || ver != SERIAL_VERSION) throw std::runtime_error("Nekompatibilni verze mppread.dll");
    Project p; io(in, p);
    if (in.left() != 0) throw std::runtime_error("Poskozena data z mppread.dll");
    // kontrola konzistence indexů (data z nahraditelné knihovny se neberou naslepo)
    for (auto& kv : p.uidIndex) if (kv.second < 0 || kv.second >= (int)p.tasks.size()) throw std::runtime_error("Neplatny index ukolu z mppread.dll");
    for (auto& kv : p.rscIndex) if (kv.second < 0 || kv.second >= (int)p.resources.size()) throw std::runtime_error("Neplatny index zdroje z mppread.dll");
    for (int r : p.roots) if (r < 0 || r >= (int)p.tasks.size()) throw std::runtime_error("Neplatny index ukolu z mppread.dll");
    for (auto& t : p.tasks) {
        if (t.parent >= (int)p.tasks.size() || t.parent < -1) throw std::runtime_error("Neplatny index ukolu z mppread.dll");
        for (int c : t.children) if (c < 0 || c >= (int)p.tasks.size()) throw std::runtime_error("Neplatny index ukolu z mppread.dll");
        for (int ai : t.assignments) if (ai < 0 || ai >= (int)p.assignments.size()) throw std::runtime_error("Neplatny index prirazeni z mppread.dll");
    }
    for (auto& r : p.resources) for (int ai : r.assignments) if (ai < 0 || ai >= (int)p.assignments.size()) throw std::runtime_error("Neplatny index prirazeni z mppread.dll");
    auto checkCal = [](const Calendar& c) { for (size_t i = 0; i < c.days.size(); i++) if ((i && c.days[i].day <= c.days[i - 1].day) || c.days[i].ex < 0 || c.days[i].ex >= (int)c.exceptions.size()) throw std::runtime_error("Neplatny kalendar z mppread.dll"); };
    for (auto& c : p.calendars) checkCal(c); checkCal(p.builtin);
    if (p.defaultCalendar < -1 || p.defaultCalendar >= (int)p.calendars.size()) throw std::runtime_error("Neplatny kalendar z mppread.dll");
    return p;
}
