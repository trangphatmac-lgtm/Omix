# Jello Sigma 移植记录

源参考：用户提供的 `SigmaRebase`，`com.mentalfrostbyte.jello` 的 Jello 模式。
目标：Minecraft 1.21.11 / Yarn，所有辅助类位于 `cn.omix.util.sigma`。

## 验收范围

- ChestESP：Implementation = Classic / Omix / Sigma；Sigma 的 Outline / Box、三类箱子开关及颜色。
- ESP：Implementation = Classic / Sigma；Shadow / Sims / Box Outline / Vanilla、实体过滤及颜色。
- NameTags：Magnify / Furnaces / Mob Owners；玩家名牌、生命值、熔炉跟踪、主人显示。
- Tracers：Implementation = Classic / Sigma；原版 Color 和从准星方向到目标中部的透明渐变。
- Waypoint：Unspawn Positions、地图共享路径点、世界隔离、动态地表高度与旋转标记。
- ClickGui：Mode = Sigma；Jello 分类面板、设置、按键绑定、Profiles、动画；不移植 Jello Music。
- Maps：Jello Maps 地图、缩放平移、地图缓存、路径点管理。
- HUD：Mode = Sigma；ActiveMods / BrainFreeze / Compass / InfoHUD / KeyStrokes / MiniMap / RearView / TabGUI；保留各自子配置。

## 适配原则

沿用原版图片、字体、颜色、布局、时长和计算公式。将 1.16.4 的即时 OpenGL 调用适配为 1.21.11 的 RenderPipeline / VertexConsumer / DrawContext；不调用已经移除的固定功能管线。原有模式默认值和配置键保持兼容。客户端视觉效果不得改写服务端实体状态。

逐帧工作仅负责绘制，已加载区块扫描、地形采样、资源加载和持久化应有缓存或预算；不得为了显示主动请求未加载区块。

## 启用方式

在 ClickGui 中将 `Mode` 设为 `Sigma`，再用原来的右 Shift 打开；HUD 单独将 `Mode` 设为 `Sigma`，按需开启八个子组件。ChestESP、ESP、Tracers 的 `Implementation` 单独切换为 `Sigma`。NameTags / Waypoint 位于 Render，Maps 位于 World，Maps 启用后打开窗口并自动关闭模块状态。

ClickGUI 左键切换模块、右键配置、中键绑定按键，标题栏可拖动；右下角打开 Profiles。地图右键新增路径点，左键拖动地图、滚轮或右下角按钮缩放；左键列表定位，右键编辑，拖动手柄排序，拖到左下角垃圾桶删除。位置、地形、路径点保存在运行目录 `Omix/sigma/`；地图数据按服务器/本地存档及维度隔离。

打开 ClickGUI 的按键由模块入口消费，不再传给刚打开的界面；按住开启键产生的重复事件会忽略到松键为止。松开后再按绑定键或按 Esc 可关闭，避免 Sigma 模式在同一次右 Shift 输入中立即关闭。`ClickGui: OFF` 是界面入口模块打开后自动复位的提示，不代表界面开启失败。

请求中的模块和组件已实现并完成下述验收；Minecraft 版本、配置格式及外部服务的适配边界见后文。

### 已验证

- Java 编译通过；Sigma 几何、动画、存储、高斯模糊和配置名校验共 13 项测试通过。
- `gradlew build -x test -x syncModVersion` 通过，包含 remapJar、前端/脚本/Music sidecar/AI Harness 检查与打包资源完整性检查。产物为 `build/libs/omix-260925-SNAPSHOT.jar`。模块文档校验通过（97 个模块、505 项配置/分组）。
- 全量 Java 测试已运行：310 项，4 项失败、2 项跳过。失败均在未修改的 `GrimFullStateTest`：测试断言 30 秒刷新，而未修改的 `GrimFullState` 使用 60 秒；与本次 Sigma 移植无关。不能将全量测试描述为全部通过。
- 在独立的 Minecraft 1.21.11 测试目录、1280×800 窗口及 GUI Scale 3 中检查了实际截图：原版字体、水印、分类面板、设置弹窗、箱子 Outline/Box、ESP 四种模式、玩家/主人名牌、地图、MiniMap、RearView、InfoHUD 玩家模型均能显示。
- 实际输入验证：地图右键新增、改名、坐标/颜色修改、列表手柄拖动排序和拖入垃圾桶后的动画删除；删除后内存与磁盘快照一致。ESP 设置下拉切换、布尔开关和颜色拖动通过。
- ClickGUI 的模块入口、中键绑定、标题拖动及重开后的布局持久化通过；BrainFreeze 的粒子已检查。TabGUI 的 Enter 和右方向键切换模块通过，InfoHUD 装备/耐久、精确坐标、聊天偏移及 F3 下布局已检查。
- ClickGUI 按键回归经过实际 `Keyboard.onKey` 分发入口：右 Shift 和自定义 F8 的按下、长按重复、松开、再次按下关闭、重新打开、Esc 关闭均通过；覆盖直接调用模块开启无法发现的同次按键重复分发问题。
- RearView 的 120 像素尺寸、Smart Visibility（前方隐藏/身后显示）、Show in GUI 及隐藏后的缓冲区释放通过。
- 960×600 / GUI Scale 2 与 1280×800 / GUI Scale 3 的缩放切换通过；小窗口中地图标题完整可见。主世界→下界→主世界的地图、路径点和后视图恢复通过；Unspawn Positions 记录玩家卸载位置，并在世界切换时清空。
- Classic 模式的 HUD、ESP、ChestESP、Tracers 回归显示正常；切换后闲置的 Sigma 模糊、遮罩和后视镜缓冲区释放通过。地图未使用时不采样，Maps 首次打开后开始记录，已实机验证。
- 正常退出世界并保存后，地图区域纹理、路径点世界状态和离屏缓冲区清理通过；接受删除后立即关闭地图也会完成删除，不会被动画中断撤销。
- 世界事件钩子接入 `MinecraftClient.setWorld(ClientWorld, boolean)` 共用入口，覆盖新版正常断开连接直接调用该重载的路径，避免仅钩住单参数入口而遗漏退出清理。
- Profiles 的复制、行内改名、创建空白、切换与删除均经过真实点击验证。
- 熔炉真实交互包、窗口 ID、库存与燃料/烧炼进度已验证；关闭窗口后同一时刻的估算/服务端数量均为 5。已确认后台读取 70 个内置烧炼配方，未解锁的粗金可推算金锭。

