"""Making a stack of pull requests on GitHub from the stack menu of a branch (ADR 73)."""

from test_pull_request_files import DATE, LISTED


def node(repository: str, number: int, branch: str, base: str, stack: dict = None, position: int = None) -> dict:
    """Open pull request number of repository, from branch into base, at position in stack if given."""
    return {
        "number": number, "title": f"Add {branch}", "url": f"https://github.com/{repository}/pull/{number}",
        "updatedAt": DATE, "isDraft": False, "isCrossRepository": False, "headRefName": branch, "baseRefName": base,
        "author": {"login": "agent"}, "repository": {"nameWithOwner": repository},
        "reviewDecision": None,
        "stackEntry": {"position": position} if stack else None, "stack": stack,
        "commits": {"nodes": [{"commit": {"statusCheckRollup": None, "checkSuites": {"nodes": []}}}]},
    }


def listed(repository: str, nodes: list) -> list:
    """The open pull requests of repository, one page of them."""
    return [{"data": {"viewer": {"login": "me"}, "repository": {
        "nameWithOwner": repository,
        "latest": {"nodes": [{"number": nodes[0]["number"]}]},
        "pullRequests": {
            "totalCount": len(nodes),
            "pageInfo": {"hasNextPage": False, "endCursor": None},
            "nodes": nodes,
        }}}}]


def test_the_branches_an_agent_built_on_each_other_are_made_a_stack_from_the_menu(repo, origin, gh, jazygit):
    repo.commit("Initial commit", **{"README.md": "# Repo\n"})
    repo("push", "--quiet", "origin", "main")
    repo("checkout", "--quiet", "-b", "feat/a")
    repo.commit("Add a", **{"a.txt": "a\n"})
    repo("push", "--quiet", "origin", "feat/a")
    repo("checkout", "--quiet", "-b", "feat/b")
    repo.commit("Add b", **{"b.txt": "b\n"})
    repo("push", "--quiet", "origin", "feat/b")
    repository = f"{origin.path.parent.name}/origin"
    # As an agent opens them: #2 on the branch of #1.
    gh.answer(
        (LISTED, listed(repository, [node(repository, 2, "feat/b", "feat/a"), node(repository, 1, "feat/a", "main")])),
        ("/stacks", {"number": 12}))
    ui = jazygit()
    ui.branches.select("feat/b")
    ui.app.eventually(lambda: "#2" in ui.app.text(), "the pull requests read")

    ui.app.press("s")
    ui.app.wait_for("(m) make a stack…")
    ui.app.press("m")
    ui.app.wait_for("Make a stack → main")
    assert "┗ feat/a  #1" in ui.app.text()
    assert "┏ feat/b  #2  ← this branch" in ui.app.text()
    ui.app.press("enter")

    ui.app.eventually(
        lambda: ["api", "--method", "POST", "repos/{owner}/{repo}/stacks",
                 "-F", "pull_requests[]=1", "-F", "pull_requests[]=2"] in gh.calls(),
        "the stack made on GitHub")
    assert not any(call[:2] == ["pr", "edit"] for call in gh.calls()), "no base changed"

    # Once GitHub lists them as stacked, the branches show as one stack, without starting again.
    stack = {"number": 12, "size": 2, "entries": {"nodes": [
        {"position": 1, "pullRequest": {"number": 1, "state": "OPEN", "headRefName": "feat/a", "baseRefName": "main"}},
        {"position": 2, "pullRequest": {"number": 2, "state": "OPEN", "headRefName": "feat/b", "baseRefName": "feat/a"}},
    ]}}
    gh.answer((LISTED, listed(repository, [node(repository, 2, "feat/b", "feat/a", stack, 2),
                                           node(repository, 1, "feat/a", "main", stack, 1)])))
    ui.app.eventually(lambda: "stack #12 → main" in ui.app.text(), "the stack shown")
