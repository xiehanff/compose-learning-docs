# 第 23 章：rememberSaveable 与配置变化后的状态恢复

## 1. 本章目标

先看一个具体的页面问题：表单页填了十几个字段，屏幕一旋转全部清空。用户的第一反应是「bug」，开发者应该问的是：这份数据放在哪里、它能活过哪些「界面被重建」的时刻。本章把状态按存活范围分层：只跨重组（remember）、跨配置变化与系统恢复（rememberSaveable）、跨启动（DataStore / Room，第 72 章）。

理解项目当前 remember 的保存边界，以及表单、页签等状态何时需要 rememberSaveable。

学完后，你应当能够：

- 区分重组、配置变化和进程重建
- 说出四种场景下 remember / rememberSaveable 各自的存留结果
- 知道 remember 只跟随当前组合实例
- 知道 rememberSaveable 可写入保存状态
- 知道哪些类型可以直接保存（Bundle 能存的），自定义类型为什么需要 Saver
- 理解复杂对象需要 Saver 的适用场景与数据量边界

## 2. 项目中的位置

- `components/AlertDialogDemo.kt`
- `components/DatePickerDemo.kt`
- `components/StateDemo.kt`

StateDemo.kt 的第三小节用 `rememberSaveable` 与 `remember` 做对照（旋转屏幕后前者保留、后者重置），是本章的实践入口。

## 3. 项目源码片段

### 3.1 项目节选

<!-- compile -->
```kotlin
// components/StateDemo.kt:135：只要跨重组保留，用 remember
var plainCount by remember { mutableIntStateOf(0) }

// components/StateDemo.kt:136：需要跨配置变化恢复时改用 rememberSaveable
var savedCount by rememberSaveable { mutableStateOf(0) }

// components/AlertDialogDemo.kt:35：弹窗是否显示这类临时状态，项目里只用 remember 保存
var showDialog by remember { mutableStateOf(false) }
```

### 3.2 对照实验：两个计数器并排放

<!-- compile decls -->
```kotlin
@Composable
fun SaveableCompareDemo() {
    var plainCount by remember { mutableIntStateOf(0) }
    var savedCount by rememberSaveable { mutableStateOf(0) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { plainCount++ }) { Text("remember 计数：$plainCount") }
        Button(onClick = { savedCount++ }) { Text("rememberSaveable 计数：$savedCount") }
        Text("把两个计数点到不同值，再做旋转 / 切后台实验")
    }
}
```

初始界面：两行带计数的按钮，都是 0。操作步骤与预期观察（待真机验证）：

1. 把两个计数分别点到 1 和 5，旋转屏幕：预期第一行归零、第二行仍是 5。
2. 在开发者选项中开启「不保留活动」（Don't keep activities），把应用切到后台再回来：预期同样一行归零、一行保留；验证完关闭该选项。
3. 按返回键退出应用再重新打开：预期两行都归零——用户主动结束的界面，系统不再走恢复流程。

### 3.3 自定义 Saver：保存自己的数据类

<!-- compile decls -->
```kotlin
data class Filter(val query: String, val onlyFavorite: Boolean)

// Saver 说明「怎么把对象拆成 Bundle 能存的小值，以及怎么用这些值拼回对象」
val FilterSaver = listSaver<Filter, Any>(
    save = { listOf(it.query, it.onlyFavorite) },
    restore = { Filter(it[0] as String, it[1] as Boolean) },
)

@Composable
fun FilterBar() {
    var filter by rememberSaveable(stateSaver = FilterSaver) {
        mutableStateOf(Filter(query = "", onlyFavorite = false))
    }
    Column {
        OutlinedTextField(
            value = filter.query,
            onValueChange = { filter = filter.copy(query = it) },
            label = { Text("搜索关键词") },
            singleLine = true,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = filter.onlyFavorite,
                onCheckedChange = { filter = filter.copy(onlyFavorite = it) },
            )
            Text("只看收藏")
        }
    }
}
```

初始界面：空关键词、未勾选。操作：输入「北京」并勾选「只看收藏」，旋转屏幕。预期观察（待真机验证）：两项都保留。每改一次筛选条件，都是用 `copy` 生成新的 Filter 替换整个状态值，而不是原地改字段。

