package com.dalkco.bindisland;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** All keys that have something bound, with what is on each of them. Click a key to choose its binds. */
public class KeyListScreen extends Screen {
    private enum Filter { ALL, SHOWN, HIDDEN }

    /** One key and everything bound to it. */
    private record KeyGroup(String name, Component key, List<Binds.BindInfo> binds) {
        int shown() {
            int n = 0;
            for (Binds.BindInfo b : binds) {
                if (!BindConfig.isHidden(b.id())) {
                    n++;
                }
            }
            return n;
        }
    }

    private static final int ROW_H = 20;
    private static final int ROW_GAP = 22;

    private final Screen parent;
    private List<KeyGroup> groups = new ArrayList<>();
    private final List<KeyGroup> filtered = new ArrayList<>();
    private final List<Button> rowButtons = new ArrayList<>();
    private final List<Button> quickButtons = new ArrayList<>();
    private String query = "";
    private Filter filter = Filter.ALL;
    private int page;
    private int rows;
    private int listTop;
    private int bottomY;
    private Button prev;
    private Button next;

    public KeyListScreen(Screen parent) {
        super(Component.translatable("bindisland.binds.title"));
        this.parent = parent;
    }

    private static List<KeyGroup> collect() {
        Map<String, KeyGroup> map = new LinkedHashMap<>();
        for (Binds.BindInfo b : Binds.all()) {
            if (!b.bound()) {
                continue;
            }
            String name = b.key().getString();
            map.computeIfAbsent(name.toLowerCase(Locale.ROOT), k -> new KeyGroup(name, b.key(), new ArrayList<>()))
                    .binds().add(b);
        }
        List<KeyGroup> out = new ArrayList<>(map.values());
        // single keys first (A, B, F5…), then combos and mouse buttons
        out.sort(Comparator.comparingInt((KeyGroup g) -> g.name().contains("+") ? 1 : 0)
                .thenComparingInt(g -> g.name().length())
                .thenComparing(g -> g.name().toLowerCase(Locale.ROOT)));
        return out;
    }

