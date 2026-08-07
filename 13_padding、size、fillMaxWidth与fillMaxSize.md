# 第 13 章：padding、size、fillMaxWidth 与 fillMaxSize

## 1. 本章目标

理解常用尺寸 Modifier 如何响应父布局约束，并区分内容内边距、组件尺寸和填充可用空间。

学完后，你应当能够：

- 分清 padding 与 contentPadding
- 理解 size、width、height 的约束含义
- 理解 fillMaxWidth/fillMaxSize 是填充父级允许的最大空间
- 避免在无限约束方向滥用 fillMaxSize

## 2. 项目中的位置

- `ui/home/HomeScreen.kt`
- `components/LazyColumnDemo.kt`
- `components/ImageDemo.kt`



## 3. 项目源码片段

```kotlin
LazyColumn(
    modifier = Modifier
        .fillMaxSize()
        .padding(padding),
    contentPadding = PaddingValues(16.px),
)

Box(
    Modifier
        .size(32.px)
        .background(Color.Blue)
)
```

## 4. 逐步解释

`size(32.dp)` 同时提出宽高 32dp 的尺寸要求，但最终仍受父约束限制。`fillMaxWidth()` 表示在父级允许的宽度范围内取最大值，不是设备屏幕宽度的绝对同义词；放在窄父容器中只会填满该父容器。

`Modifier.padding` 改变当前节点测量内容时使用的空间，可理解为在节点布局链中增加内边距。LazyColumn 的 `contentPadding` 则属于列表内容模型：首条和末条会保留滚动范围中的边距。两者视觉有时相似，但滚动和背景行为不同。

## 5. 实际执行过程

1. 父节点向子节点传递最小/最大约束
2. fill 修饰符把目标尺寸设到允许的最大值
3. size/width/height进一步收紧或提出期望尺寸
4. padding 为内容扣除空间
5. 子内容在剩余约束中测量

## 6. Flutter / Dart 3 对照

Flutter 的 double.infinity、SizedBox、Padding 与这些能力相似，但 Compose 更强调父约束。fillMaxWidth 类似 `width: double.infinity`，前提都是父级给出有限最大宽度。

```dart
Padding(
  padding: const EdgeInsets.all(16),
  child: SizedBox(
    width: double.infinity,
    height: 32,
    child: ColoredBox(color: Colors.blue),
  ),
);
```

## 7. 常见错误

- 认为 fillMaxWidth 一定等于手机屏幕宽
- 给垂直滚动 Column 使用 fillMaxSize 后不理解其测量结果
- 把 LazyColumn contentPadding 和外层 padding 当成完全相同
- Modifier 顺序不同却期待尺寸和背景范围相同

## 8. 本章检查题

1. fillMaxWidth 填满的是谁提供的宽度？
2. 列表首尾留白优先使用 contentPadding 还是外层 padding？
3. size(32.dp) 在父级最大只能给 20dp 时最终多大？

答案：

1. 父级约束允许的最大宽度，不是设备屏幕宽度的绝对同义词；放在窄父容器中只会填满该父容器。
2. contentPadding。它属于列表内容模型，首条和末条在滚动范围中保留边距；外层 padding 在滚动与背景行为上不同。
3. 20dp。size 只是尺寸要求，最终受父级约束限制。

## 9. 本章总结

Compose 尺寸始终在父约束中协商。fill 表达“使用允许的最大空间”，size 表达尺寸要求，padding 影响内容可用空间，列表 contentPadding 则影响可滚动内容范围。
