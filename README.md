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
2. **Updates Termux and installs tools:** git, openssh, curl, wget, python, Node.js (LTS), ripgrep, jq, nano, vim, tmux, ttyd, the GitHub CLI (gh) and proot-distro.
3. **Creates a small Debian environment** (`claude-env`) with proot-distro. Claude Code is published only as Linux (glibc) binaries, not Android ones, so it runs in there. Debian also gets git, Python, Node.js and npm.
4. **Installs Claude Code** with the official installer, falling back to npm if that fails.
5. **Adds the `claude`, `claude-shell`, `github-login` and `remote-access` commands.**
6. **Offers to sign you in to GitHub** (optional, see [GitHub](#github)).

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

**Updates.** Claude Code updates itself. Run `pkg upgrade` now and then to update the Termux tools. When you install a newer APK, setup reruns automatically if anything changed. It skips the steps that are already done.

## GitHub

```sh
github-login
```

This signs you in with the GitHub CLI. It shows a one-time code and opens github.com in your browser, where you paste the code and approve. Setup also offers this at the end. Afterwards:

- `git clone`, `git pull` and `git push` to your GitHub repos work over HTTPS with no password prompts.
- `gh` works: `gh repo clone OWNER/REPO`, `gh repo create`, `gh pr create`, `gh issue list`, …
- **Claude Code can use it too.** The sign-in and `~/.gitconfig` are shared with the Debian environment, so Claude can commit, push and open pull requests for you.
- If you haven't set a commit name and email, it uses your GitHub name and your private `ID+username@users.noreply.github.com` address. To change them: `git config --global user.name "…"` and `git config --global user.email …`.
- Git normally refuses repos on shared storage (`/sdcard`) with a "dubious ownership" error. `github-login` turns that check off with `safe.directory = *`, which is fine for a single-user phone.

`github-login --status` shows the signed-in account, and `github-login --logout` signs out. The token is kept in `~/.config/gh/hosts.yml` in the app's private storage, which other apps can't read.

## Run local servers (localhost)

The app works like a small Linux computer. Servers you, or Claude, start keep running and open in the phone's browser at `http://localhost:PORT`:

```sh
python3 -m http.server 8000      # serve the current folder: http://localhost:8000
npm run dev                      # a web app Claude made for you
```

This works the same in Termux, in `claude-shell` and in anything Claude runs, because they all share the phone's network.

- **Use ports 1024 and above** (3000, 5173, 8000, 8080, …). Android doesn't allow lower ports.
- **To open a server from a computer on the same Wi-Fi**, have it listen on `0.0.0.0` instead of only `localhost`. For example: `python3 -m http.server 8000 --bind 0.0.0.0`, `npx vite --host`, `next dev -H 0.0.0.0`. Then open `http://PHONE_IP:PORT`. `remote-access status` shows the phone's IP.
- **Over USB, the server shows up as localhost on the computer.** Turn on USB debugging, then on the computer run `adb forward tcp:3000 tcp:3000` and open `http://localhost:3000`.
- Claude already knows these rules. Setup writes them to its `~/.claude/CLAUDE.md` in the Debian environment, so it binds dev servers to `0.0.0.0` and avoids low ports.

## Use it from a computer

`remote-access` lets you open this terminal, and Claude Code, from a computer's browser or over SSH:

```sh
remote-access start           # first time: asks you to choose a password
```

It prints the addresses to open, for example:

```
Web terminal:  http://192.168.1.42:7681     (user: claude, your password)
SSH:           ssh -p 8022 192.168.1.42
```

Type `claude` in the browser terminal to start Claude Code there.

| Command | What it does |
|---|---|
| `remote-access start` | Start the web terminal (port 7681) and SSH server (port 8022) on your Wi-Fi network. |
| `remote-access start --local` | Listen only on the phone itself. Reach it from a computer over USB: `adb forward tcp:7681 tcp:7681`, then open `http://localhost:7681`. |
| `remote-access stop` | Stop both. |
| `remote-access status` | Show what's running and the phone's addresses. |
| `remote-access password` | Change the password. |
| `remote-access autostart on` | Start automatically whenever the app opens. Add `--local` for USB-only. Turn it off with `autostart off`. |

**Security.** Anyone on the same network who has the password can control the terminal, including your files and your Claude account. Use it only on networks you trust, such as home Wi-Fi or the phone's own hotspot, and choose a strong password. The web terminal uses plain HTTP, so on an untrusted network use `--local` over USB, or SSH.

**Keeping it running.** The app keeps a notification while it runs, and `remote-access` holds a wake lock so the servers still answer when the screen is off. If Android still stops it, open *Settings → Apps → Terminal Claude → Battery* and choose *Unrestricted*.

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
| `app/src/main/assets/terminal-claude/github-login` | The `github-login` command (GitHub CLI sign-in plus git credential and identity setup), installed to `$PREFIX/bin/`. |
| `app/src/main/assets/terminal-claude/remote-access` | The `remote-access` command (web terminal with ttyd, SSH with sshd), installed to `$PREFIX/bin/`. |
| `app/src/main/assets/terminal-claude/terminal-claude.sh` | Installed to `$PREFIX/etc/profile.d/`. Runs `claude-setup` in new sessions until the current setup version has finished, and starts `remote-access` if autostart is on. |
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
