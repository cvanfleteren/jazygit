package net.vanfleteren.jazygit.state;

import net.vanfleteren.jazygit.state.Update.Next;

/**
 * Reads from GitHub that run one at a time, like the pull requests and the checks of the branches. They run in the
 * background next to other commands, so two of the same would finish in either order, and the one started first
 * could replace what the later one read. A read asked for while one runs therefore waits for it: when it is asked
 * for anyway, as something just changed on GitHub that the running one may have missed, it starts once that one is
 * done; else the running one is enough.
 */
final class GitHubReads {

    private GitHubReads() {}

    /**
     * {@code next}, also running {@code read}, unless it runs already.
     */
    static Next load(Next next, Cmd read) {
        return next.model().gitHub().reading().contains(read) ? next : start(next, read);
    }

    /**
     * {@code next}, also running {@code read}; when it runs already, once it is done.
     */
    static Next loadAnyway(Next next, Cmd read) {
        return next.model().gitHub().reading().contains(read)
                ? new Next(
                        next.model()
                                .mapGitHub(gitHub ->
                                        gitHub.withAgain(gitHub.again().add(read))),
                        next.cmds())
                : start(next, read);
    }

    /**
     * {@code model}, with what {@code read} read in it, now that it is done; it runs again if it was asked for
     * meanwhile.
     */
    static Next done(Model model, Cmd read) {
        Model done =
                model.mapGitHub(gitHub -> gitHub.withReading(gitHub.reading().remove(read))
                        .withAgain(gitHub.again().remove(read)));
        return model.gitHub().again().contains(read) ? start(Next.of(done), read) : Next.of(done);
    }

    private static Next start(Next next, Cmd read) {
        return new Next(
                next.model()
                        .mapGitHub(gitHub -> gitHub.withReading(gitHub.reading().add(read))),
                next.cmds().append(read));
    }
}
