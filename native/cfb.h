// cfb.h - čtečka OLE2 / Compound File Binary (MS-CFB) kontejneru, ve kterém je uložen .mpp
// Jen čtení, bez závislostí. Vše je bounds-checked: poškozený soubor vrací nuly, nepadá.
// Součást knihovny mppread.dll - licence GNU LGPL 2.1 nebo pozdější (viz COPYING.LIB). Copyright (c) 2026 Milan Knor (M3K Soft).
#pragma once
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <stdexcept>
#include <string>
#include <vector>

struct CfbEntry {
    std::wstring name;
    uint8_t type = 0;           // 1 = storage (adresář), 2 = stream, 5 = root
    uint32_t left = 0xFFFFFFFF, right = 0xFFFFFFFF, child = 0xFFFFFFFF;
    uint32_t start = 0;
    uint64_t size = 0;
};

class Cfb {
public:
    static const uint32_t NOSTREAM = 0xFFFFFFFF;
    static const uint32_t ENDOFCHAIN = 0xFFFFFFFE;
    static const uint32_t MAXREGSECT = 0xFFFFFFFA;

    void load(const std::wstring& path) {
        FILE* f = _wfopen(path.c_str(), L"rb");
        if (!f) throw std::runtime_error("Soubor nelze otevrit");
        fseek(f, 0, SEEK_END);
        long n = ftell(f);
        fseek(f, 0, SEEK_SET);
        if (n < 0) { fclose(f); throw std::runtime_error("Soubor nelze precist"); }
        file.resize((size_t)n);
        if (n > 0 && fread(file.data(), 1, (size_t)n, f) != (size_t)n) { fclose(f); throw std::runtime_error("Soubor nelze precist"); }
        fclose(f);
        parse();
    }

    int root() const { return 0; }

    // Najde potomka adresáře 'dir' podle jména; -1 když neexistuje.
    int find(int dir, const std::wstring& name) const {
        if (dir < 0 || dir >= (int)entries.size()) return -1;
        std::vector<uint32_t> stack;
        stack.push_back(entries[dir].child);
        size_t guard = 0;
        while (!stack.empty() && guard++ < entries.size() * 2) {
            uint32_t i = stack.back(); stack.pop_back();
            if (i >= entries.size()) continue;
            const CfbEntry& e = entries[i];
            if (e.name == name) return (int)i;
            stack.push_back(e.left);
            stack.push_back(e.right);
        }
        return -1;
    }

    // Vrátí jména potomků (pro diagnostiku).
    std::vector<std::wstring> list(int dir) const {
        std::vector<std::wstring> r;
        if (dir < 0 || dir >= (int)entries.size()) return r;
        std::vector<uint32_t> stack;
        stack.push_back(entries[dir].child);
        size_t guard = 0;
        while (!stack.empty() && guard++ < entries.size() * 2) {
            uint32_t i = stack.back(); stack.pop_back();
            if (i >= entries.size()) continue;
            r.push_back(entries[i].name);
            stack.push_back(entries[i].left);
            stack.push_back(entries[i].right);
        }
        return r;
    }

    std::vector<uint8_t> read(int idx) const {
        if (idx < 0 || idx >= (int)entries.size()) return {};
        const CfbEntry& e = entries[idx];
        if (e.type == 5) return miniStream;
        if (e.size < miniCutoff) return readChain(miniStream, miniSecSize, minifat, e.start, e.size);
        return readChain(file, secSize, fat, e.start, e.size, secSize);
    }

    // Pohodlí: přečte stream podle cesty v adresáři; vyhodí výjimku, když chybí.
    std::vector<uint8_t> readStream(int dir, const std::wstring& name) const {
        int i = find(dir, name);
        if (i < 0) throw std::runtime_error("Chybi stream v souboru");
        return read(i);
    }

private:
    std::vector<uint8_t> file;
    uint32_t secSize = 512, miniSecSize = 64, miniCutoff = 4096;
    std::vector<uint32_t> fat, minifat;
    std::vector<CfbEntry> entries;
    std::vector<uint8_t> miniStream;

    static uint16_t u16(const std::vector<uint8_t>& d, size_t o) { return o + 2 <= d.size() ? (uint16_t)(d[o] | (d[o + 1] << 8)) : 0; }
    static uint32_t u32(const std::vector<uint8_t>& d, size_t o) { return o + 4 <= d.size() ? (uint32_t)(d[o] | (d[o + 1] << 8) | (d[o + 2] << 16) | ((uint32_t)d[o + 3] << 24)) : 0; }

