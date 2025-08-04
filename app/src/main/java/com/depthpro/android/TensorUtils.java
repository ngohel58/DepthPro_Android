package com.depthpro.android;

import android.util.Log;
import java.util.Arrays;

public class TensorUtils {
    private static final String TAG = "TensorUtils";

    public static float[][][][] convertToTensorFormat(float[][][] imageData) {
        int height = imageData.length;
        int width = imageData[0].length;
        int channels = imageData[0][0].length;

        float[][][][] tensorData = new float[1][channels][height][width];

        for (int c = 0; c < channels; c++) {
            for (int h = 0; h < height; h++) {
                for (int w = 0; w < width; w++) {
                    tensorData[0][c][h][w] = imageData[h][w][c];
                }
            }
        }

        Log.d(TAG, String.format("Converted to tensor format: [1, %d, %d, %d]", channels, height, width));
        return tensorData;
    }

    public static float[] flattenTensor(float[][][][] tensor) {
        int batch = tensor.length;
        int channels = tensor[0].length;
        int height = tensor[0][0].length;
        int width = tensor[0][0][0].length;

        float[] flattened = new float[batch * channels * height * width];
        int index = 0;

        for (int b = 0; b < batch; b++) {
            for (int c = 0; c < channels; c++) {
                for (int h = 0; h < height; h++) {
                    for (int w = 0; w < width; w++) {
                        flattened[index++] = tensor[b][c][h][w];
                    }
                }
            }
        }

        return flattened;
    }

    public static float[][] reshapeTo2D(float[] data, int height, int width) {
        if (data.length != height * width) {
            throw new IllegalArgumentException(
                    String.format("Data length %d doesn't match dimensions %dx%d",
                            data.length, height, width));
        }

        float[][] reshaped = new float[height][width];
        for (int h = 0; h < height; h++) {
            for (int w = 0; w < width; w++) {
                reshaped[h][w] = data[h * width + w];
            }
        }

        return reshaped;
    }

    public static void normalizeArray(float[] array) {
        float min = Float.MAX_VALUE;
        float max = Float.MIN_VALUE;

        // Find min and max
        for (float value : array) {
            if (!Float.isInfinite(value) && !Float.isNaN(value)) {
                min = Math.min(min, value);
                max = Math.max(max, value);
            }
        }

        // Normalize to [0, 1]
        float range = max - min;
        if (range > 0) {
            for (int i = 0; i < array.length; i++) {
                if (!Float.isInfinite(array[i]) && !Float.isNaN(array[i])) {
                    array[i] = (array[i] - min) / range;
                } else {
                    array[i] = 0.0f;
                }
            }
        }

        Log.d(TAG, String.format("Normalized array: min=%.3f, max=%.3f", min, max));
    }

    public static void normalize2DArray(float[][] array) {
        float min = Float.MAX_VALUE;
        float max = Float.MIN_VALUE;

        // Find min and max
        for (float[] row : array) {
            for (float value : row) {
                if (!Float.isInfinite(value) && !Float.isNaN(value)) {
                    min = Math.min(min, value);
                    max = Math.max(max, value);
                }
            }
        }

        // Normalize to [0, 1]
        float range = max - min;
        if (range > 0) {
            for (int i = 0; i < array.length; i++) {
                for (int j = 0; j < array[i].length; j++) {
                    if (!Float.isInfinite(array[i][j]) && !Float.isNaN(array[i][j])) {
                        array[i][j] = (array[i][j] - min) / range;
                    } else {
                        array[i][j] = 0.0f;
                    }
                }
            }
        }

        Log.d(TAG, String.format("Normalized 2D array: min=%.3f, max=%.3f", min, max));
    }

