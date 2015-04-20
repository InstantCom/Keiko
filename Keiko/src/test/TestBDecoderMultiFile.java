import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.Arrays;
import java.util.HashMap;

import junit.framework.TestCase;

import net.instantcom.keiko.bittorrent.MetaInfo;
import net.instantcom.util.BDecoder;

public class TestBDecoderMultiFile extends TestCase {

    private static final String FILENAME = "src/test/multifiles.torrent";
    private static final byte[] INFO_HASH =
        {
            (byte) 0xdd, (byte) 0x93, (byte) 0xb0, 0x06, 0x2f, (byte) 0xe7,
            0x13, (byte) 0xdb, (byte) 0xb2, 0x53, 0x66, (byte) 0xc4, 0x07,
            0x77, 0x7c, 0x78, (byte) 0xd3, 0x1b, 0x08, 0x09
        };
    private static final String INFO_HASH_STRING =
        "dd93b0062fe713dbb25366c407777c78d31b0809";
    private static final String ANNOUNCE = "http://test.tracker.com/announce";
    private static final String ANNOUNCE_LIST = null;
    private static final String ENCODING = "UTF-8";
    private static final String CREATED_BY = "uTorrent/1750";
    private static final String CREATION_DATE = "1200363970";
    private static final String NUM_PIECES = "10";
    private static final String LENGTH = "4826762";
    private static final String NAME = "Sample Pictures";
    private static final String FILES =
        "[{path=[Autumn Leaves.jpg], length=276216}, "
            + "{path=[Creek.jpg], length=264409}, "
            + "{path=[Desert Landscape.jpg], length=228863}, "
            + "{path=[Dock.jpg], length=316892}, "
            + "{path=[Forest Flowers.jpg], length=128755}, "
            + "{path=[Forest.jpg], length=664489}, "
            + "{path=[Frangipani Flowers.jpg], length=108051}, "
            + "{path=[Garden.jpg], length=516424}, "
            + "{path=[Green Sea Turtle.jpg], length=378729}, "
            + "{path=[Humpback Whale.jpg], length=262368}, "
            + "{path=[Oryx Antelope.jpg], length=297834}, "
            + "{path=[Toco Toucan.jpg], length=114852}, "
            + "{path=[Tree.jpg], length=770042}, "
            + "{path=[Waterfall.jpg], length=287631}, "
            + "{path=[Winter Leaves.jpg], length=211207}]";
    private static final String PIECE_LENGTH = "524288";
    private static final byte[] PIECES_SHA1 =
        {
            0x71, (byte) 0xA9, (byte) 0x94, (byte) 0xCF, (byte) 0xE8,
            (byte) 0x8D, (byte) 0xB3, (byte) 0xA6, 0x79, 0x6F, 0x15, 0x3C,
            0x3F, 0x1F, 0x74, 0x39, 0x0A, (byte) 0xDF, (byte) 0xAB, 0x13,
            (byte) 0xE5, (byte) 0xF6, 0x31, 0x06, (byte) 0xB4, 0x6F,
            (byte) 0xAA, 0x76, (byte) 0xA3, 0x56, 0x25, (byte) 0xAE, 0x6F,
            0x59, 0x1A, 0x3F, 0x1A, (byte) 0xD7, 0x79, 0x72, (byte) 0xD6, 0x10,
            0x74, (byte) 0xF7, 0x74, 0x0C, 0x6D, (byte) 0xEB, 0x10,
            (byte) 0xD4, (byte) 0xC2, (byte) 0xD9, (byte) 0xB7, (byte) 0xB2,
            0x1C, 0x16, (byte) 0x85, 0x7A, 0x6F, 0x02, 0x55, 0x2B, 0x39, 0x16,
            (byte) 0xA4, 0x66, (byte) 0x96, (byte) 0xD7, (byte) 0xAC, 0x15,
            0x65, 0x5C, (byte) 0xE5, (byte) 0xBD, 0x6A, 0x73, (byte) 0x98,
            0x4B, (byte) 0x80, (byte) 0xF5, 0x2F, (byte) 0x81, 0x25,
            (byte) 0x92, 0x0B, 0x61, (byte) 0xD9, (byte) 0xF0, (byte) 0xFA,
            (byte) 0x98, (byte) 0xEA, 0x21, (byte) 0xA2, (byte) 0xA3, 0x39,
            0x59, (byte) 0xE0, 0x42, (byte) 0xFD, (byte) 0x9F, 0x5B,
            (byte) 0xFE, 0x3B, (byte) 0xBE, (byte) 0xB9, 0x5E, (byte) 0x83,
            0x34, 0x05, (byte) 0xC8, 0x37, 0x38, 0x55, (byte) 0xCE,
            (byte) 0x83, (byte) 0xA4, (byte) 0xBD, (byte) 0x82, (byte) 0x97,
            0x2E, (byte) 0xD5, (byte) 0xBF, (byte) 0xE7, (byte) 0xA7,
            (byte) 0xE7, (byte) 0xA5, 0x2F, 0x35, 0x33, 0x69, 0x0F,
            (byte) 0x98, 0x37, (byte) 0xF3, (byte) 0xAB, 0x68, 0x56, 0x72,
            0x37, 0x5A, 0x20, 0x18, (byte) 0x9F, (byte) 0xB7, (byte) 0x8D,
            (byte) 0x93, (byte) 0x9F, (byte) 0x93, (byte) 0xEE, 0x3B, 0x1D,
            (byte) 0xE1, (byte) 0x9D, (byte) 0xF5, (byte) 0x8E, 0x55, 0x16,
            (byte) 0xBD, (byte) 0x90, 0x13, 0x2C, (byte) 0x93, 0x53, 0x23,
            (byte) 0xC2, (byte) 0xBF, 0x76, 0x22, (byte) 0xF5, (byte) 0xC4,
            0x7D, (byte) 0xAD, (byte) 0xB0, 0x4D, (byte) 0xD2, (byte) 0x82,
            (byte) 0x8F, (byte) 0x80, 0x59, (byte) 0x8D, (byte) 0xB2,
            (byte) 0xA3, (byte) 0xF1, 0x28, 0x3E, 0x20, 0x4A, 0x59, 0x6E,
            (byte) 0x89, (byte) 0xA7, (byte) 0xC6, (byte) 0x97, 0x41, 0x18,
            0x38, 0x18, (byte) 0x91, (byte) 0xEC, (byte) 0xCC
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
        assertEquals(ANNOUNCE_LIST, map.get("announce-list"));
        assertEquals(CREATED_BY, map.get("created by"));
        assertEquals(CREATION_DATE, map.get("creation date"));
        assertEquals(ENCODING, map.get("encoding"));

        // info
        HashMap info = (HashMap) map.get("info");
        assertEquals(FILES, info.get("files").toString());
        assertEquals(LENGTH, info.get("length"));
        assertEquals(NAME, info.get("name"));
        assertEquals(PIECE_LENGTH, info.get("piece length"));
        assertTrue(Arrays.equals(PIECES_SHA1, (byte[]) info.get("pieces")));

        // metainfo
        MetaInfo meta = BDecoder.loadMetaInfo(new File(FILENAME));
        assertTrue(Arrays.equals(INFO_HASH, meta.getInfoHash()));
        assertEquals(INFO_HASH_STRING, meta.getInfoHashAsString());
        assertEquals(ANNOUNCE, meta.getAnnounce());
        assertEquals(ANNOUNCE_LIST, meta.getAnnounceList());
        assertEquals(CREATED_BY, meta.getCreatedBy());
        assertEquals(Long.parseLong(CREATION_DATE), meta.getCreationDate());
        assertEquals(ENCODING, meta.getEncoding());
        assertEquals(FILES, meta.getFiles().toString());
        assertEquals(Long.parseLong(LENGTH), meta.getLength());
        assertEquals(NAME, meta.getName());
        assertEquals(Integer.parseInt(PIECE_LENGTH), meta.getPieceLength());
        assertTrue(Arrays.equals(PIECES_SHA1, meta.getPiecesSHA1()));
    }

}
