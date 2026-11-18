package fr.heetstudio.confee.ui.render;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Pixel-art icons drawn with the theme colors ('#' = pixel). One consistent 1 px stroke.
 * <p>Internal: may change without notice.</p>
 */
public enum Icon {
    CHEVRON_DOWN(
            "#######",
            ".#####.",
            "..###..",
            "...#..."),
    CHEVRON_RIGHT(
            "#...",
            "##..",
            "###.",
            "####",
            "###.",
            "##..",
            "#..."),
    BACK(
            "...##",
            "..##.",
            ".##..",
            "##...",
            ".##..",
            "..##.",
            "...##"),
    CROSS(
            "##...##",
            ".##.##.",
            "..###..",
            "...#...",
            "..###..",
            ".##.##.",
            "##...##"),
    PLUS(
            "...#...",
            "...#...",
            "...#...",
            "#######",
            "...#...",
            "...#...",
            "...#..."),
    ARROW_UP(
            "..#..",
            ".###.",
            "#####"),
    ARROW_DOWN(
            "#####",
            ".###.",
            "..#.."),
    RESET(
            "..####.#",
            ".#....##",
            "#....###",
            "#.......",
            "#.......",
            "#......#",
            ".#....#.",
            "..####.."),
    EXCLAMATION(
            "#",
            "#",
            "#",
            "#",
            ".",
            "#"),
    SEARCH(
            ".###...",
            "#...#..",
            "#...#..",
            "#...#..",
            ".###...",
            "....##.",
            ".....##"),
    TOGGLE_ON(
            "#",
            "#",
            "#",
            "#",
            "#"),
    TOGGLE_OFF(
            ".##.",
            "#..#",
            "#..#",
            "#..#",
            ".##."),
    MINUS(
            "#######"),
    GRID(
            "###.###",
            "###.###",
            "###.###",
            ".......",
            "###.###",
            "###.###",
            "###.###"),
    CHECK(
            "......#",
            ".....##",
            "#...##.",
            "##.##..",
            ".###...",
            "..#...."),
    INFO(
            "#",
            ".",
            "#",
            "#",
            "#",
            "#");

    private final String[] rows;

    Icon(String... rows) {
        this.rows = rows;
    }

    public int width() {
        return rows[0].length();
    }

    public int height() {
        return rows.length;
    }

    public void draw(GuiGraphics g, int x, int y, int color) {
        for (int row = 0; row < rows.length; row++) {
            String line = rows[row];
            int start = -1;
            for (int col = 0; col <= line.length(); col++) {
                boolean on = col < line.length() && line.charAt(col) == '#';
                if (on && start < 0) {
                    start = col;
                } else if (!on && start >= 0) {
                    g.fill(x + start, y + row, x + col, y + row + 1, color);
                    start = -1;
                }
            }
        }
    }

    /** Draws the icon centered in a box. */
    public void drawCentered(GuiGraphics g, int boxX, int boxY, int boxW, int boxH, int color) {
        draw(g, boxX + (boxW - width()) / 2, boxY + (boxH - height()) / 2, color);
    }
}
