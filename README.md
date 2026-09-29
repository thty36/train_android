# 训练记录

一款 Android 训练计划与记录应用。用户可以为未来日期安排训练、从模板快速生成当天计划、记录实际完成情况，并同步到手机日历。

## 项目目标

帮助用户持续规划、执行和回顾锻炼。应用需要同时保存“原本计划练什么”和“实际上完成了什么”，使训练成果可以按日期和动作长期查询。

## 核心功能

### 日计划与训练记录

- 在任意过去或未来日期创建训练计划。
- 在日历视图中查看每天的计划、完成状态和训练历史。
- 为当天计划添加、删除、排序和修改动作。
- 完成训练时录入实际结果，并保留计划目标作为对照。
- 支持 `未开始`、`进行中`、`已完成`、`跳过` 四种计划状态。
- 支持为当天训练添加备注，例如身体状态、未完成原因或伤病提示。

### 动作与指标

每个动作至少包含名称、组数和目标指标。指标必须支持不同训练类型，不能只固定为重量、次数和速度：

| 训练类型 | 常用目标/实际指标 |
| --- | --- |
| 器械和力量训练 | 组数、重量、次数、RPE |
| 跑步 | 距离、配速、时长、间歇次数 |
| 自重训练 | 组数、次数、时长 |
| 拉伸或静态训练 | 时长、组数 |

- 每项指标都应带单位，例如 `kg`、`次`、`km`、`分钟`、`分/公里`。
- 用户可按实际训练需要增加或修改指标。
- 实际结果允许与计划目标不同，不应覆盖原计划。

### 训练模板

- 可把一组动作保存为具名训练模板，例如“跑步计划 1”“跑步计划 2”“器械计划”。
- 选择模板后，可直接将其应用到指定日期。
- 应用模板时生成当天计划的独立快照；之后修改当天计划，只影响该日期，不影响模板或其他已创建的日计划。
- 可将某一天的计划固定为新的训练模板，并命名保存。
- 已保存的模板可继续编辑；编辑后只影响未来新建的日计划，不追溯修改历史日计划。

示例：

- **跑步计划 1**：长跑 3 km、短跑 100 m x 8。
- **跑步计划 2**：短跑 50 m x 8、间歇跑 3 km。

### 手机日历

- 用户可将某日训练计划写入手机系统日历。
- 日历事件至少包含计划名称、日期/开始时间、预计时长和提醒时间。
- 应用首次访问日历时请求系统日历权限。
- 当用户修改或删除已同步的日计划时，应用应提供同步更新或删除对应日历事件的能力。
- 需保存应用计划与系统日历事件之间的关联，避免重复创建事件。

### 剪贴板

- 可将某一天的训练计划复制为结构化纯文本，便于粘贴到聊天、备忘录或其他应用。
- 复制内容应包括日期、计划名称、每个动作的组数与目标指标；已完成的计划还应包含实际完成结果。

建议格式：

```text
2026-09-28 跑步计划 1
1. 长跑：目标 3 km
2. 短跑：目标 100 m x 8
```

## 数据模型

| 对象 | 说明 |
| --- | --- |
| 训练模板 | 可复用的计划定义，包含模板名和动作列表。 |
| 日计划 | 某个具体日期的独立计划快照，可来源于模板或手动创建。 |
| 计划动作 | 日计划中的动作，保存目标组数和自定义目标指标。 |
| 训练记录 | 对应一次实际执行，保存完成状态、实际指标、完成时间和备注。 |
| 日历关联 | 日计划与系统日历事件的标识和同步状态。 |

## 使用流程

1. 创建训练模板，或直接为未来某天手动添加动作。
2. 在日历中选择日期，应用模板或编辑当天计划。
3. 可选择将计划同步到手机日历，并设置提醒。
4. 训练结束后填写实际重量、次数、距离、配速、时长等结果。
5. 从日历和历史记录中按日期查看训练内容与完成情况。

## MVP 范围

首个版本应完成：

