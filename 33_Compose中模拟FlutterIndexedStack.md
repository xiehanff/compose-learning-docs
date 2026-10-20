# 第 33 章：Compose 中模拟 Flutter IndexedStack

从 Flutter 转到 Compose 的开发者很快会撞上同一个问题：底部导航切换页面后，上一个页面的输入、滚动位置、计数全部归零。在 Flutter 里用 `IndexedStack` 包一层就能保住所有子页状态；Compose 没有同名组件，需要理解机制后自己组合。本章讲清「状态为什么丢」，并比较三种方案的保留范围与代价，最后用「切换面板后计数器是否保留」做验收。

## 1. 本章目标

比较条件渲染与所有面板保留在组合树中的差异。

学完后，你应当能够：

- 知道 when/if 会移除未选分支
- 使用 Box 叠放面板
- 使用 zIndex 改变绘制层级
- 识别隐藏面板的输入和资源成本
- 在「条件切换、叠放保活、导航」三种方案之间按状态需求做选择
- 用「切换面板后计数器是否保留」验证所选方案

## 2. 项目中的位置

- `components/IndexedStackDemo.kt`

没有 compose_demo 工程的读者：本章 3.2 节的示例不依赖项目私有符号，可直接复制到任意含 Material 3 的 Compose 工程运行。

## 3. 项目源码片段

### 3.1 项目中的写法：叠放 + zIndex

<!-- compile
prelude: val index = 0
-->
```kotlin
Box(Modifier.fillMaxWidth().height(156.px)) {
    Box(
        Modifier
            .matchParentSize()
            .zIndex(if (index == 0) 1f else 0f)
    ) { Text("面板 A") }

    Box(
        Modifier
            .matchParentSize()
            .zIndex(if (index == 1) 1f else 0f)
    ) { Text("面板 B") }
}
```

这段节选只演示一件事：两个面板都放进同一个 `Box`，用 `zIndex` 决定谁画在上面。`index` 在真实文件里是可变状态；`156.px` 是项目的设计稿单位（第 41 章起详解），复制到普通工程时可换成 `156.dp`。

先补两个 Kotlin 语法点，后面所有示例都会用到：

- `Modifier.zIndex(值)` 是 Box 子项可用的修饰符（`androidx.compose.ui.draw.zIndex`）：值越大的子项画在同层兄弟之上。它只影响绘制顺序，不影响子项是否存在，也不参与布局与输入的判定。
- `if (index == 0) 1f else 0f` 用到 Kotlin 的「if 表达式」：Kotlin 的 `if` 可以直接作为值使用，整个表达式的值就是命中分支的值，作用类似其他语言的三元运算符。

### 3.2 可独立运行的最小示例：面板各带计数器

三种方案用同一个案例验收：两个面板各含一个计数器，切换后看计数是否保留。公共部分与方案一、方案二如下（参与本仓库编译校验）：

<!-- compile decls
-->
```kotlin
// ---------- 公共部分：带计数器的面板 ----------

// 每个面板内部各有一个计数器状态，用来观察「切走再切回」后状态是否保留
@Composable
private fun CounterPanel(title: String, modifier: Modifier = Modifier) {
    var count by remember { mutableIntStateOf(0) }
    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title)
        Button(onClick = { count++ }) {
            Text("count = $count") // 点一次加一，count 直接显示在按钮上
        }
    }
}

// ---------- 方案一：when 条件切换（离开组合即丢失状态） ----------

@Composable
fun SwitchByWhenDemo() {
    var current by remember { mutableIntStateOf(0) }
    Column {
        Row {
            Button(onClick = { current = 0 }) { Text("面板 A") }
            Spacer(Modifier.width(8.dp))
            Button(onClick = { current = 1 }) { Text("面板 B") }
        }
        when (current) {
            0 -> CounterPanel("面板 A", Modifier.fillMaxWidth())
            1 -> CounterPanel("面板 B", Modifier.fillMaxWidth())
        }
    }
}

// ---------- 方案二：Box 叠放保活（子树不离开组合） ----------

@Composable
fun SwitchByStackDemo() {
    var current by remember { mutableIntStateOf(0) }
    Column {
        Row {
            Button(onClick = { current = 0 }) { Text("面板 A") }
            Spacer(Modifier.width(8.dp))
            Button(onClick = { current = 1 }) { Text("面板 B") }
        }
        Box(Modifier.fillMaxWidth().height(128.dp)) {
            // 两个面板始终都在组合中，只靠 zIndex 决定谁画在上面
            Box(
                Modifier
                    .matchParentSize()
                    .background(MaterialTheme.colorScheme.surface) // 靠不透明背景遮住下层
                    .zIndex(if (current == 0) 1f else 0f),
            ) {
                CounterPanel("面板 A")
            }
            Box(
                Modifier
                    .matchParentSize()
                    .background(MaterialTheme.colorScheme.surface)
                    .zIndex(if (current == 1) 1f else 0f),
            ) {
                CounterPanel("面板 B")
            }
        }
    }
}
```

