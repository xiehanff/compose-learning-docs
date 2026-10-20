# 第 36 章：ComponentInfo 类及其四个字段

## 1. 本章目标

理解项目用普通 Kotlin 类把 Demo 元数据和可执行 UI 组合成统一模型。

学完后，你应当能够：

- 看懂构造参数和 val 属性
- 理解四个字段的用途
- 知道类本身不需要 @Composable
- 理解普通 class 与 data class 的取舍

## 2. 项目中的位置

- `model/ComponentInfo.kt`



## 3. 项目源码片段

```kotlin
class ComponentInfo(
    val id: String,
    val title: String,
    val description: String,
    val composeExample: @Composable () -> Unit,
)
```

## 4. 逐步解释

id 同时用于路由参数、列表 key 和查询；title 用于首页和详情标题；description 是说明文本；composeExample 保存稍后可执行的 UI 函数。`@Composable () -> Unit` 可读作「一个带 Compose 标记、没有参数、调用后不返回值的函数」；第 37 章会专门讲这种函数值。

ComponentInfo 只是数据模型，不直接绘制，所以类不需要 @Composable。当前使用普通 class，因为项目只创建条目并读取属性，不依赖 copy、componentN 或结构相等。若改成 data class，要考虑函数字段的比较是否符合业务含义。

## 5. 实际执行过程

1. 顶层创建 ComponentInfo 对象
2. Registry 收集对象
3. 首页读取 id/title
4. 详情读取 description
5. 详情调用 composeExample

## 6. Flutter / Dart 3 对照

Dart 3 可用 final class 保存字符串和 `Widget Function()`。

```dart
final class ComponentInfo {
  const ComponentInfo({
    required this.id,
    required this.title,
    required this.description,
    required this.buildExample,
  });
  final String id;
  final String title;
  final String description;
  final Widget Function() buildExample;
}
```

## 7. 常见错误

- id 使用会变化的标题
- 把 ComponentInfo 当页面实例
- composeExample 写成立即调用
- 盲目改 data class 而不需要结构语义

## 8. 本章检查题

1. id 至少承担哪些作用？
2. 为什么类不需要 @Composable？
3. composeExample 的类型是什么？

答案：

1. 同时承担路由参数、列表 key 和查询定位三种作用。
2. ComponentInfo 只是数据模型，本身不直接绘制 UI，不需要 @Composable。
3. `@Composable () -> Unit`，保存稍后可执行的 UI 函数。

## 9. 本章总结

ComponentInfo 是统一 Demo 描述模型，让首页和详情页读取同一份数据。
