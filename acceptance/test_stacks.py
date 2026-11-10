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


def stacked(repository: str, numbers_and_branches: list) -> dict:
    """Stack 12 of repository, on main, of the pull requests given as (number, branch), the bottom one first."""
    nodes = []
    base = "main"
    for position, (number, branch) in enumerate(numbers_and_branches, start=1):
        nodes.append({"position": position, "pullRequest": {
            "number": number, "state": "OPEN", "headRefName": branch, "baseRefName": base}})
        base = branch
    return {"number": 12, "size": len(nodes), "entries": {"nodes": nodes}}


def test_a_stack_whose_branch_is_not_on_the_one_below_is_rebased_and_pushed_from_the_menu(
        repo, origin, gh, jazygit):
    repo.commit("Initial commit", **{"README.md": "# Repo\n"})
    repo("push", "--quiet", "origin", "main")
    repo("checkout", "--quiet", "-b", "feat/c")
    repo.commit("Add c", **{"c.txt": "c\n"})
    repo("push", "--quiet", "-u", "origin", "feat/c")
    # feat/d was made from main, not from feat/c, then linked on it: GitHub would not merge the stack.
    repo("checkout", "--quiet", "-b", "feat/d", "main")
    repo.commit("Add d", **{"d.txt": "d\n"})
    repo("push", "--quiet", "-u", "origin", "feat/d")
    repo("checkout", "--quiet", "main")
    repository = f"{origin.path.parent.name}/origin"
    stack = stacked(repository, [(1, "feat/c"), (2, "feat/d")])
    gh.answer((LISTED, listed(repository, [node(repository, 2, "feat/d", "feat/c", stack, 2),
                                           node(repository, 1, "feat/c", "main", stack, 1)])))
    ui = jazygit()
    ui.app.eventually(lambda: "stack #12 → main" in ui.app.text(), "the stack shown")
    ui.branches.select("feat/d")

    ui.app.press("s")
    ui.app.wait_for("(r) rebase the stack")
    ui.app.press("r")
    ui.app.wait_for("Rebase stack #12")
    # Wrapped at the width of the dialog.
    assert "Puts feat/c, feat/d each on the one" in ui.app.text()
    assert "origin/main" in ui.app.text()
    assert "(enter) rebase and push   (l) rebase only" in ui.app.text()
    ui.app.press("enter")

    # Rebased and pushed at once, without asking again.
    # git push updates origin before it updates origin/feat/d here, so wait for both.
    ui.app.eventually(
        lambda: origin("log", "--format=%s", "feat/d").splitlines() == ["Add d", "Add c", "Initial commit"]
        and repo("rev-parse", "origin/feat/d") == repo("rev-parse", "feat/d"),
        "the stack rebased and pushed")
    assert repo("log", "--format=%s", "feat/d").splitlines() == ["Add d", "Add c", "Initial commit"]
    assert repo("rev-parse", "--abbrev-ref", "HEAD") == "main", "the branch that was is checked out again"
    assert "Push stack" not in ui.app.text()


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


def two_pushed_branches(repo, origin) -> str:
    """feat/a on main and feat/b on feat/a, pushed; the repository on GitHub."""
    repo.commit("Initial commit", **{"README.md": "# Repo\n"})
    repo("push", "--quiet", "origin", "main")
    repo("checkout", "--quiet", "-b", "feat/a")
    repo.commit("Add a", **{"a.txt": "a\n"})
    repo("push", "--quiet", "-u", "origin", "feat/a")
    repo("checkout", "--quiet", "-b", "feat/b")
    repo.commit("Add b", **{"b.txt": "b\n"})
    repo("push", "--quiet", "-u", "origin", "feat/b")
    return f"{origin.path.parent.name}/origin"


def test_a_stack_is_unstacked_from_the_menu_after_saying_what_stays(repo, origin, gh, jazygit):
    repository = two_pushed_branches(repo, origin)
    stack = stacked(repository, [(1, "feat/a"), (2, "feat/b")])
    gh.answer((LISTED, listed(repository, [node(repository, 2, "feat/b", "feat/a", stack, 2),
                                           node(repository, 1, "feat/a", "main", stack, 1)])),
              ("/unstack", ""))
    ui = jazygit()
    ui.app.eventually(lambda: "stack #12 → main" in ui.app.text(), "the stack shown")
    ui.branches.select("feat/b")

    ui.app.press("s")
    ui.app.wait_for("(u) unstack")
    ui.app.press("u")
    ui.app.wait_for("Unstack #12")
    assert "2 pull requests stay open, unlinked." in ui.app.text()
    ui.app.press("enter")

    ui.app.eventually(
        lambda: ["api", "--method", "POST", "repos/{owner}/{repo}/stacks/12/unstack"] in gh.calls(),
        "the stack taken apart on GitHub")
    # Once GitHub lists them unlinked, the stack shows no more.
    gh.answer((LISTED, listed(repository, [node(repository, 2, "feat/b", "feat/a"),
                                           node(repository, 1, "feat/a", "main")])))
    ui.app.eventually(lambda: "stack #12" not in ui.app.text(), "the stack no longer shown")


