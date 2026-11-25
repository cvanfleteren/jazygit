package net.vanfleteren.jazygit.features.branches.branch.newbranch;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.core.git.model.Fixtures;
import net.vanfleteren.jazygit.core.git.model.SampleData;
import net.vanfleteren.jazygit.core.repo.ReflogEntry;
import net.vanfleteren.jazygit.core.repo.ReflogEntry.Action;
import net.vanfleteren.jazygit.core.repo.ReflogEntry.CommitKind;
import net.vanfleteren.jazygit.core.repo.Revision;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.features.changes.reflog.ReflogView;
import net.vanfleteren.jazygit.kernel.state.Loadable;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.TestModels;
import org.junit.jupiter.api.Test;

/**
 * A new branch at an entry of the reflog starts out named after the branch HEAD left there, when the reflog says and
 * no branch has that name now (ADR 194).
 */
class NewBranchAtEntryTest {

    private static final Sha MAIN_TIP = new Sha("a".repeat(40));
    private static final Sha LEFT_TIP = new Sha("b".repeat(40));
    private static final Model LOADED = TestModels.loaded(SampleData.git());

    @Test
    void atTheEntryHeadLeftABranchFromTheNameStartsOutAsThatBranch() {
        Model model = withReflog(
                entry(0, "checkout: moving from gone to main", new Action.Checkout("gone", "main"), MAIN_TIP),
                commit(1, "Add the parser", LEFT_TIP));

        assertEquals(
                Option.of(new NewBranchPopup(new Revision.Commit(LEFT_TIP), Option.none()).withText("gone")),
                NameAsked.asked(model, new NewBranchMsg.RequestedAtEntry(entryAt(model, 1)), Option.of("wip/"))
                        .popup());
    }

    @Test
    void aBranchThatIsThereStillStartsOutAsTheTemplateSays() {
        Model model = withReflog(
                entry(0, "checkout: moving from main to x", new Action.Checkout("main", "x"), MAIN_TIP),
                commit(1, "Add the parser", LEFT_TIP));

        assertEquals(
                Option.of(new NewBranchPopup(new Revision.Commit(LEFT_TIP), Option.none()).withText("wip/")),
                NameAsked.asked(model, new NewBranchMsg.RequestedAtEntry(entryAt(model, 1)), Option.of("wip/"))
                        .popup());
    }

    @Test
    void anEntryNoCheckoutLeftFromStartsOutAsTheTemplateSays() {
        Model model = withReflog(commit(0, "Add more", MAIN_TIP), commit(1, "Add the parser", LEFT_TIP));

        assertEquals(
                Option.of(new NewBranchPopup(new Revision.Commit(LEFT_TIP), Option.none()).withText("wip/")),
                NameAsked.asked(model, new NewBranchMsg.RequestedAtEntry(entryAt(model, 1)), Option.of("wip/"))
                        .popup());
        assertEquals(
                Option.of(new NewBranchPopup(new Revision.Commit(MAIN_TIP), Option.none())),
                NameAsked.asked(model, new NewBranchMsg.RequestedAtEntry(entryAt(model, 0)))
                        .popup());
    }

    private static Model withReflog(ReflogEntry... entries) {
        return LOADED.withPart(ReflogView.PART, ReflogView.NONE.withEntries(new Loadable.Loaded<>(List.of(entries))));
    }

    private static ReflogEntry entryAt(Model model, int place) {
        return model.part(ReflogView.PART).entries().getOrElse(List.empty()).get(place);
    }

    private static ReflogEntry commit(int place, String message, Sha sha) {
        return entry(place, "commit: " + message, new Action.Committed(CommitKind.PLAIN, message), sha);
    }

    private static ReflogEntry entry(int place, String subject, Action action, Sha sha) {
        return new ReflogEntry(
                place,
                Instant.ofEpochSecond(1_000 - place),
                subject,
                action,
                Fixtures.commit(sha, "Ada", "ada@example.com", Instant.EPOCH, "c", "", List.empty()));
    }
}
