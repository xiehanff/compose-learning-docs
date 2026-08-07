# 第 76 章：Compose UI 测试：语义树与交互测试

## 1. 本章目标

学完后，你应当能够：

- 搭建 Compose UI 测试环境，区分仪器测试（androidTest）与 Robolectric 单元测试
- 理解语义树（Semantics tree）的生成规则，用 onNodeWith* 系列 API 定位节点
- 模拟点击、输入、滚动等用户交互，并用断言验证 UI 状态
- 处理异步加载与动画等待，写出稳定不 flaky 的测试

## 2. 项目中的位置

进阶篇不再局限于 compose_demo 项目，本章使用通用 Compose API。

- `androidx.compose.ui:ui-test-junit4`：测试规则、交互与断言
- `androidx.compose.ui:ui-test-manifest`：仪器测试所需的测试 Activity
- `androidx.compose.ui.test.junit4.createComposeRule` / `createAndroidComposeRule`
- `androidx.compose.ui.test`：SemanticsMatcher（hasText、hasContentDescription 等）与 perform*、assert* 系列扩展
- 仪器测试放在 src/androidTest；配合 Robolectric 时放在 src/test

## 3. 项目源码片段

被测组件是一个计数器：点击 "+1" 按钮，文本随之增加。测试要验证这条交互链路。

```kotlin
@Composable
fun Counter() {
    var count by remember { mutableStateOf(0) }
    Column {
        Text("当前计数：$count", Modifier.testTag("countText"))
        Button(onClick = { count++ }) {
            Text("+1")
        }
    }
}

@RunWith(AndroidJUnit4::class)
class CounterTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun 点击加一按钮_计数增加() {
        // 1. 建立测试宿主
        rule.setContent { Counter() }
        // 2. 按文本找到按钮并点击
        rule.onNodeWithText("+1").performClick()
        // 3. 按 testTag 找到文本节点并断言
        rule.onNodeWithTag("countText").assertTextEquals("当前计数：1")
    }
}
```

## 4. 逐步解释

1. setContent 是测试宿主：createComposeRule() 会在内部启动一个空的测试 Activity 并执行 setContent，把被测的可组合函数放进去。它比 createAndroidComposeRule 更轻量，后者用于需要真实 Activity（读取 intent、依赖 fragment 等）的场景。

2. 语义树是测试的"眼睛"：Compose 在渲染的同时会为每个携带语义的节点构建一棵语义树。Text、Button、Image 默认都带语义；测试不直接找 View，而是通过 SemanticsNodeInteraction 在这棵树上查找。这是 Compose 测试与旧 View 体系测试（Espresso 找控件）的本质区别：测试面向"语义"而非"实现"。

3. 节点查找：onNodeWithText("+1") 按文本内容查找；onNodeWithTag 按 Modifier.testTag 查找——testTag 不显示在界面上，是专门给测试与语义用的"锚点"；onNodeWithContentDescription 按无障碍描述查找；更通用的写法是 onNode(hasText("+1"))——hasText 是一个 SemanticsMatcher，还能与 hasClickAction()、hasAnyDescendant(hasText(...)) 等组合成精确条件。

4. 交互与自动等待：performClick() 触发点击。测试框架在每次交互与断言前后会自动 waitForIdle，等待组合、布局、绘制完成，所以点击后的重组无需手动刷新——这是比 Flutter widget test 省心的地方。performScrollToNode(matcher) 在滚动容器内滚动到目标；performTextInput("hello") 模拟键盘输入；performTouchInput { swipeUp() } 模拟手势。

5. 断言：assertExists() 验证节点存在；assertIsDisplayed() 验证节点在屏幕内可见；assertTextEquals(...) 精确匹配文本。断言失败时，框架会输出当前语义树快照与差异，方便排查。

6. 合并与 testTag：clickable、Button 等组件默认开启语义合并（mergeDescendants 的默认行为），内部 Text 的语义并入按钮节点，所以 onNodeWithText("+1") 直接找到按钮。若父节点显式设置 mergeDescendants = true，子节点文本会并入父节点，此时想找子节点需用 useUnmergedTree = true 查询未合并树。

7. 异步等待：界面要等网络返回才显示内容时，必须显式等待：rule.waitUntil(timeoutMillis = 5_000) { rule.onAllNodesWithText("加载完成").fetchSemanticsNodes().isNotEmpty() }。动画时间由 rule.mainClock 控制，可用 advanceTimeBy 快进，不必真实等待。

