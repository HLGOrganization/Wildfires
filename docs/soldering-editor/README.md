# 8×8 洞洞板电路编辑器（Wildfires）

2026-10-20 重做。旧的四槽“焊接台”页面被整体替换，旧代码里只保留 `soldering_iron`
右键打开界面的入口。

- 页面：`src/main/resources/assets/apricityui/apricity/screens/soldering.html` 与
  `soldering.css`
- 逻辑：`src/main/java/first/wildfires/soldering/`
- 线框图：`docs/soldering-editor/wireframe.png`（非实机截图）
- 图标生成：`docs/soldering-editor/gen_icons.py`

## 1. 数据模型

### 1.1 板子

`CircuitBoard` 是菜单里的唯一权威状态：

- `int[64] cells`：每个格子一个 8 位编码，但**只存元件的锚点格**。
  编码 = `typeId(3 bit) | vertical(1 bit) << 3 | flipped(1 bit) << 4`，最大 29。
  元件的其余占位格由“锚点 + 类型 + 方向”推导，所以 2×3 芯片只占一个 cell 数组位置。
- `Wire[64] wireSlots` + `int wireCount`：每条线只存玩家点的两个洞
  `(x1,y1,x2,y2)`，不存路径。
- `int revision`：任何修改 +1，页面用它判断是否需要重画。

没有 BlockEntity。板子随菜单存在；关屏即丢弃未完成的板子。点“完成”后，产物
`soldered_circuit` 的 NBT 里带一份快照（`WildfiresCircuit`），所以离开界面后组合还在。

### 1.2 元件占位

| 元件 | id | 横向（水平） | 竖向 |
|---|---|---|---|
| 电阻 | `wildfires:resistor` | 2×1 | 1×2 |
| 电容 | `wildfires:capacitor` | 2×2 | 2×2（不可旋转） |
| 开关 | `wildfires:switch` | 3×1 | 1×3 |
| 二极管 | `wildfires:diode` | 2×1 | 1×2（另有 flipped 极性位） |
| 芯片 | `wildfires:ic_chip` | 3×2 | 2×3 |

约束：

- 元件 footprint 不能重叠，不能覆盖另一个元件的格。
- **连线端点可以落在元件格上**：那一格就是该元件的引脚；元件也可以放在已有线端点上，
  端点随后成为引脚。连线可以从元件下方穿过（画在元件下面）。
- 生存模式装元件要求背包里至少有 1 个对应物品，但**不消耗**它
  （按需求：元件本体留在背包）；创造模式跳过背包检查，视为无限。
- 因此 1 个电阻可以作为多个“已安装电阻”的凭证；这是需求明确的简化。

### 1.3 连线

- 点两个洞。同排/同列画直线；否则画 L 形，规则固定为**先横后竖**：
  `(x1,y1) -> (x2,y1) -> (x2,y2)`。
- 花费 = `|x1-x2| + |y1-y2|`（曼哈顿距离），从背包扣 `wildfires:copper_wire`。
- 同一对洞（不分方向）不能重复连。
- 最多 64 条线。
- 删线按**两个端点**查找，不按列表下标；删线**不退铜线**。
- 端点可以是空穴，也可以是元件格（引脚）；页面在元件上的端点画一个金点。
- **右键 = 删除**：元件优先，其次线；空穴/空白网格不做任何事。删除模式按钮仍保留。
- **R 键**切换当前待放元件的横/竖；极性仍用“极性”按钮（R 只切横竖）。
- 创造模式连线不扣 `copper_wire`，调色板和铜线数量显示 `∞`。
- 连线只是图形/铜迹，不做电气仿真；完成时也不做连通性判定。

### 1.4 完成

- “完成”按钮只要求背包里有 1 块 `circuit_board`，点击即扣 1 块并产出 1 块
  `soldered_circuit`；不检查元件/连线的电气正确性，也不要求至少一个元件。
- 产物 NBT：
  ```
  WildfiresCircuit: {
    Components: [{Type:"resistor", X:0, Y:0, Vertical:0b, Flipped:0b}, ...],
    Wires: [I; packed12, ...]
  }
  ```
- `SolderedCircuitItem` 在 tooltip 里显示“元件：电阻 ×1 / 芯片 ×1；连线：N 根”；
  同时有 `ic_chip` + `switch` 时显示“逻辑板”。这只是展示规则，不是功能判定。
- 产物统一沿用 `soldered_circuit`，没有按组合拆成多种物品；组合信息在 NBT/tooltip 里。

## 2. 同步

只用一个 `ContainerData`，共 98 个槽（每个值按 `short` 传）：

| 槽 | 内容 |
|---|---|
| 0 | revision |
| 1 | wireCount |
| 2..33 | 每槽 2 个 cell 的 8 位编码（低字节在前） |
| 34..97 | 每槽 1 条线的 12 位 packed 端点 |

