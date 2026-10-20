# 第 20 章：Lazy 容器与普通滚动容器的选择

## 1. 本章目标

先看两个真实翻车：同一个「消息页」，有人用 Column + verticalScroll 装了几百条消息，数据一多首屏构建明显变慢；另一个「设置页」只有 8 个固定区块，有人硬套 LazyColumn，为每个区块想 key、登记条目，代码变长却没有换来任何东西。两族容器都能滚，差别在组合模型。本章建立选择标准，而不是看到「可滚动」就随意挑一个。

学完后，你应当能够：

- 从子项总量、创建成本、状态保留、测量约束四个维度评估页面
- 用决策表为具体业务场景选容器
- 用多个 item {} 在 LazyColumn 里组织异构区块
- 不依赖「谁永远更快」这类无依据结论（本章不做任何性能数字承诺）

## 2. 项目中的位置

- `components/LazyColumnDemo.kt`
- `components/ScrollViewDemo.kt`
- `components/GridViewDemo.kt`

## 3. 项目源码片段

### 3.1 选型示意（结构示意，不参与编译）

```kotlin
// 大量同构数据
LazyColumn {
    items(messages, key = { it.id }) { MessageRow(it) }
}

// 少量固定页面区块
Column(
    Modifier.verticalScroll(rememberScrollState())
) {
    Header()
    ProfileForm()
    Footer()
}
```

### 3.2 对照示例：同一份数据、两种容器（不依赖项目代码）

<!-- compile decls -->
```kotlin
// 同一份消息数据放进两种容器：上半 Column+verticalScroll，下半 LazyColumn
data class Message(val id: Long, val text: String)

@Composable
fun SameDataTwoWays(
    messages: List<Message> = List(100) { Message(it.toLong(), "消息 $it") },
) {
    Column(Modifier.fillMaxSize()) {
        Text("上：Column + verticalScroll（全部子项立即组合）", Modifier.padding(8.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            messages.forEach { message ->
                Text(message.text, Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
            }
        }
        Text("下：LazyColumn（只组合可见附近条目）", Modifier.padding(8.dp))
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(messages, key = { it.id }) { message ->
                Text(message.text, Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
            }
        }
    }
}
```

初始界面：上下两个半屏列表，内容一模一样。

操作步骤与预期观察：

1. 分别滚动上下两块。预期：视觉行为相同，都能平滑滚动到底。
2. 想验证组合范围差异，用第 19 章 8.6 题的日志法：给每个子项包一层 `LaunchedEffect { println("组合 $index") }` 再进入页面。预期：上半一次打印约 100 条，下半只打印可见附近的若干条，滚动时陆续补打。

差别不在「能不能滚」，在「哪些子项存在于组合中」。

## 4. 逐步解释

### 4.1 四个维度

| 维度 | 要问的问题 | 倾向 Column + verticalScroll | 倾向 LazyColumn |
| --- | --- | --- | --- |
| 子项总量 | 大概多少个子项？会不会持续增长？ | 少量、一屏到两屏内、结构固定 | 大量、不可预知、持续追加 |
| 创建成本 | 单个子项组合贵不贵？ | 子项简单便宜 | 子项重（复杂布局、图片等），希望不可见的不构建 |
| 状态保留 | 子项内部状态要不要一直保留？ | 容器存活期间天然全保留 | 滚出窗口即丢弃，需 rememberSaveable + key（第 17、23 章） |
| 测量约束 | 嵌套环境给不给有限高度？ | 自身就要求「高度不限」的测量 | 必须拿到有限视口，嵌套时要 height/weight 约束（第 18、19 章） |

两点说明：其一，本课程不给「超过 N 条就用 Lazy」的数字阈值——没有设备实测依据，数量级判断要结合单条创建成本与增长方式；其二，四个维度可能互相牵制（比如子项少但嵌在无限高度环境里），按页面实际约束取舍。

### 4.2 选型决策表

| 场景特征 | 选择 |
| --- | --- |
| 同构条目、大量或动态增长、数据会插入删除 | LazyColumn（配 key） |
| 少量固定异构区块：表单、设置页、说明页 | Column + verticalScroll |
| 固定头部/底部 + 长列表 | LazyColumn，用多个 item {} 放固定区块 |
| 滚动内容里还要嵌一段长列表/网格 | 给内层有限高度（height/weight），或重新设计为单一 Lazy 容器 |
| 大量等宽小块（相册、表情） | LazyVerticalGrid（第 18 章） |
| 单行横排标签 | Row + horizontalScroll（第 19 章） |

### 4.3 三个业务场景

场景一：个人资料编辑表单（头像选择、5 个输入框、2 个开关、保存按钮）。

选择：Column + verticalScroll。理由：子项总量少且固定，区块异构——每块结构都不同，没有「同构条目」可复用；每个输入子项自带草稿状态，全部常驻组合让状态天然保留，不必处理「滚远丢草稿」；也不需要条目身份与 key。

场景二：群聊历史消息（数千条，持续增长）。

选择：LazyColumn + key + 业务层分页。理由：子项同构、总量大且不可预知，只需要组合可见窗口；消息有稳定 id，天然适合 `key = { it.id }`（第 17 章）；数据获取与按页加载由业务层负责（第 16 章 4.4 节）。

场景三：文章详情页（固定作者卡片 + 正文 + 可增长的评论区）。

