# 第 60 章：Brush、Shader 与渐变

## 1. 本章目标

学完后，你应当能够：

- 区分 `SolidColor`、`linearGradient`、`radialGradient`、`sweepGradient`、`horizontalGradient`、`verticalGradient` 等常用 `Brush`；
- 把 `Brush` 用在 `drawRect` 等绘制函数的 `brush` 参数和 `Modifier.background` 上；
- 理解 `Brush.shader` 与底层 `Shader`（`Tiling`）的关系；
- 用动画驱动颜色或偏移实现渐变动画，并规避"每帧 new Brush"的性能陷阱。

## 2. 项目中的位置

进阶篇不再局限于 compose_demo 项目，本章使用通用 Compose API。

- `androidx.compose.ui.graphics`：`Brush`、`SolidColor`、`linearGradient`、`radialGradient`、`sweepGradient`、`ShaderBrush`
- `androidx.compose.ui.graphics.drawscope`：`DrawScope.drawRect`、`drawCircle`
- `androidx.compose.foundation`：`Modifier.background`
- `androidx.compose.ui`：`Modifier.drawWithCache`（缓存 Brush）

## 3. 项目源码片段

线性渐变背景 + 径向渐变圆形 + 扫描渐变"表盘"，展示三种渐变 Brush 的典型用法：

```kotlin
@Composable
fun GradientShowcase() {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {

        // 1) 线性渐变背景：从上到下
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF673AB7), Color(0xFF00BCD4))
                    )
                )
        )

        // 2) 径向渐变圆形：中心淡紫，向外变深
        Canvas(Modifier.size(120.dp)) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFE1BEE7), Color(0xFF8E24AA)),
                    center = center,
                    radius = size.minDimension / 2f
                ),
                radius = size.minDimension / 2f,
                center = center
            )
        }

        // 3) 扫描渐变：模拟表盘刻度色带，用 drawWithCache 缓存
        Canvas(Modifier.size(120.dp)) {
            drawCircle(
                brush = Brush.sweepGradient(
                    colors = listOf(Color.Red, Color.Green, Color.Blue, Color.Red)
                ),
                radius = size.minDimension / 2f
            )
        }
    }
}

// 渐变动画：用 animateFloat 的进度驱动颜色插值，Brush 每次重建但缓存避免每帧分配
@Composable
fun AnimatedGradient() {
    val transition = rememberInfiniteTransition(label = "gradient")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "progress"
    )

    val start = lerp(Color(0xFF673AB7), Color(0xFFFF5722), progress)
    val end = lerp(Color(0xFF00BCD4), Color(0xFFFFC107), progress)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .drawWithCache {
                val brush = Brush.verticalGradient(colors = listOf(start, end))
                onDrawBehind { drawRect(brush = brush) }
            }
    )
}
```

## 4. 逐步解释

1. **`Brush` 是"画刷"，决定怎么着色**。普通绘制给一个 `Color` 是纯色；`Brush` 则提供渐变、图像等复杂着色方式。最基础的是 `SolidColor(color)`——本质还是纯色，但类型上是 `Brush`，方便统一接口。`Brush` 有两种使用位置：绘制函数的 `brush = ...` 参数（`drawRect`、`drawCircle` 等都支持），以及 `Modifier.background(brush)`。记住：看到"想要渐变"就想到 `Brush`，看到"想要纯色"直接用 `Color` 即可。

2. **三种渐变各管各的"方向"**。`linearGradient` 沿一条直线过渡：`Brush.linearGradient(colors, start, end)` 自定义起终点，`horizontalGradient`/`verticalGradient` 是它的上下/左右快捷方式；`radialGradient` 从 `center` 向 `radius` 外扩散，参数是圆心和半径；`sweepGradient` 绕中心旋转一周过渡，像表盘色带或雷达扫描。共同参数 `colors` 是颜色列表（至少两个），`colorStops` 可以指定每个颜色的位置（0f..1f）。记住：选 Brush 先想"渐变沿着什么形状走"——直线、圆还是圆周。

3. **`Brush.shader` 通往底层 `Shader`**。`ShaderBrush` 内部持有 `androidx.compose.ui.graphics.Shader`，`Brush.shader(shader)` 可以从任意 `Shader` 构造 Brush。`Shader` 是更底层的着色器抽象，支持 `TileMode`（`Clamp`/`Repeat`/`Mirror`）控制渐变区域外的行为：`Clamp` 用边缘色填充、`Repeat` 平铺重复、`Mirror` 镜像翻转。常规渐变用 `Brush` 就够，只有需要平铺、图像纹理时才下沉到 `Shader`。记住：`Brush` 是面向开发者的便捷层，`Shader` 是底层实现，两者通过 `Brush.shader` 桥接。

4. **渐变动画的两种思路**。一是"颜色插值"：用 `lerp(colorA, colorB, progress)` 逐帧算出中间色，再组出新 `Brush`（如 `AnimatedGradient` 所示）；二是"偏移动画"：固定 `Brush`，用动画值平移 `start`/`end` 制造流动感。无论哪种，`Brush` 都是按帧变化的，所以必须放进 `drawWithCache`（第 58 章）缓存，避免每帧在绘制阶段分配对象。记住：动态 Brush 配 `drawWithCache`，静态 Brush 配 `remember`。

