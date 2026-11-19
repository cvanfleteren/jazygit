# 153. The askpass socket opens when ssh may ask

Date: 2026-11-19

## Context

ADR 150 has ssh ask the user through jazygit: ssh runs jazygit as its `SSH_ASKPASS`, which passes the question over a
Unix domain socket to the running app. jazygit listens on that socket from its start, in a directory of its own in
the user's temporary directory, with the launcher `java -jar` needs written beside it, and removes both when it
quits.

Most sessions never ask: they browse, stage and commit, and the fetches on the timer run ssh in batch mode. Yet every
session makes the directory, the socket and the launcher, and keeps a thread waiting on the socket. A jazygit that is
killed, or crashes, never runs its shutdown hook, and leaves them in the temporary directory.

ssh reads `SSH_ASKPASS` and the address of the socket from its environment as it starts, so the socket has to be there
before git runs. Whether ssh will ask cannot be known before then: the agent may hold the key, and only ssh knows which
keys it tries.

## Decision

- **The socket opens for the first operation that lets ssh ask**: one the user started, whose ssh is an OpenSSH that
  has `SSH_ASKPASS_REQUIRE` (ADR 150). The directory, the socket, the launcher and the thread that waits on it are
  made then, once, and stay until jazygit quits.
- **A socket that cannot be opened leaves the operation in batch mode**, as one that could not be opened at the start
  did. It is not tried again for the next operation.
- **Once jazygit quits, nothing opens again**: an operation still running then keeps batch mode.

## Consequences

- A session that never pushes, pulls or checks out a pull request leaves nothing in the temporary directory, and
  listens on no socket.
- The first push or pull of a session makes the socket, a few milliseconds before git starts.
- A socket that cannot be opened only shows at the first such operation, in the debug log, as before.
