# 第 72 章：状态持久化：rememberSaveable、DataStore 与 Room 的边界

## 1. 本章目标

学完后，你应当能够：

- 分清四层状态的边界：组合内存（remember）→ 配置变化及系统可恢复时的进程重建（rememberSaveable）→ 跨启动键值（DataStore）→ 结构化大数据（Room）
- 用 Saver（`listSaver` / `mapSaver`）把自定义类安全地存进 rememberSaveable
- 用 DataStore Preferences 读写设置项，理解其 Flow 异步模型
- 用 Room 的 Flow 查询驱动 UI，并在 ViewModel 中 `stateIn` 成 StateFlow

## 2. 项目中的位置

进阶篇不再局限于 compose_demo 项目，本章使用通用 Compose API。

- `androidx.compose.runtime.saveable`（rememberSaveable、Saver、listSaver）
- `androidx.datastore:datastore-preferences`（键值持久化）
- `androidx.room:room-runtime` 与 `room-ktx`（结构化数据）

## 3. 项目源码片段

场景：一个笔记 App：编辑页的草稿要扛住旋转屏幕（rememberSaveable + 自定义 Saver）；主题、字号这类用户设置要跨启动保存（DataStore）；笔记列表要持久化并实时响应增删改（Room + Flow）。

以下是展示 API 关系的教学片段，不是可直接运行的完整工程：省略了 imports、Gradle 插件/处理器配置、DataStore 的依赖注入，以及 Room 数据库实例的创建。Room 的 KSP/KAPT 配置可先回看预备篇 B；完整接线时再分别按 DataStore 与 Room 的官方入门步骤补齐。

```kotlin
// ---------- 第一层：rememberSaveable + 自定义 Saver ----------
data class Draft(val title: String, val body: String)

val DraftSaver = listSaver<Draft, Any>(
    save = { listOf(it.title, it.body) },
    restore = { Draft(it[0] as String, it[1] as String) }
)

@Composable
fun EditorScreen() {
    var draft by rememberSaveable(stateSaver = DraftSaver) {
        mutableStateOf(Draft("", ""))
    }
    OutlinedTextField(
        value = draft.title,
        onValueChange = { draft = draft.copy(title = it) }
    )
}

// ---------- 第二层：DataStore Preferences ----------
data class AppSettings(val theme: String, val fontSize: Float)

private val Context.dataStore by preferencesDataStore(name = "settings")
private val KEY_THEME = stringPreferencesKey("theme")
private val KEY_FONT_SIZE = floatPreferencesKey("font_size")

val Context.settingsFlow: Flow<AppSettings>
    get() = dataStore.data.map { prefs ->
        AppSettings(
            theme = prefs[KEY_THEME] ?: "light",
            fontSize = prefs[KEY_FONT_SIZE] ?: 14f
        )
    }

suspend fun Context.saveTheme(theme: String) {
    dataStore.edit { prefs -> prefs[KEY_THEME] = theme }
}

// ---------- 第三层：Room ----------
@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey val id: Long,
    val title: String,
    val updatedAt: Long
)

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<NoteEntity>>

    @Insert
    suspend fun insert(note: NoteEntity)
}

@Database(entities = [NoteEntity::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
}

class NoteRepository(private val dao: NoteDao) {
    fun observeNotes(): Flow<List<NoteEntity>> = dao.observeAll()
    suspend fun add(note: NoteEntity) = dao.insert(note)
}

class NoteViewModel(private val repository: NoteRepository) : ViewModel() {
    val notes: StateFlow<List<NoteEntity>> = repository.observeNotes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
```

## 4. 逐步解释

1. 先划清四层边界：`remember` 会跨重组保留，但关联的节点离开组合后就会丢；`rememberSaveable` 借助 Saved State 机制，在配置变化以及系统能够恢复状态的进程重建场景中恢复小数据；DataStore 落盘到文件，App 重启后依然在，适合设置类键值；Room 落盘到 SQLite，适合需要查询、排序、联动的结构化数据。记忆口诀：**留在组合用 remember，需要恢复用 Saveable，跨启动小配置用 DataStore，要查询就用 Room**。

