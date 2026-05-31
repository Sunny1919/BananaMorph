package xyz.nifeather.morph.backends.server.renderer.network;

import io.papermc.paper.adventure.PaperAdventure;
import io.papermc.paper.math.Rotations;
import net.kyori.adventure.text.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.animal.armadillo.Armadillo;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerType;
import org.bukkit.entity.*;
import org.joml.Vector3i;
import xyz.nifeather.morph.backends.server.renderer.network.datawatcher.DataWrappers;
import xyz.nifeather.morph.backends.server.renderer.utilties.HolderUtils;

import java.util.List;
import java.util.Optional;

public class CustomSerializeMethods
{
    public static ICustomSerializeMethod<Wolf.Variant> WOLF_VARIANT = (sv, bukkitVariant) ->
    {
        var rl = ResourceLocation.parse(bukkitVariant.getKey().asString());
        var holder = HolderUtils.getHolderOrThrow(rl, Registries.WOLF_VARIANT);

        return new SynchedEntityData.DataValue<>(sv.index(), EntityDataSerializers.WOLF_VARIANT, holder);
    };

    public static ICustomSerializeMethod<Cat.Type> CAT_VARIANT = (sv, bukkitVariant) ->
    {
        var rl = ResourceLocation.parse(bukkitVariant.getKey().asString());
        var holder = HolderUtils.getHolderOrThrow(rl, Registries.CAT_VARIANT);

        return new SynchedEntityData.DataValue<>(sv.index(), EntityDataSerializers.CAT_VARIANT, holder);
    };

    public static ICustomSerializeMethod<Frog.Variant> FROG_VARIANT = (sv, bukkitVariant) ->
    {
        var rl = ResourceLocation.parse(bukkitVariant.getKey().asString());
        var holder = HolderUtils.getHolderOrThrow(rl, Registries.FROG_VARIANT);

        return new SynchedEntityData.DataValue<>(sv.index(), EntityDataSerializers.FROG_VARIANT, holder);
    };

    public static ICustomSerializeMethod<Optional<Component>> COMPONENT_ADVENTURE_TO_NMS = (sv, val) ->
    {
        var serializer = EntityDataSerializers.OPTIONAL_COMPONENT;
        if (val.isEmpty())
            return new SynchedEntityData.DataValue<>(sv.index(), serializer, Optional.empty());

        var asVanilla = PaperAdventure.asVanilla(val.get());

        return new SynchedEntityData.DataValue<>(sv.index(), serializer, Optional.of(asVanilla));
    };

    // Bukkit's Armadillo don't have the ArmadilloState! :(
    public static ICustomSerializeMethod<DataWrappers.ArmadilloState> ARMADILLO_STATE = (sv, val) ->
    {
        return new SynchedEntityData.DataValue<>(sv.index(), EntityDataSerializers.ARMADILLO_STATE, val.nmsState());
    };

    // WTF?
    public static ICustomSerializeMethod<List<ParticleOptions>> PARTICLE_OPTIONS = (sv, val) ->
    {
        return new SynchedEntityData.DataValue<>(sv.index(), EntityDataSerializers.PARTICLES, val);
    };

    public static ICustomSerializeMethod<Rotations> ROTATIONS = (sv, val) ->
    {
        var nmsRotation = new net.minecraft.core.Rotations((float)val.x(), (float)val.y(), (float)val.z());

        return new SynchedEntityData.DataValue<>(sv.index(), EntityDataSerializers.ROTATIONS, nmsRotation);
    };

    public static ICustomSerializeMethod<DataWrappers.VillagerData> VILLAGER_DATA = (sv, val) ->
    {
        var bukkitVillagerType = val.type();
        var nmsVillagerTypeOptional = BuiltInRegistries.VILLAGER_TYPE
                .getOptional(ResourceLocation.parse(bukkitVillagerType.getKey().asString()));

        var bukkitVillagerProfession = val.profession();
        var nmsVillagerProfessionOptional = BuiltInRegistries.VILLAGER_PROFESSION
                .getOptional(ResourceLocation.parse(bukkitVillagerProfession.getKey().asString()));

        var nmsType = nmsVillagerTypeOptional.orElse(VillagerType.PLAINS);
        var nmsProfession = nmsVillagerProfessionOptional.orElse(VillagerProfession.NONE);
        var nmsData = new VillagerData(nmsType, nmsProfession, val.level());

        return new SynchedEntityData.DataValue<>(sv.index(), EntityDataSerializers.VILLAGER_DATA, nmsData);
    };

    public static ICustomSerializeMethod<DataWrappers.ShulkerDirection> SHULKER_DIRECTION = (sv, val) ->
    {
        return new SynchedEntityData.DataValue<>(sv.index(), EntityDataSerializers.DIRECTION, val.nmsDirection());
    };

    public static ICustomSerializeMethod<Pose> POSE = (sv, val) ->
    {
        var nmsPose = net.minecraft.world.entity.Pose.values()[val.ordinal()];

        return new SynchedEntityData.DataValue<>(sv.index(), EntityDataSerializers.POSE, nmsPose);
    };

    public static ICustomSerializeMethod<Sniffer.State> SNIFFER_STATE = (sv, val) ->
    {
        var nmsState = net.minecraft.world.entity.animal.sniffer.Sniffer.State.values()[val.ordinal()];

        return new SynchedEntityData.DataValue<>(sv.index(), EntityDataSerializers.SNIFFER_STATE, nmsState);
    };

    public static ICustomSerializeMethod<Optional<Vector3i>> BLOCKPOS = (sv, optional) ->
    {
        if (optional.isEmpty())
            return new SynchedEntityData.DataValue<>(sv.index(), EntityDataSerializers.OPTIONAL_BLOCK_POS, Optional.empty());

        var val = optional.get();
        var blockPos = new BlockPos(val.x(), val.y(), val.z());

        return new SynchedEntityData.DataValue<>(sv.index(), EntityDataSerializers.OPTIONAL_BLOCK_POS, Optional.of(blockPos));
    };
}
