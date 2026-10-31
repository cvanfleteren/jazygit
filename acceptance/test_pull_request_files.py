"""The files a pull request changes, as a tree in the files pane, read with git (ADR 55)."""

DATE = "2026-01-01T00:00:00Z"
# What the calls that read pull requests contain.
LISTED = "pullRequests(states: OPEN"
DETAILS = "nameWithOwner url pullRequest(number: $number) { number title"


def listed(repository: str, branch: str) -> dict:
    """The open pull requests of repository, one page of them: number 1, of branch."""
    return [{"data": {"viewer": {"login": "me"}, "repository": {
        "nameWithOwner": repository,
        "latest": {"nodes": [{"number": 1}]},
        "pullRequests": {
            "totalCount": 1,
            "pageInfo": {"hasNextPage": False, "endCursor": None},
            "nodes": [{
                "number": 1, "title": "Add b", "url": f"https://github.com/{repository}/pull/1", "updatedAt": DATE,
                "isDraft": False, "isCrossRepository": False, "headRefName": branch,
                "author": {"login": "teammate"}, "repository": {"nameWithOwner": repository},
                "reviewDecision": None, "stackEntry": None, "stack": None,
                "commits": {"nodes": [{"commit": {"statusCheckRollup": None, "checkSuites": {"nodes": []}}}]},
            }],
        }}}}]


