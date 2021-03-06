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

package com.mucommander.ui.theme;

import java.awt.Color;
import java.awt.Font;
import java.io.*;
import java.util.*;

import com.mucommander.commons.file.util.PathUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mucommander.PlatformManager;
import com.mucommander.RuntimeConstants;
import com.mucommander.commons.file.AbstractFile;
import com.mucommander.commons.file.FileFactory;
import com.mucommander.commons.file.filter.ExtensionFilenameFilter;
import com.mucommander.commons.file.util.ResourceLoader;
import com.mucommander.commons.io.StreamUtils;
import com.mucommander.commons.util.StringUtils;
import com.mucommander.conf.MuConfigurations;
import com.mucommander.conf.MuPreference;
import com.mucommander.conf.MuPreferences;
import com.mucommander.io.backup.BackupInputStream;
import com.mucommander.io.backup.BackupOutputStream;
import com.mucommander.utils.text.Translator;
import com.mucommander.ui.theme.Theme.Type;

/**
 * Offers methods for accessing and modifying themes.
 * @author Nicolas Rinaudo
 */
public class ThemeManager {
	private static Logger logger;
	
    // - Class variables -----------------------------------------------------------------
    // -----------------------------------------------------------------------------------
    /** Path to the user defined theme file. */
    private static       AbstractFile userThemeFile;
    /** Default user defined theme file name. */
    private static final String       USER_THEME_FILE_NAME             = "user_theme.xml";
    /** Path to the custom themes repository. */
    private static final String       CUSTOM_THEME_FOLDER              = "themes";
    /** List of all registered theme change listeners. */
    private static final WeakHashMap<ThemeListener, Object>  listeners = new WeakHashMap<>();
    /** List of all predefined theme names. */
    private static List<String> predefinedThemeNames;
    /** List of all predefined syntax highlight theme names. */
    private static List<String> predefinedSyntaxThemeNames;


    // - Instance variables --------------------------------------------------------------
    // -----------------------------------------------------------------------------------
    /** Whether or not the user theme was modified. */
    private static boolean       wasUserThemeModified;
    /** Theme that is currently applied to muCommander. */
    private static Theme         currentTheme;
    /** Used to listen on the current theme's modifications. */
    private static ThemeListener listener = new CurrentThemeListener();
    /** Theme that is currently applied to viewer and editor. */
    private static String currentSyntaxThemeName;



    // - Initialisation ------------------------------------------------------------------
    // -----------------------------------------------------------------------------------

    /**
     * Prevents instanciation of the class.
     */
    private ThemeManager() {}

    /**
     * Loads the current theme.
     * <p>
     * This method goes through the following steps:
     * <ul>
     *  <li>Try to load the theme defined in the configuration.</li>
     *  <li>If that failed, try to load the default theme.</li>
     *  <li>If that failed, try to load the user theme if that hasn't been tried yet.</li>
     *  <li>If that failed, use an empty theme.</li>
     * </ul>
     */
    public static void loadCurrentTheme() {
        Theme.Type type;               // Current theme's type.
        String name;               // Current theme's name.
        boolean wasUserThemeLoaded; // Whether we have tried loading the user theme or not.

        // Loads the current theme type as defined in configuration.
        try {
            type = getThemeTypeFromLabel(MuConfigurations.getPreferences().getVariable(MuPreference.THEME_TYPE, MuPreferences.DEFAULT_THEME_TYPE));
        } catch(Exception e) {
            e.printStackTrace();
            type = getThemeTypeFromLabel(MuPreferences.DEFAULT_THEME_TYPE);
        }

        // Loads the current theme name as defined in configuration.
        if (type != Theme.Type.USER) {
            wasUserThemeLoaded = false;
            name = MuConfigurations.getPreferences().getVariable(MuPreference.THEME_NAME, MuPreferences.DEFAULT_THEME_NAME);
        } else {
            name = null;
            wasUserThemeLoaded = true;
        }
        // If the current theme couldn't be loaded, uses the default theme as defined in the configuration.
        currentTheme = null;
        try {
            currentTheme = readTheme(type, name);
        } catch(Exception e1) {
            e1.printStackTrace();
            type = getThemeTypeFromLabel(MuPreferences.DEFAULT_THEME_TYPE);
            name = MuPreferences.DEFAULT_THEME_NAME;

            if (type == Theme.Type.USER) {
                wasUserThemeLoaded = true;
            }

            // If the default theme can be loaded, tries to load the user theme if we haven't done so yet.
            // If we have, or if it fails, defaults to an empty user theme.
            try {
                currentTheme = readTheme(type, name);
            } catch(Exception e2) {
                if (!wasUserThemeLoaded) {
                    try {currentTheme = readTheme(Theme.Type.USER, null);}
                    catch(Exception e3) {
                        e3.printStackTrace();
                    }
                }
                if (currentTheme == null) {
                    currentTheme         = new Theme(listener);
                    wasUserThemeModified = true;
                }
            }
            setConfigurationTheme(currentTheme);
        }
        currentSyntaxThemeName = MuConfigurations.getPreferences().getVariable(MuPreference.SYNTAX_THEME_NAME, MuPreferences.DEFAULT_SYNTAX_THEME_NAME);
    }



