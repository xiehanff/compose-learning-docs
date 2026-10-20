# 第 21 章：remember、mutableStateOf 与状态委托

## 1. 本章目标

先看一个具体的页面问题：详情页有一行文字「点此展开」，点击后应在下方显示一段补充说明，再点一次收起。刚接触 Compose 时最自然的写法，是在函数里放一个普通局部变量 `var expanded = false`，点击时把它翻转——代码可以通过编译，但点击后画面毫无反应。本章就用同一个「展开 / 折叠」例子讲清 Compose 本地状态的三件事：状态为什么要「可观察」（mutableStateOf）、为什么要「记住」（remember）、`by` 委托又是什么语法糖。

理解 Compose 最基础的本地状态写法，以及 `by` 委托语法如何把 State 读写简化成普通变量。

学完后，你应当能够：

- 知道 mutableStateOf 返回可观察状态
- 理解 remember 跨重组保留状态对象（重组详见第 22 章）
- 理解 `by` 与 `.value` 的等价关系
- 区分普通局部变量和 Compose State
- 预测普通 var 版与 remember 版点击后的画面，并解释为什么普通 var 不行
- 知道 remember 的 key 变化会把状态重置回初始值
- 知道需要跨配置变化恢复状态时改用 rememberSaveable（详见第 23 章）

## 2. 项目中的位置

- `components/AlertDialogDemo.kt`
- `components/ColumnRowDemo.kt`
- `components/AddressPickerDemo.kt`

## 3. 项目源码片段

### 3.1 反例：普通局部 var——能编译，点击无效

错误示例（故意错误，不参与编译；注意这段代码本身可以通过编译，问题出在运行时行为）：

```kotlin
var expanded = false   // 普通局部变量：Compose 观察不到它

Column {
    Text(
        text = if (expanded) "已展开" else "点此展开",
        modifier = Modifier
            .clickable { expanded = !expanded }
            .padding(16.dp),
    )
    if (expanded) {
        Text("状态变为 true 后，这段内容才会显示")
    }
}
```

初始界面：一行「点此展开」。操作：点一次这行文字。预期观察：文字仍是「点此展开」，下方说明永不出现——尽管 `expanded` 这个变量在点击回调里确实被改成了 true。原因在 4.1 节展开。

### 3.2 项目里的写法：remember + mutableStateOf + `by` 委托

<!-- compile -->
```kotlin
var expanded by remember { mutableStateOf(false) }

Column {
    Text(
        text = if (expanded) "已展开" else "点此展开",
        modifier = Modifier
            .clickable { expanded = !expanded }
            .padding(16.dp),
    )
    if (expanded) {
        Text("状态变为 true 后，这段内容才会显示")
    }
}
```

初始界面：一行「点此展开」。操作：点一次。预期观察：文字变为「已展开」，下方出现补充说明；再点一次恢复原状。本例只用已经见过的 `Text`、`Column` 和点击修饰符来观察状态变化。项目还会用同一套状态知识控制弹窗；`AlertDialog`、`Button` 等控件 API 分别在第 26—27 章学习。

### 3.3 同一例子的 `.value` 写法

<!-- compile -->
```kotlin
val expanded = remember { mutableStateOf(false) }   // 不用 by：拿到的是 MutableState 对象

Column {
    Text(
        text = if (expanded.value) "已展开" else "点此展开",   // 读值要 .value
        modifier = Modifier
            .clickable { expanded.value = !expanded.value }   // 写值也要 .value
            .padding(16.dp),
    )
    if (expanded.value) {
        Text("状态变为 true 后，这段内容才会显示")
    }
}
```

这版与 3.2 行为完全一致，只是不用属性委托，所有读写都显式经过 `.value`。

### 3.4 remember 带 key：key 变化会重置状态

<!-- compile -->
```kotlin
var userId by remember { mutableIntStateOf(1) }        // 会话 id：点击按钮切换
var draft by remember(userId) { mutableStateOf("") }   // key 是 userId：换会话就换一份草稿

Column {
    Text("当前会话：用户 $userId")
    OutlinedTextField(
        value = draft,
        onValueChange = { draft = it },
        label = { Text("给用户 $userId 的留言") },
        modifier = Modifier.fillMaxWidth(),
    )
    Button(onClick = { userId++ }) { Text("切换到下一个会话") }
}
```

初始界面：会话 1 的空输入框。操作：输入「你好」，点「切换到下一个会话」。预期观察：标题变成「用户 2」，输入框被清空——因为 remember 的 key 变了，草稿状态被重新初始化为 ""（输入框控件本身的用法见第 26A 章）。

