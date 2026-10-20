# 第 17 章：key 与列表项身份

## 1. 本章目标

先看一个具体页面问题：待办清单里你勾选了「买牛奶」，然后把它移到列表顶部，结果勾选标记留在了原来的位置上——现在没买的那件事显示「已完成」，已经勾过的反而变回未勾选。数据没丢，丢的是「哪条勾选属于哪条待办」的对应关系。本章的 key 就是解决这个问题的：让 Compose 在数据插入、删除、重排后，仍能认出「这还是不是原来那个条目」。

理解 `key = { it.id }` 不是排序条件，而是条目的身份标识。学完后，你应当能够：

- 知道 key 必须稳定且在同一列表中唯一
- 理解没有 key 时默认身份由位置承担
- 预测插入、删除、重排后，条目内部 remember 状态跟谁走
- 避免使用随机数、下标或可变字段作为 key
- 知道 key 能做什么、不能做什么（不把 key 神化）

## 2. 项目中的位置

- `ui/home/HomeScreen.kt`

## 3. 项目源码片段

### 3.1 项目节选

以下节选依赖 `ComponentRegistry`、`ComponentInfo`、`EntryCard` 与外层 `onNavigate` 回调等项目私有符号，不可直接复制，仅用于对照真实用法：

```kotlin
items(
    items = ComponentRegistry.all,
    key = { info -> info.id },
) { info ->
    EntryCard(
        info = info,
        onClick = { onNavigate(info.id) },
    )
}
```

### 3.2 独立可运行示例：同一个待办列表的两版写法

示例用带 `id` 的待办列表展示「插入、删除、重排」，并在条目内部放一个 `remember` 的勾选状态，用来观察状态归属。可直接复制运行。

<!-- compile decls -->
```kotlin
// 待办模型：id 是业务身份（稳定且不重复）；title 只是展示文本，可能重复、可能修改
data class Todo(val id: Long, val title: String)

// 单行待办：内部的 remember 勾选状态就是本章的「观察对象」
@Composable
private fun TodoRow(todo: Todo, onMoveToTop: () -> Unit) {
    var done by remember { mutableStateOf(false) } // 状态保存在条目的组合位置上
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = done, onCheckedChange = { done = it })
        Text(
            text = if (done) "${todo.title}（已完成）" else todo.title,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onMoveToTop) { Text("移到顶部") }
    }
}

// 版本一：无 key——条目身份默认由「列表中的位置」承担
@Composable
fun TodoListWithoutKey(todos: List<Todo>, onMoveToTop: (Todo) -> Unit) {
    LazyColumn {
        items(todos) { todo ->
            TodoRow(todo = todo, onMoveToTop = { onMoveToTop(todo) })
        }
    }
}

// 版本二：key = { it.id }——条目身份由业务 id 承担
@Composable
fun TodoListWithKey(todos: List<Todo>, onMoveToTop: (Todo) -> Unit) {
    LazyColumn {
        items(todos, key = { it.id }) { todo ->
            TodoRow(todo = todo, onMoveToTop = { onMoveToTop(todo) })
        }
    }
}

// 演示页面：持有列表状态，提供头部插入、删除末尾、移到顶部三种操作
@Composable
fun TodoKeyDemoScreen() {
    var todos by remember {
        mutableStateOf(
            listOf(
                Todo(1L, "买牛奶"),
                Todo(2L, "复习第 16 章"),
                Todo(3L, "给列表加 key"),
            )
        )
    }
    var nextId by remember { mutableStateOf(4L) }

    // 移到顶部：生成新列表（listOf(a) + 其余部分 = 拼接），再整体替换状态
    val moveTop: (Todo) -> Unit = { todo ->
        todos = listOf(todo) + todos.filter { it.id != todo.id }
    }

    Column(Modifier.fillMaxSize()) {
        Row {
            Button(
                onClick = { todos = listOf(Todo(nextId, "新任务 $nextId")) + todos }, // 头部插入
                modifier = Modifier.padding(4.dp),
            ) { Text("头部插入") }
            Button(
                onClick = { todos = todos.dropLast(1) }, // 删除末尾
                modifier = Modifier.padding(4.dp),
            ) { Text("删除末尾") }
        }
        Text(
            "实验：勾选某一行，再点这一行的「移到顶部」，看勾选状态跟谁走",
            modifier = Modifier.padding(8.dp),
        )
        // 交互完全相同；做对照实验时，把这行换成 TodoListWithoutKey(todos, onMoveToTop = moveTop)
        TodoListWithKey(todos = todos, onMoveToTop = moveTop)
    }
}
```

