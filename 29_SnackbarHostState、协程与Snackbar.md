# 第 29 章：SnackbarHostState、协程与 Snackbar

## 1. 本章目标

先看一个真实问题：列表页点「收藏」后，想弹一条「已收藏，可撤销」的底部提示，点「撤销」还能回滚。从其他框架带来的直觉通常是「调一个全局的弹提示函数就完事」，而在 Compose 里这条提示由三部分合作完成：谁记录消息（状态）、谁把它画出来（UI 槽位）、谁控制时序（协程）。少挂一个组件或少开一个协程，提示都不会出现。本章用一个「连续点击保存」的例子把三部分串起来，并观察一个反直觉的行为：连点三次不会互相覆盖，而是排队逐条播放。

理解 Snackbar 的状态、挂载点和协程调用三部分。

学完后，你应当能够：

- remember SnackbarHostState
- 在 Scaffold 中挂载 SnackbarHost
- 使用 rememberCoroutineScope
- 理解 showSnackbar 是 suspend
- 预测连续调用 showSnackbar 时的排队行为（新消息不会顶掉正在显示的旧消息）
- 会用返回值 SnackbarResult 区分「点了 action 按钮」和「自动消失」

## 2. 项目中的位置

- `components/SnackbarDemo.kt`

## 3. 项目源码片段

### 3.1 项目里的最小写法

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

这是项目 Demo 的骨架：点击后在协程里调 showSnackbar。它没展示两件事：连续点击会发生什么、action 按钮的返回值怎么用。下面补一个完整例子。

### 3.2 完整可运行示例：连点三次，看消息排队

<!-- compile decls -->
```kotlin
@Composable
fun SnackbarQueueDemo() {
    val hostState = remember { SnackbarHostState() }  // 状态与队列
    val scope = rememberCoroutineScope()              // 点击事件用的协程作用域
    var count by remember { mutableIntStateOf(0) }    // 已保存次数
    var undone by remember { mutableIntStateOf(0) }   // 点过「撤销」的次数

    Scaffold(
        snackbarHost = { SnackbarHost(hostState) },   // UI 槽位：只负责显示
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("已保存 $count 次，点「撤销」$undone 次")
            Button(onClick = {
                count++
                val current = count                  // 进协程前固定编号，避免排队期间读到后来的值
                scope.launch {
                    // showSnackbar 是挂起函数：上一条没消失时，这里先排队等待
                    val result = hostState.showSnackbar(
                        message = "第 $current 次已保存",
                        actionLabel = "撤销",
                        // 传了 actionLabel 时 duration 默认是 Indefinite（一直等用户操作），
                        // 这里显式改回 Short，让队列能自动往下走
                        duration = SnackbarDuration.Short,
                    )
                    if (result == SnackbarResult.ActionPerformed) undone++
                }
            }) { Text("保存") }
        }
    }
}
```

初始界面：中间一行统计文字加一个「保存」按钮，底部没有提示。

操作步骤：

1. 快速连点「保存」三次，不要碰弹出的提示。
2. 等第一条提示自动消失，继续观察。
3. 在某条提示还显示时，点它上面的「撤销」。

期望观察：三条提示按点击顺序一条接一条显示，正在显示的旧消息不会被新消息顶掉；点「撤销」的那条立即消失，统计行的「撤销」计数加一，下一条接着出现。本例只验证编译与机制描述，提示的具体停留时长以实际运行为准。

### 3.3 想立刻关掉正在显示的那条：程序化 dismiss

<!-- compile
prelude: val hostState = remember { SnackbarHostState() }
-->
```kotlin
// currentSnackbarData 是 HostState 当前正在显示的那条；dismiss() 让它立即结束，
// 对应的那次 showSnackbar 会以 SnackbarResult.Dismissed 返回并释放队列
hostState.currentSnackbarData?.dismiss()
```

注意：这只会结束「正在显示」的一条，不会清掉已经排队的其他消息——它们仍按先后顺序继续显示（排队顺序的公平性属于实现细节，以官方文档为准）。

## 4. 逐步解释

### 4.1 Host 与 HostState 的分工：UI 槽位 vs 状态与队列

SnackbarHostState 管理消息队列和当前显示状态，SnackbarHost 负责把状态绘制出来，Scaffold 提供合适的页面位置。缺少 Host，即使状态变化也没有 UI 挂载点。

分工细节：官方 API 文档对 SnackbarHostState 的定位是 "State of the SnackbarHost, which controls the queue and the current Snackbar being shown inside the SnackbarHost"——队列和「当前显示哪条」都在状态对象里。SnackbarHost 只是一个普通 Composable，读取 HostState 的 `currentSnackbarData` 画出当前那条（默认外观由 Snackbar 组件提供），自己不保存业务状态，这与第 24 章的受控组件是同一模式。Scaffold 的 `snackbarHost` 参数只是给 Host 一个标准槽位（页面底部、避开系统栏）。

