package net.vanfleteren.jazygit.features.branches.worktree.add;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.kernel.state.Cmd;
import net.vanfleteren.jazygit.kernel.state.Update;
import net.vanfleteren.jazygit.kernel.state.Update.Next;

/**
 * Asking where a new worktree goes in tests: the reading of where it starts out that the popup waits for (ADR 134).
 */
public final class LocationAsked {

    private LocationAsked() {}

    /**
     * {@code next} once the template it reads, if any, was read, with none set: the other commands it asks for are
     * kept, and those the read asks for come after.
     */
    public static Next located(Next next) {
        return located(next, Option.none());
    }

    /**
     * {@code next} once the template it reads, if any, was read as {@code template}, as {@link #located(Next)}.
     */
    public static Next located(Next next, Option<String> template) {
        List<ReadWorktreeTemplate> reads =
                next.cmds().filter(ReadWorktreeTemplate.class::isInstance).map(ReadWorktreeTemplate.class::cast);
        List<Cmd> others = next.cmds().filter(cmd -> !(cmd instanceof ReadWorktreeTemplate));
        return reads.foldLeft(new Next(next.model(), others, next.quit()), (asking, read) -> {
            Next answered = Update.update(
                    asking.model(), new AddWorktreeMsg.LocationTemplateRead(read.source(), read.switching(), template));
            return new Next(answered.model(), asking.cmds().appendAll(answered.cmds()), asking.quit());
        });
    }
}
