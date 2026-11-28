



import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import javax.crypto.Cipher;

/**
 * v1.70: the one-time permission without a computer. Fire TV (and some Google TVs) hide outside screensavers;
 * with "ADB debugging" switched on in the TV's Developer options, the TV listens for debugging on this
 * very device (127.0.0.1:5555). The app connects to itself, the TV asks once "Allow USB debugging?",
 * and the app grants itself WRITE_SECURE_SETTINGS, sets itself as the screensaver, and switches
 * debugging off again. A minimal ADB client: connect, authenticate with a key kept in the app, run one shell line.
 */
class LocalAdb {
    private LocalAdb() { }

    private static final int CNXN = 0x4e584e43, AUTH = 0x48545541, OPEN = 0x4e45504f, OKAY = 0x59414b4f,
            CLSE = 0x45534c43, WRTE = 0x45545257;

    /** Runs one shell command line on this TV; returns its output. Throws if debugging is off or refused. */
    static String shell(File c, String command) throws Exception {
        KeyPair keys = keys(c);
        Socket s = new Socket();
        try {
            s.connect(new InetSocketAddress("127.0.0.1", 5555), 3000);
            s.setSoTimeout(65000);   // the TV's "Allow USB debugging?" question waits for the person
            InputStream in = s.getInputStream();
            OutputStream out = s.getOutputStream();
            send(out, CNXN, 0x01000000, 4096, "host::\0".getBytes("UTF-8"));
            boolean signed = false;
            int[] h = new int[6];
            byte[] d;
            while (true) {
                d = read(in, h);
                if (h[0] == CNXN) break;
                if (h[0] != AUTH || h[1] != 1) throw new Exception("unexpected answer");
                if (!signed) { send(out, AUTH, 2, 0, sign(keys.getPrivate(), d)); signed = true; }
                else send(out, AUTH, 3, 0, publicKey((RSAPublicKey) keys.getPublic()));   // the TV asks the person
            }
            send(out, OPEN, 1, 0, ("shell:" + command + "\0").getBytes("UTF-8"));
            ByteArrayOutputStream text = new ByteArrayOutputStream();
            int remote = 0;
            while (true) {
                d = read(in, h);
                if (h[0] == OKAY) { remote = h[1]; continue; }
                if (h[0] == WRTE) { text.write(d); send(out, OKAY, 1, h[1], new byte[0]); if (text.size() > 65536) break; continue; }
                if (h[0] == CLSE) break;
            }
            try { send(out, CLSE, 1, remote, new byte[0]); } catch (Throwable ignored) { }
            return text.toString("UTF-8");
        } finally {
            try { s.close(); } catch (Throwable ignored) { }
        }
    }

    /** True when the TV accepts debugging connections right now (ADB debugging is on). */
    static boolean listening() {
        Socket s = new Socket();
        try { s.connect(new InetSocketAddress("127.0.0.1", 5555), 1500); return true; }
        catch (Throwable e) { return false; }
        finally { try { s.close(); } catch (Throwable ignored) { } }
    }

    private static void send(OutputStream out, int cmd, int a0, int a1, byte[] data) throws Exception {
        ByteBuffer b = ByteBuffer.allocate(24 + data.length).order(ByteOrder.LITTLE_ENDIAN);
        int sum = 0;
        for (byte x : data) sum += x & 0xff;
        b.putInt(cmd).putInt(a0).putInt(a1).putInt(data.length).putInt(sum).putInt(~cmd).put(data);
        out.write(b.array());
        out.flush();
    }

