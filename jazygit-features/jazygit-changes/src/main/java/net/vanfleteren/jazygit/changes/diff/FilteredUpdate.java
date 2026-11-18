package net.vanfleteren.jazygit.changes.diff;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.state.AreaId;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Update.Next;
import net.vanfleteren.jazygit.state.diff.Anchor;
import net.vanfleteren.jazygit.state.diff.DiffCursorAt;
import net.vanfleteren.jazygit.state.diff.Opened;
import net.vanfleteren.jazygit.state.diff.ScrolledContent;

/**
 * Opening the hunks the diff filters hide, and hiding them again (ADR 144). What is opened is kept for the area, by the
 * path of each hunk and where it starts in the old file and the new one, as git gave it.
 */
public final class FilteredUpdate {

    private FilteredUpdate() {}

    public static Next update(Model model, FilteredMsg msg) {
        return switch (msg) {
            case FilteredMsg.Opened(DiffCursorAt at)
            when at.cursor() instanceof Anchor.Filtered marker ->
                Next.of(changed(
                        model,
                        at,
                        opened ->
                                opened.appendAll(marker.starts().map(start -> new Opened.Hunk(marker.path(), start)))));
            case FilteredMsg.Opened _ -> Next.of(model);
            case FilteredMsg.Closed(DiffCursorAt at)
            when at.cursor() instanceof Anchor.Filtered marker ->
                Next.of(changed(
                        model,
                        at,
                        opened ->
                                opened.removeAll(marker.starts().map(start -> new Opened.Hunk(marker.path(), start)))));
            case FilteredMsg.Closed _ -> Next.of(model);
        };
    }

    /**
     * {@code model} with the hunks opened in the area {@code at} is in as {@code change} makes them.
     */
    private static Model changed(
            Model model, DiffCursorAt at, java.util.function.UnaryOperator<List<Opened.Hunk>> change) {
        AreaId area = at.area();
        ScrolledContent content = at.at().content();
        Opened opened = model.repoView().opened(area, content).getOrElse(() -> Opened.of(content));
        return model.mapRepoView(view -> view.withOpened(
                area,
                new Opened(
                        opened.content(), change.apply(opened.hunks().toList()).toSet())));
    }
}
