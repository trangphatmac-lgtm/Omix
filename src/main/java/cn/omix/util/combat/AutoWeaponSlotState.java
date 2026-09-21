package cn.omix.util.combat;

/** Tracks a temporary selection without undoing another owner or the user's selection. */
public final class AutoWeaponSlotState {
    private int previous = -1;
    private int selected = -1;
    private int expiresAt;

    public void select(int current, int next, int tick, int duration) {
        observe(current);
        if (current != next && previous == -1) previous = current;
        if (previous != -1) {
            selected = next;
            expiresAt = tick + duration;
        }
    }

    public void observe(int current) {
        if (selected != -1 && current != selected) clear();
    }

    public int expire(int current, int tick) {
        observe(current);
        return previous != -1 && tick >= expiresAt ? restore(current) : -1;
    }

    public int restore(int current) {
        int result = current == selected ? previous : -1;
        clear();
        return result;
    }

    public boolean owns(int current) {
        return previous != -1 && current == selected;
    }

    public void clear() {
        previous = selected = -1;
        expiresAt = 0;
    }
}
