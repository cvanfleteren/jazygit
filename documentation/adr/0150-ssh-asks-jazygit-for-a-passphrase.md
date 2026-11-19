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
- **Only an OpenSSH that has `SSH_ASKPASS_REQUIRE` asks.** One older than 8.4 ignores it and, without batch mode, asks
  on `/dev/tty`. The ssh command git runs, `GIT_SSH_COMMAND` or `core.sshCommand` of the worktree, is asked its version
  with `-V` the first time; the answer is kept per command. Where it is older, or the version cannot be told, the
  operation keeps batch mode, `gh pr checkout` included, which then gets a `GIT_SSH_COMMAND` in batch mode too.
- **jazygit is its own helper, on every platform.** `SSH_ASKPASS` runs jazygit again, with `JAZYGIT_ASKPASS` set to
  the address of the jazygit that started the operation and a token. That run starts no TUI: it sends ssh's prompt and
  the token to that address, prints the answer, and exits; with code 1 when the prompt was refused or cancelled.
  - **The two talk over a Unix domain socket**, which Java has on Linux, macOS and Windows 10 and later. jazygit
    listens on it from start, in a directory of its own, `0700`, in the user's temporary directory, and removes both
    when it quits. Their names are short: a socket's path may only be about 100 bytes, and the temporary directory of
    macOS is long already.
  - **Each operation has a token of its own**, random, in its `JAZYGIT_ASKPASS`. It ties a request to the operation
    that asks, and is retired when that operation ends; a request with a retired or unknown token is refused. It
    keeps out a process that only finds the socket, but not one of the same user that reads the environment of git
    or ssh: the directory's permissions are what keeps other users out.
  - **The native binary is the helper itself**: its path is `ProcessHandle.current().info().command()`.
  - **`java -jar` gets a launcher**: `SSH_ASKPASS` takes one program and no arguments, so jazygit writes one into its
    directory at start, `askpass.sh` or `askpass.cmd` on Windows, that runs the same `java` on the same jar with the
    helper's main class. That helper loads no TUI, but each prompt waits for a JVM to start.
- **Only secrets are asked for, by what ssh says it wants.** The helper passes on ssh's prompt and
  `SSH_ASKPASS_PROMPT`; the running jazygit decides on the latter, not on the prompt's words, which differ for a
  passphrase, a password, a security key's PIN or a one-time code:
  - unset: ssh wants a secret, and the popup asks for it, with ssh's prompt as it is;
  - `confirm`: ssh wants a yes, as to trust an unknown host. It is refused without a popup, as batch mode refused it;
  - `none`: ssh only shows something while it waits, as for touching a security key. It is answered at once with
    nothing.
- **The socket's thread waits for the user, not the command's.** The command's thread waits in `Exec` for git, as it
  does for any command. Each request on the socket is handled on a thread of its own, which hands it to an `Asking`
  service: that opens the popup on the render thread and waits until it is confirmed or cancelled, or its operation
  ends. Only the `io` thread asks, one operation at a time, so one popup is asked at a time.
- **An open popup comes back.** A popup the user had open while the operation ran, such as a commit message being
  written, is set aside while ssh asks, and shown again, as it was, once the popup that asks closes.
- **The popup goes with its operation.** The two minutes a remote command may take include the time spent typing.
  When the operation ends, for a timeout or otherwise, its token is retired and its popup closed, if still open,
  with its buffer cleared: an answer has nowhere to go any more.
- **Cancelling refuses the rest of the operation.** A cancel retires the operation's token: ssh gets no answer, and
  whatever it would ask next in that operation, another key or a password, is refused without a popup. ssh then fails
  as a refused key does, and git is left to end by itself, so that its output is kept. A wrong passphrase is no
  cancel: ssh asks again, and the popup opens again with its prompt.
- **The passphrase is never a `String`, and never in the model.** A `String` cannot be wiped, and the collector copies
  it; a text popup would leave one in the model for each key typed.
  - The model has the popup as ssh's prompt only. No message carries a character, nor how many were typed, so the
    debug log has none either.
  - The characters are in a `char[]` the `Asking` service holds, outside the model, as a cursor is view state. The
    popup's input is an element of jazygit's own, which edits that buffer and draws a `•` for each character in it:
    TamboUI's text input keeps its text itself. A buffer that grows clears the array it leaves.
  - A paste is taken, as a passphrase often comes from a password manager, but not as a `PasteEvent`, whose text is
    a `String`. Bracketed paste is off while the popup is open, as it is when the terminal is handed over: the
    terminal then sends a paste as keys, which the input takes as it takes typed ones. It goes on again when the
    popup closes.
  - The buffer is a class, no record, whose `toString()` is `****`. It is cleared once it is written to the socket, on
    a cancel, and when jazygit quits. Encoding it, and the helper run that prints it for ssh, use byte arrays that are
    cleared after, no `String`.
  - It is never stored, nor added to the agent: ssh asks again for the next operation.
  - Not off-heap memory, which the collector never copies: without `mlock`, it can still be swapped out.
- **A cancelled operation fails as a refused key does**: `GitError.SshKeyRefused`. Its hint to `ssh-add` only shows
  when what was cancelled was a key's passphrase; after a password, a PIN or a code, the error says that it was
  cancelled.
- **Windows works as the others do.** Git for Windows runs its own OpenSSH, which has `SSH_ASKPASS_REQUIRE`. Taken to
  hold, and still to be tried on Windows: that the ssh Windows itself ships, which `core.sshCommand` may name for the
  Windows agent, has `SSH_ASKPASS_REQUIRE` too; that both start `askpass.cmd`; and that the native image has Unix
  domain sockets there. Where one does not, only the native binary, or none, can ask there.

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
- `gh pr checkout` no longer asks on the terminal under the TUI. With an older OpenSSH, it fails as a fetch does
  when the agent lacks the key.
- Renaming a branch on its remote fetches, then pushes: two connections, so a key not in the agent is asked for twice.
- Without batch mode, ssh also tries a password or a one-time code where the server takes them, which it skipped
  before: those are asked for too.
- jazygit has a second way in: a helper run, chosen by `JAZYGIT_ASKPASS` before the command line is read, and a socket
  open as long as it runs.
- With `java -jar`, a JVM starts for each prompt, a fraction of a second before the popup opens; the native binary
  starts at once.
- Clearing only shortens how long the passphrase is in memory: each key still comes in as an event of the terminal
  backend, with its own buffers, and ssh holds it as it does on a terminal. The protection is that jazygit only sees it
  when the agent lacks the key, and drops it once ssh has it.
- A pasted newline is an Enter, which confirms the popup; what was pasted after it reaches the panels as keys.
- A command blocked on the user holds the `io` thread and the remote refs guard: other operations, and fetches, wait
  for the answer.
