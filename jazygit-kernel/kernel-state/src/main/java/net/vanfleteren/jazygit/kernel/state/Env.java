package net.vanfleteren.jazygit.kernel.state;

import io.vavr.collection.HashMap;
import io.vavr.collection.Map;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.git.Git;
import net.vanfleteren.jazygit.kernel.settings.RecentRepositories;
import net.vanfleteren.jazygit.kernel.settings.SettingsStore;
import net.vanfleteren.jazygit.kernel.state.build.ForgeAccount;
import net.vanfleteren.jazygit.kernel.state.launch.ProgramLauncher;

/**
 * What a {@link Cmd} runs against: the repository through git, where the settings and the recent repositories are
 * kept, where another repository is opened, the terminal the app is shown on, what starts the programs the user set
 * (ADR 183), and what the features run against of their own, which each builds once a repository is opened (ADR 169,
 * 171): the kernel names no forge.
 *
 * @param launcher what starts the programs the user set, as editors and agents, rather than git (ADR 183)
 * @param services what the features run against of their own, each under a {@link Service} declared in its package,
 *                 as a part of the model is (ADR 169)
 */
public record Env(
        Git git,
        SettingsStore settings,
        RecentRepositories recent,
        Repositories repositories,
        Terminal terminal,
        ProgramLauncher launcher,
        Services services) {

    /**
     * How the account the repository is configured to use on its forge is had, for a program jazygit starts that may
     * use the forge's own program, as an agent (ADR 162): the feature of the forge gives it, the kernel names none.
     */
    public static final Service<AccountOnForge> FORGE_ACCOUNT = Service.of("kernel.forgeAccount", env -> Option.none());

    /**
     * As given, starting no program: for the tests, which give a launcher of their own when they look at one.
     */
    public Env(
            Git git,
            SettingsStore settings,
            RecentRepositories recent,
            Repositories repositories,
            Terminal terminal,
            Services services) {
        this(git, settings, recent, repositories, terminal, ProgramLauncher.NONE, services);
    }

    /**
     * As given, with nothing of the features'.
     */
    public Env(
            Git git, SettingsStore settings, RecentRepositories recent, Repositories repositories, Terminal terminal) {
        this(git, settings, recent, repositories, terminal, Services.NONE);
    }

    /**
     * As given, on no terminal.
     */
    public Env(Git git, SettingsStore settings, RecentRepositories recent, Repositories repositories) {
        this(git, settings, recent, repositories, Terminal.NONE);
    }

    /**
     * Against git, keeping no settings and no recent repositories, and opening no other repository.
     */
    public Env(Git git) {
        this(git, SettingsStore.NONE);
    }

    /**
     * Against git, keeping the settings in {@code settings}, keeping no recent repositories, and opening no other
     * repository.
     */
    public Env(Git git, SettingsStore settings) {
        this(git, settings, RecentRepositories.NONE, Repositories.NONE);
    }

    /**
     * As this, opening other repositories in {@code repositories} and shown on {@code terminal}, which the programs
     * the user set are started on, as processes of this one.
     */
    public Env withApp(Repositories repositories, Terminal terminal) {
        return new Env(git, settings, recent, repositories, terminal, ProgramLauncher.on(terminal), services);
    }

    /**
     * As this, starting the programs the user set with {@code launcher}.
     */
    public Env with(ProgramLauncher launcher) {
        return new Env(git, settings, recent, repositories, terminal, launcher, services);
    }

    /**
     * As this, with {@code value} for {@code service}.
     */
    public <T> Env with(Service<T> service, T value) {
        return new Env(git, settings, recent, repositories, terminal, launcher, services.with(service, value));
    }

    /**
     * As this, with {@code more} as well: what it gives wins.
     */
    public Env with(Services more) {
        return new Env(git, settings, recent, repositories, terminal, launcher, services.and(more));
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

        /**
         * These, with {@code more} as well: what it gives wins.
         */
        public Services and(Services more) {
            return new Services(given.merge(more.given, (mine, theirs) -> theirs));
        }
    }
}
