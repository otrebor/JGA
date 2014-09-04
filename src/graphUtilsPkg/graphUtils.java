package graphUtilsPkg;

import java.util.*;
import java.util.logging.Logger;
import java.io.*;

import org.jgrapht.*;
import org.jgrapht.alg.ConnectivityInspector;
import org.jgrapht.generate.*;
import org.jgrapht.graph.*;
import org.jgrapht.traverse.*;

import dataStructures.GraphCL;

import PerformanceEvaluation.SimpleTimer;


public class graphUtils {
	private static final Logger logger = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);

	public static Graph<Object, DefaultEdge> scaleFreeGraphGenerator(int size) {

		Graph<Object, DefaultEdge> scaleFreeGraph;

		// check Number of vertices
		if (size < 1) {
			throw new IllegalArgumentException("Invalid graph size");
		}
		// Create the graph object; it is null at this point
		scaleFreeGraph = new SimpleGraph<Object, DefaultEdge>(DefaultEdge.class);

		// Create the CompleteGraphGenerator object
		ScaleFreeGraphGenerator<Object, DefaultEdge> scaleFreeGenerator = new ScaleFreeGraphGenerator<Object, DefaultEdge>(
				size);

		// Create the VertexFactory so the generator can create vertices
		VertexFactory<Object> vFactory = new ClassBasedVertexFactory<Object>(
				Object.class);

		// Use the CompleteGraphGenerator object to make completeGraph a
		// complete graph with [size] number of vertices
		scaleFreeGenerator.generateGraph(scaleFreeGraph, vFactory, null);

		// Now, replace all the vertices with sequential numbers so we can ID
		// them
		Set<Object> vertices = new HashSet<Object>();
		vertices.addAll(scaleFreeGraph.vertexSet());

		Integer counter = 0;
		for (Object vertex : vertices) {
			replaceVertex(scaleFreeGraph, vertex, (Object) counter++);
		}

		return scaleFreeGraph;
	}

	public static Graph<Object, DefaultEdge> simpleGraphGenerator2(int degree) {
		// probs[0] = 1 => connected graph
		double[] probs = { 1, 0.4, 0.2 };

		Graph<Object, DefaultEdge> target = new SimpleGraph<Object, DefaultEdge>(
				DefaultEdge.class);
		Object insertedNodesArr[] = new Object[degree];
		// ArrayList<Object> insertedNodes = new ArrayList<Object>();

		Integer counter = 0;
		Object first = (Object) counter++;
		target.addVertex(first);

		// insertedNodes.add(first);
		insertedNodesArr[0] = first;
		int nInsertedNodes = 1;
		while (target.vertexSet().size() != degree) {
			/* inserting new node in graph */
			Object newNode = (Object) counter++;
			target.addVertex(newNode);
			/* generating edges */
			for (int i = 0; i < probs.length; i++) {
				if (Math.random() < probs[i]) {
					int connection = (int) (Math.random() * nInsertedNodes);
					// Object connected = insertedNodes.get(connection);
					Object connected = insertedNodesArr[connection];

					if (!target.containsEdge(newNode, connected))
						target.addEdge(newNode, connected);
				}
			}
			/* pre-graph = post-graph */
			insertedNodesArr[nInsertedNodes++] = newNode;
			// insertedNodes.add(newNode);
		}

		return target;
	}

	public static Graph<Object, DefaultEdge> simpleGraphGenerator(int degree) {
		// probs[0] = 1 => connected graph
		double[] probs = { 1, 0.4, 0.2 };
		SimpleTimer st = new SimpleTimer();
		logger.fine("Nodes generation ---> ");
		st.start();

		Graph<Object, DefaultEdge> target = new SimpleGraph<Object, DefaultEdge>(
				DefaultEdge.class);
		Object insertedNodesArr[] = new Object[degree];
		int firstEdge[] = new int[degree];
		int secondEdge[] = new int[degree];
		int thirdEdge[] = new int[degree];
		Integer counter = 0;
		while (counter < degree) {
			firstEdge[counter] = secondEdge[counter] = thirdEdge[counter] = -1;
			insertedNodesArr[counter] = (Object) counter;
			target.addVertex(insertedNodesArr[counter++]);
		}
		st.stop();
		logger.fine("done in "+st.getTimeCountAndReset()+" msec!");

		logger.fine("Edges generation ---> ");
		st.start();
		int nInsertedNodes = 1;
		while (nInsertedNodes < degree) {
			/* inserting new node in graph */
			Object newNode = insertedNodesArr[nInsertedNodes];

			/* generating edges */
			for (int i = 0; i < probs.length; i++) {
				if (Math.random() < probs[i]) {
					int connection = (int) (Math.random() * nInsertedNodes);
					Object connected = insertedNodesArr[connection];
					if(!(firstEdge[nInsertedNodes]==connection||secondEdge[nInsertedNodes]==connection||thirdEdge[nInsertedNodes]==connection))
						
						target.addEdge(newNode, connected);
					
						if(firstEdge[nInsertedNodes]==-1){
							firstEdge[nInsertedNodes]=connection;
						}else if(secondEdge[nInsertedNodes]==-1){
							secondEdge[nInsertedNodes]=connection;
						}else {
							thirdEdge[nInsertedNodes]=connection;
						}
						
				}
			}
			/* pre-graph = post-graph */
			insertedNodesArr[nInsertedNodes++] = newNode;
			if((nInsertedNodes%10000)==0)
				logger.finest("inserted node "+ nInsertedNodes);
		}
		st.stop();
		logger.fine("done in "+st.getTimeCountAndReset()+" msec!");

		return target;
	}
	

	public static void printGraph(Graph<Object, DefaultEdge> graph) {
		// Print out the graph to be sure it's really complete
		Iterator<Object> iter = new DepthFirstIterator<Object, DefaultEdge>(
				graph);
		Object vertex;
		while (iter.hasNext()) {
			vertex = iter.next();
			logger.info("Vertex " + vertex.toString()
					+ " is connected to: " + graph.edgesOf(vertex).toString());
		}
	}

	private static boolean replaceVertex(
			Graph<Object, DefaultEdge> completeGraph, Object oldVertex,
			Object newVertex) {
		if ((oldVertex == null) || (newVertex == null)) {
			return false;
		}
		Set<DefaultEdge> relatedEdges = completeGraph.edgesOf(oldVertex);
		completeGraph.addVertex(newVertex);

		Object sourceVertex;
		Object targetVertex;
		for (DefaultEdge e : relatedEdges) {
			sourceVertex = completeGraph.getEdgeSource(e);
			targetVertex = completeGraph.getEdgeTarget(e);
			if (sourceVertex.equals(oldVertex)
					&& targetVertex.equals(oldVertex)) {
				completeGraph.addEdge(newVertex, newVertex);
			} else {
				if (sourceVertex.equals(oldVertex)) {
					completeGraph.addEdge(newVertex, targetVertex);
				} else {
					completeGraph.addEdge(sourceVertex, newVertex);
				}
			}
		}
		completeGraph.removeVertex(oldVertex);
		return true;
	}

	public static void serializeGraph(Graph<Object, DefaultEdge> graph,
			File graphFile) {
		try {
			FileOutputStream fileOut = new FileOutputStream(graphFile);
			ObjectOutputStream out = new ObjectOutputStream(fileOut);
			out.writeObject(graph);
			out.close();
			fileOut.close();
			System.out.println("Serialized graph is saved in "
					+ graphFile.getPath());
		} catch (IOException i) {
			i.printStackTrace();
		}
	}

	@SuppressWarnings("unchecked")
	public static Graph<Object, DefaultEdge> deserializeGraph(File graphFile) {
		Graph<Object, DefaultEdge> graph = null;
		try {
			FileInputStream fileIn = new FileInputStream(graphFile);
			ObjectInputStream in = new ObjectInputStream(fileIn);
			graph = (Graph<Object, DefaultEdge>) in.readObject();
			in.close();
			fileIn.close();
		} catch (IOException i) {
			i.printStackTrace();
			System.exit(1);
		} catch (ClassNotFoundException c) {
			System.out.println("Graph class not found");
			c.printStackTrace();
			System.exit(1);

		}
		System.out.println("Deserialized graph contained in the file "
				+ graphFile.getPath());
		return graph;
	}
	
	public static GraphCL correctGraphGeneration(GraphCL graph){
		Random rgen = new Random();
		logger.info("\nTEST: \"Checking Graph proprieties \" \n");
		SimpleWeightedGraph<Object, DefaultWeightedEdge> swg = graph.ConvertGraph();
		//check double unidiretional archs
		Set<DefaultWeightedEdge> edset = swg.edgeSet();
		Iterator<DefaultWeightedEdge> ited = edset.iterator();
		while(ited.hasNext()){
			DefaultWeightedEdge ed = ited.next();
			Object src = swg.getEdgeSource(ed);
			Object dst = swg.getEdgeTarget(ed);
			double wed = swg.getEdgeWeight(ed);
			//i have to check this because i need to simulate an undirected graph doubling
			//the directed edges
			if(swg.getEdge(dst, src)==null){
				DefaultWeightedEdge added = swg.addEdge(dst, src);
				swg.setEdgeWeight(added, wed);
				logger.fine("Fixed directed arch "+(Integer)dst+","+(Integer)src);
			}
		}
		//connectivity check
		ConnectivityInspector<Object, DefaultWeightedEdge> ci = new ConnectivityInspector<Object, DefaultWeightedEdge>(swg); 
		if(!ci.isGraphConnected()){
			//not a connected graph
			List<Set<Object>> list = ci.connectedSets();
			Object unconnIn[] = new Object[list.size()];
			Object unconnOut[] = new Object[list.size()];
			for(int i=0 ; i < list.size() ; i++){
				Set<Object> sl = list.get(i);
				int rv = rgen.nextInt(sl.size());
				//choosing a random node inside each subgraph 
				Object[] sarr = sl.toArray();
				//incoming
				unconnIn[i]= sarr[rv];
				rv = rgen.nextInt(sl.size());
				//outcoming
				unconnOut[i]= sarr[rv];
			}
			for(int i=1 ; i < list.size() ; i++){
				//adding new edges
				Object src = unconnOut[i-1];
				Object dst = unconnIn[i];
				swg.addEdge(src, dst);
				swg.addEdge(dst, src);
				logger.fine("CORRECTION: New edge -> "+(Integer)src+","+(Integer)dst);
			}
			ci = new ConnectivityInspector<Object, DefaultWeightedEdge>(swg); 
			if(!ci.isGraphConnected()){
				System.out.println("Error, graph not connected");
				System.exit(1);
			}
		}
		logger.severe("Graph check completed successifully (it is connected and undirected)");
		return new GraphCL(swg, null);
		}
	

}