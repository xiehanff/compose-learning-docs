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

mock 的每个接口都返回 `{code, data, msg}`，`code == 0` 才成功，`data` 才是业务数据。注意三点：价格在接口里是**字符串**（`"49.90"`，保留两位小数的惯例），DTO 层如实接收，展示时再处理；`tagText` 在推荐商品里是字符串数组；所有字段给默认值配合 `ignoreUnknownKeys`，服务端加字段 App 不崩。

### 3.2 接口声明与客户端单例

接口声明只回答「调什么」：路径、方法、入参、返回。BASE_URL 按第 81 章的环境表填写。`Json` 配置里 `encodeDefaults = true` 不可省——否则 `BannerQueryDto.position` 这种默认值字段不会被序列化，mock 端 `req.body?.position` 取到 undefined 后落到兜底值，行为侥幸相同但契约是坏的。

下面是首页所需的完整网络层（片段必须自包含，故 DTO 与接口写在一起；详情、购物车接口在第 84、85 章按同样写法追加到 `MallApi`）：

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

### 3.3 结果封装：ApiResult 与协程取消

`code != 0` 是业务失败；IOException/HttpException 是网络失败。对 UI 层来说两者都该归约为「失败 + 一句人话」，但**协程取消不是失败**：`viewModelScope` 结束时会取消正在飞的请求，若把它吞成 `ApiResult.Failure`，会干扰结构化并发（第 73 章）。

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

完整实现见练习工程 `network/MallRepo.kt`（除上面三个，还有 `loadBannerList`、`loadProductDetail`、`loadComments`、`loadCartCount`、`loadCartList`、`addCart`、`updateCart`、`deleteCart`、`loadAddresses` 等，写法完全一致）；本仓库编译支撑见 `snippets/src/main/kotlin/support/MallSupport.kt`，第 83—87 章的 ViewModel 片段直接 `import snippets.support.MallRepo` 参与编译。注意 `loadCustomerService` 是唯一不走 `unwrap` 的方法：「该店铺没有配置客服」时服务端返回 `code == 0` 且 `data == null`，null 本身就是要交给 ViewModel 的业务结果，若走 `unwrap` 会被误判成「响应数据为空」的失败，所以它单独展开 `try/catch`，只把 `code != 0` 和网络异常当失败。

这个写法直接对齐 ymq Flutter 项目的 Repository 静态方法风格，职责相同、机制不同：

| ymq（Flutter） | 练习工程（Compose） |
| --- | --- |
| `MallRecommendProductRepository.fetch(...)` 等静态方法 | `object MallRepo` 的静态挂起方法 |
| 方法内发 post 请求 + `YQBaseResModel` 拆壳 | 方法内调 `MallApi` + `unwrap` 拆 `BaseResponse` 壳 |
| 页面/控制器不直接碰网络库与壳模型 | ViewModel 不直接碰 Retrofit 与 `BaseResponse` |

需要说明：上表只对照职责与写法，不代表两平台机制等价——例如取消语义就不同，Kotlin 侧透传 `CancellationException` 是协程结构化并发的特有要求（3.3 节），Dart 侧的异步取消走的是另一套机制。两边相同的只是「无状态静态入口 + 统一拆壳」这一层职责划分。

## 4. 实际执行过程

写完先不碰 UI，用一个临时入口直接打接口（`viewModelScope.launch { ... }` 里调 `callApi { api.parentCategories() }`，结果打日志），或直接看 Logcat 里 `HttpLoggingInterceptor` 的 BODY 级日志：请求行、请求体、响应体一目了然。确认三件事：URL 拼接正确（`app-api/` 前缀没丢）、请求体确实带上了默认值字段、`code == 0` 分支能拿到 `data`。

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

- **`baseUrl` 不以 `/` 结尾**：Retrofit 直接抛 `IllegalArgumentException`（`baseUrl must end in /`）。
- **`encodeDefaults` 忘开**：请求体字段被静默丢弃，mock 端全部走默认分支，筛选/分页「看起来没用但也没报错」。
- **把 `CancellationException` 吞成失败**：页面退出时弹「网络异常」 Toast，属于典型副作用泄漏。
- **在 DTO 里用 `Double` 接价格**：`"49.90"` 能转，但 `"49.9"`、`"1,299.00"` 这类格式会踩坑；DTO 如实用 String，格式化放到 UI 层。

## 7. 检查题

1. `BaseResponse<T>` 的泛型 `T` 对应 JSON 里哪个字段？`code != 0` 时应当抛异常还是返回 Failure？
2. `Json { encodeDefaults = true }` 去掉后，`BannerQueryDto(position = "mall_home")` 发出的请求体是什么？
3. 为什么 `callApi` 里 `catch (e: CancellationException) { throw e }` 不能省？

## 8. 总结

网络层只有三样东西：贴着接口写的 DTO、声明式的 `MallApi`、全局唯一的 `NetworkClient`，再加一个把「壳 + 异常」归约为 `ApiResult` 的出口函数。第 83 章开始，每个页面都只跟 `ApiResult` 和 DTO 打交道，不再直接感知 HTTP。
