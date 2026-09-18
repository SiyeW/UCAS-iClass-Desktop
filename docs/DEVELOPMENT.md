# 开发说明

## 边界

本仓库仅维护 Windows 桌面客户端。不要把 Android SDK、APK、Android 小部件、Android 前台服务、WorkManager、AlarmManager 或 Android Keystore 引入本仓库。

`core/` 只容纳不依赖桌面 UI 或 Windows API 的 Kotlin/JVM 协议逻辑。`app/` 容纳 Compose Desktop UI、Windows 打包和未来的 Windows 专属实现。

## 本地验证

```powershell
.\gradlew.bat :core:test
.\gradlew.bat :app:compileKotlin
.\gradlew.bat :app:packageExe
```

`packageExe` 的安装包在 Windows 上包含运行时。不要将 `app/build/`、`.gradle/`、`.tooling/`、凭据、会话或任何学校账号数据提交到 Git。

## 后续功能顺序

1. 先用真实账号完成登录、课程查询、二维码刷新和手动签到的人工验证。
2. Windows 凭据保存只可使用可审查的 DPAPI 方案，必须保持默认关闭、仅在成功登录后写入，并提供清除操作。
3. 实现托盘、显式退出和 Windows 通知。
4. 只有在交互流程与失败语义稳定后，才评估计划任务或其他后台能力。

每次修改 `core/` 后都应保留或新增相应 JVM 单元测试，并同时执行打包验证。
