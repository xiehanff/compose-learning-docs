# 第 16 章：LazyColumn、item、items 与 itemsIndexed

## 1. 本章目标

掌握 Compose 懒列表 DSL，理解静态条目、集合条目和带索引条目的不同写法。

学完后，你应当能够：

- 知道 LazyColumn 只组合可见附近条目
- 使用 item 添加单个静态条目
- 使用 items 遍历对象列表
- 使用 itemsIndexed 同时获取下标和元素

## 2. 项目中的位置

- `ui/home/HomeScreen.kt`
- `components/LazyColumnDemo.kt`
- `ui/detail/ComponentDetailScreen.kt`



## 3. 项目源码片段

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

## 4. 逐步解释

`LazyColumn` 的 content 不是普通 Column 内容，而是 `LazyListScope` DSL。`item {}` 注册一个条目描述；`items(list) { element -> }` 为集合中每个元素注册条目；`itemsIndexed` 额外提供 index。

“懒加载”在这里主要是懒组合和懒布局：屏幕附近的条目才会进入组合与测量，适合大量或不确定数量数据。它并不自动进行网络分页。项目把 100 条数据放进 LazyColumn，固定列表高度后滚动，就能看到只有可见区域需要参与 UI 工作。

## 5. 实际执行过程

1. LazyColumn 收到数据条目描述
2. 根据滚动位置决定需要哪些索引
3. 只组合和测量可见附近条目
4. 条目离开有效区域后可被移出组合
5. 滚动到新位置时组合新条目

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

## 7. 常见错误

- 把 LazyColumn 当作自动分页加载器
- 在 LazyColumn 外再套同方向 verticalScroll
- 大量数据用普通 Column 一次性组合全部子项
- 在 items 中使用列表下标作为业务身份，却忽略插入删除

## 8. 本章检查题

1. item 与 items 的用途分别是什么？
2. itemsIndexed 相比 items 多提供了什么？
3. LazyColumn 为什么仍需要数据分页逻辑由业务层处理？

答案：

1. item 注册单个条目；items 为集合中每个元素注册条目。
2. 额外提供 index 下标。
3. LazyColumn 只负责懒组合和懒布局（可见区域才工作），不负责网络请求与分页，数据获取仍由业务层完成。

## 9. 本章总结

LazyColumn 用 DSL 描述条目，并根据滚动窗口懒组合。item 适合静态条目，items 适合集合，itemsIndexed 在确实需要位置时才使用。
