"""@, or a click, gives the command log the focus: it grows to show all it logged, to scroll through, and Escape gives
the focus back (ADR 129)."""


def log_top(ui) -> int:
    """The row the top border of the command log is drawn on, which goes up as the log grows."""
    return next(y for y, line in enumerate(ui.app.text().splitlines()) if "╭Command log" in line)


def test_at_shows_all_the_command_log_and_escape_gives_the_focus_back(repo, jazygit):
    repo.commit("Add base", **{"base.txt": "base\n"})
    for n in range(1, 7):
        repo.write(f"file{n}.txt", f"{n}\n")
    ui = jazygit()
    for n in range(1, 7):
        ui.files.stage(f"file{n}.txt")
    # Six operations of two lines each: without the focus, the newest eight show.
    ui.app.wait_for("git add -- file6.txt")
    assert "git add -- file1.txt" not in ui.app.text()
    shrunk = log_top(ui)

    ui.app.press("@")

    ui.app.wait_for("git add -- file1.txt")
    assert log_top(ui) < shrunk
    # The panes keep their colours: none of them has the focus.
    assert ui.app.focused_pane() is None

    ui.app.press("escape")

    ui.app.eventually(lambda: ui.app.focused_pane() == "Files", "the focus back on the files")
    ui.app.eventually(lambda: log_top(ui) == shrunk, "the log as tall as before")
    assert "git add -- file1.txt" not in ui.app.text()
