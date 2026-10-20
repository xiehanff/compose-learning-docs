# 第 40 章：添加一个新 Demo 的完整步骤

学完前面各章的组件之后，最想做的是「加一个自己的 Demo」。这件事本身也是对前四章架构的验收：如果全程不用改首页和详情页一行代码，说明「数据驱动」真的生效了；如果发现要加 when 分支、要改列表，说明哪里耦合了。原工程里这件事被压缩成「新建一个文件 + 注册一行」；没有原工程的读者，本章 3.2 节给出一个能从零搭起来的简化练习工程，把同一条链路完整走一遍。

先划线：「新建文件 → 定义条目 → 注册」这三步流程是原 Demo 的组织方式（依赖它的包结构），但「入口列表、详情页、导航图都不用改」这个结果，来自第 36—39 章的通用机制，任何工程照同一思路都能得到。

## 1. 本章目标

独立添加一个组件示例，并理解数据驱动架构为何无需修改首页和详情模板。

学完后，你应当能够：

- 新建 Demo 文件
- 定义唯一 id 和说明
- 提供 Composable 示例 Lambda
- 注册到 ComponentRegistry
- 验证导航和主题
- 在没有原工程的练习项目里搭出同构的「注册表 + 通用首页 + 通用详情」
- 说出新增一个 Demo 最少要动哪几处、哪些文件一定不用动

## 2. 项目中的位置

- `model/ComponentInfo.kt`
- `components/TextDemo.kt`
- `ui/home/HomeScreen.kt`
- `ui/detail/ComponentDetailScreen.kt`

这些路径属于原 `compose_demo` 工程结构，仓库不含该工程。没有它的读者直接走 3.2 节：一个 Empty Activity 工程（预备篇 A）加本章的代码即可复现全链路。

## 3. 项目源码片段

### 3.1 原工程的做法（项目节选）

以下节选依赖原 Demo 的 `ComponentInfo`、`ComponentRegistry` 与 components 包结构，不可直接复制，仅用于对照真实用法：

```kotlin
val BadgeDemo = ComponentInfo(
    id = "badge",
    title = "Badge",
    description = "Badge 用于显示角标。",
    composeExample = { BadgeExample() },
)

@Composable
private fun BadgeExample() {
    Badge { Text("3") }
}

// ComponentRegistry.all 追加 BadgeDemo
```

在原工程里新建一个 Demo 就是这三段：`components/` 包新建文件、文件里写顶层条目加私有示例函数、最后把条目加进 `ComponentRegistry.all`。

### 3.2 简化练习工程：分步照做（不依赖原工程）

目标链路：**新增一个组件 → 注册到列表 → 列表入口自动出现 → 点击跳转 → 返回**。步骤 2 到 4 的代码合在一个可独立编译的片段里（各段用注释分隔，可直接抄进工程）。

#### 步骤 1：建工程

按预备篇 A 用 Empty Activity 模板建一个 Compose 工程，不需要任何原工程符号。

#### 步骤 2—4：模型、注册表、通用首页与详情页

<!-- compile decls -->
```kotlin
// ---------- 步骤 2：数据模型与注册表（DemoEntry 与第 36 章独立示例同构） ----------

data class DemoEntry(
    val id: String,                      // 唯一标识：路由参数、列表 key、查询
    val title: String,                   // 入口标题与详情标题
    val description: String,             // 详情说明
    val content: @Composable () -> Unit, // 示例 UI，进入详情才调用（第 37 章）
)

// 每个示例一个 Composable 函数；只描述自己的 UI，不依赖导航等外部对象
@Composable
private fun GreetingExample() {
    Text("你好，Compose")
}

@Composable
private fun CounterExample() {
    var count by remember { mutableIntStateOf(0) }
    Button(onClick = { count++ }) { Text("点了 $count 次") }
}

// 每个示例一个条目
private val greetingEntry = DemoEntry(
    id = "greeting",
    title = "问候语",
    description = "显示一行文字。",
    content = { GreetingExample() },
)

private val counterEntry = DemoEntry(
    id = "counter",
    title = "计数器",
    description = "点击按钮累加。",
    content = { CounterExample() },
)

// 注册表：进程内一份（原 Demo 叫 ComponentRegistry，第 38 章详解）
object DemoRegistry {
    val all: List<DemoEntry> = listOf(greetingEntry, counterEntry)

    fun findById(id: String): DemoEntry? = all.firstOrNull { it.id == id }
}

// ---------- 步骤 3：通用首页——入口由数据生成，不认识任何具体示例 ----------

@Composable
fun DemoHomeScreen(onOpen: (String) -> Unit) {
    LazyColumn {
        items(DemoRegistry.all, key = { it.id }) { entry ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable { onOpen(entry.id) } // 只把 id 交出去，跳转由导航层决定
                    .padding(16.dp),
            ) {
                Text(entry.title, style = MaterialTheme.typography.titleMedium)
                Text(entry.description, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

// ---------- 步骤 4：通用详情页——查 id，找不到走 null 分支（第 39 章） ----------

@Composable
fun DemoDetailScreen(componentId: String, onBack: () -> Unit) {
    val info = DemoRegistry.findById(componentId)
    if (info == null) {
        Column(Modifier.padding(16.dp)) {
            Text("未找到组件：$componentId")
            Button(onClick = onBack) { Text("返回") }
        }
        return
    }
    Column(
        Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(info.title, style = MaterialTheme.typography.titleLarge)
        Text(info.description)
        info.content() // 通用模板的核心一行：没有 when，也不认识具体示例（第 37 章）
        Button(onClick = onBack) { Text("返回") }
    }
}
```

