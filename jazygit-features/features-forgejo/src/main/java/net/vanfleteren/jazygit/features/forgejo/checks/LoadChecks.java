package net.vanfleteren.jazygit.features.forgejo.checks;

import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.forgejo.Forgejo;
import net.vanfleteren.jazygit.core.forgejo.ForgejoRepository;
import net.vanfleteren.jazygit.core.repo.RemoteBranch;
import net.vanfleteren.jazygit.core.repo.RemoteName;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.features.forgejo.ForgejoFailure;
import net.vanfleteren.jazygit.features.forgejo.builds.ForgejoServices;
import net.vanfleteren.jazygit.kernel.state.Cmd;
import net.vanfleteren.jazygit.kernel.state.Env;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.Label;
import net.vanfleteren.jazygit.kernel.state.Msg;

/**
 * Reads the checks on {@code sha}, the tip of {@code branch}, from the Forgejo repository of its remote, one by one,
 * which the user is waiting for (ADR 195).
 */
public record LoadChecks(RemoteBranch branch, Sha sha) implements Cmd {

    @Override
    public Option<Label> label() {
        return Option.of(Label.of("forgejo.work.checksOf", branch.branch().value()));
    }

    @Override
    public Msg run(Env env) {
        Forgejo forgejo = ForgejoServices.of(env);
        return new ChecksMsg.Read(
                branch,
                repository(env, forgejo, branch.remote())
                        .flatMap(repository -> forgejo.checks(repository, sha)
                                .<Failure>mapLeft(ForgejoFailure.Asked::new)
                                .map(checks ->
                                        new ChecksRead(repository.owner() + "/" + repository.name(), sha, checks))));
    }

    /**
     * The repository on Forgejo of {@code remote}; why not when it is not on Forgejo, or the remotes could not be
     * listed.
     */
    static Either<Failure, ForgejoRepository> repository(Env env, Forgejo forgejo, RemoteName remote) {
        return env.git()
                .remotes()
                .urls()
                .toOption()
                .flatMap(urls -> urls.get(remote))
                .flatMap(forgejo::repository)
                .filter(forgejo::isForgejo)
                .<Either<Failure, ForgejoRepository>>map(Either::right)
                .getOrElse(() -> Either.left(new ForgejoFailure.NotOnForgejo(remote.value())));
    }

    @Override
    public Msg failed(Failure failure) {
        return new ChecksMsg.Read(branch, Either.left(failure));
    }
}
