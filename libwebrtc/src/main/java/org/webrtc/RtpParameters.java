package org.webrtc;

import java.util.LinkedList;

public class RtpParameters {
   public final LinkedList<RtpParameters.Encoding> encodings = new LinkedList();
   public final LinkedList<RtpParameters.Codec> codecs = new LinkedList();

   public static class Codec {
      public int payloadType;
      public String name;
      MediaStreamTrack.MediaType kind;
      public Integer clockRate;
      public Integer numChannels;
   }

   public static class Encoding {
      public boolean active = true;
      public Integer maxBitrateBps;
      public Long ssrc;
   }
}
