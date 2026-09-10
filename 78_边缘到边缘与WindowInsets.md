# 第 78 章：边缘到边缘与 Window Insets

## 1. 本章目标

学完后，你应当能够：

- 理解 edge-to-edge 模式下内容延伸到系统栏后面带来的遮挡问题
- 掌握 systemBars、safeDrawing、displayCutout、ime 等 WindowInsets，以及 safeGestures / safeContent 的含义
- 会用 windowInsetsPadding、imePadding、consumeWindowInsets 处理间距
- 知道 Scaffold 的默认行为与手动处理如何正确配合

## 2. 项目中的位置

进阶篇不再局限于 compose_demo 项目，本章使用通用 Compose API。

- `androidx.activity.enableEdgeToEdge`（Activity 级 API）
- `androidx.compose.foundation.layout.WindowInsets` 及 systemBars、safeDrawing、displayCutout、ime 等成员
- `Modifier.windowInsetsPadding` / `displayCutoutPadding` / `imePadding`
- `Modifier.consumeWindowInsets`
- `androidx.compose.material3.Scaffold` 的 contentWindowInsets

## 3. 项目源码片段

场景一：Activity 开启 edge-to-edge，让内容延伸到系统栏后面，再由自己把 Scaffold 算出的 insets 应用到内容上。

```kotlin
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge() // 系统栏透明，内容延伸到状态栏/导航栏后面
        super.onCreate(savedInstanceState)
        setContent {
            // contentWindowInsets 默认来自 ScaffoldDefaults.contentWindowInsets（系统栏类 insets），
            // 不是 safeDrawing；而且 Scaffold 只把它算成 PaddingValues 交给我，不会自己应用
            // 对应方向有 topBar / bottomBar 时，该方向的 insets 交给栏自己处理，不计入 innerPadding
            Scaffold(
                topBar = { TopAppBar(title = { Text("首页") }) }
            ) { innerPadding ->
                Column(
                    Modifier
                        .padding(innerPadding) // 由我来应用
                        .consumeWindowInsets(innerPadding) // 声明已消费，内层不再重复叠加
                ) { /* 业务内容 */ }
            }
        }
    }
}
```

场景二：非 Scaffold 的自定义页面手动处理 Insets，并让输入框避开软键盘。

<!-- compile
prelude: var input by remember { mutableStateOf("") }
-->
```kotlin
@Composable
fun ChatPage() {
    Column(
        Modifier
            .fillMaxSize()
            // safeDrawing = systemBars ∪ displayCutout ∪ ime（键盘也算在 safeDrawing 里）
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        LazyColumn(Modifier.weight(1f)) { /* 消息列表 */ }
        // 键盘弹出时输入框自动上移，避免被盖住
        TextField(
            value = input,
            onValueChange = { input = it },
            modifier = Modifier.fillMaxWidth().imePadding()
        )
    }
}
```

## 4. 逐步解释

1. 为什么需要 edge-to-edge：老式做法是把状态栏设成固定颜色（statusBarColor），内容区天然在系统栏下方，但屏幕利用率低、视觉割裂。edge-to-edge 让应用内容绘制到状态栏与导航栏后面，更沉浸，代价是内容可能被系统栏遮挡——处理这个遮挡就是本章核心。

   时效背景（必须清楚，因为它决定了本章为什么重要）：Android 14（API 34）及以下，应用默认不绘制到系统栏与挖孔后面。Android 15（API 35）起，targetSdk 35 的应用被**强制** edge-to-edge，但仍可以用 `R.attr.windowOptOutEdgeToEdgeEnforcement = true` 退出；在低版本设备上不会自动生效，需要自己调 `enableEdgeToEdge()`。Android 16（API 36）起，targetSdk 36 时 `windowOptOutEdgeToEdgeEnforcement` 已被废弃并禁用，**在 Android 16 设备上无法再退出** edge-to-edge（targetSdk 36 跑在 Android 15 设备上时该属性仍然有效）。也就是说，「要不要画到系统栏后面」已不由应用单方面决定，insets 是唯一可靠的避让手段——这正是必须正确理解 insets 的现实原因。

