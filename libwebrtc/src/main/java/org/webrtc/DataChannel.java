package org.webrtc;

import java.nio.ByteBuffer;

public class DataChannel {
   private final long nativeDataChannel;
   private long nativeObserver;

   public DataChannel(long nativeDataChannel) {
      this.nativeDataChannel = nativeDataChannel;
   }

   public void registerObserver(DataChannel.Observer observer) {
      if (this.nativeObserver != 0L) {
         this.unregisterObserverNative(this.nativeObserver);
      }

      this.nativeObserver = this.registerObserverNative(observer);
   }

   private native long registerObserverNative(DataChannel.Observer var1);

   public void unregisterObserver() {
      this.unregisterObserverNative(this.nativeObserver);
   }

   private native void unregisterObserverNative(long var1);

   public native String label();

   public native int id();

   public native DataChannel.State state();

   public native long bufferedAmount();

   public native void close();

   public boolean send(DataChannel.Buffer buffer) {
      byte[] data = new byte[buffer.data.remaining()];
      buffer.data.get(data);
      return this.sendNative(data, buffer.binary);
   }

   private native boolean sendNative(byte[] var1, boolean var2);

   public native void dispose();

   public static enum State {
      CONNECTING,
      OPEN,
      CLOSING,
      CLOSED;
   }

   public interface Observer {
      void onBufferedAmountChange(long var1);

      void onStateChange();

      void onMessage(DataChannel.Buffer var1);
   }

   public static class Buffer {
      public final ByteBuffer data;
      public final boolean binary;

      public Buffer(ByteBuffer data, boolean binary) {
         this.data = data;
         this.binary = binary;
      }
   }

   public static class Init {
      public boolean ordered = true;
      public int maxRetransmitTimeMs = -1;
      public int maxRetransmits = -1;
      public String protocol = "";
      public boolean negotiated = false;
      public int id = -1;

      public Init() {
      }

      private Init(boolean ordered, int maxRetransmitTimeMs, int maxRetransmits, String protocol, boolean negotiated, int id) {
         this.ordered = ordered;
         this.maxRetransmitTimeMs = maxRetransmitTimeMs;
         this.maxRetransmits = maxRetransmits;
         this.protocol = protocol;
         this.negotiated = negotiated;
         this.id = id;
      }
   }
}
