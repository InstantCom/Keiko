package net.instantcom.keiko.bittorrent.extensions;

import net.instantcom.util.BitField;

public abstract class Extensions {

    private static final BitField supportedExtensions = new BitField(64);

    public static BitField getSupportedExtensions() {
        return supportedExtensions;
    }

}
