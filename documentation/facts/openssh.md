# OpenSSH

What OpenSSH does that jazygit relies on, learned by testing, reading the source or docs, or measuring. Each fact says
when it was learned, so a fact that has grown old can be checked again; see README.md.

## Agents and batch mode

- With `-o BatchMode=yes`, ssh never prompts. A key whose passphrase is not in the agent (for example after its agent
  lifetime ran out) is skipped, and the server refuses the connection. Batch mode also disables `SSH_ASKPASS`. *Why it
  matters:* operations the user starts drop batch mode to let jazygit ask, while timer fetches keep it. — learned
  2026-11-22 · [ADR 0150](../adr/0150-ssh-asks-jazygit-for-a-passphrase.md)
- Without batch mode, ssh also tries password and one-time-code authentication where the server allows them, which batch
  mode skipped. *Why it matters:* the askpass popup can be asked for passwords and codes, not only passphrases. —
  learned 2026-11-22 · [ADR 0150](../adr/0150-ssh-asks-jazygit-for-a-passphrase.md)
- SSH agents that ask for approval on every use of a key (the 1Password agent, Secretive, a FIDO key that wants a touch)
  prompt on every fetch, and `ssh -o BatchMode=yes` does not stop it, because the agent asks and not ssh. *Why it
  matters:* the periodic fetch must be possible to turn off (`git.auto-fetch`). — learned 2026-11-22 · [ADR
  0125](../adr/0125-reading-the-status-every-3-s-and-turning-the-timers-off.md)

## SSH_ASKPASS

- With `SSH_ASKPASS` set and `SSH_ASKPASS_REQUIRE=force`, ssh runs that program with its prompt as the argument and
  takes what it prints as the answer. Exit code 1 refuses or cancels. *Why it matters:* this is the mechanism by which
  ssh asks jazygit for secrets. — learned 2026-11-22 · [ADR 0150](../adr/0150-ssh-asks-jazygit-for-a-passphrase.md) ·
  OpenSSH 8.4
- `SSH_ASKPASS` takes one program path and no arguments. *Why it matters:* under `java -jar`, jazygit must write a
  launcher script (`askpass.sh` / `askpass.cmd`). — learned 2026-11-22 · [ADR
  0150](../adr/0150-ssh-asks-jazygit-for-a-passphrase.md)
- ssh sets `SSH_ASKPASS_PROMPT` for the askpass program: unset means it wants a secret; `confirm` means it wants a yes
  (for example to trust an unknown host key); `none` means it is only showing something while it waits (for example
  "touch your security key"). The prompt text itself differs for a passphrase, a password, a security key PIN or a
  one-time code. *Why it matters:* jazygit decides by `SSH_ASKPASS_PROMPT`, not by the prompt text: it asks for secrets,
  refuses confirms, and answers `none` at once. — learned 2026-11-22 · [ADR
  0150](../adr/0150-ssh-asks-jazygit-for-a-passphrase.md)
- After a wrong passphrase, ssh asks again through askpass. If askpass gives no answer, ssh fails as for a refused key,
  and may go on to ask for the next key or a password within the same connection. *Why it matters:* a cancel retires the
  operation's token so that later prompts are refused without a popup. — learned 2026-11-22 · [ADR
  0150](../adr/0150-ssh-asks-jazygit-for-a-passphrase.md)
- ssh reads `SSH_ASKPASS` and any address it needs from its environment when it starts. *Why it matters:* the askpass
  socket must exist before git runs, even though whether ssh will ask cannot be known in advance. — learned 2026-11-22 ·
  [ADR 0153](../adr/0153-the-askpass-socket-opens-when-ssh-may-ask.md)
- Whether ssh will ask cannot be known before it runs: the agent may hold the key, and only ssh knows which keys it
  tries. *Why it matters:* the socket is opened for every operation that may let ssh ask. — learned 2026-11-22 · [ADR
  0153](../adr/0153-the-askpass-socket-opens-when-ssh-may-ask.md)
- ssh holds the passphrase in its own memory, as it does when reading from a terminal. *Why it matters:* clearing
  jazygit's buffer only shortens jazygit's own exposure. — learned 2026-11-22 · [ADR
  0150](../adr/0150-ssh-asks-jazygit-for-a-passphrase.md)

## Versions and platforms

- OpenSSH older than 8.4 ignores `SSH_ASKPASS_REQUIRE` and, without batch mode, prompts on `/dev/tty`. `ssh -V` reports
  the version. *Why it matters:* jazygit probes the version once per ssh command and keeps batch mode when it is older
  or unknown. — learned 2026-11-22 · [ADR 0150](../adr/0150-ssh-asks-jazygit-for-a-passphrase.md) · OpenSSH 8.4
- Git for Windows ships its own OpenSSH, which supports `SSH_ASKPASS_REQUIRE`. Still untested and only assumed: that
  Windows' own ssh (which `core.sshCommand` may name, for the Windows agent) supports it too, and that both start
  `askpass.cmd`. *Why it matters:* askpass on Windows rests partly on these unverified assumptions. — learned 2026-11-22
  · [ADR 0150](../adr/0150-ssh-asks-jazygit-for-a-passphrase.md)

## Configuration

- `ssh -G <alias>` prints the resolved configuration of an ssh host alias, including the real host name. *Why it
  matters:* a remote using an alias such as `github-work` maps to the host it stands for, so one per-host setting covers
  every alias. — learned 2026-11-22 · [ADR 0088](../adr/0088-a-browser-per-account.md)
