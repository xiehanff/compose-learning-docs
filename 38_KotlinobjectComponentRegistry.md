# 第 38 章：Kotlin object ComponentRegistry

## 1. 本章目标

理解 Kotlin object 的唯一实例语义，以及它为何适合只读组件目录。

学完后，你应当能够：

- 知道 object 不需要 new
- 理解全局访问和初始化
- 区分只读注册定义与动态业务状态
- 认识单例的测试和生命周期风险

## 2. 项目中的位置

- `model/ComponentInfo.kt`



## 3. 项目源码片段

```kotlin
object ComponentRegistry {
    val all: List<ComponentInfo> = listOf(
        ColumnRowDemo,
        BoxDemo,
        TextDemo,
    )

    fun findById(id: String): ComponentInfo? =
        all.firstOrNull { it.id == id }
}
```

## 4. 逐步解释

`object` 同时声明类型和进程内唯一实例，调用直接写 `ComponentRegistry.all`。当前 all 是代码中固定的 Demo 定义表，所有页面共享，适合作为单例。

object 不应成为任意全局状态仓库。不要在其中持有 Activity、View、临时页面状态或大量可变业务数据。需要动态替换、测试注入和远程更新时，更适合接口加依赖注入。`val all` 只保证引用不可重新赋值，不自动保证内部对象深不可变。

## 5. 实际执行过程

1. 首次访问 object
2. 初始化唯一实例和 all
3. 首页读取列表
4. 详情调用 findById
5. 整个进程共享同一注册定义

## 6. Flutter / Dart 3 对照

Dart 3 可用顶层 final 列表或带 static 成员的不可实例化类。

```dart
final class ComponentRegistry {
  ComponentRegistry._();
  static final all = <ComponentInfo>[textDemo, imageDemo];
}
```

## 7. 常见错误

- 在 object 中持有 Activity 导致泄漏
- 把用户会话和页面状态全部塞入单例
- 误认为 val 等于深不可变
- 测试无法替换全局注册表

## 8. 本章检查题

1. object 与 class 的使用方式有何不同？
2. 为什么注册表适合单例？
3. 哪些对象不应放入全局 object？

## 9. 本章总结

ComponentRegistry 是稳定代码目录，而不是通用全局状态中心。
