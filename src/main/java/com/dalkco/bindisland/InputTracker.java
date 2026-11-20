package com.dalkco.bindisland;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;

import java.util.List;

/** Turns raw key / mouse presses into island entries. */
public final class InputTracker {
    private InputTracker() {
    }

    public static void onKey(KeyEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || event.key() == InputConstants.KEY_ESCAPE) {
            return;
        }
        boolean screenOpen = mc.gui.screen() != null;
        BindConfig.Data c = BindConfig.get();
        if (screenOpen && !c.inScreens) {
            return;
        }
        InputConstants.Key key = InputConstants.getKey(event);
        List<Binds.BindInfo> matched = Binds.matchKey(event, screenOpen);
        // keys without an action only make sense in game (in menus every letter is typing)
        IslandHud.push(matched, key.getDisplayName(), "key:" + key.getName(), !screenOpen);
    }

    public static void onMouse(int button) {
        Minecraft mc = Minecraft.getInstance();
        BindConfig.Data c = BindConfig.get();
        if (!c.mouseButtons || mc.level == null || mc.player == null || mc.gui.screen() != null) {
            return;
        }
        InputConstants.Key key = InputConstants.Type.MOUSE.getOrCreate(button);
        IslandHud.push(Binds.matchMouse(button), key.getDisplayName(), "key:" + key.getName(), true);
    }
}
