package com.dalkco.bindisland;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** One bind: custom text on the island and show / hide. */
public class BindEditScreen extends Screen {
    private static final int W = 240;

    private final Screen parent;
    private final Binds.BindInfo info;
    private EditBox label;
    private String value;
    private boolean shown;
    private int top;

    public BindEditScreen(Screen parent, Binds.BindInfo info) {
        super(Component.translatable("bindisland.edit.title"));
        this.parent = parent;
        this.info = info;
        String l = BindConfig.label(info.id());
        this.value = l == null ? "" : l;
        this.shown = !BindConfig.isHidden(info.id());
    }

    @Override
    protected void init() {
        int x = this.width / 2 - W / 2;
        top = Math.max(70, this.height / 2 - 50);
        label = new EditBox(this.font, x, top, W, 20, Component.translatable("bindisland.edit.label"));
        label.setMaxLength(64);
        label.setValue(value);
        label.setHint(Component.literal(info.action().getString()).withStyle(ChatFormatting.DARK_GRAY));
        label.setResponder(s -> value = s);
        addRenderableWidget(label);
        setInitialFocus(label);

        addRenderableWidget(CycleButton.onOffBuilder(shown)
                .create(x, top + 26, W, 20, Component.translatable("bindisland.edit.shown"), (b, v) -> shown = v));
        addRenderableWidget(Button.builder(Component.translatable("bindisland.edit.reset"), b -> label.setValue(""))
                .bounds(x, top + 50, W, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(x, top + 82, W / 2 - 2, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> back())
                .bounds(x + W / 2 + 2, top + 82, W / 2 - 2, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);
        int cx = this.width / 2;
        g.centeredText(this.font, this.title, cx, 10, 0xFFFFFFFF);
        g.centeredText(this.font, Component.translatable("bindisland.edit.info", info.action(), info.source(), info.key()),
                cx, top - 26, 0xFFBBBBBB);
        g.text(this.font, Component.translatable("bindisland.edit.label"), cx - W / 2, top - 11, 0xFFAAAAAA);

        // preview with the text being typed
        BindConfig.Data c = BindConfig.get();
        Binds.BindInfo preview = new Binds.BindInfo("bindisland:preview",
                value.isBlank() ? info.action() : Component.literal(value.trim()), info.source(), info.key(), true,
                info.state());
        IslandHud.View view = new IslandHud.View(info.key(), java.util.List.of(preview), 1, false);
        int w = IslandHud.measure(this.font, view, c);
        int h = IslandHud.height(this.font, view, c);
        IslandHud.paint(g, this.font, cx - w / 2, Math.max(24, top - 40 - h), w, w, view, shown ? 1f : 0.45f, c);
    }

    private void back() {
        if (this.minecraft != null) {
            this.minecraft.gui.setScreen(parent);
        }
    }

    @Override
    public void onClose() {
        BindConfig.setLabel(info.id(), value);
        BindConfig.setHidden(info.id(), !shown);
        back();
    }
}
