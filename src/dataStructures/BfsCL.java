package dataStructures;

import static org.jocl.CL.*;

import java.io.FileNotFoundException;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

import joclUtilsPkg.*;

import org.jocl.Pointer;
import org.jocl.Sizeof;
import org.jocl.cl_command_queue;
import org.jocl.cl_context;
import org.jocl.cl_event;
import org.jocl.cl_kernel;
import org.jocl.cl_mem;

import PerformanceEvaluation.ExecutionStatisticsCL;
import PerformanceEvaluation.ExecutionStatisticsCPU;
import PerformanceEvaluation.SimpleTimer;

@SuppressWarnings("unused")
public class BfsCL {
	private static final Logger logger = Logger
			.getLogger(Logger.GLOBAL_LOGGER_NAME);
	private static final String kernelFilePath = "./kernels/kernelsBFS.cl";
	// each invocation is related with one kernel related to a device
	private DeviceRecord dr;
	private KernelRecord kernelCL, kernel2CL;
	// representation of the graph in a compressed ds
	private GraphCL graph;
	private int num_of_nodes[];
	// result of the BFS
	private int cost[];
	// service data structures
	// boolean can't be used in this environment, it don't have a specified size
	// and can't be pointed
	// i will emulate it using an integer value in which 0 = false
	//also unsigned int doesn't not exist in java
	private int graphMask[];
	private int updatingGraphMask[];
	private int graphVisited[];
	// CL variables
	private cl_mem memObjects[];
	private int stop[] = { 1 };
	private int MAX_WORK_ITEM_PER_WORK_GROUP[] = new int[1];
	// work-item dimensions
	private long global_work_size[];
	private long local_work_size[];
	private SimpleTimer ti = new SimpleTimer("TimerBfs");
	private boolean profilingEnabled = false;

	public BfsCL(DeviceRecord dr, GraphCL graph) {
		this.dr = dr;
		this.graph = graph;
		this.num_of_nodes = new int[1];
		this.num_of_nodes[0] = graph.getGraphNodesNumber();
		// init service structures
		graphMask = new int[num_of_nodes[0]];
		updatingGraphMask = new int[num_of_nodes[0]];
		graphVisited = new int[num_of_nodes[0]];
		
		profilingEnabled = JoclDeviceUtils.isProfilingEnabled(dr);
		Level fine = Level.FINE;
		if(logger.getLevel().intValue() >= fine.intValue()){
			profilingEnabled=false;
		}
		this.MAX_WORK_ITEM_PER_WORK_GROUP[0] = (int) JoclDeviceUtils.getCL_DEVICE_MAX_WORK_ITEM_SIZES(dr.getDeviceID());
		// setting dimension of global and local work group

		/*
		 * global size is the total number of work items you want to run, and
		 * the local size is the size of each workgroup
		 */
		
		// leaving the size equal to null we delegate to the library to choose
		// an appropriate local work size
		global_work_size = new long[]{ num_of_nodes[0] };
		local_work_size = null;

		cost = new int[graph.getGraphNodesNumber()];
		for (int i = 0; i < graph.getGraphNodesNumber(); i++) {
			cost[i] = -1;
			updatingGraphMask[i] = 0;
			graphMask[i] = 0;
			graphVisited[i] = 0;
		}
		// set up the source
		cost[graph.getSource()] = 0;
		graphMask[graph.getSource()] = 1;
		graphVisited[graph.getSource()] = 1;

		// move data structures to the device memory
		initCLDeviceMem();
		// kernel is related to BFS so is necessary to import BFK kernel here
		// load kernel
		kernelCL = null;
		kernel2CL = null;
		try {
			// load kernel from file
			kernelCL = JoclKernelUtils.createKernelFromFile(dr,
					kernelFilePath, "kernelBFS", null);
			kernel2CL = JoclKernelUtils.createKernelFromFile(dr,
					kernelFilePath, "kernelBFS2", null);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			System.exit(1);
		}
	}

