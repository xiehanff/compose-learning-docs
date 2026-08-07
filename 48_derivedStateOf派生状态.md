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

典型场景：列表滚动超过第一项时显示「回到顶部」按钮。滚动偏移每秒变化几十次，但按钮显示与否只取决于「是否在第一项」这个低频事实。

```kotlin
val listState = rememberLazyListState()

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

对比错误/低效写法：直接在组合中读取原始状态做判断。

```kotlin
// 每次 firstVisibleItemIndex 变化（每滚一格）都会触发重组，
// 即使结果 true/false 根本没变
val show = listState.firstVisibleItemIndex > 0
```

## 4. 逐步解释

`derivedStateOf { ... }` 的核心机制是：它的 lambda 会在组合期间执行，记录其中读取的所有 State 作为依赖；当这些依赖变化时，它重新计算 lambda，并把新结果与旧结果做 equals 比较——**只有结果变化时才通知读取者**，结果没变就保持沉默。所以滚动从第 3 项滚到第 5 项时，`firstVisibleItemIndex` 变了 2 次，但 `> 0` 的结果始终是 true，读取方不会收到任何通知，也就不会重组。

「回到顶部按钮」是教科书级场景：高频输入（滚动偏移）、低频输出（布尔值），中间有一层明显的降频边界。其他类似场景：拖动进度条时只在「拖完松开」时更新落点文本；聊天列表只在「是否处于底部」这个边界变化时更新悬浮提示。

与「组合中直接计算」的区别：直接写 `listState.firstVisibleItemIndex > 0`，组合期间每次该 State 变化都会重新执行这段代码并重组 UI，无论结果是否变化；derivedStateOf 缓存了结果并做相等性过滤，等于在状态与 UI 之间加了一层「带缓存的计算节点」。注意它必须在组合期间创建（配合 remember），lambda 中只能读 State——读普通变量不会建立依赖，永远不触发重算。

与 LaunchedEffect+状态的区别：derivedStateOf 是纯计算，无副作用，结果可被缓存和比较；LaunchedEffect 用于执行副作用（网络请求、日志），每次触发都是一次全新执行。把网络请求写进 derivedStateOf 是典型错误——它可能在任意重组时机被调用多次，且不应有副作用。

记住：derivedStateOf 是「把高频状态变成低频派生结果」的缓存节点，不是万能优化。简单的布尔/数值派生，如果输入本身低频，直接算即可；过度使用反而增加 slot 开销。

## 5. 实际执行过程

1. 首次组合：remember 创建 derivedStateOf，lambda 首次执行，读到 firstVisibleItemIndex = 0，缓存结果 false
2. 用户滚动列表：LazyListState 内部状态高频更新（每帧/每次滚格）
3. 依赖变化触发 derivedStateOf 重算：lambda 再次执行，结果为 true
4. 与缓存值 false 比较：不相等，通知读取者 showBackToTop
5. UI 重组，显示回到顶部按钮
6. 继续滚动：结果仍是 true，每次比较都相等，不再通知，按钮区不再重组

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

本质区别：Flutter 里「监听 → 阈值判断 → 手动 setState」三步全部由开发者手写，容易遗漏（忘记判断 `show != _showBackToTop` 就会每帧重建）；Compose 的 derivedStateOf 把「监听 + 相等性过滤 + 通知」封装成一个声明式节点，开发者只写「结果怎么算」，其余交给运行时。可以理解为：derivedStateOf = 带缓存与相等性过滤的计算，Flutter 需要你手动实现这三件事。

## 7. 常见错误

1. 在 derivedStateOf 的 lambda 里做副作用（网络请求、写日志、改其他状态）——它是纯计算节点，可能在任何重组时机执行
2. lambda 里读普通变量而不是 State：没有建立依赖，结果永远不会重算
3. 过度使用：简单派生直接 `remember + mutableStateOf` 或直接计算即可，不要给每个布尔值套 derivedStateOf
4. 在 lambda 中调用 Composable：derivedStateOf 不是组合上下文，内部不能调用 @Composable 函数
5. 忘记 remember 包裹：每次重组都新建 derivedStateOf，缓存与依赖追踪全部失效

## 8. 本章检查题

1. derivedStateOf 的适用场景是什么？
2. 依赖变化后，结果每次都通知 UI 吗？
3. 与「组合中直接计算」相比，derivedStateOf 多做了什么？
4. 为什么不能在 derivedStateOf 里做副作用？

答案：

1. 输入状态高频变化、派生结果低频变化（变化有边界）的场景，如滚动位置 → 是否显示按钮
2. 不一定。重算后结果与上次相等就不通知；只有结果变化才通知读取者
3. 缓存计算结果并做 equals 比较，结果不变时不触发下游重组
4. 因为它可能在任意重组时机被多次执行，且属于纯计算节点，副作用应放在 LaunchedEffect 等副作用作用域

## 9. 本章总结

derivedStateOf 在「高频状态 → 低频结果」之间建立带缓存的派生节点，用相等性比较过滤掉无意义的重组。它是声明式降频的标准工具，但只用于真正有边界变化的地方。
