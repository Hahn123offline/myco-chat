#!/usr/bin/env python3
"""UI 交互穷举与覆盖率对账工具。

用途：把「一个页面有多少可交互点」变成机器可数的分母，防止人肉盘点漏项。

用法：
  # 1) 扫一个源码树，产出交互点清单
  python tools/ui-audit/ui_audit.py scan --root <源码目录> --out <findings.csv>

  # 2) 用事实库（人工审计结果）对账，列出未覆盖项
  python tools/ui-audit/ui_audit.py check --findings <findings.csv> --facts <facts.csv> \
         --out <report.md>

两阶段门禁（docs/spec/README.md §2）：
  P1 事实库必须过 check；未覆盖项 = 0 才允许进入 P2。
  逐条未覆盖必须二选一：写进事实库（status=E0/E1），或写进排除表（status=excluded + 理由）。

facts.csv 列：path,line,kind,status,node_id,note
  status ∈ {E0, E1, excluded}
  node_id：对应 docs/spec/panels/*.md 的节点 ID，excluded 时写 '-'。
"""
from __future__ import annotations

import argparse
import csv
import re
import sys
from dataclasses import dataclass, field
from pathlib import Path

SKIP_DIRS = {
    "node_modules", ".git", "dist", "build", "out", "coverage",
    "__pycache__", ".gradle", ".idea", "vendor", "third_party",
}

# (kind, 正则, 说明)。按行匹配，一行可命中多个 kind。
PATTERNS: list[tuple[str, re.Pattern[str], str]] = [
    ("jsx.onclick", re.compile(r"\bonClick\b"), "JSX 单击"),
    ("jsx.onauxclick", re.compile(r"\bonAuxClick\b"), "中键单击"),
    ("jsx.ondoubleclick", re.compile(r"\bonDoubleClick\b"), "双击"),
    ("jsx.oncontextmenu", re.compile(r"\bonContextMenu\b"), "右键/长按菜单"),
    ("jsx.onpointer", re.compile(r"\bon(PointerDown|PointerUp|PointerMove|PointerCancel)\b"), "指针手势"),
    ("jsx.ontouch", re.compile(r"\bon(TouchStart|TouchMove|TouchEnd)\b"), "触摸手势"),
    ("jsx.ondrag", re.compile(r"\bon(DragStart|DragOver|Drop|DragEnd)\b"), "拖放"),
    ("jsx.onwheel", re.compile(r"\bonWheel\b"), "滚轮"),
    ("jsx.onscroll", re.compile(r"\bonScroll\b"), "滚动"),
    ("jsx.onkeydown", re.compile(r"\bon(KeyDown|KeyUp)\b"), "键盘"),
    ("jsx.onfocus", re.compile(r"\bon(Focus|Blur)\b"), "焦点"),
    ("native.button", re.compile(r"<button\b"), "原生按钮"),
    ("native.anchor", re.compile(r"<a\s[^>]*href"), "链接"),
    ("native.input", re.compile(r"<(input|select|textarea)\b"), "原生输入"),
    ("native.details", re.compile(r"<(details|summary)\b"), "原生折叠"),
    ("aria.role", re.compile(r"\brole=\{?[\"']?(button|menuitem|tab|switch|option|checkbox)"), "ARIA 可交互角色"),
    ("disclosure", re.compile(r"\b(useDisclosure|useBoundDisclosure|expandOnRowClick|onToggle)\b"), "可展开"),
    ("pointer.events", re.compile(r"\b(pointerEvents|touchAction|userSelect)\b"), "指针事件控制"),
    ("a11y.expanded", re.compile(r"\baria-expanded\b"), "展开态语义"),
    ("container.scroll", re.compile(r"\b(overflowY|overscrollBehavior|scrollIntoView|scrollTo|scrollBy)\b"), "滚动控制"),
]

CSS_PATTERNS: list[tuple[str, re.Pattern[str], str]] = [
    ("css.cursor", re.compile(r"cursor:\s*(pointer|grab|text|not-allowed)"), "光标语义"),
    ("css.focus", re.compile(r":focus(-visible|-within)?"), "焦点样式"),
    ("css.sticky", re.compile(r"position:\s*sticky"), "吸附"),
    ("css.contain", re.compile(r"\bcontain:"), "尺寸隔离"),
    ("css.overflow", re.compile(r"overflow(-y)?:\s*(auto|scroll|hidden)"), "溢出/滚动容器"),
]


@dataclass
class Finding:
    kind: str
    path: str
    line: int
    col: int
    snippet: str


@dataclass
class ScanResult:
    root: str
    findings: list[Finding] = field(default_factory=list)
    files_scanned: int = 0
    files_skipped: int = 0


def iter_files(root: Path, exts: set[str]):
    for p in root.rglob("*"):
        if p.is_dir():
            continue
        if any(part in SKIP_DIRS for part in p.parts):
            continue
        if p.suffix.lower() in exts:
            yield p


def scan(root: Path, exts: set[str]) -> ScanResult:
    res = ScanResult(root=str(root))
    for path in iter_files(root, exts):
        res.files_scanned += 1
        try:
            text = path.read_text(encoding="utf-8", errors="replace")
        except OSError:
            res.files_skipped += 1
            continue
        rel = str(path.relative_to(root)).replace("\\", "/")
        is_css = path.suffix.lower() in {".css", ".scss", ".less"}
        table = CSS_PATTERNS if is_css else PATTERNS
        for lineno, raw in enumerate(text.splitlines(), start=1):
            stripped = raw.strip()
            for kind, rx, _desc in table:
                m = rx.search(raw)
                if m is None:
                    continue
                col = m.start() + 1
                snippet = stripped[:140]
                res.findings.append(Finding(kind, rel, lineno, col, snippet))
    return res


