package pl.iomreborn.command;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArgParserTest {

    private static ArgParser.Parsed parse(String line) {
        return ArgParser.parse(line.split(" ", -1));
    }

    private static List<String> values(ArgParser.Parsed parsed) {
        return parsed.tokens().stream().map(ArgParser.Token::value).toList();
    }

    @Test
    void quotedNames() {
        ArgParser.Parsed parsed = parse("Vlammar \"A very cool map name\"");
        assertEquals(List.of("Vlammar", "A very cool map name"), values(parsed));
        assertFalse(parsed.get(0).qualifier());
        assertTrue(parsed.get(1).quoted());
    }

    @Test
    void playerPrefix() {
        ArgParser.Parsed parsed = parse("Vlammar AmauryPi:\"A very cool map name\"");
        assertEquals(List.of("Vlammar", "AmauryPi", "A very cool map name"), values(parsed));
        assertTrue(parsed.get(1).qualifier());

        ArgParser.Parsed simple = parse("Player:Map");
        assertEquals(List.of("Player", "Map"), values(simple));
        assertTrue(simple.get(0).qualifier());
    }

    @Test
    void colonsInsideQuotesAndUrlsAreKept() {
        assertEquals(List.of("Re:Zero"), values(parse("\"Re:Zero\"")));
        ArgParser.Parsed parsed = parse("\"My map\" https://example.com/a:b.png covered");
        assertEquals(List.of("My map", "https://example.com/a:b.png", "covered"), values(parsed));
        assertTrue(ArgParser.isUrl(parsed.get(1).value()));
    }

    @Test
    void flagsAndJoining() {
        ArgParser.Parsed parsed = parse("My long map --confirm");
        assertTrue(parsed.hasFlag("confirm"));
        assertEquals("My long map", parsed.joinFrom(0));
        assertEquals("long map", parsed.join(1, 3));
    }

    @Test
    void lenientWithUnclosedQuotes() {
        assertEquals(List.of("abc def"), values(parse("\"abc def")));
        assertEquals(List.of("a", "b"), values(parse("a  b")));
    }
}
