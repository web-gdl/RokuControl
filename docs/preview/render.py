#!/usr/bin/env python3
"""Genera docs/screenshots/*.png.

NO son capturas reales: son renders con las mismas medidas, colores y trazos que el código
(ui/Theme.java, ui/Icons.java, ui/DPadView.java, ui/MainActivity.java), con datos de ejemplo.

Uso:  python3 docs/preview/render.py [--fonts DIR_CON_roboto-latin-*.woff2] [--chrome RUTA]
Requisitos: Python 3, playwright (con Chrome/Chromium), Pillow.
Roboto (Apache 2.0): `npm pack @fontsource/roboto@5.1.0` y usar la carpeta package/files.
"""
import argparse, math, os, tempfile
from pathlib import Path
from PIL import Image
from playwright.sync_api import sync_playwright

# --- Theme.java ---
BG, SURFACE, PRESSED, SHEET = "#0E1014", "#1A1D25", "#2C3140", "#161920"
ACCENT, TEXT, DIM, OK, WARN, OFF, DANGER = "#8B6CFF", "#EDEFF5", "#8D93A5", "#3DDC84", "#FFB020", "#5A5F70", "#FF6B6B"
W, H = 360, 800  # dp


def f(v):
    return f"{v:.2f}"


def icon(kind, cx, cy, s, color):
    """Port de ui/Icons.java a SVG."""
    h = s / 2
    sw = s * 0.1
    st = f'fill="none" stroke="{color}" stroke-width="{f(sw)}" stroke-linecap="round" stroke-linejoin="round"'
    fl = f'fill="{color}"'
    o = []
    if kind == "POWER":
        r = h * .7
        a0, a1 = math.radians(-60), math.radians(240)
        o.append(f'<path d="M{f(cx+r*math.cos(a0))} {f(cy+r*math.sin(a0))} A{f(r)} {f(r)} 0 1 1 {f(cx+r*math.cos(a1))} {f(cy+r*math.sin(a1))}" {st}/>')
        o.append(f'<path d="M{f(cx)} {f(cy-h*.85)} L{f(cx)} {f(cy-h*.1)}" {st}/>')
    elif kind == "HOME":
        o.append(f'<path d="M{f(cx-h*.8)} {f(cy)} L{f(cx)} {f(cy-h*.75)} L{f(cx+h*.8)} {f(cy)} '
                 f'M{f(cx-h*.5)} {f(cy-h*.2)} L{f(cx-h*.5)} {f(cy+h*.7)} L{f(cx+h*.5)} {f(cy+h*.7)} L{f(cx+h*.5)} {f(cy-h*.2)}" {st}/>')
    elif kind == "BACK":
        o.append(f'<path d="M{f(cx-h*.3)} {f(cy-h*.55)} L{f(cx-h*.75)} {f(cy-h*.1)} L{f(cx-h*.3)} {f(cy+h*.35)} '
                 f'M{f(cx-h*.75)} {f(cy-h*.1)} L{f(cx+h*.2)} {f(cy-h*.1)} Q{f(cx+h*.75)} {f(cy-h*.1)} {f(cx+h*.75)} {f(cy+h*.4)}" {st}/>')
    elif kind == "PLAY":
        o.append(f'<path d="M{f(cx-h*.8)} {f(cy-h*.6)} L{f(cx-h*.8)} {f(cy+h*.6)} L{f(cx-h*.1)} {f(cy)} Z" {fl}/>')
        for x0, x1 in ((.2, .42), (.6, .82)):
            o.append(f'<rect x="{f(cx+h*x0)}" y="{f(cy-h*.6)}" width="{f(h*(x1-x0))}" height="{f(h*1.2)}" rx="{f(h*.08)}" {fl}/>')
    elif kind in ("REV", "FWD"):
        d = 1 if kind == "FWD" else -1
        for i in range(2):
            base = cx + d * (-.55 + i * .65) * h
            apex = base + d * .65 * h
            o.append(f'<path d="M{f(base)} {f(cy-h*.55)} L{f(base)} {f(cy+h*.55)} L{f(apex)} {f(cy)} Z" {fl}/>')
    elif kind == "REPLAY":
        r = h * .7
        a0, a1 = math.radians(-40), math.radians(40)
        o.append(f'<path d="M{f(cx+r*math.cos(a0))} {f(cy+r*math.sin(a0))} A{f(r)} {f(r)} 0 1 0 {f(cx+r*math.cos(a1))} {f(cy+r*math.sin(a1))}" {st}/>')
        ex, ey = cx + r * math.cos(a1), cy + r * math.sin(a1)
        dx, dy = math.sin(a1), -math.cos(a1)
        p = [(ex + dx*h*.45, ey + dy*h*.45), (ex - dy*h*.35 - dx*h*.1, ey + dx*h*.35 - dy*h*.1), (ex + dy*h*.35 - dx*h*.1, ey - dx*h*.35 - dy*h*.1)]
        o.append(f'<path d="M{f(p[0][0])} {f(p[0][1])} L{f(p[1][0])} {f(p[1][1])} L{f(p[2][0])} {f(p[2][1])} Z" {fl}/>')
    elif kind == "OPTIONS":
        for a in (90, 30, 150):
            dx, dy = math.cos(math.radians(a))*h*.8, math.sin(math.radians(a))*h*.8
            o.append(f'<path d="M{f(cx-dx)} {f(cy-dy)} L{f(cx+dx)} {f(cy+dy)}" {st}/>')
    elif kind in ("VOL_UP", "VOL_DOWN", "MUTE"):
        o.append(f'<path d="M{f(cx-h*.85)} {f(cy-h*.25)} L{f(cx-h*.45)} {f(cy-h*.25)} L{f(cx)} {f(cy-h*.65)} L{f(cx)} {f(cy+h*.65)} '
                 f'L{f(cx-h*.45)} {f(cy+h*.25)} L{f(cx-h*.85)} {f(cy+h*.25)} Z" {fl}/>')
        if kind == "VOL_UP":
            o.append(f'<path d="M{f(cx+h*.35)} {f(cy)} L{f(cx+h*.9)} {f(cy)} M{f(cx+h*.625)} {f(cy-h*.275)} L{f(cx+h*.625)} {f(cy+h*.275)}" {st}/>')
        elif kind == "VOL_DOWN":
            o.append(f'<path d="M{f(cx+h*.35)} {f(cy)} L{f(cx+h*.9)} {f(cy)}" {st}/>')
        else:
            o.append(f'<path d="M{f(cx+h*.3)} {f(cy-h*.3)} L{f(cx+h*.85)} {f(cy+h*.3)} M{f(cx+h*.3)} {f(cy+h*.3)} L{f(cx+h*.85)} {f(cy-h*.3)}" {st}/>')
    elif kind == "KEYBOARD":
        o.append(f'<rect x="{f(cx-h*.9)}" y="{f(cy-h*.55)}" width="{f(h*1.8)}" height="{f(h*1.1)}" rx="{f(h*.15)}" {st}/>')
        for i in (-1, 0, 1):
            o.append(f'<circle cx="{f(cx+i*h*.5)}" cy="{f(cy-h*.2)}" r="{f(h*.08)}" {fl}/>')
        o.append(f'<path d="M{f(cx-h*.45)} {f(cy+h*.25)} L{f(cx+h*.45)} {f(cy+h*.25)}" {st}/>')
    elif kind == "APPS":
        for sx in (-1, 1):
            for sy in (-1, 1):
                x, y = cx + sx*h*.4, cy + sy*h*.4
                o.append(f'<rect x="{f(x-h*.3)}" y="{f(y-h*.3)}" width="{f(h*.6)}" height="{f(h*.6)}" rx="{f(h*.1)}" {fl}/>')
    elif kind == "CHEVRON":
        o.append(f'<path d="M{f(cx-h*.5)} {f(cy-h*.2)} L{f(cx)} {f(cy+h*.3)} L{f(cx+h*.5)} {f(cy-h*.2)}" {st}/>')
    elif kind == "CLOSE":
        o.append(f'<path d="M{f(cx-h*.5)} {f(cy-h*.5)} L{f(cx+h*.5)} {f(cy+h*.5)} M{f(cx-h*.5)} {f(cy+h*.5)} L{f(cx+h*.5)} {f(cy-h*.5)}" {st}/>')
    return "".join(o)


