package org.rug.tracker;

import org.apache.tinkerpop.gremlin.process.traversal.P;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversal;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.*;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph;
import org.rug.data.project.IVersion;
import org.rug.data.smells.ArchitecturalSmell;

import java.io.Serializable;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Tracks incrementally the architectural smells and saves them internally.
 */
public class ASmellTracker implements Serializable{

    public static final String NAME = "name";
    public static final String SMELL = "smell";
    public static final String VERSION = "version";
    public static final String VERSION_INDEX = "versionIndex";
    public static final String SMELL_OBJECT = "smellObject";
    public static final String LATEST_VERSION = "latestVersion";
    public static final String EVOLVED_FROM = "evolvedFrom";
    public static final String HEAD = "head";
    public static final String UNIQUE_SMELL_ID = "uniqueSmellID";
    public static final String REAPPEARED = "reappeared";
    public static final String STARTED_IN = "startedIn";
    public static final String END = "end";
    public static final String SIMILARITY = "similarity";
    public static final String CHARACTERISTIC = "characteristic";
    public static final String HAS_CHARACTERISTIC = "hasCharacteristic";
    public static final String COMPONENT = "component";
    public static final String AFFECTS = "affects";
    public static final String SMELL_TYPE = "smellType";
    public static final String AGE = "age";
    public static final String NA = "NA";
    public static final String FIRST_APPEARED = "firstAppeared";
    public static final String LAST_DETECTED = "lastDetected";
    public static final String FIRST_APPEARED_INDEX = "firstAppearedIndex";
    public static final String LAST_DETECTED_INDEX  = "lastDetectedIndex";
    public static final String SMELL_ID = "smellId";
    public static final String COMPONENT_TYPE = "componentType";
    public static final String TAIL = "tail";
    public static final String SMELL_STATUS = "status";
    public static final String COMPONENT_CHARACTERISTIC = "componentCharacteristic";
    public static final String LATEST_VERSION_INDEX = "latestVersionIndex";

    private transient Graph trackGraph;
    private transient Graph condensedGraph;
    private transient Vertex tail;
    private long uniqueSmellID;
    private ISimilarityLinker scorer;
    private DecimalFormat decimal;

    private boolean trackNonConsecutiveVersions;

    /**
     * Builds an instance of this tracker.
     * @param trackNonConsecutiveVersions whether to track a smell through non-consecutive versions.
     *                                    This adds the possibility to track reappearing smells.
     */
    public ASmellTracker(ISimilarityLinker scorer, boolean trackNonConsecutiveVersions){
        this.trackGraph = TinkerGraph.open();
        this.tail = trackGraph.traversal().addV(TAIL).next();
        this.condensedGraph = TinkerGraph.open();
        this.uniqueSmellID = 1L;
        this.trackNonConsecutiveVersions = trackNonConsecutiveVersions;
        this.scorer = scorer;
        this.decimal = new DecimalFormat("0.0#");
    }


    /**
     * Builds an instance of this tracker that does not tracks smells through non-consecutive versions.
     * A JaccardSimilarityLinker is used to select the single successor of the given smell.
     */
    public ASmellTracker(){
        this(new JaccardSimilarityLinker(), false);
    }

    /**
     * Computes the tracking algorithm on the given system and saves internally the results
     * @param smellsInVersion the architectural smells identified in version
     * @param version the version of the given system
     */
    public void track(List<ArchitecturalSmell> smellsInVersion, IVersion version){
        List<ArchitecturalSmell> nextVersionSmells = new ArrayList<>(smellsInVersion);

        GraphTraversalSource g1 = trackGraph.traversal();

        if (g1.V(tail).outE().hasNext()) {
            List<ArchitecturalSmell> currentVersionSmells;
            if (trackNonConsecutiveVersions) {
                currentVersionSmells = g1.V(tail).out().values(SMELL_OBJECT)
                        .toStream().map(o -> (ArchitecturalSmell) o).collect(Collectors.toList());
            }else {
                currentVersionSmells = g1.V(tail).out().has(VERSION, tail.value(LATEST_VERSION).toString()).values(SMELL_OBJECT)
                        .toStream().map(o -> (ArchitecturalSmell) o).collect(Collectors.toList());
            }

            Set<LinkScoreTriple> bestMatch = scorer.bestMatch(currentVersionSmells, nextVersionSmells);

            bestMatch.forEach(t -> {
                // If this fails it means that a successor has already been found, which should never happen!
                Vertex predecessor = g1.V(tail).out().has(SMELL_OBJECT, t.getA()).next();
                Vertex successor = g1.addV(SMELL)
                        .property(VERSION, version.getVersionString())
                        .property(VERSION_INDEX, version.getVersionIndex())
                        .property(SMELL_ID, t.getB().getId())
                        .property(SMELL_OBJECT, t.getB()).next();

                g1.V(tail).outE().where(otherV().is(predecessor)).drop().iterate();
                String eLabel = tail.value(LATEST_VERSION).equals(predecessor.value(VERSION)) ? EVOLVED_FROM : REAPPEARED;
                g1.addE(eLabel).property(SIMILARITY, decimal.format(t.getC())).from(successor).to(predecessor).next();
                g1.addE(LATEST_VERSION).from(tail).to(successor).next();
                currentVersionSmells.remove(t.getA());
                nextVersionSmells.remove(t.getB());
            });
            if (!trackNonConsecutiveVersions) {
                currentVersionSmells.forEach(this::endDynasty);
            }

        }
        nextVersionSmells.forEach(s -> addNewDynasty(s, version.getVersionString(), version.getVersionIndex()));
        tail.property(LATEST_VERSION, version.getVersionString());
        tail.property(LATEST_VERSION_INDEX, version.getVersionIndex());
        updateCondensedGraph();
        clearSmellObjects();
    }