2. rememberSaveable 的介质与限制：它把数据放进 Bundle（底层是 Parcel）。Bundle 支持基本类型、String、Parcelable、Serializable 以及它们的数组 / List / Map。自定义 data class 既不是 Parcelable 也不保证可序列化，所以要用 Saver 告诉它「怎么拆、怎么拼」：`listSaver` 把对象拆成 List 再存，`restore` 时按顺序拼回。只写 `rememberSaveable { mutableStateOf(Draft(...)) }` 而不配 Saver，运行时直接抛异常——这是新人最常见的崩溃之一。

3. DataStore 为什么是异步的：Preferences DataStore 内部用协程做文件读写，`data` 暴露的是 `Flow<Preferences>`，每次 `edit` 后订阅者都能收到新值。因为磁盘 IO 不能卡主线程，写入使用 suspend API，读取通过 Flow 收集。在 Android Compose 中优先用 `collectAsStateWithLifecycle()` 进行生命周期感知收集。设置项这种「低频、键值、要跨启动」的数据正合适。

4. Room 的 Flow 查询为什么好用：`@Query` 返回 Flow 时，Room 会在表数据变化后自动重新查询并发出新列表，UI 层无需手动刷新；查询本身由 Room 在自己的线程池执行。拿到 Flow 后在 ViewModel 里 `stateIn` 成 StateFlow，Compose 侧 `collectAsState()` 订阅，数据从数据库到 UI 一条龙全响应式。关键原则：数据库访问只存在于数据层（DAO / Repository），Composable 与 ViewModel 不直接触碰 Room 与 DataStore。

5. 什么时候选哪个：草稿、输入框内容、滚动位置这类「临时但怕丢」的小状态用 rememberSaveable；主题、语言等「跨启动的小配置」用 DataStore；笔记、消息、缓存列表这类「多、要查、要排序」的数据用 Room。登录 token 属于敏感凭证，不能因为体积小就直接归为普通设置；应根据威胁模型使用 Keystore 支持的加密方案，并避免在日志、备份和版本库中泄露。

## 5. 实际执行过程

1. 用户在编辑页输入标题，`draft` 变化触发重组；此时值只存在组合内存里。
2. 用户旋转屏幕：Activity 重建，系统调用 onSaveInstanceState，rememberSaveable 借助 DraftSaver 把 Draft 拆成 List 存入 Bundle；重建后 restore 拼回，草稿不丢。
3. 用户在设置页切到深色主题：ViewModel 调 `saveTheme("dark")`，DataStore `edit` 落盘；settingsFlow 发出新值，订阅方自动更新 UI。
4. 用户新建笔记：`dao.insert(...)` 写入 SQLite；表数据变化触发 `observeAll()` 重新查询，Flow 发出新列表；NoteViewModel 的 notes StateFlow 更新，列表页自动多出一行。
5. App 被系统杀死：内存里的 draft 丢失（除非已进 Bundle），但 DataStore 的主题与 Room 的笔记都在，下次启动原样恢复。

## 6. Flutter / Dart 3 对照

先看相似点：Dart 侧 shared_preferences 对应 DataStore（键值持久化），sqflite / drift 对应 Room（SQL 持久化）：

```dart
// shared_preferences：等价 DataStore 的简单用法
final prefs = await SharedPreferences.getInstance();
await prefs.setString('theme', 'dark');
final theme = prefs.getString('theme') ?? 'light';

// sqflite：等价 Room 的 Entity / DAO
final db = await openDatabase('notes.db');
await db.insert('notes', {'id': 1, 'title': '标题'});
final rows = await db.query('notes', orderBy: 'updatedAt DESC');
```

对照表：

