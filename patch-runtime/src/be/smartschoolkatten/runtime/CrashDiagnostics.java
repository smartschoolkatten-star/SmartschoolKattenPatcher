package be.smartschoolkatten.runtime;

import android.app.Application;
import android.os.Build;
import java.io.*;
import java.nio.charset.StandardCharsets;

/** Local diagnostics only. Preserve Android's existing fatal-error handling. */
public final class CrashDiagnostics {
    private static final String FILE = "katten-last-error.txt";
    private static boolean installed;
    private CrashDiagnostics() {}
    public static synchronized void install(Application app) {
        if (installed) return;
        Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            record(app, "Onverwerkte fout op " + thread.getName(), error);
            if (previous != null) previous.uncaughtException(thread, error);
            else { android.os.Process.killProcess(android.os.Process.myPid()); System.exit(10); }
        });
        installed = true;
    }
    public static synchronized void record(Application app, String stage, Throwable error) {
        try {
            android.util.Log.e("SmartschoolKatten", stage, error);
            if (app == null) return;
            StringWriter stack = new StringWriter();
            error.printStackTrace(new PrintWriter(stack));
            String report = "Smartschool Katten 12.3.0 — patch 14\n" + java.time.Instant.now()
                + "\nAndroid " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")"
                + "\n" + Build.MANUFACTURER + " " + Build.MODEL + "\n" + stage + "\n\n" + stack;
            if (report.length() > 65536) report = report.substring(0, 65536);
            try (OutputStream out = new FileOutputStream(new File(app.getFilesDir(), FILE))) {
                out.write(report.getBytes(StandardCharsets.UTF_8));
            }
        } catch (Throwable ignored) { /* diagnostics must never replace the original error */ }
    }
    public static String read(Application app) {
        try (Reader reader = new InputStreamReader(new FileInputStream(new File(app.getFilesDir(), FILE)), StandardCharsets.UTF_8)) {
            char[] buffer = new char[4096]; StringBuilder result = new StringBuilder(); int count;
            while (result.length() < 65536 && (count = reader.read(buffer, 0, Math.min(buffer.length, 65536 - result.length()))) != -1)
                result.append(buffer, 0, count);
            return result.length() > 0 ? result.toString() : "Nog geen foutgegevens opgeslagen.";
        } catch (IOException ignored) { return "Nog geen foutgegevens opgeslagen."; }
    }
}
