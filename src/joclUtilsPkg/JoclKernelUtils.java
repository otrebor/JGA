package joclUtilsPkg;

import static org.jocl.CL.clBuildProgram;
import static org.jocl.CL.clCreateKernel;
import static org.jocl.CL.clCreateProgramWithSource;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Scanner;

import org.jocl.cl_context;
import org.jocl.cl_kernel;
import org.jocl.cl_program;

public class JoclKernelUtils {
	
	public static KernelRecord createKernel(DeviceRecord device, String programSource, String programName, String precompilerOptions) {
		
		cl_context context = device.getContext();
		// Create the program from the source code
		cl_program program = clCreateProgramWithSource(context, 1,
				new String[] { programSource }, null, null);

		// Build the program
		clBuildProgram(program, 0, null, precompilerOptions, null, null);

		// Create the kernel
		cl_kernel kernel = clCreateKernel(program, programName, null);
		
		return new KernelRecord(kernel,program);
	}
	
public static KernelRecord createKernelFromFile(DeviceRecord device, String kernelPath, String programName, String precompilerOptions) throws FileNotFoundException {
		
		File kernelFile = new File (kernelPath);
		Scanner kernelScanner = new Scanner( kernelFile );
	 	String programSource = kernelScanner.useDelimiter("\\A").next();
	 	kernelScanner.close();
		
		return createKernel(device,programSource,programName, precompilerOptions);
	}
}
