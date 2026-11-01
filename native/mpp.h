// mpp.h - čtečka souborů MS Project .mpp ve formátech MPP12 (Project 2007) a MPP14 (Project 2010 / 2013 / 2016 / 2019 / 2021 / 365).
// Součást knihovny mppread.dll. Licence: GNU LGPL 2.1 nebo (dle vaší volby) pozdější verze (viz COPYING.LIB).
//
// Tento soubor je upravené dílo odvozené z knihovny MPXJ (https://github.com/joniles/mpxj, stav repozitáře
// c5e1320cde0acd404031495aa66c17639a57239b ze 17. 9. 2026), zejména ze souborů (autor Jon Iles):
//   org/mpxj/mpp/MPPReader, MPP12Reader, MPP14Reader, MPPUtility, Props, Props12, Props14, FixedMeta, FixedData,
//   VarMeta, VarMeta12, Var2Data, FieldMap, FieldMap12, FieldMap14, CustomFieldReader12, CustomFieldReader14,
//   AbstractCalendarFactory, MPP12CalendarFactory, MPP14CalendarFactory, MppBitFlag;
//   org/mpxj/TaskContainer, Task, ProjectCalendar, RecurringData; org/mpxj/mspdi/MSPDIReader, DatatypeConverter.
// Původní oznámení: Copyright (c) Packwood Software 2002-2022.
//   "This library is free software; you can redistribute it and/or modify it under the terms of the GNU Lesser
//    General Public License as published by the Free Software Foundation; either version 2.1 of the License,
//    or (at your option) any later version. This library is distributed in the hope that it will be useful, but
//    WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR
//    PURPOSE. See the GNU Lesser General Public License for more details."
//
// Změny (M3K Soft):
//   2026-09-21  převod vybraných částí z Javy do C++ (čtení MPP12/MPP14), vlastní datový model (model.h),
//               zjednodušení na potřeby prohlížeče; dopočet kritičnosti ručních odhadovaných úkolů;
//               jednotky a měna podle MS Project; rozvinutí výjimek kalendáře na dny (expandCalendar).
//   2026-10-09  úpravy podle analýzy kódu MSVC: kontrola výsledku swscanf (mspdi.h, xmlClock),
//               potlačení varování u záměrného zachycení pádu čtečky (mppread.cpp).
// Úpravy: Copyright (c) 2026 Milan Knor (M3K Soft).
// Čte: vlastnosti projektu, úkoly (hierarchie, termíny, doby, %, milníky, směrné plány 0-10, rezervy, priorita, typ, náklady, práce,
// vlastní pole s aliasy, poznámky), vazby, zdroje a přiřazení, kalendáře (pracovní dny + výjimky).
#pragma once
#include "model.h"
#include "cfb.h"
#include <functional>
#include <unordered_map>

