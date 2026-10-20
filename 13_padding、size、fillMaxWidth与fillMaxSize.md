# 第 13 章：padding、size、fillMaxWidth 与 fillMaxSize

## 1. 本章目标

先看一个真实问题：同一个 Box，写 `Modifier.size(100.dp).padding(16.dp)`，里面的内容只剩 68dp；把两个修饰符换个位置写 `padding(16.dp).size(100.dp)`，整盒反而变成 132dp。数字对不上，多数人以为是自己算错了——其实是约束（Constraints）在 Modifier 链上一层层传递，顺序就是语义。另一个高频坑：给可滚动的 Column 里塞 `fillMaxSize()`，期待撑满屏幕，实际只有内容那么高。

理解常用尺寸 Modifier 如何响应父布局约束，并区分内容内边距、组件尺寸和填充可用空间。

学完后，你应当能够：

- 分清 padding 与 contentPadding
- 理解 size、width、height 的约束含义
- 用约束传递链解释 padding 与 size 顺序互换后的尺寸差异
- 理解 fillMaxWidth/fillMaxSize 是填充父级允许的最大空间，不是屏幕宽
- 知道有界/无界约束的差异，预测 fillMaxSize 在可滚动容器里的行为
- 识别"表现与直觉不一致"的案例并解释原因

## 2. 项目中的位置

- `ui/home/HomeScreen.kt`
- `components/LazyColumnDemo.kt`
- `components/ImageDemo.kt`



## 3. 项目源码片段

<!-- compile
prelude: val padding = PaddingValues(16.dp)
-->
```kotlin
LazyColumn(
    modifier = Modifier
        .fillMaxSize()
        .padding(padding),
    contentPadding = PaddingValues(16.px),
) {
    items(3) { index ->
        Text("第 $index 项")
    }

    item {
        Text("最后一项") // contentPadding 会让首末项与滚动范围的边缘留出间距
    }
}

Box(
    Modifier
        .size(32.px)
        .background(Color.Blue)
)
```

项目节选（LazyColumn 的用法见第 16 章）：这段同时用了外层 `padding` 与列表 `contentPadding`，两者差异在 4.4 对比。原项目用 `.px` 设计稿单位（第 43、44 章）。

### 3.1 独立示例：顺序互换的尺寸账

参与编译校验。初始界面：一行两个盒子，左蓝右绿。

<!-- compile -->
```kotlin
Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
    // A：先 size 后 padding——整盒 100dp，内容区被压到 68dp
    Box(
        Modifier
            .size(100.dp)             // 把约束收成恰好 100×100
            .padding(16.dp)           // 内容约束减为 68×68
            .background(Color.Blue),  // 背景画在 padding 内侧：68dp 蓝块
    )
    // B：先 padding 后 size——内容仍是 100dp，整盒上报 132dp
    Box(
        Modifier
            .padding(16.dp)           // 约束上限先各收 16（父级 Row 足够宽时）
            .size(100.dp)             // 在缩水后的约束里要求恰好 100×100
            .background(Color.Green), // 背景画 100dp 绿块
    )
}
```

预期观察：左盒总宽 100dp，蓝色只有中间 68dp（四周各 16dp 透出父级背景）；右盒总宽 132dp，绿色是完整的 100dp（四周各 16dp 透出父级背景）。同样两个修饰符、同样数字，顺序不同：蓝色 68dp、绿色 100dp。

## 4. 逐步解释

### 4.1 约束传递链：每个尺寸节点都在改写约束

`size(32.dp)` 同时提出宽高 32dp 的尺寸要求，但最终仍受父约束限制。`fillMaxWidth()` 表示在父级允许的宽度范围内取最大值，不是设备屏幕宽度的绝对同义词；放在窄父容器中只会填满该父容器。

要解释顺序差异，需要理解约束怎么走：每个布局节点的测量分两步——父级发来一组约束（宽、高各自的 min/max），节点在约束内测量后向父级上报尺寸。Modifier 链上先写的在外层：链上每个尺寸类节点接收外侧传来的约束，按职责改写，再把新约束传给内侧。3.1 的数字账（假设父级给的宽度约束是 0..200，高同理）：

| 链条 | 每步约束（以宽为例） | 上报尺寸 | 内容区 |
| --- | --- | --- | --- |
| A：size(100).padding(16) | 0..200 → size 收成 100..100 → padding 减成 68..68 | 100dp | 68dp |
| B：padding(16).size(100) | 0..200 → padding 减成 0..168 → size 收成 100..100 | 132dp（100+16×2） | 100dp |

size 是"愿望"，父约束是天花板：父级宽度上限只有 120dp 时，B 的 size(100) 被压进 0..88 的约束（padding 先收掉 32），最终内容 88dp、整盒 120dp——这就是"size 只是尺寸要求，最终受父级约束限制"的机制版。

