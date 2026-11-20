package com.dalkco.bindisland;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * The island: a pill at the edge of the screen that shows the last pressed key and every bind it triggered.
 * Drawn by the HUD in game and on top of the screen while a menu (or the chat) is open.
 */
public final class IslandHud {
    public static final int PAD = 6;
    private static final int LINE = 11;
    private static final int MAX_ACTION_W = 220;
    private static final int MAX_BINDS = 8;
    private static final int APPEAR_MS = 140;
    private static final int FADE_MS = 260;
    private static final int GRAY = 0xFF9A9AA6;
    private static final int BG = 0x101016;

    /** Island bounds in GUI coordinates. */
    public record Bounds(int x1, int y1, int x2, int y2) {
        public boolean contains(double x, double y) {
            return x >= x1 && x < x2 && y >= y1 && y < y2;
        }
    }

    /** What one island shows: the key, the binds it triggered and how many times it was pressed. */
    public record View(Component key, List<Binds.BindInfo> binds, int count, boolean self) {
    }

    /** One line of the island; {@code keyRow} lines start with the key cap. */
    private record Row(boolean keyRow, String text, int color, String suffix, String inline, Component state) {
    }

    private static final class Entry {
        final String id;
        final List<Binds.BindInfo> binds;
        final Component key;
        final long start;
        final boolean forced;
        long last;
        int count = 1;
        float animW = -1;

        Entry(String id, List<Binds.BindInfo> binds, Component key, long now, boolean forced) {
            this.id = id;
            this.binds = binds;
            this.key = key;
            this.start = now;
            this.last = now;
            this.forced = forced;
        }

        View view() {
            return new View(key, binds, count, id.startsWith("bindisland:"));
        }
    }

    private static final Deque<Entry> entries = new ArrayDeque<>();
    private static long lastFrame;
    private static Bounds anchor;

    // dragging in chat
    private static boolean dragging;
    private static double dragDx;
    private static double dragDy;

    private IslandHud() {
    }

    // ---------------------------------------------------------------- input

    /** A key was pressed: show every visible bind it triggered. */
    public static void push(List<Binds.BindInfo> matched, Component keyName, String keyId, boolean allowUnbound) {
        BindConfig.Data c = BindConfig.get();
        if (!c.enabled) {
            return;
        }
        List<Binds.BindInfo> visible = new ArrayList<>();
        for (Binds.BindInfo b : matched) {
            if (!BindConfig.isHidden(b.id())) {
                visible.add(b);
            }
        }
        if (matched.isEmpty() && allowUnbound && c.unboundKeys && !BindConfig.isHidden(keyId)) {
            visible.add(new Binds.BindInfo(keyId, Component.translatable("bindisland.no_action"), "", keyName, true, null));
        }
        if (!visible.isEmpty()) {
            add(keyId, visible, keyName, false);
        }
    }

    /** Shown even when the island is off (used for the on/off toggle itself). */
    public static void flashSelf() {
        add("bindisland:self", List.of(selfInfo()), null, true);
    }

    public static Binds.BindInfo selfInfo() {
        return new Binds.BindInfo("bindisland:self", Component.translatable("bindisland.name"), "", null, true,
                () -> Binds.onOff(BindConfig.get().enabled));
    }

    private static void add(String id, List<Binds.BindInfo> binds, Component key, boolean forced) {
        BindConfig.Data c = BindConfig.get();
        long now = System.currentTimeMillis();
        Entry top = entries.peekFirst();
        if (top != null && top.id.equals(id) && !expired(top, now, c)) {
            if (c.combineRepeats) {
                top.count++;
            }
            top.last = now;
            return;
        }
        entries.addFirst(new Entry(id, binds, key, now, forced));
        while (entries.size() > Math.max(1, c.stack)) {
            entries.removeLast();
        }
    }

    private static boolean expired(Entry e, long now, BindConfig.Data c) {
        return now > e.last + c.durationMs + (c.animations ? FADE_MS : 0);
    }

