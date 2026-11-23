package net.vanfleteren.jazygit.features.github.remarks;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.net.URI;
import java.util.Locale;
import net.vanfleteren.jazygit.kernel.ui.markdown.MarkdownImage;

/**
 * The images of a comment on GitHub that are read to be shown (ADR 84): only those GitHub keeps, over https, as an
 * image elsewhere would tell its server who reads the comment.
 */
public final class GitHubImages {

    private GitHubImages() {}

    /**
     * Where the images of {@code markdown} are that GitHub keeps, over https, each once, in the order they are in.
     */
    public static List<String> in(String markdown) {
        return MarkdownImage.in(markdown)
                .flatMap(MarkdownImage::source)
                .filter(GitHubImages::isOnGitHub)
                .distinct();
    }

    private static boolean isOnGitHub(String url) {
        return Option.of(url)
                .toTry()
                .map(URI::create)
                .filter(uri -> "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null)
                .map(uri -> uri.getHost().toLowerCase(Locale.ROOT))
                .filter(host -> host.equals("github.com") || host.endsWith(".githubusercontent.com"))
                .isSuccess();
    }
}
