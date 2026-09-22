# 源码与生命周期

## 编译

`.java` 是类体片段。允许显式 import、静态 import、字段、方法、内部类、Java 21 语法与 Lambda。自动导入 java.util、Color、脚本 API、Category、Value 实现、全部客户端事件、Minecraft math、RotationRequest 和 MovementCorrection。导入 java.lang.Module 冲突时显式 import cn.omix.module.Module。

内置 ECJ，source/target 21，禁用注解处理。不依赖启动器的 javac。开发环境使用 named 类路径；生产环境把本地运行类路径映射为当前锁定的 Yarn named 后编译，再把脚本输出映射回运行命名空间。映射和编译在单独工作线程执行。文件、依赖、映射变化会失效对应缓存；每代使用独立 ClassLoader，父加载器共享客户端类型身份。

编译诊断保留原始行列，运行日志映射脚本栈帧行号；自动包装生成的代码标为行 0。原生 Java 编译诊断可能含生成类名，脚本行号仍可跳转。

## 替换

源码快照 → 后台编译和映射 → 客户端线程构造及 onLoad 暂存注册 → 冲突检查 → 卸下旧注册 → 安装新注册、恢复设置和开关 → 保存加载清单 → 清理旧代次。

编译/准备失败保留旧运行版本。提交失败清理新资源并尝试恢复旧受管状态。Java 原生副作用不可事务回滚；onLoad 应只声明功能。JVM 对未执行路径采用惰性链接，未走到的原生调用仍可能在运行时抛出 LinkageError，此时隔离出错功能并记录日志。

同稳定 ID 的模块恢复 key、hidden、值、HUD 位置和开关。恢复先设置后启用。所有注册在 onLoad 中完成；已卸载代次不能通过脚本 API重新注册/提交游戏操作。通过任务 API提交到客户端线程的工作绑定当前世界身份，换世界时丢弃迟到工作。

## 所有权与错误

`feature.onEnable/onDisable/on` 在功能启停时工作。`feature.own(resource)` 绑定本次启用周期，停用逆序释放；`script.own(resource)` 绑定整个代次，卸载逆序释放。Registration.close 幂等。运行回调异常停用该功能；脚本级 events.on 只停用出错监听器。

`onUnload()` 在受管资源注销后调用，用于清理自建原生资源；此时不能再注册或提交受管操作。后台任务由 tasks.async 记录 Future 和线程，卸载取消并 interrupt；原生代码必须合作响应中断。自建线程、外部文件修改、已发出的包、服务操作和非受管注册不承诺回滚。

`.loaded.json` 是启动加载意图；`.cache` 是可重建编译缓存；`.data/<id>` 是持久数据；`.agent` 是随客户端导出的开发包。退出先保存用户配置再注销脚本。
