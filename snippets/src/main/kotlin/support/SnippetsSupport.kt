// 供书里的代码片段编译通过的最小支撑代码（仅编译校验用，不是 App 代码）。
//
// 注意：这里的代码**只能证明片段语法/API 对得上**，不能证明书里的语义正确。
// 尺寸单位等实现直接照抄 compose_demo 里的真实源码，保证编译用的类型与换算方式
// 和书里描述的一致。

package snippets.support

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

// ------------------------------------------------- 1) 设计稿单位（照抄 ui/adapter/Px.kt）

// 设计稿尺寸单位：在 DesignWidthScale 内，1.px 表示设计稿中的 1px。
// 返回类型是 Dp；真正换算成物理像素靠第 41/43 章替换的 Density。
val Number.px: Dp
    get() = toFloat().dp

// 文字使用同一套设计稿 px 数值，转换为 Compose 可接受的 TextUnit。
@Composable
fun Number.pxSp(): TextUnit = with(LocalDensity.current) {
    TextUnit(
        value = toFloat() * density / fontScale,
        type = TextUnitType.Sp,
    )
}

// ------------------------------------------------- 2) 章节片段引用的零散符号

/** `ComponentDetailScreen.kt` 里详情页返回键的回调，第 26 章片段直接引用。 */
val onBack: () -> Unit = {}

// ------------------------------------------------- 3) 第 71A 章的 Nav3 示例 key

// 章节里这份 key 由读者自己声明（第 71A 章第 3 节“第二步”）。因为每个标记片段
// 都生成到独立的包装 object 里，后面的 NavDisplay / entryProvider 片段引用不到
// 另一个片段里声明的 Home / Product，所以这里放一份**内容完全相同**的副本。
@Serializable
data object Home : NavKey

@Serializable
data class Product(val id: String) : NavKey
