# 文档扩写进度记录

- 基准 HEAD：`657c5333176c4822d6d570ef2d1133cf78b700a2`（main）
- 工作分支：`docs/expand-compose-course`（自 main 创建，创建时工作区干净，无用户未提交改动）
- 执行日期：2026-10-20
- 执行方式：15 个批次并行子代理（每批负责互不重叠的章节文件，仅修改 Markdown）；抽取/编译校验、编译错误修复、审计与进度文件、git 提交由主控统一执行

## 校验执行记录（按实际时序）

| 轮次 | 范围 | 结果 |
|---|---|---|
| 基线三件套（HEAD 未修改） | 抽取 60 片段成功；`compileDebugKotlin` 失败：SDK location not found（`ANDROID_HOME` 未设置且 `snippets/local.properties` 缺失）。定位到本机 `~/Library/Android/sdk`（含 platform 36）+ JDK 21，创建 gitignore 的 `local.properties` 后恢复 | 环境问题，非代码问题 |
| 第一波校验（M1+M2 共 34 篇修改后） | 抽取 129 片段；编译失败：`Ch26B_02.kt` 片段内 import 带行内注释未被提升（语法错误）。修复 26B 章 import 写法 | 暴露 1 处片段写法问题，已修复 |
| 最终校验（全部 89 篇修改后，第一轮） | 抽取 167 片段；编译失败 3 组：Ch32_03（foundation.pager 缺 import）、Ch59_01（`Modifier.clipPath` 不存在——正文概念错误）、Ch68_02（sealed interface 实现误写构造括号 + lifecycle 2.8+ inline API 需 jvmTarget 11）。逐项修复：32 章片段补 import；59 章代码改 `drawWithContent + DrawScope.clipPath` 并重写正文 4 处 API 归属表述；68 章去括号；`snippets/build.gradle.kts` jvmTarget 1.8→11（见依据） | 3 组失败全部修复 |
| 最终校验（第二轮） | 普通编译 BUILD SUCCESSFUL；严格编译失败：3 处警告（50/73 章 `debounce` 缺 FlowPreview opt-in、59 章 `quadraticBezierTo` 已弃用）。修复：50/73 章片段与正文补 `@OptIn(FlowPreview)`；59 章改用 `quadraticTo` 并更新对照表述 | 3 处警告全部修复 |
| 最终校验（第三轮，最终状态） | 抽取 167 片段；普通编译 **BUILD SUCCESSFUL**；严格编译（-PsnippetsStrictWarnings --rerun-tasks）**BUILD SUCCESSFUL** | **全部通过** |
| 实验 API 审计（可选，--no-file-optin） | 命中 Ch06_01、Ch28_01；核对第 6、28 章正文均有 `@OptIn(ExperimentalMaterial3Api)` 说明与漏写后果提示；审计后已恢复默认抽取并确认 | 通过 |

- 设备行为验证：NOT RUN（本任务禁止模拟器/真机；正文相关结论标注「待真机验证」）
- `snippets/build.gradle.kts` jvmTarget 1.8→11 依据：lifecycle 2.8+ 的 -android 构件以 JVM target 11 编译，第 68 章示例使用的 `collectAsStateWithLifecycle` 为 inline 函数，编译器报错原文「Cannot inline bytecode built with JVM target 11 into bytecode that is being built with JVM target 1.8」。未升级任何依赖版本，仅调整编译目标（AGP 8.6.1 / JDK 21 均支持）。

## Batch M1-1 — 列表与滚动（16–20）
- 提交：`5980b0b`
- 修改文件：16/17/18/19/20 五章
- 已补齐：16 章独立 DemoItem 示例（固定头部+列表+下标标签）、item/items/itemsIndexed 拆分、懒≠分页；17 章 key 两版对照与状态归属逐位置结论；18 章 Fixed/Adaptive 列数算例；19 章协程程序滚动；20 章双容器对照与选型决策表
- 删除/修正的不准确结论：无（原文方向正确）
- 新增可编译示例：11
- 抽取/编译：随最终统一校验通过

## Batch M1-2 — 状态基础（21–25）
- 提交：`1f30b63`
- 修改文件：21/22/23/24/25 五章
- 已补齐：普通 var 反例与两步失效解释；父/子读取双场景日志对照；四场景状态存留对照表；受控组件三步重构；三种 Effect 对照与 7 步 key 追踪表
- 修正：无硬错误（23 章按四场景展开并标明恢复前提）
- 新增可编译示例：12
- 抽取/编译：随最终统一校验通过

