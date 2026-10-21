# 第 84 章：商品详情：SKU 选择与评论分页

## 1. 本章目标

学完后，你应当能够：

- 写出 `DetailViewModel`：沿用第 83 章的可变字段模式，`detail/selectedSpecValues/quantity/comments/commentError` 一个职责一个字段；`loadSeq` 防换商品后的旧响应，换商品时评论分页与加购状态同步归位
- 实现 SKU 匹配核心：每组规格选一个值后，把已选规格值 id 与 `SkuDto.specValueIds` 按**集合相等**比较（不按数组顺序），命中后价格/图片/库存联动，数量上限随 SKU 收紧
- 用一个 `LazyColumn` 组出「轮播 + 基础信息 + SKU 选择 + 评论分页 + 图文明细」的详情页，底部操作栏含加购与去购物车；评论区块用两个字段区分首屏 loading、成功为空、首页失败、下一页失败四态
- 说清 `reviewInfo` 概览与 `comment/page` 列表的分工、`detailHtml` 不渲染的教学取舍，以及加购反馈为什么分「全局事实（`onCartChanged`）」与「页面提示（一次性回调）」两层，而不写进持久错误字段
- 对照 GetX 版详情控制器的 SKU 选择与加购交互，说出两边**职责上的对应**与机制差异

## 2. 接口与区块地图

DTO 与 `MallRepo` 已在第 82 章就位。`SpuDetailDto` 是一个六块的嵌套大对象，本章对每块的处理不同：

| 区块 | 接口 | MallRepo 方法 |
| --- | --- | --- |
| 详情主对象（六块一次返回） | `POST app-api/shop-product/spu/detail` | `loadProductDetail(spuId)` |
| 评论分页 | `POST app-api/shop-product/comment/page` | `loadComments(spuId, pageNo, pageSize)` |
| 加入购物车 | `POST app-api/shop-trade/cart/add` | `addCart(skuId, quantity)` |

| SpuDetailDto 字段 | 页面落点 | 本章处理 |
| --- | --- | --- |
| `basicInfo` | 顶部轮播 + 基础信息区 | 主要展示字段：`imgsUrl/price/originPrice/name/subTitle/tags/pointReward/salesVolume/deliveryTime/isFreeShipping/unit`；`shopType` 不展示 |
| `skuInfo.specGroupList` | SKU 选择区（每组一行 chip） | 只用 `id/name` 与 `specValueList` 的 `id/name` 做选择；规格值自带的 `imgUrl` 不展示 |
| `skuInfo.skuList` | SKU 匹配、价格/图片/库存联动 | 用 `id/name/imgUrl/price/stock/specValueIds`；`originPrice/vipPrice` 不展示 |
| `reviewInfo` | 评论概览条 | 只读 `reviewCount/goodRate`，`list` **不入列表** |
| `shopInfo` | — | 不展示，留给第 86、87 章 |
| `detailInfo` | 尾部图文 | 只铺 `detailImages`，`detailHtml` 不渲染 |
| `groupBuyInfo` | 基础信息区标签 | 只判空打「拼团」标签，不展开字段 |

刻意不展示的字段不是没用：店铺类型标、规格值小图、SKU 划线价与会员价都是真实详情页的常见物料，本章为了聚焦状态管理从简——数据都在 DTO 里，练习时随手可加。

契约要点：

- **有详情数据的商品是 2001—2005、2011**；推荐流其余 id（如 2006）请求详情返回 `code=1`「商品不存在」——现成的业务失败演示，`MallRepo` 的拆壳会把它变成异常。
- **SKU 在 `data.skuInfo.skuList`**，不在 `data.skuList`——curl 验证时注意路径，别把 `skuInfo` 这层跳过去。
- `reviewInfo` 是概览：评论数、好评率，外加**一条**预览评论。它与 `comment/page` 第一页的数据是重叠关系，两边不要拼接。
- `comment/page` 的分页壳与第 83 章商品流同款：`PageDto{list, total, hasMore}`，翻页只认 `hasMore`。
- `SkuDto.specValueIds` 是「该 SKU 由哪些规格值组成」的 id 集合——匹配要用**集合语义**，契约不承诺数组顺序。
- `SkuDto` **没有** `purchaseLimit` 字段：限购只存在于购物车条目（`CartItemDto.purchaseLimit`，第 85 章），详情侧的数量约束只有库存（见 3.2「数量边界只管『本次添加』」）。
- 价格字段全是字符串（`"49.90"`），划线价比较前必须数值化，规则与第 83 章 `ProductCard` 相同。

## 3. 逐步实现

### 3.1 DetailViewModel：字段与加载方法

详情页的形态是「一个大嵌套对象 + 三条独立请求」。ViewModel 不走第 68 章的 `StateFlow + sealed UiState`（那是状态要跨层共享时的写法），沿用第 83 章的取舍：页面私有状态用一组 `mutableStateOf` 字段、直连 `MallRepo`（第 82 章 3.4）、不设 Repository 接口、不引入 DI。每个字段对应页面一个可独立变化的区块，`private set` 保证外部只读。

