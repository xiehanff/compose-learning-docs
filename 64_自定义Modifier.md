# 第 64 章：自定义 Modifier：layout、draw、pointerInput

## 1. 本章目标

第 62 章我们学会了用 `Layout` 组合实现自定义布局，但那是一次性的。本章解决「把布局、绘制、手势逻辑封装成可复用的 Modifier」的问题。

学完后，你应当能够：

- 用 `Modifier.layout` 修改测量与放置
- 用 `Modifier.drawBehind` / `drawWithContent` 在链中绘制
- 用 `Modifier.pointerInput` 接收手势（手势检测的完整规则在第 65 章）
- 理解 modifier 链顺序的意义；知道 `composed` 是兼容性/易懂写法，生产级高频自定义节点应了解 `Modifier.Node`

## 2. 项目中的位置

进阶篇不再局限于 compose_demo 项目，本章使用通用 Compose API。

- `androidx.compose.ui.layout.layout`
- `androidx.compose.ui.draw.drawBehind` / `drawWithContent`
- `androidx.compose.ui.input.pointer.pointerInput`
- `androidx.compose.ui.composed`（短小、需要组合上下文的兼容性写法）
- `Modifier.Node` / `ModifierNodeElement`（生产级自定义节点的推荐方向）
- `androidx.compose.foundation.gestures.detectTapGestures`（点按识别器，第 65 章详解）

## 3. 项目源码片段

场景一：文字图标太小，手指点不准。封装一个「扩大点击热区」的 modifier——layout 撑大测量区域，pointerInput 接收整个区域的点击。

<!-- compile -->
```kotlin
fun Modifier.expandClickArea(
    extra: Dp = 12.dp,
    onClick: () -> Unit,
): Modifier = composed {
    val extraPx = with(LocalDensity.current) { extra.toPx() }
    // pointerInput 的 keys 是 Unit，协程不会重启；用 rememberUpdatedState 保证总是调到最新的 onClick
    val currentOnClick by rememberUpdatedState(onClick)
    this
        .layout { measurable, constraints ->
            // 抬高最小尺寸：子项在四周各空出 extra（自带固定尺寸并居中绘制的 Icon 效果最直观），
            // 热区就是扩大后的布局尺寸。上限必须同时受父约束限制：
            // 父级最大尺寸比 extra 小时，minWidth > maxWidth 的约束会直接抛异常
            val expandedMinWidth = (constraints.minWidth + (extraPx * 2).roundToInt())
                .coerceAtMost(constraints.maxWidth)
            val expandedMinHeight = (constraints.minHeight + (extraPx * 2).roundToInt())
                .coerceAtMost(constraints.maxHeight)
            val placeable = measurable.measure(
                constraints.copy(
                    minWidth = expandedMinWidth,
                    minHeight = expandedMinHeight,
                )
            )
            // 报给父级的尺寸同样要夹回父约束，否则会突破父级上限
            layout(
                constraints.constrainWidth(placeable.width),
                constraints.constrainHeight(placeable.height),
            ) {
                placeable.place(0, 0)
            }
        }
        .pointerInput(Unit) {
            detectTapGestures(onTap = { currentOnClick() })
        }
}

// 使用：expandClickArea 必须写在固定尺寸的 modifier 之前（更靠外层），
// 否则 size(24.dp) 把约束卡死在 24，热区根本没有扩展空间
Icon(
    Icons.Filled.FavoriteBorder,
    contentDescription = "收藏",
    modifier = Modifier.expandClickArea(extra = 16.dp, onClick = { /* 收藏 */ }),
)
```

场景二：按下时画一层按压高光——把状态放进 composed，绘制交给 drawBehind。

<!-- compile -->
```kotlin
fun Modifier.pressHighlight(): Modifier = composed {
    var pressed by remember { mutableStateOf(false) }
    this
        .pointerInput(Unit) {
            detectTapGestures(
                onPress = {
                    pressed = true
                    tryAwaitRelease()
                    pressed = false
                },
            )
        }
        .drawBehind {
            if (pressed) {
                drawCircle(color = Color.Black.copy(alpha = 0.15f))
            }
        }
}
```

