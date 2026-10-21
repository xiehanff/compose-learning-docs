# 实践篇 mock 服务器：ymq-mall-json-server

实践篇（第 81—87 章）统一使用现成的商城 mock 服务 [campos/ymq-mall-json-server](https://gitee.com/campos/ymq-mall-json-server)：接口结构按真实 Flutter 商城项目的 Dart 模型反推，自带首页/分类/详情/购物车/地址等数据与分页、购物车增删改等真实交互，**数据不用自己编**。

## 启动

```bash
git clone https://gitee.com/campos/ymq-mall-json-server.git
cd ymq-mall-json-server
npm install
npm run dev        # 即 node server.js，监听 3001
```

需要 Node.js 18+。改乱了数据随时 `curl http://localhost:3001/__reset` 还原。

## App 侧 BASE_URL

| 环境 | BASE_URL | 说明 |
| --- | --- | --- |
| Android 模拟器 | `http://10.0.2.2:3001/` | 模拟器内 `10.0.2.2` 指向宿主机（[Emulator networking](https://developer.android.com/studio/run/emulator-networking)） |
| 真机（USB） | `http://127.0.0.1:3001/` | 先 `adb reverse tcp:3001 tcp:3001` |
| 同局域网真机 | `http://<电脑局域网IP>:3001/` | 手机与电脑同一 Wi-Fi |

## 接口速览（第 81—87 章用到）

统一返回壳 `{ code, data, msg }`，`code == 0` 才算成功。完整契约与真实返回见该仓库 `API_CONTRACT.md`、`ALL_API_RESPONSES.md`。

| 用途 | 接口 |
| --- | --- |
| 首页分类 | `GET /app-api/merchant/category/getParentCategoryList` |
| 轮播图 | `POST /app-api/shop-product/banner/list`（body `{"position":"mall_home"}`） |
| 跑马灯（最近下单） | `GET /app-api/shop-trade/order/list-recent-paid` |
| 推荐商品分页/分类筛选 | `POST /app-api/shop-product/spu/categoryProductList`（`pageNo`/`pageSize`/`categoryId`） |
| 商品详情 | `POST /app-api/shop-product/spu/detail`（body `{"id":2001}`） |
| 评论分页 | `POST /app-api/shop-product/comment/page`（`spuId`/`pageNo`/`pageSize`） |
| 购物车角标 | `GET /app-api/shop-trade/cart/count` |
| 购物车列表（按店铺分组） | `GET /app-api/shop-trade/cart/list` |
| 加入购物车 | `POST /app-api/shop-trade/cart/add`（`{"skuId":51011,"quantity":1}`） |
| 改数量/换规格 | `POST /app-api/shop-trade/cart/update`（`{"cartId":91001,"quantity":3}`） |
| 删除 | `POST /app-api/shop-trade/cart/delete`（`{"cartIds":[91001]}`） |
| 结算校验 | `POST /app-api/shop-trade/cart/settlement` |
| 收货地址 | `GET /app-api/member/address/list` |
| 店铺客服 | `GET /app-api/merchant/customer-service/get?shopId=3001` |
| 重置 mock 状态 | `GET /__reset` |

有详情数据的商品 id：2001—2005、2011；推荐流其余 id（如 2006）请求详情会返回 `code=1`「商品不存在」，正好用作业务失败演示。可用 SKU 如 51011（2001 的「24包整箱」）、52021（2002）。分类 103「拼团专区」过滤结果为空，可作空态演示；图片 URL 来自 `uapis.cn` 随机图（302 跳真实图），Coil 可直接加载。

## 该 mock 的边界（教学简化）

- 无登录/鉴权接口：`mine`（个人主页）一页的「用户信息」只能本地静态；真实工程的登录态与 token 存取回看第 68、72 章。
- `settlement` 只返回校验列表，不产生真实订单流转。
- 购物车三态（`status`：1 正常 / 2 需重选规格 / 3 已失效）是 mock 预置样例，用来练分支 UI。
