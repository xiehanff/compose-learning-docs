# 项目规则（适用任何 AI coding agent：pi、Codex、Claude Code 等）

本仓库是 Jetpack Compose 中文学习文档，不是可运行 App。任何 agent 修改章节前先读以下两份权威文档，正文不得与其冲突：

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
8. **站点与正文分离**：静态站点只渲染章节 `*.md`，`README.md` 不上站。面向读者的内容放章节，面向维护者的构建/校验/部署说明放 `README.md` 与 `AGENTS.md`。

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

- 根目录 `*.md`：章节正文（00—87，含 08A/26A/26B/71A）、规划、基线规范、阶段项目任务书
- `practice/mock-server/README.md`：实践篇 mock 服务（ymq-mall-json-server）的启动说明与接口速览
- `tools/extract_snippets.py`：从章节 Markdown 抽取带标记的 Kotlin 片段
- `snippets/`：片段编译工程（compileSdk 36；依赖见 `snippets/gradle/libs.versions.toml`；Material 3 Adaptive 不在 BOM 内，需显式版本；实践篇网络/图片/导航依赖同见该文件）
- `snippets/src/main/kotlin/support/SnippetsSupport.kt`：`.px`/`.pxSp()` 与 Nav3 路由 key 支撑
- `snippets/src/main/kotlin/support/MallSupport.kt`：实践篇商城网络层支撑（DTO/MallApi/NetworkClient/MallRepo，与第 82 章正文同构），81—87 章片段 import 它编译
- `.github/workflows/snippet-compile.yml`：CI 执行同样的抽取与编译校验
- `src/`、`index.html`、`vite.config.js`、`public/fonts/`：GitHub Pages 静态站点（见下节）

## 静态站点（GitHub Pages）

仓库内容会发布为 GitBook 风格的文档站：<https://xiehanff.github.io/compose-learning-docs/>。站点是章节 Markdown 的只读渲染层，不复制正文，也不维护第二份目录。

### 站点的取舍（改动前必须知道）

- **只有根目录 `*.md` 会上站**（`import.meta.glob('../*.md')`）。标题取自文件内第一个一级标题，侧边目录按文件名数字前缀分组：`0` 开始学习、`1—45` 基础篇、`46—80` 进阶篇、`81—90` 实践篇；数字前缀不在这些区间或无数字前缀的文件不上站。
- **维护者文档不上站**：`src/App.vue` 的 `HIDDEN_FILES` 排除 `README.md`、`AGENTS.md` 和 `docs-expansion-*.md` 审计报告。README 只服务 agent（片段标记、验证命令、站点维护说明），因此**不要把面向读者的教学内容写进 README**，也不要把面向维护者的构建/校验说明写进章节正文。
- 站点首页固定为 `00_完整学习规划.md`（`HOME_FILE`），README 里给读者的入口仍是这个规划页。
- 章节之间跳转用相对 `.md` 链接即可，渲染时会改写成站内路由；指向仓库外部的链接原样保留。

### 本地开发与构建

```bash
timeout 300 npm ci                 # 首次安装（Node 22，依赖见 package.json）
timeout 120 npm run dev            # 本地预览 http://localhost:5173
timeout 120 npm run build          # 产出 dist/，用于自检渲染结果
```

- 站点样式与逻辑集中在 `src/style.css`（GitBook 风格设计令牌：主题色 `--accent:#fe551b`）、`src/App.vue`（Markdown 渲染、目录分组、上下章节导航、搜索）。
- 字体来自 `public/fonts/`（Cascadia Next SC、Google Sans Mono），参考 myblog 的排版方案；不要改成外链字体 CDN。
- Markdown 渲染开启 `html: true`、代码高亮、标题锚点，并支持 GitHub 风格提示框（`> [!NOTE]`、`TIP`、`IMPORTANT`、`WARNING`、`CAUTION`）。
- 新增代码块语言时，在 `src/App.vue` 里注册对应 `highlight.js/lib/languages/*`，否则该语言不高亮。

### 部署

- `.github/workflows/pages.yml` 在 push `main` 或手动触发时执行 `npm ci && npm run build`，用官方 Pages Actions 发布 `dist/`。
- 站点子路径由 `vite.config.js` 决定：CI（`GITHUB_ACTIONS`）下是 `/compose-learning-docs/`，本地是 `/`。换成自定义域名时要同步修改该 base。
- 站点发布与片段编译校验是两套独立流程：改章节仍要跑上面的 `extract_snippets.py` + `compileDebugKotlin`。
