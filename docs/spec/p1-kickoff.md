# P1 开工手册（网络恢复后照此执行）

> 状态：**P0 已完成并拍板，P1 未开始**（2026-11-27 用户需关机、网络差，约定换环境全量开工）。
> 本文是"换到好网络后不需要再讨论、直接开跑"的执行清单。

## 0. 前置检查（3 分钟）

```powershell
# 1) 网络
git ls-remote --heads https://github.com/deepseek-ai/chat-demo   # 成功再继续
# 2) 主源（最关键，已在本机，无需网络）
Test-Path E:\deepseek-harness\packages\client                    # 必须 True
# 3) 工具
python --version                                                  # 3.14.6 已验证可用
```

## 1. 拉参考仓库到 `_ws/refs`（已 gitignore）

```powershell
$root='E:\myco-chat\_ws\refs'; New-Item -ItemType Directory -Force $root | Out-Null
$repos=@(
 @{n='dsh-Go';u='https://github.com/ccch1mneyyy/dsh-Go'},
 @{n='dsh-mobile-android';u='https://github.com/songzijiang/dsh-mobile-android'},
 @{n='dsh-client-ui-mobile-adapt';u='https://github.com/Hotsteel2901/dsh-client-ui-mobile-adapt'},
 @{n='taixu';u='https://github.com/taixu-lab/taixu'},
 @{n='chat-demo';u='https://github.com/deepseek-ai/chat-demo'}
)
foreach($r in $repos){
  $dir=Join-Path $root $r.n
  if(Test-Path (Join-Path $dir '.git')){ "SKIP $($r.n)"; continue }
  for($i=1;$i -le 4;$i++){
    git clone --depth 1 --single-branch $r.u $dir 2>&1 | Select-Object -Last 2
    if(Test-Path (Join-Path $dir '.git')){ "OK $($r.n)"; break }
    Start-Sleep 4
  }
}
```

**502 持续时的备选**（按成功率排序）：
1. `git -c http.version=HTTP/1.1 -c http.postBuffer=524288000 clone --depth 1 <url> <dir>`
2. 走 zip：`Invoke-WebRequest "https://codeload.github.com/<owner>/<repo>/zip/refs/heads/main" -OutFile _ws\refs\<n>.zip` 再解压
3. 镜像：`git clone --depth 1 https://ghproxy.net/https://github.com/<owner>/<repo> <dir>`

> 已有半成品 `_ws/refs/dsh-Go`（上次 502 中断），先删再拉：
> `Remove-Item -Recurse -Force E:\myco-chat\_ws\refs\dsh-Go`

## 2. 先跑一遍机器盘点（建立分母，10 分钟）

```powershell
cd E:\myco-chat
python tools\ui-audit\ui_audit.py scan --root E:\deepseek-harness\packages\client --out _ws\out\dsh-client-findings.csv
python tools\ui-audit\ui_audit.py scan --root app-android\src\main\kotlin   --out _ws\out\myco-findings.csv
# 每个参考仓库同样扫一份
```

**已实测基线（2026-11-27）**

| 对象 | 文件数 | 交互点 | 其中纯点击类（onClick/button/role） |
|---|---|---|---|
| DSH 全客户端（70 个 ui-* 包） | 1918 | **3060** | ≈1012 |
| DSH `ui-chat` 单包 | 113 | **141** | ≈54 |
| myco app-android 全量 | 24 | **158** | 158 |

> 这就是"功能密度"的量化：**聊天页单独一个包的可交互点就接近 myco 整个 App**。

## 3. 六路并行审计（P1 主体）

每路一个 subagent，**只读**，输出一份 `docs/spec/ref/<id>.md`，**写作用域互不重叠**。

| 路 | 对象 | 输出文件 | 重点 |
|---|---|---|---|
| R1 | DSH `packages/client`（主源，最重） | `ref/dsh-web.md` | 70 包逐个归位；ui-chat 深挖到每个节点 |
| R2 | `dsh-Go` | `ref/dsh-go.md` | 移动端**手势与命中区**的真实做法（补 Q-03） |
| R3 | `dsh-mobile-android` + `dsh-client-ui-mobile-adapt` | `ref/dsh-mobile.md` | 手机端如何压缩桌面 UI；键盘/安全区/横屏 |
| R4 | `taixu` | `ref/taixu.md` | 移动工作区/面板化架构；A2UI 声明式界面 |
| R5 | `chat-demo` | `ref/chat-demo.md` | 回复五态、贴底滚动、首 token 三段反馈 |
| R6 | 旧 myco（本仓库 Kotlin） | `ref/legacy-myco.md` | 全量可点元素 + 现命令映射 + 已知缺陷 |

**每路 prompt 必须包含**（自包含，subagent 看不到本会话）：
1. 铁律：禁止推测；`E0/E1/E2` 分级；`E2` 只许写进"待确认"小节，不许进结论。
2. 节点 13 字段定义（贴 `schema-node.md` 全文）。
3. 每个节点必须写**反证**："反过来做会坏在哪"。
4. 输出路径、格式（照抄 `schema-node.md` 金标准样本的表结构）。
5. 覆盖率门禁：用 `ui_audit.py scan` 扫你负责的树，事实库条目必须覆盖所有
   `jsx.onclick / native.button / jsx.oncontextmenu / disclosure / aria.role` 命中，
   差额写进 `## 排除清单`（每条一句理由）。

## 4. 覆盖率对账（P1 门禁）

```powershell
python tools\ui-audit\ui_audit.py check --findings _ws\out\dsh-client-findings.csv `
       --facts docs\spec\ref\dsh-web.facts.csv --out _ws\out\coverage-dsh-web.md
```

- 事实库需附一份机器可读 `ref/<id>.facts.csv`（列：`kind,path,line,status,node_id,note`，
  `status ∈ E0/E1/excluded`）。
- **门禁：未覆盖 = 0 才进 P2。** 对账报告存 `_ws/out/`，进 P2 时附在对照表后面。

## 5. 输出物与写作用域

```
docs/spec/ref/<id>.md            每路独占，互不重叠
docs/spec/ref/<id>.facts.csv     每路独占
docs/spec/ref/<id>.excluded.csv  可选
```

R1–R6 并行期间**禁止**改 `00-global-rules.md` / `schema-node.md` / `panels/*.md`（我主笔）。
发现规则要改 → 写进 `open-questions.md` 的"新增"小节。

## 6. 收尾与下一步

1. 我合并六份事实库 → 每面板一张**对照表**（参考A/B/C/旧myco 各自做法 + 冲突 + 建议）。
2. 用户逐面板拍板（P2）→ 写 `panels/<id>.md`（P3）。
3. 拍板结果回填 `acceptance.md`，然后才进 P4 实现。

## 7. 已知风险

| 风险 | 应对 |
|---|---|
| GitHub 502 反复 | §1 三套备选；R2–R5 若拉不下来，先用 R1+R6 开工，不阻塞 |
| 六路格式不统一 | prompt 内贴金标准样本；我合并时统一改写，不要求一次到位 |
| 事实库变成"截图描述" | 强制 `path:line` 可对账；对不上 = 不合格 |
| 审计膨胀成读完整个 DSH | 每路限定"只审与 myco 首版 8 个面板相关的包"，其余只登记一行归位 |
