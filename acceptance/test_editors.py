"""Opening a file in an editor of the settings that opens files (ADR 145)."""
from pathlib import Path

from harness import written


def file_editors(env, *entries: tuple[str, str, str | None]):
    """Sets editors that open files in the settings, each (name, marker, shortcut): one that writes the file and the
    line it was asked to open to the file named marker in the worktree, and is left running, as a GUI editor is."""
    config = Path(env["XDG_CONFIG_HOME"]) / "jazygit" / "config.toml"
    with config.open("a") as file:
        for name, marker, shortcut in entries:
            file.write(f'\n[[editors]]\nname = "{name}"\n'
                       f'file-command = ["sh", "-c", "echo \\"$1:$2\\" > {{dir}}/{marker}", "sh", "{{file}}", '
                       f'"{{line}}"]\n')
            if shortcut is not None:
                file.write(f'shortcut = "{shortcut}"\n')


def opened(ui, marker: Path) -> str:
    return written(ui.app, marker, f"the editor to write {marker.name}").strip()


def test_a_file_opens_in_the_one_editor_set_to_open_files_at_its_start(repo, env, jazygit):
    repo.commit("Add one", **{"one.txt": "one\n"})
    repo.write("one.txt", "one\nchanged\n")
    file_editors(env, ("Marker", "opened", None))
    ui = jazygit()

    ui.files.edit("one.txt")

    assert opened(ui, repo.path / "opened") == f"{repo.path / 'one.txt'}:1"


def test_with_more_editors_set_to_open_files_the_letter_chooses_one(repo, env, jazygit):
    repo.commit("Add one", **{"one.txt": "one\n"})
    repo.write("one.txt", "one\nchanged\n")
    file_editors(env, ("First", "first", None), ("Kate", "kate", "k"))
    ui = jazygit()

    ui.files.edit("one.txt", "k")

    assert opened(ui, repo.path / "kate") == f"{repo.path / 'one.txt'}:1"
    assert not (repo.path / "first").exists()