    // - Themes access -------------------------------------------------------------------
    // -----------------------------------------------------------------------------------
    private static Iterator<String> predefinedThemeNames() {
        // The list of predefined themes is no longer dynamically created as this causes Webstart to retrieve and
        // explore the application's JAR via HTTP, which is inefficient and prevents the application from being
        // launched offline.
        if (predefinedThemeNames == null) {
            try {
                predefinedThemeNames = getThemeNames(ResourceLoader.getRootPackageAsFile(ThemeManager.class).getChild(PathUtils.removeLeadingSeparator(RuntimeConstants.THEMES_PATH, "/")));
            } catch (IOException e) {
                predefinedThemeNames = new ArrayList<>();
            }
        }
        return predefinedThemeNames.iterator();
    }

    public static List<String> predefinedSyntaxThemeNames() {
        // The list of predefined themes is no longer dynamically created as this causes Webstart to retrieve and
        // explore the application's JAR via HTTP, which is inefficient and prevents the application from being
        // launched offline.
        if (predefinedSyntaxThemeNames == null) {
            try {
                predefinedSyntaxThemeNames = getThemeNames(ResourceLoader.getRootPackageAsFile(ThemeManager.class).getChild(PathUtils.removeLeadingSeparator(RuntimeConstants.TEXT_SYNTAX_THEMES_PATH, "/")));
            } catch (IOException e) {
                predefinedSyntaxThemeNames = new ArrayList<>();
            }
        }
        return predefinedSyntaxThemeNames;
    }


    private static Iterator<String> customThemeNames() throws IOException {
        return getThemeNames(FileFactory.getFile(getCustomThemesFolder().getAbsolutePath())).iterator();
    }

    private static List<String> getThemeNames(AbstractFile themeFolder) {
        try {
            AbstractFile[] files = themeFolder.ls(new ExtensionFilenameFilter(".xml"));
            List<String> names = new ArrayList<>();
            for (AbstractFile file : files)
                names.add(getThemeName(file));
            return names;
        } catch(Exception e) {
            return new ArrayList<>();
        }
    }

    private static List<Theme> getAvailableThemes() {
        Iterator<String> iterator;

        List<Theme> themes = new ArrayList<>();

        // Tries to load the user theme. If it's corrupt, uses an empty user theme.
        try {
            themes.add(readTheme(Theme.Type.USER, null));
        } catch(Exception e) {
            themes.add(new Theme(listener));
        }

        // Loads predefined themes.
        iterator = predefinedThemeNames();
        while (iterator.hasNext()) {
            String name = iterator.next();
            try {
                themes.add(readTheme(Theme.Type.PREDEFINED, name));
            } catch(Exception e) {
                getLogger().warn("Failed to load predefined theme " + name, e);
            }
        }

        // Loads custom themes.
        try {
            iterator = customThemeNames();
            while (iterator.hasNext()) {
                String name = iterator.next();
                try {
                    themes.add(readTheme(Theme.Type.CUSTOM, name));
                } catch(Exception e) {
                    getLogger().warn("Failed to load custom theme " + name, e);
                }
            }
        } catch(Exception e) {
            getLogger().warn("Failed to load custom themes", e);
        }

        // Sorts the themes by name.
        themes.sort(Comparator.comparing(t -> (t.getName())));

        return themes;
    }

    private static List<String> getAvailableThemeNames() {
        List<String> themes = new ArrayList<>();

        // Adds the user theme name.
        themes.add(Translator.get("theme.custom_theme"));

        // Adds predefined theme names.
        Iterator<String> iterator = predefinedThemeNames();
        while(iterator.hasNext())
            themes.add(iterator.next());

        // Adds custom theme names.
        try {
            iterator = customThemeNames();
            while (iterator.hasNext()) {
                themes.add(iterator.next());
            }
        } catch(Exception e) {
            getLogger().debug("Failed to load custom theme names", e);
        }

        // Sorts the theme names.
        Collections.sort(themes);

        return themes;
    }

    public static Iterator<String> availableThemeNames() {
        return getAvailableThemeNames().iterator();
    }

    public static synchronized Iterator<Theme> availableThemes() {
        return getAvailableThemes().iterator();
    }



    // - Theme paths access --------------------------------------------------------------
    // -----------------------------------------------------------------------------------
    /**
     * Returns the path to the user's theme file.
     * <p>
     * This method cannot guarantee the file's existence, and it's up to the caller
     * to deal with the fact that the user might not actually have created a user theme.
     * <p>
     * This method's return value can be modified through {@link #setUserThemeFile(String)}.
     * If this wasn't called, the default path will be used. This is generated by calling
     * <code>new java.io.File({@link com.mucommander.PlatformManager#getPreferencesFolder()}, {@link #USER_THEME_FILE_NAME})</code>.
     *
     * @return             the path to the user's theme file.
     * @see                #setUserThemeFile(String)
     * @throws IOException if an error occurred while locating the default user theme file.
     */
    private static AbstractFile getUserThemeFile() throws IOException {
        if (userThemeFile == null) {
            return PlatformManager.getPreferencesFolder().getChild(USER_THEME_FILE_NAME);
        }
        return userThemeFile;
    }

