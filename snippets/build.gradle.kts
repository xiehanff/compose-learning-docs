import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// 只用于编译校验书里的代码示例：不产出 App，不需要应用图标与资源。
// 检查命令：./gradlew compileDebugKotlin
//
// 严格校验（把编译器警告当错误，用来发现书里用了已弃用 API）：
//   ./gradlew compileDebugKotlin -PsnippetsStrictWarnings --rerun-tasks
// 它与普通编译互相独立：普通编译不因为警告中断，严格校验则让警告浮出水面。
plugins {
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.jetbrainsKotlinAndroid)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.jetbrainsKotlinSerialization) // 第 71A 章的 @Serializable 路由 key
}

android {
    namespace = "snippets"
    // 第 71A 章的 Navigation 3 依赖要求 compileSdk 36 或更高。
    compileSdk = 36

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
    }

    lint {
        // 本书片段是节选，不做 lint 阻断。
        abortOnError = false
        checkReleaseBuilds = false
    }
}

kotlin {
    compilerOptions {
        // lifecycle 2.8+ 的 -android 构件以 JVM target 11 编译，其 inline 函数
        // （如 collectAsStateWithLifecycle）要求消费方 jvmTarget >= 11 才能内联。
        jvmTarget.set(JvmTarget.JVM_11)
        // 严格模式：由 -PsnippetsStrictWarnings 打开，用于发现弃用 API 等警告。
        allWarningsAsErrors.set(providers.gradleProperty("snippetsStrictWarnings").isPresent)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.runtime)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.foundation)
    implementation(libs.androidx.animation)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.icons.core)
    implementation(libs.androidx.icons.extended) // 第 54/64 章用到 extended 里的图标（如 Pause、FavoriteBorder）
    // 第 71A 章 Navigation 3 示例（不是 compose_demo 的依赖）。
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.kotlinx.serialization.core)
    // 第 79 章 WindowSizeClass 响应式示例；断点常量（WIDTH_DP_*_LOWER_BOUND）来自其传递的 androidx.window:window-core。
    implementation(libs.androidx.material3.adaptive)
    // 实践篇（81—87 章）：ymq-mall-json-server 教学链路。
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.coil.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.serialization.json)
}
