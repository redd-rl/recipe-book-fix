package recipebookfix.mixin;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.stats.RecipeBookSettings;
import net.minecraft.world.inventory.RecipeBookType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.nio.charset.StandardCharsets;

@Mixin(RecipeBookSettings.class)
public abstract class RecipeBookSettingsMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger("recipebookfix");
    private static final int VANILLA_TYPES = 4;

    // m_12752_ = RecipeBookSettings.read(FriendlyByteBuf)
    @Inject(method = "m_12752_(Lnet/minecraft/network/FriendlyByteBuf;)Lnet/minecraft/stats/RecipeBookSettings;",
            at = @At("HEAD"), cancellable = true, remap = false)
    private static void recipebookfix$read(FriendlyByteBuf buf, CallbackInfoReturnable<RecipeBookSettings> cir) {
        RecipeBookType[] types = RecipeBookType.values();
        if (types.length <= VANILLA_TYPES || fits(buf, types.length) || !fits(buf, VANILLA_TYPES)) {
            return;
        }
        RecipeBookSettings settings = new RecipeBookSettings();
        for (int i = 0; i < VANILLA_TYPES; i++) {
            settings.setOpen(types[i], buf.readBoolean());
            settings.setFiltering(types[i], buf.readBoolean());
        }
        LOGGER.info("Server sent a vanilla recipe book layout ({} types, client has {}); ignoring modded recipe book types",
                VANILLA_TYPES, types.length);
        cir.setReturnValue(settings);
    }

    private static boolean fits(FriendlyByteBuf buf, int typeCount) {
        ByteBuf d = buf.duplicate();
        try {
            if (d.readableBytes() < typeCount * 2) {
                return false;
            }
            for (int i = 0; i < typeCount * 2; i++) {
                byte b = d.readByte();
                if (b != 0 && b != 1) {
                    return false;
                }
            }
            int lists = 0;
            while (d.isReadable() && lists < 2) {
                if (!readIdList(d)) {
                    return false;
                }
                lists++;
            }
            return lists > 0 && !d.isReadable();
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static boolean readIdList(ByteBuf d) {
        int count = readVarInt(d);
        if (count < 0 || count > d.readableBytes()) {
            return false;
        }
        for (int i = 0; i < count; i++) {
            int len = readVarInt(d);
            if (len <= 0 || len > 32767 * 3 || len > d.readableBytes()) {
                return false;
            }
            String id = d.readCharSequence(len, StandardCharsets.UTF_8).toString();
            if (ResourceLocation.tryParse(id) == null) {
                return false;
            }
        }
        return true;
    }

    private static int readVarInt(ByteBuf d) {
        int value = 0;
        for (int shift = 0; shift < 35; shift += 7) {
            byte b = d.readByte();
            value |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) {
                return value;
            }
        }
        return -1;
    }
}
