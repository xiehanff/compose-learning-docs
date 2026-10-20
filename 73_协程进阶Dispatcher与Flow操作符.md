# 第 73 章：协程进阶：Dispatchers、结构化并发与 Flow 操作符

## 1. 本章目标

学完后，你应当能够：

- 说清 Dispatchers.Main / IO / Default 的适用场景，并用 `withContext` 切换上下文
- 理解结构化并发：`coroutineScope` 与 `supervisorScope` 的失败传播差异
- 掌握协程取消的协作机制（isActive、suspend 函数自动响应）
- 熟练组合 Flow 操作符（flowOn、catch、distinctUntilChanged、combine 等），并在 Compose 中用 LaunchedEffect + viewModelScope 落地

## 2. 项目中的位置

进阶篇不再局限于 compose_demo 项目，本章使用通用 Compose API。

- `org.jetbrains.kotlinx:kotlinx-coroutines-android`（Dispatchers、coroutineScope、Flow）
- `androidx.compose.runtime`（LaunchedEffect、rememberCoroutineScope、snapshotFlow）
- `androidx.lifecycle:lifecycle-viewmodel-compose`（viewModelScope）

## 3. 项目源码片段

场景一：详情页并行请求「基本信息」与「评论列表」两个接口，全部成功才显示，任一失败进错误态。

```kotlin
class DetailViewModel(private val api: DetailApi) : ViewModel() {

    private val _state = MutableStateFlow<DetailUiState>(DetailUiState.Loading)
    val state: StateFlow<DetailUiState> = _state.asStateFlow()

    fun load(id: Long) {
        viewModelScope.launch {                        // Main 启动
            _state.value = DetailUiState.Loading
            try {
                val result = coroutineScope {          // 结构化并发：子任务失败全部取消
                    val info = async { api.fetchInfo(id) }        // 网络 IO
                    val comments = async { api.fetchComments(id) } // 网络 IO
                    DetailUiState.Success(info.await(), comments.await())
                }
                _state.value = result
            } catch (e: CancellationException) {
                throw e                                // 取消必须继续向上传播
            } catch (e: Exception) {
                _state.value = DetailUiState.Error(e.message ?: "加载失败")
            }
        }
    }
}
```

场景二：Feed 数据流经过解析、过滤、去重后进 UI；解析这类 CPU 重活切到 Default。

```kotlin
class FeedViewModel(private val repo: FeedRepository) : ViewModel() {
    private val _feed = MutableStateFlow<List<FeedItem>>(emptyList())
    val feed: StateFlow<List<FeedItem>> = _feed.asStateFlow()

    fun observe() {
        viewModelScope.launch {
            repo.fetchFeed()                        // 上游：网络 IO
                .map { parse(it) }                  // CPU 重活
                .flowOn(Dispatchers.Default)        // 影响上游（map 及之前）
                .filter { it.isVisible }
                .distinctUntilChanged()             // 与上次相同就不发
                .catch { e ->
                    Log.e("Feed", "加载失败", e)
                    emit(emptyList())
                }
                .collect { list -> _feed.value = list }
        }
    }
}
```

场景三：Compose 侧监听滚动位置做上报（LaunchedEffect + snapshotFlow）。

```kotlin
LaunchedEffect(listState) {
    snapshotFlow { listState.firstVisibleItemIndex }
        .distinctUntilChanged()
        .collect { index -> analytics.track("feed_scroll", index) }
}
```

场景四：搜索框防抖 + 新查询取消旧搜索（flatMapLatest）。输入停顿 300ms 才触发；新查询到来时取消上一次查询流的收集，避免慢请求的旧结果覆盖新结果。

<!-- compile decls -->
```kotlin
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

// flatMapLatest 是 @ExperimentalCoroutinesApi、debounce 是 @FlowPreview，使用处需在 @OptIn 中一并显式启用
@OptIn(ExperimentalCoroutinesApi::class, kotlinx.coroutines.FlowPreview::class)
fun searchResults(query: Flow<String>): Flow<String> =
    query
        .debounce(300)          // 输入停顿 300ms 才向下游发一次
        .flatMapLatest { q ->   // 新查询到来时，取消上一个 inner Flow 的收集
            flowOf("「$q」的结果 A", "「$q」的结果 B")  // 实际项目里这里是网络请求 Flow
        }
```

## 4. 逐步解释

1. 三个 Dispatcher：Main 通常用于 Android 主线程上的 UI 工作；IO 适合可能阻塞的磁盘/网络操作；Default 适合 CPU 密集计算。线程数和调度细节以当前 Kotlin 协程实现为准，不应把它们理解成严格的业务线程保证。特别要区分“挂起”和“切线程”：`suspend` 只表示函数可以挂起，不会自动把普通阻塞代码移到后台。Retrofit、Room 等库通常会为自己的阻塞工作提供调度，但自定义 `suspend` 函数仍必须由实现者或调用方决定执行上下文。

