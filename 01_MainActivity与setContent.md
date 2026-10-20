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

先不要被 `setContent` 里面的三层嵌套吓到。本章会先用一个最小示例把主线走通：

```kotlin
setContent {
    Text("Hello Compose")
}
```

理解清楚之后，再回来看项目真实入口里的这几层包装分别是什么。

本章先回答一个最基础的问题：

> Android 启动应用后，界面是怎么从 Activity 一路走到 Composable 的？

主线可以简化成：

```text
Android 系统
   ↓
MainActivity（Activity）
   ↓
onCreate
   ↓
setContent
   ↓
Composable 内容
```

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
Compose 根内容（Composable）
   ↓ 组合出
界面
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

这里的 `super` 指**当前类 `MainActivity` 的直接父类**，也就是 `ComponentActivity`。

所以：

```text
MainActivity 的直接父类 = ComponentActivity
super.onCreate(savedInstanceState)
    ↓ 实际调用
ComponentActivity 中的 onCreate(savedInstanceState) 实现
```

也就是说，`super.onCreate(...)` 调用的是 `ComponentActivity.onCreate(...)` 这个实现，而不是更上层某个类的实现，也不是 `MainActivity` 自己的 `onCreate`。

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

先用最小示例：

```kotlin
setContent {
    Text("Hello Compose")
}
```

`setContent` 的作用可以先理解为：

> 把大括号中的 Compose UI 设置为当前 Activity 的界面内容。

逐段看这段代码：

```text
setContent            从 Android Activity 进入 Compose
大括号内容             这段内容负责构建界面
Text("Hello Compose")  一个最简单的 Composable：显示一行文字
```

`Text` 是 Compose 最基础的文字组件（第 11 章会详细讲它的其他参数）。本章只需要把它理解成“显示一行文字的 Composable”。

传统 Android 页面经常使用 XML：

```kotlin
setContentView(R.layout.activity_main)
```

Compose 改为：

```kotlin
setContent {
    Text("Hello Compose")
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
- `@Composable`：这个函数是 Compose 的 UI 内容，可以在其中构建界面

`() -> Unit` 是 Kotlin 的函数类型写法，`setContent { ... }` 的大括号则是把一个 Lambda 传给它；函数类型与 Lambda 的完整语法见[预备篇 D 的「Lambda 与函数类型」「尾随 Lambda」两节](00D_Kotlin基础与Compose必备语法.md)。

所以：

```kotlin
setContent {
    Text("Hello Compose")
}
```

等于把一个“负责构建 UI 的函数”交给 `setContent`，由它把这个函数产生的界面挂到当前 Activity 上。

注意：`setContent` 的大括号里放几层包装、放哪些组件，是每个工程自己的选择，不影响 `setContent` 的含义。给它一个 Composable 内容，它就把这份内容装进 Activity。

第 2 章会单独讲清楚 `@Composable`。

---

## 7. 项目真实入口长什么样（本阶段先当黑盒）

回到项目代码：

```kotlin
setContent {
    DesignWidthScale {
        DemoTheme {
            DemoApp()
        }
    }
}
```

先明确一件事：

> 这是 **compose_demo 这个 demo 工程自己的启动写法，不是 Compose 的用法标准**。

Compose 只要求 `setContent` 的大括号里是 Composable 内容。里面套三层还是零层，套的是什么，属于工程组织方式。上面这段代码和最小示例 `setContent { Text("Hello Compose") }` 相比，对 `setContent` 本身来说没有区别。

从外向内看：

```text
setContent
└── DesignWidthScale
    └── DemoTheme
        └── DemoApp
