# Acceptance tests

Black-box tests that run the built jazygit jar in a pseudo terminal (pexpect, with pyte emulating the screen),
press keys, and check the repository afterwards with git. They import nothing from the application, so a code
change can only make them pass by working.

Each test starts from a fresh repository in a temporary directory, with its own `HOME`, no global or system git
config, and fixed author, committer and dates.

## Running

The tests run the jar, they don't build it: build it first, and again after every change you want them to see.

```sh
./mvnw -DskipTests package   # builds jazygit-app/target/jazygit-*-fat.jar
```

Run them from the repository root, so `acceptance` and the jar are found.

### With Nix

The dev shell (`flake.nix`) has Python with pexpect, pyte and pytest:

```sh
nix develop -c python -m pytest acceptance
```

or, inside `nix develop` (or with direnv), just `python -m pytest acceptance`.

### Without Nix

You need Python 3.12 or newer and Java 25. Install the dependencies in a virtualenv:

```sh
python3 -m venv .venv
.venv/bin/pip install -r acceptance/requirements.txt
.venv/bin/python -m pytest acceptance
```

### Choosing what runs

```sh
python -m pytest acceptance -v                          # list each test as it runs
python -m pytest acceptance/test_commit.py              # one file
python -m pytest acceptance -k empty_repository         # tests whose name matches
python -m pytest acceptance --jar path/to/jazygit.jar   # another jar than the newest in jazygit-app/target
```

The java that runs the jar is `JAZYGIT_JAVA` if set, else `$JAVA_HOME/bin/java`, else `java` on the `PATH`. Nix's
JDK starts processes slowly, which makes the app slow to load, so on NixOS a plain JDK is quicker:

```sh
JAZYGIT_JAVA=~/.jdks/openjdk-25.0.1/bin/java python -m pytest acceptance
```

### When a test fails

A test that times out waiting for text prints the screen as the app last drew it, under `--- screen ---`. The
temporary repository a test ran in is kept under pytest's base temp directory (`/tmp/pytest-of-$USER/`, the last
three runs), so you can `cd` into it and look around, or start jazygit on it yourself.

### In CI

`.github/workflows/build.yml` runs them after `./mvnw verify`, on every pull request and before every publish, with
Python 3.12 and the dependencies from `requirements.txt`.

## Writing a test

Set up the repository with `repo(...)` (runs git), `repo.write(...)` and `repo.commit("message", **files)`, start
the app with `jazygit()`, then:

- `app.press("space", "c", "enter", "ctrl+s", ...)` for keys, `app.type("text")` for text;
- `app.wait_for("text")` to wait for something on screen, and fail showing the screen when it doesn't come;
- `app.wait_in("Files", "text")` to wait for it in one pane of the left column: the same name can show in the
  command log long before the files pane has it;
- `app.focus("Commits")` to tab to a pane, which knows the focused pane by its border colour;
- `app.eventually(check, "description")` to wait for the repository to get where it should.

`steps.py` has the moves that take more than a key: `select_commit(app, repo, "subject")` and
`select_branch(app, repo, "name")` (they wait for the diff pane to name it), `type_over(app, "text")` to replace the
text of a field, and `stage_and_commit(app, repo, "summary", **files)`. `repo.subjects()`, `repo.branches()`,
`repo.find("subject")` and friends read the result back.

Every wait fails at once when the app has died, with its exit status and the last of its output.

Assert on git state rather than on the screen where you can: it survives layout changes. Wait for the app to show
the result of one step before taking the next: a key pressed before it has caught up acts on what it showed before.

`test_commits.py` and `test_branches.py` try one operation each, from a repository set up with git;
`test_scenario.py` chains them in one session, from an empty repository to a tidied history on three branches.