2. WindowInsets 是什么：它是系统向应用描述"屏幕被系统元素占用多少"的数据类，每个 insets 包含四个方向的值。常见类型：systemBars（状态栏 + 导航栏）、displayCutout（挖孔/刘海区域）、ime（软键盘），以及把底层类型打包起来的三个「安全」类型。注意 safeDrawing 不是独立的系统数据，而是 systemBars、displayCutout 与 ime 的并集，即 `safeDrawing = systemBars ∪ displayCutout ∪ ime`，代表「内容不该被别的东西盖住」的区域——注意它把软键盘也算进去了。另外两个安全类型：safeGestures 是手势容易和系统手势冲突的区域（systemGestures、mandatorySystemGestures、waterfall、tappableElement 的并集），适合包住要滑动的组件；safeContent 是 safeDrawing 与 safeGestures 的并集，视觉与手势都不冲突。

3. 三种 modifier 的分工：windowInsetsPadding(insets) 按指定 insets 给组件加 padding；displayCutoutPadding() 只处理挖孔区域；imePadding() 只跟随键盘高度，键盘收起时为 0。关键点是这些 modifier 是响应式的——insets 变化（旋转、键盘弹出、折叠屏展开）会自动触发重组重新计算 padding，无需手动监听。

4. Scaffold 的 insets 行为（最容易误解的一段，务必分清）：`contentWindowInsets` 的默认值来自 `ScaffoldDefaults.contentWindowInsets`（系统栏类 insets），不是 `safeDrawing`。而且 Scaffold **不会自己把 insets 应用到内容上**——它只是把这些 insets 算成一个 `PaddingValues`（也就是 content lambda 的 `innerPadding`）交给你，官方原话是："Scaffold does not apply the insets to content; this responsibility is yours."，应用与否是你的责任。上下方向还要看有没有栏，官方原话是："Scaffold will take the insets into account from the top/bottom only if the `topBar`/`bottomBar` are not present, as the scaffold expects `topBar`/`bottomBar` to handle insets instead."，即有对应方向的 topBar / bottomBar 时，该方向的 insets 由栏自己处理，`innerPadding` 主要剩下栏与内容之间的间距和没有栏的方向的安全区。此外，已经被子布局或 `consumeWindowInsets` 消费过的 insets 会从 `contentWindowInsets` 中排除；正确写法是给内容根同时用 `padding(innerPadding)` 与 `consumeWindowInsets(innerPadding)`，既应用了间距，又告诉内层「这份 insets 已经用掉了」。

5. consumeWindowInsets：当一个组件已消费（应用）了 insets，内层组件再读同一 insets 会重复计算。Modifier.consumeWindowInsets(insets) 标记"这段 insets 已处理"，让内层不再叠加。典型场景是 Scaffold 的 bottomBar 里嵌套需要 imePadding 的输入区。

6. 键盘与 imePadding：软键盘弹出属于 ime insets 变化。输入框底部加 imePadding()，键盘弹出时输入框自动上移；配合默认的 insets 动画，效果平滑。ime insets 通常在输入框聚焦时才非零，所以 imePadding 应加在输入框自身或其直接父容器上。

记住：edge-to-edge 是"让内容延伸到系统栏后"，WindowInsets 是"告诉内容哪里被占了"，padding 是"把内容推回安全区"——三层各司其职。

## 5. 实际执行过程

1. Activity onCreate 调用 enableEdgeToEdge()：系统栏变透明，窗口布局延伸到系统栏区域。
2. Compose 内容开始组合，Scaffold 读取 `ScaffoldDefaults.contentWindowInsets`（系统栏类 insets；某个方向有对应的 topBar / bottomBar 时，该方向的 insets 不计入），把上下左右算成一份 `PaddingValues`。
3. Scaffold 把这份 `PaddingValues` 作为 `innerPadding` 交给内容 lambda——注意它不会替你应用。内容根自己执行 `padding(innerPadding)` 避开栏与系统栏，再执行 `consumeWindowInsets(innerPadding)` 声明已消费，内层就不会重复叠加。
4. 用户点击输入框，软键盘弹出：系统分发 ime insets 变化，imePadding() 监听到变化，输入框所在列自动上移键盘高度。
5. 用户旋转屏幕或折叠屏展开：insets 重新计算，所有 windowInsetsPadding 触发重组，间距自动修正，无需任何手动监听代码。

## 6. Flutter / Dart 3 对照

```dart
Scaffold(
  // 键盘弹出时自动缩小 body，避免输入框被盖（对应 imePadding）
  resizeToAvoidBottomInset: true,
  body: SafeArea( // 对应 windowInsetsPadding(WindowInsets.safeDrawing)
    child: Column(
      children: [
        Expanded(child: ListView(...)),
        TextField(...),
      ],
    ),
  ),
)
// 需要精确控制时读 MediaQuery：
final padding = MediaQuery.viewPaddingOf(context);   // 状态栏/导航栏占用
final bottom = MediaQuery.viewInsetsOf(context).bottom; // 键盘高度
```

