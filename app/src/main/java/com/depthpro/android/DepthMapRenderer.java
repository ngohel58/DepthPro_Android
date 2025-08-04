package com.depthpro.android;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.util.Log;

public class DepthMapRenderer {
    private static final String TAG = "DepthMapRenderer";

    // Color map types
    public enum ColorMap {
        GRAYSCALE,
        JET,
        VIRIDIS,
        PLASMA,
        INFERNO
    }

    private ColorMap currentColorMap = ColorMap.JET;

    public Bitmap renderDepthMap(float[][] depthMap, int targetWidth, int targetHeight) {
        return renderDepthMap(depthMap, targetWidth, targetHeight, currentColorMap);
    }

    public Bitmap renderDepthMap(float[][] depthMap, int targetWidth, int targetHeight, ColorMap colorMap) {
        int mapHeight = depthMap.length;
        int mapWidth = depthMap[0].length;

        Log.d(TAG, String.format("Rendering depth map: %dx%d -> %dx%d",
                mapWidth, mapHeight, targetWidth, targetHeight));

        // Create bitmap with target dimensions
        Bitmap bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888);
        int[] pixels = new int[targetWidth * targetHeight];

        // Calculate scaling factors
        float scaleX = (float) mapWidth / targetWidth;
        float scaleY = (float) mapHeight / targetHeight;

        // Render pixels
        for (int y = 0; y < targetHeight; y++) {
            for (int x = 0; x < targetWidth; x++) {
                // Map target coordinates to depth map coordinates
                int mapX = Math.min((int) (x * scaleX), mapWidth - 1);
                int mapY = Math.min((int) (y * scaleY), mapHeight - 1);

                float depth = depthMap[mapY][mapX];
                int color = applyColorMap(depth, colorMap);

                pixels[y * targetWidth + x] = color;
            }
        }

        bitmap.setPixels(pixels, 0, targetWidth, 0, 0, targetWidth, targetHeight);