`ContainerData` 在网络上按 `short` 传，`get` 端做 `& 0xFFFF` / `& 0xFF` 掩码。
服务端 `get` 读权威 `CircuitBoard`；客户端 `set` 写同一个 `CircuitBoard` 的客户端副本。
页面每 100ms 调一次 `WildfiresSoldering.state()`，拿到：

```
R<revision>|C<64 个 base32 cell 字符>|W<wireCount>|<每条线 3 个十六进制字符>
```

`state` 字符串是给页面用的，避免在 AUI 里依赖 JSON 解析器。

## 3. 服务端权威与按钮协议

装/删/连线/删线/完成/清空全部走**原版容器按钮**（`clickMenuButton`），没有新增网络通道。

### 3.1 原版按钮只给一个 byte（实测）

`ServerboundContainerButtonClickPacket` 在 1.20.1 的实现（mapped jar，`javap -c`）：

```
writeByte(containerId)
writeByte(buttonId)
```

所以 `clickMenuButton(Player, int)` 的 `int` 在网络上只有**一个有符号字节**，坐标装不下。
（这就是“按钮参数够不够传坐标”的答案：不够。）

### 3.2 多点击序列

一次逻辑操作拆成 1 个控制字节 + 1~2 个数据字节，按顺序发送同一菜单的普通按钮包：

```
控制字节 = 0x80 | action
数据字节 = payload 的低 7 位一组（小端）
```

服务端 `SolderingMenu` 里有一个 per-menu 累加器：控制字节重置，数据字节拼 7 位，
凑够 `requiredBits(action)` 就执行。TCP 保序，所以序列可靠；多余数据字节在动作执行后被忽略。

| action | bits | 按钮包数 |
|---|---|---|
| PLACE | 11 | 3 |
| REMOVE | 6 | 2 |
| WIRE | 12 | 3 |
| DELETE_WIRE | 12（按端点） | 3 |
| COMPLETE | 0 | 1 |
| CLEAR | 0 | 1 |

`SolderingActions` 是页面和服务端共用的编码器；`ProtocolCheck` 做过位级校验。

## 4. 页面结构

- 8×8 板用 `<canvas>` 画（一个元素），不是 64 个 `<slot>`：
  - AUI 自带 `canvas` 元素和 2D context；它自己的
    `assets/apricityui/apricity/tests/canvas-doodle-board.html` 用了
    `getContext("2d")`、`offsetX/offsetY`、`fillRect`、`ellipse`、`stroke` 等，
    本页面用的 `arc/fill/stroke/clearRect` 是同一套 `CanvasRenderingContext2D`。
  - 取舍：DOM 也能用绝对定位的 1~2px `div` 画线，但要 64 个洞 + 最多 112 条网格边，
    DOM 节点多、L 形折线要拼两段；canvas 一次画完，命中测试由页面自己做。
  - 风险：canvas 是 AUI 的 Java2D 后端，**没有实机验证**；见第 6 节。
- 右侧：模式（布线/装元件/删除）、5 个元件调色板、方向/极性、库存、状态、完成/清空。
- 底部：手写 9×4 背包，`slot-index` 顺序 9..35（主背包）再 0..8（快捷栏），
  与 `ApricityContainerMenu.resolveGlobalSlotIndex("player", local)` 的语义一致
  （local = 玩家背包索引，不是视觉行序）。
- 所有槽 **48px**，`--aui-slot-icon-scale: 2.6667` 同时设在容器和槽上；
  页面里没有 40px 槽，也没有第二套图标比例。
- **布局固定**：面板 610×730（受 `max-height` 约束），canvas 256×256；
  `.editor-row`/`.side-column` 高 420；模式行 28、调色板 192、方向行 26、库存行 14、
  状态栈 54、按钮行 30；状态的两行放在固定高度的 `.status-stack` 里，隐藏用
  `visibility:hidden` 而不是 `display:none`。切模式只改可见性/背景/边框/文字，
  不改变任何外框尺寸。
- 页面没有 `<meta name="aui-mouse-events" content="intercept">`。
- `SolderingScreen` 仍然**只写构造器**，不覆写 `mouseClicked` / `mouseReleased`。
- 键鼠：canvas `mousedown` 左键操作、右键删除；`document` 级 `keydown` 处理 R。
  AUI 的 `Operation.handleKeyInput` 会向页面 document 派发 `keydown`（AUI 自带
  `richtext-editor.html` 也用了 document 级 keydown），但容器 Screen 下未实机验证。

## 5. 构建 / 产物

