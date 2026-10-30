"""Editing the title, body, base and draft of a pull request from its dialog (ADR 59)."""

from test_pull_request_files import pull_request


def test_an_edited_title_is_saved_with_gh_and_nothing_else_is(repo, origin, clone, gh, jazygit):
    pull_request(repo, origin, clone, gh)
    gh.answer(("pr edit", ""))
    ui = jazygit()
    ui.branches.select("feature")

    ui.app.press("o")
    # The action does nothing until the details are read: wait for them.
    ui.app.wait_for("in 2 files, 2 commits")
    ui.app.press("e")
    ui.app.wait_for("Edit pull request #1: 'feature'")
    ui.app.type(" and c")
    ui.app.press("ctrl+s")

    ui.app.eventually(
        lambda: ["pr", "edit", "1", "--title", "Add b and c"] in gh.calls(), "the title saved with gh")
    ui.app.eventually(lambda: "Edit pull request #1" not in ui.app.text(), "the edit popup closed")
