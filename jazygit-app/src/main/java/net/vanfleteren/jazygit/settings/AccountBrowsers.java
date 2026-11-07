package net.vanfleteren.jazygit.settings;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.LinkedHashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Option;
import java.util.Locale;

/**
 * The browser set for each GitHub account, by host and login, as the settings file keeps them under
 * {@code github.browsers} (ADR 88). Hosts and logins are matched as GitHub matches them, without regard to case, and
 * kept as they were written.
 *
 * @param byHost the browser of each login, by host
 */
public record AccountBrowsers(Map<String, Map<String, String>> byHost) {

    /**
     * No browser set for any account.
     */
    public static final AccountBrowsers NONE = new AccountBrowsers(LinkedHashMap.empty());

    /**
     * The browser set for {@code login} on {@code host}, if any.
     */
    public Option<String> of(String host, String login) {
        return byHost.find(entry -> same(entry._1, host))
                .flatMap(entry -> entry._2.find(browser -> same(browser._1, login)))
                .map(Tuple2::_2);
    }

    /**
     * These browsers with {@code browser} set for {@code login} on {@code host}; none takes the account's away. The
     * host keeps how it is written already, the login is written as given.
     */
    public AccountBrowsers with(String host, String login, Option<String> browser) {
        String key = byHost.keySet().find(written -> same(written, host)).getOrElse(host);
        Map<String, String> logins =
                byHost.get(key).getOrElse(LinkedHashMap.empty()).filterKeys(written -> !same(written, login));
        Map<String, String> changed = browser.fold(() -> logins, set -> logins.put(login, set));
        return new AccountBrowsers(changed.isEmpty() ? byHost.remove(key) : byHost.put(key, changed));
    }

    /**
     * The browsers {@code value} holds, a table of hosts whose tables give each login its browser, as the settings file
     * is read; a blank browser is not set. None when {@code value} is not such a table.
     */
    static Option<AccountBrowsers> read(Object value) {
        return table(value)
                .flatMap(hosts -> List.ofAll(hosts)
                        .map(host -> table(host._2)
                                .flatMap(AccountBrowsers::logins)
                                .map(logins -> Tuple.of(host._1, logins)))
                        .transform(Option::sequence))
                .map(hosts -> new AccountBrowsers(LinkedHashMap.ofEntries(hosts.filter(host -> !host._2.isEmpty()))));
    }

    /**
     * The browser of each login of {@code table}, the blank ones left out; none when one is no text.
     */
    private static Option<Map<String, String>> logins(Iterable<Tuple2<String, Object>> table) {
        return List.ofAll(table)
                .map(login -> Option.of(login._2)
                        .filter(String.class::isInstance)
                        .map(browser -> Tuple.of(login._1, ((String) browser).strip())))
                .transform(Option::sequence)
                .map(logins -> LinkedHashMap.ofEntries(logins.filter(login -> !login._2.isEmpty())));
    }

    /**
     * The browsers as the settings file keeps them, by the path of their key.
     */
    Map<List<String>, Object> entries() {
        return byHost.flatMap(host -> host._2.map(login ->
                        Tuple.<List<String>, Object>of(List.of("github", "browsers", host._1, login._1), login._2)))
                .foldLeft(LinkedHashMap.empty(), (entries, entry) -> entries.put(entry));
    }

    private static Option<Iterable<Tuple2<String, Object>>> table(Object value) {
        return Option.of(value)
                .filter(java.util.Map.class::isInstance)
                .map(table -> List.ofAll(((java.util.Map<?, ?>) table).entrySet())
                        .map(entry -> Tuple.of(String.valueOf(entry.getKey()), (Object) entry.getValue())));
    }

    private static boolean same(String written, String asked) {
        return written.toLowerCase(Locale.ROOT).equals(asked.toLowerCase(Locale.ROOT));
    }
}
