import java.io.IOException;

import junit.framework.TestCase;
import net.instantcom.keikosniffer.http.HttpUtil;
import net.instantcom.keikosniffer.http.SessionInputBufferMockup;

import org.apache.http.HttpEntity;
import org.apache.http.HttpException;
import org.apache.http.HttpResponse;
import org.apache.http.HttpResponseFactory;
import org.apache.http.impl.DefaultHttpResponseFactory;
import org.apache.http.impl.entity.EntityDeserializer;
import org.apache.http.impl.entity.StrictContentLengthStrategy;
import org.apache.http.impl.io.HttpResponseParser;
import org.apache.http.io.SessionInputBuffer;
import org.apache.http.message.BasicLineParser;
import org.apache.http.message.LineParser;
import org.apache.http.params.BasicHttpParams;

public class TestHttpResponse extends TestCase {

    private static final HttpResponseFactory responseFactory = new DefaultHttpResponseFactory();
    private static final LineParser lineParser = new BasicLineParser();
    private static final EntityDeserializer entityDeserializer =
        new EntityDeserializer(new StrictContentLengthStrategy());

    public void testBodyExtraction() throws HttpException, IOException {
        final String RESPONSE =
            "HTTP/1.0 200 OK\r\nDate: Fri, 31 Dec 1999 23:59:59 GMT\r\n"
                + "Content-Length: 6\r\nContent-Type: text/html\r\n\r\n" + "foobar\r\n";
        HttpResponse response = parse(RESPONSE.getBytes());
        assertNotNull(response);
        HttpEntity body = response.getEntity();
        assertNotNull(body);
        String s = new String(HttpUtil.getBody(response));
        assertEquals("foobar", s);
    }

    // // doesn't work without headers
    // public void testHeaderlessBody() throws HttpException, IOException {
    // final String RESPONSE = "foobar";
    // HttpResponse response = parse(RESPONSE.getBytes());
    // assertNotNull(response);
    // HttpEntity body = response.getEntity();
    // assertNotNull(body);
    // String s = new String(HttpUtil.getBody(response));
    // assertEquals("foobar", s);
    // }

    public void testChunkedResponse() throws HttpException, IOException {
        final String RESPONSE =
            "HTTP/1.1 200 OK\r\n" + "Date: Fri, 31 Dec 1999 23:59:59 GMT\r\n"
                + "Content-Type: text/plain\r\n" + "Transfer-Encoding: chunked\r\n" + "\r\n"
                + "1a; ignore-stuff-here\r\n" + "abcdefghijklmnopqrstuvwxyz\r\n" + "10\r\n"
                + "1234567890abcdef\r\n" + "0\r\n" + "some-footer: some-value\r\n"
                + "another-footer: another-value" + "\r\n";
        HttpResponse response = parse(RESPONSE.getBytes());
        assertNotNull(response);
        HttpEntity body = response.getEntity();
        assertNotNull(body);
        String s = new String(HttpUtil.getBody(response));
        assertEquals("abcdefghijklmnopqrstuvwxyz1234567890abcdef", s);
    }

    private HttpResponse parse(byte[] data) throws HttpException, IOException {
        SessionInputBuffer input = new SessionInputBufferMockup(data);
        HttpResponseParser responseParser =
            new HttpResponseParser(input, lineParser, responseFactory, new BasicHttpParams());
        HttpResponse msg = (HttpResponse) responseParser.parse();
        msg.setEntity(entityDeserializer.deserialize(input, msg));
        return msg;
    }

}