def text(x, y, s, size, color=TEXT, weight=400, anchor="start"):
    s = s.replace("&", "&amp;")
    return f'<text x="{f(x)}" y="{f(y)}" font-size="{size}" fill="{color}" font-weight="{weight}" text-anchor="{anchor}">{s}</text>'


def pill(x, y, w, h, fill=SURFACE):
    return f'<rect x="{f(x)}" y="{f(y)}" width="{f(w)}" height="{f(h)}" rx="{f(h/2)}" fill="{fill}"/>'


def status_bar():
    return (text(20, 24, "9:41", 13, TEXT, 500)
            + f'<rect x="318" y="14" width="22" height="11" rx="2.5" fill="none" stroke="{TEXT}" stroke-width="1.2"/>'
            + f'<rect x="320" y="16" width="15" height="7" rx="1.2" fill="{TEXT}"/>'
            + f'<path d="M290 25 L294 19 L298 25 Z M300 25 L304 16 L308 25 Z" fill="{TEXT}" opacity=".9"/>')


def dpad(cx, cy, R):
    o = [f'<circle cx="{f(cx)}" cy="{f(cy)}" r="{f(R)}" fill="{SURFACE}"/>']
    ri = R * .34
    o.append(f'<circle cx="{f(cx)}" cy="{f(cy)}" r="{f(ri+3)}" fill="{BG}"/>')
    o.append(f'<circle cx="{f(cx)}" cy="{f(cy)}" r="{f(ri)}" fill="{SURFACE}"/>')
    o.append(text(cx, cy + ri * .62 * .36, "OK", ri * .62, TEXT, 700, "middle"))
    d, a = R * .67, R * .09
    for dx, dy in ((0, -1), (0, 1), (-1, 0), (1, 0)):
        x, y = cx + dx * d, cy + dy * d
        p = [(x + dx*a*1.4, y + dy*a*1.4), (x - dx*a + (-dy)*a*1.5, y - dy*a + dx*a*1.5), (x - dx*a - (-dy)*a*1.5, y - dy*a - dx*a*1.5)]
        o.append(f'<path d="M{f(p[0][0])} {f(p[0][1])} L{f(p[1][0])} {f(p[1][1])} L{f(p[2][0])} {f(p[2][1])} Z" fill="{TEXT}"/>')
    return "".join(o)


