package net.vanfleteren.jazygit.features.github.checks;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.repo.RemoteBranch;
import net.vanfleteren.jazygit.features.forge.checks.ChecksMsg;
import net.vanfleteren.jazygit.features.forge.checks.ChecksRead;
import net.vanfleteren.jazygit.features.github.GitHubFailure;
import net.vanfleteren.jazygit.features.github.GitHubServices;
import net.vanfleteren.jazygit.features.github.state.PendingChecks;
import net.vanfleteren.jazygit.kernel.state.Cmd;
import net.vanfleteren.jazygit.kernel.state.Env;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.Label;
import net.vanfleteren.jazygit.kernel.state.Msg;

/**
 * Reads the checks on the tip of {@code branch} on GitHub one by one, with their timing, which the user is waiting
 * for.
 */
public record LoadChecks(RemoteBranch branch) implements Cmd {

    @Override
    public Option<Label> label() {
        return Option.of(Label.of("github.work.checksOf", branch.branch().value()));
    }

    @Override
    public Msg run(Env env) {
        return GitHubServices.of(env)
                .checks()
                .runs(List.of(branch.branch().value()))
                .mapLeft(GitHubFailure::of)
                .fold(this::failed, runs -> loaded(branch, PendingChecks.of(runs)));
    }

    @Override
    public Msg failed(Failure failure) {
        return notLoaded(branch, failure);
    }

    /**
     * The checks of {@code branch} among those {@code pending} tells were read, as the checks flow reads them.
     */
    static Msg loaded(RemoteBranch branch, PendingChecks pending) {
        return new ChecksMsg.Read(
                GitHubChecksPort.PORT,
                branch,
                Either.right(new ChecksRead(
                        pending.repository(),
                        Option.none(),
                        pending.of(branch.branch()).map(GitHubChecksPort::of))));
    }

    /**
     * Reading the checks of {@code branch} failed, as {@code failure} says.
     */
    static Msg notLoaded(RemoteBranch branch, Failure failure) {
        return new ChecksMsg.Read(GitHubChecksPort.PORT, branch, Either.left(failure));
    }
}
