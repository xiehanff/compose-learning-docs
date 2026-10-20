# 第 14 章：weight、Spacer 与 Flutter Expanded

## 1. 本章目标

先看一个真实问题：Row 里三个色块按 1:2:1 排，中间那块总比预期宽（或窄）；想"标题靠左、箭头靠右"，有人写两个 weight，有人写 Spacer(weight)，还有人直接用 Arrangement.SpaceBetween——到底哪种对？本章主线只有一个：Row/Column 的剩余主轴空间按权重分配，weight 是把"分配意愿"告诉父容器的修饰符，子项自己的宽度请求默认会被覆盖。

掌握 Row/Column 中剩余空间的权重分配，并准确理解 Compose 为什么没有单独的 Expanded 组件。

学完后，你应当能够：

- 理解 weight 只在 RowScope/ColumnScope 内使用，以及它是告诉父容器的 ParentDataModifier
- 计算 1:2:1 权重比例，说明固定尺寸先测量、剩余空间再按权重分配
- 解释 weight 子项"自身宽度请求被覆盖"的测量行为与 fill 参数的关系
- 区分 `weight(w, fill = true/false)` 与 Flutter Expanded / Flexible 的对应与差异
- 会用 Spacer(Modifier.weight(1f)) 表达弹性空白，并知道它和固定间距的差别

## 2. 项目中的位置

- `components/ExpandedDemo.kt`
- `ui/home/HomeScreen.kt`



## 3. 项目源码片段

<!-- compile -->
```kotlin
Row(Modifier.fillMaxWidth()) {
    Box(Modifier.weight(1f).height(64.px))
    Box(Modifier.weight(2f).height(64.px))
    Box(Modifier.weight(1f).height(64.px))
}

Row(Modifier.fillMaxWidth()) {
    Text("左")
    Spacer(Modifier.weight(1f))
    Text("右")
}
```

项目节选，原项目用 `.px` 设计稿单位（第 43、44 章）。项目首页 EntryCard 的 `Column(Modifier.weight(1f))` 让文字区吃掉剩余宽度，把箭头挤到最右，原理见 4.3。

### 3.1 独立示例：fill = true / false 对照

参与编译校验。初始界面：一个带边框、48dp 高、占满整行的 Row，里面两段带底色的文字。

<!-- compile -->
```kotlin
Row(
    modifier = Modifier
        .fillMaxWidth()
        .height(48.dp)
        .border(1.dp, Color.Gray),
) {
    // fill = true（默认）：主轴被固定为分配额，文字占满自己的 1/2 份额
    Text("甲", Modifier.weight(1f).background(Color(0xFFFFE082)))
    // fill = false：主轴上限是分配额，但允许只占内容自身宽度
    Text("乙", Modifier.weight(1f, fill = false).background(Color(0xFFB39DDB)))
}
```

预期观察："甲"的黄色背景横贯左半行（占满分配到的 1/2 份额）；"乙"的紫色背景只有一个字宽，剩下的份额空着。两个 weight 同为 1f，占的主轴空间完全不同——差别只在 fill 参数。

## 4. 逐步解释

### 4.1 weight 是"给父容器的分配意愿"

weight 不是自己改尺寸的修饰符。它属于 ParentDataModifier：把"我要按比例分剩余主轴空间"的意愿挂在子项上，由 Row/Column 在测量阶段读取并执行。所以 `weight` 只在 RowScope/ColumnScope 里拿得到——作用域接收者保证了父容器一定认识这个意愿；在 Box 内容或普通函数里调用时，作用域里根本没有这个扩展，直接编译不过（第 7 章）。

首次出现的 Kotlin 语法：`1f`、`2f` 是 Float 字面量（后缀 f）。weight 的参数类型是 Float，整数 1、2 不会被自动当作 Float 使用，必须写 `1f`、`2f`，也可以写 `1.5f` 这样的小数。

Row 会先处理没有 weight 的子项和自身固定开销，再把主轴剩余宽度按照权重总和分配。1 + 2 + 1 的总权重是 4，因此三段分别得到剩余空间的 1/4、2/4、1/4。Column 中同理，只是分配的是剩余高度。

