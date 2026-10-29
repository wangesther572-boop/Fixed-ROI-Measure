# Fixed ROI Measure

A Fiji/ImageJ plugin for measuring fluorescence intensity (e.g. GFP) with **fixed-size rectangle or oval ROIs** on a **selected channel**.

Every region you measure has exactly the same size and shape, so intensity values are directly comparable between cells and images. You set the channel, shape and size once, then click on each cell and measure it with one button.

<!-- Add a screenshot here, e.g.:
![Fixed ROI Measure control window](screenshot.png)
-->

## Features

- Choose the **channel to measure** (1–4). The chosen channel is measured even if another channel is displayed.
- **Rectangle or oval** ROI of fixed width and height, in **pixels or calibrated units** (e.g. µm).
- **Click on the image** to move the shape there, or drag it. If the shape is resized by accident, it snaps back to the set size.
- Separate **Measure cell** and **Measure background** buttons, labelled `cell_1, cell_2, …` and `bg_1, bg_2, …`.
- Measured regions stay outlined on the image (yellow = cell, cyan = background) and are saved in the **ROI Manager**.
- Works with multichannel (hyper)stacks, single-channel images and RGB images (channel 1 = red, 2 = green, 3 = blue).
- Settings are remembered between sessions.

## Installation

1. Download [`Fixed_ROI_Measure.jar`](Fixed_ROI_Measure.jar).
2. Quit Fiji and copy the file into the `Fiji.app/plugins/` folder.
   *(On macOS: right-click Fiji.app › Show Package Contents.)*
3. Start Fiji. The plugin appears as **Plugins › Fixed ROI Measure**.

Requires Fiji or ImageJ 1.x with Java 8 or newer.

## How to use

1. Open your image and start **Plugins › Fixed ROI Measure**.
2. In the control window, set:
   - **Channel to measure** (1–4)
   - **Shape** (Rectangle or Oval)
   - **Width** and **Height**
   - **Size in** pixels or calibrated units
3. Click **Show / update shape**. The shape appears in the centre of the image.
4. Click on a cell to move the shape there (or drag it; arrow keys nudge by 1 pixel).
5. Click **Measure cell**. For an empty area next to the cells, click **Measure background**.
6. Repeat for all cells. When you move to a new image, click **Reset numbering** so labels start again at `cell_1` / `bg_1`.

To change the size, shape or channel, edit the fields and click **Show / update shape** again.

## Output

Results are added to the standard ImageJ **Results** table:

| Column | Meaning |
|---|---|
| Label | Image name and measured channel |
| Area | Area of the ROI |
| Mean | Mean intensity |
| Min / Max | Minimum and maximum pixel value |
| IntDen / RawIntDen | Integrated density |
| Name | `cell_n` or `bg_n` |
| Channel | Channel that was measured |
| Shape | Shape and size in pixels, e.g. `Rectangle 50x50` |

### Background correction

Because all ROIs have the same size, background subtraction is straightforward:

```
corrected mean      = Mean(cell) − Mean(background)
corrected intensity = IntDen(cell) − Mean(background) × Area(cell)
```

Tip: check the **Max** column for saturated pixels (255 for 8-bit, 4095 or 65535 for 12/16-bit images).

## Source code

The source is in [`Fixed_ROI_Measure.java`](Fixed_ROI_Measure.java) (also included inside the .jar). To build it yourself:

```bash
javac --release 8 -cp ij.jar Fixed_ROI_Measure.java
echo 'Plugins, "Fixed ROI Measure", Fixed_ROI_Measure' > plugins.config
jar cf Fixed_ROI_Measure.jar plugins.config *.class
```

## License

See [LICENSE](LICENSE).
