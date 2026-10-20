# 第 53 章：AnimatedVisibility、AnimatedContent 与 Crossfade

## 1. 本章目标

学完后，你应当能够：

- 用 `AnimatedVisibility` 为内容的出现/消失定义 `enter`/`exit` 过渡；
- 理解退出动画期间内容仍保留在组合中这一机制；
- 用 `AnimatedContent` 实现内容切换时的过渡，理解 `targetState` 比较；
- 根据场景在 `AnimatedVisibility`、`AnimatedContent`、`Crossfade` 三者中做出正确选择。

## 2. 项目中的位置

进阶篇不再局限于 compose_demo 项目，本章使用通用 Compose API。

涉及的 androidx 包与官方 API：

- `androidx.compose.animation`：`AnimatedVisibility`、`AnimatedContent`、`Crossfade`、`fadeIn`、`fadeOut`、`slideInVertically`、`slideOutVertically`、`slideInHorizontally`、`slideOutHorizontally`、`expandVertically`、`shrinkVertically`、`togetherWith`、`EnterTransition`、`ExitTransition`
- `androidx.compose.animation.core`：`tween`
- `androidx.compose.runtime`：`remember`、`mutableStateOf`、`getValue`/`setValue`
- `androidx.compose.material3`：`Surface`、`TextButton`、`MaterialTheme`

## 3. 项目源码片段

场景一：可展开/收起的面板。展开时内容从顶部纵向展开并淡入，收起时反向收缩淡出。

<!-- compile decls -->
```kotlin
@Composable
fun ExpandablePanel() {
    var expanded by remember { mutableStateOf(false) }

    Column {
        TextButton(onClick = { expanded = !expanded }) {
            Text(if (expanded) "收起详情" else "展开详情")
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "这里是折叠与展开的详情内容。",
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}
```

场景二：Tab 切换内容，新旧内容同时做位移与淡入淡出。

```kotlin
@Composable
fun TabContentSwitcher() {
    var tab by remember { mutableStateOf(0) }

    AnimatedContent(
        targetState = tab,
        transitionSpec = {
            (slideInHorizontally { it / 2 } + fadeIn())
                .togetherWith(slideOutHorizontally { -it / 2 } + fadeOut())
        },
        label = "tabContent"
    ) { currentTab ->
        when (currentTab) {
            0 -> Text("首页内容", modifier = Modifier.fillMaxSize())
            1 -> Text("发现内容", modifier = Modifier.fillMaxSize())
            else -> Text("我的内容", modifier = Modifier.fillMaxSize())
        }
    }
}
```

场景三：仅需淡入淡出的简单切换，用 `Crossfade` 最省事。

<!-- compile decls
-->
```kotlin
enum class ViewMode { List, Grid }

@Composable
fun ViewSwitcher(mode: ViewMode) {
    Crossfade(
        targetState = mode,
        animationSpec = tween(durationMillis = 400),
        label = "viewMode"
    ) { currentMode ->
        when (currentMode) {
            ViewMode.List -> Text("列表视图内容", modifier = Modifier.fillMaxSize())
            ViewMode.Grid -> Text("网格视图内容", modifier = Modifier.fillMaxSize())
        }
    }
}
```

## 4. 逐步解释

### 4.1 `AnimatedVisibility`：内容生命周期的动画

`AnimatedVisibility(visible, enter, exit)` 控制的是**内容是否存在**。`visible = false` 时内容不会立即从组合中移除，而是先播放 `exit` 过渡，播放完毕才真正移除。

`enter`/`exit` 的类型是 `EnterTransition`/`ExitTransition`，由基础过渡用 `+` 组合：

- `fadeIn()` / `fadeOut()`：透明度；
- `slideInVertically { it }` / `slideOutVertically { it }`：纵向位移，lambda 参数是内容高度，返回起始/结束偏移；
- `expandVertically()` / `shrinkVertically()`：纵向展开/收缩，适合"面板"类内容。

记住：`visible` 是"内容该不该存在"的声明，动画只是它的呈现方式。

### 4.2 `AnimatedContent`：内容切换的过渡

`AnimatedContent(targetState, transitionSpec)` 管理的是**内容状态本身的变化**：当 `targetState` 变化时，旧内容播放退出过渡、新内容播放进入过渡，两者在切换期间**同时存在于组合中**，所以可以做出"旧内容向左滑出、新内容向右滑入"的效果。这里的 `transitionSpec` 可以读作「过渡配方」：它是一个 lambda，规定新内容怎么进、旧内容怎么出，并把这两者配成一对交给框架播放（第 54 章的子动画上还会出现一个同名参数，含义不同，到时再区别）。

