# 第 25 章：SideEffect、LaunchedEffect 与普通业务代码的边界

## 1. 本章目标

先看三个具体的页面需求：进页面就要开始倒计时；面板出现时要注册某个外部监听、消失时必须注销；把主题的明暗同步到系统的状态栏图标颜色。这三件事都不是「描述 UI」——它们是对外部世界产生影响的副作用，写在 Composable 函数体里会在重组时重复执行、且没人负责取消。本章讲清副作用 API 为什么存在，以及系统同步、进入页面任务、用户点击协程应分别放在哪里。

理解 Compose 副作用 API 为什么存在，以及系统同步、进入页面任务、用户点击协程应分别放在哪里。

学完后，你应当能够：

- 理解 SideEffect 在成功提交组合后执行
- 理解 LaunchedEffect 的 key 与取消重启
- 理解 DisposableEffect 的清理逻辑 dispose
- 区分事件协程（点击一次做一次）和组合驱动协程（UI 出现就运行）
- 避免在函数体直接修改外部系统
- 说出「在 Composable 函数体直接发网络请求」为什么危险

## 2. 项目中的位置

- `ui/theme/Theme.kt`
- `components/SnackbarDemo.kt`
- `components/SideEffectDemo.kt`

Theme.kt 使用 `SideEffect` 同步系统栏；SideEffectDemo.kt 完整演示了 `LaunchedEffect`、`DisposableEffect` 与 `rememberCoroutineScope`（与组合同生命周期、供事件回调启动协程的作用域，第 49 章详解）三种副作用。

## 3. 项目源码片段

### 3.1 项目节选

```kotlin
// ui/theme/Theme.kt：把 Compose 的 darkTheme 同步到 Android Window
SideEffect {
    val window = (view.context as Activity).window
    WindowCompat.getInsetsController(window, view)
        .isAppearanceLightStatusBars = !darkTheme
}

// components/SideEffectDemo.kt：key 驱动的协程与清理回调
LaunchedEffect(timerOn) {          // timerOn 变化时取消旧协程并重启新协程
    if (timerOn) {
        while (true) {
            delay(1000)            // 挂起 1 秒，不阻塞线程
            seconds++              // 写状态，触发重组
        }
    }
}

if (panelVisible) {
    DisposableEffect(Unit) {       // 进入组合时执行一次
        logs.add("进入组合：注册监听器")
        onDispose {                // 离开组合时执行清理
            logs.add("离开组合：onDispose 执行清理")
        }
    }
}

// 事件回调里启动协程：回调不是 suspend 环境
onClick = {
    scope.launch {                 // scope = rememberCoroutineScope()
        delay(1500)
        taskResult = "任务完成"
    }
}
```

项目节选，依赖 Theme.kt / SideEffectDemo.kt 里的 view、timerOn、seconds、logs、scope 等上下文，不可直接复制。下面给三个不依赖项目符号、可直接复制运行的最小示例。

### 3.2 LaunchedEffect：进入组合 / key 变化时启动协程

<!-- compile decls -->
```kotlin
@Composable
fun TimerDemo() {
    var timerOn by remember { mutableStateOf(false) }
    var seconds by remember { mutableIntStateOf(0) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Switch(checked = timerOn, onCheckedChange = { timerOn = it })
        Text(if (timerOn) "计时中：$seconds 秒" else "已暂停：$seconds 秒")
    }

    // key 是 timerOn：开关变化 → 旧协程取消、新协程重启
    LaunchedEffect(timerOn) {
        if (timerOn) {
            while (true) {
                delay(1000)   // 挂起 1 秒，不阻塞线程
                seconds++     // 写状态，触发重组
            }
        }
    }
}
```

初始界面：关闭的开关 + 「已暂停：0 秒」。操作：拨开开关等 3 秒，再拨回。预期观察：秒数每秒加一；拨回后停止累加，数值保留（不清零）。

### 3.3 DisposableEffect：注册与清理成对出现

<!-- compile decls -->
```kotlin
@Composable
fun PanelLifecycleDemo() {
    var panelVisible by remember { mutableStateOf(false) }
    val logs = remember { mutableStateListOf<String>() }   // 可观察列表：add 后界面自动更新

    Column {
        Button(onClick = { panelVisible = !panelVisible }) {
            Text(if (panelVisible) "移除面板" else "显示面板")
        }
        if (panelVisible) {
            DisposableEffect(Unit) {
                logs.add("进入组合：注册监听器")
                onDispose { logs.add("离开组合：onDispose 清理") }
            }
            Text("面板内容")
        }
        logs.forEach { log -> Text(log) }
    }
}
```

