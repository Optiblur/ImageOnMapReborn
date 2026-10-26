package pl.iomreborn.update;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdateCheckerTest {

    @Test
    void comparesVersions() {
        assertTrue(Version.isNewer("1.1.0", "1.0.0"));
        assertTrue(Version.isNewer("1.10.0", "1.9.9"));
        assertTrue(Version.isNewer("2", "1.99"));
        assertTrue(Version.isNewer("v1.0.1", "1.0.0"));
        assertFalse(Version.isNewer("1.0.0", "1.0.0"));
        assertFalse(Version.isNewer("1.0", "1.0.0"));
        assertFalse(Version.isNewer("1.0.0", "1.1.0"));
        assertTrue(Version.isNewer("1.1.0", "1.1.0-beta"), "a release beats its pre-release");
        assertFalse(Version.isNewer("1.1.0-beta", "1.1.0"));
        assertTrue(Version.isNewer("1.1.1-beta", "1.1.0"));
        assertEquals(0, Version.compare("V1.2", "1.2.0"));
    }

    @Test
    void acceptsOnlyVersionNumbers() {
        assertTrue(Version.isValid("1.0.0"));
        assertTrue(Version.isValid(" v1.2 "));
        assertTrue(Version.isValid("1.3.0-beta.2"));
        assertFalse(Version.isValid(""));
        assertFalse(Version.isValid(null));
        assertFalse(Version.isValid("<!DOCTYPE html>"));
        assertFalse(Version.isValid("Invalid resource"));
        assertFalse(Version.isValid("1.0.0 is out"));
    }

    @Test
    void readsTheSpigotAnswer() {
        UpdateChecker.Release release = UpdateChecker.parseSpigot("1.2.0\n");
        assertEquals("1.2.0", release.version());
        assertEquals(UpdateChecker.SPIGOT_PAGE, release.url());
        assertNull(UpdateChecker.parseSpigot("<html><body>Just a moment...</body></html>"));
        assertNull(UpdateChecker.parseSpigot(""));
    }

    @Test
    void readsTheGitHubAnswer() {
        String json = "{\"url\":\"https://api.github.com/repos/Optiblur/ImageOnMapReborn/releases/1\","
                + "\"html_url\":\"https://github.com/Optiblur/ImageOnMapReborn/releases/tag/v1.3.0\","
                + "\"id\":1,\"tag_name\": \"v1.3.0\",\"name\":\"ImageOnMapReborn 1.3.0\"}";
        UpdateChecker.Release release = UpdateChecker.parseGitHub(json);
        assertEquals("1.3.0", release.version());
        assertEquals("https://github.com/Optiblur/ImageOnMapReborn/releases/tag/v1.3.0", release.url());
        assertEquals(UpdateChecker.GITHUB_RELEASES, UpdateChecker.parseGitHub("{\"tag_name\":\"1.3.1\"}").url());
        assertNull(UpdateChecker.parseGitHub("{\"message\":\"API rate limit exceeded\"}"));
        assertNull(UpdateChecker.parseGitHub("{\"tag_name\":\"latest-build\"}"));
    }

    @Test
    void triesTheNextSourceWhenOneFails() {
        UpdateChecker.Source broken = () -> {
            throw new IOException("offline");
        };
        UpdateChecker.Source useless = () -> null;
        UpdateChecker.Source good = () -> new UpdateChecker.Release("1.4.0", "https://example.org");
        assertEquals("1.4.0", UpdateChecker.latest(List.of(broken, useless, good)).version());
        assertNull(UpdateChecker.latest(List.of(broken, useless)));
    }

    @Test
    void reportsTheStatusForMaptoolInfo() {
        UpdateChecker.Release v120 = new UpdateChecker.Release("1.2.0", "x");
        UpdateChecker.Release v130 = new UpdateChecker.Release("1.3.0", "y");
        assertEquals(UpdateChecker.Status.UNKNOWN, UpdateChecker.status("1.2.0", null, null));
        assertEquals(UpdateChecker.Status.LATEST, UpdateChecker.status("1.2.0", v120, null));
        assertEquals(UpdateChecker.Status.NEWER_BUILD, UpdateChecker.status("1.3.0", v120, null));
        assertEquals(UpdateChecker.Status.UPDATE, UpdateChecker.status("1.2.0", v130, v130));
    }

    @Test
    void tellsWhetherTheRunningVersionIsTheLatest() {
        assertEquals("You are running the latest version (1.2.0).", UpdateChecker.statusLine("1.2.0", "1.2.0"));
        assertEquals("This build (1.3.0) is newer than the latest release (1.2.0).",
                UpdateChecker.statusLine("1.3.0", "1.2.0"));
    }

    @Test
    void onlyNewerVersionsAreAnnounced() {
        assertTrue(UpdateChecker.isUpdate(new UpdateChecker.Release("1.2.0", "x"), "1.1.0"));
        assertFalse(UpdateChecker.isUpdate(new UpdateChecker.Release("1.1.0", "x"), "1.1.0"));
        assertFalse(UpdateChecker.isUpdate(new UpdateChecker.Release("1.0.0", "x"), "1.1.0"));
        assertFalse(UpdateChecker.isUpdate(new UpdateChecker.Release("garbage", "x"), "1.1.0"));
        assertFalse(UpdateChecker.isUpdate(null, "1.1.0"));
    }
}
