# 第 82 章：网络层：Retrofit、kotlinx.serialization 与统一响应壳

## 1. 本章目标

学完后，你应当能够：

- 用 Retrofit + kotlinx.serialization 搭出练习工程的网络层三件套：DTO、`MallApi`、`NetworkClient`
- 处理 ymq mock 的统一响应壳 `{code, data, msg}`：DTO 只映射 `data` 里的业务字段
- 用挂起函数 + `suspend` 写接口声明，用 `Response<T>` 只在需要响应头/状态码时引入
- 把一次网络调用包成 `ApiResult`，并理解为什么协程取消异常必须原样抛出

## 2. 项目中的位置

- `com.squareup.retrofit2:retrofit`（[官方仓库](https://github.com/square/retrofit)）：把接口声明变成 HTTP 调用
- `converter-kotlinx-serialization`：Retrofit 官方的 kotlinx.serialization 转换器，随 Retrofit 2.11.0 起提供
- `com.squareup.okhttp3:okhttp` 与 `logging-interceptor`（[官方仓库](https://github.com/square/okhttp)）：底层传输与日志
- `org.jetbrains.kotlinx:kotlinx-serialization-json`（[官方文档](https://kotlinlang.org/docs/serialization.html)）：JSON 与 Kotlin 类的互转

版本为仓库基线快照（Retrofit 2.11.0 / OkHttp 4.12.0 / kotlinx.serialization 1.9.0），见《文档基线与审校规范》。

## 3. 逐步实现

### 3.1 响应壳与首页 DTO

**开工前，先翻四次车。** 第 81 章 curl 过分类接口，看到返回 `{"code":0,"data":[...],"msg":"success"}`。现在要把它变成 Kotlin 对象，直觉的第一版多半长这样：建一个 DTO，把页面要用的两个字段抄进去，类型凭感觉，`Json` 什么都不配。直觉错在哪，一口气讲不清——拆成四个最小实验，每个都能独立观察：先给最小的 DTO 和输入，先猜结果，再看答案。四个实验共用同一份严格版 `Json`（什么都不配，最省事）：

```kotlin
// 伪代码：四个实验共用的严格版 Json——默认配置，什么都不写
val json = Json {}
```

**实验 1：JSON 里多出 DTO 没声明的键。** 后台某天给分类对象加了个 `subtitle`：

```kotlin
// 伪代码：实验 1——输入多出一个 DTO 没声明的键
@Serializable
data class CategoryMini(val id: Long, val name: String)

// 输入：{"id":101,"name":"居家日用","subtitle":"新品专区"}
val cat: CategoryMini = json.decodeFromString(input)
```

**先猜：能解码吗？**——不能。kotlinx.serialization 默认严格模式，遇到 DTO 没声明的键当场抛异常，信息形如 `Encountered an unknown key 'subtitle'`，整页崩溃。提醒一句：这不是「明天后台一改」才兑现的远虑——真实 mock 的分类对象里今天就躺着 `iconUrl`、`sortOrder` 这些键，两字段的 DTO 去解真实数据立刻翻同一辆车，第二步的最小实验会亲眼见到。

**实验 2：DTO 声明了字段，JSON 里没有。** 同一个 DTO，换一份缺了 `name` 的输入：

```kotlin
// 伪代码：实验 2——输入缺少 DTO 声明的必填键（DTO 同实验 1）
// 输入：{"id":101}
val cat: CategoryMini = json.decodeFromString(input)
```

**先猜：`name` 会落成 null 还是空串？**——都不是。DTO 声明了字段、JSON 里没有、又没给默认值，抛的是 `MissingFieldException`。它是实验 1 的镜像：一个「多了」、一个「少了」，同一个严格模式的两面。

**实验 3：价格字符串接 `Double`。** mock 的价格是**带引号的字符串**（`"49.90"`），不是 JSON 数字。第一眼的直觉是写 `val price: Double`：

```kotlin
// 伪代码：实验 3——字符串价格接 Double
@Serializable
data class PriceDto(val price: Double)

// 输入一：{"price":"49.90"}
// 输入二：{"price":"1,299.00"}
val p: PriceDto = json.decodeFromString(input)
```

**先猜两问：两份输入各能过吗？**——输入一能过：kotlinx.serialization 允许「带引号的数字」落到数值字段，`"49.90"` 解析成 49.9，能跑的只是巧合；输入二直接解析失败，`1,299.00` 不是合法的数字字面量。说明一句：mock 现契约的价格固定是两位小数的字符串，`"1,299.00"`（千分位）是本章人为构造的变异输入，用来预演契约之外的格式——真实后台未必永远这么客气。

**实验 4：默认编码丢字段。** 请求体嘛，字段有默认值就行，发出去服务端总能看懂？——正好相反：

```kotlin
// 伪代码：实验 4——默认序列化省略等于默认值的属性
@Serializable
data class BannerQueryDto(val position: String = "mall_home")

val body: String = json.encodeToString(BannerQueryDto(position = "mall_home"))
// body == "{}" —— position 没发出去
```

**先猜：请求体长什么样？**——是 `{}`。默认配置下，kotlinx.serialization 序列化时会**省略等于默认值的属性**。mock 端 `req.body?.position` 取到 undefined 后落进兜底值，行为侥幸相同，契约是坏的；哪天兜底逻辑一改，轮播就悄悄失灵。

本章要交付的网络层，就是被这四类问题逼出来的形状：**DTO 只负责 `data` 的形状、字段全给默认值；`{code, data, msg}` 壳用泛型统一拆；失败在网络层归约成一句人话，协程取消原样放行**。实验 1、2、4 由第三步的配置三件套兜住，实验 3 是第四步的建模决策，失败归约与取消放行在 3.3、3.4。下面按「curl 看清 JSON → 最小实验（检查响应结构）→ 壳建模 → Json 配置三件套 → 完整网络层」把 DTO 这层爬出来。

**第一步：curl 再看一次原始 JSON。** 写 DTO 前把第 81 章的 curl 重跑一遍（轮播、商品分页接口要带请求体；分类与轮播的返回里没有价格字段，看价格引号要靠商品分页接口）：

```bash
curl http://localhost:3001/app-api/merchant/category/getParentCategoryList
curl -X POST http://localhost:3001/app-api/shop-product/banner/list \
     -H "Content-Type: application/json" -d '{"position":"mall_home"}'
curl -X POST http://localhost:3001/app-api/shop-product/spu/categoryProductList \
     -H "Content-Type: application/json" -d '{"pageNo":1,"pageSize":2}'
```

看三样东西：外层永远是 `{code, data, msg}`，`code == 0` 才成功，**`data` 才是业务数据**；商品分页接口 `data.list` 里的价格字段带引号（`"49.90"`，保留两位小数的惯例）；字段比你页面要用的多——分类除了 `id`/`name`，还有 `iconUrl`、`sortOrder` 这些。记住一个原则：**DTO 是照着 JSON 抄形状，不是照着 UI 需求抄**。

**第二步：最小实验，把 curl 结果直接喂给 Json。** 不搭网络层，kotlinx.serialization 单独就能开工——把 curl 到的 JSON 存成字符串。两案分开预测、分开观察，先猜再看：

**实验 A：跳过壳，直接把整个响应体解成分类列表。**

```kotlin
// 伪代码：实验 A——目标类型跳过壳，直接解码「整个响应体」
@Serializable
data class CategoryMini(val id: Long, val name: String)

val cats: List<CategoryMini> = json.decodeFromString(curlResult)
// curlResult = {"code":0,"data":[{...},{...}],"msg":"success"} 原样存下来的字符串
```

**先猜：会崩吗？崩在哪一步？** 逐帧看解码器在做什么：目标是 `List<CategoryMini>`，解码第一步要的是数组的起始符 `[`；而输入的第一个字符是 `{`——还没进到任何对象、更没轮到检查 `code`/`data`/`msg` 是不是已知键，解码就抛异常了，信息形如 `Expected start of the array, but had '{' instead`。注意两点：这不是 unknown key（解码根本没活到看键名那一步）；`ignoreUnknownKeys = true` 也救不了它——响应外层是**对象**、目标类型却是**数组**，结构不匹配，配置无能为力。

**实验 B：给壳建模后再解一次。** 失败原因既然是结构，就把壳写进类型——给它一个泛型声明，`T` 只对应 `data` 的形状：

```kotlin
// 伪代码：实验 B——壳建模后的最小版，T 才是业务数据
@Serializable
data class BaseResponse<T>(
    val code: Int = -1,
    val data: T? = null,
    val msg: String = "",
)

val res: BaseResponse<List<CategoryMini>> = json.decodeFromString(curlResult)
// res.data 才是分类列表；res.code / res.msg 是壳的元信息
```

**先猜：这次总能跑通了吧？** 外层三个键 `code`/`data`/`msg` 对上了，解码顺利进到 `data` 数组——然后翻车：真实分类对象里有 `iconUrl`、`sortOrder`、`enabled`，`CategoryMini` 一个都没声明，开场实验 1 的 unknown key 在真实数据上现形，信息形如 `Encountered an unknown key 'iconUrl'`。开 `ignoreUnknownKeys = true` 重跑，这回才真正跑通——前提是 `data` 数组里每个对象剩下的键，恰好和 `CategoryMini` 声明的一一对齐。又是在买彩票。

两案合起来，结论有两条：**壳不是噪音，是 JSON 结构的一部分，必须建模**（实验 A 用失败证明）；**严格模式的雷在真实数据上存在，必须靠配置兜住**（实验 B 现形）——配置就是下一步。

**第三步：Json 配置三件套，把彩票换成保险。** 三行配置加一条「字段全给默认值」的纪律，各自挡住一类翻车（真实代码在本节末尾片段的 `NetworkClient.json` 里）：

| 手段 | 方向 | 挡哪次翻车 | 先猜：少了它会怎样 |
| --- | --- | --- | --- |
| `ignoreUnknownKeys = true` | 响应（解码） | JSON 多出 DTO 没声明的键 | 实验 1 的 `subtitle`、实验 B 的 `iconUrl` → 解码即崩 |
| 所有字段给默认值 | 响应（解码） | JSON 少了 DTO 声明的键 | `MissingFieldException`（实验 2） |
| `coerceInputValues = true` | 响应（解码） | JSON 里显式 `null` 落到非空字段 | 解码按失败抛出，落不回默认值 |
| `encodeDefaults = true` | 请求（编码） | 序列化省略默认值属性 | `BannerQueryDto` 发出 `{}`（实验 4） |

注意方向：前三行守响应侧的解码容错，`encodeDefaults` 守请求侧的编码完整——一次网络调用两个方向各有各的坑。实验 3（价格用 `Double`）要靠的不是配置，是建模决策：

**第四步：价格字段如实用 String。** 第一眼看到 `"price": "49.90"`，直觉想写 `val price: Double`。别急着数值化：跨「字符串 → 数字」这道坎时 `"49.90"` 能过、`"1,299.00"` 过不去；就算碰上 `"49.9"` 这种能过的写法，转成数值也丢了「服务端原文是两位小数」这层格式信息，排查展示问题时对不上账。再交代一次边界：mock 现契约的价格固定是两位小数的字符串，千分位是本章人为构造的变异输入——这不妨碍把 DTO 写稳。练习工程的约定：**DTO 如实接收字符串，数值比较与格式化放到 UI 层**（第 83 章划线价的 `toBigDecimalOrNull` 就是这么用的）。`tagText` 在推荐商品里是字符串数组，同样照抄。

**完整实现登场。** 壳、DTO、接口、客户端一次给全（片段必须自包含，故 DTO 与接口写在一起；接口与客户端两块先当黑盒用，3.2 再拆开细读；详情、购物车接口在第 84、85 章按同样写法追加到 `MallApi`）：

<!-- compile decls -->
```kotlin
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

// 统一响应壳：DTO 泛型 T 只对应 data 的形状
@Serializable
data class BaseResponse<T>(
    val code: Int = -1,
    val data: T? = null,
    val msg: String = "",
)

// —— 首页用到的 data 模型（字段名与接口 JSON 一一对应）——

@Serializable
data class CategoryDto(
    val id: Long = 0,
    val name: String = "",
    val iconUrl: String = "",
    val sortOrder: Int = 0,
    val enabled: Boolean = true,
)

@Serializable
data class BannerDto(
    val id: Long = 0,
    val position: String = "",
    val title: String = "",
    val imgUrl: String = "",
    val jumpTarget: String = "",
    val extraParams: String = "", // JSON 字符串，需要时再二次解析
)

// 分页统一结构：list/total/hasMore
@Serializable
data class PageDto<T>(
    val list: List<T> = emptyList(),
    val total: Int = 0,
    val hasMore: Boolean = false,
)

@Serializable
data class ProductCardDto(
    val id: Long = 0,
    val categoryId: Long = 0,
    val name: String = "",
    val mainImgUrl: String = "",
    val price: String = "0.00",
    val originPrice: String = "0.00",
    val points: String = "",
    val tagText: List<String> = emptyList(),
    val groupBuyTag: String = "",
)

// 请求体也用 @Serializable：encodeDefaults 保证默认参数同样被发送
@Serializable
data class BannerQueryDto(val position: String = "mall_home")

@Serializable
data class ProductPageQueryDto(
    val categoryId: Long? = null,
    val pageNo: Int = 1,
    val pageSize: Int = 10,
)

@Serializable
data class RecentPaidOrderDto(
    val orderId: Long = 0,
    val displayText: String = "",
)

interface MallApi {

    @GET("app-api/merchant/category/getParentCategoryList")
    suspend fun parentCategories(): BaseResponse<List<CategoryDto>>

    @POST("app-api/shop-product/banner/list")
    suspend fun banners(@Body body: BannerQueryDto): BaseResponse<List<BannerDto>>

    @POST("app-api/shop-product/spu/categoryProductList")
    suspend fun categoryProductList(
        @Body body: ProductPageQueryDto,
    ): BaseResponse<PageDto<ProductCardDto>>

    @GET("app-api/shop-trade/order/list-recent-paid")
    suspend fun recentPaidOrders(): BaseResponse<List<RecentPaidOrderDto>>

    @GET("app-api/shop-trade/cart/count")
    suspend fun cartCount(): BaseResponse<Int>
}

object NetworkClient {

    // 模拟器访问宿主机；真机 adb reverse 后改 127.0.0.1
    var baseUrl: String = "http://10.0.2.2:3001/"

    private val json = Json {
        ignoreUnknownKeys = true  // 服务端多返回的字段直接忽略
        encodeDefaults = true     // 请求体默认值也要发送
        coerceInputValues = true  // JSON null 落回 Kotlin 默认值
    }

    fun create(): MallApi = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(
            OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .addInterceptor(
                    HttpLoggingInterceptor().apply {
                        level = HttpLoggingInterceptor.Level.BODY
                    },
                )
                .build(),
        )
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(MallApi::class.java)
}
```

片段 130 多行，别逐行啃。先建立「阅读地图」——五组各司其职，每组埋一个「先猜：缺了会怎样」，答案都在前文的翻车现场或后文的小节里：

| 组 | 成员 | 先猜：缺了会怎样 |
| --- | --- | --- |
| 响应壳 | `BaseResponse<T>` / `PageDto<T>` | 每个接口手拆一遍壳；`code != 0` 的业务失败散落各处、没有统一出口（3.3 兑现） |
| data 模型 | `CategoryDto` / `BannerDto` / `ProductCardDto` / `RecentPaidOrderDto` | 回到实验 1/2：严格模式下契约一漂移就崩；价格失去原文（第四步） |
| 请求体 | `BannerQueryDto` / `ProductPageQueryDto` | `encodeDefaults` 关掉后请求体退化成 `{}`（实验 4） |
| 接口声明 | `MallApi` 的 5 个挂起方法 | 回到手拼 URL 的世界：banner 该用 POST 却写成 GET、路径漏了 `app-api/` 前缀——这类错误编译期无感，运行时 405/404 才暴露（3.2 细读声明式如何收口） |
| 客户端单例 | `NetworkClient` | `baseUrl`、超时、日志、Json 三件套没了统一安放的配置入口；注意 object 本身不自动缓存客户端——api 由 `MallRepo` 初始化时持有一次 `create()` 的结果，App 存续期间复用；`baseUrl` 的尾斜杠见 3.2 的翻车 |

再留一个钩子：`BaseResponse.data` 为什么声明成 `T?` 可空？先猜——「`code == 0` 但 `data` 为空」一定是失败吗？答案在 3.4 兑现。

### 3.2 接口声明与客户端单例

接口声明只回答「调什么」：路径、方法、入参、返回——除此之外一概不管。逐项对照片段里的 `MallApi`：`@GET`/`@POST` 注解给 HTTP 方法和相对路径（`app-api/...` 前缀不能丢）；`@Body` 标记请求体；返回值统一是 `BaseResponse<...>`，泛型参数就是 `data` 的形状；方法声明成 `suspend` 挂起函数，调用方在协程里像调本地函数一样调它。需要响应头或 HTTP 状态码时，把返回值包成 `Response<T>` 再读——本练习工程用不到，保持裸的 `BaseResponse` 即可。

**客户端这边先猜一次车：把 `baseUrl` 末尾的 `/` 去掉会怎样？** 答案要分两种情况，不能一概而论。先看最小验证（只调 Builder 的校验，不创建接口、不发任何请求）：

```kotlin
// 伪代码：只观察 Builder 的校验，不实际请求
Retrofit.Builder().baseUrl("http://example.com/api")    // 抛 IllegalArgumentException: baseUrl must end in /
Retrofit.Builder().baseUrl("http://example.com/api/")   // 通过：最后一个路径段是空串
Retrofit.Builder().baseUrl("http://10.0.2.2:3001")      // 也通过——特例，见下
```

- **路径非空、不以 `/` 结尾**（`http://example.com/api`）：`NetworkClient.create()` 里 `Retrofit.Builder().baseUrl()` 一执行就抛 `IllegalArgumentException`（`baseUrl must end in /`）。Retrofit 校验的是**解析后 URL 的最后一个路径段必须为空**——这是「一改 BASE_URL 就崩」的最常见原因，见第 87 章第 6 节的 BASE_URL 条目。
- **只有 host:port、没有路径**（`http://10.0.2.2:3001`）：**不抛**。OkHttp 解析 URL 时会把空路径规范化成 `/`，解析结果的最后一个路径段恰好是空串，校验侥幸通过——纯 host:port 是规范化救场的特例，别拿它当「不加斜杠也没事」的证据，规则仍然是「基地址一律以 `/` 结尾」。

尾斜杠不是装饰：`MallApi` 里的 `app-api/...` 是**相对路径**，靠它和 `baseUrl` 拼出完整 URL，`baseUrl` 以 `/` 结尾才保证相对路径接在预期的位置。BASE_URL 按第 81 章的环境表填写：模拟器 `http://10.0.2.2:3001/`（模拟器内 `10.0.2.2` 就是你的电脑）；真机先 `adb reverse tcp:3001 tcp:3001` 再改 `http://127.0.0.1:3001/`。

`NetworkClient` 其余装配逐行看：`Json` 三件套就住在 `json` 那个代码块里（3.1 表格的兑现位置）；OkHttp 配了连接 5 秒 / 读取 10 秒超时，再挂 `HttpLoggingInterceptor` 开 BODY 级日志——第 4 节的验证全靠它；最后 `json.asConverterFactory("application/json".toMediaType())` 把 kotlinx.serialization 接进 Retrofit（转换器随 Retrofit 2.11.0 起提供）。`baseUrl` 声明成 `var`，是为了 87 章真机验收时能在运行前改地址。

### 3.3 结果封装：ApiResult 与协程取消

`code != 0` 是业务失败；IOException/HttpException 是网络失败。对 UI 层来说两者都该归约为「失败 + 一句人话」，但**协程取消不是失败**。先看一把梭的直觉版出口，猜一次车：

```kotlin
// 伪代码：直觉版出口——拆壳 + 异常一把梭全吞
suspend fun <T> callApiNaive(block: suspend () -> BaseResponse<T>): ApiResult<T> = try {
    val res = block()
    if (res.code == 0) ApiResult.Success(res.data!!)
    else ApiResult.Failure(res.msg)
} catch (e: Exception) {   // 网络异常、业务异常……一把梭全接成 Failure
    ApiResult.Failure("网络异常，请稍后重试")
}
```

**先猜 10 秒**：首页正在加载（mock 有 400ms 延迟），用户等不及退出了页面——这一瞬间会发生什么？先立一个假设：这次退出**真的清理了首页的 ViewModel**（导航返回栈弹出该页即如此；若页面只是被别的页面覆盖、自己还留在返回栈里，ViewModel 不会清理，也就没有下面要讲的取消）。时间线：

```text
t0  首页还在，请求在途（400ms 延迟中）
t1  ViewModel 被清理 → viewModelScope 结束 → 结构化并发取消在途请求
t2  协程以 CancellationException「醒来」——这本是正常退场信号
t3  catch (e: Exception) 把它接住——CancellationException 也是 Exception 的子类
t4  吞成 ApiResult.Failure("网络异常") 正常返回——注意：吞掉异常不会让这个已取消的 Job
    恢复活跃，但 catch 之后的非挂起代码照样执行，错误处理跑在已取消的上下文里
```

光有出口还看不到症状，得有调用方消费这个 `Failure`：

```kotlin
// 伪代码：调用方——Failure 分支写状态、弹提示
viewModelScope.launch {
    when (val r = callApiNaive { api.parentCategories() }) {
        is ApiResult.Success -> categories = r.data
        is ApiResult.Failure -> {
            pageError = r.message   // ViewModel 正在清理，状态写进了一个将死的作用域
            toast("网络异常")        // 用户是主动离开的，却被「报错」打扰
        }
    }
}
```

对照着看：用户在 t1 主动离开，t4 返回的 `Failure` 还是会点亮 `is ApiResult.Failure` 分支——Toast 和状态写入都是没人想要的副作用。取消是协程的正常退场，不是失败；把它吞成 `ApiResult.Failure` 还会干扰结构化并发（第 73 章）。修复就是把透传放在 catch 链的最前面。完整实现：

<!-- compile decls -->
```kotlin
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable

// 统一响应壳（同 3.1；片段自包含需要重新声明）
@Serializable
data class BaseResponse<T>(
    val code: Int = -1,
    val data: T? = null,
    val msg: String = "",
)

sealed interface ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>
    data class Failure(val message: String) : ApiResult<Nothing>
}

// 统一出口：壳里 code != 0 视为业务失败；取消异常必须原样抛出
suspend fun <T> callApi(block: suspend () -> BaseResponse<T>): ApiResult<T> = try {
    val response = block()
    if (response.code == 0 && response.data != null) {
        ApiResult.Success(response.data)
    } else {
        ApiResult.Failure(response.msg.ifBlank { "业务失败(code=${response.code})" })
    }
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    ApiResult.Failure(e.message ?: "网络异常，请稍后重试")
}
```

逐行对照：`code == 0 && data != null` 才算 `Success`——`code != 0` 归约为带 `msg` 的 `Failure`，`data == null` 也按失败处理（壳允许 `data` 可空，出口处收紧；「可空 data 是不是一定失败」的悬念留到 3.4）；`catch (e: Exception)` 兜底把网络异常归约成一句人话；而最前面那行 `throw e`，就是为 t2—t4 的时间线准备的——顺序不能换，透传必须排在兜底捕获之前。透传之后，上面调用方那段 `is ApiResult.Failure` 分支根本不会执行——协程以取消异常直接收场，这才是想要的退场。

### 3.4 MallRepo：ViewModel 直接调用的薄封装

DTO、`MallApi`、`NetworkClient` 就位后，还差最后一层：让 ViewModel 不直接碰 `BaseResponse` 和 HTTP 细节。练习工程的做法是在 `network/` 目录下放一个 `object MallRepo`，用一组静态挂起方法把「调接口 + 拆 `{code, data, msg}` 壳 + 失败抛异常」收敛起来。链路是一条直线：`ViewModel → MallRepo → MallApi（Retrofit）→ HTTP`；ViewModel 调 MallRepo 拿到 DTO 后写进状态字段，UI 读取状态自动重组刷新（第 68 章）。这里刻意不引入 Repository 接口层、不做可测试性抽象——这是本练习工程的教学取舍，不是架构上的要求。

先说清楚它和 3.3 节 `callApi` 的关系：两者是同一件事的两种归约方式——`callApi` 把失败归约为 `ApiResult.Failure` 返回值，MallRepo 内部的 `unwrap` 把失败归约为抛异常。练习工程从第 83 章起，ViewModel 统一走 MallRepo（异常风格）：挂起方法要么返回 DTO、要么抛异常，`try/catch` 集中写在 ViewModel 的加载函数里；`callApi` 作为 3.3 节讲解的另一种归约方式保留，两者选一即可，本章后续章节均按 MallRepo 的异常风格书写。

MallRepo 的核心是私有的 `unwrap`：`code != 0` 抛业务异常，`data == null` 抛空数据异常，`CancellationException` 原样透传（原因同 3.3 节），其余异常统一包成一句人话。每个公开方法一行到几行，只负责把 `MallApi` 的调用包进 `unwrap`：

```kotlin
object MallRepo {

    val api: MallApi = NetworkClient.create()

    // 拆壳：code != 0 或 data 为空都按失败抛出；协程取消原样透传
    private suspend fun <T : Any> unwrap(block: suspend () -> BaseResponse<T>): T = try {
        val res = block()
        if (res.code != 0) {
            throw IllegalStateException(res.msg.ifBlank { "业务失败(code=${res.code})" })
        }
        res.data ?: throw IllegalStateException("响应数据为空")
    } catch (e: CancellationException) {
        throw e
    } catch (e: IllegalStateException) {
        throw e // 业务异常原样上抛，不再包一层
    } catch (e: Exception) {
        throw IllegalStateException(e.message ?: "网络异常，请稍后重试", e)
    }

    // 普通列表：一行就是一次「调接口 + 拆壳」
    suspend fun loadCategoryItems(): List<CategoryDto> = unwrap { api.parentCategories() }

    // 分页查询：参数带默认值，加载下一页时只覆盖 pageNo
    suspend fun loadRecommendProducts(
        categoryId: Long? = null,
        pageNo: Int = 1,
        pageSize: Int = 10,
    ): PageDto<ProductCardDto> = unwrap {
        api.categoryProductList(ProductPageQueryDto(categoryId, pageNo, pageSize))
    }

    // 客服按店铺查询，查不到时服务端返回 data: null——这是合法业务态，返回 null 而非抛异常
    suspend fun loadCustomerService(shopId: Long): CustomerServiceDto? = try {
        val res = api.customerService(shopId)
        if (res.code != 0) throw IllegalStateException(res.msg.ifBlank { "业务失败" })
        res.data
    } catch (e: CancellationException) {
        throw e
    } catch (e: IllegalStateException) {
        throw e
    } catch (e: Exception) {
        throw IllegalStateException(e.message ?: "网络异常，请稍后重试", e)
    }
}
```

完整实现见练习工程 `network/MallRepo.kt`（除上面三个，还有 `loadBannerList`、`loadProductDetail`、`loadComments`、`loadCartCount`、`loadCartList`、`addCart`、`updateCart`、`deleteCart`、`loadAddresses` 等，写法完全一致）；本仓库编译支撑见 `snippets/src/main/kotlin/support/MallSupport.kt`，第 83—87 章的 ViewModel 片段直接 `import snippets.support.MallRepo` 参与编译。注意 `loadCustomerService` 是唯一不走 `unwrap` 的方法：「该店铺没有配置客服」时服务端返回 `code == 0` 且 `data == null`，null 本身就是要交给 ViewModel 的业务结果，若走 `unwrap` 会被误判成「响应数据为空」的失败，所以它单独展开 `try/catch`，只把 `code != 0` 和网络异常当失败。这正是 3.1 留下的钩子的答案：`BaseResponse.data` 声明成可空，就是给这类「成功但无数据」的合法业务态留门——`callApi` 和 `unwrap` 把它统一按失败处理，是首页/商品流场景的取舍，客服场景则要原样交还 null。

这个写法直接对齐 ymq Flutter 项目的 Repository 静态方法风格，职责相同、机制不同：

| ymq（Flutter） | 练习工程（Compose） |
| --- | --- |
| `MallRecommendProductRepository.fetch(...)` 等静态方法 | `object MallRepo` 的静态挂起方法 |
| 方法内发 post 请求 + `YQBaseResModel` 拆壳 | 方法内调 `MallApi` + `unwrap` 拆 `BaseResponse` 壳 |
| 页面/控制器不直接碰网络库与壳模型 | ViewModel 不直接碰 Retrofit 与 `BaseResponse` |

需要说明：上表只对照职责与写法，不代表两平台机制等价——例如取消语义就不同，Kotlin 侧透传 `CancellationException` 是协程结构化并发的特有要求（3.3 节），Dart 侧的异步取消走的是另一套机制。两边相同的只是「无状态静态入口 + 统一拆壳」这一层职责划分。

## 4. 实际执行过程（先猜 → 操作 → 对照）

1. **猜**：banner 请求发出去，请求体长什么样？→ **操作**：写完先不碰 UI，用一个临时入口直接打接口（`viewModelScope.launch { ... }` 里调 `callApi { api.banners(BannerQueryDto()) }`，结果打日志），或直接看 Logcat 里 `HttpLoggingInterceptor` 的 BODY 级日志：请求行、请求体、响应体一目了然。→ **对照**：请求体确实带上了默认值字段 `{"position":"mall_home"}`；把 `encodeDefaults` 关掉重跑一次，它退化成 `{}`——实验 4 的契约缺口肉眼可见，看完改回来。
2. **猜**：日志里的完整 URL 会怎么拼？→ **操作**：看请求行。→ **对照**：`app-api/` 前缀没丢（尾斜杠 `baseUrl` + 相对路径拼接）；顺手把 `baseUrl` 改成 `http://10.0.2.2:3001/v1`（带非空路径且不以 `/` 结尾）再跑，`create()` 直接抛 `IllegalArgumentException`——3.2 翻过的一次车，这里亲眼见一遍；再换成纯 host:port 的 `http://10.0.2.2:3001` 试一次，这次不抛（空路径被规范化成 `/`，3.2 的特例），两种都看完改回来。
3. **猜**：请求一个不存在的商品（如 2006）的详情，HTTP 状态码和壳里的 `code` 各是多少？→ **操作**：`curl -i -X POST http://localhost:3001/app-api/shop-product/spu/detail -H "Content-Type: application/json" -d '{"id":2006}'`（`-i` 让响应头里的状态码一起打印出来）。→ **对照**：HTTP 200、`code=1`「商品不存在」——只看状态码的调用方毫无感知，这就是壳必须在网络层拆的原因（3.3 出口的业务失败分支）。
4. **猜**：`code == 0` 分支能拿到什么？→ **操作**：临时入口打印分类列表。→ **对照**：`ApiResult.Success` 里的 `data` 就是分类列表，条数与 curl 输出对得上。
5. 数据改乱随时 `curl http://localhost:3001/__reset` 还原再验。

## 5. Flutter/Dart 3 对照

| Compose 练习工程 | Flutter 常用对应 |
| --- | --- |
| Retrofit 接口声明 | `retrofit_for_dart` 或手写 `dio` 封装 |
| kotlinx.serialization + `@Serializable` | `json_serializable` + `json.decode<T>` |
| `BaseResponse<T>` 拆壳 | 同样的泛型壳模型，多在拦截器/Repository 拆 |
| `ApiResult` 密封接口 | `sealed class Result<T>` / `dartz` 的 `Either` |
| `HttpLoggingInterceptor` | `dio.InterceptorsWrapper` 里 `LogInterceptor` |

差别：Dio 的取消走 `CancelToken`，Compose 侧协程取消是结构化并发的默认行为，这就是必须透传 `CancellationException` 的原因。

## 6. 常见错误

- **`baseUrl` 不以 `/` 结尾（路径非空时）**：Retrofit 在 `Builder.baseUrl()` 就抛 `IllegalArgumentException`（`baseUrl must end in /`）。纯 host:port（如 `http://10.0.2.2:3001`）因 OkHttp 把空路径规范化成 `/` 而侥幸不抛——规则仍然是「一律以 `/` 结尾」，别依赖特例。
- **`encodeDefaults` 忘开**：被省略的只有**等于默认值的属性**——`BannerQueryDto(position = "mall_home")` 发出 `{}`，mock 端靠同值兑底掩盖了契约缺口；**不等于默认值的参数不受影响**，`ProductPageQueryDto(categoryId = 101, pageNo = 2)` 里的 `categoryId`/`pageNo` 照常发送，筛选分页功能本身不会因此失效。真正坏掉的是「默认值字段也参与契约」这件事。
- **把 `CancellationException` 吞成失败**：页面退出（对应 ViewModel 被清理）时错误分支照跑、弹「网络异常」Toast，属于典型副作用泄漏（3.3 的调用方伪代码就是症状现场）。
- **在 DTO 里用 `Double` 接价格**：`"49.90"` 能解析，只是巧合；`"1,299.00"`（千分位）直接解析失败；`"49.9"` 这类能解析的变体则丢失「两位小数」的原文格式信息——两类问题分开记。DTO 如实用 String，格式化放到 UI 层。
- **DTO 字段不给默认值、也不开 `ignoreUnknownKeys`**：服务端契约一漂移就崩——多返回字段是 unknown key 解码异常，少返回字段是 `MissingFieldException`，两个方向各中一枪。
- **只看 HTTP 状态码判断成功**：mock 的业务失败（商品不存在）走 `code=1` 但 HTTP 200，调用方毫无感知；成功判据必须是 `code == 0`。

## 7. 检查题

1. `BaseResponse<T>` 的泛型 `T` 对应 JSON 里哪个字段？`code != 0` 时应当抛异常还是返回 Failure？
2. `Json { encodeDefaults = true }` 去掉后，`BannerQueryDto(position = "mall_home")` 发出的请求体是什么？
3. 为什么 `callApi` 里 `catch (e: CancellationException) { throw e }` 不能省？
4. 默认 `Json {}` 下，服务端多返回一个 DTO 没声明的字段、少返回一个没默认值的字段，分别会发生什么？分别靠哪个配置或写法兜住？
5. `baseUrl` 分别写成 `http://example.com/api` 和 `http://10.0.2.2:3001`（都不带末尾的 `/`），各会怎样？为什么不同？异常（如果有）在网络层的哪个环节抛出？

## 8. 验收标准

- [ ] **curl 验证契约**：mock 启动，分类、轮播、商品分页接口均返回 `{"code":0,...}`；商品分页 `data.list` 里的价格是带引号的字符串
- [ ] **App 调用验证 Success 分支**：临时入口调 `callApi { api.parentCategories() }` 打印出分类列表：`code == 0` 分支拿到 `data`，条数与 curl 输出一致
- [ ] Logcat BODY 级日志三看：URL 的 `app-api/` 前缀没丢、banner 请求体带 `position` 字段、响应体完整可见
- [ ] 把 `encodeDefaults` 关掉重跑，亲眼看到 banner 请求体退化成 `{}`，再改回并复验
- [ ] **curl 验证契约**：`curl -i` 请求商品 2006 的详情，看到 HTTP 200 + `code=1`
- [ ] **App 调用验证 Failure/unwrap 分支**：临时入口请求商品 2006，确认这类响应只落进 `ApiResult.Failure`（走 MallRepo 则是 `unwrap` 抛异常），不进 Success
- [ ] `python3 tools/extract_snippets.py` 与严格编译对本章片段通过

## 9. 总结

网络层只有三样东西：贴着接口写的 DTO、声明式的 `MallApi`、配置集中且被 `MallRepo` 持有一次的 `NetworkClient`，再加两个把「壳 + 异常」归约干净的出口——3.3 的 `callApi`（归约成 `ApiResult` 返回值）与 3.4 的 `MallRepo`（归约成抛异常，练习工程从第 83 章起统一走它）。教学主线回放一遍：开场四个最小实验（新增键崩 / 缺键崩 / 价格接 `Double` / 默认编码丢字段）各自现形；最小实验两连翻——对象喂给数组目标先崩在结构上、壳建模后 unknown key 在真实数据上现形——逼出 `BaseResponse<T>`；Json 三件套 + 字段默认值把「赌契约不漂移」换成「契约容错」；`catch(Exception)` 一把梭吞掉取消异常的时间线（连同它的调用方症状），逼出出口函数最前面那行透传。第 83 章开始，每个页面都只跟 MallRepo（或 callApi）和 DTO 打交道，不再直接感知 HTTP。
