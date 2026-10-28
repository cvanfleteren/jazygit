package net.vanfleteren.jazygit.gh;

import static net.vanfleteren.jazygit.gh.GhCommand.gh;
import static net.vanfleteren.jazygit.json.Decoder.bool;
import static net.vanfleteren.jazygit.json.Decoder.dict;
import static net.vanfleteren.jazygit.json.Decoder.field;
import static net.vanfleteren.jazygit.json.Decoder.list;
import static net.vanfleteren.jazygit.json.Decoder.map4;
import static net.vanfleteren.jazygit.json.Decoder.string;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.gh.model.GhLogin;
import net.vanfleteren.jazygit.json.Decoder;

/**
 * The accounts gh is logged in to, the hosts in the order gh gives them: each with its host, its login, whether it is
 * the active one and whether its token works (ADR 42).
 */
final class LoginsQuery {

    static final GhCommand COMMAND = gh("auth", "status", "--json", "hosts");

    static final Decoder<GhLogin> LOGIN = map4(
            field("host", string()),
            field("login", string()),
            field("active", bool()),
            field("state", string()),
            (host, login, active, state) -> new GhLogin(host, login, active, state.equals("success")));

    static final Decoder<List<GhLogin>> DECODER = field("hosts", dict(list(LOGIN)))
            .map(hosts -> hosts.values().toList().flatMap(logins -> logins));

    private LoginsQuery() {}
}
