package dataStructures;

import java.util.logging.Logger;

import org.jgrapht.alg.KruskalMinimumSpanningTree;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleWeightedGraph;

import PerformanceEvaluation.SimpleTimer;

public class MstCPUKruskal {
	private final static Logger logger = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
	// representation of the graph in a compressed ds
	private SimpleWeightedGraph<Object, DefaultWeightedEdge> graph;

	private KruskalMinimumSpanningTree<Object, DefaultWeightedEdge> mst ;
	// result of the BFS
	SimpleTimer ti = new SimpleTimer("Timer");
	// service data structures
	private boolean done = false;
	
	public MstCPUKruskal(GraphCL graphCL){
		logger.fine("Starting Graph conversion -->");
		ti.start();
		graph = graphCL.ConvertGraph();
		ti.stop();
		logger.fine("Graph conversion done!");
		logger.fine("MstCPU : graph conversion done in "+ti.getTimeCountAndReset()+" msec");
	}
	
	public void runMST() {
		//i will use the same compressed representation in order to not have any duplicate of the same ds
		//in memory
		ti.start();
		mst = new KruskalMinimumSpanningTree<Object, DefaultWeightedEdge>(graph);
		
		ti.stop();
		logger.info("MST_CPU : "+ti.getTimeCountAndReset()+" ms (KruskalMST alg)");
		done = true;
	}
	
	/**
	 * @returns an array containing the following infos { No of edges in MST , no of nodes , minimum cost }
	 * null if the calculation has to be done
	 * */
	public double[] getResult() {
		double result[] = {0,0,0};
		if (done){
			result[0]=mst.getEdgeSet().size();
			result[1]=graph.vertexSet().size();
			result[2]= mst.getSpanningTreeCost();
			return result;
		}
			
		return null;
	}
}
