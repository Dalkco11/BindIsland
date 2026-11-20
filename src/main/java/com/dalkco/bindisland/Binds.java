package com.dalkco.bindisland;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/** Finds which binds a key press triggers: vanilla / Fabric key mappings and malilib hotkeys. */
public final class Binds {
    private static final boolean MALILIB = FabricLoader.getInstance().isModLoaded("malilib");

    /**
     * One bind. {@code id} is stable ("km:key.jump", "ml:Litematica/openGuiMainMenu"), {@code state} shows
     * the result of a toggle (may be null).
     */
    public record BindInfo(String id, Component action, String source, Component key, boolean bound,
                           Supplier<Component> state) {
        /** The custom label if the player set one, otherwise the bind's own name. */
        public Component displayAction() {
            String l = BindConfig.label(id);
            return l != null ? Component.literal(l) : action;
        }
    }

    private Binds() {
    }

    public static List<BindInfo> matchKey(KeyEvent event, boolean screenOpen) {
        List<BindInfo> out = new ArrayList<>();
        Minecraft mc = Minecraft.getInstance();
        if (!screenOpen) {
            for (KeyMapping km : mc.options.keyMappings) {
                if (!km.isUnbound() && km.matches(event)) {
                    out.add(vanilla(km));
                }
            }
        }
        if (MALILIB) {
            out.addAll(MalilibBinds.match(event.key(), screenOpen));
        }
        return out;
    }

    public static List<BindInfo> matchMouse(int button) {
        List<BindInfo> out = new ArrayList<>();
        InputConstants.Key key = InputConstants.Type.MOUSE.getOrCreate(button);
        for (KeyMapping km : Minecraft.getInstance().options.keyMappings) {
            if (!km.isUnbound() && km.matches(key)) {
                out.add(vanilla(km));
            }
        }
        if (MALILIB) {
            // malilib stores mouse buttons as button - 100
            out.addAll(MalilibBinds.match(button - 100, false));
        }
        return out;
    }

    /** Every known bind, sorted by mod and name (for the settings list). */
    public static List<BindInfo> all() {
        List<BindInfo> out = new ArrayList<>();
        for (KeyMapping km : Minecraft.getInstance().options.keyMappings) {
            out.add(vanilla(km));
        }
        if (MALILIB) {
            out.addAll(MalilibBinds.all());
        }
        out.sort(Comparator.comparing((BindInfo b) -> b.source().toLowerCase(Locale.ROOT))
                .thenComparing(b -> b.action().getString().toLowerCase(Locale.ROOT)));
        return out;
    }

    public static boolean hasMalilib() {
        return MALILIB;
    }

    static BindInfo vanilla(KeyMapping km) {
        return new BindInfo("km:" + km.getName(), Component.translatable(km.getName()), source(km.getCategory()),
                km.getTranslatedKeyMessage(), !km.isUnbound(), state(km));
    }

    private static String source(KeyMapping.Category category) {
        String ns = category.id().getNamespace();
        if ("minecraft".equals(ns)) {
            return "Minecraft";
        }
        return FabricLoader.getInstance().getModContainer(ns)
                .map(m -> m.getMetadata().getName())
                .orElseGet(() -> category.label().getString());
    }

    /** Result of vanilla toggles that are worth showing. */
    private static Supplier<Component> state(KeyMapping km) {
        Minecraft mc = Minecraft.getInstance();
        if (km == mc.options.keyTogglePerspective) {
            return () -> Component.translatable("bindisland.state.camera."
                    + mc.options.getCameraType().name().toLowerCase(Locale.ROOT));
        }
        if (km == mc.options.keySmoothCamera) {
            return () -> onOff(mc.options.smoothCamera);
        }
        return null;
    }

    public static Component onOff(boolean on) {
        return on
                ? Component.translatable("bindisland.state.on").withStyle(ChatFormatting.GREEN)
                : Component.translatable("bindisland.state.off").withStyle(ChatFormatting.RED);
    }
}
