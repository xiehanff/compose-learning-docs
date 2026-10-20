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

后续章节里的状态对象推荐尽量不可变，是为了让变化来源清晰：

```kotlin
data class CounterState(val count: Int = 0)

val oldState = CounterState(count = 0)
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

参数类型在编译期就会检查，写错会直接编译失败：

```kotlin
fun drawLabel(text: String, size: Int, alpha: Float) { }

drawLabel(text = "标题", size = 8, alpha = 0.5f)
```

看起来都是数字，类型却不同：

```text
8    Int
8L   Long
8f   Float
```

Kotlin 不会自动在这些类型之间转换，不能混用。后续章节还会出现带单位后缀的数值类型（第 41—45 章），它们同样各有自己的类型。

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
println(name)
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

Kotlin 函数在声明和调用时都常用默认参数与命名参数：

```kotlin
fun printTitle(
    text: String,
    color: String = "black",
    maxLines: Int = 1,
) {
    println("$text / $color / $maxLines")
}

printTitle(
    text = "标题",
    color = "red",
    maxLines = 1,
)
```

命名参数能让调用含义清晰，也避免记忆大量参数顺序；有默认值的参数可以省略，也可以只传其中一部分。

## 6. Lambda 与函数类型

Lambda 是一段可以作为值传递的函数：

```kotlin
val onClick: () -> Unit = {
    println("clicked")
}
```

常见函数类型：

```text
() -> Unit            无参数、无返回值
(String) -> Unit      接收 String、无返回值
(Int) -> Boolean      接收 Int、返回 Boolean
(Int) -> String       接收 Int、返回 String
```

把 Lambda 当作参数接收的函数，就是高阶函数：

```kotlin
fun runThenReport(task: () -> Unit, report: (Boolean) -> Unit) {
    task()
    report(true)
}

runThenReport(
    task = { println("执行任务") },
    report = { ok -> println("结果：$ok") },
)
```

`runThenReport` 不需要知道任务具体做了什么，只负责在合适的时机调用传进来的 Lambda。

## 7. 尾随 Lambda

如果函数的最后一个参数是 Lambda，可以写在圆括号外：

```kotlin
fun withLog(prefix: String, block: () -> Unit) {
    println("开始 $prefix")
    block()
    println("结束 $prefix")
}

withLog("任务") {
    println("任务内容")
}
```

等价理解：

```kotlin
withLog(
    prefix = "任务",
    block = { println("任务内容") },
)
```

后续章节会看到大量 Compose 组件（如第 8 章的 Column、Row，第 16 章的 LazyColumn）都用尾随 Lambda 表达子内容。

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

val user = User(id = 1, name = "小明")
val updated = user.copy(name = "新名字")
```

`object` 声明单例（单例指整个程序里只会创建一个实例的对象；第 38 章会用 `object` 组织全局注册表）：

```kotlin
object Routes {
    const val HOME = "home"
}
```

不要因为 object 使用方便，就把所有可变全局状态都塞进去。全局可变状态很难测试，也不随页面生命周期释放。

## 9. sealed interface 表达有限状态

页面加载过程这类有限状态可以建模为：

```kotlin
sealed interface LoadState {
    data object Loading : LoadState
    data class Success(val name: String) : LoadState
    data class Error(val message: String) : LoadState
}
```

使用 `when`：

```kotlin
when (val state = loadState) {
    LoadState.Loading -> println("加载中")
    is LoadState.Success -> println("你好，${state.name}")
    is LoadState.Error -> println("出错：${state.message}")
}
```

sealed 类型让编译器知道所有分支，新增状态时能提醒你补齐处理逻辑。

## 10. List、map、filter 与 firstOrNull

```kotlin
val numbers = listOf(1, 2, 3, 4)
val doubled = numbers.map { it * 2 }
val even = numbers.filter { it % 2 == 0 }
val firstLarge = numbers.firstOrNull { it > 10 } // Int?，结果可能为空
```

不要在会被频繁调用的代码路径中对大列表反复做昂贵转换。先保证结果正确，再根据性能测量决定是否移动或缓存计算。

更新列表状态时优先产生新列表：

```kotlin
items = items + newItem
items = items.filterNot { it.id == removedId }
```

原地修改普通 MutableList 不会让使用方自动感知到变化，所以要显式产生新列表。

## 11. 扩展函数与扩展属性

扩展让现有类型拥有新的调用形式：

```kotlin
fun String.initial(): Char? = firstOrNull()

val first = "Compose".initial()
```

