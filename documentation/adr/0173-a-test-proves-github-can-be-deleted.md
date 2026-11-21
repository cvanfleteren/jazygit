# 173. A test proves GitHub can be deleted

Date: 2026-11-21

## Context

ADR 171 has CI prove that GitHub can be deleted: a job runs `scripts/without-github.sh`, which copies the repository,
deletes `features-github`, `core-gh` and `jazygit-github-app-tests`, their lines in the poms and the line of
`GitHubFeature.FEATURE` in `Features.ALL`, and runs `./mvnw verify` on what is left. That is a second build of the
whole repository on every pull request, without a build cache of its own: as long as the first, for a proof that
changes only when the lines around GitHub do.

What the build of the copy catches is:

- a class that is not GitHub's using one of GitHub's, which then does not compile;
- a pom that names one of GitHub's modules other than to list it or for the app to depend on it, which then names
  one that is not there;
- `Features` naming GitHub other than on the lines that are deleted;
- a test of the app that needs GitHub to pass.

`jazygit --without github` (#628) leaves GitHub out of `Features.ALL`, so that the app runs as if it were deleted.

## Decision

- **`GitHubDeletableTest` in `jazygit-app` checks what the build of the copy catches**, on what the app sees, which is
  every module:
  - no class outside `..features.github..` and `..core.gh..`, but `Features`, uses a class in them, in the main code
    of every module and in the tests on the app's classpath (ArchUnit);
  - `Features` uses GitHub only by reading `GitHubFeature.FEATURE`, and names it on two lines of its own: the import
    and the line in the list;
  - the poms name GitHub's modules only as a `module` of the root, `jazygit-core` and `jazygit-features` poms, as a
    dependency of `jazygit-app`, and in the dependency management of the root pom. A module that cannot see GitHub
    cannot use it, in its main code or its tests, so no other module's tests need checking;
  - no other file under a module's sources, as a service file, names a class of GitHub's.
- **The app's tests run without GitHub**: `jazygit-app`'s surefire sets `jazygit.without` to `github`, so that
  `Features.ALL` leaves it out, as `--without github` does. GitHub's tests of the whole app are in
  `jazygit-github-app-tests`, which runs with it. `FeaturesTest` checks the features declared, not those running.
- **`scripts/without-github.sh`, `scripts/pom_strip.py` and the `without-github` job go.**

## Consequences

- One build a pull request, not two: a test of a few seconds stands in for a second `verify`.
- The proof stops at what the test checks: something only the copy would catch, as a resource of GitHub's another
  module reads by its name, is not caught. GitHub's classes are still on the classpath of the app's tests, and only
  left out of what the app runs.
- A second forge (Forgejo) gets a test of the same kind.
- ADR 171 gets a line "Amended by ADR 173".
