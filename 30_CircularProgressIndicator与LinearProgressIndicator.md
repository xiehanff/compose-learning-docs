# 第 30 章：CircularProgressIndicator 与 LinearProgressIndicator

## 1. 本章目标

先看一个真实问题：文章列表页打开时应当先显示一个加载圈，请求成功后换成本文列表，失败则显示错误文字和「重试」按钮。很多初学者会把这三个界面当成三件事分别 hack（隐藏控件、改透明度、布尔标记满天飞），而标准做法是把页面建模成三种状态，让指示器只负责其中「加载中」这一态。本章用同一套状态讲两件事：进度指示器的确定/不确定两种模式，以及「加载中 / 成功 / 失败」的状态映射。

区分确定进度和不确定进度，并把业务进度作为状态传给指示器。

学完后，你应当能够：

- 使用圆形和线性指示器
- 区分无 progress 与有 progress
- 理解 0f..1f 的比例
- 知道指示器不负责执行任务
- 用 sealed 状态驱动「加载圈 / 内容 / 错误 + 重试」三态界面
- 通过拖动滑块观察确定进度的长度变化

## 2. 项目中的位置

- `components/ProgressIndicatorDemo.kt`

## 3. 项目源码片段

### 3.1 项目里的写法

<!-- compile
prelude: val progress = 0.35f
-->
```kotlin
CircularProgressIndicator()
LinearProgressIndicator(Modifier.fillMaxWidth())

LinearProgressIndicator(
    progress = { progress.coerceIn(0f, 1f) }, // lambda 重载：进度延迟读取，不会因为进度变化而重组调用方
    modifier = Modifier.fillMaxWidth(),
)
```

### 3.2 完整可运行示例：加载中 / 成功 / 失败三态

状态模型沿用预备篇 00D 第 9 节介绍的 sealed interface（换成本章的列表版），再用一个 when 把三种状态映射成三种界面。真实 App 里 loadState 由 ViewModel 或数据层更新（第 68 章），这里用三个按钮模拟状态来源，方便观察：

<!-- compile decls -->
```kotlin
sealed interface LoadState {
    data object Loading : LoadState        // 加载中：显示不确定进度圈
    data class Success(val items: List<String>) : LoadState  // 成功：显示内容
    data class Error(val message: String) : LoadState        // 失败：错误文字 + 重试
}

@Composable
fun LoadStateDemo() {
    var loadState by remember { mutableStateOf<LoadState>(LoadState.Loading) }

    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { loadState = LoadState.Loading }) { Text("模拟加载") }
            Button(onClick = { loadState = LoadState.Success(listOf("文章 A", "文章 B", "文章 C")) }) { Text("模拟成功") }
            Button(onClick = { loadState = LoadState.Error("网络超时") }) { Text("模拟失败") }
        }

        when (val state = loadState) {                 // when 的 subject 变量写法见 00D 第 9 节
            LoadState.Loading -> CircularProgressIndicator()          // 不确定进度：任务时长未知
            is LoadState.Success -> state.items.forEach { Text(it) }  // 成功：换掉加载圈显示内容
            is LoadState.Error -> Column {
                Text("加载失败：${state.message}")
                Button(onClick = { loadState = LoadState.Loading }) { Text("重试") }
            }
        }
    }
}
```

初始界面：转动的圆圈。操作步骤：依次点「模拟成功」「模拟失败」「重试」。期望观察：圆圈换成三行文章；再变成「加载失败：网络超时」加「重试」按钮；点「重试」又回到转圈。sealed 保证 when 必须写全三个分支，新增状态时编译器会提醒补齐，这是它比布尔标记组合更适合三态建模的原因。

### 3.3 静态进度观察例：拖滑块，看进度条变长

<!-- compile -->
```kotlin
var progress by remember { mutableFloatStateOf(0.35f) }

Column(Modifier.padding(16.dp)) {
    // 确定进度：长度随 progress 变化
    LinearProgressIndicator(
        progress = { progress },
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(16.dp))
    Slider(
        value = progress,
        onValueChange = { progress = it },   // Slider 默认取值范围 0f..1f，正好匹配进度
    )                                        // Slider 用法见第 26B 章
    Button(onClick = { progress = (progress + 0.1f).coerceAtMost(1f) }) {
        Text("前进 10%")
    }
}
```

初始界面：约 35% 长度的进度条。操作步骤：来回拖滑块，再连点「前进 10%」。期望观察：进度条长度即时跟随 progress 变化；按钮每次加 0.1，到满格（1f）后不再增长。

## 4. 逐步解释

### 4.1 两类进度：确定与不确定

不传 progress 时是循环动画，表示任务正在进行但不知道比例；传 0f..1f 时是确定进度，例如 0.35f 表示 35%。确定进度推荐写成 lambda 形式 `progress = { progress }`：进度值延迟到绘制时才读取，进度变化不会让调用方重组。旧的非 lambda 重载（`progress = 0.35f`）已弃用。指示器只读取状态并绘制，不会自动下载、计时或修改 progress。

两类模式对应两类问题：「任务还要多久」用确定进度（下载、上传、批量处理的百分比）；「任务还活着吗」用不确定进度（首次请求、无总量信息的加载）。同一个组件用是否传 progress 区分：CircularProgressIndicator() 转圈，CircularProgressIndicator(progress = { v }) 显示 0..1 的弧长；Linear 同理。Material 3 规范把这两种分别称为 determinate 与 indeterminate。