2. withContext 与 flowOn：`withContext(Dispatchers.Default) { ... }` 把一段代码临时切到指定线程池，执行完自动切回原上下文；`flowOn` 是 Flow 版的「上游切换」——它只影响之前的上游操作符，之后的代码回到原上下文。位置放错，线程就切错地方，这是 Flow 最常见的坑之一。

3. 结构化并发：`coroutineScope { }` 是一个「子任务容器」：它等所有子任务完成才返回；任何一个子任务失败，其余子任务全部取消、异常向上抛。`supervisorScope` 相反：子任务失败不影响兄弟任务，各自独立。**怎么选：一个结果依赖多个请求（并行合并）用 coroutineScope；多个互不相关的任务（一个失败不拖累别人）用 supervisorScope。** 永远不要用 GlobalScope 起协程——它没有父作业，页面销毁后没人取消它，泄漏和崩溃都从这里来。

4. 取消是协作的：协程不会因为 `cancel()` 就立刻停，只有协程代码「配合」才响应：调用会检查取消的挂起函数，或主动检查 `isActive` / `ensureActive()`。没有挂起点且不检查取消的长循环可能继续运行。捕获到 `CancellationException` 后通常应继续传播，除非有明确的取消转换边界，否则吞掉它会破坏结构化取消语义。

5. Flow 操作符速记：map / filter 做转换过滤；`catch` 只捕获它上游产生的异常，不会捕获 `collect` 下游代码抛出的异常；`try/catch` 包住 `collect` 可以捕获整个收集调用向外抛出的异常。retry 负责按策略重试；flowOn 切换上游上下文；distinctUntilChanged 去重；conflate 在消费者跟不上时保留较新的值；combine 合并多个流的最新值；flatMapLatest 把上游每个值映射成一个新的 Flow 并取消上一个——「旧查询作废、新查询接管」的取消-重建语义（场景四），注意它目前是实验 API，需要 `@OptIn(ExperimentalCoroutinesApi::class)`；场景四同链使用的 `debounce` 是 FlowPreview API，需要一并在 `@OptIn` 中启用 `kotlinx.coroutines.FlowPreview`。冷流通常在每次 collect 时重新执行，可以发出一个或多个值；StateFlow/SharedFlow 是热流，但其上游是否运行还取决于创建方式和 SharingStarted 策略。

6. Compose 里的常见搭配：`LaunchedEffect(key)` 进入组合或 key 变化时启动协程，离开组合自动取消；`viewModelScope` 随 ViewModel 销毁取消。屏幕级业务数据通常由 ViewModel 响应明确事件或在初始化时加载；只与当前组合节点有关的 UI 副作用才放 LaunchedEffect。`rememberCoroutineScope` 适合从点击等回调启动并由当前组合节点管理的协程。

## 5. 实际执行过程

1. 用户进入详情页，`load(id)` 被调用，viewModelScope.launch 在 Main 启动，state 置为 Loading。
2. coroutineScope 内两个 async 同时发起：fetchInfo 与 fetchComments 并行执行，Main 挂起等待。
3. 两个请求都返回后，`info.await()` / `comments.await()` 各得结果，coroutineScope 返回 Success，_state 更新，UI 重组显示内容。
4. 若评论接口抛异常：coroutineScope 取消还在跑的 fetchInfo，异常向上抛，被外层 catch 捕获，UI 进入 Error 态。
5. 用户离开页面，ViewModel 销毁，viewModelScope 自动取消所有子任务——即使请求还没回来也不会泄漏。

## 6. Flutter / Dart 3 对照

先看相似点：Dart 的 async / await 与 Kotlin 的 suspend 语法几乎一一对应；Future.wait 对应「并行请求合并」；Stream 对应 Flow；isolate 对应 Dispatchers.Default 的重计算：

```dart
Future<DetailUiState> loadDetail(int id) async {
  // Future.wait：等价 coroutineScope + async 的并行合并
  try {
    final results = await Future.wait([
      api.fetchInfo(id),
      api.fetchComments(id),
    ]);
    return DetailUiState.success(results[0] as Info, results[1] as List<Comment>);
  } catch (e) {
    return DetailUiState.error(e.toString());
  }
}

// isolate：等价 Dispatchers.Default 的 CPU 重活
final json = await compute(parseJson, raw); // compute() 在后台 isolate 执行
```

本质区别：Dart 的 Future API 与 Kotlin 的 coroutineScope / supervisorScope 不是同一套结构化并发模型，父子取消和失败传播不能直接一一对应；未等待 Future 的异常处理也取决于 Dart 代码和运行环境，不能简单概括为“静默”。Flutter 里页面销毁后的任务通常需要显式结合生命周期取消，Kotlin 则可以使用 viewModelScope / LaunchedEffect 等结构化作用域，但库调用和自定义任务仍需正确处理取消。

