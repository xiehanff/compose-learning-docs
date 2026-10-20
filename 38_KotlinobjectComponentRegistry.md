# 第 38 章：Kotlin object ComponentRegistry

第 36 章定义了条目模型，第 37 章让条目能携带可执行的 UI——接下来一个问题很现实：这些条目放哪？首页要读列表，详情页要按 id 查询，两处需要同一份数据。Kotlin 给了一个语言级的答案：`object`，声明即单例。本章讲它的写法、初始化时机和代价；同时先划清一条线：**用全局单例对象当组件目录，是原 Demo 的组织方式，不是 Compose 官方强制的架构**。官方架构指南推荐用依赖注入管理依赖（见 4.4 节与第 69 章），单例注册表只是小工程里成本最低的选择。

## 1. 本章目标

理解 Kotlin object 的唯一实例语义，以及它为何适合只读组件目录。

学完后，你应当能够：

- 知道 object 不需要 new
- 理解全局访问和初始化
- 区分只读注册定义与动态业务状态
- 认识单例的测试和生命周期风险
- 说清 object 声明的初始化时机
- 判断哪些数据适合放进全局单例、哪些不适合

## 2. 项目中的位置

- `model/ComponentInfo.kt`

没有 compose_demo 工程的读者：本章 3.2 节的示例不依赖项目私有符号，可直接复制到任意含 Material 3 的 Compose 工程运行。

## 3. 项目源码片段

### 3.1 项目节选

以下节选依赖原 Demo 的 `ComponentInfo` 及 ColumnRowDemo 等条目定义，不可直接复制，仅用于对照真实用法：

```kotlin
object ComponentRegistry {
    val all: List<ComponentInfo> = listOf(
        ColumnRowDemo,
        BoxDemo,
        TextDemo,
    )

    fun findById(id: String): ComponentInfo? =
        all.firstOrNull { it.id == id }
}
```

### 3.2 独立可运行示例（不依赖项目代码）

用第 36 章同款的最小模型 `DemoEntry` 建一个自己的注册表，并演示两个消费方（列表计数 + 按 id 查询）如何全局访问它。

<!-- compile decls -->
```kotlin
// 最小数据模型：与第 36 章独立示例同构
data class DemoEntry(
    val id: String,
    val title: String,
    val content: @Composable () -> Unit,
)

@Composable
private fun CounterExample() {
    var count by remember { mutableIntStateOf(0) }
    Button(onClick = { count++ }) { Text("点了 $count 次") }
}

// object 声明：类型名 + 进程内唯一实例，一次给出
object DemoRegistry {
    // 只读目录：定义写死在代码里，进程内共享一份，不承载可变业务状态
    val all: List<DemoEntry> = listOf(
        DemoEntry("greeting", "问候语") { Text("你好，Compose") },
        DemoEntry("counter", "计数器") { CounterExample() },
    )

    // 查询可能失败，返回可空类型（第 39 章详解）
    fun findById(id: String): DemoEntry? = all.firstOrNull { it.id == id }
}

@Composable
fun RegistryAccessDemo() {
    // 全局访问：类型名直接点出来，不需要（也不能）创建实例
    val found = DemoRegistry.findById("counter")
    Column(
        Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("目录共 ${DemoRegistry.all.size} 个条目")
        Text("查找 counter：${found?.title ?: "未找到"}")
        if (found != null) {
            // null 检查后智能转换，found 可直接用（第 39 章）
            found.content() // 调用条目存的 UI 代码（第 37 章）
        }
    }
}
```

初始界面：两行文字（「目录共 2 个条目」「查找 counter：计数器」）加一个「点了 0 次」按钮。

操作步骤与预期观察（运行行为为预期观察，待真机验证）：

1. 点按钮 3 次。预期：显示「点了 3 次」；上面两行文字不变——`all` 是只读目录，计数状态属于 CounterExample 内部（第 35 章）。
2. 把 `findById("counter")` 换成 `findById("nope")`。预期：第二行变「未找到」，按钮消失——null 分支生效。

