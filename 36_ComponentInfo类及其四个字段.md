# 第 36 章：ComponentInfo 类及其四个字段

首页要列出所有 Demo，详情页要展示某个 Demo 的说明和示例，路由要靠一个标识定位目标——三处用的是同一份数据。如果标题写在首页、说明写在详情页、示例调用写进 when 分支，每加一个 Demo 就要改三个文件。原 Demo 的解法是先定义一个数据类，把「一个 Demo 的全部描述」收敛成一个对象，这就是 `ComponentInfo`。

先划一条线：`ComponentInfo` **不是 Compose 官方类型**，是原 `compose_demo` 项目自定义的数据类，Compose 的 API 里查不到这个名字。本章要学的通用知识是「用 Kotlin 类给 UI 元数据建模，并把『可运行的 UI』当成一种数据保存」；至于字段恰好叫 `id/title/description/composeExample`、文件放在 `model/ComponentInfo.kt`，属于原 Demo 的私有方案，换个项目完全可以有自己的命名和字段设计。

## 1. 本章目标

理解项目用普通 Kotlin 类把 Demo 元数据和可执行 UI 组合成统一模型。

学完后，你应当能够：

- 看懂构造参数和 val 属性
- 理解四个字段的用途
- 知道类本身不需要 @Composable
- 理解普通 class 与 data class 的取舍
- 分清「官方类型」与「项目自定义类型」
- 用「数据列表 + items + 点击回调」从模型生成入口列表

## 2. 项目中的位置

- `model/ComponentInfo.kt`

没有 compose_demo 工程的读者：本章 3.2 节的示例不依赖任何项目私有符号，可直接复制到任意含 Material 3 的 Compose 工程运行。

## 3. 项目源码片段

### 3.1 项目节选

以下节选依赖原 Demo 的包结构，不可直接复制，仅用于对照真实用法：

```kotlin
class ComponentInfo(
    val id: String,
    val title: String,
    val description: String,
    val composeExample: @Composable () -> Unit,
)
```

注意两点：这是普通 `class` 而不是 `data class`（原因见 4.3 节）；`composeExample` 的类型是 `@Composable () -> Unit`——一个「可以组合出 UI 的函数值」，第 37 章会专门拆开讲。

### 3.2 独立可运行示例（不依赖项目代码）

下面用同构的最小模型 `DemoEntry` 重写同样的思路：四字段建模 + 数据列表 + 自动生成入口列表。为了不引入导航依赖，这里先用一个选中状态模拟「点击进入详情」，第 40 章会把它换成真正的 NavHost。

<!-- compile decls -->
```kotlin
// ---------- 数据模型：与项目 ComponentInfo 同构，改名为 DemoEntry 以表明这是本章自建示例 ----------

// data class：编译器自动生成 equals/copy/解构等成员的类，适合纯数据建模（见 4.3 节）
data class DemoEntry(
    val id: String,                      // 稳定唯一标识：列表 key、路由参数、查询都靠它
    val title: String,                   // 入口列表与详情页显示的标题
    val description: String,             // 详情页的说明文本
    val content: @Composable () -> Unit, // 「可运行的 UI」：调用时才组合出界面（第 37 章详解）
)

@Composable
private fun CounterExample() {
    var count by remember { mutableIntStateOf(0) }
    Button(onClick = { count++ }) { Text("点了 $count 次") }
}

// 注册数据：每条描述一个 Demo；注意 content 传入的是 lambda，不是调用结果
private val demoEntries = listOf(
    DemoEntry(
        id = "greeting",
        title = "问候语",
        description = "显示一行文字。",
        content = { Text("你好，Compose") },
    ),
    DemoEntry(
        id = "counter",
        title = "计数器",
        description = "点击按钮累加。",
        content = { CounterExample() },
    ),
)

// ---------- 入口列表 + 模拟详情：模板代码不认识任何一个具体示例 ----------

@Composable
fun DemoCatalogScreen() {
    // 选中状态模拟跳转：null 显示列表，非 null 显示对应详情
    var selectedId by remember { mutableStateOf<String?>(null) }

    if (selectedId == null) {
        LazyColumn {
            // items 为集合中每个元素登记一个条目；key 用稳定 id（第 16、17 章详解）
            items(demoEntries, key = { it.id }) { entry ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable { selectedId = entry.id }
                        .padding(16.dp),
                ) {
                    Text(entry.title, style = MaterialTheme.typography.titleMedium)
                    Text(entry.description, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    } else {
        // 通用详情模板：查 id、展示说明、调用存的 UI 代码，全程没有 when
        val entry = demoEntries.firstOrNull { it.id == selectedId }
        if (entry == null) {
            Text("未找到组件：$selectedId")
        } else {
            Column(
                Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(entry.title, style = MaterialTheme.typography.titleLarge)
                Text(entry.description)
                entry.content() // 关键一行：在这里才执行示例 UI
                TextButton(onClick = { selectedId = null }) { Text("返回列表") }
            }
        }
    }
}
```

