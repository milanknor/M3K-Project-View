// mppread.cpp - knihovna mppread.dll: načte .mpp / .mpt (MPP12, MPP14) nebo .xml (MSPDI) a vrátí serializovaný model.
// Licence: GNU LGPL 2.1 nebo pozdější (viz COPYING.LIB). Copyright (c) 2026 Milan Knor (M3K Soft); čtení MPP je port části knihovny MPXJ,
// Copyright (c) Packwood Software 2002-2022 (Jon Iles), https://github.com/joniles/mpxj (podrobně viz mpp.h).
// Sestavení samostatně: build-mppread.cmd (MSVC), výsledek nahraďte vedle mppview.exe.
#define _CRT_SECURE_NO_WARNINGS
#define MPPREAD_BUILD
#include "mpp.h"
#include "mppread.h"
#include <cstdlib>

static int openImpl(const wchar_t* path, uint8_t** data, size_t* size, wchar_t* err, int errLen) {
    try {
        mpp::Project p = mpp::open(path);
        std::vector<uint8_t> b = mpp::serializeProject(p);
        uint8_t* m = (uint8_t*)malloc(b.empty() ? 1 : b.size());
        if (!m) throw std::runtime_error("Nedostatek pameti");
        if (!b.empty()) memcpy(m, b.data(), b.size());
        *data = m; *size = b.size();
        return 0;
    } catch (const std::exception& e) {
        if (err && errLen > 0) { std::wstring w = mpp::ansiToWide(e.what(), CP_UTF8); wcsncpy_s(err, (size_t)errLen, w.c_str(), _TRUNCATE); }
        return 1;
    } catch (...) {
        if (err && errLen > 0) wcsncpy_s(err, (size_t)errLen, L"Neočekávaná chyba při čtení souboru.", _TRUNCATE);
        return 2;
    }
}
// Zachytí i pád (např. přístup mimo paměť u poškozeného souboru), aby nespadla celá aplikace.
static int openGuarded(const wchar_t* path, uint8_t** data, size_t* size, wchar_t* err, int errLen) {
    __try { return openImpl(path, data, size, err, errLen); }
#pragma warning(suppress: 6320) // záměrně zachytí jakoukoli výjimku, aby poškozený soubor neshodil aplikaci
    __except (EXCEPTION_EXECUTE_HANDLER) {
        if (err && errLen > 0) wcsncpy_s(err, (size_t)errLen, L"Soubor se nepodařilo přečíst (vnitřní chyba čtečky – soubor je nejspíš poškozený).", _TRUNCATE);
        return 3;
    }
}

MPPREAD_EXPORT int __cdecl MppRead_ApiVersion(void) { return MPPREAD_API_VERSION; }
MPPREAD_EXPORT int __cdecl MppRead_Open(const wchar_t* path, uint8_t** data, size_t* size, wchar_t* err, int errLen) {
    if (!path || !data || !size) return 4;
    *data = nullptr; *size = 0; if (err && errLen > 0) err[0] = 0;
    return openGuarded(path, data, size, err, errLen);
}
MPPREAD_EXPORT void __cdecl MppRead_Free(uint8_t* data) { free(data); }
