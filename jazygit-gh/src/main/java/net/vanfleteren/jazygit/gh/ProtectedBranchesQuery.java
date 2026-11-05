package net.vanfleteren.jazygit.gh;

import static net.vanfleteren.jazygit.gh.GhCommand.gh;
import static net.vanfleteren.jazygit.json.Decoder.field;
import static net.vanfleteren.jazygit.json.Decoder.list;
import static net.vanfleteren.jazygit.json.Decoder.string;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.json.Decoder;

/**
 * The names of the branches of the repository that are protected, all pages of them, which are the roots that others
 * are built on (ADR 72). Reading them needs no more than read access.
 */
final class ProtectedBranchesQuery {

    // gh fills in the repository of the working tree for {owner} and {repo}; all pages, as one array of them.
    static final GhCommand COMMAND =
            gh("api", "repos/{owner}/{repo}/branches?protected=true&per_page=100", "--paginate", "--slurp");

    static final Decoder<List<String>> DECODER =
            list(list(field("name", string()))).map(pages -> pages.flatMap(page -> page));

    private ProtectedBranchesQuery() {}
}
