package ctrl.mietze.veyraroot;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.core.content.FileProvider;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

public final class PublicGateActivity extends Activity {
    private static final int KEY = 0x5A;
    private static final int[] UPDATE_CHANNEL = {
            50,46,46,42,41,96,117,117,40,59,45,116,61,51,46,50,47,56,47,41,
            63,40,57,53,52,46,63,52,46,116,57,53,55,117,57,46,40,54,119,55,
            51,63,46,32,63,117,12,63,35,40,59,117,55,59,51,52,117,47,42,62,
            59,46,63,117,57,50,59,52,52,63,54,116,48,41,53,52
    };

    private TextView status;
    private ProgressBar progress;
    private Button retry;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        showCheckingUi();
        checkInBackground();
    }

    private void showCheckingUi() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        int pad = dp(24);
        box.setPadding(pad, dp(48), pad, dp(24));

        TextView title = new TextView(this);
        title.setText("Veyra Root 2.0");
        title.setTextSize(26f);
        title.setGravity(Gravity.CENTER);
        box.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        TextView channel = new TextView(this);
        channel.setText("Public Release");
        channel.setTextSize(14f);
        channel.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams channelLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        channelLp.topMargin = dp(4);
        box.addView(channel, channelLp);

        progress = new ProgressBar(this);
        LinearLayout.LayoutParams progressLp = new LinearLayout.LayoutParams(dp(36), dp(36));
        progressLp.topMargin = dp(36);
        box.addView(progress, progressLp);

        status = new TextView(this);
        status.setText("Checking the public update channel…");
        status.setTextSize(15f);
        status.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams statusLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        statusLp.topMargin = dp(18);
        box.addView(status, statusLp);

        retry = new Button(this);
        retry.setText("Retry");
        retry.setVisibility(Button.GONE);
        retry.setOnClickListener(v -> {
            retry.setVisibility(Button.GONE);
            progress.setVisibility(ProgressBar.VISIBLE);
            status.setText("Checking the public update channel…");
            checkInBackground();
        });
        LinearLayout.LayoutParams retryLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        retryLp.topMargin = dp(16);
        box.addView(retry, retryLp);

        setContentView(box);
    }

    private void checkInBackground() {
        new Thread(() -> {
            try {
                UpdateMeta meta = fetchMetadata();
                if (meta.versionCode > BuildConfig.VERSION_CODE) {
                    runOnUiThread(() -> showRequiredUpdate(meta));
                } else {
                    launchMain();
                }
            } catch (Throwable offlineOrUnavailable) {
                // Requirement is online enforcement. Offline use remains possible.
                launchMain();
            }
        }, "VeyraPublicUpdateCheck").start();
    }

    private void launchMain() {
        runOnUiThread(() -> {
            Intent intent = new Intent(this, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        });
    }

    private void showRequiredUpdate(UpdateMeta meta) {
        progress.setVisibility(ProgressBar.GONE);
        status.setText(
                "Update required: " + BuildConfig.VERSION_CODE + " → " + meta.versionCode
        );

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Veyra Root update required")
                .setMessage(
                        "Veyra Root " + meta.versionName +
                        " is published. Public Release builds must update when GitHub reports a newer versionCode.\n\n" +
                        "The downloaded APK is verified before Android is allowed to install it."
                )
                .setCancelable(false)
                .setPositiveButton("Update now", null)
                .create();

        dialog.setOnShowListener(ignored -> {
            Button update = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            update.setOnClickListener(v -> {
                update.setEnabled(false);
                status.setText("Downloading and verifying Veyra Root " + meta.versionName + "…");
                progress.setVisibility(ProgressBar.VISIBLE);
                downloadInBackground(meta, dialog, update);
            });
        });
        dialog.show();
    }

    private void downloadInBackground(UpdateMeta meta, AlertDialog dialog, Button button) {
        new Thread(() -> {
            try {
                if (blank(meta.apkUrl)) {
                    if (!blank(meta.releaseUrl)) {
                        runOnUiThread(() -> {
                            progress.setVisibility(ProgressBar.GONE);
                            button.setEnabled(true);
                            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(meta.releaseUrl)));
                        });
                        return;
                    }
                    throw new IllegalStateException("No APK URL is published for this update.");
                }

                File apk = downloadApk(meta);
                verifyApk(apk, meta);

                runOnUiThread(() -> {
                    progress.setVisibility(ProgressBar.GONE);
                    status.setText("Verified. Opening Android package installer…");
                    Uri uri = FileProvider.getUriForFile(
                            this,
                            getPackageName() + ".fileprovider",
                            apk
                    );
                    Intent install = new Intent(Intent.ACTION_VIEW)
                            .setDataAndType(uri, "application/vnd.android.package-archive")
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(install);
                });
            } catch (Throwable error) {
                runOnUiThread(() -> {
                    progress.setVisibility(ProgressBar.GONE);
                    status.setText("Update failed: " + safeMessage(error));
                    button.setEnabled(true);
                });
            }
        }, "VeyraPublicUpdateDownload").start();
    }

    private UpdateMeta fetchMetadata() throws Exception {
        JSONObject root = new JSONObject(fetchText(decode(UPDATE_CHANNEL), 128 * 1024));
        if (root.optInt("schemaVersion", 0) != 1) {
            throw new IllegalStateException("Unsupported update metadata.");
        }
        long code = root.optLong("versionCode", 0L);
        if (code <= 0L) throw new IllegalStateException("Invalid versionCode.");
        String packageName = root.optString("packageName", BuildConfig.APPLICATION_ID);
        if (!BuildConfig.APPLICATION_ID.equals(packageName)) {
            throw new IllegalStateException("Update channel package mismatch.");
        }
        UpdateMeta meta = new UpdateMeta();
        meta.versionCode = code;
        meta.versionName = root.optString("versionName", Long.toString(code));
        meta.apkUrl = httpsOrBlank(root.optString("apkUrl", ""));
        meta.releaseUrl = httpsOrBlank(root.optString("releaseUrl", ""));
        meta.sha256 = root.optString("sha256", "").trim().toLowerCase(Locale.ROOT);
        meta.signerSha256 = root.optString("signerSha256", "").trim().toLowerCase(Locale.ROOT);
        return meta;
    }

    private File downloadApk(UpdateMeta meta) throws Exception {
        File dir = new File(getCacheDir(), "updates");
        if (!dir.mkdirs() && !dir.isDirectory()) {
            throw new IllegalStateException("Could not create update cache.");
        }
        File apk = new File(dir, "VeyraRoot-" + meta.versionCode + ".apk");
        if (apk.exists() && !apk.delete()) {
            throw new IllegalStateException("Could not replace cached update.");
        }

        HttpURLConnection c = open(meta.apkUrl);
        try {
            int statusCode = c.getResponseCode();
            if (statusCode < 200 || statusCode > 299) {
                throw new IllegalStateException("Update download HTTP " + statusCode);
            }
            long declared = c.getContentLengthLong();
            if (declared > 256L * 1024L * 1024L) {
                throw new IllegalStateException("Update APK is too large.");
            }

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long total = 0L;
            byte[] buffer = new byte[128 * 1024];
            try (InputStream in = c.getInputStream();
                 FileOutputStream out = new FileOutputStream(apk, false)) {
                int read;
                while ((read = in.read(buffer)) >= 0) {
                    if (read == 0) continue;
                    total += read;
                    if (total > 256L * 1024L * 1024L) {
                        throw new IllegalStateException("Update APK exceeds size limit.");
                    }
                    digest.update(buffer, 0, read);
                    out.write(buffer, 0, read);
                }
                out.getFD().sync();
            }

            if (total <= 0L) throw new IllegalStateException("Downloaded APK is empty.");
            String actual = toHex(digest.digest());
            if (!blank(meta.sha256) && !constantAscii(actual, meta.sha256)) {
                apk.delete();
                throw new IllegalStateException("Update SHA-256 mismatch.");
            }
            return apk;
        } catch (Throwable error) {
            apk.delete();
            throw error;
        } finally {
            c.disconnect();
        }
    }

    @SuppressWarnings("deprecation")
    private void verifyApk(File apk, UpdateMeta meta) throws Exception {
        PackageManager pm = getPackageManager();
        int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                ? PackageManager.GET_SIGNING_CERTIFICATES
                : PackageManager.GET_SIGNATURES;

        PackageInfo archive = pm.getPackageArchiveInfo(apk.getAbsolutePath(), flags);
        if (archive == null) throw new IllegalStateException("Downloaded file is not a valid APK.");
        if (!BuildConfig.APPLICATION_ID.equals(archive.packageName)) {
            throw new IllegalStateException("Downloaded APK package mismatch.");
        }

        long archiveCode = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                ? archive.getLongVersionCode()
                : archive.versionCode;
        if (archiveCode != meta.versionCode) {
            throw new IllegalStateException("Downloaded APK versionCode mismatch.");
        }

        PackageInfo installed = pm.getPackageInfo(getPackageName(), flags);
        Set<String> installedSigners = signerDigests(installed);
        Set<String> archiveSigners = signerDigests(archive);
        if (archiveSigners.isEmpty() || installedSigners.isEmpty()) {
            throw new IllegalStateException("APK signer is missing.");
        }

        boolean sameSigner = false;
        for (String signer : archiveSigners) {
            if (installedSigners.contains(signer)) {
                sameSigner = true;
                break;
            }
        }
        if (!sameSigner) throw new IllegalStateException("APK signer mismatch.");

        if (!blank(meta.signerSha256)) {
            boolean publishedSigner = false;
            for (String signer : archiveSigners) {
                if (constantAscii(signer, meta.signerSha256)) {
                    publishedSigner = true;
                    break;
                }
            }
            if (!publishedSigner) {
                throw new IllegalStateException("Published signer pin mismatch.");
            }
        }
    }

    @SuppressWarnings("deprecation")
    private static Set<String> signerDigests(PackageInfo info) throws Exception {
        Signature[] signatures;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            if (info.signingInfo == null) return new LinkedHashSet<>();
            signatures = info.signingInfo.hasMultipleSigners()
                    ? info.signingInfo.getApkContentsSigners()
                    : info.signingInfo.getSigningCertificateHistory();
        } else {
            signatures = info.signatures == null ? new Signature[0] : info.signatures;
        }
        LinkedHashSet<String> result = new LinkedHashSet<>();
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        for (Signature signature : signatures) {
            result.add(toHex(digest.digest(signature.toByteArray())));
            digest.reset();
        }
        return result;
    }

    private static String fetchText(String url, int maxBytes) throws Exception {
        HttpURLConnection c = open(url);
        try {
            int statusCode = c.getResponseCode();
            if (statusCode < 200 || statusCode > 299) {
                throw new IllegalStateException("Update check HTTP " + statusCode);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int total = 0;
            try (InputStream in = c.getInputStream()) {
                int read;
                while ((read = in.read(buffer)) >= 0) {
                    if (read == 0) continue;
                    total += read;
                    if (total > maxBytes) throw new IllegalStateException("Update metadata is too large.");
                    out.write(buffer, 0, read);
                }
            }
            return out.toString(StandardCharsets.UTF_8.name());
        } finally {
            c.disconnect();
        }
    }

    private static HttpURLConnection open(String url) throws Exception {
        if (blank(url) || !url.startsWith("https://")) {
            throw new IllegalArgumentException("HTTPS required.");
        }
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(20000);
        c.setInstanceFollowRedirects(true);
        c.setRequestMethod("GET");
        c.setRequestProperty("User-Agent", "VeyraRoot/2.0 Public");
        return c;
    }

    private static String httpsOrBlank(String value) {
        String v = value == null ? "" : value.trim();
        return v.isEmpty() || v.startsWith("https://") ? v : "";
    }

    private static String decode(int[] values) {
        StringBuilder out = new StringBuilder(values.length);
        for (int value : values) out.append((char) (value ^ KEY));
        return out.toString();
    }

    private static String toHex(byte[] bytes) {
        StringBuilder b = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) b.append(String.format(Locale.US, "%02x", value & 0xff));
        return b.toString();
    }

    private static boolean constantAscii(String a, String b) {
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.US_ASCII),
                b.getBytes(StandardCharsets.US_ASCII)
        );
    }

    private static boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String safeMessage(Throwable error) {
        String message = error.getMessage();
        return blank(message) ? error.getClass().getSimpleName() : message;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static final class UpdateMeta {
        long versionCode;
        String versionName;
        String apkUrl;
        String releaseUrl;
        String sha256;
        String signerSha256;
    }
}
