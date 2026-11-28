package net.vanfleteren.jazygit.core.git.branches;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.HashMap;
import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.collection.Set;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.git.process.GitOutput;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.RemoteName;

/**
 * What the git configuration says of the remotes of a repository, as far as the base remote and where a branch is
 * pushed go by it (ADR 215).
 *
 * @param remotes         the remotes it configures
 * @param checkoutDefault {@code checkout.defaultRemote}
 * @param pushDefault     {@code remote.pushDefault}
 * @param pushRemotes     {@code branch.<name>.pushRemote}, by the branch
 */
record RemoteConfig(
        Set<RemoteName> remotes,
        Option<RemoteName> checkoutDefault,
        Option<RemoteName> pushDefault,
        Map<BranchName, RemoteName> pushRemotes) {

    /**
     * Nothing configured.
     */
    static final RemoteConfig NONE = new RemoteConfig(HashSet.empty(), Option.none(), Option.none(), HashMap.empty());

    /**
     * The keys it reads, as {@code git config --get-regexp} matches them: git prints the section and the key in lower
     * case.
     */
    static final String KEYS =
            "^(checkout\\.defaultremote|remote\\.pushdefault|remote\\..*\\.(url|pushurl)|branch\\..*\\.pushremote)$";

    private static final String REMOTE = "remote.";
    private static final String BRANCH = "branch.";
    private static final String PUSH_REMOTE = ".pushremote";

    /**
     * The configuration in {@code raw}, the lines {@code <key> <value>} of {@code git config --get-regexp} for
     * {@link #KEYS}.
     */
    static RemoteConfig parse(String raw) {
        List<Line> lines = GitOutput.nonBlankLines(raw).map(Line::of);
        return new RemoteConfig(
                lines.flatMap(RemoteConfig::remote).toSet(),
                value(lines, "checkout.defaultremote"),
                value(lines, "remote.pushdefault"),
                HashMap.ofEntries(lines.flatMap(RemoteConfig::pushRemote)));
    }

    private static Option<RemoteName> value(List<Line> lines, String key) {
        return lines.find(line -> line.key().equals(key)).flatMap(line -> RemoteName.parse(line.value()));
    }

    private static Option<RemoteName> remote(Line line) {
        return Option.when(
                        line.key().startsWith(REMOTE)
                                && (line.key().endsWith(".url") || line.key().endsWith(".pushurl")),
                        () -> line.key().substring(REMOTE.length(), line.key().lastIndexOf('.')))
                .filter(name -> !name.isEmpty())
                .map(RemoteName::new);
    }

    private static Option<Tuple2<BranchName, RemoteName>> pushRemote(Line line) {
        return Option.when(
                        line.key().startsWith(BRANCH)
                                && line.key().endsWith(PUSH_REMOTE)
                                && line.key().length() > BRANCH.length() + PUSH_REMOTE.length(),
                        () -> line.key().substring(BRANCH.length(), line.key().length() - PUSH_REMOTE.length()))
                .flatMap(branch ->
                        RemoteName.parse(line.value()).map(remote -> Tuple.of(new BranchName(branch), remote)));
    }

    /**
     * A line of the output: its key, and its value.
     */
    private record Line(String key, String value) {

        static Line of(String line) {
            int space = line.indexOf(' ');
            return space < 0 ? new Line(line, "") : new Line(line.substring(0, space), line.substring(space + 1));
        }
    }
}
