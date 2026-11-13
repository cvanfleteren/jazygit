package net.vanfleteren.jazygit.feature.branch.newbranch;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.ConfigValue;
import net.vanfleteren.jazygit.state.Cmd;
import net.vanfleteren.jazygit.state.Env;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.Msg;
import org.slf4j.LoggerFactory;

/**
 * Reads what the name of a new branch starts out as, from the git configuration, before {@code popup} asks for it
 * (ADR 121). It is read as git reads it, from the repository's configuration, the user's or the system's, so that it
 * can be set for one repository, for all, or for the repositories in a directory. That it could not be read is not
 * worth telling: the name then starts out empty, and the debug log says why.
 */
public record ReadNameTemplate(NewBranchPopup popup) implements Cmd {

    /**
     * The key of the git configuration the template is read from.
     */
    public static final String KEY = "jazygit.newBranchNameTemplate";

    @Override
    public Msg run(Env env) {
        return env.git()
                .config()
                .all(KEY)
                .map(List::lastOption)
                .map(value -> value.map(ConfigValue::value))
                .peekLeft(error ->
                        LoggerFactory.getLogger(ReadNameTemplate.class).debug("{} could not be read: {}", KEY, error))
                .fold(error -> read(Option.none()), this::read);
    }

    @Override
    public Msg failed(Failure failure) {
        return read(Option.none());
    }

    private Msg read(Option<String> template) {
        return new NewBranchMsg.NameTemplateRead(popup, template);
    }
}