## 4. 逐步解释

`Modifier.layout { measurable, constraints -> ... }` 是第 62 章 Layout 组合的修饰符形态：同样是 measure → place → layout 三步，但作用对象是「链中它后面的内容」。它的典型用途：扩大可点击区域、加内边距、偏移位置、强制最小尺寸。

`Modifier.drawBehind` 在绘制阶段、内容**之后**（底层）绘制；`Modifier.drawWithContent` 允许在 drawContent() 前后分别绘制，可以做渐变遮罩、边框叠加等。绘制 modifier 里拿到的 `size` 是布局后的尺寸，绘制逻辑与布局无关。

`Modifier.pointerInput(keys) { ... }` 为链上节点启动一个手势协程，用来接住这个节点上的原始指针事件；块里通常搭配一个"手势识别器"把原始事件翻译成有意义的回调，本章用的是 `detectTapGestures`——它把"按下、抬起"的序列识别成 `onTap`、`onPress`、长按等回调（两者都是第 65 章的内容，本章只需会照着写）。关键点是 `keys`：协程块内捕获的外部值变化时，只有 keys 变化协程才会重启并拿到新值；keys 不变，块内闭包永远是组合那一刻的旧值。所以像 `onClick` 这种「每次重组都可能换一个新 lambda」的回调，要先用 `rememberUpdatedState(onClick)` 包一层，再在协程里读 `currentOnClick()`（第 49 章）。

上面的 `expandClickArea` 还有两个容易踩的坑：`layout` 里修改约束时必须保证修改后的最小值不超过最大值（父级可能已经把最大尺寸卡得很小），报给父级的尺寸也要用 `constraints.constrainWidth/Height` 夹回父约束，否则会违反约束；因为 `Icon` 自带的 `size(24.dp)` 排在调用方 modifier 的后面（更靠内），所以扩展热区的 modifier 要写在固定尺寸之前，写成 `Modifier.size(24.dp).expandClickArea(...)` 就没有扩展空间了。

`Modifier.composed { ... }` 让 modifier 工厂函数获得组合上下文——里面可以调用 `remember`、`LocalDensity.current` 等。它适合教学、兼容旧实现或确实需要直接读取组合上下文的短小 Modifier，但本身有组合开销，不能把它当作所有有状态 Modifier 的必选方案。官方当前更推荐用 `Modifier.Node` / `ModifierNodeElement` 构建可复用、高性能的自定义节点；Node 实现通过 `create/update` 管理参数，并按需实现绘制、布局或指针接口。普通无状态组合优先直接组合已有 Modifier。

modifier 链的顺序意义：链按声明顺序从外到内包裹，**先声明的在外层、后声明的在内层**。对 layout 类 modifier，外层先测量、其约束包裹内层结果；对内层内容，绘制时先声明的画在更下层、后声明的覆盖其上。比如 `padding().background()` 与 `background().padding()` 的绘制结果不同——顺序错了，背景就画不到 padding 区域。

记住：**先判断是否能组合已有 Modifier；需要自定义节点时优先考虑 Modifier.Node；短小示例才用 composed。测量、绘制、输入各有自己的 Modifier 能力，顺序决定包裹与覆盖关系。**

## 5. 实际执行过程

1. 组合阶段执行 composed 块，remember 创建 pressed 状态、读取 density
2. 测量阶段 expandClickArea 的 layout 抬高最小约束（并夹回父约束上限），测量子项得到被撑大后的 Placeable
3. 报给父级的尺寸同样夹回父约束，子项不变形（`Icon` 自带的 `size(24.dp)` 居中显示）
4. 用户点击扩大后的热区，pointerInput 协程收到事件，detectTapGestures 识别出 tap
5. onClick 执行（rememberUpdatedState 保证是最新传入的那个）；pressHighlight 场景里 pressed 置 true 触发重组
6. 重组后 drawBehind 按新状态重绘高光，抬起后恢复

## 6. Flutter / Dart 3 对照

先讲相似点：Flutter 的装饰能力也通过「包裹」实现，只是形态是嵌套 widget。