## 4. 逐步解释

普通 remember 能跨重组保留，但组合被销毁后就没有保证。屏幕旋转、Activity 重建或导航目的地重建时，值可能回到初始状态。`rememberSaveable` 会借助 Compose 与 Android 的保存状态机制恢复基础可保存值；不需要自己直接操作底层注册表。

### 4.1 四种场景下的状态存留对照

| 场景 | 怎么触发 | remember | rememberSaveable |
| --- | --- | --- | --- |
| 普通重组 | 状态变化，同一组合实例内重新执行函数 | 保留 | 保留 |
| 配置变化（旋转） | 旋转屏幕、系统深色模式切换、更改系统语言等 | 丢失 | 恢复 |
| Activity 重建 | 开发者选项「不保留活动」、后台内存压力回收后返回 | 丢失 | 恢复（系统按保存的实例状态重建时） |
| 进程死亡后重建 | 系统杀掉进程，用户从最近任务返回且任务仍被系统保留 | 丢失 | 恢复（同上，前提是系统保留并恢复该任务） |
| 用户主动退出 | 按返回键结束、最近任务里划掉后再打开 | 丢失 | 丢失 |

三点边界说明：

- 配置变化、Activity 重建、进程死亡走的是同一条「保存实例状态（SavedInstanceState，介质是 Bundle）」通道，差别在被销毁的是组合、Activity 还是整个进程。
- 「恢复」都有前提：系统执行了「保存→恢复」流程才有效；用户主动退出、任务被划掉后没有这个流程，什么都不会恢复。
- 本表是机制口径；具体实验步骤与结果以待真机验证为准（见 3.2 的三条实验）。想模拟真实进程死亡可用 `adb shell am kill` 等命令，属于进阶操作，不在本仓库验证范围内。

### 4.2 哪些类型可以直接保存

rememberSaveable 的保存介质最终是 Android 的 Bundle。原生可直接保存的是 Bundle 支持的类型：基本类型（Int、Boolean、Double……）、String、Parcelable、Serializable，以及它们组成的数组 / List / Map。布尔、字符串、数字这类小状态通常直接可用。

自定义 data class 默认既不是 Parcelable 也不保证可序列化，直接交给默认的 autoSaver 保存会在保存环节出问题，所以需要提供 `Saver`：它说明「把对象拆成哪些受支持的小值来保存，以及如何用这些值重新构造对象」。例如，筛选条件可只保存查询词与是否仅看收藏（3.3 的 Filter）；更多拆分与恢复写法（mapSaver 等）见第 72 章的 `listSaver` 示例。只保存恢复 UI 所需的最小字段。

### 4.3 数据量要小：它不是大仓库，更不是持久化

Bundle 走系统进程间通道，有事务大小上限；官方文档明确提示往保存状态里塞过大的数据有 TransactionTooLarge 风险。所以 rememberSaveable 适合恢复小型 UI 状态（筛选条件、页签下标、草稿文本、滚动位置），不适合大型对象，也不是永久持久化：它不保证用户主动结束应用后仍能恢复，长期业务数据（如订单）应写入持久层（DataStore / Room，第 72 章）；ViewModel 的轻量恢复状态会在第 68 章介绍。第 26A 章的 `rememberTextFieldState` 内部用的也是这套保存恢复机制，输入框文本默认就能扛过旋转与进程重建。

## 5. 实际执行过程

1. 首次组合创建 saveable 状态
2. 状态变化被保存状态注册表记录
3. 配置变化 / Activity 销毁 / 进程死亡前，系统把可保存值写入 Bundle
4. 新的 Activity 与新组合创建
5. rememberSaveable 在同一组合位置用 Saver 拆回的值重新构造状态（`restore` 返回 null 或没有保存值时，回退到 init 的初始值）

## 6. Flutter / Dart 3 对照

Flutter 可通过 RestorationMixin 和 RestorableProperty 实现系统状态恢复。普通 State 字段与 Compose remember 类似，只覆盖当前 Widget 实例生命周期。

