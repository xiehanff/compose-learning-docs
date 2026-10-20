# 第 28 章：ModalBottomSheet

## 1. 本章目标

掌握模态底部弹层的声明式显示、遮罩关闭和操作项处理。

学完后，你应当能够：

- 用布尔状态决定弹层是否存在
- 处理下滑和遮罩关闭
- 在 content 中组织 action sheet
- 知道高级控制可使用 SheetState
- 会用 `sheetState.hide()`（挂起函数）等收起动画播完，再把弹层移出组合
- 知道 `skipPartiallyExpanded` 的适用场景，以及控制返回键/遮罩关闭要用 material3 自己的 properties（版本敏感）

## 2. 项目中的位置

- `components/ModalBottomSheetDemo.kt`



## 3. 项目源码片段

<!-- compile
prelude: var showSheet by remember { mutableStateOf(false) }
prelude: var action by remember { mutableStateOf("") }
-->
```kotlin
if (showSheet) {
    ModalBottomSheet(
        onDismissRequest = { showSheet = false },
    ) {
        listOf("分享到微信", "复制链接", "举报").forEach { item ->
            TextButton(onClick = {
                action = item
                showSheet = false
            }) { Text(item) }
        }
    }
}
```

### 扩展示例：等收起动画播完再移除弹层

项目片段点操作项后直接 `showSheet = false`，弹层立即从组合移除、看不到退出动画。官方文档给的标准收法是先调 `sheetState.hide()`（挂起函数）等动画完成。初始界面：一个「打开弹层」按钮。操作：点开 → 底部弹层直接全展开（内容较矮，跳过半展开）；点弹层里的「选好了，收起弹层」。期望观察：弹层向下滑出、动画播完后才消失。

<!-- compile decls -->
```kotlin
@OptIn(ExperimentalMaterial3Api::class) // ModalBottomSheet 在本仓库基线（material3 1.4.0）仍是实验 API
@Composable
fun SheetHideDemo() {
    var showSheet by remember { mutableStateOf(false) }
    // skipPartiallyExpanded = true：内容不高时跳过半展开，直接全展开
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            sheetState = sheetState,
        ) {
            TextButton(
                onClick = {
                    // hide() 是挂起函数：等收起动画完成；之后再把弹层移出组合
                    scope.launch { sheetState.hide() }.invokeOnCompletion {
                        if (!sheetState.isVisible) showSheet = false
                    }
                },
            ) { Text("选好了，收起弹层") }
        }
    }
    Button(onClick = { showSheet = true }) { Text("打开弹层") }
}
```

## 4. 逐步解释

ModalBottomSheet 与 AlertDialog 一样由状态控制。showSheet 为 true 时进入组合并播放进入动画；下滑、点击遮罩或返回会调用 onDismissRequest。操作项点击后通常记录选择并将 showSheet 设为 false。

一个容易被截图片段藏起来的细节：`ModalBottomSheet` 在 material3 里仍标注为 `@ExperimentalMaterial3Api`，使用它的地方必须显式 opt-in。项目源码 `ModalBottomSheetDemo.kt` 里就写在函数上：

```kotlin
@OptIn(ExperimentalMaterial3Api::class) // ModalBottomSheet 仍是实验 API，必须显式 opt-in
@Composable
fun ModalBottomSheetDemo() {
    // …上面的 if (showSheet) { ModalBottomSheet(...) } 就在这里
}
```

漏写这行会直接编译报错「This material API is experimental」；第 8A 章的 `FlowRow` 同理（用 `ExperimentalLayoutApi`）。（基线核对：已确认本仓库 snippets 工程解析的 material3 1.4.0 中该 API 仍带实验标注；升级依赖后需复查。）

**SheetState 与隐藏时序。** `rememberModalBottomSheetState()` 创建的 `SheetState` 记录弹层位置，`currentValue` 是 `SheetValue.Hidden` / `PartiallyExpanded` / `Expanded` 之一，`isVisible` 表示当前是否可见。它的 `hide()` / `show()` / `expand()` 都是挂起函数，必须在协程里调用：`hide()` 会挂起到收起动画播完才返回。官方文档的标准收法就是扩展示例那三行——`scope.launch { sheetState.hide() }.invokeOnCompletion { ... }`：`launch` 返回一个 Job，`invokeOnCompletion` 在协程结束（无论正常结束、被取消还是异常）时执行一次，这里用它承接「动画完成后把 `showSheet` 置 false、弹层离开组合」。直接 `showSheet = false` 也能关，但弹层立即从组合移除，退出动画被跳过（本例观察级别的结论）。`if (!sheetState.isVisible)` 是防御性判断：用户可能在你调 `hide()` 的同时自己下滑关闭了弹层，此时系统已完成动画，判断让意图更明确。

