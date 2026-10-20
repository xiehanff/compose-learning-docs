# 第 71A 章：Navigation 3 与 NavDisplay

## 1. 本章目标

掌握基于本仓库版本基线的 Navigation 3（简称 Nav3），理解它“你拥有返回栈”的核心心智模型，并知道它与本书第 [4 章](04_NavHost与路由导航.md)、第 [71 章](71_导航进阶嵌套导航与类型安全导航.md) 所讲的 Nav2 有什么区别、各自适合什么场景。Navigation 3 的 API 和推荐实践仍应以对应版本的官方文档为准。

阅读路线：先完成“定义 key → 创建返回栈 → 用 NavDisplay 显示 → 添加/移除 key”的单栏主线。保存状态装饰器、自适应 Scene 与转场是后续选读；理解基础返回栈之前可以先跳过，完成主线后再逐节加回。

学完后，你应当能够：

- 说清 Nav3 是什么、为什么要专门为 Compose 做一套新导航库
- 分清 Nav3 各个构件的职责：`navigation3-runtime`、`navigation3-ui`、`lifecycle-viewmodel-navigation3`、`adaptive-navigation3`、kotlinx-serialization
- 用 `@Serializable` 的 key（`data object` / `data class`）建模目的地，用 `rememberNavBackStack` 创建返回栈
- 用 `NavDisplay` + `entryProvider` 把「栈顶 key」渲染成界面
- 用 `backStack.add(...)` / `backStack.removeLastOrNull()` 实现前进与返回
- 掌握 `entryProvider` 的 lambda 写法与 DSL 写法两种形式
- 用 `onBack` 处理系统返回，用 `entryDecorators` 给条目加保存状态与 ViewModel 能力
- 知道 `sceneStrategies` 默认是单栏，可换成自适应多栏；知道 `transitionSpec` / `popTransitionSpec` 与 metadata DSL 定制转场
- 知道使用 Nav3 需要 compileSdk 36 或更高

## 2. 项目中的位置

**本章讲的是导航库本身，不是 compose_demo 的现有代码。**

- compose_demo 项目当前用的是 **Nav2**，即 `androidx.navigation:navigation-compose`：第 [4 章](04_NavHost与路由导航.md) 讲 `rememberNavController` / `NavHost` / `composable` 的基础用法，第 [71 章](71_导航进阶嵌套导航与类型安全导航.md) 讲 `navigation<T>` 嵌套图与类型安全路由。**本章不改动这两个章节的内容，也不改 compose_demo 的任何代码。**
- 本章的 Nav3 示例是**独立可复制的最小示例**：新建一个 Empty Activity 工程，按第 3 节加依赖、粘贴代码即可运行，不依赖 compose_demo 的任何源码文件。

Nav3 由若干构件组成，职责划分如下：

| 构件 | 作用 |
| --- | --- |
| `androidx.navigation3:navigation3-runtime` | 核心 API：`NavEntry`、`EntryProvider` 及 `entryProvider` DSL、`rememberNavBackStack` |
| `androidx.navigation3:navigation3-ui` | 提供展示层：`NavDisplay`、`Scene` |
| `androidx.lifecycle:lifecycle-viewmodel-navigation3` | 让 ViewModel 的作用域绑定到返回栈里的条目 |
| `androidx.compose.material3.adaptive:adaptive-navigation3` | 自适应布局用的 `SceneStrategy` / `Scene` 与 metadata 定义 |
| `org.jetbrains.kotlinx:kotlinx-serialization-core` + 序列化插件 | 让路由 key 可以被序列化保存 |

本章版本快照：`navigation3` 使用 1.1.4，`lifecycle-viewmodel-navigation3` 使用 2.11.0，示例工程使用 `compileSdk = 36`；这些是本仓库基线（见 [文档基线与审校规范](文档基线与审校规范.md)），不是永久版本结论。官方入门文档当前要求使用 Navigation 3 时将 `compileSdk` 设置为 36 或更高；升级依赖时应重新检查该要求。

## 3. 项目源码片段

> 再次强调：以下代码不来自 compose_demo，是一份可直接复制的最小示例。

**第一步：加依赖**（`gradle/libs.versions.toml`）：

以下保留本章最小示例用到的构件；`adaptive-navigation3` 仅在实现自适应场景策略时另行添加。序列化插件版本应与项目 Kotlin 插件版本一致，这里与仓库基线统一为 2.3.21。

