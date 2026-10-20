# 第 2 章：`@Composable` 与 Composable 函数

## 1. 项目中的例子

项目里大量函数写成：

```kotlin
@Composable
fun DemoApp() {
    // UI
}
```

```kotlin
@Composable
fun HomeScreen(onNavigate: (String) -> Unit) {
    // UI
}
```

```kotlin
@Composable
private fun EntryCard(
    info: ComponentInfo,
    onClick: () -> Unit,
) {
    // UI
}
```

它们共同拥有：

```kotlin
@Composable
```

本章只解决一个核心问题：

> Composable 函数到底和普通 Kotlin 函数有什么不同？

---

## 2. 普通 Kotlin 函数

先看普通函数：

```kotlin
fun add(a: Int, b: Int): Int {
    return a + b
}
```

它的工作过程很直接：

```text
接收 a、b
  ↓
计算 a + b
  ↓
返回 Int
```

调用：

```kotlin
val result = add(2, 3)
```

得到：

```text
5
```

---

## 3. Composable 函数

```kotlin
@Composable
fun Greeting() {
    Text("Hello")
}
```

它不返回一个 Android `View`，也没有返回一个 Flutter `Widget`。

它是在 Compose 的组合过程中执行，用代码描述当前 UI 应该包含什么。

可以先理解为：

```text
执行 Greeting()
      ↓
告诉 Compose：这里需要一个 Text("Hello")
      ↓
Compose 记录并管理这部分 UI
```

因此：

```kotlin
@Composable
fun Greeting() {
    Text("Hello")
}
```

不需要：

```kotlin
return Text("Hello")
```

`Text()` 本身也是一个 Composable 函数调用。

---

## 4. `@Composable` 是什么

从 Kotlin 语法角度看，`@Composable` 是一个注解。

但是它不只是给人看的标记。Compose 编译器插件会识别它，并让这个函数参与 Compose 的组合与重组机制（重组指状态变化后，Compose 重新执行「读取过该状态」的 Composable 函数，用新值重新生成这部分界面，第 22 章详解）。

因此，不能把它理解成普通注释：

```kotlin
// 这只是注释，不改变编译行为
```

而：

```kotlin
@Composable
```

会改变编译器如何处理这个函数及其调用关系。

---

## 5. 为什么普通函数不能直接调用 `Text`

错误示例：

```kotlin
fun showTitle() {
    Text("标题")
}
```

`Text` 是 Composable，只能在 Composable 上下文中调用。

正确：

```kotlin
@Composable
fun ShowTitle() {
    Text("标题")
}
```

原因可以先简单理解成：

> 普通 Kotlin 函数没有加入 Compose 当前的组合过程，因此不能直接向 UI 树中加入 `Text`。

调用方向：

```text
Composable 函数 → 可以调用普通函数
Composable 函数 → 可以调用 Composable 函数
普通函数       → 可以调用普通函数
普通函数       → 不能直接调用 Composable 函数
```

例如：

```kotlin
fun buildTitle(): String {
    return "Compose Demo"
}

@Composable
fun Title() {
    Text(buildTitle())
}
```

这是允许的。

---

## 6. Composable 函数为什么通常返回 `Unit`

项目中的函数：

```kotlin
@Composable
fun DemoApp() {
    // ...
}
```

没有显式返回类型，因此 Kotlin 推断为：

```kotlin
Unit
```

`Unit` 可以先理解为：

> 函数执行完成，但不向调用者返回一个需要接收的业务值。

它不表示函数“什么也没做”。

`DemoApp()` 虽然返回 `Unit`，但它在 Compose 环境中声明了导航 UI。

类似：

```kotlin
fun printMessage(): Unit {
    println("Hello")
}
```

它没有返回业务结果，但产生了行为。

Composable 则是在 Compose 环境中描述 UI。

---

## 7. Composable 不等于“页面”

错误理解：

```text
每个 @Composable 都是一个页面
```

项目中这些都是 Composable：

```kotlin
DemoApp()
HomeScreen()
ComponentDetailScreen()
EntryCard()
SectionTitle()
DemoContent()
Text()
Row()
Column()
```

它们的粒度不同：

```text
DemoApp                 整个应用导航区域
HomeScreen              一个页面
EntryCard                页面中的一个列表条目
SectionTitle             页面中的一小段标题
Text                     基础文字组件
```

所以更准确的理解是：

> Composable 是可以参与 Compose UI 组合的函数，既可以表示页面，也可以表示页面中的很小一部分。

---

## 8. Composable 之间如何组合

项目首页可以简化为：

```kotlin
@Composable
fun HomeScreen() {
    PageTitle()
    WelcomeMessage()
}

@Composable
fun PageTitle() {
    Text("Compose 组件示例")
}

@Composable
fun WelcomeMessage() {
    Text("欢迎开始学习")
}
```

层级：

```text
HomeScreen
├── PageTitle
│   └── Text
└── WelcomeMessage
    └── Text
```

每一层都是通过函数调用组合起来的。
这里展示的是调用关系；子项如何排列属于布局问题，第 8 章再学 Column、Row 等容器。

这也是“Compose”这个名称的核心：

> 用较小的 UI 单元组合成较大的 UI 单元。

---

## 9. `content: @Composable () -> Unit`

项目 `DemoTheme` 的参数中有：

```kotlin
content: @Composable () -> Unit
```

先逐段拆：

```text
content : @Composable () -> Unit
参数名      函数类型
```

这个函数类型表示：

1. 调用它时不需要传参数：`()`
2. 不返回业务值：`Unit`
3. 函数体中允许调用 Composable、参与组合：`@Composable`

