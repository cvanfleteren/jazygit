package net.vanfleteren.jazygit.state.load;

import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.Ahead;
import net.vanfleteren.jazygit.git.model.BranchRef;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.state.Env;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.Label;
import net.vanfleteren.jazygit.state.Msg;

/**
 * Reads the commits from {@code tip}, where {@code branch} is, or HEAD for none, that are not on {@code base}. Both
 * are where the status and the branches read last have them (ADR 49), so two loads are equal only when they read
 * the same.
 *
 * @param tip  the commit the branch or HEAD is at; none for a HEAD without commits, or a branch not read
 * @param base the base of ADR 20, {@link BranchRef#bases}, with the commit it is at: the first of them the branches
 *             read last have; none when there is none to compare with
 */
public record LoadAhead(Option<BranchRef> branch, Option<Sha> tip, Option<Base> base) implements Load {

    @Override
    public Option<Label> label() {
        return Option.of(Label.of("work.ahead"));
    }

    @Override
    public Msg run(Env env) {
        return read(env).fold(this::failed, ahead -> new LoadMsg.AheadLoaded(this, ahead));
    }

    /**
     * The commits not on the base, none when there is no base to compare with, or nothing to compare; or why they
     * could not be read.
     */
    Either<Failure, Option<Ahead>> read(Env env) {
        return tip.flatMap(ours -> base.map(theirs -> env.git()
                        .history()
                        .ahead(ours, theirs.ref(), theirs.tip())
                        .<Failure>mapLeft(Failure.Git::new)
                        .map(Option::of)))
                .getOrElse(() -> Either.right(Option.none()));
    }

    /**
     * A branch compared with, and the commit it is at.
     */
    public record Base(BranchRef ref, Sha tip) {}
}
