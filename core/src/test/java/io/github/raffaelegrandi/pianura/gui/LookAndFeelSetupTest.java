// Copyright (c) 2008-2026 Raffaele Grandi
// SPDX-License-Identifier: MIT

package io.github.raffaelegrandi.pianura.gui;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** core has no dependencies: without FlatLaf on the classpath the window falls back to Metal. */
class LookAndFeelSetupTest {

    @Test
    void withoutFlatLafTheStandardLookIsUsed() {
        assertFalse(LookAndFeelSetup.flatLafAvailable(), "core must not depend on FlatLaf");
        assertEquals("Metal", LookAndFeelSetup.install());
        assertEquals("Metal", LookAndFeelSetup.install(), "installed once");
    }
}