Flow 操作符侧可以再对照一对：Kotlin 的 `flatMapLatest` 对应 Dart 侧 RxDart 的 `switchMap` 扩展（`SwitchMapExtension`，见 [RxDart 文档](https://pub.dev/documentation/rxdart/latest/rx/SwitchMapExtension.html)；注意它是第三方库扩展，需在 pubspec 引入 `rxdart` 依赖——Dart SDK 的 `Stream` 本身没有内建 switchMap，直接写会编译不过）——新事件到来时都取消上一个内部流；不等价之处在于 Kotlin 的取消沿结构化并发的父子关系自动传播到 inner Flow 内部的每个挂起点，Dart 的取消靠 `StreamSubscription.cancel()`，`async*` 生成器通常要走到下一个 `yield` 才真正退出。

可以用“父子作用域”帮助理解 Kotlin 结构化并发，但不能据此保证异常不会遗漏；`launch`、`async` 和 supervisor 的异常处理责任不同。Dart 的 Future 链和 Zone 也有异常传播规则，两者都需要明确处理错误与取消。

## 7. 常见错误

1. 用 GlobalScope 启动长期任务：不随页面销毁而取消，任务泄漏、回调打到已销毁的 UI 上直接崩。任何界面相关协程都用 viewModelScope / LaunchedEffect。
2. 在 Main 上解析大 JSON：主线程跑 CPU 重活 → 掉帧、卡顿甚至 ANR。要么用内部已切线程的 suspend 库，要么显式 `withContext(Dispatchers.Default)`。
3. 误解 catch 的边界：Flow 的 `catch` 只处理上游异常；`collect` 回调自身的异常属于下游，不会被前面的 `catch` 捕获。需要统一处理整个收集调用时，可以在外层使用 `try/catch`，但必须继续传播 CancellationException。
4. 混用 coroutineScope 与 supervisorScope：失败要连坐却用了 supervisor，两个请求各挂各的，返回残缺数据；互不相关却用了 coroutineScope，一个任务失败把兄弟任务也杀了。先问「失败要不要连坐」再选。
5. 忽略 flowOn 的位置：flowOn 只影响它之前的上游。把 flowOn 写在 collect 前想切 collect 的线程，是无效的。
6. catch 吞掉 CancellationException：取消被当成普通异常处理，协程不响应取消，任务继续在后台跑。

## 8. 本章检查题

1. Dispatchers.Main / IO / Default 各自的用途？
2. coroutineScope 与 supervisorScope 的区别？
3. flowOn 影响的是哪一侧？放错位置会怎样？
4. 为什么说协程取消是「协作」的？
5. 搜索场景为什么要用 flatMapLatest 而不是 map？它取消旧收集的语义是什么？

答案：

1. `Main` 通常对应 Android 主线程上的 UI 工作；`IO` 适合阻塞型磁盘/网络操作；`Default` 适合 CPU 密集计算。它们是调度意图，不应当作所有实现和底层线程细节的绝对保证；线程数与调度细节以当前 Kotlin 协程版本为准。
2. coroutineScope 中任一子任务失败会取消全部子任务并把异常抛给外层；supervisorScope 中一个子任务失败不影响兄弟任务。按「失败要不要连坐」选择。
3. flowOn 影响它之前的上游操作符的执行线程，之后的下游回到原上下文；放错位置会导致重活在错误线程执行，或想切 collect 线程却无效。
4. cancel() 请求取消，代码需调用支持取消的挂起函数，或主动检查 isActive / ensureActive() 才能及时响应。suspend 标记本身不保证检查取消；没有挂起点也不检查取消的长循环可能继续运行。
5. 差别不在「能不能异步」：`map` 的转换函数本身就是 suspend 函数，同样可以执行挂起的网络请求（官方签名见 [map](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.flow/map.html)）。差别在取消语义与返回值形态：`map` 把每个值转换成一个结果，新值到来不会取消上一个还在进行的转换；`flatMapLatest` 把每个值映射成一个新的 Flow，并在上游发出新值时取消对上一个 inner Flow 的收集、改为收集新的——「新输入作废未完成的旧请求」只有这种取消-重建语义才表达得出，这正是搜索场景需要它的原因。使用它需要 @OptIn(ExperimentalCoroutinesApi::class)。

官方参考：[Coroutine context and dispatchers](https://kotlinlang.org/docs/coroutine-context-and-dispatchers.html)、[Cancellation and timeouts](https://kotlinlang.org/docs/cancellation-and-timeouts.html)、[Asynchronous Flow](https://kotlinlang.org/docs/flow.html)。

## 9. 本章总结

协程进阶的核心是三件事：用对调度器让「UI、IO、CPU」各归其位，用结构化并发让「任务随作用域生、随作用域灭」，用 Flow 操作符把数据管道拼得干净利落。还需分别确认取消是否被检查、异常由谁处理，以及实际工作运行在哪个上下文；这些检查用于降低泄漏与崩溃风险。
