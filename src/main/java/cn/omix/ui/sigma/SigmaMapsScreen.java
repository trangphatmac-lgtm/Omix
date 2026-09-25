package cn.omix.ui.sigma;

import cn.omix.util.IMinecraft;
import cn.omix.util.render.Render2D;
import cn.omix.util.sigma.*;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.UUID;

/** Native Jello Maps panel; all dimensions below are the original Jello window-pixel dimensions. */
public final class SigmaMapsScreen extends Screen implements IMinecraft {
    private static final int[] COLORS = {-2565928, -35477, -17579, -6310, -9240708, -11491585, -2652417};
    private final SigmaTextInput name = new SigmaTextInput(128), coordinates = new SigmaTextInput(32);
    private final SigmaMapZoom zoomGlass = new SigmaMapZoom();
    private final SigmaMapRows rows = new SigmaMapRows();
    private double centerX, centerZ;
    private float panelX, panelY, panelWidth, panelHeight, sidebar, scroll;
    private int zoom = 8, colorIndex = 5;
    private boolean dragging, popup;
    private float popupX, popupY;
    private UUID editing;
    private long opened;
    private long popupOpened, popupClosed, closedAt, previousFrame;
    private float uiScale = 1, popupScale = 1, dragStartX, dragStartY, dragY, dragOffset, opacity = 1;
    private boolean popupAbove, listDragging;
    private UUID listPressed;
    private String error = "";

    public SigmaMapsScreen() { super(Text.literal("Jello Maps")); }
    @Override protected void init() {
        if (opened == 0 && mc.player != null) { centerX = mc.player.getX(); centerZ = mc.player.getZ(); opened = System.nanoTime(); }
        panelWidth = Math.min(850, SigmaDraw.width() - 40); panelHeight = Math.min(550, SigmaDraw.height() - 150);
        panelX = (SigmaDraw.width() - panelWidth) / 2; panelY = (SigmaDraw.height() - panelHeight) / 2;
        sidebar = Math.min(260, panelWidth * .4f);
        SigmaWaypoints.get().updateWorld();
    }

