// 没有主模式的 NoFog 临时增加 Behavior；只在脚本模式启用时移除雾。
void onLoad() {
    var mode = modes.register("clear", "NoFog", "Script Clear");
    mode.hook(ModeHooks.INTERCEPT, operation -> operation.equals("fog"));
}
