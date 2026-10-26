# CourseTable

北京理工大学本科课程表 App —— 极简、纯离线的课表查看工具。

> **这是一个临时过渡方案**：由于 [BIT101](https://github.com/BIT101-dev/BIT101) 目前登录失败、无法查看课表，作者在此期间为自己写了这个只做「看课表」这一件事的小工具。等 BIT101 恢复正常后，本项目可能不再更新。

当前版本：[v1.4.4](https://github.com/irislilymoon/CourseTable/releases/latest)

## 下载安装

前往 [Releases](https://github.com/irislilymoon/CourseTable/releases/latest) 下载：

| 文件 | 用途 |
|---|---|
| `CourseTable.apk` | App 本体，Android 8.0 及以上，下载后直接安装 |
| `bit-timetable-export.user.js` | 配套油猴脚本，用于从教务系统导出课表 JSON |

## 致谢与声明

本应用的界面布局与视觉风格参考了 [BIT101-Android](https://github.com/BIT101-dev/BIT101-Android)（AGPL-3.0）——「长得像」是有意为之。但本项目的**全部代码均为从零独立编写**：未阅读、未复制、未反编译 BIT101-Android 的任何源码，仅是在使用中学习了它的交互设计后自行实现，图标等资源也均为自制。

本项目与 BIT101 / BIT101-dev 官方无任何关联。

## 功能

- 周视图课表：自动定位当前周，课程色块，点击查看上课时间 / 地点 / 教师详情
- 支持含小学期（负数周，位于第 1 周之前）与长学期的学期结构
- 深色模式自动跟随系统
- 纯离线：无网络权限、无账号登录，数据只存在本机
- 极小体积（Release APK 不足 1 MB）

## 使用说明

App 本身不含抓取功能，课表数据通过配套油猴脚本从教务系统一次性导出：

1. 浏览器安装 [Tampermonkey](https://www.tampermonkey.net/) 扩展
2. 安装导出脚本：下载本仓库的 [`tools/bit-timetable-export.user.js`](tools/bit-timetable-export.user.js)（或 Release 附件），在 Tampermonkey 中「添加新脚本」，粘贴全部内容并保存
3. 登录北理工教务系统（jxzxehallapp.bit.edu.cn，需校园网 / VPN），进入「我的课表」页面，页面上会出现脚本浮动面板
4. 点击面板中的「抓取课表」，抓取完成后自动下载 `timetable-<起始日期>-<学期>.json`（若被浏览器拦截，点「下载课表 JSON」手动下载；也可以点「复制课表 JSON」）
5. 打开 App → 右上角「导入数据」→ 选择刚下载的 JSON 文件导入（或把 JSON 文本直接粘贴导入）。学期名、姓名、学期起始日等都来自 JSON，无需手动填写

> 课表有变动时重新执行第 3–5 步，新数据会覆盖旧课表。

## 构建

需要 JDK 17+ 与 Android SDK（`local.properties` 中配置 `sdk.dir`，此文件不入库）：

```bash
./gradlew assembleRelease
```

> 签名密钥不随仓库分发。克隆后如需构建 Release，请在 `app/build.gradle.kts` 中改用自己的签名配置，或直接构建 Debug 包（`./gradlew assembleDebug`）。

## 项目结构

```
app/src/main/java/com/example/coursetable/
├── MainActivity.kt     # 界面与交互（Jetpack Compose）
├── Models.kt           # 数据模型与周次 / 日期推算
├── DataNormalizer.kt   # 标准化课表 JSON 解析
└── TimetableStore.kt   # 本地持久化
tools/
└── bit-timetable-export.user.js   # 配套油猴导出脚本
```
