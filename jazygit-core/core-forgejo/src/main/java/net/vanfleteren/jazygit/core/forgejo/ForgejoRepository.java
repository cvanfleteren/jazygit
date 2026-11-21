package net.vanfleteren.jazygit.core.forgejo;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Option;
import io.vavr.control.Try;
import java.net.URI;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A repository on a host that may be Forgejo, as a git remote names it (ADR 174).
 *
 * @param base  where the web interface and the API of the host are, as {@code https://code.example.net}: for an ssh
 *              remote, fj's alias of its host, else that host on the https port
 * @param owner the user or organisation the repository is of
 * @param name  the name of the repository, without {@code .git}
 */
public record ForgejoRepository(String base, String owner, String name) {

    // [user@]host:owner/name as scp writes it: no scheme, and a colon before the first slash.
    private static final Pattern SCP_LIKE = Pattern.compile("^(?:[^@/]+@)?([^:/]+):(.+)$");

    /**
     * Hosts that are known to be another forge, which are not asked whether they are Forgejo.
     */
    private static final List<String> OTHER_FORGES = List.of("github.com", "gitlab.com", "bitbucket.org");

    /**
     * The repository the git remote {@code url} names, with {@code aliases} from fj's keys, from an ssh host (with
     * its port, when not 22) to the host of the web interface; none for a local path, a host of another forge, or a
     * URL without an owner and a name.
     */
    public static Option<ForgejoRepository> of(String url, Map<String, String> aliases) {
        String stripped = url.strip();
        if (stripped.contains("://")) {
            return Try.of(() -> URI.create(stripped))
                    .toOption()
                    .filter(uri -> uri.getHost() != null && !uri.getHost().isEmpty())
                    .flatMap(uri -> switch (uri.getScheme().toLowerCase(Locale.ROOT)) {
                        case "https", "http" ->
                            ownerAndName(uri.getPath())
                                    .map(path -> new ForgejoRepository(
                                            uri.getScheme().toLowerCase(Locale.ROOT) + "://"
                                                    + uri.getRawAuthority()
                                                            .substring(uri.getRawAuthority()
                                                                            .indexOf('@')
                                                                    + 1),
                                            path._1(),
                                            path._2()));
                        case "ssh" -> ssh(uri.getHost(), uri.getPort(), uri.getPath(), aliases);
                        default -> Option.none();
                    })
                    .filter(ForgejoRepository::askable);
        }
        Matcher scp = SCP_LIKE.matcher(stripped);
        return scp.matches()
                ? ssh(scp.group(1), -1, scp.group(2), aliases).filter(ForgejoRepository::askable)
                : Option.none();
    }

    /**
     * The host of the web interface, with its port when it has one: what fj keeps its token under.
     */
    public String host() {
        return base.substring(base.indexOf("://") + 3);
    }

    private boolean askable() {
        String host = host().toLowerCase(Locale.ROOT);
        return OTHER_FORGES.forAll(other -> !host.equals(other) && !host.endsWith("." + other));
    }

    private static Option<ForgejoRepository> ssh(String host, int port, String path, Map<String, String> aliases) {
        String withPort = port > 0 && port != 22 ? host + ":" + port : host;
        String web = aliases.get(withPort).orElse(aliases.get(host)).getOrElse(host);
        return ownerAndName(path).map(parts -> new ForgejoRepository("https://" + web, parts._1(), parts._2()));
    }

    /**
     * The owner and the name a path ends in, as {@code /owner/name.git}.
     */
    private static Option<Tuple2<String, String>> ownerAndName(String path) {
        List<String> parts = List.of(path.split("/")).reject(String::isEmpty);
        return Option.when(parts.size() >= 2, () -> {
            String name = parts.last();
            return Tuple.of(
                    parts.get(parts.size() - 2), name.endsWith(".git") ? name.substring(0, name.length() - 4) : name);
        });
    }
}
