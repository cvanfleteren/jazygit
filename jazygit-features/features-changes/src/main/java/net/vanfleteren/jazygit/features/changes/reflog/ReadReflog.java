package net.vanfleteren.jazygit.features.changes.reflog;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.git.history.History;
import net.vanfleteren.jazygit.core.repo.Kept;
import net.vanfleteren.jazygit.core.repo.Reflog;
import net.vanfleteren.jazygit.core.repo.ReflogEntry;
import net.vanfleteren.jazygit.kernel.state.Cmd;
import net.vanfleteren.jazygit.kernel.state.Env;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.Label;
import net.vanfleteren.jazygit.kernel.state.Msg;

/**
 * Reads the entries of the reflog of HEAD after the newest {@code skip}, at most {@code max}, in one process (ADR
 * 186); then what keeps those of their commits no ref reaches, in two more at the same time (ADR 188). What it read
 * comes back as one, so that what is lost goes with the entries it is of.
 */
public record ReadReflog(int skip, int max) implements Cmd {

    @Override
    public Option<Label> label() {
        return Option.of(Label.of("changes.work.reflog"));
    }

    @Override
    public Msg run(Env env) {
        History history = env.git().history();
        return history.reflog(skip, max)
                .mapLeft(Failure::of)
                .fold(this::failed, reflog -> new ReflogMsg.Read(this, reflog, kept(history, reflog)));
    }

    @Override
    public Msg failed(Failure failure) {
        return new ReflogMsg.Failed(this, failure);
    }

    /**
     * What keeps the commits of the entries of {@code reflog}; none when that could not be found, so that nothing is
     * marked.
     */
    private Option<Kept> kept(History history, Reflog reflog) {
        return switch (reflog) {
            case Reflog.Empty _ -> Option.of(Kept.BY_REFS);
            case Reflog.Entries(var entries) ->
                history.lost(entries.map(ReflogEntry::sha)).toOption();
        };
    }
}
