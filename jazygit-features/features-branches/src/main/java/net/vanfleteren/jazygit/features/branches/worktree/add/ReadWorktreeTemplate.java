package net.vanfleteren.jazygit.features.branches.worktree.add;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.git.model.ConfigValue;
import net.vanfleteren.jazygit.features.branches.worktree.WorktreeCmd;
import net.vanfleteren.jazygit.kernel.state.Cmd;
import net.vanfleteren.jazygit.kernel.state.Env;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.Msg;
import org.slf4j.LoggerFactory;

/**
 * Reads where the new worktree that checks out {@code source} starts out, from the git configuration, before the
 * popup asks where it goes (ADR 134). For a pull request, its own key holds, and the one for every worktree when it
 * is not set; each is read as git reads it, so that it can be set for one repository, for all, or for the
 * repositories in a directory. A blank one is none. A leading {@code ~} is the home directory. That a key could not
 * be read is not worth telling: it is then none, and the debug log says why.
 *
 * @param switching whether the app switches to the worktree once it is added, without asking (ADR 68)
 */
public record ReadWorktreeTemplate(WorktreeSource source, boolean switching) implements Cmd {

    /**
     * The key of the git configuration that holds where every new worktree starts out.
     */
    public static final String KEY = "jazygit.newWorktreeTemplate";

    @Override
    public Msg run(Env env) {
        return read(source.templateKeys()
                .toStream()
                .flatMap(key -> read(env, key))
                .headOption()
                .map(template -> WorktreeCmd.expandedHome(template, System.getProperty("user.home"))));
    }

    @Override
    public Msg failed(Failure failure) {
        return read(Option.none());
    }

    private static Option<String> read(Env env, String key) {
        return env.git()
                .config()
                .all(key)
                .map(List::lastOption)
                .map(value -> value.map(ConfigValue::value).filter(template -> !template.isBlank()))
                .peekLeft(error -> LoggerFactory.getLogger(ReadWorktreeTemplate.class)
                        .debug("{} could not be read: {}", key, error))
                .getOrElse(Option.none());
    }

    private Msg read(Option<String> template) {
        return new AddWorktreeMsg.LocationTemplateRead(source, switching, template);
    }
}
