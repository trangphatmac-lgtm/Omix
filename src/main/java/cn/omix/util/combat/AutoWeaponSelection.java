package cn.omix.util.combat;

import java.util.List;

/** Selection policy shared by hotbar scanning and cooldown prediction. */
public final class AutoWeaponSelection {
    private AutoWeaponSelection() {}

    public enum Kind { SWORD, AXE, MACE, SPEAR, PICKAXE, SHOVEL, HOE, OTHER }

    public record Candidate(int slot, Kind kind, boolean preferred, double score, int durability) {}

    public static int select(List<Candidate> candidates, boolean smash, boolean shield, int selectedSlot) {
        Candidate best = null;
        for (Candidate candidate : candidates) {
            // Special cases deliberately do not fall back to another weapon type.
            boolean eligible = smash ? candidate.kind == Kind.MACE
                    : shield ? candidate.kind == Kind.AXE : candidate.preferred;
            if (!eligible) continue;
            if (best == null || candidate.score > best.score
                    || candidate.score == best.score && candidate.durability > best.durability
                    || candidate.score == best.score && candidate.durability == best.durability
                    && candidate.slot == selectedSlot) {
                best = candidate;
            }
        }
        return best == null ? -1 : best.slot;
    }
}