```dart
GestureDetector(
  onTap: onClick,
  child: DecoratedBox(
    decoration: BoxDecoration(color: Colors.black.withValues(alpha: 0.15)),
    child: Padding(padding: const EdgeInsets.all(16), child: child),
  ),
)
```

本质区别：Flutter 没有与 Compose modifier 完全对应的概念——同样的「点击 + 背景 + 内边距」要用三个嵌套 widget 拼，自定义行为得写 RenderObject。Compose 的 modifier 是**可组合的装饰器**：一个对象链按顺序作用于同一个节点，能在测量、绘制、输入三个层面分别注入逻辑，且以函数式 API 组合复用。两者表达力等价，但 modifier 的复用和排序更紧凑。

记住：**Compose modifier = 可组合、可排序、三层可注入的装饰器。**

## 7. 常见错误

1. 在 `Modifier.layout` 里不调用 measure 或忘记 place：子项不显示或直接抛「measure 未调用」类错误，三步必须完整。
2. `pointerInput` 的 keys 不变导致事件处理用旧状态：闭包捕获的是组合时刻的值。要么把依赖放进 keys（注意会重启协程），要么用 `rememberUpdatedState` 保存最新回调（本章 `expandClickArea` 用的就是后者）。
3. 在 `layout` 里直接改约束不看上限：父级最大尺寸比 `extra` 小时，`constraints.copy(minWidth = 更大的值)` 会造出`minWidth > maxWidth`的非法约束并抛异常；正确做法是先 `coerceAtMost(constraints.maxWidth/maxHeight)`，再用 `constraints.constrainWidth/Height` 裁剪最终尺寸。
4. 把 `remember` 写在普通 modifier 扩展函数里（没包 composed）：remember 只能在组合上下文调用，编译直接报错。
5. 顺序搞反：padding 与 background 的顺序决定背景覆盖范围；drawBehind 写在链中不同位置影响绘制层级——先声明的绘制在下层，被后声明的覆盖。固定尺寸的 `size(...)` 写在扩展热区的 modifier 之前，热区就扩不出来。
6. 在 layout 块里现算 density/px：layout 块不是组合上下文，应该在 composed 阶段先算好再捕获进闭包。

## 8. 本章检查题

1. `layout` modifier 能修改什么？
2. `pointerInput` 的 keys 参数起什么作用？
3. modifier 的顺序影响什么？
4. `composed` 和 `Modifier.Node` 分别解决什么问题？为什么不能把 `composed` 当成所有有状态 modifier 的唯一方案？
5. `expandClickArea` 为什么用 `rememberUpdatedState(onClick)`？

答案：

1. 测量与放置：可以改测量时的约束（撑大/收紧）、改子项测量结果、改放置位置，从而影响尺寸与热区。
2. keys 变化时重启手势协程，让块内闭包重新捕获最新外部值；keys 不变则协程一直用旧闭包。
3. 包裹关系与覆盖层级：先声明的在外层、先测量、绘制在下层；后声明的在内层、覆盖在外层之上。
4. `composed` 为短小实现提供组合上下文，能使用 `remember` 和 CompositionLocal；但它有组合开销。生产级自定义节点通常使用 `Modifier.Node` / `ModifierNodeElement`，由节点生命周期和更新机制承载状态。
5. 因为它的 `pointerInput(Unit)` 的 keys 固定不变，手势协程不会重启，块内闭包会一直用组合那一刻捕获的旧 `onClick`；用 `rememberUpdatedState` 包一层后，协程每次读取的都是最新传入的回调。

官方参考：[Create custom modifiers](https://developer.android.com/develop/ui/compose/custom-modifiers)，包括组合已有 Modifier、Composable 工厂及 `Modifier.Node` 的选择与迁移说明。

## 9. 本章总结

自定义 modifier 就是可复用的装饰器：layout 改测量与放置、drawBehind/drawWithContent 参与绘制、pointerInput 收手势。简单示例可用 `composed`，但它不是有状态 Modifier 的唯一或长期推荐实现；性能敏感的生产代码应进一步学习 `Modifier.Node`。记住链顺序决定包裹与覆盖关系，即可把布局、绘制、交互封装成一行可复用的 API。
