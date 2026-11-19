package net.vanfleteren.jazygit.settings;

import io.vavr.collection.List;
import io.vavr.control.Option;

/**
 * A diff filter as a {@code [[diff-filters]]} table writes it, built in or the user's (ADR 144): only read, its patterns
 * and languages not yet checked, which the git model does.
 *
 * @param id        what the settings name it by, and what a filter of the user replaces a built-in one by
 * @param name      what it is shown as, if it has a name besides its id
 * @param languages the languages of the files it applies to, in lower case
 * @param paths     the globs of the files it applies to besides
 * @param lines     the regexes a changed line matches it by; none when it matches every line
 * @param builtIn   whether it comes with jazygit, rather than from the user's settings file
 */
public record FilterDefinition(
        String id,
        Option<String> name,
        List<String> languages,
        List<String> paths,
        Option<List<String>> lines,
        boolean builtIn) {}
