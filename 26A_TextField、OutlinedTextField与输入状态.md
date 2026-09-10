# 第 26A 章：TextField、OutlinedTextField 与输入状态

## 1. 本章目标

掌握 Compose 输入框的「状态驱动」写法，学会键盘类型配置、输入过滤与错误提示。

学完后，你应当能够：

- 用 `value` + `onValueChange` 驱动输入框显示与写回
- 区分 `OutlinedTextField`（描边）与 `TextField`（填充式）两种样式
- 使用 `label`、`placeholder`、`singleLine` 控制外观
- 用 `KeyboardOptions(keyboardType = ...)` 弹出合适的键盘
- 用 `isError` + `supportingText` 做校验反馈
- 理解为什么 Compose 的输入框不需要 `dispose`

## 2. 项目中的位置

- `components/TextFieldDemo.kt`（组件 id 为 `text_field`，标题「TextField 输入框」）

示例包含三个输入框：姓名（label + placeholder）、年龄（数字键盘 + 输入过滤）、手机号（11 位数字校验 + 错误提示），下方还有实时回显与校验结果。

## 3. 项目源码片段

```kotlin
var name by remember { mutableStateOf("") }
var age by remember { mutableStateOf("") }
var phone by remember { mutableStateOf("") }

// 校验规则：11 位长度 + 全部是数字字符
val phoneValid = phone.length == 11 && phone.all { it.isDigit() }
// 已输入但不合法才报错，避免一进页面就红一片
val phoneError = phone.isNotEmpty() && !phoneValid

Column(verticalArrangement = Arrangement.spacedBy(12.px)) {
    // 1) 姓名：label 会浮动上移，placeholder 只在内容为空时显示
    OutlinedTextField(
        value = name,
        onValueChange = { name = it },
        label = { Text("姓名") },
        placeholder = { Text("请输入姓名") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )

    // 2) 年龄：数字键盘 + 过滤掉非数字字符
    OutlinedTextField(
        value = age,
        onValueChange = { input -> age = input.filter { it.isDigit() } },
        label = { Text("年龄") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )

    // 3) 手机号：isError + supportingText 做校验反馈
    OutlinedTextField(
        value = phone,
        onValueChange = { phone = it }, // 原样写回，让用户能输入任意字符并看到校验结果
        label = { Text("手机号") },
        isError = phoneError,
        supportingText = {
            Text(if (phoneError) "手机号必须是 11 位数字" else "已输入 ${phone.length}/11 位")
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}
```

## 4. 逐步解释

Compose 输入框的核心只有两个参数：`value` 决定当前显示什么，`onValueChange` 在你每敲一个字符时把新文本交给你。你只要在回调里把新值写回状态（如 `name = it`），状态一变界面就重组刷新。这就是「状态驱动」，也是它和 Flutter 最大的思维差异——这里完全没有 `TextEditingController` 这种持有文本的对象，文本就存在你声明的状态里。

因此第一件事就是用 `remember { mutableStateOf("") }` 声明文本状态；没有状态，输入框就是只读的。三个示例各用一份状态，互不干扰。

样式上，`OutlinedTextField` 是描边外框，`TextField` 是填充式（带下划线），两者参数完全相同，只是外观不同，Material3 里 `OutlinedTextField` 更常用。`label` 是浮动标签，有内容或聚焦时会缩小上浮到边框上；`placeholder` 只在内容为空时显示，二者常一起用；`singleLine = true` 让回车不换行，适合表单类短输入。

键盘类型由 `KeyboardOptions(keyboardType = ...)` 指定：`KeyboardType.Number` 弹纯数字键盘，`KeyboardType.Phone` 弹适合手机号的数字键盘（带 `+ * #`）。注意它只是「提示系统弹哪种键盘」，不保证用户只能输入这些字符，所以业务上还要自己做过滤。

过滤就是普通 Kotlin 字符串操作：年龄框用 `input.filter { it.isDigit() }` 丢掉所有非数字字符，写回状态后界面上也不会出现非法字符。校验同样是纯 Kotlin 布尔运算：手机号用 `phone.length == 11 && phone.all { it.isDigit() }` 得到 `phoneValid`；再用 `phone.isNotEmpty() && !phoneValid` 得到 `phoneError`，这样「一进页面还没输入」时不会立刻报红。把 `phoneError` 传给 `isError`，描边和标签会自动变成错误色；`supportingText` 是输入框下方的辅助说明区，`isError = true` 时它也会自动使用错误色，于是同一处可以显示「手机号必须是 11 位数字」或「已输入 x/11 位」。

最后回答一个常见疑问：为什么不需要 `dispose`？因为这里根本没有需要释放的资源——没有控制器、没有监听器、没有订阅。文本是普通状态，状态随 Composable 一起进入和离开组合，Compose 自己负责回收。需要光标位置、二次格式化等复杂控制时，才需要改用 `TextFieldValue` 或 `TextFieldState`。

## 5. 实际执行过程

