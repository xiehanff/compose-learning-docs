# 第 26 章：Button、IconButton 与 Icon

## 1. 本章目标

分清交互组件与绘制组件：Button、IconButton 负责点击和语义，Icon 只负责画图标。

学完后，你应当能够：

- 理解 Button 的 content Lambda
- 使用 IconButton 提供正确触控区域
- 为 Icon 提供合适无障碍描述（第 80 章详解）
- 避免重复 clickable
- 理解 `enabled = false` 的能力边界：UI 禁用不是防重复提交的唯一保障
- 做一个「加载态按钮」：禁用 + `CircularProgressIndicator` 小圈 + 业务层防重复提交
- 知道 48dp 触摸目标建议（Material 规范，非硬性保证）

## 2. 项目中的位置

- `ui/detail/ComponentDetailScreen.kt`
- `components/ButtonDemo.kt`
- `components/AlertDialogDemo.kt`



## 3. 项目源码片段

<!-- compile
prelude: var liked by remember { mutableStateOf(false) }
-->
```kotlin
// components/ButtonDemo.kt：五种风格按钮，按视觉权重从高到低排列
Button(onClick = {}) { Text("Button（Filled）→ 主操作") }              // 主操作，视觉权重最高
FilledTonalButton(onClick = {}) { Text("FilledTonalButton → 次要操作") } // 低饱和次级实心
OutlinedButton(onClick = {}) { Text("OutlinedButton → 中等权重") }       // 描边，中等权重
TextButton(onClick = {}) { Text("TextButton → 最低权重") }             // 纯文字，权重最低
ElevatedButton(onClick = {}) { Text("ElevatedButton → 浮在内容之上") }  // 带阴影，适合浮在内容之上

// 可用 / 禁用对照：enabled = false 时按钮自动变灰且点不动
Button(onClick = {}) { Text("可用状态") }
Button(onClick = {}, enabled = false) { Text("禁用状态") }

// IconButton + Icon：图标随状态切换
IconButton(onClick = { liked = !liked }) {
    Icon(
        imageVector = if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
        contentDescription = if (liked) "取消收藏" else "收藏",
        tint = if (liked) Color(0xFFE57373) else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

// 项目其它位置（详情页返回键）。返回箭头要用 AutoMirrored 版本：
// Icons.Filled.ArrowBack 已弃用，RTL 语言下它不会自动镜像
IconButton(onClick = onBack) {
    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
}
```

### 扩展示例：加载态按钮与防重复提交

项目片段是静态对照，下面这个例子可以独立复制运行，覆盖「点击 → 异步提交 → 恢复」的完整闭环。初始界面：一个可点的「提交」按钮。操作：快速连点它。期望观察：点击一次后按钮立即变灰、文字变成「提交中…」并出现小圈，约 1.5 秒后恢复；期间再点没有任何反应，控制台也只打印一次「模拟提交完成」。

<!-- compile -->
```kotlin
var submitting by remember { mutableStateOf(false) }
val scope = rememberCoroutineScope() // UI 级协程作用域，第 29 章展开

Button(
    onClick = {
        // 第二道防线：即使禁用还没生效，标志位也拦得住重复提交
        if (submitting) return@Button
        submitting = true
        scope.launch {
            delay(1500)              // 用 delay 模拟一次网络请求，真实项目换成 suspend 函数
            println("模拟提交完成")
            submitting = false
        }
    },
    enabled = !submitting,           // 第一道防线：提交中变灰、点击无效
) {
    if (submitting) {
        CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            strokeWidth = 2.dp,
        )
        Spacer(Modifier.width(8.dp))
    }
    Text(if (submitting) "提交中…" else "提交")
}
```

## 4. 逐步解释

Button 自带 Material 形状、颜色、最小触控尺寸、禁用状态和按钮语义。IconButton 专门承载图标型操作，并提供合适的点击范围。Icon 自身只是读取 ImageVector 并绘制，没有 onClick 参数。

Material3 把按钮按「视觉权重」分成五个层级，它们的调用签名相同（都是 `onClick` + 尾随内容 lambda），共享核心参数，区别在默认颜色、边框、阴影、形状和内边距等默认参数上：`Button`（Filled 实心）是页面最高优先级的主操作，一屏通常只放一个；`FilledTonalButton` 是低饱和的次级实心按钮，适合「保存草稿」这类次要动作；`OutlinedButton` 是描边按钮，中等权重，浅色背景上更清爽；`TextButton` 是纯文字按钮，权重最低，常用于「取消」「跳过」；`ElevatedButton` 带阴影，适合浮在图片或卡片之上。