    /**
     * Sets the path to the user theme file.
     * <p>
     * The specified file does not have to exist. If it does, however, it must be accessible.
     *
     * @param  file                  path to the user theme file.
     * @throws FileNotFoundException if <code>file</code> is not accessible.
     * @see                          #getUserThemeFile()
     */
    private static void setUserThemeFile(File file) throws FileNotFoundException {
        setUserThemeFile(FileFactory.getFile(file.getAbsolutePath()));
    }

    /**
     * Sets the path to the user theme file.
     * <p>
     * The specified file does not have to exist. If it does, however, it must be accessible.
     *
     * @param  file                     path to the user theme file.
     * @throws IllegalArgumentException if <code>file</code> exists but is not accessible.
     * @see                             #getUserThemeFile()
     */
    private static void setUserThemeFile(AbstractFile file) throws FileNotFoundException {
        if (file.isBrowsable()) {
            throw new FileNotFoundException("Not a valid file: " + file.getAbsolutePath());
        }
        userThemeFile = file;
    }

    /**
     * Sets the path to the user theme file.
     * <p>
     * The specified file does not have to exist. If it does, however, it must be accessible.
     *
     * @param  path                  path to the user theme file.
     * @throws FileNotFoundException if <code>path</code> is not accessible.
     * @see                          #getUserThemeFile()
     */
    private static void setUserThemeFile(String path) throws FileNotFoundException {
        AbstractFile file = FileFactory.getFile(path);

        if (file == null) {
            setUserThemeFile(new File(path));
        } else {
            setUserThemeFile(file);
        }
    }

    /**
     * Returns the path to the custom themes' folder.
     * <p>
     * This method guarantees that the returned file actually exists.
     *
     * @return the path to the custom themes' folder.
     * @throws IOException if an error occured while locating the default user themes folder.
     */
    public static AbstractFile getCustomThemesFolder() throws IOException {
        // Retrieves the path to the custom themes folder and creates it if necessary.
        AbstractFile customFolder = PlatformManager.getPreferencesFolder().getChild(CUSTOM_THEME_FOLDER);
        if (!customFolder.exists()) {
            customFolder.mkdir();
        }

        return customFolder;
    }


    // - Theme renaming / deleting -------------------------------------------------------
    // -----------------------------------------------------------------------------------
    public static void deleteCustomTheme(String name) throws IOException {
        // Makes sure the specified theme is not the current one.
        if (isCurrentTheme(Theme.Type.CUSTOM, name)) {
            throw new IllegalArgumentException("Cannot delete current theme.");
        }

        // Deletes the theme.
        AbstractFile file = getFile(Type.CUSTOM, name);
        if (file.exists()) {
            file.delete();
        }
    }

    public static void renameCustomTheme(Theme theme, String name) throws IOException {
        if (theme.getType() != Theme.Type.CUSTOM) {
            throw new IllegalArgumentException("Cannot rename non-custom themes.");
        }

        // Makes sure the operation is necessary.
        if (theme.getName().equals(name)) {
            return;
        }

        // Computes a legal new name and renames theme.
        name = getAvailableCustomThemeName(name);
		getFile(Type.CUSTOM, theme.getName()).renameTo(getFile(Type.CUSTOM, name));
        theme.setName(name);
        if (isCurrentTheme(theme)) {
            setConfigurationTheme(theme);
        }
    }



    // - Theme writing -------------------------------------------------------------------
    // -----------------------------------------------------------------------------------
    /**
     * Returns an output stream on the specified custom theme.
     * @param  name        name of the custom theme on which to open an output stream.
     * @return             an output stream on the specified custom theme.
     * @throws IOException if an I/O related error occurs.
     */
    private static BackupOutputStream getCustomThemeOutputStream(String name) throws IOException {
        return new BackupOutputStream(getCustomThemesFolder().getChild(name + ".xml"));
    }

    /**
     * Returns an output stream on the user theme.
     * @return             an output stream on the user theme.
     * @throws IOException if an I/O related error occurs.
     */
    private static BackupOutputStream getUserThemeOutputStream() throws IOException {
        return new BackupOutputStream(getUserThemeFile());
    }

    /**
	 * Returns the file to read/write the requested theme.
	 * <p>
	 * If <code>type</code> is equal to {@link Theme.Type#USER}, the <code>name</code> argument will be ignored: there
	 * is only one user theme.
	 *
	 * If <code>type</code> is equal to {@link Theme.Type#PREDEFINED}, an <code>IllegalArgumentException</code> will be
	 * thrown: predefined themes are not editable.
	 *
	 * @param type
	 *            type of the theme for which to get the file.
	 * @param name
	 *            name of the theme for which to get the file.
	 * @return a file for the requested theme.
	 * @throws IllegalArgumentException
	 *             if <code>type</code> is equal to {@link Theme.Type#PREDEFINED}.
	 */
	public static AbstractFile getFile(Theme.Type type, String name) throws IOException {
		switch (type) {
            case PREDEFINED:
                throw new IllegalArgumentException("Can not open output streams on predefined themes.");

            case CUSTOM:
                return getCustomThemesFolder().getChild(name + ".xml");

            case USER:
                return getUserThemeFile();
            }

		// Unknown theme.
		throw new IllegalArgumentException("Illegal theme type: " + type);
	}