### 版本适配与数据

- 所有视觉辅助类放在 `cn.omix.util.sigma`，两个原生 Screen 放在 `cn.omix.ui.sigma`。字体、图片直接使用提供的 Jello 资源；音效转为原生声音系统可播放的 OGG。
- 轮廓用离屏联合遮罩适配原版 stencil；高斯缩放按钮、TabGUI 的 35 像素模糊和界面的 20 像素模糊保持原公式/半径。RearView 使用独立相机与完整世界渲染，不改动玩家旋转。
- ESP 保留源过滤规则：Shadow 仅可见非机器人玩家，Sims 包含隐形玩家；这两种模式独立于父级四个过滤开关。Box Outline/Vanilla 沿用父级开关及源码的 MobEntity 分类（含动物）。
- Profiles 使用 Omix JSON/加密配置格式；复制与重命名保留文件内容，Blank 从配置加载前的初始模块值创建。Default 保留 Omix 原有保护。Sigma 旧在线预设属于另一套模块格式，当前不导入，显示原版无预设状态。
- ClickGUI / TabGUI 使用 Omix 的模块及分类；旧版即时 OpenGL 与新版 GPU 管线的抗锯齿、字体栅格化可能有细微差别。未声称对所有分辨率做逐像素比对；Sodium/Iris 等替代渲染器未在本轮验证。
- ActiveMods 的计分板偏移沿用原版公式；F3 下模块列表移到右侧调试信息下方，原版公式没有额外避让该偏移，部分调试布局仍可能与计分板重叠。
- 熔炉估算优先同步配方展示，未解锁的普通原版配方由后台读取内置 SERVER_DATA 后缓存推算；再次打开熔炉时以服务端库存和进度覆盖估算。其他玩家、漏斗和服务器自定义规则在窗口关闭期间的变动无法获知。

### 性能约束

- 地形在 Maps/MiniMap 首次使用后才开始记录；仅采样已加载区块，每 tick 最多 4 个区块并受 1.5ms 预算限制；区域纹理分批上传，磁盘操作由单独的有界队列处理。路径点写入按文件合并最新快照，避免拖动排序产生无限写入队列。
- 字体图集使用 1024×1024 页面按需增长；与原 Omix 字体共用代码时保持原有默认采样方式。
- 路径点保留原版 360 段圆环，但使用缓存的圆周顶点和三个连续缓冲批次，避免圆环逐段切换绘制层造成数百次提交。
- HUD 模糊只处理 TabGUI 涉及的列，RearView 和模糊刷新不超过 HUD FPS 与 60 FPS 的较小值；闲置离屏资源释放。HUD 缓存随世界、窗口、GUI 比例或纹理重建失效。
- 路径点加上视锥裁剪，并保留源绘制顺序。独立平坦测试场景（2 个路径点，1280×800，i5-12600KF / RTX 4070 SUPER）中，修复批次切换前的帧耗时中位数约 17.8ms，修复后采样约 0.9–1.5ms，P95 约 1.6–2.5ms；该诊断采样用于确认异常提交开销已消除，场景开关并非严格受控基准，不能推广为真实服务器性能保证。

### 复现检查

```powershell
.\gradlew.bat compileJava -x syncModVersion --console=plain
.\gradlew.bat test --tests 'cn.omix.util.sigma.*' -x processResources -x syncModVersion --console=plain
python tools/generate_client_reference.py --check
.\gradlew.bat build -x test -x syncModVersion --console=plain
```

实机使用独立 `build/sigma-run` 存档，未改写主运行目录的用户配置。调试截图与本地桥接验证脚本在忽略目录 `build/sigma-inspect`。重点截图：`maps-small-final.png`、`map-final-reordered.png`、`clickgui-brainfreeze-final.png`、`rear-smart-small.png`、`classic-regression.png`、`furnace-fixed.png`、`owner-label.png`。