启用与禁用统一用 `enabled` 参数控制：`enabled = false` 时按钮自动变灰、点击无效，无需自己画禁用配色。`ButtonDemo.kt` 把可用态和禁用态并排放在一个 `Row` 里对照，就是为了看清这个差异。

但 `enabled = false` 只能挡住「禁用生效之后」的点击，它是 UI 层的第一道防线，不是业务层的唯一保障。原因有两个：第一，`onClick` 回调执行在前，`enabled = false` 要等一次重组把新参数应用到输入系统后才拦截后续点击，在「回调已执行、按钮还没变灰」的时序窗口里（能否实际复现取决于设备与帧调度），快速连点仍可能再次进入回调；第二，如果同一个提交还有别的入口（另一个按钮、下拉刷新），这个按钮的禁用管不住其它入口。所以扩展示例的回调开头还有第二道防线：`if (submitting) return@Button`。`return@Button` 是 Kotlin 带标签的 return——直接从传给 `Button` 的那个 lambda 里返回，后面的代码不再执行。这是作者建议的通用结构：防重复的核心判断放在业务回调内部，禁用态负责把状态同步给用户。

内容 lambda 里可以放任意 Composable。示例用 `if (clickCount == 0) ... else ...` 让按钮文字随计数状态变化，直观看到「状态驱动 UI」：点击后 `clickCount++`、状态变化、按钮文字重绘，不需要手动刷新任何东西。

contentDescription 服务 TalkBack 等无障碍工具（无障碍：让 TalkBack 等辅助工具能正确朗读并操作界面，第 80 章详解）。图标承载独立信息时应写有意义描述；如果父级按钮已经提供完整语义，纯装饰 Icon 可以传 null，避免重复朗读。`ButtonDemo.kt` 的爱心图标把「收藏 / 取消收藏」写在 contentDescription 里并随状态切换，是对的做法。除此之外，`Icon` 还可以用 `tint` 染色：示例在收藏时把爱心染成红色，未收藏时使用主题的次要色。

Button 与 IconButton 在交互语义上有一处关键差异：Button 的内容 lambda 里有文字，这段文字就是按钮的可访问名称，TalkBack 会读「提交，按钮，双击即可激活」；IconButton 只有一个动作、没有任何文字，可访问名称只能来自内部 Icon 的 contentDescription。换句话说，IconButton 里的 contentDescription 不是可选的美化项，而是这个按钮唯一的标签来源——如果传 null，TalkBack 只能读出「未加标签，按钮」，视障用户无从知道它做什么。这与纯装饰图标可以传 null 的场景（第 12 章）方向相反：装饰图标不承载信息所以不需要描述；图标按钮的操作语义，恰恰要靠描述补全。

