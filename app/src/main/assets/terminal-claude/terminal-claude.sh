# Terminal Claude: sourced by login shells from $PREFIX/etc/profile.
# Runs the Claude Code setup in the first interactive session, and again when
# an app update brings a newer setup version. Must match SETUP_VERSION in
# claude-setup.
TERMINAL_CLAUDE_SETUP_VERSION=2
case "$-" in
    *i*)
        if [ -z "$TERMINAL_CLAUDE_SETUP_RUNNING" ] && [ -x "$PREFIX/bin/claude-setup" ] &&
            [ "$(cat "$HOME/.terminal-claude/setup-complete" 2>/dev/null)" != "$TERMINAL_CLAUDE_SETUP_VERSION" ]; then
            "$PREFIX/bin/bash" "$PREFIX/bin/claude-setup"
        fi
        if [ -f "$HOME/.terminal-claude/remote-access.autostart" ] && [ -x "$PREFIX/bin/remote-access" ]; then
            "$PREFIX/bin/bash" "$PREFIX/bin/remote-access" --autostart
        fi
        ;;
esac
