# 第 12 章：Image、Painter、contentDescription 与 ContentScale

## 1. 本章目标

先看一个真实问题：同一张头图放进同一个 120dp 方形框，同事的画面满框没有白边，你的四周留了一圈空白；再换一张宽图，他的上下被裁掉一截。差异只在 `contentScale` 一个参数。另一个问题更隐蔽：应用过不了无障碍评审——每个 Image 的 `contentDescription` 都写着空字符串，TalkBack 逐个聚焦却读不出任何内容。

理解 Compose Image 的图像来源、无障碍描述（无障碍：让 TalkBack 等辅助工具能正确朗读并操作界面的能力，第 80 章详解）和缩放裁剪策略，并与 Flutter Image 做准确对照。

学完后，你应当能够：

- 知道 Image 可接收 painter 或 bitmap，会用 painterResource 加载 drawable 资源
- 理解 contentDescription 的无障碍意义：信息图给描述、装饰图给 null
- 区分 ContentScale.Crop 与 Fit，并能预测同一图同一框下的裁剪结果
- 认识 ContentScale 的其他常用值
- 知道网络图片需要 Coil 等加载库，以及为什么本仓库不引入它

## 2. 项目中的位置

- `components/ImageDemo.kt`



## 3. 项目源码片段

<!-- compile
prelude: val imageBitmap: ImageBitmap = ImageBitmap(4, 4)
-->
```kotlin
Image(
    bitmap = imageBitmap,
    contentDescription = "示例图片（三色渐变）",
    contentScale = ContentScale.Crop,
    modifier = Modifier
        .fillMaxWidth()
        .height(208.px)
        .clip(RoundedCornerShape(12.px)),
)
```

原项目源码用的是 `.px` 设计稿单位（第 43、44 章），编译校验环境用 snippets 支撑代码里的同名扩展保证类型一致。示例图是项目用 Android Bitmap 画出的「三色渐变」内存位图（颜色沿某个方向平滑过渡的填充效果，属于绘制能力，第 60 章详解），经 `asImageBitmap()` 转成 Compose `ImageBitmap` 后交给 Image。

### 3.1 独立示例：同一张图、同一个框，Fit 与 Crop 对照

参与编译校验。编译校验环境没有 drawable 资源，这里用一个宽 40、高 80 的空 `ImageBitmap` 占位演示 API 形态（空位图画出来是透明的）；真实裁剪效果需自备图片，见 4.1 节末尾的资源版示例。初始界面：左右两个 120dp 见方、浅灰底、细边框的图框。

<!-- compile -->
```kotlin
// 模拟一张宽 40、高 80（宽高比 1:2）的源图
val placeholder = ImageBitmap(width = 40, height = 80)

Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
    Image(
        bitmap = placeholder,
        contentDescription = "Fit 示例：整图可见",
        contentScale = ContentScale.Fit, // Image 的默认值
        modifier = Modifier
            .size(120.dp)
            .background(Color.LightGray) // 浅灰底用来观察“留白”
            .border(1.dp, Color.Gray),
    )
    Image(
        bitmap = placeholder,
        contentDescription = "Crop 示例：填满裁边",
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .size(120.dp)
            .background(Color.LightGray)
            .border(1.dp, Color.Gray),
    )
}
```

用真实图片时的预期观察（源图比例 1:2，图框 1:1）：左框 Fit——整张图完整可见，等比缩放到宽 60、高 120，左右各留 30dp 浅灰底；右框 Crop——等比放大到宽 120、高 240，上下各裁掉 60dp，无留白。同一张图同一个框：Fit 留白、Crop 裁边。

## 4. 逐步解释

### 4.1 图像来源：bitmap、painter 与 painterResource

项目先用 Android Bitmap 绘图（示例图是「三色渐变」：颜色沿某个方向平滑过渡的填充效果，属于绘制能力，第 60 章详解），再调用 `asImageBitmap()` 转成 Compose `ImageBitmap`。`Image` 的 bitmap 重载直接显示该内存图像；资源图片常用 `painterResource`，网络图片通常使用 Coil 的 AsyncImage。

补充机制：Image 有两个常用重载——`Image(bitmap: ImageBitmap, ...)` 直接显示内存位图；`Image(painter: Painter, ...)` 显示任何"知道怎么把自己画出来"的对象。`Painter` 是绘制抽象：矢量图返回 `VectorPainter`（按路径逐条绘制，放大不失真），PNG/JPG/WebP 位图返回 `BitmapPainter`。调用方拿到的都是 `Painter`，不必关心差异。

