package cn.omix.util.render;

import org.junit.jupiter.api.Test;
import static cn.omix.util.render.HudSidebarLayout.*;
import static org.junit.jupiter.api.Assertions.*;

class HudSidebarLayoutTest {
    @Test void translatedSidebarMovesLeftOfARightHandModuleList() {
        var sidebar = new Bounds(180, 50, 399, 190);
        var modules = new Bounds(310, 2, 399, 180);
        var offset = avoid(sidebar, modules, 400, 240);
        assertEquals(0, offset.y());
        assertEquals(modules.left() - 3, sidebar.right() + offset.x());
        assertTrue(sidebar.left() + offset.x() >= 1);
    }
    @Test void narrowViewportFallsBackToFreeSpaceBelowTheList() {
        var sidebar = new Bounds(20, 50, 239, 140);
        var modules = new Bounds(150, 2, 239, 90);
        var offset = avoid(sidebar, modules, 240, 240);
        assertEquals(new Offset(0, 43), offset);
    }
    @Test void nonOverlappingAndHiddenListsLeaveVanillaPlacementAlone() {
        var sidebar = new Bounds(180, 100, 399, 190);
        assertEquals(Offset.NONE, avoid(sidebar, new Bounds(310, 2, 399, 90), 400, 240));
        assertEquals(Offset.NONE, avoid(sidebar, null, 400, 240));
        assertEquals(Offset.NONE, avoid(sidebar, new Bounds(2, 2, 100, 200), 400, 240));
    }
    @Test void bottomAnchoredListCanBeAvoidedAboveWhenHorizontalSpaceIsInsufficient() {
        var sidebar = new Bounds(20, 150, 239, 220);
        assertEquals(new Offset(0, -63), avoid(sidebar, new Bounds(150, 160, 239, 239), 240, 240));
    }
}
