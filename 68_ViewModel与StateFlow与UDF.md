# 第 68 章：ViewModel、StateFlow 与 UDF 单向数据流

## 1. 本章目标

学完后，你应当能够：

- 用 ViewModel + StateFlow 组织页面级状态，理解 ViewModel 的职责边界
- 理解 UDF 单向数据流：事件只进 ViewModel，状态只从 ViewModel 流出
- 区分一次性事件与状态，正确使用 Channel 承载事件
- 用 stateIn + WhileSubscribed 把冷流转成热流并托管生命周期

## 2. 项目中的位置

进阶篇不再局限于 compose_demo 项目，本章使用通用 Compose API。

- `androidx.lifecycle`：ViewModel、viewModelScope
- `androidx.lifecycle.viewmodel.compose`：viewModel()
- `androidx.lifecycle.compose`：collectAsStateWithLifecycle
- `kotlinx.coroutines.flow`：StateFlow、MutableStateFlow、stateIn、SharingStarted
- `kotlinx.coroutines.channels`：Channel

## 3. 项目源码片段

场景一：计数器 + 模拟网络加载，展示 UDF 完整闭环：

```kotlin
// UiState.kt —— 状态是一个不可变 data class
data class CounterUiState(
    val count: Int = 0,
    val loading: Boolean = false
)

// CounterViewModel.kt —— 全页面唯一能改状态的地方
class CounterViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(CounterUiState())
    val uiState: StateFlow<CounterUiState> = _uiState.asStateFlow()

    // 一次性事件走 Channel，绝不放 StateFlow
    private val _events = Channel<CounterEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun increment() {
        _uiState.update { it.copy(count = it.count + 1) }
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true) }
            delay(1000) // 模拟网络请求
            _events.send(CounterEvent.ShowMessage("加载完成"))
            _uiState.update { it.copy(loading = false) }
        }
    }
}

sealed interface CounterEvent {
    data class ShowMessage(val text: String) : CounterEvent
}
```

场景二：冷流转热流，用 stateIn 托管：

```kotlin
class WeatherViewModel : ViewModel() {
    val temperature: StateFlow<Double> = weatherRepository.flow()
        .map { it.celsius }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000), // 无订阅者 5 秒后停止上游
            initialValue = 0.0
        )
}
```

场景三：UI 侧收集：

```kotlin
@Composable
fun CounterScreen(viewModel: CounterViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // 一次性事件用 LaunchedEffect + collect 消费
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is CounterEvent.ShowMessage ->
                    Toast.makeText(context, event.text, Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("当前计数：${uiState.count}")
        Text(if (uiState.loading) "加载中…" else "就绪")
        Button(onClick = viewModel::increment) { Text("+1") }
        Button(onClick = viewModel::load) { Text("加载") }
    }
}
```

## 4. 逐步解释

ViewModel 的职责很纯粹：持有 UI 状态、处理业务逻辑、跨配置变化存活。旋转屏幕时 Activity 重建，但 ViewModelStore 保留旧实例，viewModel() 取回的是同一个对象，所以计数不会丢。注意 ViewModel 里**不能**持有 Composable、Context、View 引用，否则泄漏且无法测试。

StateFlow 与普通 Flow 的区别是本章的基石：StateFlow 是热流，有当前值（value 随时可读）、相同值不重复发射（去重）、UI 收集时立刻拿到最新值。注意内部用 MutableStateFlow，对外只暴露 `asStateFlow()` 转成的只读视图，UI 永远拿不到写入口。

UDF 的闭环：UI 事件（点击）→ 调用 ViewModel 方法 → 方法修改状态 → StateFlow 发射 → collectAsStateWithLifecycle 收集 → 重组。状态只有一个来源，UI 只读不写，这就是「单向」的含义。

一次性事件（Toast、导航、Snackbar）绝不能放进 StateFlow：StateFlow 会保存并重放最新值，重组后事件会被重复消费。用 Channel 承载，消费即取走，天然「一次即走」。这是初学者最容易踩的坑，务必记住。

冷流（数据库/网络 Flow）每次收集都会重新执行上游，直接暴露给 UI 收集成本高。用 `stateIn` 把它转成热流托管在 viewModelScope；`WhileSubscribed(5000)` 表示最后一个订阅者离开 5 秒后停止上游，避免后台空跑。

