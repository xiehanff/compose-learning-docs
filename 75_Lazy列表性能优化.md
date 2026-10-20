# 第 75 章：Lazy 列表性能优化

## 1. 本章目标

长列表是 App 最常用的界面，也是最容易卡的地方。本章把 Lazy 列表滑动卡顿的常见根因逐个拆开：身份、复用、计算、分页，每一类都有对应的修法。

学完后，你应当能够：

- 用 `key` 与 `contentType` 让列表最小化重组、正确复用插槽
- 把昂贵计算与状态提升到 item 之外，用 remember 缓存 item 内计算
- 用 `derivedStateOf` + `snapshotFlow` 实现触底加载
- 识别并避免「无限高度子项」「item 内建新对象」等反模式

## 2. 项目中的位置

进阶篇不再局限于 compose_demo 项目，本章使用通用 Compose API。

涉及 API：

- `androidx.compose.foundation.lazy`（`LazyColumn`、`LazyListState`、`items(key, contentType)`、`layoutInfo`）
- `androidx.compose.runtime`（`derivedStateOf`、`snapshotFlow`、`remember`）
- `androidx.compose.runtime.saveable.rememberSaveable`（item 状态恢复）

## 3. 项目源码片段

**场景：信息流列表，带稳定的 key、类型化的 contentType，以及触底自动加载下一页。**

<!-- compile decls
prelude: class FeedItem(val id: String, val type: String, val createdAt: Long)
prelude: @Composable fun FeedItemRow(item: FeedItem) = Unit
-->
```kotlin
@Composable
fun FeedList(
    items: List<FeedItem>,
    totalCount: Int,
    onLoadMore: () -> Unit
) {
    val listState = rememberLazyListState()

    // 触底检测：最后一个可见项距末尾不足 3 条时置 true
    // derivedStateOf 保证滑动时只有「越界」瞬间才触发重组
    val shouldLoadMore by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= info.totalItemsCount - 3
        }
    }

    // 每次越过阈值才回调，配合去重避免重复请求
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) onLoadMore()
    }

    LazyColumn(state = listState) {
        items(
            items = items,
            key = { it.id },                    // 身份稳定：插入/删除时只动受影响项
            contentType = { it.type }           // "image"/"text"/"video" 分开复用插槽
        ) { item ->
            FeedItemRow(item)                   // 内容本身是稳定参数的 Composable
        }
    }
}
```

**场景：item 内昂贵的格式化计算用 remember 缓存，按 id 而非对象缓存。**

<!-- compile decls
prelude: class FeedItem(val id: String, val type: String, val createdAt: Long)
prelude: fun formatTimestamp(t: Long): String = ""
-->
```kotlin
@Composable
fun FeedItemRow(item: FeedItem) {
    val timeText = remember(item.id) { formatTimestamp(item.createdAt) }
    // 列表滑动复用插槽时，按 id 命中缓存，避免每次滚动都重新格式化
    Row { Text(timeText) /* ... */ }
}
```

**场景：需要细粒度监听时，用 snapshotFlow 订阅列表状态变化。**

<!-- compile
prelude: val listState = rememberLazyListState()
prelude: fun onLoadMore() {}
-->
```kotlin
import kotlinx.coroutines.flow.*

LaunchedEffect(listState) {
    snapshotFlow {
        val info = listState.layoutInfo
        val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
        last >= info.totalItemsCount - 3
    }
        .distinctUntilChanged()
        .filter { it }
        .collect { onLoadMore() }
}
```

## 4. 逐步解释

**key 决定「谁是谁」。** LazyColumn 只渲染可见窗口附近的 item，滚动时复用插槽。没有 key 时，Compose 按位置对应 item（第 17 章的身份实验）；列表中间插入一条，后续所有 item 的位置都变了，内容按新位置重新组合，且 item 内部状态（如记住的勾选态）会错乱挂到别的数据上。给了稳定 key（数据库 id），插入/删除只影响受影响的那几个 item，身份和状态都跟着数据走。**key 用 index 等于没给 key**——index 在插入后全体漂移，身份仍然跟着位置走；**所有条目共用同一个常量 key 更是直接错误**——key 在同一列表内必须唯一，重复 key 会在运行时抛异常，列表无法渲染（第 17 章同款结论）。

**contentType 决定「怎么复用」。** 不同类型的 item（图片卡片、纯文本、视频）布局差异大，混在一起会让框架难以复用已有的组合与布局结构。`contentType = { it.type }` 让 Compose 按 contentType 分组复用（官方文档称为 layout reuse，布局重用）：同类型 item 之间才复用已构建的结构，避免跨类型反复重建，滚动更顺滑。

**把工作移出 item。** item lambda 每次滑动都会被反复调用（组合+布局），在里面做昂贵计算、创建新对象，等于把成本乘上滚动频率。正确姿势：计算和状态提升到 item 之外（列表层算好再传入），item 内只做必要组合；确实要做的计算用 `remember(item.id)` 缓存，重复滚动不重算。item 里读全局 State 更要警惕——那会让整个列表跟着全局状态一起重组。

**触底加载的监听方式。** `listState.layoutInfo.visibleItemsInfo` 给出当前可见项索引，`derivedStateOf` 把「是否越界」变成派生状态：滑动时只有越过阈值的那一帧触发重组，而不是每帧都重组。配合 `LaunchedEffect(shouldLoadMore)`，只在值翻转时回调一次。`snapshotFlow` 版本适合需要连续响应列表状态的场景（如记录曝光埋点），配合 `distinctUntilChanged` 去重。

