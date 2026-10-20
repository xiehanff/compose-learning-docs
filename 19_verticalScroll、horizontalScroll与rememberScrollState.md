# 第 19 章：verticalScroll、horizontalScroll 与 rememberScrollState

## 1. 本章目标

先看一个具体页面问题：设置页只有十几行内容——标题、几个开关、一段说明。用 LazyColumn 写当然可以，但为了十几个固定子项去登记条目、想 key，反而啰嗦；这里真正需要的只是「整页能滚动」。Compose 给普通布局加滚动能力的方式是一个 Modifier：verticalScroll / horizontalScroll，配一个保存滚动位置的 ScrollState。

学完后，你应当能够：

- 为 Column 添加 verticalScroll，为 Row 添加 horizontalScroll
- 理解 rememberScrollState 保存滚动位置和状态
- 用协程调用 animateScrollTo 程序控制滚动
- 区分普通滚动容器与 LazyColumn 的组合范围、生命周期差异
- 知道同方向嵌套滚动的测量约束风险

## 2. 项目中的位置

- `components/ScrollViewDemo.kt`

## 3. 项目源码片段

### 3.1 项目节选

项目节选，依赖原项目的设计稿单位 `.px`（第 41/43 章；本仓库片段工程由 `SnippetsSupport.kt` 提供同名支撑），不可直接复制到普通工程，普通工程请把 `.px` 换成 `.dp`：

<!-- compile -->
```kotlin
Column(
    modifier = Modifier
        .fillMaxWidth()
        .height(220.px)
        .verticalScroll(rememberScrollState()),
) {
    repeat(20) { index -> Text("Item $index") }
}

Row(
    Modifier.horizontalScroll(rememberScrollState())
) { /* 宽内容 */ }
```

### 3.2 独立示例：带程序控制滚动的页面（不依赖项目代码）

<!-- compile decls -->
```kotlin
@Composable
fun ScrollDemoScreen() {
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope() // 点击回调不是挂起环境，需要协程作用域（第 49 章）

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = {
                    scope.launch {
                        scrollState.animateScrollTo(0) // 挂起函数：动画滚到顶部
                    }
                },
            ) { Text("滚到顶部") }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = {
                    scope.launch {
                        scrollState.animateScrollTo(scrollState.maxValue) // 滚到底部
                    }
                },
            ) { Text("滚到底部") }
            Spacer(Modifier.width(8.dp))
            Text("偏移 ${scrollState.value}/${scrollState.maxValue} px") // 读取状态，滚动时更新
        }
        Column(
            modifier = Modifier
                .weight(1f) // 占据按钮行之外的剩余高度，形成有限视口
                .verticalScroll(scrollState),
        ) {
            repeat(30) { index ->
                Text(
                    "第 $index 行",
                    modifier = Modifier.padding(16.dp),
                    fontSize = 20.sp,
                )
            }
        }
    }
}
```

初始界面：顶部两个按钮加一个偏移数字，下面是 30 行文本。

操作步骤与预期观察：

1. 手动上下滚动。预期：偏移数字随滚动变化（0 到 maxValue，单位是像素）。
2. 点「滚到顶部」。预期：内容平滑动画滚回第一行，偏移回到 0。
3. 点「滚到底部」。预期：平滑滚到第 30 行附近，偏移到 maxValue。

`animateScrollTo` 是挂起函数（suspend fun）：调用它会挂起当前协程等动画播完，所以只能写在协程里。`onClick` 的 lambda 不是挂起环境，因此先取 `rememberCoroutineScope()` 再 `scope.launch { ... }`（协程作用域与组合生命周期的绑定关系在第 49 章）。

### 3.3 横向滚动：标签栏

<!-- compile -->
```kotlin
// 横向标签栏：内容宽度超出视口后可左右滚动
Row(
    modifier = Modifier
        .fillMaxWidth()
        .horizontalScroll(rememberScrollState()),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
) {
    listOf("推荐", "关注", "热榜", "视频", "科技", "体育", "财经", "国际").forEach { tag ->
        Text(tag, modifier = Modifier.padding(vertical = 8.dp))
    }
}
```