一个更隐蔽的坑：只创建 HostState 而不挂 Host 时，不止是「看不见」——Short/Long 的自动超时也不会发生，因为超时计时在 material3 的 SnackbarHost 内部驱动（实现细节，以官方文档为准）。于是第一条 showSnackbar 会一直挂起等超时，后续消息全部堵在队列里。这就是「常见错误」第一条的机制来源。

用「事件 → 状态 → 重组 → 界面」追踪一次点击：点击按钮（事件）→ 协程里调 showSnackbar，HostState 的当前消息被更新（状态）→ SnackbarHost 读到新值重组（重组）→ 底部出现提示（界面）。显示结束的路径也一样：超时或用户操作让 showSnackbar 返回、当前消息清空 → Host 重组 → 提示消失 → 队列里下一条接上。

### 4.2 showSnackbar 是挂起函数：先补协程语法，再看排队

本章首次正式用到协程，两句话说清：`suspend` 函数是可以「挂起等结果、不阻塞线程」的函数，只能在协程或其他 suspend 函数里调用；`scope.launch { ... }` 表示在 scope 这个作用域里启动一个新协程去执行花括号里的代码。协程细节见第 49 章与官方协程指南。

showSnackbar 会挂起到提示消失或用户执行 action，因此必须在协程中调用。点击回调不是 suspend，项目使用 rememberCoroutineScope().launch。该作用域在当前组合离开时会取消，适合 UI 事件，不适合需要脱离页面继续执行的长期业务。

**连续调用是排队，不是覆盖。** 官方 API 文档对 showSnackbar 的描述是 "Shows or queues to be shown a [Snackbar]"，并明确保证 "guarantees to show at most one snackbar at a time. If this function is called while another snackbar is already visible, it suspends until shown and addressed"（同一时刻至多显示一条；已有条目显示中时，新调用先挂起等待）。所以 3.2 的三条提示是逐条播放的。material3 的实现用一把「公平的挂起互斥锁」维护这个队列（源码注释原话 "Since a suspending Mutex is a fair queue, this manages our message queue"），锁的公平性属于实现细节，业务代码不要依赖它，以官方文档为准。

排队带来的两个实际后果：

1. `duration` 的默认值和 actionLabel 绑定：不传 actionLabel 时默认 Short（短暂显示后自动消失）；传了 actionLabel 时默认 Indefinite（一直等用户操作）。如果连点五次且都带 actionLabel、又不显式指定 duration，第一条会一直停在屏幕上，后面四条全部挂起等待——界面看起来像「提示不更新了」，其实只是队列堵住了。
2. 排队等待期间协程还活着，等它真正执行到 showSnackbar 时才求值消息内容。3.2 里先 `val current = count` 再进协程，就是为了让「第 1 次」「第 2 次」的编号在点击那一刻固定下来，而不是等轮到它显示时才去读最新的 count。

### 4.3 返回值 SnackbarResult：有无 action 按钮的差异

showSnackbar 返回 `SnackbarResult`，只有两个值：`ActionPerformed`（用户点了 action 按钮）和 `Dismissed`（超时自动消失，或用户以其他方式关闭）。官方 Snackbar 指南给的标准用法是把返回值接住后用 when 分支处理——3.2 里 `if (result == SnackbarResult.ActionPerformed) undone++` 就是撤销分支。

有/无 action 按钮时的行为差异：

- 不传 `actionLabel`：提示上没有按钮，用户没有可点的动作，返回值实际上只会是 Dismissed；duration 默认 Short，短暂停留后自己走。
- 传 `actionLabel = "撤销"`：提示右侧出现文字按钮，点击后返回 ActionPerformed；duration 默认变为 Indefinite，会一直等用户表态。
- `withDismissAction = true` 可以额外加一个关闭图标，适合长文案但不想放具体动作的场景（本仓库基线的 material3 支持该参数，见 `snippets/gradle/libs.versions.toml`）。

### 4.4 作用域取消：rememberCoroutineScope 与页面生命周期

rememberCoroutineScope 返回的作用域与「当前组合位置」同生命周期：这个页面离开组合（比如被导航切走）时，作用域取消，其中还没轮到显示的排队协程一并结束。官方 API 文档同时说明：调用方取消时，对应的 snackbar 会从显示与队列中移除。这对 UI 提示正合适——页面都没了，提示也没有继续弹的意义。

反过来说，希望「离开页面也要弹完」的逻辑（比如后台保存完成的通知）不能依赖这个作用域，那是通知/事件总线层面的任务，不属于 Snackbar 这套页面内组件的职责边界。

## 5. 实际执行过程

