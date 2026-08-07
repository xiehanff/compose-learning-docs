# 第 74 章：性能分析：Layout Inspector 与编译器报告

## 1. 本章目标

学完后，你应当能够：

- 用 Layout Inspector 查看组合树、重组与跳过计数，定位可疑范围
- 配置 Compose 编译器报告，结合当前编译器产物检查稳定性推断
- 掌握减少无效工作的思路：缩小状态读取范围、保持状态模型可靠、避免错误优化
- 用 Macrobenchmark 的 Startup 与 Frame 指标做可复现的性能测量，从「猜」转向「测」

## 2. 项目中的位置

进阶篇不再局限于 compose_demo 项目，本章使用通用 Compose API。

- `androidx.compose.ui:ui-tooling`（Layout Inspector 组合树）
- `org.jetbrains.kotlin.plugin.compose`（Compose 编译器 Gradle 插件：reportsDestination / metricsDestination）
- `androidx.benchmark:benchmark-macro-junit4`（Macrobenchmark）

## 3. 项目源码片段

场景一：为模块开启编译器报告输出（app/build.gradle.kts）。

```kotlin
plugins {
    id("org.jetbrains.kotlin.plugin.compose")   // Kotlin 2.x + Compose 编译器插件
}

android {
    buildFeatures { compose = true }
}

composeCompiler {
    reportsDestination = layout.buildDirectory.dir("compose_compiler")
    metricsDestination = layout.buildDirectory.dir("compose_metrics")
}
```

构建后会在配置目录中生成稳定性、Composable 与模块指标等文本/CSV/JSON 文件。具体文件名可能随 Compose Compiler 版本变化，应以当前目录实际产物和对应版本文档为准。

场景二：一条典型「过度重组」代码与修复（把状态从大范围降到小范围）。

```kotlin
// 可优化示范：页面级读取输入状态，输入变化会使 ProfileScreen 进入重组
@Composable
fun ProfileScreen() {
    var name by remember { mutableStateOf("") }
    Column {
        Header()         // 调用会被重新访问；满足跳过条件时不会执行函数体
        OutlinedTextField(value = name, onValueChange = { name = it })
        StatsChart()     // 昂贵图表也被反复重组
    }
}

// 修复：把状态压到最小作用域
@Composable
fun ProfileScreen() {
    Column {
        Header()
        NameEditor()     // 状态只在 NameEditor 内部，重组只发生在这里
        StatsChart()
    }
}

@Composable
private fun NameEditor() {
    var name by remember { mutableStateOf("") }
    OutlinedTextField(value = name, onValueChange = { name = it })
}
```

场景三：在独立 `benchmark` 测试模块中编写 Macrobenchmark。该模块使用 `com.android.test`，并把 app 配置为 target project；不要直接把 Macrobenchmark 当作普通 app 模块 UI 测试。

```kotlin
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun coldStart() = benchmarkRule.measureRepeated(
        packageName = "com.example.app",
        metrics = listOf(StartupTimingMetric(), FrameTimingMetric()),
        iterations = 5,
        startupMode = StartupMode.COLD
    ) {
        pressHome()
        startActivityAndWait()
    }
}
```

任务名由 benchmark 模块、构建类型和设备配置共同决定。先运行 `./gradlew :benchmark:tasks` 查看实际任务；使用 Android Studio 模板时，也可以直接运行生成的 Benchmark 配置。被测 app 应使用接近 Release 且可供 Macrobenchmark 测量的专用构建类型。

## 4. 逐步解释

1. Layout Inspector 能看什么：在 Android Studio 里连接运行中的进程并查看 Compose 节点，可以观察组合结构、重组次数和跳过次数。计数只能帮助发现可疑范围，不能单独证明卡顿；最终仍要结合系统 Trace、Macrobenchmark 或其他帧指标确认真实耗时。

2. 编译器报告是什么：Compose 编译器可以输出类型稳定性、Composable 属性和模块指标，帮助解释 restartable、skippable 等推断。Kotlin 2.0.20 起默认启用强跳过后，带不稳定参数的 restartable Composable 也可能成为 skippable，因此不能再使用“出现 unstable 就一定无法跳过”的旧结论。

3. 不稳定参数意味着什么：编译器不能确认变化能被可靠观察。强跳过模式对稳定参数使用 `equals`，对不稳定参数使用引用相等。`List` 被认为不稳定，是因为接口不能保证内容不可变，并不是因为 Kotlin `List.equals` 不可靠。可选方案包括不可变 UI model、SnapshotState 集合、kotlinx immutable collection，或在确实满足契约时使用稳定性注解。先测量再选择，便宜的重组可能比复杂的相等检查更划算。

