// Copyright (c) 2008-2026 Raffaele Grandi
// SPDX-License-Identifier: MIT

package io.github.raffaelegrandi.pianura.gui;

import java.util.Locale;

import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.UIManager;

/**
 * Chooses the look of the window. Pianura has no dependencies: if
 * <a href="https://www.formdev.com/flatlaf/">FlatLaf</a> is on the classpath (the examples
 * include it) the window uses its modern light theme, otherwise Swing's standard Metal look,
 * as in JUSE. FlatLaf is looked up by name, so nothing changes for those who do not add it.
 *
 * <p>The system property {@code pianura.laf} forces a choice:
 * {@code flat} (default when available), {@code flat-dark}, {@code metal}, {@code system}
 * (the operating system's look). For example {@code java -Dpianura.laf=flat-dark ...}, or
 * {@code ./gradlew run -Dpianura.laf=metal}.
 */
public final class LookAndFeelSetup {

    private static boolean done;

    private LookAndFeelSetup() {}

    /** Installs the look once, before the first window is created. Returns its name. */
    public static synchronized String install() {
        if (done) return UIManager.getLookAndFeel().getName();
        done = true;
        String wanted = System.getProperty("pianura.laf", "flat").trim().toLowerCase(Locale.ROOT);
        boolean mac = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac");
        boolean linux = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("linux");

        if (wanted.startsWith("flat")) {
            boolean dark = wanted.equals("flat-dark");
            String cls = mac
                ? (dark ? "com.formdev.flatlaf.themes.FlatMacDarkLaf" : "com.formdev.flatlaf.themes.FlatMacLightLaf")
                : (dark ? "com.formdev.flatlaf.FlatDarkLaf" : "com.formdev.flatlaf.FlatLightLaf");
            if (trySet(cls)) {
                // native title bar on macOS and Windows; on Linux FlatLaf draws its own, matching the theme
                if (linux) { JFrame.setDefaultLookAndFeelDecorated(true); JDialog.setDefaultLookAndFeelDecorated(true); }
                return UIManager.getLookAndFeel().getName();
            }
            // FlatLaf not on the classpath: standard look
        } else if (wanted.equals("system")) {
            if (trySet(UIManager.getSystemLookAndFeelClassName())) return UIManager.getLookAndFeel().getName();
        }
        trySet(UIManager.getCrossPlatformLookAndFeelClassName());   // Metal
        JFrame.setDefaultLookAndFeelDecorated(true);                  // JUSE's window frame
        return UIManager.getLookAndFeel().getName();
    }

    /** The theme's accent colour (the blue of the main buttons), with a fallback for Metal. */
    public static java.awt.Color accentColor() {
        java.awt.Color c = UIManager.getColor("Component.accentColor");
        return c != null ? c : new java.awt.Color(0x2B63D9);
    }

    /** True if FlatLaf is on the classpath. */
    public static boolean flatLafAvailable() {
        try {
            Class.forName("com.formdev.flatlaf.FlatLightLaf", false, LookAndFeelSetup.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private static boolean trySet(String className) {
        try {
            UIManager.setLookAndFeel(className);
            return true;
        } catch (ReflectiveOperationException | javax.swing.UnsupportedLookAndFeelException | LinkageError e) {
            return false;
        }
    }
}
