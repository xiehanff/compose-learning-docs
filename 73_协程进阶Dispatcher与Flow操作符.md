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

## 4. 逐步解释

1. 三个 Dispatcher：Main 是 Android 主线程，UI 更新必须在这；IO 是专为磁盘 / 网络设计的弹性线程池，阻塞 IO 再多也不怕；Default 是 CPU 密集线程池（线程数约等于核数），适合 JSON 解析、排序、图片缩放这类重计算。挂起函数（如 Retrofit 的 suspend 接口、Room 的 suspend 查询）内部一般已切好线程，你真正要关心的是「谁在跑 CPU 重活」。

2. withContext 与 flowOn：`withContext(Dispatchers.Default) { ... }` 把一段代码临时切到指定线程池，执行完自动切回原上下文；`flowOn` 是 Flow 版的「上游切换」——它只影响之前的上游操作符，之后的代码回到原上下文。位置放错，线程就切错地方，这是 Flow 最常见的坑之一。

3. 结构化并发：`coroutineScope { }` 是一个「子任务容器」：它等所有子任务完成才返回；任何一个子任务失败，其余子任务全部取消、异常向上抛。`supervisorScope` 相反：子任务失败不影响兄弟任务，各自独立。**怎么选：一个结果依赖多个请求（并行合并）用 coroutineScope；多个互不相关的任务（一个失败不拖累别人）用 supervisorScope。** 永远不要用 GlobalScope 起协程——它没有父作业，页面销毁后没人取消它，泄漏和崩溃都从这里来。

4. 取消是协作的：协程不会因为 `cancel()` 就立刻停，只有协程代码「配合」才响应：调用 suspend 函数（内部检查取消），或主动检查 `isActive` / `ensureActive()`。死循环里没有任何挂起点，取消就无效——这就是「协作取消」。catch 捕获到 `CancellationException` 后必须重新抛出，否则取消被吞掉，协程继续跑。

5. Flow 操作符速记：map / filter 做转换过滤；catch 捕获上游异常，必须放在出错源头之后、collect 之前，放错位置捕获不到；retry 失败重试；flowOn 切上游线程；distinctUntilChanged 去重（避免重复数据触发重组）；conflate 丢弃来不及处理的值（只保留最新，适合高频事件）；combine 合并两个流的最新值（比如「登录状态 + 用户信息」合成一个 UI 状态）。冷流每次 collect 才执行、数据一次性（网络请求）；热流（SharedFlow / StateFlow）常驻、与收集者数量无关，UI 状态类数据用 StateFlow。

6. Compose 里的固定搭配：`LaunchedEffect(Unit)` 进入组合时启动协程、离开组合自动取消，生命周期与组合对齐；`viewModelScope` 随 ViewModel 销毁取消。页面数据加载、事件监听用 LaunchedEffect；只有「重组期间也要保持」的协程才用 rememberCoroutineScope。

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

本质区别：Dart 没有内置的「结构化并发作用域」——coroutineScope / supervisorScope 那种「父管子、子失败全取消」的语义要靠自己实现；Dart 里 unawaited 的 Future 出错是静默的（未处理异常），而 Kotlin 里结构化并发保证错误一定传播到作用域边界。所以 Flutter 里「页面销毁后还在跑的异步任务」要靠 widget 生命周期手动取消，Kotlin 则是 viewModelScope / LaunchedEffect 声明式搞定。

记住：Kotlin 的并发是「树状的」（作用域），Dart 的并发是「平铺的」（每个 Future 独立），前者错误不会漏，后者要自己兜底。

## 7. 常见错误

1. 用 GlobalScope 启动长期任务：不随页面销毁而取消，任务泄漏、回调打到已销毁的 UI 上直接崩。任何界面相关协程都用 viewModelScope / LaunchedEffect。
2. 在 Main 上解析大 JSON：主线程跑 CPU 重活 → 掉帧、卡顿甚至 ANR。要么用内部已切线程的 suspend 库，要么显式 `withContext(Dispatchers.Default)`。
3. catch 放错位置：catch 必须放在「可能抛异常的源头之后、collect 之前」的管道里。把 catch 写在 collect 之后、或单独用 try 包住 collect，异常在管道外抛出，永远接不住。
4. 混用 coroutineScope 与 supervisorScope：失败要连坐却用了 supervisor，两个请求各挂各的，返回残缺数据；互不相关却用了 coroutineScope，一个任务失败把兄弟任务也杀了。先问「失败要不要连坐」再选。
5. 忽略 flowOn 的位置：flowOn 只影响它之前的上游。把 flowOn 写在 collect 前想切 collect 的线程，是无效的。
6. catch 吞掉 CancellationException：取消被当成普通异常处理，协程不响应取消，任务继续在后台跑。

## 8. 本章检查题

1. Dispatchers.Main / IO / Default 各自的用途？
2. coroutineScope 与 supervisorScope 的区别？
3. flowOn 影响的是哪一侧？放错位置会怎样？
4. 为什么说协程取消是「协作」的？

答案：

1. Main：主线程，UI 更新必须在 Main；IO：磁盘 / 网络等阻塞型 IO 的弹性线程池；Default：CPU 密集计算的线程池（线程数约等于核数）。
2. coroutineScope 中任一子任务失败会取消全部子任务并把异常抛给外层；supervisorScope 中一个子任务失败不影响兄弟任务。按「失败要不要连坐」选择。
3. flowOn 影响它之前的上游操作符的执行线程，之后的下游回到原上下文；放错位置会导致重活在错误线程执行，或想切 collect 线程却无效。
4. cancel() 只发「取消信号」，协程要在挂起点（suspend 函数）或主动检查 isActive / ensureActive() 时才响应；不配合的代码（如无挂起点的死循环）取消无效。

## 9. 本章总结

协程进阶的核心是三件事：用对调度器让「UI、IO、CPU」各归其位，用结构化并发让「任务随作用域生、随作用域灭」，用 Flow 操作符把数据管道拼得干净利落。记住「取消是协作的、错误是传播的、线程是声明式的」这三句话，就不会写出泄漏与崩溃。