```toml
[versions]
nav3Core = "1.1.4"
lifecycleViewmodelNav3 = "2.11.0"
kotlinxSerializationCore = "1.9.0"
kotlinSerialization = "2.3.21"
compileSdk = "36"

# 版本快照：2026-10-17；升级依赖后请重新核对 API 和官方要求。

[libraries]
androidx-navigation3-runtime = { module = "androidx.navigation3:navigation3-runtime", version.ref = "nav3Core" }
androidx-navigation3-ui = { module = "androidx.navigation3:navigation3-ui", version.ref = "nav3Core" }
androidx-lifecycle-viewmodel-navigation3 = { module = "androidx.lifecycle:lifecycle-viewmodel-navigation3", version.ref = "lifecycleViewmodelNav3" }
kotlinx-serialization-core = { module = "org.jetbrains.kotlinx:kotlinx-serialization-core", version.ref = "kotlinxSerializationCore" }

[plugins]
jetbrains-kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlinSerialization" }
```

`app/build.gradle.kts`：

```kotlin
plugins {
    // 注册序列化插件，@Serializable 才能自动生成序列化器
    alias(libs.plugins.jetbrains.kotlin.serialization)
}

android {
    compileSdk = 36   // 本示例基线；官方入门文档要求 Nav3 使用 36 或更高
}

dependencies {
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.kotlinx.serialization.core)
}
```

**第二步：定义 key**。key 是「目的地的身份」，不是界面内容。最小写法：

<!-- compile decls -->
```kotlin
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
data object Home : NavKey                    // 无参数目的地：data object

@Serializable
data class Product(val id: String) : NavKey  // 带参数目的地：data class
```

**第三步：持有返回栈并渲染**：

<!-- compile decls -->
```kotlin
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay

@Composable
fun Nav3App() {
    // 我们自己的返回栈：记住起点 Home，并自动跨配置变化 / 进程死亡保存
    val backStack = rememberNavBackStack(Home)

    NavDisplay(
        backStack = backStack,
        // 系统返回：弹出栈顶
        onBack = { backStack.removeLastOrNull() },
        // 给每个条目补上「保存状态」与「ViewModel 作用域」能力
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        // 把「栈顶 key」翻译成 NavEntry（含 Composable 内容的类）
        entryProvider = entryProvider {
            entry<Home> {
                HomeScreen(onOpenProduct = { id -> backStack.add(Product(id)) })
            }
            entry<Product> { product ->
                ProductScreen(id = product.id, onBack = { backStack.removeLastOrNull() })
            }
        },
    )
}

@Composable
private fun HomeScreen(onOpenProduct: (String) -> Unit) {
    Button(onClick = { onOpenProduct("123") }) { Text("打开商品 123") }
}

@Composable
private fun ProductScreen(id: String, onBack: () -> Unit) {
    Button(onClick = onBack) { Text("商品 $id，点此返回") }
}
```

`entryProvider` 也可以不用 DSL，直接写 lambda：

<!-- compile
prelude: val backStack = rememberNavBackStack(Home)
prelude: @Composable fun HomeScreen(onOpenProduct: (String) -> Unit) {}
prelude: @Composable fun ProductScreen(id: String, onBack: () -> Unit) {}
-->
```kotlin
NavDisplay(
    backStack = backStack,
    onBack = { backStack.removeLastOrNull() },
    entryProvider = { key ->
        when (key) {
            is Home -> NavEntry(key) { HomeScreen(onOpenProduct = { id -> backStack.add(Product(id)) }) }
            is Product -> NavEntry(key) { ProductScreen(id = key.id, onBack = { backStack.removeLastOrNull() }) }
            else -> NavEntry(key) { Text("未知路由") }
        }
    },
)
```

## 4. 逐步解释

1. **Nav3 是什么**。Navigation 3 是一套**专门为 Jetpack Compose 设计的新导航库**，已有稳定版 1.1.x（如 1.1.4）。它和 Nav2 不是「新版本覆盖旧版本」的关系：Nav2 用字符串路由 + `NavHost` / `composable`，存量项目广泛；Nav3 用类型化 key + 自己持有的返回栈 + `NavDisplay`。教材应该同时讲历史与现状，而不是把两者对立成非黑即白。

