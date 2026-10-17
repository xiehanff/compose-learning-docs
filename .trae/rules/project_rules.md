# 项目规则（Trae）

本仓库是 Jetpack Compose 中文学习文档，不是可运行 App。修改任何章节前先读以下两份权威文档，正文不得与其冲突：

- `README.md`：片段编译标记（`<!-- compile -->` / `<!-- compile decls -->` / `<!-- compile prelude: ... -->`）与验证命令
- `文档基线与审校规范.md`：版本基线、证据等级、版本敏感表述规范、章节审校清单

## 硬性约束

1. **语言**：所有文档与交流使用中文；代码注释用中文。
2. **只改与需求直接相关的内容**：不顺手重构、不重排无关章节、不删用户注释代码。
3. **版本基线优先**：版本敏感结论以 `snippets/gradle/libs.versions.toml` 与 `文档基线与审校规范.md` 为准，正文不无边界使用「当前/最新/稳定/官方推荐」。升级依赖是独立任务，不得顺手升级 AGP、Kotlin、Compose BOM 或任何 AndroidX 依赖。
4. **官方来源**：重点 API 与版本敏感结论需附 Android Developers / Kotlin / AndroidX release notes 链接；不确定的事实先核对官方文档，禁止编造链接。
5. **区分事实与简化**：区分「官方保证 / 版本敏感 / 教学简化 / 作者建议」，绝对结论必须带适用条件。
6. **Flutter 对照**：类比章节（第 6 节）只做概念对照，不得声称两平台机制等价。
7. **编译验证边界**：`compileDebugKotlin` 通过只证明语法/类型/依赖解析，不代表运行时、布局、无障碍、性能或真机行为正确；不要在正文做此承诺。UI 测试 / Macrobenchmark / Baseline Profile 片段不在编译校验范围。

## 修改章节后必须执行的验证

```bash
python3 tools/extract_snippets.py                    # 重新生成片段与清单
cd snippets && ./gradlew --no-daemon compileDebugKotlin
cd snippets && ./gradlew --no-daemon compileDebugKotlin -PsnippetsStrictWarnings --rerun-tasks
```

- 三条全部通过才算完成；严格模式主要用于暴露已弃用 API。
- 实验 API 审计（可选）：`python3 tools/extract_snippets.py --no-file-optin` 后编译，报错即依赖实验 API 的片段，须确认对应章节已有 `@OptIn` 说明，然后**恢复默认抽取并重新编译**。
- AGP 8.6.1 对 compileSdk 36 的兼容警告是已知记录项，不要为消除它升级 AGP。
- 新增/删除章节文件时同步更新 `README.md` 目录、`00_完整学习规划.md`、`章节清单.txt`。

## 目录速览

- 根目录 `*.md`：章节正文（00—80，含 08A/26A/26B/71A）、规划、基线规范、阶段项目任务书
- `tools/extract_snippets.py`：从章节 Markdown 抽取带标记的 Kotlin 片段
- `snippets/`：片段编译工程（compileSdk 36；依赖见 `snippets/gradle/libs.versions.toml`；Material 3 Adaptive 不在 BOM 内，需显式版本）
- `.github/workflows/snippet-compile.yml`：CI 执行同样的抽取与编译校验
