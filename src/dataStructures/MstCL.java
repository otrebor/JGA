package dataStructures;

import static org.jocl.CL.CL_DEVICE_TYPE_CPU;
import static org.jocl.CL.CL_FALSE;
import static org.jocl.CL.CL_MEM_COPY_HOST_PTR;
import static org.jocl.CL.CL_MEM_READ_ONLY;
import static org.jocl.CL.CL_MEM_READ_WRITE;
import static org.jocl.CL.CL_TRUE;
import static org.jocl.CL.clCreateBuffer;
import static org.jocl.CL.clEnqueueNDRangeKernel;
import static org.jocl.CL.clEnqueueReadBuffer;
import static org.jocl.CL.clEnqueueWriteBuffer;
import static org.jocl.CL.clReleaseMemObject;
import static org.jocl.CL.clSetKernelArg;

import java.io.FileNotFoundException;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jocl.CL;
import org.jocl.Pointer;
import org.jocl.Sizeof;
import org.jocl.cl_command_queue;
import org.jocl.cl_context;
import org.jocl.cl_event;
import org.jocl.cl_kernel;
import org.jocl.cl_mem;

import PerformanceEvaluation.ExecutionStatisticsCL;
import PerformanceEvaluation.SimpleTimer;

import joclUtilsPkg.DeviceRecord;
import joclUtilsPkg.JoclDeviceUtils;
import joclUtilsPkg.JoclKernelUtils;
import joclUtilsPkg.KernelRecord;

public class MstCL {
	private static final Logger logger = Logger
			.getLogger(Logger.GLOBAL_LOGGER_NAME);
	private static boolean debug = false;
	private static final String kernelFilePath = "./kernels/kernelsMST.cl";
	// each invocation is related with one kernel related to a device
	private DeviceRecord dr;
	private KernelRecord kernel_FMEWPV, kernel_FMC2, kernel_AETGM, kernel_FD,
			kernel_PC1, kernel_PC2, kernel_DD1, kernel_DD2, kernel_ARE;
	private KernelRecord kernel_EPCNC, kernel_RCEFMST, kernel_UC, kernel_R;
	// representation of the graph in a compressed ds
	private GraphCL graph;
	private int num_of_nodes[];

	private int sameindex[];
	private int falseval[];
	private int trueval[];
	private int infinity[];
	private int zero[];
	private int maxid_maxdegree[];

	// CL variables
	private cl_mem memObjects[];
	private int done[] = { 0 };
	private int over[] = { 0 };
	private int removing[] = { 0 };
	private int MAX_WORK_ITEM_PER_WORK_GROUP[] = new int[1];
	// work-item dimensions
	private long global_work_size[];
	private long local_work_size[];
	private SimpleTimer ti = new SimpleTimer("TimerBfs");
	private boolean profilingEnabled = false;
	private int graph_MST_edges[];
	private double minimumCost = -1;
	private int mst_num_of_edges = -1;

	public MstCL(DeviceRecord dr, GraphCL graph) {
		this.dr = dr;
		this.graph = graph;
		this.num_of_nodes = new int[1];
		this.num_of_nodes[0] = graph.getGraphNodesNumber();
		// init service structures
		sameindex = new int[num_of_nodes[0]];
		falseval = new int[num_of_nodes[0]];
		trueval = new int[num_of_nodes[0]];
		infinity = new int[num_of_nodes[0]];
		zero = new int[num_of_nodes[0]];
		maxid_maxdegree = new int[num_of_nodes[0]];
		profilingEnabled = JoclDeviceUtils.isProfilingEnabled(dr);
		Level fine = Level.FINE;
		if(logger.getLevel().intValue() >= fine.intValue()){
			profilingEnabled=false;
		}
		this.MAX_WORK_ITEM_PER_WORK_GROUP[0] =(int) JoclDeviceUtils.getCL_DEVICE_MAX_WORK_ITEM_SIZES(dr
				.getDeviceID());
		// setting dimension of global and local work group

				/*
				 * global size is the total number of work items you want to run, and
				 * the local size is the size of each workgroup
				 */
				
				// leaving the size equal to null we delegate to the library to choose
				// an appropriate local work size
			    global_work_size = new long[]{ num_of_nodes[0] };
				local_work_size = null;
		
		// init service datastructures
		for (int i = 0; i < num_of_nodes[0]; i++) {
			sameindex[i] = i;
			falseval[i] = 0; // false
			trueval[i] = 1; // true
			infinity[i] = CL.CL_INT_MAX ;
			zero[i] = 0;
			maxid_maxdegree[i] = -1;

		}

		// init result array
		graph_MST_edges = new int[graph.getEdgesNumber()];

		// set up the source

		// move data structures to the device memory

		initCLDeviceMem();
		
		// kernel is related to BFS so is necessary to import BFK kernel here
		// load kernel
		kernel_FMEWPV = kernel_FMC2 = kernel_AETGM = kernel_FD = kernel_PC1 = kernel_PC2 = kernel_DD1 = kernel_DD2 = kernel_ARE = kernel_EPCNC = kernel_RCEFMST = kernel_UC = kernel_R = null;
		// enabling debugging symbols if execution planned on cpu
		// setting the value of INF macro used in various kernelsMST
		boolean cpu = ((JoclDeviceUtils.getCL_DEVICE_TYPE(dr)) & CL_DEVICE_TYPE_CPU) != 0;
		String precompilerOptions = (cpu&&debug ? "-g -D INF="+ Integer.MAX_VALUE : "-D INF="+ CL.CL_INT_MAX);
		logger.fine("Kernels compiled with options: " + precompilerOptions);
		try {
			// load kernel from file
			kernel_FMEWPV = JoclKernelUtils.createKernelFromFile(dr,
					kernelFilePath, "Kernel_Find_Min_Edge_Weight_Per_Vertex",
					precompilerOptions);
			kernel_FMC2 = JoclKernelUtils.createKernelFromFile(dr,
					kernelFilePath, "Kernel_Find_Min_C2", precompilerOptions);
			kernel_AETGM = JoclKernelUtils.createKernelFromFile(dr,
					kernelFilePath, "Kernel_Add_Edge_To_Global_Min",
					precompilerOptions);
			kernel_FD = JoclKernelUtils.createKernelFromFile(dr,
					kernelFilePath, "Kernel_Find_Degree", precompilerOptions);
			kernel_PC1 = JoclKernelUtils.createKernelFromFile(dr,
					kernelFilePath, "Kernel_Prop_Colors1", precompilerOptions);
			kernel_PC2 = JoclKernelUtils.createKernelFromFile(dr,
					kernelFilePath, "Kernel_Prop_Colors2", precompilerOptions);
			kernel_DD1 = JoclKernelUtils.createKernelFromFile(dr,
					kernelFilePath, "Kernel_Dec_Degree1", precompilerOptions);
			kernel_DD2 = JoclKernelUtils.createKernelFromFile(dr,
					kernelFilePath, "Kernel_Dec_Degree2", precompilerOptions);
			kernel_ARE = JoclKernelUtils.createKernelFromFile(dr,
					kernelFilePath, "Kernel_Add_Remaining_Edges",
					precompilerOptions);
			kernel_EPCNC = JoclKernelUtils.createKernelFromFile(dr,
					kernelFilePath, "Kernel_Edge_Per_Cycle_New_Color",
					precompilerOptions);
			kernel_RCEFMST = JoclKernelUtils.createKernelFromFile(dr,
					kernelFilePath, "Kernel_Remove_Cycle_Edge_From_MST",
					precompilerOptions);
			kernel_UC = JoclKernelUtils.createKernelFromFile(dr,
					kernelFilePath, "Kernel_Update_Colorindex",
					precompilerOptions);
			kernel_R = JoclKernelUtils.createKernelFromFile(dr, kernelFilePath,
					"Kernel_Reinitialize", precompilerOptions);

		} catch (FileNotFoundException e) {
			e.printStackTrace();
			System.exit(1);
		}
	}

