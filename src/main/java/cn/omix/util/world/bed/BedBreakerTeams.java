package cn.omix.util.world.bed;

/** Dye IDs and helmet RGB matching used by the reference's Hypixel team filter. */
public final class BedBreakerTeams {
    private static final int[] COLORS = {
            0xF9FFFE, 0xF9801D, 0xC74EBD, 0x3AB3DA, 0xFED83D, 0x80C71F, 0xF38BAA, 0x474F52,
            0x9D9D97, 0x169C9C, 0x8932B8, 0x3C44AA, 0x835432, 0x5E7C16, 0xB02E26, 0x1D1D21
    };

    private BedBreakerTeams() { }

    public static int nearestColor(int rgb) {
        int nearest = -1;
        int best = Integer.MAX_VALUE;
        for (int i = 0; i < COLORS.length; i++) {
            int r = ((rgb >> 16) & 255) - ((COLORS[i] >> 16) & 255);
            int g = ((rgb >> 8) & 255) - ((COLORS[i] >> 8) & 255);
            int b = (rgb & 255) - (COLORS[i] & 255);
            int distance = r * r + g * g + b * b;
            if (distance < best) { best = distance; nearest = i; }
        }
        return nearest;
    }

    public static boolean sameTeam(int bedColor, int helmetColor) {
        return bedColor >= 0 && helmetColor >= 0 && normalize(bedColor) == normalize(helmetColor);
    }

    private static int normalize(int color) {
        return switch (color) {
            case 5, 13 -> 13;
            case 3, 9 -> 9;
            case 7, 8 -> 7;
            default -> color;
        };
    }
}