## 4. 逐步解释

### 4.1 为什么普通 var 不行

3.1 的反例背后有两个独立的原因，理解后就能预测所有同类写法的结局：

1. **写了没人看**。`expanded = true` 只是把一个局部变量改了，没有任何机制通知 Compose。Compose 刷新的起点是「可观察状态发生变化」，普通变量不具备可观察性，因此 Compose 不会安排重组，画面自然不动。
2. **重组会把它冲掉**。即使别的状态触发了重组（比如主题色变化），重组就是**重新执行这个函数体**（详见第 22 章）。重新执行到 `var expanded = false` 这一行时，又会创建一个全新的、值为 false 的局部变量；上一轮点出来的 true 随着旧的一次函数调用一起消失。

所以「能编译」和「界面会更新」是两回事：编译器检查的是类型与语法，不检查「状态变化是否被观察」。这也解释了为什么这类错误编译器帮不上忙，只能靠理解机制。

### 4.2 mutableStateOf：把值变成可观察状态

`mutableStateOf(false)` 返回 `MutableState<Boolean>`。Composable 在组合期间读取其值时，Compose 会记录依赖；值变化后，读取它的 UI 范围会被安排重组。所谓「重组」，就是**状态变化后，Compose 重新执行「读取过该状态」的 Composable 函数，用新值重新生成这部分界面**——这正是「改一下状态，界面就跟着变」的原因，也是本章一切写法的落点，详见第 22 章。

### 4.3 remember：跨重组保留状态对象

只写 `var expanded by mutableStateOf(false)`（漏掉 remember）还不够：每次重组重新执行函数体，都会调用一次 `mutableStateOf(false)`，创建一个回到 false 的新状态对象。`remember { ... }` 把这个状态对象保存到当前组合位置：同一位置再次执行时直接复用旧对象，不再重新创建。这也是常见错误一节里「漏掉 remember」的症状来源——UI 看似能点，但任何无关重组都会把值弹回初始值。

### 4.4 `by` 委托与 `.value` 的等价关系

`var showDialog by state` 是 Kotlin 属性委托。先补两句 Kotlin 语法：`by` 会把「读写这个属性」的工作转交给后面的对象——这里就是 MutableState 对象；读 `expanded` 时实际执行 `state.value`，写 `expanded = x` 时实际执行 `state.value = x`。因此 `by` 写法（3.2）与 `.value` 写法（3.3）完全等价，前者只是省掉了满篇的 `.value`。

一个实际工程细节：`by` 依赖 `androidx.compose.runtime` 里的 `getValue` / `setValue` 这对扩展函数。把示例复制进一个新文件时，如果缺了这两个 import，编译会报「找不到合适的委托访问器」一类的错误；Android Studio 的自动导入通常会补上（`import androidx.compose.runtime.getValue` 与 `import androidx.compose.runtime.setValue`）。

项目中的 `mutableIntStateOf`、`mutableFloatStateOf` 是针对基础类型的专用状态：在组合期间读取基础类型状态时可以避免装箱，写法与 `mutableStateOf` 同构（`var count by remember { mutableIntStateOf(0) }`）。

### 4.5 状态读取的位置决定谁重组

本例中定义状态和读取状态（`if (expanded)` 与 `text = if (expanded) ...`）发生在同一个函数里，失效范围就是这整个函数。如果把状态值当参数传给子函数，读取就发生在调用方传参那一刻——「谁读了状态、哪个范围重组」在第 22 章用两个场景专门对比；参数比较与跳过的细节见第 46 章。

### 4.6 key 变化会重置；配置变化要靠 rememberSaveable

`remember(userId)` 这类带 key 的写法，key 变化时 init 会重新执行：旧状态对象被丢弃，新状态从初始值开始（3.4 里换会话清空草稿）。另外，remember 只在「当前组合实例」内存活——屏幕旋转等配置变化会把状态打回初始值。需要跨配置变化恢复时改用 rememberSaveable，它与 remember 在四种场景下的存留对照见第 23 章。

## 5. 实际执行过程

普通 var 版（3.1 反例）的追踪：

1. 首次组合执行函数体，`var expanded = false` 被创建，画面显示「点此展开」
2. 点击：回调把局部变量改为 true——但没有任何观察者
3. Compose 不安排重组，画面不变
4. 即使将来某个别的状态触发重组，函数体重新执行，`var expanded = false` 又把它初始化回 false

remember 版（3.2）的追踪：

