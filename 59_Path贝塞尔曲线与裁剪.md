# 第 59 章：Path、贝塞尔曲线与裁剪

## 1. 本章目标

学完后，你应当能够：

- 用 `Path` 的 `moveTo`、`lineTo`、`quadraticBezierTo`、`cubicTo`、`arcTo`、`close` 绘制任意形状；
- 理解二次与三次贝塞尔曲线的区别，以及控制点对曲线走向的影响；
- 用 `Modifier.clip` / `clipPath` 裁剪内容，理解裁剪对绘制与子内容的双重作用；
- 在需要时用 `fillType`（EvenOdd / NonZero）处理自相交路径的填充规则。

## 2. 项目中的位置

进阶篇不再局限于 compose_demo 项目，本章使用通用 Compose API。

- `androidx.compose.ui.graphics`：`Path`、`PathFillType`、`clipPath`
- `androidx.compose.ui.draw`：`clip`
- `androidx.compose.ui.graphics.drawscope`：`DrawScope.drawPath`
- `androidx.compose.foundation`：`Image`（配合裁剪演示）

## 3. 项目源码片段

用两段三次贝塞尔画一个心形，再把它作为 `clipPath` 把图片裁进心形区域：

```kotlin
@Composable
fun HeartShapeImage() {
    // 心形 Path：先 moveTo 定起点，再两段 cubicTo 画出左右两个圆弧
    val heartPath = remember {
        Path().apply {
            val w = 400f
            val h = 400f
            moveTo(w / 2f, h * 0.35f)                    // 心形顶部凹陷处
            cubicTo(                                     // 右半：控制点把曲线推向右侧
                w * 0.15f, h * 0.15f,                    // 控制点 1
                w * 0.02f, h * 0.55f,                    // 控制点 2
                w / 2f, h * 0.85f                        // 终点：底部尖点
            )
            cubicTo(                                     // 左半：镜像
                w * 0.98f, h * 0.55f,
                w * 0.85f, h * 0.15f,
                w / 2f, h * 0.35f
            )
            close()                                      // 闭合回起点
        }
    }

    Box(
        modifier = Modifier
            .size(200.dp)
            .clipPath(heartPath)          // 裁剪：只有心形区域内的内容可见
    ) {
        Image(
            painter = rememberAsyncImagePainter("https://example.com/avatar.png"),
            contentDescription = "头像",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }
}

// 画布上直接绘制波浪线：quadraticBezierTo 一段平滑过渡
@Composable
fun WaveLine() {
    Canvas(Modifier.fillMaxWidth().height(100.dp)) {
        val path = Path().apply {
            moveTo(0f, size.height / 2f)
            repeat(4) { i ->
                val x = size.width * (i + 1) / 4f
                // 控制点在两段中点之间上下摆动
                quadraticBezierTo(
                    x - size.width / 8f, if (i % 2 == 0) 0f else size.height,
                    x, size.height / 2f
                )
            }
        }
        drawPath(path, color = Color(0xFF2196F3), style = Stroke(width = 4.dp.toPx()))
    }
}
```

## 4. 逐步解释

1. **`Path` 是"画笔的移动轨迹"**。`moveTo(x, y)` 把当前点移动到某处但不画线，相当于提笔走过去；`lineTo` 从当前点画直线到目标点；`quadraticBezierTo(cx, cy, x, y)` 用**一个控制点**画二次贝塞尔；`cubicTo(c1x, c1y, c2x, c2y, x, y)` 用**两个控制点**画三次贝塞尔；`arcTo` 画圆弧；`close()` 从当前点画直线回到 `moveTo` 的起点并闭合。记住：第一笔必须是 `moveTo`，否则起点是 (0,0)，形状会从角落开始。

2. **贝塞尔曲线的本质是"控制点拉曲线"**。二次贝塞尔只有一个控制点，曲线像一个被该点"拉弯"的橡皮筋，适合圆弧类形状；三次贝塞尔有两个控制点，两个控制点各自牵引曲线两端，能表达 S 形、心形这种更复杂的曲率变化。心形代码里，两个 `cubicTo` 各自用两个控制点把曲线向两侧推开，最后在底部汇成尖点。控制点方向错了，曲线就拐向相反方向。记住：控制点决定曲线的**趋势**，不是曲线必须经过的点（只有起点和终点是必经的）。

3. **`clipPath` 是"把画面裁进路径内"**。`Modifier.clipPath(path)` 让后续绘制和所有子内容都只显示在路径内部，路径外的部分被裁掉。它同时作用于绘制层和子组件——上例中图片被裁成心形，就是子内容被裁剪的典型。`Modifier.clip(shape)` 则是用内置形状（圆角矩形、圆形等）裁剪，是 `clipPath` 的便捷特例。裁剪不影响布局尺寸，控件占位仍是 200dp，只是视觉上只剩心形。记住：裁剪是"显示层"的概念，不改变测量结果。

4. **`fillType` 决定自相交路径怎么填**。默认 `NonZero`（非零环绕）按环绕方向计数判断内外；`EvenOdd`（奇偶）规则是"从一点向外画射线，穿过边界的次数为奇数则在内部"。画五角星、带孔洞的图形时，自相交区域需要 `EvenOdd` 才能得到正确镂空。一般简单图形不用管，遇到"内部不该填充"的异常时再检查它。记住：`fillType` 只在路径自相交或含子环时才有意义。

