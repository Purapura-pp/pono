package org.openpnp.machine.photon.sheets.gui;

import org.openpnp.gui.shell.Ui;
import org.openpnp.machine.photon.PhotonFeeder;

import javax.swing.*;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;

/**
 * One cell for every address searched, in the colours the search's legend names: asking, a
 * feeder, none. The cells keep the last search's answers until the next.
 */
public class FeederSearchProgressBar extends JPanel {
    private int numberOfElements;
    private final Map<Integer, PhotonFeeder.FeederSearchState> feederSearchStateMap;

    public FeederSearchProgressBar() {
        numberOfElements = 0;
        feederSearchStateMap = new HashMap<>();
        setOpaque(false);
        setPreferredSize(new Dimension(com.formdev.flatlaf.util.UIScale.scale(280),
                com.formdev.flatlaf.util.UIScale.scale(14)));
    }

    private Rectangle getRectangleForElement(int elementNumber) {
        int totalWidth = getWidth();
        int totalHeight = getHeight();

        int startX = (elementNumber * totalWidth) / numberOfElements;
        int endX = ((elementNumber + 1) * totalWidth) / numberOfElements;
        return new Rectangle(startX, 0, endX - startX, totalHeight);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        for (int elementNumber = 0; elementNumber < numberOfElements; elementNumber++) {
            PhotonFeeder.FeederSearchState feederSearchState;
            feederSearchState = feederSearchStateMap.getOrDefault(elementNumber, PhotonFeeder.FeederSearchState.UNKNOWN);
            switch (feederSearchState) {
                case SEARCHING:
                    g.setColor(Ui.warn());
                    break;
                case FOUND:
                    g.setColor(Ui.ok());
                    break;
                case MISSING:
                    g.setColor(Ui.info());
                    break;
                default:
                    g.setColor(Ui.surface3());
                    break;
            }
            Rectangle rectangle = getRectangleForElement(elementNumber);
            g.fillRect(rectangle.x, rectangle.y, rectangle.width, rectangle.height);
        }

        g.setColor(Ui.border());
        g.drawRect(0, 0, getWidth() - 1, getHeight() - 1);
    }

    public void clearAllState() {
        feederSearchStateMap.clear();
        this.repaint();
    }

    public void updateFeederState(int feederAddress, PhotonFeeder.FeederSearchState feederSearchState) {
        feederSearchStateMap.put(feederAddress - 1, feederSearchState);
        this.repaint();
    }

    public void setNumberOfElements(int numberOfElements) {
        this.numberOfElements = numberOfElements;
        this.repaint();
    }
}
