package cn.omix.util.combat;

import org.junit.jupiter.api.Test;
import java.util.List;

import static cn.omix.util.combat.AutoWeaponSelection.Kind.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

class AutoWeaponSelectionTest {
    private final List<AutoWeaponSelection.Candidate> hotbar = List.of(
            new AutoWeaponSelection.Candidate(0, SWORD, true, 10, 100),
            new AutoWeaponSelection.Candidate(1, AXE, false, 12, 100),
            new AutoWeaponSelection.Candidate(2, MACE, false, 8, 100),
            new AutoWeaponSelection.Candidate(3, SWORD, true, 15, 100));

    @Test
    void prefersBestMatchingWeaponAndOverridesItForSpecialAttacks() {
        assertEquals(3, AutoWeaponSelection.select(hotbar, false, false, 0));
        assertEquals(1, AutoWeaponSelection.select(hotbar, false, true, 0));
        assertEquals(2, AutoWeaponSelection.select(hotbar, true, false, 0));
        assertEquals(2, AutoWeaponSelection.select(hotbar, true, true, 0));
    }

    @Test
    void missingSpecialWeaponDoesNotFallBackToPreference() {
        assertEquals(-1, AutoWeaponSelection.select(hotbar.subList(0, 2), true, true, 0));
        assertEquals(-1, AutoWeaponSelection.select(hotbar.subList(2, 4), false, true, 3));
    }

    @Test
    void emptyPreferencesStillPermitSpecialAttacks() {
        var specialOnly = hotbar.subList(1, 3);
        assertEquals(-1, AutoWeaponSelection.select(specialOnly, false, false, 0));
        assertEquals(1, AutoWeaponSelection.select(specialOnly, false, true, 0));
        assertEquals(-1, AutoWeaponSelection.select(List.of(), false, false, 0));
    }

    @Test
    void tiesPreferDurabilityThenCurrentSlot() {
        var tied = List.of(new AutoWeaponSelection.Candidate(0, SWORD, true, 10, 10),
                new AutoWeaponSelection.Candidate(1, SWORD, true, 10, 20),
                new AutoWeaponSelection.Candidate(2, SWORD, true, 10, 20));
        assertEquals(1, AutoWeaponSelection.select(tied, false, false, 0));
        assertEquals(2, AutoWeaponSelection.select(tied, false, false, 2));
    }
}
