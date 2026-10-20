# 第 10 章：Card、Surface 与容器组件的区别

先看一个真实问题：首页条目要一个「白底、圆角、能点、按下有反馈」的块。用 `Box` 加 `background`、`clip`、`clickable` 一层层拼也能拼出来，但圆角、涟漪、无障碍朗读、内容颜色都要自己管；而直接换成 `Card(onClick = ...)` 三件事全有了。这背后是三类容器的分工：Box 只管布局，Surface 管 Material 表面，Card 在 Surface 之上再表达「卡片」语义。本章把这条选型线讲清楚。

## 1. 本章目标

分清 Card、Surface、Box 以及“Flutter Container 等价组合”各自负责的问题。

学完后，你应当能够：

- 理解 Surface 是 Material 语义容器
- 理解 Card 是带卡片默认样式和交互语义的 Surface 风格组件
- 知道 Compose 没有一个包办所有装饰的 Container
- 根据场景选择 Box、Surface 或 Card

## 2. 项目中的位置

- `ui/home/HomeScreen.kt`
- `components/SurfaceDemo.kt`
- `components/ContainerDemo.kt`



## 3. 项目源码片段

<!-- compile -->
```kotlin
Card(
    onClick = {},
    modifier = Modifier.fillMaxWidth(),
) {
    Row(Modifier.fillMaxWidth().padding(16.dp)) {
        Text("卡片内容")
    }
}

Surface(
    shape = RoundedCornerShape(12.dp),
    color = MaterialTheme.colorScheme.surfaceVariant,
) {
    Text("主题化容器", Modifier.padding(16.dp))
}
```

## 4. 逐步解释

`Surface` 代表 Material 表面，集中处理 color、contentColor、shape、tonalElevation、shadowElevation 等主题化属性。`Card` 是更具体的卡片组件，拥有 CardDefaults，并可直接使用 `onClick` 版本获得正确的交互和语义。

Compose 没有 Flutter 那种同时提供尺寸、边距、背景、圆角、边框、对齐的单一 `Container`。项目的 ContainerDemo 使用 Box 加 Modifier 链组合这些能力。简单层叠或对齐选 Box；需要 Material 背景和内容色传播选 Surface；表达一张信息卡片并需要点击选 Card。

### 4.1 选型表：同一个矩形，三种语义

| 维度 | Box | Surface | Card |
| --- | --- | --- | --- |
| 定位 | 纯布局容器（层叠/对齐，第 9 章） | Material 表面 | Material 卡片 |
| 形状 | 自己拼：`background(色, 形状)` | `shape` 参数 | `shape` 参数，默认取主题卡片形状（`CardDefaults`） |
| 底色 / 内容色 | 自己 `background`；内容色不传播 | `color` + `contentColor` 参数，内容色自动向子树传播 | 由 `CardDefaults.cardColors()` 提供 |
| 阴影 / 高度 | 无，要自己组合 | `tonalElevation` / `shadowElevation` 两个参数 | `elevation` 参数（`CardElevation`，随按下等状态变化） |
| 点击语义 | 无，自己加 `clickable` | 有 `onClick` 版本 | 有 `onClick` 版本，自带涟漪与卡片语义 |
| 最小触摸目标 | 无 | 见下方版本说明 | 见下方版本说明 |
| 所属库 | Compose Foundation（非 Material） | Material 3 | Material 3 |

> 版本说明（以本仓库基线 `snippets/gradle/libs.versions.toml` 为准，material3 1.4.0，升级后需复核）：「最小触摸目标」在本基线中由 Material 3 层提供——可点击（`onClick` 版本）的 Surface 在修饰符链里先应用 `Modifier.minimumInteractiveComponentSize()`，再放表面与 clickable：只要父约束允许，它会在**布局层面**预留最小交互空间（`LocalMinimumInteractiveComponentSize`，默认 48 x 48dp），不改视觉尺寸，但布局占位与命中区域都会扩大；可点击的 Card 与 Button 内部都委托这个 Surface，因此同样获得该行为。不可点击的 Surface / Card 没有这一保障，与 Box 一样。要关闭它，可在 `LocalMinimumInteractiveComponentSize` 中提供 `0.dp`（material3 1.4.0 起；旧的 `LocalMinimumInteractiveComponentEnforcement` 已弃用）。第 7 章讲过的 foundation 层 `clickable` 命中外扩是另一层机制：它只扩命中判定，不改变布局占位。

