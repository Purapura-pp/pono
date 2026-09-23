package org.openpnp.gui.support;

import java.awt.Color;

import javax.swing.Icon;
import javax.swing.ImageIcon;

import org.openpnp.gui.shell.Ui;

import com.formdev.flatlaf.extras.FlatSVGIcon;

/**
 * The icons of the pages and wizards, by what they do. Every one is an icon of the mockups' set,
 * {@code icons/pono}: monochrome, drawn in the colour of the component's text, so it follows the
 * theme and the enabled state. The coloured multi-tone icons these names used to stand for are
 * gone; where an icon carried its meaning in colour - a power button on or off, a pin enabled or
 * not - the colour is the stylesheet's.
 * <p>
 * The diagrams a few wizards show, such as the nozzle arrangements and the Safe Z modes, are
 * pictures rather than icons and stay as they were drawn, under {@code icons/diagrams}.
 */
public class Icons {
    private static final int SIZE = 18;

    public static Icon add = pono("plus");
    public static Icon delete = pono("trash");
    public static Icon copy = pono("copy");
    public static Icon paste = pono("clipboard");
    public static Icon export = pono("download");
    public static Icon importt = pono("upload");

    public static Icon nozzleAdd = pono("nozzle");
    public static Icon nozzleRemove = pono("trash");

    public static Icon nozzleTipAdd = pono("plus");
    public static Icon nozzleTipRemove = pono("x");
    public static Icon nozzleTipLoad = pono("download");
    public static Icon nozzleTipUnload = pono("upload");

    public static Icon captureCamera = pono("camera");
    public static Icon captureTool = pono("nozzle");
    public static Icon capturePin = pono("pin");

    public static Icon centerCamera = pono("target");
    public static Icon centerCameraMoveNext = pono("chevsright");
    public static Icon centerTool = pono("crosshair");
    public static Icon centerToolNoSafeZ = pono("crosshair");
    public static Icon contactProbeNozzle = pono("probe");
    public static Icon centerPin = pono("pin");
    public static Icon centerPinNoSafeZ = pono("pin");
    public static Icon centerCameraOnFeeder = pono("target");
    public static Icon centerNozzleOnFeeder = pono("crosshair");

    public static Icon colorFalse = Ui.icon("dot", SIZE, new Color(0x7f8a9c)); //$NON-NLS-1$
    public static Icon colorTrue = Ui.icon("dot", SIZE, new Color(0x34c77b)); //$NON-NLS-1$
    
    public static Icon start = pono("play");
    public static Icon pause = pono("pause");
    public static Icon step = pono("step");
    public static Icon stop = pono("stop");

    public static Icon errorDefer = pono("clock");
    public static Icon errorAlert = pono("alert");

    public static Icon twoPointLocate = pono("move");
    public static Icon fiducialCheck = pono("target");
    public static Icon autoPanelize = pono("grid");
    public static Icon autoPanelizeXOut = pono("x");
    public static Icon autoPanelizeFidCheck = pono("target");
    public static Icon useChildFiducial = pono("crosshair");
    public static Icon board = pono("board");
    public static Icon panel = pono("layers");
    public static Icon clean = pono("broom");

    public static Icon feed = pono("step");
    public static Icon pick = pono("hand");
    public static Icon place = pono("job");
    public static Icon showPart = pono("footprint");
    public static Icon editFeeder = pono("edit");
    public static Icon feeder = pono("feeder");

    public static Icon partAlign = pono("target");

    public static Icon arrowUp = pono("up");
    public static Icon arrowDown = pono("down");
    public static Icon arrowLeft = pono("left");
    public static Icon arrowRight = pono("right");
    public static Icon home = pono("home");
    public static Icon homeWarning = Ui.icon("home", SIZE, new Color(0xf5b840)); //$NON-NLS-1$
    public static Icon refresh = pono("refresh");
    public static Icon rotateClockwise = pono("rcw");
    public static Icon rotateCounterclockwise = pono("rccw");
    public static Icon zero = pono("circle");
    
    public static Icon navigateFirst = pono("chevsleft");
    public static Icon navigateLast = pono("chevsright");
    public static Icon navigatePrevious = pono("chevleft");
    public static Icon navigateNext = pono("chevright");

    public static Icon pinDisabled = Ui.icon("pin", SIZE, new Color(0x7f8a9c)); //$NON-NLS-1$
    public static Icon pinEnabled = Ui.icon("pin", SIZE, new Color(0x4f8cff)); //$NON-NLS-1$

    public static Icon powerOn = Ui.icon("power", SIZE, new Color(0x34c77b)); //$NON-NLS-1$
    public static Icon powerOff = Ui.icon("power", SIZE, new Color(0x7f8a9c)); //$NON-NLS-1$

    public static Icon park = pono("park");

    public static Icon scrollDown = pono("down");

    public static Icon lockOutline = pono("lock");
    public static Icon lockOpenOutline = pono("unlock");
    public static Icon lockQuestion = Ui.icon("lock", SIZE, new Color(0xf5b840)); //$NON-NLS-1$

    public static Icon openSCadIcon = pono("download");
    public static Icon processActivity1Icon = pono("loader");
    public static Icon processActivity2Icon = pono("activity");

    public static Icon axisCartesian = pono("machine");
    public static Icon axisRotation = pono("rcw");
    public static Icon captureAxisLow = pono("download");
    public static Icon captureAxisHigh = pono("upload");
    public static Icon positionAxisLow = pono("down");
    public static Icon positionAxisHigh = pono("up");
    public static Icon driver = pono("sliders");
    public static Icon solutions = pono("alert");
    public static Icon accept = pono("check");
    public static Icon dismiss = pono("x");
    public static Icon undo = pono("undo");
    public static Icon info = pono("info");

    public static Icon nozzleSingle = getIcon("/icons/diagrams/nozzle-single.svg", 96, 96);
    public static Icon nozzleDualNeg = getIcon("/icons/diagrams/nozzle-neg.svg", 96, 96);
    public static Icon nozzleDualCam = getIcon("/icons/diagrams/nozzle-cam.svg", 96, 96);

    public static Icon milestone = getIcon("/icons/diagrams/milestone.svg", 96, 96);
    public static Icon camAxisTransform = getIcon("/icons/diagrams/cam-axis-transform.svg", 283, 283);
    public static Icon safeZFixed = getIcon("/icons/diagrams/safe-z-fixed.svg", 96, 96);
    public static Icon safeZDynamic = getIcon("/icons/diagrams/safe-z-dynamic.svg", 96, 96);
    public static Icon safeZCapture = getIcon("/icons/diagrams/safe-z-capture.svg", 96, 96);

    public static Icon footprintQuad = pono("pkg");
    public static Icon footprintDual = pono("parts");
    public static Icon footprintBga = pono("grid");
    public static Icon footprintToggle = pono("footprint");
    public static Icon kicad = getIcon("/icons/diagrams/kicad-logo.svg");

    private static Icon pono(String name) {
        return Ui.icon(name, SIZE);
    }

    public static Icon getIcon(String resourceName, int width, int height) {
        if (resourceName.endsWith(".svg")) {
            return new FlatSVGIcon(resourceName.substring(1), width, height);
        }
        else {
            return new ImageIcon(Icons.class.getResource(resourceName));
        }
    }

    public static Icon getIcon(String resourceName) {
        return getIcon(resourceName, 24, 24);
    }
}
