# 第 18 章：LazyVerticalGrid 与 GridCells

## 1. 本章目标

先看一个具体页面问题：相册页要把照片排成网格。手机竖屏上 3 列正好，可同样的代码放到平板或横屏上，3 列每格被拉得过宽、照片模糊难看。你当然希望「宽度够就多放几列，每格别小于某个尺寸」。LazyVerticalGrid 的 GridCells 就是回答这个问题的两种列规则：Fixed 固定列数，Adaptive 按最小格宽自适应。

学完后，你应当能够：

- 使用 GridCells.Fixed 固定列数
- 使用 GridCells.Adaptive 按最小格宽自适应，并能按公式推算列数
- 配置横向间距（列间距）与纵向间距（行间距）
- 在网格条目上使用 key
- 知道 span 的作用与版本边界
- 在 LazyColumn 与 LazyVerticalGrid 之间做选型

## 2. 项目中的位置

- `components/GridViewDemo.kt`

## 3. 项目源码片段

### 3.1 项目节选

项目节选，依赖原项目的设计稿单位 `.px`（第 41/43 章的适配体系；本仓库片段工程由 `SnippetsSupport.kt` 提供同名支撑），不可直接复制到普通工程，普通工程请把 `.px` 换成 `.dp`：

<!-- compile -->
```kotlin
LazyVerticalGrid(
    columns = GridCells.Fixed(3),
    modifier = Modifier
        .fillMaxWidth()
        .height(208.px),
    verticalArrangement = Arrangement.spacedBy(8.px),
    horizontalArrangement = Arrangement.spacedBy(8.px),
) {
    items(9) { index ->
        Box(Modifier.height(64.px)) { Text("$index") }
    }
}
```

### 3.2 独立示例：Fixed 与 Adaptive 对照（不依赖项目代码）

<!-- compile decls -->
```kotlin
// 固定 3 列：无论多宽，列数不变，每列随宽度伸缩
@Composable
fun FixedGridDemo() {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxWidth().height(220.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), // 列与列之间
        verticalArrangement = Arrangement.spacedBy(8.dp),   // 行与行之间
        contentPadding = PaddingValues(8.dp),
    ) {
        items(9) { index ->
            Box(
                modifier = Modifier
                    .height(56.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Text("$index")
            }
        }
    }
}
```

<!-- compile decls -->
```kotlin
// 自适应：每列不小于 100dp，宽度够就多放列
@Composable
fun AdaptiveGridDemo() {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 100.dp),
        modifier = Modifier.fillMaxWidth().height(220.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(12) { index ->
            Box(
                modifier = Modifier
                    .height(56.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Text("$index")
            }
        }
    }
}
```

初始界面：两个示例各是一个 9 格（或 12 格）的编号网格。操作步骤与预期观察：旋转屏幕、分屏或换不同宽度的设备/模拟器。预期：Fixed 版本始终 3 列，变宽后每格变宽；Adaptive 版本列数随可用宽度变化（360dp 宽约 3 列，600dp 宽约 5 列，推导见 4.2），每格宽度大体稳定。

## 4. 逐步解释

### 4.1 GridCells.Fixed 的尺寸推导

Fixed 的规则最简单：列数写死，可用宽度扣除列间距后平均分配。以可用宽度 360dp、`Fixed(3)`、列间距 8dp 为例：

1. 预留列间距：360 − (3 − 1) × 8 = 344dp
2. 平均分给 3 列：344 ÷ 3 ≈ 114.7dp

换到 720dp 宽的平板：列数仍是 3，每列 (720 − 16) ÷ 3 ≈ 234.7dp——这就是 3.2 节 Fixed 示例在宽屏上「格子被拉宽」的算术来源。整像素分配时靠前的列可能多 1 像素，属实现细节（教学简化，不影响推导）。

### 4.2 GridCells.Adaptive 的列数推导

Adaptive 回答的问题是「宽度够就多放列，但每列不得小于 minSize」。列数推导（按 dp 近似，实现内部会先转像素取整）：

```text
列数 = max(1, (可用宽度 + 列间距) ÷ (最小列宽 + 列间距) 的整数部分)
每列宽 ≈ (可用宽度 − (列数 − 1) × 列间距) ÷ 列数
```