还有一个与直觉相反的例外：`requiredSize(100.dp)` 声明"无视父约束，必须 100dp"，测量结果可以越出父级边界（内容画出容器、可能与相邻元素重叠）。只在明确的特殊对齐需求下使用，日常布局优先 size。

### 4.2 fill：填的是约束的 maxWidth/maxHeight

`fillMaxWidth()` 的行为可以概括为"把宽度设为传入约束的 maxWidth"（fillMaxSize 则宽高都取 max），所以它填的是父级允许的最大空间，不是屏幕宽。fillMaxWidth 还能带比例参数：`fillMaxWidth(0.5f)` 填父级允许宽度的一半。

首次出现的 Kotlin 语法：`0.5f` 的后缀 f 表示 Float 字面量。fillMaxWidth 的比例参数类型是 Float，整数 1 不会自动被当作 Float 使用，想表达一半必须写 `0.5f`。

对照示例（参与编译校验）：

<!-- compile -->
```kotlin
Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    // 整行填充：宽度 = 父级允许的最大宽度
    Box(Modifier.fillMaxWidth().height(32.dp).background(Color(0xFF90CAF9)))
    // 一半填充：宽度 = 最大宽度的 0.5 倍
    Box(Modifier.fillMaxWidth(0.5f).height(32.dp).background(Color(0xFFA5D6A7)))
    // 组合：先 padding 再 fill，fill 填的是扣掉 padding 后的约束
    Box(Modifier.padding(16.dp).fillMaxWidth().height(32.dp).background(Color(0xFFCE93D8)))
}
```

预期观察：第一条占满整行；第二条占左半行；第三条宽度比整行少 32dp、左右各空 16dp（fill 填的是 padding 收缩后的约束，这也是"fill 的是约束范围"的直接证据）。

### 4.3 有界与无界：fillMaxSize 在滚动容器里的边界

约束的 max 可以是无穷大（"要多大有多大"），这叫无界方向。最常见来源：`verticalScroll` 的 Column——高度方向约束是 0..∞（内容想多高就多高，滚动兜底），宽度方向仍是有界的。

此时 `fillMaxSize()` 在高度方向"不知道该填多大"：fill 的实现要先读到有限的 maxWidth/maxHeight 才能把自己设成它，遇到无限值就不生效（这是自 Compose 1.0 起 fill 修饰符的实现行为）。结果就是"表现与直觉不一致"的经典案例：以为 fillMaxSize 会让色块撑满屏幕高，实际高度回落到内容自身大小——一个没有内容的 Box 高度是 0dp，什么都看不见；宽度方向不受影响，仍正常填充。

验证骨架（参与编译校验）。初始界面：一段 200dp 高、可垂直滚动的列。

<!-- compile -->
```kotlin
Column(
    modifier = Modifier
        .fillMaxWidth()
        .height(200.dp) // 限制演示区高度，让滚动真的发生
        .verticalScroll(rememberScrollState()), // 高度方向约束变为 0..∞（无界）
) {
    Text("向上滚动")
    // 宽度方向有界：fillMaxWidth 正常填满
    Box(Modifier.fillMaxWidth().height(80.dp).background(Color.Cyan))
    Text("下面这个 Box 如果写 fillMaxSize()：")
    // 高度方向无界：fill 在该方向退化为不生效，无内容的 Box 高度回落 0dp。
    // 这里先用显式 height 保证可见；请按下面的“操作”亲手换验。
    Box(Modifier.fillMaxWidth().height(80.dp).background(Color.Magenta))
    Text("末尾")
}
```

操作：把第二个 Box 的 `height(80.dp)` 换成 `fillMaxSize()`。预期观察：洋红色块消失（高度回落 0dp），青色块不受影响——这正是"给垂直滚动 Column 使用 fillMaxSize 后不理解其测量结果"的机制解释。想让滚动内容至少撑满一屏，用 `heightIn(min = ...)`，或让外层容器先占满、滚动放在内层（滚动容器见第 19、20 章）。

教学简化与边界：本节讲的是 fill 对"无限最大约束方向"的处理；Compose 不像 Flutter 那样对无界约束直接抛布局异常，而是让 fill 退化、内容回退（两框架差异见第 6 节）。weight 在无界主轴中的问题见第 14 章。

### 4.4 padding 与 contentPadding

`Modifier.padding` 改变当前节点测量内容时使用的空间，可理解为在节点布局链中增加内边距。LazyColumn 的 `contentPadding` 则属于列表内容模型：首条和末条会保留滚动范围中的边距。两者视觉有时相似，但滚动和背景行为不同。

差异的具体表现：外层 padding（如项目里的 `.fillMaxSize().padding(16.dp)`）把整个列表容器往里推，列表背景与滚动条也停在 padding 内侧；contentPadding 让首末项在滚动范围内留白，列表背景铺到边缘、滚动时首项从留白处滑入。"列表首尾留白"优先 contentPadding——它表达的是内容边界，不是容器缩进。

