package xyz.nifeather.morph.backends.server.renderer.network;

import net.minecraft.network.syncher.SynchedEntityData;
import xyz.nifeather.morph.backends.server.renderer.network.datawatcher.values.SingleValue;

public interface ICustomSerializeMethod<X>
{
    SynchedEntityData.DataValue<?> apply(SingleValue<X> value, X valueObj);
}
