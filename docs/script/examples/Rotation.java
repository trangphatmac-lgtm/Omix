void onLoad() {
    var feature = modules.register("rotation", "Script Rotation", Category.Player);
    feature.on(RotationRequestEvent.class, event -> {
        if (!inWorld()) return;
        rotate(event, RotationRequest.builder(feature.id(), new float[]{mc.player.getYaw(), 45f}, 10)
            .speed(30).silent(true).movementCorrection(MovementCorrection.Silent).build());
    });
}
