# 第 27 章：AlertDialog

## 1. 本章目标

掌握 Compose 声明式弹窗：状态为 true 时弹窗存在，所有关闭路径都通过修改状态完成。

学完后，你应当能够：

- 使用布尔状态控制显示
- 处理 onDismissRequest
- 配置标题、内容、确认和取消槽位
- 理解关闭不是 Navigator.pop
- 分清「对话框消失」与「用户确认」：onDismissRequest 只是关闭请求，不是确认
- 会用 `DialogProperties` 控制点外部、返回键是否可关
- 把「确认后先异步、成功再关闭」写对，避免竞态

## 2. 项目中的位置

- `components/AlertDialogDemo.kt`



## 3. 项目源码片段

<!-- compile
prelude: var showDialog by remember { mutableStateOf(false) }
-->
```kotlin
if (showDialog) {
    AlertDialog(
        onDismissRequest = { showDialog = false },
        title = { Text("删除确认") },
        text = { Text("确定要删除吗？") },
        confirmButton = {
            TextButton(onClick = { showDialog = false }) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = { showDialog = false }) {
                Text("取消")
            }
        },
    )
}
```

### 扩展示例：异步删除 + 关闭边界控制

项目片段点了「确定」立刻关弹窗，真实业务里删除是网络请求：请求期间不能重复点、不能取消，成功后才关闭。初始界面：一个「删除文章」按钮。操作：点它弹出确认框 → 点「删除」。期望观察：确认按钮变成「删除中…」且两个按钮都变灰，约 1.5 秒后弹窗消失；点弹窗外部没有反应（`dismissOnClickOutside = false`），按返回键弹窗会关闭但没有删除（返回键默认仍可关，走的是 `onDismissRequest`）。

<!-- compile -->
```kotlin
import androidx.compose.ui.window.DialogProperties

var showDialog by remember { mutableStateOf(false) }
var deleting by remember { mutableStateOf(false) }
val scope = rememberCoroutineScope()

if (showDialog) {
    AlertDialog(
        onDismissRequest = { showDialog = false }, // 点遮罩/返回键都到这里：是「取消」，不是「确认删除」
        properties = DialogProperties(
            dismissOnClickOutside = false, // 点外部不关，防误触（返回键默认仍可关）
        ),
        title = { Text("删除确认") },
        text = { Text("删除后不可恢复。取消和点外部都不会删除。") },
        confirmButton = {
            TextButton(
                onClick = {
                    if (deleting) return@TextButton // 业务层防线：禁用生效前连点也进不来
                    deleting = true
                    scope.launch {
                        delay(1500) // 模拟删除请求；真实项目换成 suspend API 并处理失败
                        deleting = false
                        showDialog = false // 成功后才关闭；失败时保持打开并提示（第 29 章 Snackbar）
                    }
                },
                enabled = !deleting,
            ) { Text(if (deleting) "删除中…" else "删除") }
        },
        dismissButton = {
            TextButton(
                onClick = { showDialog = false },
                enabled = !deleting, // 请求进行中不允许取消，避免「界面说取消、服务端已删除」
            ) { Text("取消") }
        },
    )
}
Button(onClick = { showDialog = true }) { Text("删除文章") }
```

## 4. 逐步解释

AlertDialog 是普通 Composable。`if (showDialog)` 为 true，它加入组合树；false 时移除。点击遮罩、返回键等关闭请求会触发 onDismissRequest，组件不会自动修改外部状态，所以必须在回调中设为 false。

confirmButton 和 dismissButton 是 Composable 槽位，可以放 TextButton 或自定义内容。确认动作通常先处理业务结果再关闭；异步删除应增加 loading 和防重复点击。Compose 弹窗不是导航栈上的页面，因此不依赖 Navigator.pop。

**谁拥有 showDialog。** 显示状态属于「拥有这个操作入口」的组件：入口按钮、弹窗、删除后的列表通常在同一个作用域里，把 `showDialog`（以及 `deleting`）放在这里，弹窗内容就是纯受控展示。如果要拆子组件，就把 `showDialog` 与 `onDismissRequest` / `onConfirm` 回调一起传下去（第 24 章的提升套路），确认结果靠回调向上传递，不要在弹窗组件内部藏一份业务状态再往回取。

**关闭不等于确认。** `onDismissRequest` 只表示「外部请求这个对话框消失」——点遮罩、按返回键都会走到它，它不代表用户确认了任何事。业务动作只应该写在 confirmButton 的 onClick 里。把删除逻辑放进 `onDismissRequest` 是典型事故写法：用户随手点了一下遮罩，文章就没了。

**异步确认的竞态。** 确认后请求需要时间，期间再次点击、取消都可能进来。最小防护与第 26 章加载态按钮同构：`deleting` 标志 + 回调内检查 + `enabled = !deleting`，请求成功后再置 `showDialog = false`。失败时保持弹窗打开并提示（第 29 章 Snackbar）——不要把「已发起请求」当「已删除成功」就关掉弹窗。

