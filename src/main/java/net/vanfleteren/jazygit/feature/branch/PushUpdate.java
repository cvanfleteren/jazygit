package net.vanfleteren.jazygit.feature.branch;

import io.vavr.collection.List;
import io.vavr.collection.Set;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.feature.branch.BranchCmd.Push;
import net.vanfleteren.jazygit.feature.branch.BranchCmd.PushSettingUpstream;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.PushMode;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.state.CommandLog;
import net.vanfleteren.jazygit.state.Loadable.Loaded;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Update.Next;
import net.vanfleteren.jazygit.state.Update;

/**
 * Pushing a branch.
 */
public final class PushUpdate {

    private PushUpdate() {
    }

    public static Next update(Model model, PushMsg msg) {
        return switch (msg) {
            case PushMsg.Requested(String branch) -> requested(model, branch);
            case PushMsg.ForceCancelled() -> Next.of(model.withoutPopup(ForcePushPopup.class));
            case PushMsg.UpstreamCancelled() -> Next.of(model.withoutPopup(SetUpstreamPopup.class));
            case PushMsg.UpstreamConfirmed(String upstream) -> upstreamConfirmed(model, upstream);
            case PushMsg.ForceConfirmed() -> model.popup(ForcePushPopup.class).map(ForcePushPopup::branch)
                    .map(branch -> push(model.withoutPopup(ForcePushPopup.class), branch, PushMode.FORCE_WITH_LEASE))
                    .getOrElse(() -> Next.of(model));
            // The branches then show the new ahead/behind counts.
            case PushMsg.Done(String branch, List<String> commands) ->
                    Update.refresh(CommandLog.append(
                            model.withPushing(without(model, branch)).withError(Option.none()),
                            "log.title.push", commands));
            case PushMsg.Failed(String branch, String message) ->
                    Update.refresh(model.withPushing(without(model, branch))
                            .withError(Option.of(Messages.get("error.push.failed", branch, message))));
        };
    }

    private static Next requested(Model model, String branch) {
        boolean known = model.branches() instanceof Loaded<List<Branch>>(List<Branch> branches)
                && branches.exists(b -> b.name().equals(branch));
        // A branch that is being pushed is not pushed again.
        if (!known || model.pushing().contains(branch)) {
            return Next.of(model);
        }
        // A branch without an upstream is pushed where the user says. Pushing a diverged branch needs a
        // force push, which the user must confirm.
        return !hasUpstream(model, branch)
                ? Next.of(model.openPopup(new SetUpstreamPopup(branch)).withError(Option.none()))
                : diverged(model, branch)
                ? Next.of(model.openPopup(new ForcePushPopup(branch)).withError(Option.none()))
                : push(model, branch, PushMode.FAST_FORWARD);
    }

    /**
     * Whether {@code branch} tracks a remote branch that exists. One whose upstream is gone is asked for
     * a new one too.
     */
    private static boolean hasUpstream(Model model, String branch) {
        return model.branches() instanceof Loaded<List<Branch>>(List<Branch> branches)
                && branches.exists(b -> b.name().equals(branch) && b.upstream().isDefined());
    }

    /**
     * Something other than {@code <remote> <branch>} keeps the dialog open, saying how to write it.
     */
    private static Next upstreamConfirmed(Model model, String text) {
        return model.popup(SetUpstreamPopup.class).map(SetUpstreamPopup::branch)
                .map(branch -> RemoteBranch.parse(text)
                        .map(upstream -> Next.of(model.withoutPopup(SetUpstreamPopup.class)
                                        .withPushing(with(model, branch))
                                        .withError(Option.none()),
                                new PushSettingUpstream(branch, upstream)))
                        .getOrElse(() -> Next.of(model.withError(Option.of(Messages.get("error.upstream.invalid", text.strip()))))))
                .getOrElse(() -> Next.of(model));
    }

    private static boolean diverged(Model model, String branch) {
        return model.branches() instanceof Loaded<List<Branch>>(List<Branch> branches)
                && branches.exists(b -> b.name().equals(branch) && b.behind() > 0);
    }

    private static Next push(Model model, String branch, PushMode mode) {
        return Next.of(model.withPushing(with(model, branch)).withError(Option.none()),
                new Push(branch, mode));
    }

    private static Set<String> with(Model model, String branch) {
        return model.pushing().add(branch);
    }

    private static Set<String> without(Model model, String branch) {
        return model.pushing().remove(branch);
    }
}
