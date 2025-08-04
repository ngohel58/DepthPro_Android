package com.depthpro.android;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.util.Collections;
import java.util.Map;

public class DepthAnythingV2Processor {
    private static final String TAG = "DepthAnythingV2Processor";
    private static final String MODEL_FILENAME = "depth_anything_v2_large.onnx";

    // Model input dimensions
    private static final int INPUT_HEIGHT = 518;
    private static final int INPUT_WIDTH = 518;
    private static final int CHANNELS = 3;

    // Model input/output names
    private static final String INPUT_NAME = "pixel_values";
    private static final String DEPTH_OUTPUT_NAME = "depth";

    private final Context context;
    private OrtEnvironment ortEnvironment;
    private OrtSession ortSession;

    private final ImageUtils imageUtils;
    private final TensorUtils tensorUtils;
    private final DepthMapRenderer depthMapRenderer;

    public DepthAnythingV2Processor(Context context) throws OrtException, IOException {
        this.context = context;
        this.imageUtils = new ImageUtils();
        this.tensorUtils = new TensorUtils();
        this.depthMapRenderer = new DepthMapRenderer();

        initializeModel();
    }

    private void initializeModel() throws OrtException, IOException {
        Log.d(TAG, "Initializing Depth Anything V2 ONNX model...");

        // Initialize ONNX Runtime environment
        ortEnvironment = OrtEnvironment.getEnvironment();

        // Copy model to internal storage for direct file access
        String modelPath = copyModelToInternalStorage();

        // Create session options
        OrtSession.SessionOptions sessionOptions = new OrtSession.SessionOptions();
        sessionOptions.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.BASIC_OPT);

        // Enable CPU optimizations
        sessionOptions.setIntraOpNumThreads(4);
        sessionOptions.setInterOpNumThreads(4);

        // Create session from file path
        ortSession = ortEnvironment.createSession(modelPath, sessionOptions);

