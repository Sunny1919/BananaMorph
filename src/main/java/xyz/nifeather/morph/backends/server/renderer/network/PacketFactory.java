package xyz.nifeather.morph.backends.server.renderer.network;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.Util;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.level.GameType;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import xyz.nifeather.morph.MorphPluginObject;
import xyz.nifeather.morph.backends.server.renderer.network.datawatcher.values.AbstractValues;
import xyz.nifeather.morph.backends.server.renderer.network.datawatcher.values.SingleValue;
import xyz.nifeather.morph.backends.server.renderer.network.datawatcher.watchers.SingleWatcher;
import xyz.nifeather.morph.backends.server.renderer.network.registries.CustomEntries;
import xyz.nifeather.morph.misc.DisguiseEquipment;
import xyz.nifeather.morph.misc.NmsRecord;
import xyz.nifeather.morph.utilities.EntityTypeUtils;
import xyz.nifeather.morph.utilities.NmsUtils;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class PacketFactory extends MorphPluginObject
{
    private final Cache<Integer, Object> cache = CacheBuilder.newBuilder().expireAfterAccess(1, TimeUnit.SECONDS).build();

    public void markPacketOurs(Packet<?> packet)
    {
        cache.put(packet.hashCode(), packet);
    }

    public boolean isPacketOurs(Packet<?> packet)
    {
        return cache.getIfPresent(packet.hashCode()) != null;
    }

    public List<Packet<?>> buildSpawnPackets(DisplayParameters parameters)
    {
        var watcher = parameters.getWatcher();
        var player = watcher.getBindingPlayer();

        List<Packet<?>> packets = new ObjectArrayList<>();

        if (watcher.readEntryOrDefault(CustomEntries.VANISHED, false))
            return packets;

        var disguiseEntityType = watcher.getEntityType();
        var nmsType = EntityTypeUtils.getNmsType(disguiseEntityType);
        if (nmsType == null)
        {
            logger.error("No NMS Type for Bukkit Type '%s'".formatted(disguiseEntityType));
            logger.error("Not build spawn packets!");
            return packets;
        }

        var nmsPlayer = NmsRecord.ofPlayer(player);
        UUID spawnUUID = watcher.readEntryOrThrow(CustomEntries.SPAWN_UUID);
        if (spawnUUID.equals(Util.NIL_UUID))
            throw new IllegalStateException("A watcher with NIL UUID?!");

        //如果是玩家
        if (disguiseEntityType == org.bukkit.entity.EntityType.PLAYER)
        {
            var gameProfile = watcher.readEntryOrThrow(CustomEntries.PROFILE);

            if (gameProfile.getName().isBlank())
                throw new IllegalArgumentException("GameProfile name is empty!");

            packets.addAll(this.buildPlayerInfoPackets(watcher));
        }

        var pitch = player.getPitch();
        var yaw = player.getYaw();

        if (disguiseEntityType == EntityType.PHANTOM)
            pitch = -player.getPitch();

        if (disguiseEntityType == EntityType.ENDER_DRAGON)
            yaw = 180 + yaw;

        //生成实体
        var packetAdd = new ClientboundAddEntityPacket(
                watcher.readEntryOrThrow(CustomEntries.SPAWN_ID), spawnUUID,
                player.getX(), player.getY(), player.getZ(),
                pitch, yaw,
                nmsType, 0,
                nmsPlayer.getDeltaMovement(),
                nmsPlayer.getYHeadRot()
        );

        packets.add(packetAdd);

        //生成装备和Meta
        var displayingFake = watcher.readEntryOrDefault(CustomEntries.DISPLAY_FAKE_EQUIPMENT, false);
        var equip = displayingFake
                ? watcher.readEntryOrDefault(CustomEntries.EQUIPMENT, new DisguiseEquipment())
                : player.getEquipment();

        var equipmentPacket = new ClientboundSetEquipmentPacket(player.getEntityId(),
                ProtocolEquipment.toPairs(equip));

        packets.add(equipmentPacket);
        packets.add(buildFullMetaPacket(player, watcher));

        // 载具
        if (player.getVehicle() != null)
        {
            var nmsEntity = ((CraftEntity)player.getVehicle()).getHandle();
            packets.add(new ClientboundSetPassengersPacket(nmsEntity));
        }

        if (!player.getPassengers().isEmpty())
            packets.add(new ClientboundSetPassengersPacket(nmsPlayer));

        // 属性
        if (disguiseEntityType.isAlive())
        {
            //Attributes
            List<AttributeInstance> attributes = disguiseEntityType == EntityType.PLAYER
                    ? new ObjectArrayList<>(nmsPlayer.getAttributes().getSyncableAttributes())
                    : NmsUtils.getValidAttributes(disguiseEntityType, nmsPlayer.getAttributes());

            var attributePacket = new ClientboundUpdateAttributesPacket(player.getEntityId(), attributes);
            packets.add(attributePacket);
        }

        for (Packet<?> packet : packets)
            markPacketOurs(packet);

        return packets;
    }

    public List<Packet<?>> buildPlayerInfoPackets(SingleWatcher watcher)
    {
        var spawnUUID = watcher.readEntryOrThrow(CustomEntries.SPAWN_UUID);
        var infoRemove = new ClientboundPlayerInfoRemovePacket(List.of(spawnUUID));

        var infoUpdate = new ClientboundPlayerInfoUpdatePacket(
            EnumSet.of(
                    ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER,
                    ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LISTED
            ),
            new ClientboundPlayerInfoUpdatePacket.Entry(
                    spawnUUID, watcher.readEntryOrThrow(CustomEntries.PROFILE),
                    watcher.readEntryOrDefault(CustomEntries.PROFILE_LISTED, false),
                    114514, GameType.DEFAULT_MODE,
                    null, null
            )
        );

        return List.of(infoRemove, infoUpdate);
    }

    public ClientboundSetEntityDataPacket rebuildServerMetaPacket(AbstractValues av, SingleWatcher watcher, ClientboundSetEntityDataPacket originalPacket)
    {
        var values = av.getValues();

        //获取原Meta包中的数据
        var originalData = originalPacket.packedItems();
        if (originalData == null)
            return originalPacket;

        List<SynchedEntityData.DataValue<?>> valuesToAdd = new ObjectArrayList<>();
        var blockedValues = watcher.getBlockedValues();

        for (SynchedEntityData.DataValue<?> w : originalData)
        {
            var index = w.id();

            // 跳过被屏蔽的数据
            if (blockedValues.contains(index))
                continue;

            // 寻找与其匹配的SingleValue
            var singleValue = values.stream()
                    .filter(sv -> sv.index() == index)
                    .findFirst().orElse(null);

            // 如果没有找到，则代表此Index和伪装不兼容，跳过
            if (singleValue == null)
                continue;

            // 如果 Watcher 中有覆盖的有对应的值，则重新包装，否则原样返回
            var val = watcher.readOr(singleValue.index(), null);

            if (val != null)
            {
                var wrapped = ((SingleValue<Object>)singleValue).wrap(val);
                if (wrapped != null)
                    valuesToAdd.add(wrapped);
            }
            else
            {
                valuesToAdd.add(w);
            }
        }

        var newPacket = new ClientboundSetEntityDataPacket(originalPacket.id(), valuesToAdd);
        markPacketOurs(newPacket);

        return newPacket;
    }

    public ClientboundSetEntityDataPacket buildDiffMetaPacket(SingleWatcher watcher)
    {
        List<SynchedEntityData.DataValue<?>> wrappedDataValues = new ObjectArrayList<>();
        var valuesToSent = watcher.getDirty();
        watcher.clearDirty();

        valuesToSent.forEach((single, val) ->
        {
            var wrapped = ((SingleValue<Object>)single).wrap(val);

            if (wrapped != null)
                wrappedDataValues.add(wrapped);
        });

        var metaPacket = new ClientboundSetEntityDataPacket(watcher.readEntryOrThrow(CustomEntries.SPAWN_ID), wrappedDataValues);
        markPacketOurs(metaPacket);

        return metaPacket;
    }

    public ClientboundSetEntityDataPacket buildFullMetaPacket(Player player, SingleWatcher watcher)
    {
        watcher.sync();

        List<SynchedEntityData.DataValue<?>> wrappedDataValues = new ObjectArrayList<>();

        var valuesToSent = watcher.getOverlayedRegistry();
        watcher.clearDirty();

        valuesToSent.forEach((index, val) ->
        {
            var sv = watcher.getSingle(index);

            if (sv == null)
                throw new IllegalArgumentException("Not SingleValue found for index " + index);

            var wrapped = ((SingleValue<Object>)sv).wrap(val);

            if (wrapped != null)
                wrappedDataValues.add(wrapped);
        });

        var metaPacket = new ClientboundSetEntityDataPacket(player.getEntityId(), wrappedDataValues);
        markPacketOurs(metaPacket);

        return metaPacket;
    }

    public ClientboundSetEquipmentPacket getEquipmentPacket(Player player, SingleWatcher watcher)
    {
        var shouldDisplayFakeEquip = watcher.readEntryOrDefault(CustomEntries.DISPLAY_FAKE_EQUIPMENT, false);
        EntityEquipment equipment = shouldDisplayFakeEquip
                    ? watcher.readEntryOrDefault(CustomEntries.EQUIPMENT, new DisguiseEquipment())
                    : player.getEquipment();

        var rawPacket = new ClientboundSetEquipmentPacket(player.getEntityId(),
                ProtocolEquipment.toPairs(equipment));
        markPacketOurs(rawPacket);

        return rawPacket;
    }
}
