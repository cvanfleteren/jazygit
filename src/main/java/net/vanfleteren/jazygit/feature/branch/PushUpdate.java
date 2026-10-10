package net.vanfleteren.jazygit.feature.branch;

import net.vanfleteren.jazygit.feature.branch.BranchCmd.Push;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.state.Loadable.Loaded;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.Update.Next;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Pushing a branch.
 */
public final class PushUpdate {

    private PushUpdate() {
    }

    public static Next update(Model model, PushMsg msg) {
        return switch (msg) {
            case PushMsg.Requested(String branch) -> requested(model, branch);
            case PushMsg.ForceCancelled() -> Next.of(model.withForcePushTarget(Optional.empty()));
            case PushMsg.ForceConfirmed() -> model.forcePushTarget()
                    .map(branch -> push(model.withForcePushTarget(Optional.empty()), branch, true))
                    .orElseGet(() -> Next.of(model));
            // The branches then show the new ahead/behind counts.
            case PushMsg.Done(String branch) ->
                    Update.refresh(model.withPushing(without(model, branch)).withError(Optional.empty()));
            case PushMsg.Failed(String branch, String message) ->
                    Update.refresh(model.withPushing(without(model, branch))
                            .withError(Optional.of(Messages.get("error.push.failed", branch, message))));
        };
    }

    private static Next requested(Model model, String branch) {
        boolean known = model.branches() instanceof Loaded<List<Branch>>(List<Branch> branches)
                && branches.stream().anyMatch(b -> b.name().equals(branch));
        // A branch that is being pushed is not pushed again.
        if (!known || model.pushing().contains(branch)) {
            return Next.of(model);
        }
        // Pushing a diverged branch needs a force push, which the user must confirm.
        return diverged(model, branch)
                ? Next.of(model.withForcePushTarget(Optional.of(branch)).withError(Optional.empty()))
                : push(model, branch, false);
    }

    private static boolean diverged(Model model, String branch) {
        return model.branches() instanceof Loaded<List<Branch>>(List<Branch> branches)
                && branches.stream().anyMatch(b -> b.name().equals(branch) && b.behind() > 0);
    }

    private static Next push(Model model, String branch, boolean forceWithLease) {
        return Next.of(model.withPushing(with(model, branch)).withError(Optional.empty()),
                new Push(branch, forceWithLease));
    }

    private static Set<String> with(Model model, String branch) {
        return Stream.concat(model.pushing().stream(), Stream.of(branch)).collect(Collectors.toUnmodifiableSet());
    }

    private static Set<String> without(Model model, String branch) {
        return model.pushing().stream().filter(b -> !b.equals(branch)).collect(Collectors.toUnmodifiableSet());
    }
}