def details(repository: str, branch: str, head: str, base: str) -> dict:
    """Pull request 1 of repository, from branch at commit head into main at commit base."""
    return {"data": {"repository": {"nameWithOwner": repository, "url": f"https://github.com/{repository}", "pullRequest": {
        "number": 1, "title": "Add b", "body": "", "author": {"login": "teammate"}, "state": "OPEN", "isDraft": False,
        "headRefName": branch, "baseRefName": "main", "headRefOid": head, "baseRefOid": base,
        "baseRef": {"compare": {"behindBy": 0}},
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
        (LISTED, listed(repository, "feature")),
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
        return {"id": f"PRRT_{body}", "path": "b.txt", "diffSide": "RIGHT", "line": 1, "startLine": None, "startDiffSide": None,
                "originalLine": 1, "isResolved": resolved, "isOutdated": False, "subjectType": "LINE",
                "viewerCanReply": True, "viewerCanResolve": not resolved, "viewerCanUnresolve": resolved,
                "comments": {"totalCount": 1, "nodes": [
                    {"id": f"IC_{body}", "fullDatabaseId": str(len(body)), "author": {"login": "reviewer"}, "createdAt": DATE, "body": body, "state": "SUBMITTED",
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

    # Space opens the resolved thread, the second (ADR 61).
    ui.app.press("enter")
    ui.app.press("n")
    ui.app.press("n")
    ui.app.press("space")

    ui.app.wait_for("┃  Settled already")
    assert "✓ resolved" not in ui.app.text()
    assert any(THREADS in " ".join(call) for call in gh.calls())


def test_only_the_files_of_a_pull_request_with_remarks_can_be_listed(repo, origin, clone, gh, jazygit):
    pull_request(repo, origin, clone, gh)
    head = origin("rev-parse", "refs/pull/1/head").strip()
    gh.answer((THREADS, threads(head, repo("rev-parse", "main").strip())))
    ui = jazygit()
    ui.branches.select("feature")
    ui.app.press("o")
    # The action shows dimmed until the details are read, and does nothing then: wait for them.
    ui.app.wait_for("in 2 files, 2 commits")
    ui.app.press("f")
    ui.app.wait_in("Files", "b.txt 💬1 ✓1")
    assert "a.txt" in ui.app.pane_text("Files")

    ui.app.press("c")

    ui.app.wait_for("· unresolved remarks")
    ui.app.eventually(lambda: "a.txt" not in ui.app.pane_text("Files"), "a.txt left out")
    assert "b.txt 💬1 ✓1" in ui.app.pane_text("Files")

    ui.app.press("c")

    ui.app.wait_for("· with remarks")
    assert "a.txt" not in ui.app.pane_text("Files")

    ui.app.press("c")

    ui.app.wait_in("Files", "a.txt")
    assert "· with remarks" not in ui.app.text()


# A thread is started and replied to with REST, which publishes the comment at once.
REPLY = "/replies"
START = "pulls/1/comments"
RESOLVE = "resolveReviewThread("


def calls_with(gh, text: str, without: str = "\0") -> list:
    """The calls of gh whose arguments contain text, and not without."""
    return [call for call in gh.calls() if text in " ".join(call) and without not in " ".join(call)]


def reversed_at(app, text: str) -> bool:
    """Whether the first character of where text first shows on the screen is drawn reversed, as the rule of the
    thread the cursor is on while the diff has the focus."""
    app.pump()
    for y, line in enumerate(app.screen.display):
        x = line.find(text)
        if x >= 0:
            return app.screen.buffer[y][x].reverse
    return False


def replied(head: str, base: str) -> list:
    """The review threads of pull request 1: one open on line 1 of b.txt, with a reply and a hidden comment."""
    def comment(body: str, hidden: bool) -> dict:
        return {"id": f"IC_{body}", "fullDatabaseId": str(len(body)), "author": {"login": "reviewer"},
                "createdAt": DATE, "body": body, "state": "SUBMITTED",
                "isMinimized": hidden, "minimizedReason": "SPAM" if hidden else None}
    return [{"data": {"repository": {"pullRequest": {
        "state": "OPEN", "headRefOid": head, "baseRefOid": base,
        "reviewThreads": {"pageInfo": {"hasNextPage": False, "endCursor": None}, "nodes": [{
            "id": "PRRT_1", "path": "b.txt", "diffSide": "RIGHT", "line": 1, "startLine": None,
            "startDiffSide": None, "originalLine": 1, "isResolved": False, "isOutdated": False, "subjectType": "LINE",
            "viewerCanReply": True, "viewerCanResolve": True, "viewerCanUnresolve": False,
            "comments": {"totalCount": 3, "nodes": [
                comment("Why b?", False), comment("Because", False), comment("Buy now", True)]}}]},
    }}}}]


def test_a_thread_and_each_of_its_comments_are_collapsed_and_opened_with_space(repo, origin, clone, gh, jazygit):
    pull_request(repo, origin, clone, gh)
    head = origin("rev-parse", "refs/pull/1/head").strip()
    gh.answer((THREADS, replied(head, repo("rev-parse", "main").strip())))
    ui = jazygit()
    ui.branches.select("feature")
    ui.app.press("o")
    # The action shows dimmed until the details are read, and does nothing then: wait for them.
    ui.app.wait_for("in 2 files, 2 commits")
    ui.app.press("f")
    ui.app.wait_in("Files", "💬1")
    ui.app.move_cursor("Files", "b.txt")
    ui.app.press("ctrl+d")
    ui.app.wait_for("┃  Because")
    # A hidden comment is collapsed to its header.
    assert "· hidden · spam" in ui.app.text()
    assert "Buy now" not in ui.app.text()
    ui.app.press("enter")

    # An unresolved thread collapses to one line, and opens again.
    ui.app.press("n")
    ui.app.eventually(lambda: reversed_at(ui.app, "┏━ reviewer"), "the cursor on the thread")
    ui.app.press("space")
    ui.app.wait_for("▸ 3 remarks · reviewer: Why b?")
    assert "Because" not in ui.app.text()
    ui.app.press("space")
    ui.app.wait_for("┃  Because")

    # Each comment but the first is a stop: the reply collapses on its own, the hidden comment opens.
    ui.app.press("j")
    ui.app.press("space")
    ui.app.wait_for("· Because")
    assert "┃  Because" not in ui.app.text()
    assert "┃  Why b?" in ui.app.text()
    ui.app.press("j")
    ui.app.press("space")
    ui.app.wait_for("┃  Buy now")


def test_the_remarks_of_a_pull_request_are_resolved_replied_to_and_started_from_the_cursor_of_its_diff(
        repo, origin, clone, gh, jazygit):
    pull_request(repo, origin, clone, gh)
    head = origin("rev-parse", "refs/pull/1/head").strip()
    gh.answer(
        (THREADS, threads(head, repo("rev-parse", "main").strip())),
        (REPLY, {"id": 7, "node_id": "IC_reply"}),
        (START, {"id": 8, "node_id": "IC_new"}),
        # Slow enough for a second x to come while it is sent.
        (RESOLVE, {"data": {"resolveReviewThread": {"thread": {"id": "PRRT_Why **b**?"}}}}, 2))
    ui = jazygit()
    ui.branches.select("feature")
    ui.app.press("o")
    # The action shows dimmed until the details are read, and does nothing then: wait for them.
    ui.app.wait_for("in 2 files, 2 commits")
    ui.app.press("f")
    ui.app.wait_in("Files", "💬1")
    ui.app.move_cursor("Files", "b.txt")
    ui.app.press("ctrl+d")
    ui.app.wait_for("Why b?")
    ui.app.press("enter")

    # To the first thread, resolved; a second x while it is sent sends nothing.
    ui.app.press("n")
    ui.app.eventually(lambda: reversed_at(ui.app, "┏━ reviewer"), "the cursor on the thread")
    ui.app.press("x")
    ui.app.press("x")

    ui.app.wait_for("still being sent")
    ui.app.press("escape")
    ui.app.eventually(lambda: "sending…" not in ui.app.text(), "the thread read again")
    ui.app.eventually(lambda: reversed_at(ui.app, "┏━ reviewer"), "the focus back on the thread")
    assert len(calls_with(gh, RESOLVE)) == 1
    assert "thread=PRRT_Why **b**?" in calls_with(gh, RESOLVE)[0]

    ui.app.press("r")
    ui.app.wait_for("Reply on b.txt")
    ui.app.type("Because")
    ui.app.press("ctrl+s")

    ui.app.eventually(lambda: calls_with(gh, REPLY), "the reply sent")
    # To the first comment of the thread, "Why **b**?", whose REST id the fake made its length.
    assert "repos/{owner}/{repo}/pulls/1/comments/10/replies" in calls_with(gh, REPLY)[0]
    assert "body=Because" in calls_with(gh, REPLY)[0]
    ui.app.eventually(lambda: "Reply on b.txt" not in ui.app.text(), "the popup closed")
    ui.app.eventually(lambda: reversed_at(ui.app, "┏━ reviewer"), "the focus back on the thread")

    # From the thread up to its line.
    ui.app.press("k")
    ui.app.press("c")
    ui.app.wait_for("Comment on b.txt · line 1")
    ui.app.type("Hm")
    ui.app.press("ctrl+s")

    ui.app.eventually(lambda: calls_with(gh, START, REPLY), "the thread started")
    started = calls_with(gh, START, REPLY)[0]
    assert "line=1" in started and "side=RIGHT" in started and "body=Hm" in started and "path=b.txt" in started
    assert f"commit_id={head}" in started
