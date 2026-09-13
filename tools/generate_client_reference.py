#!/usr/bin/env python3
"""Render reviewed module descriptions with source-derived settings; --check detects drift."""
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def clean(s):
    return re.sub(
        r'("(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\')|//[^\n]*|/\*[\s\S]*?\*/',
        lambda m: m.group(1) or " ",
        s,
    )


def args(s, start):
    depth = 0
    quote = None
    escape = False
    parts = []
    last = start
    for i in range(start, len(s)):
        c = s[i]
        if quote:
            if escape:
                escape = False
            elif c == "\\":
                escape = True
            elif c == quote:
                quote = None
            continue
        if c in "\"'":
            quote = c
        elif c in "({[":
            depth += 1
        elif c in ")}]":
            if depth == 0:
                parts.append(s[last:i].strip())
                return parts, i
            depth -= 1
        elif c == "," and depth == 0:
            parts.append(s[last:i].strip())
            last = i + 1
    raise ValueError("unclosed")


def extract(p):
    s = clean(p.read_text(encoding="utf-8"))
    vals = []
    for m in re.finditer(r"new\s+(\w+Value)\s*\(", s):
        a, end = args(s, m.end())
        prefix = s[: m.start()].split(";")[-1]
        field = re.search(r"(\w+)\s*=\s*$", prefix)
        vals.append(
            dict(
                type=m[1],
                args=a,
                field=field[1] if field else "",
                start=m.start(),
                end=end,
            )
        )
    return vals


paths = {p.stem: p for p in (ROOT / "src/main/java/cn/omix/module").rglob("*.java")}
paths["Drag"] = ROOT / "src/main/java/cn/omix/ui/hud/Drag.java"
s = clean(
    (ROOT / "src/main/java/cn/omix/module/ModuleManager.java").read_text(
        encoding="utf-8"
    )
)
names = re.findall(
    r"new (\w+)\(\)",
    s[s.index("        addModules(") : s.index("        sortModules();")],
)
out = []
for name in names:
    p = paths[name]
    s = clean(p.read_text(encoding="utf-8"))
    base = re.search(r"class\s+\w+\s+extends\s+(\w+)", s)[1]
    vals = extract(p)
    inherited = []
    while base != "Module":
        parent = paths.get(base)
        if not parent:
            raise ValueError(f"Unknown module superclass: {base}")
        inherited += extract(parent)
        ps = clean(parent.read_text(encoding="utf-8"))
        parent_base = re.search(r"class\s+\w+\s+extends\s+(\w+)", ps)
        base = parent_base[1] if parent_base else "Module"
    sm = re.search(r'super\(\s*"([^"]+)"', s)
    out.append(
        dict(
            id=name,
            name=sm[1] if sm else name,
            category=p.parent.name,
            path=str(p.relative_to(ROOT)),
            values=vals + inherited,
        )
    )


def compact(value):
    return " ".join(value.split())


def literal(value):
    value = compact(value)
    if value.startswith('"') and value.endswith('"'):
        return value[1:-1] or "空文本"
    if value == "MAX_MULTI_TARGETS":
        source = (
            ROOT / "src/main/java/cn/omix/module/impl/combat/TPAura.java"
        ).read_text(encoding="utf-8")
        return re.search(r"MAX_MULTI_TARGETS\s*=\s*(\d+)", source)[1]
    if re.fullmatch(r"-?(?:\d[\d_]*(?:\.\d*)?|\.\d+)[fFdD]?", value):
        return value.rstrip("fFdD").replace("_", "")
    return {"Color.WHITE": "白色", "GLFW.GLFW_KEY_X": "X"}.get(value, value)


def predicate(value, fields):
    value = compact(value).removeprefix("() -> ")
    value = re.sub(
        r'(\w+)\.is\("([^"\n]+)"\)',
        lambda m: fields.get(m[1], m[1]) + " = " + m[2],
        value,
    )
    value = re.sub(
        r"(\w+)(?:\.getValue\(\)|::getValue)",
        lambda m: fields.get(m[1], m[1]) + " 开启",
        value,
    )
    value = value.replace(
        "this::isPredictionMode", "Prediction 或 Prediction2 模式"
    ).replace("this::usesAttackTiming", "1.9+ 或 Heypixel 模式")
    return value.replace("&&", "且").replace("||", "或").replace("!", "非 ")


