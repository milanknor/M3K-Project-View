# -*- coding: utf-8 -*-
"""Vygeneruje ikonu M3K Project View (ICO s vice velikostmi + PNG 256) bez externich knihoven.

Kresba odpovida Icons.java (antracitovy zaobleny ctverec, dva oranzove pruhy,
diamant milniku, oblouk). Pri zmene tvaru upravit obe mista.

Pouziti: py -3 make_icon.py vystup.ico   (funguje i s Pythonem 2.7)
"""
import math
import struct
import sys
import zlib

DARK = (0x11, 0x11, 0x15)
ORANGE = (0xFF, 0x79, 0x0A)
SS = 4  # supersampling na pixel (SS x SS vzorku)

CORNER = 14.0
BARS = [(9.0, 20.0, 22.0, 8.0), (15.0, 31.0, 24.0, 8.0)]  # x, y, w, h; polomer rohu 4
DIAMOND = (41.0, 47.0, 5.5)  # stred x, y, polovina uhlopricky
ARC_CENTER = (24.0, 32.0)
ARC_R = 28.0
ARC_W = 3.6
ARC_HALF_ANGLE = math.radians(62.0)


def in_round_rect(x, y, rx, ry, w, h, r):
    if not (rx <= x < rx + w and ry <= y < ry + h):
        return False
    cx = min(max(x, rx + r), rx + w - r)
    cy = min(max(y, ry + r), ry + h - r)
    return (x - cx) ** 2 + (y - cy) ** 2 <= r * r


def in_symbol(x, y):
    for bx, by, bw, bh in BARS:
        if in_round_rect(x, y, bx, by, bw, bh, 4.0):
            return True
    dx, dy, half = DIAMOND
    if abs(x - dx) + abs(y - dy) <= half:
        return True
    ax = x - ARC_CENTER[0]
    ay = y - ARC_CENTER[1]
    d = math.hypot(ax, ay)
    if ARC_R - ARC_W / 2 <= d <= ARC_R + ARC_W / 2 and ax > 0:
        if abs(math.atan2(ay, ax)) <= ARC_HALF_ANGLE:
            return True
    return False


def sample(u, v):
    """Barva (r, g, b, a) v souradnicich 0..64."""
    if in_symbol(u, v):
        return ORANGE + (255,)
    if in_round_rect(u, v, 0.0, 0.0, 64.0, 64.0, CORNER):
        return DARK + (255,)
    return (0, 0, 0, 0)


PAGE = (255, 255, 255)
PAGE_EDGE = (0x9a, 0xa0, 0xa6)
FOLD = 14.0


def sample_doc(u, v):
    """Ikona dokumentu (.mpp v Pruzkumniku): bily list s ohnutym rohem a symbolem M3K."""
    # list: x 8..56, y 2..62, prava horni cast odriznuta ohybem
    if not (8.0 <= u < 56.0 and 2.0 <= v < 62.0):
        return (0, 0, 0, 0)
    if u > 56.0 - FOLD and v < 2.0 + FOLD and (u - (56.0 - FOLD)) > (v - 2.0):
        # oblast nad diagonalou ohybu je pruhledna, samotny ohyb sedy trojuhelnik
        return (0, 0, 0, 0)
    edge = u < 9.2 or u >= 54.8 or v < 3.2 or v >= 60.8
    if u > 56.0 - FOLD - 1.2 and v < 2.0 + FOLD + 1.2 and abs((u - (56.0 - FOLD)) - (v - 2.0)) < 1.4:
        edge = True
    if u > 56.0 - FOLD and v < 2.0 + FOLD:
        return PAGE_EDGE + (255,) if edge else (0xe6, 0xe8, 0xeb, 255)
    if edge:
        return PAGE_EDGE + (255,)
    # symbol zmenseny do stredu listu (pruhy + diamant + oblouk v meritku 0.7)
    su = (u - 32.0) / 0.7 + 32.0
    sv = (v - 34.0) / 0.7 + 32.0
    if in_symbol(su, sv):
        return ORANGE + (255,)
    return PAGE + (255,)


def pixel(x, y, size, fn=None):
    fn = fn or sample
    scale = 64.0 / size
    r = g = b = a = 0
    n = SS * SS
    for j in range(SS):
        for i in range(SS):
            u = (x + (i + 0.5) / SS) * scale
            v = (y + (j + 0.5) / SS) * scale
            sr, sg, sb, sa = fn(u, v)
            r += sr * sa
            g += sg * sa
            b += sb * sa
            a += sa
    if a == 0:
        return (0, 0, 0, 0)
    return (r // a, g // a, b // a, a // n)


def png(size, fn=None):
    raw = bytearray()
    for y in range(size):
        raw.append(0)
        for x in range(size):
            raw.extend(pixel(x, y, size, fn))

    def chunk(kind, data):
        body = kind + data
        return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body) & 0xFFFFFFFF)

    ihdr = struct.pack(">IIBBBBB", size, size, 8, 6, 0, 0, 0)
    return b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", ihdr) + chunk(b"IDAT", zlib.compress(bytes(raw), 9)) + chunk(b"IEND", b"")


def ico(sizes, fn=None):
    images = [(n, png(n, fn)) for n in sizes]
    header = struct.pack("<HHH", 0, 1, len(images))
    offset = 6 + 16 * len(images)
    entries = b""
    data = b""
    for n, blob in images:
        entries += struct.pack("<BBBBHHII", n if n < 256 else 0, n if n < 256 else 0, 0, 0, 1, 32, len(blob), offset)
        data += blob
        offset += len(blob)
    return header + entries + data


def main():
    out = sys.argv[1] if len(sys.argv) > 1 else "icon.ico"
    with open(out, "wb") as f:
        f.write(ico([16, 24, 32, 48, 64, 128, 256]))
    png_path = out[:-4] + ".png" if out.lower().endswith(".ico") else out + ".png"
    with open(png_path, "wb") as f:
        f.write(png(256))
    # ikona dokumentu vedle: <nazev>-doc.ico (pro pripony .mpp apod. v Pruzkumniku)
    doc = (out[:-4] if out.lower().endswith(".ico") else out) + "-doc.ico"
    with open(doc, "wb") as f:
        f.write(ico([16, 24, 32, 48, 64, 128, 256], sample_doc))
    with open(doc[:-4] + ".png", "wb") as f:
        f.write(png(256, sample_doc))
    print(out)
    print(doc)


if __name__ == "__main__":
    main()
