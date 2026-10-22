# jazygit

A lazygit-style terminal UI for git, written in Java on [TamboUI](https://github.com/tamboui).

## Requirements

- **git 2.45 or newer**, on the `PATH`. jazygit does everything through the git command line.
- **Java 25**, to run the jar. The native image (`mvn -Pnative package`) needs no Java to run.
- **A real terminal**: jazygit is a full-screen TUI, so it needs a terminal attached to its input and output.
  It runs on Linux, macOS and Windows.
- **[gh](https://cli.github.com/)**, optional: the GitHub features (pull requests, checks, opening in the browser)
  run through it, logged in with `gh auth login`. Without it, everything else works. Another gh than the one on the
  `PATH` can be set in the settings (Ctrl+S).

To build from source:

- **JDK 25** and **Maven**: `mvn verify` builds and tests both modules, and `./run.sh` builds the jar when needed and
  runs it.
- **Python 3.12 or newer**, for the acceptance tests that drive the built jar in a terminal; see
  [acceptance/README.md](acceptance/README.md).
- **GraalVM 25**, for the native image, plus a C compiler and the static zlib it links against. The Nix dev shell
  (`flake.nix`) provides GraalVM and Maven.
