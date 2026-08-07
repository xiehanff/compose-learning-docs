# 第 28 章：ModalBottomSheet

## 1. 本章目标

掌握模态底部弹层的声明式显示、遮罩关闭和操作项处理。

学完后，你应当能够：

- 用布尔状态决定弹层是否存在
- 处理下滑和遮罩关闭
- 在 content 中组织 action sheet
- 知道高级控制可使用 SheetState

## 2. 项目中的位置

- `components/ModalBottomSheetDemo.kt`



## 3. 项目源码片段

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

## 4. 逐步解释

ModalBottomSheet 与 AlertDialog 一样由状态控制。showSheet 为 true 时进入组合并播放进入动画；下滑、点击遮罩或返回会调用 onDismissRequest。操作项点击后通常记录选择并将 showSheet 设为 false。

需要程序化 show/hide、跳过半展开状态或等待动画完成时，可引入 SheetState；其方法是挂起函数，需要协程。项目为了突出基础模式，只使用布尔状态。弹层底部还应考虑系统导航栏 Insets。

## 5. 实际执行过程

1. 点击按钮显示弹层
2. 弹层进入组合并执行动画
3. 用户选择项目或请求关闭
4. 回调更新结果和状态
5. 弹层退出并移除

## 6. Flutter / Dart 3 对照

Flutter 的 showModalBottomSheet 是命令式函数并返回 Future；Compose 把 Sheet 作为状态驱动的 UI 节点。

```dart
final action = await showModalBottomSheet<String>(
  context: context,
  builder: (_) => ListTile(
    title: const Text('复制链接'),
    onTap: () => Navigator.pop(context, '复制链接'),
  ),
);
```

## 7. 常见错误

- onDismissRequest 不修改 showSheet
- 点击操作项后忘记关闭
- 忽略底部系统安全区
- 同时随意操作布尔值和 SheetState，动画状态竞争

## 8. 本章检查题

1. ModalBottomSheet 与 AlertDialog 的共同显示模型是什么？
2. 何时需要 SheetState？
3. 下滑关闭应更新哪个状态？

## 9. 本章总结

基础 BottomSheet 模式是布尔状态 + onDismissRequest；高级动画控制再加入 SheetState 和协程。
