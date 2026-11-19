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
- **The helper is a shell script with two FIFOs.** At start, jazygit makes a directory only the user can read
  (`0700`) in the temporary directory, with the script and two named pipes (`mkfifo`). The script writes its prompt to
  one and reads the answer from the other; exit code 1 when the user cancelled. A thread of jazygit reads the
  requests. The directory goes when jazygit quits.
- **Only passphrases and passwords are answered.** jazygit, not the script, looks at the prompt: one asking for a
  passphrase or password opens the dialog; any other, such as ssh asking to trust an unknown host, is refused without
  a dialog, as batch mode refused it. A prompt ssh only shows while waiting (`SSH_ASKPASS_PROMPT=none`, as for touching
  a security key) is answered at once with nothing.
- **A service blocks the command until the user answers.** An `Asking` service on `Env`, like `Terminal`, opens the
  popup on the render thread and blocks the command's thread until it is confirmed or cancelled. The popup replaces one
  that is open: the user just started the operation that asks. The two minutes a remote command may take include the
  time spent typing.
- **The passphrase is shown as dots, and kept nowhere.** The popup shows ssh's prompt and one `•` per character typed.
  The text is in the model only while the popup is open; it is written to the FIFO and dropped. It is never logged (the
  debug log names messages, not their contents), stored, or added to the agent. ssh asks again for the next operation.
- **Cancelling fails the operation** as a refused key does: `GitError.SshKeyRefused`, with its hint to `ssh-add`.
- **Not on Windows.** No FIFOs and no `sh`: batch mode stays, and the error from #563 is all there is.

Not:

- **A pseudo-terminal, as lazygit has**, reading ssh's prompts from git's output: Java has none, and pty4j brings
  native libraries, which the single native binary cannot carry (see the AWT libraries in the native image).
- **jazygit itself as the helper** (`jazygit --askpass`), talking over a Unix socket: it works on Windows too, but
  jazygit does not know its own executable, which is `java -jar …` or the native binary, and starting a JVM for each
  prompt is slow.
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
- The passphrase is a Java `String` in the model while it is typed, which cannot be wiped; it lives no longer than the
  popup.
- A command blocked on the user holds the `io` thread and the remote refs guard: other operations, and fetches, wait
  for the answer.