<!-- compile decls -->
```kotlin
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import snippets.support.*

// 详情 ViewModel：沿用第 83 章可变字段 + private set；loadSeq 防换商品后的旧响应
class DetailViewModel : ViewModel() {
    var detail by mutableStateOf<SpuDetailDto?>(null)
        private set
    var pageLoading by mutableStateOf(true)   // 详情首屏 loading
        private set
    var pageError by mutableStateOf<String?>(null)   // 整页错误：只由详情主请求写入
        private set
    // 规格组id -> 规格值id：键即身份，不靠下标对位（见 3.2）
    var selectedSpecValues by mutableStateOf(emptyMap<Long, Long>())
        private set
    var quantity by mutableStateOf(1)
        private set
    var addCartLoading by mutableStateOf(false)
        private set
    var comments by mutableStateOf(emptyList<CommentDto>())
        private set
    var commentLoading by mutableStateOf(false)   // 评论区块 loading：首页与翻页共用
        private set
    var commentHasMore by mutableStateOf(false)   // 首页成功前恒为 false：翻页开关天然关闭
        private set
    var commentError by mutableStateOf<String?>(null)   // 评论区块错误：首页失败与翻页失败共用
        private set
    private var commentPageNo = 1
    private var spuId = 0L
    private var loadSeq = 0

    val specGroups get() = detail?.skuInfo?.specGroupList.orEmpty()
    val skuList get() = detail?.skuInfo?.skuList.orEmpty()
    // SKU 匹配核心：每组都选了值后，已选规格值 id 收成 Set，与 specValueIds 的 Set 比相等——不看顺序
    val matchedSku: SkuDto?
        get() {
            if (specGroups.isEmpty() || selectedSpecValues.size != specGroups.size) return null
            val selected = selectedSpecValues.values.toSet()
            return skuList.firstOrNull { it.specValueIds.toSet() == selected }
        }
    // 详情侧只有库存上限；限购（purchaseLimit）在购物车条目里，第 85 章再约束（见 3.2 数量边界）
    val maxQuantity get() = matchedSku?.stock ?: 1

    fun loadDetail(spuId: Long) {
        this.spuId = spuId
        // 换商品即换世界：所有旧状态清零——包括评论分页四件套与加购 loading
        detail = null; comments = emptyList(); selectedSpecValues = emptyMap(); quantity = 1
        pageLoading = true; pageError = null
        commentPageNo = 1; commentHasMore = false; commentError = null; commentLoading = false
        addCartLoading = false
        val seq = ++loadSeq
        viewModelScope.launch {   // 主请求：详情，决定整页 loading/错误态
            try {
                val d = MallRepo.loadProductDetail(spuId)
                if (seq != loadSeq) return@launch
                detail = d
            } catch (e: CancellationException) {
                throw e   // 取消不是失败：退出页面时在途请求被取消，别写进错误态（第 82 章 3.3）
            } catch (e: Exception) {
                if (seq == loadSeq) pageError = e.message
            } finally {
                if (seq == loadSeq) pageLoading = false
            }
        }
        loadCommentFirstPage(seq)   // 副请求：评论首页，与详情并发、互不阻塞
    }

    // 评论首页：loadDetail 与 retryComments 共用；不 ++loadSeq——重试不是换商品
    private fun loadCommentFirstPage(seq: Int) {
        commentLoading = true
        viewModelScope.launch {
            try {
                // mock 每个商品只有 2—4 条评论，pageSize 取 2 才看得到翻页（教学选择）
                val page = MallRepo.loadComments(spuId, pageNo = 1, pageSize = 2)
                if (seq != loadSeq) return@launch
                comments = page.list; commentPageNo = 1
                commentHasMore = page.hasMore; commentError = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // 区块错误：概览条仍来自 detail，评论区自己显示错误 + 重试，不掀翻整页
                if (seq == loadSeq) commentError = e.message
            } finally {
                if (seq == loadSeq) commentLoading = false
            }
        }
    }

    fun retry() { if (spuId != 0L) loadDetail(spuId) }

    // 评论首页重试：只在首页失败（comments 为空）时由 UI 提供
    fun retryComments() {
        if (spuId == 0L || commentLoading) return
        commentError = null
        loadCommentFirstPage(loadSeq)
    }

    fun loadMoreComments() {
        if (commentLoading || !commentHasMore) return   // 双开关幂等；首页成功前 hasMore=false，开关天然关闭
        commentLoading = true
        val seq = loadSeq   // 只读当前序号：翻页回来时商品已换就整段丢弃
        viewModelScope.launch {
            try {
                val page = MallRepo.loadComments(spuId, pageNo = commentPageNo + 1, pageSize = 2)
                if (seq != loadSeq) return@launch
                comments = comments + page.list
                commentPageNo += 1; commentHasMore = page.hasMore; commentError = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // 下一页失败：列表、页码、hasMore 全不动——再点一次按钮就是重试同一页
                if (seq == loadSeq) commentError = e.message
            } finally {
                if (seq == loadSeq) commentLoading = false
            }
        }
    }

    // 再点一次已选中的值 = 取消该组选择，回到未选齐
    fun selectSpecValue(groupId: Long, valueId: Long) {
        selectedSpecValues = if (selectedSpecValues[groupId] == valueId) {
            selectedSpecValues - groupId
        } else {
            selectedSpecValues + (groupId to valueId)
        }
        if (quantity > maxQuantity) quantity = maxQuantity.coerceAtLeast(1)   // 换 SKU 后收紧数量
    }

    fun incQuantity() { if (quantity < maxQuantity) quantity += 1 }
    fun decQuantity() { if (quantity > 1) quantity -= 1 }

    // 加购反馈分两层：onCartChanged 是全局事实（迟到成功也要通知角标刷新）；
    // onPageFeedback 是本页一次性提示，只对发起代际（seq 对得上）生效。回调在主线程，可安全碰 Compose 状态
    fun addToCart(onCartChanged: () -> Unit, onPageFeedback: (String) -> Unit) {
        val sku = matchedSku ?: return   // 未选齐/无匹配：按钮层已禁用，这里再兜底
        if (addCartLoading || sku.stock <= 0) return
        val seq = loadSeq   // 捕获请求代际：页面反馈与 loading 只属于发起时的商品
        val skuId = sku.id
        val qty = quantity
        addCartLoading = true
        viewModelScope.launch {
            try {
                val ok = MallRepo.addCart(skuId, qty)
                // 契约上 data=false 是业务拒绝（库存不足等）；mock 成功路径固定返回 true，这行检查是对齐契约的防御
                if (ok) onCartChanged()
                if (seq == loadSeq) onPageFeedback(if (ok) "已加入购物车" else "加购失败")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (seq == loadSeq) onPageFeedback(e.message ?: "加购失败")
            } finally {
                if (seq == loadSeq) addCartLoading = false
            }
        }
    }
}
```

几个设计决定的理由：

- **详情与评论并发、错误分级**。详情失败是「页面失败」，整页进错误态；评论失败是「区块失败」，`commentError` 只让评论区显示错误与重试，概览条仍然来自已成功的 `detail`，不掀翻整页。这与第 83 章头部区块「失败保旧数据」是同族策略：**谁有资格掀翻整页，在写第一行代码时就想清楚**。`pageError` 从此只由详情主请求写入——它驱动的是常驻错误 UI，职责单一才好推理。
- **换商品时评论分页状态整体归位**。`loadDetail` 的清零清单里有 `commentPageNo/commentHasMore/commentError/commentLoading` 四件套——少任何一个都会残留旧商品的进度。最典型的漏洞是旧 `hasMore=true` 残留：切到新商品后它的评论首页还没成功（甚至已经失败）时，「查看更多」按钮就可能亮着，一点就直接请求新商品的**第二页**，跳过了首页。归位之后，「**首页成功前不得加载下一页**」由 `hasMore` 的初值天然保证：它只在首页成功路径上被置 true，`loadMoreComments` 的 `!commentHasMore` 开关在此之前一直关着。同理 `addCartLoading` 也归位——它是「当前展示商品的加购按钮」状态，旧商品的迟到响应无权碰它（`seq` 校验挡住），不归位就会把新页面的按钮永久禁用。
- **翻页失败的过程推演（字段级）**。2001、pageSize=2：首页成功 → `comments=[id1,id2]`、`commentPageNo=1`、`hasMore=true`、`commentError=null`。点「查看更多」→ 请求 `pageNo=2` 失败 → catch 只写 `commentError`，**列表、页码、hasMore 原样保留** → 已加载的 2 条评论还在，按钮变成「加载失败，点击重试」。再点 → `loadMoreComments` 请求的仍是 `commentPageNo + 1 = 2`（页码没动，重试的就是失败的那一页）→ 成功 → 追加 `[id5,id6]`、页码=2、`hasMore=false`、错误清空 → 按钮消失。这套「**进度字段只在成功路径前进**」的纪律与第 83 章商品流一致。
- **为什么 Map 是整体替换，不能原地改**。`selectedSpecValues` 声明为只读的 `Map`，`selectSpecValue` 里 `+ (groupId to valueId)` / `- groupId` 都生成**新 Map** 再赋回字段。快照系统追踪的是 `mutableStateOf` 这个「盒子」的写——对盒子赋新值，读过的组合作用域才失效；若改存 `MutableMap` 并原地 `put`，盒子里的对象没换，快照层看不到任何写，选了 chip 页面纹丝不动。这是 Compose 的组合拳：**容器可变（状态对象），内容尽量不可变（值对象）**——想要「内容级」的细粒度通知，就得付「每次复制新集合」的代价，几个规格值的规模下完全值得。
- **`addToCart` 的守卫有三层**：UI 按钮禁用（第一层）、`matchedSku ?: return`（第二层）、`addCartLoading || stock <= 0`（第三层）。UI 的禁用是体验，VM 的兜底是正确性——回调链一长，总有人会绕过按钮直接调方法。`addCartLoading` 的归属要明确：它是**发起代际**的按钮状态，响应回来时 `seq` 对不上就不碰它（新代际已在 `loadDetail` 里归位），迟到响应无权解锁别人的按钮。
- **两层反馈：全局事实与页面提示分开**。`onCartChanged` 表达「购物车变了」这个**全局事实**——即使响应回来时页面已切到别的商品（`seq` 对不上），迟到成功仍要通知外层刷角标（第 87 章接上）；`onPageFeedback` 是**本页一次性提示**，只对发起代际生效——页面已经不在展示那个商品，再弹「已加入购物车」就是误导。为什么不用状态字段装这种提示？`var addCartSuccess by mutableStateOf(false)` 这类布尔「一次性状态」有两个坑：重组可能重复消费同一状态（Snackbar 弹两次），消费后还得手动清零（忘了就永远「成功」）。把加购失败写进 `pageError` 也是同族错误：`pageError` 是**持久错误状态**，驱动常驻错误 UI、重试成功才清；拿它喂 `LaunchedEffect(msg)` 弹 Snackbar，键不变就不重放（连续两次相同失败第二次不弹），配置变化重进组合还可能重放旧值。**持久状态装「页面的现状」，一次性回调装「刚发生的事件」**——消费边界不同，别混用。若某天需要提示也扛过转屏，标准做法是 VM 持有可消费字段（`cartFeedback` + `consume()` 清除方法），UI 展示后立刻清空——那是「状态承载的一次性事件」，消费边界由清除方法显式划定。
- **`catch(Exception)` 之前先放行取消**。退出页面时 `viewModelScope` 会取消在途请求，`CancellationException` 同样会被 `catch(Exception)` 接住——不处理就会把「离开页面」当成失败写进错误态。所有 catch 都先接 `CancellationException` 原样抛出，理由第 82 章 3.3 讲过，这里照做。
- **`retry()` 而不是暴露 `spuId`**。重试语义就是「用同一个 id 再来一次」，把 id 留在 VM 私有字段里，Screen 不需要知道它。`retryComments` 同理，而且它**不自增** `loadSeq`——重试不是换商品；若重试期间恰好换了商品，响应照样被 `seq` 校验丢弃。

