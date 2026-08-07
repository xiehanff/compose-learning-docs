# 第 80 章：无障碍、Baseline Profile 与发布质量

## 1. 本章目标

学完后，你应当能够：

- 用 semantics、contentDescription、hideFromAccessibility 与 clearAndSetSemantics 构建无障碍语义
- 遵守 48dp 触控目标与字体缩放规范，并用 TalkBack 实测验证
- 使用 Baseline Profile Generator、Gradle 插件和关键用户路径生成并验证 Profile
- 掌握 R8 混淆、资源缩减与 AAB 发布的检查清单

## 2. 项目中的位置

进阶篇不再局限于 compose_demo 项目，本章使用通用 Compose API。

- `androidx.compose.ui.semantics`：semantics、clearAndSetSemantics、mergeDescendants
- `androidx.compose.foundation`：clickable、sizeIn 与 Material 最小触控目标
- `androidx.benchmark:benchmark-macro-junit4`、`androidx.baselineprofile` Gradle 插件
- AGP 构建配置：`isMinifyEnabled`、`isShrinkResources`、`proguard-rules.pro`

## 3. 项目源码片段

场景一：为有意义的图片提供无障碍描述；纯装饰图标从语义树彻底移除；卡片语义合并。

```kotlin
// 有意义的图片：读屏会朗读 contentDescription
Image(
    painter = rememberVectorPainter(Icons.Outlined.Star),
    contentDescription = "收藏",
    modifier = Modifier
        .size(24.dp)
        .clickable { toggleFavorite() }
)

// 纯装饰图片：Image 没有需要表达的含义时传 null 即可
Image(
    painter = rememberVectorPainter(Icons.Outlined.Star),
    contentDescription = null
)

// 装饰性父容器只需对无障碍隐藏、但仍希望测试读取语义时
Box(Modifier.semantics { hideFromAccessibility() }) {
    /* watermark or decorative content */
}

// 合并子语义：让"订单卡片"作为一个整体被朗读
Card(Modifier.semantics(mergeDescendants = true) {
    contentDescription = "订单，共 3 件商品，金额 128 元"
}) { /* 卡片内容 */ }
```

场景二：在 Android Studio 创建 Baseline Profile Generator 模块，并用自动化关键用户路径收集规则。

```kotlin
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun generate() = baselineProfileRule.collect(
        packageName = "com.example.app"
    ) {
        startActivityAndWait()
        // 继续执行真实关键用户路径：进入列表、滚动、打开详情等
    }
}
```

## 4. 逐步解释

1. 语义是读屏的"台词本"：TalkBack 不会"看"界面，它朗读语义树。Text 默认朗读文本；Image 需要 contentDescription（传 null 表示纯装饰）；clickable 会自动给节点附加 click 动作语义。所以给可点击元素补充描述是最基本的无障碍。

2. mergeDescendants 的默认行为：Compose 语义默认不合并；`clickable`、`toggleable`、Button，以及使用可点击重载的 Card 等交互节点会合并后代语义。普通、不可点击的 Card 不能一概认为会自动合并。需要把一组信息表达成整体时，可以显式使用 `semantics(mergeDescendants = true)`，但应避免把内部仍需独立操作的按钮一起吞并。

3. 隐藏、清除与覆盖不是同一件事：单个纯装饰 Image 使用 `contentDescription = null`；内容只需对无障碍服务隐藏、但仍希望测试等消费者访问时，可以使用 `hideFromAccessibility()`；`clearAndSetSemantics {}` 会清除自身和后代对无障碍、测试、自动填充等消费者提供的原语义，适合确实需要整体清除或重新定义自定义控件语义的场景，不应给所有装饰图片机械套用。

4. 触控目标与字体缩放：Material 规范要求触控目标至少 48dp。Compose 会扩展过小 clickable 的触摸区域，但相邻小控件可能因此出现重叠，所以仍应使用 `sizeIn(minWidth = 48.dp, minHeight = 48.dp)` 等方式提供足够布局空间；自定义 pointerInput 更要手动保证尺寸。字号使用 sp，并尊重系统字体缩放，不要全局篡改 Density。

5. Baseline Profile 是什么：它记录应用启动和关键用户路径中的热点代码，帮助 ART 提前优化这些路径，改善首次启动、滚动和导航等体验。当前推荐通过独立 Generator/Benchmark 模块、BaselineProfileRule 与 Gradle 插件自动生成，而不是让初学者手工猜测类和方法规则。生成任务会把结果复制到目标 app 对应的 generated baselineProfiles 源目录，并由构建工具打包。

6. 验证与发布检查：用 Macrobenchmark 在物理设备上分别以 `CompilationMode.None` 和 `CompilationMode.Partial` 等明确模式比较，不能只检查文件是否存在。发布前检查 R8 缺失类和必要 Keep 规则；资源缩减要覆盖动态资源引用；生成 AAB 并在最低、主流和目标 API 设备上回归。ProfileInstaller 是否需要显式依赖取决于 AGP、安装方式和支持的 Android 版本，不应写成所有现代项目的固定必选步骤。

记住：无障碍是功能不是加分项；性能优化要可测量；发布清单要逐项过。

## 5. 实际执行过程

