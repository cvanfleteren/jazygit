# External facts

What the tools and services jazygit stands on do, as far as jazygit relies on it: learned by testing them, reading
their source or documentation, or measuring. The code says what jazygit does about it; these files say why it has to.
They began as the context of the ADRs (`../adr`), gathered here so that they outlive the decisions they were found
for, and can be checked again as the tools change.

| File | About |
|---|---|
| [git.md](git.md) | git: refs, fetch and push, rebase, worktrees, the index, diff, log, blame, config, and what they cost |
| [gh.md](gh.md) | the gh CLI |
| [github.md](github.md) | GitHub's GraphQL and REST APIs, and what its web pages do |
| [forgejo.md](forgejo.md) | Forgejo's API, and fj |
| [openssh.md](openssh.md) | OpenSSH: askpass, agents, batch mode |
| [terminals.md](terminals.md) | terminals and tmux: escapes, the keyboard, the width of characters |
| [tamboui.md](tamboui.md) | TamboUI |
| [java.md](java.md) | Java and GraalVM native image |
| [libraries.md](libraries.md) | other libraries, file formats and languages, and the precedents of other tools |
| [os.md](os.md) | filesystems and operating systems |

## A fact

Each fact is one line:

```
- <what the tool does>. *Why it matters:* <what that means for jazygit>. — learned 2026-11-22 · ADR 0112 · git ≥ 2.45
```

- **learned** is the date the fact was last known to hold. The facts gathered from the ADRs on 2026-11-22 all carry
  that date, though most were found earlier: the ADR they cite has the date it was written.
- **ADR** is where the fact was found, and where what jazygit decided about it is told. An ADR that was folded into
  another has a stub that says where; one that was removed is in git, and in the index of `../adr/README.md`.
- **A version**, where there is one, is the version the fact needs or was seen on.

## Keeping them true

- A fact that is learned is added to the file of its tool, with the date, and the ADR it came from if there is one.
- A fact that is checked again and still holds gets the new date: `learned` says how old what we know is, not how old
  the fact is.
- A fact that no longer holds is changed to what is true now, with the new date and, after the old version, the
  version it changed in (`gh ≥ 2.104`). Where jazygit relies on the old behaviour, that is a bug to fix, not a fact to
  keep.
- A fact older than a year about something that moves fast (GitHub's and Forgejo's APIs, gh, fj) is worth checking
  again before a decision rests on it.
