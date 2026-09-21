/* mppread.h - rozhraní knihovny mppread.dll (čtečka harmonogramů MS Project .mpp / .mpt / .xml)
 * Licence: GNU LGPL 2.1 nebo pozdější (viz COPYING.LIB). Copyright (c) 2026 Milan Knor (M3K Soft). Knihovnu lze nahradit vlastní upravenou verzí se stejným rozhraním.
 *
 * Rozhraní je čisté C (žádné C++ objekty přes hranici DLL):
 *   MppRead_ApiVersion()  -> verze rozhraní (aktuálně 1); aplikace odmítne jinou.
 *   MppRead_Open(path, &data, &size, err, errLen)
 *        -> 0 = OK: data/size = serializovaný model projektu (formát viz serial.h, SERIAL_MAGIC/SERIAL_VERSION)
 *        -> jinak chyba: err = popis (UTF-16, max errLen znaků vč. nuly)
 *   MppRead_Free(data)    -> uvolní data vrácená z MppRead_Open (alokovaná uvnitř knihovny).
 */
#pragma once
#include <stddef.h>
#include <stdint.h>

#define MPPREAD_API_VERSION 1

#ifdef MPPREAD_BUILD
#define MPPREAD_EXPORT extern "C" __declspec(dllexport)
#else
#define MPPREAD_EXPORT extern "C" __declspec(dllimport)
#endif

typedef int (__cdecl *MppRead_ApiVersion_t)(void);
typedef int (__cdecl *MppRead_Open_t)(const wchar_t* path, uint8_t** data, size_t* size, wchar_t* err, int errLen);
typedef void (__cdecl *MppRead_Free_t)(uint8_t* data);
