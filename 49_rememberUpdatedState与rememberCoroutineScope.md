# 第 49 章：rememberUpdatedState 与 rememberCoroutineScope

## 1. 本章目标

解决两个经典问题：长期协程/回调中捕获到过期值；在非组合环境中安全地启动协程。

学完后，你应当能够：

- 说出 LaunchedEffect(Unit) 捕获旧参数的原因
- 用 rememberUpdatedState 拿到最新值，并理解它仍是可观察的 State
- 理解 rememberCoroutineScope 与组合生命周期的绑定关系
- 避免把组合作用域存进 ViewModel 造成的所有权错配

## 2. 项目中的位置

进阶篇不再局限于 compose_demo 项目，本章使用通用 Compose API。

涉及的内容包括：

- androidx.compose.runtime（LaunchedEffect、rememberUpdatedState、rememberCoroutineScope、remember）
- kotlinx.coroutines（CoroutineScope、launch、delay）

## 3. 项目源码片段

场景一：倒计时 `seconds` 秒后回调「最新」的 onFinished。key 选择很关键：`LaunchedEffect(seconds)` 让时长变化时重启倒计时，而回调引用变化不重启——回调走 rememberUpdatedState 取最新值。

<!-- compile decls -->
```kotlin
@Composable
fun Countdown(
    seconds: Int = 10,
    onFinished: () -> Unit,
) {
    // 记住一个 State：每次重组把值更新为最新的 onFinished
    val currentOnFinished by rememberUpdatedState(onFinished)

    // key 是 seconds：时长变化 → 重启倒计时；回调变化 → 不重启
    LaunchedEffect(seconds) {
        delay(seconds * 1000L)
        // 读的是 State 的当前值，而不是启动那一刻捕获的旧引用
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

先看问题的根源：`LaunchedEffect` 的协程体是启动那一刻生成的闭包，里面直接引用的参数会被捕获为「当时的值」。如果不写 rememberUpdatedState，倒计时期间父组件换了回调，协程里拿到的仍是旧引用；如果时长参数变了而 key 用 Unit，协程也不会按新时长重启。所以本例 key 用 `seconds`（时长是重启的正当理由），回调的「保鲜」交给 rememberUpdatedState。

`rememberUpdatedState(onFinished)` 的实现并不神秘：它 `remember` 一个 `mutableStateOf(onFinished)`，并在每次重组时把值更新为最新参数。三个关键点：

- **它是普通的可观察 State**。effect 里读 `currentOnFinished` 就是在读 `state.value`，与读其他 State 一样受快照系统追踪——如果这个读取发生在组合期间，值变化同样会让读取处失效。不要把它理解成「不通知的普通字段」。
- **参数更新不引入「额外」重组**。参数 `onFinished` 变化本身就是本函数重组的输入（父组件传新 lambda 进来，Countdown 必然重组）；State 的写入只是搭这次重组的便车，没有产生第二次重组。
- **长 effect 内读而不是捕获**。协程体捕获的是 State 对象（稳定引用），恢复执行时读到的才是最新值。这就是「最新值随时可读」的真正含义。

再看 `rememberCoroutineScope`：Compose 里协程需要作用域。LaunchedEffect 自带组合作用域，但 onClick、事件回调、非组合上下文没有。`rememberCoroutineScope()` 返回一个与当前组合位置绑定的 CoroutineScope：组合销毁（离开组合树）时自动 cancel，杜绝协程泄漏。与 `remember` 的区别：remember 保存任意对象且不提供生命周期取消；rememberCoroutineScope 专为协程设计，附带自动取消语义。注意它只在组合存活期间有效——组件离开组合后再次进入（如导航返回），作用域随新组合重新创建，之前的协程不会复活。

「不要把组合作用域存进 ViewModel」的真实后果要分清楚：存进去**并不会阻止取消**——组合销毁时这个 scope 照样被 cancel，它绑定的生命周期不因存放位置而改变。危害是所有权错配：ViewModel 生命周期比组合长，它持有一个「已取消的 scope」，之后用它 launch 的协程会立刻被取消而静默失败（难排查），并且延长了对已销毁组合对象的引用；组合重建后，旧 scope 与新组合也不匹配。原则是：谁拥有生命周期，谁提供作用域——UI 协程用 rememberCoroutineScope，数据层协程用 ViewModel 自己的 scope（viewModelScope），不要交叉存放。

## 5. 实际执行过程

1. 首次组合：Countdown 执行，rememberUpdatedState 创建 State 并写入 onFinished 当前引用；LaunchedEffect(seconds) 启动协程并挂起在 delay
2. 父组件重组，传入新的 onFinished 引用：Countdown 因参数变化重组，rememberUpdatedState 的值更新为最新引用（没有额外重组）
3. 若父组件改变 seconds：LaunchedEffect 的 key 变化，旧协程取消、按新时长重启倒计时
4. 10 秒后 delay 返回，协程恢复执行，读取 currentOnFinished 的当前值并调用
5. 若此时 Countdown 已离开组合：协程随 LaunchedEffect 取消，不会调用回调；组件重新进入组合时，一切从头开始（新 scope、新协程）
6. 点击按钮时：scope.launch 启动加载协程，挂起期间若页面退出，scope 自动取消，加载协程一并取消

## 6. Flutter / Dart 3 对照

先讲相似点：Flutter 中 StatefulWidget 的 State 对象存活于整个元素生命周期，`widget.onFinished` 始终指向最新的 widget 配置——但要注意这个「最新」是 framework 在父组件重建、生成新 widget 实例并挂载到 Element 时更新的，不是 build 方法赋的值。Compose 因为 lambda 捕获发生在组合时点，才需要 rememberUpdatedState 兜底：

```dart
class Countdown extends StatefulWidget {
  const Countdown({super.key, required this.onFinished, this.seconds = 10});

