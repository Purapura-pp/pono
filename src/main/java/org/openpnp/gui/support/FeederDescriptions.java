/*
 * Copyright (C) 2026 Pono
 * 
 * This file is part of OpenPnP.
 * 
 * OpenPnP is free software: you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * OpenPnP is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with OpenPnP. If not, see
 * <http://www.gnu.org/licenses/>.
 * 
 * For more information about OpenPnP visit http://openpnp.org
 */

package org.openpnp.gui.support;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.openpnp.Translations;
import org.openpnp.machine.pandaplacer.AbstractPandaplacerVisionFeeder;
import org.openpnp.machine.photon.PhotonFeeder;
import org.openpnp.machine.reference.feeder.BlindsFeeder;
import org.openpnp.machine.reference.feeder.ReferenceDragFeeder;
import org.openpnp.machine.reference.feeder.ReferenceLeverFeeder;
import org.openpnp.machine.reference.feeder.ReferencePushPullFeeder;
import org.openpnp.machine.reference.feeder.ReferenceRotatedTrayFeeder;
import org.openpnp.machine.reference.feeder.ReferenceStripFeeder;
import org.openpnp.machine.reference.feeder.ReferenceTrayFeeder;
import org.openpnp.model.Length;
import org.openpnp.model.LengthUnit;
import org.openpnp.spi.Feeder;
import org.openpnp.spi.base.AbstractFeeder;

/**
 * A feeder as a line of text, where the pages say which feeder carries a part: what kind it is,
 * its tape, where it sits and what it has left. One place, so that the placement form, the
 * feeders table and the feeder's own form say the same.
 */
public final class FeederDescriptions {
    private static final String NONE = "\u2014"; //$NON-NLS-1$

    private FeederDescriptions() {
    }

    /** "8mm 料带 · 余 1,240": what the feeder is and what it has left, for the placement form. */
    public static String summary(Feeder feeder) {
        if (feeder == null) {
            return ""; //$NON-NLS-1$
        }
        Length width = tapeWidth(feeder);
        String what = width != null && width.getValue() > 0
                ? String.format(Translations.getString("FeederDescriptions.Tape"), number(width)) //$NON-NLS-1$
                : DisplayNames.typeName(feeder.getClass());
        Integer left = feeder.getPartsLeft();
        return left == null ? what
                : what + " \u00b7 " + String.format(Translations.getString("FeederDescriptions.Left"), count(left)); //$NON-NLS-1$ //$NON-NLS-2$
    }

    /**
     * "料带飞达 · 槽位 B2": the kind and the slot where known. The mockup had the class name
     * between them; no class name is shown to the people at the machine.
     */
    public static String subtitle(Feeder feeder) {
        String text = DisplayNames.typeName(feeder.getClass());
        String slot = slot(feeder);
        return NONE.equals(slot) ? text
                : text + " \u00b7 " + String.format(Translations.getString("FeederDescriptions.Slot"), slot); //$NON-NLS-1$ //$NON-NLS-2$
    }

    /** The tape's width, for the feeders that take tape and are told how wide it is. */
    public static Length tapeWidth(Feeder feeder) {
        if (feeder instanceof ReferenceStripFeeder) {
            return ((ReferenceStripFeeder) feeder).getTapeWidth();
        }
        if (feeder instanceof ReferencePushPullFeeder) {
            return ((ReferencePushPullFeeder) feeder).getTapeWidth();
        }
        return null;
    }

    /** The distance from one part to the next, for the feeders that step along a tape or a row. */
    public static Length pitch(Feeder feeder) {
        if (feeder instanceof ReferenceStripFeeder) {
            return ((ReferenceStripFeeder) feeder).getPartPitch();
        }
        if (feeder instanceof ReferencePushPullFeeder) {
            return ((ReferencePushPullFeeder) feeder).getPartPitch();
        }
        if (feeder instanceof ReferenceLeverFeeder) {
            return ((ReferenceLeverFeeder) feeder).getPartPitch();
        }
        if (feeder instanceof ReferenceDragFeeder) {
            return ((ReferenceDragFeeder) feeder).getPartPitch();
        }
        if (feeder instanceof AbstractPandaplacerVisionFeeder) {
            return ((AbstractPandaplacerVisionFeeder) feeder).getPartPitch();
        }
        if (feeder instanceof BlindsFeeder) {
            return ((BlindsFeeder) feeder).getPocketPitch();
        }
        if (feeder instanceof PhotonFeeder) {
            return new Length(((PhotonFeeder) feeder).getPartPitch(), LengthUnit.Millimeters);
        }
        return null;
    }

