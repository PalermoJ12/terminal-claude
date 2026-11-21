package com.termux.app;

import android.content.Context;

import com.termux.shared.logger.Logger;
import com.termux.shared.termux.TermuxConstants;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Terminal Claude: copies the Claude Code setup files from the APK's assets into the Termux
 * prefix. Called each time the first terminal session is about to start, after the bootstrap is
 * installed, so the files are also refreshed when the app is updated.
 *
 * <ul>
 *   <li>{@code $PREFIX/bin/claude-setup}: installs storage access, tools, a Debian environment
 *   and Claude Code.</li>
 *   <li>{@code $PREFIX/etc/profile.d/terminal-claude.sh}: runs {@code claude-setup} in the first
 *   interactive shell until setup has finished (and again after an update changes it).</li>
 *   <li>{@code $PREFIX/bin/remote-access}: serves the terminal to a computer over the web
 *   (ttyd) and SSH.</li>
 *   <li>{@code $PREFIX/bin/github-login}: signs in to GitHub with gh and sets up git.</li>
 * </ul>
 */
final class ClaudeSetupInstaller {

    private static final String LOG_TAG = "ClaudeSetupInstaller";
    private static final String ASSET_DIR = "terminal-claude/";

    private ClaudeSetupInstaller() {}

    static void installSetupFiles(Context context) {
        copyAsset(context, "claude-setup",
            TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH + "/claude-setup", true);
        copyAsset(context, "remote-access",
            TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH + "/remote-access", true);
        copyAsset(context, "github-login",
            TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH + "/github-login", true);
        copyAsset(context, "terminal-claude.sh",
            TermuxConstants.TERMUX_ETC_PREFIX_DIR_PATH + "/profile.d/terminal-claude.sh", false);
    }

    private static void copyAsset(Context context, String assetName, String destPath, boolean executable) {
        File dest = new File(destPath);
        File parent = dest.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            Logger.logError(LOG_TAG, "Failed to create directory " + parent);
            return;
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
            return;
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
        }
    }
}