    /**
     * Returns an output stream on the requested theme.
     * <p>
     * This method is just a convenience, and wraps calls to {@link #getUserThemeInputStream()},
     * and {@link #getCustomThemeInputStream(String)}.
     *
     * <p>
     * If <code>type</code> is equal to {@link Theme.Type#USER}, the <code>name</code> argument
     * will be ignored: there is only one user theme.
     *
     * <p>
     * If <code>type</code> is equal to {@link Theme.Type#PREDEFINED}, an <code>IllegalArgumentException</code>
     * will be thrown: predefined themes are not editable.
     *
     * @param  type        type of the theme on which to open an output stream.
     * @param  name        name of the theme on which to open an output stream.
     * @return             an output stream on the requested theme.
     * @throws IOException if an I/O related error occurs.
     */
    private static BackupOutputStream getOutputStream(Theme.Type type, String name) throws IOException {
        switch(type) {
            case PREDEFINED:
                throw new IllegalArgumentException("Can not open output streams on predefined themes.");

            case CUSTOM:
                return getCustomThemeOutputStream(name);

            case USER:
                return getUserThemeOutputStream();
        }

        // Unknown theme.
        throw new IllegalArgumentException("Illegal theme type: " + type);
    }

    /**
     * Writes the content of the specified theme data to the specified output stream.
     * <p>
     * This method differs from {@link #exportTheme(Theme,OutputStream)} in that it will
     * write the theme data only, skipping comments and other metadata.
     *
     * @param  data        theme data to write.
     * @param  out         where to write the theme data.
     * @throws IOException if an I/O related error occurs.
     * @see                #exportTheme(Theme,OutputStream)
     * @see                #exportTheme(Theme,File)
     * @see                #writeThemeData(ThemeData,File).
     */
    private static void writeThemeData(ThemeData data, OutputStream out) throws IOException {ThemeWriter.write(data, out);}

    /**
     * Writes the content of the specified theme data to the specified file.
     * <p>
     * This method differs from {@link #exportTheme(Theme,File)} in that it will
     * write the theme data only, skipping comments and other metadata.
     *
     * @param  data        theme data to write.
     * @param  file        file in which to write the theme data.
     * @throws IOException if an I/O related error occurs.
     * @see                #exportTheme(Theme,OutputStream)
     * @see                #exportTheme(Theme,File)
     * @see                #writeThemeData(ThemeData,OutputStream).
     */
    private static void writeThemeData(ThemeData data, File file) throws IOException {
        try (OutputStream out = new FileOutputStream(file)) {
            writeThemeData(data, out);
        }

    }

    /**
     * Writes the content of the specified theme to its description file.
     * @param  theme                    theme to write.
     * @throws IOException              if any I/O related error occurs.
     * @throws IllegalArgumentException if <code>theme</code> is a predefined theme.
     * @see                             #writeTheme(ThemeData,Theme.Type,String)
     */
    public static void writeTheme(Theme theme) throws IOException {writeTheme(theme, theme.getType(), theme.getName());}

    /**
     * Writes the specified theme data over the theme described by <code>type</code> and <code>name</code>.
     * <p>
     * Note that this method doesn't check whether this will overwrite an existing theme.
     *
     * <p>
     * If <code>type</code> equals {@link Theme.Type#USER}, <code>name</code> will be ignored.
     *
     * @param  data                     data to write.
     * @param  type                     type of the theme that is being written.
     * @param  name                     name of the theme that is being written.
     * @throws IOException              if any I/O related error occurs.
     * @throws IllegalArgumentException if <code>theme</code> is a predefined theme.
     * @see                             #writeTheme(Theme)
     */
    public static void writeTheme(ThemeData data, Theme.Type type, String name) throws IOException {
        try (OutputStream out = getOutputStream(type, name)) {
            writeThemeData(data, out);
        }
    }

    /**
     * Exports the specified theme to the specified output stream.
     * <p>
     * If <code>type</code> is equal to {@link Theme.Type#USER}, the <code>name</code> argument will be ignored
     * as there is only one user theme.
     *
     * <p>
     * This method differs from {@link #writeThemeData(ThemeData,OutputStream)} in that it doesn't only copy
     * the theme's data, but the whole content of the theme file, including comments. It also requires the theme
     * file to exist.
     *
     * @param  type        type of the theme to export.
     * @param  name        name of the theme to export.
     * @param  out         where to write the theme.
     * @throws IOException if any I/O related error occurs.
     * @see                #exportTheme(Theme.Type,String,File)
     * @see                #writeThemeData(ThemeData,OutputStream)
     */
    private static void exportTheme(Theme.Type type, String name, OutputStream out) throws IOException {
        try (InputStream in = getInputStream(type, name)) {
            StreamUtils.copyStream(in, out);
        }
    }