def test_a_branch_built_on_the_top_of_a_stack_is_added_to_it_from_the_menu(repo, origin, gh, jazygit):
    repository = two_pushed_branches(repo, origin)
    repo("checkout", "--quiet", "-b", "feat/c")
    repo.commit("Add c", **{"c.txt": "c\n"})
    repo("push", "--quiet", "-u", "origin", "feat/c")
    stack = stacked(repository, [(1, "feat/a"), (2, "feat/b")])
    gh.answer((LISTED, listed(repository, [node(repository, 3, "feat/c", "feat/b"),
                                           node(repository, 2, "feat/b", "feat/a", stack, 2),
                                           node(repository, 1, "feat/a", "main", stack, 1)])),
              ("/stacks/12/add", {"number": 12}))
    # Checked out, the stack is listed first: feat/b's own row comes before the "on feat/b" of feat/c.
    repo("checkout", "--quiet", "feat/b")
    ui = jazygit()
    ui.app.eventually(lambda: "stack #12 → main" in ui.app.text(), "the stack shown")
    ui.branches.select("feat/b")

    ui.app.press("s")
    ui.app.wait_for("(a) add to the stack…")
    ui.app.press("a")
    ui.app.wait_for("(t) top")
    # The top of the stack is the bottom; the branch built on it is chosen as the new top.
    ui.app.press("j")
    ui.app.press("t")
    ui.app.press("enter")

    ui.app.eventually(
        lambda: ["api", "--method", "POST", "repos/{owner}/{repo}/stacks/12/add",
                 "-F", "pull_requests[]=3"] in gh.calls(),
        "the branch added on GitHub")
    assert not any(call[:2] == ["pr", "edit"] for call in gh.calls()), "no base changed"


def test_a_pull_request_is_stacked_on_another_from_the_menu_after_changing_its_base(repo, origin, gh, jazygit):
    repo.commit("Initial commit", **{"README.md": "# Repo\n"})
    repo("push", "--quiet", "origin", "main")
    repo("checkout", "--quiet", "-b", "feat/a")
    repo.commit("Add a", **{"a.txt": "a\n"})
    repo("push", "--quiet", "-u", "origin", "feat/a")
    # feat/c was opened on main; it is to go on feat/a.
    repo("checkout", "--quiet", "-b", "feat/c")
    repo.commit("Add c", **{"c.txt": "c\n"})
    repo("push", "--quiet", "-u", "origin", "feat/c")
    repository = f"{origin.path.parent.name}/origin"
    gh.answer((LISTED, listed(repository, [node(repository, 3, "feat/c", "main"),
                                           node(repository, 1, "feat/a", "main")])),
              ("pr edit", ""),
              ("/stacks", {"number": 12}))
    ui = jazygit()
    ui.branches.select("feat/c")
    ui.app.eventually(lambda: "#3" in ui.app.text(), "the pull requests read")

    ui.app.press("s")
    ui.app.wait_for("(o) stack on…")
    ui.app.press("o")
    ui.app.wait_for("Stack #3 of feat/c on")
    assert "feat/a" in ui.app.text()
    ui.app.press("enter")
    ui.app.wait_for("#3: main → feat/a")
    ui.app.press("enter")

    ui.app.eventually(
        lambda: ["api", "--method", "POST", "repos/{owner}/{repo}/stacks",
                 "-F", "pull_requests[]=1", "-F", "pull_requests[]=3"] in gh.calls(),
        "the stack made on GitHub")
    edits = [call for call in gh.calls() if call[:2] == ["pr", "edit"]]
    assert edits and edits[0][2:] == ["3", "--base", "feat/a"], edits


def test_a_stack_with_a_branch_not_here_is_checked_out_from_the_menu(repo, origin, gh, jazygit):
    repository = two_pushed_branches(repo, origin)
    # Only feat/a is here, one commit behind its branch on the remote; feat/b is only on the remote.
    repo("checkout", "--quiet", "feat/a")
    repo.commit("Add a2", **{"a2.txt": "a2\n"})
    repo("push", "--quiet", "origin", "feat/a")
    repo("reset", "--quiet", "--hard", "HEAD~1")
    repo("checkout", "--quiet", "main")
    repo("branch", "--quiet", "-D", "feat/b")
    stack = stacked(repository, [(1, "feat/a"), (2, "feat/b")])
    gh.answer((LISTED, listed(repository, [node(repository, 2, "feat/b", "feat/a", stack, 2),
                                           node(repository, 1, "feat/a", "main", stack, 1)])))
    ui = jazygit()
    ui.app.eventually(lambda: "#2 not checked out" in ui.app.text(), "the stack shown")
    ui.branches.select("feat/a")

    ui.app.press("s")
    ui.app.wait_for("(c) check out the stack")
    ui.app.press("c")
    ui.app.wait_for("Check out stack #12")
    assert "fast-forwarded by 1 commit" in ui.app.text()
    assert "new, following origin/feat/b" in ui.app.text()
    ui.app.press("t")

    ui.app.eventually(
        lambda: repo("rev-parse", "--abbrev-ref", "HEAD") == "feat/b", "the top of the stack checked out")
    assert repo("rev-parse", "feat/a") == origin("rev-parse", "feat/a"), "feat/a fast-forwarded"
    assert repo("rev-parse", "--abbrev-ref", "feat/b@{upstream}") == "origin/feat/b"
    assert repo("config", "branch.feat/b.gh-merge-base") == "feat/a"
