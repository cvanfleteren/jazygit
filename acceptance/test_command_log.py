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
    ui.app.wait_gone("git add -- file1.txt")
    shrunk = log_top(ui)

    ui.app.press("@")

    # It grows to show all six, the newest still at its bottom.
    ui.app.wait_for("git add -- file1.txt")
    ui.app.eventually(lambda: log_top(ui) < shrunk, "the log to grow")
    assert "git add -- file6.txt" in ui.app.text()
    # The panes keep their colours: none of them has the focus.
    ui.app.eventually(lambda: ui.app.focused_pane() is None, "no pane to have the focus")

    ui.app.press("escape")

    ui.app.eventually(lambda: ui.app.focused_pane() == "Files", "the focus back on the files")
    ui.app.eventually(lambda: log_top(ui) == shrunk, "the log as tall as before")
    ui.app.wait_gone("git add -- file1.txt")
