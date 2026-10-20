# 第 69 章：Hilt 依赖注入

## 1. 本章目标

学完后，你应当能够：

- 理解 Hilt 是什么：Dagger 的 Android 封装，编译期生成代码
- 配置 @HiltAndroidApp 与 @AndroidEntryPoint 两个入口注解
- 用 @HiltViewModel + @Inject constructor 创建带参 ViewModel
- 用 @Module + @Provides/@Binds 提供依赖，并理解作用域注解

## 2. 项目中的位置

进阶篇不再局限于 compose_demo 项目，本章使用通用 Compose API。

- `com.google.dagger.hilt.android`：@HiltAndroidApp、@AndroidEntryPoint
- `androidx.hilt:hilt-navigation-compose`：在 Navigation Compose 目的地中使用 `hiltViewModel()`
- `javax.inject`：@Inject、@Singleton
- `dagger.hilt`：@Module、@InstallIn、@HiltViewModel

## 3. 项目源码片段

场景：网络接口依赖注入到 ViewModel，Compose 里直接取用：

下面的代码聚焦 Hilt 的装配关系，不是完整可运行工程：Retrofit/Gson 的 Gradle 依赖、Hilt 插件与 KSP/KAPT 处理器配置需要按预备篇 B 和本仓库版本基线另行加入；首次阅读可先看注入链路，不必先复制网络配置。本章示例**需工程环境，不参与编译校验**——snippets 校验工程没有 Hilt 与 Retrofit 依赖，因此以下代码块不加编译标记。

```kotlin
// App.kt —— Application 必须加 @HiltAndroidApp
@HiltAndroidApp
class DemoApplication : Application()

// MainActivity.kt —— 每个用到注入的 Activity 都要加 @AndroidEntryPoint
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { HomeScreen() } }
    }
}

// UserApi.kt —— 依赖接口
interface UserApi {
    suspend fun fetchUser(id: String): User
}

// NetworkModule.kt —— 提供 Retrofit 接口
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideRetrofit(): Retrofit = Retrofit.Builder()
        .baseUrl("https://api.example.com/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    @Provides
    @Singleton
    fun provideUserApi(retrofit: Retrofit): UserApi =
        retrofit.create(UserApi::class.java)
}

// MainViewModel.kt —— 构造注入
@HiltViewModel
class MainViewModel @Inject constructor(
    private val userApi: UserApi
) : ViewModel() {

    private val _userName = MutableStateFlow<String?>(null)
    val userName: StateFlow<String?> = _userName.asStateFlow()

    init {
        viewModelScope.launch {
            _userName.value = userApi.fetchUser("1").name
        }
    }
}

// HomeScreen.kt —— Navigation Compose 目的地中使用 hiltViewModel()
@Composable
fun HomeScreen(viewModel: MainViewModel = hiltViewModel()) {
    val userName by viewModel.userName.collectAsStateWithLifecycle()
    Text(userName ?: "加载中…")
}
```

## 4. 逐步解释

Hilt 是 Dagger 在 Android 上的封装。它在编译期扫描 @Module、@Inject、@HiltViewModel 等注解，生成依赖图代码，运行时无反射。这意味着依赖缺失、循环依赖、重复提供等错误在**编译期**就能暴露，而不是运行到那一步才崩。

入口三件套是常见组合，但并不是唯一方式：`@HiltAndroidApp` 标记 Application，生成全局组件；`@AndroidEntryPoint` 标记 Activity/Fragment，生成注入入口；`@Inject constructor` 声明「我要什么依赖」。依赖同样可以由 `@Provides` / `@Binds` 在 `@Module` 里提供，很多类不需要（也不应该）在构造函数上标 `@Inject`。Hilt/Dagger 在编译期生成并校验依赖图，大部分「图不完整」的配置错误（例如缺少绑定）在编译期就会报错，不是都要等到运行期；会表现到运行期的主要是入口注解缺失一类问题，例如漏掉 `@AndroidEntryPoint`（正确报错样例见第 7 节）。

`@HiltViewModel` 与 `@Inject constructor` 是 ViewModel 注入的一对组合：前者标记「这个 ViewModel 由 Hilt 创建」，后者声明构造参数由 Hilt 提供，两者配合生成 ViewModelFactory。在 Navigation Compose 目的地中搭配 `hiltViewModel()`，无需手写工厂代码，且 ViewModel 会按当前导航返回栈条目确定作用域。

提供依赖有两种方式：`@Provides` 用于提供第三方类（Retrofit 本身）或接口实现（retrofit.create 出来的 UserApi），`@Binds` 用于把接口绑定到具体实现类。模块用 `@InstallIn` 声明挂在哪个组件下，SingletonComponent 对应 Application 级。

作用域注解决定实例存活时长：`@Singleton` 应用级唯一，`@ActivityRetainedScoped` 随 Activity 重建保留，`@ActivityScoped` 随 Activity 销毁。不加作用域时每次注入都 new 新实例，加了才能复用。

