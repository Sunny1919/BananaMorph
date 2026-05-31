package xyz.nifeather.morph.backends.server.renderer.utilties;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataSerializer;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.npc.VillagerData;
import org.jetbrains.annotations.Nullable;
import xyz.nifeather.morph.backends.server.renderer.network.datawatcher.values.SingleValue;
import org.joml.Vector3f;

import java.util.Optional;
import java.util.UUID;

public class ProtocolRegistryUtils
{
    @Nullable
    public static EntityDataSerializer<?> getSerializer(SingleValue<?> sv)
    {
        return getSerializer(sv.defaultValue());
    }

    @Nullable
    public static EntityDataSerializer<?> getSerializer(Object instance)
    {
        var clazz = instance.getClass();

        boolean isOptional = false;
        if (instance instanceof Optional<?> optional)
        {
            if (optional.isEmpty())
                throw new IllegalArgumentException("An empty Optional is given");

            clazz = optional.get().getClass();
            instance = optional.get();
            isOptional = true;
        }

        if (isOptional)
        {
            if (clazz == BlockPos.class)
                return EntityDataSerializers.OPTIONAL_BLOCK_POS;
            if (Component.class.isAssignableFrom(clazz))
                return EntityDataSerializers.OPTIONAL_COMPONENT;
            if (clazz == Integer.class)
                return EntityDataSerializers.OPTIONAL_UNSIGNED_INT;
            if (clazz == UUID.class)
                return EntityDataSerializers.OPTIONAL_UUID;
        }

        if (clazz == Byte.class)
            return EntityDataSerializers.BYTE;
        if (clazz == Integer.class)
            return EntityDataSerializers.INT;
        if (clazz == Float.class)
            return EntityDataSerializers.FLOAT;
        if (clazz == String.class)
            return EntityDataSerializers.STRING;
        if (Component.class.isAssignableFrom(clazz))
            return EntityDataSerializers.COMPONENT;
        if (clazz == Boolean.class)
            return EntityDataSerializers.BOOLEAN;
        if (clazz == BlockPos.class)
            return EntityDataSerializers.BLOCK_POS;
        if (clazz == Vector3f.class)
            return EntityDataSerializers.VECTOR3;
        if (clazz == Pose.class)
            return EntityDataSerializers.POSE;
        if (clazz == VillagerData.class)
            return EntityDataSerializers.VILLAGER_DATA;
        if (clazz == UUID.class)
            return EntityDataSerializers.OPTIONAL_UUID;
        if (clazz == net.minecraft.core.Rotations.class)
            return EntityDataSerializers.ROTATIONS;

        throw new IllegalArgumentException("Unsupported class for serialization: " + clazz.getName());
    }
}