初始界面：「显示面板」按钮。操作：点一下显示面板，再点一下移除。预期观察：日志区依次出现「进入组合：注册监听器」「离开组合：onDispose 清理」——注册与清理严格成对，对应 Flutter 的 `dispose()`。

### 3.4 事件回调：rememberCoroutineScope + launch

<!-- compile decls -->
```kotlin
@Composable
fun ClickTaskDemo() {
    var taskResult by remember { mutableStateOf("未开始") }
    val scope = rememberCoroutineScope()   // 与组合同生命周期的作用域

    Column {
        Button(onClick = {
            taskResult = "任务进行中…"
            scope.launch {                 // 点击一次，启动一次
                delay(1500)
                taskResult = "任务完成"
            }
        }) { Text("开始 1.5 秒任务") }
        Text(taskResult)
    }
}
```

初始界面：按钮 + 「未开始」。操作：点一次按钮。预期观察：文字先变「任务进行中…」，1.5 秒后变「任务完成」；连点几次就同时有几个协程在跑。

### 3.5 错误示例：在函数体直接发网络请求

错误示例（故意错误，不参与编译；User 与 loadUser 为假想符号）：

```kotlin
@Composable
fun BadUserProfile(userId: String) {
    var user by remember { mutableStateOf<User?>(null) }

    loadUser(userId)   // 危险：每次重组都可能重复执行，且无法取消

    Text(user?.name ?: "加载中")
}
```

危险点有三个：**每次重组都可能重复执行**（userId 一变、本函数读取的其他状态一变，请求就再来一遍）；**无法取消**（旧请求的响应可能在页面销毁后才回来，写进已失效的状态）；**执行时机不受控**（组合过程中的函数调用不代表界面一定应用，详见第 22 章）。正确写法见 4.2：进入页面加载数据用 LaunchedEffect。

## 4. 逐步解释

Theme.kt 要把 Compose 的 darkTheme 同步到 Android Window，这会修改组合树外部对象，因此使用 SideEffect：只有组合成功提交后才执行同步。

LaunchedEffect(key) 用于启动与组合节点绑定的协程。进入组合时启动，key 改变时取消旧协程并重启，离开组合时取消。`SideEffectDemo.kt` 的计时器就是标准写法：`LaunchedEffect(timerOn)` 中用 `while (true) { delay(1000); seconds++ }` 每秒写一次状态，绝不能用 `Thread.sleep` 阻塞线程；拨动开关让 `timerOn` 变化时，旧协程被取消、新协程重启。

DisposableEffect 适合「注册了就必须手动清理」的场景，它要求写 `onDispose` 回调：示例用 `if (panelVisible)` 把面板放进 / 移出组合，进入时记录一条日志，离开时由 `onDispose` 追加清理日志，对应 Flutter 的 `dispose()`。

先补几句 Kotlin 协程语法：`scope.launch { ... }` 表示在 scope 这个协程作用域里启动一段并发执行的代码，launch 立即返回、花括号里的代码在别处继续跑；花括号里可以调用 `delay` 这样的**挂起函数**——挂起时让出线程而不阻塞它。协程系统在第 29 章与第 73 章展开。

### 4.1 三个副作用 API 的定位

| API | 执行时机 | 取消 / 清理 | 典型用途 |
| --- | --- | --- | --- |
| `SideEffect { }` | 每次成功重组提交后 | 无（不能取消，也不该长跑） | 把 Compose 状态同步到非 Compose 世界：系统栏、分析埋点 |
| `LaunchedEffect(key) { }` | 进入组合或 key 变化时启动协程 | key 变化或离开组合时取消 | 进入页面加载、轮询计时等 suspend 任务 |
| `DisposableEffect(key) { onDispose { } }` | 进入组合或 key 变化时执行 | key 变化或离开组合时执行 onDispose | 注册 / 注销监听器等非协程资源 |

SideEffect 额外注意两点：它在**每次**成功重组后都会执行（不只是第一次），所以块里应当是幂等的「读出状态、同步出去」；不要在里面写回 Compose 状态——写了自己读的状态，同步完又触发重组、重组后又执行 SideEffect，形成循环。