记住：测试写的是"用户行为"而非"实现细节"——优先按文本、标签、描述查找，而不是按坐标。

## 5. 实际执行过程

1. 运行测试时，AndroidJUnit4 在测试设备/模拟器上启动测试 Activity，初始化 Compose 测试规则。
2. setContent 把 Counter 组合进宿主，框架等待首次组合与布局完成（idle）。
3. onNodeWithText("+1") 遍历语义树，找到唯一匹配的按钮节点并缓存。
4. performClick() 向该节点分发点击事件，触发 count++，状态变化引发重组。
5. 框架再次自动等待 idle，确保重组完成。
6. onNodeWithTag("countText") 定位文本节点，assertTextEquals 校验内容为"当前计数：1"。
7. 断言通过则测试绿；失败则打印语义树快照与期望差异，帮助定位。

## 6. Flutter / Dart 3 对照

Flutter 用 flutter_test 写 widget test，步骤几乎一一对应：

```dart
testWidgets('点击加一按钮_计数增加', (tester) async {
  await tester.pumpWidget(const CounterApp());       // 对应 setContent
  await tester.tap(find.text('+1'));                 // 对应 onNodeWithText(...).performClick()
  await tester.pump();                               // 手动刷新一帧
  expect(find.text('当前计数：1'), findsOneWidget);  // 对应 assertTextEquals
});
```

相似点：都是"建立宿主 → 查找节点 → 模拟交互 → 断言状态"四步；find.text / find.byKey 对应 onNodeWithText / onNodeWithTag；tester.tap 对应 performClick。

本质区别：Flutter 的 widget test 直接在 widget 树上查找（find 匹配 widget 实例），而 Compose 测试走独立的语义树——语义树是面向读屏与测试的抽象，与 View 实现解耦。另一个区别是等待机制：Compose 测试在交互前后自动 waitForIdle，Flutter 需要手动 pump / pumpAndSettle 推进帧，所以同样的异步场景，Compose 测试更不容易出现"忘刷新"导致的 flaky。

## 7. 常见错误

1. 测试依赖真实网络或真实时间：用例里发起 HTTP 请求或 Thread.sleep。应注入假数据源，动画用 mainClock.advanceTimeBy 快进，否则测试又慢又不稳定。
2. onNodeWithText 匹配到多个节点：页面上多处相同文本（如两个"+1"）会直接抛异常。改用 onAllNodesWithText(...).onFirst()，或追加 hasAnyDescendant 等条件缩小范围。
3. 忘记等待异步状态：网络返回后立即断言，结果时好时坏。必须 waitUntil 等待条件成立，而不是靠运气。
4. 给纯绘制组件找节点：Canvas / drawBehind 画的图形没有语义，测试找不到。要么补 testTag + semantics，要么改测其所在容器。
5. 在仪器测试里用 Thread.sleep：阻塞测试线程反而破坏自动 idle 机制，应使用 waitUntil 或 mainClock 控制时间。

## 8. 本章检查题

1. 语义树是什么？为什么测试不直接操作 View？
2. performClick() 之前为什么不需要手动等待重组？
3. 如何等待一个异步加载完成后出现的节点？
4. onNodeWithText 找到多个节点时会怎样？如何解决？

答案：

1. 语义树是 Compose 为每个携带语义的节点建立的抽象树，面向读屏与测试。测试通过语义树定位节点，与具体 View 解耦，这正是声明式 UI 可测试性的基石。
2. 因为测试框架在每次交互与断言前会自动 waitForIdle，等待组合、布局、绘制完成；只有动画或自定义时钟场景才需要手动控制 mainClock。
3. 用 rule.waitUntil(timeoutMillis) { ... }，在条件闭包里检查目标节点是否已出现在语义树上（如 onAllNodesWithText(...).fetchSemanticsNodes().isNotEmpty()）。
4. 会抛出"匹配到多个节点"的异常。改用 onAllNodesWithText(...) 取 onFirst()，或追加 hasAnyDescendant(hasText(...)) 等 matcher 缩小匹配范围。

## 9. 本章总结

Compose UI 测试建立在语义树之上：setContent 建立宿主，onNodeWith* 定位节点，perform* 模拟交互，assert* 验证结果，框架自动等待 idle 保证稳定。把测试当作"用户视角的验收单"，优先用文本、标签、描述而非坐标定位节点，就能写出可维护、抗重构的 UI 测试。
