// Copyright (c) 2008-2026 Raffaele Grandi
// SPDX-License-Identifier: MIT

package io.github.raffaelegrandi.pianura.examples;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

class LauncherTest {

    @Test
    void theLauncherListsEveryExampleAndEachHasAMain() throws Exception {
        File dir = new File("src/main/java/io/github/raffaelegrandi/pianura/examples");
        List<String> folders = Arrays.stream(dir.listFiles(File::isDirectory)).map(File::getName).sorted().toList();
        List<String> listed = Launcher.EXAMPLES.stream().map(Launcher.Example::name).sorted().toList();
        assertEquals(folders, listed, "a new example needs its line in Launcher.EXAMPLES");
        for (Launcher.Example e : Launcher.EXAMPLES)
            assertNotNull(Class.forName(e.mainClass()).getMethod("main", String[].class));
    }

    @Test
    void everyExampleHasAPictureForTheStartScreen() {
        for (Launcher.Example e : Launcher.EXAMPLES) {
            var img = io.github.raffaelegrandi.pianura.gui.start.Thumbnails.ofScenario(e.preview().get(), 208, 112);
            assertEquals(208, img.getWidth(), e.name());
            assertFalse(e.tag().isBlank(), e.name());
        }
    }

    @Test
    void theSandboxOpensASceneFileWithItsRobots(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) throws Exception {
        java.nio.file.Path f = dir.resolve("scene.json");
        new io.github.raffaelegrandi.pianura.examples.planning.PlanningScenario().saveScene(f);
        var sandbox = new io.github.raffaelegrandi.pianura.examples.sandbox.SandboxScenario(f);
        assertEquals(40, sandbox.getArenaWidth());
        assertEquals(1, sandbox.getNumRobots());
        assertEquals(4, sandbox.getRobots().get(0).getPosition().x, 1e-9, "where the file puts it");
        assertTrue(sandbox.getNumObstacles() > 3);
        assertEquals(1, new io.github.raffaelegrandi.pianura.examples.sandbox.SandboxScenario().getNumRobots());
    }
}
