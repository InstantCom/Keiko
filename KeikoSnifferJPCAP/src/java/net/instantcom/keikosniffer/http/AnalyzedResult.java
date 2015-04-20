package net.instantcom.keikosniffer.http;

public class AnalyzedResult {

    public AnalyzedResult() {
    }

    public boolean isPotentialTrackerResponse() {
        return potentialTrackerResponse;
    }

    public void setPotentialTrackerResponse(boolean potentialTrackerResponse) {
        this.potentialTrackerResponse = potentialTrackerResponse;
    }

    public boolean isPotentialTrackerFailure() {
        return potentialTrackerFailure;
    }

    public void setPotentialTrackerFailure(boolean potentialTrackerFailure) {
        this.potentialTrackerFailure = potentialTrackerFailure;
    }

    public boolean isPotentialTorrent() {
        return potentialTorrent;
    }

    public void setPotentialTorrent(boolean potentialTorrent) {
        this.potentialTorrent = potentialTorrent;
    }

    private boolean potentialTrackerResponse;
    private boolean potentialTrackerFailure;
    private boolean potentialTorrent;

}
