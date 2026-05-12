# Tetris LAN

局域网最多 3 人俄罗斯方块。

## 启动

```bash
cd /root/tetris-lan
npm start
```

当前地址：

```text
http://192.168.2.217:8081/
```

同一 WiFi 下，最多 3 台设备打开同一地址即可进入同一房间。

## 手机安装

### 方式 A：PWA/桌面快捷方式

安卓 Chrome 打开 `http://192.168.2.217:8081/`，菜单选择“添加到主屏幕”。

局域网 HTTP 不是 HTTPS，部分 Chrome 版本只会创建网页快捷方式，不一定触发完整 PWA 安装提示。

### 方式 B：原生 APK

已生成 Android WebView 工程：

```text
/root/tetris-lan/android-app
```

构建需要 JDK + Gradle/Android SDK：

```bash
cd /root/tetris-lan/android-app
gradle assembleDebug
```

本机当前缺少 Java/Gradle/Android SDK，不能直接产出 APK。

## 功能

- 最多 3 人局域网联机
- 对手小屏同步
- 消 2 行以上向其他玩家发送垃圾行
- 自定义触摸键位
- DAS/ARR/软降速度调节
- Hold 暂存
- SRS 简化墙踢
- T-spin 检测
