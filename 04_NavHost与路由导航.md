# 第 4 章：rememberNavController、NavHost、composable 与路由参数

## 1. 本章对应代码

项目在 `DemoApp()` 中定义导航：

```kotlin
@Composable
fun DemoApp() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "home",
    ) {
        composable("home") {
            HomeScreen(
                onNavigate = { id ->
                    navController.navigate("detail/$id")
                },
            )
        }

        composable(
            route = "detail/{componentId}",
            arguments = listOf(
                navArgument("componentId") {
                    type = NavType.StringType
                },
            ),
        ) { backStackEntry ->
            val componentId =
                backStackEntry.arguments
                    ?.getString("componentId")
                    ?: ""

            ComponentDetailScreen(
                componentId = componentId,
                onBack = {
                    navController.popBackStack()
                },
            )
        }
    }
}
```

本章先理解导航结构，第 5 章再专门拆点击回调。

还需要先说清楚这套写法在官方方案里的位置：

> 项目使用的是 `androidx.navigation:navigation-compose` 的**字符串路由**写法（通常称为 Nav2 风格），目的地和参数都写成字符串。这套写法目前在大量存量项目中广泛使用，学习和维护价值很高。官方现在也有更新的方案：类型安全导航见第 71 章，Navigation 3 见新增章节。本章只讲字符串路由这套基础，不展开新旧方案的对比。

---

## 2. 先分清三个概念：目的地、返回栈、导航动作

导航（navigation）指“用户从一个界面进入另一个界面，并且能够按顺序返回”这件事。它由三个概念组成。先把概念看明白，再看第 3 节之后的具体 API。

### 目的地（destination）

目的地是一个“可以被进入的界面单元”，例如首页、详情页、登录页。

在当前写法里，一个目的地由一个路由字符串标识，并绑定到一个 Composable：

```text
目的地：首页
路由字符串：home
绑定的界面：HomeScreen
```

注意：目的地是概念，路由字符串只是它在字符串路由写法里的表示方式。

### 返回栈（back stack）

用户可能一层层进入多个界面：

```text
首页 → 详情页 → 详情页里的子页面
```

系统必须记住“用户是按什么顺序进来的”，否则按返回键时不知道该回到哪里。记录这个顺序的结构就是返回栈：它是一种后进先出的栈，最后进入的目的地放在栈顶。

```text
[首页, 详情页, 子页面]
                    ↑ 栈顶：当前正在显示的目的地
```

### 导航动作

导航动作就是对返回栈的操作，常见只有两类：

```text
前进：进入一个新目的地 → 新目的地压到栈顶
返回：回到上一个目的地 → 弹出栈顶目的地
```

后面 API 里的 `navigate(...)` 和 `popBackStack()`，分别对应这两个动作。

这三点与具体框架无关。Flutter 的 `Navigator` 也是同一套模型：它同样有目的地、返回栈和前进/返回动作。

### 项目里要解决什么

应用有两个主要页面：

```text
首页 HomeScreen
详情页 ComponentDetailScreen
```

需要实现：

```text
首页点击某个组件
      ↓
打开对应详情页
      ↓
详情页点击返回
      ↓
回到首页
```

对应到上面的概念，导航系统负责：

- 目的地和路由字符串的对应关系
- 目的地与 Composable 的绑定
- 当前显示哪个目的地
- 执行导航动作（前进、返回）
- 维护返回栈
- 解析路由参数

---

## 3. `rememberNavController()`

三个概念落到 Compose Navigation 的具体 API 上，是这样分工的：

```text
目的地          → composable(route) { ... } 注册
目的地集合       → NavHost 持有并显示
返回栈          → NavController 内部维护
导航动作        → NavController.navigate(...) / popBackStack()
```

```kotlin
val navController = rememberNavController()
```

它创建并记住一个导航控制器。

`NavController` 负责：

- `navigate(...)`：前往新目的地
- `popBackStack()`：弹出当前目的地，返回上一层
- 管理导航返回栈
- 解析导航请求

为什么不直接每次创建：

```kotlin
val navController = NavController(...)
```

因为 Composable 可能重组（指状态变化后，Compose 重新执行「读取过该状态」的 Composable 函数，用新值重新生成这部分界面，第 22 章详解）。如果每次执行 `DemoApp()` 都创建全新控制器，导航状态会被破坏。

`rememberNavController()` 会让控制器与当前 Composition 生命周期正确关联，并支持 Navigation Compose 所需的状态保存/恢复能力。

先建立直观理解：

> 它是整个导航图共用的页面跳转管理器。

Flutter 概念上接近：

```dart
Navigator.of(context)
```

或者路由框架持有的 router，但实现方式并不相同。

---

## 4. `NavHost` 是什么

