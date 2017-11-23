package wuxian.me.spidersdk.anti;

import org.mozilla.universalchardet.UniversalDetector;


public class BytesCharsetDetector {

    public static String getDetectedCharset(byte[] bytes) {
        return getDetectedCharset(bytes, "utf-8");
    }


    public static String getDetectedCharset(byte[] bytes, String defaultEncoding) {

        UniversalDetector detector = new UniversalDetector(null);
        detector.handleData(bytes, 0, bytes.length);
        detector.dataEnd();

        String encoding = detector.getDetectedCharset();
        if (encoding != null) {
            //System.out.println("Detected encoding = " + encoding);
        } else {
            //System.out.println("No encoding detected.");
            encoding = defaultEncoding;
        }

        detector.reset();
        return encoding;
    }


    public static String getCharsetByResponseContentType(String contentType) {
        if (contentType != null && contentType.length() != 0) {
            contentType = contentType.toLowerCase();
            String[] attrs = contentType.split(";");
            if (attrs != null && attrs.length > 0) {
                for (String attr : attrs) {
                    attr = attr.trim();
                    if (attr.startsWith("charset=")) {
                        return attr.replace("charset=", "");
                    }
                }
            }
        }
        return null;
    }

}
