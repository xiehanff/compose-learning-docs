# Compose 项目完整学习文档（预备篇 + 第 1—87 章）

> [在线阅读（GitHub Pages）](https://xiehanff.github.io/compose-learning-docs/) · 页面采用 GitBook 风格目录，内容由本仓库 Markdown 构建。
>
> 本文面向维护者（片段标记、验证命令、站点维护），**不会发布到站点**；面向读者的内容写在根目录章节 `*.md` 里。

> 本仓库的版本基线、平台边界、证据等级和审校规则见 [文档基线与审校规范](文档基线与审校规范.md)。正文中的 API 状态和版本结论都应以该文件及 `snippets/gradle/libs.versions.toml` 为准。

- 预备篇：面向真正零基础读者，完成环境准备、第一次构建、Gradle 基础与构建报错自救。
- 第 1—45 章：整理自开源示例工程 [xiehanff/compose_demo](https://github.com/xiehanff/compose_demo) 的源码，覆盖 Compose UI 从入门到中级的基础。
- 第 46—80 章：进阶篇，覆盖重组引擎、动画、自定义绘制/布局/手势、架构、性能、测试与发布；并新增 Navigation 3 专题（第 71A 章），按仓库版本基线介绍其 Compose 导航模型；不构成对所有项目的无条件迁移建议。
- 第 81—87 章：实践篇，以 [ymq-mall-json-server](https://gitee.com/campos/ymq-mall-json-server) 为 mock 后端，用真实 HTTP 接口完成小电商（首页/商品详情/购物车/个人主页）的最小闭环。

零基础读者必须先完成预备篇，再按编号阅读。已有 Android/Gradle 经验的读者可以从第 1 章开始。

> 当前仓库只包含学习文档；示例工程在独立仓库 [xiehanff/compose_demo](https://github.com/xiehanff/compose_demo)，第 1—45 章的源码路径均可在其中对照。读者可以先按预备篇创建 Empty Activity 项目，再运行可独立复制的示例。

## 文件说明

- `00_完整学习规划.md`：总规划。
- [阶段实践案例.md](阶段实践案例.md)：预备篇与 15 个章节部分后的短案例及验收标准。
- `00A—00E`：零基础环境、Gradle、构建排错、Kotlin 与 Android 预备篇。
- `01—45`：独立章节。
- `08A`：FlowRow 流式布局（基础篇补充章节，接在第 8 章后）。
- `26A`、`26B`：输入框与选择控件（基础篇补充章节，接在第 26 章后）。
- `71A`：Navigation 3 与 NavDisplay（进阶篇补充章节，接在第 71 章后）。
- `81—87`：实践篇，小电商最小闭环；mock 服务见 `practice/mock-server/README.md`。
- `99_源码文件索引.md`：从源码文件反查章节。
- `阶段项目_电商商城.md`：全课综合验收任务书（电商商城独立验收版，仓库不含对应源码工程）。
- `文档基线与审校规范.md`：版本、来源、平台边界与验证范围。

## 平台和内容边界

本仓库的目标是 **Android Jetpack Compose**。`Activity`、AndroidX Lifecycle、Hilt、WindowInsets、Navigation 3 等内容不能直接推导为 Compose Multiplatform、Desktop、Wear OS 或 Android TV 的通用结论。第 1—45 章基于 [xiehanff/compose_demo](https://github.com/xiehanff/compose_demo) 的源码背景；第 46—80 章使用通用 API 示例。文档不提供完整业务 App，也不以“看完所有 API”代替真实项目中的网络、错误处理、模块化和发布验证。

## 验证范围

仓库对显式标记的 Kotlin 片段执行真实编译，并在 CI 中重复检查。编译通过只说明当前版本下片段能通过语法、类型和依赖解析；它不证明运行时行为、状态恢复、导航返回栈、视觉布局、无障碍、性能或真实设备兼容性。需要验证这些性质时，应使用 Compose UI 测试、仪器测试、Macrobenchmark、真机和人工验收。详见 [文档基线与审校规范](文档基线与审校规范.md)。

## 开始之前：零基础预备篇

- [预备篇 A：零基础环境准备与第一次构建](00A_零基础环境准备与第一次构建.md)
- [预备篇 B：Gradle 构建系统从入门到理解](00B_Gradle构建系统从入门到理解.md)
- [预备篇 C：Gradle 构建报错定位与自救](00C_Gradle构建报错定位与自救.md)
- [预备篇 D：Kotlin 基础与 Compose 必读语法](00D_Kotlin基础与Compose必备语法.md)
- [预备篇 E：Android 项目、资源、生命周期与日志基础](00E_Android项目运行基础.md)

## 章节

- [第 1 章：MainActivity、ComponentActivity、onCreate 与 setContent](01_MainActivity与setContent.md)
- [第 2 章：`@Composable` 与 Composable 函数](02_Composable函数.md)
- [第 3 章：MaterialTheme 与 DemoTheme](03_DemoTheme与MaterialTheme.md)
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
- [第 71A 章：Navigation 3 与 NavDisplay](71A_Navigation3与NavDisplay.md)
- [第 72 章：状态持久化：rememberSaveable、DataStore 与 Room 的边界](72_状态持久化DataStore与Room.md)
- [第 73 章：协程进阶：Dispatchers、结构化并发与 Flow 操作符](73_协程进阶Dispatcher与Flow操作符.md)
- [第 74 章：性能分析：Layout Inspector 与编译器报告](74_性能分析LayoutInspector与编译器报告.md)
- [第 75 章：Lazy 列表性能优化](75_Lazy列表性能优化.md)
- [第 76 章：Compose UI 测试：语义树与交互测试](76_ComposeUI测试.md)
- [第 77 章：主题进阶：动态取色、Shape 与 Typography](77_主题进阶动态取色与Shape与Typography.md)
- [第 78 章：边缘到边缘与 Window Insets](78_边缘到边缘与WindowInsets.md)
- [第 79 章：响应式布局与 WindowSizeClass](79_响应式布局与WindowSizeClass.md)
- [第 80 章：无障碍、Baseline Profile 与发布质量](80_无障碍与BaselineProfile与发布质量.md)
- [阶段项目：电商商城（任务书）](阶段项目_电商商城.md)

## 实践篇（第 81—87 章）

> 以 [ymq-mall-json-server](https://gitee.com/campos/ymq-mall-json-server) 为 mock 后端，用真实 HTTP 接口完成小电商主要页面的最小闭环；启动说明见 [practice/mock-server/README.md](practice/mock-server/README.md)。

- [第 81 章：实践篇总览：用真实接口完成一个小电商](81_实践篇总览与最小闭环.md)
- [第 82 章：网络层：Retrofit、kotlinx.serialization 与统一响应壳](82_网络层Retrofit与统一响应壳.md)
- [第 83 章：电商首页：分类、轮播与分页商品流](83_电商首页分类轮播与分页商品流.md)
- [第 84 章：商品详情：SKU 选择与评论分页](84_商品详情SKU选择与评论分页.md)
- [第 85 章：购物车：分组列表与三态处理](85_购物车分组列表与三态处理.md)
- [第 86 章：个人主页：地址与客服](86_个人主页地址与客服.md)
- [第 87 章：实践整合与验收清单](87_实践整合与验收清单.md)

## 代码示例编译校验

书里可复制的 Kotlin 示例是否真的能编译，不再靠人工相信：被显式标记的代码块会被
抽取成真实的 Kotlin 源码，并交给一个只做编译检查的 Android library 模块
（`snippets/`）编译。未标记的片段（节选、伪代码、依赖项目专属符号）原样保留。

### 对作者：怎么标记

在代码块**前一行**加一个 HTML 注释（渲染后的页面看不到它）：

````markdown
<!-- compile -->
```kotlin
Text("点我")
```
````

也可以把标记写进信息串：<code>```kotlin compile</code>。**只有被标记的块才参与校验。**

标记可以带指令：

| 写法 | 含义 |
| --- | --- |
| `<!-- compile -->` | 默认：片段被包进 `@Composable fun content()` 函数体，可以直接写语句、局部 `val`、局部 `fun`/`class` |
| `<!-- compile decls -->` | 片段本身是完整声明（`@Composable fun`、`class`、`enum class`…），作为包装 object 的成员展开（函数体里不允许 `enum`，这种写法必须用它） |
| `prelude: <一行代码>` | 片段引用到的局部变量在章节上文定义、块内没有时，用它补齐；可写多行，按顺序注入到最前面 |

多行写法示例：

````markdown
<!-- compile
prelude: var liked by remember { mutableStateOf(false) }
-->
```kotlin
val scale by animateFloatAsState(if (liked) 1.2f else 1f)
```
````

要求：片段自包含（不引用 `ComponentRegistry`、`DemoTheme`、`DemoApp` 这类项目专属
符号）；`snippets/src/main/kotlin/support/SnippetsSupport.kt` 里提供了设计稿单位
`.px` / `.pxSp()`（内容照抄项目真实的 `ui/adapter/Px.kt`，保证编译校验用的单位语义
和章节描述一致）与第 71A 章的 Nav3 路由 key；第 81—87 章的商城网络层
（DTO/MallApi/NetworkClient/MallRepo，与第 82 章正文同构）在
`snippets/src/main/kotlin/support/MallSupport.kt`，实践篇片段直接 import 它编译。片段不必写 `import`，生成文件的固定
import 头已覆盖常用 Compose 包（需要新包时改 `tools/extract_snippets.py` 的
`IMPORT_HEADER`）；**片段也可以自己写 `import`**，生成时会自动提到文件顶层。

### 本地运行

```bash
python3 tools/extract_snippets.py                        # 抽取标记片段，产出 snippets/generated_manifest.md
cd snippets && ./gradlew compileDebugKotlin              # 真实编译；失败会直接报出章节与行号
cd snippets && ./gradlew compileDebugKotlin -PsnippetsStrictWarnings --rerun-tasks  # 严格编译：把警告当错误
```

两个编译任务的分工：

| 命令 | 能发现什么 |
| --- | --- |
| `compileDebugKotlin` | 片段能否编译（缺 import、API 签名不对、类型不匹配…） |
| `compileDebugKotlin -PsnippetsStrictWarnings` | 编译警告：主要是**已弃用 API**。生成文件已不再全局 `@file:Suppress("DEPRECATION")` |

注意一个已知边界：生成文件会给每个片段统一加上 `@file:OptIn(...)`（只为让“节选片段”
能编译），所以“编译通过”**不能证明章节正文说明了某个 API 需要 opt-in**；这部分仍需
人工核对，用审计模式把它暴露出来：

```bash
python3 tools/extract_snippets.py --no-file-optin   # 故意不代片段声明 @file:OptIn(...)
cd snippets && ./gradlew compileDebugKotlin          # 报错信息会指出依赖实验 API 的片段
```

审计失败不等于错误，只说明“这个片段依赖实验 API”；逐条确认章节里写了 opt-in（例如第 28 章
`ModalBottomSheet`）即可，跑完记得重新执行不带参数的抽取。同样，片段里
调用的是局部 `var`、占位符号还是真实 API，编译通过也不能区分——所以章节里的省略写法要尽量
补齐成真实 API，不要依赖支撑代码里的同名重载。

`snippets/generated_manifest.md` 列出章号、行号、片段首行与生成文件，便于回溯；生成的 Kotlin
源码位于 `snippets/src/main/kotlin/generated/`（已 gitignore，每次重新生成）。
UI 测试、Macrobenchmark 和 Baseline Profile 的测试模块示例未纳入本工程的 `compileDebugKotlin` 校验；未标记的结构示例也不参与编译。
第 79 章示例额外依赖 `androidx.compose.material3.adaptive:adaptive:1.3.0-rc01`（不在 Compose BOM 管理内，已在 `snippets/gradle/libs.versions.toml` 显式声明并纳入编译；片段内的 ListPane/DetailPane/MainContent 为教学占位组件）。
需要有 Android SDK（`ANDROID_HOME` 或 `snippets/local.properties` 里的 `sdk.dir`，
需含 platform 36）与 JDK 17+。CI 见 `.github/workflows/snippet-compile.yml`，push / PR 自动跑同一套检查。

## 静态站点（GitHub Pages）

站点把根目录章节 Markdown 渲染成 GitBook 风格的阅读页，内容和目录都直接从章节文件生成，不维护第二份正文。

```bash
timeout 300 npm ci          # 首次安装（Node 22）
timeout 120 npm run dev     # 本地预览 http://localhost:5173
timeout 120 npm run build   # 产出 dist/，自检渲染结果
```

- 站点代码：`index.html`、`vite.config.js`、`src/App.vue`（Markdown 渲染、目录分组、上下章节导航、搜索）、`src/style.css`（GitBook 风格设计令牌，主题色 `--accent:#fe551b`）、`public/fonts/`（Cascadia Next SC、Google Sans Mono，参考 myblog 的排版方案）。
- **只有根目录 `*.md` 会上站**；页面标题取文件内第一个一级标题，侧边目录按文件名数字前缀分组（`0` 开始学习、`1—45` 基础篇、`46—80` 进阶篇）。数字前缀不在这些区间的文件不上站。
- **维护者文档不上站**（`src/App.vue` 的 `HIDDEN_FILES`）：`README.md`、`AGENTS.md`、`docs-expansion-*.md` 审计报告。README 只服务 agent，读者入口是站内规划页。面向读者的内容写进章节，面向维护者的构建/校验/部署说明写在这里。
- 章节间跳转直接写相对 `.md` 链接，渲染时会改写成站内路由；外部链接原样保留。
- Markdown 渲染开启 `html: true`、代码高亮、标题锚点，并支持 GitHub 风格提示框（`> [!NOTE]` / `TIP` / `IMPORTANT` / `WARNING` / `CAUTION`）。新增代码块语言需在 `src/App.vue` 注册对应 `highlight.js/lib/languages/*`。
- 部署：`.github/workflows/pages.yml` 在 push `main` 或手动触发时执行 `npm ci && npm run build` 并发布 `dist/`；子路径由 `vite.config.js` 决定（CI 下为 `/compose-learning-docs/`，本地为 `/`）。
- 站点发布与片段编译校验相互独立：改章节仍需跑上面的 `extract_snippets.py` + `compileDebugKotlin`。

新增/删除章节文件时，除了 `00_完整学习规划.md`、`章节清单.txt` 和上面的目录，无需额外配置站点——站点会自动收录并归组。
