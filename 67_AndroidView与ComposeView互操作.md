# 第 67 章：AndroidView 与 ComposeView：与 View 体系互操作

## 1. 本章目标

学完后，你应当能够：

- 用 AndroidView 在 Compose 里嵌入传统 View（地图、WebView、自定义 View）
- 用 ComposeView 在 View/XML 布局里承载 Compose UI
- 理解 View 生命周期与组合生命周期不同步，会用 ViewCompositionStrategy
- 判断什么场景该做互操作、什么场景不该

## 2. 项目中的位置

进阶篇不再局限于 compose_demo 项目，本章使用通用 Compose API。

- `androidx.compose.ui.viewinterop`：AndroidView
- `androidx.compose.ui.platform`：ComposeView、ViewCompositionStrategy
- `android.view.*`：View、ViewGroup、Activity 等传统 View 体系 API

## 3. 项目源码片段

场景一：用 AndroidView 包装自定义地图 View：

```kotlin
@Composable
fun MapViewWrapper(markerTitle: String) {
    AndroidView(
        // factory：首次组合时创建 View，整个生命周期只执行一次
        factory = { context ->
            MyMapView(context).apply {
                setOnMapClickListener { _, _ -> /* 点击地图 */ }
            }
        },
        // update：每次重组都会执行，把最新状态同步给 View
        update = { view ->
            view.setMarkerTitle(markerTitle)
        },
        // onRelease：离开组合时清理资源，防止泄漏
        onRelease = { view ->
            view.release()
        },
        modifier = Modifier.fillMaxSize()
    )
}
```

场景二：在 XML 布局里放 ComposeView，从 View 体系进入 Compose：

```xml
<!-- res/layout/activity_main.xml -->
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical">

    <TextView
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="传统 View 区域" />

    <androidx.compose.ui.platform.ComposeView
        android:id="@+id/compose_view"
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1" />
</LinearLayout>
```

```kotlin
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<ComposeView>(R.id.compose_view).apply {
            // 组合生命周期跟随 View 树：脱离窗口即销毁组合，避免泄漏
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                MaterialTheme {
                    GreetingScreen()
                }
            }
        }
    }
}
```

## 4. 逐步解释

AndroidView 的三个核心参数构成一个完整生命周期：`factory` 负责创建（组合中只调用一次），`update` 负责同步（每次重组时调用，把最新状态推给 View），`onRelease` 负责清理（组合销毁时调用）。三个都要认真写，尤其 onRelease——组合销毁不等于 View 立刻销毁，View 可能仍挂在 ViewGroup 上，监听器、Timer、Handler 不清理就会泄漏。

生命周期不同步是互操作最大的坑：View 的 onDetachedFromWindow 与组合的 dispose 没有一一对应关系。所以资源释放不能依赖 View 自己的销毁回调，必须显式放在 onRelease 里。

触摸与焦点：AndroidView 内的触摸事件默认优先交给 View 自身处理，与 Compose 手势的竞争遵循第 66 章的手势仲裁规则；焦点、输入法切换等行为也需要按 View 体系的规则来。

ComposeView 是反方向的路：把 Compose 装进 View 世界。`setContent` 建立组合，而 `ViewCompositionStrategy` 决定组合**何时销毁**：DisposeOnDetachedFromWindow 表示 ComposeView 脱离窗口即销毁组合（适合普通页面）；DisposeOnViewTreeLifecycleDestroyed 表示跟随 LifecycleOwner 销毁（适合与 Fragment/Activity 生命周期对齐的场景）。不配置策略，组合可能在窗口销毁后仍存活，造成状态与内存泄漏。

何时需要互操作：只有 Compose 没有替代品的场景——成熟 SDK（地图）、WebView、视频播放器、需要复用的存量自定义 View。能用 Compose 重写的就不要桥接，桥接层是维护成本。

记住：factory 创建、update 同步、onRelease 清理，三者缺一不可；互操作是两个体系之间的桥，桥的两端都要自己维护。