horizontalScroll 与 verticalScroll 是同一个机制的不同方向，常搭配 Row 做标签栏、轮播条。

## 4. 逐步解释

### 4.1 verticalScroll 是 Modifier，不是容器

`verticalScroll` 让一个普通布局在内容超过视口时沿垂直方向偏移显示。按「触发 → 读/写 → 框架行为 → 最终界面」追踪：

1. 触发：用户拖动或惯性滑动；或协程调用 scrollTo / animateScrollTo。
2. 读/写：手势把新偏移写入 ScrollState；在组合中读取了该状态的代码（如偏移数字 Text）登记依赖，值一变就重组刷新。
3. 框架行为：子项早已全部组合、测量完毕，滚动只是按偏移动内容，不重新决定「哪些子项存在」。
4. 最终界面：视口按当前偏移显示内容的不同区域。

有一个测量细节值得记住：加 verticalScroll 后，Column 给子项的高度约束变成「不限」（内容想多高就多高），这是它能滚的前提，也是第 20 章「测量约束」维度的伏笔。

### 4.2 ScrollState 保存什么

`rememberScrollState()` 创建并 remember 一个 ScrollState（[API 参考](https://developer.android.com/reference/kotlin/androidx/compose/foundation/ScrollState)），保存三样东西：

- `value: Int`——当前滚动偏移，单位像素；
- `maxValue: Int`——最大可滚偏移（内容高度减视口高度），内容或视口变化时由框架更新；
- `isScrollInProgress: Boolean`——是否正在滚动。

用 `rememberScrollState()` 而不是 `ScrollState(0)` 直接写在函数体里，是为了让同一次组合的生命周期内，即使发生重组（状态变化后，Compose 重新执行「读取过该状态」的 Composable 函数，详见第 22 章）也继续持有同一个状态对象、保住位置。

### 4.3 程序控制滚动

两类写法（都是挂起函数，都要在协程中调用）：

- `scrollState.animateScrollTo(target)`：动画滚到 target，示例用它做「滚到顶部/底部」；
- `scrollState.scrollTo(target)`：无动画直接跳转。

「滚到底部」传的是 `scrollState.maxValue`——内容高度未知时这是唯一入口；想滚到「某个子项」，普通滚动容器没有 LazyColumn 的 `animateScrollToItem(index)` 等条目级 API，需要自己拿子项位置换算偏移。这也是选型时的一个考虑点（第 20 章）。

### 4.4 与 LazyColumn 的组合范围与生命周期区别

| 维度 | Column + verticalScroll | LazyColumn |
| --- | --- | --- |
| 首次组合 | 全部子项立即组合、测量 | 只组合可见窗口内条目 |
| 滚动时 | 子项始终留在组合中，只改变偏移 | 新条目进入窗口才组合，滚远的被移出组合 |
| 子项副作用 | 每个子项的 LaunchedEffect 等进入页面即全部启动（第 25 章） | 只启动可见条目的 |
| 子项 remember 状态 | 容器存活期间一直保留 | 条目被移出组合即丢弃（rememberSaveable 可恢复，第 23 章） |
| 条目 API | 无 item 概念 | item/items、key、animateScrollToItem 等 |

因此它适合数量少、结构固定、需要把一个整体页面滚动起来的场景；数量大、同构、动态的数据集合交给 Lazy 容器（第 20 章给完整决策表）。

### 4.5 嵌套同方向滚动的约束

要分清两件事：

- 测量约束：LazyColumn 这类懒容器必须有有限的视口高度。把它直接放进带 verticalScroll 的 Column，会拿到「高度不限」的约束，运行时抛 IllegalStateException（提示形如 "Vertically scrollable component was measured with an infinity maximum height constraints"），见第 7 节反例。
- 手势分发：两个同方向都能滚的普通容器嵌套（如 verticalScroll 的 Column 里再放一个固定高度、也可 verticalScroll 的子块）不会崩溃，内层先消费手势、滚到边界再传给外层（嵌套滚动手势在第 66 章展开）；但用户很难分辨「这块屏该滚哪层」，能不用就不用。

## 5. 实际执行过程

1. remember 创建 ScrollState
2. 普通 Column/Row 组合全部内容
3. 测量得到大于视口的内容尺寸，框架更新 maxValue
4. ScrollState 限制偏移范围在 0 到 maxValue
5. 手势或协程改变 value，布局按偏移绘制

## 6. Flutter / Dart 3 对照

Flutter 最接近 SingleChildScrollView + Column/Row，ScrollController 对应更强的 ScrollState/控制能力。两者都不适合无界大量条目。

```dart
SingleChildScrollView(
  scrollDirection: Axis.vertical,
  child: Column(
    children: List.generate(20, (i) => Text('Item $i')),
  ),
);
```

相似点：都是「普通布局 + 滚动包装 + 状态对象」的组合，都一次构建全部子项。不等价处：Flutter 的 ScrollController 可以监听 offset、可 attach 到不同滚动视图，程序滚动用 `animateTo`；Compose 把状态（ScrollState）与控制（挂起的 animateScrollTo）合在同一个对象上，且必须借助协程作用域调用。API 面与生命周期不同，不能视为等价。

## 7. 常见错误

- 给一个没有有限视口尺寸的 Column 加 verticalScroll，却期待内部形成独立滚动区（见 4.1 的测量说明）
- 大量数据仍使用 repeat + Column
- 同方向滚动容器嵌套导致手势和测量问题（见 4.5）
- 每次重组手动创建新的 ScrollState（写成 `verticalScroll(ScrollState(0))`），位置不断重置
- 在 onClick 里直接调用 animateScrollTo，不放在协程中

反例：想给 LazyColumn 加固定头部而套了 verticalScroll。

```kotlin
// 错误示例（故意错误，不参与编译）
Column(Modifier.verticalScroll(rememberScrollState())) {
    Text("我想当固定头部")
    LazyColumn { // 拿到高度不限的约束，运行时崩溃
        items(100) { index -> Text("第 $index 行") }
    }
}
```

现象：运行时抛出 IllegalStateException（"Vertically scrollable component was measured with an infinity maximum height constraints"）。原因：外层 verticalScroll 给 LazyColumn 的高度约束是无限的，懒容器无法确定可见窗口。修正：头部直接用 LazyColumn 自己的 `item { Text("我想当固定头部") }` 登记（第 16 章），根本不需要外层滚动。

## 8. 本章检查题

1. rememberScrollState 主要保存什么？
2. verticalScroll 是否让 Column 变成懒加载？
3. 20 个固定设置项页面更适合 Column+verticalScroll 还是 LazyColumn？
4. 为什么 `onClick = { scrollState.animateScrollTo(0) }` 这样写编译不通过？
5. 动手修改：把 3.2 节「滚到底部」按钮里的 animateScrollTo 换成 scrollTo，运行并描述差别。
6. 预测行为：在 Column+verticalScroll 里放 30 个各自带 `LaunchedEffect { println("出现 $index") }` 的子项，进入页面时会打印几条日志？换成 LazyColumn 呢？

答案：

1. 当前滚动值、最大值以及滚动进行状态，使重组后能继续保持位置。
2. 不会。Column 中的所有子项仍会一次性组合和测量，只是通过滚动偏移查看不同区域。
3. Column + verticalScroll。数量少、结构固定，不需要懒加载。
4. animateScrollTo 是挂起函数，只能在协程中调用；onClick 的 lambda 不是挂起环境，编译不通过。要用 `rememberCoroutineScope()` 取作用域，再 `scope.launch { scrollState.animateScrollTo(0) }`。
5. scrollTo 无动画直接跳到底部，animateScrollTo 平滑滚过去；其余行为相同。
6. Column+verticalScroll 会立刻打印 30 条（全部子项立即组合，LaunchedEffect 随之全部启动）；LazyColumn 只打印可见附近的若干条，滚动时才继续打印新进入窗口的。

## 9. 本章总结

verticalScroll/horizontalScroll 给普通布局增加滚动，但不增加懒组合：全部子项立即组合并常驻，滚动只是偏移。ScrollState 保存像素偏移与最大值；程序滚动用挂起的 animateScrollTo/scrollTo，配合 rememberCoroutineScope。懒容器需要有限视口，同方向嵌套要么崩溃要么交互混乱。两族容器怎么选，下一章给完整标准。
