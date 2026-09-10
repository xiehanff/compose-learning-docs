# 预备篇 E：Android 项目、资源、生命周期与日志基础

## 1. 本章目标

学完后，你应当能够：

- 区分应用进程、Activity、ViewModel 和 Composition 的生命周期；
- 看懂 AndroidManifest、资源目录与生成的 R 类；
- 理解 Context、Intent、权限和配置变化的基本边界；
- 区分编译错误、安装错误和运行时崩溃；
- 使用 Logcat 找到崩溃类型、文件和第一条业务调用栈。

## 2. Compose 不是独立操作系统

Compose 负责声明和更新 Android UI，但应用仍运行在 Android 平台上：

```text
Android 应用进程
└── Activity / 其他 Android 组件
    ├── ViewModelStore
    └── setContent 创建的 Composition
        └── Composable UI 树
```

因此即使只写 Compose，也必须理解：

- 应用组件从哪里声明；
- 系统何时创建/销毁 Activity；
- 权限和 Intent 由谁处理；
- 资源如何打包；
- 进程被杀后哪些内存状态会消失。

## 3. AndroidManifest.xml

Manifest 描述系统必须提前知道的信息：

```xml
<manifest ...>
    <uses-permission android:name="android.permission.INTERNET" />

    <application
        android:name=".DemoApplication"
        android:theme="@style/Theme.ComposeLearning">

        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

先理解：

- `uses-permission` 声明应用可能使用的系统能力；
- `application` 配置应用级属性；
- `activity` 把 Activity 注册给系统；
- MAIN + LAUNCHER 让 Activity 成为桌面入口；
- `exported` 决定其他应用是否能启动该组件。

不是所有权限只声明 Manifest 就够了。危险权限通常还需要在运行时向用户请求，并处理拒绝和永久拒绝。

## 4. 资源目录与 R 类

常见资源：

```text
res/
├── drawable/     图片和 Drawable XML
├── mipmap/       启动图标
├── values/       strings、colors、themes 等值资源
├── values-zh/    中文本地化资源示例
├── font/         字体
└── raw/          原始文件
```

资源经 AGP 处理后生成可引用标识：

```kotlin
stringResource(R.string.app_name)
painterResource(R.drawable.logo)
```

不要把所有文案硬编码进 Composable。字符串资源支持本地化、复数、格式化和工具检查。

资源名称必须符合 Android 命名规则，非法名称会在资源处理任务中失败，而不是 Kotlin 编译阶段失败。

## 5. Context 是什么

Context 提供对 Android 环境和服务的访问，例如：

- 读取资源；
- 启动 Activity；
- 获取系统服务；
- 打开文件或数据库；
- 显示 Toast。

Compose 中可以获取当前 Context：

```kotlin
val context = LocalContext.current
```

但不要为了方便把 Activity Context 长期保存在单例或 ViewModel 中。Activity 销毁后仍被引用会造成泄漏。确实需要应用级 Context 的底层依赖，应显式注入 Application Context，并避免让业务层依赖具体 UI 组件。

## 6. Activity 生命周期

常见回调顺序：

```text
onCreate
→ onStart
→ onResume
→ 用户离开
→ onPause
→ onStop
→ 可能 onDestroy
```

不要把 `onDestroy` 当成“应用一定退出”。配置变化、系统回收、用户返回和进程结束的行为不同，某些情况下系统也不会给应用执行最后清理代码的机会。

Compose 中仍应使用生命周期感知收集（指能感知 Activity 生命周期、在页面不可见时自动暂停收集，第 70 章详解）：

```kotlin
val uiState by viewModel.uiState.collectAsStateWithLifecycle()
```

详细的 Lifecycle 与 repeatOnLifecycle 在第 70 章讲解。

## 7. 四种容易混淆的存活范围

这里的「保留」指对象还在内存中，「丢」指需要重新创建或重新读取。`remember` 与 `rememberSaveable` 是 Compose 提供的两种保存界面小值的写法（分别在第 21、23 章展开）：`remember` 的值在界面重新生成之间保留，节点离开界面后被丢弃；`rememberSaveable` 在此基础上还能在 Activity 重建后恢复。这里的「重组」指状态变化后，Compose 重新执行「读取过该状态」的 Composable 函数，用新值重新生成这部分界面（第 22 章）。

| 对象/状态 | 通常何时保留 | 何时会丢 |
| --- | --- | --- |
| `remember` | 当前节点重组 | 节点离开 Composition |
| `rememberSaveable` | 重组、可恢复的 Activity/进程重建 | 状态无法保存、用户结束任务等场景 |
| ViewModel | 当前 ViewModelStoreOwner 的配置变化 | owner 真正结束或进程终止 |
| DataStore/Room | 应用重启 | 数据被清除、卸载或业务删除 |

“旋转屏幕”“切到后台”“从返回栈移除页面”“系统杀进程”“用户清除应用”不是同一个事件。选择状态容器前先问数据需要活多久。

## 8. 配置变化与进程重建

旋转、窗口尺寸和语言变化可能让 Activity 重建。ViewModel 可以跨配置变化保留内存状态，但进程被系统终止后，普通 ViewModel 内存也会消失。

需要恢复的轻量导航/输入状态可以使用 SavedStateHandle 或 rememberSaveable；真正重要的数据应写入 DataStore、Room 或远端数据源。

不要把大列表、Bitmap 或完整网络响应塞进 Saved State。只保存恢复页面所需的最小键值，例如 id、查询条件和当前输入。

## 9. Intent 与导航的边界

Compose Navigation 主要管理应用内部目的地；Intent 是 Android 组件和应用之间的消息机制。

常见 Intent 场景：

- 打开浏览器；
- 调用系统分享；
- 从通知进入指定页面；
- Deep Link；
- 启动另一个 Activity。

内部导航参数尽量只传 id 等最小标识，详情数据由目标页面根据 id 从数据层恢复。不要把大型对象通过路由字符串或 Intent 传来传去。

## 10. 权限不是一行代码

权限流程至少包含：

```text
判断当前是否已有权限
→ 解释为什么需要
→ 发起请求
→ 处理允许/拒绝
→ 处理永久拒绝或功能降级
```

权限结果属于 UI 与系统交互状态，应由 Activity Result API 等生命周期安全机制处理。不要在 Composable 函数体每次重组时直接发起权限请求。

## 11. 用 Logcat 定位运行时崩溃

构建成功但应用闪退时，打开 Logcat 并筛选当前应用进程。优先找：

```text
FATAL EXCEPTION
异常类型，例如 NullPointerException / IllegalStateException
异常消息
第一条指向自己包名的调用栈
文件名与行号
```

示意：

```text
FATAL EXCEPTION: main
java.lang.IllegalStateException: User must not be null
    at com.example.composelearning.UserScreen(UserScreen.kt:42)
