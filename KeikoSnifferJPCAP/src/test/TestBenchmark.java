import java.util.regex.Matcher;
import java.util.regex.Pattern;

import junit.framework.TestCase;
import net.instantcom.util.BinaryMatcher;

public class TestBenchmark extends TestCase {

    private static final int ITERATIONS = 100000;

    private static final byte[] MATCHING_GET =
        "GET /announce?info_hash=012345678901234567890012345678901234567890&downloaded=0&uploaded=0&left=0&num_want=200 HTTP/1.1"
            .getBytes();
    private static final byte[] MISMATCHING_GET =
        "GET /someForm?some_vars=012345678901234567890012345678901234567890&downloaded=0&uploaded=0&left=0&num_want=200 HTTP/1.1"
            .getBytes();

    public void testPatternMatching() throws Exception {
        Pattern pattern = Pattern.compile("^GET .*announce.*info_hash=.*");

        // this is actually a cheat, real app must create strings from byte arrays in runtime.
        // nevertheless, binary matching is still over 10 times faster (over 13 times faster with
        // strings created in runtime).
        String MATCHING_GET = new String(TestBenchmark.MATCHING_GET);
        String MISMATCHING_GET = new String(TestBenchmark.MISMATCHING_GET);

        int hitCount = 0;
        int missCount = 0;
        long startTime = System.currentTimeMillis();
        for (int i = 0; i < ITERATIONS; i++) {
            Matcher matcher = pattern.matcher(MATCHING_GET);
            if (matcher.matches()) {
                ++hitCount;
            } else {
                throw new Exception("no match for MATCHING_GET!");
            }
            matcher = pattern.matcher(MISMATCHING_GET);
            if (matcher.matches()) {
                throw new Exception("match for MISMATCHING_GET!");
            } else {
                ++missCount;
            }
        }
        long timeTaken = System.currentTimeMillis() - startTime;
        assertEquals(hitCount, missCount);
        System.out.println("testPatternMatching: " + timeTaken + " ms");
    }

    public void testBinaryMatching() throws Exception {
        BinaryMatcher matcher = new BinaryMatcher(new String[] {
            "GET ", "announce", "info_hash="
        });
        int hitCount = 0;
        int missCount = 0;
        long startTime = System.currentTimeMillis();
        for (int i = 0; i < ITERATIONS; i++) {
            if (matcher.matches(MATCHING_GET, MATCHING_GET.length)) {
                ++hitCount;
            } else {
                throw new Exception("no match for MATCHING_GET!");
            }
            if (matcher.matches(MISMATCHING_GET, MISMATCHING_GET.length)) {
                throw new Exception("match for MISMATCHING_GET!");
            } else {
                ++missCount;
            }
        }
        long timeTaken = System.currentTimeMillis() - startTime;
        assertEquals(hitCount, missCount);
        System.out.println("testBinaryMatching: " + timeTaken + " ms");
    }

}
