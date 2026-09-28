/*
 * Copyright (C) 2026 Pono contributors
 * 
 * This file is part of Pono, a modified version of OpenPnP.
 * 
 * Pono is free software: you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * Pono is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with Pono. If not, see
 * <http://www.gnu.org/licenses/>.
 */

package org.openpnp.gui.shell;

import java.awt.Dimension;
import java.awt.Rectangle;
import java.util.function.BiFunction;

/**
 * Where everything goes in the camera card, from sizes alone, so that it can be tested without a
 * window.
 * <p>
 * The large card is a tool row along the top - the camera choice, the readout, the view tools - the
 * manual controls down the right, and the pictures in what is left, whole and with nothing over
 * them. Of the height the page would give it, the card takes what its pictures and its manual
 * controls use there ({@link Result#fittedHeight}) and leaves the rest to the page under it; room
 * beside the pictures holds the machine panel when there is enough of it. The strip has the camera choice and the readout at its left, the sizes and Home at
 * its right and the pictures between.
 */
public final class CameraWorkspaceLayout {
    /** The tool row, and its padding and the room between its three parts. */
    public static final int BAR = 44, BAR_PAD = 12, BAR_GAP = 16;
    /** Round the pictures. */
    public static final int MARGIN = 10;
    /** Between the pictures and the machine panel. */
    public static final int INFO_GAP = 12;
    /** The strip's two sides where what they hold is not known. */
    public static final int STRIP_LEFT = 300, STRIP_RIGHT = 250;
    /** The narrowest the pictures are left by the wide manual controls, which otherwise fold. */
    public static final int MIN_PICTURES = 240;

    public enum Mode {
        /** The large camera: tool row, pictures, manual controls. */
        Large,
        /** The camera strip over the page. */
        Strip,
        /** Production mode: the pictures and the banner over them, nothing else. */
        Operator,
        /** The camera hidden, a sliver over the page: the pictures alone, in what there is. */
        Hidden
    }

    /** What the card has and what its parts want. */
    public static final class Input {
        public int width, height;
        /**
         * The height the page would give the card if its pictures did not leave any of it, or 0
         * for its height. The card is laid out at its height; the fitted height comes from this.
         */
        public int wanted;
        public Mode mode = Mode.Large;
        /** Whether the room above and below the pictures is the page's to take. */
        public boolean fit;
        /** The height of a banner along the top - a wizard's instructions - or 0. */
        public int banner;
        public int selector;
        /** The readout's width in its one-row forms: with units, without, and a size smaller. */
        public int droRow, droBare, droTight;
        /** The readout's size in two rows, for the manual controls or beside the pictures. */
        public int droGridWidth, droGridHeight;
        public int tools, toolsCompact;
        /** The manual controls folded by hand. */
        public boolean folded;
        public boolean info = true;
        /** The machine panel's height: it shows only where the pictures' band is as tall. */
        public int infoHeight;
        /**
         * The widest of what the strip holds at its left - the camera choice, the readout - and at
         * its right - the sizes, Home, the handle - or 0.
         */
        public int stripLeftWidth, stripRightWidth;
        /** The pictures' size in a rectangle: whole, each under its bar. */
        public BiFunction<Integer, Integer, Dimension> pictures = (w, h) -> new Dimension(w, h);
    }

    public static final class Result {
        public Rectangle selector, dro, tools, banner, video, info, column;
        public DroPanel.Form droForm;
        public boolean droInColumn, toolsCompact, autoFolded;
        public JogCard.Level level;
        /** The tool row's line underneath, and the column's down its left, or -1. */
        public int barLine = -1, columnLine = -1;
        /** The strip's two sides. */
        public Rectangle stripLeft, stripRight;
        /**
         * The card's height with no room to spare above and below the pictures, of the wanted
         * height: what the pictures and the manual controls use there. It depends on the wanted
         * height and not on the card's own, so a card given its fitted height fits at the same
         * height again - worked out from the card's own height, a lower card fitted lower again
         * and shrank away. The card's height where nothing is fitted.
         */
        public int fittedHeight;
    }

