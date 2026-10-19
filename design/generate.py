#!/usr/bin/env python3
"""Generates platform files from design/tokens.json.

Outputs:
  app/src/main/java/dev/purpletv/design/Tokens.kt   Compose (TV app)
  app/src/main/assets/web/tokens.css                CSS custom properties (phone remote)
  homeassistant/themes/couch.yaml                   Home Assistant theme

Run `python3 design/generate.py` after editing tokens.json.
`--check` exits non-zero when a generated file is out of date (used in CI).
"""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
TOKENS = json.loads((ROOT / "design" / "tokens.json").read_text(encoding="utf-8"))

KOTLIN_OUT = ROOT / "app/src/main/java/dev/purpletv/design/Tokens.kt"
CSS_OUT = ROOT / "app/src/main/assets/web/tokens.css"
HA_OUT = ROOT / "homeassistant/themes/couch.yaml"

HEADER = "Generated from design/tokens.json by design/generate.py. Do not edit."


def kebab(name: str) -> str:
    return re.sub(r"(?<!^)(?=[A-Z])", "-", name).lower()


def argb(hex_color: str) -> str:
    """'#RRGGBB' or '#AARRGGBB' -> '0xAARRGGBB'."""
    h = hex_color.lstrip("#").upper()
    if len(h) == 6:
        h = "FF" + h
    return "0x" + h


def css_color(hex_color: str) -> str:
    h = hex_color.lstrip("#")
    if len(h) == 8:  # AARRGGBB -> rgba()
        a, r, g, b = (int(h[i : i + 2], 16) for i in range(0, 8, 2))
        return f"rgba({r}, {g}, {b}, {round(a / 255, 3)})"
    return "#" + h.upper()


def fmt(n: float) -> str:
    return str(int(n)) if float(n).is_integer() else str(n)


def kotlin() -> str:
    out = [
        f"// {HEADER}",
        "package dev.purpletv.design",
        "",
        "import androidx.compose.ui.graphics.Color",
        "import androidx.compose.ui.text.font.FontWeight",
        "import androidx.compose.ui.unit.dp",
        "import androidx.compose.ui.unit.sp",
        "",
        "/** Raw design tokens. Prefer the semantic helpers in Theme.kt in UI code. */",
        "object Tokens {",
        "    object Colors {",
    ]
    for name, t in TOKENS["color"].items():
        out.append(f"        /** {t.get('description', '')} */")
        out.append(f"        val {name} = Color({argb(t['value'])})")
    out.append("    }")

    for group, obj in (("space", "Space"), ("radius", "Radius"), ("layout", "Layout")):
        out.append(f"    object {obj} {{")
        for name, t in TOKENS[group].items():
            out.append(f"        val {name} = {fmt(t['value'])}.dp")
        out.append("    }")

    out.append("    object Type {")
    for name, t in TOKENS["type"].items():
        out.append(
            f"        val {name} = TypeToken({fmt(t['size'])}.sp, {fmt(t['line'])}.sp, "
            f"FontWeight({t['weight']}), {fmt(t['tracking'])}.sp)"
        )
    out.append("    }")

    out.append("    object Motion {")
    for name, t in TOKENS["motion"].items():
        out.append(f"        const val {name} = {t['value']}")
    out.append("    }")

    out.append("    object Focus {")
    for name, t in TOKENS["focus"].items():
        v = t["value"]
        out.append(f"        val {name} = {fmt(v)}.dp" if name.endswith("Width") else f"        const val {name} = {v}f")
    out.append("    }")
    out.append("}")
    out.append("")
    return "\n".join(out)


def css() -> str:
    out = [f"/* {HEADER} */", ":root {"]
    for name, t in TOKENS["color"].items():
        out.append(f"  --pt-color-{kebab(name)}: {css_color(t['value'])};")
    for group in ("space", "radius", "layout"):
        for name, t in TOKENS[group].items():
            out.append(f"  --pt-{group}-{kebab(name)}: {fmt(t['value'])}px;")
    for name, t in TOKENS["type"].items():
        k = kebab(name)
        out.append(f"  --pt-type-{k}-size: {fmt(t['size'])}px;")
        out.append(f"  --pt-type-{k}-line: {fmt(t['line'])}px;")
        out.append(f"  --pt-type-{k}-weight: {t['weight']};")
        out.append(f"  --pt-type-{k}-tracking: {fmt(t['tracking'])}px;")
    for name, t in TOKENS["motion"].items():
        out.append(f"  --pt-motion-{kebab(name)}: {t['value']}ms;")
    out.append(f"  --pt-focus-ring-width: {fmt(TOKENS['focus']['ringWidth']['value'])}px;")
    out.append("}")
    out.append("")
    return "\n".join(out)


def ha_theme() -> str:
    c = {k: css_color(v["value"]) for k, v in TOKENS["color"].items()}
    r = TOKENS["radius"]
    mapping = {
        "primary-color": c["hub"],
        "accent-color": c["hubGlow"],
        "light-primary-color": c["hubLight"],
        "dark-primary-color": c["hubDeep"],
        "primary-background-color": c["canvas"],
        "secondary-background-color": c["surface"],
        "card-background-color": c["surface"],
        "ha-card-background": c["surface"],
        "ha-card-border-radius": f"{fmt(r['lg']['value'])}px",
        "ha-card-border-width": "0px",
        "primary-text-color": c["textPrimary"],
        "secondary-text-color": c["textSecondary"],
        "disabled-text-color": c["textTertiary"],
        "divider-color": c["outline"],
        "app-header-background-color": c["canvas"],
        "app-header-text-color": c["textPrimary"],
        "sidebar-background-color": c["canvas"],
        "sidebar-text-color": c["textSecondary"],
        "sidebar-selected-icon-color": c["hubLight"],
        "sidebar-selected-text-color": c["textPrimary"],
        "state-icon-color": c["textSecondary"],
        "state-icon-active-color": c["hubLight"],
        "state-on-color": c["hub"],
        "switch-checked-color": c["hub"],
        "error-color": c["danger"],
        "warning-color": c["warning"],
        "success-color": c["success"],
        "info-color": c["info"],
        "mdc-theme-primary": c["hub"],
        "paper-item-icon-color": c["textSecondary"],
        "code-editor-background-color": c["surfaceRaised"],
        "input-fill-color": c["surfaceHigh"],
        "couch-twitch-color": c["twitch"],
        "couch-youtube-color": c["youtube"],
        "couch-live-color": c["live"],
    }
    out = [
        f"# {HEADER}",
        "# Copy to <config>/themes/ and add `frontend: themes: !include_dir_merge_named themes`",
        "# to configuration.yaml, then pick \"Couch\" in your profile.",
        "Couch:",
        "  modes:",
        "    dark:",
    ]
    for key, value in mapping.items():
        out.append(f'      {key}: "{value}"')
    out.append("")
    return "\n".join(out)


def main() -> int:
    check = "--check" in sys.argv
    stale = []
    for path, content in ((KOTLIN_OUT, kotlin()), (CSS_OUT, css()), (HA_OUT, ha_theme())):
        if check:
            if not path.exists() or path.read_text(encoding="utf-8") != content:
                stale.append(str(path.relative_to(ROOT)))
        else:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(content, encoding="utf-8")
            print(f"wrote {path.relative_to(ROOT)}")
    if stale:
        print("Out of date, run python3 design/generate.py:\n  " + "\n  ".join(stale))
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
