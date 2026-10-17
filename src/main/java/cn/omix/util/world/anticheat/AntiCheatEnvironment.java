package cn.omix.util.world.anticheat;

import cn.omix.util.network.GameConnectionContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.Team;

import java.util.Locale;

public final class AntiCheatEnvironment {
    private AntiCheatEnvironment() {}

    public static boolean isHypixel(String address) {
        if (address == null) return false;
        String host = address.toLowerCase(Locale.ROOT).trim().split(":", 2)[0];
        return host.equals("hypixel.net") || host.endsWith(".hypixel.net");
    }

    public static String sidebar(MinecraftClient mc) {
        if (mc.world == null || mc.player == null) return "";
        var board = mc.world.getScoreboard();
        var team = mc.player.getScoreboardTeam();
        var slot = team == null ? null : ScoreboardDisplaySlot.fromFormatting(team.getColor());
        var objective = slot == null ? null : board.getObjectiveForSlot(slot);
        if (objective == null) objective = board.getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR);
        if (objective == null) return "";
        StringBuilder text = new StringBuilder(objective.getDisplayName().getString());
        board.getScoreboardEntries(objective).stream().filter(entry -> !entry.hidden()).limit(15).forEach(entry ->
                text.append('\n').append(Team.decorateName(board.getScoreHolderTeam(entry.owner()), entry.name()).getString()));
        return text.toString().replaceAll("§.", "").toLowerCase(Locale.ROOT);
    }

    public static boolean inMinigame(String sidebar) {
        if (sidebar.contains("lobby") || sidebar.contains("waiting") || sidebar.contains("starting in") || sidebar.contains("replay")) return false;
        return sidebar.contains("diamond ii") || sidebar.contains("emerald ii")
                || sidebar.contains("next event:") || sidebar.contains("refill")
                || sidebar.contains("time left:") || sidebar.contains("survivors:")
                || sidebar.contains("beds broken:") || sidebar.contains("round:");
    }

    public static boolean hypixel(MinecraftClient mc) {
        return isHypixel(GameConnectionContext.serverAddress(mc));
    }
}
