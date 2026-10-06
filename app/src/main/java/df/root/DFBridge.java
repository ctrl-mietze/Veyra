package df.root;

import android.content.Context;
import android.net.IpSecAlgorithm;
import android.net.IpSecManager;
import android.net.IpSecTransform;
import android.os.Build;
import androidx.annotation.RequiresApi;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.security.SecureRandom;

public final class DFBridge {
    private static boolean loaded;
    private static Boolean loadedNext;

    private DFBridge() {}

    public static synchronized void load(boolean next) {
        if (loaded) {
            if (loadedNext != null && loadedNext.booleanValue() != next) {
                throw new IllegalStateException("DirtyFrag flavour already loaded for this process");
            }
            return;
        }
        System.loadLibrary(next ? "dfexpnext" : "dfexp");
        loaded = true;
        loadedNext = next;
    }

    public static native int nativeRunAll(
            IReporter reporter,
            int encapsulationPort,
            int spi,
            byte[] encryptionKey,
            byte[] authenticationKey,
            int authenticationTruncationBytes,
            int localPort,
            boolean softReboot
    );
    public static int run(Context context, boolean next, boolean softReboot, IReporter reporter)
            throws Exception {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            throw new IllegalStateException("DirtyFrag requires Android 9 / API 28 or newer");
        }
        return runApi28(context, next, softReboot, reporter);
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private static int runApi28(
            Context context,
            boolean next,
            boolean softReboot,
            IReporter reporter
    ) throws Exception {
        load(next);
        stageKsud(context, next, reporter);

        IpSecManager manager = context.getSystemService(IpSecManager.class);
        if (manager == null) throw new IllegalStateException("IpSecManager unavailable");

        InetAddress loopback = InetAddress.getByName("127.0.0.1");
        try (
                IpSecManager.UdpEncapsulationSocket encap = manager.openUdpEncapsulationSocket();
                IpSecManager.SecurityParameterIndex spi = manager.allocateSecurityParameterIndex(loopback);
                DatagramSocket portSocket = new DatagramSocket()
        ) {
            int encapsulationPort = encap.getPort();
            int spiValue = spi.getSpi();
            int localPort = portSocket.getLocalPort();

            byte[] encryptionKey = new byte[32];
            byte[] authenticationKey = new byte[32];
            SecureRandom random = new SecureRandom();
            random.nextBytes(encryptionKey);
            random.nextBytes(authenticationKey);

            IpSecAlgorithm encryption = new IpSecAlgorithm("cbc(aes)", encryptionKey);
            IpSecAlgorithm authentication =
                    new IpSecAlgorithm("hmac(sha256)", authenticationKey, 128);

            try (IpSecTransform transform = new IpSecTransform.Builder(context)
                    .setEncryption(encryption)
                    .setAuthentication(authentication)
                    .setIpv4Encapsulation(encap, localPort)
                    .buildTransportModeTransform(loopback, spi)) {
                reporter.report("DF: encap=" + encapsulationPort + " spi=0x"
                        + Integer.toHexString(spiValue));
                return nativeRunAll(
                        reporter,
                        encapsulationPort,
                        spiValue,
                        encryptionKey,
                        authenticationKey,
                        16,
                        localPort,
                        next ? false : softReboot
                );
            }
        }
    }
    private static void stageKsud(Context context, boolean next, IReporter reporter) throws Exception {
        File dataDir = context.getFilesDir().getParentFile();
        if (dataDir == null) throw new IllegalStateException("App data directory unavailable");
        File destination = new File(dataDir, "ksud");
        String asset = next
                ? "local-sources/dfroot/ksud-new-next"
                : "local-sources/dfroot/ksud-new-classic";

        File temporary = new File(dataDir, "ksud.part");
        try (InputStream input = context.getAssets().open(asset);
             FileOutputStream output = new FileOutputStream(temporary, false)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) >= 0) {
                output.write(buffer, 0, count);
            }
            output.getFD().sync();
        }
        if (destination.exists() && !destination.delete()) {
            throw new IllegalStateException("Could not replace staged ksud");
        }
        if (!temporary.renameTo(destination)) {
            temporary.delete();
            throw new IllegalStateException("Could not finalize staged ksud");
        }
        if (!destination.setExecutable(true, false)) {
            throw new IllegalStateException("Could not chmod staged ksud");
        }
        reporter.report("DF: staged " + (next ? "KernelSU-Next" : "KernelSU") + " ksud");
    }
}