    private static byte[] read(InputStream in, int[] h) throws Exception {
        byte[] head = full(in, 24);
        ByteBuffer b = ByteBuffer.wrap(head).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < 6; i++) h[i] = b.getInt();
        if (h[3] < 0 || h[3] > 1 << 20) throw new Exception("bad length");
        return full(in, h[3]);
    }

    private static byte[] full(InputStream in, int n) throws Exception {
        byte[] r = new byte[n];
        for (int o = 0; o < n; ) { int k = in.read(r, o, n - o); if (k < 0) throw new Exception("closed"); o += k; }
        return r;
    }

    /** adbd checks an RSA PKCS#1 signature with the token taken as a SHA-1 digest. */
    private static byte[] sign(PrivateKey k, byte[] token) throws Exception {
        byte[] info = {0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e, 0x03, 0x02, 0x1a, 0x05, 0x00, 0x04, 0x14};
        byte[] m = new byte[256];   // 00 01 FF..FF 00 DigestInfo token, padded by hand (same on every Android version)
        m[1] = 1;
        int start = m.length - info.length - token.length;
        for (int i = 2; i < start - 1; i++) m[i] = (byte) 0xff;
        System.arraycopy(info, 0, m, start, info.length);
        System.arraycopy(token, 0, m, start + info.length, token.length);
        Cipher c = Cipher.getInstance("RSA/ECB/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, k);
        return c.doFinal(m);
    }

    /** The public key in Android's own format (what adb writes to adbkey.pub), plus a name the TV shows. */
    static byte[] publicKey(RSAPublicKey pub) throws Exception {
        final int words = 64;   // 2048 bits
        BigInteger n = pub.getModulus(), r32 = BigInteger.ONE.shiftLeft(32);
        BigInteger rr = BigInteger.ONE.shiftLeft(4096).mod(n);
        long n0inv = n.mod(r32).modInverse(r32).negate().mod(r32).longValue();
        ByteBuffer b = ByteBuffer.allocate(4 + 4 + words * 8 + 4).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(words).putInt((int) n0inv);
        for (int i = 0; i < words; i++) b.putInt(n.shiftRight(32 * i).mod(r32).intValue());
        for (int i = 0; i < words; i++) b.putInt(rr.shiftRight(32 * i).mod(r32).intValue());
        b.putInt(pub.getPublicExponent().intValue());
        String text = java.util.Base64.getEncoder().encodeToString(b.array()) + " TV Bible Screensaver\0";
        return text.getBytes("UTF-8");
    }

    /** One key per install, kept in the app's private files, so "Always allow" on the TV keeps working. */
    private static synchronized KeyPair keys(File c) throws Exception {
        File pv = new File(c, "adbkey"), pb = new File(c, "adbkey.pub");
        KeyFactory f = KeyFactory.getInstance("RSA");
        if (pv.length() > 0 && pb.length() > 0) {
            try {
                return new KeyPair(f.generatePublic(new X509EncodedKeySpec(bytes(pb))), f.generatePrivate(new PKCS8EncodedKeySpec(bytes(pv))));
            } catch (Throwable ignored) { }
        }
        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
        g.initialize(2048);
        KeyPair k = g.generateKeyPair();
        write(pv, k.getPrivate().getEncoded());
        write(pb, k.getPublic().getEncoded());
        return k;
    }

    private static byte[] bytes(File f) throws Exception {
        FileInputStream in = new FileInputStream(f);
        try { return full(in, (int) f.length()); } finally { in.close(); }
    }

    private static void write(File f, byte[] d) throws Exception {
        FileOutputStream o = new FileOutputStream(f);
        try { o.write(d); } finally { o.close(); }
    }
}

/** CI check of the TV app's on-device ADB client: java Main keydir pub | java Main keydir run "<command>" */
class Main { public static void main(String[] a) throws Exception {
  File d = new File(a[0]); d.mkdirs();
  if (a[1].equals("pub")) { java.lang.reflect.Method m = LocalAdb.class.getDeclaredMethod("keys", File.class); m.setAccessible(true);
    java.security.KeyPair k = (java.security.KeyPair) m.invoke(null, d);
    String s = new String(LocalAdb.publicKey((java.security.interfaces.RSAPublicKey) k.getPublic()), "UTF-8"); System.out.println(s.substring(0, s.length() - 1)); }
  else System.out.print(LocalAdb.shell(d, a[2]));
}}