# 文档扩写审计表（终稿）

- 审计基线 HEAD：`657c5333176c4822d6d570ef2d1133cf78b700a2`（main）→ 扩写分支：`docs/expand-compose-course`；审计日期 2026-10-20
- 续作复核基准：`657c533` → `b36e4f5`。行数按 `splitlines()` 统计（含空行，不将文件末尾换行另计一行）；代码块数统计 Kotlin 围栏；编译标记数按 `tools/extract_snippets.py` 的实际抽取结果统计，包含 HTML 注释、prelude 与围栏信息串。共 167 个片段，覆盖 75 篇文档，本次重新通过普通与严格编译。
- 评分沿用原轮批次审校记录，本次续作未重新评分；本次复核范围是 Git 差异统计、片段抽取与编译、Markdown 相对文件链接，不替代逐章技术审校。
- 设备/仪器行为验证未执行；表中“标记片段通过”只指该文档被抽取的片段通过语法、类型和依赖解析，不代表整章代码或运行行为已验证。“无片段参与编译”表示该文档没有代码块纳入 snippets 校验。

| 章节 | 原行数→新行数 | 原代码块/标记→新代码块/标记 | 动作 | 主要新增场景/修正点 | 原轮评分 | 编译范围与结果 |
|---|---|---|---|---|---:|---|
| 00A_零基础环境准备与第一次构建.md | 246→248 | 0/0→0/0 | 修正 | 模板版本变化说明 | 90 | 无片段参与编译 |
| 00B_Gradle构建系统从入门到理解.md | 312→312 | 6/0→6/0 | 通过 | 零改动 | 95 | 无片段参与编译 |
| 00C_Gradle构建报错定位与自救.md | 355→355 | 0/0→0/0 | 通过 | 零改动 | 95 | 无片段参与编译 |
| 00D_Kotlin基础与Compose必备语法.md | 395→395 | 26/0→26/0 | 修正 | data class 补解/const 补句 | 92 | 无片段参与编译 |
| 00E_Android项目运行基础.md | 261→261 | 2/0→2/0 | 通过 | 零改动 | 95 | 无片段参与编译 |
| 00_完整学习规划.md | 237→237 | 0/0→0/0 | 修正 | 章节结构描述对齐 | 95 | 无片段参与编译 |
| 01_MainActivity与setContent.md | 581→583 | 20/0→20/0 | 修正 | 代码块分类/00D 回链 | 90 | 无片段参与编译 |
| 02_Composable函数.md | 644→655 | 35/0→35/9 | 修正 | 新增 9 个编译标记/回链 | 92 | 9 个标记片段通过 |
| 03_DemoTheme与MaterialTheme.md | 254→254 | 7/0→7/0 | 通过 | 零改动 | 95 | 无片段参与编译 |
| 04_NavHost与路由导航.md | 692→699 | 28/0→28/1 | 修正 | Nav2/3 并存说明/object 标记 | 92 | 1 个标记片段通过 |
| 05_Lambda点击回调与导航链路.md | 734→738 | 43/0→43/2 | 修正 | Text onClick 版本边界/2 标记 | 93 | 2 个标记片段通过 |
| 06_Scaffold、TopAppBar与内容区padding.md | 139→229 | 2/0→3/2 | 扩写 | Scaffold 骨架例/innerPadding 传递链 | 93 | 2 个标记片段通过 |
| 07_Modifier的本质、作用范围与调用顺序.md | 94→259 | 1/1→5/4 | 扩写+修正 | 顺序对照/命中区域演示/修饰链机制 | 92 | 4 个标记片段通过 |
| 08A_FlowRow与流式布局.md | 130→196 | 1/1→2/2 | 扩写 | 固定宽度色块换行预测/FlowRow 与固定列 Grid 对照 | 93 | 2 个标记片段通过 |
| 08_Column、Row、Arrangement与Alignment.md | 90→276 | 1/1→5/4 | 扩写 | 三色块 Arrangement 对照/主交叉轴/剩余空间 | 93 | 4 个标记片段通过 |
| 09_Box、层叠布局与FlutterStack.md | 87→206 | 1/1→5/4 | 扩写 | align 覆盖/重叠命中/角标实战 | 92 | 4 个标记片段通过 |
| 10_Card、Surface与容器组件的区别.md | 94→216 | 1/1→4/3 | 扩写+修正 | 三容器选型表/点击语义/触摸目标核正 | 90 | 3 个标记片段通过 |
| 11_Text、TextStyle、FontWeight与lineHeight.md | 92→208 | 1/0→4/2 | 扩写 | 行高截断四变体/sp 可访问性 | 88 | 2 个标记片段通过 |
| 12_Image、Painter、contentDescription与ContentScale.md | 89→212 | 1/1→4/2 | 扩写 | ContentScale 全表/无障碍描述/Coil 边界 | 87 | 2 个标记片段通过 |
| 13_padding、size、fillMaxWidth与fillMaxSize.md | 100→218 | 1/1→4/4 | 扩写 | 顺序互换约束账/无界退化案例 | 90 | 4 个标记片段通过 |
| 14_weight、Spacer与FlutterExpanded.md | 87→188 | 1/1→4/4 | 扩写 | weight 测量三步/与 Expanded 不等价 | 89 | 4 个标记片段通过 |
| 15_RoundedCornerShape、background、clip与border.md | 104→218 | 1/1→5/3 | 扩写 | 四工具分工/裁剪溢出对照 | 88 | 3 个标记片段通过 |
| 16_LazyColumn、item、items与itemsIndexed.md | 88→276 | 1/0→5/2 | 扩写 | 独立 DemoItem 例/三种 DSL/懒≠分页 | 90 | 2 个标记片段通过 |
| 17_key与列表项身份.md | 84→269 | 1/0→6/3 | 扩写 | 待办 key 两版对照/状态归属逐位置 | 90 | 3 个标记片段通过 |
| 18_LazyVerticalGrid与GridCells.md | 89→264 | 1/1→5/4 | 扩写 | Fixed/Adaptive 算例/列数推导 | 88 | 4 个标记片段通过 |
| 19_verticalScroll、horizontalScroll与rememberScrollState.md | 86→232 | 1/1→4/3 | 扩写 | 程序滚动协程例/生命周期五维对比 | 89 | 3 个标记片段通过 |
| 20_Lazy容器与普通滚动容器的选择.md | 93→182 | 1/0→2/1 | 扩写 | 同数据双容器对照/选型决策表 | 90 | 1 个标记片段通过 |
| 21_remember、mutableStateOf与状态委托.md | 98→224 | 1/1→4/3 | 扩写 | 普通 var 反例/委托细节/读取范围 | 92 | 3 个标记片段通过 |
| 22_重组什么时候发生，什么代码可能重复执行.md | 90→225 | 1/1→4/3 | 扩写 | 父子读取双场景日志/三阶段表 | 92 | 3 个标记片段通过 |
| 23_rememberSaveable与配置变化后的状态恢复.md | 87→189 | 1/1→3/3 | 扩写 | 双计数器对照/Saver/四场景表 | 90 | 3 个标记片段通过 |
| 24_状态提升：状态放在哪里，事件回调传向哪里.md | 117→269 | 1/1→4/4 | 扩写 | 三步受控重构/UDF 角色表 | 93 | 4 个标记片段通过 |
| 25_SideEffect、LaunchedEffect与普通业务代码的边界.md | 122→279 | 1/0→5/3 | 扩写 | 三 Effect 对照/7 步追踪表 | 92 | 3 个标记片段通过 |
| 26A_TextField、OutlinedTextField与输入状态.md | 314→386 | 6/2→9/3 | 扩写 | 键盘动作与焦点/请求焦点的版本边界 | 92 | 3 个标记片段通过 |
| 26B_Checkbox、Switch与Slider选择控件.md | 165→280 | 1/1→4/3 | 扩写 | 单项开关与列表多选/Slider 实时显示与提交分工 | 92 | 3 个标记片段通过 |
| 26_Button、IconButton与Icon.md | 117→183 | 1/1→2/2 | 扩写 | 加载态双防线/48dp 触摸目标 | 92 | 2 个标记片段通过 |
| 27_AlertDialog.md | 97→167 | 1/1→2/2 | 扩写 | 异步确认闭环/dismiss 边界 | 93 | 2 个标记片段通过 |
| 28_ModalBottomSheet.md | 103→153 | 2/1→3/2 | 扩写 | hide 收法/skipPartially/Properties | 92 | 2 个标记片段通过 |
| 29_SnackbarHostState、协程与Snackbar.md | 84→213 | 1/1→4/3 | 扩写 | 连点排队观察/Result 两分支 | 92 | 3 个标记片段通过 |
| 30_CircularProgressIndicator与LinearProgressIndicator.md | 79→183 | 1/1→4/3 | 扩写 | sealed 三态映射/确定进度观察 | 92 | 3 个标记片段通过 |
| 31_NavigationBar与NavigationBarItem.md | 91→200 | 1/1→3/2 | 扩写 | 三目的地闭环/两切换模式对比 | 92 | 2 个标记片段通过 |
| 32_TabRow与Tab.md | 127→233 | 2/2→4/3 | 审校+补例 | TabRow+Pager 联动/选型表 | 93 | 3 个标记片段通过 |
| 33_Compose中模拟FlutterIndexedStack.md | 88→304 | 1/1→4/2 | 扩写 | 三方案分层/双面板计数器对照 | 88 | 2 个标记片段通过 |
| 34_页面切换时如何保留状态.md | 82→379 | 1/0→5/1 | 重构扩写 | 双Tab+搜索+滚动三策略主线/四概念分离 | 90 | 1 个标记片段通过 |
| 35_组件内部状态与页面导航状态的区别.md | 81→168 | 1/0→3/1 | 扩写 | 四状态所有者表/选择题 | 88 | 1 个标记片段通过 |
| 36_ComponentInfo类及其四个字段.md | 85→262 | 1/0→4/2 | 扩写 | 非官方类型划线/DemoEntry 建模 | 92 | 2 个标记片段通过 |
| 37_@Composable()-_Unit作为可运行UI数据.md | 86→222 | 1/0→5/2 | 扩写 | 函数类型差异/调用时机反例 | 92 | 2 个标记片段通过 |
| 38_KotlinobjectComponentRegistry.md | 81→195 | 1/0→3/1 | 扩写 | object 时机/单例三代价 | 93 | 1 个标记片段通过 |
| 39_List、firstOrNull与可空返回值.md | 89→212 | 1/0→4/1 | 扩写 | 可空操作符链/智能转换/Dart 对照 | 93 | 1 个标记片段通过 |
| 40_添加一个新Demo的完整步骤.md | 92→308 | 1/0→5/2 | 扩写 | 7 步可照走清单/无原工程方案 | 91 | 2 个标记片段通过 |
| 41_Density到底是什么.md | 83→163 | 1/0→3/1 | 扩写 | px/dp/sp 算例/覆盖≠响应式 | 92 | 1 个标记片段通过 |
| 42_CompositionLocalProvider与LocalDensity.md | 93→168 | 1/0→3/1 | 扩写 | 最小 provider-consumer/两种 CL 差异 | 92 | 1 个标记片段通过 |
| 43_DesignWidthScale如何影响整个子树.md | 84→164 | 1/0→3/1 | 扩写 | 计算链表/四副作用算例 | 90 | 1 个标记片段通过 |
| 44_.px与.pxSp()扩展属性和函数.md | 101→174 | 2/2→5/3 | 扩写+修正 | 双 Density 算例/pxSp 精确换算 | 90 | 3 个标记片段通过 |
| 45_与Flutter设计稿适配的对比、风险与建议.md | 109→183 | 2/0→4/2 | 扩写 | 四方案选择表/不推荐理由 | 91 | 2 个标记片段通过 |
| 46_重组的跳过机制与位置记忆.md | 169→171 | 3/2→3/2 | 修正 | 强跳过版本边界/位置记忆=remember | 88 | 2 个标记片段通过 |
| 47_稳定性与强跳过模式.md | 124→125 | 2/0→2/1 | 修正 | 补编译标记/@NonSkippable 退出手段 | 90 | 1 个标记片段通过 |
| 48_derivedStateOf派生状态.md | 151→194 | 2/0→4/4 | 扩写+修正 | fullName 反例对/开销警示/remember 区分 | 90 | 4 个标记片段通过 |
| 49_rememberUpdatedState与rememberCoroutineScope.md | 168→193 | 2/1→3/2 | 扩写 | PulsingLog 保鲜场景/key 对比 | 92 | 2 个标记片段通过 |
| 50_snapshotFlow与Flow数据接入.md | 157→171 | 2/0→2/1 | 扩写 | 可编译防抖管线/职责边界段 | 91 | 1 个标记片段通过 |
| 51_animateAsState与Tween基础动画.md | 166→172 | 2/1→2/2 | 修正 | 包归属/初始值描述/graphicsLayer 延迟读 | 85 | 2 个标记片段通过 |
| 52_Animatable与手动动画控制.md | 277→282 | 6/2→6/2 | 修正（微调） | 伪代码真实化/官方链接 | 92 | 2 个标记片段通过 |
| 53_AnimatedVisibility与AnimatedContent与Crossfade.md | 245→251 | 5/2→5/2 | 修正 | 默认过渡值/尺寸行为/平滑打断 | 85 | 2 个标记片段通过 |
| 54_updateTransition多状态动画编排.md | 260→267 | 8/1→8/1 | 修正 | 包归属/transitionSpec 误判重写 | 84 | 1 个标记片段通过 |
| 55_动画规格spring与keyframes与repeatable.md | 268→285 | 9/3→10/3 | 修正 | 包归属/keyframes 默认时长 | 85 | 3 个标记片段通过 |
| 56_rememberInfiniteTransition无限动画.md | 150→158 | 1/1→1/1 | 修正 | 包归属/rotate 机制/移除动画偏好 | 83 | 1 个标记片段通过 |
| 57_Canvas与DrawScope基础.md | 158→161 | 1/0→1/1 | 修正 | Paint 残留错误/绘制边界 | 90 | 1 个标记片段通过 |
| 58_drawBehind与drawWithContent与drawWithCache.md | 157→159 | 1/1→1/1 | 修正 | 重绘范围机制/缓存描述 | 90 | 1 个标记片段通过 |
| 59_Path贝塞尔曲线与裁剪.md | 160→169 | 1/0→1/1 | 修正 | Path 构建表述/clipPath 用法+API 归属 | 88 | 1 个标记片段通过 |
| 60_BrushShader与渐变.md | 175→178 | 1/1→1/1 | 修正 | 缓存注释矛盾/radius 默认/ShaderBrush | 88 | 1 个标记片段通过 |
| 61_富文本AnnotatedString与Span.md | 137→139 | 1/1→1/1 | 修正 | annotations 精确化/ClickableText 边界 | 89 | 1 个标记片段通过 |
| 62_自定义Layout与measure与place.md | 166→176 | 1/1→1/1 | 修正（重点） | measure→layout→place 顺序重写/二次测量异常 | 90 | 1 个标记片段通过 |
| 63_Constraints与固有尺寸测量.md | 131→133 | 2/2→2/2 | 修正 | Constraints 真实 API/Intrinsic 触发方 | 89 | 2 个标记片段通过 |
| 64_自定义Modifier.md | 171→171 | 2/2→2/2 | 修正 | drawBehind 表述/绝对结论删除 | 92 | 2 个标记片段通过 |
| 65_手势检测pointerInput.md | 156→156 | 2/1→2/1 | 修正 | 消费传播机制/挂起特性 | 93 | 1 个标记片段通过 |
| 66_手势冲突与嵌套滚动.md | 185→194 | 2/0→2/2 | 修正 | onPreScroll 方向反转修复/取消传播 | 90 | 2 个标记片段通过 |
| 67_AndroidView与ComposeView互操作.md | 166→168 | 2/0→2/0 | 修正 | 默认策略事实/View 状态边界 | 92 | 无片段参与编译 |
| 68_ViewModel与StateFlow与UDF.md | 201→307 | 3/0→4/2 | 扩写+修正 | 四态迁移完整闭环/依赖坐标修正 | 90 | 2 个标记片段通过 |
| 69_Hilt依赖注入.md | 172→172 | 1/0→1/0 | 修正 | 补"需工程环境"声明 | 90 | 无片段参与编译 |
| 70_生命周期感知repeatOnLifecycle.md | 157→159 | 3/0→3/0 | 修正 | 三层职责区分/版本边界 | 91 | 无片段参与编译 |
| 71A_Navigation3与NavDisplay.md | 324→324 | 4/3→4/3 | 修正 | Scene/SceneStrategy 构件归属修正 | 90 | 3 个标记片段通过 |
| 71_导航进阶嵌套导航与类型安全导航.md | 161→161 | 1/0→1/0 | 通过 | Navigation 2.8 表述一致 | 90 | 无片段参与编译 |
| 72_状态持久化DataStore与Room.md | 171→171 | 1/0→1/0 | 通过 | 四层边界完整 | 89 | 无片段参与编译 |
| 73_协程进阶Dispatcher与Flow操作符.md | 159→183 | 3/0→4/1 | 扩写 | flatMapLatest 搜索取消场景 | 91 | 1 个标记片段通过 |
| 74_性能分析LayoutInspector与编译器报告.md | 160→171 | 3/0→4/2 | 修正 | 强跳过版本/场景拆分/边界声明 | 90 | 2 个标记片段通过 |
| 75_Lazy列表性能优化.md | 167→173 | 3/2→3/3 | 修正 | 常量 key 抛异常核正/contentType 对齐 | 90 | 3 个标记片段通过 |
| 76_ComposeUI测试.md | 128→130 | 1/0→1/0 | 修正 | 补测试编译边界声明 | 92 | 无片段参与编译 |
| 77_主题进阶动态取色与Shape与Typography.md | 154→154 | 1/0→1/0 | 修正（微） | 对比度改条件化建议 | 93 | 无片段参与编译 |
| 78_边缘到边缘与WindowInsets.md | 165→165 | 2/1→2/1 | 修正 | 替换编造 KDoc 引语/默认值核实 | 88 | 1 个标记片段通过 |
| 79_响应式布局与WindowSizeClass.md | 213→213 | 2/2→2/2 | 通过 | 与基线一致零改动 | 95 | 2 个标记片段通过 |
| 80_无障碍与BaselineProfile与发布质量.md | 158→158 | 2/1→2/1 | 修正 | 48dp 建议化/BaselineProfile 边界 | 90 | 1 个标记片段通过 |
| 99_源码文件索引.md | 37→39 | 0/0→0/0 | 修正 | 源码映射 10 处修正 | 95 | 无片段参与编译 |

