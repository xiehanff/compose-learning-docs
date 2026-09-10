# 第 70 章：生命周期感知：repeatOnLifecycle 与 LifecycleEventEffect

## 1. 本章目标

学完后，你应当能够：

- 理解 Lifecycle 状态机：CREATED、STARTED、RESUMED 的升降关系
- 用 `repeatOnLifecycle` 让协程块随生命周期反复进入与退出
- 知道 `collectAsStateWithLifecycle` 底层就是 repeatOnLifecycle
- 用 `LifecycleEventEffect` 处理事件型副作用（如进入前台上报）

## 2. 项目中的位置

进阶篇不再局限于 compose_demo 项目，本章使用通用 Compose API。

- `androidx.lifecycle`：Lifecycle、repeatOnLifecycle、lifecycleScope
- `androidx.lifecycle.compose`：collectAsStateWithLifecycle、LifecycleEventEffect、LocalLifecycleOwner
- `androidx.activity`：ComponentActivity

## 3. 项目源码片段

场景一：Activity / Fragment 中收集 ViewModel 的 StateFlow，只有界面可见（STARTED 及以上）时才收集：

```kotlin
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            // 进入 STARTED 时启动块内协程，低于 STARTED 时取消；
            // 前后台切换会反复进入/退出，直到生命周期永久销毁
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    updateUi(state)
                }
            }
        }
    }
}
```

场景二：组合内直接用封装好的 API，效果与场景一相同：

```kotlin
@Composable
fun MainScreen(viewModel: MainViewModel = viewModel()) {
    // 内部实现就是 repeatOnLifecycle(STARTED) + collect
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // 事件型副作用：每次进入 RESUMED 时上报一次
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.reportScreenView()
    }
}
```

场景三：非组合环境（自定义 View、仓库类）拿到 LifecycleOwner 后手动绑定：

```kotlin
class Tracker(
    private val lifecycleOwner: LifecycleOwner,
    private val viewModel: MainViewModel
) {
    fun start() {
        lifecycleOwner.lifecycleScope.launch {
            lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.trackScroll()
            }
        }
    }
}
```

## 4. 逐步解释

**Lifecycle 状态机。** Lifecycle 的状态不是事件，而是「当前所处阶段」：INITIALIZED → CREATED → STARTED → RESUMED，向下是 DESTROYED。事件（ON_CREATE、ON_START、ON_RESUME、ON_PAUSE、ON_STOP、ON_DESTROY）驱动状态升降。repeatOnLifecycle 说的是「目标状态」，比如 STARTED 表示「至少到达 STARTED 才执行，低于 STARTED 就取消」。记住：状态是阶段，事件是变化，repeatOnLifecycle 绑定的是状态。

**repeatOnLifecycle 的语义。** `lifecycleScope.launch { repeatOnLifecycle(state) { ... } }`：当生命周期升到目标状态时，启动块内的协程；降到目标状态之下时，取消块内协程（连同它启动的子协程）；再次升上来时重新启动。这个「反复进入/退出」正是前后台切换的写照：退后台取消收集，回前台重新收集，天然避免后台空跑与资源浪费。记住：块内协程是「随状态生、随状态死」，外层 launch 一次，内层反复执行。

**collectAsStateWithLifecycle 的底层。** 它就是 `repeatOnLifecycle(Lifecycle.State.STARTED) { flow.collect { ... } }` 加上把最新值转成 State 的封装，并自动使用 LocalLifecycleOwner 提供的 Lifecycle。因此组合里收集 StateFlow 直接用这一个函数，不需要手写 repeatOnLifecycle。记住：UI 收集状态，一个 collectAsStateWithLifecycle 就够了。

**LifecycleEventEffect 与事件型副作用。** 有些逻辑不是「持续收集」而是「每次发生某事件时执行一次」：统计上报、埋点、启动动画、恢复轮询。LifecycleEventEffect(ON_RESUME) 在每次到达该事件时执行块，对应的还有 LifecycleStartEffect 等。它与 repeatOnLifecycle 的区别：repeatOnLifecycle 管「持续块」的启停，LifecycleEventEffect 管「事件触发的一次性动作」。记住：持续收集用 repeatOnLifecycle，单次动作用 LifecycleEventEffect。

**组合生命周期与 Activity 生命周期的关系。** 组合里 LocalLifecycleOwner 拿到的是宿主（通常是 Activity 或 Fragment）的 Lifecycle。组合节点可能因重组而进入/退出组合，而 Activity 生命周期独立升降，两者通过 LifecycleOwner 关联但不重合。记住：组合的生命周期由组合框架管，Lifecycle 的生命周期由宿主管，repeatOnLifecycle 把协程绑定到后者。

