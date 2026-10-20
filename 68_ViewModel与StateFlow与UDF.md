# 第 68 章：ViewModel、StateFlow 与 UDF 单向数据流

## 1. 本章目标

学完后，你应当能够：

- 用 ViewModel + StateFlow 组织页面级状态，理解 ViewModel 的职责边界
- 理解 UDF 单向数据流：事件只进 ViewModel，状态只从 ViewModel 流出
- 区分 UI State 与 UI 事件，理解重要结果为什么应归约为状态
- 用 sealed 接口把互斥的界面形态建模为四态，并走通「点击 → 事件 → 状态 → UI」的完整闭环
- 用 stateIn + WhileSubscribed 把 `flow { }` 这类冷流 producer 转成共享的热流并托管生命周期

## 2. 项目中的位置

进阶篇不再局限于 compose_demo 项目，本章使用通用 AndroidX API。

- `androidx.lifecycle:lifecycle-viewmodel`：`ViewModel`、`viewModelScope`
- `androidx.lifecycle:lifecycle-viewmodel-compose`：`viewModel()`
- `androidx.lifecycle:lifecycle-runtime-compose`：`collectAsStateWithLifecycle`
- `kotlinx.coroutines.flow`：StateFlow、MutableStateFlow、stateIn、SharingStarted（来自 Compose runtime 传递依赖的 kotlinx-coroutines）

注意这些 lifecycle 构件**不在 Compose BOM 管理范围内**，真实工程需要按 lifecycle 版本单独声明（常见做法是声明 `lifecycle-viewmodel-compose` 与 `lifecycle-runtime-compose`，`ViewModel`/`viewModelScope` 会随之传递引入）。本仓库 snippets 校验工程里，它们由第 71A 章依赖的 `lifecycle-viewmodel-navigation3`（2.11.0）传递提供，本章标记为可编译的片段即在该基线下通过编译；单独搭建验证工程时须自行声明。

## 3. 项目源码片段

场景一：计数器 + 模拟网络加载，展示 UDF 完整闭环：

<!-- compile decls -->
```kotlin
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

// UiState.kt —— 状态是一个不可变 data class
data class CounterUiState(
    val count: Int = 0,
    val loading: Boolean = false,
    val userMessage: String? = null
)

// CounterViewModel.kt —— 全页面唯一能改状态的地方
class CounterViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(CounterUiState())
    val uiState: StateFlow<CounterUiState> = _uiState.asStateFlow()

    fun increment() {
        _uiState.update { it.copy(count = it.count + 1) }
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true) }
            delay(1000) // 模拟网络请求
            _uiState.update {
                it.copy(loading = false, userMessage = "加载完成")
            }
        }
    }

    fun userMessageShown() {
        _uiState.update { it.copy(userMessage = null) }
    }
}
```

场景二：把 `flow { }` 这类冷流 producer 转成热流，用 stateIn 托管。`weatherRepository.flow()` 与 `it.celsius` 是节选占位（任何暴露 Flow 的数据仓库），不参与编译校验，`map/stateIn` 流水线才是重点：

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

场景三：UI 侧收集（与场景一配套的节选，`CounterViewModel` 即场景一定义的那个类）：

```kotlin
@Composable
fun CounterScreen(viewModel: CounterViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // UI 根据状态执行显示动作，完成后再回传“已处理”事件
    val context = LocalContext.current
    LaunchedEffect(uiState.userMessage) {
        uiState.userMessage?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            viewModel.userMessageShown()
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

场景四：UI 状态的最小迁移案例——从「一堆 Boolean 字段」换成 sealed 接口建模 `loading / error / empty / content` 四态，并把「按钮点击 → 事件进 ViewModel → 状态变化 → UI 更新」的 UDF 闭环走完整。这是把场景一的单 data class 换成互斥分支的写法：四个状态不可能同时成立，非法组合（「加载中且又出错」）在类型层面就不存在。

<!-- compile decls -->
```kotlin
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

// UI 状态：四个互斥分支，一个时刻只有一个是当前值
sealed interface NoteUiState {
    data object Loading : NoteUiState
    data object Empty : NoteUiState
    data class Error(val message: String) : NoteUiState
    data class Content(val notes: List<String>) : NoteUiState
}

class NoteViewModel : ViewModel() {

