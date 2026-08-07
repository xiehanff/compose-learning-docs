# 第 27 章：AlertDialog

## 1. 本章目标

掌握 Compose 声明式弹窗：状态为 true 时弹窗存在，所有关闭路径都通过修改状态完成。

学完后，你应当能够：

- 使用布尔状态控制显示
- 处理 onDismissRequest
- 配置标题、内容、确认和取消槽位
- 理解关闭不是 Navigator.pop

## 2. 项目中的位置

- `components/AlertDialogDemo.kt`



## 3. 项目源码片段

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

## 4. 逐步解释

AlertDialog 是普通 Composable。`if (showDialog)` 为 true，它加入组合树；false 时移除。点击遮罩、返回键等关闭请求会触发 onDismissRequest，组件不会自动修改外部状态，所以必须在回调中设为 false。

confirmButton 和 dismissButton 是 Composable 槽位，可以放 TextButton 或自定义内容。确认动作通常先处理业务结果再关闭；异步删除应增加 loading 和防重复点击。Compose 弹窗不是导航栈上的页面，因此不依赖 Navigator.pop。

## 5. 实际执行过程

1. 按钮把 showDialog 设为 true
2. 弹窗进入组合
3. 用户确认、取消或点击外部
4. 对应回调修改状态
5. 重组后弹窗移除

## 6. Flutter / Dart 3 对照

Flutter 常调用 showDialog 并通过 Navigator.pop 返回结果；Compose 直接把弹窗写在 UI 树中，由状态控制。

```dart
final ok = await showDialog<bool>(
  context: context,
  builder: (_) => AlertDialog(
    title: const Text('删除确认'),
    actions: [
      TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('取消')),
      TextButton(onPressed: () => Navigator.pop(context, true), child: const Text('确定')),
    ],
  ),
);
```

## 7. 常见错误

- onDismissRequest 不修改状态，弹窗无法关闭
- 确认按钮忘记 showDialog=false
- 弹窗永久写在树中却期待自动隐藏
- 异步确认允许重复点击

## 8. 本章检查题

1. Compose AlertDialog 如何关闭？
2. 点遮罩时哪个回调执行？
3. confirmButton 为什么是 Lambda？

## 9. 本章总结

AlertDialog 的存在由状态决定。确认、取消和外部关闭最终都应统一更新状态，并明确处理业务结果。
