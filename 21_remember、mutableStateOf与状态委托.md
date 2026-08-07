# 第 21 章：remember、mutableStateOf 与状态委托

## 1. 本章目标

理解 Compose 最基础的本地状态写法，以及 `by` 委托语法如何把 State 读写简化成普通变量。

学完后，你应当能够：

- 知道 mutableStateOf 返回可观察状态
- 理解 remember 跨重组保留状态对象
- 理解 `by` 与 `.value` 的等价关系
- 区分普通局部变量和 Compose State

## 2. 项目中的位置

- `components/AlertDialogDemo.kt`
- `components/ColumnRowDemo.kt`
- `components/AddressPickerDemo.kt`



## 3. 项目源码片段

```kotlin
var showDialog by remember { mutableStateOf(false) }
var result by remember { mutableStateOf<String?>(null) }

Button(onClick = { showDialog = true }) {
    Text("弹出弹窗")
}

if (showDialog) {
    AlertDialog(/* ... */)
}
```

## 4. 逐步解释

`mutableStateOf(false)` 返回 `MutableState<Boolean>`。Composable 在组合期间读取其值时，Compose 会记录依赖；值变化后，读取它的 UI 范围会被安排重组。`remember { ... }` 把这个状态对象保存到当前组合位置，避免每次重组都重新创建并回到 false。

`var showDialog by state` 是 Kotlin 属性委托。读取变量等价于 `state.value`，赋值等价于修改 `state.value`。项目中的 `mutableIntStateOf`、`mutableFloatStateOf` 是针对基础类型的专用状态。普通 `var count = 0` 放在 Composable 函数体中既不会被观察，也会在再次执行函数时重新初始化。

## 5. 实际执行过程

1. 首次组合执行 remember 并创建 State
2. UI 读取状态，Compose 记录依赖
3. 点击回调修改状态值
4. 相关组合范围失效
5. 下一帧重组并根据新值显示或移除 UI

## 6. Flutter / Dart 3 对照

Flutter 通常在 StatefulWidget 的 State 字段中保存值，并调用 setState 通知 build 重新执行。Compose 的 State 自身可观察，不需要额外调用 setState。

```dart
class DemoState extends State<Demo> {
  bool showDialog = false;

  void open() => setState(() => showDialog = true);

  @override
  Widget build(BuildContext context) => Column(
    children: [
      ElevatedButton(onPressed: open, child: const Text('弹出弹窗')),
      if (showDialog) const Text('弹窗显示中'),
    ],
  );
}
```

## 7. 常见错误

- 漏掉 remember，状态每次重组回到初始值
- 把 MutableState 对象本身当成 Boolean 使用
- 使用普通局部 var 并期待 UI 自动刷新
- 只修改可变集合内部元素，没有替换可观察状态值

## 8. 本章检查题

1. mutableStateOf(false) 返回什么类型？
2. remember 保存的是值还是状态对象所在的组合位置？
3. `by` 写法与 `.value` 写法有什么关系？

## 9. 本章总结

本地状态的基础组合是 `remember + mutableStateOf`。remember 负责跨重组保存，State 负责通知 UI，`by` 只是 Kotlin 语法糖。
