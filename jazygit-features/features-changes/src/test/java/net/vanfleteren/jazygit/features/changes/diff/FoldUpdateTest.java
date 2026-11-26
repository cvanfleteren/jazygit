package net.vanfleteren.jazygit.features.changes.diff;

import static net.vanfleteren.jazygit.kernel.state.TestModels.loaded;
import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.git.model.DiffText;
import net.vanfleteren.jazygit.core.git.model.FileVersion;
import net.vanfleteren.jazygit.core.repo.RepoPath;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.kernel.state.AreaId;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.TestModels;
import net.vanfleteren.jazygit.kernel.state.Update;
import net.vanfleteren.jazygit.kernel.state.Update.Next;
import net.vanfleteren.jazygit.kernel.state.diff.Anchor;
import net.vanfleteren.jazygit.kernel.state.diff.DiffCursorAt;
import net.vanfleteren.jazygit.kernel.state.diff.DiffSide;
import net.vanfleteren.jazygit.kernel.state.diff.Scrolled;
import net.vanfleteren.jazygit.kernel.state.diff.ScrolledContent;
import net.vanfleteren.jazygit.kernel.state.diff.Version;
import net.vanfleteren.jazygit.kernel.state.load.LoadMsg;
import net.vanfleteren.jazygit.kernel.state.load.ReadVersion;
import org.junit.jupiter.api.Test;

/**
 * Opening and closing the nodes of a version of JSON, YAML or XML rendered, Enter and {@code z} (ADR 122, 123).
 */
class FoldUpdateTest {

    private static final AreaId AREA = new AreaId("stash-changes");
    private static final Sha STASH = new Sha("5555");
    private static final DiffText CHANGES = DiffText.of("diff --git a/a.json b/a.json\n--- a/a.json\n+++ b/a.json\n"
            + "@@ -1,7 +1,7 @@\n"
            + " {\n"
            + "   \"few\": {\n"
            + "-    \"a\": [1],\n"
            + "+    \"a\": [2],\n"
            + "     \"b\": [2]\n"
            + "   },\n"
            + "   \"many\": [" + List.range(0, 25).mkString(", ") + "]\n"
            + " }\n");
    private static final Anchor.OnLine ADDED = new Anchor.OnLine(new RepoPath("a.json"), DiffSide.NEW, 3);
    private static final Anchor.Node FEW =
            new Anchor.Node(new RepoPath("a.json"), "/few", new Anchor.OnLine(new RepoPath("a.json"), DiffSide.NEW, 2));
    private static final Anchor.Node MANY = new Anchor.Node(
            new RepoPath("a.json"), "/many", new Anchor.OnLine(new RepoPath("a.json"), DiffSide.NEW, 6));
    private static final DiffText MANIFESTS = DiffText.of("diff --git a/a.yml b/a.yml\n--- a/a.yml\n+++ b/a.yml\n"
            + "@@ -1,6 +1,6 @@\n"
            + " kind: Job\n"
            + " run: |\n"
            + "-  old\n"
            + "+  new\n"
            + " ---\n"
            + " kind: Service\n"
            + " port: 80\n");
    private static final Anchor.Node RUN =
            new Anchor.Node(new RepoPath("a.yml"), "/0/run", new Anchor.OnLine(new RepoPath("a.yml"), DiffSide.NEW, 2));

    @Test
    void enterClosesAnOpenNode() {
        Model folded = Update.update(rendered(), new FoldMsg.Toggled(at(FEW))).model();

        assertEquals(HashMap.of("/few", false), nodes(folded));
    }

    @Test
    void enterOpensANodeThatStartsClosed() {
        Model folded = Update.update(rendered(), new FoldMsg.Toggled(at(MANY))).model();

        assertEquals(HashMap.of("/many", true), nodes(folded));
    }

    @Test
    void enterTwiceKeepsWhatWasChosenLast() {
        Model once = Update.update(rendered(), new FoldMsg.Toggled(at(FEW))).model();
        Model twice = Update.update(once, new FoldMsg.Toggled(at(FEW))).model();

        assertEquals(HashMap.of("/few", true), nodes(twice));
    }

    @Test
    void zClosesEveryNodeWhileAnyIsOpenAndThenOpensThemAll() {
        Model closed =
                Update.update(rendered(), new FoldMsg.AllToggled(at(FEW))).model();
        assertEquals(HashMap.of("/few", false, "/many", false), nodes(closed));

        Model open = Update.update(closed, new FoldMsg.AllToggled(at(FEW))).model();
        assertEquals(HashMap.of("/few", true, "/many", true), nodes(open));
    }

    @Test
    void aRowThatIsNoNodeIsLeftAsItIs() {
        Model model = rendered();
        Anchor.Node scalar = new Anchor.Node(new RepoPath("a.json"), "/few/a", ADDED);

        assertSame(model, Update.update(model, new FoldMsg.Toggled(at(scalar))).model());
    }

