package com.jajua.gts;

import android.content.Context;

import java.io.File;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Small on-device log of playback events (sessions, Bluetooth song reports, XTService source). Wireless
 * CarPlay switches the unit's Wi-Fi off, so what happens during CarPlay can only be sent to the PC
 * afterwards, with the diagnostic. Stays in the app's private storage; only changes are written and
 * the file is trimmed to the last MAX_LINES lines.
 */
final class MediaLog {
    private static final int MAX_LINES = 400;
    private static final String FILE = "registro-multimedia.txt";
    private static String last;
    private static int written;

    private MediaLog() {}

    static synchronized void add(Context c, String line) {
        if (line.equals(last)) return;
        last = line;
        File f = new File(c.getFilesDir(), FILE);
        String stamp = new SimpleDateFormat("HH:mm:ss", Locale.ROOT).format(new Date());
        try (FileWriter w = new FileWriter(f, true)) {
            w.write(stamp + "  " + line + "\n");
        } catch (Exception ignored) {
            return;
        }
        if (++written % 50 == 0) trim(f);
    }

    static synchronized String read(Context c) {
        File f = new File(c.getFilesDir(), FILE);
        if (!f.exists()) return "  (vacío)\n";
        try {
            StringBuilder sb = new StringBuilder();
            for (String l : Files.readAllLines(f.toPath(), StandardCharsets.UTF_8)) sb.append("  ").append(l).append('\n');
            return sb.toString();
        } catch (Exception ex) {
            return "  no se pudo leer: " + ex.getMessage() + "\n";
        }
    }

    private static void trim(File f) {
        try {
            List<String> lines = Files.readAllLines(f.toPath(), StandardCharsets.UTF_8);
            if (lines.size() <= MAX_LINES) return;
            Files.write(f.toPath(), new ArrayList<>(lines.subList(lines.size() - MAX_LINES, lines.size())),
                    StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            // Log is best effort.
        }
    }
}