#### 步骤 5：接导航（需工程环境，不参与编译校验）

```kotlin
// 需工程环境：依赖 androidx.navigation:navigation-compose（Nav2），不参与本仓库编译校验。
// 写法与第 4 章一致；本仓库基线未锁定该库版本（snippets 编译工程不含此依赖），以所用版本官方文档为准：
// https://developer.android.com/develop/ui/compose/navigation
@Composable
fun DemoApp() {
    val navController = rememberNavController()
    NavHost(navController, startDestination = "home") {
        composable("home") {
            DemoHomeScreen(onOpen = { id -> navController.navigate("detail/$id") })
        }
        composable("detail/{componentId}") { entry ->
            val componentId = entry.arguments?.getString("componentId") ?: ""
            DemoDetailScreen(
                componentId = componentId,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
```

在 `MainActivity` 的 `setContent { DemoApp() }`（第 1 章）后，这条链路就通了：首页 `onOpen` 把 id 交给 `navigate("detail/$id")`，详情页用 id 查注册表，返回走 `popBackStack`。

#### 步骤 6：运行走查（验收清单）

运行行为为预期观察，待真机（或模拟器）验证，逐项打勾：

1. 列表入口出现：首页显示两条入口。
2. 点击跳转：点「计数器」进入详情，显示标题、说明和按钮。
3. 返回：详情「返回」按钮或系统返回键回到首页，列表状态正常。
4. 兜底：临时把 navigate 的 id 换成不存在的值（如 `navigate("detail/none")`），详情显示「未找到组件」而不是崩溃（第 39 章），验证完改回。
5. 配置变化：旋转屏幕、切换深色主题，确认无崩溃、显示正常（主题与状态恢复见第 3、23 章）。

#### 步骤 7：真正「新增一个 Demo」——三步，零模板改动

以 Badge 角标为例（本片段独立编译，DemoEntry 定义与步骤 2 相同）：

<!-- compile decls -->
```kotlin
// 本片段独立编译，模型定义与步骤 2 相同
data class DemoEntry(
    val id: String,
    val title: String,
    val description: String,
    val content: @Composable () -> Unit,
)

// 新增三步之一：写示例函数（只描述自己的 UI）
@Composable
private fun BadgeExample() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("消息")
        Spacer(Modifier.width(4.dp))
        Badge { Text("3") } // material3 的 Badge：数字角标
    }
}

// 新增三步之二：定义条目（id 保证唯一即可）
private val badgeEntry = DemoEntry(
    id = "badge",
    title = "角标",
    description = "Badge 用于显示数字角标。",
    content = { BadgeExample() },
)
```

第三步是注册。示意（接步骤 2 的 `DemoRegistry`，引用上文声明，不参与编译校验）：

```kotlin
// 只在 listOf 里加一行；首页、详情页、导航图都不用改
val all: List<DemoEntry> = listOf(
    greetingEntry,
    counterEntry,
    badgeEntry, // ← 新增的一行
)
```

注册完成后回到步骤 6 的清单再走一遍：首页自动出现第三个入口「角标」，点击进入、返回、旋转，全部不需要碰模板代码。

## 4. 逐步解释

在 components 包创建文件，定义顶层 ComponentInfo 和私有示例函数。id 必须稳定、唯一并适合路由。然后导入对象并追加到 Registry.all。

首页遍历 all 自动生成入口；点击把 id 传给导航；详情页 findById 后调用 composeExample。因此无需给 HomeScreen 新写按钮，也无需在详情页增加 when。完成后应测试入口、跳转、返回、暗色主题、横竖屏和小屏布局。

把「为什么每一步都这么短」对回前四章：

- 步骤 2 之所以只要「函数 + 条目」，是因为函数值可以存进数据字段（第 36、37 章）：`content = { BadgeExample() }` 打包的是代码，进详情才执行。
- 注册之所以只要一行，是因为首页用 `items(all, key = { it.id })` 由数据生成入口（第 16、36 章），注册表是全局单例（第 38 章）。
- 详情页之所以不用改，是因为它只做「查 id + null 分支 + 调 content()」（第 37、39 章），对新旧条目一视同仁。
- 唯一真正的新知识是导航接线（步骤 5，第 4、5 章），而它同样写在通用层，不随 Demo 增减而变化。

