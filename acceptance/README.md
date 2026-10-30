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
python -m pytest acceptance -n 0                        # one by one, instead of four at a time
```

`pytest.ini` runs four tests at a time with pytest-xdist (`-n 4`): each has its own repository, `HOME` and app, so
they don't see each other. More workers start more JVMs at once, and on a busy machine their waits can time out. To
run more on your own machine, set `PYTEST_ADDOPTS`, for instance in a `.envrc.local` at the root, which direnv reads
and git ignores:

```sh
export PYTEST_ADDOPTS="-n 12"
```

The tests marked `slow` (the scenarios) start first, each on a worker of its own, and the run takes about as long as
the slowest of them: past twelve workers or so, more don't make it quicker. The apps start from a class data archive
of the jar, which the first run after a build makes next to it, and read the status every 100ms instead of every second
(`-Djazygit.refreshInterval`), so that they see sooner what a test wrote.

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

`.github/workflows/build.yml` runs them on every pull request and before every publish, with Python 3.12 and the
dependencies from `requirements.txt`. A job builds the jar without tests, and the acceptance tests run on it while
`./mvnw verify` runs in a job of its own, at the same time. They run on four machines at once, each taking a share
of the tests with `--shard 1/4` to `--shard 4/4`; a share runs four at a time like the whole.

## Writing a test

Set up the repository with `repo(...)` (runs git), `repo.write(...)` and `repo.commit("message", **files)`, then
start the app with `ui = jazygit()` and say what to do with it:

```python
ui.files.commit("Add parser")                 # stages all files first when nothing is staged
ui.branches.rename("feature", "parser")
ui.commits.fixup("Fix typo", keep_message="below")
```

Then check the repository with git: `repo.subjects()`, `repo.branches()`, `repo.current_branch()`,
`repo.find("subject")`, `repo.status()` (each changed path with its `git status --short` code, such as `"M "` for
staged or `"??"` for untracked), `repo.stashes()`, `repo.read("file")`, `repo.worktrees()` (each worktree's path with
the branch it has checked out, `None` on no branch), `repo.locked_worktrees()`, `repo.in_progress()` (`"merge"`,
`"rebase"`, `"cherry-pick"`, `"revert"` or `None`), `repo.conflicts()`, `repo.parents()` and plain `repo(...)`. Assert on git
state rather than on the screen where you can: it survives layout changes.

### Worktrees

`ui.worktrees` is the Worktrees tab of the branches pane, which takes a worktree by the name of its directory. A new
worktree goes where the app offers to put it, `<tmp>/repo-<branch>` next to the repository, unless given a
`location`. An operation that ends with the app showing another worktree returns the page object for that one,
whose `repo` is a `Git` in that worktree:

```python
there = ui.branches.new_worktree("feature", switch=True)   # or ui.worktrees.switch_to("repo-feature")
there.repo.write("three.txt", "three\n")
there.files.commit("Add three")
assert repo.subjects("feature") == ["Add three", ...]
```

### Remotes

Take the `origin` fixture for a test that pushes or pulls: a bare repository, added to `repo` as its remote
`origin`, and a `Git` of its own to check what reached it (`origin.branches()`, `origin.subjects("main")`). The
`clone` fixture clones it for a teammate, whose commits reach `repo` by being pushed:

```python
repo("push", "--set-upstream", "origin", "main")
teammate = clone()
teammate.commit("Add theirs", **{"theirs.txt": "theirs\n"})
teammate("push")
repo("fetch")                                   # as the app does every minute, but not at its start
ui.branches.pull("main")
ui.branches.push("feature", upstream="origin shared-name")
ui.branches.checkout_remote("origin/theirs")
```

`repo.upstream("main")`, `repo.sync("main")` (`"ahead 1, behind 2"`, `"gone"` or empty) and
`repo.remote_branches()` tell how the branches stand to their remotes. The app fetches only once a minute, so a
test fetches itself what it wants the app to know of. Before each operation the page objects also wait until the
branches pane shows each branch's commits to push (↑) and to pull (↓) as git counts them: the app decides on
them, such as whether a push must be forced.

### GitHub

Take the `gh` fixture, before starting the app, for a test that needs GitHub: it puts a fake `gh` (`fake_gh.py`) on
the app's `PATH`, which answers a call that contains a given text with a given answer, and fails any other call as gh
does outside a GitHub repository. The answer is a string, or what `json.dumps` makes of it:

```python
gh.answer(("pullRequests(states: OPEN", [{"data": ...}]))   # the open pull requests of the repository
gh.calls()                                                  # each call so far, as the list of its arguments
```

### Conflicts

An operation that stops on conflicts returns once the app shows it stopped: the status line names what is in
progress, and the files pane the conflicted files. Resolve them with `repo.resolve("file", "content")`, which writes
and stages the file, and go on from the app (M):

```python
ui.branches.merge("feature")                    # or rebase_onto, pull, commits.revert, commits.cherry_pick...
repo.resolve("one.txt", "both\n")
ui.continue_in_progress()                       # or abort_in_progress(), skip_in_progress()
```

A rebase that git stopped keeps its directory in `.git`, with the commit it stopped at in `REBASE_HEAD`: the page
objects take it for running only without that.

### Page objects

`pages.py` has a page object per pane of the left column, `ui.files`, `ui.branches`, `ui.commits` and `ui.stash`,
and `ui.worktrees` for the worktrees tab of the branches pane,
with the operations that pane has keys for, named the way a person says them and taking commits by subject and branches by
name. The keys and dialog titles they take are only there, so a change to the UI is a change to that file. A new
operation goes on the pane it is done from.

Every operation first waits until the app has caught up with the repository (`ui.caught_up()`): no rebase running,
and the panes showing the commits, branches and changed files that git has. A key pressed before then acts on what
the app showed before. It returns once the repository changed and the app shows that, so the next one can follow.

### Keys

Under the page objects, and for what they don't cover, `ui.app` is the app itself:

- `app.press("space", "c", "enter", "ctrl+s", ...)` for keys, `app.type("text")` for text;
- `app.wait_for("text")` to wait for something on screen, and fail showing the screen when it doesn't come;
- `app.wait_in("Files", "text")` to wait for it in one pane of the left column: the same name can show in the
  command log long before the files pane has it;
- `app.focus("Commits")` to tab to a pane, which knows the focused pane by its border colour;
- `app.move_cursor("Commits", "abc1234")` to move the cursor of the focused pane to the row with that word,
  which knows the cursor row by its background, and waits for each key to move it;
- `app.eventually(check, "description")` to wait for the repository to get where it should.

`test_commit.py` spells the keys out like this, for one commit.

Go by the cursor, not by the title of the pane on the right, to know where the cursor is: while the next item
loads, that pane keeps showing the one before. Once the cursor is there, the title naming the item says the app
has taken it as selected.

Every wait fails at once when the app has died, with its exit status and the last of its output, and shows the
screen when it times out.

`test_files.py` (staging and discarding), `test_stash.py`, `test_commits.py`, `test_reset.py`, `test_branches.py`,
`test_remotes.py`, `test_worktrees.py` and `test_conflicts.py` (operations that stop on conflicts, and continuing,
skipping or aborting them) try one operation each, from a repository set up with git; `test_scenario.py` chains them in one session: from an empty repository to a tidied history on three
branches, and a feature worked on with a teammate through origin.