    private static float alpha(Entry e, long now, BindConfig.Data c) {
        float appear = c.animations ? Math.min(1f, (now - e.start) / (float) APPEAR_MS) : 1f;
        long end = e.last + c.durationMs;
        float fade = now <= end ? 1f : (c.animations ? 1f - (now - end) / (float) FADE_MS : 0f);
        return Math.max(0f, Math.min(appear, fade));
    }

    /** Where the chat buttons attach (null when nothing is drawn). */
    public static Bounds anchor() {
        return anchor;
    }

    // ---------------------------------------------------------------- dragging (chat only)

    public static boolean startDrag(double mouseX, double mouseY) {
        Bounds b = anchor;
        if (b == null || !b.contains(mouseX, mouseY)) {
            return false;
        }
        dragging = true;
        dragDx = mouseX - (b.x1() + b.x2()) / 2.0;
        dragDy = mouseY - b.y1();
        return true;
    }

    public static boolean drag(double mouseX, double mouseY, int guiWidth, int guiHeight) {
        if (!dragging) {
            return false;
        }
        BindConfig.Data c = BindConfig.get();
        c.position = BindConfig.Position.CUSTOM;
        c.customX = (float) Math.max(0, Math.min(1, (mouseX - dragDx) / guiWidth));
        c.customY = (float) Math.max(0, Math.min(1, (mouseY - dragDy) / guiHeight));
        return true;
    }

    public static boolean stopDrag() {
        if (!dragging) {
            return false;
        }
        dragging = false;
        BindConfig.save();
        return true;
    }

    // ---------------------------------------------------------------- drawing entry points

    /** HUD layer: only while no screen is open (screens get the island drawn on top, see below). */
    public static void renderHud(GuiGraphicsExtractor g, DeltaTracker delta) {
        if (Minecraft.getInstance().gui.screen() == null) {
            draw(g, false, -1, -1);
        }
    }

    /** After a screen is drawn: the chat always (placeholder + drag), other menus when "in menus" is on. */
    public static void renderOverScreen(Screen screen, GuiGraphicsExtractor g, int mouseX, int mouseY) {
        if (screen instanceof ChatScreen) {
            draw(g, true, mouseX, mouseY);
        } else if (BindConfig.get().inScreens && !(screen instanceof BindIslandConfigScreen)
                && !(screen instanceof BindListScreen) && !(screen instanceof KeyListScreen)
                && !(screen instanceof BindEditScreen)) {
            draw(g, false, -1, -1);
        }
    }

    private static void draw(GuiGraphicsExtractor g, boolean chat, int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getInstance();
        BindConfig.Data c = BindConfig.get();
        long now = System.currentTimeMillis();
        float dt = lastFrame == 0 ? 16f : Math.min(100f, now - lastFrame);
        lastFrame = now;

        entries.removeIf(e -> expired(e, now, c) || (!c.enabled && !e.forced));
        anchor = null;
        if (entries.isEmpty() && !chat) {
            return;
        }
        Font font = mc.font;
        float s = c.scale / 100f;
        int sw = Math.round(g.guiWidth() / s);
        int sh = Math.round(g.guiHeight() / s);

        // what to draw, newest first
        List<View> views = new ArrayList<>();
        List<Float> alphas = new ArrayList<>();
        List<Entry> owners = new ArrayList<>();
        if (entries.isEmpty()) {
            // chat is open and nothing was pressed: a placeholder so the switch, the gear and dragging have a home
            views.add(new View(null, List.of(selfInfo()), 1, true));
            alphas.add(c.enabled ? 1f : 0.7f);
            owners.add(null);
        } else {
            int i = 0;
            for (Entry e : entries) {
                views.add(e.view());
                alphas.add(alpha(e, now, c) * (i == 0 ? 1f : 0.7f));
                owners.add(e);
                i++;
            }
        }

        boolean up = c.position == BindConfig.Position.BOTTOM_CENTER
                || c.position == BindConfig.Position.CUSTOM && c.customY > 0.6f;
        g.pose().pushMatrix();
        g.pose().scale(s, s);
        int y = 0;
        for (int i = 0; i < views.size(); i++) {
            View v = views.get(i);
            int target = measure(font, v, c);
            int h = height(font, v, c);
            Entry e = owners.get(i);
            int w = target;
            if (e != null) {
                if (e.animW < 0) {
                    e.animW = c.animations ? target * 0.55f : target;
                }
                e.animW = c.animations ? e.animW + (target - e.animW) * Math.min(1f, dt / 70f) : target;
                w = Math.round(e.animW);
            }
            if (i == 0) {
                y = firstY(c, sw, sh, h);
            } else if (up) {
                y -= h + 3;
            }
            int x = xFor(c, sw, w);
            paint(g, font, x, y, w, target, v, alphas.get(i), c);
            if (i == 0) {
                anchor = new Bounds(Math.round(x * s), Math.round(y * s), Math.round((x + w) * s), Math.round((y + h) * s));
            }
            if (!up) {
                y += h + 3;
            }
        }
        g.pose().popMatrix();

        if (chat && anchor != null && (dragging || anchor.contains(mouseX, mouseY))) {
            Bounds b = anchor;
            g.outline(b.x1() - 1, b.y1() - 1, b.x2() - b.x1() + 2, b.y2() - b.y1() + 2, BindConfig.accent());
            Component hint = Component.translatable("bindisland.chat.drag");
            int hw = font.width(hint);
            int hx = Math.max(2, Math.min(g.guiWidth() - hw - 2, (b.x1() + b.x2()) / 2 - hw / 2));
            int hy = b.y2() + 3 + 9 < g.guiHeight() ? b.y2() + 3 : b.y1() - 12;
            g.text(font, hint, hx, hy, 0xFFDDDDDD, true);
        }
    }

