import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.ref.WeakReference;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.StreamHandler;

import joclUtilsPkg.*;
import graphUtilsPkg.*;

import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultEdge;
import org.jocl.CL;

import PerformanceEvaluation.SimpleTimer;
import dataStructures.BfsCL;
import dataStructures.BfsCPU;
import dataStructures.GraphCL;
import dataStructures.MstCL;
import dataStructures.MstCPUKruskal;
import dataStructures.MstCPUPrism;
import dataStructures.MyFormatter;

import joptsimple.OptionParser;
import joptsimple.OptionSpec;
import joptsimple.OptionSet;

@SuppressWarnings("unused")
public class JoclGraphAlgorithms {

	private static boolean deserializeGraph = false;
	private static boolean deserializeDIMACSGraph = false;
	private static File deserializationFile = new File("./in.ser");
	private static boolean generateGraph = true;
	private static int generateGraphSize = 10000;
	private static int genMaxEdgesPerNode = 5;
	private static int maxEdgeWeight = 100;
	private static boolean serializeGeneratedGraph = false;
	private static File serializationFile = new File("./in.ser");
	private static boolean printGraph = false;
	private static boolean selectDevice = false;
	private static boolean testGen = false;
	private static int testGenSize = 10000;

	private static Logger LOGGER;
	static private ConsoleHandler stderrH;
	static private StreamHandler stdoutH;
	static private MyFormatter formatter;
	private static boolean BfsTest = false;
	private static boolean MstTest = false;
	private static boolean checkGraph = false;

	/**
	 * @param args
	 */
	public static void main(String[] args) {

		setupLogger();
		LOGGER.setLevel(Level.INFO);
		argParse(args);

		GraphCL grcl = null;
		// graph generation
		if (generateGraph) {
			if (deserializeGraph) {
				// if deserialization required, no generation of graphs needed
				// gr = graphUtils.deserializeGraph(deserializationFile);
				grcl = GraphCL.deserializeGraph(deserializationFile);
			} else if (deserializeDIMACSGraph) {
				try {
					grcl = GraphCL.deserializeGraphDIMACS(deserializationFile);
				} catch (IOException e) {
					System.out
							.println("Error while deserializing DIMACS graph");
					e.printStackTrace();
					System.exit(1);
				}
			} else {
				LOGGER.fine("Start graph generation");
				grcl = GraphCL.randomWeightGraphCLGenerator(generateGraphSize,
						maxEdgeWeight, genMaxEdgesPerNode);
				LOGGER.fine("Graph generation done");
			}
		}
		if (checkGraph) {
			WeakReference<GraphCL> ref = new WeakReference<GraphCL>(grcl);
			grcl = graphUtils.correctGraphGeneration(grcl);
		    while(ref.get() != null) {
		       System.gc();
		     }
		}
		if (serializeGeneratedGraph) {
			// we prefer to serialize the graph in the compact DS
			grcl.serializeGraph(serializationFile);
		}
		if (printGraph) {
			// Print out the graph to be sure it's really complete
			LOGGER.info(grcl.toString());
		}
		
		String mxBeanName = ManagementFactory.getRuntimeMXBean().getName();
		String pid = mxBeanName.substring(0, mxBeanName.indexOf("@"));
		LOGGER.info("Process pid=" + pid);
		// enabling timed logger flush
		TimedFlush.startAutoFlush();
		DeviceRecord deviceCL = null;
		if(BfsTest || MstTest){
		// Enable exceptions and subsequently omit error checks in this sample
		CL.setExceptionsEnabled(true);
		// Select the device, create a context and a related command queue
		deviceCL = JoclDeviceUtils.selectDevice(!selectDevice);
		}
		if(testGen)
			testGraphGeneration(testGenSize);
		
		if(BfsTest)
			testBFS(deviceCL, grcl);
		
		if(MstTest)
			testMST(deviceCL, grcl);

		System.exit(0);

	}

