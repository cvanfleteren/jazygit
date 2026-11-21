package net.vanfleteren.jazygit.core.forgejo;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.HashMap;
import io.vavr.collection.Map;
import io.vavr.control.Option;
import org.junit.jupiter.api.Test;

class ForgejoRepositoryTest {

    private static final Map<String, String> NO_ALIASES = HashMap.empty();

    @Test
    void anSshRemoteOnAnotherPortIsOnTheHostFjAliasesItTo() {
        assertEquals(
                Option.of(new ForgejoRepository("https://web.example.net", "cvf", "nix")),
                ForgejoRepository.of(
                        "ssh://git@code.example.net:10022/cvf/nix.git",
                        HashMap.of("code.example.net:10022", "web.example.net")));
    }

    @Test
    void anSshRemoteWithoutAnAliasIsOnTheSameHostOverHttps() {
        assertEquals(
                Option.of(new ForgejoRepository("https://code.example.net", "cvf", "nix")),
                ForgejoRepository.of("ssh://git@code.example.net:10022/cvf/nix.git", NO_ALIASES));
    }

    @Test
    void anScpLikeRemoteIsOnItsHost() {
        assertEquals(
                Option.of(new ForgejoRepository("https://codeberg.org", "forgejo", "forgejo")),
                ForgejoRepository.of("git@codeberg.org:forgejo/forgejo.git", NO_ALIASES));
    }

    @Test
    void anHttpsRemoteKeepsItsPortAndDropsItsUser() {
        assertEquals(
                Option.of(new ForgejoRepository("https://code.example.net:3000", "cvf", "nix")),
                ForgejoRepository.of("https://cvf@code.example.net:3000/cvf/nix", NO_ALIASES));
    }

    @Test
    void theHostIsWhatFjKeepsTheTokenUnder() {
        assertEquals(
                "code.example.net:3000", new ForgejoRepository("https://code.example.net:3000", "cvf", "nix").host());
    }

    @Test
    void anotherForgeALocalPathAndAUrlWithoutOwnerAreNoForgejoRepository() {
        assertEquals(Option.none(), ForgejoRepository.of("git@github.com:cvanfleteren/jazygit.git", NO_ALIASES));
        assertEquals(Option.none(), ForgejoRepository.of("https://gitlab.com/group/project.git", NO_ALIASES));
        assertEquals(Option.none(), ForgejoRepository.of("/srv/git/nix.git", NO_ALIASES));
        assertEquals(Option.none(), ForgejoRepository.of("file:///srv/git/nix.git", NO_ALIASES));
        assertEquals(Option.none(), ForgejoRepository.of("https://code.example.net/nix.git", NO_ALIASES));
    }
}