关键点：

- `transitionSpec` 里的 `togetherWith` 把进入与退出过渡绑定成一对；
- content lambda 的参数是**当前正在显示的 targetState**，不是旧的、也不是外部变量；
- 如果两个状态的内容相同（相等判断），不会触发动画；
- 动画进行中 `targetState` 再次变化时是**平滑打断**：正在进入的内容转为退出，所有子动画从当前值转向新目标，不会排队等旧动画播完。

记住：`AnimatedContent` 的 lambda 参数才是"正在显示的那个状态"，内容必须基于它构建。

### 4.3 `Crossfade`：最简单的切换

`Crossfade(targetState)` 是 `AnimatedContent` 的简化特例：内部就是 `fadeIn` + `fadeOut` 的交叉淡入淡出（透明度此消彼长），不提供位移等自定义过渡。新旧内容在同一容器里**重叠绘制**（各播各的透明度动画），容器尺寸不做过渡：切换期间按新旧内容中较大者布局，动画结束后收敛为新内容的尺寸。适合 logo 切换、主题切换这类"只需要淡入淡出、尺寸相近"的场景。

注意术语：本章说的"淡入淡出"指的是透明度变化，和第 60 章讲的"渐变"（颜色沿某个方向平滑过渡的填充效果）不是一回事。

### 4.4 三者如何选择

| 需求 | 选谁 |
|---|---|
| 元素出现/消失（展开面板、提示条） | `AnimatedVisibility` |
| 内容整体切换且需要位移+淡入等复杂过渡 | `AnimatedContent` |
| 内容整体切换，只需淡入淡出 | `Crossfade` |

判断口诀：**先问"动的是存在性还是内容本身"，再问"要不要位移动画"**。出现/消失用 `AnimatedVisibility`；切换内容要复杂过渡用 `AnimatedContent`；切换内容只要淡入淡出用 `Crossfade`。

### 4.5 退出动画期间的注意点

退出动画播放时，旧内容仍在组合中，这意味着：

- 它的布局仍然占用空间（直到动画结束）；
- 动画期间它仍可能重组，副作用可能被重复触发。

所以 `AnimatedVisibility` 的内容里不要放"每次进入组合只应执行一次"的副作用，这类逻辑应放在 `LaunchedEffect` 并配合可见性状态控制。

记住：退出动画期间，内容是"还活着"的，不是"已经没了"。

## 5. 实际执行过程

以场景一展开面板为例：

1. 初始 `expanded = false`，`AnimatedVisibility` 内容不在组合中；
2. 点击"展开详情" → `expanded = true` → 重组；
3. `AnimatedVisibility` 检测到 `visible` 从 false 变 true，启动 `enter` 过渡：`expandVertically() + fadeIn()` 同时播放；
4. 内容进入组合，高度从 0 展开到实际高度，透明度从 0 到 1；动画期间内容参与布局，面板逐帧撑开；
5. 动画结束，内容以完整状态稳定显示；
6. 再次点击 → `expanded = false` → 播放 `exit`：`shrinkVertically() + fadeOut()`，内容高度收缩、变透明；
7. 退出动画完成后，内容才从组合中移除，布局恢复。

整个过程里，内容的"出现/消失"由 `visible` 驱动，具体过渡由 `enter`/`exit` 描述。

## 6. Flutter / Dart 3 对照

Flutter 的 `AnimatedSwitcher` 与 `AnimatedContent`/`Crossfade` 对应：

```dart
AnimatedSwitcher(
  duration: const Duration(milliseconds: 300),
  transitionBuilder: (child, animation) =>
      FadeTransition(opacity: animation, child: child),
  child: KeyedSubtree(
    key: ValueKey<int>(tab),
    child: tab == 0 ? const Text('首页') : const Text('发现'),
  ),
)
```

相似点：

- 都是"内容切换时自动播放过渡"的声明式 API，只需声明目标，过渡由框架执行；
- 切换期间**新旧 child 都会存在**：Flutter 用 `AnimatedSwitcher` 默认把新旧 child 叠放在 Stack 中，Compose 的 `AnimatedContent`/`Crossfade` 也是新旧内容重叠——这是两者共同的布局特性，尺寸不同时会有重叠或跳动；
- 都需要一个"身份"区分新旧内容：Flutter 靠 `key`（`ValueKey`），Compose 靠 `targetState` 的相等性。

