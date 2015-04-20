package net.instantcom.keikosniffer;

public class Stats {

    public Stats() {
    }

    public void incRequestsFound() {
        ++requestsFound;
    }

    public long getRequestsFound() {
        return requestsFound;
    }

    public void incResponsesFound() {
        ++responsesFound;
    }

    public long getResponsesFound() {
        return responsesFound;
    }

    public void incResponseFailuresFound() {
        ++responseFailuresFound;
        ++responsesFound;
    }

    public long getResponseFailuresFound() {
        return responseFailuresFound;
    }

    public void incTorrentsFound() {
        ++torrentsFound;
    }

    public long getTorrentsFound() {
        return torrentsFound;
    }

    public void incRequestsSent() {
        ++requestsSent;
    }

    public long getRequestsSent() {
        return requestsSent;
    }

    public void incResponsesSent() {
        ++responsesSent;
    }

    public long getResponsesSent() {
        return responsesSent;
    }

    public void incTorrentsSent() {
        ++torrentsSent;
    }

    public long getTorrentsSent() {
        return torrentsSent;
    }

    // public void incTorrentsNotSent() {
    // ++torrentsNotSent;
    // }

    // public long getTorrentsNotSent() {
    // return torrentsNotSent;
    // }

    // public void incNormalStreamsCreated() {
    // ++normalStreamsCreated;
    // }
    //
    // public long getNormalStreamsCreated() {
    // return normalStreamsCreated;
    // }
    //
    // public void incNormalStreamsCompleted() {
    // ++normalStreamsCompleted;
    // }
    //
    // public long getNormalStreamsCompleted() {
    // return normalStreamsCompleted;
    // }
    //
    // public void incChunkedStreamsCreated() {
    // ++chunkedStreamsCreated;
    // }
    //
    // public long getChunkedStreamsCreated() {
    // return chunkedStreamsCreated;
    // }
    //
    // public void incChunkedStreamsCompleted() {
    // ++chunkedStreamsCompleted;
    // }
    //
    // public long getChunkedStreamsCompleted() {
    // return chunkedStreamsCompleted;
    // }
    //
    // public void incGzippedStreamsCreated() {
    // ++gzippedStreamsCreated;
    // }
    //
    // public long getGzippedStreamsCreated() {
    // return gzippedStreamsCreated;
    // }
    //
    // public void incGzippedStreamsCompleted() {
    // ++gzippedStreamsCompleted;
    // }
    //
    // public long getGzippedStreamsCompleted() {
    // return gzippedStreamsCompleted;
    // }

    public long getRequestsMissed() {
        return requestsFound - requestsSent;
    }

    public long getResponsesMissed() {
        return responsesFound - responseFailuresFound - responsesSent;
    }

    public long getTorrentsMissed() {
        // return torrentsFound - torrentsSent - torrentsNotSent;
        return torrentsFound - torrentsSent;
    }

    // public long getStreamsCreated() {
    // return normalStreamsCreated + chunkedStreamsCreated + gzippedStreamsCreated;
    // }
    //
    // public long getStreamsCompleted() {
    // return normalStreamsCompleted + chunkedStreamsCompleted + gzippedStreamsCompleted;
    // }

    public void merge(Stats other) {
        requestsFound += other.getRequestsFound();
        responsesFound += other.getResponsesFound();
        responseFailuresFound += other.getResponseFailuresFound();
        torrentsFound += other.getTorrentsFound();
        requestsSent += other.getRequestsSent();
        responsesSent += other.getResponsesSent();
        torrentsSent += other.getTorrentsSent();
        // torrentsNotSent += other.getTorrentsNotSent();
        // normalStreamsCreated += other.getNormalStreamsCreated();
        // normalStreamsCompleted += other.getNormalStreamsCompleted();
        // chunkedStreamsCreated += other.getChunkedStreamsCreated();
        // chunkedStreamsCompleted += other.getChunkedStreamsCompleted();
        // gzippedStreamsCreated += other.getGzippedStreamsCreated();
        // gzippedStreamsCompleted += other.getGzippedStreamsCompleted();
    }

    private long requestsFound;
    private long responsesFound;
    private long responseFailuresFound;
    private long torrentsFound;
    private long requestsSent;
    private long responsesSent;
    private long torrentsSent;
    // private long torrentsNotSent;
    // private long normalStreamsCreated;
    // private long normalStreamsCompleted;
    // private long chunkedStreamsCreated;
    // private long chunkedStreamsCompleted;
    // private long gzippedStreamsCreated;
    // private long gzippedStreamsCompleted;

}
