package com.reactnative.ivpusic.imagepicker;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.os.Environment;
import android.util.Log;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReadableMap;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Created by ipusic on 12/27/16.
 */
class Compression {
	private static final float BYTES_IN_MB = 1024.0f * 1024.0f;
	private String TAG = "image-crop-picker";
	private BitmapFactory.Options options = new BitmapFactory.Options();
	private ResultCollector resultCollector = new ResultCollector();

	File resize(String originalImagePath, int maxWidth, int maxHeight, int quality) throws IOException {
		File resizeImageFile = null;
		/*
			In the first attempt try without creating bitmap
			and compare it with available memory,if its fine
			then continue to create bitmap,if its not then
			sample or reduce it.

		*/
		Bitmap original = null;
		Bitmap resized = null;
		options.inJustDecodeBounds = true;
		// this will create bitmap in memory right away,we don't want that
		// Bitmap original = BitmapFactory.decodeFile(originalImagePath);
		BitmapFactory.decodeFile(originalImagePath, options);
		options.outWidth = options.outWidth * 100;
		options.outHeight = options.outHeight * 100;
		int width = options.outWidth;
		int height = options.outHeight;
		float requiredMemory = (width * height * 4.0f) / BYTES_IN_MB;
		// Log.d(TAG, "first bitmap size required:" + requiredMemory + " MB");
		float availableMemory = availMemory();
		// Log.d(TAG, "available memory" + availableMemory);

		// Log.d(TAG, "max_width:" + maxWidth);
		// Log.d(TAG, "max_height:" + maxHeight);
		// Log.d(TAG, "width" + width);
		// Log.d(TAG, "height" + height);
		// after we get meta data of bitmap we again set it to false
		// so that we can further create a bitmap image in memory,
		// based on the available memory or after sampling it.
		options.inJustDecodeBounds = false;
		if (requiredMemory < availableMemory) {
			// Log.d(TAG, "Hit this condition");
			original = BitmapFactory.decodeFile(originalImagePath, options);
		} else {
			// Log.d(TAG,"Hit sampling condition");
			// Log.d(TAG, "AFTER SAMPLEING" + options.outWidth + " " + options.inSampleSize + " " + options.outHeight);
			// Log.d(TAG, "SAMPLE FACTOR" + calculateInSampleSize(maxWidth, maxHeight));
			options.inSampleSize = calculateInSampleSize(maxWidth, maxHeight);
			original = BitmapFactory.decodeFile(originalImagePath, options);
		}


		// Use original image exif orientation data to preserve image orientation for the resized bitmap
		ExifInterface originalExif = new ExifInterface(originalImagePath);
		int originalOrientation = originalExif.getAttributeInt(ExifInterface.TAG_ORIENTATION, 1);

		Matrix rotationMatrix = new Matrix();
		int rotationAngleInDegrees = getRotationInDegreesForOrientationTag(originalOrientation);
		rotationMatrix.postRotate(rotationAngleInDegrees);

		float ratioBitmap = (float) width / (float) height;
		float ratioMax = (float) maxWidth / (float) maxHeight;

		int finalWidth = maxWidth;
		int finalHeight = maxHeight;

		if (ratioMax > 1) {
			finalWidth = (int) ((float) maxHeight * ratioBitmap);
		} else {
			finalHeight = (int) ((float) maxWidth / ratioBitmap);
		}

		if (original != null) {
			resized = Bitmap.createScaledBitmap(original, finalWidth, finalHeight, true);
			resized = Bitmap.createBitmap(resized, 0, 0, finalWidth, finalHeight, rotationMatrix, true);
			File imageDirectory = Environment.getExternalStoragePublicDirectory(
					Environment.DIRECTORY_PICTURES);
			if (!imageDirectory.exists()) {
				Log.d(TAG, "Pictures Directory is not existing. Will create this directory.");
				imageDirectory.mkdirs();
			}
			resizeImageFile = new File(imageDirectory, UUID.randomUUID() + ".jpg");

			OutputStream os = new BufferedOutputStream(new FileOutputStream(resizeImageFile));
			resized.compress(Bitmap.CompressFormat.JPEG, quality, os);

			os.close();
			original.recycle();
			resized.recycle();
		}
		return resizeImageFile;
	}