**控制点外部与返回键。** 两个开关在 `properties = DialogProperties(...)` 里：`dismissOnClickOutside` 与 `dismissOnBackPress`，默认都是 true。不可逆操作建议至少关掉「点外部关闭」（作者建议，非规范强制）。注意参数名以基线为准（本仓库解析的 ui 1.11.3 如上），且这是 `androidx.compose.ui.window.DialogProperties`——第 28 章 ModalBottomSheet 用的是 material3 自己的 `ModalBottomSheetProperties`，参数名是 `shouldDismissOnClickOutside` / `shouldDismissOnBackPress`，两套不要混用。

官方参考：[Dialogs](https://developer.android.com/develop/ui/compose/components/dialog)、[DialogProperties API](https://developer.android.com/reference/kotlin/androidx/compose/ui/window/DialogProperties)。

## 5. 实际执行过程

1. 按钮把 showDialog 设为 true
2. 弹窗进入组合
3. 用户确认、取消或点击外部
4. 对应回调修改状态
5. 重组后弹窗移除
6. 异步确认链路：点「删除」→ 回调先查 `deleting` 标志 → 置 `true` 并启动协程 → 读取该状态的按钮重组为禁用与「删除中…」→ 请求成功 → `showDialog = false` → 弹窗离开组合
7. 点外部：`DialogProperties` 拦下这次点击，`onDismissRequest` 不触发；按返回键：`onDismissRequest` 触发，弹窗关闭但没有任何删除发生

## 6. Flutter / Dart 3 对照

Flutter 常调用 showDialog 并通过 Navigator.pop 返回结果；Compose 直接把弹窗写在 UI 树中，由状态控制。相似点：两边都有确认/取消槽位与遮罩，Flutter 的 `barrierDismissible: false` 对应 Compose 的 `dismissOnClickOutside = false`。不等价的关键在「结果怎么回来」：Flutter 的 `showDialog` 是命令式调用并返回 `Future`，`await` 一下就拿到用户选了什么；Compose 的 AlertDialog 没有结果通道，确认结果靠状态与回调传递——弹窗只负责把「用户点了确认」这件事上报，不负责携带返回值。

```dart
final ok = await showDialog<bool>(
  context: context,
  barrierDismissible: false, // 对应 Compose 的 dismissOnClickOutside = false
  builder: (_) => AlertDialog(
    title: const Text('删除确认'),
    actions: [
      TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('取消')),
      TextButton(onPressed: () => Navigator.pop(context, true), child: const Text('确定')),
    ],
  ),
);
// await 返回 true/false/null，结果沿调用链回来；Compose 里这件事用「回调改状态」完成
```

## 7. 常见错误

- onDismissRequest 不修改状态，弹窗无法关闭
- 确认按钮忘记 showDialog=false
- 弹窗永久写在树中却期待自动隐藏
- 异步确认允许重复点击
- 把删除等业务动作写进 onDismissRequest：点遮罩、按返回键也会执行
- 「发起请求」就关弹窗：请求失败后界面与真实状态不一致
- 请求进行中不禁用取消按钮，出现「界面说已取消、服务端已删除」的竞态
- 找 Flutter 式的「弹窗返回值」：AlertDialog 没有结果参数，用状态 + 回调传结果

## 8. 本章检查题

1. Compose AlertDialog 如何关闭？
2. 点遮罩时哪个回调执行？
3. confirmButton 为什么是 Lambda？
4. 预测行为：把删除动作从 confirmButton 移到 onDismissRequest，用户点一下遮罩会发生什么？
5. 动手：给扩展示例再加 `dismissOnBackPress = false`，验证删除进行中按返回键也无法关闭。

答案：

1. AlertDialog 是普通 Composable，由外部状态控制：showDialog 设为 false 时它离开组合树即关闭。
2. onDismissRequest（点击遮罩、返回键等关闭请求都会触发它；两者是否可关由 DialogProperties 控制）。
3. 它是 Composable 槽位（@Composable () -> Unit），可以放 TextButton 或任意自定义内容，而不是固定按钮类型。
4. 点遮罩会触发 onDismissRequest，删除逻辑在里面执行——用户「随手一划」就删了数据。这正是「关闭≠确认」要防的事故：业务动作只放 confirmButton。
5. 修改 properties：`DialogProperties(dismissOnClickOutside = false, dismissOnBackPress = false)`。此时两个外部关闭入口都失效，只能点弹窗内按钮。代价是正常取消也只能走「取消」按钮，是否值得由场景权衡。

## 9. 本章总结

AlertDialog 的存在由状态决定。确认、取消和外部关闭最终都应统一更新状态，并明确处理业务结果：业务动作只放 confirmButton，`onDismissRequest` 只是「关闭请求」不是「确认」；点外部与返回键能否关闭由 `DialogProperties` 控制；异步确认用「标志位 + 禁用 + 成功后再关」防竞态，弹窗自身不持有业务状态，结果通过状态与回调传回。
