# 第 39 章：List、firstOrNull 与可空返回值

## 1. 本章目标

理解查询可能失败时如何用可空类型表达，并在 UI 中安全兜底。

学完后，你应当能够：

- 使用 firstOrNull
- 读懂 ComponentInfo?
- 使用安全调用与 Elvis
- 通过 null 分支和智能转换访问非空对象

## 2. 项目中的位置

- `model/ComponentInfo.kt`
- `ui/detail/ComponentDetailScreen.kt`



## 3. 项目源码片段

```kotlin
fun findById(id: String): ComponentInfo? =
    all.firstOrNull { it.id == id }

val info = ComponentRegistry.findById(componentId)
val title = info?.title ?: "未找到组件"

if (info == null) {
    Text("未找到组件: $componentId")
    return@Scaffold
}
Text(info.description)
```

## 4. 逐步解释

firstOrNull 从前向后检查条件，找到第一个匹配对象就返回，找不到返回 null。返回类型因此是 ComponentInfo?，调用方必须处理。

`info?.title` 只在 info 非空时读取 title，Elvis `?:` 为 null 提供默认值。显式 `if (info == null)` 并提前返回后，Kotlin 能智能转换后续 info 为非空。相比 `first` 或 `!!`，这种写法能安全应对非法深链、拼错路由或注册项被删除。

## 5. 实际执行过程

1. 接收 componentId
2. 遍历列表匹配 id
3. 返回对象或 null
4. 标题安全兜底
5. 内容区处理 null
6. 非空分支正常渲染

## 6. Flutter / Dart 3 对照

Dart 3 同样有可空类型和流程分析，检查 null 后可类型提升。

```dart
ComponentInfo? findById(String id) {
  for (final item in all) {
    if (item.id == id) return item;
  }
  return null;
}
final info = findById(id);
if (info == null) return const Text('未找到');
return Text(info.description);
```

## 7. 常见错误

- 对不可信 id 使用 first，找不到直接抛异常
- 使用 info!! 强制解包
- 只兜底标题却继续访问 null 内容
- 用空字符串掩盖路由错误

## 8. 本章检查题

1. first 与 firstOrNull 的差异是什么？
2. Elvis 操作符何时使用右侧值？
3. null 检查后为什么能直接访问 description？

答案：

1. first 找不到匹配项会抛异常；firstOrNull 返回 null，安全。
2. 左侧表达式为 null 时（`info?.title ?: "默认"` 中 info 为 null 时取右侧默认值）。
3. 显式 `if (info == null)` 提前返回后，Kotlin 智能转换（smart cast）把 info 视为非空，后续可直接访问其属性。

## 9. 本章总结

用可空返回表达“可能找不到”，并把 null 当成正常 UI 分支处理。
