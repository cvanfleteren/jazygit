package net.vanfleteren.jazygit.git.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vavr.collection.List;
import io.vavr.control.Option;
import org.junit.jupiter.api.Test;

class CommitQueryTest {

    @Test
    void theBareWordsAreOnePhraseOfTheMessage() {
        assertEquals(
                new CommitQuery(Option.of("fix login"), List.empty(), List.empty(), Option.none()),
                CommitQuery.parse("  fix   login "));
    }

    @Test
    void thePrefixesMakeTheirOwnFilters() {
        assertEquals(
                new CommitQuery(Option.of("fix login"), List.of("alice"), List.of("src/"), Option.of("token")),
                CommitQuery.parse("fix author:alice login path:src/ -S:token"));
    }

    @Test
    void authorsAndPathsRepeat() {
        CommitQuery query = CommitQuery.parse("author:alice author:bob path:a path:b");

        assertEquals(List.of("alice", "bob"), query.authors());
        assertEquals(List.of("a", "b"), query.paths());
    }

    @Test
    void theLastPickaxeCounts() {
        assertEquals(Option.of("b"), CommitQuery.parse("-S:a -S:b").pickaxe());
    }

    @Test
    void quotesKeepSpacesInAValue() {
        CommitQuery query = CommitQuery.parse("author:\"Jane Doe\" -S:\"two  words\" \"a  b\"");

        assertEquals(List.of("Jane Doe"), query.authors());
        assertEquals(Option.of("two  words"), query.pickaxe());
        assertEquals(Option.of("a  b"), query.message());
    }

    @Test
    void anUnclosedQuoteRunsToTheEnd() {
        assertEquals(List.of("Jane Doe"), CommitQuery.parse("author:\"Jane Doe").authors());
    }

    @Test
    void otherPrefixesAreTextOfTheMessage() {
        assertEquals(
                Option.of("feat: fix(ui): Author:x"),
                CommitQuery.parse("feat: fix(ui): Author:x").message());
    }

    @Test
    void aPrefixWithoutAValueFiltersNothing() {
        CommitQuery query = CommitQuery.parse("author: path:\"\" -S:");

        assertEquals(CommitQuery.parse(""), query);
        assertTrue(query.isEmpty());
    }

    @Test
    void aQueryWithAFilterIsNotEmpty() {
        assertFalse(CommitQuery.parse("path:src").isEmpty());
    }

    @Test
    void itIgnoresCaseUnlessTheTextToFindHasAnUppercaseLetter() {
        assertTrue(CommitQuery.parse("fix author:bob path:Src -S:token").ignoresCase());
        assertFalse(CommitQuery.parse("Fix").ignoresCase());
        assertFalse(CommitQuery.parse("fix author:Bob").ignoresCase());
        assertFalse(CommitQuery.parse("fix -S:Token").ignoresCase());
    }
}