    public static float[] bilinearInterpolation(float[][] source, int targetHeight, int targetWidth) {
        int sourceHeight = source.length;
        int sourceWidth = source[0].length;

        float[] result = new float[targetHeight * targetWidth];

        float scaleY = (float) sourceHeight / targetHeight;
        float scaleX = (float) sourceWidth / targetWidth;

        for (int y = 0; y < targetHeight; y++) {
            for (int x = 0; x < targetWidth; x++) {
                float sourceY = y * scaleY;
                float sourceX = x * scaleX;

                int y1 = (int) Math.floor(sourceY);
                int x1 = (int) Math.floor(sourceX);
                int y2 = Math.min(y1 + 1, sourceHeight - 1);
                int x2 = Math.min(x1 + 1, sourceWidth - 1);

                float dy = sourceY - y1;
                float dx = sourceX - x1;

                float v11 = source[y1][x1];
                float v12 = source[y1][x2];
                float v21 = source[y2][x1];
                float v22 = source[y2][x2];

                float interpolated = v11 * (1 - dx) * (1 - dy) +
                        v12 * dx * (1 - dy) +
                        v21 * (1 - dx) * dy +
                        v22 * dx * dy;

                result[y * targetWidth + x] = interpolated;
            }
        }

        Log.d(TAG, String.format("Bilinear interpolation: %dx%d -> %dx%d",
                sourceWidth, sourceHeight, targetWidth, targetHeight));

        return result;
    }

    public static float calculateMean(float[] array) {
        float sum = 0.0f;
        int count = 0;

        for (float value : array) {
            if (!Float.isInfinite(value) && !Float.isNaN(value)) {
                sum += value;
                count++;
            }
        }

        return count > 0 ? sum / count : 0.0f;
    }

    public static float calculateStandardDeviation(float[] array) {
        float mean = calculateMean(array);
        float sumSquaredDiffs = 0.0f;
        int count = 0;

        for (float value : array) {
            if (!Float.isInfinite(value) && !Float.isNaN(value)) {
                float diff = value - mean;
                sumSquaredDiffs += diff * diff;
                count++;
            }
        }

        return count > 1 ? (float) Math.sqrt(sumSquaredDiffs / (count - 1)) : 0.0f;
    }

    public static void applyGaussianBlur(float[][] data, float sigma) {
        int height = data.length;
        int width = data[0].length;

        // Create Gaussian kernel
        int kernelSize = (int) Math.ceil(6 * sigma);
        if (kernelSize % 2 == 0) kernelSize++;
        int halfKernel = kernelSize / 2;

        float[] kernel = new float[kernelSize];
        float sum = 0.0f;

        for (int i = 0; i < kernelSize; i++) {
            int x = i - halfKernel;
            kernel[i] = (float) Math.exp(-(x * x) / (2 * sigma * sigma));
            sum += kernel[i];
        }

        // Normalize kernel
        for (int i = 0; i < kernelSize; i++) {
            kernel[i] /= sum;
        }

        // Apply horizontal blur
        float[][] temp = new float[height][width];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                float value = 0.0f;
                for (int k = 0; k < kernelSize; k++) {
                    int srcX = Math.max(0, Math.min(width - 1, x + k - halfKernel));
                    value += data[y][srcX] * kernel[k];
                }
                temp[y][x] = value;
            }
        }

        // Apply vertical blur
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                float value = 0.0f;
                for (int k = 0; k < kernelSize; k++) {
                    int srcY = Math.max(0, Math.min(height - 1, y + k - halfKernel));
                    value += temp[srcY][x] * kernel[k];
                }
                data[y][x] = value;
            }
        }

        Log.d(TAG, String.format("Applied Gaussian blur with sigma=%.2f, kernel size=%d", sigma, kernelSize));
    }

    public static void logTensorStats(float[][] tensor, String name) {
        float min = Float.MAX_VALUE;
        float max = Float.MIN_VALUE;
        float sum = 0.0f;
        int count = 0;

        for (float[] row : tensor) {
            for (float value : row) {
                if (!Float.isInfinite(value) && !Float.isNaN(value)) {
                    min = Math.min(min, value);
                    max = Math.max(max, value);
                    sum += value;
                    count++;
                }
            }
        }

        float mean = count > 0 ? sum / count : 0.0f;

        Log.d(TAG, String.format("%s stats - Shape: [%d, %d], Min: %.3f, Max: %.3f, Mean: %.3f",
                name, tensor.length, tensor[0].length, min, max, mean));
    }
}