# 8-BALL CLUB · Render 免费部署

Render 免费 Web Service 可以跑这个 Python 服务（自带 SQLite）。
仓库里已经放好 `render.yaml`，按下面点几下即可。

---

## 1. 注册 Render（用 GitHub 登录）

1. 打开 <https://render.com>
2. 点 **Get Started** → **GitHub** 登录
3. 授权时选 **Only select repositories** → 勾选 `8-ball-club`

> 一般不需要信用卡；如果提示绑定，按页面要求操作即可。

---

## 2. 创建服务（Blueprint 方式，最省事）

1. Render 控制台 → **New +** → **Blueprint**
2. 选择仓库 `Wdwzsn/8-ball-club` → **Connect**
3. Render 会自动读到根目录的 `render.yaml`，显示服务 `8-ball-club`
4. 点 **Apply** / **Create**

等 1~2 分钟构建完成，状态变成 **Live**。

---

## 3. 拿到网址

服务页顶部会显示：

```
https://8-ball-club.onrender.com
```

（名字可能带随机后缀，以页面显示为准）

验证：

- 打开 `https://你的地址/api/health` → 应返回 `{"ok": true, ...}`
- 打开 `https://你的地址/` → 注册 / 登录 / 匹配对战

---

## 4. 免费版的两个限制（重要）

1. **15 分钟没人访问会休眠**，下一次访问需要冷启动（约 30~60 秒）。
2. **磁盘是临时的**：重新部署后 `pool-online/data/pool.db`（账号数据）可能丢失。
   - 只是测试/演示没问题；
   - 要长期保存账号，二选一：
     - Render 付费实例 + Persistent Disk（约 $7/月）
     - 把数据库换成 **Supabase Postgres**（你已经有项目，我可以改 DB 层）

---

## 5. 更新代码

仓库已开启 `autoDeploy`：往 `main` 分支 push 后，Render 会自动重新部署。

也可以手动：服务页 → **Manual Deploy** → **Deploy latest commit**。

---

## 6. 常见问题

**构建失败：找不到 requirements.txt**
已放在 `pool-online/requirements.txt`，确认仓库里有这个文件。

**502 / 服务起不来**
- 看服务页的 **Logs**
- 确认启动命令是 `cd pool-online && python server.py`
- 本服务监听 `PORT` 环境变量（Render 会自动注入），代码已支持

**访问很慢**
免费实例休眠后的冷启动，正常现象；访问一次后就会热起来。
