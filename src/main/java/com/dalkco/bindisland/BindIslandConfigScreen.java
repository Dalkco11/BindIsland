package com.dalkco.bindisland;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Locale;
import java.util.function.Consumer;

/** Main settings: look on the left, behaviour on the right, live preview on top. */
public class BindIslandConfigScreen extends Screen {
    private static final int COL_W = 160;
    private static final int H = 20;
    private static final int GAP = 22;

    private final Screen parent;
    private int top;

    public BindIslandConfigScreen(Screen parent) {
        super(Component.translatable("bindisland.config.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        BindConfig.Data c = BindConfig.get();
        top = Math.max(70, this.height / 2 - 4 * GAP - 14);
        int lx = this.width / 2 - COL_W - 4;
        int rx = this.width / 2 + 4;

        // ---- left: look
        int y = top;
        y = toggle(lx, y, "enabled", c.enabled, v -> c.enabled = v);
        addRenderableWidget(tip(CycleButton.<BindConfig.Position>builder(
                        p -> Component.translatable("bindisland.position." + p.name().toLowerCase(Locale.ROOT)), c.position)
                .withValues(BindConfig.Position.values())
                .create(lx, y, COL_W, H, Component.translatable("bindisland.config.position"),
                        (b, v) -> { c.position = v; BindConfig.save(); }), "position"));
        y += GAP;
        addRenderableWidget(tip(CycleButton.<BindConfig.Style>builder(
                        st -> Component.translatable("bindisland.style." + st.name().toLowerCase(Locale.ROOT)), c.style)
                .withValues(BindConfig.Style.values())
                .create(lx, y, COL_W, H, Component.translatable("bindisland.config.style"),
                        (b, v) -> { c.style = v; BindConfig.save(); }), "style"));
        y += GAP;
        addRenderableWidget(tip(CycleButton.<Integer>builder(
                        i -> Component.translatable("bindisland.accent." + i).withColor(BindConfig.ACCENTS[i] & 0xFFFFFF), c.accent)
                .withValues(0, 1, 2, 3, 4, 5)
                .create(lx, y, COL_W, H, Component.translatable("bindisland.config.accent"),
                        (b, v) -> { c.accent = v; BindConfig.save(); }), "accent"));
        y += GAP;
        addRenderableWidget(tip(new IntSlider(lx, y, COL_W, H, 50, 200, 10, c.scale,
                v -> Component.translatable("bindisland.config.scale", v), v -> c.scale = v), "scale"));
        y += GAP;
        addRenderableWidget(tip(new IntSlider(lx, y, COL_W, H, 0, 100, 5, c.opacity,
                v -> Component.translatable("bindisland.config.opacity", v), v -> c.opacity = v), "opacity"));
        y += GAP;
        addRenderableWidget(tip(new IntSlider(lx, y, COL_W, H, 500, 6000, 100, c.durationMs,
                v -> Component.translatable("bindisland.config.duration", String.format(Locale.ROOT, "%.1f", v / 1000.0)),
                v -> c.durationMs = v), "duration"));
        y += GAP;
        addRenderableWidget(tip(new IntSlider(lx, y, COL_W, H, 0, 120, 1, c.offsetY,
                v -> Component.translatable("bindisland.config.offset", v), v -> c.offsetY = v), "offset"));

        // ---- right: behaviour
        y = top;
        y = toggle(rx, y, "show_source", c.showSource, v -> c.showSource = v);
        y = toggle(rx, y, "show_state", c.showState, v -> c.showState = v);
        y = toggle(rx, y, "animations", c.animations, v -> c.animations = v);
        y = toggle(rx, y, "combine", c.combineRepeats, v -> c.combineRepeats = v);
        addRenderableWidget(tip(new IntSlider(rx, y, COL_W, H, 1, 5, 1, c.stack,
                v -> Component.translatable("bindisland.config.stack", v), v -> c.stack = v), "stack"));
        y += GAP;
        y = toggle(rx, y, "mouse", c.mouseButtons, v -> c.mouseButtons = v);
        y = toggle(rx, y, "in_screens", c.inScreens, v -> c.inScreens = v);
        toggle(rx, y, "unbound", c.unboundKeys, v -> c.unboundKeys = v);

        // ---- bottom
        int by = Math.min(top + 8 * GAP + 8, this.height - 50);
        addRenderableWidget(Button.builder(Component.translatable("bindisland.config.binds"),
                        b -> this.minecraft.gui.setScreen(new KeyListScreen(this)))
                .bounds(lx, by, COL_W, H)
                .tooltip(Tooltip.create(Component.translatable("bindisland.config.binds.tooltip"))).build());
        addRenderableWidget(Button.builder(Component.translatable("bindisland.config.reset"), b -> {
                    BindConfig.resetSettings();
                    rebuildWidgets();
                }).bounds(rx, by, COL_W, H)
                .tooltip(Tooltip.create(Component.translatable("bindisland.config.reset.tooltip"))).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(this.width / 2 - 100, by + GAP + 2, 200, H).build());
    }

    private int toggle(int x, int y, String key, boolean value, Consumer<Boolean> setter) {
        addRenderableWidget(tip(CycleButton.onOffBuilder(value)
                .create(x, y, COL_W, H, Component.translatable("bindisland.config." + key), (b, v) -> {
                    setter.accept(v);
                    BindConfig.save();
                }), key));
        return y + GAP;
    }

    private static <T extends net.minecraft.client.gui.components.AbstractWidget> T tip(T widget, String key) {
        widget.setTooltip(Tooltip.create(Component.translatable("bindisland.config." + key + ".tooltip")));
        return widget;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);
        g.centeredText(this.font, this.title, this.width / 2, 10, 0xFFFFFFFF);

        // live preview
        BindConfig.Data c = BindConfig.get();
        String src = Component.translatable("bindisland.preview.source").getString();
        IslandHud.View sample = new IslandHud.View(Component.literal("M"), java.util.List.of(
                new Binds.BindInfo("bindisland:preview1", Component.translatable("bindisland.preview.action"), src,
                        Component.literal("M"), true, null),
                new Binds.BindInfo("bindisland:preview2", Component.translatable("bindisland.preview.action2"), src,
                        Component.literal("M"), true, () -> Binds.onOff(true))), 2, false);
        float s = c.scale / 100f;
        int w = IslandHud.measure(this.font, sample, c);
        int h = IslandHud.height(this.font, sample, c);
        int areaTop = 24;
        int areaBottom = top - 6;
        g.pose().pushMatrix();
        g.pose().scale(s, s);
        int px = Math.round(this.width / 2f / s) - w / 2;
        int py = Math.round((areaTop + (areaBottom - areaTop - h * s) / 2f) / s);
        IslandHud.paint(g, this.font, px, Math.max(Math.round(22 / s), py), w, w, sample, c.enabled ? 1f : 0.45f, c);
        g.pose().popMatrix();
    }

    @Override
    public void onClose() {
        BindConfig.save();
        if (this.minecraft != null) {
            this.minecraft.gui.setScreen(parent);
        }
    }
}