```dart
class DemoState extends State<Demo> with RestorationMixin {
  final RestorableInt tab = RestorableInt(0);
  @override String? get restorationId => 'demo';
  @override void restoreState(RestorationBucket? old, bool initial) {
    registerForRestoration(tab, 'tab');
  }
}
```

对照（概念对照，不是机制等价）：Flutter 的状态恢复需要显式给 restorationId 并逐个 registerForRestoration，默认不开启；Compose 的 rememberSaveable 把注册这件事收进了函数本身，但两边「只覆盖系统能恢复的场景、不覆盖用户主动退出」的边界一致。

## 7. 常见错误

反例详解（错误现象 → 原因 → 修正）：

- **自定义类不写 Saver**。现象：`rememberSaveable { mutableStateOf(MyData(...)) }` 平时正常，一旦旋转 / 进程重建就崩溃或状态丢失。原因：默认 autoSaver 会把对象原样塞进 Bundle，自定义类不在 Bundle 支持范围内。修正：提供 listSaver / mapSaver（3.3 的 FilterSaver），或把类实现为 Parcelable。
- 把 rememberSaveable 当作长期数据库：它只覆盖系统能恢复的场景，跨启动保存要走 DataStore / Room（第 72 章）
- 把大 Bitmap 或连接对象直接保存：数据量大有 TransactionTooLarge 风险，且连接这类非数据对象本来就不该进保存状态
- 所有临时弹层状态都恢复，造成返回页面自动弹出旧弹窗：弹窗这类「瞬间状态」通常只用 remember，让重建后回到干净界面
- 没有为自定义类型提供 Saver（同第一条，新人最常见的崩溃来源）

## 8. 本章检查题

1. 普通 remember 在 Activity 重建后一定保留吗？
2. 哪些类型适合直接 saveable？
3. 订单等长期业务数据应只放 rememberSaveable 吗？
4. 预测：SaveableCompareDemo 两个计数都点到 9，依次经历「旋转」「不保留活动下切后台再回来」「返回键退出再进入」，第二行（savedCount）分别显示多少？第一行呢？
5. 动手：给 Filter 增加一个 `sortAsc: Boolean` 字段：同步更新 data class、FilterSaver 的 save / restore 与 UI（提示：save 里追加一项，restore 里多取一个下标）。

答案：

1. 不一定。普通 remember 能跨重组保留，但组合被销毁（旋转、Activity 重建、导航重建）后没有保证。
2. Bundle 支持的类型：Boolean、String、Int 等基本类型，以及 Parcelable / Serializable 和它们的数组 / List / Map；自定义类需要 Saver，并只保存恢复 UI 所需的最小字段。
3. 不应。rememberSaveable 不是数据库，订单等长期业务数据应放在持久层；ViewModel 的轻量恢复状态另有机制。
4. 第二行依次是 9、9、0——前两次系统执行了保存恢复，用户主动退出后没有。第一行在第一次旋转时就归零，之后一直是 0。
5. 要点：`save = { listOf(it.query, it.onlyFavorite, it.sortAsc) }`，`restore = { Filter(it[0] as String, it[1] as Boolean, it[2] as Boolean) }`；UI 加一个 Checkbox 读写 `filter.copy(sortAsc = it)`。漏改 Saver 时新字段在旋转后会回到默认值。

## 9. 本章总结

rememberSaveable 用于恢复轻量 UI 状态，不是永久持久化，也不是大对象的仓库。四种场景的记忆口诀：**重组两者都在；旋转、Activity 重建、进程死亡看 rememberSaveable（前提是系统走了保存恢复）；用户主动退出谁也救不了**。项目里 `components/StateDemo.kt` 已用 `rememberSaveable` 与 `remember` 做对照，可直接旋转屏幕观察两者差异。

官方参考：

- [Save UI state in Compose（保存与恢复，含 Bundle 限制提示）](https://developer.android.com/develop/ui/compose/state-saving)
- [State and Jetpack Compose（rememberSaveable 与 Saver 入口）](https://developer.android.com/develop/ui/compose/state)
