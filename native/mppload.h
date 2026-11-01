// mppload.h - načtení harmonogramu přes knihovnu mppread.dll (dynamicky, z adresáře programu).
// Copyright (c) 2026 Milan Knor (M3K Soft). Licence MIT (viz LICENSE-MIT.txt).
#pragma once
#include "model.h"
#include "mppread.h"

namespace mpp {
inline Project loadProject(const std::wstring& path) {
    static HMODULE h = nullptr; static MppRead_Open_t fOpen = nullptr; static MppRead_Free_t fFree = nullptr;
    if (!h) {
        // jen z adresáře programu (ne z aktuálního adresáře - ochrana proti podvržené DLL)
        wchar_t exe[MAX_PATH] = {}; DWORD n = GetModuleFileNameW(nullptr, exe, MAX_PATH);
        if (n == 0 || n >= MAX_PATH) throw std::runtime_error("Cesta k programu je prilis dlouha."); // useknutá cesta by ukázala do nadřazené složky
        std::wstring dll = exe; dll = dll.substr(0, dll.find_last_of(L"\\/") + 1) + L"mppread.dll";
        // závislosti knihovny (xmllite, shlwapi…) jen ze System32 – podvržená DLL vedle programu se nenačte
        HMODULE m = LoadLibraryExW(dll.c_str(), nullptr, LOAD_LIBRARY_SEARCH_SYSTEM32);
        if (!m) throw std::runtime_error("Chybí knihovna mppread.dll (musí být ve stejné složce jako program).");
        auto fVer = (MppRead_ApiVersion_t)GetProcAddress(m, "MppRead_ApiVersion");
        auto o = (MppRead_Open_t)GetProcAddress(m, "MppRead_Open"); auto f = (MppRead_Free_t)GetProcAddress(m, "MppRead_Free");
        if (!fVer || !o || !f) { FreeLibrary(m); throw std::runtime_error("Knihovna mppread.dll je neplatná (chybí funkce rozhraní)."); }
        if (fVer() != MPPREAD_API_VERSION) { FreeLibrary(m); throw std::runtime_error("Knihovna mppread.dll má nekompatibilní verzi rozhraní."); }
        h = m; fOpen = o; fFree = f;
    }
    uint8_t* data = nullptr; size_t size = 0; wchar_t err[1024] = {};
    int rc = fOpen(path.c_str(), &data, &size, err, 1024);
    if (rc != 0) { if (data) fFree(data); throw std::runtime_error(wideToUtf8(err[0] ? err : L"Soubor nelze otevřít.")); }
    try { Project p = deserializeProject(data, size); fFree(data); return p; }
    catch (...) { fFree(data); throw; }
}
} // namespace mpp