算例一（手机竖屏，可用宽度 360dp，Adaptive(100.dp)，间距 8dp）：

1. (360 + 8) ÷ (100 + 8) = 368 ÷ 108 = 3.40，取整数部分 3 → 3 列
2. 每列 (360 − 2 × 8) ÷ 3 = 344 ÷ 3 ≈ 114.7dp，不小于 100dp，成立

算例二（平板，可用宽度 720dp）：

1. (720 + 8) ÷ 108 = 6.74 → 6 列
2. 每列 (720 − 5 × 8) ÷ 6 = 680 ÷ 6 ≈ 113.3dp

窄屏与宽屏对照：

| 可用宽度 | 列数 | 每列宽（约） |
| --- | --- | --- |
| 360dp（手机竖屏） | 3 | 114.7dp |
| 600dp（折叠屏/分屏） | (608 ÷ 108) = 5 | (600 − 32) ÷ 5 = 113.6dp |
| 720dp（平板） | 6 | 113.3dp |

可以看到 Adaptive 的设计意图：列数变了，每格尺寸大体稳定。

两个必须知道的边界：

- 算例三（极窄，可用宽度 90dp）：(90 + 8) ÷ 108 = 0.9，取整数部分 0，再由 max(1, 0) 兜底成 1 列——这唯一的一列宽 90dp，比 minSize 还窄。minSize 是「排列数」的最小值，不是「列宽」的硬下限。
- 可用宽度指扣除横向 contentPadding 之后的部分；上文算例都假设 padding 为 0。
- 结论：不要在代码里假设「手机都是 360dp」。设备宽度随屏幕、旋转、分屏、折叠变化，需要响应宽度就用 Adaptive，或在 WindowSizeClass（第 79 章）等约束信息上做分支。

### 4.3 间距与 key

- `horizontalArrangement = Arrangement.spacedBy(8.dp)`：列间距。
- `verticalArrangement = Arrangement.spacedBy(8.dp)`：行间距。
- 只设其一的另一维默认紧贴，常见「忘了列间距」的网格看起来像表格线粘连。

网格条目的 key 与第 17 章完全同构：数据会插入、删除、重排的网格（相册、表情面板）应提供 `key = { it.id }`，让条目内部状态跟着业务对象走。

<!-- compile decls -->
```kotlin
// 相册模型：id 用作条目身份（第 17 章）
data class Album(val id: Long, val title: String)

@Composable
fun AlbumGrid(albums: List<Album>) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 100.dp),
        modifier = Modifier.fillMaxWidth().height(240.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // 头部条目：key 标识身份；span 让它横跨当前行的所有列
        item(key = "header", span = { GridItemSpan(maxCurrentLineSpan) }) {
            Text("我的相册", fontWeight = FontWeight.Bold)
        }
        items(albums, key = { it.id }) { album ->
            Box(
                modifier = Modifier
                    .height(48.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Text(album.title)
            }
        }
    }
}
```

### 4.4 span：让条目横跨多列

`span` 是网格 DSL 中 item/items 的参数（不是 Modifier），值是 `GridItemSpan(跨列数)`；`maxCurrentLineSpan` 是「当前行一共几列」，常用来做横跨整行的头部或分隔条，如上例的相册标题。跨固定列数则写 `GridItemSpan(2)`。