1. 创建 HostState 和作用域
2. Scaffold 组合 SnackbarHost
3. 点击后启动协程
4. 消息进入 HostState 队列
5. Host 显示 Snackbar
6. 消失后 showSnackbar 返回
7. 若上一条仍在显示，新协程先在队列上挂起等待，轮到它再显示
8. 返回 ActionPerformed 或 Dismissed，调用方据此更新撤销计数等状态

## 6. Flutter / Dart 3 对照

Flutter 使用 ScaffoldMessenger.showSnackBar，通常不需要 await。Compose 的挂起 API 还能返回 ActionPerformed 或 Dismissed 结果。

```dart
ScaffoldMessenger.of(context).showSnackBar(
  const SnackBar(content: Text('保存成功')),
);
```

相似点：两边都保证同一时刻只显示一条 SnackBar，后续消息由管理器排队；Flutter 的 SnackBar 也可以带 action（SnackBarAction）并收到用户是否点击的回调。

不等价点：Flutter 的 ScaffoldMessenger 通常由 MaterialApp 根部自动提供，在任何子树都能 `ScaffoldMessenger.of(context)` 取到，跨页面仍然有效；Compose 没有全局信使，每个页面自己 remember 一个 SnackbarHostState、把 SnackbarHost 挂在自己的 Scaffold 上，页面离开组合后作用域取消、未显示的排队提示随之丢弃。另外 Flutter 的 showSnackBar 返回的是 controller（不是挂起结果），关闭时机通过回调监听，与 Compose 的挂起返回值是两种编程模型。

## 7. 常见错误

- 只创建 HostState 没有挂 SnackbarHost（连自动超时都不会发生，showSnackbar 一直挂起，见 4.1）
- 直接在 onClick 调 suspend 函数
- 每次重组创建新 HostState
- 把长期下载任务放在 UI scope 中

```kotlin
// 错误示例（故意错误，不参与编译）：onClick 的 lambda 不是挂起环境，
// 直接调用挂起函数 showSnackbar 无法通过编译
Button(onClick = { hostState.showSnackbar("保存成功") }) { Text("弹出提示") }
```

- 期待新消息「顶掉」旧消息：实际是排队，连点会逐条播完；要立刻结束当前条，用 3.3 的 currentSnackbarData?.dismiss()
- 传了 actionLabel 却没意识到 duration 默认变 Indefinite，队列被第一条堵住，误以为 Snackbar「坏了」

## 8. 本章检查题

1. Snackbar 需要哪三部分？
2. showSnackbar 为什么是挂起函数？
3. 组合离开时 rememberCoroutineScope 如何处理？
4. 预测行为：在 3.2 的示例里快速连点「保存」三次，然后什么都不做，屏幕上会看到什么？如果把 `duration = SnackbarDuration.Short` 这行删掉（保留 actionLabel），行为又有什么变化？
5. 动手修改：给 3.2 的提示加一个关闭图标（提示：withDismissAction），并验证点关闭图标时撤销计数是否增加（应当不变，想一想为什么）。

答案：

1. SnackbarHostState（管理消息队列与当前显示状态）+ SnackbarHost（负责绘制）+ Scaffold（提供位置）。
2. 因为 showSnackbar 会挂起到提示消失或用户执行 action 后才返回，只能在协程中调用。
3. 组合离开时该作用域自动取消，正在执行的协程随之结束，适合 UI 事件场景。
4. 前半：三条提示按点击顺序逐条显示，每条短暂停留后自动消失，互不覆盖（官方文档保证同一时刻至多显示一条，后来的排队等待）。后半：带 actionLabel 时 duration 默认 Indefinite，第一条会一直停在屏幕上等待用户操作，后两条一直不出现，直到点「撤销」或用 3.3 的方式关掉第一条。
5. 撤销计数不变。关闭图标触发的是 Dismissed 而不是 ActionPerformed，`if (result == SnackbarResult.ActionPerformed)` 分支不执行；这正是用返回值区分两种结束方式的意义。

## 9. 本章总结

Snackbar = HostState + SnackbarHost + 协程调用。三者分别负责状态、显示位置和异步顺序；连续调用是排队而不是覆盖，action 与否决定 duration 默认值，返回值区分用户动作与自动消失，作用域取消让未显示的提示随页面一起结束。

官方参考：

- [Snackbar（官方组件指南，含 SnackbarResult 示例）](https://developer.android.com/develop/ui/compose/components/snackbar)
- [SnackbarHostState（API 参考：showSnackbar 的排队与取消说明）](https://developer.android.com/reference/kotlin/androidx/compose/material3/SnackbarHostState)
- [SnackbarHost（API 参考）](https://developer.android.com/reference/kotlin/androidx/compose/material3/SnackbarHost)
- [rememberCoroutineScope（API 参考）](https://developer.android.com/reference/kotlin/androidx/compose/runtime/rememberCoroutineScope)