    /**
     * Begins a new dynasty for the given AS at the given starting version
     * @param s the starter of the dynasty
     * @param startingVersion the version
     */
    private void addNewDynasty(ArchitecturalSmell s, String startingVersion, long startingVersionIndex) {
        GraphTraversalSource g = trackGraph.traversal();
        Vertex successor = g.addV(SMELL)
                .property(VERSION, startingVersion)
                .property(VERSION_INDEX, startingVersionIndex)
                .property(SMELL_ID, s.getId())
                .property(SMELL_OBJECT, s).next();
        Vertex head = g.addV(HEAD)
                .property(VERSION, startingVersion)
                .property(VERSION_INDEX, startingVersionIndex)
                .property(UNIQUE_SMELL_ID, uniqueSmellID++).next();
        g.addE(STARTED_IN).from(head).to(successor).next();
        g.addE(LATEST_VERSION).from(tail).to(successor).next();
    }

    /**
     * Concludes the dynasty of the given smell (last smell in the dynasty)
     * @param smell the smell
     */
    private void endDynasty(ArchitecturalSmell smell){
        GraphTraversalSource g = trackGraph.traversal();
        Vertex lastHeir = g.V().has(SMELL_OBJECT, smell).next();
        Vertex end = g.addV(END).property(VERSION, currentVersion())
                .property(VERSION_INDEX, currentVersionIndex())
                .next();
        g.V(tail).outE().where(otherV().is(lastHeir)).drop().iterate();
        g.addE(END).from(end).to(lastHeir).next();
    }

    /**
     * Get the scorer used to instantiate this instance.
     * @return The scorer used to link the smells between versions.
     */
    public ISimilarityLinker getScorer() {
        return scorer;
    }

    /**
     * Returns the latest version of update of this tracker.
     * @return a string representing the version or {@link #NA} if no current version is available.
     */
    public String currentVersion(){
        return tail.property(LATEST_VERSION).orElse(NA).toString();
    }

    /**
     * Returns the latest version of update of this tracker.
     * @return a string representing the version or {@link #NA} if no current version is available.
     */
    public String currentVersionIndex(){
        return tail.property(LATEST_VERSION_INDEX).orElse(NA).toString();
    }