再看触控区域大小。Android 无障碍指南建议：触摸界面上的可交互元素，触摸目标至少 48dp × 48dp（Material 规范建议，不是编译器强制的硬性保证，触摸目标参考 [Make apps more accessible](https://developer.android.com/guide/topics/ui/accessibility/apps)）。在本仓库基线（material3 1.4.0，见 `snippets/gradle/libs.versions.toml`）中，IconButton 内部默认通过最小交互尺寸机制把点击区域撑到 48dp——视觉图标仍是 24dp，多出来的区域用于接收点击（可用 `LocalMinimumInteractiveComponentSize` / `LocalMinimumInteractiveComponentEnforcement` 调整，属版本敏感内容）；Button 也有 Material 规定的最小尺寸（高度来自 Material tokens），但不会像 IconButton 那样把交互区域额外外扩，触摸目标基本就是它的可视边界——常规尺寸通常够用，自绘的小尺寸可点元素要自己保证触摸目标。

官方参考：[Buttons](https://developer.android.com/develop/ui/compose/components/button)、[Accessibility in Compose](https://developer.android.com/develop/ui/compose/accessibility)、[Make apps more accessible（48dp 触摸目标）](https://developer.android.com/guide/topics/ui/accessibility/apps)。

不要给 IconButton 外层再加 clickable，否则可能产生两层点击和语义冲突。

## 5. 实际执行过程

1. 交互组件组合内部内容
2. Icon 绘制矢量路径
3. 用户点击命中 Button/IconButton
4. 组件处理按压状态和语义
5. onClick 回调执行
6. 提交按钮的完整链路：onClick 先检查 `submitting` 标志 → 置 `true` 并启动协程 → 读取该状态的按钮重组，`enabled = false`、内容换成小圈与「提交中…」→ `delay` 结束置回 `false` → 按钮恢复可点
7. 若在「已置 true、尚未变灰」的时序窗口里又来一次点击，回调开头的 `if (submitting) return@Button` 直接返回，协程只启动一次

## 6. Flutter / Dart 3 对照

Flutter 的 ElevatedButton、IconButton、Icon 几乎一一对应：Icon 同样不是点击组件；IconButton 同样负责把触控区域撑到 Material 建议的最小点击区域。两处不等价值得注意：禁用方式不同——Flutter 没有独立的 `enabled` 参数，把 `onPressed` 置为 `null` 即禁用，Compose 用 `enabled = false`；无障碍标签来源不同——Flutter 的 IconButton 用 `tooltip` 兼任语义标签，Compose 的 IconButton 没有文字参数，靠内部 Icon 的 `contentDescription`。

```dart
IconButton(
  onPressed: onBack,
  tooltip: '返回', // tooltip 兼任无障碍标签，作用类似 Icon 的 contentDescription
  icon: const Icon(Icons.arrow_back),
);

ElevatedButton(onPressed: onBack, child: const Text('返回首页'));

// 禁用与加载态：Flutter 把 onPressed 置 null 表示禁用
ElevatedButton(
  onPressed: submitting ? null : _submit,
  child: submitting
      ? const SizedBox(
          width: 16,
          height: 16,
          child: CircularProgressIndicator(strokeWidth: 2),
        )
      : const Text('提交'),
);
```

## 7. 常见错误

- 给 Icon 直接添加 onClick 参数
- IconButton 外再套 Gesture/clickable
- 图标没有 contentDescription 或 tooltip
- 用 Text.clickable 模拟所有按钮，丢失标准语义
- 一屏堆多个 `Button`（Filled）争当主操作，没利用 `FilledTonalButton` / `OutlinedButton` / `TextButton` 拉开权重
- 禁用态用 `alpha`（控制透明度的属性，第 57 章详解）或自定义颜色自己模拟，而不用 `enabled = false`
- 收藏类图标切换时只换 `imageVector`，忘了同步改 `contentDescription`，TalkBack 仍读旧语义
- 把 `enabled = false` 当成防重复提交的唯一保障：回调先执行、禁用随后才生效，其它入口也不受这个按钮约束
- 加载态只把文字换成「提交中」，不禁用按钮，请求期间还能再点
- 图标按钮里的 Icon 一律 `contentDescription = null`：按钮没有文字，TalkBack 读不出用途
- 用 `Icon + Modifier.clickable` 顶替 IconButton：点击区域只有图标大小，还丢了按钮语义

## 8. 本章检查题

1. Icon 与 IconButton 的职责分别是什么？
2. 返回图标的触控区域由谁提供？
3. 何时可将 contentDescription 设为 null？
4. Material3 有哪五种按钮风格？禁用态怎么实现？
5. 预测行为：把扩展示例里的 `if (submitting) return@Button` 删掉，再快速连点按钮，最坏会发生什么？
6. 动手：把扩展示例改成 `FilledTonalButton`，加载文案改为「保存中…」。

答案：

1. Icon 只读取 ImageVector 并绘制，自身没有点击能力；IconButton 承载图标型操作并提供合适的点击范围。
2. IconButton 自带的最小触控区域（Material 默认触控尺寸）。
3. 图标是纯装饰，或父级按钮已经提供完整语义时，避免 TalkBack 重复朗读。注意：IconButton 里的图标承载操作语义，几乎总要写。
4. `Button`（Filled 实心）、`FilledTonalButton`（低饱和次级实心）、`OutlinedButton`（描边）、`TextButton`（纯文字）、`ElevatedButton`（带阴影）。禁用态用 `enabled = false`，按钮自动变灰且点击无效。
5. `enabled = !submitting` 依赖一次重组才生效，在「回调已执行、按钮尚未变灰」的时序窗口里（或从其它入口触发时），第二次点击仍会进入 onClick，重复启动提交协程。删掉标志位检查后，两道防线就只剩禁用一道。
6. 把 `Button(` 换成 `FilledTonalButton(`（参数一致），并把 `Text(if (submitting) "提交中…" else "提交")` 的两个文案换成「保存中…」与「保存」。

## 9. 本章总结

优先使用语义正确的 Material 交互组件。Button/IconButton 处理交互，Icon 只绘制，二者职责不能混淆。Button 的可访问名称来自内容文字，IconButton 只能靠 Icon 的 contentDescription。`enabled = false` 负责禁用态视觉与交互，但防重复提交要在回调里用业务标志兜底；加载态按钮 = 禁用 + 小圈 + 标志位检查，48dp 触摸目标是 IconButton 已内置、自绘交互需要自己保证的规范建议。
