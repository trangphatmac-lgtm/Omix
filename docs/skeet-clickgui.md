# Skeet ClickGUI

在 `ClickGui` 模块中将 `Mode` 设为 `Skeet`，关闭菜单后再次按模块绑定键（默认右 Shift）打开。默认模式仍为 `Web`，原有 `Web / Remix / Sigma` 模式继续可用。

Skeet 移植自 Exhibition-Reborn 的 Gamesense 界面：390 × 350 逻辑画布、多层细边框、链纹背景、顶部动态彩色细线、左侧图标分类、双列模块分组框、紧凑的渐变控件。沿用源项目的 `skeetchainmail.png`、Tahoma 常规/粗体与 `icon.ttf`，渲染和输入已改用 Minecraft 1.21.11 的 `DrawContext`、GLFW 与当前客户端的字体管线。控件使用经典绿色强调色，模块按名称排序。

## 操作

- 拖动窗口顶部的细边框移动窗口。位置、分类与各分类的滚动位置在本次客户端运行期间保留。
- 左侧六个图标对应 Combat、Move、Player、World、Render、Exploit；最后的 `[+]` 打开配置页。悬停可查看分类名称。
- 点击模块的 `Enable` 开关；点击右侧 `[按键]` 或中键点击 Enable 区域进入模块绑定。右键点击绑定区域，或捕获按键时按 Esc / Backspace / Delete 清除绑定。
- 布尔值使用勾选框；数值使用滑条，按配置的最小值、最大值和步长取值；模式使用下拉单选；多布尔值使用下拉多选，选择后保持展开。
- 点击颜色预览打开饱和度/亮度和色相选择器。`ColorValue` 沿用客户端 RGB/HSB 模型，不增加未受配置系统支持的透明度。
- 点击文本框编辑，支持中文、Unicode、方向键、Home/End、Shift 选择，以及 Ctrl/Cmd+A/C/X/V。敏感文本遮盖显示，禁止复制和剪切明文。Enter 或 Esc 结束编辑。
- 点击 `KeyValue` 捕获按键；只有声明允许鼠标的设置接受中键和侧键，使用客户端既有鼠标键编码。捕获时 Esc / Backspace / Delete 清除该值。
- 滚轮滚动当前分类或长下拉列表。弹出菜单优先处理输入；点击菜单外部只收起菜单。Esc 先退出编辑、捕获或弹出菜单，再关闭界面；没有输入焦点时也可按 ClickGui 绑定键关闭。

窗口不随 Minecraft GUI Scale 改变逻辑尺寸，会自动适配小窗口及高 DPI；窗口和弹出菜单保持在可见范围内。

## 模块与配置适配

模块列表来自 `ModuleManager`，控件直接读写 `BoolValue / NumberValue / ModeValue / MultiBoolValue / ColorValue / TextValue / KeyValue`。每次布局重新检查 `Value.isVisible()`，因此原生可见条件、脚本可见条件、运行时新增/删除模块、动态设置与脚本扩展模式都会同步。模式写入经过 `ModeValue.setValue()`，保留监听器和异常回滚语义；隐藏或移除正在编辑的设置会清除其交互状态。

配置页使用 `ConfigManager` 和 `ModuleConfig`：

- `Create`：把当前模块状态另存为输入名称，不覆盖同名配置。
- `Load`：加载右侧选中配置并将其设为当前配置。加载会用文件中的值替换当前改动；需要保留的改动应先 Save。
- `Save`：将当前模块状态写入选中配置；保留该文件已有的 `none / normal / heavy` 存储模式。
- `Delete`：再次点击 `Confirm delete` 确认；`Default` 不允许删除。删除当前配置时按 ConfigManager 规则回退 Default。
- `Refresh`：重新读取本地配置列表；界面显示 Active 配置和选中配置的存储模式。

退出界面会保存当前配置，写入延迟到本次模块操作完成后，避免把 ClickGui 等临时打开界面的模块保存为启用状态。Skeet 仅处理当前客户端的 Omix 配置，不导入 Exhibition-Reborn 配置文件。

## 来源与实现

参考源码为 Exhibition-Reborn 的 `src/me/yuzusoft/gui/click/SkeetUI.java`、`component/TabComponent.java`、`component/impl/GroupBoxComponent.java` 及其设置控件。源项目 MIT 许可证和版权声明随移植资源保留于 `src/main/resources/assets/omix/skeet/LICENSE`，构建时一同打包。

入口为 `src/main/java/cn/omix/ui/skeet/SkeetClickGuiScreen.java`；布局、设置适配、文本编辑、绘制和配置辅助代码位于 `src/main/java/cn/omix/util/skeet/`。对应自动化测试位于 `src/test/java/cn/omix/util/skeet/`。
