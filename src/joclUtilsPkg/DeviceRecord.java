package joclUtilsPkg;

import static org.jocl.CL.clReleaseCommandQueue;
import static org.jocl.CL.clReleaseContext;

import org.jocl.*;

public class DeviceRecord {
	private cl_command_queue commandQueue;
	private cl_device_id deviceID;
	private cl_context context;
	
	public DeviceRecord(cl_command_queue commandQueue, cl_device_id deviceID , cl_context context){
		setCommandQueue(commandQueue);
		setContext(context);
		setDeviceID(deviceID);
	}
	
	public cl_command_queue getCommandQueue() {
		return commandQueue;
	}
	
	private void setCommandQueue(cl_command_queue commandQueue) {
		this.commandQueue = commandQueue;
	}
	
	public cl_context getContext() {
		return context;
	}
	
	private void setContext(cl_context context) {
		this.context = context;
	}

	public cl_device_id getDeviceID() {
		return deviceID;
	}

	private void setDeviceID(cl_device_id deviceID) {
		this.deviceID = deviceID;
	}
	
	private void releaseCLDevice(){
		clReleaseCommandQueue(commandQueue);
        clReleaseContext(context);
	}
	
	protected void finalize(){
		releaseCLDevice();
	}
}
