void onLoad() {
    // Invoked explicitly; loading this script alone changes no other module.
    commands.register("script-module <module> [on|off|toggle]", args -> {
        if (args.length == 0) { log("Specify a module name or stable ID"); return; }
        String name = args[0];
        if (modules.get(name) == null) { log("Unknown module: " + name); return; }
        if (args.length == 1) {
            log(name + " enabled=" + modules.isEnabled(name));
            log(modules.settings(name));
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "on" -> modules.enable(name);
            case "off" -> modules.disable(name);
            case "toggle" -> modules.toggle(name);
            default -> log("Use on, off or toggle");
        }
    }, "script-module");
    // In an enabled feature callback, command, tool or script.afterCommit:
    // modules.setSlider("Aura", "Range", 3.0);
    // modules.setButton("Example", "Boolean setting", true);
    // modules.setSetting("Example", "Mode", "An option from modules.settings(...)");
    // modules.setSetting("Example", "Targets", Map.of("Players", true));
    // modules.setSetting("Example", "Color", new Color(80, 160, 255));
    // modules.setKey("Example", 82);
    // log(modules.getSetting("Example", "Mode"));
}