**两个结构红线。** 一是 item 高度要稳定：item 里做高度动画会让列表在滚动中反复重新测量（测量抖动），要动画就放在 item 内部固定区域内做，别动 item 根节点高度。二是绝不把无限高度内容（如另一个 LazyColumn、整屏 Column）塞进 LazyColumn——LazyColumn 靠「测量可见项」工作，无限高度子项会破坏它的懒加载假设。

**记住：** 列表性能 = 身份稳定（key）+ 插槽合理（contentType）+ 计算外移（提升/remember）+ 按需加载（触底分页）。

## 5. 实际执行过程

1. 列表首次组合，LazyColumn 只组合可视区 item，滑动时回收离屏插槽
2. 用户下拉刷新，列表头部插入新数据：由于 key 稳定，Compose 只重组被插入位置附近的 item，其余 item 直接跳过
3. 用户滚动，`layoutInfo` 更新，`derivedStateOf` 在最后一个可见项距末尾不足 3 条时翻转
4. `LaunchedEffect` 触发 `onLoadMore()`，请求下一页，期间 `totalCount` 未到新数据前触底条件保持，靠去重防止重复请求
5. 新数据到位，items 变长，列表继续懒加载新 item
6. 用户快速滚动经过大量 item，item 内缓存命中，无重复格式化，帧率稳定

## 6. Flutter / Dart 3 对照

```dart
// ListView.builder 对应 LazyColumn：按需构建
ListView.builder(
  itemCount: items.length,
  itemExtent: 88,                    // 固定高度优化，对应「item 高度稳定」
  itemBuilder: (context, index) => FeedItemRow(item: items[index]),
);

// 触底加载：监听滚动位置
final controller = ScrollController();
controller.addListener(() {
  if (controller.position.pixels >= controller.position.maxScrollExtent - 200) {
    onLoadMore();
  }
});

// AutomaticKeepAliveClientMixin：保持 item 状态不回收
```

相似点：`ListView.builder` 与 LazyColumn 同为按需构建；`itemExtent` 对应稳定高度优化；滚动监听对应 layoutInfo 触底检测。本质区别：身份模型不同——Flutter 的 `ListView.builder` 没有显式 key 概念，item 默认按 index 定位，跨页保留状态要靠 `AutomaticKeepAlive` 显式声明；Compose 的 `key` 是 API 的一等公民，身份、状态、重组范围都围绕它展开，语义更显式。另外 Flutter 用 `itemExtent` 一次性声明所有 item 高度来跳过测量，Compose 则靠编译器稳定性 + 稳定高度假设来优化，机制不同但目标一致。

## 7. 常见错误

- key 用 index：身份跟随位置漂移，插入/删除后内容错位重组、状态错乱；key 用不变常量：重复 key 在运行时直接抛异常，列表无法渲染
- item 内读取全局状态（如顶层 `mutableStateOf`）：状态一变整个列表重组，滑动性能瞬间崩塌
- 每帧在 item lambda 里创建新对象（新字符串、新列表）：对象分配触发频繁 GC，卡顿元凶之一
- 无限滚动不做防重复请求：触底回调在数据到达前反复触发，同一页请求 N 次
- 在 LazyColumn 里嵌无限高度的子项（另一个 LazyColumn / 整屏内容）：破坏懒加载，直接卡死
- 忘记 `rememberSaveable` 恢复 item 状态：进程重建后 item 内滚动位置、选中态丢失

## 8. 本章检查题

1. 为什么 `key` 同时影响性能与 item 状态？用 index 当 key 有什么问题？
2. `contentType` 解决什么问题？
3. 触底加载是怎么监听的？为什么用 `derivedStateOf` 而不是直接读 `layoutInfo`？

答案：

1. key 提供稳定身份：插入/删除时 Compose 能精确定位受影响的 item，其余跳过重组；item 内部状态（remember）也按 key 归属数据。用 index 当 key，插入后所有 item 的 index 漂移，身份错乱，全量重组且状态挂错数据
2. 让同类型 item（图片/文本/视频）之间复用组合与布局结构（官方称 layout reuse），避免跨类型反复重建，减少滚动时的构建开销
3. 监听 `listState.layoutInfo.visibleItemsInfo` 的最后一个可见项索引，距 totalItemsCount 不足阈值时触发加载；用 `derivedStateOf` 把越界判断变成派生状态，只在越过阈值时重组一次，直接读 layoutInfo 会导致每次滚动帧都触发重组

## 9. 本章总结

Lazy 列表性能的四个杠杆：稳定 `key` 保证最小重组与状态正确，`contentType` 优化组合与布局结构的复用，计算与状态外移（提升到 item 外、remember 缓存）砍掉滚动期开销，`derivedStateOf` + 触底检测实现按需加载。同时守住两条结构红线：item 高度稳定、绝不嵌套无限高度内容。把这几条做对，长列表滚动才有顺滑的基础；实际帧率仍应以 Macrobenchmark 在可重复条件下（release-like 构建、固定设备）测得的数字为准——本仓库未做任何设备实测，不宣称已验证的具体收益。