def control_screen(name="Sala", sub="Roku TV Test · 192.168.1.20", dot=OK, tv=True):
    """Pantalla principal (360x800 dp) según MainActivity.buildUi."""
    o = [f'<rect width="{W}" height="{H}" fill="{BG}"/>', status_bar()]
    top = 24 + 8
    # Cabecera
    o.append(f'<circle cx="{f(16+10+5)}" cy="{f(top+32)}" r="5" fill="{dot}"/>')
    o.append(text(16 + 10 + 10 + 12, top + 30, name, 18, TEXT, 700))
    o.append(text(16 + 10 + 10 + 12, top + 47, sub, 13, DIM))
    o.append(icon("CHEVRON", 252, top + 32, 22, DIM))
    op = "1" if tv else ".38"
    o.append(f'<g opacity="{op}"><circle cx="{f(W-16-26)}" cy="{f(top+32)}" r="26" fill="{SURFACE}"/>')
    o.append(icon("POWER", W - 16 - 26, top + 32, 24, DANGER) + "</g>")
    # Cruceta
    y0, y1 = top + 64 + 6, H - 24 - 8 - 4 * 68 - 6
    o.append(dpad(W / 2, (y0 + y1) / 2, min(W - 32, y1 - y0) / 2 - 4))
    # Filas
    def row(y, items, locked=()):
        n = len(items)
        slot = (W - 32) / n
        for i, (kind, label) in enumerate(items):
            x = 16 + i * slot + 5
            w = slot - 10
            op = ".38" if kind in locked else "1"
            o.append(f'<g opacity="{op}">' + pill(x, y, w, 60))
            if label is None:
                o.append(icon(kind, x + w / 2, y + 30, 26, TEXT))
            else:
                tw = len(label) * 8.4
                x0 = x + (w - (26 + 10 + tw)) / 2
                o.append(icon(kind, x0 + 13, y + 30, 26, TEXT))
                o.append(text(x0 + 36, y + 35.5, label, 15, TEXT, 700))
            o.append("</g>")
    base = H - 24 - 8 - 4 * 68 + 8
    locked = () if tv else ("VOL_DOWN", "MUTE", "VOL_UP")
    row(base, [("BACK", None), ("HOME", None), ("REPLAY", None), ("OPTIONS", None)])
    row(base + 68, [("REV", None), ("PLAY", None), ("FWD", None)])
    row(base + 136, [("VOL_DOWN", None), ("MUTE", None), ("VOL_UP", None)], locked)
    row(base + 204, [("KEYBOARD", "Teclado"), ("APPS", "Apps")])
    return "".join(o)


