/* 
 * Copyright 2015 ScAi, CSD, UCLA.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package edu.ucla.cs.scai.swims.wordnetws;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import org.apache.log4j.Logger;
import org.openrdf.model.URI;
import slib.graph.algo.utils.GAction;
import slib.graph.algo.utils.GActionType;
import slib.graph.algo.utils.GraphActionExecutor;
import slib.graph.algo.validator.dag.ValidatorDAG;
import slib.graph.io.conf.GDataConf;
import slib.graph.io.loader.wordnet.GraphLoader_Wordnet;
import slib.graph.io.util.GFormat;
import slib.graph.model.graph.G;
import slib.graph.model.impl.graph.memory.GraphMemory;
import slib.graph.model.impl.repo.URIFactoryMemory;
import slib.graph.model.repo.URIFactory;
import slib.indexer.wordnet.IndexerWordNetBasic;
import slib.sml.sm.core.engine.SM_Engine;
import slib.sml.sm.core.metrics.ic.utils.IC_Conf_Topo;
import slib.sml.sm.core.metrics.ic.utils.ICconf;
import slib.sml.sm.core.utils.SMConstants;
import slib.sml.sm.core.utils.SMconf;
import slib.utils.ex.SLIB_Ex_Critic;

/**
 *
 * @author Giuseppe M. Mazzeo <mazzeo@cs.ucla.edu>
 */
public class WordnetService {

    public static String dataLocation;

    static WordnetService instance;

    private final IndexerWordNetBasic indexWordnetNoun;
    private final SMconf measureConf;
    private final SM_Engine engine;

    static {
        try {
            Properties p=new Properties();
            //a file config.properties must be put in the project resource folder, containing the key wornet_path associated with the actual path
            p.load(WordnetService.class.getResourceAsStream("/config.properties"));
            dataLocation=p.getProperty("wordnet_path");
            instance = new WordnetService();
        } catch (Exception e) {
            Logger.getLogger(WordnetService.class).error("Error while accessing the WordNet folder");
            e.printStackTrace();
            Logger.getLogger(WordnetService.class).error("Any request will fail");
        }
    }

    private WordnetService() throws Exception {

        // We create the graph
        URIFactory factory = URIFactoryMemory.getSingleton();
        URI guri = factory.getURI("http://graph/wordnet/");
        G wordnet = new GraphMemory(guri);

        // We load the data into the graph
        GraphLoader_Wordnet loader = new GraphLoader_Wordnet();

        GDataConf dataNoun = new GDataConf(GFormat.WORDNET_DATA, dataLocation + "data.noun");
        GDataConf dataVerb = new GDataConf(GFormat.WORDNET_DATA, dataLocation + "data.verb");
        GDataConf dataAdj = new GDataConf(GFormat.WORDNET_DATA, dataLocation + "data.adj");
        GDataConf dataAdv = new GDataConf(GFormat.WORDNET_DATA, dataLocation + "data.adv");

        loader.populate(dataNoun, wordnet);
        loader.populate(dataVerb, wordnet);
        loader.populate(dataAdj, wordnet);
        loader.populate(dataAdv, wordnet);

        // We root the graph which has been loaded (this is optional but may be required to compare synset which do not share common ancestors).
        GAction addRoot = new GAction(GActionType.REROOTING);
        GraphActionExecutor.applyAction(addRoot, wordnet);

        // This is optional. It just shows which are the synsets which are not subsumed
        ValidatorDAG validatorDAG = new ValidatorDAG();
        Set<URI> roots = validatorDAG.getTaxonomicRoots(wordnet);
        System.out.println("Roots: " + roots);

        // We create an index to map the nouns to the vertices of the graph
        // We only build an index for the nouns in this example
        String data_noun = dataLocation + "index.noun";

        indexWordnetNoun = new IndexerWordNetBasic(factory, wordnet, data_noun);

        // uncomment if you want to show the index, i.e. nouns and associated URIs (identifiers)
        for (Map.Entry<String, Set<URI>> entry : indexWordnetNoun.getIndex().entrySet()) {
            System.out.println(entry.getKey() + "\t" + entry.getValue());
        }

        // We configure a pairwise semantic pairSimilarity measure, 
        // i.e., a measure which will be used to assess the pairSimilarity 
        // of two nouns regarding their associated vertices in WordNet
        ICconf iconf = new IC_Conf_Topo(SMConstants.FLAG_ICI_SECO_2004);
        measureConf = new SMconf(SMConstants.FLAG_SIM_PAIRWISE_DAG_NODE_LIN_1998);
        measureConf.setICconf(iconf);

        // We define the engine used to compute the score of the configured measure
        // several preprocessing will be made prior to the first computation, e.g. to compute the Information Content (IC)
        // of the vertices. This may take some few secondes
        engine = new SM_Engine(wordnet);

    }

    public static WordnetService getInstance() {
        return instance;
    }

    public synchronized double pairSimilarity(String w1, String w2) throws SLIB_Ex_Critic {

        //Retrive identifiers
        w1=w1.toLowerCase();
        w2=w2.toLowerCase();
        if (w1.equals(w2)) {
            return 1;
        }
        Set<URI> id1 = indexWordnetNoun.get(w1);
        Set<URI> id2 = indexWordnetNoun.get(w2);

        double max = 0;
        if (id1 != null && id2 != null) {
            for (URI uri1 : id1) {
                for (URI uri2 : id2) {
                    double sim = engine.compare(measureConf, uri1, uri2);
                    if (sim > max) {
                        max = sim;
                    }
                }
            }
        }

        return max*0.5;
    }

    public synchronized double jaccardSimilarity(String[] s1, String[] s2) throws SLIB_Ex_Critic {
        ArrayList<Cell> cells = new ArrayList<>();
        
        for (int i = 0; i < s1.length; i++) {
            for (int j = 0; j < s2.length; j++) {
                cells.add(new Cell(i, j, pairSimilarity(s1[i], s2[j])));
            }
        }

        Collections.sort(cells);

        double[] sim1 = new double[s1.length];
        double[] sim2 = new double[s2.length];
        
        HashSet<Integer> set1=new HashSet<>();
        HashSet<Integer> set2=new HashSet<>();
        
        for (Cell c : cells) {
            if (!set1.contains(c.i) && !set2.contains(c.j)) {
                set1.add(c.i);
                set2.add(c.j);
                sim1[c.i]=c.value;
                sim2[c.j]=c.value;
            }
        }
        
        double num = 0;

        for (int i = 0; i < s1.length; i++) {
            num += sim1[i]/2;
        }
        
        for (int j = 0; j < s2.length; j++) {
            num += sim2[j]/2;
        }        

        return num / (s1.length+s2.length-num);
    }

    class Cell implements Comparable<Cell> {

        int i;
        int j;
        double value;

        public Cell(int i, int j, double value) {
            this.i = i;
            this.j = j;
            this.value = value;
        }

        @Override
        public int compareTo(Cell o) {
            return Double.compare(-value, -o.value);
        }
    }

}