2. **你拥有返回栈**。这是 Nav3 最核心的心智模型。返回栈不再藏在库的内部（Nav2 的 `NavController` 替你拿着），而是**由你自己声明和持有**的一个可观察列表：`rememberNavBackStack(Home)` 会返回一个 `NavBackStack`，它内部包裹 Compose 的 `SnapshotStateList`，本身也是 `MutableList`，因此任何改动都会触发读取它的 Composable 重组。你也可以按官方基础文档的写法用 `remember { mutableStateListOf(Home) }` 自己造一个；用 `rememberNavBackStack` 的好处是它自动跨配置变化与进程死亡保存。

3. **栈里放的是 key，不是内容**。这是最容易写错的地方。返回栈里存的是「目的地身份」（如 `Home`、`Product("123")`），不是 Composable 本身。好处是：推入栈只是一个轻量的数据对象；只要 key 可序列化，整个返回栈就能持久化，用户离开再回来能回到原来的页面。key 通常写成 `@Serializable` 的 `data object`（无参数）或 `data class`（带参数），并实现 `NavKey` 标记接口——`rememberNavBackStack` 要求每个元素都实现 `NavKey` 且标注 `@Serializable`。

4. **推入 / 弹出就是 add / remove**。所谓「前进」，就是往列表末尾添加一个 key：`backStack.add(Product("123"))`；所谓「返回」，就是从末尾移除：`backStack.removeLastOrNull()`。没有 `navigate()` / `popBackStack()` 这类命令式 API，导航变成了纯粹的列表操作。

5. **`NavDisplay` 负责把栈渲染成界面**。`NavDisplay` 观察这个返回栈，默认只显示**栈顶**那一个条目（单栏布局）。它拿栈顶 key 去问 `entryProvider`，得到对应的 `NavEntry`（一个包含 key、metadata 和 Composable 内容的类），然后渲染内容。默认的 `onBack` 行为就是在返回栈是 `MutableList` 时弹出最后一个元素，但显式写出 `onBack = { backStack.removeLastOrNull() }` 更清晰。

6. **`entryProvider` 的两种写法**。第一种是直接写 `{ key -> when (key) { ... } }` 的 lambda，自己用 `when` 覆盖每种 key，并用 `NavEntry(key) { ... }` 构造条目。第二种是 `entryProvider { entry<Home> { ... } entry<Product> { product -> ... } }` DSL：泛型参数指明 key 类型，代码更短，并且**自带未命中 key 的默认行为**（默认抛 `IllegalStateException("Unknown screen ...")`）。如果不想抛异常，可以在 DSL 构造时传入 fallback，例如 `entryProvider({ NavEntry(it) { Text("未知路由") } }) { ... }`。

7. **`entryDecorators` 给条目加能力**。`NavEntry` 只是想渲染的内容，它默认不具备「保存状态」和「独立 ViewModel 作用域」。装饰器可以给每个条目套上额外逻辑：`rememberSaveableStateHolderNavEntryDecorator()` 让条目内的 `rememberSaveable` 正常工作（配置变化、进程死亡后能恢复），`rememberViewModelStoreNavEntryDecorator()` 为每个条目创建一个 `ViewModelStore`，这样在条目里调用 `viewModel<XxxViewModel>()` 才会得到正确的、跟随条目出栈而清理的实例。注意 `NavDisplay` 的 `entryDecorators` 默认值是 `listOf(rememberSaveableStateHolderNavEntryDecorator())`；一旦你传入自己的列表，就应当把保存状态的那个放在第一位，除非你的装饰器自己提供了 `SaveableStateProvider`。

8. **`sceneStrategies` 决定「一次显示几个条目」**。`NavDisplay` 默认用单栏策略（`SinglePaneSceneStrategy`），也就是只显示栈顶；这是手机竖屏常见形态。传入别的策略列表就能变成「列表 + 详情」的双栏等自适应布局。1.1 起该参数接受的是 `List`（早期版本是单个 `SceneStrategy` 并用 `then` 串联）。在本仓库基线（navigation3 1.1.4，核对自 Gradle 缓存构件）中，`Scene` / `SceneStrategy` / `SinglePaneSceneStrategy` 位于 `navigation3-ui` 的 `androidx.navigation3.scene` 包，多栏自适应策略由 `adaptive-navigation3` 构件提供；构件与 API 归属在后续版本可能调整，以官方发布说明为准。

