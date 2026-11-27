import re, pathlib

PANELS = [
    ('base.layout', 'BL'), ('base.primitives', 'BP'), ('chat', 'CHAT'),
    ('composer', 'CMP'), ('model', 'MODEL'), ('attachment', 'ATT'),
    ('settings', 'SET'), ('sidebar', 'SB'),
]
ROOT = pathlib.Path('docs/spec/panels')
OUT = pathlib.Path('docs/spec/acceptance.md')

# 历史遗留条目（不在任何 panels §4 里，但已存在于验收清单）
LEGACY = [
    ('CHAT-01', '折叠行摘要流式变化时行顶 y 恒定、下方正文零位移', '真机录屏'),
    ('CHAT-02', '展开长思考后向下滚 3 屏，头部仍在视口顶可点收起（形态 A）', '真机手势'),
    ('CHAT-03', '流式摘要右端为渐隐，无省略号', '截图比对'),
    ('CHAT-04', '运行中摘要 = 最后完成段落首个非空行；结束后 = 首行；均无 `**`', '真机 + 数据构造'),
    ('CHAT-05', '重建/切分支后回到折叠态', '真机手势'),
]

def parse_rows(text: str, prefix: str):
    """从 §4 验收清单里抽出 | ID | 判定 | 方式 | 依赖 | 形式的行。"""
    m = re.search(r'^## 4\.', text, re.M)
    if not m:
        return []
    sec = text[m.start():]
    m5 = re.search(r'^## 5\.', sec, re.M)
    if m5:
        sec = sec[:m5.start()]
    rows = []
    for line in sec.splitlines():
        if not line.strip().startswith('|'):
            continue
        cells = [c.strip() for c in line.strip().strip('|').split('|')]
        if not cells:
            continue
        idm = re.fullmatch(rf'`?({prefix}-\d{{2}})`?', cells[0])
        if not idm:
            continue
        rid = idm.group(1)
        rest = cells[1:]
        judge = rest[0] if len(rest) > 0 else ''
        method = rest[1] if len(rest) > 1 else ''
        dep = rest[2] if len(rest) > 2 else ''
        rows.append((rid, judge, method, dep))
    return rows

parts = [
    '# 验收总表（P4 唯一打勾处）',
    '',
    '> 本文件由 `tools/gen_acceptance.py` 从 `docs/spec/panels/*.md` 的 §4 自动汇总。',
    '> **判定全文以实现规格为准**（每行给了所属面板文件）；本表只用于记录通过状态。',
    '> 状态：`⬜ 未做` / `🟨 已实现待验` / `✅ 通过` / `❌ 不通过（附原因）` / `⚠️ 已知缺口` / `⏸ 待真机` / `🚫 候选未启用`',
    '',
]

total = 0
for name, prefix in PANELS:
    p = ROOT / f'{name}.md'
    rows = parse_rows(p.read_text(encoding='utf-8'), prefix)
    # 去重（同 ID 重复引用时只保留首次）
    seen, uniq = set(), []
    for r in rows:
        if r[0] in seen:
            continue
        seen.add(r[0])
        uniq.append(r)
    total += len(uniq)
    parts += [f'## {name}（{len(uniq)} 条，规格：[panels/{name}.md](panels/{name}.md)）', '']
    if not uniq:
        parts += ['（该面板的验收清单未解析到行，请检查其 §4 格式）', '']
        continue
    parts += ['| ID | 判定 | 方式 | 依赖 | 状态 |', '|---|---|---|---|---|']
    for rid, judge, method, dep in uniq:
        judge = judge.replace('\n', ' ')
        if len(judge) > 120:
            judge = judge[:117] + '…'
        parts.append(f'| {rid} | {judge} | {method} | {dep} | ⬜ |')
    parts.append('')

parts += ['## 历史遗留（P3 之前已存在，规格见 [panels/chat.md](panels/chat.md)）', '',
          '| ID | 判定 | 方式 | 状态 |', '|---|---|---|---|']
for rid, judge, method in LEGACY:
    parts.append(f'| {rid} | {judge} | {method} | ⬜ |')
total += len(LEGACY)
parts += ['', f'---', '', f'**合计 {total} 条**（其中历史遗留 {len(LEGACY)} 条）。']

OUT.write_text('\n'.join(parts) + '\n', encoding='utf-8')
print(f'wrote {OUT} with {total} acceptance items')