	float availMemory() {
		final Runtime rt = Runtime.getRuntime();
		float bytesUsed = rt.totalMemory();
		float bytesTotal = rt.maxMemory();

		bytesTotal = bytesTotal / BYTES_IN_MB;
		float mbUsed = bytesUsed / BYTES_IN_MB;
		float freeMemoryInMB = bytesTotal - mbUsed;
		// Log.d(TAG, "bytesTotal:" + bytesTotal);
		// Log.d(TAG, "mbUsed:" + mbUsed);
		// Log.d(TAG, "freeMb:" + freeMemoryInMB);
		return freeMemoryInMB;
	}

	// method to reduce bitmap size from google documentation
	int calculateInSampleSize(int reqWidth, int reqHeight) {
		// Raw height and width of image
		final int height = options.outHeight;
		final int width = options.outWidth;
		int inSampleSize = 1;

		if (height > reqHeight || width > reqWidth) {

			final int halfHeight = height / 2;
			final int halfWidth = width / 2;

			// Calculate the largest inSampleSize value that is a power of 2 and keeps both
			// height and width larger than the requested height and width.
			while ((halfHeight / inSampleSize) >= reqHeight
					&& (halfWidth / inSampleSize) >= reqWidth) {
				inSampleSize *= 2;
			}
		}

		return inSampleSize;
	}

	int getRotationInDegreesForOrientationTag(int orientationTag) {
		switch (orientationTag) {
			case ExifInterface.ORIENTATION_ROTATE_90:
				return 90;
			case ExifInterface.ORIENTATION_ROTATE_270:
				return -90;
			case ExifInterface.ORIENTATION_ROTATE_180:
				return 180;
			default:
				return 0;
		}
	}

	File compressImage(final ReadableMap options, final String originalImagePath, final BitmapFactory.Options bitmapOptions) throws IOException {
		Integer maxWidth = options.hasKey("compressImageMaxWidth") ? options.getInt("compressImageMaxWidth") : null;
		Integer maxHeight = options.hasKey("compressImageMaxHeight") ? options.getInt("compressImageMaxHeight") : null;
		Double quality = options.hasKey("compressImageQuality") ? options.getDouble("compressImageQuality") : null;

		boolean isLossLess = (quality == null || quality == 1.0);
		boolean useOriginalWidth = (maxWidth == null || maxWidth >= bitmapOptions.outWidth);
		boolean useOriginalHeight = (maxHeight == null || maxHeight >= bitmapOptions.outHeight);

		List knownMimes = Arrays.asList("image/jpeg", "image/jpg", "image/png", "image/gif", "image/tiff");
		boolean isKnownMimeType = (bitmapOptions.outMimeType != null && knownMimes.contains(bitmapOptions.outMimeType.toLowerCase()));

		if (isLossLess && useOriginalWidth && useOriginalHeight && isKnownMimeType) {
			Log.d(TAG, "Skipping image compression");
			return new File(originalImagePath);
		}

		Log.d(TAG, "Image compression activated");

		// compression quality
		int targetQuality = quality != null ? (int) (quality * 100) : 100;
		Log.d(TAG, "Compressing image with quality " + targetQuality);

		if (maxWidth == null) {
			maxWidth = bitmapOptions.outWidth;
		} else {
			maxWidth = Math.min(maxWidth, bitmapOptions.outWidth);
		}

		if (maxHeight == null) {
			maxHeight = bitmapOptions.outHeight;
		} else {
			maxHeight = Math.min(maxHeight, bitmapOptions.outHeight);
		}

		return resize(originalImagePath, maxWidth, maxHeight, targetQuality);
	}

	synchronized void compressVideo(final Activity activity, final ReadableMap options, final String originalVideo, final String compressedVideo, final Promise promise) {
		// todo: video compression
		// failed attempt 1: ffmpeg => slow and licensing issues
		promise.resolve(originalVideo);
	}
}
