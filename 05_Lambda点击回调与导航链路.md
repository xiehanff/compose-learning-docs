# 第 5 章：Lambda、点击回调与 item 到导航的完整链路

## 1. 本章要解释的代码

你之前询问的代码：

```kotlin
HomeScreen(
    onNavigate = { id ->
        navController.navigate("detail/$id")
    },
)
```

你的理解是：

> 给一个点击事件，然后每个 item 都触发导航事件。

这个理解接近，但需要改得更准确：

> 外部给 `HomeScreen` 传入一条“收到组件 ID 后如何导航”的回调函数。`HomeScreen` 中的每个 item 被点击时，再调用同一条回调，并传入自己的 ID。

它不是给每个 item 提前创建一个 Android 点击事件对象。

---

## 2. 先看 `HomeScreen` 参数类型

```kotlin
fun HomeScreen(
    onNavigate: (String) -> Unit,
)
```

参数：

```text
参数名       类型
onNavigate : (String) -> Unit
```

`(String) -> Unit` 是函数类型。

逐段理解：

```text
(String)  接收一个 String 参数
   ->     函数类型的输入与输出分隔
Unit      不返回业务结果
```

所以 `onNavigate` 不是字符串、对象或布尔值，而是一个可以调用的函数。

调用方式：

```kotlin
onNavigate("text")
```

表示把字符串 `"text"` 传给这个回调。

---

## 3. 外部传入的 Lambda

```kotlin
onNavigate = { id ->
    navController.navigate("detail/$id")
}
```

这是一个 Lambda，也就是没有单独函数名的函数值。

结构：

```text
{ id -> navController.navigate("detail/$id") }
  ↑                    ↑
 输入参数              函数体
```

因为 `HomeScreen` 已经声明：

```kotlin
onNavigate: (String) -> Unit
```

编译器知道 `id` 是 `String`。

完整写出类型也可以：

```kotlin
onNavigate = { id: String ->
    navController.navigate("detail/$id")
}
```

通常不必重复写。

---

## 4. 它等价于先声明一个普通变量

```kotlin
val navigateToDetail: (String) -> Unit = { id ->
    navController.navigate("detail/$id")
}

HomeScreen(
    onNavigate = navigateToDetail,
)
```

因此：

```kotlin
HomeScreen(
    onNavigate = { id ->
        navController.navigate("detail/$id")
    },
)
```

只是把函数值直接写在参数位置。

---

## 5. HomeScreen 内部并没有立即导航

调用 `HomeScreen` 时：

```kotlin
HomeScreen(
    onNavigate = { id ->
        navController.navigate("detail/$id")
    },
)
```

做的是：

```text
把函数交给 HomeScreen 保存为本次调用的参数
```

不是立即执行：

```kotlin
navController.navigate(...)
```

什么时候执行，取决于 `HomeScreen` 内部什么时候调用：

```kotlin
onNavigate(...)
```

这和“传函数”与“调用函数”的区别有关：

```text
onNavigate              函数本身
onNavigate("text")      调用函数
```

---

## 6. 每个条目如何拿到自己的 `info`

先把行为说清楚：注册表里有多少个 `ComponentInfo`，首页就会生成多少张卡片；每张卡片生成时都会拿到列表里的那一个对象，并把它保存为自己的 `info`。这里的 `items` 就是「让列表中的每个元素各生成一段内容」的写法，第 16 章会专门讲它，本章只关心它带来的行为。示例里的 `key` 是第 17 章介绍的列表项身份标识，不是导航回调的必需参数；第一次阅读时可先只看 `info` 如何传给卡片。

项目代码：

```kotlin
items(
    items = ComponentRegistry.all,
    key = { it.id },
) { info ->
    EntryCard(
        info = info,
        onClick = {
            onNavigate(info.id)
        },
    )
}
```

实际项目使用了更紧凑的写法：

```kotlin
items(ComponentRegistry.all, key = { it.id }) { info ->
    EntryCard(
        info = info,
        onClick = { onNavigate(info.id) },
    )
}
```

假设列表有三个对象：

```text
ComponentInfo(id = "column_row")
ComponentInfo(id = "text")
ComponentInfo(id = "image")
```

`items` 会分别构建：

```text
第一个 EntryCard：info.id = "column_row"
第二个 EntryCard：info.id = "text"
第三个 EntryCard：info.id = "image"
```

每个条目都有自己的 `info`。

