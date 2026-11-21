package com.termux.app;

import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.termux.R;
import com.termux.shared.logger.Logger;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.shell.command.runner.terminal.TermuxSession;
import com.termux.terminal.TerminalSession;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Terminal Claude: the row of buttons above the keyboard keys (Claude, + Tab, Tabs, GitHub,
 * Server, Keys, ?) and the dialogs behind them.
 *
 * <p>Actions that run something open a new tab running {@code $PREFIX/bin/tc}, which starts the
 * command and then leaves a normal shell in that tab. This way a button never types into a tab
 * where Claude or another program is already running.
 */
final class ClaudeQuickActions {

    private static final String LOG_TAG = "ClaudeQuickActions";

    private static final String HOME = TermuxConstants.TERMUX_HOME_DIR_PATH;
    private static final String PROJECTS = HOME + "/projects";
    private static final String SHARED_STORAGE = HOME + "/storage/shared";
    private static final String BASH = TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH + "/bash";
    private static final String TC = TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH + "/tc";
    /** Same limit as TermuxTerminalSessionActivityClient. */
    private static final int MAX_SESSIONS = 8;

    private static final int COLOR_BAR = 0xFF1A1917;
    private static final int COLOR_CHIP = 0xFF2F2C28;
    private static final int COLOR_CHIP_TEXT = 0xFFE8E4DC;
    private static final int COLOR_ACCENT = 0xFFD97757;
    private static final int COLOR_ACCENT_TEXT = 0xFF1A1917;
    private static final int COLOR_RIPPLE = 0x33FFFFFF;

