package com.shipflow.sf.client;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/** Implements the SF Open sandbox signing formula exactly as documented. */
public class SfSignUtil {
    /**
     * Base64(MD5(URLEncoder.encode(msgData + timestamp + checkWord, "UTF-8"))).
     * The timestamp is the decimal epoch-seconds string used in the form field.
     */
    public String digest(SfApiRequest request, String checkWord) {
        return digest(request.msgData(), Long.toString(request.timestamp()), checkWord);
    }

    public String digest(String msgData, String timestamp, String checkWord) {
        if (msgData == null || timestamp == null || checkWord == null) {
            throw new IllegalArgumentException("SF signing inputs must not be null");
        }
        String encoded = URLEncoder.encode(msgData + timestamp + checkWord, StandardCharsets.UTF_8);
        try {
            byte[] md5 = MessageDigest.getInstance("MD5")
                    .digest(encoded.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(md5);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("MD5 is required by the SF Open contract", impossible);
        }
    }
}
