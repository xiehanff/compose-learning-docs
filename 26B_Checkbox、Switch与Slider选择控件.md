# 第 26B 章：Checkbox、Switch 与 Slider 选择控件

## 1. 本章目标

掌握三个选择类控件统一的「状态 + 回调」模式，并学会用同一个状态联动其它组件。

学完后，你应当能够：

- 用 `checked` / `onCheckedChange` 驱动 Checkbox 与 Switch
- 用 `value` / `onValueChange` 驱动 Slider，并理解 `valueRange`
- 用 `mutableFloatStateOf` 保存浮点状态
- 把布尔状态绑定到 `Button(enabled = ...)` 形成联动
- 处理深色底与浅色底的文字对比度

## 2. 项目中的位置

- `components/SelectionControlsDemo.kt`（组件 id 为 `selection_controls`，标题「Checkbox / Switch / Slider」）

三个小节分别演示：Checkbox 联动按钮禁用态、Switch 切换背景色、Slider 调节圆角大小。

## 3. 项目源码片段

<!-- compile -->
```kotlin
var agreed by remember { mutableStateOf(false) }          // 是否同意协议
var darkMode by remember { mutableStateOf(false) }        // 是否深色背景
var radiusValue by remember { mutableFloatStateOf(20f) }  // 圆角值，0f 到 100f

// 1) Checkbox 与 Button 的联动
Checkbox(
    checked = agreed,
    onCheckedChange = { agreed = it }, // 把新的布尔值写回状态，界面随之刷新
)
Button(
    onClick = { agreed = false },  // 点击后重置勾选，方便反复演示
    enabled = agreed,              // 未勾选时按钮变灰不可点
) {
    Text(if (agreed) "提交（已同意）" else "请先勾选同意协议", fontSize = 24.pxSp())
}

// 2) Switch 控制背景色
Switch(
    checked = darkMode,
    onCheckedChange = { darkMode = it },
)
Box(
    modifier = Modifier
        .fillMaxWidth()
        .height(64.px)
        .background(
            if (darkMode) Color(0xFF263238) else Color(0xFFF2F2F2),
            RoundedCornerShape(8.px),
        ),
    contentAlignment = Alignment.Center,
) {
    Text(
        "背景色随 Switch 变化",
        fontSize = 24.pxSp(),
        color = if (darkMode) Color.White else Color(0xFF616161), // 深底白字、浅底深灰字，保证对比度
    )
}

// 3) Slider 调节圆角
Text("当前值：%.1f".format(radiusValue), fontSize = 24.pxSp())
Slider(
    value = radiusValue,
    onValueChange = { radiusValue = it }, // 拖动过程中持续回调
    valueRange = 0f..100f,                // 默认是 0f..1f，需要显式放宽
    modifier = Modifier.fillMaxWidth(),
)
Box(
    modifier = Modifier
        .size(120.px)
        .background(Color(0xFF64B5F6), RoundedCornerShape(radiusValue.px)),
)
```

## 4. 逐步解释

三个控件的 API 模式完全一致：一个状态保存当前值，一个回调接收用户的新选择并写回状态。

- `Checkbox`：`checked: Boolean` + `onCheckedChange: (Boolean) -> Unit`。适合「同意协议」这类一次性布尔确认。
- `Switch`：`checked: Boolean` + `onCheckedChange: (Boolean) -> Unit`。和 Checkbox 参数名一样，语义偏「设置项里的即时开关」，拨动立即生效。
- `Slider`：`value: Float` + `onValueChange: (Float) -> Unit`。适合在连续区间上取值。

共同点是「控件自己不保存数据」：它只负责把你的选择通知给你，界面显示什么完全由你传入的状态决定。这就是 Compose 的「状态即真相」，所以没有 Flutter 的 `setState`，也没有控制器的概念。

状态类型要和控件匹配：布尔用 `mutableStateOf(false)`，浮点用 `mutableFloatStateOf(20f)`，这样能避免装箱并让读取更直接。

联动是这套模式最有价值的地方。示例一里 `Button(enabled = agreed)` 直接读同一个 `agreed`：未勾选时按钮自动变灰、点击无效；勾选后立即可用。这就是「状态提升 + 单向数据流」在表单场景的最小体现——一个状态同时决定两个控件的表现。

`Slider` 必须显式给 `valueRange`：它的默认值域是 `0f..1f`，而示例想调的是 0 到 100 的圆角，所以写 `valueRange = 0f..100f`。拖动过程中 `onValueChange` 会持续回调，每动一下都会写回状态并触发重组，所以数值文字是实时跟着动的。注意这里有一个容易误判的地方：色块是 `size(120.px)` 的正方形，圆角半径一旦达到边长一半（60dp），方块就已经是圆形；再往上调（60 到 100）渲染结果还是同一个圆形，只有 `当前值：xx.x` 的数字在变。想看到每一档都有可见变化，把 `valueRange` 调到 `0f..60f`，或者把色块改得更大（如 `size(240.px)` 对应 `0f..120f`）。显示当前值时用 `"%.1f".format(radiusValue)` 保留一位小数，否则浮点数的原始精度会让文字看着很乱。

