package com.dalkco.bindisland;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.List;

/** Every bind on one key: show / hide each of them and rename it. */
public class BindListScreen extends Screen {
    private static final int ROW_H = 20;
    private static final int ROW_GAP = 22;

    private final Screen parent;
    private final List<Binds.BindInfo> binds;
    private final List<Button> rowButtons = new ArrayList<>();
    private final List<Button> editButtons = new ArrayList<>();
    private int page;
    private int rows;
    private int listTop;
    private int bottomY;
    private Button prev;
    private Button next;

    public BindListScreen(Screen parent, Component key, List<Binds.BindInfo> binds) {
        super(Component.translatable("bindisland.key.title", key));
        this.parent = parent;
        this.binds = binds;
    }

    @Override
    protected void init() {
        rowButtons.clear();
        editButtons.clear();
        int w = Math.min(340, this.width - 20);
        int x = this.width / 2 - w / 2;
        listTop = 40;
        bottomY = this.height - 50;
        rows = Math.max(2, (bottomY - listTop - 14) / ROW_GAP);
        for (int i = 0; i < rows; i++) {
            final int row = i;
            int y = listTop + i * ROW_GAP;
            rowButtons.add(addRenderableWidget(Button.builder(Component.empty(), b -> toggleRow(row))
                    .bounds(x, y, w - 24, ROW_H).build()));
            editButtons.add(addRenderableWidget(Button.builder(Component.literal("✎"), b -> editRow(row))
                    .bounds(x + w - 22, y, 22, ROW_H)
                    .tooltip(Tooltip.create(Component.translatable("bindisland.binds.edit.tooltip"))).build()));
        }
        int half = (w - 48) / 2;
        prev = addRenderableWidget(Button.builder(Component.literal("<"), b -> { page--; refresh(); })
                .bounds(x, bottomY, 20, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("bindisland.key.show_all"), b -> setAll(false))
                .bounds(x + 24, bottomY, half, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("bindisland.key.hide_all"), b -> setAll(true))
                .bounds(x + 24 + half + 4, bottomY, half, 20).build());
        next = addRenderableWidget(Button.builder(Component.literal(">"), b -> { page++; refresh(); })
                .bounds(x + w - 20, bottomY, 20, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(this.width / 2 - 100, bottomY + 24, 200, 20).build());
        refresh();
    }

    private void setAll(boolean hidden) {
        for (Binds.BindInfo b : binds) {
            BindConfig.setHidden(b.id(), hidden);
        }
        refresh();
    }

    private Binds.BindInfo at(int row) {
        int idx = page * rows + row;
        return idx < binds.size() ? binds.get(idx) : null;
    }

    private void refresh() {
        int pages = Math.max(1, (binds.size() + rows - 1) / rows);
        page = Math.max(0, Math.min(page, pages - 1));
        for (int i = 0; i < rows; i++) {
            Binds.BindInfo info = at(i);
            Button b = rowButtons.get(i);
            Button e = editButtons.get(i);
            b.visible = e.visible = info != null;
            if (info != null) {
                b.setMessage(rowText(info));
            }
        }
        prev.visible = next.visible = pages > 1;
        prev.active = page > 0;
        next.active = page < pages - 1;
    }

    static Component rowText(Binds.BindInfo info) {
        boolean hidden = BindConfig.isHidden(info.id());
        MutableComponent text = Component.literal(hidden ? "✘ " : "✔ ")
                .withStyle(hidden ? ChatFormatting.DARK_GRAY : ChatFormatting.GREEN);
        text.append(info.displayAction().copy().withStyle(hidden ? ChatFormatting.GRAY : ChatFormatting.WHITE));
        text.append(Component.literal(" · " + info.source()).withStyle(ChatFormatting.DARK_GRAY));
        return text;
    }

    private void toggleRow(int row) {
        Binds.BindInfo info = at(row);
        if (info != null) {
            BindConfig.setHidden(info.id(), !BindConfig.isHidden(info.id()));
            refresh();
        }
    }

    private void editRow(int row) {
        Binds.BindInfo info = at(row);
        if (info != null && this.minecraft != null) {
            this.minecraft.gui.setScreen(new BindEditScreen(this, info));
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);
        g.centeredText(this.font, this.title, this.width / 2, 12, 0xFFFFFFFF);
        g.centeredText(this.font, Component.translatable("bindisland.key.hint"), this.width / 2, 25, 0xFF999999);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.gui.setScreen(parent);
        }
    }
}
