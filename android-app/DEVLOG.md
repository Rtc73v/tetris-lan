# Tetris LAN 开发记录

## 项目概述

- **名称**: 俄罗斯方块 LAN 对战版
- **包名**: `com.echo.tetrislan`
- **技术栈**: Android Canvas 原生渲染，P2P 局域网对战
- **源码目录**: `/root/tetris-lan/android-app`
- **构建环境**: `ANDROID_HOME=/opt/android-sdk`

## 网络架构

- **发现层**: UDP 广播 (端口 39731) — 房间发现
- **数据层**: TCP 直连 (端口 39732) — 状态同步、聊天、垃圾行
- **协议格式**: `TG1|ROOM|PASS|TYPE|senderName|...|playerId`
- **消息类型**: HELLO / STATE / READY / CHAT / START / GARBAGE / KO / LEAVE / KICK / DISBAND / SURRENDER / RETURN_LOBBY / RECONNECT / BOT_STATE
- **Bot 不走网络**: Bot 是本地模拟对象，通过内存直接分发垃圾行

## 版本历史

| 版本 | versionCode | 关键修改 |
|------|-------------|---------|
| v1.9.2 | 25 | 挖掘模式修复：`digCleared` 独立计数器；`advanceStage()` 改为不清盘追加垃圾行；移除自动保存 |
| v1.9.3 | 26 | Bot 垃圾行双通道：`botLock()` 消行后真人收到垃圾行；`doClear()` 消行后 Bot 收到垃圾行 |
| v1.9.4 | 27 | Bot 速度放缓 3 倍：`dropDelay`/`thinkUntil` 系数整体 ÷3 |
| v1.9.5 | 28 | Bot 速度再放缓 3 倍；APK 文件名带版本号 (`outputFileName`) |
| v1.9.6 | 29 | 结算逻辑修复：无真人时正常结算；有真人时等所有真人被 KO 才结算 |
| v1.9.7 | 30 | KO 归属追踪：新增 `lastAttacker`、P2P `KO` 消息协议、Bot/真人互记 KO |
| v1.9.8 | 31 | 断联误判修复：`onPeer()` 收到数据时重置 `disconnected = false` |
| v1.10.0 | 32 | 大版本：挖掘模式过关溢出修复（增量推进替代全板清空）；无尽生存合并到经典模式速度档位；存档扩充完整字段；结算UI重叠修复；DAS/ARR粒度加倍；垃圾行进场预告；最高分保存+显示；Bot清理修复；垃圾行计算去重 |

## 关键设计决策

### 1. 挖掘模式 (MODE_DIG)
- 使用独立的 `boolean[20][10] isGarbage` 标记每格是否为预设垃圾行
- 不修改 board 数值类型（7 为垃圾行标识）
- `advanceStage()` 不清盘，底部插入新垃圾行；顶部空间不足则判定被撑爆
- `doClear()` 只统计包含垃圾行的消行计入 `digCleared`

### 2. Bot AI 参数
```java
// 下落间隔 (ms/格)
bot.dropDelay = max(1050, min(3600, 4200 - level*15 - actionSpeed*7));
// 思考间隔 (ms)
bot.thinkUntil = now + max(600, 2700 - actionSpeed*13);
```
- `actionSpeed` 范围 1-10，越高越快
- `iq` 范围 1-10，控制寻路质量

### 3. 垃圾行封顶
- `garbageFor()` 单次最多返回 4 行

### 4. 版本号同步（强制）
- `APP_VERSION`（`TetrisView.java`）
- `versionCode` / `versionName`（`app/build.gradle`）
- 三者必须一致，每次构建同步更新

### 5. APK 签名
- Debug 使用固定 keystore，**不可替换**

## 已知问题与待办

| 问题 | 状态 | 备注 |
|------|------|------|
| 互联网 P2P 跨 NAT 穿透失败 | 待研究 | UDP 广播 + TCP 直连在公网 NAT 后无法互通。需调研 STUN/TURN/中继方案 |
| 结算逻辑 | 已修复 | v1.9.6/v1.9.7/v1.9.8 三连修，当前逻辑稳定 |
| Bot 速度 | 已修复 | v1.9.4/v1.9.5 放缓到合理水平 |

## 构建命令

```bash
cd /root/tetris-lan/android-app
export ANDROID_HOME=/opt/android-sdk
./gradlew assembleDebug
# 输出: app/build/outputs/apk/debug/tetrislan_v{versionName}.apk
```

## 核心文件清单

| 文件 | 职责 |
|------|------|
| `TetrisView.java` | 主游戏逻辑、渲染、Bot AI、P2P 回调 |
| `P2pTransport.java` | UDP/TCP 网络层、协议编解码 |
| `app/build.gradle` | 构建配置、版本号、APK 文件名 |

## 最近修改摘要（v1.9.8）

1. **结算**: `checkMultiFinish()` 分两种情况——无真人用原始逻辑；有真人只统计真人存活
2. **KO 归属**: 新增 `lastAttacker` 字段，`spawn()`/bot 死亡时通知攻击者，`onKO` 回调增加 `kos`
3. **断联修复**: `onPeer()` 中 `pi.disconnected = false; pi.disconnectedAt = 0;` 防止误判