记住：@HiltAndroidApp 管 Application，@AndroidEntryPoint 管入口类，@HiltViewModel + @Inject constructor 是 ViewModel 注入的常见写法（依赖也可以由模块提供），@Module + @Provides/@Binds 管依赖怎么造，作用域管造几个。

## 5. 实际执行过程

1. 编译期：Hilt 注解处理器扫描各注解，生成组件与工厂代码，依赖图不完整直接编译报错
2. 应用启动，DemoApplication 的 Hilt 组件初始化，按需创建 Retrofit（@Singleton 只建一次）
3. MainActivity 启动，@AndroidEntryPoint 完成入口注入
4. HomeScreen 首次组合，hiltViewModel() 使用 Hilt 工厂和当前导航作用域构造 MainViewModel
5. init 里 viewModelScope 启动协程拉取用户数据，StateFlow 发射，UI 显示名字
6. 旋转屏幕，Activity 重建但 ViewModel 存活，依赖仍是同一批单例

## 6. Flutter / Dart 3 对照

先讲相似点：Dart 生态也有服务定位/依赖注入容器，最常用的是 get_it：

```dart
// get_it：运行时注册与解析
final locator = GetIt.instance;

void setupLocator() {
  locator
    ..registerLazySingleton<UserApi>(() => DioUserApi(Dio()))
    ..registerFactory<MainViewModel>(() => MainViewModel(locator<UserApi>()));
}

class MainViewModel {
  MainViewModel(this._api);
  final UserApi _api;

  Future<String> loadUserName() => _api.fetchUserName();
}

// 使用
final vm = locator<MainViewModel>();
```

Riverpod 的 ProviderContainer 也承担类似职责（依赖 + 作用域 + 生命周期管理）。

本质区别：

- Hilt 在编译期生成代码并校验依赖图，缺失/重复依赖在编译时暴露；get_it 是运行时容器，注册遗漏到运行时才报错。
- Hilt 的作用域（@Singleton 等）是编译期声明的生命周期契约；get_it 用 registerLazySingleton/registerFactory 手动区分，没有类型级校验。
- Dart 的注入常与状态管理（Riverpod）融合在一起；Hilt 专注依赖装配，状态管理由 ViewModel + Flow 负责。

记住：Hilt 是编译期「图纸校验」的依赖装配，get_it 是运行时「现查现用」的服务定位，前者更早暴露错误、更省心。

## 7. 常见错误

- 忘记 @AndroidEntryPoint → 运行时崩溃，报「has not been annotated with @AndroidEntryPoint」，深链接（deeplink）启动的 Activity 最容易踩
- @HiltViewModel 与 @Inject constructor 是 ViewModel 注入的常见搭配，但不是唯一方式 → 依赖同样可以由 @Module 里的 @Provides/@Binds 提供，@HiltViewModel 负责让 Hilt 接管 ViewModel 的创建
- 宿主没有正确接入 Hilt 却调用 hiltViewModel() → 无法取得 Hilt ViewModelFactory
- 两个模块重复提供同一类型（比如两个 @Provides Retrofit）→ 编译期报错，注意 @InstallIn 作用域不同也会冲突
- 忘加 KSP/KAPT 或 Hilt Gradle 插件 → 通常在生成代码或编译阶段报缺失类型/配置错误，应先检查具体失败任务

## 8. 本章检查题

1. @HiltViewModel 与 @Inject constructor 各自的作用？
2. Hilt 与手动 new 依赖相比优势在哪？
3. 作用域注解（@Singleton、@ActivityRetainedScoped）的意义？
4. @Provides 与 @Binds 的区别？

答案：

1. @HiltViewModel 标记「这个 ViewModel 由 Hilt 创建」，@Inject constructor 声明构造参数由 Hilt 提供，两者配合生成 ViewModelFactory。
2. 依赖关系集中声明、编译期校验依赖图、作用域统一管理生命周期，代码可测试、可替换；手动 new 会到处散落创建逻辑且难以替换。
3. 决定实例的存活时长：@Singleton 应用级唯一，@ActivityRetainedScoped 随 Activity 重建保留，避免重复创建与状态丢失。
4. @Provides 用方法体提供任意逻辑构造的实例（第三方类、接口实现），@Binds 把接口绑定到具体实现类，必须是抽象函数。

## 9. 本章总结

Hilt 把「谁依赖谁、谁造谁」集中到编译期声明的依赖图里。常用配置是 @HiltAndroidApp、@AndroidEntryPoint、@HiltViewModel + @Inject constructor 这一组入口注解，依赖的提供与绑定交给 @Module + @Provides/@Binds，再用作用域注解控制实例存活时长。

官方参考：[Dependency injection with Hilt](https://developer.android.com/training/dependency-injection/hilt-android)。
