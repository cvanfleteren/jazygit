# Filesystems and operating systems

What filesystems and operating systems do that jazygit relies on, learned by testing, reading the source or docs, or
measuring. Each fact says when it was learned, so a fact that has grown old can be checked again; see README.md.

## File stamps

- File modification times are kept only so precisely: a few milliseconds on ext4, a second or two on FAT and many
  network mounts; two changes within that window can leave a stamp unchanged. *Why it matters:* a file modified less
  than 2 s before its stamp was taken counts as unknown (`CliMetadata.SETTLED`). — learned 2026-11-22 · [ADR
  0015](../adr/0015-check-metadata-before-loading.md)
- Modification times come from the file system's clock: on a network mount whose server clock is more than 2 s behind, a
  change right after another can be missed; one whose clock is ahead makes every stamp look fresh. *Why it matters:*
  metadata checks degrade on skewed network mounts. — learned 2026-11-22 · [ADR
  0015](../adr/0015-check-metadata-before-loading.md)
- A file's identity can be stamped by modification time, size and file key (inode). *Why it matters:* a file replaced by
  rename is detected even with the same time and size. — learned 2026-11-22 · [ADR
  0015](../adr/0015-check-metadata-before-loading.md)

## Processes, pipes and command lines

- A command line with tens of thousands of paths can exceed the system's limit on argument length. *Why it matters:*
  staging a filtered directory file by file can fail on very large sets. — learned 2026-11-22 · [ADR
  0105](../adr/0105-filtering-the-files-of-a-tree.md)
- A program left running on a pipe that nobody reads any more (for example after jazygit quits or crashes) is killed by
  `SIGPIPE` at its next write. *Why it matters:* the output of editors and browsers left running is thrown away rather
  than piped to jazygit. — learned 2026-11-22 · [ADR 0146](../adr/0146-a-failed-editor-in-the-command-log.md)
- A temporary file removed while a program still writes to it keeps growing, invisible to any directory listing, until
  the program stops, and can fill the disk. *Why it matters:* an editor's stderr is not captured to a temp file either.
  — learned 2026-11-22 · [ADR 0146](../adr/0146-a-failed-editor-in-the-command-log.md)
- `sh -c 'trap "" PIPE; exec "$@"'` makes a program ignore `SIGPIPE`, but it needs a POSIX shell, which Windows lacks,
  and a program that treats a failed write as fatal may still stop. *Why it matters:* this workaround for capturing an
  editor's stderr was rejected. — learned 2026-11-22 · [ADR 0146](../adr/0146-a-failed-editor-in-the-command-log.md)

## Windows

- Windows has no `sh`. *Why it matters:* an editor's `command` is a list run without a shell, not a string for `sh -c`.
  — learned 2026-11-22 · [ADR 0110](../adr/0110-a-worktree-opened-in-an-editor.md)
- Windows has no `mkfifo`, no `0700` permissions and no shebang scripts. *Why it matters:* a shell-script askpass helper
  with FIFOs would leave Windows without one, so jazygit is its own helper. — learned 2026-11-22 · [ADR
  0150](../adr/0150-ssh-asks-jazygit-for-a-passphrase.md)

## Sockets and permissions

- A Unix domain socket's path may be only about 100 bytes, and macOS's temporary directory path is already long. *Why it
  matters:* the askpass directory and socket names are kept short. — learned 2026-11-22 · [ADR
  0150](../adr/0150-ssh-asks-jazygit-for-a-passphrase.md)
- A `0700` directory keeps other users out, but a process of the same user can read the environment of git or ssh. *Why
  it matters:* the per-operation token only stops processes that just find the socket, and directory permissions are the
  real barrier against other users. — learned 2026-11-22 · [ADR 0150](../adr/0150-ssh-asks-jazygit-for-a-passphrase.md)

## Browsers and editors

- The desktop's opener for a URL is `xdg-open` on Linux and `open` on macOS. On Windows it is `rundll32
  url.dll,FileProtocolHandler`, because `cmd /c start` takes an `&` in the URL as the end of the command. *Why it
  matters:* these open a link when no browser is set. — learned 2026-11-22 · [ADR
  0096](../adr/0096-opening-the-links-of-a-comment.md)
- The browser to open is `$BROWSER`, else the desktop's opener: `xdg-open` on Linux, `open` on macOS, the URL handler on
  Windows. *Why it matters:* Forgejo pull requests are opened in the browser without fj. — learned 2026-11-22 · [ADR
  0176](../adr/0176-the-pull-request-of-a-branch-on-forgejo.md)
- A browser started from a command may be the browser process itself (and outlive the caller) or a command that hands
  the link to a running browser and exits. *Why it matters:* the browser is not waited for; it counts as opened if it is
  still running a moment later or exited with 0. — learned 2026-11-22 · [ADR
  0085](../adr/0085-a-review-remark-opens-in-the-browser.md)
- Not every editor opens a directory. *Why it matters:* git's editor is not used as a fallback to open a worktree. —
  learned 2026-11-22 · [ADR 0110](../adr/0110-a-worktree-opened-in-an-editor.md)

## Standard directories

- The OS cache directory is `$XDG_CACHE_HOME`, else `~/.cache`, on Linux; `~/Library/Caches` on macOS; and
  `%LOCALAPPDATA%` on Windows. *Why it matters:* job logs are cached under `<cache>/jazygit` (overridable with
  `$JAZYGIT_CACHE_DIR`). — learned 2026-11-22 · [ADR 0160](../adr/0160-the-log-of-a-check.md)
- `$XDG_DATA_HOME`, else `~/.local/share`, is where fj keeps its data. *Why it matters:* this is where jazygit looks for
  fj's `keys.json`. — learned 2026-11-22 · [ADR 0174](../adr/0174-how-the-last-build-on-forgejo-went.md)
