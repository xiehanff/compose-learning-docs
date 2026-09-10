// 代码示例编译校验模块（独立 Gradle 构建，不影响仓库其它内容）。
// 运行方式：python3 tools/extract_snippets.py && cd snippets && ./gradlew compileDebugKotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "compose-learning-snippets"
