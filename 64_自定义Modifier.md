# 第 64 章：自定义 Modifier：layout、draw、pointerInput

## 1. 本章目标

第 62 章我们学会了用 `Layout` 组合实现自定义布局，但那是一次性的。本章解决「把布局、绘制、手势逻辑封装成可复用的 Modifier」的问题。

学完后，你应当能够：

- 用 `Modifier.layout` 修改测量与放置
- 用 `Modifier.drawBehind` / `drawWithContent` 在链中绘制
- 用 `Modifier.pointerInput` 接收手势
- 理解 modifier 链顺序的意义，用 `composed` 封装有状态 modifier

## 2. 项目中的位置

进阶篇不再局限于 compose_demo 项目，本章使用通用 Compose API。

- `androidx.compose.ui.layout.layout`
- `androidx.compose.ui.draw.drawBehind` / `drawWithContent`
- `androidx.compose.ui.input.pointer.pointerInput`
- `androidx.compose.ui.composed`
- `androidx.compose.foundation.gestures.detectTapGestures`

## 3. 项目源码片段

场景一：文字图标太小，手指点不准。封装一个「扩大点击热区」的 modifier——layout 撑大测量区域，pointerInput 接收整个区域的点击。

```kotlin
fun Modifier.expandClickArea(
    extra: Dp = 12.dp,
    onClick: () -> Unit,
): Modifier = composed {
    val extraPx = with(LocalDensity.current) { extra.toPx() }
    this
        .layout { measurable, constraints ->
            val placeable = measurable.measure(
                constraints.copy(
                    minWidth = constraints.minWidth + (extraPx * 2).roundToInt(),
                    minHeight = constraints.minHeight + (extraPx * 2).roundToInt(),
                )
            )
            layout(placeable.width, placeable.height) {
                placeable.place(extraPx.roundToInt(), extraPx.roundToInt())
            }
        }
        .pointerInput(Unit) {
            detectTapGestures(onTap = { onClick() })
        }
}

// 使用
Icon(
    Icons.FavoriteBorder,
    contentDescription = "收藏",
    modifier = Modifier
        .size(24.dp)
        .expandClickArea(extra = 16.dp, onClick = { /* 收藏 */ }),
)
```

场景二：按下时画一层按压高光——把状态放进 composed，绘制交给 drawBehind。

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

`Modifier.pointerInput(keys) { ... }` 为链上节点启动一个手势协程，接收指针事件（详见第 65 章）。关键点是 `keys`：协程块内捕获的外部值变化时，只有 keys 变化协程才会重启并拿到新值；keys 不变，块内闭包永远是组合那一刻的旧值。

`Modifier.composed { ... }` 让 modifier 工厂函数获得组合上下文——里面可以调用 `remember`、`LocalDensity.current` 等。普通扩展函数不能调用 remember（没有组合作用域），所以**有状态的 modifier 必须用 composed 封装**。注意 composed 本身有开销（每次组合都执行 lambda），简单无状态 modifier 用普通函数即可。

modifier 链的顺序意义：链按声明顺序从外到内包裹，**先声明的在外层、后声明的在内层**。对 layout 类 modifier，外层先测量、其约束包裹内层结果；对内层内容，绘制时先声明的画在更下层、后声明的覆盖其上。比如 `padding().background()` 与 `background().padding()` 的绘制结果不同——顺序错了，背景就画不到 padding 区域。

记住：**有状态用 composed，测量/绘制/输入三层各用各的 modifier，顺序决定包裹与覆盖关系。**

## 5. 实际执行过程

1. 组合阶段执行 composed 块，remember 创建 pressed 状态、读取 density
2. 测量阶段 expandClickArea 的 layout 把约束撑大，测量子项得到放大后的 Placeable
3. 放置阶段把子项偏移 extra，形成四周的「热区」
4. 用户点击热区，pointerInput 协程收到事件，detectTapGestures 识别出 tap
5. onClick 执行；pressHighlight 场景里 pressed 置 true 触发重组
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
2. `pointerInput` 的 keys 不变导致事件处理用旧状态：闭包捕获的是组合时刻的值。要么把依赖放进 keys（注意会重启协程），要么用 `rememberUpdatedState` 保存最新回调。
3. 把 `remember` 写在普通 modifier 扩展函数里（没包 composed）：remember 只能在组合上下文调用，编译直接报错。
4. 顺序搞反：padding 与 background 的顺序决定背景覆盖范围；drawBehind 写在链中不同位置影响绘制层级——先声明的绘制在下层，被后声明的覆盖。
5. 在 layout 块里现算 density/px：layout 块不是组合上下文，应该在 composed 阶段先算好再捕获进闭包。

## 8. 本章检查题

1. `layout` modifier 能修改什么？
2. `pointerInput` 的 keys 参数起什么作用？
3. modifier 的顺序影响什么？
4. 为什么有状态的 modifier 必须用 `composed` 封装？

答案：

1. 测量与放置：可以改测量时的约束（撑大/收紧）、改子项测量结果、改放置位置，从而影响尺寸与热区。
2. keys 变化时重启手势协程，让块内闭包重新捕获最新外部值；keys 不变则协程一直用旧闭包。
3. 包裹关系与覆盖层级：先声明的在外层、先测量、绘制在下层；后声明的在内层、覆盖在外层之上。
4. 因为 `remember` 等 API 只能在组合上下文调用，普通扩展函数没有组合作用域，`composed` 提供了它。

## 9. 本章总结

自定义 modifier 就是「可复用的装饰器」：layout 改测量与放置、drawBehind/drawWithContent 参与绘制、pointerInput 收手势，有状态时用 composed 提供组合上下文。记住链顺序决定包裹与覆盖关系，即可把布局、绘制、交互封装成一行可复用的 API。
