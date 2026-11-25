"""The steps of an operation that stopped, listed above the log of the commits pane, and where it is, said in the
repository panel and in M's dialog (ADR 199)."""

import subprocess

import pytest


def four_on_feature(repo):
    """Main and feature parted at "Add one"; feature adds two commits around one that changes one.txt, as main does,
    feature checked out."""
    repo.commit("Add one", **{"one.txt": "one\n"})
    repo("switch", "--create", "feature")
    repo.commit("Add two", **{"two.txt": "two\n"})
    repo.commit("Change one on feature", **{"one.txt": "feature\n"})
    repo.commit("Add three", **{"three.txt": "three\n"})
    repo("switch", "main")
    repo.commit("Change one on main", **{"one.txt": "main\n"})
    repo("switch", "feature")


def short(repo, subject, rev="feature"):
    return repo.short(repo.find(subject, rev))


def shows_steps(ui, *expected):
    """Whether the steps above the log are those expected, top first, each as how it starts and how it ends: a subject
    is cut off in a narrow pane, before what follows it."""
    steps = ui.commits.steps()
    return len(steps) == len(expected) and all(
        step.startswith(start) and step.endswith(end) for step, (start, end) in zip(steps, expected))


def repo_lines(ui):
    return [line.strip() for line in ui.app.pane_text("Repo").splitlines() if line.strip()]


def stopped_rebase(repo, jazygit):
    """The app rebasing feature onto main, stopped on the conflict of "Change one on feature"."""
    four_on_feature(repo)
    ui = jazygit()
    ui.branches.rebase_onto("main")
    return ui


def test_a_rebase_that_stopped_lists_the_steps_to_come_above_the_one_it_stopped_at(repo, jazygit):
    ui = stopped_rebase(repo, jazygit)

    ui.app.eventually(lambda: shows_steps(
        ui,
        (f"{short(repo, 'Add three')}    ○ Add three", ""),
        (f"{short(repo, 'Change one on feature')}    ✗ Change one", "· 1 conflict"),
    ), "the steps of the rebase above the log")
    commits = ui.app.pane_text("Commits")
    # The commit rebased already is done; main's is not.
    assert f"{repo.short('HEAD')} Ac ✓ Add two" in commits, commits
    assert "○ Change one on main" in commits, commits
    # The app rebases with --rebase-merges: git counts its label and reset lines as steps too.
    assert "rebasing feature · 1 ahead of main · 4/5" in commits, commits


def test_the_repository_panel_says_what_is_in_progress_on_a_line_of_its_own(repo, jazygit):
    ui = stopped_rebase(repo, jazygit)

    ui.app.wait_in("Repo", "REBASING 4/5 onto main · 1 conflict")
    lines = repo_lines(ui)
    assert lines[0].endswith("→ (rebasing) feature"), lines
    assert lines[1] == "REBASING 4/5 onto main · 1 conflict", lines

    repo.resolve("one.txt", "both\n")

    ui.app.wait_in("Repo", "REBASING 4/5 onto main · ready to continue")
    ui.app.eventually(lambda: shows_steps(
        ui,
        (f"{short(repo, 'Add three')}    ○ Add three", ""),
        (f"{short(repo, 'Change one on feature')}    ✗ Change one", "· ready to continue"),
    ), "the step it stopped at resolved")


def test_highlighting_the_step_it_stopped_at_shows_its_commit(repo, jazygit):
    ui = stopped_rebase(repo, jazygit)
    stopped = short(repo, "Change one on feature")
    ui.app.wait_in("Commits", f"{stopped}    ✗")

    ui.app.focus("Commits")
    ui.app.move_cursor("Commits", stopped)

    ui.app.wait_for(f"Commit: {stopped}")


def test_m_says_where_the_rebase_stopped_and_what_it_goes_on_with(repo, jazygit):
    ui = stopped_rebase(repo, jazygit)
    ui.app.wait_in("Commits", f"{short(repo, 'Change one on feature')}    ✗")

    ui.app.press("M")

    ui.app.wait_for(f"Stopped at 4 of 5: {short(repo, 'Change one on feature')}")
    ui.app.wait_for(f"Next: {short(repo, 'Add three')}")


def test_an_interactive_rebase_lists_its_commands_as_they_were_written(repo, jazygit):
    four_on_feature(repo)
    # Edit "Add two", fix "Change one on feature" up into it, and run a command before "Add three". It stops at the
    # edit, which git reports as a success.
    editor = "sed -i -e '1s/^pick/edit/' -e '2s/^pick/fixup/' -e '2a exec true'"
    repo("-c", f"sequence.editor={editor}", "rebase", "-i", "main")
    assert repo.in_progress() == "rebase"
    ui = jazygit()

    ui.app.eventually(lambda: shows_steps(
        ui,
        (f"{short(repo, 'Add three')}    ○ Add three", ""),
        ("           · exec true", ""),
        (f"{short(repo, 'Change one on feature')}    ○ fixup Change", ""),
        (f"{short(repo, 'Add two')}    ✗ edit Add two", "· stopped to edit"),
    ), "the commands of the rebase above the log")
    assert repo_lines(ui)[1] == "REBASING 1/4 onto main · ready to continue"


def test_a_sequence_of_cherry_picks_lists_its_steps_and_counts_those_made(repo, jazygit):
    four_on_feature(repo)
    repo("switch", "main")
    with pytest.raises(subprocess.CalledProcessError):
        repo("cherry-pick", "main..feature")
    assert repo.in_progress() == "cherry-pick"
    ui = jazygit()

    ui.app.eventually(lambda: shows_steps(
        ui,
        (f"{short(repo, 'Add three')}    ○ Add three", ""),
        (f"{short(repo, 'Change one on feature')}    ✗ Change one", "· 1 conflict"),
    ), "the steps of the cherry-picks above the log")
    commits = ui.app.pane_text("Commits")
    assert "✓ Add two" in commits and "main · 2/3" in commits, commits
    lines = repo_lines(ui)
    assert lines[0].endswith("→ main"), lines
    assert lines[1] == f"CHERRY-PICKING {short(repo, 'Change one on feature')} · 1 conflict", lines
