# UCAS iClass Desktop

面向 UCAS 轻新课堂 / iClass 的独立 Windows 桌面客户端。

> [!CAUTION]
> 本项目仅供学习交流使用。它不是学校官方客户端，也不代表或隶属于
> `lccipher/UCAS-Course-Sign-in`、`zhan-nine/UCAS-Sign-in` 的 Windows 版本。

当前版本提供课程查询、动态签到二维码和手动签到主流程。Windows 凭据存储、系统托盘、后台自动签到、计划任务、开机启动与通知尚未实现。

## 当前功能

- 使用 SEP 邮箱或轻新课堂学号登录
- 查询并选择当天课程
- 按学校时间轴生成、自动刷新签到二维码
- 对选中课程手动提交签到
- 账号、密码和会话仅在当前进程内存中保存；关闭程序后即丢弃

## 安装与运行

Windows 安装包自带运行时，最终用户无需安装 Java。

开发构建需要 JDK 17 或更新版本：

```powershell
.\gradlew.bat :core:test
.\gradlew.bat :app:run
.\gradlew.bat :app:packageExe
```

安装包输出到：

```text
app\build\compose\binaries\main\exe\
```

安装器会创建桌面快捷方式，并在开始菜单中创建 `UCAS iClass Desktop` 分组。

## 项目结构

```text
UCAS-iClass-Desktop/
├─ app/              # Compose Desktop 界面与 Windows 打包配置
├─ core/             # 课程模型、学校接口与二维码时间轴
├─ docs/             # 开发与安全说明
├─ LICENSE
├─ LICENSES/
├─ NOTICE
└─ gradlew.bat
```

`core/` 是本桌面项目内部的纯 Kotlin/JVM 核心模块；它不再是 Android/Windows 的共享模块。本仓库不包含 Android 客户端、APK、Android SDK 配置或 Android 特有代码。

## 安全与隐私

- 请勿在他人电脑上输入或保存学校账号密码。
- 当前版本不将凭据、会话或课程内容写入磁盘。
- 账号密码会按学校登录流程提交至相应学校服务；本项目不提供自建账号服务。
- 请勿提交账号、密码、会话 Cookie、签名密钥或包含敏感信息的调试日志。

详见 [`docs/SECURITY_AND_PRIVACY.md`](docs/SECURITY_AND_PRIVACY.md)。

## 来源与许可

本项目独立维护，但课程查询、二维码和签到流程参考并继承自公开的 AGPL-3.0 项目：

1. [`lccipher/UCAS-Course-Sign-in`](https://github.com/lccipher/UCAS-Course-Sign-in)
2. [`zhan-nine/UCAS-Sign-in`](https://github.com/zhan-nine/UCAS-Sign-in)

具体文件归属、未包含的 Android 代码范围和二进制分发义务见 [`NOTICE`](NOTICE)。本项目整体以 [AGPL-3.0](LICENSE) 发布；分发 Windows 安装包时必须同时提供本仓库的对应源代码、许可证与 NOTICE。

## 开发

请先阅读 [`docs/DEVELOPMENT.md`](docs/DEVELOPMENT.md)。在真实学校账号完成手动流程验证前，不应实现凭据持久化、后台自动签到或计划任务。
