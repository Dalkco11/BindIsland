package com.dalkco.bindisland.mixin;

import com.dalkco.bindisland.InputTracker;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Mouse button presses (only used when "mouse buttons" is enabled). */
@Mixin(MouseHandler.class)
public class MouseHandlerMixin {
    @Inject(method = "onButton", at = @At("HEAD"))
    private void bindisland$onButton(long window, MouseButtonInfo info, int action, CallbackInfo ci) {
        if (action == InputConstants.PRESS) {
            InputTracker.onMouse(info.button());
        }
    }
}
