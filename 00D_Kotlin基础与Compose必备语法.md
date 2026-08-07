# 预备篇 D：Kotlin 基础与 Compose 必读语法

## 1. 本章目标

这一章不是完整 Kotlin 语言手册，而是帮助零基础读者读懂后续 Compose 示例。

学完后，你应当能够：

- 区分 `val`、`var`、可空类型和非空类型；
- 看懂函数、默认参数、命名参数和尾随 Lambda；
- 理解 class、data class、object 与 sealed interface 的用途；
- 使用 List 的常见操作并理解不可变状态更新；
- 看懂扩展函数、属性委托、泛型和基础协程代码。

如果本章练习无法独立完成，建议先系统学习 Kotlin，再进入 Compose 状态和架构章节。

## 2. `val` 与 `var`

```kotlin
val title = "Compose" // 引用不能重新赋值
var count = 0         // 可以重新赋值
count += 1
```

`val` 不是“对象内部绝对不可变”：

```kotlin
val names = mutableListOf("A")
names.add("B") // val 没变，但列表内容变了
```

Compose 推荐状态模型尽量不可变，是为了让变化来源清晰：

```kotlin
data class UiState(val count: Int = 0)

val oldState = UiState(count = 0)
val newState = oldState.copy(count = 1)
```

不要把 `val` 与“深度不可变”混为一谈。

## 3. 类型与类型推断

```kotlin
val name: String = "Alice"
val age: Int = 18
val enabled: Boolean = true
val progress: Float = 0.5f

// 编译器能推断时可以省略类型
val message = "Hello"
```

Compose API 的参数类型很重要：

```kotlin
Text(text = "标题")       // text 需要 String
Spacer(Modifier.size(8.dp)) // size 需要 Dp，不是普通 Int
```

`8`、`8f`、`8.dp` 和 `8.sp` 是不同概念，不能因为看起来都是数字就混用。

## 4. 可空类型与安全访问

```kotlin
val userName: String? = null

val length = userName?.length       // userName 为 null 时结果也是 null
val display = userName ?: "未登录" // Elvis：为空时使用默认值
```

非空断言：

```kotlin
val length = userName!!.length
```

`!!` 会在值为 null 时直接抛出异常。除非上游条件能被严格证明，否则优先使用安全访问、默认值或提前返回：

```kotlin
val name = userName ?: return
Text(name)
```

导航参数、网络响应和数据库查询都可能为空，空安全不是语法装饰，而是业务边界。

## 5. 函数、默认参数和命名参数

```kotlin
fun greeting(
    name: String,
    prefix: String = "你好",
): String {
    return "$prefix，$name"
}

val text = greeting(name = "小明")
```

Compose 函数大量使用默认参数和命名参数：

```kotlin
Text(
    text = "标题",
    color = Color.Red,
    maxLines = 1,
)
```

命名参数能让调用含义清晰，也避免记忆大量参数顺序。

## 6. Lambda 与函数类型

Lambda 是一段可以作为值传递的函数：

```kotlin
val onClick: () -> Unit = {
    println("clicked")
}
```

常见函数类型：

```text
() -> Unit          无参数、无返回值
(String) -> Unit    接收 String、无返回值
(Int) -> Boolean    接收 Int、返回 Boolean
@Composable () -> Unit 可组合 UI 内容
```

Compose 通过 Lambda 把事件向上传递：

```kotlin
@Composable
fun SaveButton(onSave: () -> Unit) {
    Button(onClick = onSave) {
        Text("保存")
    }
}
```

`SaveButton` 不需要知道保存到网络还是数据库，只负责报告点击事件。

## 7. 尾随 Lambda

如果函数的最后一个参数是 Lambda，可以写在圆括号外：

```kotlin
Button(onClick = { println("点击") }) {
    Text("按钮内容")
}
```

等价理解：

```kotlin
Button(
    onClick = { println("点击") },
    content = { Text("按钮内容") },
)
```

Column、Row、LazyColumn、MaterialTheme 等大量 Compose API 都使用尾随 Lambda 表达子内容。

## 8. class、data class 与 object

普通 class 用于描述有行为或生命周期的对象：

```kotlin
class Counter {
    var value: Int = 0
        private set

    fun increment() {
        value += 1
    }
}
```

data class 适合不可变数据：

```kotlin
data class User(
    val id: Long,
    val name: String,
)

val updated = user.copy(name = "新名字")
```

`object` 声明单例：

```kotlin
object Routes {
    const val HOME = "home"
}
```

不要因为 object 使用方便，就把所有可变全局状态都塞进去。全局可变状态很难测试，也不随页面生命周期释放。

## 9. sealed interface 表达有限状态

页面常见状态可以建模为：

