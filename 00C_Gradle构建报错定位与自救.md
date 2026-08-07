# 预备篇 C：Gradle 构建报错定位与自救

## 1. 本章目标

这一章不承诺记住所有错误，而是建立一套遇到陌生错误也能执行的流程。

学完后，你应当能够：

- 从大量日志中找到第一个失败任务和第一条有效根因；
- 区分环境、依赖、代码、资源、打包和设备问题；
- 使用 `--stacktrace`、`--info`、`dependencies` 和 `dependencyInsight`；
- 按低风险到高风险的顺序处理缓存问题；
- 整理一份别人可以复现的求助信息。

## 2. 排错的总原则

```text
先复现
→ 再收集证据
→ 给错误分类
→ 一次只验证一个假设
→ 用相同命令确认修复
```

不要看到红字就修改。构建日志可能包含许多警告和后续失败，最靠后的错误不一定是根因。

## 3. 第一步：用 Wrapper 稳定复现

在项目根目录执行：

```bash
./gradlew :app:assembleDebug --stacktrace --console=plain
```

为什么这样做：

- `./gradlew` 使用项目规定的 Gradle；
- `:app:assembleDebug` 明确模块和目标；
- `--stacktrace` 补充异常调用链；
- `--console=plain` 去掉动态进度条，日志更适合搜索和分享。

如果命令本身找不到：

```bash
pwd
ls -la
```

确认当前目录中是否真的存在 `gradlew`、`settings.gradle.kts`。`No such file or directory` 可能只是终端不在项目根目录，与 Gradle 配置无关。

macOS/Linux 第一次执行若提示没有权限：

```bash
chmod +x gradlew
```

Windows 使用：

```powershell
.\gradlew.bat :app:assembleDebug --stacktrace --console=plain
```

## 4. 第二步：从哪里开始读日志

按顺序搜索：

1. `FAILURE: Build failed with an exception.`
2. `What went wrong:`
3. 第一个 `Execution failed for task`；
4. 第一个说明具体原因的 `Caused by`；
5. 编译错误中的文件、行号和错误码。

示意：

```text
Execution failed for task ':app:compileDebugKotlin'.
└── 失败阶段：Debug Kotlin 编译
    └── e: MainActivity.kt:42: Unresolved reference: Foo
        └── 根因候选：Foo 没有定义、没导入或依赖没加入
```

不要只复制：

```text
BUILD FAILED in 12s
```

这句话没有根因信息。

## 5. 第三步：建立版本证据

```bash
./gradlew --version
```

至少记录：

- Gradle 版本；
- Launcher/Daemon JVM 版本与路径；
- 操作系统；
- AGP、Kotlin、Compose 相关版本；
- `compileSdk`、`minSdk`、`targetSdk`；
- Android Studio 版本。

如果 Android Studio 构建成功但终端失败，比较 Android Studio Gradle JDK 与 `./gradlew --version` 的 JVM。两边不同是常见原因。

## 6. 按失败任务快速分类

| 失败任务/关键词 | 优先检查 |
| --- | --- |
| 插件解析、`Plugin ... was not found` | `pluginManagement`、插件版本、网络 |
| `requires Java`、`Unsupported class file` | JDK、Gradle、AGP 兼容关系 |
| `checkDebugAarMetadata` | `compileSdk`、minSdk 或依赖要求 |
| `compileDebugKotlin` | Kotlin 源码、Compose Compiler、KSP 生成代码 |
| `kspDebugKotlin` / `kaptDebugKotlin` | 处理器版本、注解、生成代码和依赖图 |
| `mergeDebugResources` / `processDebugResources` | XML、资源命名、重复资源 |
| `processDebugMainManifest` | Manifest 合并、组件属性、占位符 |
| `checkDebugDuplicateClasses` | 直接/传递依赖冲突 |
| `packageDebug` | 打包、磁盘空间、重复文件 |
| `minifyReleaseWithR8` | R8、缺失类、Keep 规则 |
| 安装任务或 `adb` | 设备、签名、存储、版本降级 |

