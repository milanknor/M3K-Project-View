# Žádost o licenční kontrolu: M3K Project View 1.0.1 (nativní C++), freeware s uzavřeným zdrojem a knihovnou pod LGPL 2.1

Prosím o kritickou kontrolu, zda níže popsané uspořádání a licenční podmínky splňují **GNU LGPL 2.1** a zda
nemám jiný licenční problém. Nechci zdvořilé potvrzení – hledej slabá místa, nejasné formulace a rizika.
Pokud si nejsi jistý, řekni to a uveď, co by bylo potřeba ověřit (případně u právníka). Odpověz česky.

## Cíl
- Program **M3K Project View** (autor **M3K Soft**) má být **freeware**: zdarma pro **soukromé i komerční** použití,
  **bez zveřejnění zdrojového kódu aplikace**, šíření jen bezplatně a v nezměněné podobě.
- Jde o nativní prohlížeč souborů Microsoft Project (.mpp, .xml) pro Windows, napsaný v C++ (Win32).

## Co je převzato a odkud
- Čtení binárního formátu **.mpp** je **převod (port) části knihovny MPXJ z Javy do C++**
  (https://github.com/joniles/mpxj, „Copyright (c) Packwood Software 2002–…“, autor Jon Iles).
  Hlavičky zdrojů MPXJ uvádějí: *„GNU Lesser General Public License … either version 2.1 of the License,
  or (at your option) any later version.“* Převáděny byly třídy pro čtení MPP12/MPP14 (struktury Props,
  FixedMeta/FixedData, VarMeta/Var2Data, FieldMap, čtení úkolů, zdrojů, přiřazení, kalendářů, vlastních polí,
  logika „updateStructure“ a „calculateCritical“). Port považuji za **odvozené dílo MPXJ → LGPL**.
- Žádný jiný cizí kód se nepoužívá, kromě:
  - algoritmů `days_from_civil` / `civil_from_days` od Howarda Hinnanta (převod data; autor je zveřejnil
    k volnému použití – prosím ověř, zda je potřeba uvádět poznámku),
  - systémových součástí Windows volaných přes API (XmlLite, GDI+, Common Controls) – nejsou přibaleny.
- Aplikace funkčně napodobuje moji vlastní dřívější Java aplikaci „M3K Project View“ (která MPXJ používá jako
  samostatný `mpxj.jar`). GUI kód nového programu je napsán znovu, z Java aplikace ani z MPXJ nepochází.
- Ikona je vlastní (z mé Java aplikace).

## Technické uspořádání (jak je LGPL část oddělená)
| Soubor | Obsah | Licence |
|---|---|---|
| `mppview.exe` | celá aplikace (GUI, Gantt, záložky, exporty CSV/XLSX/PNG/PDF, kontroly) | uzavřený freeware |
| `mppread.dll` | jen čtení .mpp/.xml (port MPXJ + vlastní čtení XML a kontejneru OLE2) | **LGPL 2.1** |
| `mppread-src.zip` | **úplný zdrojový kód mppread.dll** + skript pro sestavení + testovací konzole | LGPL 2.1 / MIT (viz níže) |
| `LICENSE.txt` | licenční podmínky programu (plné znění níže) | – |
| `COPYING.LIB` | doslovný text GNU LGPL 2.1 (převzatý z repozitáře MPXJ) | – |
| `LICENSE-MIT.txt` | text licence MIT pro sdílené hlavičky | – |

Podrobnosti:
1. **Dynamické načítání:** `mppview.exe` načítá `mppread.dll` až za běhu (`LoadLibraryExW` s plnou cestou do složky
   programu, pak `GetProcAddress`). Exe **neobsahuje žádný kód čtečky** – ověřeno i tak, že charakteristické řetězce
   čtečky jsou jen v DLL, ne v exe. Exe s DLL není staticky linkováno (ani importní knihovnou).
2. **Rozhraní je čisté C** (3 funkce): `MppRead_ApiVersion()`, `MppRead_Open(path, &data, &size, err, errLen)`,
   `MppRead_Free(data)`. DLL vrací načtený projekt jako **blok bajtů** (vlastní binární serializace), exe ho převede
   na svůj datový model. Obě strany mají staticky linkovaný runtime MSVC (`/MT`), proto se nepředávají C++ objekty.
3. **Nahraditelnost ověřena:** z rozbaleného `mppread-src.zip` jsem jen přiloženým skriptem (`build-mppread.cmd`,
   MSVC Build Tools 2022) sestavil novou `mppread.dll`, podstrčil ji aplikaci a výsledek byl identický.
   Při chybějící nebo cizí DLL aplikace nespadne, jen oznámí chybu.
4. **Sdílené hlavičky pod MIT:** `model.h` (datové struktury projektu, práce s datem, kalendářní výpočty, formátování),
   `serial.h` (serializace) a `mppload.h` (jak exe DLL načítá). Jsou **moje vlastní**, používá je exe i DLL,
   jsou zveřejněny v `mppread-src.zip` pod MIT, aby šla DLL sestavit. Upozornění k posouzení: `model.h` jsem psal
   sám, ale **význam kódů formátu** (jednotky dob trvání, typy opakování výjimek kalendáře, převody jednotek) jsem
   zjistil ze zdrojů MPXJ; logika vyhodnocení opakovaných výjimek kalendáře v `model.h` odpovídá interpretaci MPXJ.
5. **Soubory pod LGPL v DLL, které nejsou z MPXJ:** `cfb.h` (čtení kontejneru OLE2 podle specifikace Microsoft
   MS-CFB, vlastní) a `mspdi.h` (čtení XML podle schématu Microsoft MSPDI, vlastní; několik převodních pravidel,
   např. „měna v setinách“, převzato z chování MPXJ). Obojí jsem dobrovolně dal pod LGPL jako součást knihovny.
6. **Testovací konzole `mppdump.exe`** má čtečku zkompilovanou staticky, proto se **nešíří** v instalaci;
   její zdroj je v `mppread-src.zip` (LGPL).
7. **Instalátor** (Inno Setup 6) instaluje: `mppview.exe`, `mppread.dll`, `mppread-src.zip`, `LICENSE.txt`,
   `COPYING.LIB`, `LICENSE-MIT.txt`, `README.md`; před instalací zobrazí `LICENSE.txt`.
8. **V programu:** Nápověda → „Licenční podmínky…“ (otevře LICENSE.txt), „Složka programu (licence, zdroje
   mppread)…“; okno „O programu“ uvádí: *„Freeware pro soukromé i komerční použití … Soubory čte knihovna
   mppread.dll pod licencí GNU LGPL 2.1 – port části knihovny MPXJ (Jon Iles, https://github.com/joniles/mpxj);
   její zdrojový kód je přiložen (mppread-src.zip) a knihovnu lze nahradit.“*
9. Hlavička převedeného souboru `mpp.h` uvádí: *„Původní dílo: MPXJ, Copyright (c) Packwood Software 2002-2026
   (Jon Iles), GNU LGPL 2.1 nebo pozdější. Úpravy a převod do C++: Copyright (c) 2026 M3K Soft. Licence tohoto
   souboru: GNU LGPL 2.1.“* Soubory jsou označeny jako změněné (jde o převod do jiného jazyka).
10. Sestaveno **Microsoft Visual Studio Build Tools 2022** (statický runtime `/MT`), instalátor **Inno Setup 6**.

## Plné znění LICENSE.txt

```
M3K Project View (nativní verze) – licenční podmínky (freeware)
Copyright (c) 2026 M3K Soft

1. Program M3K Project View („Program“ – soubor mppview.exe a související soubory) je
   poskytován zdarma (freeware). Smíte jej používat bez omezení počtu instalací,
   k soukromým i komerčním účelům.

2. Program smíte volně kopírovat a dále šířit, a to pouze bezplatně, v nezměněné podobě
   (celý instalační balíček nebo celá složka Programu včetně mppread.dll,
   mppread-src.zip a licenčních souborů) a s těmito licenčními podmínkami. Není dovoleno
   Program prodávat, pronajímat, upravovat, dekompilovat ani z něj odvozovat jiná díla,
   není-li dále (bod 3) stanoveno jinak. Zdrojový kód Programu – s výjimkou knihovny
   podle bodu 3 – není součástí distribuce a autor jej neposkytuje.

3. Knihovna mppread.dll (čtení souborů .mpp a .xml) je samostatnou součástí Programu
   pod licencí GNU Lesser General Public License, verze 2.1 (soubor COPYING.LIB).
   Je odvozena z knihovny MPXJ (Copyright (c) Packwood Software, Jon Iles,
   https://github.com/joniles/mpxj). Její úplný
   zdrojový kód je přiložen v souboru mppread-src.zip. V souladu s LGPL smíte:
   a) knihovnu upravovat a šířit za podmínek LGPL,
   b) nahradit mppread.dll ve složce Programu vlastní upravenou verzí se stejným
      rozhraním (Program ji načítá dynamicky při spuštění),
   c) bez ohledu na bod 2 Program pro vlastní potřebu upravit a provádět jeho zpětnou
      analýzu v rozsahu nezbytném k použití a ladění takových úprav knihovny.
   Soubory model.h, serial.h a mppload.h v mppread-src.zip jsou poskytnuty pod licencí
   MIT (soubor LICENSE-MIT.txt), aby bylo možné knihovnu sestavit. Omezení bodu 2 se na
   obsah mppread-src.zip nevztahují.

4. Program pouze čte soubory, nepřipojuje se k síti a nikam neodesílá žádná data.

5. PROGRAM JE POSKYTOVÁN „TAK, JAK JE“, BEZ JAKÉKOLI ZÁRUKY, výslovné či
   předpokládané, včetně záruky prodejnosti, vhodnosti pro určitý účel nebo
   správnosti zobrazených údajů. Autor neodpovídá za žádnou škodu vzniklou
   používáním Programu nebo nemožností jej použít. Program používáte na vlastní
   odpovědnost; rozhodující jsou vždy údaje v původním souboru Microsoft Project.

6. Microsoft Project je ochranná známka společnosti Microsoft Corporation. Program
   není produktem společnosti Microsoft ani s ní není nijak spojen.

----------------------------------------------------------------------
(následuje anglické shrnutí se stejným obsahem; rozhoduje české znění)
```

## Otázky, na které prosím odpověz (u každé: ano / ne / nejisté + zdůvodnění s odkazem na konkrétní § LGPL 2.1)

1. Je správné považovat C++ převod kódu MPXJ za odvozené dílo, které musí zůstat pod LGPL? Mohu zvolit
   „LGPL 2.1“ (místo „2.1 nebo pozdější“), když MPXJ je „2.1 or any later version“?
2. Je `mppview.exe` v tomto uspořádání „work that uses the Library“ ve smyslu § 5–6, a smí tedy být pod
   vlastními (uzavřenými) podmínkami? Splňuje dynamické načítání DLL § 6 b) („suitable shared library mechanism“)?
3. Nevadí, že exe obsahuje moje hlavičky `model.h` / `serial.h` / `mppload.h`, které jsou zároveň součástí zdrojů
   knihovny (pod MIT)? Týká se toho § 5 (hlavičkové soubory knihovny – limit „10 řádků“ u maker/inline funkcí)?
4. Je problém, že významy kódů formátu a interpretace opakování kalendáře v `model.h` (MIT, v exe) vycházejí ze
   studia zdrojů MPXJ? Je to jen převzetí faktů o formátu, nebo riziko odvozeného díla? Doporučil bys tyto části
   raději přesunout do LGPL knihovny?
5. Stačí přiložit úplný zdrojový kód knihovny k binárce (`mppread-src.zip` v instalaci), nebo je potřeba i
   písemná nabídka / zveřejnění ke stažení? Je obsah balíčku úplný („complete source code“ včetně skriptů
   pro sestavení)?
6. Splňuje bod 3 c) LICENSE.txt požadavek § 6 LGPL 2.1 na podmínky, které „permit modification of the work
   for the customer's own use and reverse engineering for debugging such modifications“? Nebo je formulace
   „v rozsahu nezbytném k použití a ladění takových úprav knihovny“ příliš úzká? Navrhni přesné znění.
