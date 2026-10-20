# 第 37 章：@Composable () -> Unit 作为可运行 UI 数据

第 36 章把「可运行的 UI」存进了数据类字段，但留了一个问题：`composeExample = { TextExample() }` 这行注册时不执行，进详情页才执行——凭什么一段代码能「存着不跑、想跑再跑」？本章拆开这个函数类型：它和普通 `() -> Unit` 差在哪、怎么传、什么时候真的执行。这也是理解「通用详情页不需要 when」的机制基础。

先划线：`@Composable () -> Unit` 是 Kotlin 函数类型加 Compose 编译器插件语义的组合，属于通用知识；把它放进 `ComponentInfo` 字段、在详情页统一调用的用法，是原 Demo 的组织方式。

## 1. 本章目标

理解函数可以保存为值，以及保存函数与立即调用函数的区别。

学完后，你应当能够：

- 读懂无参数函数类型
- 理解 @Composable 修饰函数类型
- 区分 `{ Demo() }` 与 `Demo()`
- 理解详情页的动态调用
- 说清 `@Composable () -> Unit` 与普通 `() -> Unit` 的类型差异
- 把 Composable lambda 当参数传递并延迟调用

## 2. 项目中的位置

- `model/ComponentInfo.kt`
- `ui/detail/ComponentDetailScreen.kt`

没有 compose_demo 工程的读者：本章 3.2 节的示例不依赖项目私有符号，可直接复制到任意含 Material 3 的 Compose 工程运行。

## 3. 项目源码片段

### 3.1 项目节选

以下节选依赖原 Demo 的 `ComponentInfo` 与详情页结构，不可直接复制，仅用于对照真实用法：

```kotlin
val composeExample: @Composable () -> Unit

val TextDemo = ComponentInfo(
    id = "text",
    title = "Text",
    description = "...",
    composeExample = { TextExample() },
)

info.composeExample()
```

三行代码三个角色：类型声明（字段类型）、注册（创建并保存 lambda）、调用（详情页里执行它）。

### 3.2 独立可运行示例（不依赖项目代码）

用一个「通用卡片外壳」演示同一套机制：外壳接收一段 UI 代码，自己决定在什么位置、什么时机调用它。

<!-- compile decls -->
```kotlin
// 通用外壳：最后一个参数是 @Composable 函数类型——调用方把「一段 UI 代码」传进来
@Composable
private fun DemoCard(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content() // 延迟调用：传进来的代码在这里才执行，位置由外壳决定
        }
    }
}

@Composable
private fun CounterContent() {
    var count by remember { mutableIntStateOf(0) }
    Button(onClick = { count++ }) { Text("点了 $count 次") }
}

@Composable
fun DeferredCallDemoScreen() {
    // Column 把两张卡片纵向排开：不套容器的话两个根级 DemoCard 会叠放在左上角
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // 传法一：尾随 lambda——最后一个函数类型参数直接写在大括号里（第 5 章）
        DemoCard(title = "计数器") {
            CounterContent() // 注意：此刻不执行，只是把「调用 CounterContent」这段代码打包传入
        }

        // 传法二：先存成值再传——函数值是一等公民，能赋值给 val、能当普通参数传
        val greeting: @Composable () -> Unit = { Text("你好，Compose") }
        DemoCard(title = "问候语", content = greeting)
    }
}
```

初始界面：两张上下排列的卡片。第一张含标题「计数器」和「点了 0 次」按钮；第二张含标题「问候语」和一行「你好，Compose」。

操作步骤与预期观察（运行行为为预期观察，待真机验证）：

1. 点第一张卡片的按钮 3 次。预期：按钮文字变「点了 3 次」，第二张卡片不受影响。
2. 交换两个 `DemoCard` 调用的书写顺序。预期：界面两张卡片的顺序跟着交换——外壳只负责「在哪调」，内容跟注册走。

再看普通函数类型与 Composable 函数类型的差别（同样可独立运行）：

<!-- compile decls -->
```kotlin
@Composable
fun FunctionTypeKindDemo() {
    // 普通 () -> Unit：可存、可传、可在任何地方调用，但函数体里不能组合 UI
    val plain: () -> Unit = { println("普通函数值：做普通计算") }
    // @Composable () -> Unit：函数体里可以组合 UI，但只能在可组合上下文中被调用
    val ui: @Composable () -> Unit = { Text("可组合函数值：能组合出 UI") }

    Column {
        ui() // 当前在 @Composable 函数体内，调用合法
        Button(onClick = plain) { Text("执行普通函数值") } // onClick 恰好要 () -> Unit
    }
}
```