9. **转场定制**。`NavDisplay` 提供 `transitionSpec`（进入）与 `popTransitionSpec`（弹出返回）来覆盖默认转场；还可以通过 `NavEntry` 的 metadata DSL 针对单个条目定制，例如官方示例里的 `NavDisplay.PredictivePopTransitionKey` 用于预测式返回的转场。不确定具体参数名时，先不要写，保留默认行为即可。

**Nav2 vs Nav3 对比表**（重点）：

| 维度 | Nav2（`navigation-compose`，本书第 4 / 71 章） | Nav3（`navigation3-runtime` + `navigation3-ui`） |
| --- | --- | --- |
| 路由表示 | 字符串路由（`"detail/{id}"`）；2.8+ 也可用 `@Serializable` 类型安全路由 | 类型化 key，通常是 `@Serializable` 的 `data object` / `data class`（并实现 `NavKey`） |
| 谁持有返回栈 | 库持有，`NavController` 内部管理，你用 `navigate` / `popBackStack` 间接操作 | **你持有**，就是一个可观察的 `SnapshotStateList` / `NavBackStack`，用 `add` / `removeLastOrNull` 直接操作 |
| 入口组件 | `NavHost` + `composable` / `navigation` | `NavDisplay` + `entryProvider` |
| 类型安全 | 2.8 起支持，但与字符串路由并存，属于增量能力 | 生来就是类型化 key，无字符串路由概念 |
| 参数传递 | 字符串模板拼参数，或 `entry.toRoute<T>()` 解码 | 直接就是 key 对象本身，`Product(id)` 里的 `id` 有编译期类型 |
| 可用性 | 存量项目广泛使用，与 View 体系、深层链接等生态成熟 | 专门为 Compose 设计，适合全新 Compose 项目；深层链接等能力仍在快速演进 |
| 适用场景 | 老项目迁移成本低、需要与现有导航/深链方案配合 | 新项目、希望完全掌控返回栈、想用自适应多栏与类型化 key |

记住一句话：**Nav2 是「库替你管返回栈」，Nav3 是「你自己管返回栈，库帮你渲染」。**

## 5. 实际执行过程

1. 首次组合：`rememberNavBackStack(Home)` 创建返回栈 `[Home]`，并把这份数据登记到 Compose 的可保存状态中。
2. `NavDisplay` 读取返回栈，发现栈顶是 `Home`，调用 `entryProvider(Home)`。
3. `entryProvider` 的 DSL 命中 `entry<Home>`，返回 `NavEntry(Home) { HomeScreen(...) }`。
4. `NavDisplay` 用默认的单栏 `SinglePaneSceneStrategy` 把该条目渲染出来；装饰器先包一层 `SaveableStateProvider`，再包一层 ViewModel 作用域。
5. 用户在 `HomeScreen` 点击「打开商品 123」，回调执行 `backStack.add(Product("123"))`。
6. 返回栈由 `[Home]` 变成 `[Home, Product("123")]`；由于它是可观察列表，`NavDisplay` 发生重组。
7. `NavDisplay` 取新的栈顶 `Product("123")`，向 `entryProvider` 请求条目，命中 `entry<Product>`，lambda 参数 `product` 就是那个 key 对象，`product.id == "123"`。
8. `NavDisplay` 切换到新的 `Scene`，按默认转场做页面切换动画（也可被 `transitionSpec` 覆盖）。
9. 用户按返回键（或点页面里的返回按钮）：`onBack` / 回调执行 `backStack.removeLastOrNull()`。
10. 返回栈回到 `[Home]`，`NavDisplay` 重组并切回 `HomeScreen`；如果 `Product` 条目上创建过 ViewModel，其 `ViewModelStore` 会随出栈被清理。

## 6. Flutter / Dart 3 对照

先看两种经典写法。

**Flutter 的 `Navigator` 1.0 是命令式的**：你调用 `push` / `pop`，由框架内部维护路由栈，你并不直接持有那个栈：

```dart
// 前进：压入一个路由
Navigator.of(context).push(
  MaterialPageRoute(builder: (_) => const ProductScreen(id: '123')),
);
// 返回：弹出栈顶
Navigator.of(context).pop();
```

**`go_router` 是声明式的，但路径仍然是字符串**：

```dart
final router = GoRouter(
  initialLocation: '/',
  routes: [
    GoRoute(path: '/', builder: (_, __) => const HomeScreen()),
    GoRoute(
      path: '/product/:id',
      builder: (_, state) => ProductScreen(id: state.pathParameters['id']!),
    ),
  ],
);

context.go('/product/123');   // 用字符串跳转
```

