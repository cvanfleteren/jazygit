package net.vanfleteren.jazygit.ui;

import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Duration;
import net.vanfleteren.jazygit.git.model.*;
import org.junit.jupiter.api.Test;

class LocalBranchesListTest {

    @Test
    void theCurrentBranchStandsOutByTheStyleOfItsNameAlone() {
        LoadableList.Row current = LocalBranchesList.row(
                Fixtures.branch(new BranchName("main"), true, new Sha("1")),
                Duration.ofMinutes(5),
                List.empty(),
                Option.none());
        LoadableList.Row other = LocalBranchesList.row(
                Fixtures.branch(new BranchName("feature"), false, new Sha("2")),
                Duration.ofMinutes(5),
                List.empty(),
                Option.none());

        assertEquals("  5m main", current.text());
        assertEquals(
                List.of(Option.none(), Option.of(Theme.Classes.CURRENT)),
                current.segs().map(LoadableList.Seg::cssClass));
        assertEquals("  5m feature", other.text());
        assertEquals(List.of(Option.none(), Option.none()), other.segs().map(LoadableList.Seg::cssClass));
    }
}