    private static int firstY(BindConfig.Data c, int sw, int sh, int h) {
        float s = c.scale / 100f;
        return switch (c.position) {
            case BOTTOM_CENTER -> sh - Math.round((c.offsetY + 40) / s) - h;
            case CUSTOM -> Math.max(1, Math.min(sh - h - 1, Math.round(c.customY * sh)));
            default -> Math.round(c.offsetY / s);
        };
    }

    private static int xFor(BindConfig.Data c, int sw, int w) {
        return switch (c.position) {
            case TOP_LEFT -> 4;
            case TOP_RIGHT -> sw - w - 4;
            case CUSTOM -> Math.max(1, Math.min(sw - w - 1, Math.round(c.customX * sw - w / 2f)));
            default -> sw / 2 - w / 2;
        };
    }

    // ---------------------------------------------------------------- layout (also used by the previews)

    private static String trim(Font font, String s, int max) {
        if (font.width(s) <= max) {
            return s;
        }
        return font.plainSubstrByWidth(s, max - font.width("…")) + "…";
    }

    private static List<Row> rows(Font font, View v, BindConfig.Data c) {
        boolean detailed = c.style == BindConfig.Style.DETAILED;
        List<Row> rows = new ArrayList<>();
        int n = Math.min(v.binds().size(), MAX_BINDS);
        for (int i = 0; i < n; i++) {
            Binds.BindInfo info = v.binds().get(i);
            String action = trim(font, info.displayAction().getString(), MAX_ACTION_W);
            String suffix = i == 0 && v.count() > 1 ? " ×" + v.count() : "";
            String src = c.showSource && info.source() != null && !info.source().isEmpty() ? info.source() : null;
            Component state = info.state() != null && (c.showState || v.self()) ? info.state().get() : null;
            if (detailed) {
                rows.add(new Row(true, action, 0xFFFFFFFF, suffix, null, state));
                if (src != null) {
                    rows.add(new Row(false, src, GRAY, "", null, null));
                }
            } else {
                // "[A] Open menu : Minecraft"
                rows.add(new Row(true, action, 0xFFFFFFFF, suffix, src == null ? null : " : " + src, state));
            }
        }
        if (v.binds().size() > n) {
            rows.add(new Row(false, Component.translatable("bindisland.more", v.binds().size() - n).getString(), GRAY,
                    "", null, null));
        }
        return rows;
    }

    private static int keyWidth(Font font, View v) {
        return v.key() == null ? 0 : font.width(v.key()) + 6 + 5;
    }