分类的作用是缩小检查范围，不是凭关键词直接下结论。

## 7. 场景一：JDK、Gradle、AGP 不兼容

常见错误：

```text
Android Gradle plugin requires Java ...
Minimum supported Gradle version is ...
Unsupported class file major version ...
```

处理步骤：

1. 执行 `./gradlew --version` 确认实际 JVM；
2. 查看项目 Wrapper 版本；
3. 查看 AGP 官方兼容表；
4. 让 Android Studio Gradle JDK 与终端构建尽量一致；
5. 一次只调整兼容链上的必要项；
6. 使用原命令重新构建。

不要因为报 Java 错误就修改 `sourceCompatibility`。运行 Gradle 的 JDK、编译源码的 Java toolchain 和 Android 设备支持的 Java API 是三个相关但不同的概念。

## 8. 场景二：SDK 或 Build Tools 缺失

常见错误：

```text
SDK location not found
failed to find target with hash string 'android-XX'
Installed Build Tools revision ... is corrupted
```

处理步骤：

1. 在 SDK Manager 查看 `compileSdk` 对应平台是否安装；
2. 检查 `local.properties` 的 `sdk.dir` 是否指向当前电脑真实路径；
3. 不要把个人电脑的绝对 SDK 路径提交给团队；
4. 只重装错误明确指出的 SDK 组件，不要删除整个 SDK。

## 9. 场景三：插件或依赖下载失败

常见关键词：

```text
Could not resolve
Could not GET
Read timed out
PKIX path building failed
Unknown host
```

先判断属于：

- 没声明正确仓库；
- DNS/网络不可达；
- 代理配置错误；
- HTTPS 证书问题；
- 依赖版本根本不存在；
- 离线模式开启但本地没有缓存。

检查顺序：

1. 错误中请求的完整坐标和仓库地址；
2. `pluginManagement.repositories` 或依赖仓库是否正确；
3. Android Studio 是否误开 Offline Mode；
4. 浏览器/命令行能否访问相应官方仓库；
5. 代理是否同时配置给 IDE 和 Gradle；
6. 版本号是否真实存在。

确认缓存元数据异常后才尝试：

```bash
./gradlew :app:assembleDebug --refresh-dependencies
```

它会增加网络请求，不应成为每次构建的固定参数。

## 10. 场景四：依赖版本冲突或重复类

先查看依赖树：

```bash
./gradlew :app:dependencies --configuration debugRuntimeClasspath
```

针对一个依赖追踪选择原因：

```bash
./gradlew :app:dependencyInsight \
  --dependency 依赖名称 \
  --configuration debugRuntimeClasspath
```

重点看：

- 谁直接声明了它；
- 哪个传递依赖又引入了它；
- Gradle 最终选择了哪个版本；
- 是否同时存在旧 support 库与 AndroidX；
- 是否同时引入本地 JAR/AAR 和相同远程依赖。

修复优先级：

1. 删除重复的直接依赖；
2. 统一版本来源，例如 Version Catalog 或 BOM；
3. 升级真正不兼容的上游库；
4. 最后才考虑精确 `exclude`。

不要看到冲突就全局强制一个版本。强制版本可能让编译通过，却在运行时触发二进制不兼容。

## 11. 场景五：KSP、KAPT、Hilt 或 Room 生成代码失败

常见表现：

- 找不到 `*_Impl`、`Hilt_*`、`Dagger*` 等生成类型；
- `kspDebugKotlin` 或 `kaptDebugKotlin` 失败；
- Hilt 报 MissingBinding、重复绑定或作用域错误；
- Room 报 Entity、DAO、Query 校验错误。

处理顺序：

1. 先读处理器给出的第一条业务错误；
2. 检查插件是否应用在正确模块；
3. 检查处理器依赖是否使用 `ksp`/`kapt` 而非普通 `implementation`；
4. 检查插件、处理器、Kotlin/AGP 的兼容性；
5. 修复注解或依赖图，而不是手写本应生成的类；
6. 重新执行具体失败任务。