### 3.2 SKU 匹配：集合相等，不按顺序

这是本章的核心教学点。`matchedSku` 的判定分两层。

**第一层：未选齐必为 null。** `selectedSpecValues.size != specGroups.size` 直接返回 null。因为 Map 的键是规格组 id、每组只可能存一个值，size 就是「已选组数」；只要还有任何一组没选，就不存在合法 SKU。底部按钮的「请选择规格」禁用态、SKU 区不出现价格预览，都靠这一层。

**第二层：集合相等。** 把已选的规格值 id 与候选 SKU 的 `specValueIds` 都转成 `Set` 再比较。以 2001（单规格组）为例：

```text
selectedSpecValues = { 组1id -> 11 }              已选集合 = { 11 }
SkuDto(51011).specValueIds = [11]        toSet() = { 11 }   → 相等，命中
SkuDto(51012).specValueIds = [12]        toSet() = { 12 }   → 不等，跳过
```

单规格组看不出顺序问题，因为只有一个元素。真实工程的多规格组才是重灾区——假设「规格 [11,12] × 香型 [21,22]」四个 SKU，后端组装 `specValueIds` 时按自己的 join 顺序给了 `[21, 11]`：

```text
已选：{ 规格 -> 11, 香型 -> 21 }                  已选集合 = { 11, 21 }
SkuDto.specValueIds = [21, 11]           toSet() = { 11, 21 }   → 集合相等，命中
若按 List 直接比较：[11, 21] == [21, 11]          → false，漏匹配
```

Kotlin 的 `List ==` 是结构相等：**长度相同且逐下标元素相同**才算相等，顺序敏感。所以按下标 zip、逐位对表、直接 `==` 三种写法，只要后端组装顺序与前端假设不一致就会落空，症状是「明明选了存在的组合却提示无货」。集合语义的适用条件要交代清楚：规格值 id 在商品内不重复（ymq 契约如此）；若后端允许同 id 跨组复用，就得先按组配对再比较。

**联动是自动的，但作用域要说准。** `matchedSku` 是普通 getter、没有缓存：组合中每次读它，都会顺带读取 `selectedSpecValues`（以及 `detail`）。快照系统的规则是：**状态一变，本次组合里读过它的作用域失效重组**。所以选规格后，失效的是「读了 `selectedSpecValues` / `matchedSku` 的那些作用域」。在 3.3 的代码里，`DetailScreen` 自己就读了 `vm.matchedSku`（按钮文案与 enabled 都靠它），所以 DetailScreen 的作用域会重组；`SkuSection` 等子组件收到的参数若没变、类型又可跳过（第 46、47 章的稳定性与强跳过模式），就可以整体跳过。联动效果已经达成，但**「只重组 SKU 区」不是快照系统免费送的保证**——它取决于读取发生在哪个作用域、参数是否变化、编译器的跳过策略。想把失效范围收窄，就把读取下沉：比如让价格预览成为单独的小组件，只在它内部读 `matchedSku`。选齐后展示的价格用 `matchedSku.price`（精确到 SKU），基础信息区的 `basicInfo.price` 是 SPU 级展示价（通常是起价），两者职责不同、并不冲突。

**数量边界只管「本次添加」。** `maxQuantity` 取 `matchedSku?.stock ?: 1`，它的适用范围与不适用范围都要交代：

- **未选齐**：`matchedSku == null`，上限落在兜底的 1。此时数量展示的 1 是**占位**，不是「有效数量 1」——真正的拦截是加购按钮禁用，「＋」也按不动。
- **选齐**：上限 = 该 SKU 库存。`incQuantity` 挡在上限；`selectSpecValue` 换 SKU 后若当前数量超过新库存，立即收紧。
- **库存 0**：预览区显示「库存 0」、按钮「已售罄」、数量停在 1（占位）。mock 六个 SKU 库存全部大于 0，这个分支触发不到，属于防御性代码。
- **库存 1**：数量停在 1，「＋」「－」都禁用。mock 同样没有库存 1 的 SKU，逻辑上由 `quantity < maxQuantity` 与 `quantity > 1` 两个条件自然覆盖。
- **切到低库存 SKU**：见下面的连续推演，这是 mock 里唯一能实际复现「收紧」的路径。
- **它不包含购物车已有数量，也不包含限购**：`quantity <= stock` 只约束**本次**添加；购物车里已有的同一 SKU 数量不在本页视野里。`SkuDto` 没有 `purchaseLimit` 字段——那是购物车条目（`CartItemDto.purchaseLimit`）才有的数据，详情页拿不到也不该编造；mock 的 `cart/add` 对已有条目**直接累加、没有任何超量拒绝**，不能指望服务端兜底。真实工程的完整校验要么让详情接口直接带出限购，要么加购前调校验接口（mock 里就有 `POST app-api/shop-product/spu/checkCartQuantity`，按 `stock - 已加数量` 返回可加余量）。第 85 章拿到 `purchaseLimit` 后，购物车步进器把单条上限夹到 `min(stock, purchaseLimit)`——「本次还能加多少 = min(库存, 限购) − 购物车已有」，要等购物车数据在场才算得清。

**一个连续推演（2001，mock 可复现）：**

1. 初始：未选任何规格。SKU 区无价格预览、加购按钮「请选择规格」禁用、数量「＋」按不动（占位 1）。
2. 点「24包整箱」（51011）：预览出现 ¥49.90/库存 78，按钮变「加入购物车」，数量最多可加到 78。
3. 数量加到 37。
4. 切「12包尝鲜」（51012）：预览变 ¥29.90/库存 36——37 超过新上限，`selectSpecValue` 立即把数量收紧为 36。这就是 clamp 的复现路径：**上限随 SKU 变小，数量跟着收紧**。
5. 再点一次「12包尝鲜」取消选择：预览隐藏、数量回 1、按钮回「请选择规格」禁用。

推演全程，顶部 SPU 轮播图与基础信息区的 `¥49.90/箱` **不变**——那是 `basicInfo` 的 SPU 级内容；跟着 SKU 走的只有 SKU 预览区（价格/库存/规格名）与数量上限。还有一个数据边界：mock 里 51011/51012 的 `imgUrl` 是**同一个 URL**，切 SKU 看不到换图——图片联动的验收只能看「价格与库存文案变化」，不能承诺视觉换图；真实工程多 SKU 各自带图，才有换图效果。

**一个已知的教学简化**：ymq 版本进页面会用第一个有库存的 SKU 自动初始化选择（`firstInStockSku`），我们刻意从「未选」开始，让「选齐才可加购」的判定路径可见。mock 里 2003、2004、2005、2011 都是单规格值单 SKU，用户必须点一下唯一的 chip 才能加购——把它改成「单规格值自动选中」是很好的练习（提示：在 `loadDetail` 成功后判断 `specGroups.all { it.specValueList.size == 1 }`）。

### 3.3 页面与子组件