5. **渐变文字**（简述）。`Text` 的默认 `color` 只接受纯色；要渐变文字，需要配合 `drawText`（`TextMeasurer`）把文字画到 `Paint` 上，并给 Paint 设 `shader` 为渐变。这条链路较深，本章先知道"文字渐变 = drawText + Shader"即可，实际项目用到再展开。

## 5. 实际执行过程

1. 组合阶段：`GradientShowcase` 的三个画布完成测量，尺寸确定。
2. 绘制阶段：线性渐变背景按 `verticalGradient` 的颜色列表在矩形内从上到下插值着色。
3. 径向渐变圆形以画布中心为圆心、以 `size.minDimension / 2f` 为半径向外扩散渐变。
4. 扫描渐变围绕中心旋转一周，红→绿→蓝→红循环（首尾同色避免接缝）。
5. `AnimatedGradient`：`infiniteTransition` 驱动 `progress` 在 0..1 间往复，`lerp` 算出起止色，`drawWithCache` 重建 Brush 并重绘；组合销毁时动画自动停止。

## 6. Flutter / Dart 3 对照

相似点：Flutter 的 `LinearGradient`、`RadialGradient`、`SweepGradient` 与 Compose 三种渐变 Brush 一一对应，参数也接近——`colors`、`begin`/`end`（对应 `start`/`end`）、`center`、`radius`、`tileMode`（对应 `TileMode`）。

```dart
Container(
  width: 120,
  height: 120,
  decoration: const BoxDecoration(
    gradient: LinearGradient(
      colors: [Color(0xFF673AB7), Color(0xFF00BCD4)],
      begin: Alignment.topCenter,
      end: Alignment.bottomCenter,
    ),
  ),
)

// 径向渐变
Container(
  width: 120,
  height: 120,
  decoration: const BoxDecoration(
    shape: BoxShape.circle,
    gradient: RadialGradient(
      colors: [Color(0xFFE1BEE7), Color(0xFF8E24AA)],
      center: Alignment.center,
      radius: 0.5,
    ),
  ),
)
```

本质区别：Flutter 的渐变是 `BoxDecoration` 的一部分，只能作为"容器装饰"使用，且用 `Alignment` 表达方向（`Alignment.topCenter`）；Compose 的 `Brush` 是独立类型，既能给 `background`，也能直接作为 `drawRect`/`drawCircle` 的 `brush` 参数在 Canvas 里用，使用面更宽、与绘制体系耦合更紧。Flutter 里没有与 `Brush` 完全等价的抽象——它把渐变收敛在 decoration 里，而 Compose 把着色能力统一进了 `Brush` 这一个接口。

## 7. 常见错误

1. **颜色列表与位置数组长度不匹配**。`colors` 给三个颜色、`colorStops` 只给两个位置，抛异常或效果错乱。`colorStops` 必须与 `colors` 一一对应（长度相同）。
2. **`radialGradient` 忘了 `center`/`radius`**。默认 `center` 是画布中心、`radius` 是 `Float.POSITIVE_INFINITY`，效果可能与预期差异很大（比如渐变只集中在角落）。绘制到具体形状时显式传 `center` 与 `radius`。
3. **每帧 `new Brush` 不做缓存**。动画里每次绘制都 `Brush.linearGradient(...)`，对象频繁分配。放 `drawWithCache` 或 `remember`。
4. **把 `Brush` 当 `Paint` 用**。`drawRect` 的 `brush` 参数不能替代 `Paint` 的 `style`、`strokeWidth`；描边圆环仍需传 `style = Stroke(...)`，`brush` 只负责着色。
5. **`sweepGradient` 首尾颜色不闭合**。颜色列表首尾色不同，扫描渐变出现明显接缝。让最后一个颜色等于第一个颜色（如红→绿→蓝→红）。

## 8. 本章检查题

1. `linearGradient`、`radialGradient`、`sweepGradient` 三种 Brush 的区别是什么？
2. `Brush` 可以用在哪些位置？
3. `tileMode` 的作用是什么？何时需要用到它？
4. 渐变动画中如何避免每帧分配对象？

答案：

1. `linearGradient` 沿一条直线过渡（`horizontalGradient`/`verticalGradient` 是其快捷方式）；`radialGradient` 从 `center` 向 `radius` 方向同心圆扩散；`sweepGradient` 绕中心旋转一周过渡，形如表盘色带。
2. 两处：绘制函数的 `brush` 参数（`drawRect`、`drawCircle`、`drawArc` 等），以及 `Modifier.background(brush)`。此外可经 `Brush.shader` 与底层 `Shader` 互转。
3. `tileMode` 控制渐变区域之外的着色行为：`Clamp` 用边缘色延伸、`Repeat` 平铺重复渐变、`Mirror` 镜像翻转。做纹理平铺或渐变范围小于绘制区域时用到。
4. 动态变化的 Brush 用 `drawWithCache` 缓存（尺寸变化时才重建）；静态 Brush 用 `remember` 构建一次。避免在每帧绘制 lambda 里直接 `new Brush`。

## 9. 本章总结

`Brush` 是 Compose 的统一着色抽象：`linearGradient`、`radialGradient`、`sweepGradient` 分别对应直线、圆和圆周三种渐变形态，可用于绘制函数的 `brush` 参数与 `background`；需要平铺等底层能力时通过 `Brush.shader` 下沉到 `Shader`。渐变动画用 `lerp` 插值颜色或平移偏移驱动，记住把 Brush 放进缓存，避免每帧分配对象。