- 构建：`gradlew.bat build --offline`
- 产物：`build/libs/wildfires-1.4.21.jar`
- 图标：6 张 16×16 PNG + 6 个 `item/generated` 模型
- 语言：中英文条目都加了（元件名、界面、错误、tooltip）
- 页面 smoke test（Node，不需要启动游戏）：
  ```bash
  node docs/soldering-editor/page-smoke-test.js \
    src/main/resources/assets/apricityui/apricity/screens/soldering.html
  ```
  它会解状态、跑命中测试、验证 wire/place/remove/disconnect、创造模式 ∞、R 键、
  右键删除，并在切 3 次模式后断言 `canvas.clientWidth/clientHeight` 不变。

## 6. 诊断（临时，拿到实机数值后删除）

当前构建是**诊断构建，不是右键修复**。页面板下方多了一行 `diag:`，保留最近 2 次鼠标事件：

```
diag: btn=<button> btns=<buttons> type=<type> which=<which> right=<isRightClick>
```

右键还会额外记录 `diag: ctxmenu ...`（如果 AUI 派发 contextmenu）。同样的内容写到
`console.log("[soldering-diag] ...")`。服务端 `placeComponent` 每次请求写：

```
[Soldering] place request ...
[Soldering] place rejected reason=invalid|out_of_bounds|overlap|no_stock|board_place_false ...
[Soldering] place accepted ...
```

需要老大做的两次点击：纯左键一次、纯右键一次；把板下 `diag:` 两行（或日志）发回来。
电容问题的额外观察：放完电容后换电阻，`diag:` 是 `place sent`、`place rejected: ...` 还是
没有变化；状态栏/红色预览框有没有反应；是创造还是生存、调色板计数是 0/∞/N。

## 7. 没有实机验证 / 拿不准的地方

1. **没有启动 Minecraft 实机验证**。本文所有“能/不能”的判断来自源码/字节码、AUI 自带
   测试页和 headless 脚本，不是游戏内截图。
2. **AUI canvas 没有在容器 Screen 里实机跑过**。AUI 的测试页是独立页面；
   容器 Screen 下 `Operation.onMouseDown` 是否稳定把 `mousedown` 送给 canvas、
   `offsetX/offsetY` 在页面滚动后是否仍相对 canvas，需要实机确认。
3. **`<item>` 作为调色板独立元素**：AUI 的 `Item` 类有 standalone 分支，
   但现有测试页都是 `<slot><item>…</item></slot>`。如果调色板图标不显示，
   名字/数量仍然可用，但需要改成 `<slot>` 包一层。
4. **KubeJS 绑定带参数的方法**：`WildfiresSoldering.place(int,int,int,int,int)` 等
   通过 KubeJS 的 class binding 暴露；旧的 `start()` 无参方法验证过，带参方法没有实机验证。
5. **多点按钮序列**依赖同一连接保序；理论可靠，但没有在真实客户端/服务端往返里压过。
6. **手写 36 个 `<slot>` 的 DOM 尺寸**与 `SlotLayout` 一致，但最终的 AUI slot 尺寸/
   图标缩放仍要实机看（尤其是 `--aui-slot-icon-scale` 的继承和 floating item）。
7. **右键删除**依赖 AUI 把右键 `mousedown` 送给 canvas。AUI 的 `MouseEvent.button` 来自
   Forge 的 GLFW 按钮码，而 DOM 约定是 0/1/2，两者对右键的编号不同；页面优先用
   `event.buttons & 2`（GLFW 与 DOM 的右键掩码都是 2），没有 `buttons` 时再接受
   `button == 2 || button == 1`，因此两种约定都能删，且不会把中键（掩码 4）当删除。
   没有实机点过；若右键仍被屏幕层吃掉，可在 `contextmenu` 上补一个同样的 `deleteAt`。
8. **R 键**依赖 AUI 的 screen key 事件派发到页面 document；AUI 的 richtext 测试页证明
   document 级 keydown 可用，但容器 Screen 下未实机验证。若拿不到，替代是 AUI 的 keybind
   事件或 `ApricityContainerScreen` 的 keyPressed 转发（本 agent 没改 Screen）。
9. **固定布局**目前只保证页面内部元素不因切模式改尺寸；AUI 对 `height`/`visibility`/
   `white-space` 的实现是否与浏览器一致，需要实机看。smoke test 断言的是
   `canvas.clientWidth/Height` 在三次模式切换后不变（headless 版）。
10. **没有配方**：6 个新物品目前只进创造标签，没有加工作台配方（任务未要求）。
11. **实例目录副本**：本机只在 `src/main/resources/.../soldering.html` 和
    `build/resources/main/.../soldering.html` 找到页面；没有找到独立的实例 `apricity/screens`
    副本（`D:\` 全盘搜索只命中这两处）。部署时如需实例覆盖副本，需要父 agent 按实例路径再同步一份。
