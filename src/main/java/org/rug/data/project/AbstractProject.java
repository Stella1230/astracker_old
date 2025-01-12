package org.rug.data.project;

import org.rug.data.characteristics.comps.*;
import org.rug.data.smells.ArchitecturalSmell;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Provides an implementation for the common operations on a project.
 */
public abstract class AbstractProject implements IProject {

    protected SortedMap<String, IVersion> versionedSystem;
    protected String name;
    protected Type projectType;
    protected Function<Path, IVersion> versionInitializer;
    /**
     * Instantiates this project and sets the given name.
     * @param name the name of the project.
     */
    public AbstractProject(String name, Type projectType, Comparator<String> versionStringComparator) {
        this.name = name;
        this.projectType = projectType;
        this.versionedSystem = new TreeMap<>(versionStringComparator);
        ArcanDependencyGraphParser.PROJECT_TYPE = projectType;
    }

    /**
     * Gets the name of the project as set up at instantiation time.
     * @return the name of the project.
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the architectural smells in the given version.
     * @param version the version of the system to parse smells from
     * @return the smells as a list.
     */
    public List<ArchitecturalSmell> getArchitecturalSmellsIn(IVersion version){
        var smells = ArcanDependencyGraphParser.getArchitecturalSmellsIn(version.getGraph(), this.projectType);
        var versionString = version.getVersionString();
        smells.forEach(as -> as.setAffectedVersion(versionString));
        return smells;
    }

    /**
     * Returns the architectural smells in the given version.
     * @param version the version of the system to parse smells from
     * @return the smells as a list.
     */
    public List<ArchitecturalSmell> getArchitecturalSmellsIn(String version){
        return getArchitecturalSmellsIn(versionedSystem.get(version));
    }

    @Override
    public Iterator<IVersion> iterator() {
        return versionedSystem.values().iterator();
    }

    @Override
    public void forEach(Consumer<? super IVersion> action) {
        versionedSystem.values().forEach(action);
    }

    long counter = 1;
    public void forEach(BiConsumer<? super IVersion, Long> action){
        for (IVersion version : versionedSystem.values()) {
            action.accept(version, counter++);
        }
        counter = 1;
    }

    @Override
    public Spliterator<IVersion> spliterator() {
        return versionedSystem.values().spliterator();
    }

    /**
     * Returns the version of the system with the given version string.
     * @param version the string denoting the version to retrieve.
     * @return the version object mapped to the given version string.
     */
    public IVersion getVersion(String version){
        return versionedSystem.get(version);
    }

    /**
     * Returns the version with the given version position.
     * @param versionPosition the position of the version as an index.
     * @return a version object or null if no version is found.
     */
    @Override
    public IVersion getVersionWith(long versionPosition) {
        return versionedSystem.values().stream().filter(v -> v.getVersionIndex() == versionPosition).findFirst().orElse(null);
    }

    /**
     * Returns the number of versions in this project.
     * @return the counting of the versions.
     */
    public long numberOfVersions(){
        return versionedSystem.size();
    }

    /**
     * Returns a copy of the sorted set of versions in this system.
     * @return a sorted set of versions.
     */
    public SortedSet<IVersion> versions(){
        return new TreeSet<>(versionedSystem.values());
    }

    /**
     * Returns the index in the order list of versions of this project.
     * This collection is automatically updated when the system's versions change.
     * @param version the version to return the position of.
     * @return the position of the given version in the ordered list of versions of this system.
     */
    @Override
    public Long getVersionIndex(String version){
        return versionedSystem.get(version).getVersionIndex();
    }

    /**
     * Returns a sorted map where keys are versions of the system and values are triples
     * where the first element is the directory, or jar file, corresponding to the graphml file, saved as the second
     * element, and also to corresponding system graph, saved as third element.
     * @return a sorted map as described above.
     */
    public SortedMap<String, IVersion> getVersionedSystem() {
        return versionedSystem;
    }

    @Override
    public void setVersionedSystem(SortedMap<String, IVersion> versionedSystem) {
        this.versionedSystem = versionedSystem;
    }

    /**
     * Initializes the version positions.
     */
    protected void initVersionPositions(){
        long counter = 1;
        for (var version : getVersionedSystem().values()){
            version.setVersionIndex(counter++);
        }
    }

    /**
     * Returns the type of the project under analysis. Namely, the programming language used.
     * @return the programming language of the analysed project.
     */
    public Type getProjectType() {
        return projectType;
    }

    /**
     * Adds the given directory of graphML files to the current versioned system.
     * If directory does not exist, this method will fill the current versioned systems
     * with the paths to the ghost graphMl files. In that case, the paths will have
     * the following format: graphMLDir/name/version/name-version.graphml.
     * @param graphMLDir the directory where to read graph files from, or where they should be written.
     * @throws IOException
     */
    public void addGraphMLfiles(String graphMLDir) throws IOException{
        File dir = new File(graphMLDir);

        var graphMlFiles = getGraphMls(dir.toPath());
        graphMlFiles.forEach(f -> {
            var version = addVersion(f);
            version.setGraphMLPath(f);
        });

        initVersionPositions();
    }

    /**
     * Helper method that retrieves all GraphML files from a given directory and returns
     * them as a list of Path.
     * @param dir the directory containing the GraphMLs
     * @return list of GraphML files.
     * @throws IOException
     */
    protected List<Path> getGraphMls(Path dir) throws IOException{
        try(var list = Files.list(dir)){
            return list.filter(f -> Files.isRegularFile(f) && f.getFileName().toString().matches(".*\\.graphml")).collect(Collectors.toList());
        }
    }

    /**
     * Helper method that adds a file to the versions of the system.
     * @param f the file to add.
     */
    protected IVersion addVersion(Path f){
        IVersion version = versionInitializer.apply(f);
        version = versionedSystem.getOrDefault(version.getVersionString(), version);
        versionedSystem.putIfAbsent(version.getVersionString(), version);
        return version;
    }

    /**
     * Defines the project type under analysis (programming language).
     * A project type instantiates the version instance object based
     * on the type of project.
     */
    public enum Type {
        C("C", Pattern.compile("^.*\\.([ch])$"), CSourceCodeRetriever::new),
        CPP("C++", Pattern.compile("^.*\\.((cpp)|[ch])$"), CppSourceCodeRetriever::new),
        JAVA("Java", Pattern.compile("^.*\\.jar$"), JavaSourceCodeRetriever::new);

        private String typeName;
        private Pattern sourcesFileExt;
        private Function<Path, SourceCodeRetriever> sourceCodeRetrieverSupplier;

        Type(String typeName, Pattern sourcesFileExt, Function<Path, SourceCodeRetriever> sourceCodeRetrieverSupplier){
            this.typeName = typeName;
            this.sourcesFileExt = sourcesFileExt;
            this.sourceCodeRetrieverSupplier = sourceCodeRetrieverSupplier;
        }

        /**
         * The name of the project type.
         * @return the project type.
         */
        public String getTypeName() {
            return typeName;
        }

        /**
         * Returns the correct implementation of the source code retrieved depending on the language
         * of this project.
         * @return A new instance of a retriever.
         */
        public SourceCodeRetriever getSourceCodeRetrieverInstance(Path p){
            return sourceCodeRetrieverSupplier.apply(p);
        }

        /**
         * Returns the regular expression pattern matching the files for this project type.
         * @return a compiled Regex pattern.
         */
        public boolean sourcesMatch(Path p){
            return sourcesFileExt.matcher(p.toString()).matches();
        }
    }
}
