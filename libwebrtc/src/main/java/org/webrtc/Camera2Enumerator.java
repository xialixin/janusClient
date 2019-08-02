package org.webrtc;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.SystemClock;
import android.os.Build.VERSION;
import android.util.AndroidException;
import android.util.Range;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

@TargetApi(21)
public class Camera2Enumerator implements CameraEnumerator {
   private static final String TAG = "Camera2Enumerator";
   private static final double NANO_SECONDS_PER_SECOND = 1.0E9D;
   private static final Map<String, List<CameraEnumerationAndroid.CaptureFormat>> cachedSupportedFormats = new HashMap();
   final Context context;
   final CameraManager cameraManager;

   public Camera2Enumerator(Context context) {
      this.context = context;
      this.cameraManager = (CameraManager)context.getSystemService("camera");
   }

   public String[] getDeviceNames() {
      try {
         return this.cameraManager.getCameraIdList();
      } catch (AndroidException var2) {
         Logging.e("Camera2Enumerator", "Camera access exception: " + var2);
         return new String[0];
      }
   }

   public boolean isFrontFacing(String deviceName) {
      CameraCharacteristics characteristics = this.getCameraCharacteristics(deviceName);
      return characteristics != null && (Integer)characteristics.get(CameraCharacteristics.LENS_FACING) == 0;
   }

   public boolean isBackFacing(String deviceName) {
      CameraCharacteristics characteristics = this.getCameraCharacteristics(deviceName);
      return characteristics != null && (Integer)characteristics.get(CameraCharacteristics.LENS_FACING) == 1;
   }

   public List<CameraEnumerationAndroid.CaptureFormat> getSupportedFormats(String deviceName) {
      return getSupportedFormats(this.context, deviceName);
   }

   public CameraVideoCapturer createCapturer(String deviceName, CameraVideoCapturer.CameraEventsHandler eventsHandler) {
      return new Camera2Capturer(this.context, deviceName, eventsHandler);
   }

   private CameraCharacteristics getCameraCharacteristics(String deviceName) {
      try {
         return this.cameraManager.getCameraCharacteristics(deviceName);
      } catch (AndroidException var3) {
         Logging.e("Camera2Enumerator", "Camera access exception: " + var3);
         return null;
      }
   }

