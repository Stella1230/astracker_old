package org.rug.simpletests;

import org.rug.data.project.Project;

import java.io.File;
import java.io.IOException;

public class TestData {
    public final static Project antlr = new Project("antlr", Project.Type.JAVA);
    private final static String antlrProjectDir = "./test-data/input/antlr";
    private final static String antlrGraphMLDir = "./test-data/output/arcanOutput/antlr";

    public final static Project pure = new Project("pure", Project.Type.CPP);
    private final static String pureGraphMLDir = "./test-data/output/arcanOutput/pure";

    public final static String trackASOutputDir = "./test-data/output/trackASOutput/";

    static {
        try {
            antlr.addJars(antlrProjectDir);
            antlr.addGraphMLs(antlrGraphMLDir);
        } catch (IOException e){
            System.err.println("Cannot load antlr project for tests execution. Are you sure the paths are correct?");
            System.err.println(String.format("Project dir: %s", antlrProjectDir));
            System.err.println(String.format("GraphML dir: %s", antlrGraphMLDir));
        }

        try {
            pure.addGraphMLs(pureGraphMLDir);
        } catch (IOException e){
            System.err.println("Cannot load antlr project for tests execution. Are you sure the paths are correct?");
            System.err.println(String.format("GraphML dir: %s", pureGraphMLDir));
        }

        String[] projects = new String[]{ antlr.getName(), pure.getName() };
        for(var p : projects){
            File f = new File(trackASOutputDir + p);
            f.mkdirs();
        }
    }
}
