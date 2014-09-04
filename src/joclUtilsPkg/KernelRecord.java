package joclUtilsPkg;

import static org.jocl.CL.clReleaseKernel;
import static org.jocl.CL.clReleaseProgram;

import org.jocl.cl_kernel;
import org.jocl.cl_program;

public class KernelRecord {
	private cl_kernel kernel;
	private cl_program program;
	
	public KernelRecord(cl_kernel kernel,cl_program program){
		setKernel(kernel);
		setProgram(program);
	}
	
	public cl_kernel getKernel() {
		return kernel;
	}
	
	private void setKernel(cl_kernel kernel) {
		this.kernel = kernel;
	}
	
	public cl_program getProgram() {
		return program;
	}
	
	private void setProgram(cl_program program) {
		this.program = program;
	}
	
	
	public void finalizeCL(){
		clReleaseKernel(kernel);
        clReleaseProgram(program);
	}
}
