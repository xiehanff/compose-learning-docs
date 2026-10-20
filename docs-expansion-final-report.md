# 文档扩写最终验收报告

> 已基于 `657c5333176c4822d6d570ef2d1133cf78b700a2`（main）完成 Compose 学习文档扩写与复审，工作分支 `docs/expand-compose-course`。原轮交付截至 `b36e4f5`，共 18 个提交（16 个内容批次 + 1 个支撑配置 + 本报告）；2026-10-20 续作复核修正了以下统计口径，并重新执行抽取、普通编译与严格编译。

## 总量与覆盖率

| 指标 | 数值 |
|---|---|
| 审计章节 | 91 个 Markdown 文件（89 篇正文 + 00 规划 + 99 索引），100% 覆盖 |
| 修改章节 | 84 篇（82 篇正文 + 00 规划 + 99 索引，以 Git 基线差异为准） |
| 审校通过零改动 | 7 篇（00B/00C/00E/03/71/72/79）；第 52 章实际有修改，已从此项移除 |
| 91 篇审计文档总行数 | 15526 → 21225（+5699；按 `splitlines()` 统计，含空行，不将文件末尾换行另计一行） |
| 带编译标记的代码块 | 60 → 167（同时统计 HTML 注释和围栏信息串标记，与抽取器口径一致；覆盖 75 篇文档） |
| 修复事实错误 | 30+ 处（每篇明细见 `docs-expansion-audit.md` 与 `docs-expansion-progress.md`） |
| 阶段分布 | 按批次归属去重：M1 17 篇、M2 19 篇、M3 10 篇、M4 32 篇、M5 13 篇，合计 91 篇；各批具体范围见进度记录 |

## 通过的校验

- `python3 tools/extract_snippets.py`：167 个片段抽取成功
- `./gradlew --no-daemon compileDebugKotlin`：BUILD SUCCESSFUL
- `./gradlew --no-daemon compileDebugKotlin -PsnippetsStrictWarnings --rerun-tasks`：BUILD SUCCESSFUL
- 实验 API 审计（`--no-file-optin`，可选）：原轮记录命中 Ch06_01/Ch28_01，两章正文均有 @OptIn 说明，审计后恢复默认抽取；本次续作未重复该可选审计
- 续作相对链接校验：检查 121 个 Markdown 相对文件链接，失效 0 个；排除围栏中的示例，不检查锚点、外链或其他类型目标
- 续作完整日志：抽取 `/tmp/compose-docs-recheck-extract.log`、普通编译 `/tmp/compose-docs-recheck-normal.log`、严格编译 `/tmp/compose-docs-recheck-strict.log`（本机临时文件）
- 基线环境说明：初次基线编译因 `snippets/local.properties` 缺失失败（SDK location not found），配置本机 SDK 路径（文件 gitignore 不入库）后恢复执行；`snippets/build.gradle.kts` jvmTarget 1.8→11（lifecycle 2.8+ inline API 要求，未升级任何依赖）

## 未能执行的校验及原因

- 模拟器/真机运行验证：未执行。原轮记录的任务边界禁止启动模拟器；第 23/34 章给出状态恢复的操作与预期观察，第 76/80 章说明设备/仪器验证边界。导航 back stack、动画外观、TalkBack、性能和 Baseline Profile 等行为不能由本次编译证明
- UI 测试 / Macrobenchmark 片段：不在 snippets 编译范围（第 76/80 章正文已显式声明此边界）
- 第 4/34/71 章部分 Navigation 2 代码：snippets 工程不含 navigation-compose 依赖，按仓库既有口径标注「需工程环境，不参与编译校验」（第 4 章 §1 已注明）

## 最有价值的 5 个前后差异

1. **编译片段覆盖 60→167**：第 2/4/5/6 章的自包含代码块纳入真实编译；第 47/57/59/66/73/74/75 章现有示例补标记。共 75 篇文档有片段参与校验，全部通过严格模式；其余文档及未标记代码块不在编译范围内
2. **第 62 章 Layout 机制重写**：原文把调用顺序写成「measure → place → layout」；重写为真实 API 结构「先测量全部子项 → `layout(width, height)` 声明自身尺寸 → place 在摆放 lambda 内执行」，并按基线构件写明同一 Measurable 二次测量抛异常与 Intrinsic/SubcomposeLayout 替代方案
3. **第 34 章核心示例重构**：从多个互不关联的节选重构为「双 Tab + 搜索输入 + 列表滚动位置」一条主线跑三种保留策略，把「实例保活 / UI 值恢复 / ViewModel / back stack」四个常被混为一谈的概念分离
4. **M3 密度章数字算例化与划线**：41–45 章从概念描述升级为可验算的数字算例（px/dp/sp 换算、缩放系数链、48dp 触控目标在缩放下的 7.6mm→4.0mm），并把「通用 Compose 知识」与「原 Demo 私有方案（DesignWidthScale/.px）」明确划线
5. **30+ 处事实错误修复**：包括编造的 KDoc 引语（78 章）、嵌套滚动方向反转（66 章）、不存在的 `Modifier.clipPath`（59 章）、常量 key 行为（75 章，实为运行时抛异常）、API 包归属与默认值系列错误（51–56/60/63 章）、虚构 Flutter API（62 章）

## 未完成事项

原轮记录的 M0–M5 文档批次已完成；本次续作发现的统计与验证范围表述已修正。设备/仪器行为仍未验证，见上文边界。

## 后续建议（非本轮范围）

- 若要让第 4/34/71 章导航片段参与编译，需为 snippets 工程新增 navigation-compose 依赖并扩展 IMPORT_HEADER——属独立任务，本轮未动版本基线
- 第 68 章起 lifecycle 相关片段依赖 jvmTarget 11，未来升级 Compose BOM 或 lifecycle 后需复查内联兼容性
- 真机验证优先项：第 23/34 章旋转与进程死亡恢复、第 28 章底部弹层手势、第 76 章 UI 测试、第 80 章 TalkBack 与 Baseline Profile