	private static void testBFS(DeviceRecord deviceCL, GraphCL grcl) {
		LOGGER.severe("\nTEST: \"BFS\"");
		LOGGER.severe("In the BFS test we consider each edge weight equal to 1");
		String gnn = NumberFormat.getNumberInstance(Locale.US)
				.format(grcl.getGraphNodesNumber());
		String enn = NumberFormat
				.getNumberInstance(Locale.US)
				.format(grcl.getEdgesNumber()/2);
		LOGGER.info("BFS Graph -> #nodes:" + gnn + " #edges:"
				+ enn );
		// creating instance of BFS
		BfsCL bfs = new BfsCL(deviceCL, grcl);
		// run BFS
		bfs.runBFS();
		// CPU instance
		BfsCPU bfsCPU = new BfsCPU(grcl);
		// run BFS
		bfsCPU.runBFS();
		// retrieve results
		LOGGER.severe("Result BFS: ");
		/*
		LOGGER.fine("CL  Array: " +
		 java.util.Arrays.toString(bfs.getCostResult()));
		LOGGER.fine("CPU Array: " +
		 java.util.Arrays.toString(bfsCPU.getCostResult()));
		 */
		// compare results
		if (java.util.Arrays
				.equals(bfs.getCostResult(), bfsCPU.getCostResult())) {
			LOGGER.severe("BFS: well done!");
		} else {
			LOGGER.severe("BFS: Error!");
		}
	}

	private static void testGraphGeneration(int generateGraphSize){
	SimpleTimer st = new SimpleTimer();
	LOGGER.info("\nTEST: \"Graph Generation\" \n");
	
	LOGGER.info("ScaleFreeGraphGenerator -> ");
	st.start();
		Graph<Object, DefaultEdge>	gr1 = graphUtils.scaleFreeGraphGenerator(generateGraphSize);
	st.stop();
	String gnn = NumberFormat.getNumberInstance(Locale.US)
			.format(gr1.vertexSet().size());
	String enn = NumberFormat
			.getNumberInstance(Locale.US)
			.format(gr1.edgeSet().size());
	LOGGER.info("Graph -> #nodes:" + gnn + " #edges:"
			+ enn +" generated in "+st.getTimeCountAndReset() +" ms");
	
	LOGGER.info("SimpleGraphGenerator -> ");
	st.start();
		Graph<Object, DefaultEdge>	gr2 = graphUtils.simpleGraphGenerator(generateGraphSize);
		st.stop();
		 gnn = NumberFormat.getNumberInstance(Locale.US)
				.format(gr2.vertexSet().size());
		enn = NumberFormat
				.getNumberInstance(Locale.US)
				.format(gr2.edgeSet().size() );
		LOGGER.info("Graph -> #nodes:" + gnn + " #edges:"
				+ enn +" generated in "+st.getTimeCountAndReset() +" ms");
		
		LOGGER.info("RandomWeightGraphCLGenerator (#EPN "+genMaxEdgesPerNode+") -> ");
		st.start();
		GraphCL	gr3 = GraphCL.randomWeightGraphCLGenerator(generateGraphSize,
				maxEdgeWeight, genMaxEdgesPerNode);
		st.stop();
		 gnn = NumberFormat.getNumberInstance(Locale.US)
					.format(gr3.getGraphNodesNumber());
		 // division by two is necessary because we have 2 unidirectional edges for representing one undirected
			enn = NumberFormat
					.getNumberInstance(Locale.US)
					.format(gr3.getEdgesNumber() / 2);
			LOGGER.info("Graph -> #nodes:" + gnn + " #edges:"
					+ enn +" generated in "+st.getTimeCountAndReset() +" ms");
	}
	
	
	
	private static void testMST(DeviceRecord deviceCL, GraphCL grcl) {
		LOGGER.severe("\nTEST: \"MST\"");
		String gnn = NumberFormat.getNumberInstance(Locale.US)
				.format(grcl.getGraphNodesNumber());
		String enn = NumberFormat
				.getNumberInstance(Locale.US)
				.format(grcl.getEdgesNumber()/2);
		LOGGER.info("MST Graph -> #nodes:" + gnn + " #edges:"
				+ enn );
		// creating instance of BFS
		// CPU instances
		// CL instance
		MstCL mst = new MstCL(deviceCL, grcl);
		// run BFS
		mst.runMST();
		// memory efficient but really slow
		/*
		MstCPUPrism mstCPUP = new MstCPUPrism(grcl);
	    mstCPUP.runMST();
		LOGGER.fine("CPU Array Prism: "
				+ java.util.Arrays.toString(mstCPUP.getResult()));
		 */
		
		// run BFS
		MstCPUKruskal mstCPUK = new MstCPUKruskal(grcl);
		mstCPUK.runMST();

		// retrieve results
		LOGGER.severe("Result MST: ");
		LOGGER.fine("CL  Array: "
				+ java.util.Arrays.toString(mst.getResult()));
		LOGGER.fine("CPU Array Kruskal: "
				+ java.util.Arrays.toString(mstCPUK.getResult()));
		if (java.util.Arrays.equals(mstCPUK.getResult(), mst.getResult())) {
			LOGGER.severe("MST: well done!");
		} else {
			LOGGER.severe("MST: Error!");
		}
	}

