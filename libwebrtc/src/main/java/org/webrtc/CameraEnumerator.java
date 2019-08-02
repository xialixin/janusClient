package org.webrtc;

import java.util.List;

public interface CameraEnumerator {
   String[] getDeviceNames();

   boolean isFrontFacing(String var1);

   boolean isBackFacing(String var1);

   List<CameraEnumerationAndroid.CaptureFormat> getSupportedFormats(String var1);

   CameraVideoCapturer createCapturer(String var1, CameraVideoCapturer.CameraEventsHandler var2);
}