**Nav3 的「自己持有返回栈」在思路上更接近 Flutter 的 `Router` / `RouterDelegate`（以及 `Navigator.pages`）**，而不是 `Navigator.push`。Router API 就是要求你自己持有一个 `List<Page>`，改变列表后框架据此重建导航栈：

```dart
class MyRouterDelegate extends RouterDelegate<Object> with ChangeNotifier {
  // 返回栈由我们自己持有：列表本身就是导航状态
  final List<Page> pages = [const MaterialPage(child: HomeScreen())];

  void pushProduct(String id) {
    pages.add(MaterialPage(child: ProductScreen(id: id)));
    notifyListeners();          // 通知框架刷新
  }

  void pop() {
    if (pages.length > 1) {
      pages.removeLast();
      notifyListeners();
    }
  }

  @override
  Widget build(BuildContext context) => Navigator(pages: pages);

  @override
  Future<void> setNewRoutePath(Object configuration) async {}
}
```

对应关系一眼可见：`pages.add(...)` ≈ `backStack.add(Product(id))`，`pages.removeLast()` ≈ `backStack.removeLastOrNull()`，`notifyListeners()` 由 Compose 的 `SnapshotStateList` 自动完成（列表变化直接触发重组，不需要手动通知）。区别在于 Nav3 的栈里放的是 key，而 Flutter 的 `pages` 里放的是 `Page` 对象（已经携带 builder）；也正因为 Nav3 存的是可序列化的 key，返回栈才能被保存到持久化存储、支持进程死亡恢复——Flutter 侧要自己序列化 `pages` 或依赖路由信息还原。

和 `go_router` 相比：`go_router` 的跳转仍是路径字符串（`context.go('/product/123')`，取参后还要 `int.parse`），Nav3 的 key 是编译期类型（`Product(id = "123")`），写错类型编译就报错。这与第 [71 章](71_导航进阶嵌套导航与类型安全导航.md) 讲 Nav2 类型安全路由时的结论一致：Compose 方向是「类型即路由」，路径字符串是历史包袱。

## 7. 常见错误

- **把内容而不是 key 放进返回栈**：写成 `backStack.add { Text("...") }` 或往列表里塞 Composable。返回栈里只能放 key 对象，内容是 `entryProvider` 里由 key 映射出来的。
- **忘了 `NavEntry` 需要 key**：`NavEntry { ... }` 缺少 key 参数会编译不过。正确写法是 `NavEntry(key) { ... }`，DSL 里的 `entry<Home> { ... }` 已经替你带上了 key。
- **继续用字符串路由的思维写 Nav3**：写 `backStack.add("product/123")` 或 `NavEntry("home")`。Nav3 没有字符串路由的概念，key 必须是类型化对象；字符串既不类型安全，也无法表达参数结构。
- **忘了 `compileSdk 36`**：依赖加好了，Sync 仍失败或运行时报错，常见原因是 `compileSdk` 低于 36。
- **混淆 Nav2 的 `NavHost` 与 Nav3 的 `NavDisplay`**：`NavHost` 配 `NavController` + `composable`，`NavDisplay` 配自己的返回栈 + `entryProvider`。把两者混在半张页面里（例如在 Nav3 里找 `NavController`）说明心智模型还没切换。
- **key 没有实现 `NavKey` 或没加 `@Serializable`**：`rememberNavBackStack` 要求两者兼备。只写 `data class Product(val id: String)` 就传进去，配置变化 / 进程恢复时会因为无法保存而失败或丢失返回栈。
- **`entryProvider` 漏掉某个 key**：直接写 `when` 时漏了分支会走到 `else`；用 DSL 时未命中的 key 默认抛 `IllegalStateException("Unknown screen ...")`。要么补全 `entry<...>`，要么在构造 DSL 时提供 fallback，要么跳转前就保证 key 合法。
- **在条目里直接用 `viewModel()` 却没加 ViewModel 装饰器**：`entryDecorators` 里缺少 `rememberViewModelStoreNavEntryDecorator()`，ViewModel 的作用域就不跟随条目，行为会与预期不符。加装饰器时要记得把保存状态的那个放第一位。
- **直接用 `remember { mutableStateListOf(...) }` 又指望它自动保存**：它只是可观察列表，不负责持久化。要跨配置变化 / 进程死亡保存返回栈，用 `rememberNavBackStack`（或自己 `rememberSerializable` + 手工处理序列化）。

