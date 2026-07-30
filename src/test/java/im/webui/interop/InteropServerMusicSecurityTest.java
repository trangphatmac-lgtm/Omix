package im.webui.interop;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InteropServerMusicSecurityTest {
    @Test
    void onlyAllowsDeclaredReadSidecarRoutes() {
        assertTrue(InteropServer.isMusicApiAllowed("GET", "/search"));
        assertTrue(InteropServer.isMusicApiAllowed("POST", "/login/qr/check"));
        assertTrue(InteropServer.isMusicApiAllowed("GET", "/likelist"));
        assertTrue(InteropServer.isMusicApiAllowed("GET", "/album/sublist"));
        assertTrue(InteropServer.isMusicApiAllowed("GET", "/artist/sublist"));
        assertTrue(InteropServer.isMusicApiAllowed("GET", "/mv/sublist"));
        assertTrue(InteropServer.isMusicApiAllowed("GET", "/user/cloud"));
        assertTrue(InteropServer.isMusicApiAllowed("GET", "/user/record"));
        assertTrue(InteropServer.isMusicApiAllowed("GET", "/audio/transcode"));
        assertFalse(InteropServer.isMusicApiAllowed("DELETE", "/search"));
        assertFalse(InteropServer.isMusicApiAllowed("GET", "/playlist/delete"));
        assertFalse(InteropServer.isMusicApiAllowed("GET", "/../healthz"));
    }

    @Test
    void neverForwardsTheOmixAuthenticationCookie() {
        assertEquals(
                "MUSIC_U=music-token; __csrf=csrf-token",
                InteropServer.filteredMusicCookie(
                        "MUSIC_U=music-token; omix_webui_auth=secret; __csrf=csrf-token"
                )
        );
        assertTrue(InteropServer.isProtectedAuthCookie(" omix_webui_auth=other; Path=/"));
        assertTrue(InteropServer.isProtectedAuthCookie("OMIX_WEBUI_AUTH=other"));
        assertFalse(InteropServer.isProtectedAuthCookie("MUSIC_U=value"));
    }
}