## 4. 逐步解释

### 4.1 先讲清函数类型（首次出现）

`() -> Unit` 是 Kotlin 的函数类型，读作「不接收参数、返回 Unit 的函数」。函数在 Kotlin 里是一等公民：可以赋值给 `val`、当参数传、当返回值、存进数据类属性——和存一个字符串没有语法上的区别（函数类型基础见预备篇 D 第 6 节与 [Kotlin 高阶函数与 lambda](https://kotlinlang.org/docs/lambdas.html)）。`{ TextExample() }` 是一个 lambda 表达式，它本身是值；`TextExample()` 是一次调用，它本身是执行。写法上只差一层花括号，语义完全不同。

### 4.2 @Composable 加在类型上意味着什么

在函数类型的参数列表前加 `@Composable`，得到的是另一个类型：`@Composable () -> Unit`。这个注解由 Compose 编译器插件赋予语义（教学简化表述，插件如何变换代码属于实现细节），落到使用者身上是两条可观察的规则：

1. **只能在可组合上下文中调用**。`ui()` 写在 `@Composable` 函数体内合法；写进普通 lambda（如 `onClick`）或普通函数里，编译直接报错。反过来，普通 `() -> Unit` 的函数体里也不允许调用 `@Composable` 函数——`Button(onClick = { Text("x") })` 编不过，就是因为 onClick 的参数类型是普通 `() -> Unit`。
2. **调用会参与组合**。调用一个 Composable 函数值和调用一个普通 Composable 函数一样，在组合（composition，Compose 维护的 UI 树）中占一个位置，读取的状态变化能触发它所在范围的重组（第 22 章）。普通函数值调用完就结束，与组合系统没有任何关系。

所以「存着不跑、想跑再跑」不是这条语法自带的魔法，而是「创建 lambda」和「调用 lambda」本来就是两件事；`@Composable` 只是把「什么时候允许调、调了会发生什么」规定清楚了。项目把注册表放在顶层（第 38 章）时只创建不调用，因此应用启动不会组合任何示例 UI——这就是第 36 章 3.2 节「进详情才第一次组合」的机制来源。

### 4.3 传递与调用的完整链路

首页只读取 title；进入详情页后才调用 `info.composeExample()`。因此通用详情页不需要为每种组件写 when。若写 `composeExample = TextExample()`，就变成立即调用，并且返回 Unit，类型不匹配。

对照 3.2 节把链路走一遍：

1. **创建**：`{ CounterContent() }` 生成一个函数值，内容是「调用 CounterContent」这句话本身。
2. **传递**：通过尾随 lambda 或具名参数交给 `DemoCard`；也可以先存进 `val greeting` 再传。传的是「代码」，不是「执行结果」。
3. **保存**：参数 `content` 持有这个值；此时函数体一句都没执行。
4. **调用**：`content()` 在外壳的 Column 里执行，传入的 UI 代码这才进入组合。
5. **复用**：同一个外壳可以接收任何 Composable lambda——两行 `DemoCard` 调用，外壳代码零改动。

调用位置还有个实际后果：`content()` 写在 `DemoCard` 的 Column 里，所以内容永远出现在标题下方。想让内容出现在别处，改的是外壳里的调用点，而不是每个调用方。第 36 章详情页的 `entry.content()` 同理——模板决定「在哪调」，数据决定「调出什么」。

## 5. 实际执行过程

1. 创建 Lambda（注册时，不执行）
2. 保存到 ComponentInfo
3. 首页不执行它
4. 详情查询对应对象
5. 在 Composable 区域调用
6. 具体 Demo 开始组合

补充第 6 步之后的机制（教学简化概述，以[官方心智模型文档](https://developer.android.com/develop/ui/compose/mental-model)为准）：调用点在组合中获得位置记忆，lambda 读到的可观察状态变化时，对应范围重组；lambda 本身换了（比如注册表换成另一个条目），旧内容离开组合、新内容进入。

## 6. Flutter / Dart 3 对照

Flutter 中可保存 `Widget Function()` 或 WidgetBuilder，稍后由通用页面调用。

```dart
final info = ComponentInfo(
  id: 'text',
  title: 'Text',
  description: '...',
  buildExample: () => const TextDemo(),
);
final widget = info.buildExample();
```

对照边界（概念对照，不是等价声明）：

- 相似：两边都是「传一段构建 UI 的代码、稍后调用」，[WidgetBuilder](https://api.flutter.dev/flutter/widgets/WidgetBuilder.html) 就是 Flutter 里 `Widget Function(BuildContext)` 的 typedef，MaterialPageRoute 等大量 API 用它接收 builder 回调。
- 差异一（类型层面）：Kotlin 的函数类型是语言内建类型，可以写进任何字段和参数签名；Dart 闭包同样能到处传，但「builder 约定」靠 typedef 与使用方约定，没有对应「只能在可组合上下文调用」的编译期规则——返回的 Widget 不对才在运行时暴露。
- 差异二（机制层面）：`@Composable () -> Unit` 的调用参与组合与重组跟踪，由编译器插件变换；Flutter builder 每次调用返回一个新 Widget 实例交给框架 diff。两边只做概念对照，不能视为等价机制。

## 7. 常见错误

- 注册时立即调用函数
- 在普通函数调用 Composable Lambda
- 误解 Unit 为函数不会执行
- 全局 Lambda 捕获不应长期持有的大对象

反例一：注册时立即调用（第 36 章已看，这里从调用方视角再看）。

```kotlin
// 错误示例（故意错误，不参与编译）
val broken = ComponentInfo(
    id = "text",
    title = "Text",
    description = "...",
    composeExample = TextExample(), // 需要函数值，传了 Unit：类型不匹配，编译报错
)
```

反例二：在非组合上下文里调用 Composable 函数值。

```kotlin
// 错误示例（故意错误，不参与编译）
Button(onClick = { info.composeExample() }) { // onClick 的 lambda 是普通 () -> Unit
    Text("打开示例")
}
```

onClick 的参数类型是普通 `() -> Unit`，不是可组合上下文；在它里面调用 `@Composable` 函数值会被编译器拒绝（报错大意：@Composable 调用只能出现在可组合上下文中）。想响应点击展示 UI，应通过状态：onClick 改状态，组合代码读状态决定显示什么（第 21、24 章）。

另外注意「误解 Unit 为函数不会执行」：`Unit` 只说明没有有意义的返回值，不代表函数体是空操作。`content()` 一调用，里面的组合代码照常执行——「什么时候执行」由调用点决定，与返回类型无关。

## 8. 本章检查题

1. `{ TextExample() }` 与 `TextExample()` 有什么区别？
2. 为什么类型上要写 @Composable？
3. 详情页为什么不需要 when？
4. （判断行为）`val greeting: @Composable () -> Unit = { Text("你好") }` 执行后，屏幕上立刻出现「你好」吗？它在什么条件下才会出现？
5. （动手修改）给 3.2 节的 `DemoCard` 增加一个 `subtitle: String = ""` 参数，显示在标题下方；先判断两处既有调用是否必须修改，再动手验证。

答案：

1. `{ TextExample() }` 创建 Lambda 并保存、不立即执行；`TextExample()` 是立即调用，返回 Unit。
2. 声明这个函数只能在可组合环境调用、调用会参与组合；由 Compose 编译器插件检查与落实。
3. 详情页统一调用 `info.composeExample()` 即可展示任意组件，不需要为每种组件写 when 分支。
4. 不会。赋值只是创建并保存函数值，没有调用；只有在某个可组合上下文中执行 `greeting()`（比如放进一个正在组合的界面），「你好」才会组合出来。
5. 不必修改。带默认值的参数让既有调用自动取 ""；只有需要副标题的调用方才显式传 `subtitle = "..."`。验证点：新增参数后两处旧调用原样编译通过，界面不变。

## 9. 本章总结

把 Composable Lambda 存为数据，实现了通用模板和具体 UI 的解耦。机制上有三件事要分清：创建 lambda（注册时）、传递 lambda（当普通值传）、调用 lambda（只能在组合中，调用即组合）。`@Composable` 写在类型上，换来的是「调用位置受编译期约束、调用行为参与组合」的保证。下一章看这些条目存在哪：Kotlin object 与全局注册表。