	private void initCLDeviceMem() {
		cl_context context = dr.getContext();
		ti.start();
		// Allocate the memory objects for the input- and output data
		memObjects = new cl_mem[10];
		// init graph nodes arrays
		/*
		 * is an alternative way to do the transfer but  it is asynchronous
		 * SimpleTimer sw = new SimpleTimer(); sw.start(); memObjects[0] =
		 * clCreateBuffer(context, CL_MEM_READ_ONLY, Sizeof.cl_int *
		 * graph.getGraphNodesNumber(), null, null);
		 * clEnqueueWriteBuffer(dr.getCommandQueue(), memObjects[0] , CL_TRUE,
		 * 0, Sizeof.cl_int * graph.getGraphNodesNumber() ,
		 * graph.getGraphNodesNofEdgesPtr(), 0, null, null);
		 * clFinish(dr.getCommandQueue()); sw.stop();
		 * System.out.println(sw.getTimeCountAndReset());
		 */
		memObjects[BfsCL_ID.GRAPH_NODES_NOE] = clCreateBuffer(context, CL_MEM_READ_ONLY
				| CL_MEM_COPY_HOST_PTR,
				Sizeof.cl_int * graph.getGraphNodesNumber(),
				graph.getGraphNodesNofEdgesPtr(), null);

		memObjects[BfsCL_ID.GRAPH_NODES_STARTING] = clCreateBuffer(context, CL_MEM_READ_ONLY
				| CL_MEM_COPY_HOST_PTR,
				Sizeof.cl_int * graph.getGraphNodesNumber(),
				graph.getGraphNodesStartingPtr(), null);

		// init edges array
		memObjects[BfsCL_ID.GRAPH_EDGES] = clCreateBuffer(context, CL_MEM_READ_ONLY
				| CL_MEM_COPY_HOST_PTR, Sizeof.cl_int * graph.getEdgesNumber(),
				graph.getGraphEdgesPtr(), null);
		// init serv arrays
		memObjects[BfsCL_ID.GRAPH_MASK] = clCreateBuffer(context, CL_MEM_READ_WRITE
				| CL_MEM_COPY_HOST_PTR,
				Sizeof.cl_int * graph.getGraphNodesNumber(), getGraphMaskPtr(),
				null);
		memObjects[BfsCL_ID.UPDATING_GRAPH_MASK] = clCreateBuffer(context, CL_MEM_READ_WRITE
				| CL_MEM_COPY_HOST_PTR,
				Sizeof.cl_int * graph.getGraphNodesNumber(),
				getUpdatingGraphMaskPtr(), null);
		memObjects[BfsCL_ID.GRAPH_VISITED] = clCreateBuffer(context, CL_MEM_READ_WRITE
				| CL_MEM_COPY_HOST_PTR,
				Sizeof.cl_int * graph.getGraphNodesNumber(),
				getGraphVisitedPtr(), null);

		// init results ds

		memObjects[BfsCL_ID.COST] = clCreateBuffer(context, CL_MEM_READ_WRITE
				| CL_MEM_COPY_HOST_PTR,
				Sizeof.cl_int * graph.getGraphNodesNumber(), Pointer.to(cost),
				null);
		// setup last parameter: number of nodes
		memObjects[BfsCL_ID.N_OF_NODES] = clCreateBuffer(context, CL_MEM_READ_ONLY
				| CL_MEM_COPY_HOST_PTR, Sizeof.cl_int,
				Pointer.to(num_of_nodes), null);
		memObjects[BfsCL_ID.MAX_WORK_ITEM_PER_WORK_GROUP] = clCreateBuffer(context, CL_MEM_READ_ONLY
				| CL_MEM_COPY_HOST_PTR, Sizeof.cl_int,
				Pointer.to(MAX_WORK_ITEM_PER_WORK_GROUP), null);
		memObjects[BfsCL_ID.STOP] = clCreateBuffer(context, CL_MEM_READ_WRITE
				| CL_MEM_COPY_HOST_PTR, Sizeof.cl_int, Pointer.to(stop), null);
		ti.stop();
		String tBytes = NumberFormat
				.getNumberInstance(Locale.US)
				.format(((6 * graph.getGraphNodesNumber() * Sizeof.cl_int) + (Sizeof.cl_int
						* graph.getEdgesNumber() * 1)));
		logger.fine("Allocation of " + tBytes + " Bytes on the device done in "
				+ ti.getTimeCountAndReset() + " ms");
	}

