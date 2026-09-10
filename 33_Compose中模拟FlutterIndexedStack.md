# 第 33 章：Compose 中模拟 Flutter IndexedStack

## 1. 本章目标

比较条件渲染与所有面板保留在组合树中的差异。

学完后，你应当能够：

- 知道 when/if 会移除未选分支
- 使用 Box 叠放面板
- 使用 zIndex 改变绘制层级
- 识别隐藏面板的输入和资源成本

## 2. 项目中的位置

- `components/IndexedStackDemo.kt`



## 3. 项目源码片段

```kotlin
Box(Modifier.fillMaxWidth().height(156.px)) {
    Box(
        Modifier
            .matchParentSize()
            .zIndex(if (index == 0) 1f else 0f)
    ) { Text("面板 A") }

    Box(
        Modifier
            .matchParentSize()
            .zIndex(if (index == 1) 1f else 0f)
    ) { Text("面板 B") }
}
```

## 4. 逐步解释

使用 `when(index)` 时，只有当前分支存在；切换后旧分支离开组合，内部 remember 状态通常被丢弃。要接近 Flutter IndexedStack，就让全部面板始终存在于同一个 Box，再用 zIndex 把选中面板放到上层。

不过 zIndex 只控制绘制层级，不会自动停止未选面板的点击、语义、副作用或资源消耗。真实实现要同时处理 alpha、输入、无障碍（无障碍，第 80 章详解）和生命周期，或者采用导航 saveState、SaveableStateHolder 等更合适方案。

## 5. 实际执行过程

1. 全部面板进入组合
2. 各自状态保持
3. index 改变
4. zIndex 重新计算
5. 目标面板显示在上层
6. 其他面板仍然存在

## 6. Flutter / Dart 3 对照

Flutter IndexedStack 原生保留所有 children，只显示指定 index。Compose 需要自行组合保活和显隐策略。

```dart
IndexedStack(
  index: index,
  children: const [HomePage(), SearchPage(), SettingsPage()],
);
```

## 7. 常见错误

- 未选面板仍拦截点击
- 所有重型页面长期运行
- 以为 zIndex 会取消未选页面副作用
- 使用 when 却期待 remember 永远保留

## 8. 本章检查题

1. 条件渲染为什么可能丢状态？
2. zIndex 改变的是存在性还是层级？
3. 保留所有页面有什么代价？

答案：

1. when 切换时旧分支离开组合，其内部 remember 状态通常被丢弃。
2. zIndex 只改变绘制层级，不改变存在性；所有面板仍在组合中。
3. 所有页面常驻组合，持续占用布局、内存、语义与资源；真实实现还需处理 alpha、输入、无障碍和生命周期。

## 9. 本章总结

IndexedStack 语义的核心是保留所有页面，但完整实现还要处理输入、语义、副作用和资源成本。