    // Přečte řetězec sektorů 'start' ze zdroje 'src' (sektory velikosti 'ss', tabulka 'table').
    // 'base' = posun prvního sektoru (u hlavního souboru je sektor 0 až za 512B hlavičkou).
    static std::vector<uint8_t> readChain(const std::vector<uint8_t>& src, uint32_t ss, const std::vector<uint32_t>& table,
                                          uint32_t start, uint64_t size, uint32_t base = 0) {
        std::vector<uint8_t> out;
        uint64_t remaining = size;
        uint32_t s = start;
        size_t guard = 0;
        while (s < MAXREGSECT && remaining > 0 && guard++ <= table.size()) {
            uint64_t off = (uint64_t)base + (uint64_t)s * ss;
            if (off >= src.size()) break;
            uint64_t avail = src.size() - off;
            uint64_t take = ss < remaining ? ss : remaining;
            if (take > avail) take = avail;
            out.insert(out.end(), src.begin() + (size_t)off, src.begin() + (size_t)(off + take));
            remaining -= take;
            if (s >= table.size()) break;
            s = table[s];
        }
        return out;
    }

    void parse() {
        static const uint8_t sig[8] = { 0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1 };
        if (file.size() < 512 || memcmp(file.data(), sig, 8) != 0) throw std::runtime_error("Toto neni soubor MS Project (chybi OLE2 hlavicka)");
        uint16_t secShift = u16(file, 0x1E);
        if (secShift != 9 && secShift != 12) throw std::runtime_error("Neplatna velikost sektoru");
        secSize = 1u << secShift;
        miniSecSize = 1u << u16(file, 0x20);
        if (miniSecSize == 0 || miniSecSize > secSize) miniSecSize = 64;
        uint32_t numFat = u32(file, 0x2C);
        uint32_t firstDir = u32(file, 0x30);
        miniCutoff = u32(file, 0x38);
        uint32_t firstMiniFat = u32(file, 0x3C);
        uint32_t numMiniFat = u32(file, 0x40);
        uint32_t firstDifat = u32(file, 0x44);
        uint32_t numDifat = u32(file, 0x48);
        uint32_t perSec = secSize / 4;

        // DIFAT: 109 položek v hlavičce + řetězec DIFAT sektorů
        std::vector<uint32_t> fatSectors;
        for (uint32_t i = 0; i < 109 && fatSectors.size() < numFat; i++) {
            uint32_t s = u32(file, 0x4C + i * 4);
            if (s < MAXREGSECT) fatSectors.push_back(s);
        }
        uint32_t ds = firstDifat;
        for (uint32_t i = 0; i < numDifat && ds < MAXREGSECT; i++) {
            size_t off = (size_t)(ds + 1) * secSize;
            for (uint32_t j = 0; j + 1 < perSec && fatSectors.size() < numFat; j++) {
                uint32_t s = u32(file, off + j * 4);
                if (s < MAXREGSECT) fatSectors.push_back(s);
            }
            ds = u32(file, off + (perSec - 1) * 4);
        }
        for (uint32_t s : fatSectors) {
            size_t off = (size_t)(s + 1) * secSize;
            for (uint32_t j = 0; j < perSec; j++) fat.push_back(u32(file, off + j * 4));
        }

        // Adresář
        std::vector<uint8_t> dir = readChain(file, secSize, fat, firstDir, (uint64_t)-1, secSize);
        for (size_t off = 0; off + 128 <= dir.size(); off += 128) {
            CfbEntry e;
            uint16_t nameLen = u16(dir, off + 64);
            if (nameLen > 64) nameLen = 64;
            for (uint16_t i = 0; i + 1 < nameLen; i += 2) {
                wchar_t c = (wchar_t)u16(dir, off + i);
                if (c == 0) break;
                e.name.push_back(c);
            }
            e.type = dir[off + 66];
            e.left = u32(dir, off + 68);
            e.right = u32(dir, off + 72);
            e.child = u32(dir, off + 76);
            e.start = u32(dir, off + 116);
            e.size = u32(dir, off + 120);
            if (secSize == 4096) e.size |= (uint64_t)u32(dir, off + 124) << 32;
            entries.push_back(e);
        }
        if (entries.empty() || entries[0].type != 5) throw std::runtime_error("Poskozeny adresar OLE2");

        // Mini FAT + mini stream (malé streamy < 4096 B leží uvnitř root streamu)
        std::vector<uint8_t> mf = readChain(file, secSize, fat, firstMiniFat, (uint64_t)numMiniFat * secSize, secSize);
        for (size_t o = 0; o + 4 <= mf.size(); o += 4) minifat.push_back(u32(mf, o));
        miniStream = readChain(file, secSize, fat, entries[0].start, entries[0].size, secSize);
    }
};
