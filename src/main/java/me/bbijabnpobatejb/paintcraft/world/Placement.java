package me.bbijabnpobatejb.paintcraft.world;

import me.bbijabnpobatejb.paintcraft.model.Painting;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.ItemFrame;
import org.bukkit.util.Vector;

import java.util.UUID;

public final class Placement {

    public final UUID id;
    public final Painting painting;
    public final World world;
    public final BlockFace face;
    public final int rot;
    public final UUID placer;
    public final int ox, oy, oz;
    public final int ux, uy, uz, vx, vy, vz;
    public final ItemFrame[] frames;
    public final Plane plane;

    private final int nx, ny, nz;
    private final double cornerX, cornerY, cornerZ;

    public Placement(UUID id, Painting painting, World world, BlockFace face, int rot, UUID placer, int ox, int oy, int oz) {
        this.id = id;
        this.painting = painting;
        this.world = world;
        this.face = face;
        this.rot = rot;
        this.placer = placer;
        this.ox = ox;
        this.oy = oy;
        this.oz = oz;
        int[] a = axes(face, rot);
        ux = a[0];
        uy = a[1];
        uz = a[2];
        vx = a[3];
        vy = a[4];
        vz = a[5];
        nx = face.getModX();
        ny = face.getModY();
        nz = face.getModZ();
        frames = new ItemFrame[painting.tileCount()];

        plane = Plane.of(world, face, rot, ox, oy, oz);
        cornerX = plane.cornerX;
        cornerY = plane.cornerY;
        cornerZ = plane.cornerZ;
    }

    public static int[] axes(BlockFace face, int rot) {
        return switch (face) {
            case SOUTH -> new int[]{1, 0, 0, 0, -1, 0};
            case NORTH -> new int[]{-1, 0, 0, 0, -1, 0};
            case EAST -> new int[]{0, 0, -1, 0, -1, 0};
            case WEST -> new int[]{0, 0, 1, 0, -1, 0};
            case UP, DOWN -> {
                int hx = rot == 1 ? 1 : rot == 3 ? -1 : 0;
                int hz = rot == 0 ? -1 : rot == 2 ? 1 : 0;
                int rx = -hz, rz = hx;
                yield face == BlockFace.UP
                        ? new int[]{rx, 0, rz, -hx, 0, -hz}
                        : new int[]{rx, 0, rz, hx, 0, hz};
            }
            default -> throw new IllegalArgumentException("Face " + face);
        };
    }

    public int cellX(int tx, int ty) {
        return ox + tx * ux + ty * vx;
    }

    public int cellY(int tx, int ty) {
        return oy + tx * uy + ty * vy;
    }

    public int cellZ(int tx, int ty) {
        return oz + tx * uz + ty * vz;
    }

    public Location center() {
        double w = painting.blocksW / 2.0, h = painting.blocksH / 2.0;
        return new Location(world,
                cornerX + ux * w + vx * h + nx * 0.2,
                cornerY + uy * w + vy * h + ny * 0.2,
                cornerZ + uz * w + vz * h + nz * 0.2);
    }

    public boolean isComplete() {
        for (ItemFrame frame : frames) {
            if (frame == null || !frame.isValid()) {
                return false;
            }
        }
        return true;
    }

    public boolean isEmpty() {
        for (ItemFrame frame : frames) {
            if (frame != null) {
                return false;
            }
        }
        return true;
    }

    public Hit trace(Vector origin, Vector direction, double max) {
        double denominator = direction.getX() * nx + direction.getY() * ny + direction.getZ() * nz;
        if (denominator > -1.0e-6) {
            return null;
        }
        double t = ((cornerX - origin.getX()) * nx + (cornerY - origin.getY()) * ny + (cornerZ - origin.getZ()) * nz) / denominator;
        if (t < 0 || t > max) {
            return null;
        }
        double px = origin.getX() + direction.getX() * t - cornerX;
        double py = origin.getY() + direction.getY() * t - cornerY;
        double pz = origin.getZ() + direction.getZ() * t - cornerZ;
        double a = px * ux + py * uy + pz * uz;
        double b = px * vx + py * vy + pz * vz;
        return new Hit(this, a, b, t);
    }

    public record Hit(Placement placement, double a, double b, double distance) {

        public boolean inside() {
            return a >= 0 && b >= 0 && a < placement.painting.blocksW && b < placement.painting.blocksH;
        }

        public double outside() {
            double da = Math.max(0, Math.max(-a, a - placement.painting.blocksW));
            double db = Math.max(0, Math.max(-b, b - placement.painting.blocksH));
            return Math.max(da, db);
        }

        public int x() {
            return clampPixel(a * placement.painting.res, placement.painting.width());
        }

        public int y() {
            return clampPixel(b * placement.painting.res, placement.painting.height());
        }

        private static int clampPixel(double value, int size) {
            double limit = size * 2.0 + 64;
            return (int) Math.floor(Math.max(-limit, Math.min(limit + size, value)));
        }
    }
}