    @Override
    protected void init() {
        groups = collect();
        rowButtons.clear();
        quickButtons.clear();
        int w = Math.min(360, this.width - 20);
        int x = this.width / 2 - w / 2;

        EditBox search = new EditBox(this.font, x, 24, w - 124, 20, Component.translatable("bindisland.binds.search"));
        search.setHint(Component.translatable("bindisland.binds.search").withStyle(ChatFormatting.DARK_GRAY));
        search.setMaxLength(64);
        search.setValue(query);
        search.setResponder(s -> {
            query = s;
            page = 0;
            refresh();
        });
        addRenderableWidget(search);
        addRenderableWidget(CycleButton.<Filter>builder(
                        f -> Component.translatable("bindisland.binds.filter." + f.name().toLowerCase(Locale.ROOT)), filter)
                .withValues(Filter.values())
                .create(x + w - 120, 24, 120, 20, Component.translatable("bindisland.binds.filter"), (b, v) -> {
                    filter = v;
                    page = 0;
                    refresh();
                }));

        listTop = 50;
        bottomY = this.height - 50;
        rows = Math.max(3, (bottomY - listTop - 14) / ROW_GAP);
        for (int i = 0; i < rows; i++) {
            final int row = i;
            int y = listTop + i * ROW_GAP;
            rowButtons.add(addRenderableWidget(Button.builder(Component.empty(), b -> openRow(row))
                    .bounds(x, y, w - 24, ROW_H).build()));
            quickButtons.add(addRenderableWidget(Button.builder(Component.empty(), b -> quickToggle(row))
                    .bounds(x + w - 22, y, 22, ROW_H)
                    .tooltip(Tooltip.create(Component.translatable("bindisland.binds.quick.tooltip"))).build()));
        }

        int third = (w - 48) / 2;
        prev = addRenderableWidget(Button.builder(Component.literal("<"), b -> { page--; refresh(); })
                .bounds(x, bottomY, 20, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("bindisland.binds.show_all"), b -> {
                    BindConfig.showAll();
                    refresh();
                }).bounds(x + 24, bottomY, third, 20)
                .tooltip(Tooltip.create(Component.translatable("bindisland.binds.show_all.tooltip"))).build());
        addRenderableWidget(Button.builder(Component.translatable("bindisland.binds.reset"), b -> {
                    BindConfig.resetBinds();
                    refresh();
                }).bounds(x + 24 + third + 4, bottomY, third, 20)
                .tooltip(Tooltip.create(Component.translatable("bindisland.binds.reset.tooltip"))).build());
        next = addRenderableWidget(Button.builder(Component.literal(">"), b -> { page++; refresh(); })
                .bounds(x + w - 20, bottomY, 20, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(this.width / 2 - 100, bottomY + 24, 200, 20).build());
        refresh();
    }

    private void refresh() {
        filtered.clear();
        String q = query.trim().toLowerCase(Locale.ROOT);
        for (KeyGroup g : groups) {
            int shown = g.shown();
            if (filter == Filter.SHOWN && shown == 0 || filter == Filter.HIDDEN && shown == g.binds().size()) {
                continue;
            }
            if (!q.isEmpty() && !matches(g, q)) {
                continue;
            }
            filtered.add(g);
        }
        int pages = Math.max(1, (filtered.size() + rows - 1) / rows);
        page = Math.max(0, Math.min(page, pages - 1));
        for (int i = 0; i < rows; i++) {
            KeyGroup g = at(i);
            Button b = rowButtons.get(i);
            Button quick = quickButtons.get(i);
            b.visible = quick.visible = g != null;
            if (g != null) {
                b.setMessage(rowText(g));
                int shown = g.shown();
                quick.setMessage(shown == 0
                        ? Component.literal("✘").withStyle(ChatFormatting.DARK_GRAY)
                        : Component.literal("✔").withStyle(shown == g.binds().size() ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
            }
        }
        prev.active = page > 0;
        next.active = page < pages - 1;
    }

    private static boolean matches(KeyGroup g, String q) {
        if (g.name().toLowerCase(Locale.ROOT).contains(q)) {
            return true;
        }
        for (Binds.BindInfo b : g.binds()) {
            String text = (b.displayAction().getString() + " " + b.action().getString() + " " + b.source())
                    .toLowerCase(Locale.ROOT);
            if (text.contains(q)) {
                return true;
            }
        }
        return false;
    }

    private KeyGroup at(int row) {
        int idx = page * rows + row;
        return idx < filtered.size() ? filtered.get(idx) : null;
    }

    /** "[M]  Open main menu, Easy Place  +1" — shown binds in white, hidden ones in gray. */
    private static Component rowText(KeyGroup g) {
        MutableComponent text = Component.literal("[").withStyle(ChatFormatting.DARK_GRAY)
                .append(g.key().copy().withStyle(ChatFormatting.YELLOW))
                .append(Component.literal("]  ").withStyle(ChatFormatting.DARK_GRAY));
        int max = Math.min(3, g.binds().size());
        for (int i = 0; i < max; i++) {
            Binds.BindInfo b = g.binds().get(i);
            if (i > 0) {
                text.append(Component.literal(", ").withStyle(ChatFormatting.DARK_GRAY));
            }
            boolean hidden = BindConfig.isHidden(b.id());
            text.append(b.displayAction().copy().withStyle(hidden ? ChatFormatting.DARK_GRAY : ChatFormatting.WHITE));
        }
        if (g.binds().size() > max) {
            text.append(Component.literal("  +" + (g.binds().size() - max)).withStyle(ChatFormatting.GRAY));
        }
        return text;
    }

    private void openRow(int row) {
        KeyGroup g = at(row);
        if (g != null && this.minecraft != null) {
            this.minecraft.gui.setScreen(new BindListScreen(this, g.key(), g.binds()));
        }
    }

    /** ✔/✘ next to a key: hide everything on it, or show everything if it is all hidden. */
    private void quickToggle(int row) {
        KeyGroup g = at(row);
        if (g == null) {
            return;
        }
        boolean hide = g.shown() > 0;
        for (Binds.BindInfo b : g.binds()) {
            BindConfig.setHidden(b.id(), hide);
        }
        refresh();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);
        g.centeredText(this.font, this.title, this.width / 2, 8, 0xFFFFFFFF);
        int pages = Math.max(1, (filtered.size() + rows - 1) / rows);
        g.centeredText(this.font, Component.translatable("bindisland.binds.page", page + 1, pages, groups.size()),
                this.width / 2, bottomY - 11, 0xFFAAAAAA);
        if (filtered.isEmpty()) {
            g.centeredText(this.font, Component.translatable("bindisland.binds.empty"), this.width / 2, listTop + 20,
                    0xFF888888);
        }
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.gui.setScreen(parent);
        }
    }
}
