# Terminal Claude: sourced by login shells from $PREFIX/etc/profile.
#  - Runs the Claude Code setup in the first interactive session, and again when
#    an app update brings a newer setup version.
#  - Shows a short welcome and sets a prompt with the folder and git branch.
#    Put `TC_PROMPT=0` in ~/.terminal-claude/config to keep your own prompt.
#  - Starts remote access if autostart is on.
case "$-" in
    *i*)
        [ -f "$HOME/.terminal-claude/config" ] && . "$HOME/.terminal-claude/config"

        __tc_setup_version="$(sed -n 's/^SETUP_VERSION=//p' "$PREFIX/bin/claude-setup" 2>/dev/null | head -1)"
        if [ -z "$TERMINAL_CLAUDE_SETUP_RUNNING" ] && [ -n "$__tc_setup_version" ] &&
            [ "$(cat "$HOME/.terminal-claude/setup-complete" 2>/dev/null)" != "$__tc_setup_version" ] &&
            [ ! -d "$HOME/.terminal-claude/setup.lock" ]; then
            "$PREFIX/bin/bash" "$PREFIX/bin/claude-setup"
            TC_NO_WELCOME=1
        fi
        unset __tc_setup_version

        if [ -f "$HOME/.terminal-claude/remote-access.autostart" ] && [ -x "$PREFIX/bin/remote-access" ]; then
            "$PREFIX/bin/bash" "$PREFIX/bin/remote-access" --autostart
        fi

        if [ -z "$TC_NO_WELCOME" ]; then
            printf '\n  \033[38;5;173m\033[1m✦ Terminal Claude\033[0m\n'
            printf '  \033[2mTap\033[0m ✦ Claude \033[2mto start, or type\033[0m claude\033[2m. Tap\033[0m ? \033[2mfor help.\033[0m\n'
        fi
        unset TC_NO_WELCOME

        if [ -n "$BASH_VERSION" ] && [ "${TC_PROMPT:-1}" != 0 ]; then
            PROMPT_DIRTRIM=2
            __tc_branch() {
                local b
                b="$(git symbolic-ref --short HEAD 2>/dev/null || git rev-parse --short HEAD 2>/dev/null)" || return
                [ -n "$b" ] && printf ' (%s)' "$b"
            }
            # Set on every prompt, so the default PS1 from bash.bashrc doesn't win.
            __tc_prompt() {
                local s=$?
                local arrow='\[\e[38;5;173m\]❯\[\e[0m\]'
                [ $s -ne 0 ] && arrow='\[\e[31m\]❯\[\e[0m\]'
                PS1='\n\[\e[1;38;5;111m\]\w\[\e[0;38;5;245m\]$(__tc_branch)\[\e[0m\]\n'"$arrow "
                return $s
            }
            case ";$PROMPT_COMMAND;" in
                *";__tc_prompt;"*) ;;
                *) PROMPT_COMMAND="__tc_prompt${PROMPT_COMMAND:+;$PROMPT_COMMAND}" ;;
            esac
            alias ll='ls -la'
        fi
        ;;
esac
