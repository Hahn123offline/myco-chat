# 组体手势完全接管（修复 fling 绕过 gate）（2026-11-26）

根因：之前的 gate 只在 pointerInput 的拖动阶段生效；用户直接"把页面扔出去"时，惯性会走 Compose 内部 nested scroll/fling，绕过我们的边界判断。
解决：组体**完全自己分发滚动**，不再让任何自动 nested scroll/fling 参与。
- 组体内的 LazyColumn `userScrollEnabled=false`。
- 组体 pointerInput 在每次手势开始时判断：
  - 组体还能在该方向滚动 → 本次手势所有位移和惯性都 `dispatchRawDelta` 给组体滚动状态；
  - 组体已在该方向边界 → 所有位移和惯性都 `dispatchRawDelta` 给外层消息 LazyColumn。
- 两种目标都由我们自己的衰减 fling 驱动，**没有任何自动的 nested scroll 惯性传递**。
- 因此：
  - 手贴屏滑到边界：撞墙；同一手势反向只滚组体；
  - 手指离屏"扔"：惯性只作用于当次手势选中的目标，不会绕过 gate；
  - 只有新手势起点在边界，才会选中外层。
- 真机验证：展开长思考后连续快速上甩，前几次只滚组体；组体到底后新的甩动开始滚外层。外层没有出现"先无视 gate 滚一会再停"的自动惯性。
