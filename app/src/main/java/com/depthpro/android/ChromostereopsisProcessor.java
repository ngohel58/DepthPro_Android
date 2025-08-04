package com.depthpro.android;

import android.graphics.Bitmap;
import android.graphics.Color;

public class ChromostereopsisProcessor {
    public static class EffectParams {
        public float threshold = 50f;
        public float depthScale = 50f;
        public float feather = 10f;
        public float redBrightness = 50f;
        public float blueBrightness = 50f;
        public float gamma = 50f;
        public float blackLevel = 0f;
        public float whiteLevel = 100f;
        public float smoothing = 0f;
    }

    public Bitmap applyEffect(Bitmap original, float[][] depthMap, EffectParams params) {
        if (original == null || depthMap == null) return null;
        int width = original.getWidth();
        int height = original.getHeight();

        // Prepare grayscale image
        int[] pixels = new int[width * height];
        original.getPixels(pixels, 0, width, 0, 0, width, height);
        float[] gray = new float[pixels.length];
        for (int i = 0; i < pixels.length; i++) {
            int c = pixels[i];
            float r = Color.red(c);
            float g = Color.green(c);
            float b = Color.blue(c);
            gray[i] = (0.299f * r + 0.587f * g + 0.114f * b); // 0-255
        }

        // Levels adjustment
        float black = params.blackLevel * 2.55f;
        float white = params.whiteLevel * 2.55f;
        float denom = Math.max(white - black, 1e-6f);
        for (int i = 0; i < gray.length; i++) {
            float v = (gray[i] - black) / denom;
            gray[i] = clamp01(v);
        }

        // Gamma correction
        float gammaVal = 0.1f + (params.gamma / 100f) * 2.9f;
        for (int i = 0; i < gray.length; i++) {
            gray[i] = (float) Math.pow(gray[i], gammaVal);
        }

        // Smooth depth map
        float[][] smoothed = preprocessDepth(depthMap, (int) (params.smoothing / 10f));

        int mapH = smoothed.length;
        int mapW = smoothed[0].length;
        float scaleX = (float) mapW / width;
        float scaleY = (float) mapH / height;

        // Prepare output
        Bitmap output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        int[] outPixels = new int[pixels.length];

        float thresholdNorm = params.threshold / 100f;
        float steepness = Math.max(params.depthScale, 1e-3f);
        float featherNorm = params.feather / 100f;
        float steepnessAdj = steepness / (featherNorm * 10f + 1f);

        float redFactor = params.redBrightness / 50f;
        float blueFactor = params.blueBrightness / 50f;

        for (int y = 0; y < height; y++) {
            int mapY = Math.min((int) (y * scaleY), mapH - 1);
            for (int x = 0; x < width; x++) {
                int mapX = Math.min((int) (x * scaleX), mapW - 1);
                float depth = smoothed[mapY][mapX];
                float blend = 1f / (1f + (float) Math.exp(-steepnessAdj * (depth - thresholdNorm)));
                float gVal = gray[y * width + x];
                float rOut = redFactor * gVal * blend;
                float bOut = blueFactor * gVal * (1f - blend);
                outPixels[y * width + x] = Color.rgb(
                        clamp255(rOut * 255f),
                        0,
                        clamp255(bOut * 255f)
                );
            }
        }

        output.setPixels(outPixels, 0, width, 0, 0, width, height);
        return output;
    }

    private float[][] preprocessDepth(float[][] depth, int radius) {
        if (depth == null || radius <= 0) return depth;
        int h = depth.length;
        int w = depth[0].length;
        float[][] out = new float[h][w];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                float sum = 0f;
                int count = 0;
                for (int dy = -radius; dy <= radius; dy++) {
                    int ny = y + dy;
                    if (ny < 0 || ny >= h) continue;
                    for (int dx = -radius; dx <= radius; dx++) {
                        int nx = x + dx;
                        if (nx < 0 || nx >= w) continue;
                        sum += depth[ny][nx];
                        count++;
                    }
                }
                out[y][x] = count > 0 ? sum / count : depth[y][x];
            }
        }
        return out;
    }

    private float clamp01(float v) {
        if (v < 0f) return 0f;
        if (v > 1f) return 1f;
        return v;
    }

    private int clamp255(float v) {
        int iv = Math.round(v);
        if (iv < 0) return 0;
        if (iv > 255) return 255;
        return iv;
    }
}
