package cn.omix.util.sigma;

import cn.omix.util.render.Render2D;
import net.minecraft.client.gui.DrawContext;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Jello's waypoint row artwork, 60 Hz spring and 200 ms slide-to-delete. */
public final class SigmaMapRows {
    private final Map<UUID, Float> positions = new HashMap<>();
    private final Map<UUID, Long> deleting = new HashMap<>();
    private final SigmaAnimation dragAnimation = new SigmaAnimation();
    private long previous;

    public void update(List<SigmaWaypoint> points, UUID dragged, float dragPosition, long now) {
        float factor = previous == 0 ? 1 : Math.clamp((now - previous) / 1_000_000_000f * 60 * .21f, 0, 1);
        previous = now;
        var present = new HashSet<UUID>();
        for (int i = 0; i < points.size(); i++) {
            UUID id = points.get(i).id(); present.add(id);
            float target = i * 70 + 5, old = positions.getOrDefault(id, target);
            positions.put(id, id.equals(dragged) ? dragPosition : old + (target - old) * factor);
        }
        positions.keySet().retainAll(present);
        deleting.entrySet().removeIf(entry -> {
            if (now - entry.getValue() < 200_000_000L) return false;
            SigmaWaypoints.get().remove(entry.getKey()); return true;
        });
        dragAnimation.update(dragged != null, now, 300);
    }

    public float position(UUID id) { return positions.getOrDefault(id, 0f); }
    public float trashProgress() { return dragAnimation.value(); }
    public boolean deleting(UUID id) { return deleting.containsKey(id); }
    public void delete(UUID id) { deleting.putIfAbsent(id, System.nanoTime()); }

    public void finishDeletes() {
        // A close/resize must not cancel a drop that was already accepted by the trash target.
        for (UUID id : deleting.keySet()) SigmaWaypoints.get().remove(id);
        deleting.clear();
    }

    public void draw(DrawContext context, SigmaWaypoint point, float x, float y, float width, boolean dragging, float opacity) {
        Long started = deleting.get(point.id());
        if (started != null) {
            float progress = Math.clamp((System.nanoTime() - started) / 200_000_000f, 0, 1);
            x += width * progress * progress;
        }
        if (dragging) {
            Render2D.drawRect(context, x, y, width, 70, SigmaColors.alpha(SigmaColors.mix(SigmaColors.WHITE, SigmaColors.BLACK, .03f), opacity));
            SigmaDraw.shadow(context, x, y, width, 70, 14, .2f * opacity);
        }
        SigmaShape.rounded(context, x + 25, y + 25, 20, 20, 10, SigmaColors.alpha(SigmaColors.mix(point.color(), SigmaColors.BLACK, .1f), opacity));
        SigmaShape.rounded(context, x + 26.5f, y + 26.5f, 17, 17, 8.5f, SigmaColors.alpha(point.color(), opacity));
        Render2D.beginScissor(context, x + 68, y + 5, width - 118, 60);
        SigmaResources.light(20).drawString(context, point.name(), x + 68, y + 14, SigmaColors.alpha(SigmaColors.BLACK, .8f * opacity));
        SigmaResources.light(14).drawString(context, "x:" + point.x() + " z:" + point.z(), x + 68, y + 38, SigmaColors.alpha(SigmaColors.BLACK, .5f * opacity));
        Render2D.endScissor(context);
        for (int i = 0; i < 3; i++) Render2D.drawRect(context, x + width - 43, y + 27 + i * 5, 20, 2,
                SigmaColors.alpha(SigmaColors.BLACK, (dragging ? .4f : .2f) * opacity));
    }
}
