package net.vanfleteren.jazygit.ui;

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
import net.vanfleteren.jazygit.image.Picture;
import net.vanfleteren.jazygit.image.Sharp;
import net.vanfleteren.jazygit.image.Sixel;

/**
 * Pictures drawn with Sixel (ADR 87), over the placeholder cells Kitty's protocol draws them by (ADR 86).
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

    private Plan plan = Plan.NONE;
    private Plan painted = Plan.NONE;
    private Option<Buffer> buffer = Option.none();
    private boolean dirty;
    // The images at the size of their cells, and the parts of them as Sixel, the last used last.
    private LinkedHashMap<Tuple2<Integer, CellPixels>, int[]> scaled = LinkedHashMap.empty();
    private LinkedHashMap<Tuple2<Placed, CellPixels>, String> encoded = LinkedHashMap.empty();

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
        List<Placed> placed = spots.groupBy(spot -> spot._2().id())
                .toList()
                .flatMap(image -> place(frame, image._1(), image._2(), shown, cell));
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
     * again, the images, then the cells of what is over them.
     */
    public Option<String> paint() {
        if (plan.equals(painted) && !dirty) {
            return Option.none();
        }
        StringBuilder out = new StringBuilder("\u001b7");
        buffer.forEach(frame -> {
            painted.placed().forEach(was -> cells(out, frame, was.screen(), HashSet.empty()));
            plan.placed().forEach(now -> {
                out.append("\u001b[")
                        .append(now.screen().y() + 1)
                        .append(';')
                        .append(now.screen().x() + 1)
                        .append('H');
                Tuple2<Placed, CellPixels> key = Tuple.of(now, plan.cell());
                String sixel = encoded.get(key).getOrElse(() -> sixel(now, plan.cell()));
                encoded = kept(encoded.remove(key).put(key, sixel));
                out.append(sixel);
                cells(out, frame, now.screen(), now.images());
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
            Option<Picture> half = image.map(sharp -> halfBlocks(id, sharp));
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
        spots.forEach(spot -> frame.set(spot._1().x(), spot._1().y(), Cell.EMPTY));
        return Option.of(new Placed(id, image.get(), screen, left, top, cells));
    }

    /**
     * The part of image {@code placed} that shows, as Sixel, at {@code cell} pixels a cell.
     */
    private String sixel(Placed placed, CellPixels cell) {
        int columns = KittyGraphics.columnsOf(placed.id());
        int rows = KittyGraphics.rowsOf(placed.id());
        int width = columns * cell.width();
        int height = rows * cell.height();
        Tuple2<Integer, CellPixels> key = Tuple.of(placed.id(), cell);
        int[] whole = scaled.get(key)
                .getOrElse(() -> Picture.resized(
                        placed.sharp().argb(),
                        placed.sharp().width(),
                        placed.sharp().height(),
                        width,
                        height));
        scaled = kept(scaled.remove(key).put(key, whole));
        int cutLeft = placed.column() * cell.width();
        int cutTop = placed.row() * cell.height();
        int cutWidth = placed.screen().width() * cell.width();
        int cutHeight = placed.screen().height() * cell.height();
        int[] cut = new int[cutWidth * cutHeight];
        for (int row = 0; row < cutHeight; row++) {
            System.arraycopy(whole, (cutTop + row) * width + cutLeft, cut, row * cutWidth, cutWidth);
        }
        return Sixel.encode(cut, cutWidth, cutHeight);
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
    private static Picture halfBlocks(int id, Sharp sharp) {
        int columns = KittyGraphics.columnsOf(id);
        int height = KittyGraphics.rowsOf(id) * 2;
        return new Picture(
                columns,
                height,
                Vector.ofAll(Picture.resized(sharp.argb(), sharp.width(), sharp.height(), columns, height)));
    }

    // {@code used} without what was used longest ago, past KEPT.
    private static <K, V> LinkedHashMap<K, V> kept(LinkedHashMap<K, V> used) {
        return used.size() > KEPT ? used.drop(used.size() - KEPT) : used;
    }

    /**
     * An image placed to be painted: the part of it that shows, from {@code column} and {@code row} of its cells, at
     * {@code screen}, and which cells there are its own, as {@code y * width + x}; the others are of what is over it.
     */
    record Placed(int id, Sharp sharp, Rect screen, int column, int row, Set<Integer> images) {}

    /**
     * The images of a frame, and the size of a cell they are painted at.
     */
    record Plan(List<Placed> placed, CellPixels cell) {

        static final Plan NONE = new Plan(List.empty(), null);

        boolean covers(int x, int y) {
            return placed.exists(image -> image.screen().contains(x, y));
        }
    }

    private static final class Written implements Widget, RawOutputCapable {

        @Override
        public void render(Rect area, Buffer buffer) {}

        @Override
        public void render(Rect area, Buffer buffer, OutputStream rawOutput) {}
    }
}
