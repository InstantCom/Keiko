package net.instantcom.keiko.ksp;

public interface ProtocolListener {

    /**
     * Called when message arrives.
     * 
     * @param msg
     *            KSP message
     * @throws InvalidMessageException
     */
    public void onMessage(Message msg) throws InvalidMessageException;

}