## Batch M1-3 — 状态保留（33–35）
- 提交：`98a32f3`
- 修改文件：33/34/35 三章
- 已补齐：33 章三方案分层（when/叠放保活/导航返回栈）；34 章重构为「双 Tab + 搜索输入 + 列表滚动位置」三策略主线，四概念（保活/UI 值恢复/ViewModel/back stack）分离；35 章四状态所有者/生命周期表
- 修正：无（34 章按任务书要求重构核心示例，原文正确段落并入 3.3 节保留）
- 新增可编译示例：3（Nav2/ViewModel 相关代码按仓库口径标注「需工程环境」不加标记）
- 抽取/编译：随最终统一校验通过

## Batch M1-4 — 派生状态与 UDF（48–50、68）
- 提交：`9079555`
- 修改文件：48/49/50/68 四章
- 已补齐：48 章官方同款 fullName 反例对与「不需要 derivedStateOf」边界；49 章 PulsingLog 保鲜场景与 key 对比；50 章可编译防抖管线与职责边界；68 章四态（Loading/Empty/Error/Content）完整闭环
- 修正：68 章依赖坐标由包名改为真实 Gradle 构件坐标；68 章 sealed interface 实现误写构造括号（主控编译发现后修复）
- 新增可编译示例：8
- 抽取/编译：随最终统一校验通过

## Batch M2-1 — 布局基础（7–10 含 8A）
- 提交：`bf7f71e`
- 修改文件：07/08/08A/09/10 五章
- 已补齐：7 章顺序对照与命中区域演示；8 章三色块 Arrangement/剩余空间；8A 换行位置预测；9 章重叠命中与角标；10 章三容器选型表
- 修正：7 章「background 后 clip 会裁掉底色」错误（clip 只裁内层内容）；10 章「Surface 有最小触摸尺寸」按基线构件核正（命中外扩由 clickable 实现，不可点击 Surface 无保障）
- 新增可编译示例：12
- 抽取/编译：随最终统一校验通过

## Batch M2-2 — 组件与尺寸（11–15）
- 提交：`ac751ea`
- 修改文件：11/12/13/14/15 五章
- 已补齐：sp 可访问性与截断四变体；ContentScale 全表与预测算例；padding/size 顺序约束账（数字表）；weight 测量三步与 Expanded 不等价处；裁剪先后顺序与溢出对照
- 修正：无
- 新增可编译示例：11
- 抽取/编译：随最终统一校验通过

## Batch M2-3 — 交互组件（26–28 含 26A/26B）
- 提交：`5f84ecf`
- 修改文件：26/26A/26B/27/28 五章
- 已补齐：加载态双防线；26A 键盘动作与焦点（requestFocus 无参已弃用的版本警示）；设置开关 vs 列表多选；27 章异步确认闭环与 dismiss 边界；28 章 hide() 收法与 Properties
- 修正：26B 片段 import 行内注释导致未被提升（主控编译发现后修复）
- 新增可编译示例：6
- 抽取/编译：随最终统一校验通过

## Batch M2-4 — 反馈与导航（29–32）
- 提交：`4c43b18`
- 修改文件：29/30/31/32 四章
- 已补齐：Snackbar 连点排队观察与 Result 两分支；sealed 三态映射；底栏三目的地闭环与两种切换模式；TabRow+HorizontalPager 联动（Pager 基线已稳定无需 opt-in，正文注明版本边界）与 NavigationBar 选型表
- 修正：无
- 新增可编译示例：6；32 章 Pager 片段补 foundation.pager import（主控编译发现后修复）
- 抽取/编译：随最终统一校验通过

## Batch M3-1 — Demo 数据结构（36–40）
- 提交：`f606a97`
- 修改文件：36/37/38/39/40 五章
- 已补齐：36 章非官方类型划线与 DemoEntry 独立建模；37 章函数类型差异与调用时机反例；38 章 object 初始化时机与单例三代价；39 章可空操作符链与智能转换；40 章 7 步可照走清单（含无原工程替代方案）
- 修正：无
- 新增可编译示例：8（Nav2 代码按口径标注「需工程环境」）
- 抽取/编译：随最终统一校验通过

## Batch M3-2 — 密度与适配（41–45）
- 提交：`7810957`
- 修改文件：41/42/43/44/45 五章
- 已补齐：41 章 px/dp/sp 换算公式与算例；42 章最小 provider-consumer 与两种 CompositionLocal 差异；43 章计算链与四副作用（含 48dp 触控目标算例）；44 章双 Density 算例；45 章四方案选择表与不推荐生产的理由
- 修正：44 章 pxSp 换算精确化（density 乘两次的机制推导，标注教学简化）
- 新增可编译示例：7
- 抽取/编译：随最终统一校验通过