	private void initCLDeviceMem() {
		cl_context context = dr.getContext();
		ti.start();
		
		// Allocate the memory objects for the input- and output data
		memObjects = new cl_mem[24];
		// init graph nodes arrays
		memObjects[MstCL_ID.GRAPH_NODES_NOE] = clCreateBuffer(context,
				CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR,
				Sizeof.cl_int * graph.getGraphNodesNumber(),
				graph.getGraphNodesNofEdgesPtr(), null);
		
		memObjects[MstCL_ID.GRAPH_NODES_STARTING] = clCreateBuffer(context,
				CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR,
				Sizeof.cl_int * graph.getGraphNodesNumber(),
				graph.getGraphNodesStartingPtr(), null);

		// init edges array
		memObjects[MstCL_ID.GRAPH_EDGES] = clCreateBuffer(context,
				CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR,
				Sizeof.cl_int * graph.getEdgesNumber(),
				graph.getGraphEdgesPtr(), null);
		// init weight array
		memObjects[MstCL_ID.GRAPH_EDGES_WEIGHT] = clCreateBuffer(context,
				CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR,
				Sizeof.cl_int * graph.getEdgesNumber(),
				graph.getGraphEdgesWeightsPtr(), null);

		// graph_MST_edges init result array
		memObjects[MstCL_ID.GRAPH_MST_EDGES] = clCreateBuffer(context,
				CL_MEM_READ_WRITE | CL_MEM_COPY_HOST_PTR,
				Sizeof.cl_int * graph.getEdgesNumber(),
				Pointer.to(this.graph_MST_edges), null);

		// init service arrays and variables ( device_variable_name, init value
		// , size)
		// d_global_colors, sameindex, sizeof(int)*no_of_nodes
		memObjects[MstCL_ID.GLOBAL_COLORS] = clCreateBuffer(context,
				CL_MEM_READ_WRITE | CL_MEM_COPY_HOST_PTR,
				Sizeof.cl_int * graph.getGraphNodesNumber(),
				Pointer.to(this.sameindex), null);

		// d_graph_colorindex, sameindex, sizeof(int)*no_of_nodes
		memObjects[MstCL_ID.GRAPH_COLORINDEX] = clCreateBuffer(context,
				CL_MEM_READ_WRITE | CL_MEM_COPY_HOST_PTR,
				Sizeof.cl_int * graph.getGraphNodesNumber(),
				Pointer.to(this.sameindex), null);

		// d_active_colors, falseval, sizeof(bool)*no_of_nodes
		memObjects[MstCL_ID.ACTIVE_COLORS] = clCreateBuffer(context,
				CL_MEM_READ_WRITE | CL_MEM_COPY_HOST_PTR,
				Sizeof.cl_int * graph.getGraphNodesNumber(),
				Pointer.to(this.falseval), null);

		// d_active_vertices, trueval, sizeof(int)*no_of_nodes
		memObjects[MstCL_ID.ACTIVE_VERTICLES] = clCreateBuffer(context,
				CL_MEM_READ_WRITE | CL_MEM_COPY_HOST_PTR,
				Sizeof.cl_int * graph.getGraphNodesNumber(),
				Pointer.to(this.trueval), null);

		// d_updating_global_colors, sameindex, sizeof(int)*no_of_nodes
		memObjects[MstCL_ID.UPDATING_GLOBAL_COLORS] = clCreateBuffer(context,
				CL_MEM_READ_WRITE | CL_MEM_COPY_HOST_PTR,
				Sizeof.cl_int * graph.getGraphNodesNumber(),
				Pointer.to(this.sameindex), null);

		// d_updating_degree, zero, sizeof(unsigned int)*no_of_nodes
		memObjects[MstCL_ID.UPDATING_DEGREE] = clCreateBuffer(context,
				CL_MEM_READ_WRITE | CL_MEM_COPY_HOST_PTR,
				Sizeof.cl_int * graph.getGraphNodesNumber(),
				Pointer.to(this.zero), null);

		// d_prev_colors, sameindex, sizeof(int)*no_of_nodes
		memObjects[MstCL_ID.PREV_COLORS] = clCreateBuffer(context,
				CL_MEM_READ_WRITE | CL_MEM_COPY_HOST_PTR,
				Sizeof.cl_int * graph.getGraphNodesNumber(),
				Pointer.to(this.sameindex), null);

		// d_prev_colorindex, sameindex, sizeof(int)*no_of_nodes
		memObjects[MstCL_ID.PREV_COLORINDEX] = clCreateBuffer(context,
				CL_MEM_READ_WRITE | CL_MEM_COPY_HOST_PTR,
				Sizeof.cl_int * graph.getGraphNodesNumber(),
				Pointer.to(this.sameindex), null);

		// d_cycle_edge, infinity, sizeof(int)*no_of_nodes
		memObjects[MstCL_ID.CYCLE_EDGE] = clCreateBuffer(context,
				CL_MEM_READ_WRITE | CL_MEM_COPY_HOST_PTR,
				Sizeof.cl_int * graph.getGraphNodesNumber(),
				Pointer.to(this.infinity), null);

		// d_min_edge_weight, infinity, sizeof(int)*no_of_nodes
		memObjects[MstCL_ID.MIN_EDGE_WEIGHT] = clCreateBuffer(context,
				CL_MEM_READ_WRITE | CL_MEM_COPY_HOST_PTR,
				Sizeof.cl_int * graph.getGraphNodesNumber(),
				Pointer.to(this.infinity), null);

		// d_min_edge_index, infinity, sizeof(int)*no_of_nodes
		memObjects[MstCL_ID.MIN_EDGE_INDEX] = clCreateBuffer(context,
				CL_MEM_READ_WRITE | CL_MEM_COPY_HOST_PTR,
				Sizeof.cl_int * graph.getGraphNodesNumber(),
				Pointer.to(this.infinity), null);

		// d_global_min_edge_weight, infinity, sizeof(int)*no_of_nodes
		memObjects[MstCL_ID.GLOBAL_MIN_EDGE_WEIGHT] = clCreateBuffer(context,
				CL_MEM_READ_WRITE | CL_MEM_COPY_HOST_PTR,
				Sizeof.cl_int * graph.getGraphNodesNumber(),
				Pointer.to(this.infinity), null);

		// d_global_min_edge_index, infinity, sizeof(int)*no_of_nodes
		memObjects[MstCL_ID.GLOBAL_MIN_EDGE_INDEX] = clCreateBuffer(context,
				CL_MEM_READ_WRITE | CL_MEM_COPY_HOST_PTR,
				Sizeof.cl_int * graph.getGraphNodesNumber(),
				Pointer.to(this.infinity), null);

		// d_global_min_c2, infinity, sizeof(int)*no_of_nodes
		memObjects[MstCL_ID.GLOBAL_MIN_C2] = clCreateBuffer(context,
				CL_MEM_READ_WRITE | CL_MEM_COPY_HOST_PTR,
				Sizeof.cl_int * graph.getGraphNodesNumber(),
				Pointer.to(this.infinity), null);

		// d_degree, zero, sizeof(unsigned int)*no_of_nodes,
		memObjects[MstCL_ID.DEGREE] = clCreateBuffer(context, CL_MEM_READ_WRITE
				| CL_MEM_COPY_HOST_PTR,
				Sizeof.cl_int * graph.getGraphNodesNumber(),
				Pointer.to(this.zero), null);

		// d_over, sizeof(int)));
		memObjects[MstCL_ID.OVER] = clCreateBuffer(context, CL_MEM_READ_WRITE
				| CL_MEM_COPY_HOST_PTR, Sizeof.cl_int, Pointer.to(this.over),
				null);

		// d_done, sizeof(int)));
		memObjects[MstCL_ID.DONE] = clCreateBuffer(context, CL_MEM_READ_WRITE
				| CL_MEM_COPY_HOST_PTR, Sizeof.cl_int, Pointer.to(this.done),
				null);

		// d_removing, sizeof(int)));
		memObjects[MstCL_ID.REMOVING] = clCreateBuffer(context, CL_MEM_READ_WRITE
				| CL_MEM_COPY_HOST_PTR, Sizeof.cl_int,
				Pointer.to(this.removing), null);
		// n of nodes
		memObjects[MstCL_ID.N_OF_NODES] = clCreateBuffer(context, CL_MEM_READ_ONLY
				| CL_MEM_COPY_HOST_PTR, Sizeof.cl_int,
				Pointer.to(this.num_of_nodes), null);
		
		ti.stop();
		double dsSize = ((17*(graph.getGraphNodesNumber()/1.0)*Sizeof.cl_int)+(Sizeof.cl_int * (graph.getEdgesNumber()/1.0)*3));
		String tBytes = NumberFormat.getNumberInstance(Locale.US).format(dsSize);
		logger.fine("Allocation of "+  tBytes +" Bytes on the device done in "+ti.getTimeCountAndReset()+" ms");
		
	}

