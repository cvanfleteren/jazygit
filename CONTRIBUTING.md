# Contributing to jazygit

## Reporting a bug

Run `jazygit --debug` and do what goes wrong again. On exit, jazygit prints where its debug log is: the git and gh
commands it ran and what it handled, which helps to find what went wrong. Look it over before attaching it, as the
commands name your paths, branches and commit messages.

## Building

To build from source:

- **JDK 25** and **Maven**: `mvn verify` builds and tests all modules, and `./run.sh` builds the jar when needed and
  runs it. `mvn test-compile pitest:mutationCoverage` runs the [PIT](https://pitest.org) mutation tests, which take
  minutes and are no part of `verify`; the reports land in each module's `target/pit-reports/index.html`. Add
  `-pl jazygit-git` for one module, `-Dpitest.threads=16` for more threads, or `-DwithHistory` to only analyse what
  changed since the last run. The [JMH](https://github.com/openjdk/jmh) benchmarks, of the rich view of a diff for
  now, are no part of the build either: `./mvnw -Pbench -pl jazygit-benchmarks -am package -DskipTests` builds them,
  and `java -jar jazygit-benchmarks/target/benchmarks.jar` runs them (`-p shape=CODE` for one kind of diff,
  `-prof gc` for what they allocate).
- **Python 3.12 or newer**, for the acceptance tests that drive the built jar in a terminal; see
  [acceptance/README.md](acceptance/README.md).
- **GraalVM 25**, for the native image, plus a C compiler and the static zlib it links against. The Nix dev shell
  (`flake.nix`) provides GraalVM and Maven.
