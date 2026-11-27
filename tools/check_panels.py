import re, sys, pathlib

PANELS = {
    'base.layout': 'BL', 'base.primitives': 'BP', 'chat': 'CHAT',
    'composer': 'CMP', 'model': 'MODEL', 'attachment': 'ATT',
    'settings': 'SET', 'sidebar': 'SB',
}

def check(path: pathlib.Path, prefix: str):
    text = path.read_text(encoding='utf-8', errors='replace')
    lines = text.splitlines()
    out = {'file': path.name, 'lines': len(lines)}

    # 1) 必备章节
    need = ['## 0.', '## 1.', '## 2.', '## 3.', '## 4.', '## 5.']
    out['sections'] = {n: (n in text) for n in need}

    # 2) 节点节：每个 ### 到下一个 ### 之间，是否出现 | 1 | .. | 13 |
    sections = []
    start3 = next((i for i, l in enumerate(lines) if l.startswith('## 3.')), 0)
    end4 = next((i for i, l in enumerate(lines) if l.startswith('## 4.')), len(lines))
    idx = [i for i, l in enumerate(lines) if l.startswith('### ') and start3 < i < end4]
    bad = []
    for k, start in enumerate(idx):
        end = idx[k + 1] if k + 1 < len(idx) else len(lines)
        block = '\n'.join(lines[start:end])
        nums = set(int(m) for m in re.findall(r'^\|\s*(\d{1,2})\s*\|', block, re.M))
        if not {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13}.issubset(nums):
            missing = sorted({1,2,3,4,5,6,7,8,9,10,11,12,13} - nums)
            bad.append({'title': lines[start][:70], 'missing': missing})
        sections.append(lines[start])
    out['node_sections'] = len(sections)
    out['bad_nodes'] = bad[:10]
    out['bad_nodes_count'] = len(bad)

    # 3) 验收 ID：唯一 + 连续
    ids = sorted({m for m in re.findall(rf'\b{prefix}-(\d{{2}})\b', text)}, key=int)
    nums = [int(x) for x in ids]
    gaps = [n for n in range(1, (max(nums) + 1) if nums else 1) if n not in nums]
    out['ids'] = len(nums)
    out['id_range'] = (min(nums), max(nums)) if nums else None
    out['gaps'] = gaps[:12]
    return out

root = pathlib.Path('docs/spec/panels')
allok = True
for name, prefix in PANELS.items():
    p = root / f'{name}.md'
    if not p.is_file():
        print(f'MISSING {name}')
        allok = False
        continue
    r = check(p, prefix)
    sec = ''.join('' if v else '!' for v in r['sections'].values())
    status = 'OK' if (not r['bad_nodes_count'] and not r['gaps'] and all(r['sections'].values())) else 'ISSUE'
    if status != 'OK':
        allok = False
    print(f"{name:16} {r['lines']:5}行 nodes={r['node_sections']:3} ids={r['ids']:3} "
          f"range={r['id_range']} gaps={r['gaps']} bad_nodes={r['bad_nodes_count']} sections={sec}  [{status}]")
    for b in r['bad_nodes']:
        print(f"    - 缺字段 {b['missing']}: {b['title']}")

sys.exit(0 if allok else 1)