	private static void argParse(String[] args) {

		String detailInfo = new String("INFO");
		OptionParser parser = new OptionParser();

		OptionSpec<Integer> genGraphOpt = parser
				.accepts("generateGraph", "Generates a scale-free graph with specified number of verticles.")
				.withRequiredArg()
				.ofType(Integer.class)
				.describedAs(
						"number of verticles")
				.defaultsTo(generateGraphSize);
		
		OptionSpec<Integer> genGraphNumOfEdgesOpt = parser
				.accepts("maxEdgesPerNode","Specifies the maximum number of outcoming edges of each node (used only if combined with generateGraph).")
				.withRequiredArg()
				.ofType(Integer.class)
				.describedAs(
						"maximum number of outcoming edges for each node ")
				.defaultsTo(genMaxEdgesPerNode);

		OptionSpec<File> serializeOpt = parser.accepts("serializeGraph","Save the used graph in the specified path.")
				.withRequiredArg().ofType(File.class)
				.describedAs("path where save the Graph");

		OptionSpec<File> deserializeOpt = parser
				.accepts("deserializeGraph","Load a graph from the file in the specified path.")
				.withRequiredArg()
				.ofType(File.class)
				.describedAs("serialized graph's file path");

		OptionSpec<File> deserializeDIMACSOpt = parser
				.accepts("deserializeDIMACSGraph","Load a DIMACS 9 graph from the file in the specified path.")
				.withRequiredArg()
				.ofType(File.class)
				.describedAs(
						"DIMACS 9 graph's file path");

		OptionSpec<String> detailInfoOpt = parser
				.accepts("detail", "Indicates the detail of the info printed on screen.")
				.withRequiredArg()
				.ofType(String.class).defaultsTo(detailInfo)
				.describedAs(
						"admissible parameters are: SEVERE,WARNING,INFO,FINE,FINEST");

		OptionSpec<Void> selectDeviceOpt = parser
				.accepts(
						"s",
						"Interactive selection of the computing device (if not used the first GPU device is used).");
		
		OptionSpec<Integer> testGenOpt = parser
				.accepts(
						"testGen",
						"Runs a performance test on the various kind of graph generator methods.").withRequiredArg()
						.ofType(Integer.class)
						.describedAs(
								"Specifies the number of verticles of the graph that the test has to generate.")
						.defaultsTo(testGenSize);;

						OptionSpec<Void> BfsOpt = parser.accepts("bfs",
								"Starts the bfs test.");
						
						OptionSpec<Void> MstOpt = parser.accepts("mst",
								"Starts the mst test.");
						
						OptionSpec<Void> checkOpt = parser.accepts("check",
								"Checks if the input graph is connected and well formed. In case it is not, it fixes the graph");
						
		OptionSpec<Void> printGraphOpt = parser.accepts("v",
				"Print the analyzed graph.");

		parser.acceptsAll(java.util.Arrays.asList("h", "?"), "show help");
		try {
			OptionSet options = parser.parse(args);
			if (options.has("?") || options.has("h")) {
				printHelp(parser);
			}
			if (options.has(deserializeOpt)) {
				deserializationFile = ((File) options.valueOf(deserializeOpt));
				deserializeGraph = true;
				if (!(deserializationFile.exists() && deserializationFile
						.isFile())) {

					System.out
							.println("There are no files in the specified location ( "
									+ deserializationFile.getAbsolutePath()
									+ " )");
					System.exit(1);
				}
			}
			if (options.has(deserializeDIMACSOpt)) {
				deserializationFile = ((File) options
						.valueOf(deserializeDIMACSOpt));
				deserializeDIMACSGraph = true;
				if (!(deserializationFile.exists() && deserializationFile
						.isFile())) {
					System.out
							.println("There are no files in the specified location ( "
									+ deserializationFile.getAbsolutePath()
									+ " )");
					System.exit(1);
				}
			}
			if (deserializeDIMACSGraph && deserializeGraph) {
				System.out
						.println("You can not deserialize two different graph at the same time");
				System.exit(1);
			}
			if (options.has(genGraphOpt)) {
				if (!deserializeGraph) {
					generateGraph = true;
					generateGraphSize = (Integer) options.valueOf(genGraphOpt);
				} else {
					System.out
							.println("generateGraph command ignored due to deserialization opt");
				}
			}
			if (options.has(genGraphNumOfEdgesOpt)) {
				genMaxEdgesPerNode = (Integer) options.valueOf(genGraphNumOfEdgesOpt);
			}
			
			if (options.has(serializeOpt)) {
				serializationFile = ((File) options.valueOf(serializeOpt));
				serializeGeneratedGraph = true;
			}
			if (options.has(selectDeviceOpt)) {
				selectDevice = true;
			}
			if (options.has(testGenOpt)) {
				testGen = true;
				testGenSize = (Integer) options.valueOf(testGenOpt);
			}
			if (options.has(printGraphOpt)) {
				printGraph = true;
			}
			if (options.has(BfsOpt)) {
				BfsTest = true;
			}
			if (options.has(MstOpt)) {
				MstTest = true;
			}
			if (options.has(checkOpt)){
				checkGraph  = true;
			}
			if (options.has(detailInfoOpt)) {
				detailInfo = ((String) options.valueOf(detailInfoOpt));
				if (detailInfo.equals("SEVERE")) {
					LOGGER.setLevel(Level.SEVERE);
				} else if (detailInfo.equals("WARNING")) {
					LOGGER.setLevel(Level.WARNING);
				} else if (detailInfo.equals("INFO")) {
					LOGGER.setLevel(Level.INFO);
				} else if (detailInfo.equals("FINE")) {
					LOGGER.setLevel(Level.FINE);
				} else if (detailInfo.equals("FINEST")) {
					LOGGER.setLevel(Level.FINEST);
				} else {
					System.out
							.println("For the detail option you have to choose between SEVERE,WARNING,INFO,FINE,FINEST");
					printHelp(parser);
				}
			}
		} catch (joptsimple.OptionException e) {
			System.out.println(e.toString() + "\n");
			printHelp(parser);

		}

	}

