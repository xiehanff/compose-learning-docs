# 第 29 章：SnackbarHostState、协程与 Snackbar

## 1. 本章目标

理解 Snackbar 的状态、挂载点和协程调用三部分。

学完后，你应当能够：

- remember SnackbarHostState
- 在 Scaffold 中挂载 SnackbarHost
- 使用 rememberCoroutineScope
- 理解 showSnackbar 是 suspend

## 2. 项目中的位置

- `components/SnackbarDemo.kt`



## 3. 项目源码片段

<!-- compile -->
```kotlin
val hostState = remember { SnackbarHostState() }
val scope = rememberCoroutineScope()

Scaffold(
    snackbarHost = { SnackbarHost(hostState) },
) {
    Button(onClick = {
        scope.launch {
            hostState.showSnackbar("保存成功")
        }
    }) { Text("弹出提示") }
}
```

## 4. 逐步解释

SnackbarHostState 管理消息队列和当前显示状态，SnackbarHost 负责把状态绘制出来，Scaffold 提供合适的页面位置。缺少 Host，即使状态变化也没有 UI 挂载点。

showSnackbar 会挂起到提示消失或用户执行 action，因此必须在协程中调用。点击回调不是 suspend，项目使用 rememberCoroutineScope().launch。该作用域在当前组合离开时会取消，适合 UI 事件，不适合需要脱离页面继续执行的长期业务。

## 5. 实际执行过程

1. 创建 HostState 和作用域
2. Scaffold 组合 SnackbarHost
3. 点击后启动协程
4. 消息进入 HostState 队列
5. Host 显示 Snackbar
6. 消失后 showSnackbar 返回

## 6. Flutter / Dart 3 对照

Flutter 使用 ScaffoldMessenger.showSnackBar，通常不需要 await。Compose 的挂起 API还能返回 ActionPerformed 或 Dismissed 结果。

```dart
ScaffoldMessenger.of(context).showSnackBar(
  const SnackBar(content: Text('保存成功')),
);
```

## 7. 常见错误

- 只创建 HostState 没有挂 SnackbarHost
- 直接在 onClick 调 suspend 函数
- 每次重组创建新 HostState
- 把长期下载任务放在 UI scope 中

## 8. 本章检查题

1. Snackbar 需要哪三部分？
2. showSnackbar 为什么是挂起函数？
3. 组合离开时 rememberCoroutineScope 如何处理？

答案：

1. SnackbarHostState（管理消息队列与当前显示状态）+ SnackbarHost（负责绘制）+ Scaffold（提供位置）。
2. 因为 showSnackbar 会挂起到提示消失或用户执行 action 后才返回，只能在协程中调用。
3. 组合离开时该作用域自动取消，正在执行的协程随之结束，适合 UI 事件场景。

## 9. 本章总结

Snackbar = HostState + SnackbarHost + 协程调用。三者分别负责状态、显示位置和异步顺序。