    private float pixelsPerBlock() { return Math.max(panelWidth - sidebar, panelHeight) / ((zoom - 1) * 32f); }
    private float mouse(double coordinate) { return (float) coordinate * mc.getWindow().getScaleFactor(); }
    private float localMouse(double coordinate, boolean horizontal) {
        float center = (horizontal ? SigmaDraw.width() : SigmaDraw.height()) / 2f;
        return (mouse(coordinate) - center) / uiScale + center;
    }
    private boolean inside(float x, float y, float left, float top, float w, float h) { return x >= left && x < left + w && y >= top && y < top + h; }

    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (mc.player == null || mc.world == null) { mc.setScreen(null); return; }
        SigmaDraw.begin(context);
        try {
            long now = System.nanoTime();
            float dt = previousFrame == 0 ? 0 : Math.clamp((now - previousFrame) / 1_000_000_000f, 0, .1f);
            previousFrame = now;
            float t = Math.clamp((now - opened) / 200_000_000f, 0, 1);
            if (closedAt != 0) { t = 1 - Math.clamp((System.nanoTime() - closedAt) / 120_000_000f, 0, 1); if (t <= 0) { mc.setScreen(null); return; } }
            opacity = t;
            if (listDragging) {
                float mx = localMouse(mouseX, true), my = localMouse(mouseY, false);
                if (mx >= panelX && mx < panelX + sidebar) {
                    float edge = 35;
                    if (my < panelY + 65 + edge) scroll -= dt * 280 * Math.clamp((panelY + 65 + edge - my) / edge, 0, 1);
                    else if (my > panelY + panelHeight - edge) scroll += dt * 280 * Math.clamp((my - panelY - panelHeight + edge) / edge, 0, 1);
                    scroll = Math.clamp(scroll, 0, Math.max(0, SigmaWaypoints.get().points().size() * 70 - panelHeight + 70));
                    if (!inside(mx, my, panelX + 10, panelY + panelHeight - 50, 40, 40))
                        SigmaWaypoints.get().move(listPressed, (int) ((my - panelY - 65 + scroll) / 70));
                }
            }
            rows.update(SigmaWaypoints.get().points(), listDragging ? listPressed : null, dragY - panelY - 65 + scroll, now);
            SigmaBlur.draw(context, 0, 0, SigmaDraw.width(), SigmaDraw.height(), t);
            Render2D.drawRect(context, 0, 0, SigmaDraw.width(), SigmaDraw.height(), SigmaColors.alpha(SigmaColors.BLACK, .25f * t));
            float back = 1 + 2.70158f * (float) Math.pow(t - 1, 3) + 1.70158f * (float) Math.pow(t - 1, 2);
            float scale = uiScale = .8f + (closedAt == 0 ? back : SigmaAnimation.easeOut(t)) * .2f;
            context.getMatrices().translate(SigmaDraw.width() / 2f, SigmaDraw.height() / 2f);
            context.getMatrices().scale(scale, scale);
            context.getMatrices().translate(-SigmaDraw.width() / 2f, -SigmaDraw.height() / 2f);
            SigmaDraw.shadow(context, panelX + 7, panelY + 7, panelWidth - 14, panelHeight - 14, 20, .9f * t);
            SigmaShape.rounded(context, panelX, panelY, panelWidth, panelHeight, 14, SigmaColors.alpha(SigmaColors.WHITE, .88f * t));
            SigmaResources.medium(40).drawString(context, "Jello Maps", panelX, panelY - 70, SigmaColors.alpha(SigmaColors.WHITE, t));
            String world = SigmaWaypoints.get().label();
            SigmaResources.light(24).drawString(context, world, panelX + panelWidth - SigmaResources.light(24).getStringWidth(world) - 10,
                    panelY - 62, SigmaColors.alpha(SigmaColors.WHITE, .5f * t));
            SigmaResources.light(25).drawString(context, "Waypoints", panelX + 30, panelY + 25, SigmaColors.alpha(SigmaColors.BLACK, .6f * t));
            float mapX = panelX + sidebar, mapWidth = panelWidth - sidebar, ppb = pixelsPerBlock();
            SigmaMapCache.get().draw(context, mapX, panelY, mapWidth, panelHeight, centerX, centerZ, ppb, true,
                    new SigmaTexturePolygon.Clip(panelX, panelY, panelWidth, panelHeight, 14));
            Render2D.beginScissor(context, mapX, panelY, mapWidth, panelHeight);
            for (SigmaWaypoint point : SigmaWaypoints.get().points()) {
                float px = mapX + mapWidth / 2 + (float) (point.x() - centerX + 1) * ppb;
                float py = panelY + panelHeight / 2 + (float) (point.z() - centerZ + 1) * ppb;
                SigmaDraw.image(context, "component/waypoint.png", px - 16, py - 42, 32, 46, point.color());
            }
            Render2D.endScissor(context);
            Render2D.drawRect(context, mapX, panelY, 1, panelHeight, 0x23010101);
            drawList(context);
            float zx = panelX + panelWidth - 50, zy = panelY + panelHeight - 100;
            zoomGlass.draw(context, zx, zy, centerX + (zx - mapX - mapWidth / 2) / ppb, centerZ + (zy - panelY - panelHeight / 2) / ppb, ppb);
            String position = Math.round(centerX) + "  " + Math.round(centerZ);
            SigmaResources.light(14).drawString(context, position, mapX - SigmaResources.light(14).getStringWidth(position) - 23, panelY + 35, 0x66010101);
            if (popup) drawPopup(context);
            if (listDragging) {
                for (var point : SigmaWaypoints.get().points()) if (point.id().equals(listPressed)) {
                    rows.draw(context, point, panelX, dragY, sidebar, true, t);
                }
            }
            if (rows.trashProgress() > 0) {
                float p = rows.trashProgress();
                SigmaDraw.image(context, "component/trashcan.png", panelX + 18 - 30 * (1 - p) * (1 - p), panelY + panelHeight - 46, 22, 26,
                        SigmaColors.alpha(inside(localMouse(mouseX, true), localMouse(mouseY, false), panelX + 10, panelY + panelHeight - 50, 40, 40) ? -43691 : SigmaColors.BLACK, .5f * p * t));
            }
        } finally { SigmaDraw.end(context); }
    }

    private void drawList(DrawContext context) {
        List<SigmaWaypoint> points = SigmaWaypoints.get().points();
        scroll = Math.clamp(scroll, 0, Math.max(0, points.size() * 70 - panelHeight + 70));
        Render2D.beginScissor(context, panelX, panelY + 65, sidebar, panelHeight - 65);
        try {
            for (int index = 0; index < points.size(); index++) {
                SigmaWaypoint point = points.get(index);
                float y = panelY + 65 + rows.position(point.id()) - scroll;
                if (y + 70 < panelY + 65 || y > panelY + panelHeight) continue;
                if (listDragging && point.id().equals(listPressed)) continue;
                rows.draw(context, point, panelX, y, sidebar, false, opacity);
            }
        } finally { Render2D.endScissor(context); }
    }

    private void drawPopup(DrawContext context) {
        float p = Math.clamp((System.nanoTime() - popupOpened) / 250_000_000f, 0, 1);
        if (popupClosed != 0) {
            p = 1 - Math.clamp((System.nanoTime() - popupClosed) / 120_000_000f, 0, 1);
            if (p == 0) { popup = false; return; }
        }
        float back = 1 + 2.70158f * (float) Math.pow(p - 1, 3) + 1.70158f * (float) Math.pow(p - 1, 2);
        popupScale = .8f + .2f * back;
        context.getMatrices().pushMatrix();
        context.getMatrices().translate(popupX + 107, popupY + (popupAbove ? 170 : 0));
        context.getMatrices().scale(popupScale, popupScale);
        context.getMatrices().translate(-popupX - 107, -popupY - (popupAbove ? 170 : 0));
        SigmaDraw.shadow(context, popupX + 5, popupY + 5, 204, 160, 35, .9f * p);
        int background = SigmaColors.alpha(-723724, SigmaAnimation.easeOut(p));
        SigmaShape.rounded(context, popupX, popupY, 214, 170, 10, background);
        context.getMatrices().pushMatrix();
        context.getMatrices().translate(popupX + 107, popupY + (popupAbove ? 170 : 0));
        context.getMatrices().rotate((float) Math.toRadians(popupAbove ? 90 : -90));
        SigmaDraw.image(context, "alt/select.png", 0, -23.5f, 18, 47, background);
        context.getMatrices().popMatrix();
        name.draw(context, SigmaResources.light(25), popupX + 20, popupY + 22, 174, 0xff333333, "My waypoint");
        Render2D.drawRect(context, popupX + 25, popupY + 68, 164, 1, 0x0d010101);
        for (int i = 0; i < COLORS.length; i++) {
            float x = popupX + 15 + i * 27;
            if (i == colorIndex) SigmaShape.rounded(context, x - 3, popupY + 83, 24, 24, 12, 0x60010101);
            SigmaShape.rounded(context, x, popupY + 86, 18, 18, 9, COLORS[i]);
        }
        coordinates.draw(context, SigmaResources.light(18), popupX + 20, popupY + 126, 114, 0xff333333, "X Z");
        SigmaResources.light(25).drawString(context, editing == null ? "Add" : "Save", popupX + 148, popupY + 117, 0xff333333);
        if (!error.isEmpty()) SigmaResources.light(12).drawString(context, error, popupX + 15, popupY + 151, 0xffb03030);
        context.getMatrices().popMatrix();
    }

    @Override public boolean mouseClicked(Click click, boolean doubled) {
        if (closedAt != 0) return true;
        float x = localMouse(click.x(), true), y = localMouse(click.y(), false);
        if (popup) {
            if (popupClosed != 0) return true;
            x = (x - popupX - 107) / popupScale + popupX + 107;
            y = (y - popupY - (popupAbove ? 170 : 0)) / popupScale + popupY + (popupAbove ? 170 : 0);
            if (!inside(x, y, popupX, popupY, 214, 170)) { closePopup(); return true; }
            name.focus(inside(x, y, popupX + 15, popupY + 7, 184, 60));
            coordinates.focus(inside(x, y, popupX + 15, popupY + 119, 125, 32));
            for (int i = 0; i < COLORS.length; i++) if (inside(x, y, popupX + 12 + i * 27, popupY + 83, 24, 24)) colorIndex = i;
            if (inside(x, y, popupX + 143, popupY + 110, 65, 40)) savePopup();
            return true;
        }
        float zx = panelX + panelWidth - 50, zy = panelY + panelHeight - 100;
        if (inside(x, y, zx, zy, 40, 90)) { zoom = Math.clamp(zoom + (y < zy + 45 ? -1 : 1), 3, 33); zoomGlass.press(y < zy + 45); return true; }
        if (inside(x, y, panelX, panelY + 65, sidebar, panelHeight - 65)) {
            List<SigmaWaypoint> points = SigmaWaypoints.get().points();
            for (SigmaWaypoint point : points) {
                float rowY = panelY + 65 + rows.position(point.id()) - scroll;
                if (y < rowY || y >= rowY + 70 || rows.deleting(point.id())) continue;
                if (click.button() == 1) openPopup(x, y, point.x(), point.z(), point);
                else if (click.button() == 0) { listPressed = point.id(); dragStartX = x; dragStartY = y; dragOffset = y - rowY; }
                break;
            }
            return true;
        }
        if (inside(x, y, panelX + sidebar, panelY, panelWidth - sidebar, panelHeight)) {
            if (click.button() == 1) {
                int worldX = (int) Math.round(centerX + (x - panelX - sidebar - (panelWidth - sidebar) / 2) / pixelsPerBlock());
                int worldZ = (int) Math.round(centerZ + (y - panelY - panelHeight / 2) / pixelsPerBlock());
                openPopup(x, y, worldX, worldZ, null);
            } else dragging = click.button() == 0;
            return true;
        }
        return false;
    }

    private void openPopup(float x, float y, int worldX, int worldZ, SigmaWaypoint point) {
        popup = true; popupOpened = System.nanoTime(); popupClosed = 0; editing = point == null ? null : point.id(); error = "";
        popupX = Math.clamp(x - 107, 10, Math.max(10, SigmaDraw.width() - 224));
        popupY = y + 190 < SigmaDraw.height() ? y + 20 : y - 187;
        popupAbove = popupY < y;
        popupY = Math.clamp(popupY, 10, Math.max(10, SigmaDraw.height() - 180));
        name.set(point == null ? "My waypoint" : point.name()); name.focus(true); name.selectAll();
        coordinates.set(worldX + " " + worldZ); coordinates.focus(false);
        if (point != null) for (int i = 0; i < COLORS.length; i++) if (COLORS[i] == point.color()) colorIndex = i;
    }

    private void savePopup() {
        try {
            String[] parts = coordinates.value().trim().split("\\s+");
            if (parts.length != 2) throw new IllegalArgumentException();
            int x = Integer.parseInt(parts[0]), z = Integer.parseInt(parts[1]);
            SigmaWaypoint point = new SigmaWaypoint(editing == null ? UUID.randomUUID() : editing, name.value().strip(), x, 64, z, COLORS[colorIndex], true);
            SigmaWaypoints.get().put(point); closePopup();
        } catch (IllegalArgumentException exception) { error = "Enter a name and valid X Z"; }
        catch (IllegalStateException exception) { error = "Unable to save waypoint"; }
    }

    private void closePopup() { if (popupClosed == 0) popupClosed = System.nanoTime(); name.focus(false); coordinates.focus(false); }

    @Override public boolean mouseDragged(Click click, double dx, double dy) {
        if (listPressed != null) {
            float x = localMouse(click.x(), true), y = localMouse(click.y(), false);
            listDragging |= Math.abs(y - dragStartY) + Math.abs(x - dragStartX) > 5;
            dragY = y - dragOffset;
            if (listDragging && inside(x, y, panelX, panelY + 65, sidebar, panelHeight - 115)) {
                int index = (int) ((y - panelY - 65 + scroll) / 70);
                SigmaWaypoints.get().move(listPressed, index);
            }
            return true;
        }
        if (dragging) { centerX -= mouse(dx) / uiScale / pixelsPerBlock(); centerZ -= mouse(dy) / uiScale / pixelsPerBlock(); return true; }
        return false;
    }
    @Override public boolean mouseReleased(Click click) {
        if (listPressed != null) {
            if (listDragging && inside(localMouse(click.x(), true), localMouse(click.y(), false), panelX + 10, panelY + panelHeight - 50, 40, 40)) rows.delete(listPressed);
            else if (!listDragging) for (var point : SigmaWaypoints.get().points()) if (point.id().equals(listPressed)) { centerX = point.x(); centerZ = point.z(); }
        }
        dragging = listDragging = false; listPressed = null; return true;
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (popup) return true;
        if (inside(localMouse(x, true), localMouse(y, false), panelX, panelY, sidebar, panelHeight)) scroll -= (float) vertical * 35;
        else zoom = Math.clamp(zoom - (int) Math.signum(vertical), 3, 33);
        return true;
    }
    @Override public boolean keyPressed(KeyInput input) {
        if (popup) {
            if (popupClosed != 0) return true;
            if (input.key() == GLFW.GLFW_KEY_ESCAPE) { closePopup(); return true; }
            if (input.key() == GLFW.GLFW_KEY_ENTER) { savePopup(); return true; }
            if (input.key() == GLFW.GLFW_KEY_TAB) { boolean next = !name.focused(); name.focus(next); coordinates.focus(!next); return true; }
            return name.key(input) || coordinates.key(input);
        }
        return super.keyPressed(input);
    }
    @Override public boolean charTyped(CharInput input) { return popup && (name.type(input) || coordinates.type(input)); }
    @Override public boolean shouldPause() { return false; }
    @Override public void close() { if (closedAt == 0) closedAt = System.nanoTime(); }
    @Override public void removed() { rows.finishDeletes(); zoomGlass.close(); }
}
