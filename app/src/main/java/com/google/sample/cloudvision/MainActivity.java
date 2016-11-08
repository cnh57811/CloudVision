/*
 * Copyright 2016 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.sample.cloudvision;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.vision.v1.Vision;
import com.google.api.services.vision.v1.VisionRequestInitializer;
import com.google.api.services.vision.v1.model.AnnotateImageRequest;
import com.google.api.services.vision.v1.model.AnnotateImageResponse;
import com.google.api.services.vision.v1.model.BatchAnnotateImagesRequest;
import com.google.api.services.vision.v1.model.BatchAnnotateImagesResponse;
import com.google.api.services.vision.v1.model.ColorInfo;
import com.google.api.services.vision.v1.model.DominantColorsAnnotation;
import com.google.api.services.vision.v1.model.EntityAnnotation;
import com.google.api.services.vision.v1.model.FaceAnnotation;
import com.google.api.services.vision.v1.model.Feature;
import com.google.api.services.vision.v1.model.Image;
import com.google.api.services.vision.v1.model.ImageProperties;
import com.google.api.services.vision.v1.model.SafeSearchAnnotation;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    public static final String FILE_NAME = "temp.jpg";
    public static final int CAMERA_PERMISSIONS_REQUEST = 2;
    public static final int CAMERA_IMAGE_REQUEST = 3;
    private static final String CLOUD_VISION_API_KEY = "AIzaSyBKBNwF1SHcWsZ14EMeA-jNcaQER10XjuA";
    private static final String TAG = MainActivity.class.getSimpleName();
    private static final int GALLERY_IMAGE_REQUEST = 1;
    private TextView mImageDetails;
    private ImageView mMainImage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                builder.setMessage(R.string.dialog_select_prompt)
                    .setPositiveButton(R.string.dialog_select_gallery,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                startGalleryChooser();
                            }
                        }).setNegativeButton(R.string.dialog_select_camera,
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            startCamera();
                        }
                    });
                builder.create().show();
            }
        });

        mImageDetails = (TextView) findViewById(R.id.image_details);
        mMainImage = (ImageView) findViewById(R.id.main_image);
    }

    public void startGalleryChooser() {
        Intent intent = new Intent();
        intent.setType("image/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);
        startActivityForResult(Intent.createChooser(intent, "Select a photo"),
            GALLERY_IMAGE_REQUEST);
    }

    public void startCamera() {
        if (PermissionUtils.requestPermission(this, CAMERA_PERMISSIONS_REQUEST,
            Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.CAMERA)) {
            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            intent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(getCameraFile()));
            startActivityForResult(intent, CAMERA_IMAGE_REQUEST);
        }
    }

    public File getCameraFile() {
        File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        return new File(dir, FILE_NAME);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == GALLERY_IMAGE_REQUEST && resultCode == RESULT_OK && data != null) {
            uploadImage(data.getData());
        } else if (requestCode == CAMERA_IMAGE_REQUEST && resultCode == RESULT_OK) {
            uploadImage(Uri.fromFile(getCameraFile()));
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (PermissionUtils
            .permissionGranted(requestCode, CAMERA_PERMISSIONS_REQUEST, grantResults)) {
            startCamera();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Intent i = new Intent(this, SettingsActivity.class);
        startActivity(i);
        return true;
    }

    public void uploadImage(Uri uri) {
        if (uri != null) {
            try {
                // scale the image to save on bandwidth
                Bitmap bitmap = scaleBitmapDown(
                    MediaStore.Images.Media.getBitmap(getContentResolver(), uri), 1200);

                callCloudVision(bitmap);
                mMainImage.setImageBitmap(bitmap);

            } catch (IOException e) {
                Log.d(TAG, "Image picking failed because " + e.getMessage());
                Toast.makeText(this, R.string.image_picker_error, Toast.LENGTH_LONG).show();
            }
        } else {
            Log.d(TAG, "Image picker gave us a null image.");
            Toast.makeText(this, R.string.image_picker_error, Toast.LENGTH_LONG).show();
        }
    }

    private void callCloudVision(final Bitmap bitmap) throws IOException {
        // Switch text to loading
        mImageDetails.setText(R.string.loading_message);

        // Do the real work in an async task, because we need to use the network anyway
        new AsyncTask<Object, Void, String>() {
            @Override
            protected String doInBackground(Object... params) {
                try {
                    HttpTransport httpTransport = AndroidHttp.newCompatibleTransport();
                    JsonFactory jsonFactory = GsonFactory.getDefaultInstance();

                    Vision.Builder builder = new Vision.Builder(httpTransport, jsonFactory, null);
                    builder.setVisionRequestInitializer(
                        new VisionRequestInitializer(CLOUD_VISION_API_KEY));
                    Vision vision = builder.build();

                    BatchAnnotateImagesRequest batchAnnotateImagesRequest = new
                        BatchAnnotateImagesRequest();
                    batchAnnotateImagesRequest.setRequests(new ArrayList<AnnotateImageRequest>() {{
                        AnnotateImageRequest annotateImageRequest = new AnnotateImageRequest();

                        // Add the image
                        Image base64EncodedImage = new Image();
                        // Convert the bitmap to a JPEG
                        // Just in case it's a format that Android understands but Cloud Vision
                        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, byteArrayOutputStream);
                        byte[] imageBytes = byteArrayOutputStream.toByteArray();

                        // Base64 encode the JPEG
                        base64EncodedImage.encodeContent(imageBytes);
                        annotateImageRequest.setImage(base64EncodedImage);

                        // get detection types for this analysis
                        List<Feature> features = getDetectionTypes();

                        // add the features we want
                        annotateImageRequest.setFeatures(features);

                        // Add the list of one thing to the request
                        add(annotateImageRequest);
                    }});

                    Vision.Images.Annotate annotateRequest = vision.images()
                        .annotate(batchAnnotateImagesRequest);
                    // Due to a bug: requests to Vision API containing large images fail when
                    // GZipped.
                    annotateRequest.setDisableGZipContent(true);
                    Log.d(TAG, "created Cloud Vision request object, sending request");

                    BatchAnnotateImagesResponse response = annotateRequest.execute();
                    return convertResponseToString(response);

                } catch (GoogleJsonResponseException e) {
                    Log.d(TAG, "failed to make API request because " + e.getContent());
                } catch (IOException e) {
                    Log.d(TAG, "failed to make API request because of other IOException " + e
                        .getMessage());
                }
                return "Cloud Vision API request failed. Check logs for details.";
            }

            protected void onPostExecute(String result) {
                mImageDetails.setText(result);
            }
        }.execute();
    }

    private List<Feature> getDetectionTypes() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean labelDetection = prefs.getBoolean("label", true);
        boolean textDetection = prefs.getBoolean("text", false);
        boolean safeSearchDetection = prefs.getBoolean("safeSearch", false);
        boolean faceDetection = prefs.getBoolean("face", false);
        boolean landmarkDetection = prefs.getBoolean("landmark", false);
        boolean logoDetection = prefs.getBoolean("logo", false);
        boolean imageProperties = prefs.getBoolean("image", false);

        List<Feature> features = new ArrayList<>();
        if (labelDetection) {
            features.add(getFeature("LABEL_DETECTION"));
        }
        if (textDetection) {
            features.add(getFeature("TEXT_DETECTION"));
        }
        if (safeSearchDetection) {
            features.add(getFeature("SAFE_SEARCH_DETECTION"));
        }
        if (faceDetection) {
            features.add(getFeature("FACE_DETECTION"));
        }
        if (landmarkDetection) {
            features.add(getFeature("LANDMARK_DETECTION"));
        }
        if (logoDetection) {
            features.add(getFeature("LOGO_DETECTION"));
        }
        if (imageProperties) {
            features.add(getFeature("IMAGE_PROPERTIES"));
        }

        return features;
    }

    private Feature getFeature(String detectionType) {
        Feature f = new Feature();
        f.setType(detectionType);
        f.setMaxResults(5);
        return f;
    }

    public Bitmap scaleBitmapDown(Bitmap bitmap, int maxDimension) {

        int originalWidth = bitmap.getWidth();
        int originalHeight = bitmap.getHeight();
        int resizedWidth = maxDimension;
        int resizedHeight = maxDimension;

        if (originalHeight > originalWidth) {
            resizedHeight = maxDimension;
            resizedWidth = (int) (resizedHeight * (float) originalWidth / (float) originalHeight);
        } else if (originalWidth > originalHeight) {
            resizedWidth = maxDimension;
            resizedHeight = (int) (resizedWidth * (float) originalHeight / (float) originalWidth);
        } else if (originalHeight == originalWidth) {
            resizedHeight = maxDimension;
            resizedWidth = maxDimension;
        }
        return Bitmap.createScaledBitmap(bitmap, resizedWidth, resizedHeight, false);
    }

    private String convertResponseToString(BatchAnnotateImagesResponse response) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean labelDetection = prefs.getBoolean("label", true);
        boolean textDetection = prefs.getBoolean("text", false);
        boolean safeSearchDetection = prefs.getBoolean("safeSearch", false);
        boolean faceDetection = prefs.getBoolean("face", false);
        boolean landmarkDetection = prefs.getBoolean("landmark", false);
        boolean logoDetection = prefs.getBoolean("logo", false);
        boolean imageProperties = prefs.getBoolean("image", false);

        String message = "";

        if (response != null && response.getResponses() != null && response.getResponses()
            .get(0) != null) {
            AnnotateImageResponse imgRs = response.getResponses().get(0);

            if (labelDetection) {
                List<EntityAnnotation> labels = imgRs.getLabelAnnotations();
                message += getAnnotationString(labels, "Labels");
            }
            if (textDetection) {
                List<EntityAnnotation> texts = imgRs.getTextAnnotations();
                message += getAnnotationString(texts, "Text");
            }
            if (safeSearchDetection) {
                SafeSearchAnnotation safeSearch = imgRs.getSafeSearchAnnotation();
                message += getAnnotationString(safeSearch, "SafeSearch");
            }
            if (faceDetection) {
                List<FaceAnnotation> faces = imgRs.getFaceAnnotations();
                message += getFaceAnnotationString(faces, "Face");
            }
            if (landmarkDetection) {
                List<EntityAnnotation> landmarks = imgRs.getLandmarkAnnotations();
                message += getAnnotationString(landmarks, "Landmarks");
            }
            if (logoDetection) {
                List<EntityAnnotation> logos = imgRs.getLogoAnnotations();
                message += getAnnotationString(logos, "Logos");
            }
            if (imageProperties) {
                ImageProperties properties = imgRs.getImagePropertiesAnnotation();
                message += getAnnotationString(properties, "Image Properties");
            }
        }

        return message;
    }

    @NonNull
    private String getAnnotationString(List<EntityAnnotation> annotations, String type) {
        String message = (type + "\n");
        if (annotations != null) {
            for (EntityAnnotation label : annotations) {
                Float score = label.getScore() != null ? label.getScore() * 100 : 0f;
                if (type.equals("Text")) {
                    message += label.getDescription();
                    ;
                } else {
                    message += String
                        .format(Locale.getDefault(), "%f%% sure this contains %s", score,
                            label.getDescription());
                }
                message += "\n";
            }
            message += "\n";
        } else {
            message += "nothing\n\n";
        }
        return message;
    }

    @NonNull
    private String getAnnotationString(SafeSearchAnnotation annotation, String type) {
        String message = (type + "\n");
        if (annotation != null) {
            if (annotation.getAdult() != null) {
                message += "Adult: " + annotation.getAdult() + "\n";
            }
            if (annotation.getMedical() != null) {
                message += "Medical: " + annotation.getMedical() + "\n";
            }
            if (annotation.getSpoof() != null) {
                message += "Spoof: " + annotation.getSpoof() + "\n";
            }
            if (annotation.getViolence() != null) {
                message += "Violence: " + annotation.getViolence();
            }
            message += "\n\n";
        } else {
            message += "nothing\n\n";
        }
        return message;
    }

    @NonNull
    private String getFaceAnnotationString(List<FaceAnnotation> annotations, String type) {
        String message = (type + "\n");
        if (annotations != null) {
            for (FaceAnnotation face : annotations) {
                if (face.getAngerLikelihood() != null) {
                    message += "Anger likelihood: " + face.getAngerLikelihood() + "\n";
                }
                if (face.getBlurredLikelihood() != null) {
                    message += "Blurred likelihood: " + face.getBlurredLikelihood() + "\n";
                }
                if (face.getHeadwearLikelihood() != null) {
                    message += "Headwear likelihood: " + face.getHeadwearLikelihood() + "\n";
                }
                if (face.getJoyLikelihood() != null) {
                    message += "Joy likelihood: " + face.getJoyLikelihood() + "\n";
                }
                if (face.getSorrowLikelihood() != null) {
                    message += "Sorrow likelihood: " + face.getSorrowLikelihood() + "\n";
                }
                if (face.getSurpriseLikelihood() != null) {
                    message += "Surprised likelihood: " + face.getSurpriseLikelihood();
                }
                message += "\n\n";
            }
        } else {
            message += "nothing\n\n";
        }
        return message;
    }

    @NonNull
    private String getAnnotationString(ImageProperties properties, String type) {
        String message = (type + "\n");
        if (properties != null && properties.getDominantColors() != null) {
            DominantColorsAnnotation annotation = properties.getDominantColors();
            List<ColorInfo> colors = annotation.getColors();
            for (ColorInfo color : colors) {
                String colorRGB = String
                    .format(Locale.getDefault(), "#r%.0f g%.0f b%.0f", color.getColor().getRed(),
                        color.getColor().getGreen(), color.getColor().getBlue());
                String msg = String
                    .format(Locale.getDefault(), "Color %s occupies %.1f%% of the pixels.\n",
                        colorRGB, color.getPixelFraction() * 100);
                message += msg;
            }
            message += "\n\n";
        } else {
            message += "nothing\n\n";
        }
        return message;
    }
}