def sheet(title, body_svg, height):
    y = H - height
    r = 24
    return (f'<rect width="{W}" height="{H}" fill="#000" opacity=".55"/>'
            f'<path d="M0 {H} L0 {y+r} Q0 {y} {r} {y} L{W-r} {y} Q{W} {y} {W} {y+r} L{W} {H} Z" fill="{SHEET}"/>'
            + text(20, y + 40, title, 20, TEXT, 700) + body_svg(y))


def input_box(x, y, w, hint, value=None):
    o = f'<rect x="{x}" y="{y}" width="{w}" height="46" rx="14" fill="{SURFACE}"/>'
    o += text(x + 14, y + 29, value or hint, 15, TEXT if value else DIM)
    return o


def devices_sheet(y):
    o = []
    rows = [("Sala", "192.168.1.20 · en uso", OK, PRESSED), ("Dormitorio", "192.168.1.34 · disponible", OK, SURFACE),
            ("Cocina", "192.168.1.51 · sin respuesta", OFF, SURFACE), ("Roku Express", "192.168.1.60 · disponible · nuevo", OK, SURFACE)]
    yy = y + 58
    for i, (n, sub, dot, bg) in enumerate(rows):
        o.append(f'<rect x="20" y="{yy}" width="320" height="62" rx="16" fill="{bg}"/>')
        o.append(f'<circle cx="41" cy="{yy+31}" r="5" fill="{dot}"/>')
        o.append(text(60, yy + 27, n, 16, TEXT, 700))
        o.append(text(60, yy + 46, sub, 13, DIM))
        if i != 3:
            o.append(icon("CLOSE", 316, yy + 31, 30, DIM))
        yy += 70
    yy += 8
    o.append(input_box(20, yy, 320, "IP del Roku, p. ej. 192.168.1.20"))
    yy += 56
    o.append(pill(20, yy, 155, 46) + text(97.5, yy + 29, "Buscar de nuevo", 15, TEXT, 700, "middle"))
    o.append(pill(185, yy, 155, 46, ACCENT) + text(262.5, yy + 29, "Conectar por IP", 15, BG, 700, "middle"))
    return "".join(o)


def apps_sheet(y):
    o = [input_box(20, y + 56, 320, "Filtrar")]
    yy = y + 114
    for n in ("Disney+", "Hulu", "Netflix", "Prime Video", "YouTube", "Plex", "Twitch"):
        o.append(f'<rect x="20" y="{yy}" width="320" height="50" rx="16" fill="{SURFACE}"/>')
        o.append(text(36, yy + 31, n, 16))
        yy += 58
    return "".join(o)


def keyboard_sheet(y):
    o = [input_box(20, y + 56, 320, "Escribe aquí", "breaking b")]
    o.append(text(20, y + 130, "Funciona cuando el Roku muestra su teclado en pantalla", 13, DIM))
    o.append(text(20, y + 148, "(por ejemplo, al buscar). Usa la tecla de enviar del teléfono", 13, DIM))
    o.append(text(20, y + 166, "para pulsar Enter en el Roku.", 13, DIM))
    return "".join(o)


def wrap(inner, w=W, h=H):
    return f'<svg xmlns="http://www.w3.org/2000/svg" width="{w}" height="{h}" viewBox="0 0 {w} {h}">{inner}</svg>'