- 手动创建和编辑未来日计划。
- 训练模板的创建、编辑和应用。
- 模板应用后的日计划快照机制。
- 实际完成记录与历史日历查询。
- 手机日历事件创建、更新和删除。
- 日计划复制到系统剪贴板。

后续版本可增加重复排期、训练完成率和趋势统计、数据导入导出、本地备份与跨设备同步。

## 验收标准

- 用户选择“跑步计划 1”应用到某天后，该天自动得到“长跑 3 km、短跑 100 m x 8”的动作列表。
- 用户把当天短跑改为 `100 m x 6` 后，“跑步计划 1”和其他日期的计划保持不变。
- 用户将当天计划保存为“周末跑步计划”后，能在模板列表中找到并编辑它。
- 用户填写实际跑步距离、配速或器械重量后，重新进入该日期仍能看到原计划和实际结果。
- 用户同步计划到手机日历后，可在系统日历中看到对应日程；修改计划后可更新对应日程。
- 用户复制日计划后，可在任意文本输入框粘贴出可读的训练清单。

## 技术栈与工程结构

- Kotlin + Jetpack Compose（Material 3），单 Activity + Navigation。
- 架构分层：UI（Compose Screen + ViewModel + StateFlow）→ `TrainRepository` → Room DAO；日历同步通过 `CalendarSyncer` 接口抽象，便于替换与测试。
- 数据持久化：Room（数据库 `trainlog.db`，版本 1），共 6 张表：`workout_templates`、`template_actions`、`template_metrics`、`day_plans`、`plan_actions`、`action_metrics`，子表对父表全部 `ON DELETE CASCADE`。
- 指标模型：`ActionMetric(name, targetValue, targetUnit, actualValue, actualUnit)` 为通用指标，重量 kg、次数、距离 km、配速、时长、RPE 等统一用“名称 + 值 + 单位”表示，用户可自由增删，不限定字段数量。
- 版本管理：Gradle Wrapper（8.14.3）+ Gradle Version Catalog（`gradle/libs.versions.toml`）。

关键代码位置：

| 功能 | 位置 |
| --- | --- |
| 实体与关系定义 | `app/src/main/java/com/example/trainlog/data/entity/Entities.kt` |
| Room DAO / 数据库 | `app/src/main/java/com/example/trainlog/data/db/` |
| 深拷贝快照映射（模板 ↔ 日计划） | `app/src/main/java/com/example/trainlog/data/repo/SnapshotMappers.kt` |
| 仓储层（事务、日历同步编排） | `app/src/main/java/com/example/trainlog/data/repo/TrainRepository.kt` |
| 日历同步接口与系统实现 | `app/src/main/java/com/example/trainlog/data/calendar/CalendarSyncer.kt` |
| 剪贴板 / 日历描述文本格式化 | `app/src/main/java/com/example/trainlog/util/PlanTextFormatter.kt` |
| 日历主页 | `app/src/main/java/com/example/trainlog/ui/calendar/` |
| 当天计划详情/编辑 | `app/src/main/java/com/example/trainlog/ui/plan/` |
| 模板列表 / 编辑 | `app/src/main/java/com/example/trainlog/ui/templates/` |
| 单元测试 | `app/src/test/java/com/example/trainlog/` |

### 快照语义的实现

- **模板 → 日计划**：`TrainRepository.applyTemplateToDate()` 在单个 Room 事务（`db.withTransaction`）中，通过 `SnapshotMappers.planDraftFromTemplate()` 把模板动作和指标做值拷贝插入 `plan_actions` / `action_metrics`；`day_plans.source_template_id` 仅作追溯，展示时不回查模板。
- **修改模板**：`updateTemplate()` 事务内整体替换模板动作，只影响之后新建的日计划。
- **修改某天计划**：`updateAction()` / `moveAction()` 等只写当前计划的动作行，不影响模板与其他日期。
- **日计划 → 模板**：`savePlanAsTemplate()` 事务内通过 `templateDraftFromPlan()` 拷贝目标值（实际完成值不进模板），创建全新的模板与动作副本。

### 日历同步与剪贴板

