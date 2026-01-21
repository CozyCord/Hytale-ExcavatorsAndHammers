package net.cozystudios.excavatorsandhammers;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.modules.interaction.BlockHarvestUtils;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.event.events.ecs.DamageBlockEvent;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.MergedBlockFaces;
import com.hypixel.hytale.server.core.asset.type.gameplay.BrokenPenalties;
import com.hypixel.hytale.server.core.asset.type.gameplay.GameplayConfig;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemTool;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemToolSpec;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockGathering;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blockhitbox.BlockBoundingBoxes;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockBreakingDropType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockFace;
import com.hypixel.hytale.server.core.modules.item.ItemModule;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.modules.entity.component.BoundingBox;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.math.shape.Box;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.logger.HytaleLogger;

import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import javax.annotation.Nonnull;

public final class AreaMiningSystem {
    private AreaMiningSystem() {
    }

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static final Set<String> HAMMER_IDS = new HashSet<>();
    private static final Set<String> EXCAVATOR_IDS = new HashSet<>();
    private static final Set<String> LARGE_AREA_IDS = new HashSet<>();
    private static final Set<Long> PROCESSING_BLOCKS = ConcurrentHashMap.newKeySet();

    private enum MiningPlane {
        XZ,
        XY,
        ZY
    }

    static {
        HAMMER_IDS.add("Tool_Mining_Hammer_Crude");
        HAMMER_IDS.add("Tool_Mining_Hammer_Copper");
        HAMMER_IDS.add("Tool_Mining_Hammer_Iron");
        HAMMER_IDS.add("Tool_Mining_Hammer_Cobalt");
        HAMMER_IDS.add("Tool_Mining_Hammer_Thorium");
        HAMMER_IDS.add("Tool_Mining_Hammer_Mithril");
        HAMMER_IDS.add("Tool_Mining_Hammer_Onyxium");
        HAMMER_IDS.add("Tool_Mining_Hammer_Adamantite");

        EXCAVATOR_IDS.add("Tool_Excavator_Crude");
        EXCAVATOR_IDS.add("Tool_Excavator_Copper");
        EXCAVATOR_IDS.add("Tool_Excavator_Iron");
        EXCAVATOR_IDS.add("Tool_Excavator_Cobalt");
        EXCAVATOR_IDS.add("Tool_Excavator_Thorium");
        EXCAVATOR_IDS.add("Tool_Excavator_Mithril");
        EXCAVATOR_IDS.add("Tool_Excavator_Onyxium");
        EXCAVATOR_IDS.add("Tool_Excavator_Adamantite");

        LARGE_AREA_IDS.add("Tool_Mining_Hammer_Mithril");
        LARGE_AREA_IDS.add("Tool_Mining_Hammer_Adamantite");
        LARGE_AREA_IDS.add("Tool_Excavator_Mithril");
        LARGE_AREA_IDS.add("Tool_Excavator_Adamantite");
    }