def cmd_scan(args: argparse.Namespace) -> int:
    root = Path(args.root)
    if not root.is_dir():
        print(f"[ERR] root 不存在: {root}", file=sys.stderr)
        return 2
    exts = {"." + e.strip().lstrip(".") for e in args.include.split(",") if e.strip()}
    res = scan(root, exts)
    out = Path(args.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    with out.open("w", encoding="utf-8", newline="") as fh:
        w = csv.writer(fh)
        w.writerow(["kind", "path", "line", "col", "snippet"])
        for f in res.findings:
            w.writerow([f.kind, f.path, f.line, f.col, f.snippet])

    by_kind: dict[str, int] = {}
    for f in res.findings:
        by_kind[f.kind] = by_kind.get(f.kind, 0) + 1
    print(f"[scan] root={root}")
    print(f"[scan] files={res.files_scanned} skipped={res.files_skipped}")
    print(f"[scan] findings={len(res.findings)}  ->  {out}")
    for kind in sorted(by_kind, key=lambda k: (-by_kind[k], k)):
        print(f"        {by_kind[kind]:5d}  {kind}")
    return 0


def key_of(kind: str, path: str, line: int) -> str:
    return f"{kind}|{path}|{line}"


def read_facts(path: Path) -> dict[str, dict[str, str]]:
    facts: dict[str, dict[str, str]] = {}
    with path.open(encoding="utf-8", newline="") as fh:
        for row in csv.DictReader(fh):
            k = key_of(row.get("kind", ""), row.get("path", ""), int(row.get("line", "0") or 0))
            facts[k] = row
    return facts


def cmd_check(args: argparse.Namespace) -> int:
    findings_path = Path(args.findings)
    facts_path = Path(args.facts)
    if not findings_path.is_file():
        print(f"[ERR] findings 不存在: {findings_path}", file=sys.stderr)
        return 2
    facts = read_facts(facts_path) if facts_path.is_file() else {}

    rows: list[dict[str, str]] = []
    with findings_path.open(encoding="utf-8", newline="") as fh:
        for row in csv.DictReader(fh):
            rows.append(row)

    uncovered: list[dict[str, str]] = []
    covered = 0
    excluded = 0
    used: set[str] = set()
    for row in rows:
        k = key_of(row["kind"], row["path"], int(row["line"]))
        fact = facts.get(k)
        if fact is None:
            uncovered.append(row)
            continue
        used.add(k)
        status = (fact.get("status") or "").strip()
        if status == "excluded":
            excluded += 1
        elif status in {"E0", "E1"}:
            covered += 1
        else:
            uncovered.append(row)
    stale = [k for k in facts if k not in used]

    by_kind: dict[str, int] = {}
    for row in uncovered:
        by_kind[row["kind"]] = by_kind.get(row["kind"], 0) + 1

    lines = [
        "# UI 盘点覆盖率对账",
        "",
        f"- findings：`{findings_path}`（{len(rows)} 条）",
        f"- facts：`{facts_path}`（{len(facts)} 条）",
        f"- 已取证 E0/E1：**{covered}**",
        f"- 显式排除：**{excluded}**",
        f"- **未覆盖：{len(uncovered)}**",
        f"- 失效条目（facts 有、findings 已无）：{len(stale)}",
        "",
    ]
    if by_kind:
        lines += ["## 未覆盖分布", "", "| kind | 条数 |", "|---|---|"]
        for kind in sorted(by_kind, key=lambda k: (-by_kind[k], k)):
            lines.append(f"| `{kind}` | {by_kind[kind]} |")
        lines += [
            "",
            "## 未覆盖明细（必须逐条处理：取证进事实库，或进排除表写理由）",
            "",
            "| kind | 位置 | 代码片段 |",
            "|---|---|---|",
        ]
        for row in uncovered[: args.max_rows]:
            snippet = (row["snippet"] or "").replace("|", "\\|")
            lines.append(f"| `{row['kind']}` | `{row['path']}:{row['line']}` | `{snippet}` |")
        if len(uncovered) > args.max_rows:
            lines.append(f"| … | 另有 {len(uncovered) - args.max_rows} 条 | |")
    if stale:
        lines += ["", "## 失效条目（源码已变，需重审）", ""]
        for k in stale[:100]:
            lines.append(f"- `{k}`")

    report = "\n".join(lines) + "\n"
    out = Path(args.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(report, encoding="utf-8")
    print(f"[check] covered={covered} excluded={excluded} uncovered={len(uncovered)} stale={len(stale)}")
    print(f"[check] report -> {out}")
    if args.fail_on_uncovered and uncovered:
        return 1
    return 0


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description="UI 交互穷举与覆盖率对账")
    sub = ap.add_subparsers(dest="cmd", required=True)

    s = sub.add_parser("scan", help="扫描源码树，产出交互点清单")
    s.add_argument("--root", required=True)
    s.add_argument("--out", required=True)
    s.add_argument("--include", default="ts,tsx,js,jsx,kts,kt,css")
    s.set_defaults(func=cmd_scan)

    c = sub.add_parser("check", help="与事实库对账")
    c.add_argument("--findings", required=True)
    c.add_argument("--facts", default="")
    c.add_argument("--out", required=True)
    c.add_argument("--max-rows", type=int, default=200)
    c.add_argument("--fail-on-uncovered", action="store_true")
    c.set_defaults(func=cmd_check)

    args = ap.parse_args(argv)
    return args.func(args)


if __name__ == "__main__":
    raise SystemExit(main())
