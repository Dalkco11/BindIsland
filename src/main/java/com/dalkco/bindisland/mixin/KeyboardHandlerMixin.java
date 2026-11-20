package com.dalkco.bindisland.mixin;

import com.dalkco.bindisland.InputTracker;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Sees every key press before the game (and malilib) handles it. */
@Mixin(KeyboardHandler.class)
public class KeyboardHandlerMixin {
    @Inject(method = "keyPress", at = @At("HEAD"))
    private void bindisland$onKeyPress(long window, int action, KeyEvent event, CallbackInfo ci) {
        if (action == InputConstants.PRESS) {
            InputTracker.onKey(event);
        }
    }
}