## Batch M4-1 — 跳过与动画（46–47、51–56）
- 提交：`b8cd1d8`
- 修改文件：46/47/51/52/53/54/55/56 八章
- 已补齐：46 章强跳过版本边界与位置记忆=remember 机制；56 章无障碍「移除动画」偏好；53/54 章动画中途平滑打断/retarget
- 修正 9 处：51/54/55/56 章 API 包归属（animation.core）；55 章 keyframes 默认时长（默认 300ms 非编译报错）；56 章 Modifier.rotate 机制（graphicsLayer 而非布局）；53 章 AnimatedVisibility 默认过渡值与 Crossfade 尺寸行为；54 章 transitionSpec 误判重写；56 章「完全等价」违规表述
- 新增可编译示例：2（47/51 章现有示例补标记）
- 抽取/编译：随最终统一校验通过

## Batch M4-2 — 绘制与自定义布局（57–63）
- 提交：`510bf90`
- 修改文件：57/58/59/60/61/62/63 七章
- 已补齐：57 章绘制作用域边界（画多大不改布局、绘制内容不进语义树）；62 章调用顺序重写（先 measure 全部子项 → layout(w,h) 声明尺寸 → place 在摆放 lambda 内）与二次测量抛异常的基线事实；63 章 Intrinsic 触发方与默认实现机制
- 修正 10 处：57 章 Paint 残留（错套 Flutter 模型）；59 章 Path 构建表述 + `Modifier.clipPath` 不存在（主控编译发现后与批次审校共同修正：代码改 `drawWithContent`，正文 4 处 API 归属重写）+ quadraticBezierTo 弃用改 quadraticTo；60 章 radialGradient 默认 radius/ShaderBrush 机制；62 章 place 裁剪说法/Flutter 虚构 API；63 章 Constraints 不存在的工厂 API/越界协议
- 新增可编译示例：2
- 抽取/编译：随最终统一校验通过

## Batch M4-3 — 手势与架构（64–67、69–73 含 71A）
- 提交：`2fb2716`
- 修改文件：64/65/66/67/69/70/71/71A/72/73 十章
- 已补齐：65 章挂起特性与消费传播准确表述；66 章取消传播段；67 章 View 状态边界；70 章三层职责区分（repeatOnLifecycle/flowWithLifecycle/collectAsStateWithLifecycle）；73 章场景四 flatMapLatest 搜索取消重建
- 修正 4 处：66 章 onPreScroll 方向反转（delta 符号约定写反）；67 章 ViewCompositionStrategy 默认值误解；71A 章 Scene 构件归属（navigation3-ui 而非 runtime）；69 章补「需工程环境」声明
- 新增可编译示例：3
- 抽取/编译：随最终统一校验通过

## Batch M4-4 — 性能测试发布（74–80）
- 提交：`967fafe`
- 修改文件：74/75/76/77/78/79/80 七章（79 章零改动通过）
- 已补齐：74 章 Macrobenchmark 边界声明与场景拆分；76 章测试片段不在 snippets 编译范围的显式声明；80 章 48dp 建议化与 BaselineProfile 待真机边界
- 修正 2 处：75 章常量 key 行为（重复 key 运行时抛异常而非共享身份）；78 章编造 KDoc 引语替换为构件核实原句
- 新增可编译示例：3
- 抽取/编译：随最终统一校验通过

## Batch M5-1 — 预备篇与前六章（00A–00E、01–06）
- 提交：`c1f927c`
- 修改文件：00A/00D/01/02/04/05/06 七章（00B/00C/00E/03 审校通过零改动，保留理由：版本表述已自带边界、5 条外链核实有效、表述准确且超前内容已链接）
- 已补齐：Kotlin 门槛回链（00D）；第 1/2/4/5 章代码块逐块分类（新增 14 个真正自包含片段的编译标记，项目节选与需 Activity 环境的维持原状）；第 4 章 Nav2/Nav3 并存说明；第 6 章扩写（Scaffold 骨架完整例、innerPadding/insets 传递链、Flutter 边界表）
- 修正：00A 模板版本变化说明；05 章 Text 无 onClick 的版本边界（≤1.3 存在该重载，基线 1.4.0 无）
- 新增可编译示例：14
- 抽取/编译：随最终统一校验通过

## Batch M5-2 — 索引与阶段案例一致性
- 提交：`5a7c6a5`
- 修改文件：00_完整学习规划.md、99_源码文件索引.md（阶段实践案例.md 与 阶段项目_文章列表与详情.md 审校通过零改动：15 个部分均有验收标准、术语与扩写后正文一致、核心练习不依赖原工程私有实现）
- 已补齐：00 规划章节结构描述对齐实际小节；99 索引修正约 10 处失效/遗漏映射
- 抽取/编译：随最终统一校验通过；全库相对链接脚本校验全部有效

## 支撑修复（跨批次）
- 提交：`fb28e3c`（snippets jvmTarget 11）
- 主控统一修复的编译问题：26B/32 章片段 import 写法、59 章 clipPath 用法与 API 归属、68 章 sealed 括号与 JVM target、50/73 章 FlowPreview opt-in、59 章 quadraticTo。全部为片段真实修正，无占位假类型
- 仍未完成事项：无