测量顺序展开成三步：第一步测量所有无 weight 子项（按它们自己的尺寸愿望）；第二步扣除这些固定占用和 Arrangement 间距；第三步把剩余主轴空间按 weight 值占总和的比例切分给带 weight 的子项。单个 weight(1f) 独占全部剩余空间——"推挤器"写法就是它的应用。

### 4.2 weight 子项的主轴请求会被覆盖

fill = true（默认）时，父容器给 weighted 子项的主轴约束是"恰好等于分配额"（min = max = 份额）：子项自己写的 width 或 fillMaxWidth 在这个固定约束里会被覆盖——`Modifier.weight(1f).width(40.dp)` 与 `Modifier.width(40.dp).weight(1f)` 的主轴结果一样，都是分配额，不是 40dp。fill = false 时约束变成 min = 0、max = 份额：子项可以小于分配额（3.1 的"乙"），但不能超过。

这解释了一个反直觉现象：给 weighted 子项写固定宽度"不生效"不是 bug，是 weight 的测量契约——想固定就别 weight，想弹性就别期待固定值。

### 4.3 weight 与 fillMaxWidth 组合、推挤器模式

结论先行：Row 子项的主轴尺寸交给 weight 后，再写 fillMaxWidth 是冗余（主轴已被固定为分配额）；交叉轴不受影响，Row 里 `weight(1f).fillMaxHeight()` 有意义。weighted 子项内部的子组件再用 fillMaxWidth，填的是该子项自身的宽度约束，也就是分配额。

推挤器模式（项目首页的用法）：

<!-- compile -->
```kotlin
Row(Modifier.fillMaxWidth()) {
    Text("标题文字")
    // 弹性空白：吃掉标题右侧的全部剩余宽度，把“详情”顶到最右
    Spacer(Modifier.weight(1f))
    Text("详情")
}
```

预期观察："标题文字"靠左、"详情"靠右，中间空白随行宽伸缩。只有两端两个元素时，`Arrangement.SpaceBetween`（第 8 章）效果相同且更直白；中间要放弹性内容或需要三段分布时，weight/Spacer 更灵活。

### 4.4 Spacer(weight) 与 Spacer(16.dp)：弹性空白 vs 固定间距

`Spacer(Modifier.weight(1f))` 占的是"剩余空间"，行宽变化时空白跟着变，两端元素始终贴边；`Spacer(Modifier.width(16.dp))` 恒定 16dp，行宽变化时空白不变、后一个元素的位置随之移动。选哪个取决于设计意图：两端分布用弹性，元素间距恒定用固定。

<!-- compile -->
```kotlin
Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Row(Modifier.fillMaxWidth()) {
        Text("左")
        Spacer(Modifier.weight(1f)) // 弹性：空白 = 剩余全部宽度
        Text("右")
    }
    Row(Modifier.fillMaxWidth()) {
        Text("左")
        Spacer(Modifier.width(16.dp)) // 固定：空白恒为 16dp，“右”不再贴右
        Text("右")
    }
}
```

预期观察：第一行"右"贴右边缘、空白随行宽变化；第二行"右"跟在"左"后面 16dp 处，行尾留下大片空白。

## 5. 实际执行过程

1. 容器收到主轴约束，先测量非权重子项（固定尺寸先落账）
2. 扣除固定尺寸和 Arrangement 间距，得到剩余主轴空间
3. 计算全部 weight 总和
4. fill = true 的权重子项按 min = max = 份额测量（自身宽度请求被覆盖）；fill = false 按 0..份额测量
5. 交叉轴照常按容器规则测量
6. 按比例放置全部子项

## 6. Flutter / Dart 3 对照

Flutter 的 Expanded(flex: n) 与 Modifier.weight(n.toFloat()) 语义接近；Flutter Spacer(flex: n) 对应 Compose Spacer(Modifier.weight(...))。不同点是 Compose 用 Modifier 而不是额外包装组件。

| Compose | Flutter |
| --- | --- |
| weight(nf, fill = true)（默认） | Expanded(flex: n)：tight，强制占满份额 |
| weight(nf, fill = false) | Flexible(flex: n, fit: FlexFit.loose)：可小于份额 |
| Spacer(Modifier.weight(1f)) | Spacer(flex: 1) |
| 只能在 RowScope/ColumnScope 使用 | 只能作为 Flex（Row/Column）的直接子级 |

