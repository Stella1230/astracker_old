package org.rug.data.project;

import org.rug.data.characteristics.comps.JarSourceCodeRetriever;
import org.rug.data.characteristics.comps.SourceCodeRetriever;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

/**
 * Represents a version of a system of JARs and GraphML files.
 */
public class Version extends AbstractVersion{

    private final static Logger logger = LoggerFactory.getLogger(Version.class);

    /**
     * Partially builds this instance by parsing the version string from the given path.
     * @param path the directory or file that respect {@link #parseVersionString(Path)} version formatting.
     */
    public Version(Path path){
        super(path, new JarSourceCodeRetriever(path));
    }

    /**
     * Partially builds this instance by setting the source direct
     * @param path the directory or file that respect {@link #parseVersionString(Path)} version formatting.
     * @param retriever a class source code retriever.
     */
    public Version(Path path, SourceCodeRetriever retriever){
        super(path, retriever);
    }

}
