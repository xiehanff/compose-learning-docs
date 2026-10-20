# 第 57 章：Canvas 与 DrawScope 基础

## 1. 本章目标

学完后，你应当能够：

- 用 `Canvas` 组合从零绘制图形，理解 `DrawScope` 的坐标系与单位；
- 熟练使用 `drawLine`、`drawCircle`、`drawRect`、`drawRoundRect`、`drawArc`、`drawOval`；
- 正确配置 `Paint` 的 `color`、`style`、`strokeWidth`、`StrokeCap`、`alpha` 等参数；
- 用 `Density` 接口把 dp 正确换算成 px，避免尺寸错乱。

## 2. 项目中的位置

进阶篇不再局限于 compose_demo 项目，本章使用通用 Compose API。

- `androidx.compose.foundation`：`Canvas`
- `androidx.compose.ui.graphics.drawscope`：`DrawScope`、`Stroke`（描边样式）、`drawLine`、`drawCircle`、`drawRect`、`drawRoundRect`、`drawArc`、`drawOval`
- `androidx.compose.ui.graphics`：`Color`、`StrokeCap`、`StrokeJoin`
- `androidx.compose.ui.unit`：`dp`、`Density`（`toPx()`）

> 官方参考：[Canvas](https://developer.android.com/reference/kotlin/androidx/compose/foundation/Canvas)、[DrawScope](https://developer.android.com/reference/kotlin/androidx/compose/ui/graphics/drawscope/DrawScope)

## 3. 项目源码片段

画一个圆环进度底 + 网格线，覆盖本章大部分 API：

<!-- compile decls -->
```kotlin
@Composable
fun RingAndGrid() {
    Canvas(Modifier.fillMaxSize()) {
        // DrawScope 内：单位是 px，坐标原点在左上角

        // 1) 网格线：横竖各两条，用细线条
        val gridColor = Color(0x33000000)
        repeat(2) { i ->
            val offset = size.width * (i + 1) / 3f
            drawLine(
                color = gridColor,
                start = Offset(offset, 0f),
                end = Offset(offset, size.height),
                strokeWidth = 1f
            )
        }
        repeat(2) { i ->
            val offset = size.height * (i + 1) / 3f
            drawLine(
                color = gridColor,
                start = Offset(0f, offset),
                end = Offset(size.width, offset),
                strokeWidth = 1f
            )
        }

        // 2) 圆环：drawArc + Stroke 描边，粗 12dp
        val ringWidth = 12.dp.toPx()
        drawArc(
            color = Color(0xFFE0E0E0),
            startAngle = 0f,
            sweepAngle = 300f,          // 缺口 60 度，留作进度起点
            useCenter = false,
            topLeft = Offset(50f, 50f),
            size = Size(size.width - 100f, size.width - 100f),
            style = Stroke(width = ringWidth, cap = StrokeCap.Round)
        )

        // 3) 填充圆：圆心在画布中心，半径 40dp
        drawCircle(
            color = Color(0xFF2196F3),
            radius = 40.dp.toPx(),
            center = center
        )
    }
}
```

## 4. 逐步解释

1. **`Canvas` 只是一个壳**。`Canvas(modifier)` 本身不做任何事，它把 `DrawScope` 交给后面的 lambda。`DrawScope` 就是你的"画布上下文"，所有 `draw*` 函数都是它的扩展函数，所以只能在 lambda 内部调用。记住：`Canvas` 负责占位和分配尺寸，`DrawScope` 负责画。由此带来两个常被忽略的事实：其一，**画多大不改变布局占位**——占位尺寸完全由 modifier 决定，lambda 里把圆画到边界外并不会撑大或缩小组件；其二，**画的内容不进入语义树**——无障碍服务（如 TalkBack）读不到你画了什么圆环网格，需要暴露语义时要在 modifier 上另外声明（第 80 章无障碍展开）。

2. **坐标系：左上角是原点，单位是 px**。x 轴向右、y 轴向下，这与传统数学坐标系相反。`size` 是 `DrawScope` 提供的 `Size` 属性，等于 Canvas 的实际像素尺寸；`center` 是它的快捷方式。所有坐标、半径、宽度都是 px。记住：在 DrawScope 里你看到的一切数字都是 px，不是 dp。

3. **`Paint` 参数不是独立对象，而是每个 draw 函数的参数**。`drawCircle(color, radius, center)` 里 `color`、`radius` 就是 `Paint` 的快捷参数。需要更精细控制时传 `style`：`Fill`（默认，实心）或 `Stroke`（描边）。描边时用 `strokeWidth` 定宽度，`StrokeCap.Round` 让线头变圆。`drawArc` 多两个角度参数：`startAngle`（起始角，0 度在三点钟方向，顺时针）和 `sweepAngle`（扫过角度，可为负表示逆时针）；`useCenter = false` 表示画弧线而非扇形。记住：`useCenter` 决定你是画"环"还是画"饼"。

4. **dp 与 px 的换算**。`DrawScope` 实现了 `Density` 接口，所以 lambda 内部可以直接 `12.dp.toPx()`。不要在 lambda 外换算：同样的 dp，在不同屏幕密度下 px 值不同，而 `toPx()` 必须拿到当前 `Density` 才有意义。记住：dp 是逻辑单位，px 是物理单位，中间必须经过 `Density`。

5. **常见绘制函数速查**。`drawLine` 用 `start`/`end` 两个 `Offset` 定线段；`drawOval` 用 `topLeft` + `size` 定外接矩形；`drawRoundRect` 多了 `cornerRadius`。它们的共同点是：要么给中心点，要么给矩形区域，参数语义直白，写错最多是位置偏了，不会崩溃。

## 5. 实际执行过程

1. 组合阶段：`Canvas(Modifier.fillMaxSize())` 按 modifier 分配尺寸，测量完成。
2. 绘制阶段：系统为 Canvas 创建 `DrawScope`，其 `size` 等于 Canvas 的实际像素尺寸。
3. 依次执行 lambda：先画两条竖网格线、两条横网格线（依赖 `size` 计算位置）。
4. 计算 `ringWidth = 12.dp.toPx()`，画 300 度的圆环描边；缺口留在 0 度附近。
5. 画实心蓝圆，圆心 `center`、半径 `40.dp.toPx()`，覆盖在网格与圆环之上。
6. 绘制结束，画布内容合成到界面上。之后窗口尺寸等变化会让布局失效、重新走测量与绘制；普通重组在布局未失效时不会重新测量（见第 22、46 章关于重组边界的讨论）。

## 6. Flutter / Dart 3 对照

相似点：Compose 的 `Canvas` + `DrawScope` 对应 Flutter 的 `CustomPaint` + `CustomPainter`，`drawCircle`、`drawLine`、`drawArc` 等方法名几乎一一对应，连参数都相近（颜色、起点、半径）。两者底层都是 Skia 风格的绘图模型。

```dart
class GridPainter extends CustomPainter {
  @override
  void paint(Canvas canvas, Size size) {
    final gridPaint = Paint()
      ..color = const Color(0x33000000)
      ..strokeWidth = 1;

    for (var i = 1; i <= 2; i++) {
      final x = size.width * i / 3;
      canvas.drawLine(Offset(x, 0), Offset(x, size.height), gridPaint);
    }

    final ringPaint = Paint()
      ..color = const Color(0xFFE0E0E0)
      ..style = PaintingStyle.stroke
      ..strokeWidth = 12
      ..strokeCap = StrokeCap.round;

    canvas.drawArc(
      Rect.fromLTWH(50, 50, size.width - 100, size.width - 100),
      0,
      300 * (3.14159 / 180), // drawArc 用弧度，Compose 用角度
      false,
      ringPaint,
    );
  }

  @override
  bool shouldRepaint(covariant CustomPainter oldDelegate) => false;
}
```

本质区别有三点：第一，Flutter 把画笔封装成独立的 `ui.Paint` 对象，用 `..style = PaintingStyle.stroke` 链式配置，Compose 则把 `Paint` 参数直接内联在 `draw*` 函数的参数列表里；这还带来一个行为差异——Flutter 复用同一个 `Paint` 时样式会跨调用残留，Compose 每次 draw 调用的 `style` 等参数独立、默认 `Fill`，无残留；第二，Flutter 的 `drawArc` 角度用弧度，Compose 用角度（0 度在三点钟方向）；第三，Flutter 要求 `CustomPainter` 是独立类并实现 `shouldRepaint` 协议来声明何时需要重绘，Compose 的 `Canvas` lambda 直接在组合里写，绘制代码与 UI 结构同处一处，改起来更顺手。另外注意 Flutter 的 `Canvas` 来自 `dart:ui`，默认单位同样是逻辑像素而非物理像素，这点与 Compose 的 px 语义需要区分——Compose 的 DrawScope 拿到的是 px。

## 7. 常见错误

1. **把 dp 当 px 直接用**。`drawCircle(radius = 40f)` 本意是 40dp，在 2x 屏幕上实际只有 40px，看起来小一倍。必须 `40.dp.toPx()`。
2. **忘记 `size` 属性，写死尺寸**。`Size(300f, 300f)` 在横屏、分屏下就画歪了。用 `size`、`center` 相对计算。
3. **拿 Flutter 的「Paint 状态残留」套 Compose**。Flutter 里复用同一个 `Paint` 对象，上一次的 `style` 会残留到下一次调用；Compose 的 draw 函数每次调用独立接收 `style` 参数、默认 `Fill`，不存在跨调用残留。Compose 里真正常犯的错是反过来：想要描边却忘了传 `style = Stroke(...)`，结果得到实心形状。
4. **在 `Canvas` 外调用 `draw*` 系列函数**。`drawCircle` 是 `DrawScope` 的扩展函数，脱离 lambda 无法调用，报"unresolved reference"。
5. **`drawArc` 的 `useCenter` 用错**。画进度环写成 `useCenter = true`，得到的是扇形（从圆心辐射），视觉上完全不是圆环。

## 8. 本章检查题

1. `DrawScope` 里的坐标单位是什么？`size` 属性从哪来？
2. `Stroke` 与 `Fill` 的区别是什么？`drawArc` 的 `useCenter` 决定什么？
3. dp 如何转换成 px？为什么不能在 lambda 外换算？
4. `drawOval` 与 `drawCircle` 在参数上的差异是什么？

答案：

1. 单位是 px，原点在左上角，x 向右、y 向下。`size` 是 `DrawScope` 的属性，等于 Canvas 组合的实际像素尺寸，由 modifier 测量结果决定。
2. `Fill` 画实心区域，`Stroke` 只画轮廓（配合 `strokeWidth`）。`useCenter = true` 时 `drawArc` 从圆心到弧线两端形成扇形；`false` 时只画弧线本身，是进度环的标准写法。
3. 通过 `Density` 接口的 `toPx()`：`12.dp.toPx()`。`DrawScope` 实现了 `Density`，所以在 lambda 内可直接调用；在外换算没有当前密度的上下文，结果不可靠。
4. `drawCircle` 用 `radius` + `center` 定义；`drawOval` 用 `topLeft` + `size` 定义外接矩形，椭圆被限制在该矩形内。

## 9. 本章总结

`Canvas` 把 `DrawScope` 交给你，后者以左上角为原点、以 px 为单位提供全套 `draw*` 函数。掌握 `Paint` 的 `style`/`strokeWidth` 参数、`drawArc` 的角度语义，以及 dp 到 px 的 `toPx()` 换算，就能从零画出圆环、网格等基础图形，为后面的进度环、自定义绘制打下基础。
