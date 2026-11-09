package net.vanfleteren.jazygit.state;

import io.vavr.control.Option;
import java.time.Instant;
import lombok.With;
import net.vanfleteren.jazygit.git.model.MetadataStamp;

/**
 * When the index was last written anew, and what it looked like then, to tell when it changed since (ADR 97).
 *
 * @param seen  what the index looked like when it was last asked to be written anew; none before that, or when it
 *              could not be told then, so that the next stamp that can be told writes it anew. A refresh that could
 *              not write it keeps this, so only a later change, or the fallback, tries again
 * @param at    when it was last asked to be written anew; none before that, and then it waits for a stamp
 * @param adopt whether a refresh just wrote it: the next stamp that can be told is what that refresh left, not a
 *              change of the index to write anew again
 */
@With
public record IndexRefresh(Option<MetadataStamp> seen, Option<Instant> at, boolean adopt) {

    /**
     * Never written anew yet.
     */
    public static final IndexRefresh NONE = new IndexRefresh(Option.none(), Option.none(), false);
}