| 场景 | Compose | Flutter |
| --- | --- | --- |
| 组合内临时状态 | remember | State 对象字段 |
| 配置变化/进程重建恢复 | rememberSaveable | Widget State 天然存活 + Navigator restoreState |
| 跨启动键值 | DataStore | shared_preferences |
| 结构化数据库 + 响应式查询 | Room + Flow | sqflite / drift + Stream |

本质区别：Compose 的持久化是「响应式管道」——DataStore 和 Room 都直接吐出 Flow，UI 订阅即自动更新，数据层改动零样板；Flutter 的 shared_preferences 与 sqflite 都是「拉取式」API，需要自己把变更转成 Stream 或通知 UI（drift 提供了 Stream 化查询，但不如 Room 的 Flow 是官方默认）。另外 rememberSaveable 在 Flutter 没有完全对应的东西：Flutter 的 State 对象在配置变化（如旋转）时天然保留，进程死亡恢复则要自己用 PageStorage / restoreState 处理。

记住：Compose 的数据层「天生会说话」（Flow），Flutter 的数据层「需要你去问」（await）。

## 7. 常见错误

1. 把大对象塞进 rememberSaveable：图片、长列表、大 JSON 序列化进 Bundle，一旦超过约 1MB 就会抛 TransactionTooLarge 崩溃。rememberSaveable 只存「塞得进 Bundle 的小东西」，大对象放 DataStore / Room。
2. 在 Composable 里直接读写 DataStore / Room：组合函数可能被重组多次，直接在这里 `edit` 会导致重复写盘、读写时机不可控。正确姿势是数据层封装，Composable 只 collect，写操作走 ViewModel / Repository。
3. 忘了 Room 自带线程模型：Room 的 suspend 查询在自己线程池执行，Flow 查询也在后台重新查询，不需要也不应该再包一层 `withContext(Dispatchers.IO)`——那是把别人已经做好的工作重复一遍。
4. 分不清「配置变化」与「进程死亡」：旋转屏幕是配置变化，rememberSaveable 可以恢复；被系统杀进程是进程死亡，恢复依赖 onSaveInstanceState 的 Bundle 是否还活着，且用户主动划掉后台时通常没有 Bundle。把「重要数据必须进 DataStore / Room」当铁律，Saveable 只是锦上添花。
5. 自定义类忘了配 Saver：直接 `rememberSaveable { mutableStateOf(MyData(...)) }`，运行时抛 IllegalArgumentException 提示类型不支持。

## 8. 本章检查题

1. rememberSaveable 的保存介质是什么？有什么限制？
2. 什么时候应该用 DataStore 而不是 rememberSaveable？
3. Room 的 Flow 查询相比「手动查询 + 刷新」有什么优势？

答案：

1. 介质是 Bundle（onSaveInstanceState），底层是 Parcel。限制：类型必须 Bundle 可存（基本类型 / String / Parcelable / Serializable 等），自定义类需要写 Saver；数据量小，超过约 1MB 会抛 TransactionTooLarge。
2. 数据需要跨 App 启动存活、且是非敏感的键值型小配置（主题、字号）时用 DataStore；rememberSaveable 依赖 Saved State 恢复，不保证用户主动结束应用后仍可恢复。敏感凭证需要额外的安全设计。
3. 表数据变化后 Room 自动重新查询并发出新列表，UI 订阅 Flow 即自动刷新，无需手动管理「改完数据 → 手动刷新列表」的流程，天然响应式且查询在后台线程执行。

## 9. 本章总结

状态持久化的核心是「按数据特征选对层」：临时小状态交给 rememberSaveable，跨启动的键值交给 DataStore，结构化大数据交给 Room。三者的共同点是数据源与 UI 通过 Flow / StateFlow 解耦，记住「数据层不依赖 UI」这条原则，持久化代码就不会写乱。

官方参考：[在 Compose 中保存 UI 状态](https://developer.android.com/develop/ui/compose/state-saving)、[DataStore](https://developer.android.com/topic/libraries/architecture/datastore)、[使用 Room 保存本地数据](https://developer.android.com/training/data-storage/room)。
