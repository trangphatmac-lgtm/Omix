1）新增/修改模块/命令/AI Tools 后要更新位于 ./docs 的文档。
2）工具类和模块辅助类（utils）必须放在 `src/main/java/cn/omix/util/` 下，可按职责建立子包；不要放在 `src/main/java/cn/omix/module/` 下，也不要另建 `utils` 目录。即使仅供某个模块使用，也应遵循此约定。相关测试放在 `src/test/java/cn/omix/util/` 的对应子包，移动类时同步更新包声明和引用。
