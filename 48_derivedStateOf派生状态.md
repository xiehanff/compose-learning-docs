# 第 48 章：derivedStateOf：派生状态

## 1. 本章目标

掌握把高频变化的状态「降频」为低频消费结果的标准做法，避免无谓重组。

学完后，你应当能够：

- 说出 derivedStateOf 的适用场景与工作原理
- 写出「滚动位置 → 是否显示回到顶部按钮」的派生状态
- 区分 derivedStateOf 与「组合中直接计算」的性能差异
- 知道什么时候不该用 derivedStateOf

## 2. 项目中的位置

进阶篇不再局限于 compose_demo 项目，本章使用通用 Compose API。

涉及的内容包括：

- androidx.compose.runtime（derivedStateOf、remember、mutableStateOf）
- androidx.compose.foundation.lazy（LazyColumn、rememberLazyListState）

## 3. 项目源码片段

典型场景：列表滚动过第一项时显示「回到顶部」按钮。`firstVisibleItemIndex` 随滚动不断变化（1、2、3……），但按钮显示与否只取决于「是否跨过 0」这个低频边界。

```kotlin
val listState = rememberLazyListState()
val scope = rememberCoroutineScope() // 点击回调需要启动滚动协程；协程见第 29 章

// 派生状态：只在 firstVisibleItemIndex 跨过 0 这个边界时才更新
val showBackToTop by remember {
    derivedStateOf { listState.firstVisibleItemIndex > 0 }
}

LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
    items(1000) { index -> Text("条目 $index") }
}

if (showBackToTop) {
    FloatingActionButton(onClick = { scope.launch { listState.scrollToItem(0) } }) {
        Text("回到顶部")
    }
}
```

对比低效写法：直接在组合中读取原始状态做判断。

```kotlin
// firstVisibleItemIndex 每变一次（每滚过一项）都会让读取处重组，
// 即使 true/false 这个结果根本没变
val show = listState.firstVisibleItemIndex > 0
```

## 4. 逐步解释

`derivedStateOf { ... }` 的核心机制是：它的 lambda 会被执行一次并把其中读取的所有快照状态（State）记录为依赖；当依赖变化时重新计算，并把新结果与旧结果做相等性比较——**结果相同就不更新**，官方文档明确把它类比成 `Flow.distinctUntilChanged()`：输入可以高频变化，输出只在真正变化时更新。所以滚动从第 3 项滚到第 5 项时，`firstVisibleItemIndex` 变了 2 次，但 `> 0` 的结果始终是 true，读取方不会看到任何更新，也就不会为此重组。

「回到顶部按钮」是官方文档的推荐场景：输入高频、输出低频、中间有一层明显的阈值边界。要区分两个层次的「高频」：像素级的滚动偏移（`scrollOffset`）每帧都在变，是最高频的输入；`firstVisibleItemIndex` 已经比偏移低频，但直接在组合里读它，每滚过一项仍会触发一次重组。derivedStateOf 的价值是把「变化更频繁的输入」收敛为「更新次数更少的输出」。另外注意它只做**值域上的过滤**（结果不变就不更新），不做时间维度上的防抖——「拖完松开才触发」这类时序需求需要 debounce 或手势结束回调，derivedStateOf 帮不上忙。

两个常见认知误区先纠正：

第一，`derivedStateOf` 是**普通函数，不是 @Composable**。它可以在组合外创建（例如在自定义 StateHolder 类里 `val result by derivedStateOf { ... }`），也可以在组合内使用；组合内通常配合 `remember` 只是为了**跨重组保留同一个实例**，让依赖记录与结果缓存不因每次重组新建而失效，不是「必须组合内创建」。

第二，lambda 里读普通变量不违规——首次也能算出结果——但**不会建立依赖**：普通值变化不会触发重算，也不会主动反映到结果里。边界要说准：如果其他 State 依赖变化触发了 lambda 重算，重算时会读到该普通变量的**当前值**——这种更新只是「搭便车」，何时重算取决于别的依赖，不能指望它及时反映。如果派生逻辑依赖某个会变的捕获值，要么把它变成 State 供 lambda 读取，要么把它作为 `remember(key) { derivedStateOf(...) }` 的 key，key 变化时重建派生状态。

与「组合中直接计算」的区别：直接写 `listState.firstVisibleItemIndex > 0`，每次该状态变化都会让读取处重组，无论结果是否变化；derivedStateOf 在状态与 UI 之间加了一层「带缓存与相等性过滤的计算节点」。它与 `remember + mutableStateOf` 不是替代关系：后者需要你在某个时机手动写入新值，不是「派生」；派生意味着依赖变化自动重算。

与 LaunchedEffect+状态的区别：derivedStateOf 是纯计算，无副作用，结果可被缓存和比较；LaunchedEffect 用于执行副作用（网络请求、日志），每次触发都是一次全新执行。把网络请求写进 derivedStateOf 是典型错误——它可能在任意重组时机被调用多次，且不应有副作用。