示例依赖：任意含 Material 3 的 Compose 工程（本仓库 snippets 基线为 Compose BOM 2026.06.00），不需要导航库。

初始界面：顶部两个切换按钮，下方显示「面板 A」和一个 count 按钮。

操作步骤与期望观察（运行行为为预期观察，待真机验证）：

1. 运行 `SwitchByWhenDemo`：面板 A 点 3 次（count = 3）→ 切到面板 B 点 2 次 → 切回 A。预期 A 的 count 显示 0（分支被销毁后重建）。
2. 运行 `SwitchByStackDemo`：同样操作。预期 A 的 count 仍显示 3（面板从未离开组合）。
3. 对照结论与实现方式：两者界面相同、代码只差「未选面板是否还在组合里」，正是本章要观察的变量。

### 3.3 方案三：交给导航（需工程环境，不参与编译校验）

```kotlin
// 需工程环境：依赖 androidx.navigation:navigation-compose（Nav2），不参与本仓库编译校验。
// 计数器必须用 rememberSaveable：导航会把离开的目的地移出组合，只有可保存状态能随条目恢复。
@Composable
private fun NavCounterPanel(title: String) {
    var count by rememberSaveable { mutableIntStateOf(0) }
    Column(
        Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title)
        Button(onClick = { count++ }) { Text("count = $count") }
    }
}

@Composable
fun SwitchByNavDemo() {
    val navController = rememberNavController()
    Column {
        Row {
            Button(onClick = {
                navController.navigate("panelB") { launchSingleTop = true }
            }) { Text("去面板 B") }
            Spacer(Modifier.width(8.dp))
            Button(onClick = { navController.popBackStack() }) { Text("返回上一面板") }
        }
        NavHost(navController, startDestination = "panelA", modifier = Modifier.weight(1f)) {
            composable("panelA") { NavCounterPanel("面板 A") }
            composable("panelB") { NavCounterPanel("面板 B") }
        }
    }
}
```