        Log.d(TAG, "Depth map rendering completed");
        return bitmap;
    }

    private int applyColorMap(float normalizedDepth, ColorMap colorMap) {
        // Clamp to [0, 1] range
        normalizedDepth = Math.max(0.0f, Math.min(1.0f, normalizedDepth));

        switch (colorMap) {
            case GRAYSCALE:
                return applyGrayscale(normalizedDepth);
            case JET:
                return applyJet(normalizedDepth);
            case VIRIDIS:
                return applyViridis(normalizedDepth);
            case PLASMA:
                return applyPlasma(normalizedDepth);
            case INFERNO:
                return applyInferno(normalizedDepth);
            default:
                return applyJet(normalizedDepth);
        }
    }

    private int applyGrayscale(float value) {
        int gray = (int) (value * 255);
        return Color.rgb(gray, gray, gray);
    }

    private int applyJet(float value) {
        // Classic Jet colormap (blue -> cyan -> yellow -> red)
        float r, g, b;

        if (value < 0.25f) {
            r = 0.0f;
            g = 4.0f * value;
            b = 1.0f;
        } else if (value < 0.5f) {
            r = 0.0f;
            g = 1.0f;
            b = 1.0f - 4.0f * (value - 0.25f);
        } else if (value < 0.75f) {
            r = 4.0f * (value - 0.5f);
            g = 1.0f;
            b = 0.0f;
        } else {
            r = 1.0f;
            g = 1.0f - 4.0f * (value - 0.75f);
            b = 0.0f;
        }

        return Color.rgb(
                (int) (r * 255),
                (int) (g * 255),
                (int) (b * 255)
        );
    }

    private int applyViridis(float value) {
        // Viridis colormap approximation
        float r, g, b;

        if (value < 0.25f) {
            float t = value / 0.25f;
            r = 0.267f * t + 0.004f;
            g = 0.005f + 0.222f * t;
            b = 0.329f + 0.344f * t;
        } else if (value < 0.5f) {
            float t = (value - 0.25f) / 0.25f;
            r = 0.267f + 0.097f * t;
            g = 0.227f + 0.319f * t;
            b = 0.673f + 0.047f * t;
        } else if (value < 0.75f) {
            float t = (value - 0.5f) / 0.25f;
            r = 0.364f + 0.373f * t;
            g = 0.546f + 0.290f * t;
            b = 0.720f - 0.204f * t;
        } else {
            float t = (value - 0.75f) / 0.25f;
            r = 0.737f + 0.216f * t;
            g = 0.836f + 0.122f * t;
            b = 0.516f - 0.207f * t;
        }

        return Color.rgb(
                (int) (r * 255),
                (int) (g * 255),
                (int) (b * 255)
        );
    }

    private int applyPlasma(float value) {
        // Plasma colormap approximation
        float r, g, b;

        if (value < 0.25f) {
            float t = value / 0.25f;
            r = 0.050f + 0.298f * t;
            g = 0.029f + 0.076f * t;
            b = 0.527f + 0.135f * t;
        } else if (value < 0.5f) {
            float t = (value - 0.25f) / 0.25f;
            r = 0.348f + 0.252f * t;
            g = 0.105f + 0.163f * t;
            b = 0.662f + 0.043f * t;
        } else if (value < 0.75f) {
            float t = (value - 0.5f) / 0.25f;
            r = 0.600f + 0.239f * t;
            g = 0.268f + 0.329f * t;
            b = 0.705f - 0.149f * t;
        } else {
            float t = (value - 0.75f) / 0.25f;
            r = 0.839f + 0.101f * t;
            g = 0.597f + 0.312f * t;
            b = 0.556f - 0.168f * t;
        }

        return Color.rgb(
                (int) (r * 255),
                (int) (g * 255),
                (int) (b * 255)
        );
    }

    private int applyInferno(float value) {
        // Inferno colormap approximation
        float r, g, b;

        if (value < 0.25f) {
            float t = value / 0.25f;
            r = 0.001f + 0.258f * t;
            g = 0.004f + 0.024f * t;
            b = 0.013f + 0.100f * t;
        } else if (value < 0.5f) {
            float t = (value - 0.25f) / 0.25f;
            r = 0.259f + 0.340f * t;
            g = 0.028f + 0.121f * t;
            b = 0.113f + 0.113f * t;
        } else if (value < 0.75f) {
            float t = (value - 0.5f) / 0.25f;
            r = 0.599f + 0.258f * t;
            g = 0.149f + 0.364f * t;
            b = 0.226f - 0.019f * t;
        } else {
            float t = (value - 0.75f) / 0.25f;
            r = 0.857f + 0.119f * t;
            g = 0.513f + 0.415f * t;
            b = 0.207f + 0.571f * t;
        }

        return Color.rgb(
                (int) (r * 255),
                (int) (g * 255),
                (int) (b * 255)
        );
    }

    public Bitmap createColorBar(int width, int height, ColorMap colorMap) {
        Bitmap colorBar = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        int[] pixels = new int[width * height];

        for (int y = 0; y < height; y++) {
            // Map y coordinate to depth value (0 to 1)
            float value = (float) y / (height - 1);
            int color = applyColorMap(value, colorMap);

            // Fill entire row with same color
            for (int x = 0; x < width; x++) {
                pixels[y * width + x] = color;
            }
        }

        colorBar.setPixels(pixels, 0, width, 0, 0, width, height);
        return colorBar;
    }

    public void setColorMap(ColorMap colorMap) {
        this.currentColorMap = colorMap;
        Log.d(TAG, "Color map changed to: " + colorMap.name());
    }

    public ColorMap getCurrentColorMap() {
        return currentColorMap;
    }

    public Bitmap enhanceDepthMap(Bitmap depthMap, float contrast, float brightness) {
        if (depthMap == null) return null;

        int width = depthMap.getWidth();
        int height = depthMap.getHeight();

        Bitmap enhanced = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        int[] pixels = new int[width * height];
        depthMap.getPixels(pixels, 0, width, 0, 0, width, height);

        for (int i = 0; i < pixels.length; i++) {
            int pixel = pixels[i];
            int r = Color.red(pixel);
            int g = Color.green(pixel);
            int b = Color.blue(pixel);

            // Apply contrast and brightness
            r = Math.max(0, Math.min(255, (int) ((r - 128) * contrast + 128 + brightness)));
            g = Math.max(0, Math.min(255, (int) ((g - 128) * contrast + 128 + brightness)));
            b = Math.max(0, Math.min(255, (int) ((b - 128) * contrast + 128 + brightness)));

            pixels[i] = Color.rgb(r, g, b);
        }

        enhanced.setPixels(pixels, 0, width, 0, 0, width, height);
        return enhanced;
    }
}