例如：

```kotlin
@Composable
fun Frame(content: @Composable () -> Unit) {
    Column {
        Text("顶部")
        content()
        Text("底部")
    }
}
```

调用：

```kotlin
Frame {
    Text("中间内容")
}
```

最终结构：

```text
顶部
中间内容
底部
```

Flutter/Dart 3 中概念上接近：

```dart
class Frame extends StatelessWidget {
  const Frame({
    required this.child,
    super.key,
  });

  final Widget child;

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        const Text('顶部'),
        child,
        const Text('底部'),
      ],
    );
  }
}
```

但 Compose 传的是 Composable 函数，而 Flutter 常传一个已经构造好的 `Widget`。

---

## 10. 尾随 Lambda 为什么能写在圆括号外

项目中：

```kotlin
DemoTheme {
    DemoApp()
}
```

完整一点可以写成：

```kotlin
DemoTheme(
    content = {
        DemoApp()
    }
)
```

因为 `content` 是最后一个函数类型参数，Kotlin 允许把 Lambda 移到圆括号外。

这叫尾随 Lambda 写法。

再比如：

```kotlin
Column {
    Text("A")
    Text("B")
}
```

本质也是给 `Column` 的内容参数传入一个 Lambda。

不要误解为：

```text
大括号只是 Kotlin 的 UI 专用语法
```

它本质仍然是 Kotlin Lambda。

---

## 11. Composable 可能执行多次

非常重要：

> Composable 函数不是“创建一次后永远不再执行”的普通初始化函数。

当它读取的状态发生变化时，Compose 可能重新执行相关函数，以更新 UI。

例如：

```kotlin
@Composable
fun Counter(count: Int) {
    Text("$count")
}
```

当传入的 `count` 从 1 变成 2，Compose 可能重新执行 `Counter`，使文字更新。

因此不要直接这样写：

```kotlin
@Composable
fun Page() {
    // 错误思路：函数重组时可能重复执行
    requestApi()

    Text("页面")
}
```

网络请求等副作用（指组合函数体之外、会对外部世界产生影响的操作，例如发请求、写文件、注册监听，第 25 章详解）需要使用合适的 Effect 或放到 ViewModel 中。

本章先记住：

```text
Composable = 描述当前状态下 UI 应该是什么
```

而不是：

```text
Composable = 只会执行一次的页面初始化代码
```

---

## 12. 与 Flutter Widget 的对比

Flutter：

```dart
class HomeScreen extends StatelessWidget {
  const HomeScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return const Text('Hello');
  }
}
```

Compose：

```kotlin
@Composable
fun HomeScreen() {
    Text("Hello")
}
```

共同点：

- 都声明 UI
- 状态变化后相关 UI 都可能再次组合
- 都鼓励小组件组合
- 组合函数中不应该随意产生不可控副作用

主要差异：

| Compose | Flutter |
|---|---|
| UI 单元常是函数 | UI 单元是 Widget 对象 |
| 不需要返回 Widget | `build` 返回 Widget |
| `@Composable` 标记函数 | 继承 `StatelessWidget` / `StatefulWidget` |
| 内容常以 Lambda 传递 | 内容常以 `Widget child` / `List<Widget>` 传递 |

---

## 13. 项目中的 Composable 分类

### 应用级

```kotlin
@Composable
fun DemoApp()
```

负责应用导航结构。

### 页面级

```kotlin
@Composable
fun HomeScreen(...)

@Composable
fun ComponentDetailScreen(...)
```

负责完整页面。

### 局部组件级

```kotlin
@Composable
private fun EntryCard(...)

@Composable
private fun SectionTitle(...)
```

负责页面中的局部 UI。

### 环境包装级

```kotlin
@Composable
fun DemoTheme(content: @Composable () -> Unit)

@Composable
fun DesignWidthScale(content: @Composable () -> Unit)
```

先提供环境，再渲染传入内容。

---

## 14. 常见错误

### 错误 1：在普通函数里调用 Composable

```kotlin
fun buildPage() {
    Text("Hello")
}
```

应该把调用位置放进 Composable 环境。

### 错误 2：认为 Composable 必须是页面

小到一个标题，也可以单独抽成 Composable。

### 错误 3：在 Composable 函数体内直接执行不可重复的操作

例如：

- 每次执行都请求 API
- 每次执行都写数据库
- 每次执行都弹 Toast
- 每次执行都修改外部变量

需要使用状态和 Effect 管理。

### 错误 4：把 Composable 当成返回 Widget 的工厂

Compose 通常不是：

```kotlin
return Text(...)
```

而是：

```kotlin
Text(...)
```

---

## 15. 本章检查题

1. `@Composable` 只是给开发者看的注释吗？
2. 普通 Kotlin 函数为什么不能直接调用 `Text()`？
3. `@Composable () -> Unit` 中的 `()` 表示什么？
4. `EntryCard` 是页面吗？
5. Composable 是否保证只执行一次？

答案：

1. 不是，Compose 编译器会识别并处理它。
2. 普通函数不在 Compose 的组合上下文中。
3. 调用这个函数时不传参数。
4. 不是，它是页面内的局部 UI 组件。
5. 不保证，状态变化可能引发重组并再次执行。

---

## 16. 本章总结

一句话：

> `@Composable` 把一个 Kotlin 函数声明成参与 Compose 组合的单元，它可以调用其他 Composable，并可能随着状态变化重新执行。

先牢牢记住：

```text
Composable 描述 UI
Composable 不等于页面
Composable 不保证只执行一次
```
