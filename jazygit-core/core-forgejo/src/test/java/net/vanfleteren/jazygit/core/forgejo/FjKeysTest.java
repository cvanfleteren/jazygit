package net.vanfleteren.jazygit.core.forgejo;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.HashMap;
import io.vavr.control.Option;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FjKeysTest {

    // As fj 0.7 writes it, with a host logged in with OAuth, which keeps no plain token.
    private static final String KEYS = """
            {"hosts":{"code.example.net":{"type":"Application","token":"secret"},
                      "codeberg.org":{"type":"OAuth","refresh_token":"other"}},
             "aliases":{"code.example.net:10022":"code.example.net"}}
            """;

    @Test
    void theTokensOfTheHostsAndTheAliasesAreRead() {
        assertEquals(
                Option.of(new FjKeys(
                        HashMap.of("code.example.net", "secret"),
                        HashMap.of("code.example.net:10022", "code.example.net"))),
                FjKeys.parse(KEYS));
    }

    @Test
    void aFileThatIsMissingOrNotFjsHasNoKeys(@TempDir Path dir) throws IOException {
        assertEquals(FjKeys.NONE, FjKeys.read(dir.resolve("keys.json")));
        Path broken = Files.writeString(dir.resolve("broken.json"), "not json", StandardCharsets.UTF_8);
        assertEquals(FjKeys.NONE, FjKeys.read(broken));
    }

    @Test
    void theKeysAreUnderTheXdgDataDirectoryWhenItIsSet() {
        Path home = Path.of("/home/someone");
        assertEquals(Path.of("/data/forgejo-cli/keys.json"), FjKeys.file(Option.of("/data"), home));
        assertEquals(Path.of("/home/someone/.local/share/forgejo-cli/keys.json"), FjKeys.file(Option.none(), home));
        assertEquals(Path.of("/home/someone/.local/share/forgejo-cli/keys.json"), FjKeys.file(Option.of(""), home));
    }
}
