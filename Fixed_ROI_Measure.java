import ij.IJ;
import ij.ImageListener;
import ij.ImagePlus;
import ij.Prefs;
import ij.WindowManager;
import ij.gui.ImageCanvas;
import ij.gui.OvalRoi;
import ij.gui.Overlay;
import ij.gui.Roi;
import ij.gui.Toolbar;
import ij.measure.Calibration;
import ij.measure.Measurements;
import ij.measure.ResultsTable;
import ij.plugin.filter.Analyzer;
import ij.plugin.frame.PlugInFrame;
import ij.plugin.frame.RoiManager;
import ij.process.ColorProcessor;

import java.awt.*;
import java.awt.event.*;
import java.util.HashSet;
import java.util.Set;

/**
 * Fixed ROI Measure - Fiji/ImageJ plugin
 * https://github.com/wangesther572-boop/Fixed-ROI-Measure
 *
 * A small control window to place a fixed-size rectangle or oval on an image,
 * move it by clicking/dragging, and measure intensity on a chosen channel (1-4).
 */
public class Fixed_ROI_Measure extends PlugInFrame
        implements ActionListener, MouseListener, ImageListener {

    private static Fixed_ROI_Measure instance;

    private Choice channelChoice, shapeChoice, unitChoice;
    private TextField widthField, heightField;
    private Checkbox clickToPlace;
    private Button showBtn, cellBtn, bgBtn, resetBtn;
    private Label statusLabel;

    private int cellCount = 0, bgCount = 0;
    private final Set<ImageCanvas> canvases = new HashSet<ImageCanvas>();
    private boolean pressedOnShape = false;

    public Fixed_ROI_Measure() {
        super("Fixed ROI Measure");
    }

    @Override
    public void run(String arg) {
        if (instance != null && instance.isShowing()) {   // only one control window
            WindowManager.removeWindow(this);
            dispose();
            instance.toFront();
            return;
        }
        instance = this;
        buildUI();
        ImagePlus.addImageListener(this);
        int[] ids = WindowManager.getIDList();
        if (ids != null)
            for (int id : ids) attach(WindowManager.getImage(id));
    }

    // ------------------------------------------------------------------ UI

    private void buildUI() {
        setLayout(new BorderLayout(6, 6));

        Panel form = new Panel(new GridLayout(0, 2, 6, 6));

        channelChoice = new Choice();
        for (int i = 1; i <= 4; i++) channelChoice.add("" + i);
        channelChoice.select("" + (int) Prefs.get("frm.channel", 1));
        form.add(new Label("Channel to measure:"));
        form.add(channelChoice);

        shapeChoice = new Choice();
        shapeChoice.add("Rectangle");
        shapeChoice.add("Oval");
        shapeChoice.select(Prefs.get("frm.shape", "Rectangle"));
        form.add(new Label("Shape:"));
        form.add(shapeChoice);

        widthField = new TextField(Prefs.get("frm.width", "50"), 6);
        form.add(new Label("Width:"));
        form.add(widthField);

        heightField = new TextField(Prefs.get("frm.height", "50"), 6);
        form.add(new Label("Height:"));
        form.add(heightField);

        unitChoice = new Choice();
        unitChoice.add("pixels");
        unitChoice.add("calibrated units (e.g. micron)");
        unitChoice.select(Prefs.get("frm.units", "pixels"));
        form.add(new Label("Size in:"));
        form.add(unitChoice);

        clickToPlace = new Checkbox("Click on image moves the shape", true);

        Panel top = new Panel(new BorderLayout(4, 4));
        top.add(form, BorderLayout.CENTER);
        top.add(clickToPlace, BorderLayout.SOUTH);
        add(top, BorderLayout.NORTH);

        Panel buttons = new Panel(new GridLayout(2, 2, 6, 6));
        showBtn  = new Button("Show / update shape");
        cellBtn  = new Button("Measure cell");
        bgBtn    = new Button("Measure background");
        resetBtn = new Button("Reset numbering");
        for (Button b : new Button[]{showBtn, cellBtn, bgBtn, resetBtn}) {
            b.addActionListener(this);
            buttons.add(b);
        }
        add(buttons, BorderLayout.CENTER);

        statusLabel = new Label("Open an image, set size, click 'Show / update shape'.");
        add(statusLabel, BorderLayout.SOUTH);

        pack();
        Dimension d = getSize();
        setSize(Math.max(d.width, 380), d.height);
        ij.gui.GUI.center(this);
        setVisible(true);
    }

    private void status(String s) {
        if (statusLabel != null) statusLabel.setText(s);
        IJ.showStatus(s);
    }

    private void savePrefs() {
        Prefs.set("frm.channel", getChannel());
        Prefs.set("frm.shape", shapeChoice.getSelectedItem());
        Prefs.set("frm.width", widthField.getText().trim());
        Prefs.set("frm.height", heightField.getText().trim());
        Prefs.set("frm.units", unitChoice.getSelectedItem());
    }

    // ------------------------------------------------------------- buttons

    @Override
    public void actionPerformed(ActionEvent e) {
        Object src = e.getSource();
        try {
            if (src == showBtn) showShape();
            else if (src == cellBtn) measure(false);
            else if (src == bgBtn) measure(true);
            else if (src == resetBtn) {
                cellCount = 0;
                bgCount = 0;
                status("Numbering reset.");
            }
        } catch (Exception ex) {
            IJ.handleException(ex);
        }
    }

    // ----------------------------------------------------------- core logic

    private int getChannel() {
        return channelChoice.getSelectedIndex() + 1;
    }

    private boolean isOval() {
        return "Oval".equals(shapeChoice.getSelectedItem());
    }

    /** Fixed size in pixels for this image, or null (with message) if invalid. */
    private int[] getSizePx(ImagePlus imp, boolean quiet) {
        double w, h;
        try {
            w = Double.parseDouble(widthField.getText().trim().replace(',', '.'));
            h = Double.parseDouble(heightField.getText().trim().replace(',', '.'));
        } catch (NumberFormatException ex) {
            if (!quiet) IJ.error("Fixed ROI Measure", "Width and height must be numbers.");
            return null;
        }
        if (unitChoice.getSelectedIndex() == 1) {
            Calibration cal = imp.getCalibration();
            w = w / cal.pixelWidth;
            h = h / cal.pixelHeight;
        }
        int wp = (int) Math.round(w), hp = (int) Math.round(h);
        if (wp < 1 || hp < 1) {
            if (!quiet) IJ.error("Fixed ROI Measure", "Width and height must be at least 1 pixel.");
            return null;
        }
        if (wp > imp.getWidth() || hp > imp.getHeight()) {
            if (!quiet) IJ.error("Fixed ROI Measure", "The shape (" + wp + " x " + hp
                    + " px) is larger than the image (" + imp.getWidth() + " x " + imp.getHeight() + ").");
            return null;
        }
        return new int[]{wp, hp};
    }

    private ImagePlus currentImage() {
        ImagePlus imp = WindowManager.getCurrentImage();
        if (imp == null)
            IJ.error("Fixed ROI Measure", "Please open the image you want to measure first.");
        return imp;
    }

    private boolean channelOK(ImagePlus imp) {
        int ch = getChannel();
        int nCh = imp.getBitDepth() == 24 ? 3 : imp.getNChannels();
        if (ch > nCh) {
            IJ.error("Fixed ROI Measure", "You chose channel " + ch + ", but \"" + imp.getTitle()
                    + "\" has only " + nCh + " channel(s)"
                    + (imp.getBitDepth() == 24 ? " (RGB: 1 = red, 2 = green, 3 = blue)." : "."));
            return false;
        }
        return true;
    }

    private void placeShape(ImagePlus imp, double cx, double cy, int w, int h) {
        int x0 = (int) Math.round(cx - w / 2.0);
        int y0 = (int) Math.round(cy - h / 2.0);
        x0 = Math.max(0, Math.min(x0, imp.getWidth() - w));
        y0 = Math.max(0, Math.min(y0, imp.getHeight() - h));
        Roi r = isOval() ? new OvalRoi(x0, y0, w, h) : new Roi(x0, y0, w, h);
        imp.setRoi(r);
    }

    private double[] roiCentre(Roi r) {
        Rectangle b = r.getBounds();
        return new double[]{b.x + b.width / 2.0, b.y + b.height / 2.0};
    }

    private void showShape() {
        ImagePlus imp = currentImage();
        if (imp == null) return;
        int[] sz = getSizePx(imp, false);
        if (sz == null || !channelOK(imp)) return;
        savePrefs();

        if (imp.getBitDepth() != 24 && imp.getNChannels() > 1)
            imp.setPosition(getChannel(), imp.getZ(), imp.getT());   // show the measured channel

        Roi old = imp.getRoi();
        double[] c = old != null ? roiCentre(old)
                : new double[]{imp.getWidth() / 2.0, imp.getHeight() / 2.0};
        placeShape(imp, c[0], c[1], sz[0], sz[1]);

        attach(imp);
        IJ.setTool(Toolbar.RECTANGLE);   // lets you drag the shape; clicks are handled below
        if (imp.getWindow() != null) imp.getWindow().toFront();
        status((isOval() ? "Oval " : "Rectangle ") + sz[0] + " x " + sz[1] + " px on \""
                + imp.getTitle() + "\". Click/drag to move, then measure.");
    }

    private void measure(boolean background) {
        ImagePlus imp = currentImage();
        if (imp == null) return;
        int[] sz = getSizePx(imp, false);
        if (sz == null || !channelOK(imp)) return;
        savePrefs();
        attach(imp);

        Roi roi = imp.getRoi();
        if (roi == null || !roi.isArea()) {
            placeShape(imp, imp.getWidth() / 2.0, imp.getHeight() / 2.0, sz[0], sz[1]);
            status("No shape was on the image; it is now in the centre. Move it, then measure.");
            return;
        }
        // always measure exactly the fixed size/shape, centred where the shape is now
        double[] c = roiCentre(roi);
        placeShape(imp, c[0], c[1], sz[0], sz[1]);
        roi = imp.getRoi();

        String label = background ? "bg_" + (++bgCount) : "cell_" + (++cellCount);
        int ch = getChannel();

        Analyzer.setMeasurements(Measurements.AREA | Measurements.MEAN | Measurements.MIN_MAX
                | Measurements.INTEGRATED_DENSITY | Measurements.LABELS);

        if (imp.getBitDepth() == 24) {
            double[] oldW = ColorProcessor.getWeightingFactors();
            double[] w = new double[3];
            w[ch - 1] = 1.0;
            ColorProcessor.setWeightingFactors(w[0], w[1], w[2]);
            try {
                IJ.run(imp, "Measure", "");
            } finally {
                ColorProcessor.setWeightingFactors(oldW[0], oldW[1], oldW[2]);
            }
        } else if (imp.getNChannels() > 1) {
            int c0 = imp.getC(), z0 = imp.getZ(), t0 = imp.getT();
            imp.setPosition(ch, z0, t0);
            IJ.run(imp, "Measure", "");
            imp.setPosition(c0, z0, t0);
        } else {
            IJ.run(imp, "Measure", "");
        }

        ResultsTable rt = ResultsTable.getResultsTable();
        if (rt != null && rt.size() > 0) {
            int row = rt.size() - 1;
            rt.setValue("Name", row, label);
            rt.setValue("Channel", row, ch);
            rt.setValue("Shape", row, (isOval() ? "Oval " : "Rectangle ") + sz[0] + "x" + sz[1]);
            rt.show("Results");
        }

        // outline of the measured region stays visible (not clickable)
        Roi copy = (Roi) roi.clone();
        copy.setName(label);
        copy.setStrokeColor(background ? Color.cyan : Color.yellow);
        Overlay ov = imp.getOverlay();
        if (ov == null) ov = new Overlay();
        ov.add(copy);
        ov.drawLabels(true);
        ov.drawNames(true);
        ov.selectable(false);
        imp.setOverlay(ov);

        // also keep it in the ROI Manager
        RoiManager rm = RoiManager.getInstance();
        if (rm == null) rm = new RoiManager();
        Roi copy2 = (Roi) roi.clone();
        copy2.setName(label);
        rm.addRoi(copy2);

        imp.setRoi(roi);   // keep the active shape for the next cell
        status("Measured " + label + " on channel " + ch + ".");
    }

    // ----------------------------------------------- click / drag on image

    private void attach(ImagePlus imp) {
        if (imp == null) return;
        ImageCanvas ic = imp.getCanvas();
        if (ic != null && canvases.add(ic)) ic.addMouseListener(this);
    }

    private boolean selectionToolActive() {
        int t = Toolbar.getToolId();
        return t == Toolbar.RECTANGLE || t == Toolbar.OVAL;
    }

    @Override
    public void mousePressed(MouseEvent e) {
        pressedOnShape = false;
        if (!clickToPlace.getState() || !selectionToolActive()) return;
        if (!(e.getSource() instanceof ImageCanvas)) return;
        ImageCanvas ic = (ImageCanvas) e.getSource();
        ImagePlus imp = ic.getImage();
        if (imp == null) return;
        Roi r = imp.getRoi();
        if (r != null) {
            int ox = ic.offScreenX(e.getX()), oy = ic.offScreenY(e.getY());
            pressedOnShape = r.contains(ox, oy) || r.isHandle(e.getX(), e.getY()) >= 0;
        }
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        if (!clickToPlace.getState() || !selectionToolActive()) return;
        if (!(e.getSource() instanceof ImageCanvas)) return;
        final ImageCanvas ic = (ImageCanvas) e.getSource();
        final ImagePlus imp = ic.getImage();
        if (imp == null) return;
        final double ox = ic.offScreenXD(e.getX());
        final double oy = ic.offScreenYD(e.getY());
        final boolean onShape = pressedOnShape;
        EventQueue.invokeLater(new Runnable() {
            public void run() {
                int[] sz = getSizePx(imp, true);
                if (sz == null) return;
                Roi r = imp.getRoi();
                if (onShape && r != null) {
                    double[] c = roiCentre(r);          // dragged: keep position, restore size
                    placeShape(imp, c[0], c[1], sz[0], sz[1]);
                } else {
                    placeShape(imp, ox, oy, sz[0], sz[1]);   // clicked elsewhere: jump there
                }
            }
        });
    }

    @Override public void mouseClicked(MouseEvent e) {}
    @Override public void mouseEntered(MouseEvent e) {}
    @Override public void mouseExited(MouseEvent e) {}

    // ------------------------------------------------------ image listener

    @Override public void imageOpened(ImagePlus imp) { attach(imp); }
    @Override public void imageUpdated(ImagePlus imp) {}
    @Override public void imageClosed(ImagePlus imp) {
        ImageCanvas ic = imp.getCanvas();
        if (ic != null) {
            ic.removeMouseListener(this);
            canvases.remove(ic);
        }
    }

    @Override
    public void close() {
        savePrefs();
        ImagePlus.removeImageListener(this);
        for (ImageCanvas ic : canvases) ic.removeMouseListener(this);
        canvases.clear();
        if (instance == this) instance = null;
        super.close();
    }
}
