package net.instantcom.keiko.filefilter;

import java.io.File;
import java.io.FileFilter;

public class TorrentFileFilter implements FileFilter {

    public TorrentFileFilter() {
    }

    @Override
    public boolean accept(File pathname) {
        return pathname.getName().endsWith(".torrent");
    }
}