### 4.2 「点击一次做一件事」与「UI 出现就运行」的对照

| 需求 | 正确做法 | 原因 |
| --- | --- | --- |
| 点击一次执行一次任务（延迟任务、弹 Snackbar） | 事件回调里 `rememberCoroutineScope().launch` | 点击回调不是 suspend 环境；协程次数由点击次数决定 |
| UI 出现在屏幕上就开始（进页面加载、计时轮询） | `LaunchedEffect(key)` | 与组合生命周期绑定，key 变化或离开组合自动取消 |
| 每次重组后把最新状态同步给非 Compose 世界（系统栏、埋点） | `SideEffect` | 只想读出状态，不启动协程 |
| 注册 / 注销外部资源 | `DisposableEffect` + `onDispose` | 注册与清理必须成对出现 |

事件驱动型的协程要用 `rememberCoroutineScope().launch`：`rememberCoroutineScope()` 返回一个与当前组合同生命周期的协程作用域，专门用于在 `onClick` 这类不是 suspend 环境的回调里启动协程（第 49 章详解）；Snackbar 的 `showSnackbar`（`SnackbarHostState` 上用于显示一条 Snackbar、会挂起到提示消失的挂起函数，第 29 章详解）是用户点击后发生的挂起函数，而点击回调不是 suspend 环境；`SideEffectDemo.kt` 的「1.5 秒延迟任务」同理——`LaunchedEffect` 没有 `onClick` 这类入口，不能直接接收点击回调，只在进入组合 / key 变化时自动执行。要区分两种情况：点击若改变的是它的 key（例如小节一的开关点击写入 `timerOn`），确实会间接触发一次「取消旧协程 + 启动新协程」的重启，但那本质上仍是「状态变化驱动」，并不是把这次点击当作事件交给协程；要表达「点一次做一件事」这种事件语义，仍应自己取作用域再 launch。该作用域离开组合时其中的协程会自动取消。

### 4.3 key 变化与离开组合：事件追踪

以 TimerDemo 的 `LaunchedEffect(timerOn)` 为例，从进入页面到离开页面的完整编号步骤：

| 步骤 | 发生的事 |
| --- | --- |
| 1 | 首次组合：LaunchedEffect(false) 启动协程；因 timerOn 为 false，协程体内 if 不成立，立即执行结束 |
| 2 | 用户拨开开关：timerOn 被写入 true |
| 3 | 读取过 timerOn 的重组范围失效并重组：文字换成「计时中：0 秒」 |
| 4 | 框架比对 key：false → true 发生变化，先取消旧协程（本例早已结束），再启动新协程 |
| 5 | 新协程进入 while 循环：delay(1000) 挂起 → seconds++ 写状态 → 每秒一轮 |
| 6 | 用户拨回开关：key true → false，正在 delay 的协程被取消，seconds 停在上次的值（remember 保留，不清零） |
| 7 | 若整个 TimerDemo 离开组合（如导航到别的页面）：协程同样被取消，remember 的状态随之丢弃 |

DisposableEffect 的 key 与离开组合走同一套规则，只是把「取消协程」换成「执行 onDispose」。另外，协程取消是协作式的：`delay` 这类挂起函数会响应取消；`Thread.sleep` 不会，这正是「在 LaunchedEffect 里用 Thread.sleep 会卡死 UI」的原因。

网络请求不能直接写在 Composable 函数体，否则重组可能重复执行（3.5 的反例）；正确入口是 `LaunchedEffect(userId) { user = repository.load(userId) }` 这类按 key 驱动的写法，进阶架构里则交给 ViewModel（第 68 章）。

## 5. 实际执行过程

1. Composable 计算 UI
2. 成功提交组合
3. SideEffect 同步外部对象（每次成功重组后都会执行）
4. LaunchedEffect 按 key 启动协程
5. DisposableEffect 在节点进入组合时注册、离开时执行 onDispose
6. key 变化或节点离开时旧协程取消、onDispose 执行

以 TimerDemo 为例的一帧级追踪：拨开关 → 写 timerOn → 重组（文字更新）→ key 变化 → 旧协程取消、新协程启动 → delay 挂起 → 秒数 +1 写状态 → 下一轮重组更新文字。

## 6. Flutter / Dart 3 对照

