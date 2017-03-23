package org.wavescale.sourcesync.api;

import com.intellij.openapi.actionSystem.AnActionEvent;
import org.wavescale.sourcesync.logger.BalloonLogger;
import org.wavescale.sourcesync.logger.EventDataLogger;

import java.io.File;
import java.io.IOException;

/**
 * ****************************************************************************
 * Copyright (c) 2005-2014 Faur Ioan-Aurel.                                     *
 * All rights reserved. This program and the accompanying materials             *
 * are made available under the terms of the MIT License                        *
 * which accompanies this distribution, and is available at                     *
 * http://opensource.org/licenses/MIT                                           *
 * *
 * For any issues or questions send an email at: fioan89@gmail.com              *
 * *****************************************************************************
 */
public class Utils {


    /**
     * Solution by Burn L. posted in:
     * http://stackoverflow.com/questions/204784/how-to-construct-a-relative-path-in-java-from-two-absolute-paths-or-urls/1290311#1290311
     *
     * Constructs a relative path from path1 to path2, where both paths have to own a common base path e.g.:
     *
     * <example><code><pre>
     * File p1 = new File("/usr/local/bin");
     * File p2 = new File("/usr/bin");
     * String rel = FileTools.getRelativePath(p1, p2);
     * // rel is "/../../usr/bin";
     *
     * File f = new File(p.toString());
     * // f is /usr/local/bin/../../usr/bin
     *
     * // and f really exists
     * assertTrue(f.exists());
     *
     * </pre></code></example>
     *
     * @param path1 Build the relative path from this to path2
     * @param path2 The target within the build path
     * @return The relative path from path1 to path2
     * @throws IOException
     */
    public static String getRelativePath(File path1, File path2) throws IOException {
        File parent = path1.getParentFile();
        if (parent == null) throw new IOException("No common directory");
        String bpath = path1.getCanonicalPath();
        String fpath = path2.getCanonicalPath();
        if (fpath.startsWith(bpath))
            return fpath.substring(bpath.length() + 1);
        else
            return (".." + File.separator + getRelativePath(parent, path2));
    }


    /**
     * Checks if a given filename can be uploaded or not.
     *
     * @param fileName           a string representing a file name plus extension.
     * @param extensionsToFilter a string that contains file extensions separated by
     *                           by space, comma or ";" character that are not to be uploaded. The
     *                           extension MUST contain the dot character - ex: ".crt .iml .etc"
     * @return <code>true</code> if file extension is not on the extensionsToFilter, <code>False</code> otherwise.
     */
    public static boolean canBeUploaded(String fileName, String extensionsToFilter) {
        String extension = ".";

        if (fileName != null) {
            int i = fileName.lastIndexOf('.');
            if (i >= 0) {
                extension += fileName.substring(i + 1);
                if (extensionsToFilter.contains(extension)) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    /**
     * Logs an error message about no connection specified.
     *
     * @param e          action event instance.
     * @param moduleName module or project name.
     */
    public static void showNoConnectionSpecifiedError(AnActionEvent e, String moduleName) {
        StringBuilder message = new StringBuilder();
        message.append("There is no connection type associated to <b>").append(moduleName)
                .append("</b> module.\nPlease right click on module name and then select <b>Module Connection Configuration</b> to select connection type!");
        BalloonLogger.logBalloonError(message.toString(), e.getProject());
        EventDataLogger.logError(message.toString(), e.getProject());
    }

    /**
     * Normalizes Windows paths to Unix path.
     *
     * @param path a Unix or Windows path.
     * @return if a Unix like path.
     */
    public static String getUnixPath(String path) {
        return path.replace("\\", "/");
    }

    /**
     * Builds a Unix path from the list of specified strings. Array is taken in order.
     *
     * @param paths a list of relative string paths.
     * @return a string instance representing a Unix path. It may or may not be an absolute path
     * depending on the input array.
     */
    public static String buildUnixPath(String... paths) {
        StringBuilder toReturn = new StringBuilder();
        for (String path : paths) {
            toReturn.append(getUnixPath(path)).append("/");
        }
        String finalValue = toReturn.toString().replace("//", "/");
        if (finalValue.length() > 0 && finalValue.charAt(finalValue.length() - 1) == '/') {
            return finalValue.substring(0, finalValue.length() - 1);
        }
        return finalValue;
    }

    /**
     * Returns an array of folders that build up a file path
     *
     * @param path an absolute path.
     * @return an array of strings instances that represent a file or folder path.
     */
    public static String[] splitPath(String path) {
        return getUnixPath(path).split("/");
    }

    /**
     * Tries to create a file with the given absolute path, even if the parent directories do not exist.
     *
     * @param path absolute file path name to create
     * @return <code>true</code> if the path was created, <code>false</code> otherwise. If false is returned it might
     * be that the file already exists
     * @throws IOException if an I/O error occurred
     */
    public static boolean createFile(String path) throws IOException {
        File fileToCreate = new File(path);
        if (fileToCreate.exists()) {
            return false;
        }
        // the file doesn't exist so try creat it
        String dirPath = fileToCreate.getParent();
        // try create the path
        new File(dirPath).mkdirs();
        // try create the file
        return fileToCreate.createNewFile();
    }

}
