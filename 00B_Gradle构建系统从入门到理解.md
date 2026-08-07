# 预备篇 B：Gradle 构建系统从入门到理解

## 1. 本章目标

学完后，你应当能够：

- 解释 Gradle、AGP、插件、任务和依赖之间的关系；
- 知道一项配置应该写进哪个文件；
- 看懂新项目中的 Kotlin DSL 和 Version Catalog；
- 理解 Debug/Release、Build Type 与 Build Variant；
- 按兼容关系升级构建工具，而不是把所有版本改成“最新”。

## 2. Gradle 到底在做什么

构建不是简单地把 `.kt` 文件变成 APK。一个 Android 应用通常要经过：

```text
读取项目和模块配置
→ 解析插件与依赖
→ 生成代码（如果使用 KSP/KAPT）
→ 编译 Kotlin/Java
→ 处理 Manifest 与资源
→ 转换和合并字节码
→ Debug 签名或 Release 签名
→ 打包 APK/AAB
→ 运行测试、Lint 或其他质量任务
```

Gradle 使用“任务”描述这些步骤。任务之间有输入、输出和依赖关系，因此 Gradle 知道执行顺序，也能跳过没有变化的工作。

应用 `com.android.application` 插件后，AGP 会注册 Android 所需的大量任务。你通常不需要自己编写编译和打包任务。

## 3. 六个容易混淆的版本

| 版本 | 常见位置 | 决定什么 |
| --- | --- | --- |
| Android Studio | IDE About 页面 | IDE 能支持哪些 AGP、开发工具功能 |
| JDK | Gradle 设置、Daemon JVM 或环境变量 | 用哪个 JVM 运行 Gradle |
| Gradle | `gradle-wrapper.properties` | 构建系统本身的版本 |
| AGP | `libs.versions.toml` 或根构建脚本 | Android 构建能力与 DSL |
| Kotlin | 插件版本或新版 AGP 内置 Kotlin 配置 | Kotlin 编译能力和语言版本 |
| Compose BOM/库 | `libs.versions.toml`、dependencies | Compose UI 库的组合版本 |

它们不是同一个版本号，也不能全部随意升级到最新。

正确顺序通常是：

1. 明确目标 Android Studio 和 SDK；
2. 查 AGP 官方兼容表；
3. 选择兼容的 Gradle Wrapper；
4. 选择 AGP 要求的 JDK；
5. 再处理 Kotlin、Compose Compiler 与 Compose 库；
6. 每次只升级一组强相关版本，并立即构建和测试。

## 4. `settings.gradle.kts`：项目有哪些模块和仓库

典型内容如下：

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "ComposeLearning"
include(":app")
```

它主要回答：

- 插件去哪里下载；
- 普通依赖去哪里下载；
- 根项目叫什么；
- 构建包含哪些模块。

插件仓库与普通依赖仓库不是一回事。插件解析失败时，只在普通 `repositories` 里添加地址可能没有效果。

## 5. 根级与模块级 `build.gradle.kts`

根级构建脚本通常声明插件，但不立即应用到根项目：

下面是结构示意，不是让你覆盖模板的完整脚本。数字仅用于解释字段，应保留自己项目模板生成的兼容版本：

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.compose.compiler) apply false
}
```

app 模块再应用实际需要的插件：

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.example.composelearning"
    compileSdk = 36 // 示例：使用项目实际安装和要求的稳定 API

    defaultConfig {
        applicationId = "com.example.composelearning"
        minSdk = 24  // 示例：按产品支持范围选择
        targetSdk = 36 // 示例：按当前发布要求和迁移计划选择
        versionCode = 1
        versionName = "1.0"
    }

    buildFeatures {
        compose = true
    }
}
```

这里故意不提供一组“永远正确”的版本数字。应以你创建项目时的稳定模板和 AGP 兼容表为基线，避免几年后照抄过时版本。

## 6. `libs.versions.toml`：版本目录

Version Catalog 把版本、库坐标和插件集中管理：

下面同样只展示 TOML 的关系；`<...>` 是占位说明，不能直接复制成版本号：

```text
[versions]
agp = "<项目模板中的 AGP 版本>"
compose-bom = "<稳定 Compose BOM 版本>"

[libraries]
androidx-compose-bom = {
  module = "androidx.compose:compose-bom",
  version.ref = "compose-bom"
}
androidx-compose-material3 = {
  module = "androidx.compose.material3:material3"
}

