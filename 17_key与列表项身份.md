# 第 17 章：key 与列表项身份

## 1. 本章目标

理解 `key = { it.id }` 不是排序条件，而是帮助 Compose 在数据变化时识别“这还是不是原来的那个条目”。

学完后，你应当能够：

- 知道 key 必须稳定且在同一列表中唯一
- 理解没有 key 时默认身份常与位置相关
- 理解插入、删除、重排时 key 对局部状态的影响
- 避免使用随机数或可变字段作为 key

## 2. 项目中的位置

- `ui/home/HomeScreen.kt`



## 3. 项目源码片段

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

## 4. 逐步解释

Compose 需要把本次列表中的条目与上一次组合中的条目对应起来。`key = { info.id }` 告诉它：只要 id 相同，即使条目移动到了另一个位置，也应视为同一个逻辑条目。这样条目内部 remember 状态、动画和滚动定位更有机会跟随业务对象，而不是错误地留在旧位置。

key 不会改变列表顺序，也不是数据库主键约束。它只服务于 UI 身份匹配。项目的 ComponentInfo.id 同时是路由参数和列表稳定标识，天然适合作为 key。

## 5. 实际执行过程

1. 上一次组合保存条目 key 与对应节点
2. 数据集合发生插入、删除或重排
3. 新一轮 items 重新计算 key
4. Compose 按 key 匹配旧节点
5. 能复用的节点移动或更新，缺失节点被移除

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

## 7. 常见错误

- 每次组合用 UUID.randomUUID() 生成 key，导致所有条目都被视为新条目
- 用不唯一的标题作为 key
- 误认为加 key 会自动提升所有列表性能
- 把 index 当 key，在可插入重排列表中造成状态错位

## 8. 本章检查题

1. 如果在列表头部插入一项，没有稳定 key 可能发生什么？
2. 为什么 ComponentInfo.id 比 title 更适合作 key？
3. key 是否会决定列表排序？

## 9. 本章总结

key 表达列表条目的稳定身份。它在数据重排和带局部状态的列表中尤其重要，应选择唯一、稳定、来自业务对象的字段。
