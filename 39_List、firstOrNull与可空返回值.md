# 第 39 章：List、firstOrNull 与可空返回值

详情页拿到的 `componentId` 来自路由参数，路由参数是不可信输入：可能是外部深链拼错了 id，可能是旧版本书签指向一个已被删除的 Demo。查询「列表里有没有这一项」天然存在两种结局——找到、找不到。Kotlin 的做法是把「可能找不到」写进返回类型：`DemoEntry?`。本章讲这条类型规则，以及 `?.`、`?:`、`!!` 三种处理手段的取舍；这是 Kotlin 与 Dart 3 共享的设计思想，但两边的检查机制并不相同。

先划线：可空类型、安全调用、Elvis 是 Kotlin 通用语法；`findById` 这个查询入口和「null 当正常 UI 分支」的处理方式，是原 Demo 的具体应用。

## 1. 本章目标

理解查询可能失败时如何用可空类型表达，并在 UI 中安全兜底。

学完后，你应当能够：

- 使用 firstOrNull
- 读懂 ComponentInfo?
- 使用安全调用与 Elvis
- 通过 null 分支和智能转换访问非空对象
- 说清 first 与 firstOrNull 的行为差异
- 认识 `!!` 的适用边界与风险

## 2. 项目中的位置

- `model/ComponentInfo.kt`
- `ui/detail/ComponentDetailScreen.kt`

没有 compose_demo 工程的读者：本章 3.2 节的示例不依赖项目私有符号，可直接复制到任意含 Material 3 的 Compose 工程运行。

## 3. 项目源码片段

### 3.1 项目节选

以下节选依赖原 Demo 的 `ComponentRegistry` 与详情页 Scaffold 结构，不可直接复制，仅用于对照真实用法：

```kotlin
fun findById(id: String): ComponentInfo? =
    all.firstOrNull { it.id == id }

val info = ComponentRegistry.findById(componentId)
val title = info?.title ?: "未找到组件"

if (info == null) {
    Text("未找到组件: $componentId")
    return@Scaffold
}
Text(info.description)
```

### 3.2 独立可运行示例（不依赖项目代码）

为聚焦可空类型，这里用不含 UI 字段的最小模型（第 36 章的完整版多一个 `content` 字段）。两个界面共用同一份数据和同一个查询函数。

<!-- compile decls -->
```kotlin
// 最小模型：三个只读字段，够演示查询即可
data class DemoEntry(val id: String, val title: String, val description: String)

private val entries = listOf(
    DemoEntry("greeting", "问候语", "显示一行文字。"),
    DemoEntry("counter", "计数器", "点击按钮累加。"),
)

// 查询可能失败：返回类型写成 DemoEntry?（读作「DemoEntry 或 null」）
fun findById(id: String): DemoEntry? = entries.firstOrNull { it.id == id }

// ---------- 用法一：null 当成正常 UI 分支 ----------

@Composable
fun LookupDemoScreen(componentId: String) {
    val info = findById(componentId) // 类型是 DemoEntry?，编译器不允许当非空用

    if (info == null) {
        // 找不到就明说，而不是崩溃
        Text("未找到组件：$componentId")
        return
    }
    // 经过上面的 null 检查后，智能转换把 info 视为非空 DemoEntry
    Column(Modifier.padding(16.dp)) {
        Text(info.title, style = MaterialTheme.typography.titleMedium)
        Text(info.description)
    }
}

// ---------- 用法二：三个可空操作符的对照 ----------

@Composable
fun NullOperatorDemo() {
    val info: DemoEntry? = findById("counter")

    val title = info?.title                    // ?. 安全调用：非空才取 title，否则整个表达式是 null
    val shown = info?.title ?: "未找到组件"     // ?: Elvis：左侧为 null 时取右侧
    val missing = findById("nope")?.title ?: "未找到组件" // 查不到 → Elvis 右侧
    val byFirstOrNull = entries.map { it.id }.firstOrNull { it == "image" } // 也是 null

    Column {
        Text("title = $title")          // 计数器（这次找到了）
        Text("shown = $shown")          // 计数器
        Text("missing = $missing")      // 未找到组件
        Text("firstOrNull 查 image = $byFirstOrNull") // null
    }
}
```

