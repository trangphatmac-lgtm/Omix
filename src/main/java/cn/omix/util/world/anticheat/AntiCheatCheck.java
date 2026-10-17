package cn.omix.util.world.anticheat;

public enum AntiCheatCheck {
    AUTO_BLOCK("AutoBlock"), LEGIT_SCAFFOLD("Legit Scaffold"), NO_SLOW("NoSlow"),
    NUKER("Nuker"), BED_NUKER("Bed Nuker"), SCAFFOLD("Scaffold"),
    AIM_ASSIST("AimAssist"), AUTO_CLICKER("AutoClicker"), BLINK("Blink"),
    FAST_MINE("FastMine"), NO_FALL("NoFall"), PROTOCOL("Protocol");

    public final String displayName;

    AntiCheatCheck(String displayName) {
        this.displayName = displayName;
    }
}
