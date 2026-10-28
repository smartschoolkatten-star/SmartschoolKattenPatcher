package be.smartschoolkatten.runtime;

import java.lang.reflect.*;

/** Keep Coil's request, composition and animation; change only cat content scaling. */
final class ComposeImageBridge {
    private ComposeImageBridge() {}
    private static final class Api {
        static final Method render;
        static final Object crop;
        static {
            try {
                render=Class.forName("yx5").getMethod("a",Object.class,String.class,
                    Class.forName("hx6"),Class.forName("b34"),Class.forName("hn"),
                    Class.forName("sz1"),float.class,Class.forName("cx0"),
                    Class.forName("dm1"),int.class,int.class);
                crop=Class.forName("qz1").getField("a").get(null);
            } catch(ReflectiveOperationException error) {throw new ExceptionInInitializerError(error);}
        }
    }
    static void render(Object model,String description,Object modifier,Object callback,
            Object alignment,Object scale,float alpha,Object filter,Object composer,
            int changed,int defaults,boolean cat) {
        if(cat) {
            scale=Api.crop;
            // ContentScale is argument 5. Override its default and dirty bits so
            // a reused composition cannot retain the preceding Fit value.
            defaults &= ~64;
            changed=(changed & ~0x70000) | 0x40000;
        }
        try {Api.render.invoke(null,model,description,modifier,callback,alignment,scale,
            alpha,filter,composer,changed,defaults);}
        catch(ReflectiveOperationException error) {throw new IllegalStateException("Compose-afbeelding laden mislukt",error);}
    }
}