哪些步骤依赖原工程结构：步骤 3.1 的「components 包建文件、model 包放模型」是原工程的目录约定；简化工程里全部代码放进一个文件也能跑。流程不变的是「示例函数 → 条目 → 注册」这个顺序，以及「id 唯一」这条硬约束——id 重复时，列表 key 冲突会在运行时报错（第 17 章），`findById` 也只会命中靠前的一条。

## 5. 实际执行过程

1. 编写元数据和示例（新增的文件）
2. 注册表包含新条目（listOf 加一行；注册表在首次访问时初始化，第 38 章）
3. 首页 items 自动显示新入口
4. 点击把 id 传给 navigate
5. 详情 findById 查到新条目，null 分支不触发
6. content() 执行新示例
7. 返回弹出详情目的地，回到首页
8. 按步骤 6 清单走查整条链路

## 6. Flutter / Dart 3 对照

Flutter 也可用 List<ComponentInfo> 驱动目录和通用详情页。

```dart
final badgeDemo = ComponentInfo(
  id: 'badge',
  title: 'Badge',
  description: 'Badge 用于显示角标。',
  buildExample: () => const BadgeExample(),
);
final all = [...existing, badgeDemo];
```

同一份清单在 Flutter 里同样成立：新增 = 「Widget 函数 + 条目 + 列表加一项」，ListView.builder 按数据生成入口，通用详情页调 `buildExample()`。差别还是第 36、37 章那两条：Dart 条目是手写相等语义的类（或 record），builder 是闭包约定而非带编译期调用规则的函数类型。流程对照可行，机制不等价。

## 7. 常见错误

- id 与已有条目重复
- 创建文件但忘记注册
- composeExample 立即调用
- 示例强依赖 NavController，无法独立复用

逐条对照后果（运行行为为预期观察，待真机验证）：

- id 重复：`key = { it.id }` 要求 key 唯一，运行时列表组合直接抛异常（第 17 章）；即使去掉 key，`findById` 也只命中靠前条目，另一个永远进不去。注册前搜一遍 `id = "badge"` 这类字面量即可避免。
- 忘记注册：文件编译通过、什么都正常，唯独首页没有新入口——数据驱动的「缺点」是漏改数据就没有入口，注册那一行不能省。
- 立即调用（第 36、37 章的反例）：`content = BadgeExample()` 传的是 Unit，编译报错；正确写法永远要包一层 `{}`。
- 示例依赖 NavController：`BadgeExample(onBack = {...})` 这种签名让示例离开这个 App 就无法复用，也违背「详情模板统一持有返回按钮」的分工。示例函数应该只收自己的数据参数；导航回调留给模板层（步骤 4 的 `onBack` 在详情页上，不在示例里）。

## 8. 本章检查题

1. 新增 Demo 最少改哪些位置？
2. 为什么首页不需要手写新按钮？
3. 如何保证 id 唯一？
4. （预测行为）把 `badgeEntry` 的 id 误写成 `"counter"`（与已有条目相同），注册后运行会看到什么？
5. （动手修改）按步骤 7 的三步再加一个 `id = "switch"` 的 SwitchDemo（示例随意，比如一个 `Text("开关示例")`），然后回答：这次改动碰了 `DemoHomeScreen`、`DemoDetailScreen`、`DemoApp` 中的哪一个？再用步骤 6 清单走查新入口。

答案：

1. components 包新建文件（定义 ComponentInfo 与示例函数）+ 在 ComponentRegistry.all 中追加一条。
2. 首页遍历 all 自动生成入口，无需手写新按钮；详情页也通过 id 查找后统一渲染。
3. 使用稳定且业务上唯一的字符串，与现有 id 命名区分，避免与已有路由参数冲突；注册前在注册表处搜索确认。
4. 两层后果：列表的 `key = { it.id }` 出现重复 key，组合时抛异常（第 17 章），首页无法正常显示；即使绕过列表问题，`findById("counter")` 按 firstOrNull 语义命中靠前那条，后注册的 badge 永远无法通过 id 访问。这正是「id 唯一」被列为硬约束的原因。
5. 三个都不碰。改动的只有新文件（示例函数 + 条目）和 `DemoRegistry.all` 的一行；首页入口、详情渲染、导航接线全部自动适配——这是对本章目标「数据驱动架构无需修改模板」的直接验收。

## 9. 本章总结

新增流程是「定义 ComponentInfo + 注册到 all」。其余首页、路由和详情由通用结构自动完成。本章把这条流程展开成可照着走的七个步骤，并给没有原工程的读者一套从 Empty Activity 起步的等价练习工程：模型（第 36 章）、函数字段（第 37 章）、单例注册表（第 38 章）、可空查询（第 39 章）、导航接线（第 4、5 章）各就各位之后，「加一个 Demo」收敛为一次纯数据的新增。