namespace mpp {

typedef std::vector<uint8_t> Bytes;

// ---------- čtení bajtů (vše bounds-checked, mimo rozsah = 0) ----------
inline uint16_t u16(const Bytes& d, size_t o) { return o + 2 <= d.size() ? (uint16_t)(d[o] | (d[o + 1] << 8)) : 0; }
inline uint8_t u8(const Bytes& d, size_t o) { return o < d.size() ? d[o] : 0; }
inline uint32_t u32(const Bytes& d, size_t o) { return o + 4 <= d.size() ? (uint32_t)(d[o] | (d[o + 1] << 8) | (d[o + 2] << 16) | ((uint32_t)d[o + 3] << 24)) : 0; }
inline int32_t i32(const Bytes& d, size_t o) { return (int32_t)u32(d, o); }
inline int64_t i64(const Bytes& d, size_t o) { return o + 8 <= d.size() ? (int64_t)((uint64_t)u32(d, o) | ((uint64_t)u32(d, o + 4) << 32)) : 0; }
inline double f64(const Bytes& d, size_t o) {
    if (o + 8 > d.size()) return 0;
    double v; uint64_t bits = (uint64_t)i64(d, o); memcpy(&v, &bits, 8);
    return std::isnan(v) ? 0 : v;
}
inline Bytes sub(const Bytes& d, size_t o, size_t n) {
    if (o >= d.size()) return Bytes();
    if (n > d.size() - o) n = d.size() - o;
    return Bytes(d.begin() + o, d.begin() + o + n);
}
inline std::wstring ustr(const Bytes& d, size_t o = 0) {
    std::wstring s;
    for (size_t i = o; i + 1 < d.size(); i += 2) {
        wchar_t c = (wchar_t)(d[i] | (d[i + 1] << 8));
        if (c == 0) break;
        s.push_back(c);
    }
    return s;
}
inline std::string astr(const Bytes& d, size_t o, size_t n) {
    std::string s;
    for (size_t i = o; i < o + n && i < d.size() && d[i]; i++) s.push_back((char)d[i]);
    return s;
}
// Časové razítko MPP: 2 B čas (desetiny minuty od půlnoci) + 2 B dny od 31.12.1983
inline Time timestamp(const Bytes& d, size_t o) {
    if (o + 4 > d.size()) return 0;
    uint32_t days = u16(d, o + 2);
    if (days <= 1 || days == 65535) return 0;
    uint32_t t = u16(d, o);
    if (t == 65535) t = 0;
    if (days < 100 && (t % 10) != 0) return 0; // heuristika MPXJ: malé hodnoty dnů se sekundami = N/A
    return (Time)days * 86400 + (Time)t * 6;
}
// Datum bez času: 2 B dny od 31.12.1983 -> unixové dny; -1 = neuvedeno
inline int64_t dateDays(const Bytes& d, size_t o) { uint32_t v = u16(d, o); return v == 65535 ? -1 : (int64_t)v + EPOCH_UNIX_DAYS; }

// ---------- poznámky: převod RTF na prostý text ----------
inline std::wstring rtfToText(const std::string& s) {
    if (s.compare(0, 5, "{\\rtf") != 0) return ansiToWide(s, CP_ACP);
    struct G { bool skip; int uc; };
    std::vector<G> st; G cur = { false, 1 };
    int cp = 1252;
    std::wstring out; std::string pend; int skipChars = 0;
    auto flush = [&]() { if (!pend.empty()) { out += ansiToWide(pend, cp); pend.clear(); } };
    static const char* skipDest[] = { "fonttbl", "colortbl", "stylesheet", "info", "pict", "object", "header", "footer", "themedata", "colorschememapping", "latentstyles", "datastore", "xmlnstbl", "rsidtbl", "generator", "listtable", "listoverridetable", "pntext", "fldinst", "userprops", "mmathPr", "docvar" };
    size_t i = 0, n = s.size();
    while (i < n) {
        char c = s[i];
        if (c == '{') { st.push_back(cur); i++; continue; }
        if (c == '}') { if (!st.empty()) { cur = st.back(); st.pop_back(); } i++; continue; }
        if (c == '\\') {
            i++; if (i >= n) break;
            char k = s[i];
            if (k == '\'') {
                int v = 0; if (i + 2 < n) { char h[3] = { s[i + 1], s[i + 2], 0 }; v = (int)strtol(h, nullptr, 16); }
                i += 3;
                if (skipChars > 0) { skipChars--; continue; }
                if (!cur.skip) pend.push_back((char)v);
                continue;
            }
            if (k == '*') { cur.skip = true; i++; continue; }
            if (!isalpha((unsigned char)k)) {
                i++;
                if (cur.skip) continue;
                if (k == '{' || k == '}' || k == '\\') pend.push_back(k);
                else if (k == '~') { flush(); out.push_back(L' '); }
                else if (k == '_') pend.push_back('-');
                else if (k == '\n' || k == '\r') { flush(); out.push_back(L'\n'); }
                continue;
            }
            std::string word; while (i < n && isalpha((unsigned char)s[i])) word.push_back(s[i++]);
            bool hasNum = false; long num = 0;
            if (i < n && (s[i] == '-' || isdigit((unsigned char)s[i]))) { hasNum = true; size_t j = i; num = strtol(s.c_str() + i, nullptr, 10); if (s[j] == '-') j++; while (j < n && isdigit((unsigned char)s[j])) j++; i = j; }
            if (i < n && s[i] == ' ') i++;
            if (word == "ansicpg" && hasNum) cp = (int)num;
            else if (word == "uc" && hasNum) cur.uc = (int)num;
            else if (cur.skip) continue;
            else if (word == "par" || word == "line") { flush(); out.push_back(L'\n'); }
            else if (word == "tab") { flush(); out.push_back(L'\t'); }
            else if (word == "u" && hasNum) { flush(); out.push_back((wchar_t)(num < 0 ? num + 65536 : num)); skipChars = cur.uc; }
            else { for (const char* d : skipDest) if (word == d) { cur.skip = true; break; } }
            continue;
        }
        i++;
        if (c == '\r' || c == '\n') continue;
        if (skipChars > 0) { skipChars--; continue; }
        if (!cur.skip) pend.push_back(c);
    }
    flush();
    while (!out.empty() && (out.back() == L'\n' || out.back() == L' ')) out.pop_back();
    return out;
}

// ---------- Props (vlastnosti) ----------
struct Props {
    std::map<uint32_t, Bytes> m;
    static Props parse(const Bytes& d) { // shodný formát pro Props12 i Props14
        Props p;
        if (d.size() < 16) return p;
        int count = u16(d, 12), found = 0;
        size_t pos = 16;
        while (found < count) {
            if (d.size() - pos < 12) break;
            int32_t size = i32(d, pos); uint32_t key = u32(d, pos + 4);
            pos += 12;
            if (size < 1 || (size_t)size > d.size() - pos) break;
            p.m[key] = sub(d, pos, (size_t)size);
            pos += (size_t)size;
            found++;
            if (size % 2) pos++;
        }
        return p;
    }
    const Bytes* get(uint32_t k) const { auto it = m.find(k); return it == m.end() ? nullptr : &it->second; }
    uint8_t byte(uint32_t k) const { const Bytes* b = get(k); return b && !b->empty() ? (*b)[0] : 0; }
    int shortv(uint32_t k) const { const Bytes* b = get(k); return b ? u16(*b, 0) : 0; }
    int intv(uint32_t k) const { const Bytes* b = get(k); return b ? i32(*b, 0) : 0; }
    Time ts(uint32_t k) const { const Bytes* b = get(k); return b ? timestamp(*b, 0) : 0; }
    std::wstring str(uint32_t k) const { const Bytes* b = get(k); return b ? ustr(*b) : L""; }
};
enum PropsKey : uint32_t {
    PK_PASSWORD_FLAG = 893386752, PK_PROTECTION_PASSWORD_HASH = 893386756, PK_ENCRYPTION_CODE = 893386759, PK_PROJECT_FILE_PATH = 893386760,
    PK_TASK_FIELD_MAP = 131092, PK_TASK_FIELD_MAP2 = 50331668, PK_RESOURCE_FIELD_MAP = 131093, PK_RESOURCE_FIELD_MAP2 = 50331669,
    PK_ASSIGNMENT_FIELD_MAP = 131095, PK_ASSIGNMENT_FIELD_MAP2 = 50331671, PK_CUSTOM_FIELDS = 71303169,
    PK_PROJECT_START_DATE = 37748738, PK_PROJECT_FINISH_DATE = 37748739, PK_STATUS_DATE = 37748805, PK_TITLE = 37748744, PK_SUBJECT = 37748745,
    PK_DURATION_UNITS = 37748757, PK_MINUTES_PER_DAY = 37748765, PK_MINUTES_PER_WEEK = 37748766, PK_DAYS_PER_MONTH = 37753743,
    PK_CRITICAL_SLACK_LIMIT = 37748756, PK_DEFAULT_CALENDAR_HOURS = 37753736, PK_DEFAULT_CALENDAR_NAME = 37748750,
    PK_AUTHOR = 37748748, PK_MANAGER = 37748737, PK_COMPANY = 37748736, PK_COMMENTS = 37748741, PK_KEYWORDS = 37748749, PK_CATEGORY = 37748809,
    PK_CURRENCY_SYMBOL = 37748752, PK_CURRENCY_CODE = 37753787, PK_BASELINE_DATE = 37753749, PK_SCHEDULE_FROM = 37748740,
};

// ---------- FixedMeta / FixedData ----------
struct FixedMeta {
    int itemCount = 0;                 // počet z hlavičky
    std::vector<Bytes> items;          // skutečně načtené položky
    static FixedMeta parse(const Bytes& d, int itemSize) {
        FixedMeta m;
        if (d.size() < 16 || u32(d, 0) != 0xFADFADBA) throw std::runtime_error("FixedMeta: neplatna hlavicka");
        m.itemCount = i32(d, 8);
        size_t adjusted = (d.size() - 16) / (size_t)itemSize;
        m.items.reserve(adjusted);
        for (size_t i = 0; i < adjusted; i++) m.items.push_back(sub(d, 16 + i * itemSize, itemSize));
        return m;
    }
    static FixedMeta parseHeuristic(const Bytes& d, size_t otherCount, std::initializer_list<int> sizes) {
        int itemSize = *sizes.begin();
        int available = (int)d.size() - 16;
        int headerCount = i32(d, 8);
        int distance = INT32_MIN;
        for (int s : sizes) {
            if (available % s == 0) {
                if (available / s == (int)otherCount) { itemSize = s; break; }
                int td = headerCount * s - available;
                if (td <= 0 && td > distance) { itemSize = s; distance = td; }
            }
        }
        return parse(d, itemSize);
    }
    const Bytes* get(int i) const { return i >= 0 && i < (int)items.size() ? &items[i] : nullptr; }
};

struct FixedData {
    std::vector<Bytes> items;
    std::vector<int> offsets; // -1 = položka chybí
    static FixedData fromMeta(const FixedMeta& meta, const Bytes& buf, int maxExpected = 0, int minSize = 0) {
        FixedData f;
        size_t n = meta.items.size();
        f.items.resize(n); f.offsets.assign(n, -1);
        // položky platného souboru se nepřekrývají; poškozený soubor s překryvy by jinak nakopíroval desítky GB
        size_t total = 0; const size_t budget = buf.size() * 4 + ((size_t)64 << 20);
        for (size_t loop = 0; loop < n; loop++) {
            int itemOffset = i32(meta.items[loop], 4);
            if (itemOffset < 0 || (size_t)itemOffset > buf.size()) continue;
            int itemSize = (loop + 1 == n) ? (int)buf.size() - itemOffset : i32(meta.items[loop + 1], 4) - itemOffset;
            if (itemSize == 0) itemSize = minSize;
            int available = (int)buf.size() - itemOffset;
            if (itemSize < 0 || itemSize > available) itemSize = maxExpected == 0 ? available : std::min(maxExpected, available);
            if (maxExpected != 0 && itemSize > maxExpected) itemSize = maxExpected;
            if (itemSize > 0) { if ((total += (size_t)itemSize) > budget) break; f.items[loop] = sub(buf, itemOffset, itemSize); f.offsets[loop] = itemOffset; }
        }
        return f;
    }
    static FixedData fromMetaFixedSize(const FixedMeta& meta, const Bytes& buf, int itemSize) {
        FixedData f;
        size_t n = meta.items.size();
        f.items.resize(n); f.offsets.assign(n, -1);
        for (size_t loop = 0; loop < n; loop++) {
            int itemOffset = i32(meta.items[loop], 4);
            if (itemOffset < 0 || (size_t)itemOffset > buf.size()) continue;
            int available = (int)buf.size() - itemOffset;
            if (itemSize < 0 || itemSize > available) itemSize = available; // záměrně trvale (shodně s MPXJ)
            f.items[loop] = sub(buf, itemOffset, itemSize); f.offsets[loop] = itemOffset;
        }
        return f;
    }
    static FixedData chunks(const Bytes& buf, int itemSize) {
        FixedData f;
        size_t n = buf.size() / (size_t)itemSize;
        for (size_t i = 0; i < n; i++) { f.items.push_back(sub(buf, i * itemSize, itemSize)); f.offsets.push_back((int)(i * itemSize)); }
        return f;
    }
    const Bytes* get(int i) const { return i >= 0 && i < (int)items.size() && offsets[i] >= 0 ? &items[i] : nullptr; }
    // index podle offsetu: mapa se postaví při prvním dotazu (dřív lineární hledání pro každou vazbu a přiřazení = O(n²))
    mutable std::unordered_map<int, int> byOffset;
    int indexFromOffset(int off) const {
        if (byOffset.empty()) for (size_t i = offsets.size(); i-- > 0;) if (offsets[i] >= 0) byOffset[offsets[i]] = (int)i; // pozpátku: při shodě platí první
        auto it = byOffset.find(off); return it == byOffset.end() ? -1 : it->second;
    }
};

// ---------- VarMeta / Var2Data ----------
struct VarMeta {
    int itemCount = 0, dataSize = 0;
    std::map<int, std::map<int, int>> table; // uid -> (typ -> offset)
    std::vector<int> offsets;
    static VarMeta parse(const Bytes& d) {
        VarMeta m;
        uint32_t magic = u32(d, 0);
        if (magic != 0 && magic != 0xFADFADBA) throw std::runtime_error("VarMeta: neplatna hlavicka");
        m.itemCount = i32(d, 8); m.dataSize = i32(d, 20);
        size_t pos = 24;
        for (int i = 0; i < m.itemCount; i++) {
            if (d.size() < pos + 12) break;
            int uid = i32(d, pos), off = i32(d, pos + 4), type = u16(d, pos + 8);
            pos += 12;
            m.table[uid][type] = off;
            m.offsets.push_back(off);
        }
        std::sort(m.offsets.begin(), m.offsets.end());
        return m;
    }
    int offset(int uid, int type) const {
        auto it = table.find(uid); if (it == table.end()) return -1;
        auto jt = it->second.find(type); return jt == it->second.end() ? -1 : jt->second;
    }
    bool has(int uid) const { auto it = table.find(uid); return it != table.end() && !it->second.empty(); }
};

struct Var2Data {
    std::map<int, Bytes> m;
    const VarMeta* meta = nullptr;
    static Var2Data parse(const VarMeta& meta, const Bytes& d) {
        Var2Data v; v.meta = &meta;
        size_t total = 0; const size_t budget = d.size() * 4 + ((size_t)64 << 20); // viz FixedData::fromMeta – překryvy jen v poškozeném souboru
        for (int off : meta.offsets) {
            if (off < 0 || (size_t)off >= d.size()) continue;
            if (v.m.count(off)) continue;
            int size = i32(d, off);
            if (size < 0 || (size_t)size > d.size() - off - 4) continue;
            if ((total += (size_t)size) > budget) break;
            v.m[off] = sub(d, off + 4, size);
        }
        return v;
    }
    const Bytes* get(int uid, int type) const {
        if (!meta) return nullptr;
        int off = meta->offset(uid, type); if (off < 0) return nullptr;
        auto it = m.find(off); return it == m.end() ? nullptr : &it->second;
    }
};

// ---------- mapa polí (kde v blocích leží která hodnota) ----------
enum Loc { L_UNKNOWN, L_FIXED, L_VAR, L_META };
struct FieldItem { Loc loc = L_UNKNOWN; int block = 0; int offset = 0; int varKey = 0; };
// ID polí úkolu podle MS Project (viz MPXJ MPPTaskField)
enum TaskFieldId {
    F_WORK = 0, F_BASELINE_WORK = 1, F_ACTUAL_WORK = 2, F_REMAINING_WORK = 4, F_COST = 5, F_BASELINE_COST = 6, F_ACTUAL_COST = 7, F_FIXED_COST = 8, F_REMAINING_COST = 10,
    F_NAME = 14, F_NOTES = 15, F_WBS = 16, F_CONSTRAINT_TYPE = 17, F_CONSTRAINT_DATE = 18, F_FREE_SLACK = 21, F_ID = 23, F_PRIORITY = 25,
    F_BASELINE_DURATION = 27, F_ACTUAL_DURATION = 28, F_DURATION = 29, F_REMAINING_DURATION = 31, F_PCT_COMPLETE = 32, F_PCT_WORK = 33,
    F_START = 35, F_FINISH = 36, F_EARLY_START = 37, F_EARLY_FINISH = 38, F_LATE_START = 39, F_LATE_FINISH = 40,
    F_ACTUAL_START = 41, F_ACTUAL_FINISH = 42, F_BASELINE_START = 43, F_BASELINE_FINISH = 44, F_OUTLINE_LEVEL_OLD = 85, F_UNIQUE_ID = 86,
    F_CREATED = 93, F_CONTACT = 112, F_TYPE = 128, F_PARENT_UID = 160, F_BASELINE_DURATION_UNITS = 179, F_ACTUAL_DURATION_UNITS = 181, F_OUTLINE_LEVEL = 249,
    F_CALENDAR_UID = 401, F_DEADLINE = 437, F_START_SLACK = 438, F_FINISH_SLACK = 439,
    F_TASK_START = 1283, F_TASK_FINISH = 1284, F_MANUAL_DURATION = 1288, F_MANUAL_DURATION_UNITS = 1289,
};
enum RscFieldId { R_ID = 0, R_NAME = 1, R_INITIALS = 2, R_GROUP = 3, R_MAX_UNITS = 4, R_STD_RATE = 6, R_OVT_RATE = 7, R_CODE = 10, R_COST = 12, R_WORK = 13, R_COST_PER_USE = 18, R_NOTES = 20, R_UNIQUE_ID = 27, R_EMAIL = 35 };
enum AsnFieldId { A_UNIQUE_ID = 0, A_TASK_UID = 1, A_RSC_UID = 2, A_UNITS = 7, A_WORK = 8, A_ACTUAL_WORK = 10, A_START = 20, A_FINISH = 21, A_COST = 26, A_PCT_WORK = 43, A_NOTES = 71 };

// Vlastní pole úkolu: druh, pořadové číslo, ID pole a ID pole s jednotkami (u dob trvání)
struct CustomDef { CustomKind kind; int n; int id; int unitsId; };
inline const std::vector<CustomDef>& customDefs() {
    static std::vector<CustomDef> v;
    if (v.empty()) {
        int text[] = { 51, 54, 57, 60, 63, 66, 67, 68, 69, 70, 317, 318, 319, 320, 321, 322, 323, 324, 325, 326, 327, 328, 329, 330, 331, 332, 333, 334, 335, 336 };
        for (int i = 0; i < 30; i++) v.push_back({ CK_TEXT, i + 1, text[i], 0 });
        int num[] = { 87, 88, 89, 90, 91, 302, 303, 304, 305, 306, 307, 308, 309, 310, 311, 312, 313, 314, 315, 316 };
        for (int i = 0; i < 20; i++) v.push_back({ CK_NUMBER, i + 1, num[i], 0 });
        int flag[] = { 72, 73, 74, 75, 76, 77, 78, 79, 80, 81, 292, 293, 294, 295, 296, 297, 298, 299, 300, 301 };
        for (int i = 0; i < 20; i++) v.push_back({ CK_FLAG, i + 1, flag[i], 0 });
        for (int i = 0; i < 10; i++) v.push_back({ CK_DATE, i + 1, 265 + i, 0 });
        int dur[] = { 103, 104, 105, 275, 276, 277, 278, 279, 280, 281 }, durU[] = { 183, 184, 185, 337, 338, 339, 340, 341, 342, 343 };
        for (int i = 0; i < 10; i++) v.push_back({ CK_DURATION, i + 1, dur[i], durU[i] });
        int cost[] = { 106, 107, 108, 258, 259, 260, 261, 262, 263, 264 };
        for (int i = 0; i < 10; i++) v.push_back({ CK_COST, i + 1, cost[i], 0 });
        int st[] = { 52, 55, 58, 61, 64, 282, 284, 286, 288, 290 }, fi[] = { 53, 56, 59, 62, 65, 283, 285, 287, 289, 291 };
        for (int i = 0; i < 10; i++) v.push_back({ CK_START, i + 1, st[i], 0 });
        for (int i = 0; i < 10; i++) v.push_back({ CK_FINISH, i + 1, fi[i], 0 });
    }
    return v;
}
// Směrné plány 1-10: ID polí zahájení / dokončení / doby / jednotek / práce / nákladů
struct BaselineIds { int start, finish, dur, durUnits, work, cost; };
inline BaselineIds baselineIds(int n) {
    static const BaselineIds b[11] = { { 43, 44, 27, 179, 1, 6 }, { 482, 483, 487, 488, 485, 484 }, { 493, 494, 498, 499, 496, 495 }, { 504, 505, 509, 510, 507, 506 }, { 515, 516, 520, 521, 518, 517 },
        { 526, 527, 531, 532, 529, 528 }, { 544, 545, 549, 550, 547, 546 }, { 555, 556, 560, 561, 558, 557 }, { 566, 567, 571, 572, 569, 568 }, { 577, 578, 582, 583, 580, 579 }, { 588, 589, 593, 594, 591, 590 } };
    return b[n < 0 || n > 10 ? 0 : n];
}

inline int taskFieldSize(int id) {
    switch (id) {
    case 0: case 1: case 2: case 3: case 4: case 5: case 6: case 7: case 8: case 9: case 10: case 11: case 12: case 13:
    case 106: case 107: case 108: case 163: case 164: case 165: case 166: case 168: case 169: case 170: case 480: case 120: case 83:
        return 8;
    case 1143: return 16;
    case 17: case 25: case 32: case 33: case 128: case 200: case 249: case 85: case 1173: case 1250: case 24: case 92: case 19:
    case 181: case 1289: case 179: case 152: case 178: case 110: case 111: case 129: case 132: case 82: case 109: case 71:
        return 2;
    case 86: case 23: case 160: case 29: case 28: case 31: case 1288: case 27: case 20: case 21: case 22: case 438: case 439:
    case 35: case 36: case 41: case 42: case 43: case 44: case 1283: case 1284: case 437: case 93: case 18: case 100: case 99: case 101:
    case 37: case 38: case 39: case 40: case 387: case 372: case 401: case 30: case 45: case 46: case 119:
        return 4;
    default: return 0;
    }
}
inline int rscFieldSize(int id) {
    switch (id) {
    case 0: case 27: case 57: case 58: return 4;
    case 70: case 71: case 19: case 272: return 2;
    case 6: case 7: case 4: case 13: case 14: case 15: case 16: case 18: case 22: case 38: case 39: case 40: case 26: case 11: case 12: case 17: case 21: case 47: case 48: case 49: return 8;
    case 728: case 729: return 16;
    default: return 0;
    }
}
inline int asnFieldSize(int id) {
    switch (id) {
    case 0: case 1: case 2: case 20: case 21: case 145: return 4;
    case 55: case 80: return 2;
    case 270: return 1;
    case 7: case 8: case 10: case 11: case 12: case 26: case 28: case 29: return 8;
    case 636: case 637: case 638: return 16;
    default: return 0;
    }
}

struct FieldMap {
    std::map<int, FieldItem> m;
    int maxFixed[2] = { 0, 0 };
    void parse(const Bytes& d, int (*sizeOf)(int)) {
        size_t index = 0; int last = 0, blockIdx = 0;
        while (index + 28 <= d.size()) {
            int off = u16(d, index + 4);
            int typeValue = i32(d, index + 12);
            int cat = u16(d, index + 20);
            int id = typeValue & 0xFFFF;
            FieldItem it; it.varKey = id;
            if (cat == 0x0B || cat == 0x64) it.loc = L_META;
            else if (off != 65535) {
                it.loc = L_FIXED;
                if (off < last && blockIdx < 1) blockIdx++;
                last = off;
                it.block = blockIdx; it.offset = off;
                int sz = sizeOf(id);
                if (off + sz > maxFixed[blockIdx]) maxFixed[blockIdx] = off + sz;
            } else if (id != 0) it.loc = L_VAR;
            m[id] = it;
            index += 28;
        }
    }
    void add(int id, Loc loc, int block, int offset, int varKey) { FieldItem it; it.loc = loc; it.block = block; it.offset = offset; it.varKey = varKey; m[id] = it; }
    void fixed(int id, int block, int off, int size) { add(id, L_FIXED, block, off, id); if (off + size > maxFixed[block]) maxFixed[block] = off + size; }
    void taskDefaults(int mppType) {
        struct D { int id, off; };
        if (mppType == 14) {
            D f0[] = { {86,0},{23,4},{38,8},{39,12},{100,16},{99,20},{21,24},{438,28},{439,32},{160,36},{249,40},{29,42},{181,46},{28,48},{31,52},{17,56},{20,58},{178,62},{35,64},{36,68},{41,72},{42,76},{18,80},{101,84},{25,88},{32,90},{33,92},{128,94},{200,96},{93,98},{37,106},{40,110},{387,114},{401,118},{437,122},{0,126},{2,134},{4,142},{5,150},{8,158},{7,166},{10,174},{480,198} };
            for (auto& x : f0) fixed(x.id, 0, x.off, taskFieldSize(x.id));
            D f1[] = { {1143,0},{1173,40},{1250,42},{1283,50},{1284,54},{1288,58},{1289,62} };
            for (auto& x : f1) fixed(x.id, 1, x.off, taskFieldSize(x.id));
        } else {
            D f0[] = { {86,0},{23,4},{38,8},{39,12},{21,24},{438,28},{439,32},{160,36},{249,40},{29,60},{181,64},{28,66},{31,70},{27,74},{179,78},{17,80},{35,88},{36,92},{41,96},{42,100},{43,104},{44,108},{18,112},{32,122},{33,124},{93,130},{37,148},{40,152},{401,160},{437,164},{0,168} };
            for (auto& x : f0) fixed(x.id, 0, x.off, taskFieldSize(x.id));
            fixed(1143, 1, 0, 16);
        }
        int var[] = { 16, 14, 15, 43, 44, 6, 1, 27, 179, 164, 165, 168, 169, 170, 112 };
        for (int id : var) if (!m.count(id)) add(id, L_VAR, 0, 65535, id);
        for (const CustomDef& c : customDefs()) { if (!m.count(c.id)) add(c.id, L_VAR, 0, 65535, c.id); if (c.unitsId && !m.count(c.unitsId)) add(c.unitsId, L_VAR, 0, 65535, c.unitsId); }
        for (int n = 1; n <= 10; n++) { BaselineIds b = baselineIds(n); for (int id : { b.start, b.finish, b.dur, b.durUnits, b.work, b.cost }) if (!m.count(id)) add(id, L_VAR, 0, 65535, id); }
    }
    void rscDefaults() {
        struct D { int id, off; } f0[] = { {27,0},{0,4},{70,8},{71,10},{19,12},{272,14},{57,20},{58,24},{6,28},{7,36},{4,44},{13,52},{14,60},{15,68},{16,76},{18,84},{22,92},{38,100},{39,108},{40,116},{26,124},{11,132},{12,140},{17,148},{21,156},{47,164},{48,172},{49,180} };
        for (auto& x : f0) fixed(x.id, 0, x.off, rscFieldSize(x.id));
        fixed(728, 1, 0, 16); fixed(729, 1, 24, 16);
        int var[] = { 1, 2, 3, 10, 20, 35 };
        for (int id : var) add(id, L_VAR, 0, 65535, id);
    }
    void asnDefaults(int mppType) {
        struct D { int id, off; };
        if (mppType == 14) { D f0[] = { {0,0},{1,4},{2,8},{20,12},{21,16},{55,28},{145,30},{80,34},{270,44},{7,46},{8,54},{10,62},{11,70},{12,78},{26,86},{28,94},{29,102} }; for (auto& x : f0) fixed(x.id, 0, x.off, asnFieldSize(x.id)); }
        else { D f0[] = { {0,0},{1,4},{2,8},{20,12},{21,16},{7,54},{8,62},{10,70} }; for (auto& x : f0) fixed(x.id, 0, x.off, asnFieldSize(x.id)); }
        fixed(636, 1, 0, 16);
        add(71, L_VAR, 0, 65535, 71);
    }
    const FieldItem* item(int id) const { auto it = m.find(id); return it == m.end() ? nullptr : &it->second; }
    int fixedOffset(int id) const { const FieldItem* it = item(id); return it && it->loc == L_FIXED ? it->offset : -1; }
};

// ---------- čtení hodnot polí jedné entity ----------
struct Ctx {
    const FieldMap& fm; const Bytes* blk[2]; const Var2Data& vd; int uid; const Project& p;
    bool fixedOk(const FieldItem* it, size_t need) const {
        return it && it->loc == L_FIXED && it->block < 2 && blk[it->block] && (size_t)it->offset + need <= blk[it->block]->size();
    }
    // lookup: hodnota může být odkaz do číselníku vlastního pole. U textových standardních polí (název, WBS, zdroj…)
    // se nekontroluje – text začínající „Ё“ (U+0401) by se jinak zahodil a zdroj i s přiřazeními vypadl.
    const Bytes* var(const FieldItem* it, bool lookup = true) const {
        if (!it || it->loc != L_VAR) return nullptr;
        const Bytes* d = vd.get(uid, it->varKey);
        if (!d) return nullptr;
        int flag = u16(*d, 0);
        if (lookup && (flag == 0x0701 || flag == 0x0401)) return nullptr; // odkaz do číselníku vlastního pole - nepodporujeme
        return d;
    }
    Time date(int id) const {
        const FieldItem* it = fm.item(id);
        if (fixedOk(it, 4)) return timestamp(*blk[it->block], it->offset);
        const Bytes* d = var(it);
        if (d && d->size() != 512 && d->size() >= 4) return timestamp(*d, 0);
        return 0;
    }
    int intv(int id) const {
        const FieldItem* it = fm.item(id);
        if (fixedOk(it, 4)) return i32(*blk[it->block], it->offset);
        const Bytes* d = var(it);
        return d && d->size() >= 4 ? i32(*d, 0) : 0;
    }
    int shortv(int id) const {
        const FieldItem* it = fm.item(id);
        if (fixedOk(it, 2)) return u16(*blk[it->block], it->offset);
        const Bytes* d = var(it);
        return d && d->size() >= 2 ? u16(*d, 0) : 0;
    }
    bool present(int id) const { const FieldItem* it = fm.item(id); return fixedOk(it, 2) || var(it) != nullptr; }
    int pct(int id) const { int v = shortv(id); return (v >= 0 && v <= 100) ? v : -1; }
    std::wstring str(int id, bool lookup = false) const { const Bytes* d = var(fm.item(id), lookup); return d ? ustr(*d) : L""; }
    bool hasStr(int id) const { return var(fm.item(id), false) != nullptr; }
    std::wstring notes(int id) const { const Bytes* d = var(fm.item(id)); if (!d) return L""; return rtfToText(astr(*d, 0, d->size())); }
    Duration dur(int id, int unitsId) const {
        Duration r;
        const FieldItem* it = fm.item(id);
        if (!it) return r;
        if (fixedOk(it, 4)) {
            r.raw = i32(*blk[it->block], it->offset);
            r.units = present(unitsId) ? unitsOf(shortv(unitsId), p.defaultUnits) : p.defaultUnits;
        } else {
            const Bytes* d = var(it);
            if (!d || d->size() == 512 || d->size() < 4) return r;
            r.raw = i32(*d, 0);
            r.units = present(unitsId) ? unitsOf(shortv(unitsId), p.defaultUnits) : 5;
        }
        return r;
    }
    double dbl(int id) const {
        const FieldItem* it = fm.item(id);
        if (fixedOk(it, 8)) return f64(*blk[it->block], it->offset);
        const Bytes* d = var(it); return d && d->size() >= 8 ? f64(*d, 0) : 0;
    }
    bool hasDbl(int id) const { const FieldItem* it = fm.item(id); if (fixedOk(it, 8)) return true; const Bytes* d = var(it); return d && d->size() >= 8; }
    double workMin(int id) const { double v = dbl(id); if (std::fabs(v) < 1000) v = 0; return v / 1000.0; }
    double money(int id) const { double v = dbl(id); if (std::fabs(v) < 0.1) v = 0; return v / 100.0; }
};

// ---------- kalendáře ----------
inline std::wstring fmtClock(int tenths) { int m = tenths / 10; wchar_t b[16]; swprintf(b, 16, L"%d:%02d", m / 60, m % 60); return b; }
inline void parseCalendarHours(const Bytes* data, const Calendar* def, Calendar& cal) {
    static const bool DEFAULT_WEEK[7] = { false, true, true, true, true, true, false };
    for (int i = 0; i < 7; i++) {
        size_t off = 60 * i;
        int flag = data ? u16(*data, off) : 1;
        if (flag == 1) {
            if (def) { cal.working[i] = def->working[i]; cal.minutes[i] = def->minutes[i]; cal.hoursText[i] = def->hoursText[i]; }
            else { cal.working[i] = DEFAULT_WEEK[i]; cal.minutes[i] = DEFAULT_WEEK[i] ? 480 : 0; cal.hoursText[i] = DEFAULT_WEEK[i] ? L"8:00–12:00, 13:00–17:00" : L""; }
        } else {
            int count = u16(*data, off + 2), minutes = 0; std::wstring txt;
            for (int k = 0; k < count && k < 5; k++) {
                int st = u16(*data, off + 8 + k * 2), du = u16(*data, off + 20 + k * 4);
                minutes += du / 10;
                if (!txt.empty()) txt += L", ";
                txt += fmtClock(st) + L"–" + fmtClock(st + du);
            }
            cal.working[i] = count > 0; cal.minutes[i] = minutes; cal.hoursText[i] = txt;
        }
    }
}
inline void parseCalendarExceptions(const Bytes& d, Calendar& cal) {
    if (d.size() <= 420) return;
    size_t off = 420;
    int count = u16(d, off);
    if (count == 0) return;
    off += 4;
    for (int i = 0; i < count; i++) {
        if (off + 92 > d.size()) break;
        CalException e;
        e.from = dateDays(d, off); e.to = dateDays(d, off + 2);
        int periods = u16(d, off + 14);
        e.working = periods != 0;
        for (int k = 0; k < periods && k < 5; k++) e.minutes += u16(d, off + 32 + k * 4) / 10;
        int rt = u16(d, off + 72);
        e.rec = (rt >= 1 && rt <= 7) ? rt : 0;
        switch (e.rec) {
        case 7: e.freq = u16(d, off + 76); break;
        case 6: e.weekBits = u8(d, off + 76); e.freq = u16(d, off + 78); break;
        case 4: e.dayNumber = u8(d, off + 76); e.freq = u8(d, off + 78); break;
        case 5: e.dayOfWeek = (int)u8(d, off + 77) - 2; e.dayNumber = u8(d, off + 76) + 1; e.freq = u16(d, off + 78); break;
        case 2: e.dayNumber = u8(d, off + 77); e.month = u8(d, off + 76) + 1; break;
        case 3: e.dayOfWeek = (int)u8(d, off + 78) - 2; e.dayNumber = u8(d, off + 77) + 1; e.month = u8(d, off + 76) + 1; break;
        default: break;
        }
        if (e.rec == 7 && e.freq <= 1) e.rec = 0;
        int nameLen = i32(d, off + 88);
        if (nameLen % 4) nameLen = (nameLen / 4 + 1) * 4;
        if (nameLen < 0 || (size_t)nameLen > d.size()) break;
        if (nameLen) e.name = ustr(sub(d, off + 92, (size_t)nameLen));
        if (e.from >= 0 && e.to >= 0) cal.exceptions.push_back(e);
        off += 92 + (size_t)nameLen;
    }
}

// ---------- kalendář: rozvinutí výjimek (i opakovaných) na konkrétní dny ----------
inline bool nthWeekdayMatch(int64_t ud, int y, unsigned m, unsigned d, int dayOfWeek, int dayNumber) {
    unsigned sunIdx = (dowOfUnixDay(ud) + 1) % 7;          // 0 = neděle
    if ((int)sunIdx != dayOfWeek - 1) return false;
    if (dayNumber >= 1 && dayNumber <= 4) return (int)((d - 1) / 7) + 1 == dayNumber;
    unsigned nm = m == 12 ? 1 : m + 1; int ny = m == 12 ? y + 1 : y;
    int64_t last = daysFromCivil(ny, nm, 1) - 1;
    return last - ud < 7;
}
inline bool exceptionMatches(const CalException& e, int64_t ud) {
    if (ud < e.from || ud > e.to) return false;
    switch (e.rec) {
    case 0: case 1: return true;
    case 7: return e.freq <= 1 || (ud - e.from) % e.freq == 0;
    case 6: {
        unsigned sunIdx = (dowOfUnixDay(ud) + 1) % 7;
        if (!(e.weekBits & (1 << sunIdx))) return false;
        if (e.freq <= 1) return true;
        int64_t w0 = e.from - (int64_t)((dowOfUnixDay(e.from) + 1) % 7);
        return ((ud - w0) / 7) % e.freq == 0;
    }
    case 2: case 3: case 4: case 5: break; // měsíční a roční: níž, potřebují kalendářní datum
    default: return true;
    }
    int y; unsigned m, d; civilFromDays(ud, y, m, d);
    if ((e.rec == 2 || e.rec == 3) && (int)m != e.month) return false;
    if (e.rec == 2 || e.rec == 4) { // „31. den v měsíci“ připadne v kratším měsíci na poslední den (MPXJ RecurringData), 29. 2. mimo přestupný rok na 28. 2.
        int dim = (int)(daysFromCivil(m == 12 ? y + 1 : y, m == 12 ? 1 : m + 1, 1) - daysFromCivil(y, m, 1));
        if ((int)d != std::min(e.dayNumber, dim)) return false;
    } else if (!nthWeekdayMatch(ud, y, m, d, e.dayOfWeek, e.dayNumber)) return false;
    if (e.rec == 2 || e.rec == 3 || e.freq <= 1) return true; // roční se neopakují po víc letech
    int fy; unsigned fm, fd; civilFromDays(e.from, fy, fm, fd);
    return ((y - fy) * 12 + ((int)m - (int)fm)) % e.freq == 0;
}
// Pořadí výjimek rozhoduje (platí první vyhovující), rozsah 1984-2099.
// budget = kolik dnů smí projít celý projekt: poškozený soubor s tisíci výjimkami přes 116 let by jinak počítal hodiny.
inline void expandCalendar(Calendar& c, int64_t& budget) {
    const int64_t lo = daysFromCivil(1984, 1, 1), hi = daysFromCivil(2099, 12, 31);
    std::map<int64_t, int> m;
    for (size_t i = 0; i < c.exceptions.size() && budget > 0; i++) {
        const CalException& e = c.exceptions[i];
        int64_t from = std::max(e.from, lo), to = std::min(e.to, hi);
        auto scan = [&](int64_t a, int64_t b) { for (int64_t d = std::max(a, from); d <= std::min(b, to) && --budget >= 0; d++) if (exceptionMatches(e, d)) m.emplace(d, (int)i); };
        if ((e.rec == 2 || e.rec == 3) && e.month >= 1 && e.month <= 12 && from <= to) { // roční svátky: jen daný měsíc každého roku (12× méně dnů)
            int fy, ty; unsigned x, z; civilFromDays(from, fy, x, z); civilFromDays(to, ty, x, z);
            unsigned mo = (unsigned)e.month;
            for (int y = fy; y <= ty && budget > 0; y++) scan(daysFromCivil(y, mo, 1), daysFromCivil(mo == 12 ? y + 1 : y, mo == 12 ? 1 : mo + 1, 1) - 1);
        } else scan(from, to);
    }
    c.days.clear(); c.days.reserve(m.size());
    for (auto& kv : m) c.days.push_back({ kv.first, kv.second });
}
inline void expandCalendar(Calendar& c) { int64_t budget = 50000000; expandCalendar(c, budget); }

// ---------- společné doformátování projektu (MPP i XML) ----------
inline void finalizeProject(Project& p) {
    int64_t budget = 50000000; // dnů pro všechny kalendáře dohromady (platný soubor jich má nejvýš jednotky milionů)
    expandCalendar(p.builtin, budget);
    for (Calendar& c : p.calendars) expandCalendar(c, budget);
    for (Task& t : p.tasks) if (t.uid == 0 && !t.nameSet && t.level == 0 && t.start) { t.isNull = false; t.nameSet = true; t.name = p.title; } // projektový souhrn bez jména (XML)
    std::stable_sort(p.tasks.begin(), p.tasks.end(), [](const Task& a, const Task& b) { return a.id < b.id; });
    p.reindex();
    p.roots.clear();
    // úroveň 0–100: dítě má vždy vyšší úroveň než rodič, takže strom má nejvýš 101 pater a rekurze
    // (číslování osnovy, tabulka) nepřeteče zásobník ani u poškozeného souboru s tisíci úrovněmi
    for (Task& t : p.tasks) { t.children.clear(); t.parent = -1; t.level = std::max(0, std::min(t.level, 100)); }
    // Hierarchie podle úrovní osnovy (port TaskContainer.updateStructure)
    int lastIdx = -1, lastLevel = -1;
    for (size_t i = 0; i < p.tasks.size(); i++) {
        Task& t = p.tasks[i];
        int parent = -1;
        if (!t.isNull) {
            int level = t.level;
            if (lastIdx >= 0) {
                if (level == lastLevel) { parent = p.tasks[lastIdx].parent; level = lastLevel; }
                else if (level > lastLevel) parent = lastIdx;
                else {
                    while (level <= lastLevel) {
                        parent = p.tasks[lastIdx].parent;
                        if (parent < 0) break;
                        lastLevel = p.tasks[parent].level;
                        lastIdx = parent;
                    }
                }
            }
            lastIdx = (int)i; lastLevel = level;
        }
        t.parent = parent;
        if (parent < 0) p.roots.push_back((int)i); else p.tasks[parent].children.push_back((int)i);
    }
    int limitRaw = p.criticalSlackLimitDays * p.minutesPerDay * 10;
    std::function<void(int, const std::wstring&)> numberTree = [&](int idx, const std::wstring& prefix) {
        Task& t = p.tasks[idx];
        int n = 0;
        for (int c : t.children) {
            Task& ch = p.tasks[c];
            if (ch.isNull) continue;
            n++;
            if (ch.outlineNumber.empty()) ch.outlineNumber = prefix.empty() ? std::to_wstring(n) : prefix + L"." + std::to_wstring(n);
            numberTree(c, ch.outlineNumber);
        }
    };
    std::set<int> hasSucc; for (const Task& t : p.tasks) for (const Relation& r : t.preds) hasSucc.insert(r.predUid);
    std::unordered_map<int, const Calendar*> calByUid; for (const Calendar& c : p.calendars) calByUid.emplace(c.uid, &c); // emplace: první se stejným uid jako calendarByUid
    for (Task& t : p.tasks) {
        t.summary = !t.children.empty();
        if (t.totalSlackRaw == INT32_MIN && t.finishSlackRaw != INT32_MIN)
            t.totalSlackRaw = t.actualStart ? t.finishSlackRaw : (t.startSlackRaw == INT32_MIN ? t.finishSlackRaw : std::min(t.startSlackRaw, t.finishSlackRaw));
        if (!t.criticalFromFile && !t.isNull && !t.summary && t.actualFinish == 0 && t.pctComplete != 100 && t.totalSlackRaw != INT32_MIN)
            t.critical = t.totalSlackRaw <= limitRaw // port Task.calculateCritical
                && !(t.manual && t.estimated && t.preds.empty() && !hasSucc.count(t.uid)); // MS Project: ruční odhadovaný úkol bez vazeb není kritický (ověřeno na 1100 úkolech z XML sady MPXJ)
        if (t.calendarUid > 0) { auto c = calByUid.find(t.calendarUid); if (c != calByUid.end()) t.calendarName = c->second->name; }
    }
    { int n = 0; for (int r : p.roots) { Task& t = p.tasks[r]; if (t.isNull) continue; if (t.level == 0 && p.roots.size() == 1) { t.outlineNumber = L"0"; numberTree(r, L""); } else { n++; if (t.outlineNumber.empty()) t.outlineNumber = std::to_wstring(n); numberTree(r, t.outlineNumber); } } }
    // Vazby na vlastního předka jsou neplatné
    for (Task& t : p.tasks) {
        t.preds.erase(std::remove_if(t.preds.begin(), t.preds.end(), [&](const Relation& r) {
            if (!p.byUid(r.predUid)) return true;
            for (int q = t.parent; q >= 0; q = p.tasks[q].parent) if (p.tasks[q].uid == r.predUid) return true;
            return false;
        }), t.preds.end());
    }
    // Přiřazení: indexy a text "Názvy zdrojů" ve stylu MS Project ("Zedník;Jeřáb[50%]")
    for (Task& t : p.tasks) t.assignments.clear();
    for (Resource& r : p.resources) r.assignments.clear();
    for (size_t i = 0; i < p.assignments.size(); i++) {
        Assignment& a = p.assignments[i];
        Task* t = p.byUid(a.taskUid); auto ri = p.rscIndex.find(a.rscUid);
        if (!t || ri == p.rscIndex.end()) continue;
        t->assignments.push_back((int)i); p.resources[ri->second].assignments.push_back((int)i);
    }
    for (Resource& r : p.resources) { // některé .mpp nemají souhrn práce/nákladů zdroje uložený -> součet přiřazení
        double w = 0, c = 0; for (int ai : r.assignments) { w += p.assignments[ai].workHours; c += p.assignments[ai].cost; }
        if (r.workHours == 0) r.workHours = w;
        if (r.cost == 0) r.cost = c;
    }
    for (Task& t : p.tasks) {
        std::wstring s;
        for (int ai : t.assignments) {
            const Assignment& a = p.assignments[ai];
            const Resource* r = p.rscByUid(a.rscUid); if (!r) continue;
            if (!s.empty()) s += L";";
            s += r->name;
            if (r->type == 0 && std::fabs(a.units - 1.0) > 0.005) s += L"[" + fmtNumber(a.units * 100) + L"%]";
            else if (r->type == 1 && a.units > 0) s += L"[" + fmtNumber(a.units * 100) + L"]";
        }
        t.resourceNames = s;
    }
    // Vlastní pole a směrné plány, které jsou skutečně použité
    p.customFields.clear(); p.baselinesUsed.clear();
    for (const CustomDef& d : customDefs()) {
        auto al = p.aliases.find(d.id);
        bool used = al != p.aliases.end() && !al->second.empty();
        if (!used) for (const Task& t : p.tasks) { const CustomValue* v = t.customValue(d.id); if (v && v->set) { used = true; break; } }
        if (used) p.customFields.push_back({ d.kind, d.n, d.id, al != p.aliases.end() ? al->second : L"" });
    }
    for (int n = 0; n <= 10; n++) for (const Task& t : p.tasks) if (t.baselines[n].start || t.baselines[n].finish) { p.baselinesUsed.push_back(n); break; }
}

// ---------- čtení MPP ----------
inline Project readMpp(const std::wstring& path) {
    Cfb cfb; cfb.load(path);
    const int root = cfb.root();
    Project p;

    std::string format;
    int ci = cfb.find(root, L"\x01" L"CompObj");
    if (ci >= 0) {
        Bytes c = cfb.read(ci);
        size_t pos = 28;
        int32_t len = i32(c, pos); pos += 4;
        if (len > 0 && len < 1000) {
            std::string app = astr(c, pos, (size_t)len - 1); pos += (size_t)len;
            p.appName = ansiToWide(app, 1252);
            size_t k = app.find("Project ");
            if (k != std::string::npos) p.appVersion = atoi(app.c_str() + k + 8);
            int32_t len2 = i32(c, pos); pos += 4;
            if (len2 > 0 && len2 < 1000) format = astr(c, pos, (size_t)len2 - 1);
        }
    }
    if (format.find("14") != std::string::npos || cfb.find(root, L"Props14") >= 0) p.mppType = 14;
    else if (format.find("12") != std::string::npos || cfb.find(root, L"Props12") >= 0) p.mppType = 12;
    else if (format.find("MPP9") != std::string::npos || format.find("MPT9") != std::string::npos) throw std::runtime_error("Soubor je ve formatu Project 2000-2003 (MPP9). Podporovany je Project 2007 a novejsi - ulozte soubor v novejsi verzi nebo jako XML.");
    else if (format.find("MPP8") != std::string::npos) throw std::runtime_error("Soubor je ve formatu Project 98 (MPP8) - nepodporovano.");
    else throw std::runtime_error("Toto neni soubor MS Project 2007+ (chybi Props12/Props14): " + format);
    p.format = p.mppType == 14 ? L"MPP14" : L"MPP12";
    if (p.mppType == 14 && p.appVersion < 14) p.appVersion = 14;
    if (p.mppType == 12 && p.appVersion < 12) p.appVersion = 12;
    const bool is14 = p.mppType == 14;
    const bool v2010 = p.appVersion <= 14;

    Props rootProps = Props::parse(cfb.readStream(root, is14 ? L"Props14" : L"Props12"));
    uint8_t pwFlag = rootProps.byte(PK_PASSWORD_FLAG);
    if ((pwFlag & 1) && (!is14 || rootProps.get(PK_PROTECTION_PASSWORD_HASH))) throw std::runtime_error("Soubor je chranen heslem pro cteni - nelze otevrit.");
    p.encrypted = pwFlag != 0;
    uint8_t code = rootProps.byte(PK_ENCRYPTION_CODE);
    uint8_t mask = code == 0 ? 0 : (uint8_t)(0xFF - code);
    p.filePath = rootProps.str(PK_PROJECT_FILE_PATH);
    auto readEnc = [&](int dir, const wchar_t* name) { Bytes b = cfb.readStream(dir, name); if (p.encrypted) for (auto& x : b) x ^= mask; return b; };

    int projDir = cfb.find(root, is14 ? L"   114" : L"   112");
    if (projDir < 0) throw std::runtime_error("Chybi adresar projektu");
    Props pp = Props::parse(readEnc(projDir, L"Props"));
    p.startDate = pp.ts(PK_PROJECT_START_DATE); p.finishDate = pp.ts(PK_PROJECT_FINISH_DATE); p.statusDate = pp.ts(PK_STATUS_DATE); p.baselineDate = pp.ts(PK_BASELINE_DATE);
    p.scheduleFromStart = pp.shortv(PK_SCHEDULE_FROM) == 0;
    p.defaultUnits = unitsOf(pp.shortv(PK_DURATION_UNITS), 7);
    p.minutesPerDay = pp.intv(PK_MINUTES_PER_DAY); if (p.minutesPerDay <= 0) p.minutesPerDay = 480;
    p.minutesPerWeek = pp.intv(PK_MINUTES_PER_WEEK); if (p.minutesPerWeek <= 0) p.minutesPerWeek = 2400;
    p.daysPerMonth = pp.shortv(PK_DAYS_PER_MONTH); if (p.daysPerMonth <= 0) p.daysPerMonth = 20;
    p.criticalSlackLimitDays = pp.intv(PK_CRITICAL_SLACK_LIMIT); if (p.criticalSlackLimitDays < 0 || p.criticalSlackLimitDays > 10000) p.criticalSlackLimitDays = 0;
    p.title = pp.str(PK_TITLE); p.subject = pp.str(PK_SUBJECT); p.author = pp.str(PK_AUTHOR); p.manager = pp.str(PK_MANAGER); p.company = pp.str(PK_COMPANY);
    p.comments = pp.str(PK_COMMENTS); p.keywords = pp.str(PK_KEYWORDS); p.category = pp.str(PK_CATEGORY);
    p.currency = pp.str(PK_CURRENCY_SYMBOL); if (p.currency.empty()) p.currency = pp.str(PK_CURRENCY_CODE);

    // ---- kalendáře ----
    int calDir = cfb.find(projDir, L"TBkndCal");
    if (calDir >= 0) {
        try {
            VarMeta cvm = VarMeta::parse(cfb.readStream(calDir, L"VarMeta"));
            Var2Data cvd = Var2Data::parse(cvm, cfb.readStream(calDir, L"Var2Data"));
            FixedMeta cfm = FixedMeta::parse(cfb.readStream(calDir, L"FixedMeta"), 10);
            FixedData cfd = FixedData::fromMeta(cfm, readEnc(calDir, L"FixedData"), 12);
            int calOff = 0, baseOff = 4;
            if (is14 && !v2010) { calOff = 8; baseOff = 0; }
            Calendar def; parseCalendarHours(pp.get(PK_DEFAULT_CALENDAR_HOURS), nullptr, def);
            std::set<int> seen;
            for (size_t i = 0; i < cfd.items.size(); i++) {
                const Bytes* fd = cfd.get((int)i);
                if (!fd || fd->size() < 8) continue;
                for (size_t off = 0; off + 12 <= fd->size(); off += 12) {
                    int calId = i32(*fd, off + calOff), baseId = i32(*fd, off + baseOff);
                    if (calId <= 0 || seen.count(calId)) continue;
                    seen.insert(calId);
                    if (baseId > 0 && baseId != calId) continue; // odvozené (zdrojové) kalendáře nepotřebujeme
                    const Bytes* vdata = cvd.get(calId, 8);
                    Calendar cal; cal.uid = calId;
                    const Bytes* nm = cvd.get(calId, 1); if (nm) cal.name = ustr(*nm);
                    const Bytes* hours = vdata ? vdata : pp.get(PK_DEFAULT_CALENDAR_HOURS);
                    parseCalendarHours(hours, vdata ? &def : nullptr, cal);
                    if (vdata) parseCalendarExceptions(*vdata, cal);
                    p.calendars.push_back(cal);
                }
            }
            std::wstring defName = pp.str(PK_DEFAULT_CALENDAR_NAME);
            for (size_t i = 0; i < p.calendars.size(); i++) if (p.calendars[i].name == defName) { p.defaultCalendar = (int)i; break; }
            if (p.defaultCalendar < 0 && !p.calendars.empty()) p.defaultCalendar = 0;
        } catch (const std::exception&) { p.calendars.clear(); p.defaultCalendar = -1; }
    }

    // ---- mapa polí úkolu + aliasy vlastních polí ----
    FieldMap fm;
    const Bytes* fmd = pp.get(PK_TASK_FIELD_MAP);
    if (!fmd) fmd = pp.get(PK_TASK_FIELD_MAP2);
    if (fmd) fm.parse(*fmd, taskFieldSize); else fm.taskDefaults(p.mppType);
    int taskDir = cfb.find(projDir, L"TBkndTask");
    if (taskDir < 0) throw std::runtime_error("Chybi TBkndTask");
    if (cfb.find(taskDir, L"Props") >= 0) {
        try {
            Props tp = Props::parse(readEnc(taskDir, L"Props"));
            const Bytes* cfdata = tp.get(PK_CUSTOM_FIELDS);
            if (cfdata) { // port CustomFieldReader12/14: blok aliasů (velikost 2x, počet, dvojice ID + posun)
                const Bytes& d = *cfdata;
                int blockSize = i32(d, 0), count = i32(d, 8);
                size_t off = 12; int idx = 0;
                while (idx < count && (int)off < blockSize && off + 8 <= d.size()) {
                    uint32_t fieldId = u32(d, off); int aliasOff = i32(d, off + 4) + 4; off += 8;
                    if (aliasOff > 0 && (size_t)aliasOff < d.size() && (fieldId & 0xFFFF0000u) == 0x0B400000u) {
                        std::wstring alias = ustr(d, (size_t)aliasOff);
                        if (!alias.empty()) p.aliases[(int)(fieldId & 0xFFFF)] = alias;
                    }
                    idx++;
                }
            }
        } catch (const std::exception&) {}
    }

    // ---- bloky úkolů ----
    VarMeta vm = VarMeta::parse(cfb.readStream(taskDir, L"VarMeta"));
    Var2Data vd = Var2Data::parse(vm, cfb.readStream(taskDir, L"Var2Data"));
    FixedMeta fmeta = FixedMeta::parse(cfb.readStream(taskDir, L"FixedMeta"), 47);
    FixedData fdata = is14 ? FixedData::fromMeta(fmeta, cfb.readStream(taskDir, L"FixedData"), fm.maxFixed[0])
                           : FixedData::fromMeta(fmeta, cfb.readStream(taskDir, L"FixedData"), 768, fm.maxFixed[0]);
    FixedMeta f2meta = is14 ? FixedMeta::parseHeuristic(cfb.readStream(taskDir, L"Fixed2Meta"), fdata.items.size(), { 92, 93, 94, 95, 96 })
                            : FixedMeta::parse(cfb.readStream(taskDir, L"Fixed2Meta"), 86);
    FixedData f2data = FixedData::fromMeta(f2meta, cfb.readStream(taskDir, L"Fixed2Data"));

    std::map<int, int> taskMap;
    int uidOff = fm.fixedOffset(F_UNIQUE_ID); if (uidOff < 0) uidOff = 0;
    int idOff = fm.fixedOffset(F_ID); if (idOff < 0) idOff = 4;
    for (int loop = (int)fmeta.items.size() - 1; loop > 2; loop--) {
        const Bytes* data = fdata.get(loop); const Bytes* data2 = f2data.get(loop);
        if (!data || !data2) continue;
        const Bytes& meta = fmeta.items[loop];
        int flags = i32(meta, 0);
        if (flags & 0x02) {
            int uid = u16(*data, 0);
            if (is14 ? !taskMap.count(uid) : true) taskMap[uid] = -1;
        } else if (data->size() == 16) {
            int uid = i32(*data, 0);
            if (!taskMap.count(uid)) taskMap[uid] = loop;
        } else {
            int maxSize = fm.maxFixed[0];
            if (maxSize == 0 || (int)(data->size() * 100) / maxSize > 75) {
                int uid = i32(*data, uidOff);
                if (!taskMap.count(uid) || vm.has(uid)) {
                    if (!taskMap.count(uid) || (flags & 0x04) == 0) taskMap[uid] = loop;
                }
            }
        }
    }

    // Bitové příznaky Flag1-20 v metadatech (poloha podle verze Projectu)
    struct BitFlag { int off; uint32_t mask; };
    std::vector<BitFlag> flagBits;
    if (!is14) { flagBits = { {37,0x20},{37,0x40},{37,0x80},{38,0x01},{38,0x02},{38,0x04},{38,0x08},{38,0x10},{38,0x20},{38,0x40},{38,0x80},{39,0x01},{39,0x02},{39,0x04},{39,0x08},{39,0x10},{39,0x20},{39,0x40},{39,0x80},{40,0x01} }; }
    else if (v2010) { for (int i = 0; i < 20; i++) flagBits.push_back({ 35, 0x40u << i }); }
    else { for (int i = 0; i < 10; i++) flagBits.push_back({ 24, 0x2u << i }); for (int i = 0; i < 10; i++) flagBits.push_back({ 33, 0x2u << i }); }

    std::map<int, int> nullOrder;
    std::map<int64_t, int> order;
    std::vector<Task> tasks;
    // ID -> indexy nenulových úkolů v pořadí vložení (dřív hledání duplicit v celém vektoru = O(n²) u velkých souborů);
    // vyřazený duplikát dostane uid INT_MIN a smaže se až po smyčce, aby indexy platily
    std::unordered_map<int, std::vector<size_t>> nonNullById;

    for (auto& kv : taskMap) {
        int uid = kv.first, off = kv.second;
        if (off < 0 || !fdata.get(off)) continue;
        Bytes data = *fdata.get(off);
        const Bytes& meta = fmeta.items[off];
        int id = i32(data, idOff);
        if (data.size() == 16) {
            int nid = is14 ? i32(data, 4) : u16(data, 4), nuid = is14 ? i32(data, 0) : u16(data, 0);
            if (!nullOrder.count(nid)) { Task t; t.isNull = true; t.uid = nuid; t.id = nid; tasks.push_back(t); nullOrder[nid] = t.uid; }
            continue;
        }
        if ((int)data.size() < fm.maxFixed[0]) data.resize(fm.maxFixed[0] + 8, 0);
        const Bytes* meta2 = f2meta.get(off);
        const Bytes* data2 = f2data.get(off);

        auto dup = nonNullById.find(id);
        if (dup != nonNullById.end() && !dup->second.empty()) {
            if (!vm.has(uid)) continue;
            size_t di = dup->second.front();
            if (!tasks[di].nameSet) { tasks[di].uid = INT_MIN; dup->second.erase(dup->second.begin()); }
        }

        Task t; t.uid = uid; t.id = id;
        Ctx c{ fm, { &data, data2 }, vd, uid, p };
        t.name = c.str(F_NAME); t.nameSet = c.hasStr(F_NAME);
        t.wbs = c.str(F_WBS);
        t.notes = c.notes(F_NOTES);
        t.contact = c.str(F_CONTACT);
        t.level = fm.item(F_OUTLINE_LEVEL) ? c.shortv(F_OUTLINE_LEVEL) : c.shortv(F_OUTLINE_LEVEL_OLD);
        t.parentUid = c.intv(F_PARENT_UID);
        t.constraintType = c.shortv(F_CONSTRAINT_TYPE);
        t.constraintDate = c.date(F_CONSTRAINT_DATE);
        t.pctComplete = c.pct(F_PCT_COMPLETE); t.pctWork = c.pct(F_PCT_WORK);
        if (c.present(F_PRIORITY)) t.priority = c.shortv(F_PRIORITY);
        t.taskType = c.shortv(F_TYPE); if (t.taskType < 0 || t.taskType > 2) t.taskType = 0;
        Time schedStart = c.date(F_START), schedFinish = c.date(F_FINISH);
        t.start = c.date(F_TASK_START); t.finish = c.date(F_TASK_FINISH);
        t.actualStart = c.date(F_ACTUAL_START); t.actualFinish = c.date(F_ACTUAL_FINISH);
        t.deadline = c.date(F_DEADLINE); t.created = c.date(F_CREATED);
        t.earlyStart = c.date(F_EARLY_START); t.earlyFinish = c.date(F_EARLY_FINISH);
        t.lateStart = c.date(F_LATE_START); t.lateFinish = c.date(F_LATE_FINISH);
        Duration sched = c.dur(F_DURATION, F_ACTUAL_DURATION_UNITS);
        Duration manual = c.dur(F_MANUAL_DURATION, F_MANUAL_DURATION_UNITS);
        t.actualDuration = c.dur(F_ACTUAL_DURATION, F_ACTUAL_DURATION_UNITS);
        t.remainingDuration = c.dur(F_REMAINING_DURATION, F_ACTUAL_DURATION_UNITS);
        for (int n = 0; n <= 10; n++) {
            BaselineIds b = baselineIds(n); Baseline& bl = t.baselines[n];
            bl.start = c.date(b.start); bl.finish = c.date(b.finish);
            bl.duration = c.dur(b.dur, b.durUnits); bl.workHours = c.workMin(b.work) / 60.0; bl.cost = c.money(b.cost);
        }
        t.workMinutes = c.workMin(F_WORK); t.actualWorkMinutes = c.workMin(F_ACTUAL_WORK); t.remainingWorkMinutes = c.workMin(F_REMAINING_WORK);
        t.cost = c.money(F_COST); t.actualCost = c.money(F_ACTUAL_COST); t.remainingCost = c.money(F_REMAINING_COST); t.fixedCost = c.money(F_FIXED_COST);
        t.estimated = (c.shortv(F_ACTUAL_DURATION_UNITS) & 0x20) != 0;
        if (c.present(F_START_SLACK)) t.startSlackRaw = c.intv(F_START_SLACK);
        if (c.present(F_FINISH_SLACK)) t.finishSlackRaw = c.intv(F_FINISH_SLACK);
        if (c.present(F_FREE_SLACK)) t.freeSlackRaw = c.intv(F_FREE_SLACK);
        if (c.present(F_CALENDAR_UID)) t.calendarUid = c.intv(F_CALENDAR_UID);
        // vlastní pole
        for (const CustomDef& d : customDefs()) {
            CustomValue v;
            switch (d.kind) {
            case CK_TEXT: v.text = c.str(d.id, true); v.set = !v.text.empty(); break;
            case CK_NUMBER: if (c.hasDbl(d.id)) { v.num = c.dbl(d.id); v.set = std::fabs(v.num) > 1e-9; } break;
            case CK_COST: if (c.hasDbl(d.id)) { v.num = c.money(d.id); v.set = std::fabs(v.num) > 1e-9; } break;
            case CK_DATE: case CK_START: case CK_FINISH: v.date = c.date(d.id); v.set = v.date != 0; break;
            case CK_DURATION: v.dur = c.dur(d.id, d.unitsId); v.set = v.dur.valid() && v.dur.raw != 0; break;
            case CK_FLAG: { int i = d.n - 1; if (i < (int)flagBits.size() && (size_t)flagBits[i].off + 4 <= meta.size()) { v.flag = (u32(meta, flagBits[i].off) & flagBits[i].mask) != 0; v.set = v.flag; } break; }
            default: break;
            }
            if (v.set) t.custom[d.id] = v;
        }

        t.milestone = v2010 ? (i32(meta, 8) & 0x20) != 0 : (i32(meta, 10) & 0x02) != 0;
        t.effortDriven = v2010 ? (i32(meta, 11) & 0x10) != 0 : (i32(meta, 13) & 0x08) != 0;
        t.expanded = meta.size() > 12 ? (meta[12] & 0x02) == 0 : true;
        if (is14 && meta2) {
            t.active = (i32(*meta2, 8) & (v2010 ? 0x04 : 0x40)) != 0;
            t.manual = (i32(*meta2, 8) & (v2010 ? 0x08 : 0x80)) != 0;
        }

        if (t.start == 0 || (schedStart != 0 && !t.manual)) t.start = schedStart;
        if (t.finish == 0 || (schedFinish != 0 && !t.manual)) t.finish = schedFinish;
        t.duration = t.manual && manual.valid() ? manual : sched;

        switch (t.constraintType) {
        case 1:
            if (t.start && t.lateStart && t.start < t.lateStart) t.start = t.lateStart;
            if (t.finish && t.lateFinish && t.finish < t.lateFinish) t.finish = t.lateFinish;
            break;
        case 5: case 7:
            if (t.finish && t.start && t.finish < t.start) t.finish = t.lateFinish;
            break;
        default: break;
        }

        if (!t.nameSet && (t.start == 0 || t.finish == 0 || (is14 && t.created == 0))) {
            int nid = i32(data, 4);
            if (!nullOrder.count(nid)) { Task n; n.isNull = true; n.uid = i32(data, 0); n.id = nid; tasks.push_back(n); nullOrder[nid] = n.uid; }
            continue;
        }

        if (!data2 || data2->size() < 24) nullOrder[t.id] = t.uid;
        else order[i64(*data2, 16)] = t.uid;
        nonNullById[id].push_back(tasks.size());
        tasks.push_back(t);
    }
    tasks.erase(std::remove_if(tasks.begin(), tasks.end(), [](const Task& t) { return t.uid == INT_MIN; }), tasks.end()); // vyřazené duplikáty

    {   // Přečíslování ID podle řadicího klíče (opravuje duplicitní ID)
        std::map<int, int> idMap;
        int inc = ((int)(nullOrder.size() / 1000) + 1) * 2000;
        bool hasUid0 = std::any_of(tasks.begin(), tasks.end(), [](const Task& t) { return t.uid == 0; });
        int next = hasUid0 ? 0 : inc;
        for (auto& kv : order) { idMap[next] = kv.second; next += inc; }
        int insertionCount = 0;
        std::map<int, int> offsetMap;
        for (auto& kv : nullOrder) {
            int base = (kv.first - insertionCount) * inc, target = base;
            auto it = offsetMap.find(base);
            int offset = it == offsetMap.end() ? 0 : it->second + 1;
            ++insertionCount;
            while (idMap.count(target)) {
                ++offset;
                if (offset == inc) throw std::runtime_error("Nelze opravit poradi ukolu");
                target = base - (inc - offset);
            }
            offsetMap[base] = offset;
            idMap[target] = kv.second;
        }
        std::map<int, int> uidToIdx;
        for (size_t i = 0; i < tasks.size(); i++) uidToIdx[tasks[i].uid] = (int)i;
        next = hasUid0 ? 0 : 1;
        for (auto& kv : idMap) { auto it = uidToIdx.find(kv.second); if (it != uidToIdx.end()) tasks[it->second].id = next; next++; }
    }
    p.tasks = std::move(tasks);
    p.reindex();

    // ---- vazby (předchůdci) ----
    int consDir = cfb.find(projDir, L"TBkndCons");
    if (consDir >= 0 && cfb.find(consDir, L"FixedMeta") >= 0 && cfb.find(consDir, L"FixedData") >= 0) {
        FixedMeta cm = FixedMeta::parse(cfb.readStream(consDir, L"FixedMeta"), 10);
        FixedData cd = FixedData::fromMetaFixedSize(cm, readEnc(consDir, L"FixedData"), 20);
        bool p15 = is14 && p.appVersion > 14;
        int unitsOff = p15 ? 18 : 14, durOff = p15 ? 14 : 16;
        int count = std::min(cm.itemCount, (int)cm.items.size());
        for (int loop = 0; loop < count; loop++) {
            const Bytes& meta = cm.items[loop];
            if (u16(meta, 0) != 0) continue;
            int idx = cd.indexFromOffset(i32(meta, 4));
            if (idx < 0) continue;
            const Bytes& d = cd.items[idx];
            if (d.size() < 14) continue;
            int t1 = i32(d, 4), t2 = i32(d, 8);
            if (t1 == 0 || t2 == 0 || t1 == t2) continue;
            Task* b = p.byUid(t2);
            if (!p.byUid(t1) || !b) continue;
            Relation r; r.predUid = t1; r.type = u16(d, 12);
            r.lag.raw = i32(d, durOff); r.lag.units = unitsOf(u16(d, unitsOff), 7);
            b->preds.push_back(r);
        }
    }

    // ---- zdroje ----
    int rscDir = cfb.find(projDir, L"TBkndRsc");
    if (rscDir >= 0) {
        try {
            FieldMap rfm;
            const Bytes* rmd = pp.get(PK_RESOURCE_FIELD_MAP); if (!rmd) rmd = pp.get(PK_RESOURCE_FIELD_MAP2);
            if (rmd) rfm.parse(*rmd, rscFieldSize); else rfm.rscDefaults();
            VarMeta rvm = VarMeta::parse(cfb.readStream(rscDir, L"VarMeta"));
            Var2Data rvd = Var2Data::parse(rvm, cfb.readStream(rscDir, L"Var2Data"));
            FixedMeta rfmeta = FixedMeta::parse(cfb.readStream(rscDir, L"FixedMeta"), 37);
            FixedData rfdata = FixedData::fromMeta(rfmeta, readEnc(rscDir, L"FixedData"));
            FixedMeta rf2meta = is14 ? FixedMeta::parseHeuristic(cfb.readStream(rscDir, L"Fixed2Meta"), rfdata.items.size(), { 50, 51 })
                                     : FixedMeta::parse(cfb.readStream(rscDir, L"Fixed2Meta"), 49);
            FixedData rf2data = FixedData::fromMeta(rf2meta, readEnc(rscDir, L"Fixed2Data"));
            int ruidOff = rfm.fixedOffset(R_UNIQUE_ID); if (ruidOff < 0) ruidOff = 0;
            std::map<int, int> rmap;
            for (size_t i = 0; i < rfdata.items.size(); i++) {
                const Bytes* d = rfdata.get((int)i);
                if (!d || (int)d->size() < rfm.maxFixed[0]) continue;
                int uid = u16(*d, ruidOff);
                if (!rmap.count(uid)) rmap[uid] = (int)i;
            }
            for (auto& kv : rvm.table) {
                int uid = kv.first;
                auto it = rmap.find(uid); if (it == rmap.end()) continue;
                const Bytes* d = rfdata.get(it->second); if (!d) continue;
                const Bytes* d2 = rf2data.get(it->second);
                const Bytes* meta = rfmeta.get(it->second);
                const Bytes* meta2 = rf2meta.get(it->second);
                Resource r; r.uid = uid;
                Ctx c{ rfm, { d, d2 }, rvd, uid, p };
                r.id = c.intv(R_ID); r.name = c.str(R_NAME); r.initials = c.str(R_INITIALS); r.group = c.str(R_GROUP); r.email = c.str(R_EMAIL);
                r.maxUnits = c.hasDbl(R_MAX_UNITS) ? c.dbl(R_MAX_UNITS) / 10000.0 : 1.0; // uloženo jako % × 100 (jako jednotky přiřazení)
                r.stdRate = c.dbl(R_STD_RATE); r.ovtRate = c.dbl(R_OVT_RATE); r.costPerUse = c.money(R_COST_PER_USE);
                r.workHours = c.workMin(R_WORK) / 60.0; r.cost = c.money(R_COST);
                bool work = meta && ((is14 && !v2010) ? (u8(*meta, 12) & 0x10) != 0 : (u8(*meta, 9) & 0x02) != 0);
                r.type = work ? 0 : ((meta2 && (u8(*meta2, 8) & 0x10)) ? 2 : 1);
                if (r.name.empty()) continue;
                p.rscIndex[uid] = (int)p.resources.size();
                p.resources.push_back(r);
            }
        } catch (const std::exception&) { p.resources.clear(); p.rscIndex.clear(); }
    }

    // ---- přiřazení zdrojů ----
    int asnDir = cfb.find(projDir, L"TBkndAssn");
    if (asnDir >= 0 && !p.resources.empty()) {
        try {
            FieldMap afm;
            const Bytes* amd = pp.get(PK_ASSIGNMENT_FIELD_MAP); if (!amd) amd = pp.get(PK_ASSIGNMENT_FIELD_MAP2);
            if (amd) afm.parse(*amd, asnFieldSize); else afm.asnDefaults(p.mppType);
            VarMeta avm = VarMeta::parse(cfb.readStream(asnDir, L"VarMeta"));
            Var2Data avd = Var2Data::parse(avm, cfb.readStream(asnDir, L"Var2Data"));
            FixedMeta afmeta = FixedMeta::parse(cfb.readStream(asnDir, L"FixedMeta"), 34);
            FixedData afdata = is14 ? FixedData::chunks(readEnc(asnDir, L"FixedData"), 110) : FixedData::fromMeta(afmeta, readEnc(asnDir, L"FixedData"));
            FixedData af2data = FixedData::chunks(readEnc(asnDir, L"Fixed2Data"), 48);
            int count = is14 ? std::min(afmeta.itemCount, (int)afmeta.items.size()) : (int)afmeta.items.size();
            std::set<std::pair<int, int>> seen;
            for (int loop = 0; loop < count; loop++) {
                const Bytes& meta = afmeta.items[loop];
                if (meta.empty() || meta[0] != 0) continue;
                int off = i32(meta, 4);
                int idx = afdata.indexFromOffset(off);
                const Bytes* dp = afdata.get(idx); if (!dp) continue;
                Bytes data = *dp;
                if ((int)data.size() < afm.maxFixed[0]) data.resize(afm.maxFixed[0], 0);
                int uid = i32(data, std::max(0, afm.fixedOffset(A_UNIQUE_ID)));
                if (!avm.has(uid)) continue;
                const Bytes* d2 = af2data.get(idx);
                Assignment a; a.uid = uid;
                Ctx c{ afm, { &data, d2 }, avd, uid, p };
                a.taskUid = c.intv(A_TASK_UID); a.rscUid = c.intv(A_RSC_UID);
                if (!p.byUid(a.taskUid) || a.rscUid == -65535 || !p.rscByUid(a.rscUid)) continue;
                if (!seen.insert({ a.taskUid, a.rscUid }).second) continue;
                a.units = c.dbl(A_UNITS) / 10000.0;
                a.workHours = c.workMin(A_WORK) / 60.0; a.actualWorkHours = c.workMin(A_ACTUAL_WORK) / 60.0; a.cost = c.money(A_COST);
                a.start = c.date(A_START); a.finish = c.date(A_FINISH);
                p.assignments.push_back(a);
            }
        } catch (const std::exception&) { p.assignments.clear(); }
    }
    finalizeProject(p);
    return p;
}

} // namespace mpp

#include "mspdi.h"

namespace mpp {
// Otevře .mpp / .mpt (OLE2) nebo .xml (MSPDI) podle obsahu souboru
inline Project open(const std::wstring& path) {
    FILE* f = _wfopen(path.c_str(), L"rb");
    if (!f) throw std::runtime_error("Soubor nelze otevrit");
    unsigned char head[8] = { 0 }; size_t n = fread(head, 1, 8, f); fclose(f);
    if (n >= 8 && head[0] == 0xD0 && head[1] == 0xCF) return readMpp(path);
    // BOM / whitespace / "<?xml" nebo rovnou "<Project"
    size_t i = 0; while (i < n && (head[i] == 0xEF || head[i] == 0xBB || head[i] == 0xBF || head[i] == 0xFF || head[i] == 0xFE || head[i] == ' ' || head[i] == '\r' || head[i] == '\n' || head[i] == '\t' || head[i] == 0)) i++;
    if (i < n && head[i] == '<') return readMspdi(path);
    throw std::runtime_error("Neznamy format souboru (ocekavan .mpp nebo XML MS Project).");
}
}
