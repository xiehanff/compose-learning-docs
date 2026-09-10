# 第 25 章：SideEffect、LaunchedEffect 与普通业务代码的边界

## 1. 本章目标

理解 Compose 副作用 API 为什么存在，以及系统同步、进入页面任务、用户点击协程应分别放在哪里。

学完后，你应当能够：

- 理解 SideEffect 在成功提交组合后执行
- 理解 LaunchedEffect 的 key 与取消重启
- 区分事件协程和组合驱动协程
- 避免在函数体直接修改外部系统

## 2. 项目中的位置

- `ui/theme/Theme.kt`
- `components/SnackbarDemo.kt`
- `components/SideEffectDemo.kt`

Theme.kt 使用 `SideEffect` 同步系统栏；SideEffectDemo.kt 完整演示了 `LaunchedEffect`、`DisposableEffect` 与 `rememberCoroutineScope` 三种副作用。

## 3. 项目源码片段

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

## 4. 逐步解释

Theme.kt 要把 Compose 的 darkTheme 同步到 Android Window，这会修改组合树外部对象，因此使用 SideEffect：只有组合成功提交后才执行同步。

LaunchedEffect(key) 用于启动与组合节点绑定的协程。进入组合时启动，key 改变时取消旧协程并重启，离开组合时取消。`SideEffectDemo.kt` 的计时器就是标准写法：`LaunchedEffect(timerOn)` 中用 `while (true) { delay(1000); seconds++ }` 每秒写一次状态，绝不能用 `Thread.sleep` 阻塞线程；拨动开关让 `timerOn` 变化时，旧协程被取消、新协程重启。

DisposableEffect 适合「注册了就必须手动清理」的场景，它要求写 `onDispose` 回调：示例用 `if (panelVisible)` 把面板放进 / 移出组合，进入时记录一条日志，离开时由 `onDispose` 追加清理日志，对应 Flutter 的 `dispose()`。

事件驱动型的协程要用 `rememberCoroutineScope().launch`：Snackbar 的 `showSnackbar` 是用户点击后发生的挂起函数，而点击回调不是 suspend 环境；`SideEffectDemo.kt` 的「1.5 秒延迟任务」同理——`LaunchedEffect` 没有 `onClick` 这类入口，不能直接接收点击回调，只在进入组合 / key 变化时自动执行。要区分两种情况：点击若改变的是它的 key（例如小节一的开关点击写入 `timerOn`），确实会间接触发一次「取消旧协程 + 启动新协程」的重启，但那本质上仍是「状态变化驱动」，并不是把这次点击当作事件交给协程；要表达「点一次做一件事」这种事件语义，仍应自己取作用域再 launch。该作用域与组合同生命周期，离开组合时其中的协程自动取消。

网络请求不能直接写在 Composable 函数体，否则重组可能重复执行。

## 5. 实际执行过程

1. Composable 计算 UI
2. 成功提交组合
3. SideEffect 同步外部对象
4. LaunchedEffect 按 key 启动协程
5. DisposableEffect 在节点进入组合时注册、离开时执行 onDispose
6. key 变化或节点离开时旧协程取消

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

## 7. 常见错误

- 在函数体直接发请求或修改 Window
- LaunchedEffect 使用随机 key，协程不停重启
- 在 LaunchedEffect 里用 `Thread.sleep` 阻塞线程，导致 UI 卡死（应该用 `delay`）
- 用 DisposableEffect 注册了监听器却漏写 `onDispose`，造成泄漏
- 用 SideEffect 启动长期任务
- 点击事件通过额外状态绕到 LaunchedEffect（点击改 key 会间接重启协程，但它并不是事件处理器），平白增加复杂度

## 8. 本章检查题

1. Theme.kt 为什么使用 SideEffect？
2. LaunchedEffect 的 key 改变时发生什么？
3. Snackbar 点击为什么适合 rememberCoroutineScope？

答案：

1. Theme.kt 要把 Compose 的 darkTheme 同步到组合树外部的 Android Window，SideEffect 保证只在组合成功提交后执行。
2. 取消旧协程并按新 key 重新启动。
3. showSnackbar 是挂起函数，而点击回调不是 suspend 环境；rememberCoroutineScope 提供与组合绑定的作用域，且组合离开时自动取消。

## 9. 本章总结

UI 描述应可重复执行。提交后同步用 SideEffect，组合生命周期协程用 LaunchedEffect，明确用户事件可用 rememberCoroutineScope。
