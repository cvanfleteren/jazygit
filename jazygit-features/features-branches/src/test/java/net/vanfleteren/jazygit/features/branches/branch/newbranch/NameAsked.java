package net.vanfleteren.jazygit.features.branches.branch.newbranch;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.Update;

/**
 * Asking for the name of a new branch in tests: the request, and the reading of what the name starts out as that it
 * waits for (ADR 121).
 */
public final class NameAsked {

    private NameAsked() {}

    /**
     * {@code model} once {@code request} asked for the name of a new branch, with no template set.
     */
    public static Model asked(Model model, Msg request) {
        return asked(model, request, Option.none());
    }

    /**
     * {@code model} once {@code request} asked for the name of a new branch, with {@code template} read as what it
     * starts out as.
     */
    public static Model asked(Model model, Msg request, Option<String> template) {
        Update.Next next = Update.update(model, request);
        return next.cmds()
                .filter(ReadNameTemplate.class::isInstance)
                .map(ReadNameTemplate.class::cast)
                .foldLeft(
                        next.model(),
                        (asking, read) -> Update.update(
                                        asking, new NewBranchMsg.NameTemplateRead(read.popup(), template))
                                .model());
    }
}