def html(body, w, h, fonts):
    ff = "".join(f"@font-face{{font-family:Roboto;font-weight:{wt};src:url('file://{fonts}/roboto-latin-{wt}-normal.woff2')}}" for wt in (400, 500, 700))
    return f"<html><head><style>{ff}html,body{{margin:0;background:#000}}svg{{display:block;font-family:Roboto,sans-serif}}</style></head><body>{body}</body></html>"


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--fonts", default=os.environ.get("ROBOTO_DIR", "package/files"))
    ap.add_argument("--chrome", default="/usr/bin/google-chrome")
    a = ap.parse_args()
    fonts = str(Path(a.fonts).resolve())
    out = Path(__file__).resolve().parent.parent / "screenshots"
    out.mkdir(exist_ok=True)
    screens = {
        "01-control": wrap(control_screen()),
        "02-dispositivos": wrap(control_screen() + sheet("Dispositivos Roku", devices_sheet, 482)),
        "03-apps": wrap(control_screen() + sheet("Aplicaciones", apps_sheet, 560)),
        "04-teclado": wrap(control_screen() + sheet("Teclado", keyboard_sheet, 230)),
        "05-reproductor": wrap(control_screen("Roku Express", "Roku Express · 192.168.1.60", OK, tv=False)),
    }
    tmp = Path(tempfile.mkdtemp())
    with sync_playwright() as p:
        br = p.chromium.launch(executable_path=a.chrome, args=["--allow-file-access-from-files"])
        for name, svg in screens.items():
            pg = br.new_page(viewport={"width": W, "height": H}, device_scale_factor=2)
            fn = tmp / f"{name}.html"
            fn.write_text(html(svg, W, H, fonts))
            pg.goto(fn.as_uri())
            pg.wait_for_timeout(300)
            pg.screenshot(path=str(tmp / f"{name}.png"))
            pg.close()
        # Portada 1280x640
        phone = lambda svg, x: (f'<g transform="translate({x},50) scale(1.4)"><clipPath id="c{x}"><rect width="{W}" height="{H}" rx="28"/></clipPath>'
                                f'<rect x="-6" y="-6" width="{W+12}" height="{H+12}" rx="34" fill="#2A2E3A"/><g clip-path="url(#c{x})">{svg}</g></g>')
        # El teléfono se recorta al alto de la portada (muestra la mitad superior del control)
        left = (text(70, 230, "Roku Control", 64, TEXT, 700)
                + text(72, 285, "Control remoto privado para Roku TV", 26, DIM)
                + "".join(text(72, 350 + i * 36, t, 22, TEXT) for i, t in enumerate(
                    ["Sin cuentas, sin anuncios, sin telemetría", "Solo habla con tu Roku en la red local", "APK de 40 KB · código abierto"]))
                + pill(72, 480, 250, 56, ACCENT) + text(197, 516, "Descargar APK", 24, BG, 700, "middle")
                + text(72, 590, "Un regalo para la humanidad · Unlicense", 18, DIM))
        cover = (f'<svg xmlns="http://www.w3.org/2000/svg" width="1280" height="640" viewBox="0 0 1280 640">'
                 f'<defs><linearGradient id="g" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#14161D"/><stop offset="1" stop-color="#0A0B0F"/></linearGradient></defs>'
                 f'<rect width="1280" height="640" fill="url(#g)"/><circle cx="1000" cy="320" r="360" fill="{ACCENT}" opacity=".10"/>'
                 f'{left}{phone(control_screen(), 740)}</svg>')
        pg = br.new_page(viewport={"width": 1280, "height": 640})
        fn = tmp / "00-portada.html"
        fn.write_text(html(cover, 1280, 640, fonts))
        pg.goto(fn.as_uri())
        pg.wait_for_timeout(300)
        pg.screenshot(path=str(tmp / "00-portada.png"))
        br.close()
    for png in sorted(tmp.glob("*.png")):
        im = Image.open(png).convert("RGB").quantize(colors=256, method=Image.Quantize.MEDIANCUT, dither=Image.Dither.NONE)
        im.save(out / png.name, optimize=True)
        print(png.name, (out / png.name).stat().st_size // 1024, "KB")


if __name__ == "__main__":
    main()