    /** "8 / 4 mm", "— / 2 mm" or "5 × 8 格": the tape, or the tray's grid, or a dash. */
    public static String tape(Feeder feeder) {
        if (feeder instanceof ReferenceTrayFeeder) {
            ReferenceTrayFeeder tray = (ReferenceTrayFeeder) feeder;
            return String.format(Translations.getString("FeederDescriptions.Grid"), //$NON-NLS-1$
                    tray.getEffectiveTrayCountX(), tray.getEffectiveTrayCountY());
        }
        if (feeder instanceof ReferenceRotatedTrayFeeder) {
            ReferenceRotatedTrayFeeder tray = (ReferenceRotatedTrayFeeder) feeder;
            return String.format(Translations.getString("FeederDescriptions.Grid"), //$NON-NLS-1$
                    tray.getEffectiveTrayCountCols(), tray.getEffectiveTrayCountRows());
        }
        Length width = tapeWidth(feeder);
        Length pitch = pitch(feeder);
        boolean hasWidth = width != null && width.getValue() > 0;
        boolean hasPitch = pitch != null && pitch.getValue() > 0;
        if (!hasWidth && !hasPitch) {
            return NONE;
        }
        return (hasWidth ? number(width) : NONE) + " / " + (hasPitch ? number(pitch) : NONE) + " mm"; //$NON-NLS-1$ //$NON-NLS-2$
    }

    /**
     * Where the feeder sits: a Photon feeder's slot address, which the bus decides, or the slot
     * the feeder was given.
     */
    public static String slot(Feeder feeder) {
        if (feeder instanceof PhotonFeeder) {
            Integer address = ((PhotonFeeder) feeder).getSlotAddress();
            return address == null ? NONE : String.valueOf(address);
        }
        if (feeder instanceof AbstractFeeder && ((AbstractFeeder) feeder).getSlotName() != null) {
            return ((AbstractFeeder) feeder).getSlotName();
        }
        return NONE;
    }

    /** The slot can be named by hand: every feeder's but a Photon feeder's, whose bus names it. */
    public static boolean slotEditable(Feeder feeder) {
        return feeder instanceof AbstractFeeder && !(feeder instanceof PhotonFeeder);
    }

    /** "1,240 件", or "未知" for a feeder that has not been told what it holds. */
    public static String partsLeft(Feeder feeder) {
        Integer left = feeder.getPartsLeft();
        return left == null ? Translations.getString("FeederDescriptions.Unknown") //$NON-NLS-1$
                : String.format(Translations.getString("FeederDescriptions.Parts"), count(left)); //$NON-NLS-1$
    }

    /** "1,240". */
    public static String count(int count) {
        return String.format(Locale.US, "%,d", count); //$NON-NLS-1$
    }

    /**
     * "刚刚", "3 分钟前", "2 小时前", "昨天" or "9月20日": how long ago, by the clock given, as
     * the feeders table says when a part was last picked.
     */
    public static String since(long millis, long now) {
        if (millis <= 0) {
            return NONE;
        }
        long seconds = Math.max(0, (now - millis) / 1000);
        if (seconds < 60) {
            return Translations.getString("FeederDescriptions.JustNow"); //$NON-NLS-1$
        }
        if (seconds < 3600) {
            return String.format(Translations.getString("FeederDescriptions.MinutesAgo"), seconds / 60); //$NON-NLS-1$
        }
        if (seconds < 12 * 3600) {
            return String.format(Translations.getString("FeederDescriptions.HoursAgo"), seconds / 3600); //$NON-NLS-1$
        }
        ZoneId zone = ZoneId.systemDefault();
        LocalDate day = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate();
        LocalDate today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate();
        if (day.equals(today)) {
            return String.format(Translations.getString("FeederDescriptions.HoursAgo"), seconds / 3600); //$NON-NLS-1$
        }
        if (day.equals(today.minusDays(1))) {
            return Translations.getString("FeederDescriptions.Yesterday"); //$NON-NLS-1$
        }
        return DateTimeFormatter.ofPattern(Translations.getString("FeederDescriptions.Date")).format(day); //$NON-NLS-1$
    }

    /** The date and time, for the tooltip over "3 分钟前". */
    public static String when(long millis) {
        return millis <= 0 ? NONE
                : DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss") //$NON-NLS-1$
                        .format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()));
    }

    /** A length in millimetres as a person writes it: "0.45 mm", "8 mm". */
    public static String length(Length length) {
        if (length == null) {
            return NONE;
        }
        return number(length) + " mm"; //$NON-NLS-1$
    }

    /** The millimetres without the unit: "8", "0.45". */
    private static String number(Length length) {
        double mm = length.convertToUnits(LengthUnit.Millimeters).getValue();
        String text = String.format(Locale.ROOT, "%.3f", mm).replaceAll("0+$", ""); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        return text.endsWith(".") ? text.substring(0, text.length() - 1) : text; //$NON-NLS-1$
    }
}
