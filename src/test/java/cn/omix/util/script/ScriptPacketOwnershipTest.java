package cn.omix.util.script;

import cn.omix.management.packet.SubCore;
import net.minecraft.network.packet.Packet;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ScriptPacketOwnershipTest {
    private SubCore core() { return new SubCore() {
        protected void onRelease(Packet<?> packet) { }
        protected boolean shouldIgnore(Packet<?> packet) { return false; }
    }; }
    @Test void releasingOneOwnerPreservesOtherOwnersAndRepeatedCleanupIsHarmless() {
        var core=core();Object one=new Object(),two=new Object();core.start(one);core.start(two);
        core.dispatch(one);assertTrue(core.active);core.dispatch(one);assertTrue(core.active);
        core.dispatch(two);assertFalse(core.active);
    }
    @Test void unownedCleanupDoesNotStopLegacyOrNewWorldOwners() {
        var core=core();Object old=new Object(),next=new Object();core.start(old);core.clear();core.start(next);
        core.dispatch(old);assertTrue(core.active);core.dispatch(next);assertFalse(core.active);
        core.start();core.start(old);core.dispatch(old);assertTrue(core.active);core.dispatch();assertFalse(core.active);
    }
    @Test void alreadyCancelledPacketsAreNotReplayedByBuffering() {
        var core = core(); core.start(new Object());
        Packet<?> packet = (Packet<?>) java.lang.reflect.Proxy.newProxyInstance(Packet.class.getClassLoader(), new Class<?>[]{Packet.class}, (proxy, method, args) -> null);
        var cancelled = new cn.omix.event.impl.PacketEvent(packet, cn.omix.event.impl.PacketEvent.Type.Send);
        cancelled.setCancelled(); core.handle(cancelled); assertTrue(core.packets.isEmpty());
        var allowed = new cn.omix.event.impl.PacketEvent(packet, cn.omix.event.impl.PacketEvent.Type.Send);
        core.handle(allowed); assertTrue(allowed.isCancelled()); assertEquals(1, core.packets.size());
        core.clear();
    }
}
