package org.webrtc;

import android.os.Handler;
import android.os.HandlerThread;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.concurrent.CountDownLatch;

public class VideoFileRenderer implements VideoRenderer.Callbacks {
   private static final String TAG = "VideoFileRenderer";
   private final HandlerThread renderThread;
   private final Object handlerLock = new Object();
   private final Handler renderThreadHandler;
   private final FileOutputStream videoOutFile;
   private final String outputFileName;
   private final int outputFileWidth;
   private final int outputFileHeight;
   private final int outputFrameSize;
   private final ByteBuffer outputFrameBuffer;
   private EglBase eglBase;
   private YuvConverter yuvConverter;
   private ArrayList<ByteBuffer> rawFrames = new ArrayList();

   public VideoFileRenderer(String outputFile, int outputFileWidth, int outputFileHeight, final EglBase.Context sharedContext) throws IOException {
      if (outputFileWidth % 2 != 1 && outputFileHeight % 2 != 1) {
         this.outputFileName = outputFile;
         this.outputFileWidth = outputFileWidth;
         this.outputFileHeight = outputFileHeight;
         this.outputFrameSize = outputFileWidth * outputFileHeight * 3 / 2;
         this.outputFrameBuffer = ByteBuffer.allocateDirect(this.outputFrameSize);
         this.videoOutFile = new FileOutputStream(outputFile);
         this.videoOutFile.write(("YUV4MPEG2 C420 W" + outputFileWidth + " H" + outputFileHeight + " Ip F30:1 A1:1\n").getBytes());
         this.renderThread = new HandlerThread("VideoFileRenderer");
         this.renderThread.start();
         this.renderThreadHandler = new Handler(this.renderThread.getLooper());
         ThreadUtils.invokeAtFrontUninterruptibly(this.renderThreadHandler, new Runnable() {
            public void run() {
               VideoFileRenderer.this.eglBase = EglBase.create(sharedContext, EglBase.CONFIG_PIXEL_BUFFER);
               VideoFileRenderer.this.eglBase.createDummyPbufferSurface();
               VideoFileRenderer.this.eglBase.makeCurrent();
               VideoFileRenderer.this.yuvConverter = new YuvConverter();
            }
         });
      } else {
         throw new IllegalArgumentException("Does not support uneven width or height");
      }
   }

   public void renderFrame(final VideoRenderer.I420Frame frame) {
      this.renderThreadHandler.post(new Runnable() {
         public void run() {
            VideoFileRenderer.this.renderFrameOnRenderThread(frame);
         }
      });
   }

   private void renderFrameOnRenderThread(VideoRenderer.I420Frame frame) {
      float frameAspectRatio = (float)frame.rotatedWidth() / (float)frame.rotatedHeight();
      float[] rotatedSamplingMatrix = RendererCommon.rotateTextureMatrix(frame.samplingMatrix, (float)frame.rotationDegree);
      float[] layoutMatrix = RendererCommon.getLayoutMatrix(false, frameAspectRatio, (float)this.outputFileWidth / (float)this.outputFileHeight);
      float[] texMatrix = RendererCommon.multiplyMatrices(rotatedSamplingMatrix, layoutMatrix);

      try {
         ByteBuffer buffer = nativeCreateNativeByteBuffer(this.outputFrameSize);
         if (frame.yuvFrame) {
            nativeI420Scale(frame.yuvPlanes[0], frame.yuvStrides[0], frame.yuvPlanes[1], frame.yuvStrides[1], frame.yuvPlanes[2], frame.yuvStrides[2], frame.width, frame.height, this.outputFrameBuffer, this.outputFileWidth, this.outputFileHeight);
            buffer.put(this.outputFrameBuffer.array(), this.outputFrameBuffer.arrayOffset(), this.outputFrameSize);
         } else {
            this.yuvConverter.convert(this.outputFrameBuffer, this.outputFileWidth, this.outputFileHeight, this.outputFileWidth, frame.textureId, texMatrix);
            int stride = this.outputFileWidth;
            byte[] data = this.outputFrameBuffer.array();
            int offset = this.outputFrameBuffer.arrayOffset();
            buffer.put(data, offset, this.outputFileWidth * this.outputFileHeight);

            int r;
            for(r = this.outputFileHeight; r < this.outputFileHeight * 3 / 2; ++r) {
               buffer.put(data, offset + r * stride, stride / 2);
            }

            for(r = this.outputFileHeight; r < this.outputFileHeight * 3 / 2; ++r) {
               buffer.put(data, offset + r * stride + stride / 2, stride / 2);
            }
         }

         buffer.rewind();
         this.rawFrames.add(buffer);
      } finally {
         VideoRenderer.renderFrameDone(frame);
      }

   }

   public void release() {
      final CountDownLatch cleanupBarrier = new CountDownLatch(1);
      this.renderThreadHandler.post(new Runnable() {
         public void run() {
            VideoFileRenderer.this.yuvConverter.release();
            VideoFileRenderer.this.eglBase.release();
            VideoFileRenderer.this.renderThread.quit();
            cleanupBarrier.countDown();
         }
      });
      ThreadUtils.awaitUninterruptibly(cleanupBarrier);

      try {
         Iterator var2 = this.rawFrames.iterator();

         while(var2.hasNext()) {
            ByteBuffer buffer = (ByteBuffer)var2.next();
            this.videoOutFile.write("FRAME\n".getBytes());
            byte[] data = new byte[this.outputFrameSize];
            buffer.get(data);
            this.videoOutFile.write(data);
            nativeFreeNativeByteBuffer(buffer);
         }

         this.videoOutFile.close();
         Logging.d("VideoFileRenderer", "Video written to disk as " + this.outputFileName + ". Number frames are " + this.rawFrames.size() + " and the dimension of the frames are " + this.outputFileWidth + "x" + this.outputFileHeight + ".");
      } catch (IOException var5) {
         Logging.e("VideoFileRenderer", "Error writing video to disk", var5);
      }

   }

   public static native void nativeI420Scale(ByteBuffer var0, int var1, ByteBuffer var2, int var3, ByteBuffer var4, int var5, int var6, int var7, ByteBuffer var8, int var9, int var10);

   public static native ByteBuffer nativeCreateNativeByteBuffer(int var0);

   public static native void nativeFreeNativeByteBuffer(ByteBuffer var0);

   static {
      System.loadLibrary("jingle_peerconnection_so");
   }
}
