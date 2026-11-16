package net.vanfleteren.jazygit.branches.worktree.add;

import io.vavr.collection.LinkedHashMap;
import io.vavr.collection.Map;
import io.vavr.control.Option;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.vanfleteren.jazygit.git.model.Worktree;

/**
 * Where a new worktree starts out, worked out from a template of the git configuration (ADR 134): its variables in
 * braces filled in, and a relative location resolved against the directory the worktrees go next to. The paths are
 * only worked with as text: nothing on disk is looked at.
 */
final class WorktreeTemplate {

    /**
     * Without a template, next to the main worktree: named after it and the branch.
     */
    static final String NEXT_TO_MAIN = "{repo}-{branch}";

    /**
     * Without a template, in a bare layout: named after the branch only.
     */
    static final String NEXT_TO_SHOWN = "{branch}";

    private static final Pattern VARIABLE = Pattern.compile("\\{(\\w+)}");

    private WorktreeTemplate() {}

    /**
     * Where {@code template} leads, or without one, where a new worktree starts out by default: next to {@code main},
     * named after it and the branch, or without a main worktree, as next to a bare repository, next to
     * {@code current}, named after the branch. A relative location is resolved against the directory {@code main} is
     * in, or else {@code current}; none without either. {@code {repo}} is the name of {@code main}, or else of the
     * directory {@code current} is in; the other variables are filled in from {@code values}.
     */
    static Option<String> location(
            Option<String> template, Option<Worktree> main, Option<Worktree> current, Map<String, String> values) {
        Option<Path> base = main.orElse(current)
                .flatMap(worktree -> Option.of(worktree.path().getParent()));
        Map<String, String> withRepo = main.map(Worktree::name)
                .orElse(() -> base.flatMap(dir -> Option.of(dir.getFileName())).map(Path::toString))
                .<Map<String, String>>map(repo -> LinkedHashMap.of("repo", repo))
                .getOrElse(LinkedHashMap.empty())
                .merge(values);
        return resolved(filled(template.getOrElse(main.isDefined() ? NEXT_TO_MAIN : NEXT_TO_SHOWN), withRepo), base);
    }

    /**
     * {@code template} with each variable that {@code values} has replaced, once: what it is replaced with is not
     * looked at again. One it has not, like a brace without a variable, is left as it is written.
     */
    static String filled(String template, Map<String, String> values) {
        return VARIABLE.matcher(template)
                .replaceAll(match ->
                        Matcher.quoteReplacement(values.get(match.group(1)).getOrElse(match.group())));
    }

    /**
     * Where {@code filled} leads: an absolute location as it is, a relative one resolved against {@code base}; none
     * for a relative one without a base.
     */
    private static Option<String> resolved(String filled, Option<Path> base) {
        Path location = Path.of(filled);
        return location.isAbsolute()
                ? Option.of(location.normalize().toString())
                : base.map(dir -> dir.resolve(location).normalize().toString());
    }
}
