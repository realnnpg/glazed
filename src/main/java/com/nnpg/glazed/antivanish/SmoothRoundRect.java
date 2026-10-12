package com.nnpg.glazed.antivanish;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

public final class SmoothRoundRect {
    private static final int SUPER = 4;
    private static final float AA_GUI = 1.35f;
    private static final int MAX_CACHE = 48;
    private static final int CACHE_VER = 2;

    private static final Map<CacheKey, SmoothRoundRect.Entry> CACHE = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<CacheKey, SmoothRoundRect.Entry> eldest) {
            if (size() <= MAX_CACHE) return false;
            evict(eldest.getValue());
            return true;
        }
    };

    private SmoothRoundRect() {
    }

    public static void draw(GuiGraphics g, int x, int y, int w, int h, int radius, int argb) {
        Minecraft mc = Minecraft.getInstance();
        if (w <= 0 || h <= 0 || mc == null) return;

        int r = Math.max(2, Math.min(radius, Math.min(w, h) / 2));
        CacheKey key = new CacheKey(CACHE_VER, w, h, r, argb);
        Entry entry = CACHE.get(key);

        if (entry == null) {
            entry = create(key);
            if (entry != null) CACHE.put(key, entry);
        }

        if (entry == null || entry.id == null) {
            g.fill(x, y, x + w, y + h, argb);
            return;
        }

        g.blit(RenderPipelines.GUI_TEXTURED, entry.id, x, y, 0.0f, 0.0f, w, h, entry.texW, entry.texH, entry.texW, entry.texH);
    }

    public static void clear() {
        for (Entry entry : CACHE.values()) evict(entry);
        CACHE.clear();
    }

    private static void evict(Entry e) {
        if (e == null) return;

        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && e.id != null) mc.getTextureManager().release(e.id);
        } catch (Throwable ignored) {
        }

        try {
            if (e.texture != null) e.texture.close();
        } catch (Throwable ignored) {
        }
    }

    private static Entry create(CacheKey key) {
        try {
            int srcA = (key.argb >>> 24) & 0xFF;
            int srcR = (key.argb >>> 16) & 0xFF;
            int srcG = (key.argb >>> 8) & 0xFF;
            int srcB = key.argb & 0xFF;

            int tw = key.w * SUPER;
            int th = key.h * SUPER;
            float rw = key.w;
            float rh = key.h;
            float rr = key.r;

            NativeImage image = new NativeImage(tw, th, true);
            for (int py = 0; py < th; py++) {
                for (int px = 0; px < tw; px++) {
                    float gx = (px + 0.5f) / SUPER;
                    float gy = (py + 0.5f) / SUPER;
                    float sd = sdRoundRect(gx, gy, rw, rh, rr);
                    float cover = smoothstep(AA_GUI, -AA_GUI, sd);

                    if (cover <= 0.002f) {
                        image.setPixelABGR(px, py, 0);
                        continue;
                    }

                    int a = Math.max(0, Math.min(255, Math.round(srcA * cover)));
                    image.setPixelABGR(px, py, (a << 24) | (srcB << 16) | (srcG << 8) | srcR);
                }
            }

            String name = "rrv" + CACHE_VER + "_" + Integer.toHexString(key.hashCode());
            LinearDynamicTexture texture = new LinearDynamicTexture(() -> name, image);
            Identifier id = Identifier.fromNamespaceAndPath("glazed", "dynamic/" + name);
            Minecraft.getInstance().getTextureManager().register(id, texture);

            return new Entry(id, texture, tw, th);
        } catch (Throwable t) {
            return null;
        }
    }

    private static float sdRoundRect(float x, float y, float w, float h, float r) {
        float px = x - w * 0.5f;
        float py = y - h * 0.5f;
        float bx = w * 0.5f;
        float by = h * 0.5f;
        float qx = Math.abs(px) - bx + r;
        float qy = Math.abs(py) - by + r;
        float mx = Math.max(qx, 0f);
        float my = Math.max(qy, 0f);

        return (float) Math.sqrt(mx * mx + my * my) + Math.min(Math.max(qx, qy), 0f) - r;
    }

    private static float smoothstep(float edge0, float edge1, float x) {
        float t = Math.max(0f, Math.min(1f, (x - edge0) / (edge1 - edge0)));
        return t * t * (3f - 2f * t);
    }

    private static final class LinearDynamicTexture extends DynamicTexture {
        private LinearDynamicTexture(Supplier<String> name, NativeImage image) {
            super(name, image);
            this.sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        }
    }

    private record CacheKey(int ver, int w, int h, int r, int argb) {
    }

    private static final class Entry {
        private final Identifier id;
        private final DynamicTexture texture;
        private final int texW;
        private final int texH;

        private Entry(Identifier id, DynamicTexture texture, int texW, int texH) {
            this.id = id;
            this.texture = texture;
            this.texW = texW;
            this.texH = texH;
        }
    }
}
