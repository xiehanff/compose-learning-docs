# 第 1 章：MainActivity、ComponentActivity、onCreate 与 setContent

## 1. 本章对应的项目代码

项目入口位于：

```text
app/src/main/java/com/example/demo/MainActivity.kt
```

核心结构：

```kotlin
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        configStatusBar()

        setContent {
            DesignWidthScale {
                DemoTheme {
                    DemoApp()
                }
            }
        }
    }
}
```

本章先回答一个最基础的问题：

> Android 启动应用后，为什么最终会显示 `DemoApp()`？

---

## 2. `MainActivity` 是什么

```kotlin
class MainActivity : ComponentActivity()
```

分成三部分：

```text
class MainActivity : ComponentActivity()
 ①       ②                ③
```

### ① `class`

声明一个 Kotlin 类。

### ② `MainActivity`

当前类的名字。

项目的 `AndroidManifest.xml` 将它配置成应用启动入口。用户点击图标后，Android 系统会创建这个 Activity。

### ③ `: ComponentActivity()`

表示 `MainActivity` 继承 `ComponentActivity`。

它不是调用普通函数，而是在声明：

> `MainActivity` 是一种 `ComponentActivity`，因此拥有 Activity 生命周期和 Compose 集成能力。

Dart 3 中类似：

```dart
class MainPage extends StatefulWidget {
  const MainPage({super.key});
}
```

语法不同：

```text
Kotlin：class 子类 : 父类()
Dart：  class 子类 extends 父类
```

---

## 3. 为什么使用 `ComponentActivity`

Compose 项目常用：

```kotlin
androidx.activity.ComponentActivity
```

它首先是一个 Android Activity，因此拥有：

- `onCreate`
- `onStart`
- `onResume`
- `onPause`
- `onStop`
- `onDestroy`
- `window`
- 系统配置变化处理能力

同时，`androidx.activity.compose.setContent` 可以把 Compose UI 挂到这个 Activity 中。

需要注意：

> `ComponentActivity` 不是 Compose 组件。它是承载 Compose UI 的 Android 页面容器。

可以先建立这个层级：

```text
Android 系统
   ↓ 创建
MainActivity
   ↓ 调用 setContent
Compose UI
   ↓ 执行
DemoApp()
```

---

## 4. `onCreate` 是什么

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
```

它是 Activity 创建时由 Android 系统调用的生命周期函数。

### `override`

表示当前类正在重写父类已经定义的函数。

没有 `override`，编译器不会把它当作对父类函数的正确重写。

### `fun`

Kotlin 的函数声明关键字。

### `savedInstanceState: Bundle?`

这是一个参数：

```text
参数名              类型
savedInstanceState : Bundle?
```

末尾的 `?` 表示它允许为 `null`。

它可能携带 Activity 上次销毁前保存的状态。例如设备旋转后，系统重新创建 Activity 时可能传入状态数据。

本项目没有直接读取它，但仍然要传给父类：

```kotlin
super.onCreate(savedInstanceState)
```

---

## 5. 为什么必须调用 `super.onCreate`

```kotlin
super.onCreate(savedInstanceState)
```

含义：

> 先让父类完成 Activity 自身的创建初始化。

这里的 `super` 指 `ComponentActivity` 的父类实现。

通常不要删除，也不要随意放到很后面。否则 Activity、SavedState、生命周期或其他基础设施可能不能正确初始化。

Dart/Flutter 中没有完全相同的启动写法。比较接近的是某些生命周期方法中调用父类：

```dart
@override
void initState() {
  super.initState();
}
```

它们的共同点是：

> 子类扩展生命周期逻辑时，保留父类原本必须完成的初始化。

---

## 6. `setContent` 在干什么

项目代码：

```kotlin
setContent {
    DesignWidthScale {
        DemoTheme {
            DemoApp()
        }
    }
}
```

`setContent` 的作用可以先理解为：

> 把大括号中的 Compose UI 设置为当前 Activity 的界面内容。

传统 Android 页面经常使用 XML：

```kotlin
setContentView(R.layout.activity_main)
```

Compose 改为：

```kotlin
setContent {
    DemoApp()
}
```

也就是：

```text
传统 Android：Activity → XML 布局
Compose：      Activity → Composable 内容
```

### `setContent` 接收什么

简化理解，它接收一个 Composable 内容函数：

```kotlin
content: @Composable () -> Unit
```

现在只看最直观的含义：

- `()`：这个函数不需要参数
- `Unit`：这个函数不返回业务结果
- `@Composable`：函数内部允许构建 Compose UI

所以：

```kotlin
setContent {
    DemoApp()
}
```

等于把一个“负责构建 UI 的函数”交给 `setContent`。

第 2 章会单独讲清楚 `@Composable`。

---

## 7. 三层嵌套在做什么

```kotlin
setContent {
    DesignWidthScale {
        DemoTheme {
            DemoApp()
        }
    }
}
```

从外向内：

```text
setContent
└── DesignWidthScale
    └── DemoTheme
        └── DemoApp