## 5. 实际执行过程

1. 父节点向子节点传递宽、高各自的 min/max 约束
2. Modifier 链从外到内逐个节点改写约束：fill 取 max、size 收紧、padding 扣减
3. 内容在最内侧的约束中测量，得到自身尺寸
4. 每个节点按职责向外层逐级上报（padding 节点把内容尺寸加回 padding 再上报）
5. fill 遇到无界 max 的方向不生效，子项尺寸回落到内容自身
6. place 阶段按测得尺寸摆放

## 6. Flutter / Dart 3 对照

Flutter 的 double.infinity、SizedBox、Padding 与这些能力相似，但 Compose 更强调父约束。fillMaxWidth 类似 `width: double.infinity`，前提都是父级给出有限最大宽度。

不等价处（概念对照，不是机制等价）：一，Flutter 遇到"无界父级里要无限尺寸"通常直接抛 RenderFlex 布局异常（例如把 Expanded 放进可滚动的 ListView），开发者立刻看到报错；Compose 对无界约束更宽容——fill 退化为不生效、子项按内容回退，界面"安静地"和预期不一样，需要靠理解约束模型来预测。二，形态不同：Flutter 的尺寸与留白是 Widget 嵌套（外层 Padding 在外），Compose 收敛为一条 Modifier 链，顺序语义对应、书写形态不同。

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

- 认为 fillMaxWidth 一定等于手机屏幕宽（4.1：填的是父级约束的 maxWidth）
- 给垂直滚动 Column 使用 fillMaxSize 后不理解其测量结果（4.3：无界方向 fill 退化）
- 把 LazyColumn contentPadding 和外层 padding 当成完全相同（4.4）
- Modifier 顺序不同却期待尺寸和背景范围相同（3.1、4.1）
- 用 requiredSize 表达普通尺寸，内容越出父级边界还以为是渲染 bug（4.1）
- 给 fill 系列传整数比例（`fillMaxWidth(1)`）：比例参数是 Float，要写 `1f`、`0.5f`

## 8. 本章检查题

1. fillMaxWidth 填满的是谁提供的宽度？
2. 列表首尾留白优先使用 contentPadding 还是外层 padding？
3. size(32.dp) 在父级最大只能给 20dp 时最终多大？
4. 预测：父级宽度约束 0..150dp 时，`Modifier.padding(10.dp).size(100.dp)` 的整盒宽度与内容宽度各是多少？
5. 预测：`Column(Modifier.verticalScroll(rememberScrollState()))` 里放一个无内容的 `Box(Modifier.fillMaxSize())`，宽和高各是多少？
6. 动手：把 4.2 示例第三条改成 `.fillMaxWidth().height(32.dp).background(Color(0xFFCE93D8)).padding(16.dp)`（background 挪到 padding 之前），对比原写法观察色块覆盖范围；再给 3.1 示例 A 的 `size(100.dp)` 换成 `requiredSize(100.dp)` 并把整行套进 `Modifier.widthIn(max = 80.dp)` 的容器，观察内容是否越出容器。

答案：

1. 父级约束允许的最大宽度，不是设备屏幕宽度的绝对同义词；放在窄父容器中只会填满该父容器。
2. contentPadding。它属于列表内容模型，首条和末条在滚动范围中保留边距；外层 padding 在滚动与背景行为上不同。
3. 20dp。size 只是尺寸要求，最终受父级约束限制。
4. padding 先把约束收成 0..130，size(100) 在其中成立：内容 100dp，整盒 100 + 10×2 = 120dp。
5. 宽度方向有界：等于列允许的最大宽度；高度方向无界：fill 退化为不生效，无内容的 Box 高度为 0dp。
6. background 挪到 padding 之前后，紫色底按整行宽（fill 后的完整约束）铺满，内容再向内退 16dp——色块从"整行减 32dp"变成整行宽（background 覆盖范围由它在链上的位置决定，第 7 章）；requiredSize 无视父约束强制 100dp，整盒越出 80dp 容器边界，内容区 68dp，越界部分可见。

## 9. 本章总结

Compose 尺寸始终在父约束中协商。fill 表达"使用允许的最大空间"，size 表达尺寸要求，padding 影响内容可用空间，列表 contentPadding 则影响可滚动内容范围。顺序不是书写风格：size 与 padding 互换，内容区从 68dp 变 100dp、整盒从 100dp 变 132dp；无界方向上 fill 退化为内容自身大小，这是滚动容器里最常见的"直觉失灵"来源。

官方参考：

- [Compose layout basics（布局基础与 Modifier）](https://developer.android.com/develop/ui/compose/layouts/basics)
- [Custom layouts（Constraints 与测量模型）](https://developer.android.com/develop/ui/compose/layouts/custom)
