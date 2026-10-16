# 账号管理与 Cookie 登录

从主菜单的 `Account Manager` 或多人游戏页面的账号入口打开账号管理。原有 Microsoft 浏览器登录、邮箱密码、Access Token 和离线账号入口继续可用。

## 导入 Cookie

1. 在浏览器中登录自己的 Microsoft / Minecraft 账号，导出登录 Cookie。
2. 点击账号管理第三行的 `Cookie Login`。
3. 任选一种导入方式：
   - 在输入框中输入 Cookie 字符串，或用 `Ctrl+V` / `Cmd+V` 粘贴，然后点击 `Login`。输入内容以星号显示。
   - 点击 `Paste & Login`，直接读取剪贴板并登录。支持包含换行、制表符的完整导出内容。
   - 点击 `Open cookie file...` 选择 UTF-8 的 `.txt`、`.json` 或 `.cookies` 文件，也可以直接拖入一个文件。
4. 页面返回账号列表并显示登录进度。成功后保存账号、切换当前游戏会话，账号名前显示 `[Cookie]`。点击 `Back` 或按 Esc 可取消尚未完成的登录。

支持的内容格式：

| 格式 | 说明 |
| --- | --- |
| JSON | Cookie 对象数组，或包含 `cookies` 数组的对象；支持 `domain` / `host`、`path`、`secure`、`hostOnly`、`httpOnly`、`expirationDate` |
| Netscape | 七列、制表符分隔的浏览器导出；支持 `#HttpOnly_` 行，`0` 到期时间表示会话 Cookie |
| Cookie 字符串 | `name=value; name2=value2`，也支持每行一个 `name=value`，以及可选的 `Cookie:` 前缀 |

没有域名的字符串仅用于 `login.live.com`。JSON / Netscape 导出会保留域名、路径、HTTPS 限制和到期时间；无关域名、过期、空值、`Disabled` 值和格式错误的条目会被忽略。结构化导出解析失败时不会将其当作普通 Cookie 字符串提交。

Microsoft 登录通常需要 `__Host-MSAAUTH`、`__Host-MSAAUTHP`、`JSH`、`JSHP` 中至少一个可用于登录地址的 Cookie；已有有效 Minecraft `bearer_token` 或 `access_token` Cookie 也可直接验证游戏资料。缺失这些凭据时页面会提示错误。

## 保存与再次登录

选择 `[Cookie]` 账号后点击 `Use Selected`，或双击该账号。客户端先检查已保存的 Minecraft Token；验证失败后，用保存的 Cookie 重新走 Microsoft → Xbox Live → XSTS → Minecraft 流程，并保留参考实现的 minecraft.net 后备登录路径。成功后更新 Cookie、Token 和用户名，保留原有封禁记录。

Cookie 账号使用 `clientId: "cookie"` 标记，序列化 Cookie 存放在 `refreshToken` 字段中，Minecraft Token 存放在 `accessToken` 字段中；两个字段均沿用现有 `SafeStorage` 保存机制，文件仍为游戏运行目录下的 `omix.accounts.json`。不改变旧账号的文件结构，也不会把 Cookie 作为 Microsoft refresh token 提交。支持读取参考实现的 v2 Cookie 数据，新写入的 v3 数据还保存 host-only、HttpOnly 和有效期信息。

Cookie 失效、浏览器会话被撤销、Microsoft 要求交互确认，或账号没有可用 Minecraft Java 资料时，需要先在浏览器完成登录/确认，再重新导入。Cookie 属于登录凭据，不应分享导出文件或账号文件。

## 实现与验证

- Cookie 解析、HTTP 重定向和认证辅助类位于 `src/main/java/cn/omix/util/account/cookie/`；界面位于 `src/main/java/me/ksyz/accountmanager/gui/`。
- 网络认证在账号管理后台线程运行；成功结果在 Minecraft 客户端线程保存并切换会话。取消登录会中断后台任务并阻止迟到的结果切换账号。
- HTTP 使用项目现有 JDK 客户端和默认 TLS 校验，逐跳匹配 Cookie，只接受 Microsoft、Xbox、Minecraft / Mojang 登录相关域名的 HTTPS 地址，并设置请求超时和重定向次数上限。
- 相关测试位于 `src/test/java/cn/omix/util/account/cookie/`，使用虚构凭据和模拟 HTTP 响应，覆盖导出解析、Cookie 范围/删除/过期、保存兼容性、重定向与中断、Minecraft Token 提取；不访问真实账号。

运行针对性测试（这些测试不需要打包 WebUI 等资源）：

```powershell
.\gradlew.bat test --tests "cn.omix.util.account.cookie.*" -x processResources -x syncModVersion
```

真实 Cookie 的端到端登录仍需在游戏中用自己的账号验证，单元测试无法确认 Microsoft 当前是否接受某个浏览器会话。