    private CameraWorkspaceLayout() {
    }

    public static Result lay(Input in) {
        Result r = new Result();
        r.fittedHeight = in.height;
        switch (in.mode) {
            case Strip:
                strip(in, r);
                break;
            case Operator:
                operator(in, r, in.banner);
                break;
            case Hidden:
                operator(in, r, 0);
                break;
            default:
                large(in, r);
                break;
        }
        return r;
    }

    private static void large(Input in, Result r) {
        r.barLine = BAR;
        // The tool row, narrowing its parts one step at a time.
        int avail = in.width - 2 * BAR_PAD - 2 * BAR_GAP;
        int dro;
        if (in.selector + in.droRow + in.tools <= avail) {
            r.droForm = DroPanel.Form.Row;
            dro = in.droRow;
        }
        else if (in.selector + in.droBare + in.tools <= avail) {
            r.droForm = DroPanel.Form.RowBare;
            dro = in.droBare;
        }
        else if (in.selector + in.droBare + in.toolsCompact <= avail) {
            r.droForm = DroPanel.Form.RowBare;
            r.toolsCompact = true;
            dro = in.droBare;
        }
        else if (in.selector + in.droTight + in.toolsCompact <= avail) {
            r.droForm = DroPanel.Form.RowTight;
            r.toolsCompact = true;
            dro = in.droTight;
        }
        else {
            r.droForm = DroPanel.Form.Grid;
            r.droInColumn = true;
            r.toolsCompact = true;
            dro = 0;
        }
        int toolsW = r.toolsCompact ? in.toolsCompact : in.tools;
        r.selector = new Rectangle(BAR_PAD, 0, in.selector, BAR);
        r.tools = new Rectangle(in.width - BAR_PAD - toolsW, 0, toolsW, BAR);
        if (!r.droInColumn) {
            int from = BAR_PAD + in.selector + BAR_GAP;
            int to = r.tools.x - BAR_GAP;
            r.dro = new Rectangle(Math.max(from, from + (to - from - dro) / 2), 0, dro, BAR);
        }

        int top = BAR + 1;
        if (in.banner > 0) {
            r.banner = new Rectangle(MARGIN, top + MARGIN, in.width - 2 * MARGIN, in.banner);
            top += MARGIN + in.banner;
        }
        int bodyH = Math.max(0, in.height - top);

        // A readout with no room in the tool row goes to the top of the manual controls, or,
        // where they are folded, beside the pictures: in a folded column it was out of sight.
        boolean droOut = r.droInColumn;
        int extra = droOut ? JogCard.DRO_NEEDED : 0;
        JogCard.Level level = level(bodyH, extra, in.width);
        r.level = level == null ? JogCard.Level.Wide : level;
        r.autoFolded = level == null;
        int beside = in.droGridWidth + MARGIN;
        int colW = in.folded || r.autoFolded ? JogCard.FOLDED_WIDTH
                : r.level == JogCard.Level.Wide ? JogCard.WIDE_WIDTH : JogCard.WIDTH;
        boolean line = !(in.folded || r.autoFolded);
        r.column = new Rectangle(in.width - colW, top, colW, bodyH);
        if (line) {
            r.columnLine = r.column.x - 1;
        }

        // The pictures in what is left, and what they leave.
        int areaX = MARGIN, areaY = top + MARGIN;
        int areaW = Math.max(0, r.column.x - (line ? 1 : 0) - 2 * MARGIN);
        int areaH = Math.max(0, bodyH - 2 * MARGIN);
        if (droOut && !line) {
            r.droInColumn = false;
            r.dro = new Rectangle(areaX, areaY, in.droGridWidth, in.droGridHeight);
            areaX += beside;
            areaW = Math.max(0, areaW - beside);
        }
        Dimension pictures = in.pictures.apply(areaW, areaH);
        if (in.fit) {
            // The same steps at the wanted height: the size of the controls there, and the
            // pictures in what they leave.
            int wantedBody = Math.max(0, (in.wanted > 0 ? in.wanted : in.height) - top);
            JogCard.Level there = level(wantedBody, extra, in.width);
            boolean foldedThere = in.folded || there == null;
            int columnThere = foldedThere ? JogCard.FOLDED_WIDTH
                    : there == JogCard.Level.Wide ? JogCard.WIDE_WIDTH : JogCard.WIDTH;
            boolean besideThere = droOut && foldedThere;
            int widthThere = Math.max(0, in.width - columnThere - (foldedThere ? 0 : 1) - 2 * MARGIN
                    - (besideThere ? beside : 0));
            Dimension picturesThere = in.pictures.apply(widthThere, Math.max(0, wantedBody - 2 * MARGIN));
            int need = foldedThere ? besideThere ? in.droGridHeight + 2 * MARGIN : 0 : JogCard.needed(there) + extra;
            r.fittedHeight = top + Math.max(picturesThere.height + 2 * MARGIN, need);
        }
        int spare = areaW - pictures.width;
        int infoW = in.info && spare >= MachineInfoPanel.MIN_ROOM && areaH >= in.infoHeight
                ? Math.min(MachineInfoPanel.MAX_WIDTH, spare - INFO_GAP) : 0;
        int group = pictures.width + (infoW > 0 ? INFO_GAP + infoW : 0);
        int x = areaX + (areaW - group) / 2;
        int y = areaY + (areaH - pictures.height) / 2;
        r.video = new Rectangle(x, y, pictures.width, pictures.height);
        if (infoW > 0) {
            int infoH = Math.max(pictures.height, in.infoHeight);
            r.info = new Rectangle(x + pictures.width + INFO_GAP, areaY + (areaH - infoH) / 2, infoW, infoH);
        }
    }

