# 第 80 章：无障碍、Baseline Profile 与发布质量

## 1. 本章目标

学完后，你应当能够：

- 用 semantics、contentDescription、clearAndSetSemantics 构建无障碍语义
- 遵守 48dp 触控目标与字体缩放规范，并用 TalkBack 实测验证
- 编写 baseline-prof.txt 规则并接入 profileinstaller，提升冷启动性能
- 掌握 R8 混淆、资源缩减与 AAB 发布的检查清单

## 2. 项目中的位置

进阶篇不再局限于 compose_demo 项目，本章使用通用 Compose API。

- `androidx.compose.ui.semantics`：semantics、clearAndSetSemantics、mergeDescendants
- `androidx.compose.foundation`：minimumInteractiveComponentSize（clickable 默认兜底）
- `androidx.profileinstaller:profileinstaller`、`androidx.benchmark:benchmark-macro-junit4`
- AGP 构建配置：minifyEnabled、shrinkResources、proguard-rules.pro、baseline-prof

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

// 纯装饰图标：读屏不应朗读，连同子树语义一起清空
Image(
    painter = rememberVectorPainter(Icons.Outlined.Star),
    contentDescription = null, // 传 null 只是不朗读自身
    modifier = Modifier.clearAndSetSemantics {} // 连子树语义一起清空
)

// 合并子语义：让"订单卡片"作为一个整体被朗读
Card(Modifier.semantics(mergeDescendants = true) {
    contentDescription = "订单，共 3 件商品，金额 128 元"
}) { /* 卡片内容 */ }
```

场景二：baseline-prof.txt 规则文件（app/src/main/baseline-prof.txt）。

```
# 启动路径上的类与热点方法
Lcom/example/app/MainActivity;
Lcom/example/app/MainActivity;->onCreate(Landroid/os/Bundle;)V
Lcom/example/app/ui/HomeScreenKt;->HomeScreen(Landroidx/compose/runtime/Composer;I)V

