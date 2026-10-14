package net.vanfleteren.jazygit.feature.branch;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.state.Model;

/**
 * Questions about the branches that several branch operations ask.
 */
final class Branches {

    private Branches() {
    }

    /**
     * The checked out branch and {@code other}, if {@code other} is a known branch other than the checked out
     * one: what merging and rebasing work with.
     */
    static Option<Tuple2<String, String>> checkedOutAnd(Model model, String other) {
        return model.currentBranch()
                .filter(current -> !current.name().equals(other))
                .flatMap(current -> model.idleBranch(current.name()))
                .flatMap(current -> model.idleBranch(other).map(found -> Tuple.of(current.name(), found.name())));
    }

    /**
     * Whether {@code name} is a known branch that is not checked out, and that no operation runs on.
     */
    static boolean isOtherIdleBranch(Model model, String name) {
        return model.idleBranch(name).exists(branch -> !branch.current());
    }
}
