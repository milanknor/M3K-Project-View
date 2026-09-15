# -*- coding: utf-8 -*-
r"""Shromazdi licence knihoven tretich stran z lib\*.jar do lib\licenses\.

Z kazdeho jaru vytahne META-INF/LICENSE*, NOTICE* apod. do podslozky se jmenem
knihovny a vygeneruje souhrn lib\licenses\THIRD-PARTY.txt. Knihovny bez vestaveneho
textu licence dostanou odkaz na spolecny text (Apache-2.0.txt, LGPL-2.1.txt).

Pouziti: py -3 tools\collect_licenses.py   (spousti se z build.ps1 -Package)
"""
import io
import os
import re
import shutil
import sys
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
LIB = os.path.join(ROOT, "lib")
OUT = os.path.join(LIB, "licenses")

# jar (bez verze) -> (nazev, licence, domovska stranka)
LIBRARIES = {
    "mpxj": ("MPXJ", "LGPL 2.1 nebo novejsi", "https://www.mpxj.org"),
    "poi": ("Apache POI", "Apache License 2.0", "https://poi.apache.org"),
    "jackcess": ("Jackcess", "Apache License 2.0", "https://jackcess.sourceforge.io"),
    "sqlite-jdbc": ("SQLite JDBC (Xerial)", "Apache License 2.0; SQLite je public domain", "https://github.com/xerial/sqlite-jdbc"),
    "jsoup": ("jsoup", "MIT License", "https://jsoup.org"),
    "rtfparserkit": ("RTF Parser Kit", "Apache License 2.0", "https://github.com/joniles/rtfparserkit"),
    "SparseBitSet": ("SparseBitSet", "Apache License 2.0", "https://github.com/brettwooldridge/SparseBitSet"),
    "commons-codec": ("Apache Commons Codec", "Apache License 2.0", "https://commons.apache.org/codec"),
    "commons-collections4": ("Apache Commons Collections", "Apache License 2.0", "https://commons.apache.org/collections"),
    "commons-io": ("Apache Commons IO", "Apache License 2.0", "https://commons.apache.org/io"),
    "commons-lang3": ("Apache Commons Lang", "Apache License 2.0", "https://commons.apache.org/lang"),
    "commons-logging": ("Apache Commons Logging", "Apache License 2.0", "https://commons.apache.org/logging"),
    "commons-math3": ("Apache Commons Math", "Apache License 2.0", "https://commons.apache.org/math"),
    "jackson-annotations": ("Jackson Annotations", "Apache License 2.0", "https://github.com/FasterXML/jackson"),
    "jackson-core": ("Jackson Core", "Apache License 2.0", "https://github.com/FasterXML/jackson"),
    "jackson-databind": ("Jackson Databind", "Apache License 2.0", "https://github.com/FasterXML/jackson"),
    "log4j-api": ("Apache Log4j API", "Apache License 2.0", "https://logging.apache.org/log4j"),
    "istack-commons-runtime": ("istack common utility code", "Eclipse Distribution License 1.0 (BSD)", "https://github.com/eclipse-ee4j/jaxb-istack-commons"),
    "jakarta.activation": ("Jakarta Activation", "Eclipse Distribution License 1.0 (BSD)", "https://github.com/eclipse-ee4j/jaf"),
    "jakarta.xml.bind-api": ("Jakarta XML Binding API", "Eclipse Distribution License 1.0 (BSD)", "https://github.com/eclipse-ee4j/jaxb-api"),
    "jaxb-core": ("JAXB Core", "Eclipse Distribution License 1.0 (BSD)", "https://github.com/eclipse-ee4j/jaxb-ri"),
    "jaxb-runtime": ("JAXB Runtime", "Eclipse Distribution License 1.0 (BSD)", "https://github.com/eclipse-ee4j/jaxb-ri"),
    "txw2": ("TXW2", "Eclipse Distribution License 1.0 (BSD)", "https://github.com/eclipse-ee4j/jaxb-ri"),
    "flatlaf": ("FlatLaf (vzhled uzivatelskeho rozhrani)", "Apache License 2.0", "https://www.formdev.com/flatlaf/"),
}

ENTRY_RE = re.compile(r"^META-INF/(.*/)?(LICENSE|NOTICE|COPYING)[^/]*$", re.IGNORECASE)
VERSION_RE = re.compile(r"^(.*?)-(\d[\w.]*)\.jar$")


def split_name(jar):
    m = VERSION_RE.match(jar)
    if m:
        return m.group(1), m.group(2)
    return jar[:-4], ""


