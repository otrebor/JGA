package dataStructures;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Locale;
import java.util.Random;
import java.util.Scanner;
import java.util.Set;
import java.util.logging.Logger;

import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleWeightedGraph;
import org.jocl.Pointer;

import PerformanceEvaluation.SimpleTimer;

/**
 * 
 * @author Roberto Belli
 * 
 *         This class represents a Graph (generated with jgrapht) with simpler
 *         data structures usable by the GPU
 * 
 */
public class GraphCL implements java.io.Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 225117718245815917L;
	private final static Logger logger = Logger
			.getLogger(Logger.GLOBAL_LOGGER_NAME);
	// NodeCL
	private int graphNodesNumber;
	private int edgesNumber;
	// each node is represented by a struct of 2 integer. I will represent every
	// node combining 2 arrays
	private int graphNodesStarting[];
	private int graphNodesNofEdges[];
	// Edges representation
	private int graphEdges[];
	private int graphEdgesWeights[];
	// ds util
	private int source;

	private GraphCL(int degree, int nEdges) {
		setGraphNodesNumber(degree);
		setGraphNodesStarting(new int[degree]);
		setGraphNodesNofEdges(new int[degree]);
		setEdgesNumber(nEdges * 2);
		setGraphEdges(new int[getEdgesNumber()]);
		setGraphEdgesWeights(new int[getEdgesNumber()]);
		this.source = 0;
	}

	public GraphCL(SimpleWeightedGraph<Object,DefaultWeightedEdge> graph, Object source) {
		// TODO: use generics, if you can
		setGraphNodesNumber(graph.vertexSet().size());
		setGraphNodesStarting(new int[getGraphNodesNumber()]);
		setGraphNodesNofEdges(new int[getGraphNodesNumber()]);
		this.source = -1;

		Set<Object> vertices = graph.vertexSet();
		Integer ind = 0;
		// Creating Vertex Ds
		// fill up data structures in 2 rounds : the fist one add the number of
		// edges of each nodes and init the boolean emulating arrays with false value (0)
		Object vrts[] = new Object[getGraphNodesNumber()];
		for (Object vertex : vertices) {
			ind = (Integer) vertex;
			// we must mantain a copy of vertices refs in an array because we
			// can't control the order
			// in wich vertex are examined. We will use that ds for edges repr
			vrts[ind] = vertex;
			getGraphNodesNofEdges()[ind] = graph.edgesOf(vertex).size();
			// set up the source
			if ((source==null && this.source==-1) || vertex.equals(source)) {
				this.setSource(ind);
			}
		}
		// in this phase we calculate the value of "starting" propriety that
		// represent the starting
		// position of the edges related to a vertex in the array representing
		// the set of edges
		int count = 0;
		for (int i = 0; i < getGraphNodesNumber(); i++) {
			getGraphNodesStarting()[i] = count;
			count += getGraphNodesNofEdges()[i];
		}

		// init edges
		setEdgesNumber(graph.edgeSet().size() * 2); // we must consider directed
													// edges (redundant
													// representation)
		setGraphEdges(new int[getEdgesNumber()]);
		this.setGraphEdgesWeights(new int[getEdgesNumber()]);

		int k = 0;
		// for each vertex
		for (int i = 0; i < this.getGraphNodesNumber(); i++) {
			// get the edges set of the vertex (incoming and outcoming)
			Set<DefaultWeightedEdge> edset = graph.edgesOf(vrts[i]);
			// iterate on the set
			Iterator<DefaultWeightedEdge> it = edset.iterator();
			while (it.hasNext()) {
				DefaultWeightedEdge ed = it.next();
				// we must distinguish between incoming and outcoming
				// add the destination of the edge in the array
				if (vrts[i].equals(graph.getEdgeTarget(ed))) {
					getGraphEdgesWeights()[k] = 1;
					getGraphEdges()[k++] = (Integer) graph.getEdgeSource(ed);
				} else {
					getGraphEdgesWeights()[k] = 1;
					getGraphEdges()[k++] = (Integer) graph.getEdgeTarget(ed);
				}
			}
		}

	}

	public Pointer getGraphEdgesPtr() {
		return Pointer.to(getGraphEdges());
	}

	public Pointer getGraphEdgesWeightsPtr() {
		return Pointer.to(getGraphEdgesWeights());
	}

	public Pointer getGraphNodesNofEdgesPtr() {
		return Pointer.to(getGraphNodesNofEdges());
	}

	public Pointer getGraphNodesStartingPtr() {
		return Pointer.to(getGraphNodesStarting());
	}

	public String toString() {
		StringBuffer x = new StringBuffer();
		for (int i = 0; i < this.getGraphNodesNumber(); i++) {
			x.append("Vertex ").append(i)
					.append(this.source == i ? " (source) " : "")
					.append(" is connected to : [");
			for (int k = this.getGraphNodesStarting()[i]; k < this
					.getGraphNodesStarting()[i]
					+ this.getGraphNodesNofEdges()[i]; k++) {
				x.append("(").append(this.getGraphEdges()[k])
						.append(" weight: ").append(this.graphEdgesWeights[k])
						.append(") ");
			}
			x.append("]\n");
		}
		return x.toString();
	}

	public int getGraphNodesNumber() {
		return graphNodesNumber;
	}

	private void setGraphNodesNumber(int graphNodesNumber) {
		this.graphNodesNumber = graphNodesNumber;
	}

	public int getEdgesNumber() {
		return edgesNumber;
	}

	private void setEdgesNumber(int edgesNumber) {
		this.edgesNumber = edgesNumber;
	}

	public int getSource() {
		return source;
	}

	private void setSource(int source) {
		this.source = source;
	}

	protected int[] getGraphNodesStarting() {
		return graphNodesStarting;
	}

	protected void setGraphNodesStarting(int graphNodesStarting[]) {
		this.graphNodesStarting = graphNodesStarting;
	}

	protected int[] getGraphNodesNofEdges() {
		return graphNodesNofEdges;
	}

	protected void setGraphNodesNofEdges(int graphNodesNofEdges[]) {
		this.graphNodesNofEdges = graphNodesNofEdges;
	}

	protected int[] getGraphEdges() {
		return graphEdges;
	}

	protected void setGraphEdges(int graphEdges[]) {
		this.graphEdges = graphEdges;
	}

	public void serializeGraph(File graphFile) {
		logger.info("Serialization -->");
		try {
			FileOutputStream fileOut = new FileOutputStream(graphFile);
			ObjectOutputStream out = new ObjectOutputStream(fileOut);
			out.writeObject(this);
			out.close();
			fileOut.close();
			logger.info("done! graph saved in " + graphFile.getPath());
		} catch (IOException i) {
			i.printStackTrace();
		}
	}

	public static GraphCL deserializeGraph(File graphFile) {
		GraphCL graph = null;
		SimpleTimer si = new SimpleTimer();
		si.start();
		logger.info("Deserialization -->");
		try {
			FileInputStream fileIn = new FileInputStream(graphFile);
			ObjectInputStream in = new ObjectInputStream(fileIn);
			graph = (GraphCL) in.readObject();
			in.close();
			fileIn.close();
		} catch (IOException i) {
			i.printStackTrace();
			System.exit(1);
		} catch (ClassNotFoundException c) {
			logger.severe("Graph class not found");
			c.printStackTrace();
			System.exit(1);

		}
		si.stop();
		logger.info("done! file " + graphFile.getPath()+ " loaded in "+si.getTimeCountAndReset() + " ms");
		String gnn = NumberFormat
				.getNumberInstance(Locale.US)
				.format(graph.graphNodesNumber);
		String enn = NumberFormat
				.getNumberInstance(Locale.US)
				.format(graph.edgesNumber/2);
		logger.fine("Graph -> #nodes:" + gnn + " #edges:"
				+ enn);
		return graph;
	}

	@SuppressWarnings("unchecked")
	public static GraphCL deserializeGraphDIMACS(File graphFile) throws IOException {
		GraphCL target;
		//this array will contain an arraylist of edges in each cell (init with AVG og edges)
		Object edgeLists[];  
		int numOfNodes = 0;
		int numOfEdges = 0;
		SimpleTimer si = new SimpleTimer();
		si.start();
		logger.info("Deserialization DIMACS-->");
		BufferedReader br = new BufferedReader(new FileReader(graphFile));
		String line;
		//nodes
		while ((line = br.readLine()) != null) {
			Scanner s = new Scanner(line);
			if (s.next().equals("p")) {
				s.next();
				numOfNodes = s.nextInt();
				// edges are in both direction but we consider undirected ones
				numOfEdges = s.nextInt() / 2;
				break;
			}
		}
		edgeLists = new Object[numOfNodes];
		for(int i=0 ; i< numOfNodes ; i++){
			edgeLists[i] = new ArrayList<Edge>();
		}
		//edges
		while ((line = br.readLine()) != null) {
			// process the line.
			Scanner s = new Scanner(line);
			if (s.next().equals("a")) {
				int from = s.nextInt() -1 ;
				int to = s.nextInt() -1 ;
				int weight = s.nextInt();
				((ArrayList<Edge>)edgeLists[from]).add(new Edge(to,weight));	
			}
		}
		br.close();
		target = new GraphCL(numOfNodes, numOfEdges);
		
		logger.fine("Conversion CL ---> ");
		int edgeCount = 0;
		for (int node = 0; node < numOfNodes; node++) {
			ArrayList<Edge> nodeEdges = (ArrayList<Edge>) edgeLists[node];
			target.graphNodesStarting[node] = edgeCount;
			target.graphNodesNofEdges[node] = nodeEdges.size();

			for (int ed = 0; ed < target.graphNodesNofEdges[node]; ed++) {
				target.graphEdges[edgeCount] = nodeEdges.get(ed).target;
				target.graphEdgesWeights[edgeCount++] = nodeEdges.get(ed).weight;
			}
		}
		
		si.stop();
		logger.info("done! file " + graphFile.getPath()+ " loaded in "+si.getTimeCountAndReset() + " ms");
		String gnn = NumberFormat
				.getNumberInstance(Locale.US)
				.format(target.graphNodesNumber);
		String enn = NumberFormat
				.getNumberInstance(Locale.US)
				.format((edgeCount +1));
		logger.fine("Graph -> #nodes:" + gnn + " #edges:"
				+ enn);

		return target;
	}

	public static GraphCL uniformWeightGraphCLGenerator(int degree, int maxNEdgesPerNode) {
		GraphCL target = GraphCLGenerator(degree, true, 1, maxNEdgesPerNode);
		return target;
	}
	
	@SuppressWarnings("unchecked")
	private static GraphCL GraphCLGenerator(int degree, boolean uniformWeigth , int maxWeight , int maxOutEdgesPerNode) {
		// probs[0] = 1 => connected graph
		double probs[];
		Random rgen = new Random();
		if(maxOutEdgesPerNode<1){
			probs= new double[3];
			//TODO : check distribution differences between this case and maxOutEdgesPerNode=3;
			probs[0] =1; probs[1]=0.6; probs[2]=0.4; 
			} else {
		 probs = new double[maxOutEdgesPerNode];
		 probs[0] =1;
		 for(int i=1; i<maxOutEdgesPerNode ;i++){
			 //setting random probability for edges generation
			 //i-th edge is generated with probability probs[i] (true only if #nodes >> #edges per node )
			 probs[i]= rgen.nextDouble();
		 }
			}
		SimpleTimer st = new SimpleTimer();
		logger.fine("Nodes generation ---> ");
		st.start();
		/*the maximum number of edges is probs.lengt for each vertex*/
		Object edgesList[] = new Object[degree];
		GraphCL target = null;

		Integer counter = 0;
		while (counter < degree) {
			edgesList[counter] = new ArrayList<Edge>(probs.length);
			counter++;
		}

		st.stop();
		logger.fine("done in " + st.getTimeCountAndReset() + " msec!");

		logger.fine("Edges generation ---> ");
		st.start();
		// the first node is skipped
		int nInsertedNodes = 1;
		int edges = 0;
		while (nInsertedNodes < degree) {
			/* inserting new node in graph */

			/* generating edges */
			for (int i = 0; i < probs.length; i++) {

				if (rgen.nextDouble() < probs[i]) {
					//random connection between 0 inclusive e nInsertedNodes Exclusive
					int connection = rgen.nextInt(nInsertedNodes);
					
					ArrayList<Edge> edgesListOfNewNode = (ArrayList<Edge>) edgesList[nInsertedNodes];
					ArrayList<Edge> edgesListOfCandidateNode = (ArrayList<Edge>) edgesList[connection];

					//check if the edge with selected node already exists
					boolean found = false;
					for(int edIndex = 0 ; edIndex < edgesListOfNewNode.size() ; edIndex++){
						if(edgesListOfNewNode.get(edIndex).target==connection)
							found=true;
					}
					//adding new edge
					int weight = 1;
					if(!uniformWeigth)
						weight = rgen.nextInt(maxWeight);
					if (!found) {
						edgesListOfNewNode.add(new Edge(connection, weight));
						edgesListOfCandidateNode.add(new Edge(nInsertedNodes, weight));
						edges++;
					}
				}
			}
			nInsertedNodes++;
		}
		st.stop();
		logger.fine("done in " + st.getTimeCountAndReset() + " msec!");

		/* pre-graph = post-graph */
		logger.fine("Conversion CL ---> ");
		st.start();
		target = new GraphCL(degree, edges);
		int edgeCount = 0;
		for (int node = 0; node < degree; node++) {
			ArrayList<Edge> nodeEdges = (ArrayList<Edge>) edgesList[node];
			target.graphNodesStarting[node] = edgeCount;
			target.graphNodesNofEdges[node] = nodeEdges.size();

			for (int ed = 0; ed < nodeEdges.size(); ed++) {
				target.graphEdges[edgeCount] = nodeEdges.get(ed).target;
				target.graphEdgesWeights[edgeCount++] = nodeEdges.get(ed).weight;
			}
		}

		st.stop();
		logger.fine("done in " + st.getTimeCountAndReset() + " msec!");
		String gnn = NumberFormat
				.getNumberInstance(Locale.US)
				.format(target.graphNodesNumber);
		String enn = NumberFormat
				.getNumberInstance(Locale.US)
				.format(target.edgesNumber/2); //undirected edges
		logger.fine("Graph -> #nodes:" + gnn + " #edges:"
				+ enn +" (undirected)");


		return target;
	}

	public static GraphCL randomWeightGraphCLGenerator(int degree, int maxWeight, int maxNEdgesPerNode) {
		GraphCL target = GraphCLGenerator(degree, false, maxWeight, maxNEdgesPerNode);
		
		return target;

	}

	public SimpleWeightedGraph<Object, DefaultWeightedEdge> ConvertGraph() {
		SimpleWeightedGraph<Object, DefaultWeightedEdge> target = new SimpleWeightedGraph<Object, DefaultWeightedEdge>(
				DefaultWeightedEdge.class);
		Object insertedNodesArr[] = new Object[this.graphNodesNumber];
		Integer counter = 0;
		while (counter < this.graphNodesNumber) {
			// creating nodes
			insertedNodesArr[counter] = (Object) counter;
			target.addVertex(insertedNodesArr[counter++]);
		}
		// for each node I will add every edge and set the weight
		for (int i = 0; i < this.graphNodesNumber; i++) {
			int start = this.graphNodesStarting[i];
			int stop = start + this.graphNodesNofEdges[i];
			for (int k = start; k < stop; k++) {
				// selecting the target of the edge
				Object tgNode = insertedNodesArr[this.graphEdges[k]];
				if (target.getEdge(insertedNodesArr[i], tgNode) == null) {
					DefaultWeightedEdge ed = target.addEdge(
							insertedNodesArr[i], tgNode);
					target.setEdgeWeight(ed, this.graphEdgesWeights[k]);
				}
			}
		}
		return target;

	}

	public int[] getGraphEdgesWeightsArr() {
		return graphEdgesWeights.clone();
	}

	private int[] getGraphEdgesWeights() {
		return graphEdgesWeights;
	}

	private void setGraphEdgesWeights(int graphEdgesWeights[]) {
		this.graphEdgesWeights = graphEdgesWeights;
	}
	
	

}