本质区别：

- **身份机制**：Flutter 必须手动给 child 换 key，否则切换不触发动画；Compose 的 `targetState` 本身就是身份，类型安全且无需额外 key；
- **过渡能力**：`AnimatedSwitcher` 的 `transitionBuilder` 只拿到统一动画进度，难以让新旧内容做不同方向的位移；Compose 的 `AnimatedContent.transitionSpec` 可以分别定义进入与退出（`togetherWith`），表达能力更强；
- **布局策略**：Flutter `AnimatedSwitcher` 默认 `Stack` 居中叠加；Compose `AnimatedContent` 默认按内容大小布局、可用 `SizeTransform` 处理尺寸差异（本章不展开）。

| Compose | Flutter |
|---|---|
| `AnimatedContent(targetState, transitionSpec)` | `AnimatedSwitcher` + `transitionBuilder` |
| `Crossfade(targetState)` | `AnimatedSwitcher` + `FadeTransition` |
| `targetState` 相等性识别新旧 | `ValueKey` 识别新旧 |
| `togetherWith` 自定义进出 | 统一 `animation` 进度 |

## 7. 常见错误

### 错误 1：把 `AnimatedVisibility` 当 `if` 用，以为没动画

```kotlin
// 错误理解：以为不写 enter/exit 就没有动画
if (expanded) { DetailContent() }
```

`AnimatedVisibility` 的默认过渡不是"没有动画"：在本仓库基线中，顶层 `AnimatedVisibility` 的默认值是 `enter = fadeIn() + expandIn()`、`exit = shrinkOut() + fadeOut()`（Row/Column 作用域的重载默认值另有不同，以 API 文档为准），所以只要用了它、正确切换 `visible`，就天然有过渡。真正"没动画"的原因通常是内容写在 `AnimatedVisibility` 外面，或者 `visible` 根本没变。

### 错误 2：`AnimatedContent` 内容依赖外部旧状态

```kotlin
// 错误：lambda 里读外部 tab，切换瞬间可能显示旧内容
AnimatedContent(targetState = tab) {
    when (tab) { ... }   // 应使用参数 currentTab
}
```

必须基于 lambda 的参数构建内容，否则新旧内容过渡期间会读到混乱的状态。

### 错误 3：`Crossfade` 用于尺寸差异大的内容

Crossfade 不做尺寸过渡：切换期间容器取新旧内容中较大的尺寸，动画结束后立即收敛到新内容的尺寸，尺寸差异大时会有明显跳变。尺寸差异大的切换应改用 `AnimatedContent` 并配合尺寸过渡（`SizeTransform`），或固定容器尺寸。

### 错误 4：退出动画期间重复执行副作用

在 `AnimatedVisibility` 内容里直接发网络请求、写日志：退出动画期间内容仍重组，副作用被反复触发。副作用要交给 `LaunchedEffect` 并受可见性状态控制。

## 8. 本章检查题

1. `visible = false` 后，内容会立刻从组合中移除吗？
2. `AnimatedContent` 与 `Crossfade` 的区别是什么？
3. 展开面板、Tab 切换、Logo 淡入淡出切换，分别应选哪个 API？

答案：

1. 不会。先播放 `exit` 过渡，动画完成才从组合中移除；退出期间内容仍参与布局与重组。
2. `Crossfade` 是 `AnimatedContent` 的简化特例，只做交叉淡入淡出、不能自定义位移；`AnimatedContent` 可用 `transitionSpec` + `togetherWith` 分别定义进入与退出过渡。
3. 展开面板用 `AnimatedVisibility`；Tab 切换需要位移等复杂过渡用 `AnimatedContent`；Logo 等只需淡入淡出用 `Crossfade`。

## 9. 本章总结

`AnimatedVisibility`、`AnimatedContent`、`Crossfade` 覆盖了"出现/消失、内容切换、简单淡入淡出"三类过渡需求，核心机制都是"新旧内容在过渡期间共存"。选择的关键是判断动画对象是元素存在性还是内容本身，以及是否需要位移动画。理解了它们的边界，大多数页面过渡都可以用声明式写法完成。

官方参考：

- [AnimatedVisibility / AnimatedContent / Crossfade（Animation in Compose）](https://developer.android.com/develop/ui/compose/animation)
- [AnimatedContent API](https://developer.android.com/reference/kotlin/androidx/compose/animation/AnimatedContent)
