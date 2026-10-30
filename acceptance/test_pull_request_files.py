"""The files a pull request changes, as a tree in the files pane, read with git (ADR 55)."""

DATE = "2026-01-01T00:00:00Z"
# What the calls that read pull requests contain.
LISTED = "pullRequests(states: OPEN"
DETAILS = "nameWithOwner url pullRequest(number: $number) { number title"


def listed(branch: str) -> dict:
    """The open pull requests of the repository: number 1, of branch."""
    return [{"data": {"repository": {"pullRequests": {
        "pageInfo": {"hasNextPage": False, "endCursor": None},
        "nodes": [{
            "number": 1, "headRefName": branch, "isDraft": False, "isCrossRepository": False,
            "reviewDecision": None, "stackEntry": None, "stack": None,
            "commits": {"nodes": [{"commit": {"statusCheckRollup": None, "checkSuites": {"nodes": []}}}]},
        }],
    }}}}]


def details(repository: str, branch: str, head: str, base: str) -> dict:
    """Pull request 1 of repository, from branch at commit head into main at commit base."""
    return {"data": {"repository": {"nameWithOwner": repository, "url": f"https://github.com/{repository}", "pullRequest": {
        "number": 1, "title": "Add b", "author": {"login": "teammate"}, "state": "OPEN", "isDraft": False,
        "headRefName": branch, "baseRefName": "main", "headRefOid": head, "baseRefOid": base,
        "stackEntry": None, "stack": None, "createdAt": DATE, "additions": 2, "deletions": 0, "changedFiles": 2,
        "mergeable": "MERGEABLE", "reviewDecision": None, "comments": {"totalCount": 0},
        "labels": {"nodes": []}, "latestReviews": {"nodes": []}, "reviewRequests": {"nodes": []},
        "timelineItems": {"nodes": []},
        "commits": {"totalCount": 2, "nodes": [
            {"commit": {"committedDate": DATE, "statusCheckRollup": None, "checkSuites": {"nodes": []}}}]},
        "reviewThreads": {"totalCount": 0, "nodes": []},
    }}}}


def pull_request(repo, origin, clone, gh) -> str:
    """Branch feature of the repository with a pull request into main, whose head a teammate pushed on: that commit
    is only in refs/pull/1/head of origin, which the repository does not fetch. Its repository, as GitHub names it."""
    repo.commit("Initial commit", **{"README.md": "# Repo\n"})
    repo("push", "--quiet", "origin", "main")
    repo("checkout", "--quiet", "-b", "feature")
    repo.commit("Add a", **{"a.txt": "a\n"})
    repo("push", "--quiet", "origin", "feature")
    teammate = clone()
    teammate("checkout", "--quiet", "feature")
    teammate.commit("Add b", **{"b.txt": "b\n"})
    teammate("push", "--quiet", "origin", "HEAD:refs/pull/1/head")
    repository = f"{origin.path.parent.name}/origin"
    gh.answer(
        (LISTED, listed("feature")),
        (DETAILS, details(repository, "feature", teammate("rev-parse", "HEAD").strip(),
                          repo("rev-parse", "main").strip())))
    return repository


def test_the_files_changed_of_a_pull_request_show_in_the_files_pane_fetched_when_missing(
        repo, origin, clone, gh, jazygit):
    pull_request(repo, origin, clone, gh)
    ui = jazygit()
    ui.branches.select("feature")

    ui.app.press("o")
    # The action shows dimmed until the details are read, and does nothing then: wait for them.
    ui.app.wait_for("in 2 files, 2 commits")
    ui.app.press("f")

    ui.app.wait_in("Files", "b.txt")
    assert "Files of #1" in ui.app.text()
    assert "README.md" not in ui.app.pane_text("Files")
    assert ui.app.focused_pane() == "Files"
    ui.app.move_cursor("Files", "b.txt")
    ui.app.wait_for("#1 · b.txt")
    assert "+b" in ui.app.text()
    # Fetched without a ref.
    assert "refs/pull" not in repo("for-each-ref")

    ui.app.press("escape")

    ui.app.eventually(lambda: ui.app.focused_pane() == "Branches", "the focus on the branches")


THREADS = "reviewThreads(first: 100"


def threads(head: str, base: str) -> list:
    """The review threads of pull request 1: one open on line 1 of b.txt, one resolved on the same line."""
    def thread(resolved: bool, body: str) -> dict:
        return {"path": "b.txt", "diffSide": "RIGHT", "line": 1, "startLine": None, "startDiffSide": None,
                "originalLine": 1, "isResolved": resolved, "isOutdated": False, "subjectType": "LINE",
                "comments": {"totalCount": 1, "nodes": [
                    {"author": {"login": "reviewer"}, "createdAt": DATE, "body": body, "state": "SUBMITTED",
                     "isMinimized": False, "minimizedReason": None}]}}
    return [{"data": {"repository": {"pullRequest": {
        "state": "OPEN", "headRefOid": head, "baseRefOid": base,
        "reviewThreads": {"pageInfo": {"hasNextPage": False, "endCursor": None},
                          "nodes": [thread(False, "Why **b**?"), thread(True, "Settled already")]},
    }}}}]


def test_the_review_remarks_of_a_pull_request_show_next_to_their_lines(repo, origin, clone, gh, jazygit):
    pull_request(repo, origin, clone, gh)
    head = origin("rev-parse", "refs/pull/1/head").strip()
    gh.answer((THREADS, threads(head, repo("rev-parse", "main").strip())))
    ui = jazygit()
    ui.branches.select("feature")
    ui.app.press("o")
    # The action shows dimmed until the details are read, and does nothing then: wait for them.
    ui.app.wait_for("in 2 files, 2 commits")
    ui.app.press("f")
    ui.app.wait_in("Files", "💬1")
    ui.app.move_cursor("Files", "b.txt")

    # The plain view counts them.
    ui.app.wait_for("2 review remarks, Ctrl+D shows them")

    ui.app.press("ctrl+d")

    ui.app.wait_for("Why b?")
    assert "● +b" in ui.app.text()
    assert "✓ resolved · reviewer: Settled already" in ui.app.text()

    ui.app.press("t")

    ui.app.wait_for("┃ Settled already")
    assert "✓ resolved" not in ui.app.text()
    assert any(THREADS in " ".join(call) for call in gh.calls())
