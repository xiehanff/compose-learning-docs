# 第 51 章：animate*AsState 与 Tween 基础动画

## 1. 本章目标

学完后，你应当能够：

- 用一行声明式的代码让 Float、Dp、Color 等值随状态变化自动播放动画，不再手写 Animatable 和帧回调（Animatable 见第 52 章）
- 说清 animate*AsState 家族返回值的类型，以及 targetValue 变化后内部发生了什么
- 区分「值动画」与 AnimatedVisibility「内容出现/消失动画」的适用场景
- 避开动画期间反复改 targetValue、尺寸单位混用等常见坑

## 2. 项目中的位置

进阶篇不再局限于 compose_demo 项目，本章使用通用 Compose API。

- `androidx.compose.animation`：animateColorAsState（Color 的类型转换器定义在 animation 包）
- `androidx.compose.animation.core`：animateFloatAsState、animateDpAsState、animateSizeAsState、animateOffsetAsState、animateIntAsState、tween、FastOutSlowInEasing
- `androidx.compose.ui.graphics.graphicsLayer`、`androidx.compose.foundation.background`：仅用于把动画值应用到 UI

## 3. 项目源码片段

场景：社区 App 的点赞按钮。点击后心形与数字放大一下再回落，背景从透明过渡为主题红。整段动画不需要任何手动进度控制，只声明「目标值」。

<!-- compile decls -->
```kotlin
@Composable
fun LikeButton(isLiked: Boolean, likeCount: Int, onToggle: () -> Unit) {
    // 心形缩放：点赞后放大到 1.3 倍，取消点赞回到 1f
    val scale by animateFloatAsState(
        targetValue = if (isLiked) 1.3f else 1f,
        animationSpec = tween(durationMillis = 220),
        label = "likeScale"
    )
    // 背景色：点赞后过渡为主题红
    val bgColor by animateColorAsState(
        targetValue = if (isLiked) Color(0xFFFF6B81) else Color.Transparent,
        label = "likeBackground"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(bgColor)
            .clickable(onClick = onToggle)
            .padding(horizontal = 20.dp, vertical = 10.dp)
    ) {
        Text(
            text = "❤",
            fontSize = 26.sp,
            modifier = Modifier.graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
        )
        Text(text = "$likeCount", style = MaterialTheme.typography.bodyMedium)
    }
}
```

场景二：卡片折叠/展开时宽度平滑变化。尺寸必须用 Dp 专用 API，避免手动换算。

<!-- compile decls -->
```kotlin
@Composable
fun ExpandableCard(expanded: Boolean) {
    val cardWidth by animateDpAsState(
        targetValue = if (expanded) 280.dp else 120.dp,
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "cardWidth"
    )
    Surface(
        modifier = Modifier.width(cardWidth).height(80.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primaryContainer
    ) { Text(if (expanded) "展开详情" else "点击展开") }
}
```

## 4. 逐步解释

1. **animate*AsState 家族**：`animateFloatAsState`、`animateDpAsState`、`animateColorAsState`、`animateSizeAsState`、`animateOffsetAsState`、`animateIntAsState`、`animateRectAsState` 等，按目标类型一一对应。共同特征是只接收一个 `targetValue` 参数，不暴露任何进度控制。

2. **返回值是 State<T>**：函数返回 `State<Float>`、`State<Dp>` 等，配合 `by` 委托解包成普通值。值被谁读取，动画就作用在谁身上——同一个 scale 既能喂给 scaleX 也能喂给 alpha，一个动画驱动多处。

3. **内部机制**：该 API 自带 `remember`，首次组合时创建一个内部动画对象（本质是 `Animatable`，第 52 章的主角）并把初始 targetValue 作为起始值。之后每次重组发现 targetValue 与当前值不同，就自动启动动画，从当前值插值到新目标。这就是「隐式动画」：你只管目标，过程交给系统。

4. **默认动画规格**：动画规格（`AnimationSpec`）就是描述动画「怎么动」的参数集合——时长、缓动曲线、是否回弹，第 55 章详解；不传 `animationSpec` 时，这一族函数的默认值是 `spring()`（弹簧物理动画规格：时长由阻尼、刚度推算，不是预设的固定时长，第 55 章详解），并不是 `tween`。需要固定时长或自定速度曲线时，才显式传 `tween(durationMillis = ..., easing = ...)`。

5. **label 参数**：给动画起名字，供 Android Studio 的 Animation 调试面板（Layout Inspector）识别。生产代码可省略，但调试动画问题时强烈建议写上。

6. **动画期间的更新范围是预期行为**：动画进行中，每帧都会产生一个新插值，影响范围取决于值在哪里被读取——`bgColor` 是在组合中求值的参数（`Modifier.background(bgColor)`），动画期间读取它的 `LikeButton` 这个作用域每帧重组；`scale` 写在 `Modifier.graphicsLayer { }` 的 lambda 里，这类延迟读取连重组都不触发，只让该节点的 layer 块重新执行、直接更新绘制层（第 56 章展开）。未读取动画值的部分完全不参与。这不是性能问题，反而是值动画的高效之处。

7. **与 AnimatedVisibility 的差异**：animate*AsState 只动「值」，内容本身始终在组合中；AnimatedVisibility 动的是「内容的出现与消失」，会在组合中增删内容。需要"内容移入/移出"时用 AnimatedVisibility，需要"某个属性平滑变化"时用 animate*AsState，二者互补。

记住：animate*AsState 的核心心智模型是「声明目标值，系统自动补间」，返回值是可被任意 UI 属性读取的 State。

