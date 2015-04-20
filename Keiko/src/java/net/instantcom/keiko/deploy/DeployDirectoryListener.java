package net.instantcom.keiko.deploy;

import java.io.File;

/**
 * Deploy directory listener.
 */
public interface DeployDirectoryListener {

    /**
     * Receives notification about file found in deploy directory.
     * 
     * @see DeployDirectory.check()
     * @param file
     *            file found
     * @return true if listener wants to be notified again about this file on
     *         next check, false otherwise
     */
    public boolean onFileFound(File file);

}
