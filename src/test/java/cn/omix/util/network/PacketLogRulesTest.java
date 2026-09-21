package cn.omix.util.network;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PacketLogRulesTest {
    @Test
    void blankListsAllowAllAndBareIdsUseMinecraftNamespace() {
        assertTrue(PacketLogRules.parse(" , ; \n", "").allows("other:test"));
        var rules = PacketLogRules.parse("KEEP_ALIVE", "");
        assertTrue(rules.allows("minecraft:keep_alive"));
        assertFalse(rules.allows("other:keep_alive"));
        assertFalse(rules.allows("minecraft:keep_alive_extra"));
    }

    @Test
    void multipleGlobsAndBlacklistPrecedenceWorkTogether() {
        var rules = PacketLogRules.parse("move_*，keep_alive;other:packet?", "move_player_rot；other:packet2");
        assertTrue(rules.allows("minecraft:move_player_pos_rot"));
        assertTrue(rules.allows("minecraft:keep_alive"));
        assertFalse(rules.allows("minecraft:move_player_rot"));
        assertTrue(rules.allows("other:packet1"));
        assertFalse(rules.allows("other:packet2"));
        assertFalse(rules.allows("other:packet12"));
        assertFalse(rules.allows("minecraft:ping"));
    }

    @Test
    void blacklistWorksWithoutWhitelistAndPatternsAreNotRegex() {
        assertFalse(PacketLogRules.parse("", "keep_alive").allows("minecraft:keep_alive"));
        assertTrue(PacketLogRules.parse("", "keep_alive").allows("minecraft:pong"));
        assertFalse(PacketLogRules.parse("(keep_alive|pong)", "").allows("minecraft:pong"));
        assertTrue(PacketLogRules.parse("*:*", "").allows("mod:packet"));
        assertTrue(PacketLogRules.parse("*pos*rot", "").allows("minecraft:move_player_pos_rot"));
        assertFalse(PacketLogRules.parse("*pos*rot", "").allows("minecraft:move_player_pos_rot_extra"));
    }
}