首次出现的 Kotlin 语法：`R.drawable.ic_avatar`。R 是 Android 构建工具为 `res/` 目录生成的资源索引类，每个资源对应一个 Int 常量：drawable 下的资源通过 `R.drawable.名称` 访问，字符串通过 `R.string.名称` 访问。`painterResource(R.drawable.xxx)` 就是拿这个 Int 去资源系统取图。

资源版示例（需自备资源，不参与编译校验）：在 `res/drawable` 下放一张 `ic_avatar.xml`（矢量）或 `avatar.png`（位图）：

```kotlin
Image(
    painter = painterResource(R.drawable.ic_avatar),
    contentDescription = "用户头像",
    contentScale = ContentScale.Crop,
    modifier = Modifier
        .size(48.dp)
        .clip(CircleShape), // 圆形头像：clip 成圆形，机制见第 15 章
)
```

### 4.2 ContentScale：同一图同一框的缩放策略

ContentScale 决定"源图尺寸"和"目标框尺寸"比例不一致时怎么办。拿 3.1 的数字算一遍（源图 40×80，框 120×120）：

- `Fit`（默认）：等比缩放到完全放得下，系数取 min(120/40, 120/80) = 1.5，成 60×120，整图可见、左右留白。
- `Crop`：等比缩放到完全盖住，系数取 max = 3，成 120×240，上下各裁 60dp，无留白。
- `FillBounds`：宽高各自独立拉到 120×120，不留白也不裁边，但宽高比被破坏（人脸会变形），慎用。
- `FillWidth` / `FillHeight`：只保证一个方向撑满，另一方向等比，可能留白或被裁。
- `Inside`：只有当源图大于框时才等比缩小；源图本来就不大于框时按原大小摆放，不放大。
- `None`：不缩放，按源图像素大小绘制，装不下的部分交给布局与裁剪。

以上枚举集合以本仓库基线的 Compose 版本为准。"Crop 会改变原始图片文件"是错觉——它只影响本次绘制的采样区域，不动源数据。

### 4.3 contentDescription：无障碍语义，不是备注

`contentDescription` 提供给 TalkBack 等辅助技术。判断标准只有一条：图片承载信息吗？承载信息（头像、商品图、截图、图表）写有意义的描述——"用户头像"可以，"图片"这种没有信息量的不算；纯装饰（背景纹理、分隔装饰、配合文字重复表意的插图）明确传 null，让 TalkBack 直接跳过，减少朗读噪音。

`Image` 的 contentDescription 参数默认值就是 null；最差的写法是空字符串 `""`——TalkBack 会在图片上聚焦却读不出任何内容，比 null 更糟。语义树与无障碍测试在第 80 章展开。

### 4.4 网络图片：Coil 的依赖与边界

Compose 核心 Image 只负责"显示已有图像"，不负责下载、内存/磁盘缓存、请求取消与生命周期。网络图通常引入第三方加载库，Android 生态最常用的是 Coil（Kotlin 与协程实现），其 Compose 集成提供 `AsyncImage`：

```kotlin
// 依赖与边界示意（不参与编译校验；本仓库 snippets 工程不引入任何图片库）
// Coil 2.x 坐标：io.coil-kt:coil-compose
// Coil 3.x 坐标：io.coil-kt.coil3:coil-compose（以 Coil 官方文档为准）
AsyncImage(
    model = "https://example.com/avatar.png",
    contentDescription = "用户头像",
    contentScale = ContentScale.Crop,
    modifier = Modifier.size(48.dp),
)
```

版本边界：Coil 2 与 Coil 3 的包名、坐标与部分参数不同，以所用版本的官方文档为准。本仓库是学习文档与片段编译工程，不引入图片库依赖；真实工程选 Coil、Glide 的 Compose 支持还是别的方案，属于那个工程自己的技术选型，本章不下结论。

错误示例（故意错误，不参与编译）：核心 Image 没有接收 URL 字符串的重载，`Image("https://example.com/a.png")` 编译不过——网络图像必须先变成 Painter/ImageBitmap 再交给 Image，或直接使用加载库组件。

## 5. 实际执行过程

1. 准备 ImageBitmap 或 Painter（资源经 painterResource，内存位图直接构造或经 asImageBitmap() 转换）
2. Image 根据目标尺寸和 ContentScale 计算缩放：Fit 取"完全放得下"的系数，Crop 取"完全盖住"的系数
3. 布局约束决定目标尺寸（size、fillMaxWidth 等，第 13 章）
4. clip 节点限制最终绘制区域（第 15 章）
5. 内容被绘制，超出裁剪区的部分不显示
6. 无障碍系统读取 contentDescription：null 跳过，非 null 朗读

## 6. Flutter / Dart 3 对照

Flutter 内置 Image.asset、Image.network、Image.memory；Compose 核心 Image 主要负责显示，不负责网络请求。ContentScale.Crop 对应 BoxFit.cover，Fit 对应 BoxFit.contain，clip 对应 ClipRRect。