4. 减少无效工作的顺序：① 把状态读取移动到真正消费它的最小范围；② 保持 UI State 不可变、变化方式清晰；③ 检查昂贵计算、布局和绘制是否真的重复执行。强跳过模式会自动记忆组合中创建的多数 lambda，不应再把“手动 remember 每个 lambda”当成固定优化套路。

5. 从猜测到测量：先在独立 benchmark 模块建立基线，再用 Layout Inspector、Trace 和编译器报告缩小范围，修改后运行同一组 benchmark 对比。Debug 构建不代表线上性能；被测构建应使用模板生成或自行配置的 release-like benchmark Build Type，并明确是否启用 R8/Baseline Profile，保证前后条件一致。

## 5. 实际执行过程

1. 开发者在首页输入框打字，感觉卡顿，先用 Layout Inspector Attach 进程，看到整页组合树的重组次数在疯狂增长，Header 和图表节点同样在涨。
2. 查看当前编译器生成的稳定性与 Composable 报告，发现某参数被推断为 unstable，并核对强跳过下使用的比较策略。
3. 按报告把状态下沉到 NameEditor，参数换成不可变 data class 并确保 `@Immutable` 名副其实，重新构建。
4. 再跑 Layout Inspector，确认状态读取范围缩小；Header/图表是否真正减少工作仍以 Trace 或基准数据为准。
5. 用独立 benchmark 模块在相同构建条件和设备上重复测量，确认指标改善后再保留优化。

## 6. Flutter / Dart 3 对照

先看相似点：两个框架都有「组件 / 构建层」与「渲染层」的多层分析工具。Flutter 通常使用 `flutter run --profile` 配合 DevTools Timeline；Android Macrobenchmark 则使用独立测试模块和 release-like benchmark 构建。两者都不能用普通 Debug 性能代表线上：

```dart
// 运行时打开 Performance overlay
MaterialApp(
  showPerformanceOverlay: true, // 显示 UI / Raster 帧耗时条
  home: const HomeScreen(),
);
```

本质区别：Compose 的「跳过重组」依赖编译器对参数稳定性的推断，所以需要编译器报告这种静态分析工具；Flutter 的重建是 widget 树级 diff（canUpdate 判断 runtimeType + key），不需要参数稳定性声明，但性能问题的定位更依赖 DevTools 的逐帧数据。也就是说：Compose 更「静态可分析」（报告能提前告诉你哪里会重组），Flutter 更「运行时实测」（靠 Timeline 看真实耗时）。

记住：两个框架都坚持「先测量再优化」，Compose 多给你一份编译期体检报告，别浪费它。

## 7. 常见错误

1. 凭感觉优化：不看任何数据就把 Column 改 LazyColumn、到处乱加 remember，改完也不知道有没有效。任何优化先建 baseline。
2. 只盯重组次数：重组次数少不等于快——布局耗时、绘制耗时（Layout Inspector 的 Layout time）可能才是大头。「卡顿、重组过多、布局过深」三类问题要三类工具分别看。
3. 把编译器报告当结论或完全不看：报告用于解释编译器推断，但 unstable 不是必须优化的错误，仍需结合真实耗时。
4. 直接用 Debug 代表线上性能，或前后使用不同构建条件：应使用 release-like benchmark 构建，并固定设备、R8、Baseline Profile 与测试步骤。
5. 在可变类上乱标 `@Immutable`：为了「让报告变绿」把带 var 的类标成不可变，跳过重组后行为错乱——这是用性能换正确性，得不偿失。

## 8. 本章检查题

1. Layout Inspector 能看哪些信息？怎么判断真正的重组浪费？
2. 编译器报告里的 unstable 意味着什么？会造成什么后果？
3. 为什么不能使用普通 Debug 构建代表线上性能？
4. 减少无效工作的正确顺序是什么？

答案：

1. 组合树、节点的重组和跳过计数等信息。计数用于发现可疑范围，是否真正浪费必须结合 Trace 或基准耗时判断。
2. 编译器不能确认它的变化能被可靠观察；强跳过模式下通常按引用相等比较。它是诊断线索，不是性能问题的直接证据。
3. Debug 缺少线上优化；基准测试应使用独立 benchmark 模块和固定的 release-like 被测构建，保持前后条件一致。
4. 缩小状态读取范围；保持状态模型和变化方式可靠；用工具找到真正重复的昂贵工作，再做最小优化。

## 9. 本章总结

性能优化的第一原则是「先测量，再动手」：Layout Inspector 和 Trace 帮助观察运行时行为，编译器报告解释编译期推断，独立 Macrobenchmark 模块建立可复现基线。计数和 unstable 标记只是线索，只有相同条件下的耗时数据才能证明优化有效。

官方参考：

- [Macrobenchmark overview](https://developer.android.com/topic/performance/benchmarking/macrobenchmark-overview)
- [Diagnose stability issues](https://developer.android.com/develop/ui/compose/performance/stability/diagnose)