## 基线与最终校验记录

| 命令 | 基线（扩写前） | 最终（扩写后） |
|---|---|---|
| python3 tools/extract_snippets.py | 60 片段 | 167 片段 |
| ./gradlew --no-daemon compileDebugKotlin | BLOCKED（SDK 未配置，见环境说明） | BUILD SUCCESSFUL |
| ./gradlew --no-daemon compileDebugKotlin -PsnippetsStrictWarnings --rerun-tasks | 同上 | BUILD SUCCESSFUL |
| 实验API审计（--no-file-optin，可选） | 未执行 | 命中 Ch06_01/Ch28_01，两章正文均有 @OptIn 说明，审计后已恢复默认抽取 |

## 环境说明

- 初次基线编译失败原因：`snippets/local.properties` 缺失且 `ANDROID_HOME` 未设置（SDK location not found）。本机存在 `~/Library/Android/sdk`（含 platform 36）与 JDK 21，创建 gitignore 的 `snippets/local.properties`（sdk.dir 指向本机 SDK）后恢复可执行，该文件不入库。
- `snippets/build.gradle.kts` 的 jvmTarget 由 1.8 调整为 11（含 compileOptions 11）：lifecycle 2.8+ 的 -android 构件以 JVM target 11 编译，其 inline 函数 `collectAsStateWithLifecycle`（第 68 章示例使用）要求消费方 jvmTarget ≥ 11 才能内联（编译器报错原文即为此依据）。此为本轮唯一一处编译支撑工程配置变更，未升级任何依赖版本。
