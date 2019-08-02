package org.webrtc;

import android.content.Context;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.ErrorCallback;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.PreviewCallback;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.SystemClock;
import android.view.WindowManager;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.TimeUnit;

class Camera1Session implements CameraSession {
   private static final String TAG = "Camera1Session";
   private static final int NUMBER_OF_CAPTURE_BUFFERS = 3;
   private static final Histogram camera1StartTimeMsHistogram = Histogram.createCounts("WebRTC.Android.Camera1.StartTimeMs", 1, 10000, 50);
   private static final Histogram camera1StopTimeMsHistogram = Histogram.createCounts("WebRTC.Android.Camera1.StopTimeMs", 1, 10000, 50);
   private static final Histogram camera1ResolutionHistogram;
   private final Handler cameraThreadHandler;
   private final CameraSession.Events events;
   private final boolean captureToTexture;
   private final Context applicationContext;
   private final SurfaceTextureHelper surfaceTextureHelper;
   private final int cameraId;
   private final Camera camera;
   private final CameraInfo info;
   private final CameraEnumerationAndroid.CaptureFormat captureFormat;
   private final long constructionTimeNs;
   private Camera1Session.SessionState state;
   private boolean firstFrameReported = false;

   public static void create(CameraSession.CreateSessionCallback callback, CameraSession.Events events, boolean captureToTexture, Context applicationContext, SurfaceTextureHelper surfaceTextureHelper, MediaRecorder mediaRecorder, int cameraId, int width, int height, int framerate) {
      long constructionTimeNs = System.nanoTime();
      Logging.d("Camera1Session", "Open camera " + cameraId);
      events.onCameraOpening();

      Camera camera;
      try {
         camera = Camera.open(cameraId);
      } catch (RuntimeException var21) {
         callback.onFailure(CameraSession.FailureType.ERROR, var21.getMessage());
         return;
      }

      try {
         camera.setPreviewTexture(surfaceTextureHelper.getSurfaceTexture());
      } catch (IOException var20) {
         camera.release();
         callback.onFailure(CameraSession.FailureType.ERROR, var20.getMessage());
         return;
      }

      CameraInfo info = new CameraInfo();
      Camera.getCameraInfo(cameraId, info);
      Parameters parameters = camera.getParameters();
      CameraEnumerationAndroid.CaptureFormat captureFormat = findClosestCaptureFormat(parameters, width, height, framerate);
      Size pictureSize = findClosestPictureSize(parameters, width, height);
      updateCameraParameters(camera, parameters, captureFormat, pictureSize, captureToTexture);
      if (!captureToTexture) {
         int frameSize = captureFormat.frameSize();

         for(int i = 0; i < 3; ++i) {
            ByteBuffer buffer = ByteBuffer.allocateDirect(frameSize);
            camera.addCallbackBuffer(buffer.array());
         }
      }

      camera.setDisplayOrientation(0);
      callback.onDone(new Camera1Session(events, captureToTexture, applicationContext, surfaceTextureHelper, mediaRecorder, cameraId, camera, info, captureFormat, constructionTimeNs));
   }

   private static void updateCameraParameters(Camera camera, Parameters parameters, CameraEnumerationAndroid.CaptureFormat captureFormat, Size pictureSize, boolean captureToTexture) {
      List<String> focusModes = parameters.getSupportedFocusModes();
      parameters.setPreviewFpsRange(captureFormat.framerate.min, captureFormat.framerate.max);
      parameters.setPreviewSize(captureFormat.width, captureFormat.height);
      parameters.setPictureSize(pictureSize.width, pictureSize.height);
      if (!captureToTexture) {
         captureFormat.getClass();
         parameters.setPreviewFormat(17);
      }

      if (parameters.isVideoStabilizationSupported()) {
         parameters.setVideoStabilization(true);
      }

      if (focusModes.contains("continuous-video")) {
         parameters.setFocusMode("continuous-video");
      }

      camera.setParameters(parameters);
   }

