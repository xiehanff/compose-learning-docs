// 实践篇（第 81—87 章）的编译支撑：与读者练习工程的网络层同构。
// 内容对应第 82 章正文 + ymq-mall-json-server 的接口契约（practice/mock-server/README.md）。
// 这里的代码只保证章节片段的类型与签名真实可编译；BASE_URL、DTO 字段以契约为准。

package snippets.support

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

// ---------------- 统一响应壳 ----------------

@Serializable
data class BaseResponse<T>(
    val code: Int = -1,
    val data: T? = null,
    val msg: String = "",
)

// ---------------- 首页 ----------------

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
    val extraParams: String = "",
)

@Serializable
data class RecentPaidOrderDto(
    val orderId: Long = 0,
    val displayText: String = "",
)

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

@Serializable
data class BannerQueryDto(val position: String = "mall_home")

@Serializable
data class ProductPageQueryDto(
    val categoryId: Long? = null,
    val pageNo: Int = 1,
    val pageSize: Int = 10,
)

// ---------------- 商品详情 ----------------

@Serializable
data class DetailQueryDto(val id: Long)

@Serializable
data class SpuDetailDto(
    val basicInfo: BasicInfoDto? = null,
    val skuInfo: SkuInfoDto? = null,
    val reviewInfo: ReviewInfoDto? = null,
    val shopInfo: ShopInfoDto? = null,
    val detailInfo: DetailInfoDto? = null,
    // 部分商品有拼团信息，部分为 null；教学章节只判空，不展开字段
    val groupBuyInfo: JsonObject? = null,
)

@Serializable
data class BasicInfoDto(
    val id: Long = 0,
    val name: String = "",
    val subTitle: String = "",
    val imgsUrl: List<String> = emptyList(),
    val price: String = "0.00",
    val originPrice: String = "0.00",
    val shopType: String = "",
    val salesVolume: Long = 0,
    val pointReward: String = "",
    val unit: String = "",
    val deliveryTime: String = "",
    val isFreeShipping: String = "",
    val tags: List<String> = emptyList(),
)

@Serializable
data class SkuInfoDto(
    val specGroupList: List<SpecGroupDto> = emptyList(),
    val skuList: List<SkuDto> = emptyList(),
)

@Serializable
data class SpecGroupDto(
    val id: Long = 0,
    val name: String = "",
    val specValueList: List<SpecValueDto> = emptyList(),
)

@Serializable
data class SpecValueDto(
    val id: Long = 0,
    val name: String = "",
    val imgUrl: String = "",
)

@Serializable
data class SkuDto(
    val id: Long = 0,
    val name: String = "",
    val imgUrl: String = "",
    val price: String = "0.00",
    val originPrice: String = "0.00",
    val vipPrice: String = "0.00",
    val stock: Int = 0,
    val specValueIds: List<Long> = emptyList(),
)

@Serializable
data class ReviewInfoDto(
    val reviewCount: Int = 0,
    val goodRate: Double = 0.0,
    val hasMore: Boolean = false,
    val list: List<ReviewPreviewDto> = emptyList(),
)

@Serializable
data class ReviewPreviewDto(
    val userNick: String = "",
    val userAvatar: String = "",
    val score: String = "",
    val content: String = "",
)

@Serializable
data class ShopInfoDto(
    val id: Long = 0,
    val name: String = "",
    val type: String = "",
    val shopTypeUrl: String = "",
    val logoUrl: String = "",
)

@Serializable
data class DetailInfoDto(
    val detailType: String = "",
    val detailHtml: String = "",
    val detailImages: List<String> = emptyList(),
)

@Serializable
data class CommentPageQueryDto(
    val spuId: Long,
    val pageNo: Int = 1,
    val pageSize: Int = 10,
)

@Serializable
data class CommentDto(
    val id: Long = 0,
    val userId: Long = 0,
    val userNickname: String = "",
    val userAvatar: String = "",
    val anonymous: Boolean = false,
    val spuName: String = "",
    val rating: Int = 0,
    val content: String = "",
    val imgUrls: List<String> = emptyList(),
    val visible: Boolean = true,
    val replyStatus: Boolean = false,
    val replyContent: String? = null,
    val replyTime: String? = null,
    val createTime: String = "",
    val description: String = "",
)

// ---------------- 购物车 ----------------

@Serializable
data class CartListDto(
    val shops: List<CartShopDto> = emptyList(),
    val selectedCount: Int = 0,
    val totalQuantity: Int = 0,
)

@Serializable
data class CartShopDto(
    val shopId: Long = 0,
    val shopName: String = "",
    val shopTypeUrl: String = "",
    val items: List<CartItemDto> = emptyList(),
)

@Serializable
data class CartItemDto(
    val id: Long = 0,           // 购物车条目 id（cartId）
    val spuId: Long = 0,
    val skuId: Long = 0,
    val shopId: Long = 0,
    val selected: Boolean = false,
    val quantity: Int = 0,
    val price: String = "0.00",
    val skuName: String = "",
    val picUrl: String = "",
    val spuName: String = "",
    val status: Int = 1,        // 1 正常 / 2 需重选规格 / 3 已失效
    val invalidReason: Int = 0,
    val stock: Int = 0,
    val purchaseLimit: Int = 99,
    val reward: Int = 0,
    val rewardDescription: String? = null,
    val rewardTypeName: String? = null,
    val shopTypeUrl: String = "",
)

