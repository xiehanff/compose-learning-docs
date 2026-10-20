# 第 26A 章：TextField、OutlinedTextField 与输入状态

## 1. 本章目标

掌握 Compose 输入框当前的推荐写法——**state-based**（`TextFieldState` + `state = ...`），能读懂并迁移旧的 value-based 写法，学会键盘类型、输入过滤、显示格式化与错误提示。

学完后，你应当能够：

- 用 `rememberTextFieldState()` + `state =` 驱动输入框显示与写回（官方当前推荐）
- 读懂 `value` + `onValueChange` 的旧写法，并知道什么时候还该用它
- 区分 `OutlinedTextField`（描边）与 `TextField`（填充式）两种样式
- 使用 `label`、`placeholder`、`lineLimits` 控制外观与行数
- 用 `KeyboardOptions(keyboardType = ...)` 弹出合适的键盘
- 用 `InputTransformation` 过滤输入，用 `OutputTransformation` 只做显示格式化
- 用 `isError` + `supportingText` 做校验反馈
- 用 `SecureTextField` 写密码框，并理解为什么不需要 `dispose`
- 用 `imeAction` 定制软键盘动作键，并分别在两种写法里响应用户按键（`keyboardActions` / `onKeyboardAction`）
- 用 `FocusRequester` 程序化把焦点交给指定输入框

## 2. 项目中的位置

- `components/TextFieldDemo.kt`（组件 id 为 `text_field`，标题「TextField 输入框」）

示例包含三个输入框：姓名（label + placeholder）、年龄（数字键盘 + 输入过滤）、手机号（11 位数字校验 + 错误提示），下方还有实时回显与校验结果。