**为什么不能直接 collect。** 在 lifecycleScope 里直接 `launch { flow.collect { ... } }`，协程只随 lifecycleScope（Activity 销毁）取消，退后台不取消：后台继续收集、继续触发 UI 更新或业务计算，浪费资源还可能崩。包一层 repeatOnLifecycle 后，可见才收集。记住：收集永远跟着可见性走。

## 5. 实际执行过程

1. Activity onCreate，lifecycleScope.launch 启动外层协程，repeatOnLifecycle 开始等待 STARTED
2. 界面进入前台：生命周期升到 STARTED，块内协程启动，开始 collect uiState
3. 每次状态变化，collect 回调更新 UI
4. 用户按 Home 退后台：状态降到 STARTED 之下，块内协程被取消，收集停止
5. 用户回前台：状态重新升到 STARTED，块内协程重新启动，收集恢复，UI 拿到最新值
6. 用户退出 Activity：生命周期到 DESTROYED，外层协程随 lifecycleScope 取消，整个流程结束
7. 组合场景：LifecycleEventEffect(ON_RESUME) 在每次进入 RESUMED 时触发一次上报，退后台再回来会再次触发

## 6. Flutter / Dart 3 对照

先讲相似点：Flutter 也有生命周期回调——WidgetsBindingObserver 的 didChangeAppLifecycleState，收到 resumed 时恢复订阅、收到 paused 时停止，思路一致：可见才干活。

```dart
class LifecycleAwareState extends State<AppLifecycleDemo>
    with WidgetsBindingObserver {
  StreamSubscription? _sub;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _startCollecting();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    // 类似 repeatOnLifecycle 的进入/退出：resumed 恢复，其他暂停
    if (state == AppLifecycleState.resumed) {
      _startCollecting();
    } else {
      _stopCollecting();
    }
  }

  void _startCollecting() => _sub = counterStream.listen(updateUi);
  void _stopCollecting() => _sub?.cancel();
}
```

本质区别：Compose 把「生命周期 × 协程」自动绑定——repeatOnLifecycle 让你声明式地说「在这个状态区间运行这段协程」，块内协程的启动/取消由框架完成，无需手动维护订阅对象；Flutter 需要手动 addObserver、手动订阅、手动在回调里恢复/取消，稍不留神就漏取消或漏恢复。另外 Flutter 的 didChangeAppLifecycleState 只有 App 级生命周期，Compose 的 Lifecycle 同时覆盖 Activity/Fragment 级（如 Dialog 内的 Fragment 独立生命周期），粒度更细。

## 7. 常见错误

1. 在 lifecycleScope 里直接 collect 而不包 repeatOnLifecycle：退后台继续收集，后台空跑、浪费资源甚至崩溃。
2. 在 repeatOnLifecycle 块外启动无限协程（如 while(true) 轮询）：块外的协程只随 lifecycleScope 取消，退后台照样跑。
3. 把 UI 更新逻辑放进 ON_CREATE 事件：此时界面可能还没到可见状态，应绑定 STARTED/RESUMED。
4. 不理解「协程取消后会重新进入」：以为 repeatOnLifecycle 只执行一次，其实每次回到目标状态都会重新启动块内协程；依赖这种语义时要注意重复注册监听器。
5. 在 LifecycleEventEffect 里做耗时操作或启动不随事件取消的长期任务：事件块执行完就结束，长期任务要自己管理。

## 8. 本章检查题

1. repeatOnLifecycle(STARTED) 在什么时机启动块内协程、什么时机取消？
2. collectAsStateWithLifecycle 的底层实现是什么？
3. LifecycleEventEffect(ON_RESUME) 适合什么场景？与 repeatOnLifecycle 的区别是什么？

答案：

1. 生命周期升到 STARTED 时启动，降到 STARTED 之下（如退后台）时取消；再次升上来重新启动，直到生命周期永久销毁。
2. 内部是 repeatOnLifecycle(Lifecycle.State.STARTED) 包裹 flow.collect，把最新值转成 Compose State，并使用 LocalLifecycleOwner 的 Lifecycle。
3. 适合「每次事件发生执行一次」的动作，如进入前台埋点上报、恢复轮询；repeatOnLifecycle 管理持续协程块的启停，LifecycleEventEffect 管理事件触发的一次性动作。

## 9. 本章总结

生命周期感知的本质是把协程的生死绑定到可见性：repeatOnLifecycle 让持续收集随 STARTED 反复进入退出，collectAsStateWithLifecycle 是它的组合封装，LifecycleEventEffect 处理事件型单次动作。记住：后台不干活、回前台接着干，是生命周期感知的唯一目标。
