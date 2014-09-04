package dataStructures;

import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

import PerformanceEvaluation.SimpleTimer;

public class MstCPUPrism {
	private final static Logger logger = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
	// representation of the graph in a compressed ds
	private GraphCL gr;
	SimpleTimer ti = new SimpleTimer("Timer");
	// result of the MST
	private int edgeSet = 0, nodeN = 0;
	private double prismCost = 0;
	// service data structures
	private boolean done = false;
	
	public MstCPUPrism(GraphCL graphCL){
		this.gr = graphCL;
	}
	
	public void runMST() {
		//i will use the same compressed representation in order to not have any duplicate of the same ds
		//in memory
		ti.start();
		prismCost = primsMST();
		ti.stop();
		
		logger.info("MST_CPU : "+ti.getTimeCountAndReset()+" ms (PrismMST alg) ");
		done = true;
	}
	
	private double primsMST(){
		int grNodes_start[] = gr.getGraphNodesStarting();
		int grNodes_noe[] = gr.getGraphNodesNofEdges();
		int grEdges[] = gr.getGraphEdges();
		int grEdges_w[] = gr.getGraphEdgesWeightsArr();
		double prismCost = 0;
		//Initialize: Vnew = {x}, where x is an arbitrary node (starting point) from V, Enew = {}
		Set<Integer> vNew = new HashSet<Integer>();
		Set<Edge> eNew = new HashSet<Edge>();
		vNew.add(0);
		
		while(vNew.size()!=gr.getGraphNodesNumber()){
			//Choose an edge {u, v} with minimal weight such that u is in Vnew and v is not (if there are multiple edges with the same weight, any of them may be picked)
			int minTarget = Integer.MAX_VALUE ;
			int minWeight = Integer.MAX_VALUE ;
			for(Integer node : vNew){
				int start = grNodes_start[node];
				int stop = start + grNodes_noe[node];
				//for each edge o
				for(int ed = start ; ed < stop ; ed++){
					if((!vNew.contains(grEdges[ed])) && grEdges_w[ed]<minWeight){
						minTarget = grEdges[ed] ;
						minWeight = grEdges_w[ed] ;
					}
				}
			}
			//Add v to Vnew, and {u, v} to Enew
			vNew.add(minTarget);
			eNew.add(new Edge(minTarget,minWeight));
		}
		for(Edge edge : eNew){
			prismCost += edge.weight;
		}
		this.edgeSet = eNew.size();
		this.nodeN=vNew.size();
		return prismCost;
		
	}
	
	/**
	 * @returns an array containing the following infos { No of edges in MST , no of nodes , minimum cost }
	 * null if the calculation has to be done
	 * */
	public double[] getResult() {
		double result[] = {0,0,0};
		if (done){
			result[0]=this.edgeSet;
			result[1]=this.nodeN;
			result[2]=this.prismCost;
			return result;
		}
			
		return null;
	}
}