---

## 7. 每个 item 如何得到自己的点击回调

当前条目代码：

```kotlin
EntryCard(
    info = info,
    onClick = {
        onNavigate(info.id)
    },
)
```

假设当前条目是 Text：

```text
info.id = "text"
```

那么它的 `onClick` 可以理解为：

```kotlin
onClick = {
    onNavigate("text")
}
```

假设当前条目是 Image：

```kotlin
onClick = {
    onNavigate("image")
}
```

所以：

> 每个 item 都使用同一个外层 `onNavigate` 规则，但传入不同的 `info.id`。

---

## 8. EntryCard 又把回调传给 Card

`EntryCard` 定义：

```kotlin
@Composable
private fun EntryCard(
    info: ComponentInfo,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        // 卡片内容
    }
}
```

这里出现另一种函数类型：

```kotlin
onClick: () -> Unit
```

它与 `onNavigate` 不同：

```text
onNavigate: (String) -> Unit   需要接收一个 String
onClick:    () -> Unit         不需要参数
```

为什么 `EntryCard.onClick` 不需要参数？

因为当前 item 的 `info.id` 已经在外层 Lambda 中被记住：

```kotlin
onClick = {
    onNavigate(info.id)
}
```

Card 只需要通知：

```text
我被点击了
```

至于点击的是哪个 ID，外层 Lambda 已经知道。

---

## 9. 完整调用链

假设用户点击 `Text` 条目。

### 第一步：外部给 HomeScreen 传导航规则

```kotlin
HomeScreen(
    onNavigate = { id ->
        navController.navigate("detail/$id")
    },
)
```

### 第二步：列表构建 Text 条目

```text
info.id = "text"
```

### 第三步：为该条目创建点击回调

```kotlin
onClick = {
    onNavigate("text")
}
```

### 第四步：EntryCard 把回调交给 Card

```kotlin
Card(onClick = onClick) { ... }
```

### 第五步：用户点击 Card

Card 调用：

```kotlin
onClick()
```

### 第六步：条目回调调用 HomeScreen 的导航回调

```kotlin
onNavigate("text")
```

### 第七步：执行最外层传入的 Lambda

```kotlin
id = "text"
```

因此执行：

```kotlin
navController.navigate("detail/text")
```

### 第八步：NavHost 匹配详情路由

```text
detail/text
匹配
detail/{componentId}
```

得到：

```text
componentId = "text"
```

完整图：

```text
用户点击 Text 卡片
        ↓
Card 调用 onClick()
        ↓
EntryCard 的 onClick Lambda
        ↓
onNavigate(info.id)
        ↓
onNavigate("text")
        ↓
navController.navigate("detail/text")
        ↓
NavHost 显示 Text 详情页
```

---

## 10. 所以你的说法应怎样修正

原说法：

> 给一个点击事件，然后每个 item 都触发导航事件。

更准确的版本：

> `DemoApp` 给 `HomeScreen` 传入一个导航回调。`HomeScreen` 为每个 item 创建自己的无参数点击回调；item 被点击后，将自己的 ID 传给导航回调，最终由外层 `NavController` 执行导航。

这里分成两层事件：

```text
Card 点击回调：() -> Unit
        ↓
页面导航回调：(String) -> Unit
```

---

## 11. 为什么不直接把 NavController 传进 HomeScreen

也可以写：

```kotlin
@Composable
fun HomeScreen(navController: NavController) {
    // 点击时直接 navigate
}
```

但当前项目选择：

```kotlin
fun HomeScreen(
    onNavigate: (String) -> Unit,
)
```

好处是 `HomeScreen` 不需要知道：

- 导航框架是什么
- 路由字符串是什么
- 使用 `NavController` 还是其他路由实现
- 点击后一定是打开页面，还是记录日志、显示面板等

`HomeScreen` 只负责报告：

```text
用户选择了哪个组件 ID
```

外层负责决定：

```text
收到 ID 后做什么
```

这使页面更容易预览和测试。

例如测试时可以传：

```kotlin
HomeScreen(
    onNavigate = { id ->
        println("用户点击了 $id")
    },
)
```

不需要真实导航控制器。

---

## 12. 不是所有组件都有 `onClick` 参数

项目这里使用：

```kotlin
Card(onClick = onClick)
```

因为 Material 3 提供了一个可点击 Card 的函数版本。

但 `Text` 没有普通的 `onClick` 参数：

