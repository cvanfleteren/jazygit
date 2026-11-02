"""A theme per repository, from its git configuration (ADR 70)."""


def theme_from_an_include(repo, env, tmp_path):
    """Gives the repository the light theme through an includeIf of the user's git configuration, as for every
    repository in a directory."""
    work = tmp_path / "gitconfig-work"
    work.write_text("[jazygit]\n\ttheme = light\n")
    user = tmp_path / "gitconfig"
    user.write_text(f'[includeIf "gitdir:{repo.path}/"]\n\tpath = {work}\n')
    env["GIT_CONFIG_GLOBAL"] = str(user)


def local_config(repo) -> str:
    return repo("config", "--local", "--list")


def test_a_theme_the_user_gives_a_directory_shows_in_the_settings(repo, env, tmp_path, jazygit):
    repo.commit("Add one", **{"one.txt": "one\n"})
    theme_from_an_include(repo, env, tmp_path)
    ui = jazygit()
    ui.app.wait_for("Add one")

    ui.app.press("ctrl+s")

    ui.app.wait_for("Theme for this repository: light, from")
    ui.app.wait_for("Theme for all repositories: dark")


def test_a_theme_chosen_for_the_repository_is_kept_in_its_git_configuration_and_can_be_taken_away(
        repo, env, tmp_path, jazygit):
    repo.commit("Add one", **{"one.txt": "one\n"})
    theme_from_an_include(repo, env, tmp_path)
    ui = jazygit()
    ui.app.wait_for("Add one")

    ui.app.press("ctrl+s")
    ui.app.wait_for("Theme for this repository: light, from")
    ui.app.press("r")
    ui.app.wait_for("(e) ember")
    ui.app.press("e")
    ui.app.eventually(lambda: "jazygit.theme=ember" in local_config(repo), "the theme to be kept in .git/config")

    ui.app.press("ctrl+s")
    ui.app.wait_for("Theme for this repository: ember")
    ui.app.press("r")
    ui.app.wait_for("none of its own: light, from")
    ui.app.press("c")
    ui.app.eventually(lambda: "jazygit.theme" not in local_config(repo), "the theme to be taken away")
