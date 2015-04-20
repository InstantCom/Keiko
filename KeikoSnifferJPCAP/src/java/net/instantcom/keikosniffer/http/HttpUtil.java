package net.instantcom.keikosniffer.http;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;

public abstract class HttpUtil {

    public static byte[] getBody(HttpResponse response) throws IOException {
        HttpEntity body = response.getEntity();
        if (null == body) {
            return null;
        }
        InputStream is = body.getContent();
        byte[] buffer = new byte[1024];
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int read;
        while ((read = is.read(buffer)) >= 0) {
            baos.write(buffer, 0, read);
        }
        return baos.toByteArray();
    }

}
