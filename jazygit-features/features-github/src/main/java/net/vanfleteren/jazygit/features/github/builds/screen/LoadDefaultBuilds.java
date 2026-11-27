package net.vanfleteren.jazygit.features.github.builds.screen;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.features.github.GitHubFailure;
import net.vanfleteren.jazygit.features.github.GitHubServices;
import net.vanfleteren.jazygit.kernel.state.Cmd;
import net.vanfleteren.jazygit.kernel.state.Env;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.Label;
import net.vanfleteren.jazygit.kernel.state.Msg;

/**
 * Reads the builds on some commits of the default branch, from {@code after} on, or from its tip without, and its name
 * (ADR 212); {@code refresh} when it reads the newest again. It runs in the background, as it talks to GitHub.
 */
public record LoadDefaultBuilds(Option<String> after, boolean refresh) implements Cmd {

    @Override
    public Option<Label> label() {
        return Option.of(Label.of("github.work.defaultBuilds"));
    }

    @Override
    public boolean background() {
        return true;
    }

    @Override
    public Msg run(Env env) {
        return GitHubServices.of(env)
                .checks()
                .defaultBranchBuilds(after)
                .mapLeft(GitHubFailure::of)
                .fold(this::failed, page -> new BuildsScreenMsg.DefaultRead(after, refresh, page));
    }

    @Override
    public Msg failed(Failure failure) {
        return new BuildsScreenMsg.DefaultFailed(after, refresh, failure);
    }
}