### 4.2 演示一：三者并排，同一内容不同容器

<!-- compile -->
```kotlin
Column(
    modifier = Modifier.fillMaxWidth().padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
) {
    // Box：纯布局容器。圆角和底色要用 Modifier 自己拼
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text("Box：装饰自己拼")
    }

    // Surface：Material 表面。shape 与 color 是一等参数，还传播内容色
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        // tonalElevation = 6.dp,  // 「色调高度」叠加只在容器色为 surface 时改变底色，见检查题 5
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().height(56.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("Surface：主题化表面")
        }
    }

    // Card：卡片默认样式（形状、底色、少量阴影都来自 CardDefaults）
    Card(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier.fillMaxWidth().height(56.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("Card：卡片默认样式")
        }
    }
}
```

初始界面与预期观察：三个等高（56dp）的块从上到下排列。Box 块 = 手写的圆角色块，视觉上与 Surface 块接近，但没有内容色传播；Surface 块 = 同样的圆角与颜色，但改形状、颜色、加 `tonalElevation` 都只是传参数；Card 块 = 主题默认的卡片形状、底色和阴影，一行装饰代码都没写。三者的差异不在「是不是矩形」，而在**默认行为和语义**：换主题（深色模式、动态取色，第 77 章）时 Surface 与 Card 的颜色自动跟着走，手拼的 Box 不会。

### 4.3 演示二：可点击与不可点击版本的差别

`Card(onClick = ...)` 与 `Card { ... }` 是两个不同的函数版本：前者带涟漪与触摸反馈，并对外暴露「这是一个可操作的卡片」的语义；后者只是静态容器，按下去没有任何反馈。

<!-- compile -->
```kotlin
var clickCount by remember { mutableStateOf(0) } // 计数写法见第 7 章 4.3 节

Column(
    modifier = Modifier.fillMaxWidth().padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
) {
    // 可点击版：按下出现涟漪，TalkBack 会朗读为可操作元素
    Card(
        onClick = { clickCount++ },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = "可点击卡片：已点 $clickCount 次",
            modifier = Modifier.padding(16.dp),
        )
    }

    // 不可点击版：没有涟漪，也没有点击语义
    Card(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "不可点击卡片：按下去没有反馈",
            modifier = Modifier.padding(16.dp),
        )
    }
}
```

初始界面：两张全宽卡片，上张显示「已点 0 次」。操作步骤与预期观察：

1. 按住上张卡片：按下点出现涟漪扩散，松手后计数加 1。
2. 按住下张卡片：界面完全无变化。

这就是「点击语义」的具体含义：涟漪反馈、无障碍朗读、以及后续可以自然接入的启用/禁用状态，`onClick` 版本一次给齐；`Box + clickable` 只有点击行为本身，样式与语义都要自己补。Surface 同样有 `Surface(onClick = ...)` 版本，行为与 Card 的 onClick 版本一致，只是默认样式是「表面」而不是「卡片」。

## 5. 实际执行过程

1. 选择容器语义
2. 容器读取 MaterialTheme 默认值
3. 测量并布局 content
4. 绘制背景、形状和 elevation
5. 可点击版本再处理交互与无障碍语义（无障碍：让 TalkBack 等辅助工具能正确朗读并操作界面的能力，第 80 章详解）

## 6. Flutter / Dart 3 对照

Flutter 的 Card 是 Material 卡片；Container 是通用装饰盒。Compose 的 Surface 更接近“有 Material 语义的基础表面”，而 Box + Modifier 更接近 Container 的组合式实现。

