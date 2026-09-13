# 命令与选项

源码入口：`src/main/java/cn/omix/command/CommandManager.java`，实现位于同目录的 `impl/`。`.` 前缀由客户端处理，`/` 是 Minecraft/服务器命令，`#` 是另装的 Baritone 命令。下文 `<...>` 表示必填，`[...]` 表示可选，斜线分隔的是候选值，输入时不要带尖括号或方括号。

输入按空白拆分，不支持 shell 式引号转义；带空格的模块名可去掉空格，例如 `Auto Totem` 写为 `AutoTotem`。实际可执行命令和可补全参数由当前客户端与服务器决定。

## .help

显示所有已注册命令的用法以及通用模块设置命令，没有参数。

## .ai [baseurl/apikey/model/think/clear] [value]

### 禁止自我循环调用

## .chat <message>

### 禁止自我循环调用

## .toggle <module>

别名 `.t`。反转指定模块开关；`module` 为模块名，空格可省略。没有显式 true/false 参数。

## .bind <module> <key/none> 或 .bind list

别名 `.b`。设置模块启动快捷键或列出已有绑定。

| 选项 | 简介 |
| --- | --- |
| module | 要绑定的模块名称。 |
| key | 客户端 KeyUtil 识别的键名，例如 RSHIFT、X；键名不能识别时 KeyUtil 返回 0，会清除绑定。 |
| none | 清除该模块快捷键。 |
| list / l | 列出已绑定键位的模块，不再跟模块或键名。 |

## .cfg <load/save/list> [name] [--crypto 0/1/2]

别名 `.config`、`.c`。加载或保存模块配置；配置载入会改变模块及设置。

| 选项 | 简介 |
| --- | --- |
| list | 列出可用配置，不需要名称。 |
| load name | 加载指定配置；`l name` 为缩写。 |
| save [name] | 保存当前状态；省略 name 时保存当前配置；`s` 为缩写。 |
| l | 单独使用表示列出配置，带 name 则表示加载。 |
| name | 配置名称；不要用空格分词。 |
| --crypto 0 | 仅用于 `save name --crypto 0`，以明文存储。 |
| --crypto 1 | 仅用于 `save name --crypto 1`，使用 normal 加密存储。 |
| --crypto 2 | 仅用于 `save name --crypto 2`，使用 heavy 加密存储。 |

不指定 --crypto 时沿用保存逻辑的已有/默认存储模式；不在 load/list 后传该选项。

## .fis

### 用户没有明确表达意图禁止调用 autoNfa 一类会一次性消耗用户余额的功能。

管理 FisProxy 连接、会话和异步操作。无参数或 `help` 显示帮助；远端结果异步输出到游戏聊天，长结果最多显示前 40 行。参数取自客户端接入代码；具体可用服务、账号资源和操作状态由 FisProxy 返回。

| 子命令/参数 | 简介 |
| --- | --- |
| help | 显示 FisProxy 命令用法。 |
| apikey [key/clear] | 查询是否配置密钥，或设置/清除 FisProxy API 密钥。 |
| baseurl [url/default] | 查询或设置 API 地址；default 恢复 SDK 默认地址。 |
| clientid [id/auto] | 查询或指定请求使用的客户端 ID；auto 恢复自动客户端 ID。 |
| timeout [seconds] | 查询或设置 HTTP 请求超时，整数 1–300 秒。 |
| me | 查询账号信息。 |
| services | 列出可用服务。 |
| status | 查询当前代理会话状态。 |
| entrances [serviceId] | 查询入口列表；可传服务 ID 筛选。 |
| start [key=value ...] | 创建或启动代理会话，可选参数见下表。 |
| changeip / change-ip [key=value ...] | 为当前会话请求更换出口 IP，选项见下表。 |
| stop | 停止当前代理会话。 |
| connect [entranceIndex] | 读取运行中的会话并连接游戏；非负入口索引从 0 开始，省略时使用默认会话地址。可能切换当前服务器连接。 |
| op / operation / operations | 管理异步操作，子命令为 get/list/wait/cancel。 |
| request / raw | 发起底层 API 请求，参数见后文。 |

`key=value` 的键不区分大小写并忽略连字符和下划线；重复或不认识的键会报错。布尔选项只接受 true/false。

### start 的全部选项

