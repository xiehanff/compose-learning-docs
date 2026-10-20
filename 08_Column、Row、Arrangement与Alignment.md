# 第 8 章：Column、Row、Arrangement 与 Alignment

先看两个真实问题：

1. 一行里放「左标题、右按钮」，想让两端贴边、中间拉开，结果两个元素总是挤在一起。
2. 同样的 `Arrangement.SpaceBetween`，在有的页面把元素分得很开，在另一个页面却毫无效果。

这两个问题的根源都是：没有先分清容器的主轴与交叉轴。`Arrangement` 只管主轴方向怎么排，`Alignment` 只管交叉轴方向怎么对齐；而「拉开」「分散」这类效果都依赖主轴上**有剩余空间**可分配。

## 1. 本章目标

掌握 Compose 两种最常用的线性布局，并从主轴、交叉轴两个方向理解排列和对齐。

学完后，你应当能够：

- 知道 Column 与 Row 的排列方向
- 区分 Arrangement 和 Alignment
- 看懂 spacedBy、CenterVertically、CenterHorizontally
- 理解子项级 Modifier.align 与容器级对齐
- 解释有剩余空间与无剩余空间时 Arrangement 的结果为什么不同

## 2. 项目中的位置

- `components/ColumnRowDemo.kt`
- `ui/home/HomeScreen.kt`



## 3. 项目源码片段

<!-- compile -->
```kotlin
Column(
    verticalArrangement = Arrangement.spacedBy(12.px),
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("左")
        Text("右")
    }
}
```

（原 Demo 使用项目自定义 `.px` 单位；标准工程写 `.dp`，含义相同。）

## 4. 逐步解释

### 4.1 先定主轴：Column 与 Row 的参数对照

`Column` 的主轴是垂直方向，所以主轴参数叫 `verticalArrangement`；交叉轴是水平方向，所以使用 `horizontalAlignment`。`Row` 正好相反：主轴是水平，使用 `horizontalArrangement`；交叉轴是垂直，使用 `verticalAlignment`。

| 容器 | 排列方向 | 主轴（Arrangement） | 交叉轴（Alignment） |
| --- | --- | --- | --- |
| Column | 从上到下 | 垂直：`verticalArrangement` | 水平：`horizontalAlignment` |
| Row | 从左到右 | 水平：`horizontalArrangement` | 垂直：`verticalAlignment` |

记忆方法：参数名跟**方向**走，不跟容器走——管垂直方向的参数，名字里一定带 vertical；管水平方向的参数，名字里一定带 horizontal。`Arrangement.spacedBy(12.px)` 只负责相邻子项间距，不等于容器外侧 padding。`Alignment.CenterVertically` 则让 Row 中不同高度的子项沿垂直方向居中。

### 4.2 演示：同一组三个色块，四种主轴排列

同一组三个 40dp 色块，只换 `Row` 的 `horizontalArrangement`。`Row` 都用 `fillMaxWidth()` 占满整行，方便看到剩余空间的分配（容器铺一层浅灰是为了看清 Row 的边界；`background` 的完整用法见第 15 章）。

<!-- compile -->
```kotlin
Column(
    modifier = Modifier.fillMaxWidth().padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
) {
    Text("Start（默认）：从行首依次排开，剩余空间留右边")
    Row(
        modifier = Modifier.fillMaxWidth().background(Color(0xFFEEEEEE)),
        horizontalArrangement = Arrangement.Start,
    ) {
        Box(Modifier.size(40.dp).background(Color(0xFFE57373)))
        Box(Modifier.size(40.dp).background(Color(0xFF64B5F6)))
        Box(Modifier.size(40.dp).background(Color(0xFF81C784)))
    }

    Text("Center：三个块整体居中，两侧各留一半")
    Row(
        modifier = Modifier.fillMaxWidth().background(Color(0xFFEEEEEE)),
        horizontalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(40.dp).background(Color(0xFFE57373)))
        Box(Modifier.size(40.dp).background(Color(0xFF64B5F6)))
        Box(Modifier.size(40.dp).background(Color(0xFF81C784)))
    }

    Text("SpaceBetween：剩余空间平均分进两个间隙，首尾贴边")
    Row(
        modifier = Modifier.fillMaxWidth().background(Color(0xFFEEEEEE)),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(Modifier.size(40.dp).background(Color(0xFFE57373)))
        Box(Modifier.size(40.dp).background(Color(0xFF64B5F6)))
        Box(Modifier.size(40.dp).background(Color(0xFF81C784)))
    }

    Text("spacedBy(24.dp)：间隙固定 24dp，剩余空间留右边")
    Row(
        modifier = Modifier.fillMaxWidth().background(Color(0xFFEEEEEE)),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Box(Modifier.size(40.dp).background(Color(0xFFE57373)))
        Box(Modifier.size(40.dp).background(Color(0xFF64B5F6)))
        Box(Modifier.size(40.dp).background(Color(0xFF81C784)))
    }
}
```