| Compose | Flutter |
| --- | --- |
| ContentScale.Fit | BoxFit.contain |
| ContentScale.Crop | BoxFit.cover |
| ContentScale.FillBounds | BoxFit.fill |
| ContentScale.FillWidth / FillHeight | BoxFit.fitWidth / fitHeight |
| ContentScale.Inside | BoxFit.scaleDown（都"只缩不放"） |
| ContentScale.None | BoxFit.none |
| contentDescription | Image 的 semanticLabel |
| Modifier.clip(RoundedCornerShape(...)) | ClipRRect |

不等价处（概念对照，不是机制等价）：一，Flutter 的 `Image.network` 内置网络加载与缓存，Compose 核心 Image 完全不碰网络——这是两个框架分工上最大的差异；二，无障碍默认行为不同——Flutter 的图片在 semanticLabel 为 null 时仍会在语义树保留节点，Compose 的 contentDescription 为 null 时 Image 不输出文本描述，TalkBack 通常直接跳过；三，圆角裁剪在 Flutter 是包装组件（ClipRRect 会新增一层），在 Compose 是 Modifier 链上的 clip 节点。

```dart
ClipRRect(
  borderRadius: BorderRadius.circular(12),
  child: Image.memory(
    bytes,
    width: double.infinity,
    height: 208,
    fit: BoxFit.cover,
    semanticLabel: '示例图片（三色渐变）',
  ),
);
```

## 7. 常见错误

- 所有图片都把 contentDescription 写成空字符串；装饰图应为 null，信息图应有意义（4.3）
- 认为 ContentScale.Crop 会改变原始图片文件；它只影响本次绘制
- 把 clip 写在 background 前后却不检查裁剪范围（第 7、15 章）
- 期待 Image 自带网络缓存和请求生命周期；核心 Image 只显示已有图像（4.4）
- 给所有图统一用 FillBounds，把人像拉变形；宽高比敏感的内容用 Fit/Crop
- 给装饰图认认真真写了"装饰图片"之类的描述：TalkBack 会逐个朗读这些噪音，应传 null

## 8. 本章检查题

1. Crop 与 Fit 的核心差异是什么？
2. 什么情况下 contentDescription 应为 null？
3. Compose 中显示网络图为什么通常引入 Coil？
4. 预测：源图 400×100（宽高比 4:1）放入 100×100 的框，Fit 和 Crop 各是什么结果？
5. 预测：一张 20×20 的小图标放进 100×100 的框，Inside、None、Crop 各显示多大？
6. 动手：自备一张宽高比明显的图片，把 3.1 示例的两个 Image 依次改成 FillBounds、FillWidth、Inside，观察同一图同一框下四种值的结果，再换一张更宽的图验证你的预测。

答案：

1. Crop 等比放大直到填满目标区域并裁掉溢出部分；Fit 保证整张图完整可见，目标区域可能留白。
2. 图片只是纯装饰、朗读没有价值时；承载信息的图片应写有意义的描述。
3. 网络图片需要异步加载、缓存和生命周期管理，Coil 的 AsyncImage 封装了这些能力，不需要手写下载与解码；核心 Image 不负责网络请求。
4. Fit：等比系数取 min(100/400, 100/100) = 0.25，缩到 100×25 居中，上下各留约 37.5dp；Crop：系数取 max = 1，成 400×100，左右各裁 150dp。
5. Inside 与 None 都不放大小图：20×20 不大于框，按原大小 20×20 摆放；Crop 系数取 max(100/20, 100/20) = 5，放大成 100×100 盖满整个框。
6. 预期：FillBounds 把图强行拉成 100×100（宽高比被破坏）；FillWidth 宽度撑满、高度等比；Inside 居中显示原图大小。换更宽的图后，Fit 的留白位置与 Crop 的裁边位置随之变化，FillBounds 变形加剧。

## 9. 本章总结

Image 负责渲染已有图像；图像来源（bitmap / painter / painterResource）、网络加载、裁剪形状、缩放策略（ContentScale）和无障碍（contentDescription）是五件分开的事。记住 Fit 取"放得下"的系数、Crop 取"盖得住"的系数，就能预测任意图配任意框的结果；网络加载库是真实工程的独立选型，本仓库不引入。

官方参考：

- [Work with images（Compose 图像指南）](https://developer.android.com/develop/ui/compose/graphics/images)
- [ContentScale API 参考](https://developer.android.com/reference/kotlin/androidx/compose/ui/layout/ContentScale)
- [Compose 无障碍](https://developer.android.com/develop/ui/compose/accessibility)
- [Coil 官方仓库](https://github.com/coil-kt/coil)
