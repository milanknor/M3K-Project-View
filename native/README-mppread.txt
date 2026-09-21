mppread.dll - čtečka harmonogramů MS Project pro M3K Project View
====================================================================

Licence: GNU LGPL 2.1 nebo pozdější (COPYING.LIB). Čtení formátu MPP je port části knihovny MPXJ
(Copyright (c) Packwood Software 2002-2022, Jon Iles, https://github.com/joniles/mpxj; LGPL 2.1 nebo pozdější;
výchozí stav repozitáře: commit c5e1320cde0acd404031495aa66c17639a57239b ze 17. 9. 2026; seznam souborů v hlavičce mpp.h).
Úpravy a převod do C++: Copyright (c) 2026 Milan Knor (M3K Soft). Soubory model.h, serial.h a mppload.h: licence MIT
(LICENSE-MIT.txt), (c) 2026 Milan Knor (M3K Soft).

Obsah balíčku
  mppread.cpp, mppread.h   rozhraní knihovny (čisté C: MppRead_ApiVersion / MppRead_Open / MppRead_Free)
  mpp.h                    čtečka MPP12 (Project 2007) a MPP14 (Project 2010-365)
  mspdi.h                  čtečka XML (MSPDI) přes XmlLite
  cfb.h                    čtení kontejneru OLE2 (MS-CFB)
  model.h, serial.h        datový model a jeho přenos do aplikace (formát SERIAL_MAGIC / SERIAL_VERSION)
  mppload.h                jak knihovnu načítá aplikace (pro informaci a pro testovací mppdump)
  mppdump.cpp              testovací konzole: mppdump soubor.mpp / mppdump -dll soubor.mpp
  build-mppread.cmd        sestavení (MSVC Build Tools 2022, zdarma)

Instalace / náhrada knihovny
  1. Upravte zdroje, spusťte build-mppread.cmd.
  2. Zkopírujte out\mppread.dll do složky programu M3K Project View (místo původního souboru).
  Rozhraní (mppread.h, MPPREAD_API_VERSION) a formát dat (serial.h, SERIAL_VERSION) musí zůstat
  kompatibilní s nainstalovanou verzí programu, jinak program knihovnu odmítne s chybovou hláškou.
