package net.vanfleteren.jazygit.kernel.ui.markdown;

import dev.tamboui.buffer.Buffer;
import dev.tamboui.buffer.Cell;
import dev.tamboui.buffer.DiffResult;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.AnsiCellWriter;
import dev.tamboui.widget.RawOutputCapable;
import dev.tamboui.widget.Widget;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.HashSet;
import io.vavr.collection.LinkedHashMap;
import io.vavr.collection.List;
import io.vavr.collection.Set;
import io.vavr.collection.Traversable;
import io.vavr.collection.Vector;
import io.vavr.control.Option;
import java.io.OutputStream;
import java.util.function.Function;
import net.vanfleteren.jazygit.lib.image.Picture;
import net.vanfleteren.jazygit.lib.image.Sharp;
import net.vanfleteren.jazygit.lib.image.Sixel;

/**
 * Pictures drawn with Sixel (ADR 188), over the placeholder cells Kitty's protocol draws them by (ADR 188).
 *
 * <p>Sixel has no images that cells refer to: an image is pixels written at the cursor, which text written there
 * later takes the place of. So the cells of a frame tell where each image is and which part of it shows, and are
 * blank in the frame TamboUI draws; once TamboUI drew it, before the frame is shown, each image is written over them,
 * cut to the part that shows, and the cells of a popup over it are written again on top. That is done again only when
 * an image moved, or TamboUI drew a cell where one is or was; the cells where one no longer is are written again.
 *
 * <p>Only touched on the render thread: {@link #plan} while a frame is drawn, then {@link #drawn} and {@link #paint}
 * while it is written.
 */
public final class SixelImages {

    /**
     * Draws nothing, but has TamboUI write a frame as one with raw output, in a synchronized update, also when no cell
     * changed: that is where the images are painted.
     */
    public static final Widget WRITTEN = new Written();

    /**
     * Where {@link #WRITTEN} is drawn: nowhere.
     */
    public static final Rect NOWHERE = new Rect(0, 0, 0, 0);

    // How many images, at the size of their cells, and how many parts of them as Sixel, are kept.
    private static final int KEPT = 16;

    // How the pixels of an image are read, and made into Sixel.
    private final Function<Sharp, int[]> pixels;
    private final Encoder encoder;
    private Plan plan = Plan.NONE;
    private Plan painted = Plan.NONE;
    private Option<Buffer> buffer = Option.none();
    private boolean dirty;
    // The images at the size of their cells, and the parts of them as Sixel, the last used last.
    private LinkedHashMap<Tuple2<Integer, CellPixels>, int[]> scaled = LinkedHashMap.empty();
    private LinkedHashMap<Tuple2<Cut, CellPixels>, String> encoded = LinkedHashMap.empty();
    // The half blocks of the images drawn so where the size of a cell is not known, the last used last.
    private LinkedHashMap<Integer, Picture> halves = LinkedHashMap.empty();

    /**
     * Images that read their pixels from their sharp image, and are made into Sixel by jazygit's own encoder.
     */
    public SixelImages() {
        this(Sharp::argb, Sixel::encode);
    }

    /**
     * Images whose pixels {@code pixels} reads, and which {@code encoder} makes into Sixel.
     */
    SixelImages(Function<Sharp, int[]> pixels, Encoder encoder) {
        this.pixels = pixels;
        this.encoder = encoder;
    }

    /**
     * Finds the images in the cells of {@code frame}, of those {@code shown}, and blanks their cells, to be painted
     * once the frame is written. Without the size of a cell, or a picture that is not shown, its cells are half blocks
     * instead (ADR 84).
     */
    public void plan(Buffer frame, Traversable<Sharp> shown, Option<CellPixels> cell) {
        List<Tuple2<Rect, KittyGraphics.Spot>> spots = List.range(
                        frame.area().top(), frame.area().bottom())
                .flatMap(y -> List.range(frame.area().left(), frame.area().right())
                        .flatMap(x -> KittyGraphics.spotOf(frame.get(x, y))
                                .map(spot -> Tuple.of(new Rect(x, y, 1, 1), spot))));
        // Each copy of an image apart: its cells are of one image, and say the same first cell of it.
        List<Placed> placed = spots.groupBy(spot -> Tuple.of(
                        spot._2().id(),
                        spot._1().x() - spot._2().column(),
                        spot._1().y() - spot._2().row()))
                .toList()
                .flatMap(image -> place(frame, image._1()._1(), image._2(), shown, cell));
        buffer = Option.of(frame);
        plan = new Plan(placed, cell.getOrNull());
    }

    /**
     * Notes the cells TamboUI wrote of a frame: one where an image is or was has it painted again.
     */
    public void drawn(DiffResult diff) {
        for (int i = 0; i < diff.size() && !dirty; i++) {
            int x = diff.getX(i);
            int y = diff.getY(i);
            dirty = plan.covers(x, y) || painted.covers(x, y);
        }
    }

