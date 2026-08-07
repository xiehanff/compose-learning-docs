# 第 42 章：CompositionLocalProvider 与 LocalDensity

## 1. 本章目标

理解环境值如何沿组合树向下提供，以及覆盖范围为何只限子树。

学完后，你应当能够：

- 读取 LocalDensity.current
- 用 CompositionLocalProvider 覆盖值
- 理解最近 Provider 原则
- 区分环境依赖与业务参数

## 2. 项目中的位置

- `ui/adapter/DesignWidthScale.kt`
- `ui/theme/Theme.kt`



## 3. 项目源码片段

```kotlin
CompositionLocalProvider(
    LocalDensity provides designDensity,
) {
    content()
}

CompositionLocalProvider(
    LocalDemoTheme provides demoTheme,
) {
    MaterialTheme(content = content)
}
```

## 4. 逐步解释

CompositionLocal 是沿组合树向下传播的环境值。子节点读取 LocalDensity.current 时，会找到离自己最近的 Provider；父节点和兄弟子树不受内部覆盖影响。离开 Provider 范围后恢复外层值。

项目同样用 LocalDemoTheme 提供 Demo 尺寸配置。此机制适合主题、密度、语言等跨大量层级的环境信息，但会形成隐式依赖，不应取代所有明确业务参数。它也不是修改设备或线程的全局 density。

## 5. 实际执行过程

1. 外层存在默认值
2. Provider 在当前子树绑定新值
3. 后代读取 nearest value
4. 值变化使读取者重组
5. 范围外仍使用原值

## 6. Flutter / Dart 3 对照

Flutter 的 Theme、MediaQuery、Directionality 等 InheritedWidget 也沿树向下查找最近值。

```dart
MediaQuery(
  data: MediaQuery.of(context).copyWith(
    textScaler: const TextScaler.linear(1.0),
  ),
  child: const DemoBody(),
);
```

## 7. 常见错误

- 以为 Provider 永久修改整个 App
- 在 Provider 外读取却期待内部值
- 把所有业务数据塞 CompositionLocal
- 多层适配 Provider 导致规则混乱

## 8. 本章检查题

1. LocalDensity.current 如何选择值？
2. Provider 会影响父节点吗？
3. 哪些信息适合 CompositionLocal？

答案：

1. 读取时沿组合树向上找最近的 Provider，找到即用它提供的值。
2. 不会。Provider 只影响它内部的子树，父节点和兄弟子树不受影响，离开范围后恢复外层值。
3. 主题、密度、语言等需要跨大量层级传递的环境信息；不适合替代所有明确的业务参数。

## 9. 本章总结

CompositionLocalProvider 只覆盖组合子树，读取遵循最近 Provider 原则。
