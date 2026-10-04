# Setsuna ClickGUI（Pop）

将 `ClickGui` 模块的 `Mode` 设为 `Setsuna`，关闭当前菜单，再按模块绑定键（默认右 Shift）打开。也可在游戏聊天中使用 `.clickgui Mode Setsuna` 切换模式。默认模式仍为 `Web`。

本模式移植 SetsunaClient 的 **Pop 环形菜单**：六个圆形分类入口、分类气泡展开为模块面板、右键展开并排设置面板、悬停与开关动画、日间配色及退出收缩动画。环形入口映射为 Omix 的 Combat、Exploits、Render、Move、Player、World 分类。

## 操作

- 左键分类气泡打开模块列表；左键列表顶部分类图标返回环形菜单。
- 左键模块切换启用状态；右键打开设置，再次右键同一模块收起设置；中键绑定模块快捷键。
- 设置面板顶部的键盘徽标也可绑定模块快捷键，右键徽标清除绑定。捕获按键时 Esc 取消，Delete / Backspace 清除。
- 滚轮分别滚动鼠标所在的模块列表或设置列表。
- 布尔值左键切换；模式左键下一个、右键上一个；数值拖动滑条，遵守最小值、最大值和步长。
- MultiBool 左键展开子开关，只显示满足显示条件的子项。
- 文本左键编辑，支持 Unicode、光标移动、选择、Ctrl/Cmd+A/C/X/V；Enter 完成编辑。敏感值显示为星号，禁用复制和剪切。
- Color 使用 RGB 滑条或 `#RRGGBB` 文本编辑；只有合法六位十六进制值会生效，Esc 恢复开始编辑时的颜色。Omix 的 ColorValue 不存储透明度。
- KeyValue 左键捕获按键，右键清空；允许鼠标的 KeyValue 可捕获鼠标按键，使用 Omix 的鼠标键编码。
- Esc 优先结束文本编辑或取消按键捕获，然后逐级收起设置、返回分类环、关闭菜单；再次按 ClickGui 的绑定键也可关闭（文本/按键编辑优先）。打开菜单的按键重复事件会被忽略，直到该键松开。

## ClickGui 设置

以下选项仅在 `Mode = Setsuna` 时显示，保存在现有模块配置中：

| 设置 | 默认值 | 用途 |
| --- | --- | --- |
| Setsuna Daylight | false | 切换日间主题，颜色平滑过渡。 |
| Setsuna Blur | 5（0–10，步长 1） | 背景模糊强度，0 关闭；复用现有原生模糊渲染。 |
| Setsuna Accent | RGB 166, 86, 238 | 环线、设置滑条和开关的强调色；分类气泡保留原版分类配色。 |
| Setsuna Scale | 100（65–125，步长 5） | 菜单缩放百分比；小窗口自动缩小以保证两栏可见。 |

## 模块与配置适配

模块列表从 `ModuleManager` 实时读取；设置直接使用 `Module.getValues()` 和 `Value.isVisible()`，支持全部七种原生 Value、脚本新增模块、动态模式及条件显示。模式切换经过 `ModeValue.setValue()`，保留脚本监听器行为。隐藏、卸载或滚出可见区域的设置会停止拖动、编辑及按键捕获。

关闭菜单或切换至另一屏幕时，将状态保存到 `ConfigManager.getCurrentConfig()` 返回的当前配置，保留原有 `ModuleConfig` 格式和加密模式。配置加载后由同一组 Value 反映结果；不创建 Setsuna 独立配置文件。配置的新建、加载、删除沿用现有配置界面或命令。

## 移植范围与源码

来源为 SetsunaClient 的 `com.setsuna.ui.clickgui.PopClickGuiScreen` 和 `CategoryGlyphs`。本次仅移植 Pop；源项目的 Drop 面板、特定模块的 ESP/TargetHUD/KillEffect 预览、音乐预览和水印位置联动不属于通用菜单适配。

渲染改为 Minecraft 1.21.11 的 `DrawContext` 和 Omix 字体/模糊接口，圆角几何按原生延迟绘制规则提交，不依赖源项目的 Skija 或 Minecraft 26.1 API。字体按实际绘制密度栅格化；绘制投影和鼠标事件分别处理 Minecraft GUI 尺寸取整。

- 屏幕：`src/main/java/cn/omix/ui/setsuna/SetsunaClickGuiScreen.java`
- 布局、绘制和设置辅助类：`src/main/java/cn/omix/util/setsuna/`
- 测试：`src/test/java/cn/omix/util/setsuna/`
- 原版图标字体、中心图案及来源许可：`src/main/resources/assets/omix/setsuna/`

辅助类复用现有 Unicode 文本缓冲区；未引入源项目的模块或配置管理器。
