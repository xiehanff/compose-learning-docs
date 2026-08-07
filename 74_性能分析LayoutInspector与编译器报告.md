# 第 74 章：性能分析：Layout Inspector 与编译器报告

## 1. 本章目标

学完后，你应当能够：

- 用 Layout Inspector 查看组合树、重组计数、跳过次数与布局耗时，定位「为什么这里一直在重组」
- 配置 Compose 编译器报告，读懂 classes.txt / metrics 里的 `@bad`、`unstable` 标记
- 掌握弱化重组的思路：状态提升到最小范围、稳定参数、避免 Lambda 传递
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

构建后 `compose_compiler/` 下生成 classes.txt（类稳定性）与 compose-reports.txt（可跳过 / 可重启列表）；`compose_metrics/` 下生成模块级汇总。

场景二：一条典型「过度重组」代码与修复（把状态从大范围降到小范围）。

```kotlin
// 错误示范：状态挂在页面级，任何输入变化整页重组
@Composable
fun ProfileScreen() {
    var name by remember { mutableStateOf("") }
    Column {
        Header()         // 与 name 无关，却被反复重组
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

场景三：Macrobenchmark 冷启动与帧率测量（androidTest 源码集）。

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

运行：`./gradlew :app:connectedBenchmarkAndroidTest`，需要 profile 构建变体。

## 4. 逐步解释

1. Layout Inspector 能看什么：在 Android Studio 里 Attach 到运行中的进程，切到 Compose 视图，能看到每个节点的组合信息——重组次数（Recomposition count）、跳过次数（Skipped count）和布局耗时。判断依据：节点重组次数高但跳过次数也高，说明它经常被调用但什么都没做，问题不大；跳过次数低、重组次数高，才是真正的浪费。布局耗时高说明问题在 measure / layout 而不是组合。

2. 编译器报告是什么：Compose 编译器在编译期生成两份报告。classes.txt 列出每个类的稳定性：稳定的类可放心参与跳过判断，标了 `@bad` / `unstable` 的类说明「本该稳定却标错 / 被推断为不稳定」；compose-reports.txt 列出每个 composable 是否 restartable（可重组）、skippable（参数没变时可跳过）。一个 composable 的参数里有 unstable 类型，它就无法 skippable——参数一变，哪怕其他参数没变也要全量重组。

3. 不稳定参数为什么致命：Compose 靠「参数是否稳定」判断要不要跳过重组。不可变 data class 的值比较是可靠的，编译器认定它稳定；而 interface、带 var 属性的普通类、集合（List 的 equals 不可靠）常被标记 unstable。让参数稳定的办法：参数用不可变 data class、`@Immutable` / `@Stable` 注解（前提是类真的不可变——在可变类上乱标会引入 bug）、Lambda 用 remember 包住、列表用 immutable 库的 PersistentList。

4. 弱化重组的三板斧：① 状态提升到最小范围——谁用状态谁持有，见场景二的修复；② 参数保持稳定——不可变 data class、`@Immutable` 名副其实；③ 避免把新 Lambda 传给子 composable——每次重组都新建 Lambda，子项参数「看起来变了」被迫重组，用 remember 缓存或改传稳定回调。

5. 从猜测到测量：肉眼和直觉判断性能最不可靠。正确流程：先写 Macrobenchmark 建立基线 → 用 Layout Inspector / 编译器报告定位 → 修改 → 再跑同一个 benchmark 对比。StartupTimingMetric 测冷 / 温启动时间，FrameTimingMetric 统计帧时长分布与掉帧率。注意 benchmark 必须跑 profile 构建：debug 构建没有优化、测出来虚低；release 不可调试且 R8 可能改变行为；profile 是「接近线上又有可测性」的中间态。

## 5. 实际执行过程

1. 开发者在首页输入框打字，感觉卡顿，先用 Layout Inspector Attach 进程，看到整页组合树的重组次数在疯狂增长，Header 和图表节点同样在涨。
2. 查看编译器报告 classes.txt：发现 ProfileScreen 传给子项的某个参数类被标 `@bad` / unstable。
3. 按报告把状态下沉到 NameEditor，参数换成不可变 data class 并确保 `@Immutable` 名副其实，重新构建。
4. 再跑 Layout Inspector：页面级节点重组次数归零，只有 NameEditor 在变。
5. 用 Macrobenchmark 的 StartupTimingMetric + FrameTimingMetric 在 profile 构建上跑 5 轮，确认帧率指标改善，把优化固化成可回归的测试。

## 6. Flutter / Dart 3 对照

先看相似点：两个框架都有「组件 / 构建层」与「渲染层」的两层剖析。Flutter 的对应物是 DevTools Timeline（逐帧看 build / layout / paint 耗时）与 Performance overlay（真机显示 UI 线程与栅格线程的帧耗时），`flutter run --profile` 对应 Compose 的 profile 构建：

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
3. 把编译器报告当摆设：生成了 classes.txt 从不看，unstable 参数一直躺在代码里。报告是免费的体检，编译一次就能看。
4. 在 debug 或 release 下测性能：debug 构建没有编译期优化、帧率虚低；release 不可调试、R8 可能改变行为；Macrobenchmark 必须配 profile 构建变体，否则数字没有参考意义。
5. 在可变类上乱标 `@Immutable`：为了「让报告变绿」把带 var 的类标成不可变，跳过重组后行为错乱——这是用性能换正确性，得不偿失。

## 8. 本章检查题

1. Layout Inspector 能看哪些信息？怎么判断真正的重组浪费？
2. 编译器报告里的 unstable 意味着什么？会造成什么后果？
3. 为什么性能测试必须用 profile 构建？
4. 弱化重组的三板斧是什么？

答案：

1. 组合树、每个节点的重组次数与跳过次数、布局耗时。判断标准：重组次数高且跳过次数低才是浪费；跳过次数高说明经常被调用但什么都没做。
2. 该参数类型被编译器判定为「不稳定」——值是否变化无法可靠判断，导致包含它的 composable 无法标记 skippable，参数一变就全量重组。后果是性能下降、重组范围失控。
3. debug 构建没有编译期优化，测出来虚低；release 不可调试且 R8 可能改变行为；profile 构建保留优化、又支持调试与基准测量，是「接近线上又有可测性」的中间态。
4. 状态提升到最小范围；参数保持稳定（不可变 data class、`@Immutable` 名副其实）；避免每次重组都创建新 Lambda。

## 9. 本章总结

性能优化的第一原则是「先测量，再动手」：Layout Inspector 看运行时组合行为，编译器报告看编译期稳定性，Macrobenchmark 建立可复现的基线。三类工具对应三类问题，配合「状态下沉、参数稳定、Lambda 缓存」三板斧，卡顿的定位与修复就不再靠猜。
