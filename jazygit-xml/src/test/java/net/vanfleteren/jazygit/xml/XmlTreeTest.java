package net.vanfleteren.jazygit.xml;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.List;
import io.vavr.control.Either;
import net.vanfleteren.jazygit.xml.NotXml.Broken;
import net.vanfleteren.jazygit.xml.NotXml.Problem;
import net.vanfleteren.jazygit.xml.XmlTree.Attribute;
import net.vanfleteren.jazygit.xml.XmlTree.Comment;
import net.vanfleteren.jazygit.xml.XmlTree.Element;
import net.vanfleteren.jazygit.xml.XmlTree.Text;
import org.junit.jupiter.api.Test;

/**
 * Text read as {@link XmlTree}, the lines of its nodes kept (ADR 151).
 */
class XmlTreeTest {

    @Test
    void everyNodeKeepsTheLinesItStartsAndEndsOnAndTheLineItsStartTagEndsOn() {
        assertEquals(
                Either.right(List.of(
                        new Comment(2, 2, 2, List.of("top")),
                        new Element(
                                3,
                                4,
                                8,
                                "project",
                                List.of(new Attribute("xmlns", "urn:x"), new Attribute("xmlns:xsi", "urn:y")),
                                List.of(
                                        new Element(
                                                5,
                                                5,
                                                5,
                                                "xs:el",
                                                List.of(new Attribute("name", "a")),
                                                List.of(new Text(5, 5, false, List.of("t & u")))),
                                        new Element(6, 6, 6, "empty", List.empty(), List.empty()),
                                        new Element(
                                                7,
                                                7,
                                                7,
                                                "d",
                                                List.empty(),
                                                List.of(new Text(7, 7, true, List.of("c <x>")))))),
                        new Comment(9, 9, 9, List.of("after")))),
                XmlTree.read("""
                        <?xml version="1.0" encoding="UTF-8"?>
                        <!-- top -->
                        <project xmlns="urn:x"
                                 xmlns:xsi='urn:y'>
                          <xs:el name="a">t &amp; u</xs:el>
                          <empty/>
                          <d><![CDATA[c <x>]]></d>
                        </project>
                        <!-- after -->
                        """));
    }

    @Test
    void textIsTrimmedAndItsLinesLoseTheIndentationTheyShare() {
        assertEquals(
                List.of(new Text(2, 4, false, List.of("Reads XML,", "  as written,", "to be drawn."))),
                root("""
                        <description>
                            Reads XML,
                              as written,
                            to be drawn.
                        </description>""").children());
    }

    @Test
    void mixedTextAndElementsKeepTheirOrderAndWhiteSpaceAloneIsDropped() {
        assertEquals(
                List.of(
                        new Text(1, 1, false, List.of("Hello")),
                        new Element(1, 1, 1, "b", List.empty(), List.of(new Text(1, 1, false, List.of("you")))),
                        new Text(1, 1, false, List.of("!"))),
                root("<p>Hello <b>you</b>!  </p>").children());
    }

    @Test
    void aCommentKeepsTheLineOfWhatItSays() {
        assertEquals(
                List.of(new Comment(1, 4, 2, List.of("Licensed to you", "under a licence"))),
                XmlTree.read("<!--\n  Licensed to you\n  under a licence\n-->\n<a/>")
                        .get()
                        .init());
    }

    @Test
    void referencesToTheEntitiesOfXmlAndToCharactersAreUndoneAndOthersStayAsWritten() {
        assertEquals(
                List.of(new Text(1, 1, false, List.of("<é> é&nbsp;\"'"))),
                root("<a>&lt;&#233;&gt; &#xE9;&nbsp;&quot;&apos;</a>").children());
        assertEquals(
                List.of(new Attribute("title", "a & b")),
                root("<a title=\"a &amp; b\"/>").attributes());
    }

    @Test
    void aDoctypeAndInstructionsAreReadPastAndNothingIsFetched() {
        assertEquals(
                new Element(3, 3, 3, "a", List.empty(), List.of(new Text(3, 3, false, List.of("&foo;")))), root("""
                        <!DOCTYPE a [<!ENTITY foo SYSTEM "file:///etc/passwd">]>
                        <?xml-stylesheet href="a.xsl"?>
                        <a>&foo;<?pi inside?></a>"""));
    }

    @Test
    void aByteOrderMarkIsReadPast() {
        assertEquals(new Element(1, 1, 1, "a", List.empty(), List.empty()), root("﻿<a/>"));
    }

    @Test
    void textThatIsNotWellFormedSaysWhereAndWhy() {
        assertEquals(broken(3, 1, new Problem.Closes("b", "a")), XmlTree.read("<a>\n<b>\n</a>"));
        assertEquals(broken(1, 8, new Problem.EndsInside("a")), XmlTree.read("<a><b/>"));
        assertEquals(broken(1, 5, new Problem.MoreAfter()), XmlTree.read("<a/><b/>"));
        assertEquals(broken(1, 5, new Problem.MoreAfter()), XmlTree.read("<a/>x"));
        assertEquals(broken(1, 1, new Problem.TextOutside()), XmlTree.read("x<a/>"));
        assertEquals(broken(1, 1, new Problem.ClosesNothing("a")), XmlTree.read("</a>"));
        assertEquals(broken(1, 10, new Problem.Twice("b")), XmlTree.read("<a b='1' b='2'/>"));
        assertEquals(broken(1, 6, new Problem.Expected("=")), XmlTree.read("<a b '1'/>"));
        assertEquals(broken(1, 6, new Problem.Expected(Problem.QUOTED)), XmlTree.read("<a b=1/>"));
        assertEquals(broken(1, 2, new Problem.Expected(Problem.NAME)), XmlTree.read("< a/>"));
        assertEquals(broken(1, 8, new Problem.Expected(">")), XmlTree.read("<a></a b>"));
        assertEquals(broken(1, 7, new Problem.LessThan()), XmlTree.read("<a b='<'/>"));
        assertEquals(broken(1, 4, new Problem.Ampersand()), XmlTree.read("<a>& b</a>"));
        assertEquals(broken(1, 4, new Problem.Expected("-->")), XmlTree.read("<a><!-- no end</a>"));
        assertEquals(Either.left(new NotXml.Nothing()), XmlTree.read("<!-- only -->\n"));
        assertEquals(Either.left(new NotXml.Nothing()), XmlTree.read(""));
    }

    @Test
    void elementsNestedDeeperThanTheyAreReadAreTooDeep() {
        String deep = "<a>".repeat(XmlReader.MAX_DEPTH + 1) + "</a>".repeat(XmlReader.MAX_DEPTH + 1);
        assertEquals(Either.left(new NotXml.TooDeep(XmlReader.MAX_DEPTH)), XmlTree.read(deep));
        String deepest = "<a>".repeat(XmlReader.MAX_DEPTH) + "</a>".repeat(XmlReader.MAX_DEPTH);
        assertEquals(true, XmlTree.read(deepest).isRight());
    }

    private static Element root(String text) {
        return (Element)
                XmlTree.read(text).get().find(node -> node instanceof Element).get();
    }

    private static Either<NotXml, List<XmlTree>> broken(int line, int column, Problem problem) {
        return Either.left(new Broken(line, column, problem));
    }
}