初始界面：四组标签加色块行，从上到下对应四种排列。预期观察：

| Arrangement | 三个色块的位置 |
| --- | --- |
| Start | 紧靠左边缘排开，右侧一整块空白 |
| Center | 整体在行中央，左右空白相等 |
| SpaceBetween | 红、绿分别贴左右边缘，蓝色在正中 |
| spacedBy(24.dp) | 三块都从左侧排开，间隙精确 24dp |

Start / Center / SpaceBetween 分配的都是**剩余空间**（行宽减去三个 40dp），spacedBy 则不看剩余空间，间隙永远是给定值。典型应用：`SpaceBetween` 做「标题左、按钮右」，`spacedBy` 做等距列表。

### 4.3 Arrangement 管主轴，Alignment 管交叉轴，子项可用 align 覆盖

`Arrangement` 决定子项沿主轴怎么排；`Alignment` 决定子项在交叉轴上怎么对齐。容器级参数对全部子项生效，单个子项可以用作用域里的 `Modifier.align` 单独覆盖（这个作用域修饰符和第 7 章说过的 `weight` 一样，只在容器内容 lambda 里可用）。

<!-- compile -->
```kotlin
Column(
    modifier = Modifier.fillMaxWidth().padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
) {
    // Row 高 60dp，三个子项高矮不一
    Row(
        modifier = Modifier.fillMaxWidth().height(60.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top, // 容器默认：所有子项沿交叉轴顶对齐
    ) {
        // 矮块：跟随容器默认，贴 Row 顶部
        Box(Modifier.size(24.dp).background(Color(0xFFE57373)))
        // 高块单独覆盖：在 60dp 行高内垂直居中
        Box(
            Modifier
                .size(40.dp)
                .background(Color(0xFF64B5F6))
                .align(Alignment.CenterVertically),
        )
        // 矮块单独覆盖：贴 Row 底部
        Box(
            Modifier
                .size(24.dp)
                .background(Color(0xFF81C784))
                .align(Alignment.Bottom),
        )
    }

    // Column 的交叉轴是水平方向：horizontalAlignment 管整体
    Column(
        modifier = Modifier.fillMaxWidth().height(100.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.Start, // 默认靠左
    ) {
        Text("跟随容器默认，靠左")
        Text("我单独靠右", Modifier.align(Alignment.End)) // 单个子项覆盖为靠右
    }
}
```

预期观察：第一行 60dp 高的行里，红色小方块贴顶、蓝色大块垂直居中、绿色小方块贴底；第二组两行文字，第一行靠左，第二行单独靠右。注意类型是对应好的：Row 内容里 `align` 接收 `Alignment.Vertical`（Top / CenterVertically / Bottom），Column 内容里 `align` 接收 `Alignment.Horizontal`（Start / CenterHorizontally / End）——因为它们分别只在垂直、水平这条轴上定位。

### 4.4 有剩余空间，Arrangement 才有发挥空间

`SpaceBetween`、`Center`、`SpaceAround` 这类排列本质是「把剩余空间按规则分掉」。**没有剩余空间时，arrangement 无从分配**，效果自然看不出来。

<!-- compile -->
```kotlin
Column(
    modifier = Modifier.fillMaxWidth().padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
) {
    Text("宽度充足：SpaceBetween 把剩余空间分进两个间隙")
    Row(
        modifier = Modifier.fillMaxWidth().background(Color(0xFFEEEEEE)),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(Modifier.size(40.dp).background(Color(0xFFE57373)))
        Box(Modifier.size(40.dp).background(Color(0xFF64B5F6)))
        Box(Modifier.size(40.dp).background(Color(0xFF81C784)))
    }

    Text("宽度刚好 120dp（3 × 40dp）：没有剩余空间，SpaceBetween 与 Start 看不出差别")
    Row(
        modifier = Modifier.width(120.dp).background(Color(0xFFEEEEEE)),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(Modifier.size(40.dp).background(Color(0xFFE57373)))
        Box(Modifier.size(40.dp).background(Color(0xFF64B5F6)))
        Box(Modifier.size(40.dp).background(Color(0xFF81C784)))
    }
}
```

预期观察：第一行的三个色块被拉开到两端和中间；第二行容器只有 120dp，三个 40dp 色块正好塞满，`SpaceBetween` 想分的剩余空间是 0，于是三个块紧挨着排，看起来和 `Start` 完全一样。两点补充：

- `spacedBy` 不属于「分配剩余空间」：不管放不放得下，它都会插入固定间距，放不下时子项照样溢出（第 8A 章会用 FlowRow 解决换行）。
- 常见的「SpaceBetween 失效」还有另一种原因：`Row` 没有 `fillMaxWidth()`，自己收缩到刚好包住子项，剩余空间同样是 0。

