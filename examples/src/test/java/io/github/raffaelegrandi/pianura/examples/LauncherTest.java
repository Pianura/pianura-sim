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
}
