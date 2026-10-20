# 代码示例编译校验清单（自动生成）

由 `python3 tools/extract_snippets.py` 从各章 `*.md` 中抽取生成，请勿手改。

标记方法见 `README.md` 的「代码示例编译校验」一节。

注意：生成文件会为「节选片段」统一加上 `@file:OptIn(...)`，它只保证片段能编译，
**不代表章节正文已说明需要 opt-in**；弃用 API 也不会在普通编译里报错，
由独立严格编译发现：`./gradlew compileDebugKotlin -PsnippetsStrictWarnings --rerun-tasks`。

共 167 个片段待校验。

| 生成文件 | 章号 | 来源（文件:行号） | 模式 | 片段首行 |
| --- | --- | --- | --- | --- |
| `Ch02_01.kt` | Ch02 | `02_Composable函数.md:49` | body | `fun add(a: Int, b: Int): Int {` |
| `Ch02_02.kt` | Ch02 | `02_Composable函数.md:82` | decls | `@Composable` |
| `Ch02_03.kt` | Ch02 | `02_Composable函数.md:159` | decls | `@Composable` |
| `Ch02_04.kt` | Ch02 | `02_Composable函数.md:182` | decls | `fun buildTitle(): String {` |
| `Ch02_05.kt` | Ch02 | `02_Composable函数.md:279` | decls | `@Composable` |
| `Ch02_06.kt` | Ch02 | `02_Composable函数.md:340` | decls | `@Composable` |
| `Ch02_07.kt` | Ch02 | `02_Composable函数.md:422` | body | `Column {` |
| `Ch02_08.kt` | Ch02 | `02_Composable函数.md:454` | decls | `@Composable` |
| `Ch02_09.kt` | Ch02 | `02_Composable函数.md:509` | decls | `@Composable` |
| `Ch04_01.kt` | Ch04 | `04_NavHost与路由导航.md:586` | decls | `object Routes {` |
| `Ch05_01.kt` | Ch05 | `05_Lambda点击回调与导航链路.md:505` | body | `Text(` |
| `Ch05_02.kt` | Ch05 | `05_Lambda点击回调与导航链路.md:670` | body | `fun receiveClick(onClick: () -> Unit) {` |
| `Ch06_01.kt` | Ch06 | `06_Scaffold、TopAppBar与内容区padding.md:28` | body | `Scaffold(` |
| `Ch06_02.kt` | Ch06 | `06_Scaffold、TopAppBar与内容区padding.md:57` | decls | `@OptIn(ExperimentalMaterial3Api::class) // TopAppBar 在本仓库基线（material3 1.4.x）仍...` |
| `Ch07_01.kt` | Ch07 | `07_Modifier的本质、作用范围与调用顺序.md:34` | body | `Text(` |
| `Ch07_02.kt` | Ch07 | `07_Modifier的本质、作用范围与调用顺序.md:71` | body | `Column(` |
| `Ch07_03.kt` | Ch07 | `07_Modifier的本质、作用范围与调用顺序.md:110` | body | `var clicksInner by remember { mutableStateOf(0) }` |
| `Ch07_04.kt` | Ch07 | `07_Modifier的本质、作用范围与调用顺序.md:158` | body | `Column(` |
| `Ch08A_01.kt` | Ch08A | `08A_FlowRow与流式布局.md:27` | body | `// 先用固定列表观察布局；动态增删会在第 21—22 章讲状态后再练` |
| `Ch08A_02.kt` | Ch08A | `08A_FlowRow与流式布局.md:85` | body | `val colors = listOf(` |
| `Ch08_01.kt` | Ch08 | `08_Column、Row、Arrangement与Alignment.md:33` | body | `Column(` |
| `Ch08_02.kt` | Ch08 | `08_Column、Row、Arrangement与Alignment.md:68` | body | `Column(` |
| `Ch08_03.kt` | Ch08 | `08_Column、Row、Arrangement与Alignment.md:131` | body | `Column(` |
| `Ch08_04.kt` | Ch08 | `08_Column、Row、Arrangement与Alignment.md:179` | body | `Column(` |
| `Ch09_01.kt` | Ch09 | `09_Box、层叠布局与FlutterStack.md:33` | body | `Box(` |
| `Ch09_02.kt` | Ch09 | `09_Box、层叠布局与FlutterStack.md:59` | body | `Box(` |
| `Ch09_03.kt` | Ch09 | `09_Box、层叠布局与FlutterStack.md:80` | body | `var hitResult by remember { mutableStateOf("点击两块色块试试") }` |
| `Ch09_04.kt` | Ch09 | `09_Box、层叠布局与FlutterStack.md:121` | body | `Box(modifier = Modifier.size(56.dp)) {` |
| `Ch10_01.kt` | Ch10 | `10_Card、Surface与容器组件的区别.md:28` | body | `Card(` |
| `Ch10_02.kt` | Ch10 | `10_Card、Surface与容器组件的区别.md:69` | body | `Column(` |
| `Ch10_03.kt` | Ch10 | `10_Card、Surface与容器组件的区别.md:119` | body | `var clickCount by remember { mutableStateOf(0) } // 计数写法见第 7 章 4.3 节` |
| `Ch11_01.kt` | Ch11 | `11_Text、TextStyle、FontWeight与lineHeight.md:52` | body | `Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {` |
| `Ch11_02.kt` | Ch11 | `11_Text、TextStyle、FontWeight与lineHeight.md:109` | body | `val longText = "这是一段足够长的示例文字，用来演示换行与截断行为，宽度不够时文字会先尝试换行"` |
| `Ch12_01.kt` | Ch12 | `12_Image、Painter、contentDescription与ContentScale.md:29` | body | `Image(` |
| `Ch12_02.kt` | Ch12 | `12_Image、Painter、contentDescription与ContentScale.md:48` | body | `// 模拟一张宽 40、高 80（宽高比 1:2）的源图` |
| `Ch13_01.kt` | Ch13 | `13_padding、size、fillMaxWidth与fillMaxSize.md:32` | body | `LazyColumn(` |
| `Ch13_02.kt` | Ch13 | `13_padding、size、fillMaxWidth与fillMaxSize.md:62` | body | `Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {` |
| `Ch13_03.kt` | Ch13 | `13_padding、size、fillMaxWidth与fillMaxSize.md:109` | body | `Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {` |
| `Ch13_04.kt` | Ch13 | `13_padding、size、fillMaxWidth与fillMaxSize.md:131` | body | `Column(` |
| `Ch14_01.kt` | Ch14 | `14_weight、Spacer与FlutterExpanded.md:28` | body | `Row(Modifier.fillMaxWidth()) {` |
| `Ch14_02.kt` | Ch14 | `14_weight、Spacer与FlutterExpanded.md:49` | body | `Row(` |
| `Ch14_03.kt` | Ch14 | `14_weight、Spacer与FlutterExpanded.md:90` | body | `Row(Modifier.fillMaxWidth()) {` |
| `Ch14_04.kt` | Ch14 | `14_weight、Spacer与FlutterExpanded.md:106` | body | `Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {` |
| `Ch15_01.kt` | Ch15 | `15_RoundedCornerShape、background、clip与border.md:31` | body | `Box(` |
| `Ch15_02.kt` | Ch15 | `15_RoundedCornerShape、background、clip与border.md:61` | body | `val shape = RoundedCornerShape(16.dp)` |
| `Ch15_03.kt` | Ch15 | `15_RoundedCornerShape、background、clip与border.md:87` | body | `Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {` |
| `Ch16_01.kt` | Ch16 | `16_LazyColumn、item、items与itemsIndexed.md:47` | decls | `// 最小数据模型：id 是每条数据的稳定身份（第 17 章用它作 key），title 是展示文本` |
| `Ch16_02.kt` | Ch16 | `16_LazyColumn、item、items与itemsIndexed.md:115` | body | `LazyColumn {` |
| `Ch17_01.kt` | Ch17 | `17_key与列表项身份.md:43` | decls | `// 待办模型：id 是业务身份（稳定且不重复）；title 只是展示文本，可能重复、可能修改` |
| `Ch17_02.kt` | Ch17 | `17_key与列表项身份.md:166` | body | `// 用索引作 key：身份仍然是位置，效果与无 key 基本相同` |
| `Ch17_03.kt` | Ch17 | `17_key与列表项身份.md:185` | body | `// 想让勾选状态在条目滚出很远、再滚回来时也保留：rememberSaveable + key` |
| `Ch18_01.kt` | Ch18 | `18_LazyVerticalGrid与GridCells.md:28` | body | `LazyVerticalGrid(` |
| `Ch18_02.kt` | Ch18 | `18_LazyVerticalGrid与GridCells.md:46` | decls | `// 固定 3 列：无论多宽，列数不变，每列随宽度伸缩` |
| `Ch18_03.kt` | Ch18 | `18_LazyVerticalGrid与GridCells.md:72` | decls | `// 自适应：每列不小于 100dp，宽度够就多放列` |
| `Ch18_04.kt` | Ch18 | `18_LazyVerticalGrid与GridCells.md:153` | decls | `// 相册模型：id 用作条目身份（第 17 章）` |
| `Ch19_01.kt` | Ch19 | `19_verticalScroll、horizontalScroll与rememberScrollState.md:27` | body | `Column(` |
| `Ch19_02.kt` | Ch19 | `19_verticalScroll、horizontalScroll与rememberScrollState.md:45` | decls | `@Composable` |
| `Ch19_03.kt` | Ch19 | `19_verticalScroll、horizontalScroll与rememberScrollState.md:101` | body | `// 横向标签栏：内容宽度超出视口后可左右滚动` |
| `Ch20_01.kt` | Ch20 | `20_Lazy容器与普通滚动容器的选择.md:44` | decls | `// 同一份消息数据放进两种容器：上半 Column+verticalScroll，下半 LazyColumn` |
| `Ch21_01.kt` | Ch21 | `21_remember、mutableStateOf与状态委托.md:53` | body | `var expanded by remember { mutableStateOf(false) }` |
| `Ch21_02.kt` | Ch21 | `21_remember、mutableStateOf与状态委托.md:74` | body | `val expanded = remember { mutableStateOf(false) }   // 不用 by：拿到的是 MutableStat...` |
| `Ch21_03.kt` | Ch21 | `21_remember、mutableStateOf与状态委托.md:95` | body | `var userId by remember { mutableIntStateOf(1) }        // 会话 id：点击按钮切换` |
| `Ch22_01.kt` | Ch22 | `22_重组什么时候发生，什么代码可能重复执行.md:36` | body | `val imageBitmap = remember {` |
| `Ch22_02.kt` | Ch22 | `22_重组什么时候发生，什么代码可能重复执行.md:50` | decls | `import android.util.Log` |
| `Ch22_03.kt` | Ch22 | `22_重组什么时候发生，什么代码可能重复执行.md:75` | decls | `import android.util.Log` |
| `Ch23_01.kt` | Ch23 | `23_rememberSaveable与配置变化后的状态恢复.md:32` | body | `// components/StateDemo.kt:135：只要跨重组保留，用 remember` |
| `Ch23_02.kt` | Ch23 | `23_rememberSaveable与配置变化后的状态恢复.md:46` | decls | `@Composable` |
| `Ch23_03.kt` | Ch23 | `23_rememberSaveable与配置变化后的状态恢复.md:69` | decls | `data class Filter(val query: String, val onlyFavorite: Boolean)` |
| `Ch24_01.kt` | Ch24 | `24_状态提升：状态放在哪里，事件回调传向哪里.md:34` | decls | `// 节选：单个下拉框——「输入框展示选中值 + 下拉菜单上报选择」` |
| `Ch24_02.kt` | Ch24 | `24_状态提升：状态放在哪里，事件回调传向哪里.md:77` | decls | `@Composable` |
| `Ch24_03.kt` | Ch24 | `24_状态提升：状态放在哪里，事件回调传向哪里.md:95` | decls | `@Composable` |
| `Ch24_04.kt` | Ch24 | `24_状态提升：状态放在哪里，事件回调传向哪里.md:122` | decls | `@Composable` |
| `Ch25_01.kt` | Ch25 | `25_SideEffect、LaunchedEffect与普通业务代码的边界.md:72` | decls | `@Composable` |
| `Ch25_02.kt` | Ch25 | `25_SideEffect、LaunchedEffect与普通业务代码的边界.md:100` | decls | `@Composable` |
| `Ch25_03.kt` | Ch25 | `25_SideEffect、LaunchedEffect与普通业务代码的边界.md:127` | decls | `@Composable` |
| `Ch26A_01.kt` | Ch26A | `26A_TextField、OutlinedTextField与输入状态.md:36` | body | `var name by remember { mutableStateOf("") }` |
| `Ch26A_02.kt` | Ch26A | `26A_TextField、OutlinedTextField与输入状态.md:86` | body | `import androidx.compose.foundation.text.input.InputTransformation` |
| `Ch26A_03.kt` | Ch26A | `26A_TextField、OutlinedTextField与输入状态.md:224` | body | `import androidx.compose.ui.focus.FocusRequester` |
| `Ch26B_01.kt` | Ch26B | `26B_Checkbox、Switch与Slider选择控件.md:27` | body | `var agreed by remember { mutableStateOf(false) }          // 是否同意协议` |
| `Ch26B_02.kt` | Ch26B | `26B_Checkbox、Switch与Slider选择控件.md:86` | body | `// toggleable 在 foundation.selection 子包，IMPORT_HEADER 未覆盖，需在片段顶部自行导入` |
| `Ch26B_03.kt` | Ch26B | `26B_Checkbox、Switch与Slider选择控件.md:139` | body | `var dragValue by remember { mutableFloatStateOf(20f) }   // 拖动过程中的值（实时变化）` |
| `Ch26_01.kt` | Ch26 | `26_Button、IconButton与Icon.md:31` | body | `// components/ButtonDemo.kt：五种风格按钮，按视觉权重从高到低排列` |
| `Ch26_02.kt` | Ch26 | `26_Button、IconButton与Icon.md:64` | body | `var submitting by remember { mutableStateOf(false) }` |
| `Ch27_01.kt` | Ch27 | `27_AlertDialog.md:29` | body | `if (showDialog) {` |
| `Ch27_02.kt` | Ch27 | `27_AlertDialog.md:54` | body | `import androidx.compose.ui.window.DialogProperties` |
| `Ch28_01.kt` | Ch28 | `28_ModalBottomSheet.md:29` | body | `if (showSheet) {` |
| `Ch28_02.kt` | Ch28 | `28_ModalBottomSheet.md:49` | decls | `@OptIn(ExperimentalMaterial3Api::class) // ModalBottomSheet 在本仓库基线（material3 ...` |
| `Ch29_01.kt` | Ch29 | `29_SnackbarHostState、协程与Snackbar.md:28` | body | `val hostState = remember { SnackbarHostState() }` |
| `Ch29_02.kt` | Ch29 | `29_SnackbarHostState、协程与Snackbar.md:48` | decls | `@Composable` |
| `Ch29_03.kt` | Ch29 | `29_SnackbarHostState、协程与Snackbar.md:99` | body | `// currentSnackbarData 是 HostState 当前正在显示的那条；dismiss() 让它立即结束，` |
| `Ch30_01.kt` | Ch30 | `30_CircularProgressIndicator与LinearProgressIndicator.md:30` | body | `CircularProgressIndicator()` |
| `Ch30_02.kt` | Ch30 | `30_CircularProgressIndicator与LinearProgressIndicator.md:45` | decls | `sealed interface LoadState {` |
| `Ch30_03.kt` | Ch30 | `30_CircularProgressIndicator与LinearProgressIndicator.md:80` | body | `var progress by remember { mutableFloatStateOf(0.35f) }` |
| `Ch31_01.kt` | Ch31 | `31_NavigationBar与NavigationBarItem.md:30` | body | `var selectedIndex by remember { mutableIntStateOf(0) }` |
| `Ch31_02.kt` | Ch31 | `31_NavigationBar与NavigationBarItem.md:50` | decls | `// 目的地描述：图标 + 文字。data class 语法见 00D` |
| `Ch32_01.kt` | Ch32 | `32_TabRow与Tab.md:31` | body | `var selected by remember { mutableIntStateOf(0) }` |
| `Ch32_02.kt` | Ch32 | `32_TabRow与Tab.md:54` | body | `// PrimaryTabRow：主标签，通常放在页面顶部内容区；Tab 的写法一字不改` |
| `Ch32_03.kt` | Ch32 | `32_TabRow与Tab.md:74` | decls | `// rememberPagerState/HorizontalPager 在 foundation.pager 子包，IMPORT_HEADER 未覆盖...` |
| `Ch33_01.kt` | Ch33 | `33_Compose中模拟FlutterIndexedStack.md:32` | body | `Box(Modifier.fillMaxWidth().height(156.px)) {` |
| `Ch33_02.kt` | Ch33 | `33_Compose中模拟FlutterIndexedStack.md:61` | decls | `// ---------- 公共部分：带计数器的面板 ----------` |
| `Ch34_01.kt` | Ch34 | `34_页面切换时如何保留状态.md:55` | decls | `// ---------- 公共部分：列表 Tab 的内容（受控组件，状态由调用方持有） ----------` |
| `Ch35_01.kt` | Ch35 | `35_组件内部状态与页面导航状态的区别.md:30` | body | `// 单个下拉框内部` |
| `Ch36_01.kt` | Ch36 | `36_ComponentInfo类及其四个字段.md:49` | decls | `// ---------- 数据模型：与项目 ComponentInfo 同构，改名为 DemoEntry 以表明这是本章自建示例 ----------` |
| `Ch36_02.kt` | Ch36 | `36_ComponentInfo类及其四个字段.md:165` | decls | `// 用最小两字段模型演示 data class 的自动成员；DemoEntry 同理` |
| `Ch37_01.kt` | Ch37 | `37_@Composable()-_Unit作为可运行UI数据.md:54` | decls | `// 通用外壳：最后一个参数是 @Composable 函数类型——调用方把「一段 UI 代码」传进来` |
| `Ch37_02.kt` | Ch37 | `37_@Composable()-_Unit作为可运行UI数据.md:98` | decls | `@Composable` |
| `Ch38_01.kt` | Ch38 | `38_KotlinobjectComponentRegistry.md:49` | decls | `// 最小数据模型：与第 36 章独立示例同构` |
| `Ch39_01.kt` | Ch39 | `39_List、firstOrNull与可空返回值.md:53` | decls | `// 最小模型：三个只读字段，够演示查询即可` |
| `Ch40_01.kt` | Ch40 | `40_添加一个新Demo的完整步骤.md:66` | decls | `// ---------- 步骤 2：数据模型与注册表（DemoEntry 与第 36 章独立示例同构） ----------` |
| `Ch40_02.kt` | Ch40 | `40_添加一个新Demo的完整步骤.md:194` | decls | `// 本片段独立编译，模型定义与步骤 2 相同` |
| `Ch41_01.kt` | Ch41 | `41_Density到底是什么.md:47` | body | `val density = LocalDensity.current` |
| `Ch42_01.kt` | Ch42 | `42_CompositionLocalProvider与LocalDensity.md:55` | body | `// 1) 声明：staticCompositionLocalOf 带一个默认值工厂（两种声明方式的差别见 4.3）` |
| `Ch43_01.kt` | Ch43 | `43_DesignWidthScale如何影响整个子树.md:49` | body | `// 纸面设备（第 41 章）：屏宽 1080px、density 2.75 → 可用宽 1080 / 2.75 ≈ 392.7dp` |
| `Ch44_01.kt` | Ch44 | `44_.px与.pxSp()扩展属性和函数.md:32` | decls | `val Number.px: Dp` |
| `Ch44_02.kt` | Ch44 | `44_.px与.pxSp()扩展属性和函数.md:48` | body | `Column(` |
| `Ch44_03.kt` | Ch44 | `44_.px与.pxSp()扩展属性和函数.md:65` | body | `// 纸面设备（第 41 章）：density 2.75；DesignWidthScale 覆盖后（750 稿）：1080 / 750 = 1.44` |
| `Ch45_01.kt` | Ch45 | `45_与Flutter设计稿适配的对比、风险与建议.md:47` | body | `// 纸面设备（第 41 章）：可用宽 ≈ 392.7dp；实际取值用 BoxWithConstraints 或 WindowSizeClass` |
| `Ch45_02.kt` | Ch45 | `45_与Flutter设计稿适配的对比、风险与建议.md:98` | decls | `@Composable` |
| `Ch46_01.kt` | Ch46 | `46_重组的跳过机制与位置记忆.md:31` | decls | `@Composable` |
| `Ch46_02.kt` | Ch46 | `46_重组的跳过机制与位置记忆.md:59` | body | `@Composable` |
| `Ch47_01.kt` | Ch47 | `47_稳定性与强跳过模式.md:29` | decls | `// data class 自带 equals，且字段全部不可变 —— 稳定` |
| `Ch48_01.kt` | Ch48 | `48_derivedStateOf派生状态.md:29` | body | `val listState = rememberLazyListState()` |
| `Ch48_02.kt` | Ch48 | `48_derivedStateOf派生状态.md:54` | body | `// firstVisibleItemIndex 每变一次（每滚过一项）都会让读取处重组，` |
| `Ch48_03.kt` | Ch48 | `48_derivedStateOf派生状态.md:63` | body | `var firstName by remember { mutableStateOf("三") }` |
| `Ch48_04.kt` | Ch48 | `48_derivedStateOf派生状态.md:79` | body | `var items by remember { mutableStateOf(listOf<String>()) }` |
| `Ch49_01.kt` | Ch49 | `49_rememberUpdatedState与rememberCoroutineScope.md:29` | decls | `@Composable` |
| `Ch49_02.kt` | Ch49 | `49_rememberUpdatedState与rememberCoroutineScope.md:68` | decls | `@Composable` |
| `Ch50_01.kt` | Ch50 | `50_snapshotFlow与Flow数据接入.md:34` | decls | `import kotlinx.coroutines.flow.debounce` |
| `Ch51_01.kt` | Ch51 | `51_animateAsState与Tween基础动画.md:26` | decls | `@Composable` |
| `Ch51_02.kt` | Ch51 | `51_animateAsState与Tween基础动画.md:65` | decls | `@Composable` |
| `Ch52_01.kt` | Ch52 | `52_Animatable与手动动画控制.md:31` | decls | `@Composable` |
| `Ch52_02.kt` | Ch52 | `52_Animatable与手动动画控制.md:65` | decls | `@Composable` |
| `Ch53_01.kt` | Ch53 | `53_AnimatedVisibility与AnimatedContent与Crossfade.md:29` | decls | `@Composable` |
| `Ch53_02.kt` | Ch53 | `53_AnimatedVisibility与AnimatedContent与Crossfade.md:82` | decls | `enum class ViewMode { List, Grid }` |
| `Ch54_01.kt` | Ch54 | `54_updateTransition多状态动画编排.md:29` | decls | `enum class PlayState { Playing, Paused }` |
| `Ch55_01.kt` | Ch55 | `55_动画规格spring与keyframes与repeatable.md:29` | body | `val scale by animateFloatAsState(` |
| `Ch55_02.kt` | Ch55 | `55_动画规格spring与keyframes与repeatable.md:45` | body | `val progress by animateFloatAsState(` |
| `Ch55_03.kt` | Ch55 | `55_动画规格spring与keyframes与repeatable.md:62` | body | `val alpha by rememberInfiniteTransition(label = "breath").animateFloat(` |
| `Ch56_01.kt` | Ch56 | `56_rememberInfiniteTransition无限动画.md:26` | decls | `@Composable` |
| `Ch57_01.kt` | Ch57 | `57_Canvas与DrawScope基础.md:29` | decls | `@Composable` |
| `Ch58_01.kt` | Ch58 | `58_drawBehind与drawWithContent与drawWithCache.md:36` | decls | `@Composable` |
| `Ch59_01.kt` | Ch59 | `59_Path贝塞尔曲线与裁剪.md:29` | decls | `@Composable` |
| `Ch60_01.kt` | Ch60 | `60_BrushShader与渐变.md:29` | decls | `@Composable` |
| `Ch61_01.kt` | Ch61 | `61_富文本AnnotatedString与Span.md:32` | body | `@Composable` |
| `Ch62_01.kt` | Ch62 | `62_自定义Layout与measure与place.md:31` | body | `@Composable` |
| `Ch63_01.kt` | Ch63 | `63_Constraints与固有尺寸测量.md:31` | body | `@Composable` |
| `Ch63_02.kt` | Ch63 | `63_Constraints与固有尺寸测量.md:58` | body | `Layout(content = { Child() }) { measurables, constraints ->` |
| `Ch64_01.kt` | Ch64 | `64_自定义Modifier.md:31` | body | `fun Modifier.expandClickArea(` |
| `Ch64_02.kt` | Ch64 | `64_自定义Modifier.md:79` | body | `fun Modifier.pressHighlight(): Modifier = composed {` |
| `Ch65_01.kt` | Ch65 | `65_手势检测pointerInput.md:66` | body | `Modifier.pointerInput(Unit) {` |
| `Ch66_01.kt` | Ch66 | `66_手势冲突与嵌套滚动.md:27` | decls | `@Composable` |
| `Ch66_02.kt` | Ch66 | `66_手势冲突与嵌套滚动.md:56` | decls | `import androidx.compose.ui.input.nestedscroll.NestedScrollConnection` |
| `Ch68_01.kt` | Ch68 | `68_ViewModel与StateFlow与UDF.md:30` | decls | `import androidx.lifecycle.ViewModel` |
| `Ch68_02.kt` | Ch68 | `68_ViewModel与StateFlow与UDF.md:116` | decls | `import androidx.lifecycle.ViewModel` |
| `Ch71A_01.kt` | Ch71A | `71A_Navigation3与NavDisplay.md:92` | decls | `import androidx.navigation3.runtime.NavKey` |
| `Ch71A_02.kt` | Ch71A | `71A_Navigation3与NavDisplay.md:106` | decls | `import androidx.compose.material3.Button` |
| `Ch71A_03.kt` | Ch71A | `71A_Navigation3与NavDisplay.md:161` | body | `NavDisplay(` |
| `Ch73_01.kt` | Ch73 | `73_协程进阶Dispatcher与Flow操作符.md:88` | decls | `import kotlinx.coroutines.ExperimentalCoroutinesApi` |
| `Ch74_01.kt` | Ch74 | `74_性能分析LayoutInspector与编译器报告.md:48` | decls | `// 可优化示范：页面级读取输入状态，输入变化会使 ProfileScreen 进入重组` |
| `Ch74_02.kt` | Ch74 | `74_性能分析LayoutInspector与编译器报告.md:65` | decls | `// 修复：把状态压到最小作用域` |
| `Ch75_01.kt` | Ch75 | `75_Lazy列表性能优化.md:33` | decls | `@Composable` |
| `Ch75_02.kt` | Ch75 | `75_Lazy列表性能优化.md:75` | decls | `@Composable` |
| `Ch75_03.kt` | Ch75 | `75_Lazy列表性能优化.md:90` | body | `import kotlinx.coroutines.flow.*` |
| `Ch78_01.kt` | Ch78 | `78_边缘到边缘与WindowInsets.md:55` | body | `@Composable` |
| `Ch79_01.kt` | Ch79 | `79_响应式布局与WindowSizeClass.md:28` | decls | `import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2` |
| `Ch79_02.kt` | Ch79 | `79_响应式布局与WindowSizeClass.md:119` | body | `BoxWithConstraints {` |
| `Ch80_01.kt` | Ch80 | `80_无障碍与BaselineProfile与发布质量.md:29` | body | `// 有意义的图片：读屏会朗读 contentDescription` |

