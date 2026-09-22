package cn.omix.util.script;

import cn.omix.event.base.*;
import cn.omix.event.base.annotation.EventTarget;
import cn.omix.script.api.Registration;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class ScriptEventsTest {
    static class Signal extends Event {}
    static class Receiver { int count; @EventTarget public void event(Signal event) { count++; } }
    @Test void sameClassInstancesAreIndependentAndRepeatedRegistrationIsIdempotent() {
        var bus = new EventManager(); var first = new Receiver(); var second = new Receiver();
        bus.register(first); bus.register(second); bus.register(first); bus.call(new Signal());
        assertEquals(1, first.count); assertEquals(1, second.count);
        bus.unregister(first); bus.call(new Signal()); assertEquals(1, first.count); assertEquals(2, second.count);
        bus.unregister(second); assertFalse(bus.hasListeners(Signal.class));
    }
    @Test void cancellationIsSynchronousAndDispatchRetainsNetworkThreadAndPriority() throws Exception {
        var bus = new EventManager(); var order = new ArrayList<Integer>(); var threads = new ArrayList<Thread>();
        bus.subscribe(this, Signal.class, 20, event -> { assertTrue(event.isCancelled()); order.add(20); threads.add(Thread.currentThread()); });
        bus.subscribe(new Object(), Signal.class, -5, event -> { event.setCancelled(); order.add(-5); });
        Signal event = new Signal(); Thread thread = Thread.ofPlatform().start(() -> bus.call(event)); thread.join();
        assertTrue(event.isCancelled()); assertEquals(List.of(-5, 20), order); assertEquals(List.of(thread), threads);
    }
    @Test void unsubscribeDuringDispatchSkipsAlreadyCapturedListener() {
        var bus = new EventManager(); var calls = new ArrayList<String>(); Registration[] second = {null};
        bus.subscribe(this, Signal.class, 0, event -> { calls.add("first"); second[0].close(); });
        second[0] = bus.subscribe(this, Signal.class, 10, event -> calls.add("second"));
        bus.call(new Signal()); assertEquals(List.of("first"), calls); second[0].close();
    }
}
