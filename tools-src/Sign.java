import com.android.apksig.ApkSigner;
import com.android.apksig.ApkVerifier;

import java.io.File;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Collections;

/** Firma un APK con esquemas v2 y v3 y verifica el resultado. Uso: Sign <ks> <pass> <alias> <in.apk> <out.apk> */
public final class Sign {
    public static void main(String[] a) throws Exception {
        KeyStore ks = KeyStore.getInstance("PKCS12");
        try (FileInputStream in = new FileInputStream(a[0])) {
            ks.load(in, a[1].toCharArray());
        }
        PrivateKey key = (PrivateKey) ks.getKey(a[2], a[1].toCharArray());
        X509Certificate cert = (X509Certificate) ks.getCertificate(a[2]);
        ApkSigner.SignerConfig cfg = new ApkSigner.SignerConfig.Builder("release", key, Collections.singletonList(cert)).build();
        File out = new File(a[4]);
        new ApkSigner.Builder(Collections.singletonList(cfg))
                .setInputApk(new File(a[3])).setOutputApk(out)
                .setMinSdkVersion(26)
                .setV1SigningEnabled(false).setV2SigningEnabled(true).setV3SigningEnabled(true)
                .build().sign();
        ApkVerifier.Result r = new ApkVerifier.Builder(out).setMinCheckedPlatformVersion(26).build().verify();
        System.out.println("verificada=" + r.isVerified() + " v2=" + r.isVerifiedUsingV2Scheme() + " v3=" + r.isVerifiedUsingV3Scheme());
        if (!r.isVerified()) {
            r.getErrors().forEach(e -> System.out.println("error: " + e));
            System.exit(1);
        }
    }
}
