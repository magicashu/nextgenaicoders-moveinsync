package com.moveinsync.mobilitycopilot.ingestion.application;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/** SHA-256 digests prove the organizer files are unchanged and derive the data version. */
public final class DatasetChecksums {

    /** Digests recorded in docs/dataset-profile-and-capability-matrix.md section 1. */
    public static final java.util.Map<String, String> OFFICIAL = java.util.Map.of(
            "alerts_data.csv", "34b8fa3885c4db729749f956d26c3ba5603e565e872544ef018eca4ff4c86007",
            "bill_data.csv", "abe6e0be97880d08ff08738091c7048a707259515432785c8bbe6b19baee82a3",
            "emp_Data.csv", "147af45449d1f154871c14fa90b92037a6d7d887d7cee2a892963123dd63232d",
            "Ride_data _trip-July_2026.csv", "76da3741db9f0576671d8b9cea893a85a7504ec94cdda415f5e69ecf6d00ad13",
            "Ride_data _trip-June_2026.csv", "01839a6cff0c86ef09c467418a3516ca3006ccff6cc9b51c6fb1f35ff502c744",
            "Ride_data _trip-may_2026.csv", "c449ec4a4f35c84d46f922435feef78876c273e7ff5257dd760b226374a2e3da",
            "trip_feedback.csv", "662254358115429c14b912c0925813e2c0d243f7a83369d1856ad5229109405c");

    private DatasetChecksums() {
    }

    public static String sha256(Path path) {
        try (InputStream in = Files.newInputStream(path)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[1 << 20];
            int read;
            while ((read = in.read(buffer)) > 0) {
                digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new IllegalStateException("Unable to checksum " + path, e);
        }
    }

    /** Stable, short data version derived from the per-file digests in path order. */
    public static String dataVersion(List<String> fileDigests) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String d : fileDigests) {
                digest.update(d.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            return "data-" + HexFormat.of().formatHex(digest.digest()).substring(0, 12);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static boolean matchesOfficial(String fileName, String digest) {
        return digest.equals(OFFICIAL.get(fileName));
    }
}