示例依赖：`androidx.navigation:navigation-compose`（Nav2）。本仓库基线未单独锁定该库版本（`snippets/gradle/libs.versions.toml` 的编译校验工程不含此依赖），写法与第 4、71 章一致；本章只用到字符串路由与 `popBackStack`，多返回栈的 `saveState`/`restoreState` 自 Navigation 2.4.0 引入，具体以所用版本的官方文档为准（[Navigate with Compose](https://developer.android.com/develop/ui/compose/navigation)、[Navigation 发布说明](https://developer.android.com/jetpack/androidx/releases/navigation)）。

操作步骤与期望观察（待真机验证）：面板 A 点 3 次 → 「去面板 B」点 2 次 → 「返回上一面板」（或系统返回键）。预期 A 的 count 恢复为 3。再把 `rememberSaveable` 换回 `remember` 试一次，预期变成 0——这就是「返回栈保留」的准确边界（4.3 节展开）。

## 4. 逐步解释

### 4.1 方案一：if/else / when 条件切换——离开组合即丢状态

Compose 的条件渲染是「存在性」的：`when` 每次只让一个分支存在于组合（composition，Compose 正在维护的 UI 树，第 2、22 章详解）中。切换时旧分支整棵子树被销毁，其中所有 `remember` 状态随之丢弃；切回来时从头创建。`if/else` 与 `when` 在这一点上机制相同。

所以方案一不是「写错了」，而是机制如此：它适合内容无状态、或状态可以廉价重建的面板。期待条件分支里的 `remember` 跨切换保留，是把「跨重组保留」（remember 的能力）误当成「跨组合存活」（它没有的保证，第 21、23 章）。

### 4.2 方案二：叠放子树保持组合——保状态但有代价

使用 `when(index)` 时，只有当前分支存在；切换后旧分支离开组合，内部 remember 状态通常被丢弃。要接近 Flutter IndexedStack，就让全部面板始终存在于同一个 Box，再用 zIndex 把选中面板放到上层。

不过 zIndex 只控制绘制层级，不会自动停止未选面板的点击、语义、副作用或资源消耗。真实实现要同时处理 alpha、输入、无障碍（第 80 章详解）和生命周期，或者采用导航 saveState、SaveableStateHolder 等更合适方案。

叠放方案还有三个容易漏掉的细节：

1. **遮挡靠不透明背景**。zIndex 只决定绘制先后，「盖住」下层面板要靠上层面板自己有不透明背景；3.2 节两层 Box 都加了 `background(colorScheme.surface)`，去掉后两层文字会叠着显示。
2. **输入不会被 zIndex 自动拦截**。Compose 的命中测试按绘制顺序分发，上层没有消费点击的空白处，点击会落到下层可点元素上（「幽灵点击」）。3.2 节两个面板结构相同、按钮位置重合，可见面板的按钮总是先消费点击；面板结构错开时问题就会暴露，需要显式处理（例如隐藏时让可点元素 `enabled = false`，或控制 `pointerInput`/`semantics` 的挂载）。
3. **副作用继续跑**。隐藏面板里的 `LaunchedEffect`、动画会随组合存在而继续执行（第 25 章）；这是「保活」的代价之一。

### 4.3 方案三：导航——返回栈负责保留

`NavHost` 切走一个目的地时，该目的地的组合被移出，但返回栈条目还在；条目关联的可保存状态（`rememberSaveable`，保存时机与恢复边界见官方 [State 文档](https://developer.android.com/develop/ui/compose/state)与第 23 章）与作用域绑定到条目的 ViewModel 会随条目保留、返回时恢复（实现细节：NavHost 内部用 `SaveableStateHolder` 保存各条目的 saveable 状态，教学简化表述，以官方文档为准）。因此 3.3 节的计数器必须用 `rememberSaveable`，普通 `remember` 会在返回后归零。

它和方案二的「保留」含义不同：不是让组合一直活着，而是「存走、回来再恢复」。代价是引入导航库（版本敏感，见 3.3 节的基线说明），并且系统返回键的语义随之变化。`rememberSaveableStateHolder()` 也有公开 API，可以在不用整套导航时手动实现「离开时保存、回来时恢复」；直接使用它属于进阶用法，本章不展开。

### 4.4 三种方案对照（验收表）

| 验证步骤 | 方案一 when | 方案二 Box 叠放 | 方案三 导航 |
| --- | --- | --- | --- |
| A 点 3 次 → 切 B → 回 A，count | 归 0 | 仍 3 | 仍 3（经保存/恢复） |
| 普通 remember 的派生对象 / 副作用进度 | 丢 | 保 | 丢（重建） |
| 额外依赖 | 无 | 无 | navigation-compose |
| 主要代价 | 状态丢失 | 全部子树常驻组合（布局、内存、语义、副作用） | 依赖导航库；返回键语义变化 |

结论与实现方式一一对应：验收看「切换面板后内部计数能否保留」，三种方案给出三种不同答案，各自伴随不同代价。表中运行行为为预期观察（待真机验证）。

## 5. 实际执行过程

方案一（when 条件切换）：

1. 点击「面板 B」→ onClick 写 current = 1
2. 读过 current 的范围失效，触发重组
3. when 重新求值：0 分支整棵子树销毁，count 随 remember 丢弃
4. 1 分支新建，count 从 0 开始显示

方案二（叠放保活）：

1. 全部面板进入组合
2. 各自状态保持
3. index 改变
4. zIndex 重新计算
5. 目标面板显示在上层
6. 其他面板仍然存在

方案三（导航）：

1. navigate("panelB") → B 压入返回栈；A 的组合被移出，可保存状态随 A 的条目保存
2. popBackStack() → B 弹出；A 的条目重新组合，rememberSaveable 恢复 count

## 6. Flutter / Dart 3 对照

Flutter [IndexedStack](https://api.flutter.dev/flutter/widgets/IndexedStack-class.html) 原生保留所有 children，只显示指定 index。Compose 需要自行组合保活和显隐策略。

```dart
IndexedStack(
  index: index,
  children: const [HomePage(), SearchPage(), SettingsPage()],
);
```

机制细节对照（概念对照，不是等价声明）：

- IndexedStack 的所有子项都留在 widget 树中并被布局，只有 index 指定的一项被绘制，各子项的 State 因此不销毁——这与方案二「全部面板留在组合」目的一致；代价同样是所有子树持续占用资源。
- 差别在于 Flutter 替你处理了隐藏子项的绘制与命中测试；Compose 的 Box 叠放需要自己处理输入、语义与资源（4.2 节的三点）。
- Flutter 的 [Offstage](https://api.flutter.dev/flutter/widgets/Offstage-class.html) 也能保 State 不绘制；Navigator 的页面栈则对应方案三的返回栈思路（第 4 章有完整概念对照表）。

## 7. 常见错误

- 未选面板仍拦截点击
- 所有重型页面长期运行
- 以为 zIndex 会取消未选页面副作用
- 使用 when 却期待 remember 永远保留
- 叠放面板不加不透明背景，两层内容叠着显示

错误示例（故意错误，不参与编译）：

```kotlin
// 错误思路：用 alpha = 0「隐藏」面板，以为它不再响应点击
Box(
    Modifier
        .matchParentSize()
        .graphicsLayer { alpha = if (current == 0) 1f else 0f }
) {
    CounterPanel("面板 A")
}
```

alpha 只影响绘制，不影响命中测试：透明面板上的按钮仍然可以收到点击。正确做法是显式控制输入与语义（例如隐藏时让可点元素 `enabled = false`），或改用导航、`rememberSaveableStateHolder` 这类自带保存恢复的方案。

## 8. 本章检查题

1. 条件渲染为什么可能丢状态？
2. zIndex 改变的是存在性还是层级？
3. 保留所有页面有什么代价？

答案：

1. when 切换时旧分支离开组合，其内部 remember 状态通常被丢弃。
2. zIndex 只改变绘制层级，不改变存在性；所有面板仍在组合中。
3. 所有页面常驻组合，持续占用布局、内存、语义与资源；真实实现还需处理 alpha、输入、无障碍和生命周期。

4.（预测行为）方案二里把 CounterPanel 的 `remember { mutableIntStateOf(0) }` 换成普通局部变量 `var count = 0`，点 3 次按钮、切走再切回，按钮文字会显示什么？为什么？

答案：一直显示 0。普通变量不是可观察状态，点击后不会触发重组更新文字；即使因其他状态触发重组，变量也会被重新初始化（第 21 章）。

5.（预测行为）方案三中把 NavCounterPanel 的 `rememberSaveable` 换成 `remember`，「去面板 B」再「返回上一面板」后，A 的 count 是多少？

答案：0。返回栈保留的是条目与可保存状态；普通 remember 随组合销毁而丢弃。

6.（动手修改）给方案二增加「面板 C」：`current` 可取 2，叠放 Box 增加第三层，切换按钮加一个。验证 A → C → A 后 A 的计数保留，并检查三层叠放时背景遮挡与点击是否仍然正确。

## 9. 本章总结

IndexedStack 语义的核心是保留所有页面，但完整实现还要处理输入、语义、副作用和资源成本。落到 Compose 就是三种可选方案：when 条件切换最简单，但离开组合即丢状态；Box 叠放保住一切，代价是全部子树常驻组合；导航把「保留」交给返回栈与可保存状态，代价是引入导航依赖。选定方案后用计数器验收，结果应当与 4.4 节对照表一一对应。