    /**
     * What paints the images of the frame written last, if they have to be: the cells where images were written
     * again, the images, then the cells of what is over them, and those right of and under them. A terminal at a
     * fractional scale tells a cell a fraction larger than it draws it, Konsole at 125% among them, so an image may go
     * past its cells; a cell written takes the place of what of an image is there.
     */
    public Option<String> paint() {
        if (plan.equals(painted) && !dirty) {
            return Option.none();
        }
        StringBuilder out = new StringBuilder("\u001b7");
        // The cells of the images painted now, which no cell written may take the place of.
        Set<Integer> owned = plan.placed().flatMap(Placed::images).toSet();
        buffer.forEach(frame -> {
            painted.placed().forEach(was -> {
                cells(out, frame, was.screen(), HashSet.empty());
                edges(was.screen()).forEach(edge -> cells(out, frame, edge, owned));
            });
            plan.placed().forEach(now -> {
                out.append("\u001b[")
                        .append(now.screen().y() + 1)
                        .append(';')
                        .append(now.screen().x() + 1)
                        .append('H');
                Tuple2<Cut, CellPixels> key = Tuple.of(now.cut(), plan.cell());
                String sixel = encoded.get(key).getOrElse(() -> sixel(now, plan.cell()));
                encoded = kept(encoded.remove(key).put(key, sixel));
                out.append(sixel);
                cells(out, frame, now.screen(), now.images());
                edges(now.screen()).forEach(edge -> cells(out, frame, edge, owned));
            });
        });
        out.append("\u001b[0m\u001b8");
        painted = plan;
        dirty = false;
        return Option.of(out.toString());
    }

    /**
     * Forgets what was painted, as the screen was cleared: the images are painted again with the next frame.
     */
    public void forget() {
        painted = Plan.NONE;
        dirty = true;
    }

    /**
     * Image {@code id}, whose cells on the screen are {@code spots}, placed to be painted there; or drawn in half
     * blocks in {@code frame} when it cannot be.
     */
    private Option<Placed> place(
            Buffer frame,
            int id,
            List<Tuple2<Rect, KittyGraphics.Spot>> spots,
            Traversable<Sharp> shown,
            Option<CellPixels> cell) {
        Option<Sharp> image = KittyGraphics.imageOf(id, shown);
        if (image.isEmpty() || cell.isEmpty()) {
            Option<Picture> half = image.map(sharp -> halves.get(id).getOrElse(() -> halfBlocks(id, sharp)));
            half.forEach(picture -> halves = kept(halves.remove(id).put(id, picture)));
            spots.forEach(spot -> frame.set(
                    spot._1().x(),
                    spot._1().y(),
                    half.map(picture -> {
                                Tuple2<Style, String> drawn = HalfBlocks.cell(
                                        picture,
                                        spot._2().row() * picture.width()
                                                + spot._2().column());
                                return new Cell(drawn._2(), drawn._1());
                            })
                            .getOrElse(Cell.EMPTY)));
            return Option.none();
        }
        int top = spots.map(spot -> spot._2().row()).min().get();
        int bottom = spots.map(spot -> spot._2().row()).max().get();
        int left = spots.map(spot -> spot._2().column()).min().get();
        int right = spots.map(spot -> spot._2().column()).max().get();
        Tuple2<Rect, KittyGraphics.Spot> any = spots.head();
        int x = any._1().x() - any._2().column() + left;
        int y = any._1().y() - any._2().row() + top;
        Rect screen = new Rect(x, y, right - left + 1, bottom - top + 1);
        Set<Integer> cells = spots.map(
                        spot -> spot._1().y() * frame.area().width() + spot._1().x())
                .toSet();
        // Blank in the background they had, which shows where the image leaves its cells undrawn.
        spots.forEach(spot -> frame.set(
                spot._1().x(),
                spot._1().y(),
                new Cell(
                        " ",
                        Option.ofOptional(frame.get(spot._1().x(), spot._1().y())
                                        .style()
                                        .bg())
                                .map(Style.EMPTY::bg)
                                .getOrElse(Style.EMPTY))));
        return Option.of(new Placed(id, image.get(), screen, left, top, cells));
    }

    /**
     * The column right of {@code screen}, down to the row under it, and that row: where an image there may go past its
     * cells.
     */
    private static List<Rect> edges(Rect screen) {
        return List.of(
                new Rect(screen.x() + screen.width(), screen.y(), 1, screen.height() + 1),
                new Rect(screen.x(), screen.y() + screen.height(), screen.width(), 1));
    }