    /**
     * Exports the specified theme to the specified output stream.
     * <p>
     * If <code>type</code> is equal to {@link Theme.Type#USER}, the <code>name</code> argument will be ignored
     * as there is only one user theme.
     *
     * <p>
     * This method differs from {@link #writeThemeData(ThemeData,File)} in that it doesn't only copy
     * the theme's data, but the whole content of the theme file, including comments.
     *
     * @param  type        type of the theme to export.
     * @param  name        name of the theme to export.
     * @param  file        where to write the theme.
     * @throws IOException if any I/O related error occurs
     * @see                #exportTheme(Theme.Type,String,OutputStream)
     * @see                #writeThemeData(ThemeData,File).
     */
    private static void exportTheme(Theme.Type type, String name, File file) throws IOException {
        try (OutputStream out = new FileOutputStream(file)) {
            exportTheme(type, name, out);
        }
    }

    /**
     * Exports the specified theme to the specified output stream.
     * <p>
     * This is a convenience method only and is strictly equivalent to calling
     * <code>{@link #exportTheme(Theme.Type,String,OutputStream) exportTheme(}theme.getType(), theme.getName(), out);</code>
     *
     * @param  theme       theme to export.
     * @param  out         where to write the theme.
     * @throws IOException if any I/O related error occurs.
     */
    public static void exportTheme(Theme theme, OutputStream out) throws IOException {exportTheme(theme.getType(), theme.getName(), out);}

    /**
     * Exports the specified theme to the specified output stream.
     * <p>
     * This is a convenience method only and is strictly equivalent to calling
     * <code>{@link #exportTheme(Theme.Type,String,File) exportTheme(}theme.getType(), theme.getName(), file);</code>
     *
     * @param  theme       theme to export.
     * @param  file        where to write the theme.
     * @throws IOException if any I/O related error occurs.
     */
    public static void exportTheme(Theme theme, File file) throws IOException {exportTheme(theme.getType(), theme.getName(), file);}

    private static String getAvailableCustomThemeName(File file) {
        String   name;

        // Retrieves the file's name, cutting the .xml extension off if
        // necessary.
        if(StringUtils.endsWithIgnoreCase(name = file.getName(), ".xml"))
            name = name.substring(0, name.length() - 4);

        return getAvailableCustomThemeName(name);
    }

    private static boolean isNameAvailable(String name, Iterator<String> names) {
        while(names.hasNext())
            if(names.next().equals(name))
                return false;
        return true;
    }

    private static String getAvailableCustomThemeName(String name) {
        List<String> names = getAvailableThemeNames();

        // If the name is available, no need to suffix it with (xx).
        if(isNameAvailable(name, names.iterator()))
            return name;

        // Removes any trailing (x) construct, and adds a trailing space if necessary.
        name = name.replaceFirst("\\([0-9]+\\)$", "");
        if (name.charAt(name.length() - 1) != ' ') {
            name = name + ' ';
        }

        int i = 1;
        String buffer;
        do {
            buffer = name + '(' + (++i) + ')';
        } while(!isNameAvailable(buffer, names.iterator()));

        return buffer;
    }

    public static Theme duplicateTheme(Theme theme) throws IOException {
        return importTheme(theme.cloneData(), theme.getName());
    }

    public static Theme importTheme(ThemeData data, String name) throws IOException {
        writeTheme(data, Theme.Type.CUSTOM, name = getAvailableCustomThemeName(name));
        return new Theme(listener, data, Theme.Type.CUSTOM, name);
    }

    public static Theme importTheme(File file) throws Exception {
        String       name; // Name of the new theme.
        OutputStream out;  // Where to write the theme data to.
        InputStream  in;   // Where to read the theme data from.
        ThemeData    data;

        // Makes sure the file contains a valid theme.
        data = readThemeData(file);

        // Initialisation.
        name = getAvailableCustomThemeName(file);
        out  = null;
        in   = null;

        // Imports the theme.
        try {StreamUtils.copyStream(in = new FileInputStream(file), out = getCustomThemeOutputStream(name));}

        // Cleanup.
        finally {
            if(in != null) {
                try {in.close();}
                catch(Exception e) {
                    e.printStackTrace();
                }
            }
            if(out != null) {
                try {out.close();}
                catch(Exception e) {
                    e.printStackTrace();
                }
            }
        }

        return new Theme(listener, data, Theme.Type.CUSTOM, name);
    }



    // - Theme reading -------------------------------------------------------------------
    // -----------------------------------------------------------------------------------
    /**
     * Returns an input stream on the user theme.
     * @return             an input stream on the user theme.
     * @throws IOException if an I/O related error occurs.
     */
    private static InputStream getUserThemeInputStream() throws IOException {
        return new BackupInputStream(getUserThemeFile());
    }

    /**
     * Returns an input stream on the requested predefined theme.
     * @param  name        name of the predefined theme on which to open an input stream.
     * @return             an input stream on the requested predefined theme.
     */
    private static InputStream getPredefinedThemeInputStream(String name) {
        return ResourceLoader.getResourceAsStream(RuntimeConstants.THEMES_PATH + "/" + name + ".xml");
    }

    /**
     * Returns an input stream on the requested predefined theme for editor.
     * @param  name        name of the predefined editor theme on which to open an input stream.
     * @return             an input stream on the requested predefined theme.
     */
    private static InputStream getPredefinedEditorThemeInputStream(String name) {
        return ResourceLoader.getResourceAsStream(RuntimeConstants.TEXT_SYNTAX_THEMES_PATH + "/" + name + ".xml");
    }

