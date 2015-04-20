import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.Arrays;
import java.util.HashMap;

import junit.framework.TestCase;

import net.instantcom.keiko.bittorrent.MetaInfo;
import net.instantcom.util.BDecoder;

public class TestBDecoderSingleFile extends TestCase {

    private static final String FILENAME = "src/test/singlefile.torrent";
    private static final byte[] INFO_HASH =
        {
            (byte) 0xfe, 0x1b, (byte) 0xc7, 0x2c, 0x0f, (byte) 0xaa, 0x6a,
            (byte) 0xaf, (byte) 0xd0, (byte) 0xf6, (byte) 0xa3, 0x17,
            (byte) 0x90, 0x07, (byte) 0xc7, 0x7e, 0x4d, (byte) 0xa9, 0x4a,
            (byte) 0xe6
        };
    private static final String INFO_HASH_STRING =
        "fe1bc72c0faa6aafd0f6a3179007c77e4da94ae6";
    private static final String ANNOUNCE =
        "http://tpb.tracker.thepiratebay.org/announce";
    private static final String ANNOUNCE_LIST =
        "[[http://tpb.tracker.thepiratebay.org/announce], "
            + "[http://open.tracker.thepiratebay.org/announce], "
            + "[http://www.torrent-downloads.to:2710/announce], "
            + "[http://denis.stalker.h3q.com:6969/announce], "
            + "[udp://denis.stalker.h3q.com:6969/announce], "
            + "[http://www.sumotracker.com/announce], "
            + "[http://novatelbt.net:2710/announce], "
            + "[http://easydl.fabolo.us:2710/announce], "
            + "[http://tracker.to:2710/46adb0536ded9d8dedda6cb2ed6f2c9d/announce], "
            + "[http://tracker.sextorrent.to:2710/announce], "
            + "[http://tv.tracker.prq.to:80/announce.php], "
            + "[http://tracker.datorrents.com:6969/announce.php], "
            + "[http://tracker.torrent.to:2710/announce:6969/announce], "
            + "[http://tpb.tracker.thepiratebay.org:80/announce], "
            + "[http://tracker2.bt-chat.com:80/announce], "
            + "[http://www.torrentrealm.com/announce.php]]";
    private static final String ENCODING = "UTF-8";
    private static final String CREATED_BY = "uTorrent/1720";
    private static final String CREATION_DATE = "1194809884";
    private static final String NUM_PIECES = "5";
    private static final String LENGTH = "277337";
    private static final String NAME =
        "Gears.Of.War.CrackOnly.Proper-Reloaded.rar";
    private static final String FILES = null;
    private static final String PIECE_LENGTH = "65536";
    private static final byte[] PIECES_SHA1 =
        {
            0x43, (byte) 0x9B, (byte) 0xB8, 0x11, (byte) 0xAA, 0x05,
            (byte) 0xE7, (byte) 0xE7, 0x51, (byte) 0xD7, (byte) 0x9F, 0x64,
            0x08, 0x52, (byte) 0xF3, (byte) 0xDD, 0x7F, 0x1E, (byte) 0xC2,
            0x58, 0x53, 0x71, 0x32, (byte) 0xD3, (byte) 0xD8, (byte) 0xF8,
            0x03, 0x1A, 0x47, 0x4D, (byte) 0x86, 0x34, (byte) 0xC2, 0x50,
            (byte) 0xC6, (byte) 0x84, (byte) 0xD2, 0x6B, (byte) 0x89, 0x0E,
            0x57, (byte) 0x8D, (byte) 0xFC, 0x76, (byte) 0xC2, (byte) 0xDD,
            0x43, 0x1F, 0x3F, 0x55, 0x22, (byte) 0x82, 0x54, 0x5D, 0x42,
            (byte) 0x84, (byte) 0x8C, (byte) 0xE6, 0x1B, (byte) 0x82,
            (byte) 0xB2, (byte) 0xD7, (byte) 0xB5, (byte) 0xEE, (byte) 0x8E,
            (byte) 0x83, (byte) 0xBC, (byte) 0xFB, (byte) 0xB2, 0x32, 0x62,
            (byte) 0xC0, 0x74, 0x62, (byte) 0xE6, 0x5A, (byte) 0xAB,
            (byte) 0xF3, 0x0F, 0x41, (byte) 0xEF, (byte) 0xD9, (byte) 0xB7,
            (byte) 0xBA, 0x77, (byte) 0x95, (byte) 0x97, 0x44, (byte) 0x8C,
            0x7B, 0x5E, (byte) 0xBC, (byte) 0xE0, 0x47, (byte) 0xE3, 0x1F,
            0x25, (byte) 0xC3, (byte) 0x99, (byte) 0xE8
        };

    @SuppressWarnings("unchecked")
    public void test() throws Exception {
        assertEquals(20, INFO_HASH.length);
        assertEquals(40, INFO_HASH_STRING.length());
        assertEquals(20 * Integer.parseInt(NUM_PIECES), PIECES_SHA1.length);

        // general
        HashMap map =
            BDecoder.bdecode(new BufferedInputStream(new FileInputStream(
                new File(FILENAME))));
        assertTrue(Arrays.equals(INFO_HASH, (byte[]) map.get("info hash")));
        assertEquals(ANNOUNCE, map.get("announce"));
        assertEquals(ANNOUNCE_LIST, map.get("announce-list").toString());
        assertEquals(CREATED_BY, map.get("created by"));
        assertEquals(CREATION_DATE, map.get("creation date"));
        assertEquals(ENCODING, map.get("encoding"));

        // info
        HashMap info = (HashMap) map.get("info");
        assertEquals(FILES, info.get("files"));
        assertEquals(LENGTH, info.get("length"));
        assertEquals(NAME, info.get("name"));
        assertEquals(PIECE_LENGTH, info.get("piece length"));
        assertTrue(Arrays.equals(PIECES_SHA1, (byte[]) info.get("pieces")));

        // metainfo
        MetaInfo meta = BDecoder.loadMetaInfo(new File(FILENAME));
        assertTrue(Arrays.equals(INFO_HASH, meta.getInfoHash()));
        assertEquals(INFO_HASH_STRING, meta.getInfoHashAsString());
        assertEquals(ANNOUNCE, meta.getAnnounce());
        assertEquals(ANNOUNCE_LIST, meta.getAnnounceList().toString());
        assertEquals(CREATED_BY, meta.getCreatedBy());
        assertEquals(Long.parseLong(CREATION_DATE), meta.getCreationDate());
        assertEquals(ENCODING, meta.getEncoding());
        assertEquals(FILES, meta.getFiles());
        assertEquals(Long.parseLong(LENGTH), meta.getLength());
        assertEquals(NAME, meta.getName());
        assertEquals(Integer.parseInt(PIECE_LENGTH), meta.getPieceLength());
        assertTrue(Arrays.equals(PIECES_SHA1, meta.getPiecesSHA1()));
    }

}
