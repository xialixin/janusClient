package org.webrtc;

import java.util.Iterator;
import java.util.Map;

public class RTCStatsReport {
   private final long timestampUs;
   private final Map<String, RTCStats> stats;

   public RTCStatsReport(long timestampUs, Map<String, RTCStats> stats) {
      this.timestampUs = timestampUs;
      this.stats = stats;
   }

   public double getTimestampUs() {
      return (double)this.timestampUs;
   }

   public Map<String, RTCStats> getStatsMap() {
      return this.stats;
   }

   public String toString() {
      StringBuilder builder = new StringBuilder();
      builder.append("{ timestampUs: ").append(this.timestampUs).append(", stats: [\n");
      boolean first = true;

      for(Iterator var3 = this.stats.values().iterator(); var3.hasNext(); first = false) {
         RTCStats stat = (RTCStats)var3.next();
         if (!first) {
            builder.append(",\n");
         }

         builder.append(stat);
      }

      builder.append(" ] }");
      return builder.toString();
   }
}
