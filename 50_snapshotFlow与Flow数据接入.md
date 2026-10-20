# 第 50 章：snapshotFlow 与 Flow 数据接入

## 1. 本章目标

打通两个方向的数据流：把 Compose 快照状态转成冷流，把外部 Flow 接回 Compose UI。

首次阅读路线：先学 `snapshotFlow` 如何把被读取的状态变化发出来，再看场景一的防抖。Flow 可先理解为「按时间陆续发出值的数据流」；`debounce(300)` 是等输入停下 300ms 再放行，`distinctUntilChanged()` 是过滤连续相同值。场景二需要第 68 章的 ViewModel 与第 70 章的生命周期知识，首次阅读可先跳过；`flowOn` 与 Dispatcher 的细节见第 73 章，回读本章第 4 节时再看。

学完后，你应当能够：

- 理解 snapshotFlow 的发射时机、依赖收集方式与内建去重
- 写出搜索框防抖的标准组合（snapshotFlow + debounce + distinctUntilChanged）
- 区分 collectAsState 与 collectAsStateWithLifecycle 的使用场景
- 知道耗时操作应该放在流水线的哪一段，以及 flowOn 只影响上游

## 2. 项目中的位置

进阶篇不再局限于 compose_demo 项目，本章使用通用 Compose API。

涉及的内容包括：

- androidx.compose.runtime（snapshotFlow、collectAsState、remember）
- androidx.lifecycle:lifecycle-runtime-compose（collectAsStateWithLifecycle）
- kotlinx.coroutines（Flow、debounce、distinctUntilChanged、flowOn）

## 3. 项目源码片段

场景一：搜索框输入防抖。keyword 每次按键都在变，但我们只希望在用户停止输入 300ms 后才真正发起搜索。`SearchViewModel` 在编译校验里用只含 `search` 的最小接口占位（真实项目中它是暴露 `search` 与结果状态的 ViewModel，见第 68 章）：

<!-- compile decls
prelude: interface SearchViewModel { suspend fun search(query: String) }
-->
```kotlin
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
@OptIn(kotlinx.coroutines.FlowPreview::class)  // debounce 是 FlowPreview API，使用处需显式 opt-in
fun SearchScreen(viewModel: SearchViewModel) {
    var keyword by remember { mutableStateOf("") }

    // 快照状态 -> 冷流：结果变化才发射，再配防抖与去重
    LaunchedEffect(Unit) {
        snapshotFlow { keyword }
            .debounce(300)
            .distinctUntilChanged()
            .collect { query -> viewModel.search(query) }
    }

    OutlinedTextField(
        value = keyword,
        onValueChange = { keyword = it },
        placeholder = { Text("输入关键词") },
    )
}
```

场景二（学完第 68、70 章后回看）：把 ViewModel 的 Flow 接回 UI，并感知生命周期。`Loading/Success/Error` 为节选占位（密封接口表示 UI 状态的分支；完整的 loading/error/empty/content 四态建模见第 68 章场景四）。

```kotlin
@Composable
fun ResultList(viewModel: SearchViewModel) {
    // 生命周期感知：STARTED 及以上才收集，后台自动停止（第 70 章详解）
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    when (val state = uiState) {
        is Loading -> CircularProgressIndicator()
        is Success -> LazyColumn { items(state.items) { Text(it) } }
        is Error -> Text(state.message)
    }
}
```

## 4. 逐步解释

`snapshotFlow { ... }` 是连接两个世界的桥，先讲清它的三条规则：

**第一，冷流与收集时机。** 它返回冷 Flow：每次开始收集时执行一次 block（发射当前值），并记录 block 里读取的所有快照状态（State）作为依赖；之后依赖变化时重新执行 block。它本身不依赖组合——在普通协程里照样可以 collect（比如在测试或非 UI 代码中）；放进 `LaunchedEffect` 是为了把收集绑定到组合生命周期，而不是因为离开了组合就不能用。这句话反过来也成立：冷流的生命周期跟着收集协程走——收集被取消（如 LaunchedEffect 离开组合）时，block 不再执行、对快照状态的读取也不再被追踪，既不会继续发射，也不会有残留的监听。

**第二，内建去重。** 官方文档明确：只有当 block 的新结果与上一次发射的值**不相等**（equals）时才发射，行为类似 `Flow.distinctUntilChanged()`。所以「每次写必发射」是误解。这个去重还有另一面：**同一快照内的多次写入、以及未及应用的写入可能合并成一次重算与发射**，因此不要把 snapshotFlow 当作可靠的事件日志——它是「状态当前值」的流，不承诺保留每一次中间变化的历史。

**第三，只追踪 State 读取。** block 里读普通变量不建立依赖，值变了也不会重新执行 block。

