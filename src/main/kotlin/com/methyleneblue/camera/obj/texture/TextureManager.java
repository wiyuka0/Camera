package com.methyleneblue.camera.obj.texture;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class TextureManager {
    private static final ConcurrentHashMap<Material, EnumMap<BlockFace, BufferedImage>> textureBlockFaceCache = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Material, BufferedImage> textureCache = new ConcurrentHashMap<>();
    private static File texturesPath;

    private static final EnumMap<BlockFace, String> FACE_SUFFIXES = new EnumMap<>(Map.of(
            BlockFace.UP, "top",
            BlockFace.DOWN, "bottom",
            BlockFace.NORTH, "north",
            BlockFace.EAST, "east",
            BlockFace.SOUTH, "south",
            BlockFace.WEST, "west"
    ));

    static {
        initialize();
        preloadAllTextures();
    }

    private static void initialize() {
        texturesPath = new File(Bukkit.getPluginsFolder().getPath() + "\\Camera\\textures");

        if (!texturesPath.exists()) {
            // noinspection ResultOfMethodCallIgnored
            texturesPath.mkdirs();
        }
    }

    private static void preloadAllTextures() {
        for (Material material : Material.values()) {
            String namespaceKey = material.toString().toLowerCase(Locale.getDefault());

            String textureFileName = namespaceKey + ".png";
            File textureFile = new File(texturesPath, textureFileName);

            if (textureFile.exists()) {
                try {
                    cacheTexture(material, ImageIO.read(textureFile));
                    continue;
                } catch (IOException ignored) {}
            }
            for (Map.Entry<BlockFace, String> entry : FACE_SUFFIXES.entrySet()) {
                BlockFace blockFace = entry.getKey();
                String key = entry.getValue();

                textureFileName = namespaceKey + "_" + key + ".png";
                textureFile = new File(texturesPath, textureFileName);

                if (textureFile.exists()) {
                    try {
                        cacheTexture(material, blockFace, ImageIO.read(textureFile));
                        continue;
                    } catch (IOException ignored) {}
                }
                textureFileName = namespaceKey + "_side.png";
                textureFile = new File(texturesPath, textureFileName);

                if (textureFile.exists()) {
                    try {
                        cacheTexture(material, blockFace, ImageIO.read(textureFile));
                    } catch (IOException ignored) {}
                }
            }
        }
    }

    public static @Nullable BufferedImage getTexture(@NotNull Material material, @NotNull BlockFace face) {
        if (textureCache.containsKey(material)) return textureCache.get(material);
        if (textureBlockFaceCache.containsKey(material)) textureBlockFaceCache.get(material).get(face);
        return null;
    }

    private static void cacheTexture(@NotNull Material material, @NotNull BlockFace face, @NotNull BufferedImage image) {
        textureBlockFaceCache.computeIfAbsent(material, k -> new EnumMap<>(BlockFace.class)).put(face, image);
    }

    private static void cacheTexture(@NotNull Material material, @NotNull BufferedImage image) {
        textureCache.put(material, image);
    }

    @Contract("_, _, _, _, _, _ -> new")
    public static int @NotNull [] getTextureCoords(@NotNull BlockFace face, float hitX, float hitY, float hitZ, int width, int height) {
        float u, v;

        switch (face) {
            case NORTH -> {
                u = 1.0f - hitX;
                v = 1.0f - hitY;
            }
            case SOUTH -> {
                u = hitX;
                v = 1.0f - hitY;
            }
            case EAST -> {
                u = 1.0f - hitZ;
                v = 1.0f - hitY;
            }
            case WEST -> {
                u = hitZ;
                v = 1.0f - hitY;
            }
            case UP -> {
                u = hitX;
                v = 1.0f - hitZ;
            }
            case DOWN -> {
                u = hitX;
                v = hitZ;
            }
            default -> {
                return new int[] {0, 0};
            }
        }

        int x = (int) (u * (width - 1));
        int y = (int) (v * (height - 1));

        x = Math.clamp(x, 0, width - 1);
        y = Math.clamp(y, 0, height - 1);

        return new int[]{x, y};
    }
}