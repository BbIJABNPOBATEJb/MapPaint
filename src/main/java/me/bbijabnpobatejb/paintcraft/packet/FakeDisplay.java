package me.bbijabnpobatejb.paintcraft.packet;

import com.github.retrooper.packetevents.protocol.entity.EntityPositionData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityType;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.util.Quaternion4f;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityPositionSync;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import io.github.retrooper.packetevents.util.SpigotReflectionUtil;
import lombok.Getter;
import net.kyori.adventure.text.Component;
import org.bukkit.block.data.BlockData;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class FakeDisplay {

    private static final int FLAGS = 0;
    private static final int INTERPOLATION_DELAY = 8;
    private static final int TRANSFORMATION_DURATION = 9;
    private static final int TELEPORT_DURATION = 10;
    private static final int TRANSLATION = 11;
    private static final int SCALE = 12;
    private static final int LEFT_ROTATION = 13;
    private static final int RIGHT_ROTATION = 14;
    private static final int BILLBOARD = 15;
    private static final int BRIGHTNESS = 16;
    private static final int VIEW_RANGE = 17;
    private static final int GLOW_COLOR = 22;
    private static final int BLOCK_STATE = 23;
    private static final int TEXT = 23;
    private static final int TEXT_LINE_WIDTH = 24;
    private static final int TEXT_BACKGROUND = 25;
    private static final int TEXT_FLAGS = 27;
    private static final int ITEM = 23;
    private static final int ITEM_DISPLAY_TYPE = 24;
    private static final int INTERACTION_WIDTH = 8;
    private static final int INTERACTION_HEIGHT = 9;
    private static final int INTERACTION_RESPONSIVE = 10;

    private static final byte FLAG_GLOWING = 0x40;
    private static final byte BILLBOARD_CENTER = 3;
    public static final byte BILLBOARD_FIXED = 0;
    private static final byte ITEM_DISPLAY_GUI = 6;
    public static final int FULL_BRIGHT = 15 << 4 | 15 << 20;
    private static final byte TEXT_FLAG_SEE_THROUGH = 0x02;

    private static final Map<BlockData, Integer> BLOCK_IDS = new HashMap<>();

    public enum Kind {BLOCK, TEXT, ITEM, INTERACTION}

    @Getter
    private final Kind kind;
    @Getter
    private final int entityId = SpigotReflectionUtil.generateEntityId();
    private final UUID uuid = UUID.randomUUID();

    private double x, y, z;
    private int blockId;
    private com.github.retrooper.packetevents.protocol.item.ItemStack item = com.github.retrooper.packetevents.protocol.item.ItemStack.EMPTY;
    private int light = -1;
    private final Vector3f translation = new Vector3f();
    private final Vector3f scale = new Vector3f(1f);
    private final Quaternionf left = new Quaternionf();
    private final Quaternionf right = new Quaternionf();
    private int interpolation = 1;
    private int teleportDuration = 1;
    private float viewRange = 1f;
    private boolean glowing;
    private int glowColor = -1;
    private Component text = Component.empty();
    private int textBackground = 0x40000000;
    private boolean seeThrough;
    private boolean shadow;
    private byte billboard = BILLBOARD_CENTER;
    private float width = 1f, height = 1f;

    private boolean spawned;
    private double sentX, sentY, sentZ;
    private int sentBlockId;
    private com.github.retrooper.packetevents.protocol.item.ItemStack sentItem;
    private int sentTextBackground;
    private int sentLight;
    private final Vector3f sentTranslation = new Vector3f();
    private final Vector3f sentScale = new Vector3f();
    private final Quaternionf sentLeft = new Quaternionf();
    private final Quaternionf sentRight = new Quaternionf();
    private int sentInterpolation, sentTeleportDuration;
    private float sentViewRange;
    private boolean sentGlowing;
    private int sentGlowColor;
    private Component sentText;
    private float sentWidth, sentHeight;

    boolean used;

    FakeDisplay(Kind kind) {
        this.kind = kind;
    }

    public FakeDisplay position(Vector position) {
        return position(position.getX(), position.getY(), position.getZ());
    }

    public FakeDisplay position(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
        return this;
    }

    public FakeDisplay block(BlockData block) {
        this.blockId = BLOCK_IDS.computeIfAbsent(block, data -> SpigotConversionUtil.fromBukkitBlockData(data).getGlobalId());
        return this;
    }

    public FakeDisplay item(ItemStack item) {
        this.item = SpigotConversionUtil.fromBukkitItemStack(item);
        return this;
    }

    public FakeDisplay light(int packed) {
        this.light = packed;
        return this;
    }

    public FakeDisplay transform(Vector3f translation, Quaternionf left, Vector3f scale, Quaternionf right) {
        this.translation.set(translation);
        this.left.set(left);
        this.scale.set(scale);
        this.right.set(right);
        return this;
    }

    public FakeDisplay centred(float size) {
        translation.set(-size / 2);
        scale.set(size);
        left.identity();
        right.identity();
        return this;
    }

    public FakeDisplay interpolation(int ticks) {
        this.interpolation = ticks;
        this.teleportDuration = ticks;
        return this;
    }

    public FakeDisplay viewRange(float viewRange) {
        this.viewRange = viewRange;
        return this;
    }

    public FakeDisplay glow(boolean glowing, int color) {
        this.glowing = glowing;
        this.glowColor = color;
        return this;
    }

    public FakeDisplay text(Component text) {
        this.text = text;
        return this;
    }

    public FakeDisplay textLook(int background, boolean seeThrough, boolean shadow, byte billboard) {
        this.textBackground = background;
        this.seeThrough = seeThrough;
        this.shadow = shadow;
        this.billboard = billboard;
        return this;
    }

    public FakeDisplay textStyle(int background, boolean seeThrough) {
        this.textBackground = background;
        this.seeThrough = seeThrough;
        return this;
    }

    public FakeDisplay size(float width, float height) {
        this.width = width;
        this.height = height;
        return this;
    }

    public Vector3f translation() {
        return translation;
    }

    public Vector3f scale() {
        return scale;
    }

    public Quaternionf leftRotation() {
        return left;
    }

    public Quaternionf rightRotation() {
        return right;
    }

    public Vector position() {
        return new Vector(x, y, z);
    }

    boolean isSpawned() {
        return spawned;
    }

    void appendSpawn(List<PacketWrapper<?>> out) {
        EntityType type = switch (kind) {
            case BLOCK -> EntityTypes.BLOCK_DISPLAY;
            case TEXT -> EntityTypes.TEXT_DISPLAY;
            case ITEM -> EntityTypes.ITEM_DISPLAY;
            case INTERACTION -> EntityTypes.INTERACTION;
        };
        out.add(new WrapperPlayServerSpawnEntity(entityId, Optional.of(uuid), type, new Vector3d(x, y, z), 0f, 0f, 0f, 0,
                Optional.empty()));

        List<EntityData<?>> data = new ArrayList<>();
        if (kind == Kind.INTERACTION) {
            data.add(new EntityData<>(INTERACTION_WIDTH, EntityDataTypes.FLOAT, width));
            data.add(new EntityData<>(INTERACTION_HEIGHT, EntityDataTypes.FLOAT, height));
            data.add(new EntityData<>(INTERACTION_RESPONSIVE, EntityDataTypes.BOOLEAN, true));
        } else {
            data.add(new EntityData<>(FLAGS, EntityDataTypes.BYTE, glowing ? FLAG_GLOWING : (byte) 0));
            data.add(new EntityData<>(TRANSFORMATION_DURATION, EntityDataTypes.INT, interpolation));
            data.add(new EntityData<>(TELEPORT_DURATION, EntityDataTypes.INT, teleportDuration));
            data.add(new EntityData<>(TRANSLATION, EntityDataTypes.VECTOR3F, pe(translation)));
            data.add(new EntityData<>(SCALE, EntityDataTypes.VECTOR3F, pe(scale)));
            data.add(new EntityData<>(LEFT_ROTATION, EntityDataTypes.QUATERNION, pe(left)));
            data.add(new EntityData<>(RIGHT_ROTATION, EntityDataTypes.QUATERNION, pe(right)));
            data.add(new EntityData<>(BRIGHTNESS, EntityDataTypes.INT, light));
            data.add(new EntityData<>(VIEW_RANGE, EntityDataTypes.FLOAT, viewRange));
            data.add(new EntityData<>(GLOW_COLOR, EntityDataTypes.INT, glowColor));
            if (kind == Kind.BLOCK) {
                data.add(new EntityData<>(BLOCK_STATE, EntityDataTypes.BLOCK_STATE, blockId));
            } else if (kind == Kind.ITEM) {
                data.add(new EntityData<>(ITEM, EntityDataTypes.ITEMSTACK, item));
                data.add(new EntityData<>(ITEM_DISPLAY_TYPE, EntityDataTypes.BYTE, ITEM_DISPLAY_GUI));
            } else {
                data.add(new EntityData<>(BILLBOARD, EntityDataTypes.BYTE, billboard));
                data.add(new EntityData<>(TEXT, EntityDataTypes.ADV_COMPONENT, text));
                data.add(new EntityData<>(TEXT_LINE_WIDTH, EntityDataTypes.INT, 400));
                data.add(new EntityData<>(TEXT_BACKGROUND, EntityDataTypes.INT, textBackground));
                data.add(new EntityData<>(TEXT_FLAGS, EntityDataTypes.BYTE, (byte) ((seeThrough ? TEXT_FLAG_SEE_THROUGH : 0) | (shadow ? 0x01 : 0))));
            }
        }
        out.add(new WrapperPlayServerEntityMetadata(entityId, data));
    }

    void appendChanges(List<PacketWrapper<?>> out) {
        if (x != sentX || y != sentY || z != sentZ) {
            out.add(new WrapperPlayServerEntityPositionSync(entityId,
                    new EntityPositionData(new Vector3d(x, y, z), Vector3d.zero(), 0f, 0f), false));
        }

        List<EntityData<?>> data = new ArrayList<>();
        if (kind == Kind.INTERACTION) {
            if (width != sentWidth) data.add(new EntityData<>(INTERACTION_WIDTH, EntityDataTypes.FLOAT, width));
            if (height != sentHeight) data.add(new EntityData<>(INTERACTION_HEIGHT, EntityDataTypes.FLOAT, height));
        } else {
            boolean moved = false;
            if (!translation.equals(sentTranslation)) {
                data.add(new EntityData<>(TRANSLATION, EntityDataTypes.VECTOR3F, pe(translation)));
                moved = true;
            }
            if (!scale.equals(sentScale)) {
                data.add(new EntityData<>(SCALE, EntityDataTypes.VECTOR3F, pe(scale)));
                moved = true;
            }
            if (!left.equals(sentLeft)) {
                data.add(new EntityData<>(LEFT_ROTATION, EntityDataTypes.QUATERNION, pe(left)));
                moved = true;
            }
            if (!right.equals(sentRight)) {
                data.add(new EntityData<>(RIGHT_ROTATION, EntityDataTypes.QUATERNION, pe(right)));
                moved = true;
            }
            if (moved) data.add(new EntityData<>(INTERPOLATION_DELAY, EntityDataTypes.INT, 0));

            if (interpolation != sentInterpolation) {
                data.add(new EntityData<>(TRANSFORMATION_DURATION, EntityDataTypes.INT, interpolation));
            }
            if (teleportDuration != sentTeleportDuration) {
                data.add(new EntityData<>(TELEPORT_DURATION, EntityDataTypes.INT, teleportDuration));
            }
            if (light != sentLight) data.add(new EntityData<>(BRIGHTNESS, EntityDataTypes.INT, light));
            if (viewRange != sentViewRange) data.add(new EntityData<>(VIEW_RANGE, EntityDataTypes.FLOAT, viewRange));
            if (glowing != sentGlowing) data.add(new EntityData<>(FLAGS, EntityDataTypes.BYTE, glowing ? FLAG_GLOWING : (byte) 0));
            if (glowColor != sentGlowColor) data.add(new EntityData<>(GLOW_COLOR, EntityDataTypes.INT, glowColor));
            if (kind == Kind.BLOCK) {
                if (blockId != sentBlockId) data.add(new EntityData<>(BLOCK_STATE, EntityDataTypes.BLOCK_STATE, blockId));
            } else if (kind == Kind.ITEM) {
                if (!item.equals(sentItem)) data.add(new EntityData<>(ITEM, EntityDataTypes.ITEMSTACK, item));
            } else {
                if (!text.equals(sentText)) data.add(new EntityData<>(TEXT, EntityDataTypes.ADV_COMPONENT, text));
                if (textBackground != sentTextBackground) {
                    data.add(new EntityData<>(TEXT_BACKGROUND, EntityDataTypes.INT, textBackground));
                }
            }
        }
        if (!data.isEmpty()) out.add(new WrapperPlayServerEntityMetadata(entityId, data));
    }

    PacketWrapper<?> glowPacket(boolean glow, int color) {
        List<EntityData<?>> data = new ArrayList<>(2);
        data.add(new EntityData<>(FLAGS, EntityDataTypes.BYTE, glow ? FLAG_GLOWING : (byte) 0));
        data.add(new EntityData<>(GLOW_COLOR, EntityDataTypes.INT, color));
        return new WrapperPlayServerEntityMetadata(entityId, data);
    }

    void markSent() {
        spawned = true;
        sentX = x;
        sentY = y;
        sentZ = z;
        sentBlockId = blockId;
        sentItem = item;
        sentTextBackground = textBackground;
        sentLight = light;
        sentTranslation.set(translation);
        sentScale.set(scale);
        sentLeft.set(left);
        sentRight.set(right);
        sentInterpolation = interpolation;
        sentTeleportDuration = teleportDuration;
        sentViewRange = viewRange;
        sentGlowing = glowing;
        sentGlowColor = glowColor;
        sentText = text;
        sentWidth = width;
        sentHeight = height;
    }

    private static com.github.retrooper.packetevents.util.Vector3f pe(Vector3f v) {
        return new com.github.retrooper.packetevents.util.Vector3f(v.x, v.y, v.z);
    }

    private static Quaternion4f pe(Quaternionf q) {
        return new Quaternion4f(q.x, q.y, q.z, q.w);
    }
}