1. 首次组合执行 remember 并创建 State
2. UI 读取状态，Compose 记录依赖
3. 点击文字的回调修改状态值
4. 相关组合范围失效
5. 下一帧重组：remember 在同一位置复用旧 State 对象（值已是 true），UI 根据新值显示或移除内容

key 版（3.4）的追踪：点击按钮写 userId → 读取过 userId 的范围重组 → `remember(userId)` 的 key 变化，草稿的 init 重新执行 → 输入框收到空字符串。

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

两点差异值得注意（概念对照，不是机制等价）：

- **通知方式**：Flutter 必须显式调用 setState，「值变了」和「请刷新」是同一个动作；Compose 把两者拆开——写状态自动通知，读取过它的范围自动失效。于是「忘记 setState 导致界面不刷新」这类 Flutter bug，在 Compose 里换成另一种形态：忘记用 mutableStateOf 包裹值（本章 3.1 的反例）。
- **失效范围**：setState 让整个 State 的 build 重新执行，粒度是 State 对象；Compose 的失效范围通常是「读取了该状态的 Composable 函数」，可能比整页更小（第 22 章对比两种场景）。另外，Flutter 的小型可观察对象 ValueNotifier 需要配 addListener 或 AnimatedBuilder 才能驱动 UI；mutableStateOf 在组合期间读取即自动建立依赖，不需要手动注册监听，但两者机制并不等价。

## 7. 常见错误

反例详解（错误现象 → 原因 → 修正）：

- **在新文件里用 `by` 报编译错误**。现象：`var expanded by remember { ... }` 报「找不到委托访问器」类错误。原因：`by` 依赖 `androidx.compose.runtime.getValue` / `setValue` 扩展。修正：补上这两个 import，或让 IDE 自动导入。
- **漏掉 remember**（`var expanded by mutableStateOf(false)`）。现象：点击后看似正常，但任何其他状态引发的重组都会把值弹回 false。原因：每次重组都创建新的 State 对象。修正：`remember { mutableStateOf(false) }`。
- 把 MutableState 对象本身当成 Boolean 使用：不用 `by` 又不写 `.value` 时，`expanded` 的类型是 `MutableState<Boolean>`，直接放进 `if (expanded)` 编译不过；读值用 `expanded.value` 或改用 `by` 委托。
- 使用普通局部 var 并期待 UI 自动刷新（3.1 的反例）。
- 只修改可变集合内部元素、没有替换可观察状态值：Compose 观察到的是「状态对象的值」，对普通 MutableList 原地 add 不会通知任何人；应替换整个列表状态的值，或改用可观察列表 SnapshotStateList（列表场景的深入讨论见第 16—17、46 章）。

## 8. 本章检查题

1. mutableStateOf(false) 返回什么类型？
2. remember 保存的是值还是状态对象所在的组合位置？
3. `by` 写法与 `.value` 写法有什么关系？
4. 预测：3.1 的普通 var 版点一次后，画面显示什么？此时局部变量 expanded 的值是什么？为什么画面和变量对不上？
5. 动手：给 3.4 的会话草稿例子增加一个「清空草稿」按钮（提示：写 `draft = ""`，注意这不会重置 userId）。

答案：

1. `MutableState<Boolean>`（项目中使用 mutableIntStateOf/mutableFloatStateOf 时是专用基础类型状态）。
2. remember 保存的是状态对象本身，并把它关联到当前组合位置；保存的是对象而不是值。
3. 完全等价。`by` 是 Kotlin 属性委托语法糖：读取变量等价于 `state.value`，赋值等价于修改 `state.value`。
4. 画面仍是「点此展开」，下方说明不出现；但局部变量的值是 true。因为普通变量没有被 Compose 观察，写它不会触发重组；而重组又只在其他可观察状态变化时才发生。
5. 参考实现：`Button(onClick = { draft = "" }) { Text("清空草稿") }`。写 draft 只是改值，userId 没变，`remember(userId)` 的 key 不变、状态对象保留，草稿被清空后仍可继续输入。

## 9. 本章总结

本地状态的基础组合是 `remember + mutableStateOf`。mutableStateOf 负责让值可观察，remember 负责跨重组保存，`by` 只是 Kotlin 语法糖；remember 的 key 变化会把状态重置回初始值；需要跨配置变化恢复时改用 rememberSaveable（第 23 章）。

官方参考：

- [State and Jetpack Compose（状态、remember、rememberSaveable 与状态提升）](https://developer.android.com/develop/ui/compose/state)
- [Kotlin 属性委托](https://kotlinlang.org/docs/delegated-properties.html)