## 4. 逐步解释

### 4.1 object 语法（首次出现）

`object` 同时声明类型和进程内唯一实例，调用直接写 `ComponentRegistry.all`。拆开说：普通 `class` 是「图纸」，要实例化才有对象；`object DemoRegistry { ... }` 是「图纸加唯一成品」，声明本身就带来了一个实例——没有 `DemoRegistry()` 这种构造调用（也编不过），任何地方写 `DemoRegistry.xxx` 访问的都是同一个实例。这正好符合「注册表」的需要：首页、详情页、路由各自访问，拿到的必须是同一份目录。语法详见预备篇 D 第 8 节与 [Kotlin 官方文档 Object declarations](https://kotlinlang.org/docs/object-declarations.html)。

顺带区分一个易混概念：本章的 `object`（对象声明）自己就是单例；后面工程里还会遇到 `companion object`（伴生对象），它挂在某个类上、类似静态成员的载体，两者不是一回事。

### 4.2 初始化时机：首次访问时

Kotlin 官方文档对 object 声明的说明是：在**首次被访问时**初始化（JVM 上经由类初始化机制完成，该机制保证线程安全；底层细节属实现范畴，此处为教学简化表述，以官方文档为准）。落到本 Demo：

- 应用启动后第一次执行到 `ComponentRegistry.all`（通常是首页第一次组合）时，`listOf(...)` 才求值，条目对象和其中的 lambda 才被创建。
- 此后整个进程内所有访问共享同一份 `all`，不会重建。
- 结合第 37 章：初始化只「创建」lambda，不「调用」它们，所以注册表初始化不会组合任何示例 UI。

### 4.3 适合放什么：只读的稳定定义

当前 all 是代码中固定的 Demo 定义表，所有页面共享，适合作为单例。判断标准有三条：数据在编译期就确定（不来自网络或用户输入）、运行期只读（页面只查不改）、作用域确实是整个进程（首页、详情、路由都要用）。注册表三条全中。`val all` 只保证引用不可重新赋值，不自动保证内部对象深不可变——不过 `DemoEntry` 全部字段是 val 且类型不可变，这条在本示例里成立；换成含可变字段的模型就不成立了。

### 4.4 代价与边界：这是原 Demo 的方式，不是官方强制架构

object 不应成为任意全局状态仓库。不要在其中持有 Activity、View、临时页面状态或大量可变业务数据。需要动态替换、测试注入和远程更新时，更适合接口加依赖注入——也就是由外部把依赖对象传进来，而不是在类内部自己创建；详见第 69 章。

把这三类代价说透：

1. **生命周期与进程一致**。单例里的引用活到进程结束。持有 Activity 或大对象时，它们无法被回收（内存泄漏的常见来源）；存业务数据（当前用户、购物车）时，进程不死数据不清，登录/登出这类边界很容易漏清理。
2. **测试隔离难**。单元测试里全局单例在用例之间共享：一个用例改了它，下一个用例读到脏状态；也无法把注册表换成假实现（fake）来测「目录为空」「查询失败」这类分支。依赖注入通过传参解决这两点。
3. **依赖关系被隐藏**。任何代码都能写 `ComponentRegistry.all`，调用方与注册表的依赖不出现在构造参数里，从函数签名看不出一个页面用了哪些全局数据，重构时影响面难评估。

官方立场：[Android 应用架构指南](https://developer.android.com/topic/architecture)推荐通过[依赖注入](https://developer.android.com/training/dependency-injection)管理依赖，大型工程用 Hilt（第 69 章）。原 Demo 用 object 是教学工程的合理简化——目录小、只读、无测试替换需求；照搬到业务工程前，先对照上面三条代价。

## 5. 实际执行过程

1. 首次访问 object（首页组合读到 `all`）
2. 初始化唯一实例和 all（只创建条目与 lambda，不调用 UI）
3. 首页读取列表
4. 详情调用 findById
5. 整个进程共享同一注册定义
6. 进程结束时实例随进程消失，期间没有任何主动清理时机

## 6. Flutter / Dart 3 对照

Dart 3 可用顶层 final 列表或带 static 成员的不可实例化类。

```dart
final class ComponentRegistry {
  ComponentRegistry._();
  static final all = <ComponentInfo>[textDemo, imageDemo];
}
```

对照边界（概念对照，不是等价声明）：

- 相似：私有构造 + static final 同样达成「类型 + 唯一数据」，顶层 `final` 变量也具有惰性初始化与全库可见的效果。
- 差异：Kotlin 的 `object` 是语言内建声明，单例语义由语言保证；Dart 这套是「约定写法拼出来的」单例，效果接近但机制不同（static 字段的惰性初始化规则由 Dart 规范定义，与 Kotlin object 的 JVM 类初始化是两回事）。
- 两边共有的告诫相同：全局可变状态难测试、难回收，Flutter 社区同样建议把可变状态交给状态管理方案而不是塞进全局静态。

## 7. 常见错误

- 在 object 中持有 Activity 导致泄漏
- 把用户会话和页面状态全部塞入单例
- 误认为 val 等于深不可变
- 测试无法替换全局注册表

反例：把 object 当全局状态仓库。

```kotlin
// 错误示例（故意错误，不参与编译）
object BadSessionStore {
    var currentActivity: MainActivity? = null // 持有 Activity：进程不亡它不散，泄漏
    var loggedInUser: User? = null             // 全局可变业务状态：测试间互相污染
    var lastDetailId: String? = null           // 页面临时状态：放错了层（第 35 章）
}
```

三个字段的病灶不同但根源相同：它们都是「运行期会变、属于某个生命周期」的数据，进了进程级单例就没法随页面销毁、没法在测试里替换。对照第 35 章的定位表：临时页面状态归组件/页面，业务状态归 ViewModel，跨屏共享才考虑上移——单例只收留「进程级、只读、稳定」的定义数据。

## 8. 本章检查题

1. object 与 class 的使用方式有何不同？
2. 为什么注册表适合单例？
3. 哪些对象不应放入全局 object？
4. （判断行为）把 `DemoRegistry.all` 从 val 改成 var，并允许某页面整体重新赋值来「刷新目录」。会出什么问题？
5. （动手修改）给 3.2 节的 `DemoRegistry` 增加 `fun ids(): List<String> = all.map { it.id }`，并在 `RegistryAccessDemo` 里用一行 Text 显示所有 id；运行确认目录变化时（往 listOf 再注册一条）这行文字自动反映新目录。

答案：

1. object 同时声明类型和进程内唯一实例，直接 `ComponentRegistry.all` 访问；class 需要先实例化。object 没有 `DemoRegistry()` 这样的构造调用。
2. 注册表是全局共享、相对固定的 Demo 定义表，所有页面复用同一份数据，适合单例；按 4.3 节的三条标准（编译期确定、运行期只读、进程级作用域）检验也全部满足。
3. Activity、View、临时页面状态和大量可变业务数据；需要动态替换、测试注入、远程更新时应改用接口加依赖注入（第 69 章详解）。
4. 三层问题：普通 var 不是可观察状态，重新赋值不会触发任何重组，界面读到旧目录；赋值时机不受控，多个页面可能读到不同版本；测试与并发场景进一步放大不确定性。目录应该保持只读，变化需求交给可观察状态或注入的数据源。
5. 新增成员函数后 `Text("全部 id：${DemoRegistry.ids()}")` 显示「全部 id：[greeting, counter]」；再注册一条后变三项——说明消费方读的始终是注册表当前内容，模板无需感知条目数量。

## 9. 本章总结

ComponentRegistry 是稳定代码目录，而不是通用全局状态中心。`object` 一句声明解决「全局唯一」，代价是生命周期与进程一致、测试隔离难、依赖关系隐藏——所以它配得上的是只读定义数据，配不上业务状态；需要替换与注入时，官方路线是依赖注入（第 69 章）。下一章看注册表最重要的一个查询方法：`findById` 返回可空类型，找不到时怎么办。