    /**
     * Returns an input stream on the requested custom theme.
     * @param  name        name of the custom theme on which to open an input stream.
     * @return             an input stream on the requested custom theme.
     * @throws IOException if an I/O related error occurs.
     */
    private static InputStream getCustomThemeInputStream(String name) throws IOException {
		return new BackupInputStream(getFile(Type.CUSTOM, name));
    }

    /**
     * Opens an input stream on the requested theme.
     * <p>
     * This method is just a convenience, and wraps calls to {@link #getUserThemeInputStream()},
     * {@link #getPredefinedThemeInputStream(String)} and {@link #getCustomThemeInputStream(String)}.
     *
     * @param  type                     type of the theme to open an input stream on.
     * @param  name                     name of the theme to open an input stream on.
     * @return                          an input stream opened on the requested theme.
     * @throws IOException              thrown if an IO related error occurs.
     * @throws IllegalArgumentException thrown if <code>type</code> is not a legal theme type.
     */
    private static InputStream getInputStream(Theme.Type type, String name) throws IOException {
        switch(type) {
            case USER:
                return getUserThemeInputStream();

            case PREDEFINED:
                return getPredefinedThemeInputStream(name);

            case CUSTOM:
                return getCustomThemeInputStream(name);
        }

        // Error handling.
        throw new IllegalArgumentException("Illegal theme type: " + type);
    }

    /**
     * Returns the requested theme.
     * @param  type type of theme to retrieve.
     * @param  name name of the theme to retrieve.
     * @return the requested theme.
     */
    private static Theme readTheme(Theme.Type type, String name) throws Exception {

        // Do not reload the current theme, both for optimisation purposes and because
        // it might cause user theme modifications to be lost.
        if (currentTheme != null && isCurrentTheme(type, name))
            return currentTheme;

        // Reads the theme data.
        try (InputStream in = getInputStream(type, name)) {
            ThemeData data = readThemeData(in);
            return new Theme(listener, data, type, name);
        }
    }

    /**
     * Return the requested theme for file viewer/editor
     * @param name name ot the theme
     * @return the resulting theme data.
     * @throws IOException
     */
    public static EditorTheme readEditorTheme(String name) throws IOException {
        InputStream is = getPredefinedEditorThemeInputStream(name);
        return EditorTheme.load(is, ThemeManager.getCurrentFont(Theme.EDITOR_FONT));
    }

    /**
     * Reads theme data from the specified input stream.
     * @param  in        where to read the theme data from.
     * @return           the resulting theme data.
     * @throws IOException if an I/O or syntax error occurs.
     */
    private static ThemeData readThemeData(InputStream in) throws Exception {
        ThemeData data = new ThemeData(); // Buffer for the data.

        // Reads the theme data.
        ThemeReader.read(in, data);

        return data;
    }

    /**
     * Reads theme data from the specified file.
     * @param  file      where to read the theme data from.
     * @return           the resulting theme data.
     * @throws Exception if an I/O or syntax error occurs.
     */
    private static ThemeData readThemeData(File file) throws Exception {
        try (InputStream in = new BufferedInputStream(new FileInputStream(file))) {
            return readThemeData(in);
        }
    }



    // - Current theme access ------------------------------------------------------------
    // -----------------------------------------------------------------------------------
    private static void setConfigurationTheme(Theme.Type type, String name) {
        // Sets configuration depending on the new theme's type.
        switch(type) {
            case USER:
                MuConfigurations.getPreferences().setVariable(MuPreference.THEME_TYPE, MuPreferences.THEME_USER);
                MuConfigurations.getPreferences().setVariable(MuPreference.THEME_NAME, null);
                break;

            case PREDEFINED:
                MuConfigurations.getPreferences().setVariable(MuPreference.THEME_TYPE, MuPreferences.THEME_PREDEFINED);
                MuConfigurations.getPreferences().setVariable(MuPreference.THEME_NAME, name);
                break;

            case CUSTOM:
                MuConfigurations.getPreferences().setVariable(MuPreference.THEME_TYPE, MuPreferences.THEME_CUSTOM);
                MuConfigurations.getPreferences().setVariable(MuPreference.THEME_NAME, name);
                break;

                // Error.
            default:
                throw new IllegalStateException("Illegal theme type: " + type);
            }
    }

    /**
     * Sets the specified theme as the current theme in configuration.
     * @param theme theme to set as current.
     */
    private static void setConfigurationTheme(Theme theme) {setConfigurationTheme(theme.getType(), theme.getName());}


    /**
     * Saves the current theme if necessary.
     */
    private static void saveCurrentTheme() throws IOException {
        // Makes sure no NullPointerException is raised if this method is called
        // before themes have been initialised.
        if (currentTheme == null) {
            return;
        }

        // Saves the user theme if it's the current one.
        if (currentTheme.getType() == Theme.Type.USER && wasUserThemeModified) {
            writeTheme(currentTheme);
            wasUserThemeModified = false;
        }
    }

    public static Theme getCurrentTheme() {
        return currentTheme;
    }

    public static String getCurrentSyntaxThemeName() {return currentSyntaxThemeName;}

