# 150. ssh asks jazygit for a key's passphrase

Date: 2026-11-19

## Context

Every git command that talks to a remote runs ssh with `-o BatchMode=yes`, so that ssh never asks anything on the
terminal the TUI owns. A key with a passphrase that is not in the ssh agent, for instance once the agent's lifetime for
it ran out, is then skipped, and the server refuses the connection. Since #563 the error says so and suggests
`ssh-add`, but the user still has to leave jazygit to push or pull. lazygit asks for the passphrase in a prompt of its
own.

ssh can ask through a program instead of the terminal: with `SSH_ASKPASS` set and `SSH_ASKPASS_REQUIRE=force`
(OpenSSH 8.4 and later), it runs that program with its prompt as the argument, and takes what it prints as the answer.
Batch mode turns that off too.

What jazygit has to build on:

- Push, pull and the other operations the user starts run one at a time on the `io` thread (`Program.launch`). Fetches,
  also one started with `f`, and the files of a pull request run on the background pool.
- A command returns one message, once it is done; nothing reaches the model while it runs. `Terminal.handOver` is the
  one service that blocks a command's thread on the render thread, and waits for it.
- Only one popup is open at a time, and its text is in the model, edited per key. Nothing masks what is typed.
- `gh pr checkout` runs git, and so ssh, without batch mode: ssh opens `/dev/tty` and asks on the TUI's own terminal.

## Decision

- **Only what the user starts asks.** Pull, push (with `--set-upstream`, of a stack too), fast-forward, deleting and
  renaming a branch on its remote, fetching chosen branches, and checking out a pull request run ssh without batch mode,
  with `SSH_ASKPASS` set to jazygit's helper and `SSH_ASKPASS_REQUIRE=force`. Fetches and the files of a pull request
  keep batch mode: a timer must not open a dialog. `CliRemotes` decides per method; the `Remotes` interface does not
  change.
- **jazygit is its own helper, on every platform.** `SSH_ASKPASS` runs jazygit again, with `JAZYGIT_ASKPASS` set to
  the address of the jazygit that started the operation. That run starts no TUI: it sends ssh's prompt to that
  address, prints the answer, and exits; with code 1 when the user cancelled.
  - **The two talk over a Unix domain socket**, which Java has on Linux, macOS and Windows 10 and later. jazygit
    listens on it from start, in a directory of its own in the user's temporary directory, and removes both when it
    quits. A random token, also passed in `JAZYGIT_ASKPASS`, goes with each request, so that no other process of the
    user gets an answer.
  - **The native binary is the helper itself**: its path is `ProcessHandle.current().info().command()`.
  - **`java -jar` gets a launcher**: `SSH_ASKPASS` takes one program and no arguments, so jazygit writes one into its
    directory at start, `askpass.sh` or `askpass.cmd` on Windows, that runs the same `java` on the same jar with the
    helper's main class. That helper loads no TUI, but each prompt waits for a JVM to start.
- **Only passphrases and passwords are answered.** The helper passes on ssh's prompt and `SSH_ASKPASS_PROMPT`; the
  running jazygit decides. A prompt asking for a passphrase or password opens the dialog; any other, such as ssh
  asking to trust an unknown host, is refused without a dialog, as batch mode refused it. A prompt ssh only shows while
  waiting (`SSH_ASKPASS_PROMPT=none`, as for touching a security key) is answered at once with nothing.
- **A service blocks the command until the user answers.** An `Asking` service on `Env`, like `Terminal`, opens the
  popup on the render thread and blocks the command's thread until it is confirmed or cancelled. The popup replaces one
  that is open: the user just started the operation that asks. The two minutes a remote command may take include the
  time spent typing.
- **The passphrase is shown as dots, and kept nowhere.** The popup shows ssh's prompt and one `•` per character typed.
  The text is in the model only while the popup is open; it is written to the FIFO and dropped. It is never logged (the
  debug log names messages, not their contents), stored, or added to the agent. ssh asks again for the next operation.
- **Cancelling fails the operation** as a refused key does: `GitError.SshKeyRefused`, with its hint to `ssh-add`.
- **Windows works as the others do.** Git for Windows runs its own OpenSSH, which has `SSH_ASKPASS_REQUIRE`. Taken to
  hold, and still to be tried on Windows: that the ssh Windows itself ships, which `core.sshCommand` may name for the
  Windows agent, has `SSH_ASKPASS_REQUIRE` too, and that both start `askpass.cmd`. Where one does not, only the native
  binary can ask there.

Not:

- **A shell script with two FIFOs** (`mkfifo`) as the helper: no `mkfifo`, `0700` or shebang on Windows, so Windows
  would be left without.
- **A script per platform**, `sh` with FIFOs and PowerShell with a named pipe: two helpers to keep alike, and to test.
- **A script that writes files and polls for the answer**: the passphrase on disk, if only for a moment.
- **A pseudo-terminal, as lazygit has**, reading ssh's prompts from git's output: Java has none, and pty4j (ConPTY on
  Windows) brings native libraries, which the single native binary cannot carry (see the AWT libraries in the native
  image).
- **ssh in Java** (Apache MINA sshd, or JGit's transport) in place of the user's ssh: `~/.ssh/config`, `ProxyJump` and
  the agent would all have to work as they do in OpenSSH.
- **Handing the terminal to `ssh-add`** on a refused key: simpler, but it needs a running agent, leaves the TUI, and
  still fails the operation first.
- **Git's own `GIT_ASKPASS`**, for user names and passwords over https: credentials there come from a helper, such as
  gh's, and `GIT_TERMINAL_PROMPT=0` stays.

## Consequences

- A push or pull with a key whose passphrase is not in the agent asks for it in jazygit, every time; `ssh-add` stops
  that.
- A fetch, on the timer or with `f`, still fails with the hint to `ssh-add`.
- `gh pr checkout` no longer asks on the terminal under the TUI.
- An OpenSSH older than 8.4 ignores `SSH_ASKPASS_REQUIRE` and, without batch mode, asks on `/dev/tty`. Batch mode stays
  for those (`ssh -V` once at start).
- jazygit has a second way in: a helper run, chosen by `JAZYGIT_ASKPASS` before the command line is read, and a socket
  open as long as it runs.
- With `java -jar`, a JVM starts for each prompt, a fraction of a second before the popup opens; the native binary
  starts at once.
- The passphrase is a Java `String` in the model while it is typed, which cannot be wiped; it lives no longer than the
  popup.
- A command blocked on the user holds the `io` thread and the remote refs guard: other operations, and fetches, wait
  for the answer.
