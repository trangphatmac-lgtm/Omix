import java.util.concurrent.atomic.AtomicInteger;
AtomicInteger received = new AtomicInteger();
void onLoad() {
    var feature = modules.register("packets", "Script Packets", Category.Player);
    feature.on(PacketEvent.class, event -> {
        // 网络线程：只操作线程安全计数，不触碰世界或 UI。
        if (event.getType() == PacketEvent.Type.Received) received.incrementAndGet();
    });
    feature.on(TickEvent.class, event -> feature.suffix(String.valueOf(received.getAndSet(0))));
}
