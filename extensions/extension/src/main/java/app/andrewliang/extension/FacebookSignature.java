package app.andrewliang.extension;

import android.content.pm.PackageInfo;
import android.content.pm.Signature;
import android.util.Base64;

import java.util.Collections;
import java.util.List;

/**
 * Helper for the Facebook "[Fix] Restore screens on re-signed builds" patch.
 *
 * <p>Facebook's security code compares the signing certificate of a package with a table of Meta
 * certificates, and it does this for its own package too. A re-signed build has a different
 * certificate, so Facebook does not trust itself and drops a tap on some screens without a message.
 * The patch gives the check the original certificate of Facebook when the package is Facebook.
 *
 * <p>The package name is enough to know that the package is this app. Android lets only one
 * installed app have a package name, and the patch does not rename Facebook.
 */
public final class FacebookSignature {

    private FacebookSignature() {}

    private static final String PACKAGE = "com.facebook.katana";

    /** The original signing certificate of Facebook, DER in Base64. Its SHA-1 is 8a3c4b26…fa2b9. */
    private static final String CERTIFICATE =
        "MIICaDCCAdECBEqcRhAwDQYJKoZIhvcNAQEEBQAwejELMAkGA1UEBhMCVVMxCzAJBgNVBAgTAkNBMRIwEAYDVQQH"
        + "EwlQYWxvIEFsdG8xGDAWBgNVBAoTD0ZhY2Vib29rIE1vYmlsZTERMA8GA1UECxMIRmFjZWJvb2sxHTAbBgNVBAMT"
        + "FEZhY2Vib29rIENvcnBvcmF0aW9uMCAXDTA5MDgzMTIxNTIxNloYDzIwNTAwOTI1MjE1MjE2WjB6MQswCQYDVQQG"
        + "EwJVUzELMAkGA1UECBMCQ0ExEjAQBgNVBAcTCVBhbG8gQWx0bzEYMBYGA1UEChMPRmFjZWJvb2sgTW9iaWxlMREw"
        + "DwYDVQQLEwhGYWNlYm9vazEdMBsGA1UEAxMURmFjZWJvb2sgQ29ycG9yYXRpb24wgZ8wDQYJKoZIhvcNAQEBBQAD"
        + "gY0AMIGJAoGBAMIH1R3464yX2TugyMEALJKPqwDcG0L8peZumcwwI+0tIU2CK8WejjXdz19Ex66K3lDX4MQ09QDm"
        + "wTH0ooNPmH/EZAYRXeIBjruw1aPCYb2XWBzP73avxxNabVnohV7NfqzI+HN+eUxgp2HFNrcrEfrI5gP12hotVKoQ"
        + "O4oTwNvBAgMBAAEwDQYJKoZIhvcNAQEEBQADgYEAXum+i8uyUGSNO3QSkKgqHJ3C52oK8vIijx2fnEAHUpxEanAX"
        + "XFqQDVFBgShm20a+ZVniFBYWSDmYIR9KZzFJ+yIyoQ0kdmOyapAx4V+EvBx00UH/mKAtdvhbLIqyVxtkabIy2Odo"
        + "p/fKBPer5Kd1YVkWwHlAZWtYcXRXtCvZKKI=";

    private static volatile List<Signature> original;

    /**
     * The original signers of Facebook if [info] is Facebook itself, or {@code null} to keep the
     * signers that the system reports.
     */
    public static List<Signature> originalSigners(PackageInfo info) {
        if (info == null || !PACKAGE.equals(info.packageName)) return null;

        List<Signature> signers = original;
        if (signers == null) {
            signers = Collections.singletonList(new Signature(Base64.decode(CERTIFICATE, Base64.DEFAULT)));
            original = signers;
        }
        return signers;
    }
}
