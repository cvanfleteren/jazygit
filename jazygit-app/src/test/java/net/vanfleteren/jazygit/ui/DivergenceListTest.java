package net.vanfleteren.jazygit.ui;

import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.git.model.*;
import net.vanfleteren.jazygit.state.*;
import org.junit.jupiter.api.Test;

class DivergenceListTest {

    @Test
    void aDivergenceSaysWhenABranchHasNoCommitsOfItsOwnOrMoreThanAreListed() {
        DivergenceView view = DivergenceView.loading(
                new Branch.Name("feature"), new BranchRef.Local(new Branch.Name("main")), DivergenceView.Against.BASE);
        Commit ours = new Commit(new Sha("a1"), "Ada", "a@x", java.time.Instant.EPOCH, "Ours", "");

        List<String> rows = DivergenceList.entries(
                        new Divergence(new Divergence.Side(List.of(ours), 400), new Divergence.Side(List.of(), 0)))
                .map(entry -> DivergenceList.row(view, entry))
                .map(LoadableList.Row::text);

        assertEquals(List.of("── main", "  no commits", "── feature", "↑ a1 Ad ○ Ours", "  … 399 more"), rows);
    }
}
