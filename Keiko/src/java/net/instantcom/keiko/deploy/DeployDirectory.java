package net.instantcom.keiko.deploy;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.List;

/**
 * Deploy directory. Applications can use deploy directory to periodically check
 * if there are new files in it. Optionally, they can use custom
 * <tt>FileFilter</tt> to target specific files in directory.
 * <p>
 * Application should call <code>check()</code> at regular intervals.
 * <p>
 * Specified <tt>DeployDirectoryListener</tt> will be notified for each new
 * file found.
 */
public class DeployDirectory {

    /**
     * Creates new deploy directory.
     * 
     * @param dir
     *            deploy directory
     * @param listener
     *            listener
     * @throws IllegalArgumentException
     *             if <code>dir</code> is not a directory or
     *             <code>null == listener</code>
     */
    public DeployDirectory(File dir, DeployDirectoryListener listener)
        throws IllegalArgumentException {
        this(dir, null, listener);
    }

    /**
     * Creates new deploy directory.
     * 
     * @param dir
     *            deploy directory
     * @param filter
     *            file filter
     * @param listener
     *            listener
     * @throws IllegalArgumentException
     *             if <code>dir</code> is not a directory or
     *             <code>null == listener</code>
     */
    public DeployDirectory(File dir, FileFilter filter,
        DeployDirectoryListener listener) throws IllegalArgumentException {
        if (!dir.isDirectory()) {
            throw new IllegalArgumentException(dir.getAbsolutePath()
                + " is not a directory");
        } else if (null == listener) {
            throw new IllegalArgumentException("listener == null");
        }
        this.dir = dir;
        this.filter = filter;
        this.listener = listener;
    }

    /**
     * Checks directory for files and calls
     * <code>listener.onFileFound(File)</code> for each file found. If
     * listener doesn't want to receive further updates about the same file it
     * must return <tt>false</tt>.
     */
    public void check() {
        File[] files = dir.listFiles(filter);
        for (File file : files) {
            if (!processedFiles.contains(file)) {
                if (!listener.onFileFound(file)) {
                    processedFiles.add(file);
                }
            }
        }
    }

    private File dir;
    private FileFilter filter;
    private DeployDirectoryListener listener;
    private final List<File> processedFiles = new ArrayList<File>();

}
