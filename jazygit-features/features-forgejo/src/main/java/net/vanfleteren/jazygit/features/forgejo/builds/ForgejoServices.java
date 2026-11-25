package net.vanfleteren.jazygit.features.forgejo.builds;

import io.vavr.control.Option;
import java.nio.file.Path;
import net.vanfleteren.jazygit.core.forgejo.FjKeys;
import net.vanfleteren.jazygit.core.forgejo.Forgejo;
import net.vanfleteren.jazygit.core.forgejo.HttpForgejo;
import net.vanfleteren.jazygit.features.forgejo.pulls.Browser;
import net.vanfleteren.jazygit.kernel.state.Env;
import net.vanfleteren.jazygit.kernel.ui.Feature;

/**
 * What Forgejo's commands run against, which its feature builds once a repository is opened (ADR 171, 174): Forgejo's
 * API, with the tokens fj keeps.
 */
public final class ForgejoServices {

    /**
     * Forgejo's API; without it given, no host is Forgejo.
     */
    public static final Env.Service<Forgejo> FORGEJO = Env.Service.of("forgejo.api", Forgejo.NONE);

    /**
     * What opens Forgejo's links (ADR 176); without it given, nothing opens.
     */
    public static final Env.Service<Browser> BROWSER = Env.Service.of("forgejo.browser", Browser.NONE);

    /**
     * Where the logs of checks are kept to be opened (ADR 195); without it given, in the directory of temporary files.
     */
    public static final Env.Service<Path> LOGS =
            Env.Service.of("forgejo.logs", Path.of(System.getProperty("java.io.tmpdir"), "jazygit", "logs"));

    private ForgejoServices() {}

    /**
     * Where the logs of checks are kept, as {@code env} has it.
     */
    public static Path logs(Env env) {
        return env.service(LOGS);
    }

    /**
     * Forgejo, as {@code env} asks it.
     */
    public static Forgejo of(Env env) {
        return env.service(FORGEJO);
    }

    /**
     * What opens Forgejo's links, as {@code env} has it.
     */
    public static Browser browser(Env env) {
        return env.service(BROWSER);
    }

    /**
     * What Forgejo runs against for the repository {@code opening} opens: its API, with fj's keys read once they are
     * first needed, and which hosts are Forgejo found out again for each repository; and the browser of
     * {@code $BROWSER}, or the desktop's, run in the repository; and where the logs of checks are kept, in the cache.
     */
    public static Feature.Contribution running(Feature.RepositoryOpening opening) {
        return new Feature.Contribution(
                Env.Services.NONE
                        .with(
                                FORGEJO,
                                HttpForgejo.of(() -> FjKeys.read(FjKeys.file(
                                        Option.of(System.getenv("XDG_DATA_HOME")),
                                        Path.of(System.getProperty("user.home"))))))
                        .with(
                                BROWSER,
                                Browser.of(
                                        opening.git().worktree(),
                                        Option.of(System.getenv("BROWSER")),
                                        System.getProperty("os.name", "")))
                        .with(LOGS, opening.cacheDir().resolve("logs").resolve("forgejo")),
                Option.none());
    }
}