**跳过半展开与关闭边界。** 默认（`skipPartiallyExpanded = false`）时，够高的弹层先停在半展开，用户上滑才全展开；内容很少的操作面板传 `skipPartiallyExpanded = true` 跳过半展开直接全展开。另一个版本敏感配置：在本仓库基线（material3 1.4.0）中，`ModalBottomSheet` 有 `properties = ModalBottomSheetProperties(...)` 参数，`shouldDismissOnBackPress` / `shouldDismissOnClickOutside`（默认 true）分别控制返回键与点遮罩是否触发 `onDismissRequest`——功能上对应第 27 章 AlertDialog 用的 `DialogProperties`，但类与参数名都不同，两套不要混用；更早的 material3 版本没有这个参数，参考旧示例时要留意。实验 API 的 opt-in 同样覆盖 `ModalBottomSheetProperties` 的调用。

需要程序化 show/hide、跳过半展开状态或等待动画完成时，可引入 SheetState；其方法是挂起函数，需要协程。项目为了突出基础模式，只使用布尔状态。弹层底部还应考虑系统导航栏 Insets。

官方参考：[Bottom sheets](https://developer.android.com/develop/ui/compose/components/bottom-sheets)（含 `hide()` + `invokeOnCompletion` 的标准收法）。

## 5. 实际执行过程

1. 点击按钮显示弹层
2. 弹层进入组合并执行动画
3. 用户选择项目或请求关闭
4. 回调更新结果和状态
5. 弹层退出并移除
6. 程序化收起：点击「选好了」→ `launch { hide() }` 挂起等收起动画 → `invokeOnCompletion` 里确认 `!isVisible` → `showSheet = false` → 弹层离开组合。跳过 `hide()` 直接置 false，弹层是瞬间消失的（没有退出动画）

## 6. Flutter / Dart 3 对照

Flutter 的 showModalBottomSheet 是命令式函数并返回 Future；Compose 把 Sheet 作为状态驱动的 UI 节点。相似点：两边都是模态底部面板，Flutter 也有 `isDismissible` 这类关闭开关（对应 Compose 基线里的 `ModalBottomSheetProperties`）。不等价的两个关键：结果通道——Flutter 通过 `Navigator.pop(context, result)` 上报选择、`await` 拿到结果，Compose 没有这个返回值，选择靠回调写状态；关闭时序——Flutter 的面板从 widget 树移除与退场动画由框架管理，Compose 的显隐完全归你的状态管，想播完退场动画就得先 `hide()` 再移除。

```dart
final action = await showModalBottomSheet<String>(
  context: context,
  isDismissible: true, // 点遮罩是否可关，对应 shouldDismissOnClickOutside
  builder: (_) => ListTile(
    title: const Text('复制链接'),
    onTap: () => Navigator.pop(context, '复制链接'), // 结果沿 Future 返回
  ),
);
```

## 7. 常见错误

- onDismissRequest 不修改 showSheet
- 忘了 `@OptIn(ExperimentalMaterial3Api::class)`：ModalBottomSheet 还是实验 API，直接调用编译不过
- 点击操作项后忘记关闭
- 忽略底部系统安全区
- 同时随意操作布尔值和 SheetState，动画状态竞争
- 在 onClick 里直接调 `sheetState.hide()`：它是挂起函数，要 `scope.launch { ... }` 包住
- 期望直接 `showSheet = false` 还能播退出动画：弹层是立即移除的
- 把 ui 的 `DialogProperties` 参数名（`dismissOnClickOutside`）抄到 sheet 上：这里用的是 `ModalBottomSheetProperties(shouldDismissOnClickOutside = ...)`

## 8. 本章检查题

1. ModalBottomSheet 与 AlertDialog 的共同显示模型是什么？
2. 何时需要 SheetState？
3. 下滑关闭应更新哪个状态？
4. 预测行为：点操作项后直接 `showSheet = false`，与先 `hide()` 再置 false 相比，用户看到什么差别？
5. 动手：给扩展示例加 `properties = ModalBottomSheetProperties(shouldDismissOnBackPress = false)`，验证按返回键不再关闭弹层（本仓库基线 material3 1.4.0 可用，别忘了它也属于实验 API，由同一个 @OptIn 覆盖）。

答案：

1. 都是「状态为 true 时加入组合并播放进入动画，关闭请求触发 onDismissRequest」。
2. 需要程序化 show/hide、跳过半展开状态或等待动画完成时（SheetState 的方法是挂起函数，需要协程）。
3. 控制显示的状态（如 showSheet），在下滑关闭回调中把它设为 false。
4. 直接置 false 时弹层立即从组合移除，用户看到的是「瞬间消失」；先 `hide()` 再置 false，收起动画会完整播放后才移除（本例观察级别的结论）。
5. 给 `ModalBottomSheet(...)` 增加 `properties = ModalBottomSheetProperties(shouldDismissOnBackPress = false)` 参数即可。设为 false 后按返回键不再触发 `onDismissRequest`，关闭只能靠下滑、点遮罩或弹层内操作。

## 9. 本章总结

基础 BottomSheet 模式是布尔状态 + onDismissRequest；高级动画控制再加入 SheetState 和协程：`hide()` 是挂起函数，`launch { hide() }.invokeOnCompletion { ... }` 等动画播完再移出组合。`skipPartiallyExpanded` 跳过半展开，关闭边界用 material3 自己的 `ModalBottomSheetProperties`（与第 27 章 ui 的 `DialogProperties` 同功能但参数名不同）。ModalBottomSheet 在本仓库基线中仍是实验 API，@OptIn 不能省。