    /**
     * The largest size of the manual controls a height takes, the wide one only where it leaves
     * the pictures some width; null where none fits and they fold.
     */
    private static JogCard.Level level(int bodyH, int extra, int width) {
        for (JogCard.Level l : JogCard.Level.values()) {
            boolean roomy = l != JogCard.Level.Wide || width - JogCard.WIDE_WIDTH - 1 - 2 * MARGIN >= MIN_PICTURES;
            if (bodyH >= JogCard.needed(l) + extra && roomy) {
                return l;
            }
        }
        return null;
    }

    private static void strip(Input in, Result r) {
        // Each side as wide as what it holds: at a fixed 300 and 250 a narrow window left the
        // pictures between them a couple of dozen pixels.
        int left = in.stripLeftWidth > 0 ? in.stripLeftWidth + 2 * BAR_PAD : STRIP_LEFT;
        int right = in.stripRightWidth > 0 ? in.stripRightWidth + 2 * BAR_PAD : STRIP_RIGHT;
        r.stripLeft = new Rectangle(0, 0, left, in.height);
        r.stripRight = new Rectangle(in.width - right, 0, right, in.height);
        r.droForm = DroPanel.Form.Grid;
        int areaX = left + 1 + 8;
        int areaW = Math.max(0, in.width - left - right - 2 - 16);
        int areaH = Math.max(0, in.height - 16);
        Dimension pictures = in.pictures.apply(areaW, areaH);
        r.video = new Rectangle(areaX + (areaW - pictures.width) / 2, 8 + (areaH - pictures.height) / 2,
                pictures.width, pictures.height);
    }

    private static void operator(Input in, Result r, int banner) {
        int top = 0;
        if (banner > 0) {
            r.banner = new Rectangle(MARGIN, MARGIN, in.width - 2 * MARGIN, banner);
            top = MARGIN + banner;
        }
        int areaW = Math.max(0, in.width - 2 * MARGIN);
        int areaH = Math.max(0, in.height - top - 2 * MARGIN);
        Dimension pictures = in.pictures.apply(areaW, areaH);
        r.video = new Rectangle(MARGIN + (areaW - pictures.width) / 2, top + MARGIN + (areaH - pictures.height) / 2,
                pictures.width, pictures.height);
    }
}