子组件全是「数据 + 回调」的哑组件，图片统一用 Coil 的 `AsyncImage`（[官方仓库](https://github.com/coil-kt/coil)，版本为本仓库基线快照）；`viewModel()` 取实例见 [ViewModel 文档](https://developer.android.com/topic/libraries/architecture/viewmodel)。四个子组件的分工：`DetailBanner`（轮播 + 页码角标）、`SkuSection`（规格选择 + 数量步进 + 命中预览）、`CommentItem`（单条评论）、`DetailScreen` 自己组装三态与底部操作栏。

`detailHtml` 是教学取舍：Compose 的 `Text` 不渲染 HTML，把 HTML 字符串直接塞 `Text` 得到的是一屏标签源码；真实工程用 WebView 或富文本组件渲染（还要处理宽度自适应与图片占位）。本章 `detailType` 恰为 `images`、`detailHtml` 为空串，只铺图即可完整展示。

组件的来路与授权要分清，别把链接用错对象：`FilterChip` 是 material3 组件，版本状态看 [material3 releases](https://developer.android.com/jetpack/androidx/releases/material3)（本仓库基线内为稳定 API）；`HorizontalPager`/`rememberPagerState` 属 `androidx.compose.foundation.pager`，**不是 material3**——它们自 Foundation 1.7 起已稳定，版本来龙去脉见第 32 章 3.3 与[官方 Pager 指南](https://developer.android.com/develop/ui/compose/layouts/pager)；`TopAppBar` 在本仓库基线仍是实验 API，练习工程要自写 `@OptIn(ExperimentalMaterial3Api::class)`（第 6 章先例）；基础 `FlowRow` 在基线内也已稳定（第 8A 章）。另有一个工程差异容易踩：仓库的片段生成工程会为所有片段统一加 `@file:OptIn(...)`，只为让节选片段能编译——**它不代表正文声明过 opt-in**；你自己的练习工程没有这层兜底，该写的 `@OptIn` 一处都不能少（审计命令见 README「代码示例编译校验」）。

<!-- compile decls -->
```kotlin
import androidx.compose.foundation.pager.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import snippets.support.*

// 片段自包含：对齐第 83 章 3.3 的先例，重新声明 3.1 的 DetailViewModel（字段齐全，方法体从略）
class DetailViewModel : ViewModel() {
    var detail by mutableStateOf<SpuDetailDto?>(null)
    var pageLoading by mutableStateOf(true)
    var pageError by mutableStateOf<String?>(null)
    var selectedSpecValues by mutableStateOf(emptyMap<Long, Long>())
    var quantity by mutableStateOf(1)
    var addCartLoading by mutableStateOf(false)
    var comments by mutableStateOf(emptyList<CommentDto>())
    var commentLoading by mutableStateOf(false)
    var commentHasMore by mutableStateOf(false)
    var commentError by mutableStateOf<String?>(null)
    val specGroups get() = detail?.skuInfo?.specGroupList.orEmpty()
    val matchedSku: SkuDto? get() = null   // 匹配逻辑见 3.1
    fun loadDetail(spuId: Long) {}
    fun retry() {}
    fun retryComments() {}
    fun loadMoreComments() {}
    fun selectSpecValue(groupId: Long, valueId: Long) {}
    fun incQuantity() {}
    fun decQuantity() {}
    fun addToCart(onCartChanged: () -> Unit, onPageFeedback: (String) -> Unit) {}
}

// 顶部轮播：角标读 pagerState.currentPage——翻页失效的是 DetailBanner 内读取它的作用域，
// 页内容 lambda 没读它，不参与这次失效；Pager 与页内容的跳过取决于参数与稳定性（第 46、47 章）
@Composable
fun DetailBanner(imgs: List<String>, modifier: Modifier = Modifier) {
    if (imgs.isEmpty()) return
    val pagerState = rememberPagerState { imgs.size }
    Box(modifier) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxWidth().aspectRatio(1f)) { page ->
            AsyncImage(model = imgs[page], contentDescription = "商品图${page + 1}",
                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Text("${pagerState.currentPage + 1}/${imgs.size}", color = Color.White, style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp)
                .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(10.dp)).padding(horizontal = 8.dp, vertical = 2.dp))
    }
}

// SKU 选择区：每组一行 FilterChip；状态由父持有（状态提升），chip 自己无本地状态
@Composable
fun SkuSection(
    groups: List<SpecGroupDto>,
    selectedSpecValues: Map<Long, Long>,
    sku: SkuDto?,
    quantity: Int,
    onSelect: (Long, Long) -> Unit,
    onInc: () -> Unit,
    onDec: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        sku?.let {   // 未选齐时整块不出现；选齐后这里展示的就是命中 SKU 的价格/图/库存
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(model = it.imgUrl, contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(6.dp)))
                Column {
                    Text("¥${it.price}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.titleMedium)
                    Text("已选 ${it.name} · 库存 ${it.stock}", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        groups.forEach { group ->
            Text(group.name, style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                group.specValueList.forEach { v ->
                    // 选中态完全由父的 Map 推导：再点一次回传同 id，VM 负责取消（见 3.1 selectSpecValue）
                    FilterChip(selected = selectedSpecValues[group.id] == v.id,
                        onClick = { onSelect(group.id, v.id) }, label = { Text(v.name) })
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("数量", style = MaterialTheme.typography.titleSmall)
            OutlinedButton(onClick = onDec, enabled = quantity > 1) { Text("－") }
            Text("$quantity", style = MaterialTheme.typography.titleMedium)
            OutlinedButton(onClick = onInc, enabled = quantity < (sku?.stock ?: 1)) { Text("＋") }
        }
    }
}

// 评论条目：匿名昵称、星级（rating 为 1—5 的 Int）、带图评价、商家回复四个小分支
@Composable
fun CommentItem(c: CommentDto) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(model = c.userAvatar, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.size(32.dp).clip(CircleShape))
            Column {
                Text(if (c.anonymous) "匿名用户" else c.userNickname, style = MaterialTheme.typography.titleSmall)
                Row {
                    repeat(5) { i ->
                        Icon(Icons.Filled.Star, contentDescription = null, modifier = Modifier.size(14.dp),
                            tint = if (i < c.rating) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
        Text(c.content, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
        // 带图评价：imgUrls 是 List<String>，这里只铺首图示意；多图九宫格留作练习
        c.imgUrls.firstOrNull()?.let { url ->
            AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(3f).clip(RoundedCornerShape(6.dp)))
        }
        // 商家回复：replyContent 是 String?，还可能是空串——isNullOrBlank 双保险
        if (c.replyStatus && !c.replyContent.isNullOrBlank()) {
            Text("商家回复：${c.replyContent}", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp)).padding(8.dp))
        }
    }
}

// 页面组装：bottomBar 放加购/购物车；内容区按 loading/错误/内容三态分派
@Composable
fun DetailScreen(vm: DetailViewModel, onBack: () -> Unit, onOpenCart: () -> Unit, onCartChanged: () -> Unit) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val sku = vm.matchedSku
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("商品详情") }, navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") }
            })
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            Surface(shadowElevation = 8.dp) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    IconButton(onClick = onOpenCart) { Icon(Icons.Filled.ShoppingCart, contentDescription = "购物车") }
                    Button(
                        onClick = {
                            vm.addToCart(
                                // 全局事实：购物车变了，外层刷角标（第 85、87 章接上）
                                onCartChanged = onCartChanged,
                                // 本页一次性提示：成功/失败都由 VM 在对应代际回调
                                onPageFeedback = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } },
                            )
                        },
                        enabled = sku != null && sku.stock > 0 && !vm.addCartLoading, modifier = Modifier.weight(1f),
                    ) {
                        if (vm.addCartLoading) CircularProgressIndicator(Modifier.size(18.dp))
                        else Text(when {
                            // 选齐但无匹配 SKU：mock 组合齐全不会出现，真实工程要防（见 3.2）
                            sku == null && vm.selectedSpecValues.size < vm.specGroups.size -> "请选择规格"
                            sku == null -> "该组合暂无"
                            // 前两个分支已排除 null，这里 sku 已被 smart-cast 为非空
                            sku.stock <= 0 -> "已售罄"
                            else -> "加入购物车"
                        })
                    }
                    OutlinedButton(onClick = onOpenCart) { Text("去购物车") }
                }
            }
        },
    ) { padding ->
        when {
            vm.pageLoading && vm.detail == null ->
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            vm.pageError != null && vm.detail == null -> Column(
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(vm.pageError ?: "加载失败", color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(8.dp))
                Button(onClick = vm::retry) { Text("重试") }
            }
            else -> vm.detail?.let { d ->
                val basic = d.basicInfo
                LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                    item(key = "banner") { DetailBanner(basic?.imgsUrl.orEmpty()) }
                    item(key = "basic") {
                        Column(Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("¥${basic?.price}/${basic?.unit}", color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.headlineSmall)
                                // toBigDecimalOrNull 定义在非空 String 上：可空链要用 ?. 串起来，null 直接短路
                                val origin = basic?.originPrice?.toBigDecimalOrNull()
                                val price = basic?.price?.toBigDecimalOrNull()
                                if (origin != null && price != null && origin > price) {
                                    // origin/price 非空意味着可空链完整执行，basic 已被 smart-cast 为非空
                                    Text("¥${basic.originPrice}", color = MaterialTheme.colorScheme.outline,
                                        style = MaterialTheme.typography.labelMedium, textDecoration = TextDecoration.LineThrough)
                                }
                            }
                            Text(basic?.name ?: "", style = MaterialTheme.typography.titleMedium)
                            if (!basic?.subTitle.isNullOrBlank()) {   // 成立时 basic 也被 smart-cast 为非空
                                Text(basic.subTitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (d.groupBuyInfo != null) {   // 拼团信息只判空打标签，不展开字段
                                    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(4.dp)) {
                                        Text(" 拼团 ", color = MaterialTheme.colorScheme.onErrorContainer,
                                            style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                                basic?.tags?.forEach { tag ->
                                    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(4.dp)) {
                                        Text(" $tag ", color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                            if (!basic?.pointReward.isNullOrBlank()) {
                                Text("购买可得 ${basic.pointReward} 积分", style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.tertiary)
                            }
                            Text("已售 ${basic?.salesVolume ?: 0} · ${basic?.deliveryTime} · ${basic?.isFreeShipping}",
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    item(key = "sku") {
                        SkuSection(vm.specGroups, vm.selectedSpecValues, vm.matchedSku, vm.quantity,
                            onSelect = vm::selectSpecValue, onInc = vm::incQuantity, onDec = vm::decQuantity)
                    }
                    item(key = "reviewBar") { d.reviewInfo?.let { r ->   // 概览条只展示评论数/好评率，列表数据一律走 comment/page
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("商品评价(${r.reviewCount})", style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.weight(1f))
                            Text("好评率 ${r.goodRate}%", color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.labelMedium)
                        }
                    } }
                    // 评论四态：首屏 loading / 成功为空 / 首页失败 / 已有数据（含下一页失败反馈）
                    when {
                        vm.commentLoading && vm.comments.isEmpty() -> item(key = "commentsLoading") {
                            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(Modifier.size(20.dp))
                            }
                        }
                        vm.commentError != null && vm.comments.isEmpty() -> item(key = "commentsError") {
                            Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(vm.commentError ?: "评论加载失败", color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall)
                                Spacer(Modifier.height(8.dp))
                                Button(onClick = vm::retryComments) { Text("重试") }
                            }
                        }
                        vm.comments.isEmpty() -> item(key = "commentsEmpty") {
                            Text("暂无评价", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.fillMaxWidth().padding(16.dp))
                        }
                        else -> {
                            items(vm.comments, key = { it.id }) { CommentItem(it) }
                            if (vm.commentHasMore) item(key = "moreComments") {
                                // 按钮式翻页：mock 评论少，比触底加载直观；触底方案见第 83 章 3.4，键要含 comments.size
                                Column(Modifier.fillMaxWidth().padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    vm.commentError?.let { err ->   // 下一页失败：提示常驻，按钮兼作重试
                                        Text(err, color = MaterialTheme.colorScheme.error,
                                            style = MaterialTheme.typography.labelSmall)
                                    }
                                    OutlinedButton(onClick = vm::loadMoreComments, enabled = !vm.commentLoading) {
                                        if (vm.commentLoading) CircularProgressIndicator(Modifier.size(16.dp))
                                        else Text(if (vm.commentError != null) "加载失败，点击重试" else "查看更多评价")
                                    }
                                }
                            }
                        }
                    }
                    item(key = "detailImages") {   // detailHtml 不渲染（教学取舍），只铺 detailImages
                        Text("商品详情", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(12.dp))
                        d.detailInfo?.detailImages?.forEach { url ->
                            AsyncImage(model = url, contentDescription = null,
                                contentScale = ContentScale.FillWidth, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }
    }
}

// 用法包装：spuId 来自导航参数；viewModel() 绑定当前 backstack entry
@Composable
fun MallDetailEntry(spuId: Long, onBack: () -> Unit, onOpenCart: () -> Unit, onCartChanged: () -> Unit) {
    val vm: DetailViewModel = viewModel()
    // 幂等守卫：转屏后 VM 存活、detail 还在，LaunchedEffect 会重新执行，不能重复发请求
    LaunchedEffect(spuId) {
        if (vm.detail?.basicInfo?.id != spuId) vm.loadDetail(spuId)
    }
    DetailScreen(vm, onBack, onOpenCart, onCartChanged)
}
```

实现细节逐条说：

- **三态分派的边界是 `detail == null`**。`pageLoading && detail == null` 才整页转圈——注意 `loadDetail` 会先把 `detail` 清空，所以重试/换商品必然回到整页 loading，这是刻意选择（详情页旧数据属于另一个商品，保留反而误导；第 83 章首页「保旧数据」的前提是数据同源）。`pageError != null && detail == null` 才整页错误；两者都不满足就进内容分支。`pageError` 只由详情主请求写入，`detail != null` 时它必然为空，不存在「内容在屏又叠一个整页错误」的分支。
- **评论四态用两个维度区分**：`comments` 空/非空 × `commentError` 有/无，再加 `commentLoading` 兜住请求中的空窗。首屏 loading（转圈）、成功为空（「暂无评价」）、首页失败（错误 + 重试按钮，**不是**「暂无评价」——用户需要知道这是「还没拿到」而不是「没有」）、下一页失败（列表保留 + 常驻错误提示 + 按钮变「加载失败，点击重试」）。`commentError` 兼了两份职：首页失败驱动区块错误态、翻页失败驱动按钮文案——它们都是**常驻 UI**，由持久状态驱动是正确的；这和加购反馈（一次性 Snackbar，走回调）正好构成「持久状态 vs 一次事件」的两面。列表数据一律来自 `comment/page`，预览评论 `reviewInfo.list` 不拿来补位。
- **`SkuSection` 是状态提升的教科书案例**（回看第 24 章）：`FilterChip` 的 `selected` 完全由父传下的 Map 推导，chip 自己不存任何状态。好处立竿见影——「再点一次取消」不需要组件配合，`onClick` 照样回传 `(groupId, valueId)`，由 VM 决定是选中还是取消；组件复用时也没有隐藏状态要清理。
- **加购按钮的文案矩阵**：未选齐→「请选择规格」；选齐但无匹配→「该组合暂无」（mock 组合齐全触发不到，真实工程是硬需求）；有 SKU 无库存→「已售罄」；正常→「加入购物车」。`enabled` 只看 `sku != null && sku.stock > 0 && !vm.addCartLoading`——少了 `addCartLoading` 会重复提交，少了库存判断会把必败请求发给服务端。
- **两层反馈在 onClick 的接法**：`onCartChanged` 原样上抛给外层（导航接线里它通向角标刷新，第 85、87 章），`onPageFeedback` 用页面的 `scope` 弹 Snackbar。回调从 VM 的协程里发起，`viewModelScope` 默认调度在主线程，碰 Compose 状态和 `SnackbarHostState` 都安全（回看第 29 章）。要留意一个转屏边界：回调捕获的是**发起时的组合环境**——加购在途时转屏，角标回调（外层提供，引用导航级对象）不受影响，本页 Snackbar 却可能因旧 `scope` 已取消而静默丢失；按钮的 loading 状态不受影响（`addCartLoading` 是 VM 字段）。要彻底修复，就把提示改成 3.1 说的可消费字段方案，由转屏后的新组合消费。
- **评论翻页用按钮而不是触底加载**：mock 每个商品只有 2—4 条评论，触底判定在小列表上展示不出效果；「查看更多评价」按钮把「点击→loading→追加/重试/消失」的循环演得明明白白。要把评论区改成触底加载，第 83 章 3.4 的 `derivedStateOf + LaunchedEffect` 原样可用，记得键里加 `comments.size`。
- **`LazyColumn` 的 key 限定的是身份**：固定区块用字符串 key（`"banner"`、`"sku"`……），评论用 `it.id`。key 把条目身份钉住——翻页追加时已有条目仍关联同一份数据，不做无谓的销毁重建，追加导致图片重新加载的闪烁根源被「身份稳定」排除；但 key **不承诺零重组**：数据内容变化该重组还是重组，子组件能否跳过取决于参数与稳定性（第 17、46 章）。
- **图文明细用 `ContentScale.FillWidth`**：详情长图只约束宽度、高度按比例展开，这是电商详情页的标准做法；`detailImages` 用 `forEach` 铺在 item 内部即可，图片张数少不需要单独的懒条目。

### 3.4 导航接入：spuId 从哪来

详情页靠路由参数携带 `spuId`（模式见[导航文档](https://developer.android.com/develop/ui/compose/navigation)，语法回看第 4 章）。下面的示意给出**可接通的最小完整接线**：`startDestination` 有对应目的地、「cart」有占位目的地、首页入口的 `onOpenProduct` 实际导航：

```kotlin
// 说明性示意（不参与编译校验）：首页 → 详情 → 购物车的最小可运行导航接线
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

@Composable
fun MallApp() {
    val navController = rememberNavController()
    NavHost(navController, startDestination = "home") {
        composable("home") {
            // 第 83 章的首页入口：商品卡点击 → 携 spuId 进详情
            MallHomeEntry(
                onOpenProduct = { id -> navController.navigate("detail/$id") },
                onOpenCart = { navController.navigate("cart") },
            )
        }
        composable(
            route = "detail/{spuId}",
            arguments = listOf(navArgument("spuId") { type = NavType.LongType }),
        ) { backStackEntry ->
            val spuId = backStackEntry.arguments?.getLong("spuId") ?: 0L
            if (spuId > 0L) {
                MallDetailEntry(
                    spuId = spuId,
                    onBack = { navController.popBackStack() },
                    onOpenCart = { navController.navigate("cart") },
                    onCartChanged = { /* 通知首页/主框架刷新角标：第 85、87 章接上 */ },
                )
            } else {
                // 参数异常兜底：不带着非法 id 发请求，提示并可返回
                Text("商品参数异常")
                BackHandler { navController.popBackStack() }
            }
        }
        composable("cart") {
            // 占位目的地：让 onOpenCart 有处可去；第 85 章换成 MallCartEntry，第 87 章挂进主框架
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("购物车（第 85 章）") }
        }
    }
}
```

要点：

- **`startDestination` 与每个 `navigate` 的目标都必须有对应目的地**。`NavHost` 启动时找不到 `startDestination`、或导航到一个未注册的 route，都会直接抛异常崩掉——示意代码里每个 route 都要真实存在，「cart」先给占位目的地（第 85 章替换为真正的购物车页，第 87 章再整合进底部导航主框架），入口才是可接通的。
- **`spuId` 是商品（SPU）id，不是 SKU id**。2001 是 SPU，51011/51012 是它下面的 SKU——详情页先拿 SPU 全量数据，买哪个 SKU 由页内选择决定，所以路由只带 SPU id。ymq 版本还支持营销链接直达某个 SKU（外部传入 `specifiedSkuId` 自动补全选择），那是另一条入口，本章不展开。
- **参数异常要有说明地兜底**。路由声明了 Long 型路径参数，正常导航一定带值；`?: 0L` 兜的是「手工拼路由漏参」这类异常。拿到 0 不带说明地直接发请求，mock 会返回「商品不存在」——看似也有兜底，实际是把可预见的参数错误当成运行时故障演示，错误文案还会误导排查；入口处拦下、给出提示并允许返回，比打一枪再显示错误页更诚实。
- **`viewModel()` 的作用域是当前 backstack entry**。`LocalViewModelStoreOwner` 在 NavHost 的每个目的地内被替换为对应的 `NavBackStackEntry`，所以 `MallDetailEntry` 里的 `viewModel()` 每个 entry 各取各的 `DetailViewModel`：先看 2001 再看 2002 是两个 entry、两个 vm、互不串台；按返回键销毁 entry，vm 随之清理（`onCleared`），不会泄漏。也因此，`loadDetail` 的「换商品」路径（同一个 vm 先后加载不同 spuId）在当前接线下不常触发——但方法契约必须自洽：重试、未来的单 entry 详情页、或调试时直接连续调用 `vm.loadDetail(2001)`/`vm.loadDetail(2002)`，都会走这条路，3.1 的状态归位与 `loadSeq` 校验为它兜底。
- **`LaunchedEffect(spuId)` 必须幂等**。转屏（配置变化）会重建整个组合，`LaunchedEffect` 重新执行——但 VM 活着、`detail` 还在，无条件 `loadDetail` 就会白白发一次请求、页面还闪一下 loading。Entry 里的守卫 `if (vm.detail?.basicInfo?.id != spuId)` 只在「没加载过这个商品」时才发请求。这个守卫模式和第 23 章的 `rememberSaveable` 是互补的：UI 位置类状态（`PagerState` 内部自带保存）交给框架，业务数据交给 VM。
- **`onCartChanged` 是一条预留的管道**。本章外层只放一个注释占位；第 85 章购物车页会依赖「详情页改过购物车」这个事实，第 87 章主框架的角标靠它刷新。回调穿过导航边界传进来，就是为了让数据变更的通知不依赖全局单例——教程阶段够用，真实工程可换成事件总线或共享 ViewModel。加购在途时按返回键离开详情页，`viewModelScope` 取消在途请求，回调不会触发——但请求可能已发出、服务端可能已记账，角标要等下次刷新才对齐；「离开页面也保证送达与通知」需要应用级作用域（第 87 章的全局角标 VM 主动重拉正是这个思路）。

## 4. 实际执行过程

1. **准备与契约验证**。启动 mock 后先重置，保证起点一致：

   ```bash
   curl http://localhost:3001/__reset
   ```

   注意重置会清掉你之前练习写入的购物车改动（加购、改数量都会还原）。然后验证契约——**SKU 在 `data.skuInfo.skuList`，不是 `data.skuList`**：

   ```bash
   curl -X POST http://localhost:3001/app-api/shop-product/spu/detail \
        -H "Content-Type: application/json" -d '{"id":2001}'
   ```

   返回 `code=0`：`data.basicInfo` 价格 49.90/划线 59.90；`data.skuInfo.skuList` 两个 SKU：51011「24包整箱」（¥49.90/库存 78/`specValueIds=[11]`）、51012「12包尝鲜」（¥29.90/库存 36/`[12]`，两个 SKU 的 `imgUrl` 是同一个 URL）；`data.reviewInfo` 里 `reviewCount=3`、`goodRate=98.5`、`list` 只有一条预览；`data.detailInfo.detailType="images"`、`detailHtml=""`。把 id 换成 2006，返回 `{"code":1,"data":null,"msg":"商品不存在"}`——业务失败现成演示。再验评论两页：

   ```bash
   curl -X POST http://localhost:3001/app-api/shop-product/comment/page \
        -H "Content-Type: application/json" -d '{"spuId":2001,"pageNo":1,"pageSize":2}'
   curl -X POST http://localhost:3001/app-api/shop-product/comment/page \
        -H "Content-Type: application/json" -d '{"spuId":2001,"pageNo":2,"pageSize":2}'
   ```

   第一页返回 ids `[1,2]`、`total=4`、`hasMore=true`；第二页返回 `[5,6]`、`hasMore=false`。概览条的 `reviewCount=3` 与列表 `total=4` 对不上是 mock 数据自身不严格对齐——恰好证明两个接口独立，谁也别当谁的数据源。
2. **App 打开 2001**（首页商品卡点击）：轮播 2 张图可横滑、右下角角标 `1/2` 随翻页变；基础信息区 `¥49.90/箱` + 划线 `¥59.90`、标签「次日达、新人价」、「购买可得 620 积分」；页面尾部 2 张详情长图。
3. **SKU 交互（按 3.2 的连续推演走一遍）**：初始无价格预览、按钮「请选择规格」禁用、「＋」按不动；点「24包整箱」→ 预览 ¥49.90/库存 78、按钮解禁；数量加到 37；切「12包尝鲜」→ 预览 ¥29.90/库存 36，数量自动收紧为 36；再点「12包尝鲜」取消 → 预览隐藏、数量回 1、按钮回禁用。全程顶部轮播与 `¥49.90/箱` 不变；两 SKU 图同 URL，看不到换图。单规格商品（如 2004 大米）只有一个 chip，也必须点一下才能加购——3.2 末尾的教学简化。
4. **拼团标签**：从首页打开 2002「山茶油洗衣液」，`groupBuyInfo` 非空，基础信息区出现红色「拼团」标签；2001 没有该标签。
5. **评论分页**：2001 概览条「商品评价(3) · 好评率 98.5%」+ 首屏 2 条；第一条「团团妈」五星、带图、下方灰底「商家回复：感谢支持」；第二条显示「匿名用户」（`anonymous=true`）。点「查看更多评价」→ 按钮转圈 → 追加 2 条 → 按钮消失（`hasMore=false`）。
6. **评论失败态**：首屏成功后**停掉 mock**，再点「查看更多评价」→ 出现错误提示、已加载的 2 条保留、按钮变「加载失败，点击重试」；重启 mock 再点 → 追加成功（重试请求的仍是第二页）。评论**首页**失败在 mock 上无法单独制造（停 mock 时详情会先失败），要实际看到「错误 + 重试」分支，最简单的办法是临时把 VM 里 `loadComments` 一行改成 `throw IllegalStateException("评论加载失败")`——验证区块错误态不掀整页、重试按钮工作，验完改回。
7. **加购闭环**：先记录当前值（不要假设固定数字——取决于你是否刚重置、之前是否加过）：

   ```bash
   curl http://localhost:3001/app-api/shop-trade/cart/count
   curl http://localhost:3001/app-api/shop-trade/cart/list
   ```

   App 里选 24包整箱、数量 1，点「加入购物车」→ 按钮转圈 → Snackbar「已加入购物车」。再查一遍：`cart/count` 应比刚才 **+1**，`cart/list` 里 `skuId=51011` 的条目（预置 id 91001）`quantity` **+1**（刚 reset 过时是 2→3、10→11）。增量对账，不硬编码基数；如果你此前用 curl 直接调过 `cart/add` 或多加了数量，基数就变了——curl 加购与 App 加购会**累加到同一条目**，别把两次增量当成一次。
8. **失败态**：从首页点推荐流里的 2006—2010、2012—2014 任一商品 → 整页「商品不存在」+ 重试按钮；对 2006 点重试仍失败（业务态持续存在，不是网络抖动），返回再进 2001 正常。停掉 mock 造网络错误态：出现网络连接错误信息——文案不承诺固定字符串，`MallRepo` 优先保留底层异常的 `message`（如连接失败详情），兜底文案「网络异常，请稍后重试」只在 `message` 为空时出现。停 mock 期间连点两次加购，相同文案的 Snackbar 应弹两次（每次失败都回调一次）。
9. **切商品的状态归位**：当前导航接线下每个详情 entry 各有 VM，跨商品串台不会发生；验证 `loadDetail` 的归位契约可用临时接线（把首页 `onOpenProduct` 固定改成先 `2001` 后 `2002` 的两次 `loadDetail`，或直接在调试里连续调用）。配合第 6 步的临时 `throw` 让 2002 的评论首页失败：页面不得出现「查看更多评价」按钮——旧商品的 `hasMore` 已被 `loadDetail` 归位。
10. **转屏**：详情在屏时旋转模拟器，数据保留、不重新出现整页 loading（Entry 的幂等守卫生效，VM 存活）；轮播页码也停在原页（`PagerState` 自带保存）；加购失败提示弹出后转屏，旧错误不重弹；加购在途转屏，按钮转圈状态由 VM 恢复、响应回来后收起（本页 Snackbar 可能因回调捕获旧作用域而丢失，正文 3.3 有说明）。
11. 验证完全部后 `curl http://localhost:3001/__reset` 还原 mock 数据——注意这会清除本轮练习写入的购物车变更。

## 5. Flutter/ymq 对照

对照 ymq 的 `ProductDetailController`（GetX，`product_detail_controller.dart` 及其 `part` 扩展文件）：

- **选择状态建模与通知机制**：它用「按规格组下标的 `List<int?>`」平行数组存选择，还要 `_specValueGroupIndex` 反查表把规格值 id 映射回组下标——因为 chip 的点击回传的是值 id，得先反查才知道该写进哪个下标。我们的 `Map<组id, 值id>` 键即身份，反查表直接省掉。通知方式上，按 ymq 源码，SKU 扩展里选择后调的是**无参数** `update()`——广播给该控制器下**所有** `GetBuilder` 子树 rebuild，这和第 83 章首页控制器用的 `update([id])`（只通知挂了对应 id 的子树）是两种通知；SKU 弹窗这类「一变多处」的局部 UI，ymq 干脆选了全量广播省心。我们这边靠快照系统自动失效——**职责对应**（选择变了，相关 UI 跟着变），机制不同（手动广播 vs 自动追踪），粒度取舍也不同（它选粗粒度全刷，我们的失效范围跟着读取作用域走）。
- **匹配语义**：它的 `_syncSelectedSku` 用 `selectedIds.every(specIds.contains)`——「已选的每个值都在候选里」的**包含判断**，同样不依赖顺序，但比集合相等**弱**：候选比已选多元素也会命中。反例：已选 `{11, 21}`、候选 `specValueIds=[11,21,31]`（后端数据冗余或脏数据）——包含判断命中这个错误 SKU，集合相等不命中。在「已选齐、且每个 SKU 的 `specValueIds` 恰好每组一个值」的合法数据前提下，两边结果一致。我们用 `toSet()` 相等是有意的从严：宁可显示「该组合暂无」也不误命中脏数据，配合前置的 size 判定刚好构成完整防御。
- **默认选中**：它的 `initSkuSelection` 会取 `firstInStockSku`（或外部带入的 `specifiedSkuId`，比如剪贴板营销链接直达某 SKU）自动补全所有规格组；我们从「未选」开始，把判定路径留给教学（3.2 末尾给了自动选中的练习）。
- **规格值可达性**：它的 `isSpecValueEnabled` 会判断「该值与其他组已选值组合后是否存在有库存的 SKU」，不存在就灰置不可点；我们的 chip 全部可点，无货组合要选齐后才在按钮上体现——真实工程要做的可达性过滤，是很好的进阶练习。
- **数量边界**：它用 `_isPurchaseCountOverStock` 判断 + `_showMaxCountToast` 提示；我们在 `incQuantity/selectSpecValue` 里直接 clamp（静默收紧，教学从简）。两边对详情页的约束都止于库存——限购同样要等购物车/校验接口的数据。
- **图片联动**：它维护「最近交互规格值」缓存 `_lastInteractedSpecValueId` 与多级回退链，让弹窗顶部图在两个规格组来回切换时跟随用户最后一次点击；我们只认 `matchedSku?.imgUrl`，未命中不换图——简化取舍，多规格带图时它的方案更贴近真实体验。
- **首屏并发**：它的 `requestInitialData` 用 `Future.wait([详情, 评价列表, 购物车角标])` 汇合后才收 loading；角标请求单独 try/catch 静默降级（和我们评论「区块错误态、不掀整页」同一思路——它连区块错误 UI 都不做，更简）。我们 `loadDetail` 里两个 `launch` 天然并发，收尾只由详情请求的 `finally` 决定整页态。
- **加购入口**：它的 `buyProduct` 先校验 `isSkuSelectionComplete`，不完整弹 toast「请选择完整的规格」；我们直接禁用按钮（文案矩阵见 3.3），加 VM 内 `matchedSku ?: return` 兜底——UI 拦截是体验，VM 拦截是正确性。

## 6. 常见错误

- **`specValueIds` 按顺序匹配**：`List ==`、按下标 zip、逐位对表，只要后端组装顺序与前端假设不一致就会 miss，症状是「选了存在的组合却提示无货」；必须两边 `toSet()` 比集合相等。单规格组的 mock 数据测不出这个 bug；多规格组一旦后端顺序与前端假设不一致，上线即漏匹配。
- **未选齐就加购**：`matchedSku` 为 null 时强解引用价格/库存是 NPE；把 0 当 skuId 发出去是必然业务失败。按钮禁用 + VM 兜底两层都要，缺一层就赌「所有调用方都记得检查」。
- **评论首屏拼接两个接口**：先拼 `reviewInfo.list` 再拼 `comment/page` 第一页，同一条评论出现两次——预览与分页第一页数据重叠。`reviewInfo` 只做概览条，列表一律走 `comment/page`。
- **切商品不防旧响应、不归位旧状态**：2001 的迟到详情/评论覆盖 2002 的页面，`commentPageNo` 停在 2001 的页码上继续翻，旧 `hasMore=true` 让新商品的翻页按钮直接跳第二页。`loadSeq` 发前自增、回后校验；`loadDetail` 把评论分页四件套与 `addCartLoading` 一并归位。
- **加购失败写进整页错误字段**：`pageError` 是「页面加载失败」的持久状态，拿它驱动 Snackbar，键不变就不重放（连续两次相同失败第二次不弹）、配置变化还可能重放旧值。页面加载错误走错误态字段，一次操作的成功/失败反馈走回调（或带清除方法的可消费字段）——**持久状态装现状，一次性回调装事件**。
- **`detailHtml` 直接塞 `Text`**：得到一屏 HTML 标签源码，既不是图也不是文；真实工程用 WebView 或富文本组件渲染，本章只铺 `detailImages`。
- **`LaunchedEffect(spuId)` 无条件加载**：转屏后组合重建、副作用重新执行，VM 里数据还好好的却重发请求、闪一次 loading；入口副作用要幂等（已加载同一 id 就跳过）。
- **把 `matchedSku` 缓存成字段**：`selectSpecValue` 忘了同步更新缓存，价格/库存联动就失灵——联动依赖「每次读都现算」。getter 每次重算的开销可忽略（skuList 只有几个元素），不要手动缓存。
- **`selectedSpecValues` 改存 `MutableMap` 原地 put**：快照系统只认「状态盒子的写」，原地改内容它看不见，UI 不刷新；必须 `+`/`-` 生成新 Map 赋回。
- **加购按钮忘了 `addCartLoading`**：慢网下连点两次，购物车里多出两份；`enabled` 里带上 loading 标志，VM 里再挡一层。
- **加购成功不通知外层**：角标滞留旧值，用户「加了购物车数字没变」直接当 bug 上报；成功回调 `onCartChanged` 是最小闭环，第 87 章接到全局。
- **`catch(Exception)` 吞掉 `CancellationException`**：退出页面时在途请求被取消，取消异常若被当失败处理，就是往一个不存在的页面写错误态（第 82 章 3.3 的透传原则，每个 catch 都要先放行取消）。

## 7. 检查题

1. `matchedSku` 里如果写成 `it.specValueIds == selectedSpecValues.values.toList()`，在什么数据下会漏匹配？为什么 `toSet()` 不会？这个 bug 为什么在 mock 上测不出来？
2. `selectedSpecValues` 用 `Map<组id, 值id>` 而不是按组下标的 `List<Long>`，省掉了 ymq 版本里的哪张表？那张表在那里承担什么职责？
3. `reviewInfo` 与 `comment/page` 两个数据源的职责边界是什么？首屏把两边拼到一起会看到什么现象？
4. 同一个 `DetailViewModel` 上快速 2001→2002 时，`loadSeq` 经历了什么？`loadDetail` 把哪些评论分页字段归位？如果忘了重置 `commentHasMore`，在「2002 评论首页失败」的情形下点「查看更多评价」会发出什么请求？为什么首页成功前 `hasMore` 必须保持 false？
5. 加购成功为什么不用 `var addCartSuccess by mutableStateOf(false)` 这种状态字段通知 UI？把加购失败写进 `pageError` 又会出现哪两个问题？
6. 转屏后 `MallDetailEntry` 里的 `LaunchedEffect(spuId)` 会重新执行，为什么页面没有重新加载？这个守卫和 `rememberSaveable`（第 23 章）各自负责哪类状态？
7. `selectedSpecValues` 若改存 `MutableMap` 并原地 `put`，页面会发生什么？快照系统为什么不认为这是一次写？
8. 加购 A 在途时 `loadDetail(B)` 已执行：A 的响应回来时，`onCartChanged` 与 `onPageFeedback` 各自该不该被调用？`addCartLoading` 为什么只在 `seq` 对得上时才收尾？

## 8. 验收标准

- [ ] 2001 详情：轮播横滑、页码角标随翻页更新；价格/划线价/单位/标签/积分/已售信息完整
- [ ] 未选规格时加购按钮禁用（「请选择规格」）、数量不可加；选齐后价格/库存联动（mock 两 SKU 图同 URL，验收只看价格与库存文案变化）；再点一次 chip 可取消回到未选齐
- [ ] 数量约束连续推演：选 51011 数量加到 37 → 切 51012 数量自动收紧为 36 → 取消选择后预览隐藏、数量回 1、按钮回禁用
- [ ] 评论四态：首屏 2 条（含带图、商家回复、匿名用户的分支展示），「查看更多评价」追加第二页后按钮消失；停 mock 点「查看更多」出现失败提示且已加载评论保留，恢复 mock 后重试成功（仍请求第二页）；临时让评论首页抛异常时显示错误 + 重试而非「暂无评价」；列表与概览条无重复条目
- [ ] 同一 VM 连续 `loadDetail(2001)`→`loadDetail(2002)`（临时接线或直接调用）后，若 2002 评论首页失败（临时改造模拟），不出现「查看更多评价」按钮——旧商品的 `hasMore` 已归位
- [ ] 2002 显示「拼团」标签；2001 不显示
- [ ] 推荐流点 2006 显示「商品不存在」业务失败态；重试仍失败；返回后打开 2001 正常；断 mock 时出现网络连接错误信息（文案以底层异常 `message` 为准，不承诺固定字符串）
- [ ] 加购 51011 后按当前值对账：`cart/list` 条目 quantity +1、`cart/count` +1；App 内 Snackbar 弹出、`onCartChanged` 回调触发
- [ ] 停 mock 连点两次加购：相同失败文案的 Snackbar 弹两次；失败提示弹出后转屏不重弹旧错误；加购在途转屏后按钮转圈状态恢复、结束时收起
- [ ] 转屏后数据保留、不重新出现整页 loading、轮播停留在原页
- [ ] `python3 tools/extract_snippets.py` 与严格编译对本章片段通过

## 9. 总结

详情页 = 一个大嵌套 DTO + 一组字段化状态：`Map<组id, 值id>` 建模选择（整体替换赋回，不原地改）、`toSet()` 集合相等命中 SKU、`loadSeq` 防旧响应且换商品时把评论分页与加购状态一并归位、`reviewInfo` 与 `comment/page` 各管一段、评论区用「列表空/非空 × 错误有/无」区分四态、`detailHtml` 让位给 `detailImages`。SKU 联动没有任何手动通知——快照系统让读取相关状态的作用域自动失效重组，粒度取决于读取发生在哪、参数变没变；事件类反馈分两层：购物车变更是全局事实（迟到成功也要通知角标），页面提示是一次性回调（只属于发起代际）。下一章进入购物车：分组列表 + 数量增删改 + 三态条目，把本章的 `onCartChanged` 接成闭环。
