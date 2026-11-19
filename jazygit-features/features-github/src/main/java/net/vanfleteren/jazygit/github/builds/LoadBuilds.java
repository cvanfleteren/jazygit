package net.vanfleteren.jazygit.github.builds;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
import net.vanfleteren.jazygit.state.Cmd;
import net.vanfleteren.jazygit.state.Env;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.Label;
import net.vanfleteren.jazygit.state.Msg;

/**
 * Reads the builds on some commits of {@code branch} on GitHub, from {@code after} on, or from its tip without (ADR
 * 131). It runs in the background, as it talks to GitHub, which takes seconds.
 */
public record LoadBuilds(RemoteBranch branch, Option<String> after) implements Cmd {

    @Override
    public Option<Label> label() {
        return Option.of(Label.of("github.work.buildsOf", branch.shortName()));
    }

    @Override
    public boolean background() {
        return true;
    }

    @Override
    public Msg run(Env env) {
        return env.gitHub()
                .checks()
                .builds(branch.branch().value(), after)
                .mapLeft(Failure::of)
                .fold(this::failed, page -> new BuildsMsg.Read(branch, after, page));
    }

    @Override
    public Msg failed(Failure failure) {
        return new BuildsMsg.Failed(branch, after, failure);
    }
}