```

### 第一层：`setContent`

把 Compose UI 放进 Activity。

### 第二层：`DesignWidthScale`

向内部子树提供项目自定义的设计稿尺寸换算环境。

### 第三层：`DemoTheme`

向内部子树提供颜色、字体和自定义 Demo 尺寸配置。

### 最内层：`DemoApp`

构建应用导航和真正页面。

关键理解：

> 外层不是“先画完，再画内层”，而是在给内层建立运行环境。

可以类比成 Flutter：

```dart
void main() {
  runApp(
    AppScale(
      child: MaterialApp(
        theme: appTheme,
        home: const DemoApp(),
      ),
    ),
  );
}
```

这里只是概念对照，不是完全一一对应。

---

## 8. `configStatusBar()` 为什么放在 `setContent` 前面

项目先执行：

```kotlin
configStatusBar()
```

再执行：

```kotlin
setContent { ... }
```

`configStatusBar()` 修改 Activity 的 `window`：

```kotlin
private fun configStatusBar() {
    WindowCompat.setDecorFitsSystemWindows(window, false)
    window.statusBarColor = Color.TRANSPARENT
    window.navigationBarColor = Color.TRANSPARENT
}
```

它配置的是系统窗口：

- 内容是否延伸到状态栏、导航栏后面
- 状态栏背景颜色
- 导航栏背景颜色

它不是 Composable，所以不需要放在 `setContent` 内部。

这是一个重要边界：

```text
Activity / Window 配置 → 普通 Kotlin / Android 代码
Compose UI             → setContent 内的 Composable 代码
```

---

## 9. 完整启动过程

应用启动后的实际主链路可以简化为：

```text
1. 用户点击应用图标
2. Android 根据 Manifest 找到 MainActivity
3. Android 创建 MainActivity
4. Android 调用 MainActivity.onCreate(...)
5. super.onCreate(...) 初始化 Activity
6. configStatusBar() 配置系统窗口
7. setContent { ... } 安装 Compose UI
8. DesignWidthScale 建立尺寸环境
9. DemoTheme 建立主题环境
10. DemoApp() 构建导航图
11. NavHost 根据 startDestination 显示 HomeScreen
```

因此，`MainActivity` 并没有直接把 `HomeScreen` 写死在页面上。

它只负责启动 Compose 根内容，页面选择由后面的导航图管理。

---

## 10. 与 Flutter 启动入口对比

Flutter 常见入口：

```dart
void main() {
  runApp(const MyApp());
}
```

Compose 项目入口：

```kotlin
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DemoApp()
        }
    }
}
```

可以先这样对应：

| Android Compose | Flutter |
|---|---|
| `MainActivity` | 原生宿主 + Flutter Engine 外层环境 |
| `onCreate` | 应用原生页面创建时机 |
| `setContent` | 在宿主中安装根 UI |
| `DemoApp()` | `MyApp` 根 Widget |
| `DemoTheme` | `Theme` / `MaterialApp.theme` |

不能简单认为：

```text
MainActivity == Flutter 的 main()
```

因为 `MainActivity` 是 Android 系统管理的 Activity，而 `main()` 是 Dart 程序入口函数。

---

## 11. 常见错误

### 错误 1：删除 `super.onCreate`

```kotlin
// 不建议
override fun onCreate(savedInstanceState: Bundle?) {
    setContent { DemoApp() }
}
```

父类初始化可能缺失。

### 错误 2：把 Composable 直接写在 `setContent` 外

```kotlin
// 错误
Text("Hello")

setContent { ... }
```

`Text` 需要在 Composable 环境中调用。

### 错误 3：认为 `setContent` 只执行一次，所以内部代码都可以随便产生副作用

Composable 内容可能发生重组。网络请求、写文件等副作用不应该因为函数重新执行而重复触发。

这个问题会在状态与 Effect 章节专门说明。

### 错误 4：把所有业务逻辑都堆进 `MainActivity`

`MainActivity` 更适合承担：

- Activity 初始化
- Window 配置
- 安装 Compose 根内容

页面 UI 和业务逻辑应该继续拆分。

---

## 12. 本章检查题

1. `MainActivity` 是 Composable 吗？
2. `setContent` 的大括号中允许调用什么类型的函数？
3. `super.onCreate(savedInstanceState)` 的作用是什么？
4. `DemoApp()` 为什么要放在 `DemoTheme` 内层？
5. `configStatusBar()` 修改的是 Compose 组件，还是 Android Window？

答案：

1. 不是，它是 Android Activity。
2. Composable 内容。
3. 让父类完成 Activity 必需的初始化。
4. 让 `DemoApp` 及其子树能够读取主题配置。
5. Android Window。

---

## 13. 本章总结

记住这条主线：

```text
Android 创建 MainActivity
        ↓
onCreate
        ↓
setContent
        ↓
Composable 根内容
        ↓
DemoApp
```

一句话概括：

> `MainActivity` 是 Android 提供的页面宿主，`setContent` 是从 Android Activity 进入 Compose UI 世界的入口。
