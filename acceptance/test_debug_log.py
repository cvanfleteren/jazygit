"""
The debug log that --debug writes for a bug report: what jazygit ran and handled, but nothing of the environment
it ran commands in, which can hold a token.
"""
from pathlib import Path

from harness import eventually


def debug_log(env) -> Path:
    return Path(env["HOME"]) / ".local" / "state" / "jazygit" / "debug.log"


def test_debug_log_tells_what_ran_and_where_it_is(repo, env, jazygit):
    repo.commit("Initial commit", **{"README.md": "# Parser\n"})
    ui = jazygit("--debug")
    ui.caught_up()

    ui.app.press("q")
    eventually(lambda: ui.app.pump() or ui.app.exited, "jazygit to quit")

    log = debug_log(env)
    assert str(log).encode() in ui.app.output, "the path of the debug log is printed on exit"
    lines = log.read_text().splitlines()
    assert any(" INFO net.vanfleteren.jazygit.DebugLog - jazygit " in line for line in lines), lines[:5]
    assert any(" DEBUG net.vanfleteren.jazygit.process.Exec - git " in line and line.endswith(": ok")
               for line in lines), lines
    assert any(" DEBUG net.vanfleteren.jazygit.state.Program - " in line for line in lines), lines
    assert not any("with environment" in line for line in lines)


def test_without_debug_there_is_no_debug_log(repo, env, jazygit):
    repo.commit("Initial commit", **{"README.md": "# Parser\n"})
    jazygit().caught_up()

    assert not debug_log(env).exists()
