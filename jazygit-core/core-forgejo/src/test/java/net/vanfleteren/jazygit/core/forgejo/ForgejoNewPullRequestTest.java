package net.vanfleteren.jazygit.core.forgejo;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.repo.ForgeBranch;
import net.vanfleteren.jazygit.core.repo.PrNumber;
import org.junit.jupiter.api.Test;

/**
 * A pull request to create on Forgejo, as it is sent (ADR 178).
 */
class ForgejoNewPullRequestTest {

    private static final ForgejoRepository HOME = new ForgejoRepository("https://code.example.net", "cvf", "nix");

    private static final ForgeBranch FEATURE = new ForgeBranch("feature");

    private static ForgejoNewPullRequest titled(String title, boolean draft) {
        return new ForgejoNewPullRequest(
                new ForgejoNewPullRequest.Head(Option.none(), FEATURE), new ForgeBranch("main"), title, "", draft);
    }

    @Test
    void aHeadInTheHomeIsTheBranchAlone() {
        ForgejoNewPullRequest.Head head = ForgejoNewPullRequest.Head.of(
                HOME, new ForgejoRepository("https://code.example.net", "CVF", "Nix"), FEATURE);

        assertEquals(new ForgejoNewPullRequest.Head(Option.none(), FEATURE), head);
        assertEquals("feature", head.wire());
    }

    @Test
    void aHeadInAForkHasItsOwnerBeforeIt() {
        ForgejoNewPullRequest.Head head = ForgejoNewPullRequest.Head.of(
                HOME, new ForgejoRepository("https://code.example.net", "anna", "nix"), FEATURE);

        assertEquals(new ForgejoNewPullRequest.Head(Option.of("anna"), FEATURE), head);
        assertEquals("anna:feature", head.wire());
    }

    @Test
    void aDraftIsSentWithThePrefixOfWorkInProgress() {
        assertEquals("WIP: Create it", titled("Create it", true).sentTitle());
        assertEquals("Create it", titled("Create it", false).sentTitle());
    }

    @Test
    void aDraftWhoseTitleHasAPrefixAlreadyIsSentAsItIs() {
        assertEquals("wip: Create it", titled("wip: Create it", true).sentTitle());
        assertEquals("[WIP] Create it", titled("[WIP] Create it", true).sentTitle());
    }

    @Test
    void forgejosMessageIsReadFromTheAnswer() {
        assertEquals("not found", new ForgejoError.Refused(404, "{\"message\":\"not found\"}").message());
        assertEquals("Bad Gateway", new ForgejoError.Refused(502, "Bad Gateway\n").message());
    }

    @Test
    void onlyA409NamesAPullRequestOpenAlready() {
        String message = "{\"message\":\"pull request already exists for these targets [id: 1, issue_id: 4]\"}";

        assertEquals(Option.of(new PrNumber(4)), new ForgejoError.Refused(409, message).existing());
        assertEquals(Option.none(), new ForgejoError.Refused(422, message).existing());
        assertEquals(Option.none(), new ForgejoError.Refused(409, "{\"message\":\"conflict\"}").existing());
    }
}
