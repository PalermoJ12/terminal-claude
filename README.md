# Terminal Claude

An Android terminal app for running [Claude Code](https://docs.claude.com/en/docs/claude-code) on your phone or tablet, with access to your device's files.

It is a build of the open-source [Termux](https://github.com/termux/termux-app) terminal with one addition: on first launch it installs everything Claude Code needs automatically.

## Install

1. Open the [Releases](../../releases) page and download an APK:
   - `terminal-claude_arm64-v8a.apk`: most phones and tablets.
   - `terminal-claude_x86_64.apk`: x86_64 devices and emulators.
   - `terminal-claude_universal.apk`: if you're not sure which you need.
2. **If the official Termux app is installed, uninstall it first.** This app uses the same package name (`com.termux`), because Termux's prebuilt packages only work under that name, and it's signed with a different key, so Android won't install one over the other.
3. Open the APK and allow installing from unknown sources when Android asks.

Claude Code needs a **64-bit** device (arm64 or x86_64) with roughly **1 GB of free space**.

## First launch

Open **Terminal Claude** and leave it open. The first session runs `claude-setup`, which:

1. **Asks for storage access.** Tap *Allow* so Claude can read and edit your files. Your shared storage then appears at `~/storage/shared` (for example `~/storage/shared/Download`).
2. **Updates Termux and installs tools:** git, openssh, curl, wget, python, Node.js (LTS), ripgrep, jq, nano, vim, tmux and proot-distro.
3. **Creates a small Debian environment** (`claude-env`) with proot-distro. Claude Code is published only as Linux (glibc) binaries, not Android ones, so it runs in there. Debian also gets git, Python, Node.js and npm.
4. **Installs Claude Code** with the official installer, falling back to npm if that fails.
5. **Adds the `claude` and `claude-shell` commands.**

The download is about 300–500 MB and takes 5–15 minutes. If a step fails (usually because the connection dropped), run `claude-setup` to pick up where it stopped. `claude-setup --force` redoes every step. The log is at `~/.terminal-claude/setup.log`.

## Use

```sh
cd ~/storage/shared/Documents/my-project   # or any folder
claude                                      # start Claude Code here
```

- `claude` runs Claude Code in your current folder. Arguments are passed through, so `claude -p "summarize this repo"` and `claude --resume` work as usual.
- `claude-shell` opens a root shell in the Debian environment, in your current folder. Use it for `apt install ...` if Claude needs a tool that Termux doesn't have.
- Both Termux's home folder and your device storage (`/sdcard`, `/storage/emulated/0`) are visible inside the Debian environment at the same paths.

**Logging in.** The first time you run `claude`, it shows a sign-in link. Tap it, or long-press the terminal and choose *More… → Select URL*, then sign in and paste the code back into the terminal. To use an API key instead, run `export ANTHROPIC_API_KEY=sk-ant-...` before `claude`. Add that line to `~/.bashrc` to keep it.

**Updates.** Claude Code updates itself. Run `pkg upgrade` now and then to update the Termux tools.

## Build it yourself

GitHub Actions builds the APKs on every push (`.github/workflows/build-apk.yml`) and publishes them as a release. To build locally, you need JDK 17 and the Android SDK and NDK (the version in `gradle.properties`):

```sh
./gradlew assembleDebug
# APKs in app/build/outputs/apk/debug/
```

The changes from upstream Termux are:

| File | Change |
|---|---|
| `app/src/main/assets/terminal-claude/claude-setup` | The setup script described above, installed to `$PREFIX/bin/claude-setup`. |
| `app/src/main/assets/terminal-claude/terminal-claude.sh` | Installed to `$PREFIX/etc/profile.d/`. Runs `claude-setup` in new sessions until setup has finished once. |
| `app/src/main/java/com/termux/app/ClaudeSetupInstaller.java` | Copies the two files above into place when the first session starts. |
| `app/src/main/java/com/termux/app/TermuxActivity.java` | Calls `ClaudeSetupInstaller`. |
| `app/src/main/res/values/strings.xml` | App name changed to "Terminal Claude". |
| `.github/workflows/build-apk.yml` | Builds the APKs and publishes the release. |

## Notes and limitations

- Claude Code runs under `proot`, which is slower than native code for file-heavy work.
- The APK is signed with Termux's public test key, like Termux's own GitHub builds. That's fine for personal use. Don't use it to sign apps you distribute.
- Termux plugins (Termux:API, Termux:Widget, …) from F-Droid or Google Play are signed with a different key and won't work with this app.

## License

Termux is licensed under GPLv3. See [LICENSE.md](LICENSE.md) and the original [Termux README](README.termux.md). Claude Code is a separate product of Anthropic and is downloaded on first launch under its own [terms](https://www.anthropic.com/legal/commercial-terms).