需要点明一个事实：项目里的 `TextFieldDemo.kt` 目前用的是 **value-based** 写法（`value` + `onValueChange` + `remember { mutableStateOf("") }`）。它没有错，官方仍然支持；但官方当前明确推荐 **state-based**，并提供了从 value-based 迁移的[文档](https://developer.android.com/develop/ui/compose/text/migrate-state-based)（基线边界：本仓库 snippets 工程解析的 material3 版本为 1.4.0，这套 API 已完整可用）。所以本章主线讲 state-based，同时保留 value-based 作为对照——一是让你能读懂旧项目，二是官方迁移文档的重点恰好就是两者的换算关系。

阅读路线：初读先看本节项目源码与基础输入/校验，再理解 `TextFieldState` 如何保存文本和选区；后面的迁移与外部同步属于进阶部分。IME 是输入法编辑器（软键盘与输入法的输入过程）；UDF 是单向数据流，即事件传入、状态流出，第 68 章展开。`snapshotFlow` 的基础见第 50 章，ViewModel 同步见第 68 章，生命周期收集见第 70 章；首次阅读可以跳过这些跨章节组合例子，学完相应章节再回看。

## 3. 项目源码片段

项目现有代码（value-based，节选：省略顶部 import、`Column` 以外的层级与底部回显文字）：

<!-- compile -->
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

与上面等价、本章主线的 state-based 写法（**不是项目现有代码**，官方当前推荐）。节选：只列出本例相关的新 import，其余 import 与 `Column` 以外的层级、底部回显文字省略：

<!-- compile -->
```kotlin
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.maxLength
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.core.text.isDigitsOnly
// OutlinedTextField / Text 来自 material3，value-based 示例用的是同一个组件，此处不再重复强调

val nameState = rememberTextFieldState()
val ageState = rememberTextFieldState()
val phoneState = rememberTextFieldState()

// state-based 下没有 onValueChange，文本直接从 state 里读
val phone = phoneState.text.toString()
val phoneValid = phone.length == 11 && phone.all { it.isDigit() }
val phoneError = phone.isNotEmpty() && !phoneValid

Column(verticalArrangement = Arrangement.spacedBy(12.px)) {
    // 1) 姓名：label / placeholder 用法不变，行数改用 lineLimits
    OutlinedTextField(
        state = nameState,
        label = { Text("姓名") },
        placeholder = { Text("请输入姓名") },
        lineLimits = TextFieldLineLimits.SingleLine,
        modifier = Modifier.fillMaxWidth(),
    )

    // 2) 年龄：过滤逻辑搬进 InputTransformation
    OutlinedTextField(
        state = ageState,
        label = { Text("年龄") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        lineLimits = TextFieldLineLimits.SingleLine,
        inputTransformation = InputTransformation {
            // 接收者是 TextFieldBuffer：只要新文本里出现非数字就整体拒绝这次输入
            // （isDigitsOnly 来自 androidx.core.text，所以上面必须 import androidx.core.text.isDigitsOnly）
            if (!asCharSequence().isDigitsOnly()) revertAllChanges()
        },
        modifier = Modifier.fillMaxWidth(),
    )

    // 3) 手机号：isError + supportingText 不变，只用 state 读文本
    OutlinedTextField(
        state = phoneState,
        label = { Text("手机号") },
        isError = phoneError,
        supportingText = {
            Text(if (phoneError) "手机号必须是 11 位数字" else "已输入 ${phone.length}/11 位")
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        lineLimits = TextFieldLineLimits.SingleLine,
        inputTransformation = InputTransformation.maxLength(11), // 顺带限制最多 11 位
        modifier = Modifier.fillMaxWidth(),
    )
}
```

> 与项目源码的差异只有一处是行为上的：上面手机号加了 `InputTransformation.maxLength(11)`，方便演示过滤；想和项目现状完全一致，去掉这一行即可。

## 4. 逐步解释

Material3 的 `TextField` / `OutlinedTextField` 各有**两个重载**：一个是旧的 `value: String` + `onValueChange: (String) -> Unit`（value-based），一个是新的 `state: TextFieldState`（state-based）。两者都在，没有谁被删掉；但官方当前推荐 state-based，理由是：`onValueChange` 回调被移除后，你没法在“每次按键”的回调里顺手塞异步行为（例如网络请求、写数据库），文本状态也不再需要你手工从回调往 `mutableStateOf` 里搬；官方文档明确写了 state“survives recomposition, configuration, and process death”。

**状态从哪来。** `TextFieldState` 来自 `androidx.compose.foundation.text.input`，在 composable 里用 `rememberTextFieldState(initialText = "")` 创建。这个函数内部用的就是 `rememberSaveable` 那套保存/恢复机制（配合 `TextFieldState.Saver`），所以旋转屏幕、进程重建之后文本还在——这比原来的 `remember { mutableStateOf("") }` 更强，原来你还得自己换成 `rememberSaveable`。`TextFieldState` 属于 foundation 模块、没有任何 UI 依赖，官方也鼓励把它放到 ViewModel 里（需要自己处理保存恢复）。

**怎么读。** `state.text` 是当前的 `CharSequence`，`state.selection` 是选区；它们是 Compose 的 snapshot 状态，读了它们的 composable 会在文本变化时自动重组。所以手机号校验与实时回显的写法几乎没变：`val phone = phoneState.text.toString()` 之后，`phoneValid` / `phoneError` 还是纯 Kotlin 布尔运算，底部回显也还是把 `nameState.text` / `ageState.text` / `phoneState.text` 插进模板串。整条链路仍然是单向数据流：用户输入 → `TextFieldState` → 界面用 state 渲染。

**样式与行数。** `OutlinedTextField` 是描边外框，`TextField` 是填充式（带下划线），两者参数相同、只是外观不同，Material3 里 `OutlinedTextField` 更常用。`label` 是浮动标签，有内容或聚焦时会缩小上浮到边框上；`placeholder` 只在内容为空时显示。行数控制换了参数：旧的 `singleLine = true` 改成 `lineLimits = TextFieldLineLimits.SingleLine`；旧的 `maxLines = 10` 改成 `TextFieldLineLimits.MultiLine(maxHeightInLines = 10)`（构造参数名是 `minHeightInLines` 与 `maxHeightInLines`）。注意 `singleLine` / `maxLines` 只存在于 value-based 重载，state-based 重载没有这些参数。密码框用 `SecureTextField(state = ...)`（描边版是 `OutlinedSecureTextField`），它本身只支持单行、没有 `lineLimits` 参数，默认就带遮蔽与适合密码的键盘配置。

**键盘。** 两种写法都用 `KeyboardOptions(keyboardType = ...)`：`KeyboardType.Number` 弹纯数字键盘，`KeyboardType.Phone` 弹适合手机号的数字键盘（带 `+ * #`）。它只是“提示系统弹哪种键盘”，不保证用户只能输入这些字符（粘贴、硬件键盘、无障碍服务都可能绕过），所以业务上仍要自己过滤。

**输入过滤：value-based 与 state-based 对照。** 这是迁移文档的重点。下面以“手机号只允许数字、最多 11 位”为例，两种写法各来一遍：

```kotlin
// 写法一（value-based，旧项目的做法）：在 onValueChange 里用普通 Kotlin 过滤，再写回状态
// 节选：phone / OutlinedTextField 的 import 沿用上面的项目源码片段
OutlinedTextField(
    value = phone,
    onValueChange = { input -> phone = input.filter { it.isDigit() }.take(11) },
    label = { Text("手机号") },
    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
    singleLine = true,
    modifier = Modifier.fillMaxWidth(),
)
```

```kotlin
// 写法二（state-based，本章推荐）：用户输入先经过 InputTransformation，通过后才写入 TextFieldState
// 节选：phoneState 沿用上文，import 见本段前面的 InputTransformation / TextFieldLineLimits
OutlinedTextField(
    state = phoneState,
    inputTransformation = InputTransformation.maxLength(11).then {
        // 接收者是 TextFieldBuffer；不是纯数字就 revertAllChanges() 整体拒绝
        if (!asCharSequence().isDigitsOnly()) revertAllChanges()
    },
    label = { Text("手机号") },
    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
    lineLimits = TextFieldLineLimits.SingleLine,
    modifier = Modifier.fillMaxWidth(),
)
```

**`InputTransformation` 的规则。** 它是一个单函数接口，运行在 `TextFieldBuffer` 的接收者作用域里：内置的 `maxLength(...)`、`allCaps()` 可以直接用；自定义就写 `InputTransformation { ... }`，在缓冲区上调用 `delete(...)` / `replace(...)` / `insert(...)` 改写，或者用 `revertAllChanges()` 整体拒绝这次输入。多个过滤器用 `.then(...)` 串联，前一个的修改对后一个可见。两个关键性质：它只在**用户输入**时运行（软硬键盘、粘贴、拖放、无障碍服务、测试），你用代码改 state 不会经过它；它产生的结果**会写入** `TextFieldState`。

注意一个容易漏的 import：`asCharSequence()` 返回的是 `CharSequence`，官方样例里的 `isDigitsOnly()` 不是 Kotlin 标准库函数，而是 `androidx.core.text` 里的扩展函数（来自 `androidx.core:core-ktx`，Android 工程默认就有），所以要写 `import androidx.core.text.isDigitsOnly`。不肯引入它的话，用标准库的 `asCharSequence().all { it.isDigit() }` 效果一样。

**显示格式化：`OutputTransformation`。** 它只影响“画出来的样子”，不改变 state 里的文本，而且不需要你像旧的 `VisualTransformation` 那样自己算 offset mapping：

```kotlin
// 节选：只列出本例相关的新 import
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.insert

// 例：11 位数字显示成 138-0000-0000；state 里仍是 11 位纯数字
val phoneDisplay = OutputTransformation {
    if (length > 3) insert(3, "-")
    if (length > 8) insert(8, "-")
}

OutlinedTextField(
    state = phoneState,
    outputTransformation = phoneDisplay,
    label = { Text("手机号") },
    lineLimits = TextFieldLineLimits.SingleLine,
)
```

`InputTransformation` 与 `OutputTransformation` 的区别就一句话：前者改的是**数据**（结果进 `state`），后者改的是**显示**（`state` 不动）。做校验、存库、发请求时读到的都是原始文本。

**校验反馈。** 逻辑和原来完全一样：`phoneValid = phone.length == 11 && phone.all { it.isDigit() }`，再用 `phoneError = phone.isNotEmpty() && !phoneValid`，这样“一进页面还没输入”时不会立刻报红。把 `phoneError` 传给 `isError`，描边和标签自动变错误色；`supportingText` 是输入框下方的辅助说明区，`isError = true` 时它也会自动使用错误色，于是同一处可以显示“手机号必须是 11 位数字”或“已输入 x/11 位”。

**键盘动作：`imeAction` 与两种响应方式。** `KeyboardOptions` 除了 `keyboardType`，还有 `imeAction`：它决定软键盘右下角动作键显示什么、按下算什么动作——`ImeAction.Search` 显示搜索键、`ImeAction.Done` 表示完成、`ImeAction.Next` 用于多栏表单跳到下一栏。要“响应用户按了这个键”，两种写法的参数不同：value-based 用 `keyboardActions = KeyboardActions(onSearch = { ... })`，每个动作一个回调；state-based 没有 `keyboardActions` 参数，对应参数是 `onKeyboardAction: KeyboardActionHandler`——一个只有 `onKeyboardAction` 方法的接口，Kotlin 里这类单方法接口可以用 lambda 简写（SAM 转换）：`KeyboardActionHandler { performDefaultAction -> ... }`；不拦截默认行为时调用 `performDefaultAction()` 交还给组件。`keyboardOptions` 本身两种写法通用。与 `keyboardType` 一样，`imeAction` 也只是对输入法的提示，输入法可以不理会，业务永远不要只靠它保证流程。

**程序化焦点：`FocusRequester`。** 三步走：`remember { FocusRequester() }` 创建；`Modifier.focusRequester(requester)` 挂到目标输入框；在事件回调或 `LaunchedEffect` 里调用 `requestFocus(...)`——绝不能在组合主体里直接调用，否则每次重组都会重复请求。一个版本敏感点：在本仓库基线（ui 1.11.3）中，无参的 `requestFocus()` 已弃用，推荐带方向参数的 `requestFocus(FocusDirection.Enter)`，方向信息还能帮助无障碍服务正确播报焦点变化。焦点详解见[官方文档](https://developer.android.com/develop/ui/compose/touch-input/focus/request-focus)。

下面把两者合在一个可独立运行的搜索框里。初始界面：一个搜索输入框、一个「聚焦搜索框」按钮。操作：点按钮，焦点与软键盘跳到输入框；输入任意文字后按软键盘右下角的搜索键。期望观察：底部出现「已触发搜索：xxx」；再次点按钮，焦点回到输入框。

<!-- compile -->
```kotlin
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.focusRequester

var keyword by remember { mutableStateOf("") }
var searched by remember { mutableStateOf(false) }
val searchFocus = remember { FocusRequester() } // 第一步：创建并记住 requester

Column(verticalArrangement = Arrangement.spacedBy(12.px)) {
    OutlinedTextField(
        value = keyword,                       // value-based 写法，与项目现有代码一致
        onValueChange = { keyword = it },
        label = { Text("搜索关键词") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(
            onSearch = { searched = true },    // 用户按软键盘搜索键时触发
        ),
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(searchFocus),      // 第二步：关联到这个输入框
    )
    Button(
        onClick = { searchFocus.requestFocus(FocusDirection.Enter) }, // 第三步：事件回调里请求焦点
    ) { Text("聚焦搜索框") }
    if (searched) Text("已触发搜索：$keyword")
}
```

state-based 版本只需换掉两处参数（节选，`keywordState` / `searched` 沿用同类状态，`KeyboardOptions` / `ImeAction` 同上）：

```kotlin
// 节选：state-based 的键盘动作不走 keyboardActions，改传 onKeyboardAction
OutlinedTextField(
    state = keywordState,
    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
    onKeyboardAction = KeyboardActionHandler { performDefaultAction ->
        performDefaultAction() // 不拦截默认行为；需要时再接自己的逻辑
        searched = true
    },
)
```

**需要程序性改文本时。** 用 state 的方法，而不是去改一个字符串变量：

```kotlin
// 节选：只列出本例相关的新 import，Button / Text 等普通组件 import 省略
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd

// 例：点击“填入测试号”按钮
Button(onClick = { phoneState.setTextAndPlaceCursorAtEnd("13800000000") }) { Text("填入测试号") }

// 需要按当前选区做复杂编辑时用 edit { }，缓冲区同样是 TextFieldBuffer
// phoneState.edit { insert(0, "+86 ") }
```

**进阶：同步到组合外部。** 组合外部（例如 `LaunchedEffect`、ViewModel）想观察变化，可用 `snapshotFlow { state.text }` 收集，再更新 ViewModel。`snapshotFlow` 见第 50 章，`collectLatest` 及取消行为见第 73 章；第一次读到这里可先跳过。

**两种写法的取舍。** 新代码、尤其是会涉及粘贴 / IME 与状态同步的表单，优先 state-based；`value + onValueChange` 仍然受支持，适合旧项目与最简单的场景——例如界面状态本来就是 `StateFlow<UiState>` 里的一堆 `String`（第 68 章的 UDF 架构），改起来牵动一堆代码，此时保留 value-based 完全合理。官方迁移文档也给了折中方案：UI 用 state-based，`uiState: StateFlow` 保持不变，用 `snapshotFlow` 把 `state.text` 同步回 ViewModel。

**为什么不需要 `dispose`。** value-based 下没有需要释放的对象：没有控制器、没有监听器、没有订阅，文本就是普通状态。state-based 下的 `TextFieldState` 也是纯数据状态持有者（只依赖 Compose 的 snapshot 系统），由 `rememberTextFieldState()` 创建并管理，同样不需要业务代码写释放逻辑。这和 Flutter 里必须 `dispose()` 的 `TextEditingController` 形成最直接的对照。

## 5. 实际执行过程

1. 首次组合：`rememberTextFieldState()` 创建三个空 `TextFieldState`（内部经 `rememberSaveable` + `TextFieldState.Saver` 注册保存/恢复），输入框显示 `label` 与 `placeholder`。
2. 用户点击年龄框：`keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)` 让系统弹出数字键盘。
3. 用户按键：输入先经过 `InputTransformation`，接收者是 `TextFieldBuffer`；`asCharSequence().isDigitsOnly()` 通过才提交，不通过就 `revertAllChanges()` 拒绝这次输入。
4. 通过后的文本被写入 `ageState`；读过 `text` 的 composable 标记为脏并重组，输入框显示新文本——非法字符根本不会出现在界面上。
5. 手机号每输入一位都会重新计算 `phoneValid` 与 `phoneError`：不合法时 `isError` 变红、`supportingText` 显示规则提示；合法时描边恢复正常、提示变成计数。`InputTransformation.maxLength(11)` 保证不会超过 11 位。
6. 下方 `Text` 把三个 state 的 `text` 插值进模板串，实时回显证明「输入 → state → 界面」这条单向数据流。
7. 旋转屏幕或进程重建后再次组合：`rememberTextFieldState` 负责把文本找回来，不用你写额外的保存逻辑。

## 6. Flutter / Dart 3 对照

Flutter 的 `TextField` 用 `TextEditingController` 持有文本与光标，自己创建的 controller 必须在 `dispose` 里释放；Compose 的 `TextFieldState` 扮演的是同一角色，但它是 snapshot 状态，由 `rememberTextFieldState()` 管理并自带保存/恢复，不需要释放。Flutter 的 `inputFormatters` 对应 `InputTransformation`；Flutter 没有“只改显示、不改数据”的独立机制（`inputFormatters` 的结果会写进 controller 的文本），而 Compose 的 `OutputTransformation` 正好干这件事。

```dart
// 节选：省略 import（FilteringTextInputFormatter 等在 package:flutter/services.dart）
final controller = TextEditingController();

TextField(
  controller: controller,                 // 不传 controller 也能跑，改用 onChanged 即可
  keyboardType: TextInputType.phone,
  inputFormatters: [
    FilteringTextInputFormatter.digitsOnly,        // 只允许数字
    LengthLimitingTextInputFormatter(11),          // 最多 11 位
  ],
  decoration: const InputDecoration(
    labelText: '手机号',
    hintText: '请输入手机号',
    errorText: null, // 非 null 时自动显示红色错误文案
  ),
);

@override
void dispose() {
  controller.dispose(); // 自己创建的 controller 必须在这里释放
  super.dispose();
}
```

参数对照：`label` 对应 `labelText`，`placeholder` 对应 `hintText`，`isError` + `supportingText` 合起来对应 `errorText`（以及 `helperText`），`keyboardOptions.keyboardType` 对应 `keyboardType`，`lineLimits = TextFieldLineLimits.SingleLine` 对应 `maxLines: 1`，`InputTransformation` 对应 `inputFormatters`。

本质区别有三点。第一，Flutter 的典型写法是控制器持有文本、监听后 `setState`；Compose 的 state-based 是 `TextFieldState` 持有文本，界面直接读它渲染，没有 `onValueChange` 回调，也就没法在回调里引入异步行为。第二，Flutter 的 controller 必须自己 `dispose`；Compose 的输入状态由框架与 `rememberTextFieldState` 管理，业务代码不需要任何释放代码。第三，Compose 把“过滤输入”和“美化显示”拆成了 `InputTransformation` 与 `OutputTransformation` 两步，Flutter 里这两件事通常都塞在 `inputFormatters` 中，格式化字符会一起写进 controller 的数据。

## 7. 常见错误

- 在 state-based 重载里继续传 `value` / `onValueChange`，或在 value-based 重载里传 `state`：编译不过——先分清用的是哪个重载
- state-based 里还写 `singleLine = true` 或 `maxLines = 10`：这两个参数只属于 value-based 重载，应改用 `lineLimits`
- state-based 里继续用 `visualTransformation`：该参数属于 value-based 重载，改用 `outputTransformation`（并且不用自己算 offset mapping）
- 在 `InputTransformation` 里去改 `TextFieldState` 或做异步操作：它的作用是只改 `TextFieldBuffer`，异步/副作用放到别处
- 忘记 `InputTransformation` 只对用户输入生效：用 `state.setTextAndPlaceCursorAtEnd(...)` 程序性写入时不会经过过滤器，需要自己保证数据合法
- 把 `InputTransformation` 与 `OutputTransformation` 混为一谈：前者改变 state 里的数据，后者只影响显示
- 以为 `keyboardType = KeyboardType.Number` 就能保证只能输入数字，忘记做过滤
- 校验只在提交时算一次，或者一进页面就把 `isError` 置为 `true`，界面开局全是红色
- 把 `placeholder` 当 `label` 用，聚焦后占位文字消失、用户不知道这一栏填什么
- 沿用 Flutter 习惯在 Compose 里找 `dispose`：两种写法都没有需要业务代码释放的资源
- 旧代码里看到 `value + onValueChange` 就以为过时了：它仍然受支持，只是新代码优先 state-based
- 在组合主体里直接调 `requestFocus(...)`：应在事件回调或 `LaunchedEffect` 中调用，否则每次重组重复执行
- 忘了把 `Modifier.focusRequester(...)` 挂到目标输入框，调用 `requestFocus` 时报 `FocusRequester is not initialized`
- 在 state-based 重载里找 `keyboardActions` 参数：它只属于 value-based，state-based 用 `onKeyboardAction`（`KeyboardActionHandler`）
- 以为 `imeAction` 能保证用户「必须完成动作」：输入法可以不响应它，业务校验仍要自己做

## 8. 本章检查题

1. state-based 输入框靠哪个参数驱动？`TextFieldState` 怎么创建，还需要自己写保存/恢复吗？
2. value-based 与 state-based 应该怎么取舍？
3. 原来的 `singleLine = true` 和 `maxLines = 10` 在 state-based 里分别怎么写？
4. “手机号只允许数字”用 value-based 与 `InputTransformation` 分别怎么写？
5. `InputTransformation` 与 `OutputTransformation` 的区别是什么？哪一个会改变 `TextFieldState` 里的文本？
6. 想用代码把文本改成 `"13800000000"`、想在组合外监听文本变化，分别怎么做？
7. `KeyboardOptions(keyboardType = KeyboardType.Number)` 能保证用户只输入数字吗？应该怎么做？
8. 项目里 `phoneError` 为什么写成 `phone.isNotEmpty() && !phoneValid` 而不是直接 `!phoneValid`？为什么不需要 `dispose`？
9. 想让软键盘右下角显示搜索键，并在用户按下时触发搜索，value-based 与 state-based 分别用哪些参数？
10. `FocusRequester` 的三步是什么？为什么不能在组合主体里调用 `requestFocus`？
11. 动手：给上面的搜索框例子加一个「清除」按钮，点击后清空 `keyword`、把 `searched` 复位为 `false`，并重新聚焦输入框。

答案：

1. 靠 `state: TextFieldState` 驱动。在 composable 里用 `rememberTextFieldState(initialText = "")` 创建；它内部使用 `rememberSaveable`（配合 `TextFieldState.Saver`），保存与恢复都由它负责，不需要自己写。
2. 新代码优先 state-based：没有 `onValueChange` 回调、状态能扛过重组/配置变化/进程死亡、过滤与格式化分工清晰。`value + onValueChange` 仍然受支持，适合旧项目与最简单场景，例如 UI 状态本来就是 `StateFlow<UiState>` 里的 `String`，改造代价大于收益；折中做法是 UI 用 state-based，再用 `snapshotFlow { state.text }` 把变化同步回 ViewModel。
3. `singleLine = true` 改成 `lineLimits = TextFieldLineLimits.SingleLine`；`maxLines = 10` 改成 `lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = 10)`。
4. value-based：`onValueChange = { input -> phone = input.filter { it.isDigit() }.take(11) }`；state-based：`inputTransformation = InputTransformation.maxLength(11).then { if (!asCharSequence().isDigitsOnly()) revertAllChanges() }`。
5. `InputTransformation` 处理用户输入，结果写进 `TextFieldState`，所以它改变数据；`OutputTransformation` 只影响画出来的文本，不改变 state，也不需要自己计算 offset mapping（旧的 `VisualTransformation` 需要）。
6. 程序性修改用 `state.setTextAndPlaceCursorAtEnd(text)`，或按选区做复杂编辑时用 `state.edit { ... }`（接收者是 `TextFieldBuffer`）；组合外观察变化用 `snapshotFlow { state.text }`。
7. 不能。它只是提示系统弹出哪种键盘，粘贴、硬件键盘等仍可能带进非数字字符。业务上要用 `InputTransformation`（或 value-based 下在 `onValueChange` 里）过滤。
8. 如果直接取反，一进页面还没输入时 `phoneValid` 就是 `false`，输入框会立刻变红；加上 `isNotEmpty()` 后只有「已输入且不合法」才报错。不需要 `dispose` 是因为两种写法都没有业务代码要释放的资源：value-based 只是普通状态，state-based 的 `TextFieldState` 也是由 `rememberTextFieldState` 管理的纯数据状态持有者。
9. 两种写法都用 `keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search)` 提示输入法；响应动作 value-based 用 `keyboardActions = KeyboardActions(onSearch = { ... })`，state-based 用 `onKeyboardAction = KeyboardActionHandler { performDefaultAction -> ... }`（单方法接口，SAM 写法）。
10. `remember { FocusRequester() }` 创建、`Modifier.focusRequester(...)` 关联目标组件、在事件回调或 `LaunchedEffect` 里调用 `requestFocus(FocusDirection.Enter)`。组合主体每次重组都会执行，直接调用会反复请求焦点；另外基线（ui 1.11.3）中无参 `requestFocus()` 已弃用，要带方向参数。
11. 参考实现：

```kotlin
// 节选：在 Column 里追加一个按钮，searchFocus / keyword / searched 沿用上文
OutlinedButton(onClick = {
    keyword = ""
    searched = false
    searchFocus.requestFocus(FocusDirection.Enter)
}) { Text("清除") }
```

## 9. 本章总结

Compose 输入框当前推荐 state-based：`rememberTextFieldState()` 创建 `TextFieldState`（自带 `rememberSaveable` 级别的保存恢复），`state =` 是唯一入口，文本从 `state.text` 读，界面自动重组；密码框用 `SecureTextField(state = ...)`。行数用 `lineLimits`，键盘用 `KeyboardOptions(keyboardType = ...)`，输入过滤用 `InputTransformation`（内置 `maxLength`，自定义在 `TextFieldBuffer` 上用 `delete` / `replace` / `revertAllChanges`，可 `.then(...)` 串联，结果会写入 state），显示格式化用 `OutputTransformation`（不改 state、不用算 offset mapping）。`isError` + `supportingText` 做校验反馈，程序性改文本用 `setTextAndPlaceCursorAtEnd` / `edit { }`，组合外观察用 `snapshotFlow`。键盘动作键用 `imeAction`，响应方式 value-based 是 `keyboardActions`、state-based 是 `onKeyboardAction`；程序化焦点用 `FocusRequester` 三步（创建、挂修饰符、事件回调里 `requestFocus(FocusDirection.Enter)`）。`value + onValueChange` 仍然受支持，定位是旧项目与简单场景——项目里的 `TextFieldDemo.kt` 目前就是这种写法；对照 Flutter 的 `TextField` + `TextEditingController` + `inputFormatters`，能最清楚地看出「谁持有文本、谁负责过滤、谁负责释放」这三件事在两边的分工差别。
