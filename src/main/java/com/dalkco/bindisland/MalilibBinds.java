package com.dalkco.bindisland;

import com.mojang.blaze3d.platform.InputConstants;
import fi.dy.masa.malilib.config.IConfigBoolean;
import fi.dy.masa.malilib.event.InputEventHandler;
import fi.dy.masa.malilib.hotkeys.IHotkey;
import fi.dy.masa.malilib.hotkeys.IKeybind;
import fi.dy.masa.malilib.hotkeys.KeybindCategory;
import fi.dy.masa.malilib.hotkeys.KeybindSettings;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Hotkeys of malilib mods (Litematica, Tweakeroo, MiniHUD, ...). Only loaded when malilib is installed,
 * so the mod works without it.
 */
final class MalilibBinds {
    private static boolean failed;

    private MalilibBinds() {
    }

    private static List<KeybindCategory> categories() {
        return ((InputEventHandler) InputEventHandler.getKeybindManager()).getKeybindCategories();
    }

    /** Hotkeys whose last key is {@code code} and whose other keys are held right now. */
    static List<Binds.BindInfo> match(int code, boolean screenOpen) {
        List<Binds.BindInfo> out = new ArrayList<>();
        if (failed) {
            return out;
        }
        try {
            for (KeybindCategory cat : categories()) {
                for (IHotkey hotkey : cat.getHotkeys()) {
                    IKeybind kb = hotkey.getKeybind();
                    if (kb == null) {
                        continue;
                    }
                    List<Integer> keys = kb.getKeys();
                    if (keys.isEmpty() || keys.get(keys.size() - 1) != code) {
                        continue;
                    }
                    boolean held = true;
                    for (int i = 0; i < keys.size() - 1; i++) {
                        int k = keys.get(i);
                        if (k >= 0 && !InputConstants.isKeyDown(k)) {
                            held = false;
                            break;
                        }
                    }
                    if (!held) {
                        continue;
                    }
                    KeybindSettings.Context ctx = kb.getSettings().getContext();
                    if (screenOpen ? ctx == KeybindSettings.Context.INGAME : ctx == KeybindSettings.Context.GUI) {
                        continue;
                    }
                    out.add(info(cat, hotkey, kb));
                }
            }
        } catch (Throwable t) {
            failed = true;
            BindIslandClient.LOGGER.warn("Could not read malilib hotkeys, malilib support is off", t);
        }
        return out;
    }

    static List<Binds.BindInfo> all() {
        List<Binds.BindInfo> out = new ArrayList<>();
        if (failed) {
            return out;
        }
        try {
            for (KeybindCategory cat : categories()) {
                for (IHotkey hotkey : cat.getHotkeys()) {
                    IKeybind kb = hotkey.getKeybind();
                    if (kb != null) {
                        out.add(info(cat, hotkey, kb));
                    }
                }
            }
        } catch (Throwable t) {
            failed = true;
            BindIslandClient.LOGGER.warn("Could not read malilib hotkeys, malilib support is off", t);
        }
        return out;
    }

    private static Binds.BindInfo info(KeybindCategory cat, IHotkey hotkey, IKeybind kb) {
        String keys = kb.getKeys().isEmpty() ? "" : kb.getKeysDisplayString();
        return new Binds.BindInfo("ml:" + cat.getModName() + "/" + hotkey.getName(),
                Component.literal(hotkey.getPrettyName()), cat.getModName(),
                keys.isEmpty() ? Component.translatable("bindisland.key.none") : Component.literal(keys),
                !keys.isEmpty(),
                hotkey instanceof IConfigBoolean b ? () -> Binds.onOff(b.getBooleanValue()) : null);
    }
}