	private void setKernelsArgs() {
		// Set the arguments for the kernel
		// Kernel_Find_Min_Edge_Weight_Per_Vertex
		/*
		 * (d_graph_nodes, d_graph_edges, d_graph_weights, d_graph_MST_edges,
		 * d_graph_colorindex, d_global_colors, d_active_colors,
		 * d_global_min_edge_weight,d_min_edge_weight, d_min_edge_index, d_over,
		 * d_active_vertices, no_of_nodes);
		 */
		int kernelArgs_FMEWPV[] = { MstCL_ID.GRAPH_NODES_NOE, 
				MstCL_ID.GRAPH_NODES_STARTING, 
				MstCL_ID.GRAPH_EDGES, 
				MstCL_ID.GRAPH_EDGES_WEIGHT, 
				MstCL_ID.GRAPH_MST_EDGES, 
				MstCL_ID.GRAPH_COLORINDEX, 
				MstCL_ID.GLOBAL_COLORS, 
				MstCL_ID.ACTIVE_COLORS,
				MstCL_ID.GLOBAL_MIN_EDGE_WEIGHT,
				MstCL_ID.MIN_EDGE_WEIGHT,
				MstCL_ID.MIN_EDGE_INDEX,
				MstCL_ID.OVER,
				MstCL_ID.ACTIVE_VERTICLES,
				MstCL_ID.N_OF_NODES };
		setKernelArgs(kernelArgs_FMEWPV,kernel_FMEWPV.getKernel());

		/*
		 * Set the arguments for the Kernel_Find_Min_C2 (d_graph_edges,
		 * d_global_min_c2, d_graph_colorindex, d_global_colors,
		 * d_active_colors, d_global_min_edge_weight, d_min_edge_weight,
		 * d_min_edge_index, no_of_nodes );
		 */
		int kernelArgs_FMC2[] = { MstCL_ID.GRAPH_EDGES, 
				MstCL_ID.GLOBAL_MIN_C2, 
				MstCL_ID.GRAPH_COLORINDEX, 
				MstCL_ID.GLOBAL_COLORS, 
				MstCL_ID.ACTIVE_COLORS, 
				MstCL_ID.GLOBAL_MIN_EDGE_WEIGHT, 
				MstCL_ID.MIN_EDGE_WEIGHT, 
				MstCL_ID.MIN_EDGE_INDEX, 
				MstCL_ID.N_OF_NODES };
		setKernelArgs(kernelArgs_FMC2,kernel_FMC2.getKernel());

		/*
		 * Kernel_Add_Edge_To_Global_Min (constant int * g_graph_edges,global
		 * int * g_global_min_c2,global int* g_graph_colorindex,global int*
		 * g_global_colors, global int* g_global_min_edge_index,global int*
		 * g_min_edge_weight,global int* g_min_edge_index,constant int*
		 * no_of_nodes)
		 */
		int kernelArgs_AETGM[] = { MstCL_ID.GRAPH_EDGES, 
				MstCL_ID.GLOBAL_MIN_C2, 
				MstCL_ID.GRAPH_COLORINDEX, 
				MstCL_ID.GLOBAL_COLORS, 
				MstCL_ID.GLOBAL_MIN_EDGE_INDEX, 
				MstCL_ID.MIN_EDGE_WEIGHT, 
				MstCL_ID.MIN_EDGE_INDEX,  
				MstCL_ID.N_OF_NODES };
		setKernelArgs(kernelArgs_AETGM,kernel_AETGM.getKernel());

		/*
		 * Kernel_Find_Degree (d_graph_edges, d_graph_colorindex,
		 * d_global_colors, d_global_min_edge_index, d_degree,
		 * d_updating_degree, d_prev_colors, d_prev_colorindex, no_of_nodes );
		 */
		int kernelArgs_FD[] = { MstCL_ID.GRAPH_EDGES, 
				MstCL_ID.GRAPH_COLORINDEX, 
				MstCL_ID.GLOBAL_COLORS, 
				MstCL_ID.GLOBAL_MIN_EDGE_INDEX,
				MstCL_ID.DEGREE,
				MstCL_ID.UPDATING_DEGREE, 
				MstCL_ID.PREV_COLORS, 
				MstCL_ID.PREV_COLORINDEX,  
				MstCL_ID.N_OF_NODES };
		setKernelArgs(kernelArgs_FD,kernel_FD.getKernel());

		/*
		 * Kernel_Prop_Colors1 (d_graph_edges, d_graph_colorindex,
		 * d_global_colors, d_global_min_edge_index,d_updating_global_colors,
		 * no_of_nodes );
		 */
		int kernelArgs_PC1[] = { MstCL_ID.GRAPH_EDGES, 
				MstCL_ID.GRAPH_COLORINDEX, 
				MstCL_ID.GLOBAL_COLORS, 
				MstCL_ID.GLOBAL_MIN_EDGE_INDEX,
				MstCL_ID.UPDATING_GLOBAL_COLORS, 
				MstCL_ID.N_OF_NODES };
		setKernelArgs(kernelArgs_PC1,kernel_PC1.getKernel());

		/*
		 * Kernel_Prop_Colors2 (d_global_colors, d_active_colors,
		 * d_updating_global_colors, d_done, no_of_nodes );
		 */
		int kernelArgs_PC2[] = { MstCL_ID.GLOBAL_COLORS, 
				MstCL_ID.ACTIVE_COLORS, 
				MstCL_ID.UPDATING_GLOBAL_COLORS, 
				MstCL_ID.DONE,
				MstCL_ID.N_OF_NODES };
		setKernelArgs(kernelArgs_PC2,kernel_PC2.getKernel());

		/*
		 * Kernel_Update_Colorindex (d_graph_colorindex, d_global_colors,
		 * no_of_nodes);
		 */
		int kernelArgs_UC[] = { 
				MstCL_ID.GRAPH_COLORINDEX, 
				MstCL_ID.GLOBAL_COLORS, 
				MstCL_ID.N_OF_NODES };
		setKernelArgs(kernelArgs_UC,kernel_UC.getKernel());

		/*
		 * Kernel_Dec_Degree1 (d_active_colors, d_degree,
		 * d_global_min_edge_index, d_graph_edges, d_prev_colorindex,
		 * d_prev_colors, d_updating_degree, d_graph_MST_edges,no_of_nodes);
		 */
		int kernelArgs_DD1[] = { 
				MstCL_ID.ACTIVE_COLORS, 
				MstCL_ID.DEGREE, 
				MstCL_ID.GLOBAL_MIN_EDGE_INDEX, 
				MstCL_ID.GRAPH_EDGES,
				MstCL_ID.PREV_COLORINDEX, 
				MstCL_ID.PREV_COLORS, 
				MstCL_ID.UPDATING_DEGREE,
				MstCL_ID.GRAPH_MST_EDGES, 
				MstCL_ID.N_OF_NODES };
		setKernelArgs(kernelArgs_DD1,kernel_DD1.getKernel());

		
		/*
		 * Kernel_Dec_Degree2 (d_degree, d_active_colors, d_updating_degree,
		 * d_removing, no_of_nodes);
		 */
		int kernelArgs_DD2[] = { 
				MstCL_ID.DEGREE, 
				MstCL_ID.ACTIVE_COLORS, 
				MstCL_ID.UPDATING_DEGREE,
				MstCL_ID.REMOVING, 
				MstCL_ID.N_OF_NODES };
		setKernelArgs(kernelArgs_DD2,kernel_DD2.getKernel());

		/*
		 * Kernel_Add_Remaining_Edges (d_global_min_edge_index,
		 * d_graph_MST_edges, no_of_nodes);
		 */
		int kernelArgs_ARE[] = { 
				MstCL_ID.GLOBAL_MIN_EDGE_INDEX, 
				MstCL_ID.GRAPH_MST_EDGES,  
				MstCL_ID.N_OF_NODES };
		setKernelArgs(kernelArgs_ARE,kernel_ARE.getKernel());

		/*
		 * Kernel_Edge_Per_Cycle_New_Color (d_global_min_edge_index,
		 * d_graph_edges, d_graph_colorindex, d_global_colors, d_cycle_edge,
		 * no_of_nodes );
		 */
		int kernelArgs_EPCNC[] = { 
				MstCL_ID.GLOBAL_MIN_EDGE_INDEX, 
				MstCL_ID.GRAPH_EDGES, 
				MstCL_ID.GRAPH_COLORINDEX, 
				MstCL_ID.GLOBAL_COLORS, 
				MstCL_ID.CYCLE_EDGE, 
				MstCL_ID.N_OF_NODES };
		setKernelArgs(kernelArgs_EPCNC,kernel_EPCNC.getKernel());

		/*
		 * Kernel_Remove_Cycle_Edge_From_MST (d_cycle_edge, d_graph_MST_edges,
		 * no_of_nodes);
		 */
		int kernelArgs_RCEFMST[] = { 
				MstCL_ID.CYCLE_EDGE, 
				MstCL_ID.GRAPH_MST_EDGES,
				MstCL_ID.N_OF_NODES };
		setKernelArgs(kernelArgs_RCEFMST,kernel_RCEFMST.getKernel());

		/*
		 * Kernel_Reinitialize (d_active_colors, d_global_min_edge_index,
		 * d_global_min_c2, d_global_min_edge_weight,
		 * d_min_edge_index,d_min_edge_weight, d_degree, d_updating_degree,
		 * d_prev_colors, d_cycle_edge, d_prev_colorindex, no_of_nodes );
		 */
		int kernelArgs_R[] = { 
				MstCL_ID.ACTIVE_COLORS, 
				MstCL_ID.GLOBAL_MIN_EDGE_INDEX, 
				MstCL_ID.GLOBAL_MIN_C2,
				MstCL_ID.GLOBAL_MIN_EDGE_WEIGHT, 
				MstCL_ID.MIN_EDGE_INDEX, 
				MstCL_ID.MIN_EDGE_WEIGHT,
				MstCL_ID.DEGREE, 
				MstCL_ID.UPDATING_DEGREE, 
				MstCL_ID.PREV_COLORS,
				MstCL_ID.CYCLE_EDGE, 
				MstCL_ID.PREV_COLORINDEX,
				MstCL_ID.N_OF_NODES };
		setKernelArgs(kernelArgs_R,kernel_R.getKernel());

	}
	