选择：整体用 LazyColumn——`item {}` 放作者卡片与正文，`items(comments, key = { it.id })` 放评论。理由：这是「固定区块 + 长列表」的混合内容，LazyColumn 的 DSL 本来就支持异构条目混排；评论可能增长，让长数据部分享受懒组合。备选方案是外层 Column + verticalScroll 放固定区块、内层给固定高度的 LazyColumn 装评论，但两段滚动区的手势边界容易让用户困惑（第 19 章 4.5 节），除非确有「正文独立滚动」的产品需求，否则优先单一 Lazy 容器。项目详情页正是「LazyColumn + 多个 item 区块」的实例。

### 4.4 两个常见误解的修正

误解一：「LazyColumn 永远比 Column 性能好」。不对。少量子项时，两族容器组合的都是同一批内容，Lazy 侧还要多做条目登记与可见窗口管理；差异是否可感知、值多少，需要用工具实测（第 74、75 章），本课程不做数字承诺。选型依据是 4.1 的维度，不是组件名字里有没有 Lazy。

误解二：「页面区块各不相同，所以不能用 LazyColumn」。不对。item {} 里可以放任意 Composable，异构区块混排正是 DSL 的设计意图（场景三）。

反过来也要警惕：「能滚就先用 Column 凑合」。全部子项立即组合意味着构建成本随子项数线性增长，这是结构性差异，数据增长后不是调参数能弥补的，需要换容器。

## 5. 实际执行过程

1. 分析数据规模与变化方式（总量、是否增长、是否同构）
2. 确定是否需要懒组合
3. 确定是线性还是网格
4. 检查父级是否提供有限视口
5. 选择 Lazy DSL 或普通布局 + scroll Modifier，并决定 key 与固定区块的安放方式

## 6. Flutter / Dart 3 对照

Flutter 的 ListView.builder/GridView.builder 与 SingleChildScrollView 之间也遵循同一标准：大量数据用 builder，少量固定内容用普通 children。

```dart
Widget buildBody() {
  if (items.length > 50) {
    return ListView.builder(
      itemCount: items.length,
      itemBuilder: (_, i) => ItemTile(items[i]),
    );
  }
  return SingleChildScrollView(
    child: Column(children: items.map(ItemTile.new).toList()),
  );
}
```

相似点：两侧的判断标准一致——「按需构建（builder / Lazy DSL）」对应大量同构数据，「全量构建（SingleChildScrollView + Column / verticalScroll）」对应少量固定内容。不等价处：Flutter 的 `ListView(children: ...)` 虽然会立即构建全部子 Widget，渲染层仍走视口 sliver 体系；Compose 的 Column + verticalScroll 没有任何列表组件参与，滚动只是偏移。实现模型不同，只做概念对照。

## 7. 常见错误

- 认为 LazyColumn 永远比 Column 性能更好（见 4.4）
- 把多个同方向 Lazy 容器无约束嵌套（第 18、19 章反例）
- 数据量巨大却使用 Column 一次性创建
- 因为页面区块不同就认为不能用 LazyColumn 的多个 item

## 8. 本章检查题

1. 1000 条消息列表应选什么？
2. 只有 8 个固定设置区块应选什么？
3. 为什么详情页用 LazyColumn，即使只有两个主要区块？
4. 四个选型维度分别是什么？各自的典型判据是什么？
5. 判断题：聊天页顶部有固定搜索栏，下面是数千条历史消息。把搜索栏放进 LazyColumn 的 item {} 里，与外层 Column 包住搜索栏 + LazyColumn 相比，哪种结构更合适？为什么？
6. 动手修改：把 3.2 节示例的数据换成 `List(1000) { ... }`，用第 19 章的日志法先预测两种容器的日志条数差异，再运行验证。

答案：

1. LazyColumn。大量、动态、同构条目，需要懒组合和懒布局，配 key 管理身份。
2. Column + verticalScroll。少量、固定、异构区块，一次性组合即可。
3. 详情页还可能继续增加条目，而且 LazyColumn 可以用多个 item {} 放异构区块，不必因为页面不是纯列表就放弃它。
4. 子项总量（多少、是否增长）、创建成本（单条贵不贵）、状态保留（要不要跨滚动保留）、测量约束（嵌套给不给有限高度）。判据见表 4.1。
5. item {} 结构更合适。外层 Column 包 LazyColumn 需要给 LazyColumn 有限高度（如 weight），否则崩溃；即使给了高度，搜索栏与消息也形成两段滚动或一段固定+一段滚动，而单一 LazyColumn 里搜索栏随内容滚动、结构最简单。（若产品要求搜索栏固定不随消息滚动，则应反过来，用 Scaffold 等固定结构承载搜索栏。）
6. 上半容器进入页面即打印约 1000 条日志，下半只打印可见附近的若干条；滚动时下半陆续补打、上半不再新增。预测依据是两族容器的组合范围差异（第 19 章 4.4 节）。

## 9. 本章总结

选型看内容模型，不看组件名字：大量或动态条目选 Lazy（配 key）；少量固定区块选普通布局加滚动；混合内容优先用 LazyColumn 的多个 item {} 组织异构区块。评估时过一遍子项总量、创建成本、状态保留、测量约束四个维度，注意嵌套滚动容器的有限视口要求。本章不给性能数字——需要量化时用第 74、75 章的工具实测。
