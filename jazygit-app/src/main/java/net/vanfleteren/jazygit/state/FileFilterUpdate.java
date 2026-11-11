package net.vanfleteren.jazygit.state;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.state.Update.Next;

/**
 * Filtering the tree the files pane shows (ADR 105): the files of the changes open, or else those of the working
 * tree. The line above it holds the filter while it is typed, and the tree shows only the files that match as it
 * changes. Closing the line keeps it, until it is cleared.
 */
public final class FileFilterUpdate {

    private FileFilterUpdate() {}

    public static Next update(Model model, FileFilterMsg msg) {
        Option<FileFilter> current = shown(model);
        Option<FileFilter> next = switch (msg) {
            case FileFilterMsg.Opened() -> Option.of(FileFilter.opened(current));
            case FileFilterMsg.Edited(String text) ->
                current.map(filter -> filter.typing() ? new FileFilter(text, filter.before()) : filter);
            case FileFilterMsg.Cancelled() ->
                current.flatMap(filter -> filter.before()
                        .map(before -> Option.when(!before.isEmpty(), new FileFilter(before, Option.none())))
                        .getOrElse(Option.of(filter)));
            case FileFilterMsg.Submitted() ->
                current.flatMap(filter -> filter.shown().map(text -> new FileFilter(text, Option.none())));
            case FileFilterMsg.Cleared() -> Option.none();
        };
        return Next.of(with(model, next));
    }

    /**
     * The filter of the tree the files pane shows.
     */
    public static Option<FileFilter> shown(Model model) {
        return model.repoView()
                .commitFiles()
                .map(CommitFilesView::filter)
                .getOrElse(() -> model.repoView().filesFilter());
    }

    /**
     * What the files of the working tree are filtered on, if anything: a directory then stands for only the files
     * shown under it.
     */
    public static Option<String> workingTree(Model model) {
        return FileFilter.shown(model.repoView().filesFilter());
    }

    private static Model with(Model model, Option<FileFilter> filter) {
        return model.mapRepoView(repoView -> repoView.commitFiles().isDefined()
                ? repoView.withCommitFiles(repoView.commitFiles().map(view -> view.withFilter(filter)))
                : repoView.withFilesFilter(filter));
    }
}
