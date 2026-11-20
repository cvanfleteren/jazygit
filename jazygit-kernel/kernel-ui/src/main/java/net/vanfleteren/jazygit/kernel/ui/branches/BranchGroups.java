package net.vanfleteren.jazygit.kernel.ui.branches;

import dev.tamboui.tui.event.KeyEvent;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.ServiceLoader;
import net.vanfleteren.jazygit.core.repo.Branch;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.RemoteBranch;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.ui.keys.Keymap;
import net.vanfleteren.jazygit.kernel.ui.widgets.LoadableList;

/**
 * The groups the features make of the local branches in the branches pane (ADR 167): a header, the branches of the
 * group under it, indented, with rows of what belongs to the group but is no local branch among them; and the keys of
 * the header. The pane knows no feature, nor what a group is: the app gives the groupings of its features, in their
 * order, as a service, as it gives the keymap (ADR 136). A branch is in the group of the first feature that groups it.
 *
 * @param groupings how each feature groups the branches, in the order of the features
 */
public record BranchGroups(List<Grouping> groupings) {

    /**
     * No groups, as the tests of the views have them without the app.
     */
    public static final BranchGroups NONE = new BranchGroups(List.empty());

    /**
     * The groups the features make of {@code branches} in {@code model}, in the order of the features; a branch an
     * earlier group has is left out of the later ones.
     */
    public List<Group> of(Model model, List<Branch> branches) {
        return groupings
                .flatMap(grouping -> grouping.of(model, branches))
                .foldLeft(List.<Group>empty(), (kept, group) -> {
                    List<BranchName> taken = kept.flatMap(Group::members);
                    Group left = group.without(taken);
                    return left.members().isEmpty() ? kept : kept.append(left);
                });
    }

    /**
     * The groupings of the app, as declared by its features: those of the {@link Provider} on the classpath. Without
     * the app, as in the tests of the views, there are {@link #NONE}.
     */
    public static BranchGroups standard() {
        return Found.GROUPS;
    }

    /**
     * How a feature groups the local branches.
     */
    @FunctionalInterface
    public interface Grouping {

        /**
         * The groups the feature makes of {@code branches}, the local branches as the pane lists them, in
         * {@code model}; none for none.
         */
        List<Group> of(Model model, List<Branch> branches);
    }

    /**
     * A group of branches.
     *
     * @param key     what the group is known by: the highlight follows it, and the pane keeps it closed by it
     * @param title   its header, after whether it is open
     * @param after   what its header shows after the spinner of a busy branch of it, each apart
     * @param entries what is listed under its header, in order
     * @param keys    what the keys on its header act on
     */
    public record Group(
            String key, List<LoadableList.Seg> title, List<LoadableList.Seg> after, List<Entry> entries, Keys<?> keys) {

        /**
         * The local branches of the group, in its order.
         */
        public List<BranchName> members() {
            return entries.flatMap(
                    entry -> entry instanceof Entry.Member member ? List.of(member.branch()) : List.empty());
        }

        /**
         * This group without the branches {@code taken}.
         */
        Group without(List<BranchName> taken) {
            return new Group(
                    key,
                    title,
                    after,
                    entries.filter(entry -> !(entry instanceof Entry.Member member && taken.contains(member.branch()))),
                    keys);
        }
    }

    /**
     * What is listed under the header of a group.
     */
    public sealed interface Entry {

        /**
         * A local branch, listed as any other, indented.
         */
        record Member(BranchName branch) implements Entry {}

        /**
         * What belongs to the group but is no local branch here, on one dimmed line.
         *
         * @param key     what it is known by, so that the highlight follows it
         * @param text    its line, without the indent
         * @param log     the remote-tracking branch whose log it shows, if there is one
         * @param unknown what it is called when it has no log to show, as the content panel says it
         */
        record Elsewhere(String key, String text, Option<RemoteBranch> log, String unknown) implements Entry {}
    }

    /**
     * What the keys on the header of a group act on: {@code subject}, in {@code scope}, which the feature declares.
     */
    public record Keys<S>(Keymap.Scope<S> scope, S subject) {

        /**
         * What {@code event} does on the header, as {@code keymap} has the keys of its scope.
         */
        Option<Msg> handle(Keymap keymap, KeyEvent event) {
            return keymap.handle(scope, event, Option.of(subject));
        }
    }

    /**
     * Gives the groupings of the app's features, as a service.
     */
    public interface Provider {

        BranchGroups groups();
    }

    private static final class Found {

        private static final BranchGroups GROUPS = ServiceLoader.load(Provider.class)
                .findFirst()
                .map(Provider::groups)
                .orElse(NONE);
    }
}