对照表：

| 概念 | Compose | Flutter |
|---|---|---|
| 开启 edge-to-edge | enableEdgeToEdge() | SystemChrome.setEnabledSystemUIMode |
| 安全区避让 | windowInsetsPadding(safeDrawing) | SafeArea |
| 系统栏占用 | WindowInsets.systemBars | MediaQuery.viewPadding |
| 键盘遮挡 | imePadding() | resizeToAvoidBottomInset / viewInsets |

相似点：核心思想一致——"系统占用的区域是动态数据，布局根据它调整"；SafeArea 与 windowInsetsPadding 用途相同；键盘 inset 的处理目标相同。

本质区别：Compose 把 insets 建模为 WindowInsets 对象，通过 modifier 声明式消费，变化自动触发重组；Flutter 的 SafeArea / MediaQuery 是"读一次值"的查询式 API，键盘变化依赖 Scaffold 的 resize 机制或手动监听。另外 Flutter 没有 consume 概念——子组件各自读 MediaQuery，容易出现重复 padding，需要手动组织结构。

## 7. 常见错误

1. 开启 edge-to-edge 后内容顶到状态栏：只调了 enableEdgeToEdge() 却没有任何 insets 处理，标题被状态栏盖住。要么用 Scaffold 并正确应用它给出的 innerPadding，要么手动 windowInsetsPadding。
2. 双重间距：已经应用了 Scaffold 的 innerPadding，又在内容里手动加 windowInsetsPadding(WindowInsets.safeDrawing)，顶部出现两倍空白；或者忘了 consumeWindowInsets(innerPadding)，让内层布局再次避让同一份 insets。
3. 忽略键盘 inset：输入框没加 imePadding()，键盘弹出时输入框被盖住无法输入。
4. 挖孔屏内容被挡：只处理了 systemBars 没处理 displayCutout。直接用 safeDrawing 即可覆盖两种情况。
5. 用固定 dp 模拟 insets：写死 padding 值，换设备、横竖屏就错位。一切 insets 相关间距都应来自 WindowInsets。

## 8. 本章检查题

1. safeDrawing 包含哪些 insets？
2. windowInsetsPadding 与 Modifier.padding 的关系是什么？
3. ime insets 解决什么问题？通常加在哪里？
4. 已经有了 Scaffold 的 innerPadding，为什么不应再手动加 safeDrawing padding？

答案：

1. safeDrawing = systemBars ∪ displayCutout ∪ ime，即状态栏、导航栏、挖孔/刘海区域与软键盘的并集，是「内容不该被其它东西盖住」的安全区域。（相关：safeGestures 是手势冲突区域，safeContent = safeDrawing ∪ safeGestures。）
2. windowInsetsPadding 本质上是动态的 padding——它按当前 insets 计算 padding 值，insets 变化时自动重组更新，等价于"随时保持正确的 padding"；普通 padding 是写死的。
3. 解决软键盘弹出遮挡输入框的问题。通常加在输入框自身或其直接父容器（如 Column）上，键盘弹出时内容自动上移。
4. 因为 Scaffold 的 contentWindowInsets 默认取 `ScaffoldDefaults.contentWindowInsets`，它只把结果作为 innerPadding 交给我们、并不会自己应用；如果已经用了 innerPadding 再手动加 windowInsetsPadding 就会得到双重间距。正确做法是内容根用 `padding(innerPadding)` + `consumeWindowInsets(innerPadding)`；只有非 Scaffold 的自定义页面才需要从零手写 insets 处理。

## 9. 本章总结

edge-to-edge 从 Android 15（targetSdk 35）起被强制、到 Android 16（targetSdk 36）连 opt-out 都被废弃并禁用，应用不再有「不参与」的选项，WindowInsets 因此成为这套体系的地基：systemBars、displayCutout、ime 等底层类型描述系统占用，safeDrawing（= systemBars ∪ displayCutout ∪ ime）、safeGestures、safeContent 三个安全类型把常用组合打包，windowInsetsPadding、imePadding、consumeWindowInsets 三个 modifier 声明式消费它们。Scaffold 只负责把 insets 算成 innerPadding 交给你、并让对应方向的 topBar / bottomBar 自己处理该方向的 insets，应用与否是你的责任；自定义页面时手动处理并避免重复消费，才能做出既沉浸又不出错的界面。