## 5. 实际执行过程

1. 用户点击点赞按钮，父组件的 `isLiked: Boolean` 状态从 false 变为 true，触发重组。
2. LikeButton 重组时重新执行 animateFloatAsState 与 animateColorAsState，系统检测到 targetValue 从 1f→1.3f、Transparent→Color(0xFFFF6B81)。
3. 内部 Animatable 开始按 tween(220) 从当前值向新目标插值，同时请求下一帧。
4. 每帧产生新值（scale、bgColor），读取它们的 graphicsLayer 与 background 作用域重组，数字与心形逐帧放大、背景逐帧变红。
5. 约 220ms 后到达目标值，动画停止，不再触发重组，界面静止。
6. 用户再次点击取消点赞，isLiked 回到 false，同一套流程反向播放，scale 与颜色从当前值回到初始值。

## 6. Flutter / Dart 3 对照

相似点：Flutter 的隐式动画 widget 同样是声明式——你只给目标属性（颜色、尺寸、透明度），框架自动补间。

```dart
// Flutter: 隐式动画，目标值驱动
class LikeButton extends StatelessWidget {
  const LikeButton({super.key, required this.isLiked});

  final bool isLiked;

  @override
  Widget build(BuildContext context) {
    return AnimatedContainer(
      duration: const Duration(milliseconds: 220),
      curve: Curves.fastOutSlowIn,
      decoration: BoxDecoration(
        color: isLiked ? const Color(0xFFFF6B81) : Colors.transparent,
        borderRadius: BorderRadius.circular(16),
      ),
      padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 10),
      child: AnimatedScale(
        scale: isLiked ? 1.3 : 1.0,
        duration: const Duration(milliseconds: 220),
        child: const Text('❤ 128'),
      ),
    );
  }
}
```

本质区别：

1. **封装的粒度不同**：Flutter 的隐式动画是「Widget 封装」，AnimatedContainer 是一个自带动画状态的 StatefulWidget，你换属性它就动；Compose 是「值包装」，animateFloatAsState 返回一个不断变化的值，由你决定把它喂给哪个 Modifier 或参数。
2. **一个动画能驱动的属性数不同**：Flutter 里缩放用 AnimatedScale、透明度用 AnimatedOpacity、尺寸用 AnimatedContainer，各自独立计时；Compose 一个 animateFloatAsState 的值可以同时驱动 scaleX、scaleY、alpha、rotationZ，天然保证同步。
3. **组合方式不同**：Flutter 需要把动画 widget 嵌套进 widget 树对应位置；Compose 的动画值可以穿透任意层级的修饰符链。

## 7. 常见错误

1. **动画期间频繁改 targetValue**：例如把 targetValue 写成 `Random.nextFloat()`，每次重组目标值都不同，动画永远追不上目标，表现为持续抽搐。记住：目标值应当来自稳定状态（Boolean、枚举、数据字段），而非每帧变化的表达式。
2. **误以为要手写 remember 包裹**：`val x by remember { animateFloatAsState(...) }` 是多余的，API 内部已自带 remember。真正要注意的是别把它写在条件分支内部——组合被移除再回来时动画状态会重置，应从组合开始就存在。
3. **尺寸用 animateFloatAsState 处理**：用 Float 存 dp 值，还得自己记得乘 density；直接 `animateDpAsState` 由框架处理密度，语义清晰。
4. **在动画值读取处做重副作用**：动画期间该作用域每帧重组，若在里面做网络请求或写数据库，等于每秒 60 次。副作用请放 LaunchedEffect 或回调里，读取动画值只做 UI 计算。

## 8. 本章检查题

1. animateFloatAsState 的返回值是什么类型？为什么可以直接写 `val scale by animateFloatAsState(...)`？
2. targetValue 从 1f 变为 1.3f 后，内部发生了什么？动画结束后还会继续重组吗？
3. 动画进行期间每帧都在重组，这是不是性能问题？为什么？
4. 点赞数字要「放大 + 变色」两个属性同步动画，用两个 animate*AsState 是否合理？

答案：

1. 返回 `State<Float>`。`by` 委托调用 `getValue()` 解包出 Float 值；每次读到新值，读取作用域自动重组。
2. 系统检测到目标值变化，启动内部动画从当前值插值到 1.3f；每帧产生新值触发局部重组；到达目标后动画停止，不再触发重组。
3. 不是。只有「在组合中读取该动画值」的作用域会被重组（例中读取 bgColor 的 LikeButton）；写在 `graphicsLayer { }` lambda 里延迟读取的值（例中的 scale）连重组都不触发，只更新绘制层。其余 UI 不动，开销很小；这是值动画的预期工作方式。
4. 合理。它们各自记住独立状态、同步由同一状态驱动，且可分别配不同动画规格；需要严格同步多个属性时则用后续章节的 updateTransition。

## 9. 本章总结

animate*AsState 是 Compose 隐式动画的入口：声明目标值，框架自动补间并返回可读的 State，让最常用的数值、颜色、尺寸动画缩减到一行代码。它解决「单个值随状态变化自动动画」这一基本问题，是后续手动控制（Animatable）、内容过渡（AnimatedVisibility）与多属性编排（updateTransition）的基础。

官方参考：

- [Animation in Compose（animate*AsState 一节）](https://developer.android.com/develop/ui/compose/animation)
- [animateFloatAsState API](https://developer.android.com/reference/kotlin/androidx/compose/animation/core/animateFloatAsState)