	/**
	 * 
	 * @param argIndexes Array Containing indexes of the instantiated memObjects array
	 *  that are passed to the kernel (following the signature order)
	 * @param kernel kernel in witch set the arguments
	 */
	private void setKernelArgs(int argIndexes[] , cl_kernel kernel){
		for(int j=0; j<argIndexes.length; j++) 
			clSetKernelArg(kernel, j, Sizeof.cl_mem, Pointer.to(memObjects[argIndexes[j]]));
	}

	public void runMST() {

		// Set the arguments for the kernels
		this.setKernelsArgs();

		cl_command_queue commandQueue = dr.getCommandQueue();

		ti.start();
		int k = 0, k2 = 0, k3 = 0;
		/*	

*/

		ExecutionStatisticsCL executionStatistics = new ExecutionStatisticsCL();
		do {

			// Algorithm
			// if no thread changes this value then the loop stops
			over[0] = 0;
			// reset over
			cl_event writeEvent0 = new cl_event();
			clEnqueueWriteBuffer(commandQueue, memObjects[MstCL_ID.OVER],
					CL_FALSE, 0, Sizeof.cl_int, Pointer.to(over), 0, null,
					writeEvent0);
			// Execute the Kernel_Find_Min_Edge_Weight_Per_Vertex


			cl_event kernelEvent1 = new cl_event();
			clEnqueueNDRangeKernel(commandQueue,
					this.kernel_FMEWPV.getKernel(), 1, null, global_work_size,
					local_work_size, 0, null, kernelEvent1);


			// read over
			cl_event readEvent0 = new cl_event();
			clEnqueueReadBuffer(commandQueue, memObjects[MstCL_ID.OVER], CL_TRUE,
					0, Sizeof.cl_int, Pointer.to(over), 0, null, readEvent0);

			if (this.profilingEnabled) {
				executionStatistics
						.addEntry("it(" + k + ")write0", writeEvent0);
				executionStatistics.addEntry("it(" + k + ")kernel1",
						kernelEvent1);
				executionStatistics.addEntry("it(" + k + ")read0", readEvent0);
			}

			if (over[0] == 0)
				break;

			// Execute kernel Kernel_Find_Min_C2

			cl_event kernelEvent2 = new cl_event();
			clEnqueueNDRangeKernel(commandQueue, this.kernel_FMC2.getKernel(),
					1, null, global_work_size, local_work_size, 0, null,
					kernelEvent2);



			// Execute Kernel_Add_Edge_To_Global_Min

			cl_event kernelEvent3 = new cl_event();
			clEnqueueNDRangeKernel(commandQueue, this.kernel_AETGM.getKernel(),
					1, null, global_work_size, local_work_size, 0, null,
					kernelEvent3);


			// execute Kernel_Find_Degree

			cl_event kernelEvent4 = new cl_event();
			clEnqueueNDRangeKernel(commandQueue, this.kernel_FD.getKernel(), 1,
					null, global_work_size, local_work_size, 0, null,
					kernelEvent4);

			do {
				done[0] = 0;
				// reset done
				cl_event writeEvent1 = new cl_event();
				clEnqueueWriteBuffer(commandQueue, memObjects[MstCL_ID.DONE],
						CL_FALSE, 0, Sizeof.cl_int, Pointer.to(done), 0, null,
						writeEvent1);
				// Execute Kernel_Prop_Colors1

				cl_event kernelEvent5 = new cl_event();
				clEnqueueNDRangeKernel(commandQueue,
						this.kernel_PC1.getKernel(), 1, null, global_work_size,
						local_work_size, 0, null, kernelEvent5);

				// Execute Kernel_Prop_Colors2

				cl_event kernelEvent6 = new cl_event();
				clEnqueueNDRangeKernel(commandQueue,
						this.kernel_PC2.getKernel(), 1, null, global_work_size,
						local_work_size, 0, null, kernelEvent6);

				// read done
				cl_event readEvent1 = new cl_event();
				clEnqueueReadBuffer(commandQueue, memObjects[MstCL_ID.DONE],
						CL_TRUE, 0, Sizeof.cl_int, Pointer.to(done), 0, null,
						readEvent1);
				if (this.profilingEnabled) {
					executionStatistics.addEntry("it(" + k + " , " + k3
							+ ")write1", writeEvent1);
					executionStatistics.addEntry("it(" + k + " , " + k3
							+ ")kernel5", kernelEvent5);
					executionStatistics.addEntry("it(" + k + " , " + k3
							+ ")kernel6", kernelEvent6);
					executionStatistics.addEntry("it(" + k + " , " + k3
							+ ")read1", readEvent1);
				}
				k3++;
			} while (done[0] != 0);

			// Execute Kernel_Update_Colorindex
			cl_event kernelEvent7 = new cl_event();
			clEnqueueNDRangeKernel(commandQueue, this.kernel_UC.getKernel(),
					1, null, global_work_size, local_work_size, 0, null,
					kernelEvent7);

			
			do {
				removing[0] = 0;
				// reset removing
				cl_event writeEvent2 = new cl_event();
				clEnqueueWriteBuffer(commandQueue, memObjects[MstCL_ID.REMOVING],
						CL_FALSE, 0, Sizeof.cl_int, Pointer.to(removing), 0,
						null, writeEvent2);

				// execute Kernel_Dec_Degree1
				cl_event kernelEvent8 = new cl_event();
				clEnqueueNDRangeKernel(commandQueue,
						this.kernel_DD1.getKernel(), 1, null, global_work_size,
						local_work_size, 0, null, kernelEvent8);

				
				// Execute Kernel_Dec_Degree2
				cl_event kernelEvent9 = new cl_event();
				clEnqueueNDRangeKernel(commandQueue,
						this.kernel_DD2.getKernel(), 1, null, global_work_size,
						local_work_size, 0, null, kernelEvent9);

				
				// read removing
				cl_event readEvent2 = new cl_event();
				clEnqueueReadBuffer(commandQueue, memObjects[MstCL_ID.REMOVING],
						CL_TRUE, 0, Sizeof.cl_int, Pointer.to(removing), 0,
						null, readEvent2);
				if (this.profilingEnabled) {
					executionStatistics.addEntry("it(" + k + " , " + k2
							+ ")write2", writeEvent2);
					executionStatistics.addEntry("it(" + k + " , " + k2
							+ ")kernel8", kernelEvent8);
					executionStatistics.addEntry("it(" + k + " , " + k2
							+ ")kernel9", kernelEvent9);
					executionStatistics.addEntry("it(" + k + " , " + k2
							+ ")read2", readEvent2);
				}
				k2++;
			} while (removing[0] != 0);

			// Execute Kernel_Add_Remaining_Edges
			cl_event kernelEvent10 = new cl_event();
			clEnqueueNDRangeKernel(commandQueue, this.kernel_ARE.getKernel(),
					1, null, global_work_size, local_work_size, 0, null,
					kernelEvent10);

			// Kernel_Edge_Per_Cycle_New_Color
			cl_event kernelEvent11 = new cl_event();
			clEnqueueNDRangeKernel(commandQueue, this.kernel_EPCNC.getKernel(),
					1, null, global_work_size, local_work_size, 0, null,
					kernelEvent11);

			// Kernel_Remove_Cycle_Edge_From_MST
			cl_event kernelEvent12 = new cl_event();
			clEnqueueNDRangeKernel(commandQueue,
					this.kernel_RCEFMST.getKernel(), 1, null, global_work_size,
					local_work_size, 0, null, kernelEvent12);

			// Kernel_Reinitialize
			cl_event kernelEvent13 = new cl_event();
			clEnqueueNDRangeKernel(commandQueue, this.kernel_R.getKernel(), 1,
					null, global_work_size, local_work_size, 0, null,
					kernelEvent13);

			if (this.profilingEnabled) {
				// needed if profiling enabled
				CL.clFinish(commandQueue);

				executionStatistics.addEntry("it(" + k + ")kernel2",
						kernelEvent2);
				executionStatistics.addEntry("it(" + k + ")kernel3",
						kernelEvent3);
				executionStatistics.addEntry("it(" + k + ")kernel4",
						kernelEvent4);
				executionStatistics.addEntry("it(" + k + ")kernel7",
						kernelEvent7);
				executionStatistics.addEntry("it(" + k + ")kernel10",
						kernelEvent10);
				executionStatistics.addEntry("it(" + k + ")kernel11",
						kernelEvent11);
				executionStatistics.addEntry("it(" + k + ")kernel12",
						kernelEvent12);
				executionStatistics.addEntry("it(" + k + ")kernel13",
						kernelEvent13);
			}

			k++;
		} while (true);

		// retrieving results
		cl_event readOutputEvent = new cl_event();
		clEnqueueReadBuffer(commandQueue, memObjects[MstCL_ID.GRAPH_MST_EDGES],
				CL_TRUE, 0, graph.getEdgesNumber() * Sizeof.cl_int,
				Pointer.to(graph_MST_edges), 0, null, readOutputEvent);

		ti.stop();
		// error checking
		int test[] = new int[graph.getGraphNodesNumber()];
		// read colors
		clEnqueueReadBuffer(commandQueue, memObjects[MstCL_ID.GRAPH_COLORINDEX],
				CL_TRUE, 0, graph.getGraphNodesNumber() * Sizeof.cl_int,
				Pointer.to(test), 0, null, null);

		for (int i = 0; i < this.num_of_nodes[0]; i++) {
			if (test[i] != 0) {
				logger.severe("All Colors not 0, Error at " + i);
				break;
			}

		}

		if (profilingEnabled) {
			executionStatistics.addEntry("outputRead", readOutputEvent);
			logger.finest(executionStatistics.toStringAggregate());
			logger.finest(executionStatistics.toString());
		}
		logger.info("MST_CL : " + ti.getTimeCount() + " ms ( in " + (k)
				+ "-iterations )");

		
	}

