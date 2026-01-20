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
import com.hypixel.hytale.math.shape.Box;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.logger.HytaleLogger;

import java.util.Set;
import java.util.Vector;
import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;

public class AreaMiningSystem extends EntityEventSystem<EntityStore, DamageBlockEvent> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static final Set<String> HAMMER_IDS = new HashSet<>();
    private static final Set<String> EXCAVATOR_IDS = new HashSet<>();
    private static final Set<Long> PROCESSING_BLOCKS = ConcurrentHashMap.newKeySet();

    private enum MiningPlane {
        XZ,
        XY,
        ZY
    }

    static {
        HAMMER_IDS.add("CozyStudios_ExcavatorsAndHammers_Copper_Hammer");
        HAMMER_IDS.add("CozyStudios_ExcavatorsAndHammers_Iron_Hammer");
        HAMMER_IDS.add("CozyStudios_ExcavatorsAndHammers_Cobalt_Hammer");
        HAMMER_IDS.add("CozyStudios_ExcavatorsAndHammers_Thorium_Hammer");
        HAMMER_IDS.add("CozyStudios_ExcavatorsAndHammers_Mithril_Hammer");
        HAMMER_IDS.add("CozyStudios_ExcavatorsAndHammers_Onyxium_Hammer");
        HAMMER_IDS.add("CozyStudios_ExcavatorsAndHammers_Adamantite_Hammer");

        EXCAVATOR_IDS.add("CozyStudios_ExcavatorsAndHammers_Copper_Excavator");
        EXCAVATOR_IDS.add("CozyStudios_ExcavatorsAndHammers_Iron_Excavator");
        EXCAVATOR_IDS.add("CozyStudios_ExcavatorsAndHammers_Cobalt_Excavator");
        EXCAVATOR_IDS.add("CozyStudios_ExcavatorsAndHammers_Thorium_Excavator");
        EXCAVATOR_IDS.add("CozyStudios_ExcavatorsAndHammers_Mithril_Excavator");
        EXCAVATOR_IDS.add("CozyStudios_ExcavatorsAndHammers_Onyxium_Excavator");
        EXCAVATOR_IDS.add("CozyStudios_ExcavatorsAndHammers_Adamantite_Excavator");
    }

    public AreaMiningSystem() {
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
            breakSurroundingBlocks(world, centerX, centerY, centerZ, plane, isHammer, commandBuffer, itemTool);
        } finally {
            PROCESSING_BLOCKS.remove(posKey);
        }
    }

    public static @Nonnull BlockFace blockFaceCollide(Vector3d startLocation, Vector3d direction, Box objectBoundry){

        double constant = Double.MAX_VALUE;

        BlockFace blockFace = BlockFace.DOWN;

        double directionX = direction.getX();
        double directionY = direction.getY();
        double directionZ = direction.getZ();
        Vector3d min = objectBoundry.min;
        Vector3d max = objectBoundry.max;


        if(directionY > 0){
            double b = min.y - startLocation.getY();
            double tempConstant = b / directionY;
            if(tempConstant > 0 && tempConstant < constant){
                double xAtCollide = tempConstant * directionX + startLocation.getX();
                double zAtCollide = tempConstant * directionZ + startLocation.getZ();
                if (between(xAtCollide, min.x, max.x, 0)
                        && between(zAtCollide, min.z, max.z, 0)) {
                    constant = tempConstant;
                    blockFace = BlockFace.DOWN;
                }
            }
        }
        else {
            double e = max.y - startLocation.getY();
            double tempConstant = e / directionY;
            if (tempConstant > 0 && tempConstant < constant) {
                double xAtCollide = tempConstant * directionX + startLocation.getX();
                double zAtCollide = tempConstant * directionZ + startLocation.getZ();
                if (between(xAtCollide, min.x, max.x, 0)
                        && between(zAtCollide, min.z, max.z, 0)) {
                    constant = tempConstant;
                    blockFace = BlockFace.UP;
                }
            }
        }

        if(directionX < 0) {
            double d = max.x - startLocation.getX();
            double tempConstant = d / directionX;
            if (tempConstant > 0 && tempConstant < constant) {
                double yAtCollide = tempConstant * directionY + startLocation.getY();
                double zAtCollide = tempConstant * directionZ + startLocation.getZ();
                if (between(yAtCollide, min.y, max.y, 0)
                        && between(zAtCollide, min.z, max.z, 0)) {
                    constant = tempConstant;
                    blockFace = BlockFace.EAST;
                }
            }
        }
        else {
            double a = min.x - startLocation.getX();
            double tempConstant = a / directionX;
            if (tempConstant > 0 && tempConstant < constant) {
                double yAtCollide = tempConstant * directionY + startLocation.getY();
                double zAtCollide = tempConstant * directionZ + startLocation.getZ();
                if (between(yAtCollide, min.y, max.y, 0)
                        && between(zAtCollide, min.z, max.z, 0)) {
                    constant = tempConstant;
                    blockFace = BlockFace.WEST;
                }
            }
        }

        if(directionZ > 0) {
            double c = min.z - startLocation.getZ();
            double tempConstant = c / directionZ;
            if(tempConstant > 0 && tempConstant < constant) {
                double yAtCollide = tempConstant * directionY + startLocation.getY();
                double xAtCollide = tempConstant * directionX + startLocation.getX();
                if (between(yAtCollide, min.y, max.y, 0)
                        && between(xAtCollide, min.x, max.x, 0)) {
                    blockFace = BlockFace.NORTH;
                }
            }
        }
        else {
            double f = max.z - startLocation.getZ();
            double tempConstant = f / directionZ;
            if(tempConstant < constant) {
                double yAtCollide = tempConstant * directionY + startLocation.getY();
                double xAtCollide = tempConstant * directionX + startLocation.getX();
                if (between(yAtCollide, min.y, max.y, 0)
                        && between(xAtCollide, min.x, max.x, 0)) {
                    blockFace = BlockFace.SOUTH;
                }
            }
        }
        return blockFace;
    }

    public static boolean between(double num, double a, double b, double EOF) {
        if (a <= b)
            return num + EOF >= a && num - EOF <= b;
        return num + EOF >= b && num - EOF <= a;
    }

    private Box getAxisAllignedBoundBox(World world, Vector3i targetBlock) {
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

    private MiningPlane getMiningPlaneFromPlayer(Ref<EntityStore> ref, ComponentAccessor<EntityStore> componentAccessor, Player player, Vector3i targetBlock) {
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

            // I hope this isn't as slow but if they change things up on us it's more resiliant
            float eyeHeight = 0.0F;
            ModelComponent modelComponent = componentAccessor.getComponent(ref, ModelComponent.getComponentType());
            if (modelComponent != null) {
                eyeHeight = modelComponent.getModel().getEyeHeight(ref, componentAccessor);
            }

            BlockFace face = blockFaceCollide(playerPos.add(new Vector3d(0,eyeHeight,0)),direction,getAxisAllignedBoundBox(world, targetBlock));

            switch(face) {
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

    private void breakSurroundingBlocks(World world, int centerX, int centerY, int centerZ, MiningPlane plane, boolean isHammer, CommandBuffer<EntityStore> commandBuffer, ItemTool itemTool) {
        for (int d1 = -1; d1 <= 1; d1++) {
            for (int d2 = -1; d2 <= 1; d2++) {
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
                    damangeBlockWithDrops(world, x, y, z, centerX, centerY, centerZ, isHammer, commandBuffer, itemTool);
                } finally {
                    PROCESSING_BLOCKS.remove(posKey);
                }
            }
        }
    }

    private void damangeBlockWithDrops(World world, int x, int y, int z, int dropX, int dropY, int dropZ, boolean isHammer, CommandBuffer<EntityStore> commandBuffer, ItemTool itemTool) {
        Vector3i pos = new Vector3i(x, y, z);
        BlockType blockType = world.getBlockType(pos);
        if (blockType == null) {
            return;
        }

        String blockId = blockType.getId();

        if (blockId == null || blockId.equals("Air") || blockId.equals("Empty") || blockId.equals("Bedrock")) {
            return;
        }

        if (!isBlockMineableByTool(blockType, isHammer)) {
            return;
        }
        Store<ChunkStore> chunkStore = world.getChunkStore().getStore();
        long chunkIndex = ChunkUtil.indexChunkFromBlock(x, z);
        Ref<ChunkStore> chunkReference = chunkStore.getExternalData().getChunkReference(chunkIndex);
        
        boolean result = BlockHarvestUtils.performBlockDamage(pos, (ItemStack)null, itemTool, 1.0F, 0, chunkReference, commandBuffer, chunkStore);

        if (!result) {
            return;
        }

        try {
            List<ItemStack> drops = new ArrayList<>();
            String dropItemId = blockId;

            BlockGathering gathering = blockType.getGathering();
            if (gathering != null) {
                BlockBreakingDropType breaking = gathering.getBreaking();
                if (breaking != null) {
                    String specificItemId = breaking.getItemId();
                    if (specificItemId != null) {
                        dropItemId = specificItemId;
                    } else {
                        String dropListId = breaking.getDropListId();
                        if (dropListId != null) {
                            ItemModule itemModule = ItemModule.get();
                            if (itemModule != null && itemModule.isEnabled()) {
                                int quantity = breaking.getQuantity();
                                if (quantity <= 0) quantity = 1;
                                for (int i = 0; i < quantity; i++) {
                                    List<ItemStack> randomDrops = itemModule.getRandomItemDrops(dropListId);
                                    if (randomDrops != null) {
                                        drops.addAll(randomDrops);
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (drops.isEmpty() && dropItemId != null) {
                drops.add(new ItemStack(dropItemId, 1));
            }

            Vector3i dropPos = new Vector3i(dropX, dropY, dropZ);
            for (ItemStack drop : drops) {
                if (drop != null && !drop.isEmpty()) {
                    dropItemStack(world, drop, dropPos);
                }
            }
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Error spawning drops at %d, %d, %d", x, y, z);
        }
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

    private void dropItemStack(World world, ItemStack itemStack, Vector3i position) {
        Store<EntityStore> entityStore = world.getEntityStore().getStore();
        Vector3d dropPos = position.toVector3d();

        Holder<EntityStore> holder = ItemComponent.generateItemDrop(
            entityStore,
            itemStack,
            dropPos,
            Vector3f.NaN,
            0f, 0f, 0f
        );

        if (holder == null) {
            return;
        }

        world.execute(() -> {
            try {
                entityStore.addEntity(holder, AddReason.SPAWN);
            } catch (IllegalStateException e) {
                world.execute(() -> entityStore.addEntity(holder, AddReason.SPAWN));
            }
        });
    }

    private static long packPosition(int x, int y, int z) {
        return ((long)(x & 0x3FFFFFF) << 38) | ((long)(z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }
}