7. Jsou zákazy v bodě 2 (prodej, úpravy, dekompilace, šíření jen v nezměněné podobě) slučitelné s LGPL při
   výjimce v bodě 3? Nekoliduje „šíření jen v nezměněné podobě“ s právem uživatele šířit upravenou knihovnu?
8. Je dostatečné „prominent notice“ podle § 6 (okno O programu, LICENSE.txt, stránka licence v instalátoru)?
   Chybí někde uvedení copyrightu Packwood Software / Jon Iles nebo text licence?
9. Je v pořádku, že vlastní soubory `cfb.h` a `mspdi.h` jsou pod LGPL uvnitř knihovny?
10. Je `mppdump.exe` (statická čtečka) v pořádku, když se nešíří v instalaci, ale jeho zdroj je v balíčku?
11. Licence nástrojů: smím pro **komerčně používaný freeware** kompilovat pomocí **Visual Studio Build Tools 2022**
    (za jakých podmínek licence VS / Community) a staticky linkovat runtime MSVC? Smím použít **Inno Setup 6**
    pro instalátor programu určeného i ke komerčnímu použití (aktuální licenční podmínky Inno Setup)?
12. Algoritmy Howarda Hinnanta a jiné drobnosti – je potřeba nějaká poznámka o autorství?
13. Je potřeba ještě něco dalšího (poznámka „Microsoft Project“ jako ochranná známka, jméno programu,
    anglická verze licence, rozhodné právo, věk/spotřebitelské právo v ČR/EU, GDPR – program nic neodesílá)?

Na závěr prosím shrň: **(a) co je v pořádku, (b) co je nutné opravit (s navrženým zněním), (c) co je doporučené,
(d) co musí posoudit právník.**
