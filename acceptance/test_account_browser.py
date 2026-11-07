"""The browser of the account a repository uses, set in the settings and kept in the settings file (ADR 88)."""

from pathlib import Path


def config(env) -> Path:
    return Path(env["XDG_CONFIG_HOME"]) / "jazygit" / "config.toml"


def test_the_browser_of_the_account_is_set_in_the_settings_and_can_be_taken_away(repo, env, gh, jazygit):
    repo.commit("Add one", **{"one.txt": "one\n"})
    gh.answer(("config get user -h github.com", "alice"))
    env["BROWSER"] = "w3m"
    ui = jazygit()
    ui.app.wait_for("Add one")

    ui.app.press("ctrl+s")
    ui.app.wait_for("(b) Browser for alice on github.com: none, w3m from $BROWSER")
    ui.app.press("b")
    ui.app.wait_for("Browser for alice on github.com (empty for gh's)")
    ui.app.type("firefox -P personal")
    ui.app.press("enter")
    ui.app.eventually(
        lambda: '"firefox -P personal"' in config(env).read_text() and "alice" in config(env).read_text(),
        "the browser to be kept")

    ui.app.press("ctrl+s")
    ui.app.wait_for("(b) Browser for alice on github.com: firefox -P personal")
    ui.app.press("b")
    ui.app.wait_for("Browser for alice on github.com (empty for gh's)")
    ui.app.press(*["backspace"] * len("firefox -P personal"))
    ui.app.press("enter")
    ui.app.eventually(lambda: "browsers" not in config(env).read_text(), "the browser to be taken away")
