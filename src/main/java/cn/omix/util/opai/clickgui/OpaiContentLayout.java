package cn.omix.util.opai.clickgui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static cn.omix.util.opai.clickgui.OpaiLayout.*;

/** One content coordinate system for rendering, scrolling and input. */
public final class OpaiContentLayout<T> {
   public static final float BOOLEAN_H = 19.6f;
   public static final float NUMBER_H = 20.0f;
   public static final float MODE_H = 40.0f;
   public static final float OPTION_H = 20.0f;
   public static final float PADDING_H = 0.0f;
   public static final float FIELD_Y = 16.0f;
   public static final float FIELD_H = 21.0f;
   public static final float SLIDER_Y = 15.6f;

   public enum Kind { MODULE, BOOLEAN, NUMBER, MODE, OPTION, PADDING }

   public record Entry<T>(T target, Kind kind, float top, float height, int option, float clipTop, float clipBottom) {
      public float visibleTop() { return Math.max(top, clipTop); }
      public float visibleBottom() { return Math.min(top + height, clipBottom); }
      public boolean visible() { return visibleBottom() > visibleTop(); }
   }

   public record Rect(float x, float y, float width, float height) {
      public boolean contains(double mouseX, double mouseY) {
         return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
      }
   }

   public record Scrollbar(float top, float height, float travel) {
   }

   private final List<Entry<T>> entries = new ArrayList<>();
   private float height;

   public void add(T target, Kind kind) {
      add(target, kind, 1.0f);
   }

   public void add(T target, Kind kind, float scale) {
      float entryHeight = switch (kind) {
         case MODULE -> ROW_H;
         case BOOLEAN -> BOOLEAN_H;
         case NUMBER -> NUMBER_H;
         case MODE -> MODE_H;
         case OPTION -> OPTION_H;
         case PADDING -> PADDING_H;
      } * scale;
      append(target, kind, entryHeight, -1);
   }

   public void addOptions(T target, int count) {
      addOptions(target, count, 1.0f);
   }

   public void addOptions(T target, int count, float scale) {
      for (int i = 0; i < count; i++) {
         append(target, Kind.OPTION, OPTION_H * scale, i);
      }
   }

   private void append(T target, Kind kind, float entryHeight, int option) {
      entries.add(new Entry<>(target, kind, height, entryHeight, option, height, height + entryHeight));
      height += entryHeight;
   }

   /** Reveal an intact section through one animated clip, rather than squashing each control. */
   public void reveal(OpaiContentLayout<T> section, float progress) {
      float occupied = section.height * Math.clamp(progress, 0, 1);
      if (occupied <= 0) return;
      float start = this.height;
      for (Entry<T> item : section.entries) {
         this.entries.add(new Entry<>(item.target, item.kind, start + item.top, item.height, item.option,
            start + item.clipTop, Math.min(start + item.clipBottom, start + occupied)));
      }
      this.height += occupied;
   }

   public List<Entry<T>> entries() {
      return Collections.unmodifiableList(entries);
   }

   public float height() {
      return height;
   }

   public float bodyHeight(float maximum) {
      return Math.min(maximum, height + FOOTER_H);
   }

   public float maxScroll(float bodyHeight) {
      return Math.max(0, height - (bodyHeight - FOOTER_H));
   }

   public Entry<T> hit(double localY, float scroll, float bodyHeight) {
      if (localY < 0 || localY >= bodyHeight - FOOTER_H) {
         return null;
      }
      double contentY = localY + scroll;
      for (Entry<T> entry : entries) {
         if (contentY >= entry.visibleTop() && contentY < entry.visibleBottom()) {
            return entry.kind == Kind.PADDING ? null : entry;
         }
      }
      return null;
   }

   public static Rect field(float x, float y, float width) {
      return new Rect(x + TEXT_PAD - 2, y + FIELD_Y, width - TEXT_PAD * 2 + 4, FIELD_H);
   }

   public static Rect sliderHit(float x, float y, float width) {
      return new Rect(x + TEXT_PAD - 3, y + SLIDER_Y - 6, width - TEXT_PAD * 2 + 6, 12);
   }

   public static double sliderFraction(double mouseX, float columnX, float columnWidth) {
      return Math.clamp((mouseX - columnX - TEXT_PAD) / Math.max(1, columnWidth - TEXT_PAD * 2), 0, 1);
   }

   public static Scrollbar scrollbar(float bodyHeight, float contentHeight, float scroll) {
      float visible = Math.max(0, bodyHeight - FOOTER_H);
      float track = Math.max(0, visible - 4);
      float thumb = Math.min(track, Math.max(12, track * visible / Math.max(1, contentHeight)));
      float travel = track - thumb;
      float maximum = Math.max(0, contentHeight - visible);
      return new Scrollbar(2 + (maximum == 0 ? 0 : travel * Math.clamp(scroll / maximum, 0, 1)), thumb, travel);
   }

   public static float scrollFromThumb(float thumbTop, float bodyHeight, float contentHeight) {
      Scrollbar bar = scrollbar(bodyHeight, contentHeight, 0);
      float maximum = Math.max(0, contentHeight - (bodyHeight - FOOTER_H));
      return bar.travel == 0 ? 0 : Math.clamp((thumbTop - 2) / bar.travel, 0, 1) * maximum;
   }
}
