package net.vanfleteren.jazygit.core.forgejo;

import static net.vanfleteren.jazygit.lib.json.Decoder.dict;
import static net.vanfleteren.jazygit.lib.json.Decoder.map2;
import static net.vanfleteren.jazygit.lib.json.Decoder.optionalField;
import static net.vanfleteren.jazygit.lib.json.Decoder.string;

import io.vavr.Tuple;
import io.vavr.collection.HashMap;
import io.vavr.collection.Map;
import io.vavr.control.Option;
import io.vavr.control.Try;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.vanfleteren.jazygit.lib.json.Decoder;
import net.vanfleteren.jazygit.lib.json.Json;

/**
 * What fj (forgejo-cli) keeps of the hosts it is logged in to, in its {@code keys.json} (ADR 174): a token per host
 * of a web interface, and aliases from the host of an ssh remote, with its port, to that of its web interface, as
 * {@code fj auth login} makes them.
 *
 * @param tokens  the token of each host
 * @param aliases the host of the web interface of each ssh host
 */
public record FjKeys(Map<String, String> tokens, Map<String, String> aliases) {

    /**
     * No keys, as without fj.
     */
    public static final FjKeys NONE = new FjKeys(HashMap.empty(), HashMap.empty());

    // A host of another kind than a token, as one logged in with OAuth, has no token to read.
    private static final Decoder<FjKeys> DECODER = map2(
            optionalField("hosts", dict(optionalField("token", string()))),
            optionalField("aliases", dict(string())),
            (hosts, aliases) -> new FjKeys(
                    hosts.<Map<String, String>>map(each -> HashMap.ofEntries(each.toList()
                                    .flatMap(host -> host._2().map(token -> Tuple.of(host._1(), token)))))
                            .getOrElse(HashMap::empty),
                    aliases.<Map<String, String>>map(each -> HashMap.ofEntries(each.toList()))
                            .getOrElse(HashMap::empty)));

    /**
     * The token for {@code host}, if fj has one.
     */
    public Option<String> token(String host) {
        return tokens.get(host);
    }

    /**
     * The keys in {@code text}, as fj writes them; none when it is not what fj writes.
     */
    public static Option<FjKeys> parse(String text) {
        return Json.parse(text).flatMap(DECODER::decode).toOption();
    }

    /**
     * The keys fj keeps in {@code file}; {@link #NONE} when there is no such file, or it cannot be read.
     */
    public static FjKeys read(Path file) {
        return Try.of(() -> Files.readString(file, StandardCharsets.UTF_8))
                .toOption()
                .flatMap(FjKeys::parse)
                .getOrElse(NONE);
    }

    /**
     * Where fj keeps its keys: under {@code dataHome}, the XDG data directory when it is set, else under
     * {@code home}'s {@code .local/share}.
     */
    public static Path file(Option<String> dataHome, Path home) {
        Path data = dataHome.filter(dir -> !dir.isBlank())
                .map(Path::of)
                .getOrElse(() -> home.resolve(".local").resolve("share"));
        return data.resolve("forgejo-cli").resolve("keys.json");
    }
}