```kotlin
sealed interface UserUiState {
    data object Loading : UserUiState
    data class Success(val name: String) : UserUiState
    data class Error(val message: String) : UserUiState
}
```

使用 `when`：

```kotlin
when (val state = uiState) {
    UserUiState.Loading -> LoadingContent()
    is UserUiState.Success -> Text(state.name)
    is UserUiState.Error -> ErrorContent(state.message)
}
```

sealed 类型让编译器知道所有分支，新增状态时能提醒你补齐 UI。

## 10. List、map、filter 与 firstOrNull

```kotlin
val numbers = listOf(1, 2, 3, 4)
val doubled = numbers.map { it * 2 }
val even = numbers.filter { it % 2 == 0 }
val firstLarge = numbers.firstOrNull { it > 10 } // Int?，结果可能为空
```

不要在 Composable 的高频执行路径中对大列表反复做昂贵转换。先保证结果正确，再根据性能测量决定是否移动或缓存计算。

更新列表状态时优先产生新列表：

```kotlin
items = items + newItem
items = items.filterNot { it.id == removedId }
```

原地修改普通 MutableList 不会自动让 Compose 知道内容发生了变化。

## 11. 扩展函数与扩展属性

扩展让现有类型拥有新的调用形式：

```kotlin
fun String.initial(): Char? = firstOrNull()

val first = "Compose".initial()
```

后续章节的 `8.dp`、`.px`、`.pxSp()` 都使用类似思想。扩展不会真正修改原类型，也不能绕过它的可见性规则。

## 12. 属性委托 `by`

Compose 状态常见两种写法：

```kotlin
val state = remember { mutableStateOf(0) }
Text("${state.value}")

var count by remember { mutableStateOf(0) }
Text("$count")
```

第二种使用 Kotlin 属性委托。读取 `count` 会委托给 State 的 `getValue`，赋值会委托给 `setValue`。

`by` 不是 Compose 独有语法，Compose 只是为 State 提供了相应运算符。

## 13. 泛型只需要先理解占位类型

```kotlin
data class Result<T>(
    val data: T?,
    val error: String?,
)

val userResult: Result<User>
val listResult: Result<List<User>>
```

`T` 表示调用方决定的类型。后续会在 `StateFlow<UiState>`、`List<ComponentInfo>`、`Saver<T, Saveable>` 中频繁看到泛型。

## 14. suspend 与协程的最小认识

```kotlin
suspend fun loadUser(): User {
    return api.fetchUser()
}

viewModelScope.launch {
    val user = loadUser()
    // 更新 UI State
}
```

`suspend` 表示函数可以挂起，不等于自动切到后台线程。真正在哪个线程或 Dispatcher 执行，由调用上下文和库实现决定。

先记住三点：

- 不要用线程睡眠模拟异步；
- 界面相关任务要有生命周期作用域；
- 取消和异常必须继续传播到正确边界。

详细内容在第 29、49、50、68、70、73 章展开。

## 15. 常见错误

1. 把 `val` 当成深度不可变，实际原地修改内部 MutableList。
2. 到处使用 `!!`，把可恢复的空值变成运行时崩溃。
3. 看见 Lambda 就认为会立即执行；Lambda 何时调用由接收它的函数决定。
4. 使用 object 保存所有页面状态，造成全局污染和生命周期问题。
5. 原地修改普通集合并期待 Compose 自动刷新。
6. 认为 suspend 自动等于后台线程。

## 16. 动手练习与验收

请在 Kotlin Playground 或练习工程中完成：

1. 定义不可变 User，并用 copy 更新名字。
2. 定义 Loading/Success/Error 三种 sealed UI State，并写完整 when。
3. 写一个接收 `(String) -> Unit` 的函数，并调用回调。
4. 从可空 List 中安全找到第一项，不使用 `!!`。
5. 使用 map/filter 把用户列表转换成满足条件的名字列表。
6. 解释 `var count by remember { mutableStateOf(0) }` 中 Kotlin 与 Compose 各负责哪部分。

验收标准：你能先读懂第 1—5 章的 Kotlin 代码，再进入 Compose API，而不是同时猜语言语法和框架行为。

## 17. 本章总结

Compose 代码看起来特殊，底层仍由 Kotlin 的函数、Lambda、类型、委托和协程组成。先把语言语法与 Compose 运行时职责分开：Kotlin 决定代码如何表达，Compose 决定状态读取、组合和 UI 更新如何运行。

官方参考：

- [Kotlin basic syntax](https://kotlinlang.org/docs/basic-syntax.html)
- [Kotlin null safety](https://kotlinlang.org/docs/null-safety.html)
- [Kotlin lambdas](https://kotlinlang.org/docs/lambdas.html)
- [Kotlin coroutines guide](https://kotlinlang.org/docs/coroutines-guide.html)
