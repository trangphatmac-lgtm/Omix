package cn.omix.util.world.anticheat.raven;

final class RavenText {
    private RavenText() {}
    static String strip(String text) { return text == null ? "" : text.replaceAll("§.", ""); }
}