```kotlin
NavHost(
    navController = navController,
    startDestination = "home",
) {
    // 导航目的地
}
```

它是导航图的显示容器。

可以理解成：

```text
NavHost
├── 保存“路由 → 页面”的配置
├── 观察 navController 当前目的地
└── 显示当前目的地对应的 Composable
```

### 参数 `navController`

```kotlin
navController = navController
```

告诉 `NavHost` 使用哪个控制器。

### 参数 `startDestination`

```kotlin
startDestination = "home"
```

告诉导航图：第一次进入时显示哪个目的地。

项目启动后显示首页，正是因为：

```text
startDestination = "home"
```

---

## 5. `composable("home")`

```kotlin
composable("home") {
    HomeScreen(...)
}
```

这里的 `composable` 不是 `@Composable` 注解。

它是 Navigation Compose 导航图构建器提供的函数，用来声明一个导航目的地：

```text
路由：home
页面：HomeScreen
```

可以读成：

> 当当前路由是 `home` 时，在 `NavHost` 中显示 `HomeScreen`。

注意区分：

```text
@Composable     标记 UI 函数
composable(...) 注册一个导航目的地
```

名字相似，但不是同一个东西。

---

## 6. `navigate("detail/$id")`

首页目标中：

```kotlin
navController.navigate("detail/$id")
```

假设：

```kotlin
id = "text"
```

Kotlin 字符串模板：

```kotlin
"detail/$id"
```

得到：

```text
detail/text
```

然后导航控制器寻找能匹配它的路由模板：

```text
detail/{componentId}
```

匹配结果：

```text
componentId = text
```

---

## 7. 带参数的详情路由

```kotlin
composable(
    route = "detail/{componentId}",
    arguments = listOf(
        navArgument("componentId") {
            type = NavType.StringType
        },
    ),
) { backStackEntry ->
    // ...
}
```

### `route`

```kotlin
route = "detail/{componentId}"
```

`{componentId}` 是参数占位符。

它不是最终路由，而是路由模板。

```text
模板：detail/{componentId}
实际：detail/text
实际：detail/column_row
实际：detail/image
```

### `navArgument`

```kotlin
navArgument("componentId") {
    type = NavType.StringType
}
```

声明参数：

- 名称：`componentId`
- 类型：字符串

名称必须与路由模板中的占位符一致：

```text
{componentId}
navArgument("componentId")
getString("componentId")
```

三个位置要统一。

---

## 8. `backStackEntry` 是什么

```kotlin
) { backStackEntry ->
```

Navigation 为当前目的地提供一个返回栈条目。

这个对象记录当前导航目的地相关信息，包括：

- 当前路由参数
- 当前目的地
- 与该返回栈条目关联的状态

本项目用它读取参数：

```kotlin
backStackEntry.arguments?.getString("componentId")
```

可以先理解成：

```text
用户进入 detail/text
       ↓
Navigation 创建当前详情页的 backStackEntry
       ↓
arguments 中保存 componentId = "text"
```

---

## 9. `?.` 与 `?:` 在这里做什么

项目代码：

```kotlin
val componentId =
    backStackEntry.arguments?.getString("componentId") ?: ""
```

拆开：

```kotlin
backStackEntry.arguments
```

可能为 `null`。

### 安全调用 `?.`

```kotlin
arguments?.getString(...)
```

含义：

- 如果 `arguments` 不为 null，就调用 `getString`
- 如果为 null，整个表达式得到 null

### Elvis 操作符 `?:`

```kotlin
结果 ?: ""
```

含义：

- 左边不为 null，使用左边
- 左边为 null，使用空字符串

因此最终 `componentId` 一定是非空的 `String` 类型，但可能是 `""`。

Dart 3 对照：

```dart
final componentId = arguments?['componentId'] as String? ?? '';
```

---

## 10. 参数如何进入详情页

```kotlin
ComponentDetailScreen(
    componentId = componentId,
    onBack = { navController.popBackStack() },
)
```

完整数据流：

```text
首页 item 的 info.id
      ↓
onNavigate(info.id)
      ↓
navigate("detail/$id")
      ↓
匹配 detail/{componentId}
      ↓
backStackEntry.arguments
      ↓
getString("componentId")
      ↓
ComponentDetailScreen(componentId = ...)
      ↓
ComponentRegistry.findById(componentId)
```

导航参数只携带 `id`，详情数据再从注册表查询。

这样避免把整个 `ComponentInfo`（项目的 Demo 条目数据类，第 36 章详解）对象都塞进路由。

---

## 11. 返回栈的实际变化：navigate 压栈，popBackStack 弹栈

第 2 节已经把返回栈当成概念解释过；这里看它在项目里的具体变化。

初始状态：

```text
[home]
```