    /**
     * Changes the current theme.
     * <p>
     * This method will change the current theme and trigger all the proper events.
     *
     * @param  theme                    theme to use as the current theme.
     * @throws IllegalArgumentException thrown if the specified theme could not be loaded.
     */
    public synchronized static void setCurrentTheme(Theme theme) {
        // Makes sure we're not doing something useless.
        if (isCurrentTheme(theme)) {
            return;
        }

        // Saves the current theme if necessary.
        try {
            saveCurrentTheme();
        } catch(IOException e) {
            getLogger().warn("Couldn't save current theme", e);
        }

        // Updates muCommander's configuration.
        Theme oldTheme = currentTheme;
        setConfigurationTheme(currentTheme = theme);

        // Triggers the events generated by the theme change.
        triggerThemeChange(oldTheme, currentTheme);
    }

    /**
     *
     * @param name name of the theme
     */
    public synchronized static void setCurrentSyntaxTheme(String name) {
        currentSyntaxThemeName = name;
        MuConfigurations.getPreferences().setVariable(MuPreference.SYNTAX_THEME_NAME, name);
    }

    public synchronized static Font getCurrentFont(int id) {return currentTheme.getFont(id);}

    public synchronized static Color getCurrentColor(int id) {return currentTheme.getColor(id);}

    public synchronized static Theme overwriteUserTheme(ThemeData themeData) throws IOException {
        // If the current theme is the user one, we just need to import the new data.
        if (currentTheme.getType() == Theme.Type.USER) {
            currentTheme.importData(themeData);
            writeTheme(currentTheme);
            return currentTheme;
        } else {
            writeTheme(themeData, Theme.Type.CUSTOM, currentTheme.getName());
            return new Theme(listener, themeData);
        }
    }

    /**
     * Checks whether setting the specified font would require overwriting of the user theme.
     * @param  fontId identifier of the font to set.
     * @param  font   value for the specified font.
     * @return        <code>true</code> if applying the specified font will overwrite the user theme,
     *                <code>false</code> otherwise.
     */
    private synchronized static boolean willOverwriteUserTheme(int fontId, Font font) {
        return currentTheme.isFontDifferent(fontId, font) && currentTheme.getType() != Theme.Type.USER;
    }

    /**
     * Checks whether setting the specified color would require overwriting of the user theme.
     * @param  colorId identifier of the color to set.
     * @param  color   value for the specified color.
     * @return         <code>true</code> if applying the specified color will overwrite the user theme,
     *                 <code>false</code> otherwise.
     */
    private synchronized static boolean willOverwriteUserTheme(int colorId, Color color) {
        return currentTheme.isColorDifferent(colorId, color) && currentTheme.getType() != Theme.Type.USER;
    }

    /**
     * Updates the current theme with the specified font.
     * <p>
     * This method might require to overwrite the user theme: custom and predefined themes are
     * read only. In order to modify them, the ThemeManager must overwrite the user theme with
     * the current theme and then set the font.<br/>
     * If necessary, this can be checked beforehand by a call to {@link #willOverwriteUserTheme(int,Font)}.
     *
     * @param  id   identifier of the font to set.
     * @param  font font to set.
     */
    synchronized static boolean setCurrentFont(int id, Font font) {
        // Only updates if necessary.
        if (currentTheme.isFontDifferent(id, font)) {
            // Checks whether we need to overwrite the user theme to perform this action.
            if (currentTheme.getType() != Theme.Type.USER) {
                currentTheme.setType(Theme.Type.USER);
                setConfigurationTheme(currentTheme);
            }

            currentTheme.setFont(id, font);
            return true;
        }
        return false;
    }

    /**
     * Updates the current theme with the specified color.
     * <p>
     * This method might require to overwrite the user theme: custom and predefined themes are
     * read only. In order to modify them, the ThemeManager must overwrite the user theme with
     * the current theme and then set the color.<br/>
     * If necessary, this can be checked beforehand by a call to {@link #willOverwriteUserTheme(int,Color)}.
     *
     * @param  id   identifier of the color to set.
     * @param  color color to set.
     */
    synchronized static boolean setCurrentColor(int id, Color color) {
        // Only updates if necessary.
        if(currentTheme.isColorDifferent(id, color)) {
            // Checks whether we need to overwrite the user theme to perform this action.
            if(currentTheme.getType() != Theme.Type.USER) {
                currentTheme.setType(Theme.Type.USER);
                setConfigurationTheme(currentTheme);
            }

            // Updates the color and notifies listeners.
            currentTheme.setColor(id, color);
            return true;
        }
        return false;
    }

    /**
     * Returns <code>true</code> if the specified theme is the current one.
     * @param theme theme to check.
     * @return <code>true</code> if the specified theme is the current one, <code>false</code> otherwise.
     */
    public static boolean isCurrentTheme(Theme theme) {return theme == currentTheme;}

    private static boolean isCurrentTheme(Theme.Type type, String name) {
        return type == currentTheme.getType() && (type == Type.USER || name.equals(currentTheme.getName()));
    }