    private static long packPosition(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    private static int getMiningRadius(String itemId) {
        if (ModConfig.getInstance().isLargeAreaMiningEnabled() && LARGE_AREA_IDS.contains(itemId)) {
            return 2;
        }
        return 1;
    }

    @FunctionalInterface
    private interface BlockAction {
        void process(int x, int y, int z);
    }

    private static void forEachSurroundingBlock(int centerX, int centerY, int centerZ, MiningPlane plane,
            String itemId, BlockAction action) {
        int radius = getMiningRadius(itemId);
        for (int d1 = -radius; d1 <= radius; d1++) {
            for (int d2 = -radius; d2 <= radius; d2++) {
                if (d1 == 0 && d2 == 0) {
                    continue;
                }

                int x, y, z;
                switch (plane) {
                    case XZ:
                        x = centerX + d1;
                        y = centerY;
                        z = centerZ + d2;
                        break;
                    case XY:
                        x = centerX + d1;
                        y = centerY + d2;
                        z = centerZ;
                        break;
                    case ZY:
                        x = centerX;
                        y = centerY + d2;
                        z = centerZ + d1;
                        break;
                    default:
                        continue;
                }

                long posKey = packPosition(x, y, z);
                if (!PROCESSING_BLOCKS.add(posKey)) {
                    continue;
                }

                try {
                    action.process(x, y, z);
                } finally {
                    PROCESSING_BLOCKS.remove(posKey);
                }
            }
        }
    }

    // Adopted from
    // https://stackoverflow.com/questions/31640145/get-the-side-a-player-is-looking-on-the-block-bukkit
    // user andrewgazelka
    public static @Nonnull BlockFace blockFaceCollide(Vector3d startLocation, Vector3d direction, Box objectBoundry) {
        double directionX = direction.getX();
        double directionY = direction.getY();
        double directionZ = direction.getZ();
        Vector3d min = objectBoundry.min;
        Vector3d max = objectBoundry.max;

        if (directionY > 0) { // Looking +Y
            double b = min.y - startLocation.getY(); // Bottom of voxel Y - player position
            double tempConstant = b / directionY; // b / directionY
            if (tempConstant >= 0) {
                double xAtCollide = tempConstant * directionX + startLocation.getX();
                double zAtCollide = tempConstant * directionZ + startLocation.getZ();
                if (between(xAtCollide, min.x, max.x, 0)
                        && between(zAtCollide, min.z, max.z, 0)) {
                    return BlockFace.DOWN;
                }
            }
        } else { // Looking -Y
            double e = max.y - startLocation.getY();
            double tempConstant = e / directionY;
            if (tempConstant >= 0) {
                double xAtCollide = tempConstant * directionX + startLocation.getX();
                double zAtCollide = tempConstant * directionZ + startLocation.getZ();
                if (between(xAtCollide, min.x, max.x, 0)
                        && between(zAtCollide, min.z, max.z, 0)) {
                    return BlockFace.UP;
                }
            }
        }

        if (directionX > 0) {
            double d = min.x - startLocation.getX();
            double tempConstant = d / directionX;
            if (tempConstant >= 0) {
                double yAtCollide = tempConstant * directionY + startLocation.getY();
                double zAtCollide = tempConstant * directionZ + startLocation.getZ();
                if (between(yAtCollide, min.y, max.y, 0)
                        && between(zAtCollide, min.z, max.z, 0)) {
                    return BlockFace.EAST;
                }
            }
        } else {
            double a = max.x - startLocation.getX();
            double tempConstant = a / directionX;
            if (tempConstant >= 0) {
                double yAtCollide = tempConstant * directionY + startLocation.getY();
                double zAtCollide = tempConstant * directionZ + startLocation.getZ();
                if (between(yAtCollide, min.y, max.y, 0)
                        && between(zAtCollide, min.z, max.z, 0)) {
                    return BlockFace.WEST;
                }
            }
        }

        if (directionZ > 0) {
            double c = min.z - startLocation.getZ();
            double tempConstant = c / directionZ;
            if (tempConstant >= 0) {
                double yAtCollide = tempConstant * directionY + startLocation.getY();
                double xAtCollide = tempConstant * directionX + startLocation.getX();
                if (between(yAtCollide, min.y, max.y, 0)
                        && between(xAtCollide, min.x, max.x, 0)) {
                    return BlockFace.NORTH;
                }
            }
        } else {
            double f = max.z - startLocation.getZ();
            double tempConstant = f / directionZ;
            if (tempConstant >= 0) {
                double yAtCollide = tempConstant * directionY + startLocation.getY();
                double xAtCollide = tempConstant * directionX + startLocation.getX();
                if (between(yAtCollide, min.y, max.y, 0)
                        && between(xAtCollide, min.x, max.x, 0)) {
                    return BlockFace.SOUTH;
                }
            }
        }
        return null;
    }

    public static boolean between(double num, double a, double b, double EOF) {
        if (a <= b)
            return num + EOF >= a && num - EOF <= b;
        return num + EOF >= b && num - EOF <= a;
    }

    private static Box getAxisAllignedBoundBox(World world, Vector3i targetBlock) {
        int x = targetBlock.getX();
        int z = targetBlock.getZ();

        WorldChunk worldChunk = world.getChunk(ChunkUtil.indexChunkFromBlock(x, z));
        Ref<ChunkStore> blockRef = worldChunk.getBlockComponentEntity(x, targetBlock.getY(), z);
        Store<ChunkStore> chunkStore = world.getChunkStore().getStore();

        BlockType blockType = world.getBlockType(targetBlock);
        BlockBoundingBoxes hitbox = BlockBoundingBoxes.getAssetMap().getAsset(blockType.getHitboxTypeIndex());
        Box boundingBox = hitbox.get(0).getBoundingBox(); // 0 = Don't plan to break rotated blocks

        return boundingBox.getBox(targetBlock.toVector3d());
    }

    private static MiningPlane getMiningPlaneFromPlayer(Ref<EntityStore> ref, ComponentAccessor<EntityStore> componentAccessor,
            Player player, Vector3i targetBlock) {
        try {
            TransformComponent transform = player.getTransformComponent();
            if (transform == null) {
                return MiningPlane.XY;
            }

            Vector3d playerPos = transform.getPosition();
            if (playerPos == null) {
                return MiningPlane.XY;
            }

            World world = player.getWorld();
            if (world == null) {
                return MiningPlane.XY;
            }

            HeadRotation headRotationComponent = componentAccessor.getComponent(ref, HeadRotation.getComponentType());
            assert headRotationComponent != null;

            Vector3f HeadRotation = headRotationComponent.getRotation();
            Vector3d direction = headRotationComponent.getDirection();

            // I hope this isn't as slow but if they change things up on us it's more
            // resiliant
            float eyeHeight = 0.0F;
            ModelComponent modelComponent = componentAccessor.getComponent(ref, ModelComponent.getComponentType());
            if (modelComponent != null) {
                eyeHeight = modelComponent.getModel().getEyeHeight(ref, componentAccessor);
            }
            Box objectBoundry = getAxisAllignedBoundBox(world, targetBlock);

            Vector3d eyeHeightV = new Vector3d(playerPos.x,playerPos.y+eyeHeight,playerPos.z);

            BlockFace face = blockFaceCollide(eyeHeightV, direction, objectBoundry);
            if (face == null) {
                return MiningPlane.XY;
            }

            switch (face) {
                case BlockFace.NORTH:
                case BlockFace.SOUTH:
                    return MiningPlane.XY;
                case BlockFace.EAST:
                case BlockFace.WEST:
                    return MiningPlane.ZY;
                case BlockFace.UP:
                case BlockFace.DOWN:
                    return MiningPlane.XZ;
            }
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Error calculating mining plane");
        }

        return MiningPlane.XY;
    }

    public static class DamageAreaSystem extends EntityEventSystem<EntityStore, DamageBlockEvent> {
        public DamageAreaSystem() {
            super(DamageBlockEvent.class);
        }

        @Override
        public Query<EntityStore> getQuery() {
            return PlayerRef.getComponentType();
        }

        @Override
        public void handle(int entityIndex, ArchetypeChunk<EntityStore> chunk,
                Store<EntityStore> entityStore, CommandBuffer<EntityStore> commandBuffer,
                DamageBlockEvent event) {

            Ref ref = chunk.getReferenceTo(entityIndex);
            Player player = entityStore.getComponent(ref, Player.getComponentType());

            if (player == null) {
                return;
            }
            GameMode gameMode = player.getGameMode();
            if(gameMode.name() != "Adventure") {
                return;
            }

            World world = player.getWorld();
            if (world == null) {
                return;
            }
            GameplayConfig gameplayConfig = world.getGameplayConfig();

            ItemStack itemStack = event.getItemInHand();
            if (itemStack == null) {
                return;
            }

            Item heldItem = itemStack.getItem();
            if (heldItem == null) {
                return;
            }

            String itemId = itemStack.getItemId();
            if (itemId == null) {
                return;
            }

            ItemTool itemTool = heldItem.getTool();
            if (itemTool == null) {
                return;
            }

            boolean isHammer = HAMMER_IDS.contains(itemId); // [TODO] - would be nice to process this
            boolean isExcavator = EXCAVATOR_IDS.contains(itemId);

            if (!isHammer && !isExcavator) {
                return;
            }

            Vector3i targetPos = event.getTargetBlock();
            if (targetPos == null) {
                return;
            }

            ItemToolSpec itemToolSpec = BlockHarvestUtils.getSpecPowerDamageBlock(heldItem, event.getBlockType(), itemTool);
            boolean canApplyItemStackPenalties = player.canApplyItemStackPenalties(ref, entityStore);
            if (itemStack.isBroken() && canApplyItemStackPenalties) {
                return;
            }

            int centerX = targetPos.getX();
            int centerY = targetPos.getY();
            int centerZ = targetPos.getZ();

            MiningPlane plane = getMiningPlaneFromPlayer(ref, entityStore, player, targetPos);

            long posKey = packPosition(centerX, centerY, centerZ);
            if (!PROCESSING_BLOCKS.add(posKey)) {
                return;
            }

            try {
                damageSurroundingBlocks(world, centerX, centerY, centerZ, plane, isHammer, commandBuffer, itemTool, itemId);
            } finally {
                PROCESSING_BLOCKS.remove(posKey);
            }
        }

        private void damageSurroundingBlocks(World world, int centerX, int centerY, int centerZ, MiningPlane plane,
                boolean isHammer, CommandBuffer<EntityStore> commandBuffer, ItemTool itemTool, String itemId) {
            forEachSurroundingBlock(centerX, centerY, centerZ, plane, itemId,
                (x, y, z) -> damangeBlockWithDrops(world, x, y, z, centerX, centerY, centerZ, isHammer, commandBuffer, itemTool));
        }

        private void damangeBlockWithDrops(World world, int x, int y, int z, int dropX, int dropY, int dropZ,
                boolean isHammer, CommandBuffer<EntityStore> commandBuffer, ItemTool itemTool) {
            Vector3i pos = new Vector3i(x, y, z);
            BlockType blockType = world.getBlockType(pos);
            if (blockType == null) {
                return;
            }

            String blockId = blockType.getId();

            if (blockId == null) {
                return;
            }

            if (!isBlockMineableByTool(blockType, isHammer) && ModConfig.getInstance().isMinabilityCheckEnabled()) {
                return;
            }
            Store<ChunkStore> chunkStore = world.getChunkStore().getStore();
            long chunkIndex = ChunkUtil.indexChunkFromBlock(x, z);
            Ref<ChunkStore> chunkReference = chunkStore.getExternalData().getChunkReference(chunkIndex);

            BlockHarvestUtils.performBlockDamage(pos, (ItemStack)null, itemTool, 1.0F, 0, chunkReference, commandBuffer, chunkStore);
        }

        private boolean isBlockMineableByTool(BlockType blockType, boolean isHammer) {
            try {
                BlockGathering gathering = blockType.getGathering();
                if (gathering == null) {
                    return false;
                }

                String toolType = null;

                try {
                    java.lang.reflect.Method method = gathering.getClass().getMethod("getToolType");
                    Object result = method.invoke(gathering);
                    if (result != null) {
                        toolType = result.toString().toLowerCase();
                    }
                } catch (NoSuchMethodException ignored) {}

                if (toolType == null) {
                    try {
                        java.lang.reflect.Method method = gathering.getClass().getMethod("getRequiredTool");
                        Object result = method.invoke(gathering);
                        if (result != null) {
                            toolType = result.toString().toLowerCase();
                        }
                    } catch (NoSuchMethodException ignored) {}
                }

                if (toolType == null) {
                    try {
                        java.lang.reflect.Method method = gathering.getClass().getMethod("getTool");
                        Object result = method.invoke(gathering);
                        if (result != null) {
                            toolType = result.toString().toLowerCase();
                        }
                    } catch (NoSuchMethodException ignored) {}
                }

                if (toolType != null) {
                    if (isHammer) {
                        return toolType.contains("pickaxe") || toolType.contains("pick");
                    } else {
                        return toolType.contains("shovel") || toolType.contains("spade");
                    }
                }

                String blockId = blockType.getId();
                if (blockId != null) {
                    String lowerBlockId = blockId.toLowerCase();

                    boolean isShovelBlock = lowerBlockId.contains("dirt") ||
                                            lowerBlockId.contains("grass") ||
                                            lowerBlockId.contains("sand") ||
                                            lowerBlockId.contains("gravel") ||
                                            lowerBlockId.contains("clay") ||
                                            lowerBlockId.contains("mud") ||
                                            lowerBlockId.contains("soil") ||
                                            lowerBlockId.contains("snow") ||
                                            lowerBlockId.contains("farmland");

                    boolean isPickaxeBlock = lowerBlockId.contains("stone") ||
                                            lowerBlockId.contains("rock") ||
                                            lowerBlockId.contains("ore") ||
                                            lowerBlockId.contains("cobble") ||
                                            lowerBlockId.contains("brick") ||
                                            lowerBlockId.contains("concrete") ||
                                            lowerBlockId.contains("terracotta") ||
                                            lowerBlockId.contains("obsidian") ||
                                            lowerBlockId.contains("granite") ||
                                            lowerBlockId.contains("diorite") ||
                                            lowerBlockId.contains("andesite") ||
                                            lowerBlockId.contains("sandstone") ||
                                            lowerBlockId.contains("basalt") ||
                                            lowerBlockId.contains("mineral");

                    if (isHammer) {
                        return isPickaxeBlock;
                    } else {
                        return isShovelBlock;
                    }
                }

            } catch (Exception e) {
                LOGGER.atWarning().withCause(e).log("Error checking tool type for block");
            }

            return true;
        }
    }

    public static class BreakAreaSystem extends EntityEventSystem<EntityStore, BreakBlockEvent> {
        public BreakAreaSystem() {
            super(BreakBlockEvent.class);
        }

        @Override
        public Query<EntityStore> getQuery() {
            return PlayerRef.getComponentType();
        }

        @Override
        public void handle(int entityIndex, ArchetypeChunk<EntityStore> chunk,
                Store<EntityStore> entityStore, CommandBuffer<EntityStore> commandBuffer,
                BreakBlockEvent event) {

            Ref ref = chunk.getReferenceTo(entityIndex);
            Player player = entityStore.getComponent(ref, Player.getComponentType());

            if (player == null) {
                return;
            }
            GameMode gameMode = player.getGameMode();
            if(gameMode.name() != "Creative") {
                return;
            }

            World world = player.getWorld();
            if (world == null) {
                return;
            }
            GameplayConfig gameplayConfig = world.getGameplayConfig();

            ItemStack itemStack = event.getItemInHand();
            if (itemStack == null) {
                return;
            }

            Item heldItem = itemStack.getItem();
            if (heldItem == null) {
                return;
            }

            String itemId = itemStack.getItemId();
            if (itemId == null) {
                return;
            }

            ItemTool itemTool = heldItem.getTool();
            if (itemTool == null) {
                return;
            }

            boolean isHammer = HAMMER_IDS.contains(itemId); // [TODO] - would be nice to process this
            boolean isExcavator = EXCAVATOR_IDS.contains(itemId);

            if (!isHammer && !isExcavator) {
                return;
            }

            Vector3i targetPos = event.getTargetBlock();
            if (targetPos == null) {
                return;
            }

            int centerX = targetPos.getX();
            int centerY = targetPos.getY();
            int centerZ = targetPos.getZ();

            MiningPlane plane = getMiningPlaneFromPlayer(ref, entityStore, player, targetPos);

            long posKey = packPosition(centerX, centerY, centerZ);
            if (!PROCESSING_BLOCKS.add(posKey)) {
                return;
            }

            try {
                breakSurroundingBlocks(world, centerX, centerY, centerZ, plane, isHammer, commandBuffer, itemTool, ref, itemId);
            } finally {
                PROCESSING_BLOCKS.remove(posKey);
            }
        }

        private void breakSurroundingBlocks(World world, int centerX, int centerY, int centerZ, MiningPlane plane,
                boolean isHammer, CommandBuffer<EntityStore> commandBuffer, ItemTool itemTool, Ref<EntityStore> ref, String itemId) {
            forEachSurroundingBlock(centerX, centerY, centerZ, plane, itemId,
                (x, y, z) -> breakBlockWithDrops(world, x, y, z, centerX, centerY, centerZ, isHammer, commandBuffer, itemTool, ref));
        }

        private void breakBlockWithDrops(World world, int x, int y, int z, int dropX, int dropY, int dropZ,
                boolean isHammer, CommandBuffer<EntityStore> commandBuffer, ItemTool itemTool, Ref<EntityStore> ref) {
            Vector3i pos = new Vector3i(x, y, z);
            BlockType blockType = world.getBlockType(pos);
            if (blockType == null) {
                return;
            }

            String blockId = blockType.getId();

            if (blockId == null) {
                return;
            }

            Store<ChunkStore> chunkStore = world.getChunkStore().getStore();
            long chunkIndex = ChunkUtil.indexChunkFromBlock(x, z);
            Ref<ChunkStore> chunkReference = chunkStore.getExternalData().getChunkReference(chunkIndex);

            BlockHarvestUtils.performBlockBreak(ref, (ItemStack)null, pos, chunkReference, commandBuffer, chunkStore);
        }
    }
}
