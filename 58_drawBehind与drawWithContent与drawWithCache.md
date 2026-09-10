# 第 58 章：drawBehind、drawWithContent 与 drawWithCache

## 1. 本章目标

学完后，你应当能够：

- 用 `Modifier.drawBehind` 在内容下方绘制背景、底环；
- 用 `Modifier.drawWithContent` 控制"先画内容还是先画装饰"，实现进度环上层弧线；
- 用 `Modifier.drawWithCache` 缓存 `Path`（矢量路径，第 59 章）、`Shader` / `Brush`（决定用什么填充，第 60 章）等昂贵绘制资源，避免每帧重建；
- 理解绘制顺序 = modifier 链顺序 + draw 时机，并解释 `drawContent()` 缺失的后果。

## 2. 项目中的位置

进阶篇不再局限于 compose_demo 项目，本章使用通用 Compose API。

- `androidx.compose.ui.draw`：`drawBehind`、`drawWithContent`、`drawWithCache`、`drawContent`
- `androidx.compose.ui.graphics.drawscope`：`DrawScope`、`drawArc`
- `androidx.compose.ui.graphics`：`Brush`、`linearGradient`（第 60 章详解）、`Path`（第 59 章详解）
- `androidx.compose.foundation`：`Canvas`（对照使用）

> 阅读前只需知道两个词，本章不展开它们的具体用法：
>
> - `Path`（第 59 章详解）：由线段与曲线拼成的矢量路径，用来描述任意形状；
> - `Brush` / `Shader`（第 60 章详解）：决定"用什么去填充"——纯色还是渐变。
>
> 本章把它们当作「创建一次、多帧复用」的昂贵绘制对象来举例；缓存本身与对象是谁无关，后面会讲清楚。

## 3. 项目源码片段

圆形进度环：底环用 `drawBehind` 画在内容之下，进度弧用 `drawWithContent` 在 `drawContent()` 之后叠加到内容之上；渐变背景用 `drawWithCache` 缓存 `Brush` 避免每帧重建：

```kotlin
@Composable
fun CircularProgressRing(progress: Float) {  // progress: 0f..1f
    Box(
        modifier = Modifier
            .size(120.dp)
            // 第 1 层：先画底环（在内容之前）
            .drawBehind {
                drawArc(
                    color = Color(0xFFE0E0E0),
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                )
            }
            // 第 2 层：先画内容，再在内容之上画进度弧
            .drawWithContent {
                drawContent()   // 先画 Box 的内容（比如文字）
                drawArc(
                    color = Color(0xFF2196F3),
                    startAngle = -90f,               // 从 12 点方向开始
                    sweepAngle = 360f * progress,    // 按进度扫过
                    useCenter = false,
                    style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                )
            }
    ) {
        Text(
            "${(progress * 100).toInt()}%",
            modifier = Modifier.align(Alignment.Center)
        )
    }
}

// 渐变背景：Brush（填充工具，第 60 章详解）的创建有开销，用 drawWithCache 缓存
@Composable
fun GradientPanel() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .drawWithCache {
                val gradient = Brush.verticalGradient(
                    colors = listOf(Color(0xFF673AB7), Color(0xFF00BCD4))
                )
                // 只在尺寸变化时重建，绘制阶段直接复用
                onDrawBehind {
                    drawRect(brush = gradient)
                }
            }
    )
}
```

## 4. 逐步解释

1. **三个 modifier 的本质都是"绘制阶段的钩子"**。普通 modifier 决定布局与绘制；这三个只干预绘制时机。`drawBehind` 的 lambda 在**内容绘制之前**执行，`drawWithContent` 让你自己决定先后：先调 `drawContent()` 再画，就是叠在内容上面；先画再调 `drawContent()`，就是垫在内容下面。记住：`drawBehind` 是"只能垫底"的简写，`drawWithContent` 是"自由控制顺序"的完整版。

2. **绘制顺序 = modifier 链顺序 + draw 时机**。`drawBehind` 写在链上更靠前的 modifier，其绘制层就越靠下。多个绘制 modifier 叠加时，链前面的先画、后面的后画，形成"先画的在下层"的画家算法。第 57 章学过的 `Canvas` 其实是"占位 + 自己画"的特例；而 `drawBehind` 不新增节点、不改变布局，直接往已有节点的绘制层里插画，所以它不会撑大控件。记住：装饰性绘制优先用 modifier 绘制，而不是包一层 `Canvas`。

3. **进度环为什么要两层**。底环固定画 360 度灰色，进度弧按 `progress` 扫过对应角度，两者叠在一起才有"进度"语义。底环用 `drawBehind`（永远在内容下），进度弧用 `drawWithContent` 且先 `drawContent()`，这样弧线盖在文字上，视觉焦点正确。`startAngle = -90f` 把起点从三点钟转到十二点钟，这是进度环的常规起点。

4. **`drawWithCache` 用于缓存绘制对象**。普通绘制 lambda 可能频繁执行，如果每次都创建 `Path`、`Brush` 或 `Shader`，就会增加分配。`drawWithCache` 会在绘制区域尺寸变化，或其构建块读取的 State 发生变化时重新执行；其他绘制帧复用已创建对象。上例没有读取动画 State，因此渐变 Brush 通常只在尺寸等绘制环境变化时重建。这里要抓住的是缓存的**通用条件**，而不是这几个具体对象：只要某个对象"创建它有一定成本、而且每次画都需要同一个"，就值得放进构建块里创建一次；反过来，只创建一个 `Color` 这类字面量级别的值就没必要用 `drawWithCache`，普通 draw 更直接。（`Path` 与 `Brush`、`Shader` 的具体能力见第 59、60 章，届时回头看这段会更清楚。）

