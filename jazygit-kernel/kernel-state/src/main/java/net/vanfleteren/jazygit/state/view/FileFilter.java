package net.vanfleteren.jazygit.state.view;

import io.vavr.control.Option;

/**
 * The filter of a tree of files in the files pane, which shows only the files whose path contains it (ADR 105).
 *
 * @param text   the filter as typed; empty only while the line to type it in is open
 * @param before the filter from before the line was opened, empty when there was none, while the line is open
 */
public record FileFilter(String text, Option<String> before) {

    /**
     * The filter {@code current} with the line to type it in opened, showing it.
     */
    public static FileFilter opened(Option<FileFilter> current) {
        String text = current.map(FileFilter::text).getOrElse("");
        return new FileFilter(text, Option.of(text));
    }

    /**
     * Whether the line to type the filter in is open.
     */
    public boolean typing() {
        return before.isDefined();
    }

    /**
     * What the files are filtered on: nothing while the line holds no text.
     */
    public Option<String> shown() {
        return Option.when(!text.isEmpty(), text);
    }

    /**
     * What {@code filter} filters the files on, if there is one.
     */
    public static Option<String> shown(Option<FileFilter> filter) {
        return filter.flatMap(FileFilter::shown);
    }
}
