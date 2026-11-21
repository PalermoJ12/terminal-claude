#!/bin/bash
# End-to-end check on an Android emulator (run by .github/workflows/build-apk.yml
# inside reactivecircus/android-emulator-runner):
#   1. installs the APK and opens the app, which installs the bootstrap and runs
#      claude-setup for real (Termux packages, Debian, Claude Code);
#   2. waits for setup to finish and checks that Claude Code runs;
#   3. taps through the quick-action buttons;
# taking screenshots along the way into ./screenshots.
set -u

OUT="$PWD/screenshots"
APK="dist/terminal-claude_universal-all-devices.apk"
PKG=com.termux
mkdir -p "$OUT"
FAILURES=0

shot() { adb exec-out screencap -p >"$OUT/$1.png" && echo "screenshot: $1"; }
app_cat() { adb shell run-as "$PKG" cat "$1" 2>/dev/null | tr -d '\r'; }
check() {
    if eval "$2"; then echo "PASS: $1"; else echo "FAIL: $1"; FAILURES=$((FAILURES + 1)); fi
}

# Center of the first on-screen element whose text is (or starts with) $1.
find_text() {
    rm -f /tmp/ui.xml
    for _ in 1 2 3 4 5; do
        adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1 &&
            adb pull /sdcard/ui.xml /tmp/ui.xml >/dev/null 2>&1 && break
        sleep 2
    done
    [ -f /tmp/ui.xml ] || return 1
    python3 - "$1" <<'PY'
import re, sys, xml.etree.ElementTree as ET
want = sys.argv[1].lower()
for node in ET.parse('/tmp/ui.xml').iter('node'):
    text = (node.get('text') or '').strip().lower()
    if text == want or text.startswith(want):
        x1, y1, x2, y2 = map(int, re.findall(r'\d+', node.get('bounds')))
        print((x1 + x2) // 2, (y1 + y2) // 2)
        break
PY
}
tap() {
    local xy
    xy="$(find_text "$1")"
    if [ -n "$xy" ]; then
        adb shell input tap $xy
        echo "tapped '$1' at $xy"
    else
        echo "FAIL: could not find '$1' on screen"
        FAILURES=$((FAILURES + 1))
    fi
}

echo "::group::Install and launch"
adb install -r -g "$APK"
adb shell svc power stayon true
adb shell settings put system screen_off_timeout 2147483647
adb shell am start -n "$PKG/.app.TermuxActivity"
sleep 25
shot 01-first-launch
sleep 35
shot 02-setup-progress
echo "::endgroup::"

echo "::group::Wait for setup (up to 35 minutes)"
done_version=""
for i in $(seq 1 105); do
    done_version="$(app_cat files/home/.terminal-claude/setup-complete)"
    [ -n "$done_version" ] && break
    [ $((i % 15)) = 0 ] && shot "03-setup-$((i / 3))min"
    sleep 20
done
app_cat files/home/.terminal-claude/setup.log >"$OUT/setup.log"
tail -n 40 "$OUT/setup.log"
echo "::endgroup::"
shot 04-setup-finished
check "setup completed (version '$done_version')" '[ -n "$done_version" ]'
check "Claude Code starts" 'grep -q "^claude --version: [0-9]" "$OUT/setup.log"'

echo "::group::Quick actions"
# Answer the optional GitHub sign-in question with "no".
adb shell input text n
adb shell input keyevent 66
sleep 3
shot 05-ready

tap "?"
sleep 3
shot 06-help
adb shell input keyevent 4
sleep 2

tap "✦ Claude"
sleep 3
shot 07-project-picker
tap "＋"
sleep 3
shot 08-new-project
adb shell input text demo-app
sleep 1
tap "Create"
sleep 40
shot 09-claude-in-project
check "project folder created" 'adb shell run-as "$PKG" ls files/home/projects/demo-app/.git >/dev/null 2>&1'

tap "☰ Tabs"
sleep 3
shot 10-tabs
adb shell input keyevent 4
sleep 2

tap "Server"
sleep 3
shot 11-server-menu
adb shell input keyevent 4
echo "::endgroup::"

echo "$FAILURES failure(s)"
exit "$FAILURES"
