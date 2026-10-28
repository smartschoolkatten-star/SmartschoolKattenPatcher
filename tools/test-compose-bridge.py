#!/usr/bin/env python3
"""Check reflective forwarding and cat-only Crop/default overrides without Android UI."""
import argparse
from pathlib import Path
import subprocess
import tempfile

parser = argparse.ArgumentParser()
parser.add_argument("--java-home", type=Path, required=True)
args = parser.parse_args()
root = Path(__file__).resolve().parent.parent
with tempfile.TemporaryDirectory(prefix="katten-compose-test-") as directory:
    work = Path(directory)
    for name in ["hx6", "b34", "hn", "sz1", "cx0", "dm1"]:
        (work / f"{name}.java").write_text(f"public class {name} {{}}")
    (work / "qz1.java").write_text("public class qz1 { public static final sz1 a=new sz1(); }")
    (work / "yx5.java").write_text('''public class yx5 {
        public static Object[] received;
        public static void a(Object model,String description,hx6 modifier,b34 callback,
                hn alignment,sz1 scale,float alpha,cx0 filter,dm1 composer,int changed,int defaults) {
            received=new Object[]{model,description,modifier,callback,alignment,scale,
                alpha,filter,composer,changed,defaults};
        }
    }''')
    (work / "ComposeBridgeTest.java").write_text('''package be.smartschoolkatten.runtime;
    public class ComposeBridgeTest {
        public static void main(String[] args)throws Exception {
            Object modifier=Class.forName("hx6").getConstructor().newInstance();
            Object callback=Class.forName("b34").getConstructor().newInstance();
            Object alignment=Class.forName("hn").getConstructor().newInstance();
            Object fit=Class.forName("sz1").getConstructor().newInstance();
            Object filter=Class.forName("cx0").getConstructor().newInstance();
            Object composer=Class.forName("dm1").getConstructor().newInstance();
            Object model=new Object();String description="test-image";
            int changed=0x12345678,defaults=0x7ff;
            ComposeImageBridge.render(model,description,modifier,callback,alignment,fit,
                0.75f,filter,composer,changed,defaults,false);
            Object[] passed=(Object[])Class.forName("yx5").getField("received").get(null);
            if(passed[0]!=model || passed[1]!=description || passed[2]!=modifier || passed[3]!=callback
                || passed[4]!=alignment || passed[5]!=fit || !passed[6].equals(0.75f)
                || passed[7]!=filter || passed[8]!=composer || !passed[9].equals(changed)
                || !passed[10].equals(defaults))throw new AssertionError("Non-cat arguments changed");
            ComposeImageBridge.render(model,description,modifier,callback,alignment,fit,
                0.75f,filter,composer,changed,defaults,true);
            passed=(Object[])Class.forName("yx5").getField("received").get(null);
            Object crop=Class.forName("qz1").getField("a").get(null);
            if(passed[5]!=crop || !passed[10].equals(defaults & ~64))throw new AssertionError("Crop/default override failed");
            int flags=(Integer)passed[9];
            if((flags & 0x70000)!=0x40000 || (flags & ~0x70000)!=(changed & ~0x70000))
                throw new AssertionError("Other composition dirty bits changed");
            if(passed[0]!=model || passed[2]!=modifier || passed[3]!=callback || passed[4]!=alignment
                || !passed[6].equals(0.75f) || passed[7]!=filter || passed[8]!=composer)
                throw new AssertionError("Cat request or composition changed");
            System.out.println("PASS reflective Compose forwarding: non-cats unchanged; cats use Crop; request, animation path and unrelated flags preserved");
        }
    }''')
    classes = work / "classes"
    subprocess.run([str(args.java_home / "bin/javac"), "--release", "11", "-d", str(classes),
        str(root / "patch-runtime/src/be/smartschoolkatten/runtime/ComposeImageBridge.java"),
        *map(str, work.glob("*.java"))], check=True)
    subprocess.run([str(args.java_home / "bin/java"), "-cp", str(classes),
        "be.smartschoolkatten.runtime.ComposeBridgeTest"], check=True)