    private static int rowWidth(Font font, Row r) {
        int w = font.width(r.text()) + font.width(r.suffix());
        if (r.inline() != null) {
            w += font.width(r.inline());
        }
        if (r.state() != null) {
            w += font.width(r.state()) + 6 + 5;
        }
        return w;
    }

    /** Full width of the island. */
    public static int measure(Font font, View v, BindConfig.Data c) {
        int max = 0;
        for (Row r : rows(font, v, c)) {
            max = Math.max(max, rowWidth(font, r));
        }
        return PAD * 2 + keyWidth(font, v) + max;
    }

    public static int height(Font font, View v, BindConfig.Data c) {
        return 4 + Math.max(1, rows(font, v, c).size()) * LINE;
    }

    /** Draws the island at (x, y) with width w; {@code target} is the full width (w is smaller while growing). */
    public static void paint(GuiGraphicsExtractor g, Font font, int x, int y, int w, int target, View v, float alpha,
                             BindConfig.Data c) {
        List<Row> rows = rows(font, v, c);
        int h = 4 + Math.max(1, rows.size()) * LINE;
        int bgAlpha = Math.round(235 * (c.opacity / 100f) * alpha);
        round(g, x, y, w, h, Math.min(8, 7), (bgAlpha << 24) | BG);
        int accent = BindConfig.accent();
        if (c.opacity > 0) {
            round(g, x + 5, y + h - 1, Math.max(0, w - 10), 1, 0, withAlpha(accent, alpha * 0.55f));
        }

        float ca = alpha * Math.max(0f, Math.min(1f, (w - target * 0.6f) / (target * 0.4f)));
        if (ca < 0.04f) {
            return;
        }
        int left = x + PAD + Math.max(0, (w - target) / 2);
        int kw = keyWidth(font, v);
        int ty = y + 4;
        for (Row r : rows) {
            if (r.keyRow() && v.key() != null) {
                // every bind line starts with its key, so several binds on one key read as a list
                round(g, left, ty - 2, kw - 5, 10, 2, withAlpha(accent, ca));
                g.text(font, v.key(), left + 3, ty, withAlpha(0xFF101016, ca), false);
            }
            int cx = left + kw;
            g.text(font, r.text(), cx, ty, withAlpha(r.color(), ca), false);
            cx += font.width(r.text());
            if (!r.suffix().isEmpty()) {
                g.text(font, r.suffix(), cx, ty, withAlpha(accent, ca), false);
                cx += font.width(r.suffix());
            }
            if (r.inline() != null) {
                g.text(font, r.inline(), cx, ty, withAlpha(GRAY, ca), false);
                cx += font.width(r.inline());
            }
            if (r.state() != null) {
                cx += 5;
                int stw = font.width(r.state()) + 6;
                round(g, cx, ty - 2, stw, 11, 2, withAlpha(0x30FFFFFF, ca));
                g.text(font, r.state(), cx + 3, ty, withAlpha(0xFFFFFFFF, ca), false);
            }
            ty += LINE;
        }
    }

    private static int withAlpha(int argb, float alpha) {
        int a = Math.round(((argb >>> 24) & 0xFF) * Math.max(0f, Math.min(1f, alpha)));
        return (a << 24) | (argb & 0xFFFFFF);
    }

    /** Filled rectangle with rounded corners. */
    public static void round(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int color) {
        if (w <= 0 || h <= 0 || (color >>> 24) == 0) {
            return;
        }
        r = Math.min(r, Math.min(w, h) / 2);
        if (r <= 0) {
            g.fill(x, y, x + w, y + h, color);
            return;
        }
        g.fill(x, y + r, x + w, y + h - r, color);
        for (int i = 0; i < r; i++) {
            double dy = r - i - 0.5;
            int inset = (int) Math.round(r - Math.sqrt(r * r - dy * dy));
            g.fill(x + inset, y + i, x + w - inset, y + i + 1, color);
            g.fill(x + inset, y + h - 1 - i, x + w - inset, y + h - i, color);
        }
    }
}
