# 第 49 章：rememberUpdatedState 与 rememberCoroutineScope

## 1. 本章目标

解决两个经典问题：长期协程/回调中捕获到过期值；在非组合环境中安全地启动协程。

学完后，你应当能够：

- 说出 LaunchedEffect(Unit) 捕获旧参数的原因
- 用 rememberUpdatedState 拿到最新值且不引入额外重组
- 理解 rememberCoroutineScope 与组合生命周期的绑定关系
- 避免 GlobalScope 泄漏和把 scope 存进 ViewModel

## 2. 项目中的位置

进阶篇不再局限于 compose_demo 项目，本章使用通用 Compose API。

涉及的内容包括：

- androidx.compose.runtime（LaunchedEffect、rememberUpdatedState、rememberCoroutineScope、remember）
- kotlinx.coroutines（CoroutineScope、launch、delay）

## 3. 项目源码片段

场景一：倒计时 10 秒后回调「最新」的 onFinished。LaunchedEffect(Unit) 只在首次组合启动一次，直接捕获 lambda 会拿到当时的旧引用。

```kotlin
@Composable
fun Countdown(
    seconds: Int = 10,
    onFinished: () -> Unit,
) {
    // 保存最新回调引用：重组时更新，且不触发额外重组
    val currentOnFinished by rememberUpdatedState(onFinished)

    LaunchedEffect(Unit) {
        delay(seconds * 1000L)
        // 用最新引用，而不是最初捕获的旧 onFinished
        currentOnFinished()
    }
}
```

场景二：在 Button 的 onClick 中启动协程加载数据。onClick 不是组合上下文，没有组合作用域可用。

```kotlin
@Composable
fun LoadButton(viewModel: MyViewModel) {
    val scope = rememberCoroutineScope() // 与组合绑定，离开组合自动取消

    Button(onClick = {
        scope.launch {
            val data = viewModel.fetchData() // 挂起操作
            viewModel.updateUi(data)
        }
    }) {
        Text("加载数据")
    }
}
```

## 4. 逐步解释

先看问题的根源：`LaunchedEffect(Unit)` 的协程在首次组合时启动一次，key 为 Unit 意味着后续重组不会重启它。协程体是那时编译生成的闭包，里面直接引用 `onFinished` 参数——闭包捕获的是「启动那一刻」的引用。如果父组件在倒计时期间换了回调（比如页面翻页后回调指向新逻辑），协程里拿到的仍是旧引用，调用的还是老逻辑。

`rememberUpdatedState(onFinished)` 解决方式很巧妙：它返回一个 State 对象，其值在每次重组时被更新为最新参数；协程里读 `currentOnFinished`（即 state.value），每次读取时拿到的都是最新值。关键点有两个：一是「重组时更新」，不需要任何手动同步；二是「不触发额外重组」——更新值是直接写内部字段，不像 mutableStateOf 那样通知读取者，所以不会因为换回调而让 Countdown 多重组一次。记住：它是「随时可读的最新值」容器，不是普通的可观察状态。

再看 `rememberCoroutineScope`：Compose 里协程需要作用域。LaunchedEffect 自带组合作用域，但 onClick、事件回调、非组合上下文没有。`rememberCoroutineScope()` 返回一个与当前组合位置绑定的 CoroutineScope：组合销毁（离开组合树）时自动 cancel，杜绝协程泄漏。与 `remember` 的区别：remember 保存任意对象且不提供生命周期取消；rememberCoroutineScope 专为协程设计，附带自动取消语义。

「不要在 ViewModel 里存 scope」是重要红线：ViewModel 的 scope 生命周期比组合长，存进去会让协程在组合销毁后继续运行，访问已销毁的 UI 状态；反过来，也不要把组合作用域传给 ViewModel 用。原则是：谁拥有生命周期，谁提供作用域——UI 协程用 rememberCoroutineScope，数据层协程用 ViewModel 自己的 scope（viewModelScope）。

