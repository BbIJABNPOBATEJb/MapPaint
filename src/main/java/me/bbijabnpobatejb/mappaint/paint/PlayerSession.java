package me.bbijabnpobatejb.mappaint.paint;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import me.bbijabnpobatejb.mappaint.exchange.ImageOptions;
import me.bbijabnpobatejb.mappaint.model.Painting;
import me.bbijabnpobatejb.mappaint.palette.Palette;
import org.jetbrains.annotations.Nullable;

import java.awt.image.BufferedImage;

@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public final class PlayerSession {

    byte color = Palette.BLACK;
    byte previousColor = Palette.WHITE;
    int size = 1;
    Tool tool = Tool.BRUSH;
    boolean squareBrush;
    float hue;
    int pickedCell = -1;
    float[] layout = {Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN};
    int palettePage;

    private @Nullable Painting lastPainting;
    private @Nullable PaintService.Stroke stroke;
    int lastClickTick = -100;
    int lastPunchTick = -100;
    int lastInventoryClickTick = -100;
    private @Nullable ImageOptions imageOptions;
    private @Nullable BufferedImage imageCache;
    private @Nullable String imageCacheUrl;
    UndoStack.@Nullable Step imageStep;
    private @Nullable Painting imagePainting;
    String @Nullable [] importParts;
    boolean imageLoading;
}
