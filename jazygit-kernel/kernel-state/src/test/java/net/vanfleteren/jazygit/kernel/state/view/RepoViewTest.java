package net.vanfleteren.jazygit.kernel.state.view;

import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.HashMap;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.git.model.ChangeType;
import net.vanfleteren.jazygit.core.git.model.CommitFile;
import net.vanfleteren.jazygit.core.git.model.DiffOptions;
import net.vanfleteren.jazygit.core.git.model.DiffText;
import net.vanfleteren.jazygit.core.git.model.FileVersion;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.kernel.state.AreaId;
import net.vanfleteren.jazygit.kernel.state.Loadable;
import net.vanfleteren.jazygit.kernel.state.diff.Expansion;
import net.vanfleteren.jazygit.kernel.state.diff.ScrolledContent;
import net.vanfleteren.jazygit.kernel.state.diff.Version;
import net.vanfleteren.jazygit.kernel.state.diff.WholeFile;
import net.vanfleteren.jazygit.kernel.state.load.ReadVersion;
import org.junit.jupiter.api.Test;

/**
 * The version and the expansion an area keeps share one {@link AreaView}: dropping one of them keeps the other, and an
 * area left with neither is dropped.
 */
class RepoViewTest {

    private static final ScrolledContent CONTENT = new ScrolledContent.StashChanges(
            new Sha("5555"),
            DiffText.of("diff --git a/a.txt b/a.txt\n--- a/a.txt\n+++ b/a.txt\n@@ -1 +1 @@\n-a\n+A\n"));
    private static final Version VERSION =
            new Version(CONTENT, "a.txt", Option.of(FileVersion.NEW), read().file(), Loadable.loading());
    private static final Expansion EXPANSION = Expansion.of(CONTENT);

    @Test
    void droppingTheVersionsKeepsTheExpansions() {
        RepoView dropped = RepoView.INITIAL
                .withVersion(AreaId.STASH, VERSION)
                .withExpansion(AreaId.STASH, EXPANSION)
                .withoutVersions();

        assertEquals(HashMap.of(AreaId.STASH, EXPANSION), dropped.expansions());
        assertTrue(dropped.versions().isEmpty());
    }

    @Test
    void droppingTheExpansionsKeepsTheVersions() {
        RepoView dropped = RepoView.INITIAL
                .withVersion(AreaId.STASH, VERSION)
                .withExpansion(AreaId.STASH, EXPANSION)
                .withoutExpansions();

        assertEquals(HashMap.of(AreaId.STASH, VERSION), dropped.versions());
        assertTrue(dropped.expansions().isEmpty());
    }

    @Test
    void anAreaLeftWithNothingIsDropped() {
        assertTrue(RepoView.INITIAL
                .withVersion(AreaId.STASH, VERSION)
                .withoutVersions()
                .areas()
                .isEmpty());
        assertTrue(RepoView.INITIAL
                .withExpansion(AreaId.STASH, EXPANSION)
                .withoutExpansions()
                .areas()
                .isEmpty());
    }

    @Test
    void anAreaKeepingOneOfThemStays() {
        RepoView dropped = RepoView.INITIAL
                .withVersion(AreaId.STASH, VERSION)
                .withExpansion(AreaId.COMMIT, EXPANSION)
                .withoutVersions();

        assertEquals(HashMap.of(AreaId.COMMIT, AreaView.INITIAL.withExpanded(Option.of(EXPANSION))), dropped.areas());
    }

    private static ReadVersion read() {
        return ReadVersion.of(
                AreaId.STASH,
                CONTENT,
                new WholeFile.Source.OfStash(
                        new Sha("5555"), new CommitFile("a.txt", ChangeType.MODIFIED, Option.none())),
                "a.txt",
                DiffOptions.DEFAULT);
    }
}
