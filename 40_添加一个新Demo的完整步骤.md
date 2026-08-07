# 第 40 章：添加一个新 Demo 的完整步骤

## 1. 本章目标

独立添加一个组件示例，并理解数据驱动架构为何无需修改首页和详情模板。

学完后，你应当能够：

- 新建 Demo 文件
- 定义唯一 id 和说明
- 提供 Composable 示例 Lambda
- 注册到 ComponentRegistry
- 验证导航和主题

## 2. 项目中的位置

- `model/ComponentInfo.kt`
- `components/TextDemo.kt`
- `ui/home/HomeScreen.kt`
- `ui/detail/ComponentDetailScreen.kt`



## 3. 项目源码片段

```kotlin
val BadgeDemo = ComponentInfo(
    id = "badge",
    title = "Badge",
    description = "Badge 用于显示角标。",
    composeExample = { BadgeExample() },
)

@Composable
private fun BadgeExample() {
    Badge { Text("3") }
}

// ComponentRegistry.all 追加 BadgeDemo
```

## 4. 逐步解释

在 components 包创建文件，定义顶层 ComponentInfo 和私有示例函数。id 必须稳定、唯一并适合路由。然后导入对象并追加到 Registry.all。

首页遍历 all 自动生成入口；点击把 id 传给导航；详情页 findById 后调用 composeExample。因此无需给 HomeScreen 新写按钮，也无需在详情页增加 when。完成后应测试入口、跳转、返回、暗色主题、横竖屏和小屏布局。

## 5. 实际执行过程

1. 编写元数据和示例
2. 注册表包含新条目
3. 首页自动显示
4. 点击传出 id
5. 详情查询并执行示例
6. 测试完整链路

## 6. Flutter / Dart 3 对照

Flutter 也可用 List<ComponentInfo> 驱动目录和通用详情页。

```dart
final badgeDemo = ComponentInfo(
  id: 'badge',
  title: 'Badge',
  description: 'Badge 用于显示角标。',
  buildExample: () => const BadgeExample(),
);
final all = [...existing, badgeDemo];
```

## 7. 常见错误

- id 与已有条目重复
- 创建文件但忘记注册
- composeExample 立即调用
- 示例强依赖 NavController，无法独立复用

## 8. 本章检查题

1. 新增 Demo 最少改哪些位置？
2. 为什么首页不需要手写新按钮？
3. 如何保证 id 唯一？

## 9. 本章总结

新增流程是“定义 ComponentInfo + 注册到 all”。其余首页、路由和详情由通用结构自动完成。
