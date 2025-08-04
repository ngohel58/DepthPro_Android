package com.depthpro.android;

import android.Manifest;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.google.android.material.slider.Slider;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final int REQUEST_PERMISSIONS = 100;
    private static final int REQUEST_IMAGE_PICK = 101;
    private static final int REQUEST_CAMERA = 102;

    // UI Components
    private ImageView originalImageView;
    private ImageView depthMapImageView;
    private ImageView chromoImageView;
    private Button selectImageButton;
    private Button captureImageButton;
    private Button processButton;
    private Button downloadDepthButton;
    private Button downloadEffectButton;
    private ProgressBar progressBar;
    private TextView statusText;
    private TextView processingTimeText;

    // Core Components - Updated to use DepthAnythingV2
    private DepthAnythingV2Processor depthProcessor;
    private ChromostereopsisProcessor chromoProcessor;
    private ExecutorService executorService;

    // Current image
    private Bitmap currentBitmap;
    private Bitmap depthMapBitmap;
    private Bitmap chromoBitmap;
    private float[][] currentDepthMap;
    private Uri currentImageUri;

    // Effect parameters
    private ChromostereopsisProcessor.EffectParams effectParams = new ChromostereopsisProcessor.EffectParams();

    // Sliders
    private Slider thresholdSlider;
    private Slider depthScaleSlider;
    private Slider featherSlider;
    private Slider redSlider;
    private Slider blueSlider;
    private Slider gammaSlider;
    private Slider blackSlider;
    private Slider whiteSlider;
    private Slider smoothingSlider;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initializeViews();
        initializeComponents();
        checkPermissions();
        handleSharedIntent();
    }

    private void initializeViews() {
        originalImageView = findViewById(R.id.originalImageView);
        depthMapImageView = findViewById(R.id.depthMapImageView);
        chromoImageView = findViewById(R.id.chromoImageView);
        selectImageButton = findViewById(R.id.selectImageButton);
        captureImageButton = findViewById(R.id.captureImageButton);
        processButton = findViewById(R.id.processButton);
        downloadDepthButton = findViewById(R.id.downloadDepthButton);
        downloadEffectButton = findViewById(R.id.downloadEffectButton);
        progressBar = findViewById(R.id.progressBar);
        statusText = findViewById(R.id.statusText);
        processingTimeText = findViewById(R.id.processingTimeText);

        thresholdSlider = findViewById(R.id.thresholdSlider);
        depthScaleSlider = findViewById(R.id.depthScaleSlider);
        featherSlider = findViewById(R.id.featherSlider);
        redSlider = findViewById(R.id.redSlider);
        blueSlider = findViewById(R.id.blueSlider);
        gammaSlider = findViewById(R.id.gammaSlider);
        blackSlider = findViewById(R.id.blackSlider);
        whiteSlider = findViewById(R.id.whiteSlider);
        smoothingSlider = findViewById(R.id.smoothingSlider);

        // Set click listeners
        selectImageButton.setOnClickListener(v -> selectImage());
        captureImageButton.setOnClickListener(v -> captureImage());
        processButton.setOnClickListener(v -> processCurrentImage());
        downloadDepthButton.setOnClickListener(v -> {
            if (depthMapBitmap != null) {
                saveBitmapToGallery(depthMapBitmap, "depth_map");
            }
        });
        downloadEffectButton.setOnClickListener(v -> {
            if (chromoBitmap != null) {
                saveBitmapToGallery(chromoBitmap, "chromo_effect");
            }
        });

        // Initially disable process button
        processButton.setEnabled(false);
        downloadDepthButton.setEnabled(false);
        downloadEffectButton.setEnabled(false);

        setupSliders();
    }

    private void setupSliders() {
        thresholdSlider.addOnChangeListener((slider, value, fromUser) -> {
            effectParams.threshold = value;
            updateEffect();
        });
        depthScaleSlider.addOnChangeListener((slider, value, fromUser) -> {
            effectParams.depthScale = value;
            updateEffect();
        });
        featherSlider.addOnChangeListener((slider, value, fromUser) -> {
            effectParams.feather = value;
            updateEffect();
        });
        redSlider.addOnChangeListener((slider, value, fromUser) -> {
            effectParams.redBrightness = value;
            updateEffect();
        });
        blueSlider.addOnChangeListener((slider, value, fromUser) -> {
            effectParams.blueBrightness = value;
            updateEffect();
        });
        gammaSlider.addOnChangeListener((slider, value, fromUser) -> {
            effectParams.gamma = value;
            updateEffect();
        });
        blackSlider.addOnChangeListener((slider, value, fromUser) -> {
            effectParams.blackLevel = value;
            updateEffect();
        });
        whiteSlider.addOnChangeListener((slider, value, fromUser) -> {
            effectParams.whiteLevel = value;
            updateEffect();
        });
        smoothingSlider.addOnChangeListener((slider, value, fromUser) -> {
            effectParams.smoothing = value;
            updateEffect();
        });
    }

    private void initializeComponents() {
        executorService = Executors.newSingleThreadExecutor();

        // Initialize Depth Anything V2 processor
        try {
            depthProcessor = new DepthAnythingV2Processor(this);
            updateStatus("Depth Anything V2 model loaded successfully");
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize Depth Anything V2 processor", e);
            updateStatus("Failed to load Depth Anything V2 model: " + e.getMessage());
            Toast.makeText(this, "Failed to initialize model", Toast.LENGTH_LONG).show();
        }

        chromoProcessor = new ChromostereopsisProcessor();
    }

    private void checkPermissions() {
        String[] permissions = {
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.CAMERA
        };

        boolean hasAllPermissions = true;
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                hasAllPermissions = false;
                break;
            }
        }

        if (!hasAllPermissions) {
            ActivityCompat.requestPermissions(this, permissions, REQUEST_PERMISSIONS);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_PERMISSIONS) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            if (!allGranted) {
                Toast.makeText(this, "Permissions required for app functionality", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void handleSharedIntent() {
        Intent intent = getIntent();
        String action = intent.getAction();
        String type = intent.getType();

        if (Intent.ACTION_SEND.equals(action) && type != null && type.startsWith("image/")) {
            Uri imageUri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (imageUri != null) {
                loadImageFromUri(imageUri);
            }
        }
    }

    private void selectImage() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        intent.setType("image/*");
        startActivityForResult(intent, REQUEST_IMAGE_PICK);
    }

    private void captureImage() {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (intent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(intent, REQUEST_CAMERA);
        } else {
            Toast.makeText(this, "Camera not available", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode != Activity.RESULT_OK || data == null) {
            return;
        }

        switch (requestCode) {
            case REQUEST_IMAGE_PICK:
                Uri selectedImageUri = data.getData();
                if (selectedImageUri != null) {
                    loadImageFromUri(selectedImageUri);
                }
                break;

            case REQUEST_CAMERA:
                Bundle extras = data.getExtras();
                if (extras != null) {
                    Bitmap photo = (Bitmap) extras.get("data");
                    if (photo != null) {
                        loadImageFromBitmap(photo);
                    }
                }
                break;
        }
    }

    private void loadImageFromUri(Uri uri) {
        try {
            ContentResolver contentResolver = getContentResolver();
            InputStream inputStream = contentResolver.openInputStream(uri);
            Bitmap bitmap = BitmapFactory.decodeStream(inputStream);

            if (bitmap != null) {
                currentImageUri = uri;
                loadImageFromBitmap(bitmap);
            } else {
                Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show();
            }
        } catch (FileNotFoundException e) {
            Log.e(TAG, "Image file not found", e);
            Toast.makeText(this, "Image file not found", Toast.LENGTH_SHORT).show();
        }
    }

    private void loadImageFromBitmap(Bitmap bitmap) {
        currentBitmap = bitmap;

        // Display original image
        Glide.with(this)
                .load(bitmap)
                .into(originalImageView);

        // Clear previous depth map
        depthMapImageView.setImageBitmap(null);
        chromoImageView.setImageBitmap(null);
        depthMapBitmap = null;
        chromoBitmap = null;
        currentDepthMap = null;
        downloadDepthButton.setEnabled(false);
        downloadEffectButton.setEnabled(false);

        // Enable process button
        processButton.setEnabled(true);
        updateStatus("Image loaded. Ready to process.");

        // Clear previous results
        processingTimeText.setText("");
    }

    private void processCurrentImage() {
        if (currentBitmap == null || depthProcessor == null) {
            Toast.makeText(this, "No image selected or model not loaded", Toast.LENGTH_SHORT).show();
            return;
        }

        // Show progress
        progressBar.setVisibility(View.VISIBLE);
        processButton.setEnabled(false);
        updateStatus("Processing image...");

        // Process in background thread
        executorService.execute(() -> {
            try {
                long startTime = System.currentTimeMillis();

                // Process image with Depth Anything V2
                DepthAnythingV2Processor.DepthResult result = depthProcessor.processImage(currentBitmap);

                long processingTime = System.currentTimeMillis() - startTime;

                // Update UI on main thread
                runOnUiThread(() -> {
                    displayResults(result, processingTime);
                    progressBar.setVisibility(View.GONE);
                    processButton.setEnabled(true);
                });

            } catch (Exception e) {
                Log.e(TAG, "Error processing image", e);
                runOnUiThread(() -> {
                    updateStatus("Error processing image: " + e.getMessage());
                    progressBar.setVisibility(View.GONE);
                    processButton.setEnabled(true);
                    Toast.makeText(MainActivity.this, "Processing failed", Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void displayResults(DepthAnythingV2Processor.DepthResult result, long processingTime) {
        // Display depth map
        if (result.depthMapBitmap != null) {
            depthMapBitmap = result.depthMapBitmap;
            Glide.with(this)
                    .load(depthMapBitmap)
                    .into(depthMapImageView);
        }

        currentDepthMap = result.depthMap;
        updateEffect();

        downloadDepthButton.setEnabled(true);
        downloadEffectButton.setEnabled(true);

        // Display processing time (no focal length for Depth Anything V2)
        processingTimeText.setText(String.format("Processing Time: %d ms", processingTime));

        updateStatus("Processing completed successfully");

        Toast.makeText(this, "Depth map generated!", Toast.LENGTH_SHORT).show();
    }

    private void updateEffect() {
        if (currentBitmap == null || currentDepthMap == null) return;
        executorService.execute(() -> {
            Bitmap effect = chromoProcessor.applyEffect(currentBitmap, currentDepthMap, effectParams);
            chromoBitmap = effect;
            runOnUiThread(() -> {
                if (effect != null) {
                    Glide.with(this).load(effect).into(chromoImageView);
                }
            });
        });
    }

    private void saveBitmapToGallery(Bitmap bitmap, String name) {
        try {
            ContentResolver resolver = getContentResolver();
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, name + ".png");
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
            Uri uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (uri != null) {
                try (OutputStream out = resolver.openOutputStream(uri)) {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
                    runOnUiThread(() -> Toast.makeText(this, getString(R.string.toast_image_saved), Toast.LENGTH_SHORT).show());
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Save failed", e);
            runOnUiThread(() -> Toast.makeText(this, "Save failed", Toast.LENGTH_SHORT).show());
        }
    }

    private void updateStatus(String message) {
        statusText.setText(message);
        Log.d(TAG, message);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }

        if (depthProcessor != null) {
            depthProcessor.cleanup();
        }
    }
}