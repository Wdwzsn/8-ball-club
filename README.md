# 8-BALL CLUB · 台球游戏

一个拟真八球台球游戏，包含 **Java 桌面版** 和 **在线对战版**。

```
java-pool/     Java 桌面版（Swing，无第三方依赖）
pool-online/   在线对战（Python 服务端 + 网页客户端，含账号数据库）
```

## Java 桌面版

- 绿色台呢、木框、球体光影、真实滚动摩擦与库边反弹
- WPA 标准八球规则；电脑对手会用高低杆/塞
- 人机对战 / 双人同屏 / 局域网联机
- 「我的生涯」：6 个卡通头像、人机战绩、11 个成就
- 运行：双击 `java-pool/run.bat`，或 `java -jar java-pool/dist/8-ball-club.jar`

## 在线对战

**在线体验：<https://eight-ball-club.onrender.com>**

- 账号注册/登录（SQLite + PBKDF2 密码哈希）
- 服务端权威物理与规则，浏览器拖杆击球
- 本地运行：`cd pool-online && python server.py 8080`，浏览器打开 `http://127.0.0.1:8080`

详见 [`java-pool/README.md`](java-pool/README.md) 与 [`pool-online/README.md`](pool-online/README.md)。