```

这说明：

- 崩溃发生在主线程；
- 异常类型是 IllegalStateException；
- 第一条业务栈在 UserScreen.kt 第 42 行。

先检查第 42 行和它收到的数据，再考虑框架或设备问题。

## 12. 四层问题不要混在一起

| 层级 | 典型证据 | 常用入口 |
| --- | --- | --- |
| Sync/构建配置 | Gradle Build Output | 预备篇 C |
| Kotlin/资源编译 | 失败任务、文件行号 | Build Output |
| 安装/设备 | adb、安装错误码 | Run/Device Manager |
| 运行时业务 | FATAL EXCEPTION、ANR、状态错误 | Logcat、Debugger、Profiler |

某一层通过不能证明其他层正确。例如 `assembleDebug` 成功只证明 APK 构建出来，不证明网络接口、权限或页面业务一定成功。

## 13. 常见错误

1. 把 Compose 当成脱离 Android 的框架，不学 Manifest、资源和生命周期。
2. 在 ViewModel 或单例长期持有 Activity/Composable 引用。
3. 把旋转、后台、返回和进程死亡都叫“页面销毁”。
4. 在 Composable 函数体直接发权限请求、启动 Activity 或写数据库。
5. 构建失败去看 Logcat，运行时崩溃却只盯 Gradle Output。
6. 只复制异常最后一行，不查看第一条业务调用栈。

## 14. 动手练习与验收

1. 在 Manifest 中找到启动 Activity 和主题。
2. 把一个硬编码标题迁移到 strings.xml，并用 stringResource 读取。
3. 旋转设备，分别观察 remember、rememberSaveable 和 ViewModel 状态。
4. 人为制造一个空值崩溃，从 Logcat 找到文件和行号，再安全修复。
5. 执行 `assembleDebug` 后关闭设备，说明为什么构建成功但无法 Run。
6. 画出应用进程、Activity、ViewModel、Composition 和 Room 的存活关系。

验收标准：遇到问题时，你能先判断属于构建、安装还是运行时，再进入正确工具，而不是把所有红字都叫 Gradle 报错。

## 15. 本章总结

Compose 改变了 UI 的编写方式，没有取消 Android 平台。Manifest 决定组件和权限，资源系统负责可维护内容，Activity 提供宿主，ViewModel 与 Saved State 处理不同存活范围，Logcat 负责运行时证据。先分清平台层级，后续 Compose 状态、导航和架构才不会悬空。

官方参考：

- [Android app fundamentals](https://developer.android.com/guide/components/fundamentals)
- [Activity lifecycle](https://developer.android.com/guide/components/activities/activity-lifecycle)
- [App resources overview](https://developer.android.com/guide/topics/resources/providing-resources)
- [Permissions on Android](https://developer.android.com/guide/topics/permissions/overview)
- [View logs with Logcat](https://developer.android.com/studio/debug/logcat)