导航到详情：

```kotlin
navController.navigate("detail/text")
```

栈变成：

```text
[home, detail/text]
```

调用：

```kotlin
navController.popBackStack()
```

弹出顶部：

```text
[home]
```

于是重新显示首页。

因此：

```text
navigate      通常把新目的地压入栈
popBackStack  弹出当前目的地
```

---

## 12. 与 Flutter 的对比

Flutter 直接传对象式导航：

```dart
Navigator.of(context).push(
  MaterialPageRoute<void>(
    builder: (_) => ComponentDetailScreen(
      componentId: id,
    ),
  ),
);
```

Compose 当前项目使用字符串路由：

```kotlin
navController.navigate("detail/$id")
```

然后在导航图中解析：

```kotlin
composable("detail/{componentId}") { entry ->
    val id = entry.arguments?.getString("componentId") ?: ""
}
```

概念对应：

| Compose Navigation | Flutter Navigator |
|---|---|
| `NavHost` | Navigator 显示和管理路由页面的区域 |
| `NavController` | Navigator / Router 控制器 |
| `composable(route)` | 路由配置或页面 builder |
| `navigate(route)` | `push(...)` / router 跳转 |
| `popBackStack()` | `Navigator.pop(context)` |
| `backStackEntry` | 当前 Route 及其参数环境 |

概念上的对应关系更清楚：

```text
目的地     ≈ Flutter 的一个页面（Route）
返回栈     ≈ Navigator 维护的页面栈
navigate   ≈ push
popBackStack ≈ pop
```

---

## 13. 路由字符串的改进建议

当前 Demo 写法足够直观：

```kotlin
"home"
"detail/{componentId}"
"detail/$id"
```

项目变大后，建议集中管理，避免手写错误：

```kotlin
object Routes {
    const val HOME = "home"
    const val DETAIL = "detail/{componentId}"

    fun detail(componentId: String): String {
        return "detail/$componentId"
    }
}
```

使用：

```kotlin
NavHost(
    navController = navController,
    startDestination = Routes.HOME,
) { ... }
```

```kotlin
navController.navigate(Routes.detail(id))
```

这不是本项目当前必须修改的错误，而是扩展时的维护建议。

---

## 14. 常见错误

### 错误 1：参数名称不统一

```text
路由：{componentId}
声明：navArgument("id")
读取：getString("componentId")
```

这样会导致读取失败。

### 错误 2：实际路由和模板不匹配

模板：

```text
detail/{componentId}
```

实际却跳：

```text
component/text
```

无法匹配。

### 错误 3：对包含特殊字符的字符串直接拼接

如果参数可能包含 `/`、`?`、空格等，需要进行 URI 编码，或者不要把复杂数据直接放在路径中。

当前项目 ID 都是受控的简单字符串，所以暂时没有这个问题。

### 错误 4：在每个页面内部各创建一个 `NavController`

当前导航图应共用 `DemoApp()` 中创建的控制器，通过回调把导航行为传给页面。

---

## 15. 本章检查题

1. 目的地、返回栈、导航动作分别指什么？`NavHost` 和 `NavController` 又各负责什么？
2. `startDestination = "home"` 的作用是什么？
3. `composable("home")` 中的 `composable` 与 `@Composable` 是同一东西吗？
4. `detail/text` 如何匹配 `detail/{componentId}`？
5. `popBackStack()` 与 `navigate()` 的方向有什么区别？

答案：

1. 目的地是可以被进入的界面单元（在字符串路由写法里由路由字符串标识）；返回栈是记录到过哪些目的地及其顺序的后进先出结构；导航动作是对返回栈的操作，前进压栈、返回弹栈。API 层面：`NavHost` 声明并显示目的地；`NavController` 维护返回栈并执行导航动作。
2. 指定导航图首次显示首页。
3. 不是。
4. `text` 被解析为 `componentId` 参数。
5. `navigate` 前往并通常压入新目的地；`popBackStack` 弹出当前目的地并返回。

---

## 16. 本章总结

项目导航主线，先概念后 API：

```text
概念层
目的地（route 字符串 → Composable）
返回栈（[home] → [home, detail/text]）
导航动作（navigate 压栈 / popBackStack 弹栈）
        ↓
API 层
rememberNavController → NavController
        ↓
NavHost(startDestination = "home")
        ↓
composable("home") → HomeScreen
        ↓ navigate("detail/$id")
composable("detail/{componentId}")
        ↓ 读取参数
ComponentDetailScreen
        ↓ popBackStack()
返回 HomeScreen
```

一句话：

> 目的地、返回栈、导航动作是模型；`NavHost` 管目的地与页面的映射，`NavController` 管返回栈和导航动作，`composable` 声明每个路由应该显示哪个 Composable。