    private static final Pattern PROJECT_NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]*");

    private final TermuxActivity mActivity;

    ClaudeQuickActions(TermuxActivity activity) {
        mActivity = activity;
    }

    void setup() {
        View bar = mActivity.findViewById(R.id.quick_actions_bar);
        LinearLayout row = mActivity.findViewById(R.id.quick_actions_container);
        if (bar == null || row == null) return;
        bar.setBackgroundColor(COLOR_BAR);

        TextView claude = addChip(row, "✦ Claude", true, v -> showProjectPicker(true));
        claude.setOnLongClickListener(v -> { showProjectPicker(false); return true; });
        addChip(row, "+ Tab", false, v -> newShellTab());
        addChip(row, "☰ Tabs", false, v -> mActivity.getDrawer().openDrawer(Gravity.LEFT));
        addChip(row, "GitHub", false, v -> runInNewTab("GitHub", HOME, "run", "github-login"));
        addChip(row, "Server", false, v -> showServerMenu());
        TextView keys = addChip(row, "⌨", false, v -> {
            if (mActivity.getTermuxTerminalViewClient() != null)
                mActivity.getTermuxTerminalViewClient().onToggleSoftKeyboardRequest();
        });
        keys.setContentDescription("Show or hide the keyboard");
        addChip(row, "?", false, v -> showHelp()).setContentDescription("Help");
    }

    // ---- Buttons ------------------------------------------------------------------------------

    private TextView addChip(LinearLayout row, String label, boolean primary, View.OnClickListener onClick) {
        TextView chip = new TextView(mActivity);
        chip.setText(label);
        chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        chip.setTextColor(primary ? COLOR_ACCENT_TEXT : COLOR_CHIP_TEXT);
        chip.setTypeface(Typeface.DEFAULT, primary ? Typeface.BOLD : Typeface.NORMAL);
        chip.setGravity(Gravity.CENTER);
        chip.setSingleLine(true);
        chip.setMinHeight(dp(32));
        chip.setMinWidth(dp(36));
        chip.setPadding(dp(10), dp(5), dp(10), dp(5));

        GradientDrawable shape = new GradientDrawable();
        shape.setCornerRadius(dp(16));
        shape.setColor(primary ? COLOR_ACCENT : COLOR_CHIP);
        chip.setBackground(new RippleDrawable(ColorStateList.valueOf(COLOR_RIPPLE), shape, null));

        chip.setClickable(true);
        chip.setFocusable(false); // keep keyboard focus on the terminal
        chip.setOnClickListener(onClick);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(dp(2), dp(4), dp(2), dp(4));
        row.addView(chip, params);
        return chip;
    }

    // ---- Projects -----------------------------------------------------------------------------

    /**
     * Lists the folders in ~/projects, plus the current tab's folder, a new project, a clone from
     * GitHub and the phone's shared storage. Picking one opens a tab there, running Claude if
     * {@code withClaude}, or just a shell otherwise (long-press on the Claude button).
     */
    private void showProjectPicker(boolean withClaude) {
        File projectsDir = new File(PROJECTS);
        //noinspection ResultOfMethodCallIgnored
        projectsDir.mkdirs();

        final List<String> labels = new ArrayList<>();
        final List<Runnable> actions = new ArrayList<>();
        final String mode = withClaude ? "open" : "shell";

        String cwd = currentTabFolder();
        if (cwd != null && !cwd.equals(HOME) && !cwd.equals(PROJECTS)) {
            labels.add("📂  This tab's folder: " + prettyPath(cwd));
            actions.add(() -> runInNewTab(new File(cwd).getName(), cwd, mode, cwd));
        }

        File[] projects = projectsDir.listFiles(File::isDirectory);
        if (projects != null) {
            Arrays.sort(projects, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
            for (File project : projects) {
                if (project.getName().startsWith(".")) continue;
                labels.add("📁  " + project.getName());
                actions.add(() -> runInNewTab(project.getName(), project.getAbsolutePath(),
                    mode, project.getAbsolutePath()));
            }
        }

        labels.add("＋  New project…");
        actions.add(this::askNewProject);
        labels.add("⤓  Clone from GitHub…");
        actions.add(this::askClone);
        if (new File(SHARED_STORAGE).isDirectory()) {
            labels.add("📱  Phone storage (Download, Documents, …)");
            actions.add(() -> runInNewTab("Phone files", SHARED_STORAGE, mode, SHARED_STORAGE));
        }

        new AlertDialog.Builder(mActivity)
            .setTitle(withClaude ? "Start Claude in…" : "Open a terminal in…")
            .setItems(labels.toArray(new String[0]), (dialog, which) -> actions.get(which).run())
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private void askNewProject() {
        askText("New project", "Name, like my-app", "Create", InputType.TYPE_CLASS_TEXT, name -> {
            if (!PROJECT_NAME.matcher(name).matches()) {
                Logger.showToast(mActivity, "Use letters, numbers, '.', '-' or '_' (no spaces).", true);
                askNewProject();
                return;
            }
            runInNewTab(name, PROJECTS, "new", name);
        });
    }

    private void askClone() {
        askText("Clone from GitHub", "owner/repo or a git URL", "Clone",
            InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI, repo -> {
                String name = repo.replaceAll("/+$", "").replaceAll("\\.git$", "");
                name = name.substring(name.lastIndexOf('/') + 1);
                if (name.isEmpty()) name = "clone";
                runInNewTab(name, PROJECTS, "clone", repo);
            });
    }

    // ---- Server and help ----------------------------------------------------------------------

    private void showServerMenu() {
        String[] labels = {
            "Start: open from a computer on Wi-Fi",
            "Start: USB only (adb forward)",
            "Show status and addresses",
            "Stop",
        };
        String[][] commands = {
            {"run", "remote-access", "start"},
            {"run", "remote-access", "start", "--local"},
            {"run", "remote-access", "status"},
            {"run", "remote-access", "stop"},
        };
        new AlertDialog.Builder(mActivity)
            .setTitle("Use this terminal from a computer")
            .setItems(labels, (dialog, which) -> runInNewTab("Server", HOME, commands[which]))
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private void showHelp() {
        TextView text = new TextView(mActivity);
        text.setText(HELP_TEXT);
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        text.setTextIsSelectable(true);
        text.setPadding(dp(22), dp(8), dp(22), dp(8));
        ScrollView scroll = new ScrollView(mActivity);
        scroll.addView(text);
        new AlertDialog.Builder(mActivity)
            .setTitle("✦ Terminal Claude")
            .setView(scroll)
            .setPositiveButton(android.R.string.ok, null)
            .show();
    }

    private static final String HELP_TEXT =
        "BUTTONS\n" +
        "✦ Claude — pick a project (or create or clone one) and start Claude in it. " +
        "Long-press to open just a terminal there.\n" +
        "+ Tab — a new terminal tab.   ☰ Tabs — switch or close tabs.\n" +
        "GitHub — sign in, so you and Claude can clone, push and open pull requests.\n" +
        "Server — use this terminal from a computer's browser or over SSH.\n" +
        "⌨ — show or hide the keyboard.   ? — this help.\n" +
        "If the buttons don't all fit, swipe the row sideways.\n\n" +
        "KEYS ROW\n" +
        "ESC — stop Claude mid-answer.\n" +
        "⇧⇥ — switch Claude's mode (normal, auto-accept edits, plan).\n" +
        "/ — Claude commands like /help, /clear.   @ — mention a file.   ! — run a shell command.\n" +
        "↑ ↓ — earlier messages and menu choices.   ⎘ — paste.\n" +
        "Swipe up on a key for its second symbol: - gives _, / gives \\, ← gives Home, → gives End.\n" +
        "Pinch the terminal to zoom the text. Long-press for copy, paste and more.\n\n" +
        "IN CLAUDE\n" +
        "Type what you want in plain words, like \"make a todo web app\". " +
        "Press Enter to send. End a line with \\ for a new line.\n" +
        "The first time, Claude asks you to sign in: tap the link, sign in, paste the code.\n\n" +
        "PREVIEW WHAT YOU BUILD\n" +
        "Servers you or Claude start (npm run dev, python3 -m http.server 8000) open in your " +
        "phone's browser at http://localhost:PORT.\n\n" +
        "WHERE YOUR FILES ARE\n" +
        "~/projects — your projects. They also show in Android's Files app under " +
        "\"Terminal Claude\".\n" +
        "~/storage/shared — your phone's files (Download, Documents, …).\n\n" +
        "COMMANDS\n" +
        "claude · claude-shell · tc new NAME · tc clone OWNER/REPO · tc projects · " +
        "github-login · remote-access · claude-setup (repair) · tc help";

    // ---- Tabs ---------------------------------------------------------------------------------

    private void newShellTab() {
        if (mActivity.getTermuxTerminalSessionClient() != null)
            mActivity.getTermuxTerminalSessionClient().addNewSession(false, null);
    }

    /** Opens a new tab in {@code workDir} running {@code tc <tcArgs>}, and switches to it. */
    private void runInNewTab(String tabName, String workDir, String... tcArgs) {
        TermuxService service = mActivity.getTermuxService();
        if (service == null) return;
        if (service.getTermuxSessionsSize() >= MAX_SESSIONS) {
            Logger.showToast(mActivity, "Too many tabs. Close one first: ☰ Tabs, long-press a tab, or type exit.", true);
            return;
        }

        // bash -l loads the Termux environment, then runs tc with the arguments unchanged.
        String[] args = new String[4 + tcArgs.length];
        args[0] = "-l";
        args[1] = "-c";
        args[2] = "exec \"$0\" \"$@\"";
        args[3] = TC;
        System.arraycopy(tcArgs, 0, args, 4, tcArgs.length);

        String dir = new File(workDir).isDirectory() ? workDir : HOME;
        TermuxSession session = service.createTermuxSession(BASH, args, null, dir, false, tabName);
        if (session == null) return;
        mActivity.getTermuxTerminalSessionClient().setCurrentSession(session.getTerminalSession());
        mActivity.getDrawer().closeDrawers();
    }

    private String currentTabFolder() {
        TerminalSession session = mActivity.getCurrentSession();
        if (session == null) return null;
        try {
            String cwd = session.getCwd();
            return cwd != null && new File(cwd).isDirectory() ? cwd : null;
        } catch (Exception e) {
            return null;
        }
    }

    // ---- Helpers ------------------------------------------------------------------------------

    private interface TextListener { void onText(String text); }

    private void askText(String title, String hint, String positive, int inputType, TextListener listener) {
        EditText input = new EditText(mActivity);
        input.setHint(hint);
        input.setSingleLine(true);
        input.setInputType(inputType);
        FrameLayout container = new FrameLayout(mActivity);
        container.setPadding(dp(22), dp(8), dp(22), 0);
        container.addView(input);

        AlertDialog dialog = new AlertDialog.Builder(mActivity)
            .setTitle(title)
            .setView(container)
            .setPositiveButton(positive, (d, w) -> {
                String text = input.getText().toString().trim();
                if (!text.isEmpty()) listener.onText(text);
            })
            .setNegativeButton(android.R.string.cancel, null)
            .create();
        if (dialog.getWindow() != null)
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        dialog.show();
        input.requestFocus();
    }

    private static String prettyPath(String path) {
        return path.startsWith(HOME) ? "~" + path.substring(HOME.length()) : path;
    }

    private int dp(int value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
            mActivity.getResources().getDisplayMetrics()));
    }
}
