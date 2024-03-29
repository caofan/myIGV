/*
 * Copyright (c) 2007-2012 The Broad Institute, Inc.
 * SOFTWARE COPYRIGHT NOTICE
 * This software and its documentation are the copyright of the Broad Institute, Inc. All rights are reserved.
 *
 * This software is supplied without any warranty or guaranteed support whatsoever. The Broad Institute is not responsible for its use, misuse, or functionality.
 *
 * This software is licensed under the terms of the GNU Lesser General Public License (LGPL),
 * Version 2.1 which is available at http://www.opensource.org/licenses/lgpl-2.1.php.
 */


package org.broad.igv.track;

//~--- non-JDK imports --------------------------------------------------------

import org.apache.log4j.Logger;
import org.broad.igv.Globals;
import org.broad.igv.PreferenceManager;
import org.broad.igv.feature.AminoAcidManager;
import org.broad.igv.feature.Strand;
import org.broad.igv.renderer.GraphicUtils;
import org.broad.igv.renderer.Renderer;
import org.broad.igv.renderer.SequenceRenderer;
import org.broad.igv.ui.FontManager;
import org.broad.igv.ui.IGV;
import org.broad.igv.ui.panel.FrameManager;
import org.broad.igv.ui.panel.IGVPopupMenu;
import org.broad.igv.ui.panel.ReferenceFrame;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.net.URL;


/**
 * @author jrobinso
 */
public class SequenceTrack extends AbstractTrack {

    private static Logger log = Logger.getLogger(SequenceTrack.class);

    private static final int SEQUENCE_HEIGHT = 14;

    private static String NAME = "Sequence";

    private SequenceRenderer sequenceRenderer = new SequenceRenderer();

    //should translated aminoacids be shown below the sequence?
    private boolean shouldShowTranslation = true;

    //is sequence visible (zoomed in far enough, etc)
    private boolean sequenceVisible = false;

    Strand strand = Strand.POSITIVE;

    /**
     * If true show sequence in "color space"  (for SOLID alignments).  Currently not implemented.
     */
    private boolean showColorSpace = false;
    private Rectangle arrowRect;

    public SequenceTrack(String name) {
        super(name);
        setSortable(false);
        shouldShowTranslation = PreferenceManager.getInstance().getAsBoolean(PreferenceManager.SHOW_SEQUENCE_TRANSLATION);

    }


    @Override
    public void renderName(Graphics2D graphics, Rectangle trackRectangle, Rectangle visibleRectangle) {
        Font font = FontManager.getFont(fontSize);
        if (sequenceVisible) {
            graphics.setFont(font);
            int textBaseline = trackRectangle.y + 12;
            graphics.drawString(NAME, trackRectangle.x + 5, textBaseline);

            int rx = trackRectangle.x + trackRectangle.width - 20;
            arrowRect = new Rectangle(rx, trackRectangle.y + 2, 15, 10);
            drawArrow(graphics);

            //Show icon when translation non-standard
            if (AminoAcidManager.getInstance().getCodonTable().getId() != AminoAcidManager.STANDARD_TABLE_ID) {
                Font labFont = font.deriveFont(Font.BOLD);
                graphics.setFont(labFont);
                graphics.drawString("A", rx - 20, textBaseline);
                graphics.setFont(font);
            }

        }
    }

    private Image getImageIcon() {
        String path = "resources/thick_helix.png";
        URL url = getClass().getResource(path);
        return new ImageIcon(url).getImage();
    }

    private void drawArrow(Graphics2D graphics) {
        GraphicUtils.drawHorizontalArrow(graphics, arrowRect, strand == Strand.POSITIVE);
    }

    /**
     * Render the sequence, and optionally the 3 frame translation table
     *
     * @param context
     * @param rect
     */
    public void render(RenderContext context, Rectangle rect) {
        // Are we zoomed in far enough to show the sequence?  Scale is
        // in BP / pixel,  need at least 1 pixel  per bp in order to show sequence.

        int resolutionThreshold = PreferenceManager.getInstance().getAsInt(PreferenceManager.MAX_SEQUENCE_RESOLUTION);
        // TODO -- this should be calculated from a "rescale" event
        boolean visible = FrameManager.getMinimumScale() < resolutionThreshold &&
                !context.getChr().equals(Globals.CHR_ALL);

        if (visible != sequenceVisible) {
            sequenceVisible = visible;
            IGV.getInstance().doRefresh();
        }
        if (sequenceVisible) {
            sequenceRenderer.setStrand(strand);
            sequenceRenderer.draw(context, rect, showColorSpace, shouldShowTranslation, resolutionThreshold);
        }
    }