| 选项 | 简介 |
| --- | --- |
| serviceId | 要使用的服务标识。 |
| target | 传给代理服务的目标地址/标识。 |
| autoNfa | 是否请求自动分配 NFA 资源。 |
| nfaItemId | 指定使用的 NFA 资源条目。 |
| reuseNfaItemId | 指定希望复用的 NFA 条目。 |
| nfaSource | 限定 NFA 资源来源。 |
| nfaSku | 限定 NFA 资源 SKU。 |
| tryPreviousNfa | 是否尝试上一次使用的 NFA 资源。 |
| idempotencyKey | 此次请求的幂等键，用于服务端识别同一操作的重复提交。 |
| wait | 是否等待异步操作结束。 |
| timeoutSeconds | 等待操作的最长秒数，正数；与 HTTP timeout 是不同设置。 |
| intervalSeconds | 查询操作状态的间隔秒数，正数。 |

### changeip 的全部选项

| 选项 | 简介 |
| --- | --- |
| idempotencyKey | 换 IP 请求的幂等键。 |
| wait | 是否等待换 IP 操作完成。 |
| waitRouteAck | 是否等待路由确认。 |
| timeoutSeconds | 等待超时秒数，正数。 |
| intervalSeconds | 状态轮询间隔秒数，正数。 |

### op 的全部子命令与选项

| 用法/选项 | 简介 |
| --- | --- |
| get operationId | 查询指定操作的详情，operationId 为服务返回的操作 ID。 |
| cancel operationId | 请求取消指定操作，是否可取消由服务端决定。 |
| wait operationId [timeoutSeconds=180 intervalSeconds=1] | 轮询指定操作直到结束或超时；两个秒数均须为正数。 |
| list [status=... kind=... limit=20] | 列出操作，可组合以下筛选。 |
| status | 按服务端操作状态筛选，例如 running。 |
| kind | 按操作类型筛选，例如 session.start、session.change_ip。 |
| limit | 返回数量上限，整数；服务 SDK/服务端继续校验范围。 |
| timeoutSeconds | op wait 的最大等待时间，默认 180 秒。 |
| intervalSeconds | op wait 的轮询间隔，默认 1 秒。 |

### request/raw 的全部参数

用法：`.fis request <method> <path> [query={} body={} idempotencyKey=... sign=true/false]`。这是直接访问服务 API 的入口，效果取决于具体 method/path。

| 参数 | 简介 |
| --- | --- |
| method | HTTP 方法，补全提供 GET、POST、PUT、PATCH、DELETE。 |
| path | API 请求路径。 |
| query | 查询参数 JSON 对象；必须压缩为不含分词空白的单个参数。 |
| body | 请求体 JSON 对象，同样使用单个无空白参数。 |
| idempotencyKey | 请求幂等键。 |
| sign | 是否签名此请求，true/false。 |

## .modules

别名 `.l`、`.list`、`.Omix`。列出模块名称、开启状态与快捷键；不接收筛选参数。

## .show <module> / .hide <module>

`.show` 别名 `.s`、`.unhide`，取消模块的 HUD 隐藏；`.hide` 别名 `.h`，把模块从 HUD 列表隐藏。唯一参数 module 为模块名；这两个命令都不修改模块开启状态。

## .username

别名 `.name`、`.ign`。显示当前登录会话用户名；没有参数，不修改昵称。

## .vclip

在玩家当前 X/Z 位置向上搜索安全落脚点，并调用 LookTP 路径移动流程。**没有距离参数**，不要求先开启 LookTP 模块。

## .reconnect

别名 `.r`。断开并重连当前多人服务器；没有地址参数。

## .webpanel

输出可点击的本地 WebUI 地址；没有参数。

## .<module> [setting] [value]

每个已注册模块都提供一个设置命令入口，例如 `.aura range 3.2`。模块名和设置名匹配会忽略非字母数字字符和大小写；补全把名称空格变成连字符。命令无引号解析，配置项中的空格请用连字符替代。

| 参数/类型 | 简介 |
| --- | --- |
| module | 任一模块的显示名称，见六份模块参考；例如 `.autototem`。 |
| 不带 setting | 列出当前可见且可由聊天修改的设置；不是切换模块。 |
| setting | 配置项名称；MultiBool 使用子开关名称，不使用父组名。能指定因显示条件隐藏的可编辑项。 |
| Bool 无 value | 反转布尔值。 |
| Bool true/on/1 | 开启该配置项。 |
| Bool false/off/0 | 关闭该配置项。 |
| Bool toggle | 反转该配置项。 |
| Mode 无 value | 循环到下一个模式。 |
| Mode value | 设置表中列出的模式，允许多词模式值；忽略非字母数字字符及大小写匹配。 |
| Number 无 value | 查询当前数值及范围。 |
| Number value | 设置范围内的有限数值，按步长取整并限制在边界；越界输入报错。 |

Text、Color、Key 和 MultiBool 父组不支持此命令直接赋值。模块启动键用 `.bind`，其他文本、颜色或操作键在 WebUI 中编辑。通过 `run_client_command` 可使用除 `.ai`、`.chat` 外的已注册客户端命令与模块命令。