上面的示例里两处写法说明一下：`onMoveToTop: (Todo) -> Unit` 是函数类型参数（第 5 章讲过 lambda），传入「收到哪个待办就把它挪到顶部」的规则；`listOf(todo) + 其余` 用 `+` 拼接两个列表得到新列表——列表本身不可变，所谓「修改」都是生成新列表再替换状态（第 21 章展开状态写法）。

初始界面：两行按钮，提示文字，下面三行待办，每行是「勾选框 + 标题 + 移到顶部按钮」。

## 4. 逐步解释

### 4.1 key 决定「状态归属」的身份映射

`items` 的内容 lambda 每次执行，都会在对应组合位置留下 remember 槽位（第 21 章）。问题在于：数据变化后，新一轮的条目要和上一轮的槽位对应起来——按什么对应？

- 无 key：按位置对应。第 2 行还是第 2 行，哪怕它现在装的是另一个对象；槽位里的勾选值不动，只是标题文字换了。
- `key = { it.id }`：按业务身份对应。id 为 2 的条目无论挪到哪里，都连着自己的槽位一起移动。

这就是「key 决定状态归属」的含义：勾选、动画进度、输入草稿这类条目内部 remember 状态，跟随 key 走还是跟随位置走。

### 4.2 重排实验：哪一项保留勾选值

实验步骤：初始三行 [买牛奶(id=1), 复习第 16 章(id=2), 给列表加 key(id=3)]；勾选第二行「复习第 16 章」；点它自己的「移到顶部」。新顺序为 [复习第 16 章, 买牛奶, 给列表加 key]。

版本二（key = { it.id }）的预期观察：

| 位置 | 显示内容 | 勾选 |
| --- | --- | --- |
| 顶部 | 复习第 16 章（id=2） | 已勾选——状态跟着 id=2 移动过来 |
| 第二行 | 买牛奶（id=1） | 未勾选 |
| 第三行 | 给列表加 key（id=3） | 未勾选 |

版本一（无 key）的预期观察：

| 位置 | 显示内容 | 勾选 |
| --- | --- | --- |
| 顶部 | 复习第 16 章 | 未勾选——顶部的槽位原本属于「买牛奶」，勾选值没跟内容走 |
| 第二行 | 买牛奶 | 已勾选——第二行的槽位原本属于「复习第 16 章」，勾选值留在位置上 |
| 第三行 | 给列表加 key | 未勾选 |

结论必须写清楚：无 key 时，勾选值既不属于「复习第 16 章」也不属于「买牛奶」，它属于第二个位置；有 key 时，勾选值属于 id=2 的那个业务对象。原因在 4.1：无 key 的身份是位置，内容 lambda 换参数不会重置 remember（槽位绑定在调用位置上，第 21、46 章展开）；有 key 的身份是 id，节点与槽位整体移动。

头部插入实验同理：先勾选第一行「买牛奶」，再点「头部插入」。版本二中新任务在顶部且未勾选，「买牛奶」带着勾选到了第二行；版本一中顶部的新任务会直接显示「（已完成）」——它继承了第一行位置的旧状态，看起来像新任务自动完成了。

### 4.3 用索引作 key：等于没加

<!-- compile -->
```kotlin
// 用索引作 key：身份仍然是位置，效果与无 key 基本相同
LazyColumn {
    itemsIndexed(listOf("a", "b", "c"), key = { index, _ -> index }) { _, value ->
        Text(value)
    }
}
```

把上例的写法套用到待办列表（`key = { index, _ -> index }`）再做 4.2 的实验，观察结果与无 key 版本一致：插入后索引整体偏移，id=2 的内容从索引 1 变成索引 2，但「索引 1」这个 key 对应的槽位没动。看起来「加了 key」，实际问题原样保留。

### 4.4 key 能做什么，不能做什么

key 负责正确性，不是万能开关：

1. key 只在集合会变时体现价值。静态列表（数据从不插入删除重排）里，加不加 key 观察不到区别。
2. key 不保证状态永不丢。条目滚出可见窗口太远会被移出组合（第 16 章 4.3 节），普通 `remember` 的状态随之丢弃，有 key 也一样。要跨长距离滚动恢复，用 `rememberSaveable`（第 23 章）：

<!-- compile -->
```kotlin
// 想让勾选状态在条目滚出很远、再滚回来时也保留：rememberSaveable + key
var done by rememberSaveable { mutableStateOf(false) }
Checkbox(checked = done, onCheckedChange = { done = it })
```

3. 官方文档要求 key 类型能被 Bundle 支持（基本类型、enum、Parcelable 等），否则 rememberSaveable 的恢复无法工作；key 在同一列表中必须唯一，重复会在运行时报错。
4. 条目被业务层删除后，其状态随节点销毁，key 不负责「找回已删除条目的状态」——也不应该负责。
5. key 不是性能优化开关。它修正的是状态与动画的归属；列表性能的完整工具箱（contentType、条目稳定性等）在第 75 章。