5. **`drawPath` 与 `Canvas` 配合**。绘制阶段用 `drawPath(path, color, style)` 直接把 `Path` 画出来，`Stroke` 描边或 `Fill` 填充由 `style` 决定。`Path` 的构建建议放 `remember` 或 `drawWithCache`（见第 58 章），不要在每帧绘制 lambda 里重建。

## 5. 实际执行过程

1. 组合阶段：`remember` 创建心形 `Path`，`moveTo` 定起点后两段 `cubicTo` 依次执行，`close()` 闭合，路径构建完成。
2. 布局阶段：`Box` 按 `size(200.dp)` 测量，`clipPath` 不改变尺寸。
3. 绘制阶段：`clipPath` 先生效，建立心形裁剪区域；随后 `Image` 按 `ContentScale.Crop` 缩放图片，绘制时只保留心形内部的像素。
4. 图片加载完成（异步），重绘一次，头像以心形呈现。
5. `WaveLine` 的 Canvas 内每帧（尺寸不变时一次）构建波浪 `Path` 并 `drawPath` 描边，显示平滑波浪线。

## 6. Flutter / Dart 3 对照

相似点：Flutter 的 `ui.Path` 与 Compose `Path` 几乎同名同义——`moveTo`、`lineTo`、`quadraticBezierTo`、`cubicTo`、`close` 一字不差，控制点语义完全一致；`ClipPath` widget 对应 `Modifier.clipPath`，都是"把子内容裁进路径"。

```dart
class HeartClipper extends CustomClipper<Path> {
  @override
  Path getClip(Size size) {
    final path = Path();
    final w = size.width;
    final h = size.height;
    path.moveTo(w / 2, h * 0.35);
    path.cubicTo(w * 0.15, h * 0.15, w * 0.02, h * 0.55, w / 2, h * 0.85);
    path.cubicTo(w * 0.98, h * 0.55, w * 0.85, h * 0.15, w / 2, h * 0.35);
    path.close();
    return path;
  }

  @override
  bool shouldReclip(covariant CustomClipper<Path> oldClipper) => false;
}

ClipPath(
  clipper: HeartClipper(),
  child: Image.network('https://example.com/avatar.png',
      fit: BoxFit.cover),
)
```

本质区别：Flutter 的 `CustomClipper` 必须实现 `getClip` 与 `shouldReclip` 两个方法，裁剪逻辑独立成类；Compose 的 `clipPath` 直接接受一个 `Path` 参数，写在使用处即可，无需额外类型。另外 Flutter 里"先画底层再画上层"依赖 `Stack` 的树结构（见第 58 章），而 Compose 的裁剪 modifier 与绘制 modifier 一样，都是按链顺序生效的装饰器，语义更直接。

## 7. 常见错误

1. **`Path` 忘记 `moveTo`**。第一笔直接 `lineTo`，起点落在 (0,0)，线条从左上角莫名其妙地拉出来。构建路径第一笔永远是 `moveTo`。
2. **`close` 与 `lineTo` 原点混淆**。`close()` 回到的是 `moveTo` 的起点，不是 (0,0)。想回到原点要显式 `lineTo(0f, 0f)`。
3. **`clip` 与 `clipPath` 混用导致内容被意外裁掉**。`clip(RoundedCornerShape(...))` 后用 `clipPath` 覆盖，两层裁剪叠加，内容只剩交集区域。裁剪要按需只加一层。
4. **贝塞尔控制点方向理解错误**。把 `cubicTo` 的两个控制点顺序写反，曲线凸向变成凹向，心形画成"倒心"。动手调参前先在草稿上标出控制点位置。
5. **`drawPath` 传 `Stroke` 时宽度不换算**。`strokeWidth = 4f` 在 2x 屏上偏细，应 `4.dp.toPx()`。

## 8. 本章检查题

1. 二次与三次贝塞尔曲线的区别是什么？
2. `clipPath` 会影响到什么？会影响布局尺寸吗？
3. `fillType`（EvenOdd / NonZero）在什么场景下需要关心？
4. `close()` 之后的当前点在哪里？

答案：

1. 二次贝塞尔（`quadraticBezierTo`）只有一个控制点，曲线向该点弯曲，适合简单圆弧；三次贝塞尔（`cubicTo`）有两个控制点，各自牵引曲线两端，能表达 S 形、心形等复杂曲率。
2. `clipPath` 同时作用于当前节点的绘制和所有子内容，路径外的部分一律不可见。它不改变测量结果，布局尺寸保持不变。
3. 当路径自相交或包含多个子环（五角星、镂空孔洞）时，填充内外区域的判定需要 `EvenOdd` 或 `NonZero` 规则；简单图形默认即可。
4. `close()` 画一条直线回到 `moveTo` 的起点，闭合后当前点就是起点；它不是回到原点 (0,0)。

## 9. 本章总结

`Path` 用 `moveTo` 定起点、`lineTo`/贝塞尔曲线/`arcTo` 勾勒轮廓、`close` 闭合，是绘制任意形状的通用工具；控制点决定曲线趋势，是调形的关键。`clipPath` 把内容裁进路径内部，不影响布局，与 `drawPath` 配合即可做出心形头像、波浪线等效果。