```kotlin
// 不能这样写
Text(
    text = "点击我",
    onClick = { },
)
```

通常通过 Modifier 添加点击能力：

```kotlin
Text(
    text = "点击我",
    modifier = Modifier.clickable {
        // 点击逻辑
    },
)
```

因此要区分：

```text
组件自带 onClick 参数
和
通过 Modifier.clickable 添加点击能力
```

在当前 `EntryCard` 中，项目使用的是 Card 自带的 `onClick` 参数。

---

## 13. Dart 3 / Flutter 对比

Compose：

```kotlin
@Composable
fun HomeScreen(
    onNavigate: (String) -> Unit,
) {
    EntryCard(
        onClick = {
            onNavigate("text")
        },
    )
}
```

Flutter：

```dart
class HomeScreen extends StatelessWidget {
  const HomeScreen({
    required this.onNavigate,
    super.key,
  });

  final void Function(String id) onNavigate;

  @override
  Widget build(BuildContext context) {
    return EntryCard(
      onTap: () {
        onNavigate('text');
      },
    );
  }
}
```

外层：

```dart
HomeScreen(
  onNavigate: (id) {
    Navigator.of(context).push(
      MaterialPageRoute<void>(
        builder: (_) => DetailScreen(id: id),
      ),
    );
  },
)
```

函数类型对照：

| Kotlin | Dart 3 |
|---|---|
| `() -> Unit` | `void Function()` |
| `(String) -> Unit` | `void Function(String)` |
| `{ id -> ... }` | `(id) { ... }` |
| `onClick()` | `onTap()` |

---

## 14. 最容易写错的地方

### 错误 1：把函数调用结果传给 `onClick`

错误：

```kotlin
EntryCard(
    info = info,
    onClick = onNavigate(info.id),
)
```

这里会立刻调用：

```kotlin
onNavigate(info.id)
```

而且它返回 `Unit`，但 `onClick` 需要的是：

```kotlin
() -> Unit
```

正确：

```kotlin
EntryCard(
    info = info,
    onClick = {
        onNavigate(info.id)
    },
)
```

大括号创建一个函数，等用户点击后才调用。

### 错误 2：漏传 ID

```kotlin
onClick = {
    onNavigate()
}
```

`onNavigate` 需要一个 `String`，不能空参数调用。

### 错误 3：混淆两个回调类型

```text
EntryCard.onClick：不接收参数
HomeScreen.onNavigate：接收 String
```

它们不能随意互换。

### 错误 4：认为 Lambda 一传入就会执行

传入：

```kotlin
onClick = { doSomething() }
```

不等于立即执行。

立即执行是：

```kotlin
doSomething()
```

---

## 15. 一个最小可运行思维实验

不用 UI，只看函数：

```kotlin
fun receiveClick(onClick: () -> Unit) {
    println("暂时不执行")
    onClick()
}

fun main() {
    receiveClick(
        onClick = {
            println("现在执行点击逻辑")
        },
    )
}
```

输出：

```text
暂时不执行
现在执行点击逻辑
```

把它映射回 Compose：

```text
receiveClick       → Card
onClick 参数        → Card 的 onClick
onClick()          → 用户点击时 Card 调用回调
```

---

## 16. 本章检查题

1. `onNavigate` 是点击事件对象吗？
2. `(String) -> Unit` 表示什么？
3. 为什么 `EntryCard.onClick` 是 `() -> Unit`，不需要 String？
4. `onClick = onNavigate(info.id)` 为什么错误？
5. 点击 `image` item 后最终调用的路由是什么？

答案：

1. 不是，它是一个函数参数/回调。
2. 接收一个 String，不返回业务结果的函数。
3. 当前 item 的 `info.id` 已经被外层 Lambda 捕获；Card 只需要报告“被点击”。
4. 它会立即调用函数，并把返回的 Unit 当作 onClick 参数。
5. `detail/image`。

---

## 17. 本章总结

核心类型：

```kotlin
onNavigate: (String) -> Unit
onClick: () -> Unit
```

核心桥接：

```kotlin
onClick = {
    onNavigate(info.id)
}
```

完整结论：

> 每个 item 都有自己的 `onClick` Lambda；这个 Lambda 记住当前 item 的 `info.id`。用户点击后，它把 ID 交给 `HomeScreen` 的 `onNavigate` 回调，再由 `DemoApp` 中的 `NavController` 执行真正导航。
