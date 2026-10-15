package net.vanfleteren.jazygit.feature.settings;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.gh.model.GhLogin;
import net.vanfleteren.jazygit.state.Failure;

/**
 * How gh is set up for the repository, as the settings show it.
 *
 * @param version the version of the gh that runs, or why it could not be run
 * @param host    the GitHub host of the repository's remotes
 * @param user    the account the repository is configured to use, if any
 * @param logins  the accounts gh is logged in to, on every host, or why they could not be listed
 */
public record GitHubSetup(Either<Failure, String> version, String host, Option<String> user,
                          Either<Failure, List<GhLogin>> logins) {

    /**
     * The accounts gh is logged in to on the repository's host.
     */
    public List<GhLogin> onHost() {
        return logins.getOrElse(List.empty()).filter(login -> login.host().equals(host));
    }

    /**
     * The account gh uses on the repository's host when the repository is configured to use none.
     */
    public Option<String> active() {
        return onHost().find(GhLogin::active).map(GhLogin::login);
    }

    /**
     * Whether the account the repository is configured to use is one gh has a working token of; when the
     * accounts could not be listed, that is not known, and taken to be so.
     */
    public boolean userAvailable() {
        return logins.isLeft() || user.forAll(configured -> onHost().exists(login -> login.login().equals(configured)
                && login.valid()));
    }
}
