package com.mucommander.commons.file.impl.deb;

import java.io.IOException;

import com.mucommander.commons.file.AbstractArchiveFile;
import com.mucommander.commons.file.AbstractFile;
import com.mucommander.commons.file.ArchiveFormatProvider;
import com.mucommander.commons.file.filter.ExtensionFilenameFilter;
import com.mucommander.commons.file.filter.FilenameFilter;
import com.mucommander.commons.file.impl.SevenZipJBindingROArchiveFile;

import net.sf.sevenzipjbinding.ArchiveFormat;

public class DebFormatProvider implements ArchiveFormatProvider {
    private static final String[] EXTENSIONS = { ".deb" };

    private final static ExtensionFilenameFilter FILENAME_FILTER = new ExtensionFilenameFilter(EXTENSIONS);

    private final static byte[] SIGNATURE = {}; // TODO check in libmagic source

    @Override
    public AbstractArchiveFile getFile(AbstractFile file) throws IOException {
        return new SevenZipJBindingROArchiveFile(file, ArchiveFormat.AR, SIGNATURE);
    }

    @Override
    public FilenameFilter getFilenameFilter() {
        return FILENAME_FILTER;
    }

    @Override
    public String[] getFileExtensions() {
        return EXTENSIONS;
    }

}