def render(module, review):
    values = module["values"]
    expected = {v["args"][0].strip('"') for v in values}
    if not review["description"].strip():
        raise ValueError(f"Missing module description: {module['id']}")
    if expected != set(review["settings"]):
        raise ValueError(
            f"{module['id']} settings differ: {expected ^ set(review['settings'])}"
        )
    fields = {v["field"]: v["args"][0].strip('"') for v in values if v["field"]}
    lines = [
        f"## {module['name']}",
        "",
        review["description"],
        "",
        f"源码：`{module['path']}`。",
    ]
    if values:
        lines += ["", "| 配置项 | 简介 | 类型、默认值与限制 |", "| --- | --- | --- |"]
    groups = []
    for v in values:
        a = v["args"]
        kind = v["type"]
        name = a[0].strip('"')
        meta = []
        cond = [x for x in a if "->" in x and not x.startswith("new ") or "::" in x]
        group = next((g for g in groups if g["start"] < v["start"] < g["end"]), None)
        if group:
            cond += [
                x
                for x in group["args"]
                if "->" in x and not x.startswith("new ") or "::" in x
            ]
        if kind == "MultiBoolValue":
            meta = ["布尔选项组"]
            groups.append(v)
        elif kind == "ModeValue":
            meta = [
                "模式",
                "默认 " + literal(a[1]),
                "可选 " + " / ".join(literal(x) for x in a[2:] if x.startswith('"')),
            ]
        elif kind == "NumberValue":
            meta = [
                "数值",
                "默认 " + literal(a[1]),
                literal(a[2]) + "–" + literal(a[3]),
                "步长 "
                + (
                    literal(a[4])
                    if len(a) > 4 and "->" not in a[4] and "::" not in a[4]
                    else "1"
                ),
            ]
        elif kind == "BoolValue":
            meta = ["布尔", "默认 " + literal(a[1])]
        elif kind == "TextValue":
            meta = [
                "文本",
                "敏感值（默认值省略）" if a[-1] == "true" else "默认 " + literal(a[1]),
            ]
        elif kind == "ColorValue":
            meta = ["颜色", "默认 " + literal(a[1])]
        elif kind == "KeyValue":
            meta = ["键位", "默认 " + literal(a[1])]
        else:
            raise ValueError("Unknown Value type: " + kind)
        if module["id"] == "ChannelHider":
            name = "动态频道 namespace:path"
            meta = ["动态布尔", "默认 true"]
        if group:
            meta.append("属于 " + group["args"][0].strip('"'))
        if cond:
            meta.append(
                "显示条件："
                + " 且 ".join(predicate(x, fields) for x in dict.fromkeys(cond))
            )
        desc = review["settings"][a[0].strip('"')]
        if not desc.strip():
            raise ValueError("Empty description: " + name)
        lines.append(
            "| "
            + " | ".join(x.replace("|", "\\|") for x in [name, desc, "；".join(meta)])
            + " |"
        )
    if not values:
        lines += ["", "没有额外 Value 配置；通用开关、快捷键和列表可见性见总览。"]
    if module["id"] in ("ModuleList", "TargetHUD"):
        lines += [
            "",
            "继承的拖动位置配置：`percentX` 保存相对屏幕宽度的横向位置比例，`percentY` 保存相对屏幕高度的纵向位置比例；通过 HUD 拖动编辑器调整并保存，不属于聊天命令可设置的 Value。",
        ]
    return "\n".join(lines) + "\n"


def main():
    import argparse

    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true")
    check = parser.parse_args().check
    reviewed = json.loads(
        (ROOT / "tools/client_reference_descriptions.json").read_text(encoding="utf-8")
    )
    if set(reviewed) != {m["id"] for m in out}:
        raise ValueError("Registered modules and descriptions differ")
    failures = []
    for category in sorted({m["category"] for m in out}):
        content = f"# {category.title()} 模块\n\n<!-- Generated by tools/generate_client_reference.py; edit tools/client_reference_descriptions.json. -->\n\n"
        content += "\n".join(
            render(m, reviewed[m["id"]]) for m in out if m["category"] == category
        )
        target = ROOT / f"docs/modules/{category}.md"
        if check:
            if not target.exists() or target.read_text(encoding="utf-8") != content:
                failures.append(str(target.relative_to(ROOT)))
        else:
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_text(content, encoding="utf-8")
    if failures:
        raise SystemExit("Outdated reference: " + ", ".join(failures))
    print(
        f"{'Checked' if check else 'Generated'} {len(out)} modules, {sum(len(m['values']) for m in out)} settings/group entries (including dynamic template)."
    )


if __name__ == "__main__":
    main()
