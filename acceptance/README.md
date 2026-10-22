# Acceptance tests

Black-box tests that run the built jazygit jar in a pseudo terminal (pexpect, with pyte emulating the screen),
press keys, and check the repository afterwards with git. They import nothing from the application, so a code
change can only make them pass by working.

Each test starts from a fresh repository in a temporary directory, with its own `HOME`, no global or system git
config, and fixed author, committer and dates.

## Running

```sh
./mvnw -DskipTests package                 # builds jazygit-app/target/jazygit-*-fat.jar
nix develop -c python -m pytest acceptance  # or: pip install -r acceptance/requirements.txt
```

- `--jar PATH` tests another jar than the newest in `jazygit-app/target`.
- `JAZYGIT_JAVA` picks the java that runs it (else `$JAVA_HOME/bin/java`, else `java`).

## Writing a test

Set up the repository with `repo(...)` (runs git) and `repo.write(...)`, start the app with `jazygit()`, then:

- `app.press("space", "c", "enter", "ctrl+s", ...)` for keys, `app.type("text")` for text;
- `app.wait_for("text")` to wait for something on screen, and fail showing the screen when it doesn't come;
- `eventually(check, "description")` to wait for the repository to get where it should.

Assert on git state rather than on the screen where you can: it survives layout changes.