不完全等价处（概念对照，不是机制等价）：一，形态——Flutter 用包装 Widget（会新增一层树节点），Compose 用作用域 Modifier；二，参数类型——Compose 的权重是 Float（可写 1.5f），Flutter 的 flex 是 int；三，API 切面——Flexible 还有 `fit: FlexFit.tight` 选项（此时与 Expanded 等效），Compose 把"紧/松"做成 weight 的 fill 参数，一个函数切换；四，无界主轴下的行为——Flutter 直接抛布局异常，Compose 的剩余空间算不出有限值，weight 子项得不到确定尺寸（见第 13 章 4.3 与本章常见错误）。

```dart
Row(
  children: const [
    Expanded(flex: 1, child: ColoredBox(color: Colors.blue)),
    Expanded(flex: 2, child: ColoredBox(color: Colors.green)),
    Expanded(flex: 1, child: ColoredBox(color: Colors.blue)),
  ],
);
```

## 7. 常见错误

- 在没有有限主轴空间的滚动容器里使用 weight：剩余空间是无限值，分配无意义，可能出现布局异常；应给内容固定或最小高度，或调整层级（第 13 章 4.3）
- 以为 weight(2f) 表示固定 2 倍屏幕宽：它只表示"剩余空间的 2 份"，剩余空间为 0 时什么也分不到
- 在 RowScope 外调用 weight 导致无法解析（4.1：作用域里没有这个扩展）
- 用 weight 解决本应由 Arrangement.SpaceBetween 解决的简单分布，却增加不必要布局（4.3）
- 给 weighted 子项写固定 width 又期待生效：fill = true 时主轴被固定为分配额（4.2）
- 把 Spacer(weight) 当万能间距：需要恒定间距的场合它会让元素位置随屏宽漂移（4.4）

## 8. 本章检查题

1. 1:2:1 的三个子项各得到剩余空间多少比例？
2. 固定 100dp 子项是否参与权重比例？
3. 首页 EntryCard 为什么给左侧 Column 设置 weight(1f)？
4. 预测：Row 宽 500dp，子项依次是固定 100dp 的 Box、weight(1f) 的 Box、weight(3f) 的 Box（间距忽略），三个子项各多宽？
5. 预测：把第 4 题中 weight(1f) 改成 weight(1f, fill = false) 且该 Box 无内容，另外两个子项的宽度变了吗？
6. 动手：在 3.1 示例的"乙"后面再加一个 `Text("丙", Modifier.weight(1f))`，三个子项各占多少？再把"甲"的 weight 改成 2f，观察黄、紫块的份额变化。

答案：

1. 总权重为 4，三个子项分别得到剩余空间的 1/4、2/4、1/4。
2. 不参与。固定尺寸子项先被分配，weight 只分配剩余空间。
3. 让文字区域吃掉剩余宽度，把箭头图标挤到卡片右侧。
4. 固定 Box 100dp；剩余 400dp 按 1:3 分——weight(1f) 得 100dp，weight(3f) 得 300dp。
5. 没变。fill 只影响该子项自己是否占满份额；分配额按权重比例切分，不受 fill 影响。fill = false 且无内容时该子项宽度为 0，空出的份额留在行尾，不会回流给别的子项。
6. 三份各占 1/3（乙仍只占一个字宽，其余空着）；"甲"改 2f 后总权重为 2+1+1=4，甲占 1/2，乙、丙各 1/4。

## 9. 本章总结

weight 是线性布局中对"剩余主轴空间"的比例分配，不是绝对尺寸：固定子项先测量，剩余空间按权重比例切分；fill = true 时子项主轴被固定为分配额（自身宽度请求被覆盖），fill = false 允许小于分配额——这对参数是 Flutter Expanded / Flexible(loose) 的对应物，但 API 形态与无界约束下的行为并不完全等价。弹性空白用 Spacer(weight)，恒定间距用固定 Spacer；最简单的两端分布优先 Arrangement.SpaceBetween。Compose 用 Modifier.weight 实现 Flutter Expanded，并可配合 Spacer 表达弹性空白。

官方参考：

- [Compose layout basics（布局基础）](https://developer.android.com/develop/ui/compose/layouts/basics)
- [Custom layouts（Constraints 与测量模型）](https://developer.android.com/develop/ui/compose/layouts/custom)
