void onLoad() {
    var hud = ui.hud("status", "Script Status", (draw, handle) -> {
        handle.size(156, 22);
        render.rect(draw, (int) handle.x(), (int) handle.y(), 156, 22, 0xB0202738);
        render.text(draw, "Script gen " + script.generation(), (int) handle.x() + 5, (int) handle.y() + 6, 0xFFFFFFFF);
    });
    hud.position(0.1f, 0.2f);
}