	private void setKernelsArgs() {
		ti.start();
		cl_kernel kernel = kernelCL.getKernel();
		cl_kernel kernel2 = kernel2CL.getKernel();
		

		// Set the arguments for the kernel 1
		//( g_graph_nodes_noe, g_graph_nodes_start, g_graph_edges, g_graph_mask,
		//  g_updating_graph_mask,  g_graph_visited, g_cost, no_of_nodes,  MAX_WIPG)

		int kernel1Args[] = {
				BfsCL_ID.GRAPH_NODES_NOE, 
				BfsCL_ID.GRAPH_NODES_STARTING,
				BfsCL_ID.GRAPH_EDGES, 
				BfsCL_ID.GRAPH_MASK,
				BfsCL_ID.UPDATING_GRAPH_MASK, 
				BfsCL_ID.GRAPH_VISITED ,
				BfsCL_ID.COST, 
				BfsCL_ID.N_OF_NODES,
				BfsCL_ID.MAX_WORK_ITEM_PER_WORK_GROUP
		};
		setKernelArgs(kernel1Args, kernel);

		// Set the arguments for the kernel 2
		// g_graph_mask, g_updating_graph_mask, g_graph_visited,
		// g_over,no_of_nodes, MAX_WIPG
		int kernel2Args[] = { 
				BfsCL_ID.GRAPH_MASK,
				BfsCL_ID.UPDATING_GRAPH_MASK, 
				BfsCL_ID.GRAPH_VISITED ,
				BfsCL_ID.STOP, 
				BfsCL_ID.N_OF_NODES, 
				BfsCL_ID.MAX_WORK_ITEM_PER_WORK_GROUP
		};
		setKernelArgs(kernel2Args, kernel2);
		
		
		ti.stop();
		logger.fine("Set kernel args in " + ti.getTimeCountAndReset() + " ms");
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
	
	
	public void runBFS() {
		if (stop[0] == 0)
			return ;
		
		setKernelsArgs();
		
		ti.start();
		cl_command_queue commandQueue = dr.getCommandQueue();
		cl_kernel kernel = kernelCL.getKernel();
		cl_kernel kernel2 = kernel2CL.getKernel();
		int count = 0;
		ExecutionStatisticsCL executionStatistics = new ExecutionStatisticsCL();
		//ExecutionStatisticsCPU execStat = new ExecutionStatisticsCPU();
		
		do {
			// Algorithm
			// if no thread changes this value then the loop stops
			stop[0] = 0;
			count++;
			// reset stop
			
			cl_event writeEvent0 = new cl_event();
			clEnqueueWriteBuffer(commandQueue, memObjects[9], CL_FALSE, 0,
					Sizeof.cl_int, Pointer.to(stop), 0, null, writeEvent0);

			
			// Execute the kernel
			cl_event kernelEvent1 = new cl_event();
			clEnqueueNDRangeKernel(commandQueue, kernel, 1, null,
					global_work_size, local_work_size, 0, null, kernelEvent1);
			
			// Execute the kernel2
			cl_event kernelEvent2 = new cl_event();
			clEnqueueNDRangeKernel(commandQueue, kernel2, 1, null,
					global_work_size, local_work_size, 0, null, kernelEvent2);

			// verify termination
			cl_event readEvent0 = new cl_event();
			clEnqueueReadBuffer(commandQueue, memObjects[9], CL_TRUE, 0,
					Sizeof.cl_int, Pointer.to(stop), 0, null, readEvent0);

			
			if (this.profilingEnabled) {
				executionStatistics.addEntry("it(" + count + ")write0",
						writeEvent0);
				executionStatistics.addEntry("it(" + count + ")kernel0",
						kernelEvent1);
				executionStatistics.addEntry("it(" + count + ")kernel1",
						kernelEvent2);
				executionStatistics.addEntry("it(" + count + ")read0",
						readEvent0);
			}
		} while (stop[0] != 0);
		// Read the output data
		cl_event readOutputEvent = new cl_event();
		clEnqueueReadBuffer(commandQueue, memObjects[6], CL_TRUE, 0,
				graph.getGraphNodesNumber() * Sizeof.cl_int, Pointer.to(cost),
				0, null, readOutputEvent);
		ti.stop();
		if (this.profilingEnabled) {
			executionStatistics.addEntry("outputRead", readOutputEvent);
			logger.finest(executionStatistics.toStringAggregate());
			logger.finest(executionStatistics.toString());
		}
		logger.info("BFS_CL : " + ti.getTimeCount() + "ms ( in " + (count)
				+ " iterations )");
		//free memory
		this.finalizeCL();
		
	}

	private Pointer getGraphVisitedPtr() {
		return Pointer.to(graphVisited);
	}

	private Pointer getUpdatingGraphMaskPtr() {
		return Pointer.to(updatingGraphMask);
	}

	private Pointer getGraphMaskPtr() {
		return Pointer.to(graphMask);
	}

	protected void finalizeCL() {
		// Release memory objects
		//TODO : review this solution
		for (int i = 0; i < memObjects.length; i++)
			clReleaseMemObject(memObjects[i]);
		kernelCL.finalizeCL();
		kernel2CL.finalizeCL();
	}

	public int[] getCostResult() {
		if (stop[0] == 0)
			return cost;
		return null;
	}
	
	public static class BfsCL_ID {
		// read only
		public static final int GRAPH_NODES_NOE = 0;
		public static final int GRAPH_NODES_STARTING = 1;
		public static final int GRAPH_EDGES = 2;
		public static final int GRAPH_MASK = 3;
		// read write
		public static final int UPDATING_GRAPH_MASK = 4;
		public static final int GRAPH_VISITED = 5;
		public static final int COST = 6;
		public static final int N_OF_NODES = 7;
		public static final int MAX_WORK_ITEM_PER_WORK_GROUP = 8;
		public static final int STOP = 9;
		
	}

}
