import com.android.tools.smali.baksmali.*;
import com.android.tools.smali.dexlib2.*;
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile;
import java.io.*; import java.nio.file.*;
public class Dis { public static void main(String[] a) throws Exception {
  byte[] b = Files.readAllBytes(Paths.get(a[0]));
  DexBackedDexFile dex = new DexBackedDexFile(new Opcodes(23, -1), b, 0);
  BaksmaliOptions o = new BaksmaliOptions(); o.localsDirective=true; o.debugInfo=true;
  Baksmali.disassembleDexFile(dex, new File(a[1]), 1, o);
  System.out.println("done");
}}
