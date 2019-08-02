package org.webrtc;

public class Size {
   public int width;
   public int height;

   public Size(int width, int height) {
      this.width = width;
      this.height = height;
   }

   public String toString() {
      return this.width + "x" + this.height;
   }

   public boolean equals(Object other) {
      if (!(other instanceof Size)) {
         return false;
      } else {
         Size otherSize = (Size)other;
         return this.width == otherSize.width && this.height == otherSize.height;
      }
   }

   public int hashCode() {
      return 1 + 65537 * this.width + this.height;
   }
}