1. 首次组合：`remember` 创建三个空字符串状态，输入框显示 `label` 与 `placeholder`。
2. 用户点击年龄框：`keyboardType = KeyboardType.Number` 让系统弹出数字键盘。
3. 用户按键：系统把新文本交给 `onValueChange`，回调里执行 `filter { it.isDigit() }` 后写回 `age`。
4. 状态变化触发重组，输入框以新的 `value` 重新生成界面，非法字符根本不会出现在界面上。
5. 手机号每输入一位都会重新计算 `phoneValid` 与 `phoneError`：不合法时 `isError` 变红、`supportingText` 显示规则提示；合法时描边恢复正常、提示变成计数。
6. 下方 `Text` 直接把三个状态插值进模板串，实时回显证明「输入 → 状态 → 界面」这条单向数据流。

## 6. Flutter / Dart 3 对照

Flutter 的 `TextField` 的 `controller` 是可选的：不传 controller 时靠 `onChanged` 也能拿到每次输入；要主动读文本、控制光标或清空内容时才自己创建 `TextEditingController`，而一旦是自己创建的，就必须在 `dispose` 里释放它。Compose 没有这层对象，文本本身就存在状态里。

```dart
// controller 是可选的：需要主动读文本 / 控制光标时才创建
final controller = TextEditingController();

TextField(
  controller: controller,   // 不用 controller 时删掉这一行，改用 onChanged: (v) { ... }
  keyboardType: TextInputType.number,
  decoration: const InputDecoration(
    labelText: '年龄',
    hintText: '请输入年龄',
    errorText: null, // 非 null 时自动显示红色错误文案
  ),
);

@override
void dispose() {
  controller.dispose(); // 自己创建的 controller 必须在这里释放
  super.dispose();
}
```

参数对照：`label` 对应 `labelText`，`placeholder` 对应 `hintText`，`isError` + `supportingText` 合起来对应 `errorText`（以及 `helperText`），`keyboardOptions.keyboardType` 对应 `keyboardType`。

本质区别有两点：第一，Flutter 常见做法是用控制器持有文本、监听变化后 `setState`；Compose 是状态即文本，`onValueChange` 写回即刷新，没有中间对象。第二，Flutter 只有自己创建了 `TextEditingController` 时才需要负责 `dispose`（完全不传 controller、只用 `onChanged` 也可以），Compose 由框架内部管理输入状态，业务代码不需要写任何释放代码。至于输入过滤，Flutter 常用 `inputFormatters`（如 `FilteringTextInputFormatter.digitsOnly`），Compose 直接在 `onValueChange` 里用 Kotlin 的 `filter` 表达，更直白。

## 7. 常见错误

- 只写 `value` 不写 `onValueChange`，或回调里没把新值写回状态，输入框看起来「打不进去字」
- 用普通 `var` 而不是 `mutableStateOf`，文本变了但界面不刷新
- 以为 `keyboardType = KeyboardType.Number` 就能保证只能输入数字，忘记在 `onValueChange` 里过滤
- 校验只在提交时算一次，或者一进页面就把 `isError` 置为 `true`，界面开局全是红色
- 把 `placeholder` 当 `label` 用，聚焦后占位文字消失、用户不知道这一栏填什么
- 长文本忘写 `singleLine = true`，回车换行导致布局跳动
- 沿用 Flutter 习惯在 Compose 里找 `dispose`：Compose 输入框的内部状态由框架管理，业务代码没有需要释放的资源

## 8. 本章检查题

1. Compose 输入框靠哪两个参数完成「显示 + 写回」？为什么不需要 `TextEditingController`？
2. `label` 与 `placeholder` 的显示时机有什么不同？
3. `KeyboardOptions(keyboardType = KeyboardType.Number)` 能保证用户只输入数字吗？应该怎么做？
4. 项目里 `phoneError` 为什么写成 `phone.isNotEmpty() && !phoneValid` 而不是直接 `!phoneValid`？
5. 为什么 Compose 的输入框不需要 `dispose`？

答案：

1. `value` 决定显示内容，`onValueChange` 把用户新输入交给你写回状态。文本本身就在状态里，不需要额外的控制器对象持有，所以没有控制器，也就没有控制器的释放问题。
2. `label` 会在聚焦或有内容时缩小上浮到边框上方；`placeholder` 只在内容为空时显示，有内容后消失。
3. 不能。它只是提示系统弹出哪种键盘，非法字符仍可能进来（如粘贴）。业务上要在 `onValueChange` 里做过滤，例如 `input.filter { it.isDigit() }`。
4. 如果直接取反，一进页面还没输入时 `phoneValid` 就是 `false`，输入框会立刻变红。加上 `isNotEmpty()` 后只有「已输入且不合法」才报错。
5. 因为它没有控制器、监听器或订阅等需要释放的资源，文本只是普通状态，随 Composable 离开组合由 Compose 回收。

## 9. 本章总结

Compose 输入框是纯状态驱动：`value` 显示、`onValueChange` 写回，没有控制器也没有 `dispose`。用 `label`/`placeholder`/`singleLine` 控制外观，用 `KeyboardOptions(keyboardType = ...)` 提示键盘类型，用 `isError` + `supportingText` 反馈校验结果，输入过滤和校验逻辑都是普通 Kotlin 代码。对照 Flutter 的 `TextField` + `TextEditingController` + `InputDecoration(errorText:)`，能最清楚地看出「状态驱动」和「控制器驱动」的差别。
