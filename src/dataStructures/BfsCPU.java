package dataStructures;

import java.util.LinkedList;
import java.util.Queue;
import java.util.logging.Logger;

import PerformanceEvaluation.SimpleTimer;

public class BfsCPU {
	private final static Logger logger = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
	// representation of the graph in a compressed ds
	private GraphCL graph;
	private int num_of_nodes;
	// result of the BFS
	private int cost[];
	// service data structures
	private boolean graphVisited[];
	private boolean done = false;
	
	public BfsCPU(GraphCL graph){
		this.graph = graph;
		this.num_of_nodes = graph.getGraphNodesNumber();
		this.cost=new int[num_of_nodes];
		this.graphVisited= new boolean[num_of_nodes];
		//init arrays
		for(int i=0; i < num_of_nodes;i++){
			cost[i]=-1;
			graphVisited[i]=false;
		}
		//setup source
		cost[graph.getSource()] = 0;
		graphVisited[graph.getSource()] = true;
		
	}
	
	public void runBFS() {
		//i will use the same compressed representation in order to not have any duplicate of the same ds
		//in memory
		SimpleTimer ti = new SimpleTimer("Timer");
		ti.start();
		int nodes_starting[] = graph.getGraphNodesStarting();
		int nodes_noe[] = graph.getGraphNodesNofEdges();
		int edges[] = graph.getGraphEdges();
		Queue<Integer> q = new LinkedList<Integer>();
		q.offer(graph.getSource());
		while(!q.isEmpty()){
			Integer t = q.poll();
			
			for(int i=nodes_starting[t]; i<nodes_starting[t]+nodes_noe[t];i++){
				if(!this.graphVisited[edges[i]]){
					q.offer(edges[i]);
					this.graphVisited[edges[i]]=true;
					this.cost[edges[i]]=this.cost[t]+1;
				}
			}
		   
		}
		ti.stop();
		logger.info("BFS_CPU: "+ ti.getTimeCount() +" ms " );
		done = true;
	}
	
	public int[] getCostResult() {
		if (done)
			return cost;
		return null;
	}
}