   private static CameraEnumerationAndroid.CaptureFormat findClosestCaptureFormat(Parameters parameters, int width, int height, int framerate) {
      List<CameraEnumerationAndroid.CaptureFormat.FramerateRange> supportedFramerates = Camera1Enumerator.convertFramerates(parameters.getSupportedPreviewFpsRange());
      Logging.d("Camera1Session", "Available fps ranges: " + supportedFramerates);
      CameraEnumerationAndroid.CaptureFormat.FramerateRange fpsRange = CameraEnumerationAndroid.getClosestSupportedFramerateRange(supportedFramerates, framerate);
      Size previewSize = CameraEnumerationAndroid.getClosestSupportedSize(Camera1Enumerator.convertSizes(parameters.getSupportedPreviewSizes()), width, height);
      CameraEnumerationAndroid.reportCameraResolution(camera1ResolutionHistogram, previewSize);
      return new CameraEnumerationAndroid.CaptureFormat(previewSize.width, previewSize.height, fpsRange);
   }

   private static Size findClosestPictureSize(Parameters parameters, int width, int height) {
      return CameraEnumerationAndroid.getClosestSupportedSize(Camera1Enumerator.convertSizes(parameters.getSupportedPictureSizes()), width, height);
   }

   private Camera1Session(CameraSession.Events events, boolean captureToTexture, Context applicationContext, SurfaceTextureHelper surfaceTextureHelper, MediaRecorder mediaRecorder, int cameraId, Camera camera, CameraInfo info, CameraEnumerationAndroid.CaptureFormat captureFormat, long constructionTimeNs) {
      Logging.d("Camera1Session", "Create new camera1 session on camera " + cameraId);
      this.cameraThreadHandler = new Handler();
      this.events = events;
      this.captureToTexture = captureToTexture;
      this.applicationContext = applicationContext;
      this.surfaceTextureHelper = surfaceTextureHelper;
      this.cameraId = cameraId;
      this.camera = camera;
      this.info = info;
      this.captureFormat = captureFormat;
      this.constructionTimeNs = constructionTimeNs;
      this.startCapturing();
      if (mediaRecorder != null) {
         camera.unlock();
         mediaRecorder.setCamera(camera);
      }

   }

