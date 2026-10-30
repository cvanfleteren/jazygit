"""The terminal's window is titled after the repository shown, also after a switch and after the editor."""


def titled(ui, title: str):
    ui.app.eventually(lambda: ui.app.title() == title, f"the window to be titled {title!r}")


def test_the_window_is_titled_after_the_worktree_switched_to(repo, tmp_path, jazygit):
    repo.commit("Add one", **{"one.txt": "one\n"})
    repo("branch", "feature")
    repo("worktree", "add", str(tmp_path / "repo-feature"), "feature")
    ui = jazygit()
    titled(ui, "jazygit - repo")

    ui.worktrees.switch_to("repo-feature")

    titled(ui, "jazygit - repo(repo-feature)")


def test_the_window_is_titled_again_once_the_editor_that_titled_it_is_closed(repo, tmp_path, env, jazygit):
    repo.commit("Add one", **{"one.txt": "one\n"})
    repo.write("one.txt", "one\nchanged\n")
    # The editor titles the window, and stays open until the test saw that, at most 30 seconds.
    closing = tmp_path / "close-the-editor"
    editor = tmp_path / "editor"
    editor.write_text(
        "#!/bin/sh\n"
        "printf '\\033]2;the editor\\007'\n"
        "i=0\n"
        f"while [ ! -e '{closing}' ] && [ $i -lt 600 ]; do sleep 0.05; i=$((i + 1)); done\n")
    editor.chmod(0o755)
    env["GIT_EDITOR"] = str(editor)
    ui = jazygit()
    titled(ui, "jazygit - repo")

    ui.files.edit("one.txt")
    titled(ui, "the editor")
    closing.touch()

    titled(ui, "jazygit - repo")