	private static void printHelp(OptionParser parser) {
		try {
			parser.printHelpOn(System.out);
		} catch (IOException e1) {
			e1.printStackTrace();
		} finally {
			System.exit(1);
		}
	}

	private static void setupLogger() {
		LOGGER = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
		LOGGER.setUseParentHandlers(false);
		try {
			// used to disable ancestors handlers
			LOGGER.setUseParentHandlers(false);

			// Create Formatter
			formatter = new MyFormatter();
			stderrH = new ConsoleHandler();
			stdoutH = new StreamHandler(System.out, formatter);
			// if not the handler filters messages under Level.INFO
			stderrH.setLevel(Level.ALL);
			stdoutH.setLevel(Level.ALL);
			stderrH.setFormatter(formatter);
			// stdoutH.setFormatter(formatter);

			// removes all the default handlers
			Handler[] handlers = LOGGER.getHandlers();
			for (Handler handler : handlers) {
				// handler.setFormatter(formatter);
				LOGGER.removeHandler(handler);
			}
			// add the ConsoleHandler with specified formatter
			LOGGER.addHandler(stderrH);
			// LOGGER.addHandler(stdoutH);
		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException("Problems with the setup of the logger");

		}
	}

	public static class TimedFlush extends Thread {

		public void run() {
			// LOGGER.finest("logger flush!");
			// stdoutH.flush();
			stderrH.close();
			try {
				Thread.sleep(2000);
			} catch (InterruptedException e) {

			}
		}

		public static void startAutoFlush() {
			(new TimedFlush()).start();
		}

	}

}