最后是对比度：Switch 切换到深色时，色块的背景变成 `Color(0xFF263238)`（深灰蓝），里面的文字必须换成白色；浅色时背景是 `Color(0xFFF2F2F2)`，文字用 `Color(0xFF616161)` 深灰。颜色成对变化，才能保证任何状态下文字都清晰可读——这不是可选的美化，而是无障碍的基本要求（无障碍：让 TalkBack 等辅助工具能正确朗读并操作界面，第 80 章详解）。

## 5. 实际执行过程

1. 首次组合：`agreed = false`、`darkMode = false`、`radiusValue = 20f`，Checkbox 未勾选、按钮禁用、色块浅灰、圆角 20。
2. 点击 Checkbox：Compose 把取反后的布尔值交给 `onCheckedChange`，回调写入 `agreed`。
3. 状态变化触发重组：按钮的 `enabled` 变为 `true`，同时按钮文字从「请先勾选同意协议」变成「提交（已同意）」。
4. 点击按钮：`onClick` 把 `agreed` 重置为 `false`，回到未勾选状态，便于反复演示。
5. 拨动 Switch：`darkMode` 写回后，色块背景色与文字色同时重新计算，一次重组里完成整组换色。
6. 拖动 Slider：滑块位置在移动过程中持续回调 `onValueChange`，`radiusValue` 随之更新，数值文字实时变化，蓝色块的圆角同步更新（半径超过 60dp 后形状已是圆形，只有数字继续变）。

## 6. Flutter / Dart 3 对照

Flutter 的三个控件名与 Compose 完全一致，差异在于 Flutter 用 `setState` 触发重建，参数名多为 `value` / `onChanged`。

```dart
// Checkbox
Checkbox(
  value: agreed,
  onChanged: (v) => setState(() => agreed = v ?? false),
);

// Switch
Switch(
  value: darkMode,
  onChanged: (v) => setState(() => darkMode = v),
);

// Slider：默认值域就是 min..max，必须显式给区间
Slider(
  value: radiusValue,
  min: 0,
  max: 100,
  onChanged: (v) => setState(() => radiusValue = v),
);
```

参数对照：`checked` 对应 `value`，`onCheckedChange` 对应 `onChanged`（其中一个差别是 Flutter 的 `onChanged` 参数可空，`null` 表示禁用；Compose 用独立的 `enabled` 参数控制禁用）；Slider 的 `valueRange` 对应 `min` / `max` 两个参数。

本质区别是「谁保存数据」：Flutter 用 `setState` 把新值写进 State 字段后重建 widget；Compose 把新值写进可观察状态，只重组读取了这个状态的组件。另外 Slider 的默认区间不同——Compose 默认 `0f..1f`，Flutter 默认 `0..1`，两者都需要显式放宽才能取到 0 到 100。

## 7. 常见错误

- 只传 `checked` / `value` 而不在回调里写回状态，控件看起来「点不动」
- 用 `var agreed = false` 这类普通变量，回调里改了但界面不刷新
- Slider 忘了 `valueRange`，拖动范围被限制在 0 到 1，感觉「几乎不动」
- Slider 用 `mutableStateOf(20f)` 而不是 `mutableFloatStateOf(20f)`，虽然能跑但不必要地装箱
- 以为 0 到 100 每一档圆角都看得出变化：色块边长 120dp，半径到 60dp 就已经是圆形，之后视觉上不再变化（只有数字在变）
- 直接把 `radiusValue` 显示到界面，浮点原始精度导致文字很长，应该用 `"%.1f".format(...)`
- 背景色随状态切换了，文字色却没有跟着换，深色底上出现深灰字、看不清
- 忘了 `enabled` 可以用在 Checkbox / Switch / Slider 上，禁用态还要自己画灰
- 以为 Slider 只在松手时回调，其实拖动过程中会持续回调 `onValueChange`

## 8. 本章检查题

1. Checkbox、Switch、Slider 三者的「状态 + 回调」参数分别叫什么？
2. 为什么未勾选时按钮会自动变灰不可点？这体现了什么思想？
3. Slider 的 `valueRange` 默认是什么？不写会有什么后果？
4. Slider 显示当前值为什么常用 `"%.1f".format(value)`？
5. Switch 切换背景色时，为什么文字颜色也要一起改？

答案：

1. Checkbox 和 Switch 都是 `checked` + `onCheckedChange`；Slider 是 `value` + `onValueChange`。
2. 因为按钮的 `enabled` 直接绑定同一个 `agreed` 状态，状态为 `false` 时 Material3 自动应用禁用配色并屏蔽点击。体现了状态提升与单向数据流：一个状态可以决定多个控件的表现。
3. 默认是 `0f..1f`。不写就只能取到 0 到 1，无法表达 0 到 100 这种量程。
4. 浮点数原始精度会产生很长的小数串，格式化后只保留一位小数，更适合展示。
5. 因为深色背景与浅色背景对文字亮度的要求相反。背景变深时文字要用亮色（如白色），否则对比度不足、可读性差；这也是无障碍的基本要求。

## 9. 本章总结

Checkbox、Switch、Slider 走的是同一套模式：一个 `remember` 状态保存当前值，一个回调把用户的新选择写回状态，控件本身不保存数据。Slider 要显式写 `valueRange`，浮点状态用 `mutableFloatStateOf`。同一个状态还能直接驱动 `Button(enabled = ...)`，这就是 Compose 表单联动的核心写法；配对修改前景色与背景色，则是保证对比度的必要动作。