```dart
Card(
  child: InkWell(
    onTap: onTap,
    child: const Padding(
      padding: EdgeInsets.all(16),
      child: Text('卡片'),
    ),
  ),
);
```

对照要点：Flutter 的 Card 本身也不直接处理点击，要靠内层 `InkWidget`（如 `InkWell`）提供涟漪与点击；Compose 的 `Card(onClick = ...)` 把这一步收进了组件参数。Flutter `Container` 把 padding、color、decoration、constraints 集中在一个 Widget 上，Compose 没有对应物，等价能力是「Box + Modifier 链」。两边概念可以互相参照，但默认样式体系（Material 2/3、主题 token）并不相同，不能当成完全等价。

## 7. 常见错误

- 为了点击效果在 Card 外再套 clickable，造成重复点击语义
- 用 Surface 表达纯布局，却不需要任何 Material 属性
- 认为 Card 的外边距由 Card 自带；外边距仍需 Modifier.padding
- 把 Box、Surface、Card 当成仅名称不同的同一个组件

反例（可以编译，但不要这样写）：

```kotlin
// 反例：onClick 版 Card 已经处理了点击与语义，
// 外层再套 clickable 会叠加涟漪、重复触发与重复的无障碍语义
Box(Modifier.clickable { /* A 逻辑 */ }) {
    Card(onClick = { /* B 逻辑 */ }) { Text("卡片") }
}
```

## 8. 本章检查题

1. 首页 EntryCard 为什么使用 Card(onClick) 而不是 Box.clickable？
2. Surface 的 contentColor 有什么价值？
3. Flutter Container 在 Compose 中通常由哪些能力组合？
4. 预测：一个高 40dp 的 `Surface(onClick = { ... }) { Text("小按钮") }`，视觉高度是多少？可点击的高度呢（按本仓库基线）？
5. 动手：把 4.2 节演示一中的 Surface 的 `color` 改为 `MaterialTheme.colorScheme.surface`，再加上 `tonalElevation = 6.dp`，先预测颜色变化方向（更深还是更浅由主题决定，观察的是「变化」本身），再运行对比；随后把 `color` 改回 `surfaceVariant` 加同样的 `tonalElevation`，观察有什么不同。

答案：

1. Card(onClick) 自带 Material 卡片语义、正确的交互和涟漪反馈，以及 CardDefaults 默认样式；Box.clickable 只有点击行为，没有卡片语义和 Material 表面样式。
2. contentColor 会向子树传播与表面背景形成对比的内容颜色，保证表面上的文字、图标可读性。
3. Box + Modifier 链（size、padding、background、border、clip、align 等）。
4. 视觉高度仍是 40dp（预留的是布局空间，不改视觉尺寸）；但可点击 Surface 会经 `minimumInteractiveComponentSize()` 预留最小交互空间——父约束允许时布局占位至少 48 x 48dp（本基线默认），比 40dp 多出的占位也纳入命中范围。要让占位保持 40dp，需用 `LocalMinimumInteractiveComponentSize` 提供 `0.dp` 关闭（见 4.1 版本说明）。
5. 色调高度叠加只在容器色为 `colorScheme.surface` 时应用：`color = surface` 时加 `tonalElevation = 6.dp`，底色会换成「更高表面层」的颜色，变深还是变浅取决于主题配置，而阴影（shadowElevation）不变；改回 `surfaceVariant` 后，同样的 `tonalElevation` 不再改变底色。所以动手题要先把 Surface 的 `color` 改成 `surface` 再加 `tonalElevation` 才能观察到变化（material3 1.4.0 行为）。

## 9. 本章总结

Box 解决基础布局，Surface 解决 Material 表面，Card 表达卡片语义。选择组件时先看语义和默认行为，而不是只看视觉是否都是“一个矩形”。需要点击时优先用组件自带的 `onClick` 版本，让涟漪、语义与主题样式一次到位。

官方参考：[Card 组件指南](https://developer.android.com/develop/ui/compose/components/card)、[androidx.compose.material3 API 参考](https://developer.android.com/reference/kotlin/androidx/compose/material3/package-summary)。