最后记住适用边界（官方文档的原话大意）：「输入变化比 UI 需要更新的频率更高时」才用 derivedStateOf。把两个低频 State 拼成 `firstName + lastName` 这类结果与输入同频变化的派生，纯属白白加一层节点。

## 5. 实际执行过程

1. 首次组合：remember 创建并保留 derivedStateOf 实例，lambda 首次执行，读到 firstVisibleItemIndex = 0，缓存结果 false
2. 用户滚动列表：LazyListState 内部状态高频更新（偏移每帧变，索引每滚过一项变）
3. 依赖变化触发重算：lambda 再次执行，结果为 true
4. 与缓存值 false 比较：不相等，更新结果，读取 showBackToTop 的作用域重组
5. 继续滚动：索引 3→5，重算结果仍是 true，比较相等，不更新，按钮区不再重组
6. 组件离开组合：remember 连同 derivedStateOf 实例一起丢弃

## 6. Flutter / Dart 3 对照

先讲相似点：Flutter 也是「滚动位置 → UI 开关」的常见场景，需要手动监听滚动事件：

```dart
class ListPage extends StatefulWidget {
  const ListPage({super.key});

  @override
  State<ListPage> createState() => _ListPageState();
}

class _ListPageState extends State<ListPage> {
  final _controller = ScrollController();
  bool _showBackToTop = false;

  @override
  void initState() {
    super.initState();
    _controller.addListener(() {
      // 手动判断边界，只有结果变化时才 setState
      final show = _controller.offset > 0;
      if (show != _showBackToTop) {
        setState(() => _showBackToTop = show);
      }
    });
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }
}
```

本质区别：Flutter 里「监听 → 阈值判断 → 手动 setState（记得先比较结果有没有变）」三步全部由开发者手写，漏掉比较就会每帧重建；Compose 的 derivedStateOf 把「依赖追踪 + 缓存 + 相等性过滤 + 通知」封装成一个声明式节点，开发者只写「结果怎么算」，其余交给运行时。可以理解为：derivedStateOf ≈ 带依赖追踪的 distinctUntilChanged 计算节点，Flutter 需要你手动实现这几件事。

## 7. 常见错误

1. 在 derivedStateOf 的 lambda 里做副作用（网络请求、写日志、改其他状态）——它是纯计算节点，可能在任何重组时机执行
2. 忘记 remember 包裹：每次重组都新建 derivedStateOf 实例，跨重组的依赖记录与结果缓存无法复用（每次重组都要重新计算，带来额外开销）；不是依赖追踪机制整体失效——新实例内部依然会正确追踪依赖，只是实例从未被复用
3. lambda 里依赖捕获的普通变量却不处理：普通值不建立依赖；要么改成 State，要么作为 remember 的 key 在变化时重建
4. 过度使用：结果与输入同频变化的简单派生（拼接姓名、格式化低频文本）直接算即可；derivedStateOf 只在「输入高频、输出低频」时有价值
5. 在 lambda 中调用 Composable：derivedStateOf 不是组合上下文，内部不能调用 @Composable 函数
6. 把 derivedStateOf 当防抖用：它只过滤「结果没变」的情况，不提供时间维度的 debounce；时序需求用 Flow 的 debounce（第 50 章）或手势结束回调

## 8. 本章检查题

1. derivedStateOf 的适用场景是什么？
2. 依赖变化后，结果每次都更新 UI 吗？
3. derivedStateOf 必须在组合内创建吗？remember 在这里起什么作用？
4. 为什么不能在 derivedStateOf 里做副作用？

答案：

1. 输入状态变化频率高于 UI 需要更新频率（变化有阈值边界）的场景，如滚动位置 → 是否显示按钮；类似 Flow.distinctUntilChanged
2. 不一定。依赖变化触发重算，但结果与上次相等就不更新；只有结果变化才反映给读取者
3. 不是。它是普通函数，可在组合外（如 StateHolder 类）创建；组合内用 remember 是为了跨重组保留同一个实例，保住依赖记录与结果缓存
4. 因为它可能在任意重组时机被多次执行，且属于纯计算节点；副作用应放在 LaunchedEffect 等副作用作用域

## 9. 本章总结

derivedStateOf 在「高频状态 → 低频结果」之间建立带缓存与相等性过滤的派生节点，是声明式降频的标准工具，但只用于真正有阈值边界的地方。它是普通函数、组合内外皆可创建；组合内记得用 remember 保住实例。

官方参考：

- [Side-effects in Compose：derivedStateOf](https://developer.android.com/develop/ui/compose/side-effects#derivedStateOf)
- [性能最佳实践：Use derivedStateOf to limit recompositions](https://developer.android.com/develop/ui/compose/performance/bestpractices#use-derived-state)
- [State in Compose（remember 与 key）](https://developer.android.com/develop/ui/compose/state)