   public static boolean isSupported(Context context) {
      if (VERSION.SDK_INT < 21) {
         return false;
      } else {
         CameraManager cameraManager = (CameraManager)context.getSystemService("camera");

         try {
            String[] cameraIds = cameraManager.getCameraIdList();
            String[] var3 = cameraIds;
            int var4 = cameraIds.length;

            for(int var5 = 0; var5 < var4; ++var5) {
               String id = var3[var5];
               CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(id);
               if ((Integer)characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL) == 2) {
                  return false;
               }
            }

            return true;
         } catch (AndroidException var8) {
            Logging.e("Camera2Enumerator", "Camera access exception: " + var8);
            return false;
         }
      }
   }

   static int getFpsUnitFactor(Range<Integer>[] fpsRanges) {
      if (fpsRanges.length == 0) {
         return 1000;
      } else {
         return (Integer)fpsRanges[0].getUpper() < 1000 ? 1000 : 1;
      }
   }

   static List<Size> getSupportedSizes(CameraCharacteristics cameraCharacteristics) {
      StreamConfigurationMap streamMap = (StreamConfigurationMap)cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
      int supportLevel = (Integer)cameraCharacteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
      android.util.Size[] nativeSizes = streamMap.getOutputSizes(SurfaceTexture.class);
      List<Size> sizes = convertSizes(nativeSizes);
      if (VERSION.SDK_INT < 22 && supportLevel == 2) {
         Rect activeArraySize = (Rect)cameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
         ArrayList<Size> filteredSizes = new ArrayList();
         Iterator var7 = sizes.iterator();

         while(var7.hasNext()) {
            Size size = (Size)var7.next();
            if (activeArraySize.width() * size.height == activeArraySize.height() * size.width) {
               filteredSizes.add(size);
            }
         }

         return filteredSizes;
      } else {
         return sizes;
      }
   }

   static List<CameraEnumerationAndroid.CaptureFormat> getSupportedFormats(Context context, String cameraId) {
      return getSupportedFormats((CameraManager)context.getSystemService("camera"), cameraId);
   }

   static List<CameraEnumerationAndroid.CaptureFormat> getSupportedFormats(CameraManager cameraManager, String cameraId) {
      synchronized(cachedSupportedFormats) {
         if (cachedSupportedFormats.containsKey(cameraId)) {
            return (List)cachedSupportedFormats.get(cameraId);
         } else {
            Logging.d("Camera2Enumerator", "Get supported formats for camera index " + cameraId + ".");
            long startTimeMs = SystemClock.elapsedRealtime();

            CameraCharacteristics cameraCharacteristics;
            try {
               cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId);
            } catch (Exception var19) {
               Logging.e("Camera2Enumerator", "getCameraCharacteristics(): " + var19);
               return new ArrayList();
            }

            StreamConfigurationMap streamMap = (StreamConfigurationMap)cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            Range<Integer>[] fpsRanges = (Range[])cameraCharacteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);
            List<CameraEnumerationAndroid.CaptureFormat.FramerateRange> framerateRanges = convertFramerates(fpsRanges, getFpsUnitFactor(fpsRanges));
            List<Size> sizes = getSupportedSizes(cameraCharacteristics);
            int defaultMaxFps = 0;

            CameraEnumerationAndroid.CaptureFormat.FramerateRange framerateRange;
            for(Iterator var11 = framerateRanges.iterator(); var11.hasNext(); defaultMaxFps = Math.max(defaultMaxFps, framerateRange.max)) {
               framerateRange = (CameraEnumerationAndroid.CaptureFormat.FramerateRange)var11.next();
            }

            List<CameraEnumerationAndroid.CaptureFormat> formatList = new ArrayList();
            Iterator var22 = sizes.iterator();

            while(var22.hasNext()) {
               Size size = (Size)var22.next();
               long minFrameDurationNs = 0L;

               try {
                  minFrameDurationNs = streamMap.getOutputMinFrameDuration(SurfaceTexture.class, new android.util.Size(size.width, size.height));
               } catch (Exception var18) {
               }

               int maxFps = minFrameDurationNs == 0L ? defaultMaxFps : (int)Math.round(1.0E9D / (double)minFrameDurationNs) * 1000;
               formatList.add(new CameraEnumerationAndroid.CaptureFormat(size.width, size.height, 0, maxFps));
               Logging.d("Camera2Enumerator", "Format: " + size.width + "x" + size.height + "@" + maxFps);
            }

            cachedSupportedFormats.put(cameraId, formatList);
            long endTimeMs = SystemClock.elapsedRealtime();
            Logging.d("Camera2Enumerator", "Get supported formats for camera index " + cameraId + " done. Time spent: " + (endTimeMs - startTimeMs) + " ms.");
            return formatList;
         }
      }
   }

   private static List<Size> convertSizes(android.util.Size[] cameraSizes) {
      List<Size> sizes = new ArrayList();
      android.util.Size[] var2 = cameraSizes;
      int var3 = cameraSizes.length;

      for(int var4 = 0; var4 < var3; ++var4) {
         android.util.Size size = var2[var4];
         sizes.add(new Size(size.getWidth(), size.getHeight()));
      }

      return sizes;
   }

   static List<CameraEnumerationAndroid.CaptureFormat.FramerateRange> convertFramerates(Range<Integer>[] arrayRanges, int unitFactor) {
      List<CameraEnumerationAndroid.CaptureFormat.FramerateRange> ranges = new ArrayList();
      Range[] var3 = arrayRanges;
      int var4 = arrayRanges.length;

      for(int var5 = 0; var5 < var4; ++var5) {
         Range<Integer> range = var3[var5];
         ranges.add(new CameraEnumerationAndroid.CaptureFormat.FramerateRange((Integer)range.getLower() * unitFactor, (Integer)range.getUpper() * unitFactor));
      }

      return ranges;
   }
}