```

本阶段只需要知道“谁包住了谁”，各层的含义如下：

### `DesignWidthScale`

项目自定义的设计稿适配策略（按设计稿宽度等比换算尺寸，让同一套数值在各分辨率上保持比例）。

本阶段把它当**黑盒**：只知道它包住了内容。它具体改变了子树的什么、有没有风险，第 41—45 章会专门分析它的实现与风险。

### `DemoTheme`

项目的主题包装，负责向内部提供颜色、字体等配置。第 3 章详解。

### `DemoApp`

应用真正的 UI 根内容，负责组织导航和页面。第 2 章先认识 Composable 函数，第 4 章再讲导航。

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

最后再强调一次：

> 这三层是 compose_demo 的组织方式，不是理解 `setContent` 的前提。理解 `setContent`，只需要第 6 节那个最小示例。

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

先看任何 Compose 应用都一样的必要主线：

```text
1. 用户点击应用图标
2. Android 根据 Manifest 找到 MainActivity
3. Android 创建 MainActivity
4. Android 调用 MainActivity.onCreate(...)
5. super.onCreate(...) 让直接父类 ComponentActivity 完成 Activity 初始化
6. configStatusBar() 配置系统窗口
7. setContent { ... } 把大括号中的 Composable 内容挂到 Activity 上
```

`compose_demo` 在第 7 步的内容里，又加了自己的结构：

```text
8.  DesignWidthScale 建立尺寸环境（本阶段当黑盒，第 41—45 章）
9.  DemoTheme 建立主题环境（第 3 章）
10. DemoApp() 构建导航图（第 4 章）
11. NavHost 根据 startDestination 显示 HomeScreen（第 4 章）
```

也就是说，第 8 步之后的内容属于这个工程的选择，不属于 `setContent` 的语义。

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
| `DemoTheme`（项目自定义包装） | `Theme` / `MaterialApp.theme` |

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

### 错误 3：认为 `setContent` 的大括号只执行一次，所以里面可以随便写网络请求

```kotlin
setContent {
    // 不推荐：这段内容可能被反复执行
    requestApi()
    Text("页面")
}
```

Composable 内容不是“只会执行一次的初始化代码”。当它依赖的数据发生变化时，Compose 会重新执行相关的 Composable 函数，用新数据重新生成这部分界面（这个机制叫「重组」，第 22 章详解）。

因此网络请求、写文件、弹提示这类会对外部世界产生影响的操作，不应该直接写在 Composable 函数体里，否则可能被重复触发。这个问题会在第 25 章专门说明。

### 错误 4：把所有业务逻辑都堆进 `MainActivity`

`MainActivity` 更适合承担：

- Activity 初始化
- Window 配置
- 安装 Compose 根内容

页面 UI 和业务逻辑应该继续拆分。

### 错误 5：把 demo 工程的三层包装当成 Compose 的规定写法

`DesignWidthScale`、`DemoTheme` 都是这个工程的包装层。`setContent` 只要求大括号里是 Composable 内容，写成 `setContent { Text("Hello Compose") }` 同样正确。

---

## 12. 本章检查题

1. `MainActivity` 是 Composable 吗？
2. `setContent` 的大括号中允许调用什么类型的函数？
3. `super.onCreate(savedInstanceState)` 调用的到底是哪个类里的实现？
4. 项目入口里的 `DesignWidthScale` 和 `DemoTheme`，本阶段应该怎么理解？
5. `configStatusBar()` 修改的是 Compose 组件，还是 Android Window？

答案：

1. 不是，它是 Android Activity。
2. Composable 内容。
3. 当前类 `MainActivity` 的直接父类 `ComponentActivity` 里的 `onCreate(...)` 实现。
4. 都先当作项目自己的包装：`DesignWidthScale` 是黑盒，只看它包住了内容，第 41—45 章再分析；`DemoTheme` 是主题包装，第 3 章讲。它们都不用来解释 `setContent`。
5. Android Window。

---

## 13. 本章总结

记住这条主线：

```text
Android 创建 MainActivity
        ↓
onCreate
        ↓
super.onCreate(...) 让直接父类 ComponentActivity 完成初始化
        ↓
setContent
        ↓
Composable 根内容（最小示例：Text("Hello Compose")）
        ↓
项目自己的包装：DesignWidthScale → DemoTheme → DemoApp
```

一句话概括：

> `MainActivity` 是 Android 提供的页面宿主，`setContent` 是从 Android Activity 进入 Compose UI 世界的入口；`setContent` 里面包几层，是工程选择，不是 Compose 的规定。
