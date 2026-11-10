"""Approving a pull request, or asking for changes, from its dialog (ADR 100)."""

from test_pull_request_files import pull_request

# What the call that submits a review contains.
REVIEW = "addPullRequestReview("


def reviews(gh) -> list:
    """The calls that submitted a review, each joined into one string."""
    return [" ".join(call) for call in gh.calls() if REVIEW in " ".join(call)]


def review_from_the_dialog(ui):
    ui.branches.select("feature")
    ui.app.press("o")
    # The action shows dimmed until the details are read, and does nothing then: wait for them.
    ui.app.wait_for("in 2 files, 2 commits")
    ui.app.press("a")
    ui.app.wait_for("(•) Approve   ( ) Request changes")


def test_a_pull_request_is_approved_without_a_text(repo, origin, clone, gh, jazygit):
    pull_request(repo, origin, clone, gh)
    gh.answer((REVIEW, {"data": {"addPullRequestReview": {"pullRequestReview": {"state": "APPROVED"}}}}))
    ui = jazygit()
    review_from_the_dialog(ui)

    ui.app.press("ctrl+s")

    ui.app.eventually(lambda: reviews(gh), "the review submitted")
    assert "pullRequest=PR_1" in reviews(gh)[0]
    assert "event=APPROVE" in reviews(gh)[0]
    assert reviews(gh)[0].endswith("body=")
    ui.app.eventually(lambda: "Review pull request #1" not in ui.app.text(), "the popup closed")
    ui.app.wait_for("(a) Approve or request changes")


def test_asking_for_changes_needs_a_reason(repo, origin, clone, gh, jazygit):
    pull_request(repo, origin, clone, gh)
    gh.answer((REVIEW, {"data": {"addPullRequestReview": {"pullRequestReview": {"state": "CHANGES_REQUESTED"}}}}))
    ui = jazygit()
    review_from_the_dialog(ui)

    # From approving to asking for changes, with the arrow, as through radio buttons.
    ui.app.press("right")
    ui.app.wait_for("( ) Approve   (•) Request changes")
    ui.app.press("ctrl+s")
    ui.app.wait_for("Say what should change")
    assert reviews(gh) == []

    # On to the text.
    ui.app.press("tab")
    ui.app.type("Rename b")
    ui.app.press("ctrl+s")

    ui.app.eventually(lambda: reviews(gh), "the review submitted")
    assert "event=REQUEST_CHANGES" in reviews(gh)[0]
    assert reviews(gh)[0].endswith("body=Rename b")
    ui.app.eventually(lambda: "Review pull request #1" not in ui.app.text(), "the popup closed")