后续章节的 `8.dp`、`.px`、`.pxSp()` 都使用类似思想。扩展不会真正修改原类型，也不能绕过它的可见性规则。

## 12. 属性委托 `by`

属性委托把属性的读写转发给另一个对象：

```kotlin
import kotlin.reflect.KProperty

// 标准库自带的委托：第一次访问时才求值，之后复用结果
val config: String by lazy { "default" }

// 自定义委托
class Logged<T>(private var value: T) {
    operator fun getValue(thisRef: Any?, property: KProperty<*>): T {
        println("读取 ${property.name}")
        return value
    }

    operator fun setValue(thisRef: Any?, property: KProperty<*>, newValue: T) {
        println("写入 ${property.name} = $newValue")
        value = newValue
    }
}

var count by Logged(0)
count = 3       // 走 setValue
println(count)  // 走 getValue
```

`by` 是 Kotlin 语言特性，不是某个框架独有的语法：只要委托对象提供 `getValue`/`setValue` 运算符约定，属性的读写就会交给它。第 21 章会看到 Compose 用同一套约定保存界面状态。

## 13. 泛型只需要先理解占位类型

```kotlin
data class Result<T>(
    val data: T?,
    val error: String?,
)

val sampleUser = User(id = 1, name = "小明")
val userResult: Result<User> = Result(data = sampleUser, error = null)
val listResult: Result<List<User>> = Result(data = listOf(user), error = null)
```

`T` 表示调用方决定的类型。泛型函数则把类型参数写在函数名之后：

```kotlin
fun <T> describe(value: T): String = "值：$value"

describe("A") // T 是 String
describe(1)   // T 是 Int
```

泛型的价值是同一份代码可以复用在不同类型上，同时保留编译期类型检查。后续代码里常见的 `List<String>`、`Map<String, Int>` 都是泛型用法。

## 14. suspend 与协程的最小认识

```kotlin
// delay 由 kotlinx.coroutines 提供
suspend fun fetchName(): String {
    delay(100) // 挂起 100 毫秒，而不是阻塞线程
    return "Alice"
}

suspend fun greet() {
    val name = fetchName()
    println("你好，$name")
}
```

`suspend` 表示函数可以挂起，不等于自动切到后台线程。真正在哪个线程上执行，由调用上下文和库实现决定。suspend 函数也只能在另一个 suspend 函数或协程作用域中被调用。

先记住三点：

- 不要用线程睡眠（`Thread.sleep`）模拟异步挂起；
- 挂起任务要放在受控的作用域里，才能被取消；否则会出现“已经不需要结果，任务还在跑”的浪费和过期回调；
- 取消和异常必须继续传播到正确边界。

详细内容在第 29、49、50、68、70、73 章展开。

## 15. 常见错误

1. 把 `val` 当成深度不可变，实际原地修改内部 MutableList。
2. 到处使用 `!!`，把可恢复的空值变成运行时崩溃。
3. 看见 Lambda 就认为会立即执行；Lambda 何时调用由接收它的函数决定。
4. 使用 object 保存所有页面状态，造成全局污染和生命周期问题。
5. 原地修改普通集合并以为使用方会自动感知到变化。
6. 认为 suspend 自动等于后台线程。

## 16. 动手练习与验收

请在 Kotlin Playground 或练习工程中完成：

1. 定义不可变 User，并用 copy 更新名字。
2. 定义 Loading/Success/Error 三种 sealed 状态，并写完整 when。
3. 写一个接收 `(String) -> Unit` 的函数，并调用回调。
4. 从可空 List 中安全找到第一项，不使用 `!!`。
5. 使用 map/filter 把用户列表转换成满足条件的名字列表。
6. 用 `by lazy` 或自定义委托写一个属性，并解释 `getValue`、`setValue` 分别在什么时机被调用。

验收标准：你能先读懂第 1—5 章的 Kotlin 代码，再进入 Compose API，而不是同时猜语言语法和框架行为。

## 17. 本章总结

后续 Compose 代码看起来特殊，底层仍由 Kotlin 的函数、Lambda、类型、委托和协程组成。先把语言语法与框架运行时的职责分开：Kotlin 决定代码如何表达，框架决定状态读取、组合和界面更新如何运行。

官方参考：

- [Kotlin basic syntax](https://kotlinlang.org/docs/basic-syntax.html)
- [Kotlin null safety](https://kotlinlang.org/docs/null-safety.html)
- [Kotlin lambdas](https://kotlinlang.org/docs/lambdas.html)
- [Kotlin coroutines guide](https://kotlinlang.org/docs/coroutines-guide.html)
