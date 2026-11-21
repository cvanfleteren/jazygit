package net.vanfleteren.jazygit.kernel.state;

import io.vavr.collection.HashMap;
import io.vavr.collection.Map;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.gh.GitHub;
import net.vanfleteren.jazygit.core.git.Git;
import net.vanfleteren.jazygit.kernel.settings.CollapsedStore;
import net.vanfleteren.jazygit.kernel.settings.RecentRepositories;
import net.vanfleteren.jazygit.kernel.settings.SettingsStore;
import net.vanfleteren.jazygit.kernel.state.build.ForgeAccount;

/**
 * What a {@link Cmd} runs against: the repository through git, its GitHub repository through gh, where the
 * settings, the recent repositories and the review threads collapsed or opened are kept, where another repository is
 * opened, the terminal the app is shown on, and what the features run against of their own.
 *
 * <p>gh is a field, not a service of GitHub's feature, as the review threads of the rich diff, which the kernel still
 * draws, are loaded with it from {@code features-changes}; it can become one once they leave the kernel (ADR 169).
 *
 * @param services what the features run against of their own, each under a {@link Service} declared in its package,
 *                 as a part of the model is (ADR 169)
 */
public record Env(
        Git git,
        GitHub gitHub,
        SettingsStore settings,
        RecentRepositories recent,
        CollapsedStore collapsed,
        Repositories repositories,
        Terminal terminal,
        Services services) {

    /**
     * How the account the repository is configured to use on its forge is had, for a program jazygit starts that may
     * use the forge's own program, as an agent (ADR 162): the feature of the forge gives it, the kernel names none.
     */
    public static final Service<AccountOnForge> FORGE_ACCOUNT = Service.of("kernel.forgeAccount", env -> Option.none());

    /**
     * As given, keeping no review threads collapsed or opened, and with nothing of the features'.
     */
    public Env(
            Git git,
            GitHub gitHub,
            SettingsStore settings,
            RecentRepositories recent,
            Repositories repositories,
            Terminal terminal) {
        this(git, gitHub, settings, recent, CollapsedStore.NONE, repositories, terminal, Services.NONE);
    }

    /**
     * As given, on no terminal.
     */
    public Env(Git git, GitHub gitHub, SettingsStore settings, RecentRepositories recent, Repositories repositories) {
        this(git, gitHub, settings, recent, repositories, Terminal.NONE);
    }

    /**
     * Against git and gh, keeping no settings and no recent repositories, and opening no other repository.
     */
    public Env(Git git, GitHub gitHub) {
        this(git, gitHub, SettingsStore.NONE);
    }

    /**
     * Against git and gh, keeping the settings in {@code settings}, keeping no recent repositories, and opening
     * no other repository.
     */
    public Env(Git git, GitHub gitHub, SettingsStore settings) {
        this(git, gitHub, settings, RecentRepositories.NONE, Repositories.NONE);
    }

    /**
     * As this, opening other repositories in {@code repositories} and shown on {@code terminal}.
     */
    public Env withApp(Repositories repositories, Terminal terminal) {
        return new Env(git, gitHub, settings, recent, collapsed, repositories, terminal, services);
    }

    /**
     * As this, with {@code value} for {@code service}.
     */
    public <T> Env with(Service<T> service, T value) {
        return new Env(git, gitHub, settings, recent, collapsed, repositories, terminal, services.with(service, value));
    }

    /**
     * What {@code service} is here: what it is when none was given, when none was.
     */
    public <T> T service(Service<T> service) {
        return services.get(service);
    }

    /**
     * The account the repository is configured to use on its forge, for a program jazygit starts that may use the
     * forge's own program, as an agent (ADR 162); none when none is configured, and that program runs as its active
     * one, as jazygit does. It names no forge, so a feature that knows none can pass it on.
     */
    public Option<ForgeAccount> forgeAccount() {
        return service(FORGE_ACCOUNT).of(this);
    }

    /**
     * How the account a repository is configured to use on its forge is had from what commands run against.
     */
    @FunctionalInterface
    public interface AccountOnForge {
        Option<ForgeAccount> of(Env env);
    }

    /**
     * Something a feature runs against, as a store it keeps a file of its own in, declared as a constant in its own
     * package (ADR 169): the environment holds it without naming it, as the model holds a {@link Part}.
     *
     * @param name what it is kept under; it starts with the root package of the feature's module, as
     *             {@code github.unseen}
     * @param none what it is when none was given, as in the tests: one that keeps nothing
     * @param <T>  its type
     */
    public record Service<T>(String name, T none) {

        public static <T> Service<T> of(String name, T none) {
            return new Service<>(name, none);
        }
    }

    /**
     * What the features run against of their own, by the name of their {@link Service}.
     *
     * @param given what was given, by the name of its service
     */
    public record Services(Map<String, Object> given) {

        public static final Services NONE = new Services(HashMap.empty());

        /**
         * What {@code service} is: its {@code none} when it was not given.
         */
        @SuppressWarnings("unchecked") // Only with() gives a value under a service's name, one of that service's type.
        public <T> T get(Service<T> service) {
            return given.get(service.name()).map(value -> (T) value).getOrElse(service.none());
        }

        /**
         * These, with {@code value} for {@code service}.
         */
        public <T> Services with(Service<T> service, T value) {
            return new Services(given.put(service.name(), value));
        }
    }
}
