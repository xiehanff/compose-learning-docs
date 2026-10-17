# 代码示例编译校验清单（自动生成）

由 `python3 tools/extract_snippets.py` 从各章 `*.md` 中抽取生成，请勿手改。

标记方法见 `README.md` 的「代码示例编译校验」一节。

注意：生成文件会为「节选片段」统一加上 `@file:OptIn(...)`，它只保证片段能编译，
**不代表章节正文已说明需要 opt-in**；弃用 API 也不会在普通编译里报错，
由独立严格编译发现：`./gradlew compileDebugKotlin -PsnippetsStrictWarnings --rerun-tasks`。

共 61 个片段待校验。

| 生成文件 | 章号 | 来源（文件:行号） | 模式 | 片段首行 |
| --- | --- | --- | --- | --- |
| `Ch07_01.kt` | Ch07 | `07_Modifier的本质、作用范围与调用顺序.md:27` | body | `Text(` |
| `Ch08A_01.kt` | Ch08A | `08A_FlowRow与流式布局.md:30` | decls | `@Composable` |
| `Ch08_01.kt` | Ch08 | `08_Column、Row、Arrangement与Alignment.md:25` | body | `Column(` |
| `Ch09_01.kt` | Ch09 | `09_Box、层叠布局与FlutterStack.md:25` | body | `Box(` |
| `Ch10_01.kt` | Ch10 | `10_Card、Surface与容器组件的区别.md:30` | body | `Card(` |
| `Ch12_01.kt` | Ch12 | `12_Image、Painter、contentDescription与ContentScale.md:26` | body | `Image(` |
| `Ch13_01.kt` | Ch13 | `13_padding、size、fillMaxWidth与fillMaxSize.md:28` | body | `LazyColumn(` |
| `Ch14_01.kt` | Ch14 | `14_weight、Spacer与FlutterExpanded.md:25` | body | `Row(Modifier.fillMaxWidth()) {` |
| `Ch15_01.kt` | Ch15 | `15_RoundedCornerShape、background、clip与border.md:28` | body | `Box(` |
| `Ch18_01.kt` | Ch18 | `18_LazyVerticalGrid与GridCells.md:24` | body | `LazyVerticalGrid(` |
| `Ch19_01.kt` | Ch19 | `19_verticalScroll、horizontalScroll与rememberScrollState.md:24` | body | `Column(` |
| `Ch21_01.kt` | Ch21 | `21_remember、mutableStateOf与状态委托.md:26` | body | `var showDialog by remember { mutableStateOf(false) }` |
| `Ch22_01.kt` | Ch22 | `22_重组什么时候发生，什么代码可能重复执行.md:31` | body | `val imageBitmap = remember {` |
| `Ch23_01.kt` | Ch23 | `23_rememberSaveable与配置变化后的状态恢复.md:26` | body | `// components/StateDemo.kt:135：只要跨重组保留，用 remember` |
| `Ch24_01.kt` | Ch24 | `24_状态提升：状态放在哪里，事件回调传向哪里.md:28` | decls | `// 节选：单个下拉框——「输入框展示选中值 + 下拉菜单上报选择」` |
| `Ch26A_01.kt` | Ch26A | `26A_TextField、OutlinedTextField与输入状态.md:32` | body | `var name by remember { mutableStateOf("") }` |
| `Ch26A_02.kt` | Ch26A | `26A_TextField、OutlinedTextField与输入状态.md:82` | body | `import androidx.compose.foundation.text.input.InputTransformation` |
| `Ch26B_01.kt` | Ch26B | `26B_Checkbox、Switch与Slider选择控件.md:25` | body | `var agreed by remember { mutableStateOf(false) }          // 是否同意协议` |
| `Ch26_01.kt` | Ch26 | `26_Button、IconButton与Icon.md:28` | body | `// components/ButtonDemo.kt：五种风格按钮，按视觉权重从高到低排列` |
| `Ch27_01.kt` | Ch27 | `27_AlertDialog.md:26` | body | `if (showDialog) {` |
| `Ch28_01.kt` | Ch28 | `28_ModalBottomSheet.md:27` | body | `if (showSheet) {` |
| `Ch29_01.kt` | Ch29 | `29_SnackbarHostState、协程与Snackbar.md:24` | body | `val hostState = remember { SnackbarHostState() }` |
| `Ch30_01.kt` | Ch30 | `30_CircularProgressIndicator与LinearProgressIndicator.md:26` | body | `CircularProgressIndicator()` |
| `Ch31_01.kt` | Ch31 | `31_NavigationBar与NavigationBarItem.md:27` | body | `var selectedIndex by remember { mutableIntStateOf(0) }` |
| `Ch32_01.kt` | Ch32 | `32_TabRow与Tab.md:27` | body | `var selected by remember { mutableIntStateOf(0) }` |
| `Ch32_02.kt` | Ch32 | `32_TabRow与Tab.md:48` | body | `// PrimaryTabRow：主标签，通常放在页面顶部内容区；Tab 的写法一字不改` |
| `Ch33_01.kt` | Ch33 | `33_Compose中模拟FlutterIndexedStack.md:26` | body | `Box(Modifier.fillMaxWidth().height(156.px)) {` |
| `Ch44_01.kt` | Ch44 | `44_.px与.pxSp()扩展属性和函数.md:28` | decls | `val Number.px: Dp` |
| `Ch44_02.kt` | Ch44 | `44_.px与.pxSp()扩展属性和函数.md:44` | body | `Column(` |
| `Ch46_01.kt` | Ch46 | `46_重组的跳过机制与位置记忆.md:31` | decls | `@Composable` |
| `Ch46_02.kt` | Ch46 | `46_重组的跳过机制与位置记忆.md:59` | body | `@Composable` |
| `Ch48_01.kt` | Ch48 | `48_derivedStateOf派生状态.md:31` | body | `val listState = rememberLazyListState()` |
| `Ch49_01.kt` | Ch49 | `49_rememberUpdatedState与rememberCoroutineScope.md:29` | decls | `@Composable` |
| `Ch51_01.kt` | Ch51 | `51_animateAsState与Tween基础动画.md:26` | decls | `@Composable` |
| `Ch52_01.kt` | Ch52 | `52_Animatable与手动动画控制.md:31` | decls | `@Composable` |
| `Ch52_02.kt` | Ch52 | `52_Animatable与手动动画控制.md:65` | decls | `@Composable` |
| `Ch53_01.kt` | Ch53 | `53_AnimatedVisibility与AnimatedContent与Crossfade.md:29` | decls | `@Composable` |
| `Ch53_02.kt` | Ch53 | `53_AnimatedVisibility与AnimatedContent与Crossfade.md:82` | decls | `enum class ViewMode { List, Grid }` |
| `Ch54_01.kt` | Ch54 | `54_updateTransition多状态动画编排.md:30` | decls | `enum class PlayState { Playing, Paused }` |
| `Ch55_01.kt` | Ch55 | `55_动画规格spring与keyframes与repeatable.md:29` | body | `val scale by animateFloatAsState(` |
| `Ch55_02.kt` | Ch55 | `55_动画规格spring与keyframes与repeatable.md:45` | body | `val progress by animateFloatAsState(` |
| `Ch55_03.kt` | Ch55 | `55_动画规格spring与keyframes与repeatable.md:62` | body | `val alpha by rememberInfiniteTransition(label = "breath").animateFloat(` |
| `Ch56_01.kt` | Ch56 | `56_rememberInfiniteTransition无限动画.md:27` | decls | `@Composable` |
| `Ch58_01.kt` | Ch58 | `58_drawBehind与drawWithContent与drawWithCache.md:34` | decls | `@Composable` |
| `Ch60_01.kt` | Ch60 | `60_BrushShader与渐变.md:27` | decls | `@Composable` |
| `Ch61_01.kt` | Ch61 | `61_富文本AnnotatedString与Span.md:30` | body | `@Composable` |
| `Ch62_01.kt` | Ch62 | `62_自定义Layout与measure与place.md:29` | body | `@Composable` |
| `Ch63_01.kt` | Ch63 | `63_Constraints与固有尺寸测量.md:29` | body | `@Composable` |
| `Ch63_02.kt` | Ch63 | `63_Constraints与固有尺寸测量.md:56` | body | `Layout(content = { Child() }) { measurables, constraints ->` |
| `Ch64_01.kt` | Ch64 | `64_自定义Modifier.md:31` | body | `fun Modifier.expandClickArea(` |
| `Ch64_02.kt` | Ch64 | `64_自定义Modifier.md:79` | body | `fun Modifier.pressHighlight(): Modifier = composed {` |
| `Ch65_01.kt` | Ch65 | `65_手势检测pointerInput.md:66` | body | `Modifier.pointerInput(Unit) {` |
| `Ch71A_01.kt` | Ch71A | `71A_Navigation3与NavDisplay.md:90` | decls | `import androidx.navigation3.runtime.NavKey` |
| `Ch71A_02.kt` | Ch71A | `71A_Navigation3与NavDisplay.md:104` | decls | `import androidx.compose.material3.Button` |
| `Ch71A_03.kt` | Ch71A | `71A_Navigation3与NavDisplay.md:159` | body | `NavDisplay(` |
| `Ch75_01.kt` | Ch75 | `75_Lazy列表性能优化.md:33` | decls | `@Composable` |
| `Ch75_02.kt` | Ch75 | `75_Lazy列表性能优化.md:75` | decls | `@Composable` |
| `Ch78_01.kt` | Ch78 | `78_边缘到边缘与WindowInsets.md:55` | body | `@Composable` |
| `Ch79_01.kt` | Ch79 | `79_响应式布局与WindowSizeClass.md:28` | decls | `import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2` |
| `Ch79_02.kt` | Ch79 | `79_响应式布局与WindowSizeClass.md:119` | body | `BoxWithConstraints {` |
| `Ch80_01.kt` | Ch80 | `80_无障碍与BaselineProfile与发布质量.md:29` | body | `// 有意义的图片：读屏会朗读 contentDescription` |