版本边界：lazy grid 系列 API 在早期 foundation 版本（1.2 时代）曾需要 `@OptIn(ExperimentalFoundationApi::class)`，后续版本转为稳定；在本仓库基线（Compose BOM 2026.06.00，见 `snippets/gradle/libs.versions.toml`）中，GridCells、items、span 与 GridItemSpan 都是稳定 API，无需 OptIn。你自己的工程以所用 foundation 版本的 [API 参考](https://developer.android.com/reference/kotlin/androidx/compose/foundation/lazy/grid/package-summary)为准。

### 4.5 LazyVerticalGrid 与 LazyColumn 的选型

| 问题 | 倾向 |
| --- | --- |
| 条目天然占满一行（信息流、消息、设置项） | LazyColumn |
| 条目是等宽小块，需要按行排布（相册、表情、应用抽屉） | LazyVerticalGrid |
| 一个页面两者都要（项目详情页：说明文字 + 网格演示） | LazyColumn 里放固定高度的 LazyVerticalGrid（项目节选即如此），或网格里用 span 放整行区块 |

判断依据是内容模型，不是「哪个更新」；两者都是懒组合容器，第 16 章的可见窗口机制同样适用。

## 5. 实际执行过程

1. 网格获得有限宽度（含扣减 contentPadding）
2. GridCells 按可用宽度计算列数和每列宽度（4.1/4.2 的推导即此步骤）
3. 根据滚动位置确定可见行
4. 组合可见格子
5. Arrangement 应用行列间距

## 6. Flutter / Dart 3 对照

Flutter 的 GridView.builder + SliverGridDelegateWithFixedCrossAxisCount 对应 Fixed；SliverGridDelegateWithMaxCrossAxisExtent 更接近 Adaptive。

```dart
GridView.builder(
  gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
    crossAxisCount: 3,
    mainAxisSpacing: 8,
    crossAxisSpacing: 8,
  ),
  itemCount: 9,
  itemBuilder: (context, index) => Center(child: Text('$index')),
);
```

相似点：两者都把「列规则」从条目构建中独立出来（GridCells 对应 SliverGridDelegate），都按可见范围惰性构建。不等价处：Adaptive 的参数是每列最小宽度，MaxCrossAxisExtent 的参数是每列最大宽度，方向相反、公式不同，只是效果相近；且 Flutter 网格的 childAspectRatio 控制格子的纵横比，Compose 网格的格子高度由条目内容自己决定。不能视为等价机制。

## 7. 常见错误

- 在垂直 LazyColumn 中嵌套没有有限高度的 LazyVerticalGrid（见下方反例）
- 把 Adaptive 的参数误解为固定列宽而不是最小尺寸
- 只设置 verticalArrangement，忘记列间距
- 大数据网格使用 Column + 多个 Row 手工一次性创建

反例：嵌套懒网格不给高度。

```kotlin
// 错误示例（故意错误，不参与编译）
LazyColumn {
    item {
        LazyVerticalGrid(columns = GridCells.Fixed(3)) { // 没有高度约束
            items(90) { index -> Text("$index") }
        }
    }
}
```

现象：运行时抛出 IllegalStateException，提示形如 "Vertically scrollable component was measured with an infinity maximum height constraints"。原因：外层 LazyColumn 的 item 高度不限，内层网格拿不到有限的视口高度，无法决定一屏显示几行。修正：像项目节选那样给网格 `Modifier.height(...)` 等有限高度；这属于第 20 章展开的滚动容器选型问题。

## 8. 本章检查题

1. Fixed(3) 在宽屏上列数会变化吗？
2. Adaptive(100.dp) 的 100dp 表达什么？
3. 为什么项目给嵌套网格固定 height？
4. 推导：可用宽度 392dp、Adaptive(80.dp)、列间距 8dp，列数与每列宽各是多少？
5. 动手修改：把 3.2 节 Adaptive 示例的 minSize 从 100.dp 改为 160.dp，先按公式预测 360dp 宽设备的列数，再旋转屏幕或用分屏验证。

答案：

1. 不会。Fixed(3) 固定分成 3 列，扣除列间距后平均分配每列宽度。
2. 每列的最小宽度；网格会根据可用宽度放入尽可能多的列。
3. 网格嵌套在详情页的 LazyColumn 中，固定高度可以避免同方向嵌套懒布局出现无限测量问题。
4. (392 + 8) ÷ (80 + 8) = 400 ÷ 88 = 4.54，取整数部分 4 列；每列 (392 − 3 × 8) ÷ 4 = 368 ÷ 4 = 92dp。
5. (360 + 8) ÷ (160 + 8) = 368 ÷ 168 = 2.19，取 2 列；每列约 (360 − 8) ÷ 2 = 176dp。minSize 越大列越少、每格越大。

## 9. 本章总结

LazyVerticalGrid 是懒组合网格：Fixed 控制固定列数，Adaptive 按最小格宽自适应，列数都能用公式推算。列间距用 horizontalArrangement、行间距用 verticalArrangement，span 让条目跨列（本仓库基线中为稳定 API）。不要依赖固定设备宽度；嵌套在同方向滚动容器时必须给网格有限高度。下一章转向另一族滚动容器：普通布局加滚动 Modifier。