	/*private void debugTest(String str) {
		if(debug){
		logger.finest("================== DEBUG TEST: ( "
				+ (str == null ? "" : str) + " )");
		cl_command_queue commandQueue = dr.getCommandQueue();

		int nOfNodes = this.graph.getGraphNodesNumber();
		int nOfEdges = graph.getEdgesNumber();

		logger.finest("graph_edges : \n"
				+ java.util.Arrays.toString(this.graph.getGraphEdges()));
		// graph_MST_edges init result array graph.getEdgesNumber()
		int graph_MST_edges[] = new int[nOfEdges];
		clEnqueueReadBuffer(commandQueue, memObjects[MstCL_ID.GRAPH_MST_EDGES],
				CL_TRUE, 0, graph.getEdgesNumber() * Sizeof.cl_int,
				Pointer.to(graph_MST_edges), 0, null, null);
		logger.finest("graph_MST_edges : \n"
				+ java.util.Arrays.toString(graph_MST_edges));

		// d_over, sizeof(int)));
		int over[] = new int[1];
		clEnqueueReadBuffer(commandQueue, memObjects[MstCL_ID.OVER], CL_TRUE, 0,
				Sizeof.cl_int, Pointer.to(over), 0, null, null);
		// d_done, sizeof(int)));
		int done[] = new int[1];
		clEnqueueReadBuffer(commandQueue, memObjects[MstCL_ID.DONE], CL_TRUE, 0,
				Sizeof.cl_int, Pointer.to(done), 0, null, null);
		// d_removing, sizeof(int)));
		int removing[] = new int[1];
		clEnqueueReadBuffer(commandQueue, memObjects[MstCL_ID.REMOVING], CL_TRUE,
				0, Sizeof.cl_int, Pointer.to(removing), 0, null, null);
		// n of nodes
		int non[] = new int[1];
		clEnqueueReadBuffer(commandQueue, memObjects[MstCL_ID.N_OF_NODES],
				CL_TRUE, 0, Sizeof.cl_int, Pointer.to(non), 0, null, null);

		// d_active_colors, falseval, sizeof(bool)*no_of_nodes
		int active_colors[] = new int[nOfNodes];
		clEnqueueReadBuffer(commandQueue, memObjects[MstCL_ID.ACTIVE_COLORS],
				CL_TRUE, 0, nOfNodes * Sizeof.cl_int,
				Pointer.to(active_colors), 0, null, null);

		// d_global_min_edge_index, infinity, sizeof(int)*no_of_nodes
		int global_min_edge_index[] = new int[nOfNodes];
		clEnqueueReadBuffer(commandQueue,
				memObjects[MstCL_ID.GLOBAL_MIN_EDGE_INDEX], CL_TRUE, 0, nOfNodes
						* Sizeof.cl_int, Pointer.to(global_min_edge_index), 0,
				null, null);

		// d_min_edge_index, infinity, sizeof(int)*no_of_nodes
		int min_edge_index[] = new int[nOfNodes];
		clEnqueueReadBuffer(commandQueue, memObjects[MstCL_ID.MIN_EDGE_INDEX],
				CL_TRUE, 0, nOfNodes * Sizeof.cl_int,
				Pointer.to(min_edge_index), 0, null, null);

		// d_min_edge_weight, infinity, sizeof(int)*no_of_nodes
		int min_edge_weight[] = new int[nOfNodes];
		clEnqueueReadBuffer(commandQueue, memObjects[MstCL_ID.MIN_EDGE_WEIGHT],
				CL_TRUE, 0, nOfNodes * Sizeof.cl_int,
				Pointer.to(min_edge_weight), 0, null, null);

		// d_updating_global_colors, sameindex, sizeof(int)*no_of_nodes
		int updating_global_colors[] = new int[nOfNodes];
		clEnqueueReadBuffer(commandQueue,
				memObjects[MstCL_ID.UPDATING_GLOBAL_COLORS], CL_TRUE, 0, nOfNodes
						* Sizeof.cl_int, Pointer.to(updating_global_colors), 0,
				null, null);

		// d_active_vertices, trueval, sizeof(int)*no_of_nodes
		int active_vertices[] = new int[nOfNodes];
		clEnqueueReadBuffer(commandQueue, memObjects[MstCL_ID.ACTIVE_VERTICLES],
				CL_TRUE, 0, nOfNodes * Sizeof.cl_int,
				Pointer.to(active_vertices), 0, null, null);

		// d_graph_colorindex, sameindex, sizeof(int)*no_of_nodes
		int graph_colorindex[] = new int[nOfNodes];
		clEnqueueReadBuffer(commandQueue, memObjects[MstCL_ID.GRAPH_COLORINDEX],
				CL_TRUE, 0, nOfNodes * Sizeof.cl_int,
				Pointer.to(graph_colorindex), 0, null, null);

		// d_global_colors, sameindex, sizeof(int)*no_of_nodes
		int global_colors[] = new int[nOfNodes];
		clEnqueueReadBuffer(commandQueue, memObjects[MstCL_ID.GLOBAL_COLORS],
				CL_TRUE, 0, nOfNodes * Sizeof.cl_int,
				Pointer.to(global_colors), 0, null, null);

		// d_prev_colorindex, sameindex, sizeof(int)*no_of_nodes
		int prev_colorindex[] = new int[nOfNodes];
		clEnqueueReadBuffer(commandQueue, memObjects[MstCL_ID.PREV_COLORINDEX],
				CL_TRUE, 0, nOfNodes * Sizeof.cl_int,
				Pointer.to(prev_colorindex), 0, null, null);

		// d_cycle_edge, infinity, sizeof(int)*no_of_nodes
		int cycle_edge[] = new int[nOfNodes];
		clEnqueueReadBuffer(commandQueue, memObjects[MstCL_ID.CYCLE_EDGE],
				CL_TRUE, 0, nOfNodes * Sizeof.cl_int, Pointer.to(cycle_edge),
				0, null, null);

		// d_prev_colors, sameindex, sizeof(int)*no_of_nodes
		int prev_colors[] = new int[nOfNodes];
		clEnqueueReadBuffer(commandQueue, memObjects[MstCL_ID.PREV_COLORS],
				CL_TRUE, 0, nOfNodes * Sizeof.cl_int, Pointer.to(prev_colors),
				0, null, null);

		// d_updating_degree, zero, sizeof(unsigned int)*no_of_nodes
		int updating_degree[] = new int[nOfNodes];
		clEnqueueReadBuffer(commandQueue, memObjects[MstCL_ID.UPDATING_DEGREE],
				CL_TRUE, 0, nOfNodes * Sizeof.cl_int,
				Pointer.to(updating_degree), 0, null, null);

		// d_degree, zero, sizeof(unsigned int)*no_of_nodes,
		int degree[] = new int[nOfNodes];
		clEnqueueReadBuffer(commandQueue, memObjects[MstCL_ID.DEGREE], CL_TRUE, 0,
				nOfNodes * Sizeof.cl_int, Pointer.to(degree), 0, null, null);

		// d_global_min_edge_weight, infinity, sizeof(int)*no_of_nodes
		int global_min_edge_weight[] = new int[nOfNodes];
		clEnqueueReadBuffer(commandQueue,
				memObjects[MstCL_ID.GLOBAL_MIN_EDGE_WEIGHT], CL_TRUE, 0, nOfNodes
						* Sizeof.cl_int, Pointer.to(global_min_edge_weight), 0,
				null, null);

		// d_global_min_c2, infinity, sizeof(int)*no_of_nodes
		int global_min_c2[] = new int[nOfNodes];
		clEnqueueReadBuffer(commandQueue, memObjects[MstCL_ID.GLOBAL_MIN_C2],
				CL_TRUE, 0, nOfNodes * Sizeof.cl_int,
				Pointer.to(global_min_c2), 0, null, null);

		logger.finest("global_colors (si): \n"
				+ java.util.Arrays.toString(global_colors));
		logger.finest("graph_colorindexs (si): \n"
				+ java.util.Arrays.toString(graph_colorindex));
		logger.finest("active_colors (fv): \n"
				+ java.util.Arrays.toString(active_colors));
		logger.finest("active_vertices (tv): \n"
				+ java.util.Arrays.toString(active_vertices));
		logger.finest("updating_global_colors (si): \n"
				+ java.util.Arrays.toString(updating_global_colors));
		logger.finest("updating_degree (z): \n"
				+ java.util.Arrays.toString(updating_degree));
		logger.finest("prev_colors (si): \n"
				+ java.util.Arrays.toString(prev_colors));
		logger.finest("prev_colorindex (si): \n"
				+ java.util.Arrays.toString(prev_colorindex));
		logger.finest("cycle_edge (inf): \n"
				+ java.util.Arrays.toString(cycle_edge));
		logger.finest("min_edge_weight (inf): \n"
				+ java.util.Arrays.toString(min_edge_weight));
		logger.finest("min_edge_index (inf): \n"
				+ java.util.Arrays.toString(min_edge_index));
		logger.finest("global_min_edge_index (inf): \n"
				+ java.util.Arrays.toString(global_min_edge_index));
		logger.finest("global_min_edge_weight (inf): \n"
				+ java.util.Arrays.toString(global_min_edge_weight));
		logger.finest("global_min_c2 (inf): \n"
				+ java.util.Arrays.toString(global_min_c2));
		logger.finest("degree (z): \n" + java.util.Arrays.toString(degree));

		logger.finest("over - done - removing - #nodes : (" + over[0] + " , "
				+ done[0] + " , " + removing[0] + " , " + non[0] + ")");

		logger.finest("============================ END DEBUG TEST \n");
		}
	}*/