def extract(jar_path, dest):
    """Vytahne licencni soubory z jaru; vrati seznam vytvorenych souboru."""
    created = []
    with zipfile.ZipFile(jar_path) as z:
        for entry in z.namelist():
            if not ENTRY_RE.match(entry) or entry.endswith("/"):
                continue
            name = os.path.basename(entry)
            sub = entry[len("META-INF/"):-len(name)].strip("/")
            if sub and not sub.startswith("maven"):
                name = sub.replace("/", "-") + "-" + name
            if not os.path.isdir(dest):
                os.makedirs(dest)
            target = os.path.join(dest, name)
            with z.open(entry) as src, open(target, "wb") as dst:
                shutil.copyfileobj(src, dst)
            created.append(name)
    return sorted(created)


def main():
    if os.path.isdir(OUT):
        shutil.rmtree(OUT)
    os.makedirs(OUT)

    jars = sorted(f for f in os.listdir(LIB) if f.lower().endswith(".jar"))
    rows = []
    apache_source = None
    for jar in jars:
        base, version = split_name(jar)
        if base not in LIBRARIES:
            sys.stderr.write("Neznama knihovna %s - doplnte ji do LIBRARIES v collect_licenses.py\n" % jar)
            return 1
        name, lic, url = LIBRARIES[base]
        dest = os.path.join(OUT, base)
        files = extract(os.path.join(LIB, jar), dest)
        if base == "commons-io" and "LICENSE.txt" in files:
            apache_source = os.path.join(dest, "LICENSE.txt")
        rows.append((name, version, lic, url, base, files))

    shutil.copy(os.path.join(LIB, "LICENSE-mpxj.txt"), os.path.join(OUT, "LGPL-2.1.txt"))
    if apache_source:
        shutil.copy(apache_source, os.path.join(OUT, "Apache-2.0.txt"))

    lines = [
        "M3K Project View - licence knihoven tretich stran",
        "=================================================",
        "",
        "Program M3K Project View je freeware (licencni podminky v souboru LICENSE.txt).",
        "Obsahuje nasledujici knihovny tretich stran, ktere zustavaji pod svymi",
        "puvodnimi licencemi. Texty licenci a soubory NOTICE jsou v teto slozce",
        "(podslozka podle nazvu knihovny); spolecne texty: Apache-2.0.txt, LGPL-2.1.txt.",
        "",
        "Knihovna MPXJ (LGPL) je prilozena jako samostatny soubor app\\mpxj.jar, takze",
        "ji lze nahradit jinou verzi v souladu s LGPL. Zdrojove kody MPXJ: https://github.com/joniles/mpxj",
        "",
        "Behove prostredi Java (Eclipse Temurin, OpenJDK 21) je sireno pod GNU GPL v2",
        "s vyjimkou Classpath; jeho licencni texty jsou ve slozce runtime\\legal\\.",
        "",
    ]
    for name, version, lic, url, base, files in rows:
        lines.append("%s %s" % (name, version))
        lines.append("  licence:  %s" % lic)
        lines.append("  web:      %s" % url)
        if files:
            lines.append("  soubory:  %s" % ", ".join(base + "/" + f for f in files))
        elif lic.startswith("Apache"):
            lines.append("  soubory:  Apache-2.0.txt (v jaru neni text licence prilozen)")
        elif lic.startswith("LGPL"):
            lines.append("  soubory:  LGPL-2.1.txt")
        lines.append("")
    with io.open(os.path.join(OUT, "THIRD-PARTY.txt"), "w", encoding="utf-8", newline="\r\n") as f:
        f.write("\n".join(lines))

    # index.txt: "titulek|cesta" - seznam pro dialog Licence v aplikaci (LICENSE.txt
    # do slozky se zdroji prikopiruje build.ps1)
    index = [
        u"M3K Project View – licenční podmínky|LICENSE.txt",
        u"Přehled knihoven třetích stran|THIRD-PARTY.txt",
    ]
    for name, version, lic, url, base, files in rows:
        label = ("%s %s" % (name, version)).strip()
        if files:
            for fn in files:
                index.append(u"%s – %s|%s/%s" % (label, fn, base, fn))
        elif lic.startswith("Apache"):
            index.append(u"%s – Apache License 2.0|Apache-2.0.txt" % label)
        elif lic.startswith("LGPL"):
            index.append(u"%s – LGPL 2.1|LGPL-2.1.txt" % label)
    index.append(u"Apache License 2.0 – společný text|Apache-2.0.txt")
    index.append(u"GNU LGPL 2.1 – společný text|LGPL-2.1.txt")
    with io.open(os.path.join(OUT, "index.txt"), "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(index) + "\n")
    print("%d knihoven, %d polozek, licence v %s" % (len(rows), len(index), OUT))
    return 0


if __name__ == "__main__":
    sys.exit(main())