示例依赖：任意含 Material 3 的 Compose 工程（本仓库 snippets 基线为 Compose BOM 2026.06.00），不需要导航库。

初始界面：两行入口，「问候语」与「计数器」，每行显示标题和一句说明。

操作步骤与预期观察（运行行为为预期观察，待真机验证）：

1. 点击「计数器」。预期：列表消失，显示标题「计数器」、说明文字、一个「点了 0 次」按钮和「返回列表」。
2. 点 3 次按钮。预期：按钮文字变成「点了 3 次」。
3. 点「返回列表」再进「计数器」。预期：按钮回到「点了 0 次」——示例的 `remember` 状态随离开组合丢弃（第 21、34 章）。

## 4. 逐步解释

### 4.1 为什么要建这样一个类

id 同时用于路由参数、列表 key 和查询；title 用于首页和详情标题；description 是说明文本；composeExample 保存稍后可执行的 UI 函数。`@Composable () -> Unit` 可读作「一个带 Compose 标记、没有参数、调用后不返回值的函数」；第 37 章会专门讲这种函数值。

把它们合成一个对象，收获是「一份定义，三处消费」：

| 消费方 | 读取的字段 | 如果没有统一模型 |
| --- | --- | --- |
| 首页列表 | id（作 key）、title | 首页手写一份标题列表 |
| 路由 | id（作参数） | 跳转时现拼字符串，无处校验 |
| 详情页 | id（查询）、title、description、composeExample | 详情页写一堆 when 分支 |

其中最有特点的是第四个字段：把「可运行的 UI」当数据存。这不是 Compose 官方规定的做法，而是 Kotlin 函数值是「一等公民」带来的自由——函数可以像字符串一样存进属性、放进列表、稍后调用。存的结果是详情页可以完全通用：它不知道自己展示的是计数器还是问候语，只负责查 id、调 `content()`。这个机制的细节（什么时候真的执行、为什么不会在启动时全部组合）留到第 37 章。

### 4.2 从数据列表生成入口列表

3.2 节的首页没有为任何 Demo 写过一行入口代码，入口完全由数据生成：

- `items(demoEntries, key = { it.id })` 为列表里每个 `DemoEntry` 登记一个条目（第 16 章）。
- `key = { it.id }` 把条目身份绑定到稳定 id，插入删除后列表能正确追踪（第 17 章）。
- `clickable { selectedId = entry.id }` 只把 id 交给上层；真实工程里这里换成 `onNavigate(entry.id)` 回调，由导航层执行跳转（第 4、5 章）。

推论：往 `demoEntries` 里加一条数据，首页自动多一行入口——这就是第 40 章「新增 Demo 不改首页」的基础。

### 4.3 data class 语法（首次出现）与原项目的取舍

`data class` 是 Kotlin 专门给「承载数据的类」准备的写法：只要在 `class` 前加 `data`，编译器就会按主构造参数自动生成结构相等的 `equals`/`hashCode`、`copy`（只替换指定参数、其余照抄）、`toString`，以及按参数顺序解构的 `componentN`。用最小模型看一遍自动生成的成员：

<!-- compile decls -->
```kotlin
// 用最小两字段模型演示 data class 的自动成员；DemoEntry 同理
data class Point(val x: Int, val y: Int)

@Composable
fun DataClassFeatureDemo() {
    val p1 = Point(1, 2)
    val p2 = Point(1, 2)
    val moved = p1.copy(x = 10) // copy：只改 x，y 照抄 → Point(10, 2)
    val (x, y) = moved          // 解构声明：按声明顺序取字段（component1、component2）
    Column {
        Text("结构相等：${p1 == p2}") // true：equals 按字段逐个比较
        Text("copy 后解构：$x, $y")   // 10, 2
    }
}
```

那原项目为什么用普通 `class`？因为项目只创建条目并读取属性，不依赖 copy、componentN 或结构相等。若改成 data class，要考虑函数字段的比较是否符合业务含义——函数实例是按引用比较的，两个 id、title 完全相同但 lambda 各自新建的 `DemoEntry`，`==` 结果是 false；「字段像相等」和「结构相等」在这里会对不上号。这是作者的工程取舍（属于原 Demo 方案，不是通用规范）：需要 copy、解构或把条目放进 Set/Map key 时用 data class；只做「创建 + 读」时普通 class 也够。