    // - Events management ---------------------------------------------------------------
    // -----------------------------------------------------------------------------------
    /**
     * Notifies all listeners that the current theme has changed.
     * <p>
     * This method is meant to be called when the current theme has been changed.
     * It will compare all fonts and colors in <code>oldTheme</code> and <code>newTheme</code> and,
     * if any is found to be different, trigger the corresponding event.
     *
     * <p>
     * At the end of this method, all registered listeners will have been made aware of the new values
     * they should be using.
     *
     * @param oldTheme previous current theme.
     * @param newTheme new current theme.
     * @see            #triggerFontEvent(FontChangedEvent)
     * @see            #triggerColorEvent(ColorChangedEvent)
     */
    private static void triggerThemeChange(Theme oldTheme, Theme newTheme) {
        // Triggers font events.
        for(int i = 0; i < Theme.FONT_COUNT; i++)
            if(oldTheme.isFontDifferent(i, newTheme.getFont(i)))
                triggerFontEvent(new FontChangedEvent(currentTheme, i, newTheme.getFont(i)));

        // Triggers color events.
        for(int i = 0; i < Theme.COLOR_COUNT; i++)
            if(oldTheme.isColorDifferent(i, newTheme.getColor(i)))
                triggerColorEvent(new ColorChangedEvent(currentTheme, i, newTheme.getColor(i)));
    }

    /**
     * Adds the specified object to the list of registered current theme listeners.
     * <p>
     * Any object registered through this method will received {@link ThemeListener#colorChanged(ColorChangedEvent) color}
     * and {@link ThemeListener#fontChanged(FontChangedEvent) font} events whenever the current theme changes.
     *
     * <p>
     * Note that these events will not necessarily be fired as a result of a direct theme change: if, for example,
     * the current theme is using look&amp;feel dependant values and the current look&amp;feel changes, the corresponding
     * events will be passed to registered listeners.
     *
     * <p>
     * Listeners are stored as weak references, to make sure that the API doesn't keep ghost copies of objects
     * whose usefulness is long since past. This forces callers to make sure they keep a copy of the listener's instance: if
     * they do not, the instance will be weakly linked and garbage collected out of existence.
     *
     * @param listener new current theme listener.
     */
    public static void addCurrentThemeListener(ThemeListener listener) {
        synchronized (listeners) {
            listeners.put(listener, null);
        }
    }

    /**
     * Removes the specified object from the list of registered theme listeners.
     * <p>
     * Note that since listeners are stored as weak references, calling this method is not strictly necessary. As soon
     * as a listener instance is not referenced anymore, it will automatically be caught and destroyed by the garbage
     * collector.
     *
     * @param listener current theme listener to remove.
     */
    public static void removeCurrentThemeListener(ThemeListener listener) {
        synchronized (listeners) {
            listeners.remove(listener);
        }
    }

    /**
     * Notifies all theme listeners of the specified font event.
     * @param event event to pass down to registered listeners.
     * @see         #triggerThemeChange(Theme,Theme)
     */
    private static void triggerFontEvent(FontChangedEvent event) {
        synchronized (listeners) {
            for (ThemeListener listener : listeners.keySet()) {
                listener.fontChanged(event);
            }
        }
    }

    /**
     * Notifies all theme listeners of the specified color event.
     * @param event event to pass down to registered listeners.
     * @see         #triggerThemeChange(Theme,Theme)
     */
    private static void triggerColorEvent(ColorChangedEvent event) {
        synchronized (listeners) {
            for (ThemeListener listener : listeners.keySet()) {
                listener.colorChanged(event);
            }
        }
    }



    // - Helper methods ------------------------------------------------------------------
    // -----------------------------------------------------------------------------------
    /**
     * Returns a valid type identifier from the specified configuration type definition.
     * @param  label label of the theme type as defined in {@link MuPreferences}.
     * @return       a valid theme type identifier.
     */
    private static Theme.Type getThemeTypeFromLabel(String label) {
        switch (label) {
            case MuPreferences.THEME_USER:
                return Theme.Type.USER;
            case MuPreferences.THEME_PREDEFINED:
                return Theme.Type.PREDEFINED;
            case MuPreferences.THEME_CUSTOM:
                return Theme.Type.CUSTOM;
        }
        throw new IllegalStateException("Unknown theme type: " + label);
    }

    private static String getThemeName(AbstractFile themeFile) {
        return themeFile.getNameWithoutExtension();
    }



    // - Listener methods ----------------------------------------------------------------
    // -----------------------------------------------------------------------------------
    /**
     * @author Nicolas Rinaudo
     */
    private static class CurrentThemeListener implements ThemeListener {
        public void fontChanged(FontChangedEvent event) {
            if (event.getSource().getType() == Theme.Type.USER) {
                wasUserThemeModified = true;
            }

            if (event.getSource() == currentTheme) {
                triggerFontEvent(event);
            }
        }

        public void colorChanged(ColorChangedEvent event) {
            if (event.getSource().getType() == Theme.Type.USER) {
                wasUserThemeModified = true;
            }

            if (event.getSource() == currentTheme) {
                triggerColorEvent(event);
            }
        }
    }

    private static Logger getLogger() {
        if (logger == null) {
            logger = LoggerFactory.getLogger(ThemeManager.class);
        }
        return logger;
    }
}
