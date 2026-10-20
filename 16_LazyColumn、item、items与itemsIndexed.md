# 第 16 章：LazyColumn、item、items 与 itemsIndexed

## 1. 本章目标

先看一个具体页面问题：消息页要展示 500 条聊天记录，每条是一张卡片。如果用第 8 章的 `Column` 一次性构建，首屏就要完成全部 500 个子项的组合、测量和布局，用户根本滑不到的条目也白白消耗时间。LazyColumn 就是为「条目多、结构相同」的页面准备的：它只构建屏幕内及其附近可见的条目。

学完后，你应当能够：

- 知道 LazyColumn 只组合可见附近条目（懒组合与懒布局）
- 分别使用 item、items、itemsIndexed 登记条目
- 用 contentPadding 和 verticalArrangement 调整留白与间距
- 区分「懒组合」与「数据分页」
- 根据 DSL 写法推算列表条目总数

## 2. 项目中的位置

- `ui/home/HomeScreen.kt`：首页组件列表
- `components/LazyColumnDemo.kt`：LazyColumn 演示
- `ui/detail/ComponentDetailScreen.kt`：详情页，同样用 LazyColumn 组织区块

## 3. 项目源码片段

### 3.1 项目节选

以下节选依赖 `ComponentRegistry`（第 38 章）与 `EntryCard` 等项目私有符号，不可直接复制，仅用于对照真实用法：

```kotlin
LazyColumn {
    item {
        Text("顶部说明")
    }
    items(ComponentRegistry.all, key = { it.id }) { info ->
        EntryCard(info = info, onClick = { onNavigate(info.id) })
    }
    itemsIndexed((0 until 100).toList()) { index, value ->
        Text("$index: $value")
    }
}
```

### 3.2 独立可运行示例（不依赖项目代码）

下面用一个最小模型 `DemoItem` 重写同样的结构：固定头部 + 对象列表 + 带下标标签。可直接复制到任意 Compose 工程运行。

<!-- compile decls -->
```kotlin
// 最小数据模型：id 是每条数据的稳定身份（第 17 章用它作 key），title 是展示文本
data class DemoItem(val id: Long, val title: String)

@Composable
fun DemoListScreen() {
    // 页面状态：当前有哪些条目。by 委托的读写等价于 .value，第 21 章系统学习
    var demoList by remember {
        mutableStateOf(List(5) { index -> DemoItem(index.toLong() + 1L, "条目 ${index + 1}") })
    }

    Column(Modifier.fillMaxSize()) {
        Button(
            onClick = {
                // maxOfOrNull 找最大 id，空列表返回 null；?: 是 elvis 运算符，null 时取左边后备值
                val nextId = (demoList.maxOfOrNull { it.id } ?: 0L) + 1L
                demoList = demoList + DemoItem(nextId, "条目 $nextId")
            },
            modifier = Modifier.padding(8.dp),
        ) {
            Text("添加一条（当前 ${demoList.size} 条）")
        }

        LazyColumn(
            contentPadding = PaddingValues(16.dp),           // 内容边距：首尾随滚动移动
            verticalArrangement = Arrangement.spacedBy(8.dp), // 条目之间的垂直间距
        ) {
            // 用法一：item——单个静态条目，比如固定头部
            item { Text("固定头部：下面是列表", fontWeight = FontWeight.Bold) }

            // 用法二：items——为集合中每个元素登记一个条目
            items(demoList, key = { it.id }) { demo ->
                Text(demo.title, Modifier.fillMaxWidth())
            }

            // 用法三：itemsIndexed——额外拿到下标
            itemsIndexed(listOf("甲", "乙", "丙")) { index, label ->
                Text("下标 $index 的标签是 $label")
            }
        }
    }
}
```

初始界面：顶部一个按钮显示「当前 5 条」；其下依次是 1 行加粗的固定头部、5 行「条目 1—5」、3 行「下标 0/1/2 的标签」。

操作步骤与预期观察：

1. 点击「添加一条」按钮。预期：按钮文字变成「当前 6 条」，列表在「条目 5」之后新增一行「条目 6」，头部与 3 行下标标签不变。
2. 再点击两次。预期：每次只多一行，条目总数随点击次数线性增长。

示例里出现了三种本章会用到的 Kotlin 写法，先各用两三句话讲清：

- `{ it.id }` 是单参数 lambda：参数没有命名时用隐含名 `it`，等价于 `{ demo -> demo.id }`（lambda 基础见第 5 章）。
- `var demoList by remember { mutableStateOf(...) }` 是属性委托：读取 `demoList` 等价于读 `.value`，赋值等价于改 `.value` 并通知界面重组；细节在第 21 章，本章只需要知道「改它，UI 就更新」。
- `List(5) { index -> ... }` 是带初始化 lambda 的 List 构造函数：生成 5 个元素，每个元素由 lambda 按 `index` 算出。

## 4. 逐步解释

### 4.1 LazyListScope：先登记条目，再按需组合