三条规则合起来就是 snapshotFlow 的**职责边界：它是「从 UI 状态读出」的桥，不是通用 Flow 的替代品**。它管的只有一件事：把 UI 里已经存在的快照状态交给协程世界处理（防抖、打点、驱动动画）。反方向的数据——网络、数据库等业务数据源——应该作为普通 Flow/StateFlow 从 ViewModel 流入，用 collectAsStateWithLifecycle 收集（第 68 章）；把业务数据写进快照状态再用 snapshotFlow 读出来，等于绕了一圈还丢掉了 ViewModel 层的状态管理。判断方法很简单：这个值是「在 UI 里被用户改出来的」就用 snapshotFlow 读出去，是「从外面流进来的」就让 Flow 直接进来。

防抖搜索是它的经典用法：每次按键都可能发射新值，`debounce(300)` 只在停止输入 300ms 后放行最后一个值。注意 `debounce` 目前是 kotlinx.coroutines 的 FlowPreview（预览）API，在本仓库基线中调用它需要在函数上 `@OptIn(kotlinx.coroutines.FlowPreview::class)` 显式启用。debounce 之后的 `distinctUntilChanged()` 仍有价值：它过滤的是「防抖窗口之间」的重复——比如输入「com」、删成「co」、又打回「com」，两个 300ms 窗口各自放行相同的值，snapshotFlow 的去重管不到这种时间上分离的重复。最后 `collect` 触发搜索。注意防抖**不会取消已经开始的搜索**：它只约束「什么时候发起」，不管「发起之后」；如果需求是「新关键词出现时取消进行中的旧搜索」，应在搜索侧使用可取消的结构（如 `collectLatest`/`mapLatest`/`flatMapLatest`，或搜索逻辑自身支持取消），且这依赖 `viewModel.search` 的实现方式（挂起函数、返回 Job 或 Flow），不能凭空保证。

`collectAsState` 把任意 Flow 转成 State：Flow 发射新值时更新 State，读取它的 UI 重组。`collectAsStateWithLifecycle` 来自 lifecycle-runtime-compose，是生命周期感知的：只在 Lifecycle 处于 STARTED 及以上才收集，退到后台自动停止、回前台恢复。**两者核心区别就是生命周期感知**：屏幕不在前台时，普通版本仍在收集，可能白白执行昂贵的 Flow 操作。

线程方面要讲准确：`collect { }` 里的代码运行在收集者所在的上下文（LaunchedEffect 中就是主线程），**`flowOn(Dispatchers.IO)` 只影响它上游操作的执行线程，切不走 collect 块本身**。所以正确姿势是：让上游生产数据的方式本身 main-safe（挂起函数内部自行切换调度器），或者在流水线中段用 `flowOn` 把上游整体放到 IO，collect 块只留轻量逻辑；个别确需在收集点执行的重活再就地 `withContext(Dispatchers.IO)`。记住：flowOn 管上游，collect 块跟着收集者走。

## 5. 实际执行过程

1. 首次组合：LaunchedEffect 启动，snapshotFlow 立即执行一次 block，发射当前 keyword（空串）
2. 用户连续输入「com」「comp」「compo」：keyword 每次变化触发 block 重算；结果各不相同，依次发射
3. debounce(300) 收到三个值后全部进入等待窗口，只有最后一次输入停止 300ms 后才放行「compo」
4. distinctUntilChanged 与上一次放行的值比较，不同则 collect 触发 viewModel.search("compo")（若 search 内部不可取消，防抖并不能取消它）
5. 结果 Flow 从 ViewModel 发出，collectAsStateWithLifecycle 在 STARTED 状态下收集并更新 uiState
6. UI 读取 uiState 重组，显示加载/成功/错误分支；应用退后台时收集暂停，回前台恢复

## 6. Flutter / Dart 3 对照

先讲相似点：Dart 的 Stream 同样可以级联防抖、去重，StreamBuilder 对应 collectAsState——都是「订阅数据流，数据变化重建 UI」。注意 `TextEditingController` 并没有内置的 `textStream`，需要手动用 listener 桥接（或引入 rxdart 一类的包），下面是手动桥接的无依赖版本：

```dart
class _SearchScreenState extends State<SearchScreen> {
  final _controller = TextEditingController();
  final _keyword = StreamController<String>();
  StreamSubscription<String>? _sub;

  @override
  void initState() {
    super.initState();
    // 手动把 listener 回调桥接成 Stream：Compose 的 snapshotFlow 自动完成这一步
    _controller.addListener(() => _keyword.add(_controller.text));
    _sub = _keyword.stream
        .debounce(const Duration(milliseconds: 300)) // 需 rxdart 或自实现
        .distinct()
        .listen((query) => viewModel.search(query));
  }

  @override
  void dispose() {
    _sub?.cancel();
    _keyword.close();
    _controller.dispose();
    super.dispose();
  }
}
```

