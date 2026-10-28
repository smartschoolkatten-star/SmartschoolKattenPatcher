package be.smartschoolkatten.runtime;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;
import java.lang.reflect.*;

/** Smartschool 12.3.0: replace only ConfigureCard with two matching controls. */
final class HomeSettingsBridge {
    private static final class Api {
        static final Class<?> function=type("b34"), modifier=type("hx6"), composer=type("dm1");
        static final Method androidView=method("nc5","b",function,modifier,function,composer,int.class,int.class);
        static final Method click=method("a34","invoke");
        static final Object unit=unit();
        private static Object unit() {try{return type("d5c").getField("a").get(null);}catch(Exception e){throw new IllegalStateException(e);}}
    }
    private static Class<?> type(String name) {try{return Class.forName(name);}catch(Exception e){throw new IllegalStateException(e);}}
    private static Method method(String owner,String name,Class<?>... parameters) {
        try{return type(owner).getMethod(name,parameters);}catch(Exception e){throw new IllegalStateException(e);}
    }
    private interface Callback {Object call(Object argument);}
    private static Object callback(Callback block) {
        return Proxy.newProxyInstance(Api.function.getClassLoader(),new Class<?>[]{Api.function},(proxy,method,args)->{
            if(method.getDeclaringClass()==Object.class) {
                if(method.getName().equals("hashCode"))return System.identityHashCode(proxy);
                if(method.getName().equals("equals"))return proxy==args[0];
                return "SmartschoolKattenHomeCallback";
            }
            return block.call(args[0]);
        });
    }
    static void render(Object modifier,String label,Object icon,Object action,boolean compact,Object composer,int changed,int defaults) {
        Object factory=callback(context->new SettingsRow((Context)context));
        Object update=callback(view->{((SettingsRow)view).bind(label,action,compact);return Api.unit;});
        try {
            // AndroidView occupies the original ConfigureCard slot and follows its
            // scrolling, visibility and lifecycle; no activity-wide floating button.
            Api.androidView.invoke(null,factory,modifier,update,composer,0,0);
        } catch(IllegalAccessException e) {throw new IllegalStateException(e);}
          catch(InvocationTargetException e) {
            Throwable cause=e.getCause();
            if(cause instanceof RuntimeException)throw (RuntimeException)cause;
            if(cause instanceof Error)throw (Error)cause;
            throw new IllegalStateException(cause);
        }
    }
    private static final class SettingsRow extends LinearLayout {
        final Button configure,cat;
        final float density;
        SettingsRow(Context context) {
            super(context);density=getResources().getDisplayMetrics().density;
            setOrientation(HORIZONTAL);setGravity(Gravity.CENTER_VERTICAL);
            configure=button(context);cat=button(context);
            configure.setCompoundDrawablesWithIntrinsicBounds(0x7f080ccc,0,0,0);
            configure.setCompoundDrawableTintList(android.content.res.ColorStateList.valueOf(context.getColor(0x7f060099)));
            configure.setCompoundDrawablePadding(px(8));
            addView(configure,new LayoutParams(0,px(64),1));
            LayoutParams square=new LayoutParams(px(64),px(64));square.leftMargin=px(8);
            addView(cat,square);cat.setText("🐈");cat.setTextSize(26);cat.setContentDescription("Smartschool Katten instellingen");
            cat.setOnClickListener(view->KattenRuntime.openMenu(view.getContext()));
        }
        int px(int value){return Math.round(value*density);}
        Button button(Context context) {
            Button button=new Button(context);button.setAllCaps(false);button.setTextSize(14);
            button.setMinWidth(0);button.setMinimumWidth(0);button.setPadding(px(12),0,px(12),0);
            button.setTextColor(context.getColor(0x7f060099));button.setBackgroundTintList(null);
            GradientDrawable shape=new GradientDrawable();shape.setColor(context.getColor(0x7f060097));
            shape.setStroke(Math.max(1,px(1)),context.getColor(0x7f060098));shape.setCornerRadius(px(16));
            button.setBackground(new RippleDrawable(android.content.res.ColorStateList.valueOf(0x22000000),shape,null));
            button.setStateListAnimator(null);return button;
        }
        void bind(String label,Object action,boolean compact) {
            configure.setText(label);configure.setContentDescription(label);
            configure.setOnClickListener(view->{
                try { Api.click.invoke(action); }
                catch(ReflectiveOperationException error) {android.util.Log.e("SmartschoolKatten","Startscherm instellen kon niet openen",error);}
            });
            int height=px(compact?64:100);
            configure.getLayoutParams().height=height;
            cat.getLayoutParams().height=height;cat.getLayoutParams().width=height;
            setMinimumHeight(height);configure.requestLayout();cat.requestLayout();
        }
    }
}
