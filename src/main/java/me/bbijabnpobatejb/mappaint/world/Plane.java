package me.bbijabnpobatejb.mappaint.world;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import me.bbijabnpobatejb.mappaint.packet.FakeDisplay;
import net.kyori.adventure.text.Component;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public final class Plane {

    public static final double SURFACE = 0.01;

    private static final float QUAD_WIDTH = 0.125f;
    private static final float QUAD_HEIGHT = 0.25f;
    private static final float QUAD_LEFT = 0.4f;
    private static final double PIXEL = 0.025;
    private static final double SPRITE_PIXELS = 8;
    private static final double SPRITE_SHIFT_A = -1.0, SPRITE_SHIFT_B = 1.75;

    public final World world;
    public final double cornerX, cornerY, cornerZ;
    public final int ux, uy, uz, vx, vy, vz, nx, ny, nz;
    Quaternionf rotation;
    Quaternionf iconRotation;

    private Plane(World world, double cornerX, double cornerY, double cornerZ, int[] axes, BlockFace face) {
        this.world = world;
        this.cornerX = cornerX;
        this.cornerY = cornerY;
        this.cornerZ = cornerZ;
        ux = axes[0];
        uy = axes[1];
        uz = axes[2];
        vx = axes[3];
        vy = axes[4];
        vz = axes[5];
        nx = face.getModX();
        ny = face.getModY();
        nz = face.getModZ();
        rotation = new Quaternionf().setFromNormalized(new Matrix3f(
                ux, uy, uz,
                -vx, -vy, -vz,
                nx, ny, nz));
        iconRotation = new Quaternionf(rotation).rotateY((float) Math.PI);
    }

    public static Plane of(World world, BlockFace face, int rot, int ox, int oy, int oz) {
        int[] a = Placement.axes(face, rot);
        double back = 0.5 - SURFACE;
        return new Plane(world,
                ox + 0.5 - face.getModX() * back - a[0] * 0.5 - a[3] * 0.5,
                oy + 0.5 - face.getModY() * back - a[1] * 0.5 - a[4] * 0.5,
                oz + 0.5 - face.getModZ() * back - a[2] * 0.5 - a[5] * 0.5,
                a, face);
    }

    public Vector point(double a, double b, double out) {
        return new Vector(
                cornerX + ux * a + vx * b + nx * out,
                cornerY + uy * a + vy * b + ny * out,
                cornerZ + uz * a + vz * b + nz * out);
    }

    public Vector3f offset(double a, double b, double out) {
        return new Vector3f(
                (float) (ux * a + vx * b + nx * out),
                (float) (uy * a + vy * b + ny * out),
                (float) (uz * a + vz * b + nz * out));
    }

    public Quaternionf rotation() {
        return rotation;
    }

    public FakeDisplay rect(FakeDisplay display, double a0, double b0, double a1, double b1, double out, int argb) {
        float width = (float) (a1 - a0), height = (float) (b1 - b0);
        display.position(point(a0 + QUAD_LEFT * width, b1, out));
        display.transform(new Vector3f(), rotation, new Vector3f(width / QUAD_WIDTH, height / QUAD_HEIGHT, 1f), new Quaternionf());
        return display.text(Component.space())
                .textLook(argb, false, false, FakeDisplay.BILLBOARD_FIXED)
                .light(FakeDisplay.FULL_BRIGHT);
    }

    public FakeDisplay label(FakeDisplay display, double a, double b, double height, double out, Component text) {
        float scale = (float) (height / QUAD_HEIGHT);
        display.position(point(a - 0.0125 * scale, b, out));
        display.transform(new Vector3f(), rotation, new Vector3f(scale, scale, 1f), new Quaternionf());
        return display.text(text)
                .textLook(0, false, false, FakeDisplay.BILLBOARD_FIXED)
                .light(FakeDisplay.FULL_BRIGHT);
    }

    public FakeDisplay icon(FakeDisplay display, double a, double b, double size, double out, ItemStack item) {
        display.position(point(a, b, out));
        display.transform(new Vector3f(), iconRotation, new Vector3f((float) size, (float) size, 0.01f), new Quaternionf());
        return display.item(item).light(FakeDisplay.FULL_BRIGHT);
    }

    public FakeDisplay sprite(FakeDisplay display, double a, double b, double size, double out, Component sprite) {
        float scale = (float) (size / (SPRITE_PIXELS * PIXEL));
        display.position(point(a + SPRITE_SHIFT_A * PIXEL * scale, b + size / 2 + SPRITE_SHIFT_B * PIXEL * scale, out));
        display.transform(new Vector3f(), rotation, new Vector3f(scale, scale, 1f), new Quaternionf());
        return display.text(sprite)
                .textLook(0, false, false, FakeDisplay.BILLBOARD_FIXED)
                .light(FakeDisplay.FULL_BRIGHT);
    }

    public FakeDisplay rectRotated(FakeDisplay display, double a, double b, double width, double height, double angle,
                                   double out, int argb) {
        Quaternionf turned = new Quaternionf(rotation).rotateZ((float) -angle);
        float sx = (float) (width / QUAD_WIDTH), sy = (float) (height / QUAD_HEIGHT);
        Vector3f toCenter = new Vector3f((float) ((0.5 - QUAD_LEFT) * width), (float) (height / 2), 0f).rotate(turned).negate();
        display.position(point(a, b, out));
        display.transform(toCenter, turned, new Vector3f(sx, sy, 1f), new Quaternionf());
        return display.text(Component.space())
                .textLook(argb, false, false, FakeDisplay.BILLBOARD_FIXED)
                .light(FakeDisplay.FULL_BRIGHT);
    }

    public FakeDisplay box(FakeDisplay display, double a0, double b0, double a1, double b1, double out0, double out1, BlockData block) {
        Vector p = point(a0, b0, out0), q = point(a1, b1, out1);
        double minX = Math.min(p.getX(), q.getX()), minY = Math.min(p.getY(), q.getY()), minZ = Math.min(p.getZ(), q.getZ());
        display.position(minX, minY, minZ);
        display.transform(new Vector3f(), new Quaternionf(),
                new Vector3f((float) Math.abs(p.getX() - q.getX()), (float) Math.abs(p.getY() - q.getY()), (float) Math.abs(p.getZ() - q.getZ())),
                new Quaternionf());
        return display.block(block);
    }
}