    /**
     * The part of image {@code placed} that shows, as Sixel, at {@code cell} pixels a cell: as high as the whole bands
     * of six pixels that fit in its cells, as a terminal draws the last band whole, past the height it is told.
     */
    private String sixel(Placed placed, CellPixels cell) {
        int columns = KittyGraphics.columnsOf(placed.id());
        int rows = KittyGraphics.rowsOf(placed.id());
        int width = columns * cell.width();
        int height = rows * cell.height();
        Tuple2<Integer, CellPixels> key = Tuple.of(placed.id(), cell);
        int[] whole = scaled.get(key).getOrElse(() -> fitted(placed.sharp(), width, height));
        scaled = kept(scaled.remove(key).put(key, whole));
        int cutLeft = placed.column() * cell.width();
        int cutTop = placed.row() * cell.height();
        int cutWidth = placed.screen().width() * cell.width();
        int cutHeight = bands(placed.screen().height() * cell.height());
        int[] cut = new int[cutWidth * cutHeight];
        for (int row = 0; row < cutHeight; row++) {
            System.arraycopy(whole, (cutTop + row) * width + cutLeft, cut, row * cutWidth, cutWidth);
        }
        return encoder.encode(cut, cutWidth, cutHeight);
    }

    /**
     * {@code sharp} as large as fits in {@code width} by {@code height} pixels keeping its shape, at their top left:
     * the cells of a picture are as many as it takes, rounded, at a cell that is about 1 by 2, which a terminal's need
     * not be. What it leaves of them is not drawn, so the cells show there.
     */
    private int[] fitted(Sharp sharp, int width, int height) {
        double scale = Math.min((double) width / sharp.width(), (double) height / sharp.height());
        int fittedWidth = Math.clamp(Math.round(sharp.width() * scale), 1, width);
        int fittedHeight = Math.clamp(Math.round(sharp.height() * scale), 1, height);
        int[] image = Picture.resized(pixels.apply(sharp), sharp.width(), sharp.height(), fittedWidth, fittedHeight);
        int[] whole = new int[width * height];
        for (int row = 0; row < fittedHeight; row++) {
            System.arraycopy(image, row * fittedWidth, whole, row * width, fittedWidth);
        }
        return whole;
    }

    /**
     * {@code height} pixels cut to whole bands of six, but no less than one band, nor than it is.
     */
    private static int bands(int height) {
        return height < 6 ? height : height / 6 * 6;
    }

    /**
     * Writes the cells of {@code frame} in {@code area}, but those of {@code images}, which an image is painted over.
     */
    private static void cells(StringBuilder out, Buffer frame, Rect area, Set<Integer> images) {
        Rect cut = frame.area().intersection(area);
        try (AnsiCellWriter writer = new AnsiCellWriter(out::append)) {
            for (int y = cut.top(); y < cut.bottom(); y++) {
                boolean moved = false;
                for (int x = cut.left(); x < cut.right(); x++) {
                    if (images.contains(y * frame.area().width() + x)) {
                        moved = false;
                        continue;
                    }
                    if (!moved) {
                        out.append("\u001b[")
                                .append(y + 1)
                                .append(';')
                                .append(x + 1)
                                .append('H');
                        moved = true;
                    }
                    writer.writeCell(frame.get(x, y));
                }
            }
        }
    }

    /**
     * Image {@code id} of {@code sharp} as the half blocks of its cells.
     */
    private Picture halfBlocks(int id, Sharp sharp) {
        int columns = KittyGraphics.columnsOf(id);
        int height = KittyGraphics.rowsOf(id) * 2;
        return new Picture(
                columns,
                height,
                Vector.ofAll(Picture.resized(pixels.apply(sharp), sharp.width(), sharp.height(), columns, height)));
    }

    // {@code used} without what was used longest ago, past KEPT.
    private static <K, V> LinkedHashMap<K, V> kept(LinkedHashMap<K, V> used) {
        return used.size() > KEPT ? used.drop(used.size() - KEPT) : used;
    }

    /**
     * An image placed to be painted: the part of it that shows, from {@code column} and {@code row} of its cells, at
     * {@code screen}, and which cells there are its own, as {@code y * width + x}; the others are of what is over it.
     */
    record Placed(int id, Sharp sharp, Rect screen, int column, int row, Set<Integer> images) {

        /**
         * The part of its image that shows, which is what its Sixel is of.
         */
        Cut cut() {
            return new Cut(id, column, row, screen.width(), screen.height());
        }
    }

    /**
     * A part of image {@code id}: {@code columns} by {@code rows} of its cells, from {@code column} and {@code row}.
     */
    record Cut(int id, int column, int row, int columns, int rows) {}

    /**
     * The images of a frame, and the size of a cell they are painted at.
     */
    record Plan(List<Placed> placed, CellPixels cell) {

        static final Plan NONE = new Plan(List.empty(), null);

        boolean covers(int x, int y) {
            return placed.exists(image -> image.screen().contains(x, y));
        }
    }

    /**
     * Makes pixels, {@code width} by {@code height} row by row, into the Sixel that draws them.
     */
    @FunctionalInterface
    interface Encoder {
        String encode(int[] argb, int width, int height);
    }

    private static final class Written implements Widget, RawOutputCapable {

        @Override
        public void render(Rect area, Buffer buffer) {}

        @Override
        public void render(Rect area, Buffer buffer, OutputStream rawOutput) {}
    }
}