操作步骤与预期观察（运行行为为预期观察，待真机验证）：

1. 调用 `LookupDemoScreen("counter")`。预期：显示「计数器」和说明文字。
2. 改成 `LookupDemoScreen("nope")`。预期：显示「未找到组件：nope」，不崩溃。
3. 对照 `NullOperatorDemo` 的四行输出与注释，逐个核对 `?.` 与 `?:` 的求值路径。

## 4. 逐步解释

### 4.1 firstOrNull：把「找不到」编码进类型

firstOrNull 从前向后检查条件，找到第一个匹配对象就返回，找不到返回 null。返回类型因此是 ComponentInfo?，调用方必须处理。

「调用方必须处理」是编译期强制的：Kotlin 里类型默认不可空，`DemoEntry?` 是另一个类型（读作「DemoEntry 或 null」，类名加问号即得）；对它直接 `.title` 编不过，编译器逼你在「兜底、检查、断言」里三选一。这套规则的基础见 [Kotlin 空安全文档](https://kotlinlang.org/docs/null-safety.html)，`firstOrNull` 的 API 说明见[官方参考](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/first-or-null.html)。

`first` 与 `firstOrNull` 只差「找不到时给什么」：`first { ... }` 无匹配直接抛 `NoSuchElementException`，`firstOrNull { ... }` 给 null。选择标准是「绝不可能找不到」是否成立：查一个静态枚举表里的固定键，`first` 可用；查来自路由、网络、用户输入的 id，`firstOrNull` 才是对的——异常应该留给「程序 bug」，不该用来承载「数据不存在」这种正常业务结局。

### 4.2 三种处理手段：`?.`、`?:`、`!!`

- `info?.title` 只在 info 非空时读取 title，为空时整个表达式是 null——「试一下，不行就算了」。
- Elvis `?:` 为 null 提供默认值——「不行就换备胎」。`info?.title ?: "未找到组件"` 两段连用，是 Kotlin 里最常见的「安全取值加兜底」句式（第 4 章读路由参数时已用过一次）。
- `info!!` 是强制断言「我保证它非空」：编译通过，运行时若为 null 直接抛 `NullPointerException`。它把检查责任从编译器搬回了自己身上，只适合「上下文逻辑上已排除 null、类型系统却看不出来」的少数场合——比如 null 检查跨越了 lambda 边界导致智能转换失效时。对路由参数这种不可信输入用 `!!`，等于把「深链拼错」升级成「应用崩溃」。

### 4.3 null 分支与智能转换

显式 `if (info == null)` 并提前返回后，Kotlin 能智能转换后续 info 为非空。机制是编译器的流分析（flow analysis）：`return` 之后的代码只有 info 非空才可能到达，编译器据此把 `info` 当 `DemoEntry` 用，`info.description` 不需要任何问号或感叹号。相比 `first` 或 `!!`，这种写法能安全应对非法深链、拼错路由或注册项被删除；相比一路 `?.`，它把「找不到」的处理集中到一个分支里，后面的正常路径保持干净——详情页两种结局各占一段，读者一眼看清。

UI 里 null 分支该给什么内容由产品决定：原 Demo 显示「未找到组件: id」加返回入口，让用户能退回去而不是停在空白页。用空字符串或占位对象掩盖 null，只会让错误更难发现——路由配错时页面「看起来正常」，排查成本更高。

## 5. 实际执行过程

1. 接收 componentId（来自路由参数，不可信）
2. firstOrNull 遍历列表匹配 id
3. 找到返回对象；找不到返回 null
4. 标题用 `?.` + `?:` 安全兜底
5. 内容区判 null：null 分支显示提示并提前返回
6. 非空分支经智能转换正常渲染

## 6. Flutter / Dart 3 对照

Dart 3 同样有可空类型和流程分析，检查 null 后可类型提升。

```dart
ComponentInfo? findById(String id) {
  for (final item in all) {
    if (item.id == id) return item;
  }
  return null;
}
final info = findById(id);
if (info == null) return const Text('未找到');
return Text(info.description);
```

操作符对照（语法相似，仅此而已）：

| 含义 | Kotlin | Dart 3 |
| --- | --- | --- |
| 可空类型 | `DemoEntry?` | `ComponentInfo?` |
| 安全调用 | `info?.title` | `info?.title` |
| 空时取右值 | `info?.title ?: "默认"` | `info?.title ?? '默认'` |
| 强制断言非空 | `info!!` | `info!` |
| null 检查后直接用 | 智能转换（smart cast） | 类型提升（type promotion） |

对照边界（概念对照，不是等价声明）：两边都把「可能为 null」编码进类型系统、都在 null 检查后允许直接使用，但检查器是各自编译器的独立实现——智能转换与类型提升的规则细节不同（例如对可变变量、实例字段能否提升，两边条件不一样；Dart 对不满足提升条件的字段常要先赋给局部变量），Kotlin 还有与 Java 互操作带来的平台类型问题而 Dart 没有对应概念。共享的是设计思想，不是同一套机制，详见 [Dart 空安全文档](https://dart.dev/null-safety)。另外 `!`/`!!` 两边同样都不推荐滥用。

## 7. 常见错误

- 对不可信 id 使用 first，找不到直接抛异常
- 使用 info!! 强制解包
- 只兜底标题却继续访问 null 内容
- 用空字符串掩盖路由错误

反例一：用 `!!` 对待不可信输入。

```kotlin
// 危险示例（能通过编译，运行时可能崩溃，不参与编译校验，不要照抄）
val info = DemoRegistry.findById(componentId)
Text(info!!.description) // componentId 是路由参数：拼错或过期时这里抛 NullPointerException
```

它的问题不是语法而是责任转移：`!!` 之后，「深链拼错」从可兜底的分支变成必崩的异常。改法就是 3.2 节的 null 分支提前返回。

反例二：只兜底了一半。

```kotlin
// 错误示例（故意错误，不参与编译）
val title = info?.title ?: "未找到组件" // 标题兜底了
Text(info.description)                  // 这行直接编不过：info 仍是 DemoEntry?
```

`?.` 和 `?:` 只处理当前这一个表达式，不会改变 `info` 本身的类型；后续每次使用仍要面对可空。零散兜底容易漏出缺口，整段处理（null 分支提前返回，之后智能转换接管）更稳。

## 8. 本章检查题

1. first 与 firstOrNull 的差异是什么？
2. Elvis 操作符何时使用右侧值？
3. null 检查后为什么能直接访问 description？
4. （预测输出）`findById("counter")?.title ?: "未找到组件"` 与 `findById("nope")?.title ?: "未找到组件"` 各得到什么？
5. （动手修改）把 `LookupDemoScreen` 的 null 分支提示改成「未找到 X，可先看看 greeting」，让用户能直接得到一个有效 id（提示：拼接 `findById("greeting")?.title` 的结果，注意它本身可能为 null）。改完先预测 `LookupDemoScreen("nope")` 的显示，再运行验证。

答案：

1. first 找不到匹配项会抛异常；firstOrNull 返回 null，安全。
2. 左侧表达式为 null 时（`info?.title ?: "默认"` 中 info 为 null 时取右侧默认值）。
3. 显式 `if (info == null)` 提前返回后，Kotlin 智能转换（smart cast）把 info 视为非空，后续可直接访问其属性。
4. 前者是「计数器」（查到了，Elvis 不生效）；后者是「未找到组件」（`?.` 得到 null，Elvis 取右侧）。
5. 用 `val hint = findById("greeting")?.title ?: "目录为空"` 保证拼接的两段都非空后，`LookupDemoScreen("nope")` 显示「未找到 nope，可先看看 问候语」。验证点：拼接处又用了一次 `?:`——可空值一旦要进入非空上下文（字符串模板给人看时不明显，但逻辑上仍是「必须有值」），每次都得处理。

## 9. 本章总结

用可空返回表达「可能找不到」，并把 null 当成正常 UI 分支处理。`firstOrNull` 负责把失败编码进类型，null 分支提前返回加智能转换负责消化它，`?.` 加 `?:` 适合单点兜底，`first` 与 `!!` 只留给「绝不可能失败」的场合。这套规则与 Dart 3 空安全思想相通、机制各自独立。至此，模型（第 36 章）、函数字段（第 37 章）、注册表（第 38 章）、查询兜底（本章）都齐了——下一章把它们串成一个可照着走的流程：添加一个新 Demo。
