# Terminal Claude: sourced by login shells from $PREFIX/etc/profile.
# Runs the one-time Claude Code setup in the first interactive session.
case "$-" in
    *i*)
        if [ ! -f "$HOME/.terminal-claude/setup-complete" ] && [ -z "$TERMINAL_CLAUDE_SETUP_RUNNING" ]; then
            if [ -x "$PREFIX/bin/claude-setup" ]; then
                "$PREFIX/bin/bash" "$PREFIX/bin/claude-setup"
            fi
        fi
        ;;
esac