[plugins]
android-application = {
  id = "com.android.application",
  version.ref = "agp"
}
```

模块里使用：

```kotlin
dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
}
```

### BOM 解决什么问题

Compose BOM 管理一组 Compose 库之间的兼容版本。使用 BOM 后，受它管理的 Compose 依赖通常不用各自填写版本号。

BOM 不会管理：

- AGP；
- Gradle；
- JDK；
- 所有 AndroidX 库；
- Kotlin 和其他第三方库。

所以“用了 Compose BOM 就不存在版本兼容问题”是错误理解。

## 7. 插件和依赖不是一回事

插件扩展构建能力：

```kotlin
plugins {
    id("com.android.application")
}
```

依赖让源码可以使用某个库：

```kotlin
dependencies {
    implementation("group:name:version")
}
```

以 Hilt 为例，通常既需要构建插件/代码生成配置，也需要运行时库。只加注解库但没有正确配置代码生成，会在编译阶段暴露生成类型或依赖图错误。

## 8. 依赖配置是什么意思

常见配置：

| 配置 | 用途 |
| --- | --- |
| `implementation` | app/库实现所需依赖，默认不向下游暴露 API |
| `api` | 库模块对下游公开的 API 依赖 |
| `compileOnly` | 只参与编译，不进入运行时 |
| `runtimeOnly` | 只在运行时需要 |
| `testImplementation` | 本地 JVM 测试依赖 |
| `androidTestImplementation` | 设备/模拟器测试依赖 |
| `debugImplementation` | 仅 Debug 构建依赖 |
| `ksp` / `kapt` | 注解或符号处理器 |

依赖放错配置可能表现为：编译能过但运行找不到类、测试源码找不到库、Release 包缺少实现，或者不必要地增大暴露范围。

## 9. Build Type、Source Set 与 Variant

Android 项目通常至少有：

```text
debug
release
```

它们是 Build Type。源码可以按构建类型分目录：

```text
src/main/       所有构建共享
src/debug/      仅 Debug
src/release/    仅 Release
src/test/       本地 JVM 测试
src/androidTest/ 设备测试
```

如果再增加 product flavor，例如 `free` 和 `paid`，最终会组合成：

```text
freeDebug
freeRelease
paidDebug
paidRelease
```

每一种组合就是 Build Variant。

因此 `assembleDebug`、`assembleRelease`、`testDebugUnitTest` 中的名字不是随便起的，它们对应具体变体和任务。

## 10. 常用任务

```bash
# 查看 Gradle 与实际使用的 JVM
./gradlew --version

# 查看项目和任务
./gradlew projects
./gradlew tasks

# 构建 Debug APK
./gradlew :app:assembleDebug

# 运行本地单元测试
./gradlew :app:testDebugUnitTest

# 运行 Android Lint
./gradlew :app:lintDebug

# 构建 Release AAB（需要正确的发布配置）
./gradlew :app:bundleRelease
```

任务名前的 `:app:` 表示明确执行 app 模块的任务。多模块项目中，使用完整任务路径能减少歧义。

## 11. `clean` 为什么不是万能修复

```bash
./gradlew clean
```

`clean` 通常删除模块的 `build/` 输出，让下一次重新构建。它适合验证增量产物是否异常，但不能修复：

- JDK 与 AGP 不兼容；
- 仓库连接失败；
- 依赖版本冲突；
- Kotlin 代码错误；
- Manifest 配置错误；
- 缺少 SDK；
- 错误的插件配置。

每次失败都先 `clean` 会让构建更慢，也可能掩盖真正的增量构建问题。

## 12. 动手练习与验收

1. 在自己的项目中找到六类版本的配置位置。
2. 执行 `projects` 和 `tasks`，找到 app 模块。
3. 分别运行 `assembleDebug`、`testDebugUnitTest` 和 `lintDebug`。
4. 找到 Debug APK 输出目录。
5. 在不修改代码的情况下再次构建，观察哪些任务显示 `UP-TO-DATE` 或 `FROM-CACHE`。

验收标准：你能解释“插件注册任务、依赖参与编译、Variant 决定构建目标”这条完整链路。

## 13. 本章总结

Gradle 构建的核心不是背脚本，而是理解关系：Wrapper 锁定 Gradle，JDK 运行 Gradle，AGP 注册 Android 任务，插件扩展能力，依赖提供代码，Build Variant 决定最终构建哪一套应用。版本升级时沿兼容链逐项验证，构建失败时才不会靠碰运气。

官方参考：

- [Gradle build overview](https://developer.android.com/build/gradle-build-overview)
- [添加构建依赖](https://developer.android.com/build/dependencies)
- [迁移到 Version Catalog](https://developer.android.com/build/migrate-to-catalogs)
- [Android Gradle Plugin 兼容表](https://developer.android.com/build/releases/about-agp)
