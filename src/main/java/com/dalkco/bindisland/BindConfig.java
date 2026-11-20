package com.dalkco.bindisland;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Mth;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Settings and per-bind choices, saved to config/bindisland.json. */
public final class BindConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("bindisland.json");

    /** Hidden by default: keys that are held all the time and would flood the island. */
    public static final Set<String> DEFAULT_HIDDEN = Set.of(
            "km:key.forward", "km:key.left", "km:key.back", "km:key.right",
            "km:key.jump", "km:key.sneak", "km:key.sprint",
            "km:key.attack", "km:key.use",
            "km:key.hotbar.1", "km:key.hotbar.2", "km:key.hotbar.3", "km:key.hotbar.4", "km:key.hotbar.5",
            "km:key.hotbar.6", "km:key.hotbar.7", "km:key.hotbar.8", "km:key.hotbar.9");

    /** Accent colours: purple, blue, green, orange, pink, white. */
    public static final int[] ACCENTS = {0xFFB48CFF, 0xFF62B0FF, 0xFF62E08F, 0xFFFFAD52, 0xFFFF78BE, 0xFFEDEDED};

    public enum Position { TOP_CENTER, TOP_LEFT, TOP_RIGHT, BOTTOM_CENTER, CUSTOM }

    public enum Style { COMPACT, DETAILED }

    public static final class Data {
        public boolean enabled = true;
        public Position position = Position.TOP_CENTER;
        /** CUSTOM position (set by dragging the island in chat): centre X and top Y as screen fractions */
        public float customX = 0.5f;
        public float customY = 0.02f;
        public Style style = Style.COMPACT;
        /** distance from the screen edge, GUI pixels */
        public int offsetY = 3;
        /** percent */
        public int scale = 100;
        /** how long an entry stays, ms */
        public int durationMs = 1600;
        /** background opacity, percent */
        public int opacity = 85;
        public int accent = 0;
        /** how many recent presses are shown at once */
        public int stack = 1;
        public boolean showSource = true;
        public boolean showState = true;
        public boolean animations = true;
        public boolean combineRepeats = true;
        public boolean mouseButtons = false;
        public boolean inScreens = false;
        public boolean unboundKeys = false;
        public Set<String> hidden = new HashSet<>(DEFAULT_HIDDEN);
        public Map<String, String> labels = new HashMap<>();
    }

    private static Data data = new Data();

    private BindConfig() {
    }

    public static Data get() {
        return data;
    }

    public static int accent() {
        return ACCENTS[Mth.clamp(data.accent, 0, ACCENTS.length - 1)];
    }

    public static void load() {
        try {
            if (Files.exists(FILE)) {
                Data d = GSON.fromJson(Files.readString(FILE, StandardCharsets.UTF_8), Data.class);
                if (d != null) {
                    data = d;
                }
            }
        } catch (Exception e) {
            BindIslandClient.LOGGER.warn("Failed to read {}", FILE, e);
        }
        normalize();
    }

    private static void normalize() {
        Data d = data;
        if (d.position == null) {
            d.position = Position.TOP_CENTER;
        }
        if (d.style == null) {
            d.style = Style.COMPACT;
        }
        if (d.hidden == null) {
            d.hidden = new HashSet<>(DEFAULT_HIDDEN);
        } else {
            d.hidden = new HashSet<>(d.hidden);
        }
        if (d.labels == null) {
            d.labels = new HashMap<>();
        } else {
            d.labels = new HashMap<>(d.labels);
        }
        d.customX = Mth.clamp(d.customX, 0f, 1f);
        d.customY = Mth.clamp(d.customY, 0f, 1f);
        d.offsetY = Mth.clamp(d.offsetY, 0, 120);
        d.scale = Mth.clamp(d.scale, 50, 200);
        d.durationMs = Mth.clamp(d.durationMs, 500, 6000);
        d.opacity = Mth.clamp(d.opacity, 0, 100);
        d.accent = Mth.clamp(d.accent, 0, ACCENTS.length - 1);
        d.stack = Mth.clamp(d.stack, 1, 5);
    }

    public static void save() {
        normalize();
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, GSON.toJson(data), StandardCharsets.UTF_8);
        } catch (Exception e) {
            BindIslandClient.LOGGER.warn("Failed to write {}", FILE, e);
        }
    }

    public static boolean isHidden(String id) {
        return data.hidden.contains(id);
    }

    public static void setHidden(String id, boolean hidden) {
        if (hidden) {
            data.hidden.add(id);
        } else {
            data.hidden.remove(id);
        }
        save();
    }

    /** Custom text for a bind, or null. */
    public static String label(String id) {
        String l = data.labels.get(id);
        return l == null || l.isBlank() ? null : l;
    }

    public static void setLabel(String id, String label) {
        if (label == null || label.isBlank()) {
            data.labels.remove(id);
        } else {
            data.labels.put(id, label.trim());
        }
        save();
    }

    public static void showAll() {
        data.hidden.clear();
        save();
    }

    public static void resetBinds() {
        data.hidden = new HashSet<>(DEFAULT_HIDDEN);
        data.labels.clear();
        save();
    }

    public static void resetSettings() {
        Data fresh = new Data();
        fresh.hidden = data.hidden;
        fresh.labels = data.labels;
        fresh.enabled = data.enabled;
        data = fresh;
        save();
    }
}
