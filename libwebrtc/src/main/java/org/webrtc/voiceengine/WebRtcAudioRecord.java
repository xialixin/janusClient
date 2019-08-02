package org.webrtc.voiceengine;

import android.annotation.TargetApi;
import android.media.AudioRecord;
import android.os.Process;
import java.nio.ByteBuffer;
import org.webrtc.ContextUtils;
import org.webrtc.Logging;
import org.webrtc.ThreadUtils;

public class WebRtcAudioRecord {
   private static final boolean DEBUG = false;
   private static final String TAG = "WebRtcAudioRecord";
   private static final int BITS_PER_SAMPLE = 16;
   private static final int CALLBACK_BUFFER_SIZE_MS = 10;
   private static final int BUFFERS_PER_SECOND = 100;
   private static final int BUFFER_SIZE_FACTOR = 2;
   private static final long AUDIO_RECORD_THREAD_JOIN_TIMEOUT_MS = 2000L;
   private final long nativeAudioRecord;
   private WebRtcAudioEffects effects = null;
   private ByteBuffer byteBuffer;
   private AudioRecord audioRecord = null;
   private WebRtcAudioRecord.AudioRecordThread audioThread = null;
   private static volatile boolean microphoneMute = false;
   private byte[] emptyBytes;
   private static WebRtcAudioRecord.WebRtcAudioRecordErrorCallback errorCallback = null;

   public static void setErrorCallback(WebRtcAudioRecord.WebRtcAudioRecordErrorCallback errorCallback) {
      Logging.d("WebRtcAudioRecord", "Set error callback");
      WebRtcAudioRecord.errorCallback = errorCallback;
   }

   WebRtcAudioRecord(long nativeAudioRecord) {
      Logging.d("WebRtcAudioRecord", "ctor" + WebRtcAudioUtils.getThreadInfo());
      this.nativeAudioRecord = nativeAudioRecord;
      this.effects = WebRtcAudioEffects.create();
   }

   private boolean enableBuiltInAEC(boolean enable) {
      Logging.d("WebRtcAudioRecord", "enableBuiltInAEC(" + enable + ')');
      if (this.effects == null) {
         Logging.e("WebRtcAudioRecord", "Built-in AEC is not supported on this platform");
         return false;
      } else {
         return this.effects.setAEC(enable);
      }
   }

   private boolean enableBuiltInNS(boolean enable) {
      Logging.d("WebRtcAudioRecord", "enableBuiltInNS(" + enable + ')');
      if (this.effects == null) {
         Logging.e("WebRtcAudioRecord", "Built-in NS is not supported on this platform");
         return false;
      } else {
         return this.effects.setNS(enable);
      }
   }

