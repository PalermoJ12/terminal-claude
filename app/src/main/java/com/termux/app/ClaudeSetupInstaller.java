package com.termux.app;

import android.content.Context;

import com.termux.shared.logger.Logger;
import com.termux.shared.termux.TermuxConstants;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Terminal Claude: copies files from the APK's assets into the Termux prefix and home.
 *
 * <p>{@link #installSetupFiles} is called each time the first terminal session is about to start,
 * after the bootstrap is installed, so the files are refreshed when the app is updated:
 * <ul>
 *   <li>{@code $PREFIX/bin/claude-setup}: installs storage access, tools, a Debian environment
 *   and Claude Code.</li>
 *   <li>{@code $PREFIX/etc/profile.d/terminal-claude.sh}: runs {@code claude-setup} until the
 *   current setup version has finished, shows the welcome and sets the prompt.</li>
 *   <li>{@code $PREFIX/bin/tc}: project helper used by the quick-action buttons.</li>
 *   <li>{@code $PREFIX/bin/remote-access}: serves the terminal to a computer (ttyd, SSH).</li>
 *   <li>{@code $PREFIX/bin/github-login}: signs in to GitHub with gh and sets up git.</li>
 * </ul>
 *
 * <p>{@link #installTerminalDefaults} writes the look and keyboard defaults to {@code ~/.termux}.
 * A file is only written when it is missing, has no settings in it (Termux creates a
 * termux.properties that is all comments), or still starts with the managed marker, so edits the
 * user makes (after deleting the marker) are kept.
 */
final class ClaudeSetupInstaller {

    private static final String LOG_TAG = "ClaudeSetupInstaller";
    private static final String ASSET_DIR = "terminal-claude/";
    private static final String MANAGED_MARKER = "# terminal-claude:managed";
    private static final String BIN = TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH;

    private ClaudeSetupInstaller() {}

    static void installSetupFiles(Context context) {
        copyAsset(context, "claude-setup", BIN + "/claude-setup", true);
        copyAsset(context, "claude", BIN + "/claude", true);
        copyAsset(context, "claude-shell", BIN + "/claude-shell", true);
        copyAsset(context, "tc", BIN + "/tc", true);
        copyAsset(context, "remote-access", BIN + "/remote-access", true);
        copyAsset(context, "github-login", BIN + "/github-login", true);
        copyAsset(context, "terminal-claude.sh",
            TermuxConstants.TERMUX_ETC_PREFIX_DIR_PATH + "/profile.d/terminal-claude.sh", false);
    }

    /**
     * Writes the terminal theme, font and extra keys to {@code ~/.termux} and creates
     * {@code ~/projects}. Does nothing before the bootstrap is installed. Creates the home
     * directory if needed: on the first launch it doesn't exist yet when this runs.
     *
     * @return {@code true} if a file changed, so the activity styling should be reloaded.
     */
    static boolean installTerminalDefaults(Context context) {
        if (!new File(TermuxConstants.TERMUX_PREFIX_DIR_PATH).isDirectory()) return false;
        File home = new File(TermuxConstants.TERMUX_HOME_DIR_PATH);
        if (!home.isDirectory() && !home.mkdirs()) return false;

        //noinspection ResultOfMethodCallIgnored
        new File(home, "projects").mkdirs();

        boolean changed = false;
        changed |= installManagedText(context, "termux/termux.properties",
            TermuxConstants.TERMUX_PROPERTIES_PRIMARY_FILE_PATH);
        changed |= installManagedText(context, "termux/colors.properties",
            TermuxConstants.TERMUX_COLOR_PROPERTIES_FILE_PATH);

        // The font is binary, so it has no marker: install it once, and never again if the user
        // removes or replaces it.
        File font = new File(TermuxConstants.TERMUX_FONT_FILE_PATH);
        File fontMarker = new File(home, ".terminal-claude/font-installed");
        if (!font.exists() && !fontMarker.exists()) {
            if (copyAsset(context, "termux/font.ttf", font.getAbsolutePath(), false)) {
                changed = true;
                writeQuietly(fontMarker, new byte[0]);
            }
        }
        return changed;
    }

    private static boolean installManagedText(Context context, String assetName, String destPath) {
        File dest = new File(destPath);
        byte[] wanted;
        try (InputStream in = context.getAssets().open(ASSET_DIR + assetName)) {
            wanted = readAll(in);
        } catch (IOException e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to read asset " + assetName, e);
            return false;
        }
        if (dest.exists()) {
            byte[] current;
            try (InputStream in = new FileInputStream(dest)) {
                current = readAll(in);
            } catch (IOException e) {
                return false;
            }
            if (Arrays.equals(current, wanted)) return false;
            String text = new String(current, StandardCharsets.UTF_8);
            if (!text.startsWith(MANAGED_MARKER) && hasSettings(text)) return false; // the user's own
        }
        return copyAsset(context, assetName, destPath, false);
    }

    /** Whether a .properties file sets anything, i.e. has a line that isn't blank or a comment. */
    private static boolean hasSettings(String text) {
        for (String line : text.split("\n")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && !trimmed.startsWith("#") && !trimmed.startsWith("!")) return true;
        }
        return false;
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
        return out.toByteArray();
    }

    private static void writeQuietly(File file, byte[] data) {
        File parent = file.getParentFile();
        //noinspection ResultOfMethodCallIgnored
        if (parent != null) parent.mkdirs();
        try (OutputStream out = new FileOutputStream(file)) {
            out.write(data);
        } catch (IOException e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to write " + file, e);
        }
    }

    private static boolean copyAsset(Context context, String assetName, String destPath, boolean executable) {
        File dest = new File(destPath);
        File parent = dest.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            Logger.logError(LOG_TAG, "Failed to create directory " + parent);
            return false;
        }
        File tmp = new File(destPath + ".tmp");
        try (InputStream in = context.getAssets().open(ASSET_DIR + assetName);
             OutputStream out = new FileOutputStream(tmp)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
        } catch (IOException e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to write " + destPath, e);
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
            return false;
        }
        //noinspection ResultOfMethodCallIgnored
        tmp.setReadable(true, false);
        if (executable) {
            //noinspection ResultOfMethodCallIgnored
            tmp.setExecutable(true, false);
        }
        if (!tmp.renameTo(dest)) {
            Logger.logError(LOG_TAG, "Failed to move " + tmp + " to " + dest);
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
            return false;
        }
        return true;
    }
}
