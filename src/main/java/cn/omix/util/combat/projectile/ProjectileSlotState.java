package cn.omix.util.combat.projectile;

/** One temporary hotbar lease; never restore over a selection made by another owner. */
public final class ProjectileSlotState {
    private int original = -1;
    private int selected = -1;
    private boolean releasePending;

    public void observe(int current) {
        if (selected >= 0 && current != selected) clear();
    }

    public void select(int current, int next) {
        observe(current);
        if (original < 0) original = current;
        selected = next;
        releasePending = false;
    }

    public int original() { return original; }
    public boolean owns(int current) { return selected >= 0 && selected == current; }
    public void deferRelease() { releasePending = true; }

    public int flush(int current) {
        return releasePending ? release(current) : -1;
    }

    public int release(int current) {
        int restore = owns(current) ? original : -1;
        clear();
        return restore;
    }

    public void clear() {
        original = selected = -1;
        releasePending = false;
    }
}