1. 开发期：给有意义的图片补 contentDescription，纯装饰 Image 传 null；只有需要隐藏整组内容或重写语义时才选择 hideFromAccessibility/clearAndSetSemantics；同时检查触控目标与字体缩放。
2. TalkBack 实测：开启读屏，从首页扫到详情页，确认朗读顺序、合并文案、焦点可达性符合预期。
3. 性能期：使用 Android Studio 模板创建 Baseline Profile Generator 模块，编写覆盖启动、列表滚动和核心导航的关键用户路径。
4. 生成期：运行 `:app:generateBaselineProfile` 或实际变体对应任务，确认生成文件被复制到目标模块；任务名以当前插件和项目配置为准。
5. 验证期：在固定物理设备和构建条件下使用 Macrobenchmark 比较不同 CompilationMode，记录中位数和波动，不预设固定百分比收益。
6. 发布期：开启 `isMinifyEnabled` 与 `isShrinkResources`，检查 R8 映射和必要 Keep 规则；构建 AAB；在最低、主流和目标 API 设备上回归。
7. 灰度发布：监听崩溃与 ANR 上报，异常时快速回滚。

## 6. Flutter / Dart 3 对照

```dart
// 有意义的图片：标签 + 按钮语义
Semantics(
  label: '收藏',
  button: true,
  child: IconButton(
    onPressed: toggleFavorite,
    icon: const Icon(Icons.star_outline),
  ),
)
// 纯装饰元素：整体排除语义
ExcludeSemantics(
  child: DecoratedBox(decoration: ...),
)
// 无障碍自动测试：触控目标 ≥48dp
testWidgets('触控目标测试', (tester) async {
  final handle = tester.ensureSemantics();
  expect(find.byType(MyButton), meetsGuideline(androidTapTargetGuideline));
  handle.dispose();
});
```

相似点：两端都有语义树；Semantics(label) 与 contentDescription 都用于表达含义；ExcludeSemantics 与 Compose 的隐藏/清除 API 目的相近但作用范围不完全等价；两端都有语义合并和最小触控目标规范。

本质区别：Baseline Profile 是 Android 特有的优化，Flutter 没有直接等价物。Flutter 的语义合并用 MergeSemantics widget；Compose 的 clickable/toggleable 和部分交互组件会合并后代，但普通容器不会自动获得相同行为。

## 7. 常见错误

1. 图片语义使用过度或不足：有意义的图片缺少描述；纯装饰 Image 却添加重复描述；或者为了隐藏一个图片清掉整个父子树。按“表达含义、仅隐藏、整体重写”分别选 API。
2. 触控目标小于 48dp：自定义手势区域做小，读屏用户与手指粗的用户都点不到。保证可交互区域 ≥48dp。
3. 用 px 写死字号或全局改 Density：破坏系统字体缩放，视障用户无法放大文字。用 sp 语义，尊重系统设置。
4. 手工猜规则或只看生成文件：Profile 应由真实关键用户路径生成，并使用 Macrobenchmark 验证实际收益。
5. 混淆后反射类崩溃：R8 删掉了反射引用的类/方法。在 proguard-rules.pro 里加 keep 规则。
6. shrinkResources 误删资源：代码里动态引用（反射取资源 ID）被当作未使用。需要 keep 规则或 tools:keep 保留。

## 8. 本章检查题

1. contentDescription = null、hideFromAccessibility 和 clearAndSetSemantics 分别适合什么场景？
2. Baseline Profile 优化什么？当前推荐如何生成和验证？
3. R8 与资源缩减各有什么注意点？
4. mergeDescendants 的默认行为是怎样的？

答案：

1. 纯装饰 Image 使用 contentDescription = null；仅对无障碍隐藏但保留给测试等消费者时使用 hideFromAccessibility；需要清除或覆盖自身及后代的全部原语义时才使用 clearAndSetSemantics。
2. 它帮助 ART 提前优化启动和其他关键用户路径中的热点代码。使用 Generator 模块和 BaselineProfileRule 自动生成，再在固定物理设备上用 Macrobenchmark 和明确的 CompilationMode 对比。
3. R8：反射、序列化、native、注解等动态引用的类/方法需 keep 规则，否则运行期崩溃；资源缩减：只删确定未引用的资源，代码中动态引用（反射获取资源 ID）需 tools:keep 保留。
4. 语义默认不合并；clickable、toggleable、Button 和部分交互组件会合并后代。普通不可点击容器不应假定自动合并；自定义逻辑组可以显式设置 mergeDescendants。

## 9. 本章总结

发布质量 = 无障碍 + 可测性能 + 可复现构建检查。语义让辅助技术用户真正可用；由关键用户路径生成并经 Macrobenchmark 验证的 Baseline Profile 改善真实体验；R8、资源缩减和 AAB 则需要发布前回归。任何“优化已生效”的结论都必须有工具输出或基准数据支持。

官方参考：

- [Compose semantics](https://developer.android.com/develop/ui/compose/accessibility/semantics)
- [Accessibility API defaults](https://developer.android.com/develop/ui/compose/accessibility/api-defaults)
- [Create Baseline Profiles](https://developer.android.com/topic/performance/baselineprofiles/create-baselineprofile)
