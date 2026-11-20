package net.vanfleteren.jazygit.core.gh.graphql;

import static net.vanfleteren.jazygit.lib.json.Decoder.string;

import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.lib.json.Decoder;

/**
 * How the object id of a commit is read from what GitHub sends.
 */
public final class ObjectIds {

    /**
     * A commit's full object id, as {@link Sha#parse} takes it; anything else is not read, so that what is not an
     * object id never reaches git or an agent's prompt (ADR 40).
     */
    public static final Decoder<Sha> SHA = string().flatMap(text -> Sha.parse(text)
            .<Decoder<Sha>>map(Decoder::succeed)
            .getOrElse(() -> Decoder.fail("not an object id: " + text)));

    private ObjectIds() {}
}
