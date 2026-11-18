package net.vanfleteren.jazygit.branches.branch.stack;

import io.vavr.Tuple;
import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.state.Cmd;
import net.vanfleteren.jazygit.state.Env;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.Label;
import net.vanfleteren.jazygit.state.Msg;

/**
 * Reads which of {@code eligible} are built on {@code top}, the top of stack {@code stack}, by their history, before
 * "Add to stack…" lists them (ADR 148): the local branches that have its tip, then, for each of those, the ones that
 * have that one's tip, which places it on the nearest. Without them, the dialog lists the branches built on it by
 * their parents only.
 *
 * @param eligible the branches that may be found, with their tips: those with no parent, no root, not at its tip
 */
public record ReadBuiltOn(int stack, BranchName top, Sha tip, Map<BranchName, Sha> eligible) implements Cmd {

    @Override
    public Option<Label> label() {
        return Option.of(Label.of("branches.work.builtOn", top.value()));
    }

    @Override
    public Msg run(Env env) {
        return env.git()
                .branches()
                .containing(tip)
                .flatMap(onTop -> {
                    List<BranchName> found = onTop.filter(eligible::containsKey);
                    return Either.sequenceRight(found.map(branch -> env.git()
                                    .branches()
                                    .containing(eligible.get(branch).get())
                                    .map(having -> Tuple.of(branch, having))))
                            .map(each -> HashMap.ofEntries(each).put(top, onTop));
                })
                .fold(error -> failed(Failure.of(error)), this::read);
    }

    @Override
    public Msg failed(Failure failure) {
        return new StackMsg.BuiltOnRead(stack, top, eligible.keySet(), HashMap.empty());
    }

    private Msg read(Map<BranchName, List<BranchName>> containing) {
        return new StackMsg.BuiltOnRead(stack, top, eligible.keySet(), containing);
    }
}