## 5. 实际执行过程

1. 首次组合：Countdown 执行，rememberUpdatedState 创建 State 并写入 onFinished 当前引用；LaunchedEffect(Unit) 启动协程并挂起在 delay
2. 父组件重组，传入新的 onFinished 引用
3. Countdown 重组，rememberUpdatedState 内部值更新为最新引用（无额外重组通知）
4. 10 秒后 delay 返回，协程恢复执行，读取 currentOnFinished 拿到最新引用并调用
5. 若此时 Countdown 已离开组合，协程随组合取消，不会调用已销毁的回调
6. 点击按钮时：scope.launch 启动加载协程，挂起期间若页面退出，scope 自动取消，加载协程一并取消

## 6. Flutter / Dart 3 对照

先讲相似点：Flutter 中 StatefulWidget 的 State 对象存活于整个组件生命周期，`widget.onFinished` 每次 build 时都会被赋成最新引用，所以回调天然是新的；Compose 因为 lambda 捕获发生在组合时点，才需要 rememberUpdatedState 兜底：

```dart
class Countdown extends StatefulWidget {
  const Countdown({super.key, required this.onFinished});

  final VoidCallback onFinished;

  @override
  State<Countdown> createState() => _CountdownState();
}

class _CountdownState extends State<Countdown> {
  Timer? _timer;

  @override
  void initState() {
    super.initState();
    _timer = Timer(const Duration(seconds: 10), () {
      // widget.onFinished 在 build 时更新，此处始终是最新引用
      // 但若组件已销毁，必须用 mounted 检查避免崩溃
      if (mounted) widget.onFinished();
    });
  }

  @override
  void dispose() {
    _timer?.cancel();
    super.dispose();
  }
}
```

本质区别：Flutter 靠「State 对象持有最新 widget 引用」这一天然机制解决过期值，代价是必须手动管理 Timer 的取消与 `mounted` 检查；Compose 把这两件事声明式化——rememberUpdatedState 负责最新值，rememberCoroutineScope/LaunchedEffect 负责生命周期取消，开发者少写样板代码，但必须理解「捕获发生在组合时」这个前提。

## 7. 常见错误

1. `LaunchedEffect(Unit)` 里直接使用参数 `onFinished`，倒计时期间换了回调却调用旧逻辑——没有用 rememberUpdatedState
2. 用 `GlobalScope.launch` 启动协程：不随组合取消，页面销毁后协程继续跑，访问已销毁状态导致崩溃或泄漏
3. 把 rememberCoroutineScope 存进 ViewModel 或单例对象：作用域与组合绑定，脱离组合后语义失效，等于埋雷
4. 混淆 rememberUpdatedState 与 mutableStateOf：前者更新不通知读取者，后者会触发重组
5. 在事件回调里直接调 `scope.launch` 却不考虑组件是否还在组合中——应依赖 scope 的自动取消，而不是手动判断

## 8. 本章检查题

1. rememberUpdatedState 的值在什么时候更新？更新会触发重组吗？
2. rememberCoroutineScope 的生命周期是怎样的？
3. 为什么直接捕获 onFinished 会拿到过期值？
4. 为什么不能把组合作用域存进 ViewModel？

答案：

1. 每次组合（重组）时更新为最新参数；更新不触发额外重组
2. 与创建它的组合位置绑定，组合销毁时自动 cancel，协程随之取消
3. LaunchedEffect(Unit) 只在首次组合启动一次，闭包捕获的是启动那一刻的参数引用，之后参数变化不会更新闭包内引用
4. ViewModel 生命周期比组合长，作用域存进去后组合销毁仍可运行，且作用域脱离了它绑定的组合语义

## 9. 本章总结

长期协程中的过期值用 rememberUpdatedState 解决，UI 协程作用域用 rememberCoroutineScope 提供。两条原则：最新值随时可读，作用域随组合销毁而取消。