    @Test
    void aVersionShownAsSourceDoesNotFold() {
        Model source =
                Update.update(rendered(), new VersionMsg.RenderToggled(at(FEW))).model();

        assertSame(source, Update.update(source, new FoldMsg.Toggled(at(FEW))).model());
    }

    @Test
    void theOriginalSharesWhatWasChosen() {
        Model folded = Update.update(rendered(), new FoldMsg.Toggled(at(FEW))).model();

        Model original = Update.update(folded, new VersionMsg.Cycled(at(FEW))).model();

        assertEquals(Option.of(FileVersion.ORIGINAL), version(original).flatMap(Version::rendering));
        assertEquals(HashMap.of("/few", false), nodes(original));
        // The cursor stays on the node it was on.
        assertEquals(Option.of(FEW), original.scrolled().get(AREA).flatMap(Scrolled::cursor));
    }

    @Test
    void showingTheSourcePutsTheCursorOnTheLineOfItsRow() {
        Model source = Update.update(rendered(), new VersionMsg.RenderToggled(at(MANY)))
                .model();

        assertEquals(
                Option.of(new Anchor.OnLine(new RepoPath("a.json"), DiffSide.NEW, 6)),
                source.scrolled().get(AREA).flatMap(Scrolled::cursor));
    }

    @Test
    void aBlockOfYamlAndADocumentFoldAsANodeOfJsonDoes() {
        Model yaml = rendered(MANIFESTS, new Anchor.OnLine(new RepoPath("a.yml"), DiffSide.NEW, 3));

        Model folded =
                Update.update(yaml, new FoldMsg.Toggled(at(RUN, MANIFESTS))).model();
        assertEquals(HashMap.of("/0/run", false), nodes(folded));

        Model closed =
                Update.update(yaml, new FoldMsg.AllToggled(at(RUN, MANIFESTS))).model();
        assertEquals(HashMap.of("/0", false, "/0/run", false, "/1", false), nodes(closed));
    }

    @Test
    void theRootElementOfXmlFoldsByEnterButZLeavesItAsItIs() {
        DiffText pom = DiffText.of("diff --git a/a.xml b/a.xml\n--- a/a.xml\n+++ b/a.xml\n"
                + "@@ -1,6 +1,6 @@\n"
                + " <project>\n"
                + "   <dependencies>\n"
                + "-    <dependency><id>a</id></dependency>\n"
                + "+    <dependency><id>b</id></dependency>\n"
                + "   </dependencies>\n"
                + " </project>\n");
        Anchor.Node project = new Anchor.Node(
                new RepoPath("a.xml"), "/project", new Anchor.OnLine(new RepoPath("a.xml"), DiffSide.NEW, 1));
        Model xml = rendered(pom, new Anchor.OnLine(new RepoPath("a.xml"), DiffSide.NEW, 3));

        Model closed =
                Update.update(xml, new FoldMsg.AllToggled(at(project, pom))).model();
        assertEquals(
                HashMap.of("/project/dependencies", false, "/project/dependencies/dependency", false), nodes(closed));

        Model folded = Update.update(xml, new FoldMsg.Toggled(at(project, pom))).model();
        assertEquals(HashMap.of("/project", false), nodes(folded));
    }

    /**
     * The new version of {@code a.json} shown rendered, read whole.
     */
    private static Model rendered() {
        return rendered(CHANGES, ADDED);
    }

    /**
     * The new version of the file of {@code changes} shown rendered from the cursor on {@code on}, read whole.
     */
    private static Model rendered(DiffText changes, Anchor on) {
        Next asked = Update.update(loaded(), new VersionMsg.RenderToggled(at(on, changes)));
        ReadVersion read = (ReadVersion) asked.cmds().head();
        return TestModels.done(TestModels.running(asked.model(), read), read, new LoadMsg.VersionRead(read, changes))
                .model();
    }

    /**
     * The cursor on {@code anchor} in the changes of the stash.
     */
    private static DiffCursorAt at(Anchor anchor) {
        return at(anchor, CHANGES);
    }

    /**
     * The cursor on {@code anchor} in the stash, of the {@code changes}.
     */
    private static DiffCursorAt at(Anchor anchor, DiffText changes) {
        return new DiffCursorAt(
                AREA,
                new Scrolled(new ScrolledContent.StashChanges(STASH, changes), 0, 0, Option.of(anchor), Option.none()),
                anchor,
                Option.none(),
                Option.none(),
                Option.none());
    }

    private static Option<Version> version(Model model) {
        return model.repoView().versions().get(AREA);
    }

    private static Map<String, Boolean> nodes(Model model) {
        return version(model).get().nodes();
    }
}