本质区别：Compose 的 snapshotFlow 是「从声明式状态反向生成流」——状态在 UI 里，依赖读取被快照系统自动记录，不需要手动往流里写值，还自带结果去重；Flutter 里状态在 Controller 里，流要靠开发者手动搭建（listener → StreamController），订阅与桥接的生命周期（cancel/close）也要手动管理。另外，collectAsStateWithLifecycle 的生命周期感知在 Flutter 里没有直接对应物，需要借助 WidgetsBindingObserver 手动实现。

## 7. 常见错误

1. 以为 collect 块里的重活会被 `flowOn` 切走：flowOn 只影响上游；collect 块跟着收集者（主线程）走。上游要 main-safe，收集点只留轻逻辑
2. 后台仍持续收集：用 collectAsState 而不是 collectAsStateWithLifecycle，退后台后 Flow 还在跑
3. 以为每次状态写入都发射：snapshotFlow 只在新结果与上次发射值不相等时发射；同快照内的多次写入还可能合并——需要完整事件历史时它不可靠，应显式建模事件流
4. block 里读普通变量：不建立快照依赖，值变了也永远不重新执行
5. 以为防抖能取消进行中的请求：debounce 只约束发起时机，取消要靠搜索侧可取消的结构（collectLatest/flatMapLatest 或自身支持取消），且取决于 search 的实现方式
6. 忘记 debounce 之后的 distinctUntilChanged：时间上分离的重复（删字又打回）防不住，会在两个窗口后重复触发搜索
7. 把 snapshotFlow 当通用数据源用：业务数据不走 ViewModel 的 Flow，反而写进快照状态再用 snapshotFlow 读出来——它的职责只是「UI 状态读出」的桥，反向请用 StateFlow + collectAsStateWithLifecycle（第 68 章）

## 8. 本章检查题

1. snapshotFlow 在什么时机发射新值？它依赖组合吗？
2. collectAsState 与 collectAsStateWithLifecycle 的核心区别是什么？
3. flowOn(Dispatchers.IO) 能解决什么、不能解决什么？
4. 防抖之后为什么还要 distinctUntilChanged？防抖能取消已开始的搜索吗？
5. （预测行为）keyword 当前值是「abc」，再写入一次完全相同的「abc」，snapshotFlow 会发射吗？为什么？
6. （动手修改）在 debounce 之前和之后各接一个 `.onEach { println("经过：$it") }`（import kotlinx.coroutines.flow.onEach），快速输入「comp」再改成「compose」，观察两处分别打印了什么，解释差异。

答案：

1. 冷流：开始收集时执行一次 block 发射当前值；之后依赖变化重算 block，只有新结果与上次发射值不相等（equals）时才发射。它不依赖组合，普通协程中也能收集；放 LaunchedEffect 是为了绑定生命周期。收集协程取消后，block 停止执行、不再追踪读取
2. 后者感知生命周期，只在 STARTED 及以上收集，退后台停止、回前台恢复；前者无条件收集
3. 能把上游操作的执行线程切到 IO；不能切走 collect 块（跟随收集者上下文），也不能让不可取消的上游自动可取消
4. snapshotFlow 的去重只比较相邻发射值，防抖窗口之间时间上分离的重复需要再过滤；防抖不取消已开始的搜索，取消需要搜索侧本身可取消（如 collectLatest/flatMapLatest 或挂起实现）
5. 不发射。mutableStateOf 默认按结构相等判断写入，相等值的写入不产生状态失效，block 不会重新执行；即便重算，snapshotFlow 的内建去重也会拦下相同结果
6. debounce 之前的位置打印两次：「comp」和「compose」各经过一次（每次按键变化都会流到上游操作符）；debounce 之后只打印一次「compose」——防抖窗口内只放行最后一个值

## 9. 本章总结

snapshotFlow 把快照状态变成冷流：只追踪 State 读取、结果去重、收集取消即停止追踪，配合 debounce/distinctUntilChanged 处理高频输入；它只是「UI 状态读出」的桥，不是通用 Flow 的替代品。collectAsStateWithLifecycle 把外部 Flow 以生命周期感知的方式接回 UI。数据流双向打通，Compose 与协程世界就真正融合了。

官方参考：

- [Side-effects in Compose：snapshotFlow](https://developer.android.com/develop/ui/compose/side-effects#snapshotFlow)
- [Composable lifecycle](https://developer.android.com/develop/ui/compose/lifecycle)
- [Kotlin Flow：flowOn 与上下文保存](https://kotlinlang.org/docs/flow.html#flow-on)
- [Kotlin Flow：防抖与 flattening 操作符](https://kotlinlang.org/docs/flow.html#flatten-operator)
