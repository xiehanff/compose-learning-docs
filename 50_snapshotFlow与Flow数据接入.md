# 第 50 章：snapshotFlow 与 Flow 数据接入

## 1. 本章目标

打通两个方向的数据流：把 Compose 快照状态转成冷流，把外部 Flow 接回 Compose UI。

学完后，你应当能够：

- 理解 snapshotFlow 的发射时机与依赖收集方式
- 写出搜索框防抖的标准组合（snapshotFlow + debounce + distinctUntilChanged）
- 区分 collectAsState 与 collectAsStateWithLifecycle 的使用场景
- 知道耗时收集操作该放在哪个线程

## 2. 项目中的位置

进阶篇不再局限于 compose_demo 项目，本章使用通用 Compose API。

涉及的内容包括：

- androidx.compose.runtime（snapshotFlow、collectAsState、remember）
- androidx.compose.runtime:runtime 与 lifecycle-runtime-compose（collectAsStateWithLifecycle）
- kotlinx.coroutines（Flow、debounce、distinctUntilChanged、flowOn）

## 3. 项目源码片段

场景一：搜索框输入防抖。keyword 每次按键都在变，但我们只希望在用户停止输入 300ms 后才真正发起搜索。

```kotlin
@Composable
fun SearchScreen(viewModel: SearchViewModel) {
    var keyword by remember { mutableStateOf("") }

    // 快照状态 -> 冷流：任何变化都发射，配合防抖与去重
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

场景二：把 ViewModel 的 Flow 接回 UI，并感知生命周期。

```kotlin
@Composable
fun ResultList(viewModel: SearchViewModel) {
    // 生命周期感知：STARTED 及以上才收集，后台自动停止，省资源
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    when (val state = uiState) {
        is Loading -> CircularProgressIndicator()
        is Success -> LazyColumn { items(state.items) { Text(it) } }
        is Error -> Text(state.message)
    }
}
```

## 4. 逐步解释

`snapshotFlow { ... }` 是连接两个世界的桥：它的 lambda 在快照系统中执行，读取的 State 会被记录为依赖；任何依赖变化时，lambda 重新执行并把结果发射到返回的 Flow 上。注意三点：第一，它发射的是「每次变化后的新值」——初次收集时也会立即发射一次当前值；第二，lambda 里必须读 State，读普通变量不建立依赖，永远不触发发射；第三，它只能在组合期间调用（如配合 LaunchedEffect），在组合外调用快照 API 会抛异常。

防抖搜索是它的经典用法：每次按键都发射新值，`debounce(300)` 只在停止输入 300ms 后才放行最后一个值，`distinctUntilChanged()` 过滤掉连续相同的值（比如删掉再打回同一个字），最后 `collect` 触发搜索。整个流水线声明式地描述了「什么输入对应什么动作」，而不用手动管理定时器。

`collectAsState` 把任意 Flow 转成 State：Flow 发射新值时更新 State，读取它的 UI 重组。`collectAsStateWithLifecycle` 来自 lifecycle-runtime-compose，感知生命周期——只有 Lifecycle 处于 STARTED 及以上才收集，退到后台自动停止收集、回前台恢复，省电省流量。**两者核心区别就是生命周期感知**：屏幕不在前台时，普通版本仍在收集，可能白白执行昂贵的 Flow 操作。

线程方面：Flow 的 collect 默认在收集者所在线程执行。Compose 的收集发生在主线程（组合上下文），所以 `collect { }` 里若做网络、数据库等耗时操作，应在上游加 `flowOn(Dispatchers.IO)` 切换生产线程，让 UI 线程只做轻量收尾。记住：collect 回调里别做重活，重活放 flowOn 的上游。

## 5. 实际执行过程

1. 首次组合：LaunchedEffect 启动，snapshotFlow 立即执行一次 lambda，发射当前 keyword（空串）
2. 用户连续输入「com」「comp」「compo」：keyword 每次变化，snapshotFlow 发射三个值
3. debounce(300) 收到三个值后全部进入等待窗口，只有最后一次输入停止 300ms 后才放行「compo」
4. distinctUntilChanged 与上一次搜索结果比较，去重后 collect 触发 viewModel.search("compo")
5. 结果 Flow 从 ViewModel 发出，collectAsStateWithLifecycle 在 STARTED 状态下收集并更新 uiState
6. UI 读取 uiState 重组，显示加载/成功/错误分支；应用退后台时收集暂停，回前台恢复

## 6. Flutter / Dart 3 对照

先讲相似点：Dart 的 Stream 同样可以用 debounce 做防抖，StreamBuilder 对应 collectAsState——都是「订阅数据流，数据变化重建 UI」：

```dart
class SearchScreen extends StatefulWidget {
  const SearchScreen({super.key});

  @override
  State<SearchScreen> createState() => _SearchScreenState();
}

class _SearchScreenState extends State<SearchScreen> {
  final _controller = TextEditingController();
  StreamSubscription<String>? _sub;

  @override
  void initState() {
    super.initState();
    _sub = _controller.textStream // 或手动监听
        .debounce(const Duration(milliseconds: 300))
        .distinct()
        .listen((query) => viewModel.search(query));
  }

  @override
  void dispose() {
    _sub?.cancel();
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return StreamBuilder<List<String>>(
      stream: viewModel.results,
      builder: (context, snapshot) {
        final items = snapshot.data ?? const [];
        return ListView.builder(itemCount: items.length, itemBuilder: ...);
      },
    );
  }
}
```

本质区别：Compose 的 snapshotFlow 是「从声明式状态反向生成流」——状态在 UI 里，流的发射由快照系统自动驱动，不需要手动往流里写值；Flutter 里状态在 Controller 里，流（textStream）需要框架/开发者手动搭建，且订阅生命周期（cancel）要手动管理。另外，collectAsStateWithLifecycle 的生命周期感知在 Flutter 里没有直接对应物，需要借助 WidgetsBindingObserver 手动实现，Compose 把它做进了标准库。

## 7. 常见错误

1. `collect { }` 里做网络请求等重活，卡住主线程——应用 flowOn(Dispatchers.IO) 把生产切到后台线程
2. 后台仍持续收集：用 collectAsState 而不是 collectAsStateWithLifecycle，退后台后 Flow 还在跑
3. snapshotFlow 里读普通变量：不建立快照依赖，值变了也永远不发射
4. 在组合外（如 ViewModel、普通函数）调用 snapshotFlow：快照 API 只能在组合期间使用
5. 忘记 distinctUntilChanged：防抖后连续相同的关键词重复触发搜索（如删字再打回）

## 8. 本章检查题

1. snapshotFlow 在什么时机发射新值？
2. collectAsState 与 collectAsStateWithLifecycle 的核心区别是什么？
3. 防抖逻辑应该放在哪一层？
4. snapshotFlow 的 lambda 中必须读什么才能建立依赖？

答案：

1. 首次收集时发射当前值；之后每当 lambda 中读取的 State 变化时，重新执行 lambda 并发射新值
2. 后者感知生命周期，只在 STARTED 及以上收集，退后台停止、回前台恢复；前者无条件收集
3. 放在数据流层（snapshotFlow 后的 debounce），用声明式流水线处理，而不是在 UI 事件里手动管理定时器
4. 必须读 State（快照状态）；读普通变量不会建立依赖，永远不会触发发射

## 9. 本章总结

snapshotFlow 把快照状态变成可操作的冷流，配合 debounce/distinctUntilChanged 处理高频输入；collectAsStateWithLifecycle 把外部 Flow 以生命周期感知的方式接回 UI。数据流双向打通，Compose 与协程世界就真正融合了。
