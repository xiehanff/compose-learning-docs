# 第 30 章：CircularProgressIndicator 与 LinearProgressIndicator

## 1. 本章目标

区分确定进度和不确定进度，并把业务进度作为状态传给指示器。

学完后，你应当能够：

- 使用圆形和线性指示器
- 区分无 progress 与有 progress
- 理解 0f..1f 的比例
- 知道指示器不负责执行任务

## 2. 项目中的位置

- `components/ProgressIndicatorDemo.kt`



## 3. 项目源码片段

```kotlin
CircularProgressIndicator()
LinearProgressIndicator(Modifier.fillMaxWidth())

LinearProgressIndicator(
    progress = progress.coerceIn(0f, 1f),
    modifier = Modifier.fillMaxWidth(),
)
```

## 4. 逐步解释

不传 progress 时是循环动画，表示任务正在进行但不知道比例；传 0f..1f 时是确定进度，例如 0.35f 表示 35%。指示器只读取状态并绘制，不会自动下载、计时或修改 progress。

业务层应产生进度 State，UI 订阅并传入。超出范围的值应限制。完成后应切换到成功内容，失败时显示错误和重试，而不是永远停留在 loading。

## 5. 实际执行过程

1. 任务产生进度
2. State 更新
3. 读取进度的 UI 重组
4. Indicator 按新值绘制
5. 完成或失败后切换页面状态

## 6. Flutter / Dart 3 对照

Flutter 的 CircularProgressIndicator/LinearProgressIndicator 语义一致：value 为 null 是不确定模式，0..1 是确定模式。

```dart
const CircularProgressIndicator();
LinearProgressIndicator(value: progress.clamp(0.0, 1.0));
```

## 7. 常见错误

- 把 35 当作 35%，实际应传 0.35
- 指示器内部执行耗时任务
- 没有失败和重试状态
- 任务完成后仍显示 loading

## 8. 本章检查题

1. 0.6f 表示多少百分比？
2. 不传 progress 表示什么？
3. 下载逻辑应放在 Indicator 中吗？

## 9. 本章总结

进度组件只负责显示。是否确定、当前比例、成功或失败都应来自外部业务状态。