Flutter 常把一次性初始化放 initState，依赖变化放 didUpdateWidget，绘制后动作放 addPostFrameCallback。Compose 用不同 Effect API 显式声明依赖。

```dart
@override
void initState() {
  super.initState();
  Future.microtask(() => loadUser(widget.userId));
}

@override
void didUpdateWidget(covariant UserPage oldWidget) {
  super.didUpdateWidget(oldWidget);
  if (oldWidget.userId != widget.userId) loadUser(widget.userId);
}
```

近似对应（概念对照，不是机制等价）：`LaunchedEffect(key)` ≈ initState + didUpdateWidget 中「参数变化才重跑」的组合；`DisposableEffect` ≈ initState + dispose；`SideEffect` 没有完全对应的钩子——addPostFrameCallback 注册的是「下一帧绘制后」，而 SideEffect 语义是「每次成功重组提交后」，执行频率与含义都不同。

## 7. 常见错误

反例详解（错误现象 → 原因 → 修正）：

- **LaunchedEffect 使用随机 key**。现象：协程不停重启，计时永远到不了 1 秒、请求反复发起。原因：key 每次重组都是新值，框架判定「key 变了」就不停取消重启。修正：key 用真正表达「何时重跑」的状态（timerOn、userId），固定不变的用 Unit。
- 在函数体直接发请求或修改 Window（3.5 的反例）：重组重复执行、无法取消
- 在 LaunchedEffect 里用 `Thread.sleep` 阻塞线程，导致 UI 卡死（应该用 `delay`；取消是协作式的）
- 用 DisposableEffect 注册了监听器却漏写 `onDispose`，造成泄漏（漏写直接编译不过，风险是绕开它去用别的 API）
- 用 SideEffect 启动长期任务：它不可取消、每次重组后都执行
- 在 SideEffect 里写回自己读的状态：同步 → 重组 → 又同步，形成循环
- 点击事件通过额外状态绕到 LaunchedEffect（点击改 key 会间接重启协程，但它并不是事件处理器），平白增加复杂度

## 8. 本章检查题

1. Theme.kt 为什么使用 SideEffect？
2. LaunchedEffect 的 key 改变时发生什么？
3. Snackbar 点击为什么适合 rememberCoroutineScope？
4. 预测：TimerDemo 计时到 7 秒后拨回开关，再把开关拨开，文字从几秒开始继续累加？若把 LaunchedEffect 的 key 换成 Unit，拨动开关还会对计时产生任何效果吗？
5. 动手：把 ClickTaskDemo 的延迟从 1500 改成 3000，并在任务期间快速连点三次按钮，观察文字的最终变化时机；再给 onClick 加一个「防重复启动」的布尔状态（任务进行中时忽略点击）。

答案：

1. Theme.kt 要把 Compose 的 darkTheme 同步到组合树外部的 Android Window，SideEffect 保证只在组合成功提交后执行。
2. 取消旧协程并按新 key 重新启动。
3. showSnackbar 是挂起函数，而点击回调不是 suspend 环境；rememberCoroutineScope 提供与组合绑定的作用域，且组合离开时自动取消。
4. 从 7 秒继续——seconds 由 remember 保存，取消协程不重置状态。若 key 换成 Unit：协程只在首次进入组合时启动一次，当时 timerOn 是 false，if 分支没进、协程随即结束；此后拨动开关既不会取消也不会重启任何协程（key 没变），计时不工作，开关失效。
5. 要点：连点三次会有三个协程并发，每个都将在点击后 3 秒写「任务完成」，文字几乎同时定格一次；防重复启动可用 `var running by remember { mutableStateOf(false) }`，onClick 开头 `if (running) return@Button`，启动前置 true、完成或取消后置 false。

## 9. 本章总结

UI 描述应可重复执行。提交后同步用 SideEffect，组合生命周期协程用 LaunchedEffect，带清理的资源用 DisposableEffect，明确用户事件用 rememberCoroutineScope。判断口诀：**同步出去用 SideEffect，跟着界面跑用 LaunchedEffect，成对注册注销用 DisposableEffect，跟着点击跑用 rememberCoroutineScope**。

官方参考：

- [Side effects in Compose（LaunchedEffect / DisposableEffect / SideEffect 等）](https://developer.android.com/develop/ui/compose/side-effects)
- [Kotlin coroutines guide（协程与挂起函数）](https://kotlinlang.org/docs/coroutines-guide.html)
