# Compose 项目完整学习文档（预备篇 + 第 1—80 章）

- 预备篇：面向真正零基础读者，完成环境准备、第一次构建、Gradle 基础与构建报错自救。
- 第 1—45 章：根据 `compose_demo.zip` 源码整理，覆盖 Compose UI 从入门到中级的基础。
- 第 46—80 章：进阶篇，覆盖重组引擎、动画、自定义绘制/布局/手势、架构、性能、测试与发布。

零基础读者必须先完成预备篇，再按编号阅读。已有 Android/Gradle 经验的读者可以从第 1 章开始。

> 当前仓库只包含学习文档，没有附带 `compose_demo` 源码。第 1—45 章中的源码路径用于说明原始示例位置；没有该工程的读者，可以先按预备篇创建 Empty Activity 项目，再运行可独立复制的示例。

## 文件说明

- `00_完整学习规划.md`：总规划。
- `00A—00E`：零基础环境、Gradle、构建排错、Kotlin 与 Android 预备篇。
- `01—45`：独立章节。
- `08A`：FlowRow 流式布局（基础篇补充章节，接在第 8 章后）。
- `26A`、`26B`：输入框与选择控件（基础篇补充章节，接在第 26 章后）。
- `99_源码文件索引.md`：从源码文件反查章节。

## 开始之前：零基础预备篇

- [预备篇 A：零基础环境准备与第一次构建](00A_零基础环境准备与第一次构建.md)
- [预备篇 B：Gradle 构建系统从入门到理解](00B_Gradle构建系统从入门到理解.md)
- [预备篇 C：Gradle 构建报错定位与自救](00C_Gradle构建报错定位与自救.md)
- [预备篇 D：Kotlin 基础与 Compose 必读语法](00D_Kotlin基础与Compose必备语法.md)
- [预备篇 E：Android 项目、资源、生命周期与日志基础](00E_Android项目运行基础.md)

## 章节

