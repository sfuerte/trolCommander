/*
 * This file is part of muCommander, http://www.mucommander.com
 * Copyright (C) 2002-2012 Maxence Bernard
 *
 * muCommander is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * muCommander is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.mucommander.ui.action.impl;

import com.mucommander.commons.file.AbstractFile;
import com.mucommander.commons.runtime.OsFamily;
import com.mucommander.ui.action.*;
import com.mucommander.ui.main.FolderPanel;
import com.mucommander.ui.main.MainFrame;
import com.mucommander.ui.main.table.FileTable;
import com.mucommander.ui.main.table.views.BaseFileTableModel;
import org.jetbrains.annotations.Nullable;

import javax.swing.KeyStroke;
import java.awt.event.KeyEvent;
import java.util.Map;

/**
 * Opens the currently selected file and its equivalent in the inactive folder panel if it exists.
 * <p>
 * This action will analyse the current selection and, if applicable, any file from the inactive
 * panel that bears the same name and:
 * <ul>
 *   <li>
 *     If both the selection and its inactive equivalent are browsable, both will be explored in their
 *     respective panels.
 *   </li>
 *   <li>
 *     If both are non-browsable, both will be opened as defined in {@link OpenAction}.
 *   </li>
 *   <li>
 *     If one is browsable an not the other one, only the current selection will be opened.
 *   </li>
 * </ul>
 *
 * <p>
 * Note that this action's behaviour is strictly equivalent to that of {@link OpenAction} in the
 * active panel. Differences will only occur in the inactive panel, and then again only when possible.
 *
 * <p>
 * This action opens both files synchronously: it will wait for the active panel file to have been
 * opened before opening the inactive panel one.
 *
 * @author Nicolas Rinaudo
 */
public class OpenInBothPanelsAction extends SelectedFileAction {
    // - Initialization ------------------------------------------------------------------
    // -----------------------------------------------------------------------------------
    /**
     * Creates a new <code>OpenInBothPanelsAction</code> with the specified parameters.
     * @param mainFrame  frame to which the action is attached.
     * @param properties action's properties.
     */
    private OpenInBothPanelsAction(MainFrame mainFrame, Map<String, Object> properties) {
        super(mainFrame, properties);

        // Perform this action in a separate thread, to avoid locking the event thread
        setPerformActionInSeparateThread(true);
    }

    @Override
    public void activePanelChanged(FolderPanel folderPanel) {
        super.activePanelChanged(folderPanel);
        
        if (mainFrame.getInactivePanel().getTabs().getCurrentTab().isLocked()) {
            setEnabled(false);
        }
    }

    /**
     * This method is overridden to enable this action when the parent folder is selected. 
     */
    @Override
    protected boolean getFileTableCondition(FileTable fileTable) {
        AbstractFile selectedFile = fileTable.getSelectedFile(true, true);
        return selectedFile != null && selectedFile.isBrowsable();
    }


    // - Action code ---------------------------------------------------------------------
    // -----------------------------------------------------------------------------------
    /**
     * Opens the current selection and its inactive equivalent.
     */
    @Override
    public void performAction() {
        // Retrieves the current selection, aborts if none (should not normally happen).
        AbstractFile selectedFile = mainFrame.getActiveTable().getSelectedFile(true, true);
        if (selectedFile == null || !selectedFile.isBrowsable()) {
            return;
        }

        AbstractFile otherFile = findOtherFile(selectedFile);

        // Opens 'file' in the active panel.
        Thread openThread = mainFrame.getActivePanel().tryChangeCurrentFolder(selectedFile);

        // Opens 'otherFile' (if any) in the inactive panel.
        if (otherFile != null) {
            // Waits for the previous folder change to be finished.
            if (openThread != null) {
                while (openThread.isAlive()) {
                    try {
                        openThread.join();
                    } catch(InterruptedException ignore) {}
                }
            }
            mainFrame.getInactivePanel().tryChangeCurrentFolder(otherFile);
        }
    }

    @Nullable
    private AbstractFile findOtherFile(AbstractFile selectedFile) {
        try {
            BaseFileTableModel otherTableModel = mainFrame.getInactiveTable().getFileTableModel();

            if (mainFrame.getActiveTable().isParentFolderSelected()) {
                return otherTableModel.getParentFolder();
            } else {
                // Look for a file in the other table with the same name as the selected one (case insensitive)
                int fileCount = otherTableModel.getFileCount();
                String targetFilename = selectedFile.getName();
                AbstractFile otherFile = null;
                for (int i = 0; i < fileCount; i++) {
                    otherFile = otherTableModel.getCachedFileAt(i);
                    if (otherFile.getName().equalsIgnoreCase(targetFilename)) {
                        break;
                    }
                    if (i == fileCount-1) {
                        otherFile = null;
                    }
                }
                return otherFile;
            }
        } catch (Exception e) {
            return null;
        }
    }

    @Override
	public ActionDescriptor getDescriptor() {
		return new Descriptor();
	}


    public static final class Descriptor extends AbstractActionDescriptor {
    	public static final String ACTION_ID = "OpenInBothPanels";
    	
		public String getId() {
		    return ACTION_ID;
		}

		public ActionCategory getCategory() {
		    return ActionCategory.NAVIGATION;
		}

		public KeyStroke getDefaultAltKeyStroke() {
		    return null;
		}

		public KeyStroke getDefaultKeyStroke() {
            return KeyStroke.getKeyStroke(KeyEvent.VK_O, KeyEvent.SHIFT_DOWN_MASK | CTRL_OR_META_DOWN_MASK);
        }

        public MuAction createAction(MainFrame mainFrame, Map<String,Object> properties) {
            return new OpenInBothPanelsAction(mainFrame, properties);
        }
    }
}