## 5. 实际执行过程

1. 上一轮组合保存每个条目的 key（或位置）与对应节点、remember 槽位
2. 数据集合发生插入、删除或重排，DSL 重新登记
3. 新一轮 items 对每个元素重新计算 key
4. Compose 按 key（无 key 时按位置）匹配旧节点
5. 能复用的节点移动或更新内容，缺失的节点连同状态被移除

## 6. Flutter / Dart 3 对照

Flutter 中常通过 `ValueKey(item.id)` 赋给列表子 Widget，尤其在可重排、带本地状态的列表中。Compose 则把 key 直接作为 items 参数提供。

```dart
ListView(
  children: items
      .map((item) => ItemCard(
            key: ValueKey(item.id),
            item: item,
          ))
      .toList(),
);
```

相似点：两者都把「业务 id」显式交给框架做条目匹配，解决的都是同一类错位问题。不等价处：Flutter 的 key 挂在 Widget 上，参与 Element 树的 diff 与复用，还有 GlobalKey 等其它语义；Compose 的 key 是 items DSL 的参数，只作用于该列表的条目身份。两套机制只做概念对照，不能视为等价。

## 7. 常见错误

- 每次组合用 UUID.randomUUID() 生成 key，导致所有条目都被视为新条目
- 用不唯一的标题作为 key
- 用索引作 key，在可插入重排列表中造成状态错位（见 4.3）
- 误认为加 key 会自动提升所有列表性能（见 4.4 第 5 条）
- 用整个 data class 对象作 key（见下方反例）

反例一：每次登记都生成随机数。

```kotlin
// 错误示例（故意错误，不参与编译）
items(todos, key = { UUID.randomUUID().toString() }) { todo ->
    TodoRow(todo = todo, onMoveToTop = { })
}
```

现象：每次重新登记时 key 都是新值，条目永远无法与上一轮匹配，勾选状态全部丢失、条目动画无法连贯。原因：key 必须在多轮组合之间保持稳定，「每次现算一个新值」从根上破坏了身份。修正：使用来自业务对象的稳定字段，如 `{ it.id }`。

反例二：用整个对象作 key。

```kotlin
// 错误示例（故意错误，不参与编译）
data class Todo(val id: Long, val title: String)

items(todos, key = { it }) { todo -> /* ... */ }
```

现象：能编译。但 data class 的相等性由全部字段决定，一旦 title 被编辑，这个条目的 key 就变了——被视为「删了一个旧条目、加了一个新条目」，勾选状态照样丢。原因：key 里混入了会变化的字段。修正：`key = { it.id }`，只用稳定不变的身份字段。

## 8. 本章检查题

1. 如果在列表头部插入一项，没有稳定 key 可能发生什么？
2. 为什么 ComponentInfo.id 比 title 更适合作 key？
3. key 是否会决定列表排序？
4. 预测行为：三行待办，勾选第三行「给列表加 key」后点它的「移到顶部」。在 `key = { it.id }` 版本里，哪个位置显示已勾选？在无 key 版本里呢？
5. 动手修改：把 TodoRow 中的 `remember` 换成 `rememberSaveable`，并把初始列表扩到 30 条；勾选第一行后快速滚到底部再滚回顶部，比较换与不换的差别。
6. key 能否让「被业务层删除的待办」在重新添加同名任务时恢复勾选状态？

答案：

1. 头部插入会让后续条目位置整体偏移，条目的 remember 状态、动画和滚动定位错误地跟随位置而不是业务对象（如 4.2 的头部插入实验：新任务直接显示已完成）。
2. id 稳定且唯一；title 可能重复或变化，不适合作为身份标识。
3. 不会。key 只服务于 UI 身份匹配，不改变列表顺序，也不是数据库主键约束。
4. 有 key：顶部的「给列表加 key」显示已勾选（状态跟着 id=3 移动）。无 key：顶部未勾选，第三行位置显示的是原第二行内容且带着已勾选状态——状态留在位置上。
5. 用 `remember` 时，条目滚出太远被移出组合，勾选状态丢失；`rememberSaveable` 配合 key 可以在滚回时恢复。两种写法都要与 key 配合才能「跟着业务对象走」。
6. 不能。删除意味着该 key 的节点被销毁；重新添加是新的 id、新的条目。key 只负责匹配仍然存在的条目。

## 9. 本章总结

key 表达列表条目的稳定身份：无 key 时身份由位置承担，插入、删除、重排会让条目内部状态错位；`key = { it.id }` 让状态、动画跟着业务对象走。key 必须稳定、唯一、来自业务对象，类型需能被 Bundle 支持。它不是排序条件，不是性能开关，也不保证状态永不丢——跨滚动恢复要靠 rememberSaveable。下一章把同样的懒组合思想扩展到网格。
