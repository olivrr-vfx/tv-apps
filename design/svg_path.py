"""Tiny SVG path flattener (M, h, v, l, a, z; absolute M/L) for design/brand.py PNG rendering."""
import math
import re


def _arc(x0, y0, rx, ry, large, sweep, x1, y1, steps=12):
    # Circular arcs only (rx == ry), enough for rounded corners.
    r = rx
    dx, dy = (x1 - x0) / 2, (y1 - y0) / 2
    d = math.hypot(dx, dy)
    if d == 0:
        return []
    r = max(r, d)
    h = math.sqrt(max(r * r - d * d, 0))
    mx, my = (x0 + x1) / 2, (y0 + y1) / 2
    ux, uy = -dy / d, dx / d
    sign = 1 if large != sweep else -1
    cx, cy = mx + sign * h * ux, my + sign * h * uy
    a0 = math.atan2(y0 - cy, x0 - cx)
    a1 = math.atan2(y1 - cy, x1 - cx)
    da = a1 - a0
    if sweep and da < 0:
        da += 2 * math.pi
    if not sweep and da > 0:
        da -= 2 * math.pi
    return [(cx + r * math.cos(a0 + da * i / steps), cy + r * math.sin(a0 + da * i / steps)) for i in range(1, steps + 1)]


def parse_path_polys(d, scale=1.0):
    tokens = re.findall(r"[MmLlHhVvAaZz]|-?\d*\.?\d+", d)
    polys, cur, x, y, i, cmd = [], [], 0.0, 0.0, 0, None
    def num():
        nonlocal i
        v = float(tokens[i]); i += 1
        return v
    while i < len(tokens):
        if re.match(r"[A-Za-z]", tokens[i]):
            cmd = tokens[i]; i += 1
        if cmd in "Mm":
            if cur: polys.append(cur)
            nx, ny = num(), num()
            x, y = (nx, ny) if cmd == "M" else (x + nx, y + ny)
            cur = [(x, y)]
            cmd = "L" if cmd == "M" else "l"
        elif cmd in "Ll":
            nx, ny = num(), num()
            x, y = (nx, ny) if cmd == "L" else (x + nx, y + ny)
            cur.append((x, y))
        elif cmd == "h":
            x += num(); cur.append((x, y))
        elif cmd == "v":
            y += num(); cur.append((x, y))
        elif cmd in "Aa":
            rx, ry, _rot, large, sweep, nx, ny = (num() for _ in range(7))
            ex, ey = (nx, ny) if cmd == "A" else (x + nx, y + ny)
            cur += _arc(x, y, rx, ry, int(large), int(sweep), ex, ey)
            x, y = ex, ey
        elif cmd in "Zz":
            if cur: polys.append(cur)
            cur = []
    if cur: polys.append(cur)
    return [[(px * scale, py * scale) for px, py in p] for p in polys]
