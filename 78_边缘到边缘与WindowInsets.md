# 第 78 章：边缘到边缘与 Window Insets

## 1. 本章目标

学完后，你应当能够：

- 理解 edge-to-edge 模式下内容延伸到系统栏后面带来的遮挡问题
- 掌握 systemBars、safeDrawing、displayCutout、ime 四类 WindowInsets
- 会用 windowInsetsPadding、imePadding、consumeWindowInsets 处理间距
- 知道 Scaffold 的默认行为与手动处理如何正确配合

## 2. 项目中的位置

进阶篇不再局限于 compose_demo 项目，本章使用通用 Compose API。

- `androidx.activity.enableEdgeToEdge`（Activity 级 API）
- `androidx.compose.foundation.layout.WindowInsets` 及 systemBars、safeDrawing、displayCutout、ime 等成员
- `Modifier.windowInsetsPadding` / `windowInsetsDisplayCutoutPadding` / `imePadding`
- `Modifier.consumeWindowInsets`
- `androidx.compose.material3.Scaffold` 的 contentWindowInsets

## 3. 项目源码片段

场景一：Activity 开启 edge-to-edge，让内容延伸到系统栏后面，再交给 Scaffold 自动处理安全区。

```kotlin
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge() // 系统栏透明，内容延伸到状态栏/导航栏后面
        super.onCreate(savedInstanceState)
        setContent {
            // Scaffold 默认 contentWindowInsets = safeDrawing，
            // 自动给内容区加上状态栏、导航栏、挖孔屏的安全 padding
            Scaffold(
                topBar = { TopAppBar(title = { Text("首页") }) }
            ) { innerPadding ->
                Column(Modifier.padding(innerPadding)) { /* 业务内容 */ }
            }
        }
    }
}
```

场景二：非 Scaffold 的自定义页面手动处理 Insets，并让输入框避开软键盘。

```kotlin
@Composable
fun ChatPage() {
    Column(
        Modifier
            .fillMaxSize()
            // safeDrawing = systemBars + displayCutout
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

2. WindowInsets 是什么：它是系统向应用描述"屏幕被系统元素占用多少"的数据类，每个 insets 包含四个方向的值。常见类型：systemBars（状态栏 + 导航栏）、displayCutout（挖孔/刘海区域）、ime（软键盘）、safeDrawing。注意 safeDrawing 不是独立的系统数据，而是 systemBars 与 displayCutout 的并集，代表"任何情况下都不能被盖住的安全区域"。

3. 三种 modifier 的分工：windowInsetsPadding(insets) 按指定 insets 给组件加 padding；windowInsetsDisplayCutoutPadding() 只处理挖孔区域；imePadding() 只跟随键盘高度，键盘收起时为 0。关键点是这些 modifier 是响应式的——insets 变化（旋转、键盘弹出、折叠屏展开）会自动触发重组重新计算 padding，无需手动监听。

4. Scaffold 的默认行为：Scaffold 默认 contentWindowInsets = WindowInsets.safeDrawing，并把计算好的间距通过 innerPadding 传出。所以使用 Scaffold 的页面，内容区已自动避让系统栏，不要再手动加 windowInsetsPadding，否则出现双重间距。

5. consumeWindowInsets：当一个组件已消费（应用）了 insets，内层组件再读同一 insets 会重复计算。Modifier.consumeWindowInsets(insets) 标记"这段 insets 已处理"，让内层不再叠加。典型场景是 Scaffold 的 bottomBar 里嵌套需要 imePadding 的输入区。

6. 键盘与 imePadding：软键盘弹出属于 ime insets 变化。输入框底部加 imePadding()，键盘弹出时输入框自动上移；配合默认的 insets 动画，效果平滑。ime insets 通常在输入框聚焦时才非零，所以 imePadding 应加在输入框自身或其直接父容器上。

记住：edge-to-edge 是"让内容延伸到系统栏后"，WindowInsets 是"告诉内容哪里被占了"，padding 是"把内容推回安全区"——三层各司其职。

## 5. 实际执行过程

1. Activity onCreate 调用 enableEdgeToEdge()：系统栏变透明，窗口布局延伸到系统栏区域。
2. Compose 内容开始组合，Scaffold 读取 contentWindowInsets（默认 safeDrawing），计算状态栏、导航栏、挖孔区域的安全间距。
3. Scaffold 把计算结果作为 innerPadding 传给内容 lambda，内容按 innerPadding 布局，避开系统栏。
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

1. 开启 edge-to-edge 后内容顶到状态栏：只调了 enableEdgeToEdge() 却没有任何 insets 处理，标题被状态栏盖住。要么用 Scaffold，要么手动 windowInsetsPadding。
2. 双重间距：Scaffold 已经通过 innerPadding 处理 safeDrawing，又在内容里手动加 windowInsetsPadding(WindowInsets.safeDrawing)，顶部出现两倍空白。
3. 忽略键盘 inset：输入框没加 imePadding()，键盘弹出时输入框被盖住无法输入。
4. 挖孔屏内容被挡：只处理了 systemBars 没处理 displayCutout。直接用 safeDrawing 即可覆盖两种情况。
5. 用固定 dp 模拟 insets：写死 padding 值，换设备、横竖屏就错位。一切 insets 相关间距都应来自 WindowInsets。

## 8. 本章检查题

1. safeDrawing 包含哪些 insets？
2. windowInsetsPadding 与 Modifier.padding 的关系是什么？
3. ime insets 解决什么问题？通常加在哪里？
4. 为什么 Scaffold 页面不要再手动加 safeDrawing padding？

答案：

1. safeDrawing = systemBars + displayCutout，即状态栏、导航栏与挖孔/刘海区域的并集，是"任何情况下都不能被遮挡"的安全区域。
2. windowInsetsPadding 本质上是动态的 padding——它按当前 insets 计算 padding 值，insets 变化时自动重组更新，等价于"随时保持正确的 padding"；普通 padding 是写死的。
3. 解决软键盘弹出遮挡输入框的问题。通常加在输入框自身或其直接父容器（如 Column）上，键盘弹出时内容自动上移。
4. 因为 Scaffold 默认已用 contentWindowInsets = safeDrawing 处理并把结果通过 innerPadding 传出，再手动加会得到双重间距；只有非 Scaffold 的自定义页面才需要手动处理。

## 9. 本章总结

edge-to-edge 是 Android 12 之后的标准做法，WindowInsets 是这套体系的地基：systemBars、safeDrawing、displayCutout、ime 四类数据描述系统占用，windowInsetsPadding、imePadding、consumeWindowInsets 三个 modifier 声明式消费它们。用 Scaffold 时信任默认行为，自定义页面时手动处理并避免双重消费，就能做出既沉浸又不出错的界面。