普通 `Column` 的 content 参数是 `@Composable` 函数，调用时所有子项立即组合。`LazyColumn` 的最后一个参数却是 `content: LazyListScope.() -> Unit`——一个带接收者的 lambda，也就是在大括号里以 `LazyListScope` 为上下文执行 DSL。这不是教学比喻，是真实签名（[API 参考](https://developer.android.com/reference/kotlin/androidx/compose/foundation/lazy/package-summary)）。

因此 `item {}`、`items(...)` 在这里做的不是「立刻组合 UI」，而是「向容器登记条目」：item 登记一个，items 为集合中每个元素登记一个，itemsIndexed 同 items 并把下标也交给内容 lambda。真正执行内容 lambda、组合成界面，发生在框架决定某个条目可见时。

### 4.2 三种 DSL 用法对照

<!-- compile -->
```kotlin
LazyColumn {
    // 单个静态条目：写几次就有几个，不跟随任何数据集合
    item { Text("列表头部") }

    // 按数量生成：只有编号、没有现成对象集合时用
    items(3) { index ->
        Text("第 $index 个索引条目")
    }

    // 按对象集合生成：直接拿到元素本身
    items(listOf("苹果", "香蕉", "橙子")) { fruit ->
        Text("水果：$fruit")
    }

    // 带下标：确实需要位置信息（序号、隔行样式）时用
    itemsIndexed(listOf("红", "绿", "蓝")) { index, color ->
        Text("第 ${index + 1} 位的颜色是 $color")
    }
}
```

| 写法 | 条目数来自 | 内容 lambda 拿到 | 典型用途 |
| --- | --- | --- | --- |
| `item { }` | 书写次数 | 无参数 | 头部、尾部、分隔说明等静态区块 |
| `items(count) { }` | 数量参数 | `index: Int` | 只有编号、没有现成集合 |
| `items(list) { }` | 集合大小 | 元素本身 | 遍历数据模型列表 |
| `itemsIndexed(list) { }` | 集合大小 | `index` 与元素 | 确实需要下标时 |

选择原则：能用元素本身就说明问题时不用下标；下标只是位置，插入、删除后会跟着变，把它当业务身份会出问题（第 17 章展开）。

### 4.3 可见窗口与懒组合的执行机制

按「触发 → 读/写 → 框架行为 → 最终界面」追踪一次：

1. 触发：首次进入页面、用户滚动、或列表数据（如 `demoList`）被替换。
2. 读/写：LazyColumn 读取 DSL 登记的条目总数与 key；此时不执行条目内容 lambda。
3. 框架行为：结合视口尺寸与滚动位置算出需要显示的索引范围，只对范围内的条目执行内容 lambda，完成组合与测量；范围会向滚动方向多保留一段缓冲，具体保留多少属于实现细节，本课程不依赖具体数字。条目滚出范围太远后会被移出组合，其内部 `remember` 状态随之丢弃（第 17、23 章展开）。
4. 最终界面：视口内呈现已组合的条目；继续滚动时，新索引进入窗口被组合，旧索引退出。

「可见窗口」这个词描述的就是第 3 步的索引范围：它像一个随滚动移动的取景框，框住的条目才存在于组合中。

### 4.4 懒组合不等于分页

LazyColumn 解决的是「界面上只构建可见条目」，它对数据从哪来一无所知：

- 它不会发起网络请求，也不会在滚动到底部前自动「加载更多」——数据集多大，它就登记多大。
- 把 10000 条数据全部放进 `items`，组合开销确实只在可见窗口，但数据本身的内存与获取成本仍由业务层承担。
- 需要按页拉取数据时，业务层自己管理分页状态；官方在超大数据集场景提供的方案是与 Paging 库集成（`collectAsLazyPagingItems`），见[官方文档 Large data-sets 分页一节](https://developer.android.com/develop/ui/compose/lists)。

项目把 100 条数据放进 LazyColumn 并固定列表高度，滚动时只有可见区域参与 UI 工作，这是「懒组合」的演示，不是分页。

### 4.5 contentPadding 与 verticalArrangement

两个参数解决「留白放在哪」的问题（对应[官方文档 Content padding 与 Content spacing 两节](https://developer.android.com/develop/ui/compose/lists)）：

- `contentPadding = PaddingValues(16.dp)` 是内容边距：首条目上方和末条目下方各留 16dp，这段留白属于滚动内容——滚到最顶时首条目上方仍有 16dp，继续「下拉」可以把首条目推进这段留白里再回弹。
- `verticalArrangement = Arrangement.spacedBy(8.dp)` 是条目间距：相邻条目之间隔 8dp，首尾不额外加。
- 对比 `Modifier.padding(16.dp)`：那是容器外边距，固定不动、不参与滚动。两者可以并存，但作用不同，不要互相替代。

### 4.6 预测条目数量

条目按「登记制」计数：写几个 `item` 就有几个，每个 `items/itemsIndexed` 贡献其集合大小。独立示例中：

| 登记来源 | 数量 |
| --- | --- |
| `item { 固定头部 }` | 1 |
| `items(demoList)` | demoList.size（初始 5） |
| `itemsIndexed(3 个标签)` | 3 |

初始共 9 个条目；每点一次「添加一条」，`demoList.size` 加 1，总数变成 10、11……头部与标签部分不受影响。能做出这个预测，说明你已经把 DSL 当成「登记表」而不是「执行代码」来读了。

## 5. 实际执行过程

1. LazyColumn 收到条目描述（DSL 执行完毕，得到登记表）
2. 根据视口尺寸与滚动位置决定需要哪些索引
3. 只组合和测量可见附近条目
4. 条目离开有效区域后可被移出组合
5. 滚动到新位置或数据变化时，按登记表重新计算并组合新条目

## 6. Flutter / Dart 3 对照

Flutter 的 ListView.builder 通过 itemCount + itemBuilder 按索引构建；Compose 的 items(list) 更直接拿到元素。item 对应在列表中插入一个固定 Widget。

```dart
ListView.builder(
  itemCount: items.length + 1,
  itemBuilder: (context, index) {
    if (index == 0) return const Text('顶部说明');
    final item = items[index - 1];
    return ListTile(title: Text(item.title));
  },
);
```

| 对照点 | Flutter ListView.builder | Compose LazyColumn |
| --- | --- | --- |
| 惰性范围 | 可见附近的索引才调用 itemBuilder | 可见窗口内的条目才组合 |
| 条目来源 | 单一「索引 → Widget」函数 | DSL 混排 item / items / itemsIndexed |
| 头部/异构条目 | 靠 index 分支或改用 CustomScrollView 模拟 | 同一 DSL 里自然书写 |
| 条目身份 | Widget 的 key（如 ValueKey） | items 的 key 参数 |

相似点：两者都只构建可见附近的条目，本质都是「按需构建 + 视口窗口」。不等价处：ListView.builder 只有一个按索引调用的构建函数，混排头部要靠 index 偏移这类手工技巧；Compose 的 DSL 允许在登记表层面混排异构条目。加上组合模型与身份机制的差异，两者只做概念对照，不能视为等价机制。

## 7. 常见错误

- 把 LazyColumn 当作自动分页加载器（见 4.4）
- 在 LazyColumn 外再套同方向 verticalScroll（见下方反例一）
- 大量数据用普通 Column 一次性组合全部子项
- 在 items 中使用列表下标作为业务身份，却忽略插入删除（第 17 章）
- 把整个集合塞进一个 item 里手动 forEach（见下方反例二）

反例一：同方向嵌套滚动容器。

```kotlin
// 错误示例（故意错误，不参与编译）
Column(
    modifier = Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState()), // 外层可无限滚动
) {
    LazyColumn { // 内层被要求在无限高度里测量自己
        items(100) { index -> Text("第 $index 行") }
    }
}
```

现象：运行时抛出 IllegalStateException，报错信息形如 "Vertically scrollable component was measured with an infinity maximum height constraints"。原因：外层 verticalScroll 给子项的高度约束是无限的，而 LazyColumn 必须有有限的视口高度才能确定可见窗口。修正：去掉外层 verticalScroll，让 LazyColumn 直接占据有限高度；外层确需固定区块时，用 `Modifier.weight(1f)`（第 14 章）给 LazyColumn 一个有限高度。

反例二：把集合塞进一个 item。

```kotlin
// 错误示例（故意错误，不参与编译）
LazyColumn {
    item {
        demoList.forEach { demo -> // 一个条目里手动循环
            Text(demo.title)
        }
    }
}
```

现象：能编译也能显示，但整段内容成了一个不可分割的条目，懒组合对它失效，滚动定位、条目身份（第 17 章）也无从应用。修正：改用 `items(demoList) { demo -> ... }`。官方文档同样把「不要在单个 item 里放多个元素」列为 Lazy 布局的使用技巧之一。

## 8. 本章检查题

1. item 与 items 的用途分别是什么？
2. itemsIndexed 相比 items 多提供了什么？
3. LazyColumn 为什么仍需要数据分页逻辑由业务层处理？
4. 独立示例初次显示时列表共有多少个条目（含头部与标签）？再点两次「添加一条」后是多少？
5. 动手修改：把 `itemsIndexed(listOf("甲", "乙", "丙"))` 改成 5 个标签的集合，先预测界面与条目总数的变化，再运行验证。

答案：

1. item 登记单个条目（如固定头部）；items 为集合中每个元素登记条目。
2. 额外提供 index 下标。
3. LazyColumn 只负责懒组合和懒布局（可见区域才工作），不负责网络请求与分页，数据获取仍由业务层完成。
4. 初始 1 + 5 + 3 = 9 个；每点一次加 1，两次后为 11 个。
5. 下标标签从 3 行变成 5 行（下标 0—4），条目总数从 9 变为 11；头部与 `items(demoList)` 部分不变。预测依据是 4.6 的登记制计数。

## 9. 本章总结

LazyColumn 用 LazyListScope DSL 登记条目，按可见窗口懒组合：item 适合静态条目，items 适合集合，itemsIndexed 在确实需要位置时才使用。contentPadding 管内容留白、verticalArrangement 管条目间距。懒组合不等于分页，数据获取永远是业务层的事。下一章讲 key：让「同一个条目」在插入、删除、重排后仍能被认出来。