    /**
     * Updates the condensed graph from the current state of trackGraph.
     */
    private void updateCondensedGraph(){
        GraphTraversalSource gt = trackGraph.traversal();
        GraphTraversalSource gs = condensedGraph.traversal();

        Set<Vertex> smellsInVersion = gt.V().hasLabel(END, TAIL).out().hasNot(SMELL_STATUS).toSet();

        for(var smellVertex : smellsInVersion){
            var head = gt.V(smellVertex)
                    .choose(out(),
                            repeat(out()).until(in(STARTED_IN)).in(STARTED_IN),
                            in(STARTED_IN))
                    .hasLabel(HEAD)
                    .tryNext().orElseThrow(); // this should never throw an exception
            var smellUID = head.value(UNIQUE_SMELL_ID);
            var condensedSmell = gs.V()
                    .hasLabel(SMELL)
                    .has(UNIQUE_SMELL_ID, smellUID)
                    .tryNext().orElseGet(() -> gs.addV(SMELL)
                            .property(UNIQUE_SMELL_ID, smellUID)
                            .property(FIRST_APPEARED, smellVertex.value(VERSION))
                            .property(FIRST_APPEARED_INDEX, smellVertex.value(VERSION_INDEX)).next());
            ArchitecturalSmell smellObject = smellVertex.value(SMELL_OBJECT);
            if (!condensedSmell.property(SMELL_TYPE).isPresent()){
                condensedSmell.property(SMELL_TYPE, smellObject.getType().toString());
            }
            Vertex characteristics = gs.addV(CHARACTERISTIC).next();
            smellObject.getCharacteristicsMap().forEach(characteristics::property);

            gs.addE(HAS_CHARACTERISTIC).from(condensedSmell).to(characteristics)
                    .property(VERSION, smellVertex.value(VERSION))
                    .property(VERSION_INDEX, smellVertex.value(VERSION_INDEX))
                    .property(SMELL_ID, smellObject.getId()).next();

            for(var affectedComp : smellObject.getAffectedElements()){
                var name = affectedComp.value(NAME);
                var component = gs.V()
                        .has(NAME, name).tryNext()
                        .orElseGet(() -> gs.addV(COMPONENT)
                                .property(NAME, name)
                                .property(COMPONENT_TYPE, smellObject.getLevel().toString()).next());
                gs.addE(AFFECTS).from(condensedSmell).to(component)
                        .property(VERSION, smellVertex.value(VERSION))
                        .property(VERSION_INDEX, smellVertex.value(VERSION_INDEX))
                        .next();
                var cce = gs.V(component).outE(HAS_CHARACTERISTIC)
                        .has(VERSION, smellVertex.value(VERSION).toString())
                        .tryNext();
                if (cce.isEmpty()){
                    final var componentCharacteristics = gs.addV(COMPONENT_CHARACTERISTIC).next();
                    affectedComp.keys().stream().filter(k -> !k.equals(NAME)).forEach(k->
                            componentCharacteristics.property(k, affectedComp.value(k))
                    );
                    gs.addE(HAS_CHARACTERISTIC).from(component).to(componentCharacteristics)
                            .property(VERSION, smellVertex.value(VERSION))
                            .property(VERSION_INDEX, smellVertex.value(VERSION_INDEX))
                            .next();
                }
            }
            long age = condensedSmell.<Long>property(AGE).orElse(0L);
            condensedSmell.property(AGE, ++age);
            condensedSmell.property(LAST_DETECTED, smellVertex.value(VERSION));
            condensedSmell.property(LAST_DETECTED_INDEX, smellVertex.value(VERSION_INDEX));
            smellVertex.property(SMELL_STATUS, "processed");
        }
    }

    /**
     * Drop smell objects that are not accessed anymore to save up memory.
     */
    private void clearSmellObjects() {
        trackGraph.traversal().V().where(not(in().hasLabel(TAIL))).has(SMELL_STATUS, "processed")
                .properties(SMELL_OBJECT).drop().iterate();
    }

    /**
     * Retrieves the condensed graph.
     * @return the graph representing the tracked smells including their characteristics and components affected
     * with their own characteristics.
     */
    public Graph getCondensedGraph(){
        return condensedGraph;
    }

    /**
     * Get the graph object used to perform the tracking.
     * @return the track graph.
     */
    public Graph getTrackGraph(){
        return trackGraph;
    }

    /**
     * Returns the number of smells linked in the current iteration.
     * @return the number of smells linked.
     */
    public long smellsLinked(){
        return this.getScorer().bestMatch().size();
    }

    /**
     * Closes the current trackgraph and returns the graph object. This operation removes the tail from the graph.
     * The result is that this trackgraph is no more usable.
     */
    public Graph getFinalizedTrackGraph(){
        GraphTraversalSource g = trackGraph.traversal();
        g.V(tail).out().forEachRemaining( v -> g.addE(END).from(g.addV(END).property(VERSION, tail.value(LATEST_VERSION)).next()).to(v).next());
        tail.remove();

         g.V().has(SMELL_OBJECT).forEachRemaining(vertex -> {
             ArchitecturalSmell as = vertex.value(SMELL_OBJECT);
             vertex.property(SMELL_TYPE, as.getType().toString());
             as.getCharacteristicsMap().forEach(vertex::property);
             Set<String> affectedElements = as.getAffectedElements().stream()
                     .map(v -> v.value(NAME).toString())
                     .collect(Collectors.toCollection(TreeSet::new));
             vertex.property("affectedElements", affectedElements.toString());
         });
        return trackGraph;
    }

    public void setTrackGraph(Graph trackGraph) {
        this.trackGraph = trackGraph;
    }

    public void setCondensedGraph(Graph condensedGraph) {
        this.condensedGraph = condensedGraph;
    }

    public void setTail(Vertex tail) {
        this.tail = tail;
    }
}