	protected void finalize() {
		// Release memory objects
		for (int i = 0; i < memObjects.length; i++)
			clReleaseMemObject(memObjects[i]);

	}

	/**
	 * @returns an array containing the following infos { No of edges in MST ,
	 *          no of nodes , minimum cost } null if the calculation has to be
	 *          done
	 * */
	public double[] getResult() {
		double result[] = { 0, 0, 0 };
		if (over[0] == 0) {
			if(this.minimumCost==-1){
			int q = 0;
			double minCost = 0;
			// Final edges present in MST
			int weights[] = graph.getGraphEdgesWeightsArr();
			for (int i = 0; i < this.graph.getEdgesNumber(); i++) {
				if (graph_MST_edges[i] != 0) {

					int edge = i;
					int edgeweight = weights[edge];
					minCost += edgeweight;
					q++;
				}
			}
			this.minimumCost = minCost;
			mst_num_of_edges = q;
			}
			result[0] = mst_num_of_edges;
			result[1] = this.num_of_nodes[0];
			result[2] = this.minimumCost;
			//System.out.println("peso "+minimumCost + " "+ result[2]);
			return result;
		}

		return null;
	}

	public static class MstCL_ID {
		// read only
		public static final int GRAPH_NODES_NOE = 0;
		public static final int GRAPH_NODES_STARTING = 1;
		public static final int GRAPH_EDGES = 2;
		public static final int GRAPH_EDGES_WEIGHT = 3;
		// read write
		public static final int GRAPH_MST_EDGES = 4;
		public static final int GLOBAL_COLORS = 5;
		public static final int GRAPH_COLORINDEX = 6;
		public static final int ACTIVE_COLORS = 7;
		public static final int ACTIVE_VERTICLES = 8;
		public static final int UPDATING_GLOBAL_COLORS = 9;
		public static final int UPDATING_DEGREE = 10;
		public static final int PREV_COLORS = 11;
		public static final int PREV_COLORINDEX = 12;
		public static final int CYCLE_EDGE = 13;
		public static final int MIN_EDGE_WEIGHT = 14;
		public static final int MIN_EDGE_INDEX = 15;
		public static final int GLOBAL_MIN_EDGE_WEIGHT = 16;
		public static final int GLOBAL_MIN_EDGE_INDEX = 17;
		public static final int GLOBAL_MIN_C2 = 18;
		public static final int DEGREE = 19;
		public static final int OVER = 20;
		public static final int DONE = 21;
		public static final int REMOVING = 22;
		public static final int N_OF_NODES = 23;
	}
}