        Log.d(TAG, "Model initialized successfully");
        logModelInfo();
    }

    private String copyModelToInternalStorage() throws IOException {
        File modelFile = new File(context.getFilesDir(), MODEL_FILENAME);

        // Only copy if not exists
        if (!modelFile.exists()) {
            Log.d(TAG, "Copying model to internal storage...");

            try (InputStream inputStream = context.getAssets().open(MODEL_FILENAME);
                 java.io.FileOutputStream outputStream = new java.io.FileOutputStream(modelFile)) {

                byte[] buffer = new byte[8192];
                int bytesRead;
                long totalBytes = 0;

                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                    totalBytes += bytesRead;

                    // Log progress every 50MB
                    if (totalBytes % (50 * 1024 * 1024) == 0) {
                        Log.d(TAG, "Copied " + (totalBytes / (1024 * 1024)) + " MB");
                    }
                }

                Log.d(TAG, "Model copied successfully: " + (totalBytes / (1024 * 1024)) + " MB");
            }
        }

        return modelFile.getAbsolutePath();
    }

    private void logModelInfo() {
        try {
            Log.d(TAG, "Model inputs: " + ortSession.getInputNames());
            Log.d(TAG, "Model outputs: " + ortSession.getOutputNames());
        } catch (Exception e) {
            Log.w(TAG, "Could not log model info", e);
        }
    }

    public DepthResult processImage(Bitmap inputBitmap) throws OrtException {
        Log.d(TAG, "Processing image for depth estimation...");

        // Preprocess image
        Bitmap resizedBitmap = imageUtils.resizeBitmap(inputBitmap, INPUT_WIDTH, INPUT_HEIGHT);
        float[][][] preprocessedImage = preprocessImageForDepthAnything(resizedBitmap);

        // Convert to tensor format (1, 3, H, W)
        float[][][][] tensorData = new float[1][CHANNELS][INPUT_HEIGHT][INPUT_WIDTH];
        for (int c = 0; c < CHANNELS; c++) {
            for (int h = 0; h < INPUT_HEIGHT; h++) {
                for (int w = 0; w < INPUT_WIDTH; w++) {
                    tensorData[0][c][h][w] = preprocessedImage[h][w][c];
                }
            }
        }

        // Create input tensor
        long[] inputShape = {1, CHANNELS, INPUT_HEIGHT, INPUT_WIDTH};
        OnnxTensor inputTensor = OnnxTensor.createTensor(ortEnvironment, tensorData);

        // Run inference
        Map<String, OnnxTensor> inputs = Collections.singletonMap(INPUT_NAME, inputTensor);
        OrtSession.Result result = ortSession.run(inputs);

        // Extract depth output - try multiple possible names
        OnnxTensor depthTensor = null;

        // Try common output names for Depth Anything V2
        String[] possibleNames = {"depth", "output", "logits", "predicted_depth"};
        for (String name : possibleNames) {
            depthTensor = (OnnxTensor) result.get(name).orElse(null);
            if (depthTensor != null) {
                Log.d(TAG, "Found output with name: " + name);
                break;
            }
        }

        // If still null, try getting first output
        if (depthTensor == null && result.size() > 0) {
            String firstOutputName = result.iterator().next().getKey();
            depthTensor = (OnnxTensor) result.get(firstOutputName).orElse(null);
            Log.d(TAG, "Using first output: " + firstOutputName);
        }

        if (depthTensor == null) {
            // Log all available outputs for debugging
            StringBuilder availableOutputs = new StringBuilder("Available outputs: ");
            for (Map.Entry<String, ?> entry : result) {
                availableOutputs.append(entry.getKey()).append(" ");
            }
            Log.e(TAG, availableOutputs.toString());
            throw new RuntimeException("No depth output found. Available: " + availableOutputs.toString());
        }

        // Process depth map
        float[][] depthMap = extractDepthMap(depthTensor);

        // Generate depth map bitmap
        Bitmap depthMapBitmap = depthMapRenderer.renderDepthMap(depthMap, inputBitmap.getWidth(), inputBitmap.getHeight());

        // Cleanup
        inputTensor.close();
        result.close();

        Log.d(TAG, "Depth estimation completed");

        return new DepthResult(depthMapBitmap, depthMap);
    }

    private float[][][] preprocessImageForDepthAnything(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();

        float[][][] preprocessedImage = new float[height][width][3];
        int[] pixels = new int[width * height];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixel = pixels[y * width + x];

                // Extract RGB values
                int r = (pixel >> 16) & 0xFF;
                int g = (pixel >> 8) & 0xFF;
                int b = pixel & 0xFF;

                // Normalize to [0, 1] then shift by -0.45 (Depth Anything V2 preprocessing)
                preprocessedImage[y][x][0] = (r / 255.0f) - 0.45f; // R
                preprocessedImage[y][x][1] = (g / 255.0f) - 0.45f; // G
                preprocessedImage[y][x][2] = (b / 255.0f) - 0.45f; // B
            }
        }

        Log.d(TAG, String.format("Preprocessed image: %dx%d", width, height));
        return preprocessedImage;
    }

    private float[][] extractDepthMap(OnnxTensor depthTensor) throws OrtException {
        long[] shape = depthTensor.getInfo().getShape();
        Log.d(TAG, "Depth tensor shape: " + java.util.Arrays.toString(shape));

        // Depth Anything V2 output shape is [1, H, W] or [1, 1, H, W]
        int height, width;
        FloatBuffer buffer = depthTensor.getFloatBuffer();

        if (shape.length == 3) {
            height = (int) shape[1];
            width = (int) shape[2];
        } else if (shape.length == 4) {
            height = (int) shape[2];
            width = (int) shape[3];
        } else {
            throw new RuntimeException("Unexpected depth tensor shape: " + java.util.Arrays.toString(shape));
        }

        float[][] depthMap = new float[height][width];
        for (int h = 0; h < height; h++) {
            for (int w = 0; w < width; w++) {
                depthMap[h][w] = buffer.get(h * width + w);
            }
        }

        // Normalize depth values for visualization
        normalizeDepthMap(depthMap);

        return depthMap;
    }

    private void normalizeDepthMap(float[][] depthMap) {
        // Find min and max values
        float minDepth = Float.MAX_VALUE;
        float maxDepth = Float.MIN_VALUE;

        for (float[] row : depthMap) {
            for (float value : row) {
                if (!Float.isInfinite(value) && !Float.isNaN(value)) {
                    minDepth = Math.min(minDepth, value);
                    maxDepth = Math.max(maxDepth, value);
                }
            }
        }

        Log.d(TAG, String.format("Depth range: %.3f - %.3f", minDepth, maxDepth));

        // Normalize to [0, 1] range and invert for visualization
        float range = maxDepth - minDepth;
        if (range > 0) {
            for (int h = 0; h < depthMap.length; h++) {
                for (int w = 0; w < depthMap[h].length; w++) {
                    if (!Float.isInfinite(depthMap[h][w]) && !Float.isNaN(depthMap[h][w])) {
                        // Invert: closer objects appear brighter
                        depthMap[h][w] = 1.0f - ((depthMap[h][w] - minDepth) / range);
                    } else {
                        depthMap[h][w] = 0.0f;
                    }
                }
            }
        }
    }

    public void cleanup() {
        try {
            if (ortSession != null) {
                ortSession.close();
                ortSession = null;
            }
            if (ortEnvironment != null) {
                ortEnvironment.close();
                ortEnvironment = null;
            }
            Log.d(TAG, "DepthAnythingV2Processor cleaned up");
        } catch (OrtException e) {
            Log.e(TAG, "Error during cleanup", e);
        }
    }

    // Result class (removed focal length)
    public static class DepthResult {
        public final Bitmap depthMapBitmap;
        public final float[][] depthMap;

        public DepthResult(Bitmap depthMapBitmap, float[][] depthMap) {
            this.depthMapBitmap = depthMapBitmap;
            this.depthMap = depthMap;
        }
    }
}