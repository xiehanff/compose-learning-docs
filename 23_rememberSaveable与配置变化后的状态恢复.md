# 第 23 章：rememberSaveable 与配置变化后的状态恢复

## 1. 本章目标

理解项目当前 remember 的保存边界，以及表单、页签等状态何时需要 rememberSaveable。

学完后，你应当能够：

- 区分重组、配置变化和进程重建
- 知道 remember 只跟随当前组合实例
- 知道 rememberSaveable 可写入保存状态
- 理解复杂对象需要 Saver

## 2. 项目中的位置

- `components/AlertDialogDemo.kt`
- `components/DatePickerDemo.kt`

本项目当前没有直接调用 `rememberSaveable`，这里用于说明真实项目的状态恢复选择。

## 3. 项目源码片段

```kotlin
// 项目当前写法
var showDialog by remember { mutableStateOf(false) }

// 需要跨配置变化恢复时
var selectedTab by rememberSaveable {
    mutableIntStateOf(0)
}
```

## 4. 逐步解释

普通 remember 能跨重组保留，但组合被销毁后就没有保证。屏幕旋转、Activity 重建或导航目的地重建时，值可能回到初始状态。`rememberSaveable` 会借助 SaveableStateRegistry 和 Android 保存状态机制恢复基础可保存值。

Boolean、String、Int 等通常可直接保存；自定义类需要 Saver，最好只保存恢复 UI 所需的最小字段。rememberSaveable 仍不是数据库：登录令牌、订单和长期业务数据应放持久层或 ViewModel/SavedStateHandle。弹窗是否正在显示往往不必恢复，而用户输入、筛选条件和 Tab 索引通常值得保存。

## 5. 实际执行过程

1. 首次组合创建 saveable 状态
2. 状态变化被保存状态注册表记录
3. 配置变化前系统保存可保存值
4. 新组合创建
5. rememberSaveable 恢复同一位置的值

## 6. Flutter / Dart 3 对照

Flutter 可通过 RestorationMixin 和 RestorableProperty 实现系统状态恢复。普通 State 字段与 Compose remember 类似，只覆盖当前 Widget 实例生命周期。

```dart
class DemoState extends State<Demo> with RestorationMixin {
  final RestorableInt tab = RestorableInt(0);
  @override String? get restorationId => 'demo';
  @override void restoreState(RestorationBucket? old, bool initial) {
    registerForRestoration(tab, 'tab');
  }
}
```

## 7. 常见错误

- 把 rememberSaveable 当作长期数据库
- 把大 Bitmap 或连接对象直接保存
- 所有临时弹层状态都恢复，造成返回页面自动弹出旧弹窗
- 没有为自定义类型提供 Saver

## 8. 本章检查题

1. 普通 remember 在 Activity 重建后一定保留吗？
2. 哪些类型适合直接 saveable？
3. 登录令牌应只放 rememberSaveable 吗？

## 9. 本章总结

rememberSaveable 用于恢复轻量 UI 状态，不是永久持久化。项目当前没有直接使用它，本章解释的是现有 remember 的边界。
