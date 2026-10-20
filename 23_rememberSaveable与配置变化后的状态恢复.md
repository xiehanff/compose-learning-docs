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
- `components/StateDemo.kt`

StateDemo.kt 的第三小节用 `rememberSaveable` 与 `remember` 做对照（旋转屏幕后前者保留、后者重置），是本章的实践入口。

## 3. 项目源码片段

<!-- compile -->
```kotlin
// components/StateDemo.kt:135：只要跨重组保留，用 remember
var plainCount by remember { mutableIntStateOf(0) }

// components/StateDemo.kt:136：需要跨配置变化恢复时改用 rememberSaveable
var savedCount by rememberSaveable { mutableStateOf(0) }

// components/AlertDialogDemo.kt:35：弹窗是否显示这类临时状态，项目里只用 remember 保存
var showDialog by remember { mutableStateOf(false) }
```

## 4. 逐步解释

普通 remember 能跨重组保留，但组合被销毁后就没有保证。屏幕旋转、Activity 重建或导航目的地重建时，值可能回到初始状态。`rememberSaveable` 会借助 Compose 与 Android 的保存状态机制恢复基础可保存值；不需要自己直接操作底层注册表。

Boolean、String、Int 等通常可直接保存。自定义类需要提供 `Saver`：它说明「把对象拆成哪些受支持的小值来保存，以及如何用这些值重新构造对象」。例如，筛选条件可只保存查询词与是否仅看收藏；实际拆分与恢复写法见第 72 章的 `listSaver` 示例。只保存恢复 UI 所需的最小字段。

`rememberSaveable` 不是数据库：它适合恢复小型 UI 状态，不保证用户主动结束应用后仍能恢复。长期业务数据（如订单）应写入持久层；ViewModel 的轻量恢复状态会在第 68 章介绍。

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
3. 订单等长期业务数据应只放 rememberSaveable 吗？

答案：

1. 不一定。普通 remember 能跨重组保留，但组合被销毁（旋转、Activity 重建、导航重建）后没有保证。
2. Boolean、String、Int 等基础可保存类型；自定义类需要 Saver，并只保存恢复 UI 所需的最小字段。
3. 不应。rememberSaveable 不是数据库，订单等长期业务数据应放在持久层；ViewModel 的轻量恢复状态另有机制。

## 9. 本章总结

rememberSaveable 用于恢复轻量 UI 状态，不是永久持久化。项目里 `components/StateDemo.kt` 已用 `rememberSaveable` 与 `remember` 做对照，可直接旋转屏幕观察两者差异。