- [第 1 章：MainActivity、ComponentActivity、onCreate 与 setContent](01_MainActivity与setContent.md)
- [第 2 章：`@Composable` 与 Composable 函数](02_Composable函数.md)
- [第 3 章：DemoTheme、MaterialTheme 与 CompositionLocalProvider](03_DemoTheme与MaterialTheme.md)
- [第 4 章：rememberNavController、NavHost、composable 与路由参数](04_NavHost与路由导航.md)
- [第 5 章：Lambda、点击回调与 item 到导航的完整链路](05_Lambda点击回调与导航链路.md)
- [第 6 章：Scaffold、TopAppBar 与内容区 padding](06_Scaffold、TopAppBar与内容区padding.md)
- [第 7 章：Modifier 的本质、作用范围与调用顺序](07_Modifier的本质、作用范围与调用顺序.md)
- [第 8 章：Column、Row、Arrangement 与 Alignment](08_Column、Row、Arrangement与Alignment.md)
- [第 8A 章：FlowRow 与流式布局](08A_FlowRow与流式布局.md)
- [第 9 章：Box、层叠布局与 Flutter Stack](09_Box、层叠布局与FlutterStack.md)
- [第 10 章：Card、Surface 与容器组件的区别](10_Card、Surface与容器组件的区别.md)
- [第 11 章：Text、TextStyle、FontWeight 与 lineHeight](11_Text、TextStyle、FontWeight与lineHeight.md)
- [第 12 章：Image、Painter、contentDescription 与 ContentScale](12_Image、Painter、contentDescription与ContentScale.md)
- [第 13 章：padding、size、fillMaxWidth 与 fillMaxSize](13_padding、size、fillMaxWidth与fillMaxSize.md)
- [第 14 章：weight、Spacer 与 Flutter Expanded](14_weight、Spacer与FlutterExpanded.md)
- [第 15 章：RoundedCornerShape、background、clip 与 border](15_RoundedCornerShape、background、clip与border.md)
- [第 16 章：LazyColumn、item、items 与 itemsIndexed](16_LazyColumn、item、items与itemsIndexed.md)
- [第 17 章：key 与列表项身份](17_key与列表项身份.md)
- [第 18 章：LazyVerticalGrid 与 GridCells](18_LazyVerticalGrid与GridCells.md)
- [第 19 章：verticalScroll、horizontalScroll 与 rememberScrollState](19_verticalScroll、horizontalScroll与rememberScrollState.md)
- [第 20 章：Lazy 容器与普通滚动容器的选择](20_Lazy容器与普通滚动容器的选择.md)
- [第 21 章：remember、mutableStateOf 与状态委托](21_remember、mutableStateOf与状态委托.md)
- [第 22 章：重组什么时候发生，什么代码可能重复执行](22_重组什么时候发生，什么代码可能重复执行.md)
- [第 23 章：rememberSaveable 与配置变化后的状态恢复](23_rememberSaveable与配置变化后的状态恢复.md)
- [第 24 章：状态提升：状态放在哪里，事件回调传向哪里](24_状态提升：状态放在哪里，事件回调传向哪里.md)
- [第 25 章：SideEffect、LaunchedEffect 与普通业务代码的边界](25_SideEffect、LaunchedEffect与普通业务代码的边界.md)
- [第 26 章：Button、IconButton 与 Icon](26_Button、IconButton与Icon.md)
- [第 26A 章：TextField、OutlinedTextField 与输入状态](26A_TextField、OutlinedTextField与输入状态.md)
- [第 26B 章：Checkbox、Switch 与 Slider 选择控件](26B_Checkbox、Switch与Slider选择控件.md)
- [第 27 章：AlertDialog](27_AlertDialog.md)
- [第 28 章：ModalBottomSheet](28_ModalBottomSheet.md)
- [第 29 章：SnackbarHostState、协程与 Snackbar](29_SnackbarHostState、协程与Snackbar.md)
- [第 30 章：CircularProgressIndicator 与 LinearProgressIndicator](30_CircularProgressIndicator与LinearProgressIndicator.md)
- [第 31 章：NavigationBar 与 NavigationBarItem](31_NavigationBar与NavigationBarItem.md)
- [第 32 章：TabRow 与 Tab](32_TabRow与Tab.md)
- [第 33 章：Compose 中模拟 Flutter IndexedStack](33_Compose中模拟FlutterIndexedStack.md)
- [第 34 章：页面切换时如何保留状态](34_页面切换时如何保留状态.md)
- [第 35 章：组件内部状态与页面导航状态的区别](35_组件内部状态与页面导航状态的区别.md)
- [第 36 章：ComponentInfo 类及其四个字段](36_ComponentInfo类及其四个字段.md)
- [第 37 章：@Composable () -> Unit 作为可运行 UI 数据](37_@Composable()-_Unit作为可运行UI数据.md)
- [第 38 章：Kotlin object ComponentRegistry](38_KotlinobjectComponentRegistry.md)
- [第 39 章：List、firstOrNull 与可空返回值](39_List、firstOrNull与可空返回值.md)
- [第 40 章：添加一个新 Demo 的完整步骤](40_添加一个新Demo的完整步骤.md)
- [第 41 章：Density 到底是什么](41_Density到底是什么.md)
- [第 42 章：CompositionLocalProvider 与 LocalDensity](42_CompositionLocalProvider与LocalDensity.md)
- [第 43 章：DesignWidthScale 如何影响整个子树](43_DesignWidthScale如何影响整个子树.md)
- [第 44 章：.px 与 .pxSp() 扩展属性和函数](44_.px与.pxSp()扩展属性和函数.md)
- [第 45 章：与 Flutter 设计稿适配的对比、风险与建议](45_与Flutter设计稿适配的对比、风险与建议.md)

## 进阶篇（第 46—80 章）