- `CalendarSyncService` 封装“创建 vs 更新”分支：计划已有 `calendarEventId` 时调用 `updateEvent()` 更新原事件；事件被用户在系统日历中删除（更新影响行数为 0）时回退重建，保证同一计划不产生重复事件。eventId 与同步状态保存在 `day_plans` 表。
- 删除计划时可勾选“同时删除系统日历事件”。
- 日历事件标题为计划名称，描述使用与剪贴板相同的动作清单文本（含目标指标、实际完成和备注）。
- 复制计划输出纯文本，例如：

```text
2026-09-28 跑步计划 1
1. 长跑：目标 3 km；实际 3.2 km
2. 短跑：目标 100 m x 8；实际 100 m x 8
备注：状态不错
```

## 构建与运行

环境要求：

- JDK 17+（本仓库在 Temurin 21 上验证）。
- Android SDK：`compileSdk = 35`；`minSdk = 26`，`targetSdk = 35`。
- 在项目根目录创建 `local.properties`，写入 SDK 路径（Windows 示例）：

```properties
sdk.dir=C:/Users/<你的用户名>/AppData/Local/Android/Sdk
```

常用命令（Windows 用 `gradlew.bat`，macOS/Linux 用 `./gradlew`）：

```bash
# 构建 Debug APK
gradlew.bat :app:assembleDebug
# 产物位置
# app/build/outputs/apk/debug/app-debug.apk

# 运行全部单元测试（JVM）
gradlew.bat :app:testDebugUnitTest
# 测试报告
# app/build/reports/tests/testDebugUnitTest/index.html

# 安装到已连接的设备/模拟器
gradlew.bat :app:installDebug
adb shell am start -n com.example.trainlog/.MainActivity
```

也可在 Android Studio 中直接打开本仓库运行。

### 日历权限说明

- 应用在 `AndroidManifest.xml` 声明 `READ_CALENDAR` / `WRITE_CALENDAR`（危险权限）。
- 首次点击“同步到手机日历”时通过运行时权限申请（`registerForActivityResult`）；授予后立即执行同步。
- 拒绝授权时应用不崩溃：界面提示“未授予日历权限，无法同步；可在系统设置中开启”，其余功能（计划、模板、记录、剪贴板）不受影响。
- 模拟器上至少要存在一个日历账户（通常“Google 日历”本地账户即可），否则无法写入事件。

### 测试覆盖

`app/src/test` 下的 JVM 单元测试：

- `SnapshotMappersTest`：模板 → 日计划、日计划 → 模板的深拷贝（按序拷贝、值拷贝解耦、目标值保留、实际值不进模板）。
- `PlanTextFormatterTest`：剪贴板文本格式，含验收示例原文、未完成不输出实际段、多指标、无指标显示组数、备注行。
- `CalendarSyncServiceTest`：用 Fake `CalendarSyncer` 验证 eventId 的创建分支、更新分支（不重复插入）、事件丢失后的回退重建、删除分支。

## 后续扩展

云同步与账号体系、训练完成率与趋势统计、重复排期（每周几自动生成计划）、数据导入导出与本地备份。

## 模拟器验证记录

MVP 在 API 26（Android 8.0，x86_64，Pixel AVD）模拟器上完成冒烟验证，关键截图见 `docs/screenshots/`：

- `calendar-home.png`：月历主页，日期格按计划状态着色。
- `plan-applied.png`：模板应用到 2026-09-28 后自动生成动作列表，并显示"来源模板"。
- `templates-snapshot.png`：把当天计划（短跑已改为 6 组）保存为 WeekendRun 后，RunA 模板仍保持 8 组 —— 快照隔离生效。
- `after-restart.png`：杀进程重启后，原计划目标值与实际完成值完整保留。

日历同步在该无日历账户的 AOSP 镜像上验证了权限申请流程与失败提示（"同步失败：写入系统日历失败"，不崩溃）；事件创建/更新/删除分支由 `CalendarSyncServiceTest` 以 Fake 同步器覆盖，真机上存在日历账户即可正常写入。