ComponentInfo 只是数据模型，不直接绘制，所以类不需要 @Composable。`@Composable` 约束的是「会在组合中执行的函数」；`composeExample` 属性的**类型**带这个标记，但读取属性、比较对象都不触发组合，真正调用它的地方（详情页的 `entry.content()`）才在组合中。

## 5. 实际执行过程

1. 顶层创建 ComponentInfo 对象（进程首次访问注册表时，见第 38 章；此刻只创建 lambda，不执行任何示例 UI）
2. Registry 收集对象
3. 首页读取 id/title，用 items 生成入口
4. 点击把 id 交给导航，详情页读路由参数
5. 详情读取 description
6. 详情调用 composeExample，示例 UI 这时才第一次组合

整条链路里，示例 UI 的组合只发生在第 6 步——「存函数」不等于「执行函数」。

## 6. Flutter / Dart 3 对照

Dart 3 可用 final class 保存字符串和 `Widget Function()`。

```dart
final class ComponentInfo {
  const ComponentInfo({
    required this.id,
    required this.title,
    required this.description,
    required this.buildExample,
  });
  final String id;
  final String title;
  final String description;
  final Widget Function() buildExample;
}
```

对照边界（概念对照，不是等价声明）：

- 相似：两边都是「把构建 UI 的代码塞进对象字段，稍后调用」，都能实现通用详情页。
- 差异一（相等语义）：Kotlin 的 data class 自动生成结构相等；Dart 的类默认按实例身份比较，要手写 `==`/`hashCode` 或用代码生成包（Dart 3 的 record 有内建结构相等，但 record 不适合这个带函数字段的场景）。
- 差异二（函数语义）：`Widget Function()` 是普通闭包，调用只是返回一个 Widget 实例；`@Composable () -> Unit` 由 Compose 编译器插件处理，调用会参与组合与重组跟踪。机制不等价，第 37 章展开。

## 7. 常见错误

- id 使用会变化的标题
- 把 ComponentInfo 当页面实例
- composeExample 写成立即调用
- 盲目改 data class 而不需要结构语义

反例：注册时立即调用。

```kotlin
// 错误示例（故意错误，不参与编译）
val broken = DemoEntry(
    id = "greeting",
    title = "问候语",
    description = "显示一行文字。",
    content = Text("你好，Compose"), // content 需要函数值，这里却传了调用结果（Unit），类型不匹配
)
```

两个问题叠加：类型上，`Text(...)` 返回 Unit，塞不进 `@Composable () -> Unit` 字段，编译直接报错；即使类型碰巧能过，立即调用也意味着「启动时就组合 UI」，彻底失去「进详情才执行」的延迟性。正确写法是 `content = { Text("你好，Compose") }`。

另一个方向的错误是把 id 写成会变的展示文本（比如中文标题）。id 同时是路由参数、列表 key 和查询键（4.1 节），一旦变化，旧版本保存的深链、书签全部失效。id 应是稳定的机器标识，title 才是给人看的。

## 8. 本章检查题

1. id 至少承担哪些作用？
2. 为什么类不需要 @Composable？
3. composeExample 的类型是什么？
4. （预测行为）3.2 节示例里，进入「计数器」详情点 3 次按钮，「返回列表」后再进「计数器」，按钮显示什么？为什么？
5. （动手修改）给 `demoEntries` 复制一条新条目，id 改成 `"greeting2"`、title 改成「第二句问候」、content 改成 `{ Text("你好 again") }`。先预测首页行数和点击新入口后的界面，再运行验证。

答案：

1. 同时承担路由参数、列表 key 和查询定位三种作用。
2. ComponentInfo 只是数据模型，本身不直接绘制 UI，不需要 @Composable；需要这个标记的是它字段的函数类型，以及真正调用该函数的组合代码。
3. `@Composable () -> Unit`，保存稍后可执行的 UI 函数。
4. 「点了 0 次」。CounterExample 的计数存在组件内部 `remember` 里，返回列表时详情分支离开组合，状态被丢弃；重新进入时从头创建（第 21、34 章）。
5. 首页从 2 行变 3 行（数据多一条，入口自动生成）；点击新入口显示标题「第二句问候」、说明与文字「你好 again」。验证点：首页代码一行没改。

## 9. 本章总结

ComponentInfo（以及它的通用形态 DemoEntry）是统一 Demo 描述模型，让首页和详情页读取同一份数据：id 是身份，title/description 是展示，函数字段让「可运行的 UI」也能当数据存。它是原 Demo 的私有设计而不是官方类型，但背后的建模手法——数据类 + 函数值 + 数据驱动的入口列表——是可以在任何 Compose 工程复用的。下一章拆开那个函数类型：`@Composable () -> Unit` 到底是什么、什么时候真的执行。
