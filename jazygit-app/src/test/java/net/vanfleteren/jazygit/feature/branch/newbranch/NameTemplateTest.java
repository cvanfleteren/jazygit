package net.vanfleteren.jazygit.feature.branch.newbranch;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.gh.StubGitHub;
import net.vanfleteren.jazygit.git.GitError;
import net.vanfleteren.jazygit.git.StubConfig;
import net.vanfleteren.jazygit.git.StubGit;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.ConfigValue;
import net.vanfleteren.jazygit.model.SampleData;
import net.vanfleteren.jazygit.state.Env;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.TestModels;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.Update.Next;
import org.junit.jupiter.api.Test;

/**
 * What the name of a new branch starts out as, from the git configuration (ADR 121).
 */
class NameTemplateTest {

    private static final BranchName MAIN = new BranchName("main");
    private static final NewBranchPopup ASKING = new NewBranchPopup(MAIN);

    private static Env config(Either<GitError, List<ConfigValue>> values) {
        return new Env(
                StubGit.of().withConfig(new StubConfig() {
                    @Override
                    public Either<GitError, List<ConfigValue>> all(String key) {
                        return key.equals(ReadNameTemplate.KEY) ? values : Either.right(List.empty());
                    }
                }),
                StubGitHub.of());
    }

    private static ConfigValue value(String value) {
        return new ConfigValue(false, "/home/me/.gitconfig", value);
    }

    @Test
    void theTemplateIsReadBeforeTheNameIsAskedFor() {
        Next next = Update.update(TestModels.loaded(SampleData.git()), new NewBranchMsg.Requested(MAIN));

        assertEquals(List.of(new ReadNameTemplate(ASKING)), next.cmds());
        assertEquals(Option.none(), next.model().popup());
    }

    @Test
    void theNameStartsOutAsTheTemplate() {
        Model asked = NameAsked.asked(
                TestModels.loaded(SampleData.git()), new NewBranchMsg.Requested(MAIN), Option.of("ART03DEV01-"));

        assertEquals(Option.of(ASKING.withText("ART03DEV01-")), asked.popup());
    }

    @Test
    void withoutATemplateTheNameStartsOutEmpty() {
        assertEquals(
                Option.of(ASKING),
                NameAsked.asked(TestModels.loaded(SampleData.git()), new NewBranchMsg.Requested(MAIN))
                        .popup());
    }

    @Test
    void theValueThatHoldsIsTheLastGitReads() {
        assertEquals(
                new NewBranchMsg.NameTemplateRead(ASKING, Option.of("ART03DEV01-")),
                new ReadNameTemplate(ASKING)
                        .run(config(Either.right(List.of(value("GLOBAL-"), value("ART03DEV01-"))))));
    }

    @Test
    void nothingSetIsNoTemplate() {
        assertEquals(
                new NewBranchMsg.NameTemplateRead(ASKING, Option.none()),
                new ReadNameTemplate(ASKING).run(config(Either.right(List.empty()))));
    }

    @Test
    void aConfigurationThatCannotBeReadIsNoTemplate() {
        assertEquals(
                new NewBranchMsg.NameTemplateRead(ASKING, Option.none()),
                new ReadNameTemplate(ASKING)
                        .run(config(Either.left(new GitError.CommandFailed("bad config line 3", 128)))));
    }

    @Test
    void bracesAreTakenAsTheyAre() {
        assertEquals(
                new NewBranchMsg.NameTemplateRead(ASKING, Option.of("fix-{old}-")),
                new ReadNameTemplate(ASKING).run(config(Either.right(List.of(value("fix-{old}-"))))));
    }
}