# 排除不需要预热的路径
!Lcom/example/app/debug/DebugOnlyClass;
!Lcom/example/app/Logger;->d(Ljava/lang/String;)V
```

## 4. 逐步解释

1. 语义是读屏的"台词本"：TalkBack 不会"看"界面，它朗读语义树。Text 默认朗读文本；Image 需要 contentDescription（传 null 表示纯装饰）；clickable 会自动给节点附加 click 动作语义。所以给可点击元素补充描述是最基本的无障碍。

2. mergeDescendants 的默认行为：Compose 语义默认不合并，但 clickable、Card 等交互组件内部会开启合并——子节点语义并入父节点，朗读时是"按钮：收藏"整体一句话，而不是逐字读。需要显式定制合并内容时，用 semantics(mergeDescendants = true) { contentDescription = ... } 覆盖。

3. clearAndSetSemantics：装饰性元素（背景图、纯图标、分割线）对读屏用户是噪音。Modifier.clearAndSetSemantics {} 会把自身及子树的所有语义清空，只保留一个空节点。注意 contentDescription = null 只让该节点不朗读，不影响子树——这正是两者不能互相替代的原因。

4. 触控目标与字体缩放：Material 规范要求触控目标 ≥48dp 物理尺寸。clickable 默认带 minimumInteractiveComponentSize 兜底，但自定义手势（如 pointerInput 画的拖拽手柄）要手动保证尺寸。字号方面，一律用 sp 语义（Text 默认就是 sp），不要全局改 LocalDensity 来"适配"字体——那会破坏系统无障碍缩放。

5. Baseline Profile 是什么：ART 的 JIT 在应用首次启动时逐方法解释 + 编译，冷启动慢。Baseline Profile 是预生成的 AOT 编译清单（baseline-prof.txt 规则 + profileinstaller 在安装后/启动时把 profile 装入设备），让热点类方法直接编译好，冷启动与首帧显著提速。规则格式：L类名; 表示整类，L类名;->方法签名 表示单个方法，! 前缀表示排除。

6. 验证与发布检查：用 Macrobenchmark（benchmark-macro-junit4）在真机上对比有无 Baseline Profile 的启动耗时；发布前检查 R8 混淆规则——反射、序列化、native、注解等动态引用的类要 keep；资源缩减 shrinkResources 会删除未引用资源，代码里动态引用（反射取资源 ID）的要 keep 或 tools:keep；用 App Bundle（AAB）而非 APK 发布，体积更小；至少在 minSdk 与主流 API 级别设备上跑回归。

记住：无障碍是功能不是加分项；性能优化要可测量；发布清单要逐项过。

## 5. 实际执行过程

1. 开发期：给所有有意义的图片补 contentDescription，装饰元素加 clearAndSetSemantics，检查触控目标与字体缩放。
2. TalkBack 实测：开启读屏，从首页扫到详情页，确认朗读顺序、合并文案、焦点可达性符合预期。
3. 性能期：编写 baseline-prof.txt，在 build.gradle 配置 profileinstaller 依赖与 profile 规则，AGP 打包时生成 profile 并随 APK/AAB 分发。
4. 验证期：用 Macrobenchmark 在 Pixel 等真机上测量冷启动时间，对比开启前后数据（通常可带来 20%~30% 的启动性能提升）。
5. 发布期：开启 minifyEnabled 与 shrinkResources，检查 R8 映射与 keep 规则；构建 AAB 检查体积；在 minSdk 与主流版本设备上跑回归测试。
6. 灰度发布：监听崩溃与 ANR 上报，异常时快速回滚。

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

相似点：语义模型同源（SemanticsNode 与语义树都是各自框架的无障碍抽象）；Semantics(label) 对应 contentDescription；ExcludeSemantics 对应 clearAndSetSemantics；语义合并（MergeSemantics）两个框架都有；触控目标 48dp 是共同规范；flutter_test 的 meetsGuideline 可自动检查触控目标。

本质区别：Baseline Profile 是 Android 特有的优化，Flutter 没有直接等价物——Dart AOT 编译在构建期已完成，Flutter 侧对应的是 Impeller / Skia 着色器预热这类首帧优化，所以 Baseline Profile 知识点只在 Android 侧成立。另外 Flutter 的语义合并用 MergeSemantics widget，粒度更显式；Compose 的 clickable / Card 默认合并，开箱即用但需要理解默认行为。

## 7. 常见错误

1. 图片没有描述或装饰图被读屏朗读：忘了 contentDescription，或装饰图只传 null 没清子树。有意义的图补描述，装饰图用 clearAndSetSemantics。
2. 触控目标小于 48dp：自定义手势区域做小，读屏用户与手指粗的用户都点不到。保证可交互区域 ≥48dp。
3. 用 px 写死字号或全局改 Density：破坏系统字体缩放，视障用户无法放大文字。用 sp 语义，尊重系统设置。
4. 发布版没装 Baseline Profile：写了规则却忘了加 profileinstaller 依赖或配置，规则根本没生效。
5. 混淆后反射类崩溃：R8 删掉了反射引用的类/方法。在 proguard-rules.pro 里加 keep 规则。
6. shrinkResources 误删资源：代码里动态引用（反射取资源 ID）被当作未使用。需要 keep 规则或 tools:keep 保留。

## 8. 本章检查题

1. 纯装饰图标为什么用 clearAndSetSemantics 而不是只传 contentDescription = null？
2. Baseline Profile 优化的是哪个阶段？如何验证收益？
3. R8 与资源缩减各有什么注意点？
4. mergeDescendants 的默认行为是怎样的？

答案：

1. contentDescription = null 只让该节点自身不朗读，子树语义仍在；装饰元素常包裹子元素，clearAndSetSemantics 会把整棵子树语义清空，彻底消除读屏噪音。
2. 优化冷启动与首帧：ART 通过预编译清单跳过 JIT 解释阶段。用 Macrobenchmark 在真机上对比开启前后的启动时间与首帧耗时。
3. R8：反射、序列化、native、注解等动态引用的类/方法需 keep 规则，否则运行期崩溃；资源缩减：只删确定未引用的资源，代码中动态引用（反射获取资源 ID）需 tools:keep 保留。
4. 语义默认不合并，但 clickable、Card 等交互组件默认开启合并，把子节点语义并入父节点，读屏时整体朗读；需要显式定制时用 semantics(mergeDescendants = true) { ... }。

## 9. 本章总结

发布质量 = 无障碍 + 性能 + 构建检查三件事：语义让读屏用户可用，Baseline Profile 让冷启动更快，R8 / 资源缩减 / AAB 让包更小更稳。三者遵循同一原则——可测量、可回归、上线前逐项验证。做完这三件事，应用才真正达到"可发布"标准。