最后是分工：remember + mutableStateOf 管本地 UI 状态（输入框、开关、展开态），ViewModel + StateFlow 管页面级状态（需要跨重组、跨配置变化保留的数据）。

记住：状态用 StateFlow（有当前值、可去重、可立即读取），事件用 Channel（一次即走、不重放）；改状态的入口永远只在 ViewModel 里。

## 5. 实际执行过程

1. 用户点击「加载」，Button 回调调用 viewModel.load()
2. load() 在 viewModelScope 启动协程，先把 loading 置为 true
3. StateFlow 发射新状态，collectAsStateWithLifecycle 收集，触发重组，UI 显示「加载中…」
4. 协程延时结束，先向 Channel send 一次性事件，再把 loading 改回 false
5. UI 的 LaunchedEffect 收集到事件，弹 Toast；状态收集到 loading=false，显示「就绪」
6. 旋转屏幕，Activity 重建，viewModel() 从 ViewModelStore 取回同一个实例，计数不丢

## 6. Flutter / Dart 3 对照

先讲相似点：Flutter 的 Provider + ChangeNotifier 结构与 UDF 几乎一致——UI 不直接改数据，通过调用 model 的方法触发变更，model 通知所有监听者，UI 重建：

```dart
class CounterModel extends ChangeNotifier {
  int _count = 0;
  int get count => _count;

  void increment() {
    _count++;
    notifyListeners(); // 相当于 StateFlow 发射
  }
}

final counterProvider = ChangeNotifierProvider((_) => CounterModel());

class CounterScreen extends ConsumerWidget {
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final counter = ref.watch(counterProvider); // 相当于 collectAsState
    return Column(
      children: [
        Text('当前计数：${counter.count}'),
        ElevatedButton(
          onPressed: counter.increment,
          child: const Text('+1'),
        ),
      ],
    );
  }
}
```

本质区别：

- Compose 的状态是「数据流 + 可观察对象」：StateFlow 自带当前值、去重、订阅，语言级工具齐全；Flutter 的 ChangeNotifier 是手动通知模型，漏调 notifyListeners 就不同步。
- 事件流：Compose 用 Channel/SharedFlow，是 Kotlin 协程原语；Flutter 常用 Stream 或事件总线，需要自己封装。
- 生命周期：collectAsStateWithLifecycle 自动绑定生命周期；Flutter 的 ref.watch 由 Provider 框架管理，手动 listen 仍需自己处理取消与 dispose。

记住：两者都是「UI → 方法 → 状态 → 重建/重组」的单向数据流；Compose 把状态做成了 Flow，工具链更完整。

## 7. 常见错误

- 把一次性事件（Toast、导航）放进 StateFlow → 重组后事件被重复消费
- 在 ViewModel 里持有 Compose 引用（Composable、Activity 类型的 Context）→ 泄漏且无法单元测试
- 把 MutableStateFlow 直接暴露给 UI → UI 可以随意改状态，UDF 被破坏
- stateIn 忘记 WhileSubscribed → 冷流一直活跃，后台持续拉数据
- 用 collectAsState 而不是 collectAsStateWithLifecycle → 退后台仍在收集

## 8. 本章检查题

1. StateFlow 与普通 Flow 的区别？
2. 为什么一次性事件要用 Channel 而不是 StateFlow？
3. ViewModel 为什么能在旋转后存活？
4. stateIn 的 WhileSubscribed 有什么用？

答案：

1. StateFlow 是热流：有当前值可立即读取、相同值不重复发射、多个收集者共享同一数据源；普通 Flow 是冷流，每次收集都重新执行上游。
2. StateFlow 会保存并重放最新值，重组后事件会被再次消费；Channel 是「取走即无」的队列，天然适合一次性事件。
3. 旋转属于配置变化，Activity 销毁重建时 ViewModelStore 保留 ViewModel 实例，新 Activity 通过 viewModel() 取回同一个实例。
4. 指定「没有订阅者后多久停止上游」，避免冷流（网络/DB）在无 UI 消费时继续运行浪费资源。

## 9. 本章总结

页面级状态用 ViewModel + StateFlow 管理，事件只从 UI 流入、状态只从 ViewModel 流出，形成 UDF 闭环。记住分工：状态用 StateFlow、一次性事件用 Channel、冷流用 stateIn + WhileSubscribed 托管，UI 只读不写。
