# 预备篇 E：Android 项目、资源、生命周期与日志基础

## 1. 本章目标

学完后，你应当能够：

- 区分应用进程、Activity 与持久化存储各自的存活范围；
- 看懂 AndroidManifest、资源目录与生成的 R 类；
- 理解 Context、Intent、权限和配置变化的基本边界；
- 区分编译错误、安装错误和运行时崩溃；
- 使用 Logcat 找到崩溃类型、文件和第一条业务调用栈。

## 2. 应用跑在 Android 平台上

后续章节会看到，Compose 只负责声明和更新界面，应用本身仍运行在 Android 平台上：

```text
Android 应用进程
└── Activity / 其他 Android 组件
    ├── 组件自身的字段与内存状态
    └── 界面：由后续章节的 Compose 代码声明
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

资源经 AGP 处理后生成一个 R 类，里面的标识按资源类型分组：

```kotlin
R.string.app_name   // 对应 values/strings.xml 里的 app_name
R.drawable.logo     // 对应 drawable/logo.png 或 logo.xml
```

这个标识只是一个整数 ID（`Int`），不是资源内容本身。界面代码要用 Compose 提供的读取函数把它换成真正的文字或图片：

```kotlin
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource

Text(text = stringResource(R.string.app_name))

Image(
    painter = painterResource(R.drawable.logo),
    contentDescription = null, // 纯装饰图片传 null；承载信息时写有意义的描述
)
```

上面的 `R.string.app_name` 是资源 ID，`stringResource(R.string.app_name)` 才拿到字符串；`R.drawable.logo` 是资源 ID，`painterResource(R.drawable.logo)` 才拿到可绘制的图片。把整数 ID 直接赋给 `Text` / `Image` 是新手常见的错误（第 12 章讲图片的 `Painter` 与 `contentDescription`）。

不要把所有文案硬编码进界面代码。字符串资源支持本地化、复数、格式化和工具检查。

资源名称必须符合 Android 命名规则，非法名称会在资源处理任务中失败，而不是 Kotlin 编译阶段失败。

## 5. Context 是什么

Context 提供对 Android 环境和服务的访问，例如：

- 读取资源；
- 启动 Activity；
- 获取系统服务；
- 打开文件或数据库；
- 显示 Toast。

Context 由平台提供，常见来源有两种：

- `Activity`：Activity 本身就是 Context，适合与界面相关的操作；
- `Application`：与应用进程同生命周期，适合长期存在的依赖。

但不要为了方便把 Activity Context 长期保存在单例或其他长生命周期对象中。Activity 销毁后仍被引用会造成泄漏。确实需要应用级 Context 的底层依赖，应显式使用 Application Context，并避免让业务层依赖具体界面组件。

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

Activity 不可见时，正在跑的耗时任务、监听器和回调应该暂停或取消：继续运行只会浪费电量和流量，任务结束时若 Activity 已经销毁，还可能把结果写到一个不再使用的界面上。如何让任务跟随生命周期自动暂停，在第 70 章展开。

## 7. 三个容易混淆的存活范围

这里的「保留」指数据还在、还能读到，「丢」指需要重新创建或重新读取。同一个界面上的数据，可能活在不同层级上：

| 层级 | 通常何时保留 | 何时会丢 |
| --- | --- | --- |
| 应用进程（内存里的普通对象） | 进程还活着 | 系统回收进程、用户结束任务、应用崩溃 |
| Activity 需要恢复的少量状态 | 配置变化重建后，由系统在销毁前保存的状态恢复 | 没有保存、系统无法恢复、用户结束任务 |
| 持久化存储（文件、SharedPreferences、数据库） | 应用重启后仍然存在 | 用户清除应用数据、卸载应用、业务主动删除 |

“旋转屏幕”“切到后台”“从返回栈移除页面”“系统杀进程”“用户清除应用”不是同一个事件。选择把数据放在哪一层之前，先问它需要活多久。

后面的状态相关章节会介绍几种让状态活得更久、写起来更省心的手段，但它们本质上都只能落在上面这三个层级里。

## 8. 配置变化与进程重建

旋转、窗口尺寸和语言变化可能让 Activity 重建。重建意味着旧的 Activity 实例被销毁、新的实例被创建，写在 Activity 字段里的普通对象都会重新初始化；只有系统在销毁前替你保存过的少量状态，才能在重建时取回（`onSaveInstanceState` 写入 `Bundle`，重建后在 `savedInstanceState` 中读取）。但如果进程被系统终止，内存里的一切都不在了。

需要跨重建恢复的轻量导航/输入状态，就用上面这套系统保存机制；真正重要的数据，应写入文件、SharedPreferences、数据库或远端数据源。

不要把大列表、Bitmap 或完整网络响应塞进系统保存的状态：它的容量有限，而且会经过系统进程。只保存恢复页面所需的最小键值，例如 id、查询条件和当前输入。

## 9. Intent 与导航的边界

应用内部的页面切换由后续章节的导航方案管理；Intent 是 Android 组件和应用之间的消息机制。

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

权限结果属于界面与系统交互的状态，应由 Activity Result API 等生命周期安全机制处理。不要在会被重复执行的界面代码里直接发起权限请求。

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
2. 在单例或长生命周期对象中长期持有 Activity 引用。
3. 把旋转、后台、返回和进程死亡都叫“页面销毁”。
4. 在会被重复执行的界面代码里直接发权限请求、启动 Activity 或写数据库。
5. 构建失败去看 Logcat，运行时崩溃却只盯 Gradle Output。
6. 只复制异常最后一行，不查看第一条业务调用栈。

## 14. 动手练习与验收

1. 在 Manifest 中找到启动 Activity 和主题。
2. 把一个硬编码标题迁移到 strings.xml，并在代码中通过 `R.string` 引用它。
3. 旋转设备，观察 Activity 里的普通字段和系统保存的状态分别表现如何。
4. 人为制造一个空值崩溃，从 Logcat 找到文件和行号，再安全修复。
5. 执行 `assembleDebug` 后关闭设备，说明为什么构建成功但无法 Run。
6. 画出应用进程、Activity 和持久化存储的存活关系。

验收标准：遇到问题时，你能先判断属于构建、安装还是运行时，再进入正确工具，而不是把所有红字都叫 Gradle 报错。

## 15. 本章总结

Compose 改变了 UI 的编写方式，没有取消 Android 平台。Manifest 决定组件和权限，资源系统负责可维护内容，Activity 提供宿主，内存对象、Activity 保存状态与持久化存储处理不同存活范围，Logcat 负责运行时证据。先分清平台层级，后续 Compose 状态、导航和架构才不会悬空。

官方参考：

- [Android app fundamentals](https://developer.android.com/guide/components/fundamentals)
- [Activity lifecycle](https://developer.android.com/guide/components/activities/activity-lifecycle)
- [App resources overview](https://developer.android.com/guide/topics/resources/providing-resources)
- [Permissions on Android](https://developer.android.com/guide/topics/permissions/overview)
- [View logs with Logcat](https://developer.android.com/studio/debug/logcat)