5. **`drawContent()` 是内容本身的分界点**。在 `drawWithContent` 里不调用它，控件的实际内容（子组件、文字、图片）就永远不会被绘制——界面会"消失"。这在做遮罩、蒙层时是特性，但新手常常忘记调用导致内容莫名空白。记住：除非你明确要隐藏内容，否则 `drawWithContent` 里第一件事就是 `drawContent()`。

## 5. 实际执行过程

1. 组合阶段：`CircularProgressRing(0.6f)` 进入组合，modifier 链完成测量，尺寸定为 120dp。
2. 绘制阶段：系统按链顺序触发绘制。`drawBehind` 先执行，画灰色 360 度底环。
3. `drawWithContent` 执行：先调 `drawContent()` 绘制内部 `Text`，再按 `sweepAngle = 216f` 画进度弧。
4. `progress` 从 0.6f 变为 0.75f 时（比如网络加载推进），进度弧重绘，底环与文字不变的部分由 Compose 的绘制缓存优化。
5. `GradientPanel` 的构建块没有读取可变 State，因此首次绘制或尺寸等绘制环境变化时创建 `verticalGradient`，其他帧复用 Brush；如果构建块读取的 State 变化，缓存也会失效并重建。

## 6. Flutter / Dart 3 对照

相似点：两者都能在内容前后叠加装饰。Flutter 里"先画底层"通常用 `Stack` 把底层 `Container` 放前面，或用 `CustomPaint` 的 `painter`（下层）与 `foregroundPainter`（上层）两个画师分别控制前后层。

```dart
Stack(
  alignment: Alignment.center,
  children: [
    // 底层：底环（等价 drawBehind）
    CustomPaint(
      size: const Size(120, 120),
      painter: _RingPainter(progress: 0), // 灰色整环
    ),
    const Text('60%'),                    // 等价 drawContent
    // 上层：进度弧（等价 drawWithContent 中 drawContent 之后的部分）
    CustomPaint(
      size: const Size(120, 120),
      foregroundPainter: _RingPainter(progress: 0.6),
    ),
  ],
)
```

本质区别：Compose 的绘制 modifier 是**装饰器**——它不产生新节点，直接往现有节点的绘制层里按时机插画，顺序由"链顺序 + draw 时机"决定；Flutter 必须通过 `Stack` 排列出真实的子节点层级，前后层是"谁在 children 列表后面谁就在上层"的树结构关系。另一个区别：Flutter 的 `CustomPainter.shouldRepaint` 需要你手动声明何时重绘，Compose 的绘制 lambda 由状态驱动自动重绘，无需声明。

## 7. 常见错误

1. **用 `Modifier.background` 做需要动态变化的绘制**。`background` 适合静态颜色，虽然能传 `Brush`，但它在动画场景下每次都要重建画刷，且不能参与进度计算。动态背景用 `drawBehind` + `Brush` 更合适。
2. **重计算放普通 draw 里不缓存**。`Path`、渐变 `Brush`（均见第 59、60 章）每帧新建，短时间大量分配对象引发卡顿。应放入 `drawWithCache`。
3. **忘记 `drawContent()` 导致内容消失**。`drawWithContent` 里只画了装饰没调 `drawContent()`，子组件全部空白。排查这类 bug 先检查有没有调用它。
4. **层级理解反了**。以为 `drawBehind` 写在链前面就画在最上层。记住：链前面的 modifier 绘制在**下层**。
5. **把进度弧和底环都画在 `drawBehind` 里**。底环垫底没问题，进度弧也应垫底时无所谓，但需要弧线盖住文字时就只能靠 `drawWithContent` 的 `drawContent()` 时机来控制。

## 8. 本章检查题

1. `drawBehind` 与 `drawWithContent` 的绘制顺序分别是什么？
2. `drawWithCache` 缓存的是什么？什么时候会重新执行？
3. 在 `drawWithContent` 中不调用 `drawContent()` 会发生什么？
4. 为什么装饰性背景优先用绘制 modifier 而不是包一层 `Canvas`？

答案：

1. `drawBehind` 在内容之前绘制，永远垫底；`drawWithContent` 让你自行决定——先 `drawContent()` 再画是叠在内容之上，先画再 `drawContent()` 是垫在内容之下。
2. 缓存的是构建块创建的绘制对象，如 `Path`、`Shader`、渐变 `Brush`。首次绘制、尺寸/绘制环境变化，或构建块读取的 State 变化时会重新执行。
3. 控件的内容永远不会被绘制，界面呈现空白。除非刻意做遮罩，否则应先调用 `drawContent()`。
4. `Canvas` 会新增一个节点并参与布局，绘制 modifier 直接在当前节点的绘制层按时机插画，不改变布局、不产生额外节点，成本更低、语义更清晰。

## 9. 本章总结

`drawBehind`、`drawWithContent`、`drawWithCache` 三个绘制 modifier 让你在不动布局的前提下，把装饰画到内容的前后。进度环的经典结构是"`drawBehind` 垫底环 + `drawWithContent` 叠进度弧"，昂贵资源交给 `drawWithCache` 缓存；牢记 `drawContent()` 是内容的分界点，别让它缺席。