## 5. 实际执行过程

1. 首次组合到 AndroidView，执行 factory 创建 View 并加入 ComposeView 内部的 ViewGroup
2. 状态 markerTitle 变化触发重组，update 被调用，把新值同步给 View
3. 用户点击地图，View 自己消费触摸事件（与 Compose 手势按仲裁规则竞争）
4. 条件分支移除 AndroidView（如切页），离开组合，onRelease 清理资源
5. XML 场景：Activity onCreate，setContentView 加载布局，找到 ComposeView，setContent 建立组合
6. 窗口销毁/Activity finish，ComposeView 脱离窗口，按策略销毁组合并释放状态

## 6. Flutter / Dart 3 对照

先讲相似点：Flutter 的 AndroidView widget 与 Compose 的 AndroidView 概念几乎一致——都是「在声明式 UI 里嵌一块原生视图」，都通过工厂/类型创建原生视图，创建后原生视图自己负责绘制与触摸：

```dart
// Flutter：AndroidView widget 嵌入原生 Android View
class MapWidget extends StatelessWidget {
  const MapWidget({super.key});

  @override
  Widget build(BuildContext context) {
    return AndroidView(
      viewType: 'plugins.flutter.io/map', // 与原生侧注册的类型对应
      creationParams: const {'marker': '北京'},
      creationParamsCodec: const StandardMessageCodec(),
      onPlatformViewCreated: (id) => debugPrint('原生视图已创建: $id'),
    );
  }
}
```

iOS 对应 UiKitView。两个框架连名字都一样，这是历史上少有的「同名同构」API。

本质区别：

- 数据同步：Compose 的 update 回调在重组时自动把状态推给 View；Flutter 的 PlatformView 默认只传 creationParams，后续数据要靠 MethodChannel 消息通道手动同步，没有自动 update。
- 生命周期：Compose 用 ViewCompositionStrategy 显式配置组合销毁时机；Flutter 的 PlatformView 生命周期由引擎管理，开发者主要处理 onPlatformViewCreated 回调。
- 二者共同点：声明式框架只在创建与销毁时介入，中间过程原生视图自己负责。

记住：两个 AndroidView 思想一致——声明式框架只负责创建和销毁原生视图，中间的绘制与交互原生视图自己说了算。

## 7. 常见错误

- 只在 factory 里设置数据，忘记写 update → 状态变化后 View 显示陈旧
- 忘记 onRelease → 监听器/Handler 持有 Activity 引用，组合销毁后仍泄漏
- ComposeView 重复调用 setContent → 重复建立组合，浪费资源甚至崩溃
- 在 View 层与 Compose 层之间直接共享可变状态（把 MutableState 直接塞给 View 用）→ 两边状态不一致、无法观察
- 忘记给 AndroidView 设置尺寸约束，或父容器拦截触摸导致 View 收不到点击

## 8. 本章检查题

1. AndroidView 的三个核心参数分别负责什么？
2. ViewCompositionStrategy 是什么？为什么需要它？
3. 什么场景应该做互操作？
4. update 与 factory 的执行时机有什么区别？

答案：

1. factory 创建 View（仅一次）、update 在重组时同步数据、onRelease 在离开组合时清理资源。
2. 决定 ComposeView 的组合何时销毁的策略；因为 View 树与组合生命周期不同步，不配置会在窗口销毁后仍持有组合状态，造成泄漏。
3. 使用只有传统 View API 的成熟 SDK（地图）、WebView、视频播放器，以及需要复用存量 View 代码的场景。
4. factory 只在 View 首次创建时执行一次；update 在每次重组时执行，用于把最新状态同步给 View。

## 9. 本章总结

AndroidView 与 ComposeView 是 Compose 与 View 体系互操作的双向桥。记住「factory 创建、update 同步、onRelease 清理」的铁三角，同时理解组合与 View 生命周期不同步，用 ViewCompositionStrategy 控制 ComposeView 的销毁时机，就能安全地完成双向嵌入。