代码生成失败通常会直接导致编译失败，不应描述成“注解静默失效，直到运行时才发现”。

## 12. 场景六：Kotlin/Compose 编译错误

常见类别：

- `Unresolved reference`：符号不存在、未导入或依赖缺失；
- `Type mismatch`：实际类型与参数类型不一致；
- `Composable invocations can only happen...`：在非 Composable 上下文调用；
- Compose Compiler/Kotlin 版本不匹配；
- 使用了当前依赖版本尚未提供的 API。

先点击错误指向的文件和行号。只有多个模块同时出现相同编译器内部错误时，才优先怀疑工具链兼容，而不是业务源码。

## 13. 场景七：资源与 Manifest

资源文件名只能使用规定字符。重复资源、无效 XML、缺少主题或颜色都可能让 `processResources` 失败。

Manifest 合并错误应查看合并报告，确定冲突来自 app 还是某个依赖。不要在不了解来源时机械添加 `tools:replace`，它可能掩盖依赖真正要求的配置。

## 14. 场景八：构建成功但安装/运行失败

先把问题分层：

```text
assembleDebug 成功？
├── 否：仍是构建问题
└── 是
    ├── installDebug 失败：设备、adb、签名、存储、版本
    └── 安装成功但闪退：查看 Logcat 运行时异常
```

常用检查：

```bash
adb devices
./gradlew :app:installDebug
```

编译成功不能证明运行时依赖、权限、网络和业务逻辑都正确。

## 15. 缓存问题的安全处理阶梯

只有错误证据指向缓存或增量产物时，按以下顺序处理，每一步后重新验证：

1. 重新执行失败任务；
2. 使用 `--rerun-tasks` 验证任务输出；
3. 执行项目 `clean`；
4. 停止当前项目 Gradle Daemon：`./gradlew --stop`；
5. 使用 `--refresh-dependencies` 刷新依赖元数据；
6. 最后才考虑删除明确损坏的单个缓存目录。

不要默认删除整个用户 Gradle 缓存。它影响所有项目、需要重新下载大量文件，并且不能修复配置和源码错误。

## 16. 如何提供高质量求助信息

一个可诊断的问题描述应包含：

```text
目标：执行什么任务，期望得到什么
环境：OS、Android Studio、Gradle、JVM、AGP、Kotlin
复现命令：完整命令
失败任务：第一个 FAILED task
错误正文：What went wrong + 第一条有效 Caused by
改动范围：出错前修改了哪些文件
已尝试：每一步及其结果
```

分享日志前删除：

- 签名密码和密钥路径；
- 私有仓库凭证；
- Token、Cookie、Authorization；
- 用户隐私数据；
- 不需要公开的内部域名和源码。

## 17. 动手排错练习

建议在练习分支中分别制造并修复：

1. 把 `compileSdk` 改成未安装的平台；
2. 写错一个依赖版本；
3. 重复引入同一个本地/远程库；
4. 在 Kotlin 文件中引用不存在的函数；
5. 给资源文件使用非法名称；
6. 写一个错误的 Room Query 或 Hilt Binding。

每次都记录：失败任务、错误分类、根因、最小修复、验证命令。

验收标准不是“最后变绿”，而是你能解释为什么失败、为什么这个修复有效。

## 18. 本章总结

Gradle 报错并不可怕，可怕的是没有固定流程地同时修改很多地方。始终使用 Wrapper 复现，抓住第一个失败任务和有效根因，用版本与依赖报告验证假设，一次只改一个原因。排错能力来自证据链，不来自记住多少错误文本。

官方参考：

- [Gradle 命令行选项](https://docs.gradle.org/current/userguide/command_line_interface.html)
- [Gradle 构建排错](https://docs.gradle.org/current/userguide/troubleshooting.html)
- [查看与诊断依赖](https://docs.gradle.org/current/userguide/viewing_debugging_dependencies.html)
- [Android 依赖解析错误](https://developer.android.com/build/dependency-resolution-errors)
- [Android 构建中的 JDK](https://developer.android.com/build/jdks)
