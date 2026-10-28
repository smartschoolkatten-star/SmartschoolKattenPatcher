package be.smartschoolkatten.runtime;

import android.graphics.*;
import android.graphics.drawable.*;

/** Square intrinsic bounds and centre-cover rendering, including animated sources. */
final class CoverDrawable extends Drawable implements Drawable.Callback,Animatable {
    private final Drawable source;
    CoverDrawable(Drawable source) {this.source=source;source.setCallback(this);}
    @Override public int getIntrinsicWidth(){return Math.max(1,Math.min(source.getIntrinsicWidth(),source.getIntrinsicHeight()));}
    @Override public int getIntrinsicHeight(){return getIntrinsicWidth();}
    @Override public void draw(Canvas canvas) {
        Rect box=getBounds();if(box.isEmpty())return;
        int width=Math.max(1,source.getIntrinsicWidth()),height=Math.max(1,source.getIntrinsicHeight());
        float scale=CoverScale.factor(box.width(),box.height(),width,height);
        int renderedWidth=(int)Math.ceil(width*scale),renderedHeight=(int)Math.ceil(height*scale);
        int left=box.left+(box.width()-renderedWidth)/2,top=box.top+(box.height()-renderedHeight)/2;
        source.setBounds(left,top,left+renderedWidth,top+renderedHeight);
        int saved=canvas.save();canvas.clipRect(box);source.draw(canvas);canvas.restoreToCount(saved);
    }
    @Override public void setAlpha(int alpha){source.setAlpha(alpha);}
    @Override public void setColorFilter(ColorFilter filter){source.setColorFilter(filter);}
    @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    @Override public void invalidateDrawable(Drawable drawable){invalidateSelf();}
    @Override public void scheduleDrawable(Drawable drawable,Runnable task,long when){scheduleSelf(task,when);}
    @Override public void unscheduleDrawable(Drawable drawable,Runnable task){unscheduleSelf(task);}
    @Override public void start(){if(source instanceof Animatable)((Animatable)source).start();}
    @Override public void stop(){if(source instanceof Animatable)((Animatable)source).stop();}
    @Override public boolean isRunning(){return source instanceof Animatable && ((Animatable)source).isRunning();}
}
