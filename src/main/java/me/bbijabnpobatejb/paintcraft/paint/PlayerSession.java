package me.bbijabnpobatejb.paintcraft.paint;

import lombok.Getter;
import lombok.Setter;
import me.bbijabnpobatejb.paintcraft.exchange.ImageOptions;
import me.bbijabnpobatejb.paintcraft.model.Painting;
import me.bbijabnpobatejb.paintcraft.palette.Palette;
import org.jetbrains.annotations.Nullable;

import java.awt.image.BufferedImage;

@Getter
@Setter
public final class PlayerSession {

    private byte color = Palette.BLACK;
    private byte previousColor = Palette.WHITE;
    private int size = 1;
    private Tool tool = Tool.BRUSH;
    private boolean squareBrush;
    private float hue;
    private int pickedCell = -1;
    private float[] layout = {Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN};
    private int palettePage;

    private @Nullable Painting lastPainting;
    private @Nullable PaintService.Stroke stroke;
    private int lastClickTick = -100;
    private int lastPunchTick = -100;
    private int lastInventoryClickTick = -100;
    private @Nullable ImageOptions imageOptions;
    private @Nullable BufferedImage imageCache;
    private @Nullable String imageCacheUrl;
    private UndoStack.@Nullable Step imageStep;
    private @Nullable Painting imagePainting;
    private String @Nullable [] importParts;
}
