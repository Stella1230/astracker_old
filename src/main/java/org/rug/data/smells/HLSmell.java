package org.rug.data.smells;

import org.apache.tinkerpop.gremlin.process.traversal.P;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.rug.data.EdgeLabel;

import java.util.HashSet;
import java.util.Set;

public class HLSmell extends SingleElementSmell {

    private Set<Vertex> inDep;
    private Set<Vertex> outDep;

    /**
     * Initializes this smell instance starting from the smell node
     *
     * @param smell the smell that characterizes this instance.
     */
    public HLSmell(Vertex smell) {
        super(smell, Type.HL);
        this.inDep = smell.graph().traversal().V(smell).out(EdgeLabel.HLIN.toString()).toSet();
        this.outDep = smell.graph().traversal().V(smell).out(EdgeLabel.HLOUT.toString()).toSet();
    }

    /**
     * Sets the affected elements of the smell from a <code>VertexLabel.SMELL</code> vertex.
     *
     * @param smell the starting node
     */
    @Override
    public void setAffectedElements(Vertex smell) {
        // Select the appropriate label based on the smell label
        EdgeLabel label = EdgeLabel.HLAFFECTEDCLASS.toString().toLowerCase().contains(getLevel().toString().toLowerCase()) ? EdgeLabel.HLAFFECTEDCLASS : EdgeLabel.HLAFFECTEDPACK;
        setAffectedElements(new HashSet<>());
        getAffectedElements().add(smell.graph().traversal().V(smell).out(label.toString()).next());
    }

    public Set<Vertex> getInDep() {
        return inDep;
    }

    public Set<Vertex> getOutDep() {
        return outDep;
    }
}