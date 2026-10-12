package com.nnpg.glazed.antivanish;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class FaceSkins {
    public record Skin(ResourceLocation texture, int width, int height, boolean pending) {
    }

    private static final HttpClient CLIENT = HttpClient.newHttpClient();
    private static final Map<String, Skin> FETCHED = new ConcurrentHashMap<>();
    private static final Set<String> LOADING = ConcurrentHashMap.newKeySet();

    private FaceSkins() {
    }

    public static Skin get(UUID id, String name) {
        Minecraft mc = Minecraft.getInstance();
        ClientPacketListener connection = mc.getConnection();

        if (connection != null) {
            PlayerInfo info = id == null ? null : connection.getPlayerInfo(id);

            if (info == null && name != null) {
                for (PlayerInfo entry : connection.getOnlinePlayers()) {
                    if (entry.getProfile().getName().equalsIgnoreCase(name)) {
                        info = entry;
                        break;
                    }
                }
            }

            if (info != null) return new Skin(info.getSkin().texture(), 64, 64, false);
        }

        if (name != null && !name.isBlank()) {
            String key = name.trim().toLowerCase(Locale.ROOT);
            Skin cached = FETCHED.get(key);
            if (cached != null) return cached;

            preload(name);
        }

        UUID fallbackId = id != null ? id : UUID.nameUUIDFromBytes(String.valueOf(name).getBytes(StandardCharsets.UTF_8));
        return new Skin(DefaultPlayerSkin.get(fallbackId).texture(), 64, 64, true);
    }

    public static void preload(String name) {
        if (name == null || name.isBlank()) return;

        String trimmed = name.trim();
        String key = trimmed.toLowerCase(Locale.ROOT);

        if (FETCHED.containsKey(key) || !LOADING.add(key)) return;

        HttpRequest request = HttpRequest.newBuilder(
                        URI.create("https://minotar.net/skin/" + URLEncoder.encode(trimmed, StandardCharsets.UTF_8))
                )
                .header("User-Agent", "Mozilla/5.0")
                .build();

        CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
                .thenAccept(response -> {
                    if (response.statusCode() != 200) {
                        LOADING.remove(key);
                        return;
                    }

                    byte[] data = response.body();

                    Minecraft.getInstance().execute(() -> {
                        try (ByteArrayInputStream input = new ByteArrayInputStream(data)) {
                            NativeImage image = NativeImage.read(input);
                            int width = image.getWidth();
                            int height = image.getHeight();

                            ResourceLocation id = ResourceLocation.fromNamespaceAndPath("glazed", "anti_vanish/skin_" + Integer.toHexString(key.hashCode()));
                            Minecraft.getInstance().getTextureManager().release(id);
                            Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(image));

                            FETCHED.put(key, new Skin(id, width, height, false));
                        } catch (Exception ignored) {
                        }

                        LOADING.remove(key);
                    });
                })
                .exceptionally(error -> {
                    LOADING.remove(key);
                    return null;
                });
    }

    public static boolean isReady(String name) {
        return name != null && FETCHED.containsKey(name.trim().toLowerCase(Locale.ROOT));
    }
}
