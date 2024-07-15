package com.example.amigo;

import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.os.SystemClock;
import android.util.Log;

import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;
import org.tensorflow.lite.support.tensorbuffer.*;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class TFLiteObjectDetectionAPIModel {

    private static final String TAG = "TFLiteObjectDetection";
    private static final String MODEL_FILENAME = "model.tflite";  // Change this to your model's filename
    private static final String LABEL_FILENAME = "labels.txt";  // Change this to your label file's filename
    private static final int INPUT_SIZE = 300;  // Change this to your model's input size
    private static final int NUM_DETECTIONS = 10;  // Change this to your model's output size
    private final Interpreter interpreter;
    private final List<String> labels;

    public TFLiteObjectDetectionAPIModel(AssetManager assetManager) throws IOException {
        MappedByteBuffer model = loadModelFile(assetManager, MODEL_FILENAME);
        interpreter = new Interpreter(model);
        labels = loadLabels(assetManager, LABEL_FILENAME);
    }

    private MappedByteBuffer loadModelFile(AssetManager assetManager, String modelFilename) throws IOException {
        AssetFileDescriptor fileDescriptor = assetManager.openFd(modelFilename);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    private List<String> loadLabels(AssetManager assetManager, String labelFilename) throws IOException {
        List<String> labels = new ArrayList<>();
        try (FileInputStream fis = assetManager.openFd(labelFilename).createInputStream()) {
            int size = fis.available();
            byte[] buffer = new byte[size];
            fis.read(buffer);
            String labelFileContents = new String(buffer);
            String[] lines = labelFileContents.split("\n");
            for (String line : lines) {
                labels.add(line);
            }
        }
        return labels;
    }

    public List<Recognition> recognizeImage(Bitmap bitmap) {
        long startTime = SystemClock.uptimeMillis();

        TensorImage inputImage = new TensorImage();
        inputImage.load(bitmap);
        TensorBuffer outputBuffer = TensorBuffer.createFixedSize(new int[]{1, NUM_DETECTIONS, 4}, TensorBuffer.DataType.FLOAT32);
        TensorBuffer scoreBuffer = TensorBuffer.createFixedSize(new int[]{1, NUM_DETECTIONS}, TensorBuffer.DataType.FLOAT32);
        TensorBuffer classBuffer = TensorBuffer.createFixedSize(new int[]{1, NUM_DETECTIONS}, TensorBuffer.DataType.FLOAT32);

        Object[] inputs = new Object[]{inputImage.getBuffer()};
        Map<Integer, Object> outputs = new HashMap<>();
        outputs.put(0, outputBuffer.getBuffer());
        outputs.put(1, scoreBuffer.getBuffer());
        outputs.put(2, classBuffer.getBuffer());

        interpreter.runForMultipleInputsOutputs(inputs, outputs);

        long endTime = SystemClock.uptimeMillis();
        Log.d(TAG, "Time taken to run model inference: " + (endTime - startTime));

        List<Recognition> recognitions = new ArrayList<>();
        float[] outputLocations = outputBuffer.getFloatArray();
        float[] outputScores = scoreBuffer.getFloatArray();
        float[] outputClasses = classBuffer.getFloatArray();

        for (int i = 0; i < NUM_DETECTIONS; i++) {
            float score = outputScores[i];
            if (score > 0.5) {
                float left = outputLocations[i * 4];
                float top = outputLocations[i * 4 + 1];
                float right = outputLocations[i * 4 + 2];
                float bottom = outputLocations[i * 4 + 3];
                RectF rectF = new RectF(left, top, right, bottom);
                String label = labels.get((int) outputClasses[i]);
                Recognition recognition = new Recognition(label, score, rectF);
                recognitions.add(recognition);
            }
        }

        return recognitions;
    }

    public static class Recognition {
        private final String title;
        private final float confidence;
        private final RectF location;

        public Recognition(String title, float confidence, RectF location) {
            this.title = title;
            this.confidence = confidence;
            this.location = location;
        }

        public String getTitle() {
            return title;
        }

        public float getConfidence() {
            return confidence;
        }

        public RectF getLocation() {
            return location;
        }
    }
}