### 4.2 三态映射：指示器只承担其中一态

业务层应产生进度 State，UI 订阅并传入。超出范围的值应限制。完成后应切换到成功内容，失败时显示错误和重试，而不是永远停留在 loading。

三态映射的关键是把「页面处于什么阶段」建成单一真相（LoadState），而不是互相独立的三组布尔。when 的每个分支描述一种界面，界面之间互斥天然成立；这比 `isLoading && !isError` 这类组合更容易维护，也让「失败后重试」变成一次普通的状态赋值。在 3.2 里，CircularProgressIndicator 只出现在 Loading 分支——它和显示内容的 Text 之间没有任何直接通信，全靠状态切换完成交接。指示器组件本身不发起请求、不计时、不修改状态：网络在业务层（进阶篇第 68 章的 ViewModel/StateFlow），本章用按钮代替。

### 4.3 进度值怎么变成长度：事件 → 状态 → 重组 → 界面

以 3.3 为例走一遍链条：拖动滑块（事件）→ onValueChange 把新浮点值写进 progress 状态（状态）→ 依赖该状态的界面安排刷新（重组）→ 进度条按新比例绘制（界面）。确定进度的视觉就是「progress × 进度条总宽」的填充长度。

lambda 写法 `progress = { progress }` 的意义在于把读值时机从组合阶段推迟到绘制阶段：本仓库基线（material3 1.4.0）提供的 lambda 重载按官方设计可让进度高频变化时只触发重绘、跳过调用方重组（本例滑块每秒触发次数有限，肉眼上两种写法差别不大，属于性能优化而非正确性差异；教学简化）。

## 5. 实际执行过程

1. 任务产生进度
2. State 更新
3. 读取进度的 UI 重组
4. Indicator 按新值绘制
5. 完成或失败后切换页面状态
6. 失败分支显示错误与重试，点击重试把状态重置回 Loading，圈重新出现

## 6. Flutter / Dart 3 对照

Flutter 的 CircularProgressIndicator/LinearProgressIndicator 语义一致：value 为 null 是不确定模式，0..1 是确定模式。

```dart
const CircularProgressIndicator();
LinearProgressIndicator(value: progress.clamp(0.0, 1.0));
```

相似点：组件同名、确定/不确定的判断方式一致（是否传值），并且都只负责展示、不承担网络任务。

不等价点：Flutter 的 value 是普通 double，值更新依赖外层 setState/rebuild；Compose 在本仓库基线（material3 1.4.0）里把确定进度改成 lambda 重载 `progress = { v }`、把读值推迟到绘制阶段以减少重组，旧的非 lambda 重载已弃用。两边都没有内置「三态」组件，Loading/Success/Error 都要自己建模。

## 7. 常见错误

- 把 35 当作 35%，实际应传 0.35
- 指示器内部执行耗时任务
- 没有失败和重试状态
- 任务完成后仍显示 loading

```kotlin
// 错误示例（故意错误，不参与编译）：35f 不是 35%——progress 取值范围是 0f..1f，
// 传 35f 画出来是满格进度，看起来像「已完成」
LinearProgressIndicator(progress = { 35f })
```

- 用「Success + 空列表」冒充加载中：界面一片空白，用户不知道发生了什么
- 用多个布尔（isLoading/isError/isSuccess）组合三态：会出现互相矛盾的组合，漏一种就白屏

## 8. 本章检查题

1. 0.6f 表示多少百分比？
2. 不传 progress 表示什么？
3. 下载逻辑应放在 Indicator 中吗？
4. 预测输出：在 3.3 中把 progress 初值改为 1.4f（并去掉 coerceAtMost 的限制），进度条会显示 140% 吗？
5. 动手修改：给 3.2 的 Error 分支加上错误码显示（如「加载失败（1001）：网络超时」），并把「模拟失败」按钮换成两种不同的失败原因，验证文案随状态数据变化。

答案：

1. 60%。progress 取值 0f..1f，0.6f 表示 60%。
2. 表示不确定进度，显示循环动画（任务进行中但不知道比例）。
3. 不应。指示器只读取状态并绘制；下载逻辑由业务层产生进度 State，UI 订阅后传入。
4. 不会。超出 0f..1f 不在文档保证的输入范围内，行为不保证（通常表现为满格）；所以示例统一用 coerceIn/coerceAtMost 把值限制在范围内。
5. 提示：把 message 换成携带 code 与 message 两个字段的 data class，在 when 的 Error 分支里拼进 Text。改完后「模拟失败」按钮分别置入两种错误数据，界面文案随之变化——内容完全由状态数据决定，这正是三态映射的目的。

## 9. 本章总结

进度组件只负责显示。是否确定、当前比例、成功或失败都应来自外部业务状态；三态页面用 sealed 状态 + when 映射成「加载圈 / 内容 / 错误 + 重试」，指示器只是其中一态的展示件。

官方参考：

- [CircularProgressIndicator（API 参考）](https://developer.android.com/reference/kotlin/androidx/compose/material3/CircularProgressIndicator)
- [LinearProgressIndicator（API 参考）](https://developer.android.com/reference/kotlin/androidx/compose/material3/LinearProgressIndicator)
- [Material 3 规范：Progress indicators（determinate / indeterminate 定位）](https://m3.material.io/components/progress-indicators/overview)
