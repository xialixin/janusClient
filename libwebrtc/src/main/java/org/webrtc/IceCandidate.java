package org.webrtc;

public class IceCandidate {
   public final String sdpMid;
   public final int sdpMLineIndex;
   public final String sdp;
   public final String serverUrl;

   public IceCandidate(String sdpMid, int sdpMLineIndex, String sdp) {
      this.sdpMid = sdpMid;
      this.sdpMLineIndex = sdpMLineIndex;
      this.sdp = sdp;
      this.serverUrl = "";
   }

   private IceCandidate(String sdpMid, int sdpMLineIndex, String sdp, String serverUrl) {
      this.sdpMid = sdpMid;
      this.sdpMLineIndex = sdpMLineIndex;
      this.sdp = sdp;
      this.serverUrl = serverUrl;
   }

   public String toString() {
      return this.sdpMid + ":" + this.sdpMLineIndex + ":" + this.sdp + ":" + this.serverUrl;
   }
}
