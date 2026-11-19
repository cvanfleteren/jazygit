package net.vanfleteren.jazygit.features.changes.diff;

import io.vavr.collection.Map;
import io.vavr.control.Option;
import java.util.function.BiFunction;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Update.Next;
import net.vanfleteren.jazygit.kernel.state.diff.Anchor;
import net.vanfleteren.jazygit.kernel.state.diff.DiffCursorAt;
import net.vanfleteren.jazygit.kernel.state.diff.Rendered;
import net.vanfleteren.jazygit.kernel.state.diff.Tree;
import net.vanfleteren.jazygit.kernel.state.diff.TreeFolds;
import net.vanfleteren.jazygit.kernel.state.diff.Version;

/**
 * Opening and closing the nodes of a version of JSON, YAML or XML rendered (ADR 122, 123): what the user chose for a node is
 * kept by its path with the version, so the new version and the original share it, and a version read again keeps it.
 * Nothing happens where the area shows no version of JSON, YAML or XML rendered, or the cursor is on no node.
 */
public final class FoldUpdate {

    private FoldUpdate() {}

    public static Next update(Model model, FoldMsg msg) {
        return switch (msg) {
            case FoldMsg.Toggled(DiffCursorAt at) ->
                chosen(
                        model,
                        at,
                        (version, nodes) -> at.cursor() instanceof Anchor.Node node
                                ? nodes.get(node.pointer())
                                        .map(tree -> version.nodes()
                                                .put(
                                                        node.pointer(),
                                                        !TreeFolds.open(version.nodes(), node.pointer(), tree)))
                                : Option.none());
            case FoldMsg.AllToggled(DiffCursorAt at) ->
                chosen(model, at, (version, all) -> {
                    // The root element of XML is left as it is: z acts on what is below it (ADR 151).
                    Map<String, Tree> nodes = all.filter((pointer, tree) -> !TreeFolds.root(pointer, tree));
                    boolean anyOpen = nodes.exists(node -> TreeFolds.open(version.nodes(), node._1, node._2));
                    return Option.when(
                            !nodes.isEmpty(),
                            () -> nodes.keySet()
                                    .foldLeft(version.nodes(), (done, pointer) -> done.put(pointer, !anyOpen)));
                });
        };
    }

    /**
     * {@code model} with the nodes of the version of JSON, YAML or XML rendered the cursor {@code at} is in as {@code choose}
     * says, given the version and every node of it that folds, by its path; as it was when there is no such version, or
     * {@code choose} gives none.
     */
    private static Next chosen(
            Model model, DiffCursorAt at, BiFunction<Version, Map<String, Tree>, Option<Map<String, Boolean>>> choose) {
        return model.repoView()
                .version(at.area(), at.at().content())
                .filter(version -> version.rendering().isDefined())
                .flatMap(version -> Rendered.of(version.path())
                        .filter(Rendered::tree)
                        .flatMap(kind -> version.text()
                                .flatMap(lines ->
                                        Tree.read(kind, lines.mkString("\n")).toOption()))
                        .flatMap(documents -> choose.apply(version, TreeFolds.nodes(documents)))
                        .map(nodes -> model.mapRepoView(view -> view.withVersion(at.area(), version.withNodes(nodes)))))
                .map(Next::of)
                .getOrElse(() -> Next.of(model));
    }
}