    @Override
    public int getHeight() {
        return sequenceVisible ? SEQUENCE_HEIGHT + (showColorSpace ? SEQUENCE_HEIGHT : 0) +
                (shouldShowTranslation ? SequenceRenderer.TranslatedSequenceDrawer.TOTAL_HEIGHT : 0) :
                0;
    }


    @Override
    public boolean handleDataClick(TrackClickEvent e) {
        setShouldShowTranslation(!shouldShowTranslation);
        Object source = e.getMouseEvent().getSource();
        if (source instanceof JComponent) {
            repaint();

        }
        return true;
    }

    @Override
    public void handleNameClick(final MouseEvent e) {
        if (arrowRect != null && arrowRect.contains(e.getPoint())) {
            flipStrand();
        }

    }

    private void flipStrand() {
        strand = (strand == Strand.POSITIVE ? Strand.NEGATIVE : Strand.POSITIVE);
        repaint();
        IGV.getInstance().clearSelections();
    }

    public void setShouldShowTranslation(boolean shouldShowTranslation) {
        this.shouldShowTranslation = shouldShowTranslation;
        // Remember this choice
        PreferenceManager.getInstance().put(PreferenceManager.SHOW_SEQUENCE_TRANSLATION, shouldShowTranslation);
    }


    /**
     * Override to return a specialized popup menu
     *
     * @return
     */
    @Override
    public IGVPopupMenu getPopupMenu(final TrackClickEvent te) {

        IGVPopupMenu menu = new IGVPopupMenu();

        JMenuItem m1 = new JMenuItem("Flip strand");
        m1.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent e) {
                flipStrand();

            }
        });

        final JCheckBoxMenuItem m2 = new JCheckBoxMenuItem("Show translation");
        m2.setSelected(shouldShowTranslation);
        m2.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent e) {
                setShouldShowTranslation(m2.isSelected());
                repaint();
                IGV.getInstance().clearSelections();
            }
        });

        menu.add(m1);
        menu.add(m2);

        final JMenu transTableMenu = new JMenu("Translation Table");
        for (AminoAcidManager.CodonTable codonTable : AminoAcidManager.getInstance().getAllCodonTables()) {
            JMenuItem item = getCodonTableMenuItem(codonTable);
            transTableMenu.add(item);
        }
        menu.add(transTableMenu);

        return menu;
    }

    private JCheckBoxMenuItem getCodonTableMenuItem(AminoAcidManager.CodonTable codonTable) {

        JCheckBoxMenuItem item = new JCheckBoxMenuItem();
        String fullName = codonTable.getDisplayName();
        String shortName = fullName;
        if (fullName.length() > 40) {
            shortName = fullName.substring(0, 37) + "...";
            item.setToolTipText(fullName);
        }
        item.setText(shortName);
        final AminoAcidManager.CodonTableKey curKey = codonTable.getKey();
        item.setSelected(curKey.equals(AminoAcidManager.getInstance().getCodonTable().getKey()));
        item.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                AminoAcidManager.getInstance().setCodonTable(curKey);
                repaint();
            }
        });
        return item;
    }


    private void repaint() {
        // TODO -- what's really needed is a repaint of all panels the sequence track intersects
        IGV.getMainFrame().repaint();
    }

    // SequenceTrack does not expose its renderer

    public Renderer getRenderer() {
        return null;
    }

    @Override
    public String getNameValueString(int y) {
        String nvs = "<html>" + super.getNameValueString(y);
        nvs += "<br>Translation Table: ";
        nvs += AminoAcidManager.getInstance().getCodonTable().getDisplayName();
        return nvs;
    }

    public String getValueStringAt(String chr, double position, int y, ReferenceFrame frame) {
        if (sequenceVisible && !this.sequenceRenderer.hasSequence()) {
            return "Sequence info not found. Make sure the server in question supports byte-range requests, and that "
                    + "there are no firewalls which remove this information";
        } else {
            return null;
        }
    }

    public Strand getStrand() {
        return this.strand;
    }


}
