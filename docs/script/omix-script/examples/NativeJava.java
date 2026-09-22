import net.minecraft.entity.Entity;
import cn.omix.util.misc.TimerSpeedUtil;
// 原生类型、继承方法、Lambda、内部类的编译回归样例。
class Snapshot { final String name; Snapshot(Entity entity) { name = entity.getName().getString(); } }
void onLoad() {
    commands.register("scriptwhere", args -> {
        if (!inWorld()) { log("menu"); return; }
        Entity entity = mc.player;
        java.util.function.Supplier<String> describe = () -> new Snapshot(entity).name + " " + entity.getEntityPos();
        log(describe.get());
    }, "scriptwhere");
}
