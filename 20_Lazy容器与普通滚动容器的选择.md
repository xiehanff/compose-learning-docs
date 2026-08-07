# 第 20 章：Lazy 容器与普通滚动容器的选择

## 1. 本章目标

建立选择标准，而不是看到“可滚动”就随意使用 LazyColumn 或 verticalScroll。

学完后，你应当能够：

- 根据数据量和结构选择布局
- 理解懒组合与完整组合的成本差异
- 判断嵌套滚动是否需要有限尺寸
- 避免为了少量固定内容滥用复杂列表

## 2. 项目中的位置

- `components/LazyColumnDemo.kt`
- `components/ScrollViewDemo.kt`
- `components/GridViewDemo.kt`



## 3. 项目源码片段

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

## 4. 逐步解释

选择的核心不是组件名称，而是内容模型。大量、动态、同构条目需要 LazyColumn/LazyVerticalGrid；少量、固定、异构页面区块可用 Column + verticalScroll。Lazy 容器能减少不可见条目的组合和布局，但需要条目 DSL、身份管理和滚动容器约束。

项目首页和详情页都可能继续增加条目，因此采用 LazyColumn；ScrollViewDemo 则专门演示固定子项整体滚动。一个页面完全可以在 LazyColumn 中用多个 `item {}` 放异构区块，不必为了“页面不是纯列表”就放弃 LazyColumn。

## 5. 实际执行过程

1. 分析数据规模与变化方式
2. 确定是否需要懒组合
3. 确定是线性还是网格
4. 检查父级是否提供有限视口
5. 选择 Lazy DSL 或普通布局+scroll Modifier

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

## 7. 常见错误

- 认为 LazyColumn 永远比 Column 性能更好
- 把多个同方向 Lazy 容器无约束嵌套
- 数据量巨大却使用 Column 一次性创建
- 因为页面区块不同就认为不能用 LazyColumn 的多个 item

## 8. 本章检查题

1. 1000 条消息列表应选什么？
2. 只有 8 个固定设置区块应选什么？
3. 为什么详情页用 LazyColumn，即使只有两个主要区块？

## 9. 本章总结

大量或动态条目选 Lazy；少量固定区块可选普通布局加滚动。还要同时考虑嵌套关系、视口约束、条目身份和页面未来扩展。
