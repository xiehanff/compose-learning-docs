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

项目实际使用 `SideEffect` 和事件协程，没有直接使用 `LaunchedEffect`。

## 3. 项目源码片段

```kotlin
SideEffect {
    val window = (view.context as Activity).window
    WindowCompat.getInsetsController(window, view)
        .isAppearanceLightStatusBars = !darkTheme
}

// 常见扩展示例
LaunchedEffect(userId) {
    viewModel.loadUser(userId)
}
```

## 4. 逐步解释

Theme.kt 要把 Compose 的 darkTheme 同步到 Android Window，这会修改组合树外部对象，因此使用 SideEffect：只有组合成功提交后才执行同步。

LaunchedEffect(key) 用于启动与组合节点绑定的协程。进入组合时启动，key 改变时取消旧协程并重启，离开组合时取消。项目本身没有使用 LaunchedEffect；Snackbar 的 showSnackbar 是用户点击后发生，因此使用 `rememberCoroutineScope().launch` 更直接。网络请求不能直接写在 Composable 函数体，否则重组可能重复执行。

## 5. 实际执行过程

1. Composable 计算 UI
2. 成功提交组合
3. SideEffect 同步外部对象
4. LaunchedEffect 按 key 启动协程
5. key 变化或节点离开时旧协程取消

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
- 用 SideEffect 启动长期任务
- 点击事件通过额外状态绕到 LaunchedEffect，增加复杂度

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