    private val _uiState = MutableStateFlow<NoteUiState>(NoteUiState.Loading)
    val uiState: StateFlow<NoteUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    // 事件入口：UI 只能调用方法，不能改状态
    fun refresh() {
        _uiState.update { NoteUiState.Loading }
        viewModelScope.launch {
            delay(800) // 模拟网络请求；真实项目在这里 try/catch，
            // 成功且为空 -> Empty，成功非空 -> Content，抛异常 -> Error
            val result = listOf("第一条笔记", "第二条笔记")
            _uiState.update {
                if (result.isEmpty()) NoteUiState.Empty else NoteUiState.Content(result)
            }
        }
    }
}

@Composable
fun NoteScreen(viewModel: NoteViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // when 覆盖全部分支：以后新增状态，编译器会在所有漏改的 when 处报错
        when (val state = uiState) {
            is NoteUiState.Loading -> CircularProgressIndicator()
            is NoteUiState.Empty -> {
                Text("暂无内容")
                Button(onClick = viewModel::refresh) { Text("重新加载") }
            }
            is NoteUiState.Error -> {
                Text("出错了：${state.message}")
                Button(onClick = viewModel::refresh) { Text("重试") }
            }
            is NoteUiState.Content -> {
                LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                    items(state.notes) { note -> Text(note) }
                }
                Button(onClick = viewModel::refresh) { Text("刷新") }
            }
        }
    }
}
```

初始界面与预期观察：进入 NoteScreen 先看到加载指示器，约 0.8 秒后变成两行笔记和「刷新」按钮；点「刷新」回到加载态、再回到列表。Error 分支在本模拟里不会自然触发（模拟请求永远成功），它对应真实网络请求抛异常时 `catch` 里把状态归约为 `Error("...")` 的路径；想亲手看到它，按本章检查题第 6 题动手改。

「按钮点击 → 事件进 ViewModel → 状态变化 → UI 更新」的编号闭环：

1. 首次进入组合：`viewModel()` 取得 NoteViewModel（首次创建，存入 ViewModelStore），`init` 调用 `refresh()`
2. `refresh()` 把状态归约为 `Loading` → StateFlow 发射 → `collectAsStateWithLifecycle` 收集到新值 → 读取处重组 → 显示加载指示器
3. 800ms 后协程结束，状态归约为 `Content(两条笔记)` → UI 重组成列表
4. 用户点击「刷新」：`onClick` 调用 `viewModel.refresh()`——**事件从 UI 流入 ViewModel，这是唯一方向**；UI 不写状态，ViewModel 不碰 UI
5. `refresh()` 再次归约为 `Loading`，回到第 2 步：闭环转起来，全程只有状态这一个真相来源

## 4. 逐步解释

ViewModel 的职责很纯粹：持有 UI 状态、处理业务逻辑、跨配置变化存活。旋转屏幕时 Activity 重建，但 ViewModelStore 保留旧实例，viewModel() 取回的是同一个对象，所以计数不会丢。注意 ViewModel 里**不能**持有 Composable、Context、View 引用，否则泄漏且无法测试。

先澄清一个常见误会：`Flow` 只是一个**接口**，它本身不描述冷热，`StateFlow`、`SharedFlow` 也都是 `Flow` 的实现。冷热说的是**实现方式**：`flow { }` 这类 builder、以及 Room/DataStore 返回的 Flow，默认是**冷流**；`StateFlow` / `SharedFlow` 是**热流**。

StateFlow 与 `flow { }` 这类冷流 producer 的差别是本章的基石：StateFlow 是热流，独立于收集者存在、共享上游，有当前值（value 随时可读）、相同值不重复发射（去重）、UI 收集时立刻拿到最新值。注意内部用 MutableStateFlow，对外只暴露 `asStateFlow()` 转成的只读视图，UI 永远拿不到写入口。

UDF 的闭环：UI 事件（点击）→ 调用 ViewModel 方法 → 方法修改状态 → StateFlow 发射 → collectAsStateWithLifecycle 收集 → 重组。状态只有一个来源，UI 只读不写，这就是「单向」的含义。

场景一与场景四是两种建模思路，按状态关系选择：字段之间**独立变化**（计数、是否显示某条提示）用场景一的单个 data class，一次 copy 只动相关字段；字段之间**互斥**（同一时刻只可能是加载中、出错、空、有内容之一）用场景四的 sealed 接口。后者的收益是编译器接管分支完整性：`when` 对密封类型做穷尽检查，漏写分支直接编译报错，将来新增状态也会在所有旧 `when` 处暴露；而用一排 Boolean 字段拼「界面状态」时，「loading = true 且 error 非空」这类非法组合挡不住，只能靠人肉约定。sealed class 与 sealed interface 都能做到，本章选 interface 是因为它更轻（无构造开销的分支可以直接用 data object 表示）。注意四态不是教条——分支该几个就几个，关键是「互斥的界面形态用互斥的类型建模」。

不要把“Toast、导航、Snackbar”机械地等同于必须使用 Channel。ViewModel 通常比当前 Compose UI 活得更久；当没有消费者时，Channel/事件流不能保证事件已经被 UI 接收和处理。对用户有意义的结果应尽量立即归约到 UI State，例如 `userMessage`，UI 处理后再发送 `userMessageShown()` 清除。纯粹属于 UI 层的导航和显示逻辑，也可以直接在 UI 事件处理链中完成。

`flow { }` 这类 builder、以及 Room/DataStore 返回的 Flow 默认是冷流，每个收集者都会重新执行上游，直接暴露给 UI 收集成本高。用 `stateIn` 把它转成热流托管在 viewModelScope：`stateIn` 的作用就是给一个 Flow 挂上"共享"与"何时启动/停止上游"的策略，让多个订阅者共用同一个上游、并得到一个随时可读当前值的结果；`WhileSubscribed(5000)` 表示最后一个订阅者离开 5 秒后停止上游，避免后台空跑。（`stateIn` 的参数全貌与数据库场景下的用法第 72 章会再讲。）

最后是分工：remember + mutableStateOf 管本地 UI 状态（输入框、开关、展开态），ViewModel + StateFlow 管页面级状态（需要跨重组、跨配置变化保留的数据）。ViewModel 会跨配置变化存活，但不会跨进程死亡；需要在系统重建后恢复的少量键值，可用 SavedStateHandle（ViewModel 按键保存和读取小状态的对象），长期数据则写入数据库或 DataStore。

记住：ViewModel 把业务结果归约为可测试的 UI State；UI 把用户动作传入状态持有者。Channel 不是“恰好一次”的保证，只有明确接受其丢失/竞争语义时才使用。

## 5. 实际执行过程

1. 用户点击「加载」，Button 回调调用 viewModel.load()
2. load() 在 viewModelScope 启动协程，先把 loading 置为 true
3. StateFlow 发射新状态，collectAsStateWithLifecycle 收集，触发重组，UI 显示「加载中…」
4. 协程延时结束，把 loading=false 和 userMessage 一起写入 UI State
5. UI 的 LaunchedEffect 看到 userMessage 后弹 Toast，再回传 userMessageShown() 清除消息
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

final counterProvider = ChangeNotifierProvider(create: (_) => CounterModel());

class CounterScreen extends StatelessWidget {
  const CounterScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final counter = context.watch<CounterModel>(); // 相当于 collectAsState
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

- Compose 的状态是「数据流 + 可观察对象」：StateFlow 自带当前值、去重、订阅，语言级工具齐全；Flutter 的 ChangeNotifier 是手动通知模型，漏调 notifyListeners 就不同步。**StateFlow 不等于 ChangeNotifier**：前者是有当前值的热 Flow，靠结构相等过滤重复发射、跟随协程结构化并发取消；后者只是一个监听者列表加一个手动通知方法，通知粒度永远是「整个对象变了」，要表达「只有某个属性变了」只能拆 model 或靠接收端自己比对。
- 状态建模侧：Dart 3 同样有 sealed class 和穷尽 switch，四态建模（场景四那种）在 Flutter 里可以写出结构相似的代码；差别在通知与生命周期那一层——StateFlow 发射即快照值变化，ChangeNotifier 需要你在每个变更点手动记得通知。
- 临时显示动作：两端都应先判断它代表可恢复状态还是纯 UI 行为，不应默认依赖“只投递一次”的事件总线。
- 生命周期：collectAsStateWithLifecycle 自动绑定生命周期；provider 包里的 `context.watch` 由框架负责在依赖变化时重建，`ChangeNotifierProvider` 也会 dispose 它创建的 model；但若手动调用 `addListener`，仍需自己 `removeListener`。

记住：两者都是「UI → 方法 → 状态 → 重建/重组」的单向数据流；Compose 把状态做成了 Flow，工具链更完整。

## 7. 常见错误

- 默认用 Channel 追求“恰好一次” → ViewModel 比 UI 活得久时仍可能丢失，重要结果应归约为 UI State
- 在 ViewModel 里持有 Compose 引用（Composable、Activity 类型的 Context）→ 泄漏且无法单元测试
- 把 MutableStateFlow 直接暴露给 UI → UI 可以随意改状态，UDF 被破坏
- stateIn 忘记 WhileSubscribed → 上游冷流一直活跃，后台持续拉数据
- 用 collectAsState 而不是 collectAsStateWithLifecycle → 退后台仍在收集
- 用一排 Boolean 拼互斥的界面状态 → 「加载中且出错」这类非法组合挡不住，互斥形态应建模为 sealed 接口（场景四）

## 8. 本章检查题

1. StateFlow 与 `flow { }` 这类冷流 producer 的区别？
2. 为什么重要的一次性结果不应默认通过 Channel 从 ViewModel 投递？
3. ViewModel 为什么能在旋转后存活？
4. stateIn 的 WhileSubscribed 有什么用？
5. （预测行为）场景四中点击「刷新」的瞬间，屏幕上显示什么？0.8 秒后呢？如果收集用的是 collectAsState 而不是 collectAsStateWithLifecycle，应用退到后台期间点不到按钮，这两种写法有差别吗？
6. （动手修改）把场景四的 refresh() 改成 50% 概率失败：`if (Random.nextBoolean())` 成功时进 Content，失败时把状态归约为 `Error("网络异常")`。运行并多点几次「重试」，验证 Loading → Error → 重试 → Content 的闭环能走通。

答案：

1. StateFlow 是热流：独立于收集者存在、有当前值可立即读取、相同值不重复发射、多个收集者共享同一数据源；`flow { }` 这类 builder 以及 Room/DataStore 返回的 Flow 默认是冷流，每个收集者都会重新执行上游。注意 `Flow` 只是接口，本身不描述冷热，`StateFlow` 也是 `Flow` 的实现。
2. ViewModel 可能在 UI 没有收集者时继续存活，Channel 不能保证 UI 已接收并完成处理；把结果归约到 UI State 后，可以在 UI 恢复时继续处理，并在完成后显式清除。
3. 旋转属于配置变化，Activity 销毁重建时 ViewModelStore 保留 ViewModel 实例，新 Activity 通过 viewModel() 取回同一个实例。
4. 指定「没有订阅者后多久停止上游」，避免冷流（网络/DB）在无 UI 消费时继续运行浪费资源。
5. 点击瞬间显示加载指示器（状态先被归约为 Loading），0.8 秒后回到列表。这个场景里两者无差别：按钮点不到意味着没有新事件、状态也不变，后台期间两种收集方式都收不到新值；差别要等到「后台期间状态仍可能变化」（如定时刷新）才体现——collectAsStateWithLifecycle 会在回到前台后补上最新值且不白白消耗资源，它挡住的是后台的无效收集
6. 提示：用 `kotlin.random.Random`；失败分支写 `_uiState.update { NoteUiState.Error("网络异常") }`；重试仍走 refresh()，因此 Error → Loading →（随机）Content/Error 的循环成立。观察点：Error 分支出现重试按钮、点击后回到加载态，说明所有分支都接进了同一个闭环

## 9. 本章总结

页面级状态用 ViewModel + StateFlow 管理，事件只从 UI 流入、状态只从 ViewModel 流出，形成 UDF 闭环。字段独立变化用单个不可变 data class，互斥的界面形态用 sealed 接口（loading/error/empty/content 四态是典型形状，但分支数按业务定）；重要业务结果归约为 UI State；纯 UI 行为留在 UI 层；`flow { }` 这类冷流可用 stateIn + WhileSubscribed 托管。不要把 Channel 当作“一次性事件”的万能答案。

官方参考：

- [UI events](https://developer.android.com/topic/architecture/ui-layer/events)
- [Where to hoist state](https://developer.android.com/develop/ui/compose/state-hoisting)
- [ViewModel overview](https://developer.android.com/topic/libraries/architecture/viewmodel)
- [StateFlow and SharedFlow](https://developer.android.com/kotlin/flow/stateflow-and-sharedflow)
