package net.vanfleteren.jazygit.core.forgejo;

import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.repo.Sha;

/**
 * What jazygit asks Forgejo (ADR 174).
 */
public interface Forgejo {

    /**
     * No Forgejo: no remote is on it, as without the feature's services.
     */
    Forgejo NONE = new Forgejo() {
        @Override
        public Option<ForgejoRepository> repository(String remoteUrl) {
            return Option.none();
        }

        @Override
        public boolean isForgejo(ForgejoRepository repository) {
            return false;
        }

        @Override
        public Either<ForgejoError, CommitStatus> status(ForgejoRepository repository, Sha sha) {
            return Either.left(new ForgejoError.Unreachable("no Forgejo"));
        }
    };

    /**
     * The repository the git remote {@code remoteUrl} names, on a host that may be Forgejo; none for a remote that
     * cannot be.
     */
    Option<ForgejoRepository> repository(String remoteUrl);

    /**
     * Whether the host of {@code repository} is Forgejo: it answers {@code /api/forgejo/v1/version}. Each host is
     * asked once.
     */
    boolean isForgejo(ForgejoRepository repository);

    /**
     * How the builds of {@code sha} in {@code repository} went.
     */
    Either<ForgejoError, CommitStatus> status(ForgejoRepository repository, Sha sha);
}