  final VoidCallback onFinished;
  final int seconds;

  @override
  State<Countdown> createState() => _CountdownState();
}

class _CountdownState extends State<Countdown> {
  Timer? _timer;

  @override
  void initState() {
    super.initState();
    _start();
  }

  @override
  void didUpdateWidget(Countdown oldWidget) {
    super.didUpdateWidget(oldWidget);
    // 时长变化 → 重启定时器（对应 LaunchedEffect(seconds) 的 key 语义）
    if (oldWidget.seconds != widget.seconds) {
      _start();
    }
    // 回调变化 → 不重启，下次触发自然读到新引用（对应 rememberUpdatedState）
  }

  void _start() {
    _timer?.cancel();
    _timer = Timer(Duration(seconds: widget.seconds), () {
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

本质区别：Flutter 靠「State 对象持有最新 widget 引用」这一机制解决过期值，代价是必须手动在 didUpdateWidget 里比较哪个配置变化需要重启逻辑、并手写 Timer 取消与 mounted 检查；Compose 把这几件事声明式化——LaunchedEffect 的 key 对应 didUpdateWidget 的比较职责，rememberUpdatedState 对应「回调始终最新」，rememberCoroutineScope/LaunchedEffect 对应 dispose 时的取消。开发者少写样板代码，但必须理解「捕获发生在组合时」这个前提。

## 7. 常见错误

1. `LaunchedEffect(Unit)` 里直接使用参数 `onFinished`，倒计时期间换了回调却调用旧逻辑——没有用 rememberUpdatedState
2. key 选择不当：要么永远用 Unit（时长变了不重启），要么把回调当 key（每次换回调都重启长任务）。key 应该是「值得重启 effect」的输入（如 seconds），其余要「保鲜」的引用走 rememberUpdatedState
3. 误解 rememberUpdatedState 为「不通知的私有字段」：它就是 mutableStateOf 包装的 State，effect 内读取同样被快照系统观察
4. 把 rememberCoroutineScope 存进 ViewModel 或单例对象：不会阻止 scope 在组合销毁时取消，但会让 ViewModel 持有已取消的 scope，之后 launch 立即失败且难以发现；还延长了对已销毁组合的引用
5. 用 `GlobalScope.launch` 启动协程：不随任何生命周期取消，页面销毁后协程继续跑，访问已销毁状态导致崩溃或泄漏

## 8. 本章检查题

1. rememberUpdatedState 的值在什么时候更新？更新会触发额外重组吗？
2. Countdown 场景里 LaunchedEffect 的 key 应该用什么？为什么？
3. 为什么直接捕获 onFinished 会拿到过期值？
4. 把 rememberCoroutineScope 的 scope 存进 ViewModel，会发生什么？

答案：

1. 每次重组时更新为最新参数。不会引入额外重组：参数变化本身就是本函数重组的输入，State 写入发生在这次重组中；但它是普通可观察 State，读取处照常受追踪
2. 用 seconds 这类「值得重启 effect」的输入：时长变化需要重新倒计时；回调引用走 rememberUpdatedState 保鲜，不该作为 key（否则换回调会重启长任务）
3. LaunchedEffect 协程体在启动时捕获参数值，之后参数变化不会更新闭包内的旧引用；读 State 的当前值而不是捕获参数值才能拿到最新
4. 不会阻止取消（组合销毁时 scope 照常 cancel）；危害是所有权错配——ViewModel 持有已取消的 scope，用它 launch 的协程静默失败，且引用延长了已销毁组合对象的生命周期

## 9. 本章总结

长期协程中的过期值用 rememberUpdatedState 解决（key 只放值得重启的输入），UI 协程作用域用 rememberCoroutineScope 提供。两条原则：最新值随时可读、读的是 State 而非捕获值；作用域跟着组合的生命周期走，不与长生命周期对象交叉存放。

官方参考：

- [Side-effects in Compose：rememberUpdatedState、rememberCoroutineScope](https://developer.android.com/develop/ui/compose/side-effects)
- [Composable lifecycle](https://developer.android.com/develop/ui/compose/lifecycle)