- [第 46 章：重组的跳过机制与位置记忆](46_重组的跳过机制与位置记忆.md)
- [第 47 章：稳定性（Stability）与强跳过模式](47_稳定性与强跳过模式.md)
- [第 48 章：derivedStateOf：派生状态](48_derivedStateOf派生状态.md)
- [第 49 章：rememberUpdatedState 与 rememberCoroutineScope](49_rememberUpdatedState与rememberCoroutineScope.md)
- [第 50 章：snapshotFlow 与 Flow 数据接入](50_snapshotFlow与Flow数据接入.md)
- [第 51 章：animate*AsState 与 Tween 基础动画](51_animateAsState与Tween基础动画.md)
- [第 52 章：Animatable 与手动动画控制](52_Animatable与手动动画控制.md)
- [第 53 章：AnimatedVisibility、AnimatedContent 与 Crossfade](53_AnimatedVisibility与AnimatedContent与Crossfade.md)
- [第 54 章：updateTransition 多状态动画编排](54_updateTransition多状态动画编排.md)
- [第 55 章：动画规格：spring、keyframes、repeatable 与 easing](55_动画规格spring与keyframes与repeatable.md)
- [第 56 章：rememberInfiniteTransition 与无限动画](56_rememberInfiniteTransition无限动画.md)
- [第 57 章：Canvas 与 DrawScope 基础](57_Canvas与DrawScope基础.md)
- [第 58 章：drawBehind、drawWithContent 与 drawWithCache](58_drawBehind与drawWithContent与drawWithCache.md)
- [第 59 章：Path、贝塞尔曲线与裁剪](59_Path贝塞尔曲线与裁剪.md)
- [第 60 章：Brush、Shader 与渐变](60_BrushShader与渐变.md)
- [第 61 章：富文本：AnnotatedString、Span 与文本绘制](61_富文本AnnotatedString与Span.md)
- [第 62 章：自定义 Layout：measure 与 place](62_自定义Layout与measure与place.md)
- [第 63 章：Constraints 与固有尺寸测量](63_Constraints与固有尺寸测量.md)
- [第 64 章：自定义 Modifier：layout、draw、pointerInput](64_自定义Modifier.md)
- [第 65 章：手势检测：pointerInput 与手势探测器](65_手势检测pointerInput.md)
- [第 66 章：手势冲突与嵌套滚动](66_手势冲突与嵌套滚动.md)
- [第 67 章：AndroidView 与 ComposeView：与 View 体系互操作](67_AndroidView与ComposeView互操作.md)
- [第 68 章：ViewModel、StateFlow 与 UDF 单向数据流](68_ViewModel与StateFlow与UDF.md)
- [第 69 章：Hilt 依赖注入](69_Hilt依赖注入.md)
- [第 70 章：生命周期感知：repeatOnLifecycle 与 LifecycleEventEffect](70_生命周期感知repeatOnLifecycle.md)
- [第 71 章：导航进阶：嵌套导航图与类型安全导航](71_导航进阶嵌套导航与类型安全导航.md)
- [第 72 章：状态持久化：rememberSaveable、DataStore 与 Room 的边界](72_状态持久化DataStore与Room.md)
- [第 73 章：协程进阶：Dispatchers、结构化并发与 Flow 操作符](73_协程进阶Dispatcher与Flow操作符.md)
- [第 74 章：性能分析：Layout Inspector 与编译器报告](74_性能分析LayoutInspector与编译器报告.md)
- [第 75 章：Lazy 列表性能优化](75_Lazy列表性能优化.md)
- [第 76 章：Compose UI 测试：语义树与交互测试](76_ComposeUI测试.md)
- [第 77 章：主题进阶：动态取色、Shape 与 Typography](77_主题进阶动态取色与Shape与Typography.md)
- [第 78 章：边缘到边缘与 Window Insets](78_边缘到边缘与WindowInsets.md)
- [第 79 章：响应式布局与 WindowSizeClass](79_响应式布局与WindowSizeClass.md)
- [第 80 章：无障碍、Baseline Profile 与发布质量](80_无障碍与BaselineProfile与发布质量.md)
