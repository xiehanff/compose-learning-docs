# 第 37 章：@Composable () -> Unit 作为可运行 UI 数据

## 1. 本章目标

理解函数可以保存为值，以及保存函数与立即调用函数的区别。

学完后，你应当能够：

- 读懂无参数函数类型
- 理解 @Composable 修饰函数类型
- 区分 `{ Demo() }` 与 `Demo()`
- 理解详情页的动态调用

## 2. 项目中的位置

- `model/ComponentInfo.kt`
- `ui/detail/ComponentDetailScreen.kt`



## 3. 项目源码片段

```kotlin
val composeExample: @Composable () -> Unit

val TextDemo = ComponentInfo(
    id = "text",
    title = "Text",
    description = "...",
    composeExample = { TextExample() },
)

info.composeExample()
```

## 4. 逐步解释

`() -> Unit` 表示不接收参数、没有业务返回值的函数。加上 @Composable 后，它只能在可组合环境调用并可以生成 UI。注册时 `{ TextExample() }` 创建 Lambda 并保存，不会马上执行。

首页只读取 title；进入详情页后才调用 `info.composeExample()`。因此通用详情页不需要为每种组件写 when。若写 `composeExample = TextExample()`，就变成立即调用，并且返回 Unit，类型不匹配。

## 5. 实际执行过程

1. 创建 Lambda
2. 保存到 ComponentInfo
3. 首页不执行它
4. 详情查询对应对象
5. 在 Composable 区域调用
6. 具体 Demo 开始组合

## 6. Flutter / Dart 3 对照

Flutter 中可保存 `Widget Function()` 或 WidgetBuilder，稍后由通用页面调用。

```dart
final info = ComponentInfo(
  id: 'text',
  title: 'Text',
  description: '...',
  buildExample: () => const TextDemo(),
);
final widget = info.buildExample();
```

## 7. 常见错误

- 注册时立即调用函数
- 在普通函数调用 Composable Lambda
- 误解 Unit 为函数不会执行
- 全局 Lambda 捕获不应长期持有的大对象

## 8. 本章检查题

1. `{ TextExample() }` 与 `TextExample()` 有什么区别？
2. 为什么类型上要写 @Composable？
3. 详情页为什么不需要 when？

答案：

1. `{ TextExample() }` 创建 Lambda 并保存、不立即执行；`TextExample()` 是立即调用，返回 Unit。
2. 声明这个函数只能在可组合环境调用，并且可以生成 UI。
3. 详情页统一调用 `info.composeExample()` 即可展示任意组件，不需要为每种组件写 when 分支。

## 9. 本章总结

把 Composable Lambda 存为数据，实现了通用模板和具体 UI 的解耦。
