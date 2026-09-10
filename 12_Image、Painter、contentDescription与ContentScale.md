# 第 12 章：Image、Painter、contentDescription 与 ContentScale

## 1. 本章目标

理解 Compose Image 的图像来源、无障碍描述（无障碍：让 TalkBack 等辅助工具能正确朗读并操作界面的能力，第 80 章详解）和缩放裁剪策略，并与 Flutter Image 做准确对照。

学完后，你应当能够：

- 知道 Image 可接收 painter 或 bitmap
- 理解 contentDescription 的无障碍意义
- 区分 ContentScale.Crop 与 Fit
- 知道网络图片需要 Coil 等加载库

## 2. 项目中的位置

- `components/ImageDemo.kt`



## 3. 项目源码片段

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

## 4. 逐步解释

项目先用 Android Bitmap 绘图（示例图是「三色渐变」：颜色沿某个方向平滑过渡的填充效果，属于绘制能力，第 60 章详解），再调用 `asImageBitmap()` 转成 Compose `ImageBitmap`。`Image` 的 bitmap 重载直接显示该内存图像；资源图片常用 `painterResource`，网络图片通常使用 Coil 的 AsyncImage。

`contentDescription` 提供给 TalkBack 等辅助技术。如果图片只是纯装饰、朗读没有价值，可以明确传 null；如果承载信息，应写有意义的描述。`ContentScale.Crop` 等比放大直到填满目标区域，并裁掉溢出部分；`Fit` 保证整张图可见，目标区域可能留空。圆角不是 Image 参数，而由 Modifier.clip 提供。

## 5. 实际执行过程

1. 准备 ImageBitmap 或 Painter
2. Image 根据目标尺寸和 ContentScale 计算缩放
3. clip 节点限制最终绘制区域
4. 内容被绘制
5. 无障碍系统读取 contentDescription

## 6. Flutter / Dart 3 对照

Flutter 内置 Image.asset、Image.network、Image.memory；Compose 核心 Image 主要负责显示，不负责网络请求。ContentScale.Crop 对应 BoxFit.cover，Fit 对应 BoxFit.contain，clip 对应 ClipRRect。

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

- 所有图片都把 contentDescription 写成空字符串；装饰图应为 null，信息图应有意义
- 认为 ContentScale.Crop 会改变原始图片文件
- 把 clip 写在 background 前后却不检查裁剪范围
- 期待 Image 自带网络缓存和请求生命周期

## 8. 本章检查题

1. Crop 与 Fit 的核心差异是什么？
2. 什么情况下 contentDescription 应为 null？
3. Compose 中显示网络图为什么通常引入 Coil？

答案：

1. Crop 等比放大直到填满目标区域并裁掉溢出部分；Fit 保证整张图完整可见，目标区域可能留白。
2. 图片只是纯装饰、朗读没有价值时；承载信息的图片应写有意义的描述。
3. 网络图片需要异步加载、缓存和生命周期管理，Coil 的 AsyncImage 封装了这些能力，不需要手写下载与解码。

## 9. 本章总结

Image 负责渲染已有图像；图像加载、裁剪形状、尺寸和无障碍分别由不同能力处理。理解 ContentScale 是避免图片拉伸和错误裁剪的关键。
