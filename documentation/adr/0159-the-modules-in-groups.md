# 159. The modules in groups

Date: 2026-11-19

## Context

Only the features are grouped (ADR 136): `jazygit-features` gathers their four modules. The other fifteen modules sit
side by side in the root, in the order they were made: `jazygit-base` beside `jazygit-state`, `jazygit-lens` between
`jazygit-askpass` and `jazygit-image`. Their layers are there, but only the module table of `ARCHITECTURE.md` and the
dependencies of each pom show them:

- seven modules are libraries that know nothing of git, GitHub or the application: `base`, `lens`, `json`, `yaml`,
  `xml`, `syntax` and `image`. None uses another module of jazygit.
- four run a program and give back what it did as values: `process`, `git`, `gh` and `askpass`. They use only the
  libraries and each other, and `CoreRules` already calls them the core.
- four are what every feature builds on, which the ADRs call the kernel: `settings`, `state`, `ui` and `content`.

A group of these never uses one above it: the libraries use nothing of jazygit, the core the libraries, the kernel the
libraries and the core, the features all three.

## Decision

1. **The modules are in four groups**, a folder each, whose pom is both the aggregator and the parent of its modules,
   as `jazygit-features` is:

   | Group | Modules |
   |---|---|
   | `jazygit-lib` | `lib-base`, `lib-lens`, `lib-json`, `lib-yaml`, `lib-xml`, `lib-syntax`, `lib-image` |
   | `jazygit-core` | `core-process`, `core-git`, `core-gh`, `core-askpass` |
   | `jazygit-kernel` | `kernel-settings`, `kernel-state`, `kernel-ui`, `kernel-content` |
   | `jazygit-features` | `features-changes`, `features-branches`, `features-github`, `features-chrome` |

   `jazygit-app`, `jazygit-test-support` and `jazygit-benchmarks` stay in the root: the app is the one module above
   every group, and the other two serve the build.
2. **A module's name starts with its group's**, not with `jazygit-`: its folder and its artifact id are
   `<group>-<name>`.
3. **The packages follow the groups**: a module's root package is `net.vanfleteren.jazygit.<group>.<name>`, as
   `kernel-state` holds `net.vanfleteren.jazygit.kernel.state` and `core-askpass` holds
   `net.vanfleteren.jazygit.core.askpass`. The packages under a root keep their place in it (`kernel.state.diff`), and
   the app keeps `net.vanfleteren.jazygit.app`, as it is in no group. The keys of a bundle keep starting with the
   module's name without its group (`github.dialog.…`): `Messages` looks for the bundle of `github` in the groups that
   have text, the kernel and the features, then in the app.
4. **A group uses only the groups before it**: lib, core, kernel, features, then the app. The build only stops a cycle
   between two modules, so a kernel module could still use a feature that does not use it: `GroupsTest`, in the app,
   which sees every module, checks the order on the packages of the groups.

## Consequences

- The folders and the packages show the layers; a new module goes in the group of what it may use.
- A package name tells which group a class is in, so an ArchUnit rule can name a group (`..jazygit.features..`).
- Every import of jazygit changes once; the ADRs before this one keep the old names.
- One module is built by its artifact id, `-pl :core-git`; a bare `-pl core-git` is a path, and no longer one.
- The fat jar and the native image keep their names: `jazygit-app` names them.
- The group poms share nothing yet; what all the modules of a group need may move up into its pom.
