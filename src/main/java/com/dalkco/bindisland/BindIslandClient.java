package com.dalkco.bindisland;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BindIslandClient implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("bindisland");

    public static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath("bindisland", "bindisland"));

    private static final int TOGGLE_W = 30;
    private static final int GEAR_W = 16;
    private static final int BTN_H = 14;

    private static KeyMapping toggleKey;
    private static KeyMapping configKey;
    /** screen to open on the next tick (after chat has closed) */
    private static Screen pending;

    @Override
    public void onInitializeClient() {
        BindConfig.load();
        toggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.bindisland.toggle", InputConstants.Type.KEYBOARD, InputConstants.UNKNOWN.getValue(), CATEGORY));
        configKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.bindisland.config", InputConstants.Type.KEYBOARD, InputConstants.UNKNOWN.getValue(), CATEGORY));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggleKey.consumeClick()) {
                toggle();
            }
            while (configKey.consumeClick()) {
                pending = new BindIslandConfigScreen(null);
            }
            if (pending != null && client.gui.screen() == null) {
                Screen s = pending;
                pending = null;
                client.gui.setScreen(s);
            }
        });

        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath("bindisland", "island"), IslandHud::renderHud);
        // while any screen is open the island is drawn on top of it (chat always, other menus if enabled)
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> ScreenEvents.afterExtract(screen).register(
                (s, g, mouseX, mouseY, delta) -> IslandHud.renderOverScreen(s, g, mouseX, mouseY)));

        registerChatControls();

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
                ClientCommands.literal("bindisland")
                        .executes(ctx -> {
                            pending = new BindIslandConfigScreen(null);
                            return 1;
                        })
                        .then(ClientCommands.literal("config").executes(ctx -> {
                            pending = new BindIslandConfigScreen(null);
                            return 1;
                        }))
                        .then(ClientCommands.literal("binds").executes(ctx -> {
                            pending = new KeyListScreen(null);
                            return 1;
                        }))
                        .then(ClientCommands.literal("toggle").executes(ctx -> {
                            toggle();
                            ctx.getSource().sendFeedback(Component.translatable("bindisland.msg.toggled",
                                    Binds.onOff(BindConfig.get().enabled)));
                            return 1;
                        }))));
    }

    public static void toggle() {
        BindConfig.Data c = BindConfig.get();
        c.enabled = !c.enabled;
        BindConfig.save();
        IslandHud.flashSelf();
    }

    private static Component toggleLabel() {
        return BindConfig.get().enabled
                ? Component.translatable("bindisland.chat.on").withStyle(ChatFormatting.GREEN)
                : Component.translatable("bindisland.chat.off").withStyle(ChatFormatting.GRAY);
    }

    /** While chat is open: an on/off switch and a gear next to the island. */
    private static void registerChatControls() {
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (!(screen instanceof ChatScreen)) {
                return;
            }
            Button toggle = Button.builder(toggleLabel(), b -> {
                        toggle();
                        b.setMessage(toggleLabel());
                    })
                    .bounds(0, 0, TOGGLE_W, BTN_H)
                    .tooltip(Tooltip.create(Component.translatable("bindisland.chat.toggle.tooltip")))
                    .build();
            Button gear = Button.builder(Component.literal("⚙"),
                            b -> Minecraft.getInstance().gui.setScreen(new BindIslandConfigScreen(null)))
                    .bounds(0, 0, GEAR_W, BTN_H)
                    .tooltip(Tooltip.create(Component.translatable("bindisland.chat.settings.tooltip")))
                    .build();
            toggle.visible = false;
            gear.visible = false;
            Screens.getWidgets(screen).add(toggle);
            Screens.getWidgets(screen).add(gear);
            // drag the island with the left mouse button to move it
            ScreenMouseEvents.allowMouseClick(screen).register((s, event) ->
                    !(event.button() == 0 && IslandHud.startDrag(event.x(), event.y())));
            ScreenMouseEvents.allowMouseDrag(screen).register((s, event, dx, dy) ->
                    !IslandHud.drag(event.x(), event.y(), s.width, s.height));
            ScreenMouseEvents.allowMouseRelease(screen).register((s, event) -> !IslandHud.stopDrag());
            ScreenEvents.remove(screen).register(s -> IslandHud.stopDrag());
            ScreenEvents.beforeExtract(screen).register((s, g, mouseX, mouseY, delta) -> {
                IslandHud.Bounds b = IslandHud.anchor();
                boolean show = b != null;
                toggle.visible = show;
                gear.visible = show;
                if (!show) {
                    return;
                }
                int total = TOGGLE_W + 2 + GEAR_W;
                int x = b.x2() + 4;
                if (x + total > s.width - 2) {
                    x = b.x1() - 4 - total;
                }
                int y = b.y1() + (b.y2() - b.y1() - BTN_H) / 2;
                toggle.setX(x);
                toggle.setY(y);
                gear.setX(x + TOGGLE_W + 2);
                gear.setY(y);
                toggle.setMessage(toggleLabel());
            });
        });
    }
}
