package cn.omix.module.value;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.function.Supplier;

@Getter

public abstract class Value {
    private final String name;
    private final Supplier<Boolean> visible;

    protected Value(String name, Supplier<Boolean> visible) { this.name = name; this.visible = visible; }

    private Supplier<Boolean> scriptVisibility = () -> true;

    public void setScriptVisibility(Supplier<Boolean> visibility) { this.scriptVisibility = visibility; }

    public final boolean isVisible() {
        return Boolean.TRUE.equals(this.visible.get()) && Boolean.TRUE.equals(scriptVisibility.get());
    }
}