## 5. 实际执行过程

1. 父容器获得尺寸约束
2. 容器测量各个子项
3. 根据主轴 Arrangement 计算子项位置和间距
4. 根据交叉轴 Alignment 计算对齐位置
5. 按计算结果放置子项

（教学简化：第 2 步里非 weight 子项在主轴方向按不设上限的约束测量，所以子项多、空间不够时容器不会自动压缩它们，而是溢出；weight 子项才会按比例分掉主轴空间，第 14 章详解。）

## 6. Flutter / Dart 3 对照

Flutter 的 Column/Row 概念几乎一致：Compose 的 Arrangement 对应 MainAxisAlignment，Alignment 对应 CrossAxisAlignment。但 Compose 参数名会根据 Row/Column 的实际方向变化。

```dart
Column(
  children: [
    Row(
      mainAxisAlignment: MainAxisAlignment.spaceBetween,
      crossAxisAlignment: CrossAxisAlignment.center,
      children: const [Text('左'), Text('右')],
    ),
  ],
);
```

两处叫法差异值得注意：Flutter 的参数名不随容器方向变化（Row 和 Column 都叫 `mainAxisAlignment` / `crossAxisAlignment`），Compose 的参数名带方向（`horizontalArrangement` / `verticalAlignment` 等）；Flutter 的「主/交叉」由容器隐式决定，Compose 直接把方向写进名字。另外，Compose 的 `Arrangement.spacedBy(...)`、`SpaceAround` 等与 Flutter 的 `gap`、`MainAxisAlignment.spaceAround` 概念相近但能力集不完全相同，不要当成一一映射。

## 7. 常见错误

- 把 Row 的 verticalAlignment 理解为主轴排列
- 用 spacedBy 期待产生容器四周边距
- 在无限主轴空间中使用 SpaceBetween，却看不到明显效果
- 混淆容器整体对齐和单个子项的 align
- Row 忘记 fillMaxWidth，剩余空间为 0，再怎么换 Arrangement 也不生效

错误示例（故意错误，不参与编译）：

```kotlin
// Column 没有 verticalAlignment 参数：垂直是 Column 的主轴，
// 主轴用 verticalArrangement，交叉轴参数叫 horizontalAlignment
Column(verticalAlignment = Alignment.CenterVertically) { // 编译报错：无此参数
    Text("想垂直居中？应该写 verticalArrangement = Arrangement.Center")
}
```

## 8. 本章检查题

1. Column 的主轴是哪一个方向？
2. Row 中如何让三个子项间隔固定 8dp？
3. 为什么 SpaceBetween 必须有剩余空间才有明显效果？
4. 预测：Row 宽 300dp，三个子项各 100dp，`horizontalArrangement = Arrangement.SpaceEvenly`（空隙均匀分布在所有元素之间和两端）会是什么效果？
5. 动手：把 4.2 节演示中的 `SpaceBetween` 改成 `SpaceAround` 和 `SpaceEvenly`，先预测三个色块的位置差异，再运行验证；然后把第二行的窄容器从 120dp 改成 130dp，观察剩余空间只有 10dp 时三个块怎么排。

答案：

1. 垂直方向，因此主轴参数叫 verticalArrangement；交叉轴是水平方向。
2. 使用 Arrangement.spacedBy(8.dp)（注意它只负责相邻子项间距，不等于容器外侧 padding）。
3. SpaceBetween 把剩余空间分配到子项之间；没有剩余空间时无处可分配，效果就不明显。
4. 没有剩余空间（300 - 3 × 100 = 0），SpaceEvenly 的空隙无处安放，三个子项紧挨着从行首排开，与 Start、SpaceBetween 在同条件下的外观一致。
5. SpaceAround：剩余空间分到每个子项两侧，视觉上是「左右两端留一半间隙、块间间隙是端点两倍」；SpaceEvenly：所有间隙（含两端）相等。窄容器 130dp 时剩余 10dp，SpaceEvenly 会把 10dp 分成等份插入，间隙很小但仍可辨认；换成 Start 则间隙完全消失。

## 9. 本章总结

先确定容器的主轴，再选择 Arrangement；交叉轴使用 Alignment；单个子项的例外用作用域 `Modifier.align` 覆盖。Column 垂直排列，Row 水平排列，这是后续 weight、Spacer 和滚动布局的基础。记住两条边界：`spacedBy` 只管间隙不管剩余空间；`SpaceBetween` 这类排列依赖剩余空间，没有剩余空间就没有效果。

官方参考：[Compose 布局基础](https://developer.android.com/develop/ui/compose/layouts/basics)、[Arrangement API 参考](https://developer.android.com/reference/kotlin/androidx/compose/foundation/layout/Arrangement)、[Alignment API 参考](https://developer.android.com/reference/kotlin/androidx/compose/ui/Alignment)。