   private int initRecording(int sampleRate, int channels) {
      Logging.d("WebRtcAudioRecord", "initRecording(sampleRate=" + sampleRate + ", channels=" + channels + ")");
      if (!WebRtcAudioUtils.hasPermission(ContextUtils.getApplicationContext(), "android.permission.RECORD_AUDIO")) {
         this.reportWebRtcAudioRecordInitError("RECORD_AUDIO permission is missing");
         return -1;
      } else if (this.audioRecord != null) {
         this.reportWebRtcAudioRecordInitError("InitRecording called twice without StopRecording.");
         return -1;
      } else {
         int bytesPerFrame = channels * 2;
         int framesPerBuffer = sampleRate / 100;
         this.byteBuffer = ByteBuffer.allocateDirect(bytesPerFrame * framesPerBuffer);
         Logging.d("WebRtcAudioRecord", "byteBuffer.capacity: " + this.byteBuffer.capacity());
         this.emptyBytes = new byte[this.byteBuffer.capacity()];
         this.nativeCacheDirectBufferAddress(this.byteBuffer, this.nativeAudioRecord);
         int channelConfig = this.channelCountToConfiguration(channels);
         int minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, 2);
         if (minBufferSize != -1 && minBufferSize != -2) {
            Logging.d("WebRtcAudioRecord", "AudioRecord.getMinBufferSize: " + minBufferSize);
            int bufferSizeInBytes = Math.max(2 * minBufferSize, this.byteBuffer.capacity());
            Logging.d("WebRtcAudioRecord", "bufferSizeInBytes: " + bufferSizeInBytes);

            try {
               this.audioRecord = new AudioRecord(7, sampleRate, channelConfig, 2, bufferSizeInBytes);
            } catch (IllegalArgumentException var9) {
               this.reportWebRtcAudioRecordInitError("AudioRecord ctor error: " + var9.getMessage());
               this.releaseAudioResources();
               return -1;
            }

            if (this.audioRecord != null && this.audioRecord.getState() == 1) {
               if (this.effects != null) {
                  this.effects.enable(this.audioRecord.getAudioSessionId());
               }

               this.logMainParameters();
               this.logMainParametersExtended();
               return framesPerBuffer;
            } else {
               this.reportWebRtcAudioRecordInitError("Failed to create a new AudioRecord instance");
               this.releaseAudioResources();
               return -1;
            }
         } else {
            this.reportWebRtcAudioRecordInitError("AudioRecord.getMinBufferSize failed: " + minBufferSize);
            return -1;
         }
      }
   }

   private boolean startRecording() {
      Logging.d("WebRtcAudioRecord", "startRecording");
      assertTrue(this.audioRecord != null);
      assertTrue(this.audioThread == null);

      try {
         this.audioRecord.startRecording();
      } catch (IllegalStateException var2) {
         this.reportWebRtcAudioRecordStartError(WebRtcAudioRecord.AudioRecordStartErrorCode.AUDIO_RECORD_START_EXCEPTION, "AudioRecord.startRecording failed: " + var2.getMessage());
         return false;
      }

      if (this.audioRecord.getRecordingState() != 3) {
         this.reportWebRtcAudioRecordStartError(WebRtcAudioRecord.AudioRecordStartErrorCode.AUDIO_RECORD_START_STATE_MISMATCH, "AudioRecord.startRecording failed - incorrect state :" + this.audioRecord.getRecordingState());
         return false;
      } else {
         this.audioThread = new WebRtcAudioRecord.AudioRecordThread("AudioRecordJavaThread");
         this.audioThread.start();
         return true;
      }
   }

   private boolean stopRecording() {
      Logging.d("WebRtcAudioRecord", "stopRecording");
      assertTrue(this.audioThread != null);
      this.audioThread.stopThread();
      if (!ThreadUtils.joinUninterruptibly(this.audioThread, 2000L)) {
         Logging.e("WebRtcAudioRecord", "Join of AudioRecordJavaThread timed out");
      }

      this.audioThread = null;
      if (this.effects != null) {
         this.effects.release();
      }

      this.releaseAudioResources();
      return true;
   }

   private void logMainParameters() {
      Logging.d("WebRtcAudioRecord", "AudioRecord: session ID: " + this.audioRecord.getAudioSessionId() + ", channels: " + this.audioRecord.getChannelCount() + ", sample rate: " + this.audioRecord.getSampleRate());
   }

   @TargetApi(23)
   private void logMainParametersExtended() {
      if (WebRtcAudioUtils.runningOnMarshmallowOrHigher()) {
         Logging.d("WebRtcAudioRecord", "AudioRecord: buffer size in frames: " + this.audioRecord.getBufferSizeInFrames());
      }

   }

   private static void assertTrue(boolean condition) {
      if (!condition) {
         throw new AssertionError("Expected condition to be true");
      }
   }

   private int channelCountToConfiguration(int channels) {
      return channels == 1 ? 16 : 12;
   }

   private native void nativeCacheDirectBufferAddress(ByteBuffer var1, long var2);

   private native void nativeDataIsRecorded(int var1, long var2);

   public static void setMicrophoneMute(boolean mute) {
      Logging.w("WebRtcAudioRecord", "setMicrophoneMute(" + mute + ")");
      microphoneMute = mute;
   }

   private void releaseAudioResources() {
      if (this.audioRecord != null) {
         this.audioRecord.release();
         this.audioRecord = null;
      }

   }

   private void reportWebRtcAudioRecordInitError(String errorMessage) {
      Logging.e("WebRtcAudioRecord", "Init recording error: " + errorMessage);
      if (errorCallback != null) {
         errorCallback.onWebRtcAudioRecordInitError(errorMessage);
      }

   }

   private void reportWebRtcAudioRecordStartError(WebRtcAudioRecord.AudioRecordStartErrorCode errorCode, String errorMessage) {
      Logging.e("WebRtcAudioRecord", "Start recording error: " + errorCode + ". " + errorMessage);
      if (errorCallback != null) {
         errorCallback.onWebRtcAudioRecordStartError(errorCode, errorMessage);
      }

   }

   private void reportWebRtcAudioRecordError(String errorMessage) {
      Logging.e("WebRtcAudioRecord", "Run-time recording error: " + errorMessage);
      if (errorCallback != null) {
         errorCallback.onWebRtcAudioRecordError(errorMessage);
      }

   }

   private class AudioRecordThread extends Thread {
      private volatile boolean keepAlive = true;

      public AudioRecordThread(String name) {
         super(name);
      }

      public void run() {
         Process.setThreadPriority(-19);
         Logging.d("WebRtcAudioRecord", "AudioRecordThread" + WebRtcAudioUtils.getThreadInfo());
         WebRtcAudioRecord.assertTrue(WebRtcAudioRecord.this.audioRecord.getRecordingState() == 3);
         long var1 = System.nanoTime();

         while(this.keepAlive) {
            int bytesRead = WebRtcAudioRecord.this.audioRecord.read(WebRtcAudioRecord.this.byteBuffer, WebRtcAudioRecord.this.byteBuffer.capacity());
            if (bytesRead == WebRtcAudioRecord.this.byteBuffer.capacity()) {
               if (WebRtcAudioRecord.microphoneMute) {
                  WebRtcAudioRecord.this.byteBuffer.clear();
                  WebRtcAudioRecord.this.byteBuffer.put(WebRtcAudioRecord.this.emptyBytes);
               }

               WebRtcAudioRecord.this.nativeDataIsRecorded(bytesRead, WebRtcAudioRecord.this.nativeAudioRecord);
            } else {
               String errorMessage = "AudioRecord.read failed: " + bytesRead;
               Logging.e("WebRtcAudioRecord", errorMessage);
               if (bytesRead == -3) {
                  this.keepAlive = false;
                  WebRtcAudioRecord.this.reportWebRtcAudioRecordError(errorMessage);
               }
            }
         }

         try {
            if (WebRtcAudioRecord.this.audioRecord != null) {
               WebRtcAudioRecord.this.audioRecord.stop();
            }
         } catch (IllegalStateException var5) {
            Logging.e("WebRtcAudioRecord", "AudioRecord.stop failed: " + var5.getMessage());
         }

      }

      public void stopThread() {
         Logging.d("WebRtcAudioRecord", "stopThread");
         this.keepAlive = false;
      }
   }

   public interface WebRtcAudioRecordErrorCallback {
      void onWebRtcAudioRecordInitError(String var1);

      void onWebRtcAudioRecordStartError(WebRtcAudioRecord.AudioRecordStartErrorCode var1, String var2);

      void onWebRtcAudioRecordError(String var1);
   }

   public static enum AudioRecordStartErrorCode {
      AUDIO_RECORD_START_EXCEPTION,
      AUDIO_RECORD_START_STATE_MISMATCH;
   }
}
