package net.vanfleteren.jazygit.features.github.remarks;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.List;
import org.junit.jupiter.api.Test;

/**
 * The images of a comment that are read to be shown: those GitHub keeps, over https (ADR 84).
 */
class GitHubImagesTest {

    @Test
    void onlyImagesGitHubKeepsOverHttpsAreReadEachOnce() {
        String markdown = String.join(
                "\n\n",
                "![a](https://github.com/user-attachments/assets/1)",
                "![b](https://raw.githubusercontent.com/o/r/main/b.png)",
                "![again](https://github.com/user-attachments/assets/1)",
                "![c](http://github.com/c.png)",
                "![d](https://example.com/d.png)",
                "![e](https://github.com.example.com/e.png)",
                "![f](not a url)",
                "`![g](https://github.com/g.png)`");

        assertEquals(
                List.of(
                        "https://github.com/user-attachments/assets/1",
                        "https://raw.githubusercontent.com/o/r/main/b.png"),
                GitHubImages.in(markdown));
    }
}
