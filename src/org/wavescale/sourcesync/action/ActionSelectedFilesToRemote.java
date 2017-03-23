package org.wavescale.sourcesync.action;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.wavescale.sourcesync.api.ConnectionConfiguration;
import org.wavescale.sourcesync.api.FileSynchronizer;
import org.wavescale.sourcesync.api.SynchronizationQueue;
import org.wavescale.sourcesync.api.Utils;
import org.wavescale.sourcesync.factory.ConfigConnectionFactory;
import org.wavescale.sourcesync.factory.ModuleConnectionConfig;
import org.wavescale.sourcesync.logger.BalloonLogger;
import org.wavescale.sourcesync.logger.EventDataLogger;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Semaphore;

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
public class ActionSelectedFilesToRemote extends AnAction {
    public void actionPerformed(final AnActionEvent e) {
        // first check if there's a connection type associated to this module. If not alert the user
        // and get out
        Project currentProject = PlatformDataKeys.PROJECT.getData(e.getDataContext());
        String moduleName = currentProject.getName();
        String associationName = ModuleConnectionConfig.getInstance().getAssociationFor(moduleName);
        if (associationName == null) {
            Utils.showNoConnectionSpecifiedError(e, moduleName);
            return;
        }

        // get a list of selected virtual files
        VirtualFile[] virtualFiles = PlatformDataKeys.VIRTUAL_FILE_ARRAY.getData(e.getDataContext());
        if (virtualFiles.length <= 0) {
            StringBuilder builder = new StringBuilder("Project <b>");
            builder.append(e.getProject().getName()).append("</b>! does not have files selected!");
            BalloonLogger.logBalloonInfo(builder.toString(), e.getProject());
            EventDataLogger.logInfo(builder.toString(), e.getProject());
            return;
        }

        // start sync
        final ConnectionConfiguration connectionConfiguration = ConfigConnectionFactory.getInstance().
                getConnectionConfiguration(associationName);
        final Semaphore semaphores = new Semaphore(connectionConfiguration.getSimultaneousJobs());
        final int allowed_sessions = virtualFiles.length <= connectionConfiguration.getSimultaneousJobs() ?
                virtualFiles.length : connectionConfiguration.getSimultaneousJobs();
        final SynchronizationQueue synchronizationQueue = new SynchronizationQueue(e.getProject(), connectionConfiguration, allowed_sessions);
        synchronizationQueue.startCountingTo(virtualFiles.length);
        final BlockingQueue<FileSynchronizer> queue = synchronizationQueue.getSyncQueue();
        //final String projectName = e.getProject().getName();
        for (VirtualFile virtualFile : virtualFiles) {
            if (virtualFile != null && new File(virtualFile.getPath()).isFile()) {
                if (Utils.canBeUploaded(virtualFile.getName(), connectionConfiguration.getExcludedFiles())) {
                    String virtualFilePath = virtualFile.getPath();
                    String basePath = currentProject.getBasePath();

                    //EventDataLogger.logInfo("virtualFilePath:" + virtualFilePath, e.getProject());
                    //EventDataLogger.logInfo("basePath:" + basePath, e.getProject());
                    final File relativeFile;

                    if (!virtualFilePath.contains(basePath))
                    {
                        //EventDataLogger.logInfo("External file", e.getProject());
                        // The selected VirtualFile resides external project content-source-directory
                        try {
                            // Get the relative Path from project directory to external-project file
                            relativeFile = new File(Utils.getRelativePath(
                                new File(currentProject.getBaseDir().getPath()),
                                new File(virtualFilePath)
                            ));
                        } catch (IOException e1) {
                            EventDataLogger.logError(
                                "Could not get relative path from the projects-directory to the external source-root-file",
                                e.getProject()
                            );
                            synchronizationQueue.count();
                            return;
                        }
                    }
                    else
                    {
                        //EventDataLogger.logInfo("Internal file", e.getProject());
                        // The selected VirtualFile resides inside the project-directory
                        // Get the the file path below project dir
                        relativeFile = new File(virtualFilePath.replaceFirst(Utils.getUnixPath(basePath), ""));
                    }

                    //EventDataLogger.logInfo("Relative file path: "+ relativeFile.getAbsolutePath(), e.getProject());
                    //final File relativeFile = new File(virtualFile.getPath().replaceFirst(Utils.getUnixPath(currentProject.getBasePath()), ""));

                    ProgressManager.getInstance().run(new Task.Backgroundable(e.getProject(), "Uploading", false) {
                        @Override
                        public void run(@NotNull ProgressIndicator indicator) {
                            FileSynchronizer fileSynchronizer = null;
                            try {
                                semaphores.acquire();
                                fileSynchronizer = queue.take();
                                fileSynchronizer.setIndicator(indicator);
                                if (fileSynchronizer != null) {
                                    fileSynchronizer.connect();
                                    // so final destination will look like this:
                                    // root_home/ + project_relative_path_to_file/
                                    EventDataLogger.logInfo(
                                        String.format(
                                            "syncFile(%s, %s) relativeFile.getParent()=%s",
                                            Utils.getUnixPath(relativeFile.getPath()),
                                            Utils.buildUnixPath(relativeFile.getParent().substring(1)),
                                            relativeFile.getParent()
                                        ), e.getProject()
                                    );

                                    // Get files project-parent-directory e.g. src/org/.../package
                                    String fileProjectRelativeDir = Utils.buildUnixPath(relativeFile.getParent().substring(1));

                                    // If the file resides at project root sync to remote root
                                    if (fileProjectRelativeDir == null ||
                                        fileProjectRelativeDir.trim().length() == 0)
                                            fileProjectRelativeDir = "/";

                                    fileSynchronizer.syncFile(
                                        Utils.getUnixPath(relativeFile.getPath()),
                                        fileProjectRelativeDir
                                    );
                                }
                                queue.put(fileSynchronizer);
                                synchronizationQueue.count();
                            } catch (InterruptedException e1) {
                                e1.printStackTrace();
                            } finally {
                                semaphores.release();
                            }
                        }
                    });

                } else {
                    if (virtualFile != null) {
                        EventDataLogger.logWarning("File <b>" + virtualFile.getName() + "</b> is filtered out!", e.getProject());
                        synchronizationQueue.count();
                    }
                }
            } else {
                if (virtualFile != null) {
                    EventDataLogger.logWarning("File <b>" + virtualFile.getName() + "</b> is a directory!", e.getProject());
                    synchronizationQueue.count();
                }
            }
        }
    }
}