@Serializable
data class CartAddDto(
    val skuId: Long,
    val quantity: Int = 1,
)

@Serializable
data class CartUpdateDto(
    val cartId: Long,
    val quantity: Int? = null,
    val skuId: Long? = null,
)

@Serializable
data class CartDeleteDto(val cartIds: List<Long>)

// ---------------- 个人主页 ----------------

@Serializable
data class AddressDto(
    val id: Long = 0,
    val name: String = "",
    val mobile: String = "",
    val areaId: Long = 0,
    val areaName: String = "",
    val detailAddress: String = "",
    val defaultStatus: Boolean = false,
)

@Serializable
data class CustomerServiceDto(
    val shopId: Long = 0,
    val shopName: String = "",
    val appid: String = "",
    val url: String = "",
    val jumpType: Int = 0,
)

// ---------------- 接口声明 ----------------

interface MallApi {

    @GET("app-api/merchant/category/getParentCategoryList")
    suspend fun parentCategories(): BaseResponse<List<CategoryDto>>

    @POST("app-api/shop-product/banner/list")
    suspend fun banners(@Body body: BannerQueryDto): BaseResponse<List<BannerDto>>

    @GET("app-api/shop-trade/order/list-recent-paid")
    suspend fun recentPaidOrders(): BaseResponse<List<RecentPaidOrderDto>>

    @POST("app-api/shop-product/spu/categoryProductList")
    suspend fun categoryProductList(
        @Body body: ProductPageQueryDto,
    ): BaseResponse<PageDto<ProductCardDto>>

    @POST("app-api/shop-product/spu/detail")
    suspend fun spuDetail(@Body body: DetailQueryDto): BaseResponse<SpuDetailDto>

    @POST("app-api/shop-product/comment/page")
    suspend fun commentPage(@Body body: CommentPageQueryDto): BaseResponse<PageDto<CommentDto>>

    @GET("app-api/shop-trade/cart/count")
    suspend fun cartCount(): BaseResponse<Int>

    @GET("app-api/shop-trade/cart/list")
    suspend fun cartList(): BaseResponse<CartListDto>

    @POST("app-api/shop-trade/cart/add")
    suspend fun cartAdd(@Body body: CartAddDto): BaseResponse<Boolean>

    @POST("app-api/shop-trade/cart/update")
    suspend fun cartUpdate(@Body body: CartUpdateDto): BaseResponse<Boolean>

    @POST("app-api/shop-trade/cart/delete")
    suspend fun cartDelete(@Body body: CartDeleteDto): BaseResponse<Boolean>

    @GET("app-api/member/address/list")
    suspend fun addressList(): BaseResponse<List<AddressDto>>

    @GET("app-api/merchant/customer-service/get")
    suspend fun customerService(@Query("shopId") shopId: Long): BaseResponse<CustomerServiceDto?>
}

// ---------------- 客户端单例 ----------------

object NetworkClient {

    // 模拟器访问宿主机；真机 adb reverse 后改 127.0.0.1
    var baseUrl: String = "http://10.0.2.2:3001/"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        coerceInputValues = true
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

// ---------------- 薄封装：ViewModel 只跟这里说话 ----------------

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
        throw e
    } catch (e: Exception) {
        throw IllegalStateException(e.message ?: "网络异常，请稍后重试", e)
    }

    suspend fun loadCategoryItems(): List<CategoryDto> = unwrap { api.parentCategories() }

    suspend fun loadBannerList(): List<BannerDto> = unwrap { api.banners(BannerQueryDto()) }

    suspend fun loadRecentPaidOrders(): List<RecentPaidOrderDto> = unwrap { api.recentPaidOrders() }

    suspend fun loadRecommendProducts(
        categoryId: Long? = null,
        pageNo: Int = 1,
        pageSize: Int = 10,
    ): PageDto<ProductCardDto> = unwrap {
        api.categoryProductList(ProductPageQueryDto(categoryId, pageNo, pageSize))
    }

    suspend fun loadProductDetail(spuId: Long): SpuDetailDto = unwrap { api.spuDetail(DetailQueryDto(spuId)) }

    suspend fun loadComments(
        spuId: Long,
        pageNo: Int = 1,
        pageSize: Int = 10,
    ): PageDto<CommentDto> = unwrap { api.commentPage(CommentPageQueryDto(spuId, pageNo, pageSize)) }

    suspend fun loadCartCount(): Int = unwrap { api.cartCount() }

    suspend fun loadCartList(): CartListDto = unwrap { api.cartList() }

    suspend fun addCart(skuId: Long, quantity: Int = 1): Boolean = unwrap { api.cartAdd(CartAddDto(skuId, quantity)) }

    suspend fun updateCart(cartId: Long, quantity: Int? = null, skuId: Long? = null): Boolean =
        unwrap { api.cartUpdate(CartUpdateDto(cartId, quantity, skuId)) }

    suspend fun deleteCart(cartIds: List<Long>): Boolean = unwrap { api.cartDelete(CartDeleteDto(cartIds)) }

    suspend fun loadAddresses(): List<AddressDto> = unwrap { api.addressList() }

    // 客服按店铺查询，查不到时服务端返回 data: null——这是合法业务态，不做失败处理
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