   public void stop() {
      Logging.d("Camera1Session", "Stop camera1 session on camera " + this.cameraId);
      this.checkIsOnCameraThread();
      if (this.state != Camera1Session.SessionState.STOPPED) {
         long stopStartTime = System.nanoTime();
         this.stopInternal();
         int stopTimeMs = (int)TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - stopStartTime);
         camera1StopTimeMsHistogram.addSample(stopTimeMs);
      }

   }

   private void startCapturing() {
      Logging.d("Camera1Session", "Start capturing");
      this.checkIsOnCameraThread();
      this.state = Camera1Session.SessionState.RUNNING;
      this.camera.setErrorCallback(new ErrorCallback() {
         public void onError(int error, Camera camera) {
            String errorMessage;
            if (error == 100) {
               errorMessage = "Camera server died!";
            } else {
               errorMessage = "Camera error: " + error;
            }

            Logging.e("Camera1Session", errorMessage);
            Camera1Session.this.stopInternal();
            if (error == 2) {
               Camera1Session.this.events.onCameraDisconnected(Camera1Session.this);
            } else {
               Camera1Session.this.events.onCameraError(Camera1Session.this, errorMessage);
            }

         }
      });
      if (this.captureToTexture) {
         this.listenForTextureFrames();
      } else {
         this.listenForBytebufferFrames();
      }

      try {
         this.camera.startPreview();
      } catch (RuntimeException var2) {
         this.stopInternal();
         this.events.onCameraError(this, var2.getMessage());
      }

   }

   private void stopInternal() {
      Logging.d("Camera1Session", "Stop internal");
      this.checkIsOnCameraThread();
      if (this.state == Camera1Session.SessionState.STOPPED) {
         Logging.d("Camera1Session", "Camera is already stopped");
      } else {
         this.state = Camera1Session.SessionState.STOPPED;
         this.surfaceTextureHelper.stopListening();
         this.camera.stopPreview();
         this.camera.release();
         this.events.onCameraClosed(this);
         Logging.d("Camera1Session", "Stop done");
      }
   }

   private void listenForTextureFrames() {
      this.surfaceTextureHelper.startListening(new SurfaceTextureHelper.OnTextureFrameAvailableListener() {
         public void onTextureFrameAvailable(int oesTextureId, float[] transformMatrix, long timestampNs) {
            Camera1Session.this.checkIsOnCameraThread();
            if (Camera1Session.this.state != Camera1Session.SessionState.RUNNING) {
               Logging.d("Camera1Session", "Texture frame captured but camera is no longer running.");
               Camera1Session.this.surfaceTextureHelper.returnTextureFrame();
            } else {
               int rotation;
               if (!Camera1Session.this.firstFrameReported) {
                  rotation = (int)TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - Camera1Session.this.constructionTimeNs);
                  Camera1Session.camera1StartTimeMsHistogram.addSample(rotation);
                  Camera1Session.this.firstFrameReported = true;
               }

               rotation = Camera1Session.this.getFrameOrientation();
               if (Camera1Session.this.info.facing == 1) {
                  transformMatrix = RendererCommon.multiplyMatrices(transformMatrix, RendererCommon.horizontalFlipMatrix());
               }

               Camera1Session.this.events.onTextureFrameCaptured(Camera1Session.this, Camera1Session.this.captureFormat.width, Camera1Session.this.captureFormat.height, oesTextureId, transformMatrix, rotation, timestampNs);
            }
         }
      });
   }

   private void listenForBytebufferFrames() {
      this.camera.setPreviewCallbackWithBuffer(new PreviewCallback() {
         public void onPreviewFrame(byte[] data, Camera callbackCamera) {
            Camera1Session.this.checkIsOnCameraThread();
            if (callbackCamera != Camera1Session.this.camera) {
               Logging.e("Camera1Session", "Callback from a different camera. This should never happen.");
            } else if (Camera1Session.this.state != Camera1Session.SessionState.RUNNING) {
               Logging.d("Camera1Session", "Bytebuffer frame captured but camera is no longer running.");
            } else {
               long captureTimeNs = TimeUnit.MILLISECONDS.toNanos(SystemClock.elapsedRealtime());
               if (!Camera1Session.this.firstFrameReported) {
                  int startTimeMs = (int)TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - Camera1Session.this.constructionTimeNs);
                  Camera1Session.camera1StartTimeMsHistogram.addSample(startTimeMs);
                  Camera1Session.this.firstFrameReported = true;
               }

               Camera1Session.this.events.onByteBufferFrameCaptured(Camera1Session.this, data, Camera1Session.this.captureFormat.width, Camera1Session.this.captureFormat.height, Camera1Session.this.getFrameOrientation(), captureTimeNs);
               Camera1Session.this.camera.addCallbackBuffer(data);
            }
         }
      });
   }

   private int getDeviceOrientation() {
      //int orientation = false;
      WindowManager wm = (WindowManager)this.applicationContext.getSystemService("window");
      short orientation;
      switch(wm.getDefaultDisplay().getRotation()) {
      case 0:
      default:
         orientation = 0;
         break;
      case 1:
         orientation = 90;
         break;
      case 2:
         orientation = 180;
         break;
      case 3:
         orientation = 270;
      }

      return orientation;
   }

   private int getFrameOrientation() {
      int rotation = this.getDeviceOrientation();
      if (this.info.facing == 0) {
         rotation = 360 - rotation;
      }

      return (this.info.orientation + rotation) % 360;
   }

   private void checkIsOnCameraThread() {
      if (Thread.currentThread() != this.cameraThreadHandler.getLooper().getThread()) {
         throw new IllegalStateException("Wrong thread");
      }
   }

   static {
      camera1ResolutionHistogram = Histogram.createEnumeration("WebRTC.Android.Camera1.Resolution", CameraEnumerationAndroid.COMMON_RESOLUTIONS.size());
   }

   private static enum SessionState {
      RUNNING,
      STOPPED;
   }
}