## 8. 本章检查题

1. Nav3 与 Nav2 在「谁持有返回栈」上有什么本质区别？
2. Nav3 的返回栈里放的是什么？为什么不是 Composable？
3. `rememberNavBackStack` 对 key 有哪两个硬性要求？
4. `entryProvider` 的 DSL 写法与 lambda 写法各有什么特点？
5. `NavDisplay` 默认显示返回栈里的哪些条目？`sceneStrategies` 的作用是什么？
6. `entryDecorators` 里的两个装饰器分别解决什么问题？
7. 用 Nav3 时为什么必须把 `compileSdk` 提到 36 或更高？

答案：

1. Nav2 由库持有返回栈，`NavController` 内部管理，你通过 `navigate` / `popBackStack` 间接操作；Nav3 由**你自己持有**返回栈，它是一个可观察列表，你直接用 `add` / `removeLastOrNull` 操作。Nav3 的模型是「你管状态，库帮你渲染」。
2. 放的是 **key**（目的地身份，如 `Home`、`Product("123")`），不是内容。key 是轻量的可序列化对象，推入栈只是一次列表添加；只要 key 可序列化，整个返回栈就能保存、跨配置变化与进程死亡恢复。
3. 每个 key 必须实现 `NavKey` 标记接口，并且标注 `@Serializable`。
4. lambda 写法用 `when (key)` 自己覆盖每种 key，并用 `NavEntry(key) { ... }` 构造条目，控制力强但要手写分支；DSL 写法用 `entry<Home> { ... }` 声明，代码更短，泛型参数指明 key 类型，并**自带未命中 key 的默认行为**（默认抛异常，可传 fallback 自定义）。
5. `NavDisplay` 默认用单栏策略（`SinglePaneSceneStrategy`）只显示**栈顶**那一个条目。`sceneStrategies` 是一个策略列表，用来决定「一次渲染列表里的哪些条目」，换成自适应策略即可实现列表 + 详情的多栏布局。
6. `rememberSaveableStateHolderNavEntryDecorator()` 让条目内的 `rememberSaveable` 正常工作，使其状态能跨配置变化与进程死亡恢复；`rememberViewModelStoreNavEntryDecorator()` 为每个条目创建 `ViewModelStore`，让 `viewModel()` 的作用域绑定到条目，并在条目出栈时清理。
7. 因为官方 Navigation 3 入门文档要求 `compileSdk` 36 或更高，本章按此配置。该要求本身不能证明库使用了某一项 Android 16 平台能力，也不等于把最低运行版本 minSdk 提高到 36。

## 9. 本章总结

官方参考：[Navigation 3 入门](https://developer.android.com/guide/navigation/navigation-3/get-started)、[Navigation 3 发布说明](https://developer.android.com/jetpack/androidx/releases/navigation3)。

> 本章的版本号和稳定性表述均是版本快照，不应替代当前官方发布说明。升级 Navigation 3 或相关 Compose/AndroidX 依赖后，请重新检查构件名称、API、compileSdk 要求和保存状态行为。

Navigation 3 是专门为 Jetpack Compose 设计的新导航库。本章基于 1.1.4 版本快照讲解；它的核心心智模型只有一句话：**你拥有返回栈**——返回栈是一个可观察列表，里面放的是可序列化的类型化 key，`add` 就是前进、`removeLastOrNull` 就是返回；`NavDisplay` 观察这个列表，把栈顶 key 交给 `entryProvider`，得到 `NavEntry` 并渲染。`entryDecorators` 负责给条目补上保存状态与 ViewModel 作用域，`sceneStrategies` 决定单栏还是自适应多栏，`transitionSpec` / metadata DSL 负责转场定制，而 `compileSdk 36` 是入场门槛。

与 Nav2 的关系不是替代，而是演进：Nav2 用字符串路由 + `NavHost`，库替你管返回栈，存量项目广泛；Nav3 用类型化 key + `NavDisplay`，你自己管返回栈，适合全新的 Compose 项目。对照 Flutter：Nav3 的「自己持有返回栈」在思路上接近声明式的 `Router` / `Navigator.pages`，而不是命令式的 `Navigator.push`；`go_router` 仍是字符串路径，这一点与 Nav2 一脉相承。
