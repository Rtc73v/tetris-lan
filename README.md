# Tetris LAN

局域网俄罗斯方块对战（Android 原生 Canvas 渲染）。

## 下载

最新 APK 从 [Releases](https://github.com/er46s/tetris-lan/releases) 下载。

## 功能

### 单人模式
- 经典模式：传统玩法，可存档最高分
- 冲刺模式：竞速消除 40 行
- 限时模式：2 分钟内极限得分
- 马拉松模式：150 行通关挑战
- 隐形模式：落底后方块隐形
- 挖掘模式：清除预设垃圾行
- 生存模式：速度无限提升
- 关卡化设计：完成目标后自动进入下一关，支持本关重试 / 从头再来
- DAS / ARR / 软降速度调节
- Hold 暂存、SRS 简化墙踢、T-spin 检测
- 自定义触摸键位布局

### 多人模式（2-3 人局域网 P2P）
- 房间名随机生成，不与玩家绑定
- 房主一键开局，Bot 自动参战无需准备
- 真人玩家需准备，Bot 自动算作已准备
- Bot 数量可添加至房间人数上限
- 房主断线后按加入顺序自动选举新房主
- 玩家主动退出视为认输
- 断线重连：掉线方游戏自动暂停，恢复后继续
- 支持重连上一局（自动检测房间是否仍在线）
- 自定义玩家名称
- 消 2 行以上向其他玩家发送垃圾行
- 坚持到最后且分数最高者获胜

## 技术栈

- Android Canvas 原生 2D 渲染（无游戏引擎依赖）
- 自定义 Tetromino 旋转系统（基于 SRS 简化）
- 局域网 UDP 发现 + TCP 可靠传输的 P2P 架构
- Bot 决策算法：基于最优落点评估（ holes / height / bumpiness / clear / T-spin / combo 加权）

## 构建

```bash
cd android-app
ANDROID_HOME=/opt/android-sdk ./gradlew :app:assembleDebug
```

APK 输出：`app/build/outputs/apk/debug/app-debug.apk`

## 灵感来源

- 旋转系统与墙踢规则参考 Tetris Guideline（SRS）
- 垃圾行机制参考 Tetris Battle / Tetris 99
- Bot 评估函数设计参考 Classic Tetris AI 启发式策略
