package joclUtilsPkg;

import static org.jocl.CL.*;

import org.jocl.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.*;
import java.util.*;
import java.util.logging.Logger;

public class JoclDeviceUtils {
	private final static Logger logger = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
	
	public static DeviceRecord selectDevice(boolean automaticSelection) {
		long DEVICE_TYPE = (automaticSelection ? CL_DEVICE_TYPE_GPU
				: CL_DEVICE_TYPE_ALL);
		// Obtain the number of platforms
		int numPlatforms[] = new int[1];
		clGetPlatformIDs(0, null, numPlatforms);
			logger.info("Number of platforms: " + numPlatforms[0]);

		// Obtain the platform IDs
		cl_platform_id platforms[] = new cl_platform_id[numPlatforms[0]];
		clGetPlatformIDs(platforms.length, platforms, null);

		// Collect all devices of all platforms
		List<cl_device_id> devices = new ArrayList<cl_device_id>();
		List<cl_platform_id> platformsList = new ArrayList<cl_platform_id>();
		
		for (int i = 0; i < platforms.length; i++) {
			String platformName = getString(platforms[i], CL_PLATFORM_NAME);
			logger.finest("----> "+platformName);
			// Obtain the number of devices for the current platform
			int numDevices[] = new int[1];
			try{
			clGetDeviceIDs(platforms[i], DEVICE_TYPE, 0, null, numDevices);
				logger.info("Number of devices in platform "
						+ platformName + ": " + numDevices[0]);
			}catch(CLException e){
				//no devices found on this platform
				continue;
			}
			cl_device_id devicesArray[] = new cl_device_id[numDevices[0]];
			cl_platform_id plattformsArray[] = new cl_platform_id[numDevices[0]];
			
			clGetDeviceIDs(platforms[i], DEVICE_TYPE, numDevices[0],
					devicesArray, null);
			for(int z = 0 ; z < devicesArray.length; z++)
				plattformsArray[z]=platforms[i];
			
			devices.addAll(Arrays.asList(devicesArray));
			platformsList.addAll(Arrays.asList(plattformsArray));
		}

		// The platform, device type and device number
		// that will be used
		int deviceIndex = 0;

		if (!automaticSelection) {
			// Reading the user preference
			printDevicesInfos(devices, platformsList);
			System.out.println("Enter the number of the selected device: ");
			BufferedReader br = new BufferedReader(new InputStreamReader(
					System.in));
			boolean ok = false;
			while (!ok) {
				try {
					deviceIndex = Integer.parseInt(br.readLine());
					if (deviceIndex >= 0 && deviceIndex < devices.size()) {
						ok = true;
					} else {
						System.err
								.println("Invalid ID! insert the correct device ID: ");
					}
				} catch (NumberFormatException nfe) {
					System.err
							.println("Invalid Format! insert the correct device ID: ");
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
					System.exit(1);
				}
			}
		}
		if(devices.size()==0){
			logger.severe("No CL devices found");
			System.exit(1);
		}
		// Obtain a platform ID
		cl_platform_id platform = platformsList.get(deviceIndex);
		//cl_platform_id platform = platforms[platformIndex];

		// Initialize the context properties
		cl_context_properties contextProperties = new cl_context_properties();
		contextProperties.addProperty(CL_CONTEXT_PLATFORM, platform);

		// Obtain a device ID
		cl_device_id device = devices.get(deviceIndex);

		// Create a context for the selected device
		cl_context context = clCreateContext(contextProperties, 1,
				new cl_device_id[] { device }, null, null, null);

		// Create a command-queue for the selected device
		cl_command_queue commandQueue = clCreateCommandQueue(context, device,
				CL_QUEUE_PROFILING_ENABLE | 0 /*in order*/, null);

		
			logger.info("Using device: "+ getString(device, CL_DEVICE_NAME));
			logger.info(deviceDescription(device));
		
		return new DeviceRecord(commandQueue, device, context);
	}

	public static void printDevicesInfos(List<cl_device_id> devices, List<cl_platform_id> platforms) {
		if(devices.size()!=platforms.size()){
			logger.severe("Error: printDevicesInfo");
			System.exit(1);
		}
		// Print the infos about all devices
		for (int i = 0; i < devices.size(); i++) {
			cl_device_id device = devices.get(i);
			cl_platform_id platform = platforms.get(i);
			// CL_DEVICE_NAME
			String deviceName = getString(device, CL_DEVICE_NAME);
			System.out.println("---Platform: " + getString(platform, CL_PLATFORM_NAME) +" Device # " + i + " named: "
					+ deviceName + ": ---");
			logger.fine(deviceDescription(device));
		}
	}
	
	public static String deviceDescription(cl_device_id device) {
		// Print the infos about all devices
		StringBuilder sb = new StringBuilder();
			// CL_DEVICE_NAME
			String deviceName = getString(device, CL_DEVICE_NAME);
			sb.append(String.format("CL_DEVICE_NAME: \t\t\t%s\n", deviceName));

			// CL_DEVICE_VENDOR
			String deviceVendor = getString(device, CL_DEVICE_VENDOR);
			sb.append(String.format("CL_DEVICE_VENDOR: \t\t\t%s\n", deviceVendor));

			// CL_DRIVER_VERSION
			String driverVersion = getString(device, CL_DRIVER_VERSION);
			sb.append(String.format("CL_DRIVER_VERSION: \t\t\t%s\n", driverVersion));

			// CL_DEVICE_TYPE
			long deviceType = getLong(device, CL_DEVICE_TYPE);
			if ((deviceType & CL_DEVICE_TYPE_CPU) != 0)
				sb.append(String.format("CL_DEVICE_TYPE:\t\t\t\t%s\n",
						"CL_DEVICE_TYPE_CPU"));
			if ((deviceType & CL_DEVICE_TYPE_GPU) != 0)
				sb.append(String.format("CL_DEVICE_TYPE:\t\t\t\t%s\n",
						"CL_DEVICE_TYPE_GPU"));
			if ((deviceType & CL_DEVICE_TYPE_ACCELERATOR) != 0)
				sb.append(String.format("CL_DEVICE_TYPE:\t\t\t\t%s\n",
						"CL_DEVICE_TYPE_ACCELERATOR"));
			if ((deviceType & CL_DEVICE_TYPE_DEFAULT) != 0)
				sb.append(String.format("CL_DEVICE_TYPE:\t\t\t\t%s\n",
						"CL_DEVICE_TYPE_DEFAULT"));

			// CL_DEVICE_MAX_COMPUTE_UNITS
			int maxComputeUnits = getInt(device, CL_DEVICE_MAX_COMPUTE_UNITS);
			sb.append(String.format("CL_DEVICE_MAX_COMPUTE_UNITS:\t\t%d\n",
					maxComputeUnits));

			// CL_DEVICE_MAX_WORK_ITEM_DIMENSIONS
			long maxWorkItemDimensions = getLong(device,
					CL_DEVICE_MAX_WORK_ITEM_DIMENSIONS);
			sb.append(String.format("CL_DEVICE_MAX_WORK_ITEM_DIMENSIONS:\t%d\n",
					maxWorkItemDimensions));

			// CL_DEVICE_MAX_WORK_ITEM_SIZES
			long maxWorkItemSizes[] = getSizes(device,
					CL_DEVICE_MAX_WORK_ITEM_SIZES, 3);
			sb.append(String.format(
					"CL_DEVICE_MAX_WORK_ITEM_SIZES:\t\t%d / %d / %d \n",
					maxWorkItemSizes[0], maxWorkItemSizes[1],
					maxWorkItemSizes[2]));

			// CL_DEVICE_MAX_WORK_GROUP_SIZE
			long maxWorkGroupSize = getSize(device,
					CL_DEVICE_MAX_WORK_GROUP_SIZE);
			sb.append(String.format("CL_DEVICE_MAX_WORK_GROUP_SIZE:\t\t%d\n",
					maxWorkGroupSize));

			// CL_DEVICE_MAX_CLOCK_FREQUENCY
			long maxClockFrequency = getLong(device,
					CL_DEVICE_MAX_CLOCK_FREQUENCY);
			sb.append(String.format("CL_DEVICE_MAX_CLOCK_FREQUENCY:\t\t%d MHz\n",
					maxClockFrequency));

			// CL_DEVICE_ADDRESS_BITS
			int addressBits = getInt(device, CL_DEVICE_ADDRESS_BITS);
			sb.append(String.format("CL_DEVICE_ADDRESS_BITS:\t\t\t%d\n", addressBits));

			// CL_DEVICE_MAX_MEM_ALLOC_SIZE
			long maxMemAllocSize = getLong(device, CL_DEVICE_MAX_MEM_ALLOC_SIZE);
			sb.append(String.format("CL_DEVICE_MAX_MEM_ALLOC_SIZE:\t\t%d MByte\n",
					(int) (maxMemAllocSize / (1024 * 1024))));

			// CL_DEVICE_GLOBAL_MEM_SIZE
			long globalMemSize = getLong(device, CL_DEVICE_GLOBAL_MEM_SIZE);
			sb.append(String.format("CL_DEVICE_GLOBAL_MEM_SIZE:\t\t%d MByte\n",
					(int) (globalMemSize / (1024 * 1024))));

			// CL_DEVICE_ERROR_CORRECTION_SUPPORT
			int errorCorrectionSupport = getInt(device,
					CL_DEVICE_ERROR_CORRECTION_SUPPORT);
			sb.append(String.format("CL_DEVICE_ERROR_CORRECTION_SUPPORT:\t%s\n",
					errorCorrectionSupport != 0 ? "yes" : "no"));

			// CL_DEVICE_LOCAL_MEM_TYPE
			int localMemType = getInt(device, CL_DEVICE_LOCAL_MEM_TYPE);
			sb.append(String.format("CL_DEVICE_LOCAL_MEM_TYPE:\t\t%s\n",
					localMemType == 1 ? "local" : "global"));

			// CL_DEVICE_LOCAL_MEM_SIZE
			long localMemSize = getLong(device, CL_DEVICE_LOCAL_MEM_SIZE);
			sb.append(String.format("CL_DEVICE_LOCAL_MEM_SIZE:\t\t%d KByte\n",
					(int) (localMemSize / 1024)));

			// CL_DEVICE_MAX_CONSTANT_BUFFER_SIZE
			long maxConstantBufferSize = getLong(device,
					CL_DEVICE_MAX_CONSTANT_BUFFER_SIZE);
			sb.append(String.format(
					"CL_DEVICE_MAX_CONSTANT_BUFFER_SIZE:\t%d KByte\n",
					(int) (maxConstantBufferSize / 1024)));

			// CL_DEVICE_QUEUE_PROPERTIES
			long queueProperties = getLong(device, CL_DEVICE_QUEUE_PROPERTIES);
			if ((queueProperties & CL_QUEUE_OUT_OF_ORDER_EXEC_MODE_ENABLE) != 0)
				sb.append(String.format("CL_DEVICE_QUEUE_PROPERTIES:\t\t%s\n",
						"CL_QUEUE_OUT_OF_ORDER_EXEC_MODE_ENABLE"));
			if ((queueProperties & CL_QUEUE_PROFILING_ENABLE) != 0)
				sb.append(String.format("CL_DEVICE_QUEUE_PROPERTIES:\t\t%s\n",
						"CL_QUEUE_PROFILING_ENABLE"));

			// CL_DEVICE_IMAGE_SUPPORT
			int imageSupport = getInt(device, CL_DEVICE_IMAGE_SUPPORT);
			sb.append(String.format("CL_DEVICE_IMAGE_SUPPORT:\t\t%d\n", imageSupport));

			// CL_DEVICE_MAX_READ_IMAGE_ARGS
			int maxReadImageArgs = getInt(device, CL_DEVICE_MAX_READ_IMAGE_ARGS);
			sb.append(String.format("CL_DEVICE_MAX_READ_IMAGE_ARGS:\t\t%d\n",
					maxReadImageArgs));

			// CL_DEVICE_MAX_WRITE_IMAGE_ARGS
			int maxWriteImageArgs = getInt(device,
					CL_DEVICE_MAX_WRITE_IMAGE_ARGS);
			sb.append(String.format("CL_DEVICE_MAX_WRITE_IMAGE_ARGS:\t\t%d\n",
					maxWriteImageArgs));

			// CL_DEVICE_SINGLE_FP_CONFIG
			long singleFpConfig = getLong(device, CL_DEVICE_SINGLE_FP_CONFIG);
			sb.append(String.format("CL_DEVICE_SINGLE_FP_CONFIG:\t\t%s\n",
					stringFor_cl_device_fp_config(singleFpConfig)));

			// CL_DEVICE_IMAGE2D_MAX_WIDTH
			long image2dMaxWidth = getSize(device, CL_DEVICE_IMAGE2D_MAX_WIDTH);
			sb.append(String.format("CL_DEVICE_2D_MAX_WIDTH\t\t\t%d\n",
					image2dMaxWidth));

			// CL_DEVICE_IMAGE2D_MAX_HEIGHT
			long image2dMaxHeight = getSize(device,
					CL_DEVICE_IMAGE2D_MAX_HEIGHT);
			sb.append(String.format("CL_DEVICE_2D_MAX_HEIGHT\t\t\t%d\n",
					image2dMaxHeight));

			// CL_DEVICE_IMAGE3D_MAX_WIDTH
			long image3dMaxWidth = getSize(device, CL_DEVICE_IMAGE3D_MAX_WIDTH);
			sb.append(String.format("CL_DEVICE_3D_MAX_WIDTH\t\t\t%d\n",
					image3dMaxWidth));

			// CL_DEVICE_IMAGE3D_MAX_HEIGHT
			long image3dMaxHeight = getSize(device,
					CL_DEVICE_IMAGE3D_MAX_HEIGHT);
			sb.append(String.format("CL_DEVICE_3D_MAX_HEIGHT\t\t\t%d\n",
					image3dMaxHeight));

			// CL_DEVICE_IMAGE3D_MAX_DEPTH
			long image3dMaxDepth = getSize(device, CL_DEVICE_IMAGE3D_MAX_DEPTH);
			sb.append(String.format("CL_DEVICE_3D_MAX_DEPTH\t\t\t%d\n",
					image3dMaxDepth));

			
			// CL_DEVICE_EXTENSION
			
			
			
			// CL_DEVICE_PREFERRED_VECTOR_WIDTH_<type>
			sb.append(String.format("CL_DEVICE_PREFERRED_VECTOR_WIDTH_<t>\t\n"));
			int preferredVectorWidthChar = getInt(device,
					CL_DEVICE_PREFERRED_VECTOR_WIDTH_CHAR);
			int preferredVectorWidthShort = getInt(device,
					CL_DEVICE_PREFERRED_VECTOR_WIDTH_SHORT);
			int preferredVectorWidthInt = getInt(device,
					CL_DEVICE_PREFERRED_VECTOR_WIDTH_INT);
			int preferredVectorWidthLong = getInt(device,
					CL_DEVICE_PREFERRED_VECTOR_WIDTH_LONG);
			int preferredVectorWidthFloat = getInt(device,
					CL_DEVICE_PREFERRED_VECTOR_WIDTH_FLOAT);
			int preferredVectorWidthDouble = getInt(device,
					CL_DEVICE_PREFERRED_VECTOR_WIDTH_DOUBLE);
			sb.append(String.format("CHAR %d, SHORT %d, INT %d, LONG %d, FLOAT %d, DOUBLE %d\n\n\n",
							preferredVectorWidthChar,
							preferredVectorWidthShort, preferredVectorWidthInt,
							preferredVectorWidthLong,
							preferredVectorWidthFloat,
							preferredVectorWidthDouble));
		
		return sb.toString();
	}

	/**
	 * Returns the value of the device info parameter with the given name
	 * 
	 * @param device
	 *            The device
	 * @param paramName
	 *            The parameter name
	 * @return The value
	 */
	private static int getInt(cl_device_id device, int paramName) {
		return getInts(device, paramName, 1)[0];
	}

	/**
	 * Returns the values of the device info parameter with the given name
	 * 
	 * @param device
	 *            The device
	 * @param paramName
	 *            The parameter name
	 * @param numValues
	 *            The number of values
	 * @return The value
	 */
	private static int[] getInts(cl_device_id device, int paramName,
			int numValues) {
		int values[] = new int[numValues];
		clGetDeviceInfo(device, paramName, Sizeof.cl_int * numValues,
				Pointer.to(values), null);
		return values;
	}

	/**
	 * Returns the value of the device info parameter with the given name
	 * 
	 * @param device
	 *            The device
	 * @param paramName
	 *            The parameter name
	 * @return The value
	 */
	private static long getLong(cl_device_id device, int paramName) {
		return getLongs(device, paramName, 1)[0];
	}

	/**
	 * Returns the values of the device info parameter with the given name
	 * 
	 * @param device
	 *            The device
	 * @param paramName
	 *            The parameter name
	 * @param numValues
	 *            The number of values
	 * @return The value
	 */
	private static long[] getLongs(cl_device_id device, int paramName,
			int numValues) {
		long values[] = new long[numValues];
		clGetDeviceInfo(device, paramName, Sizeof.cl_long * numValues,
				Pointer.to(values), null);
		return values;
	}

	/**
	 * Returns the value of the device info parameter with the given name
	 * 
	 * @param device
	 *            The device
	 * @param paramName
	 *            The parameter name
	 * @return The value
	 */
	private static String getString(cl_device_id device, int paramName) {
		// Obtain the length of the string that will be queried
		long size[] = new long[1];
		clGetDeviceInfo(device, paramName, 0, null, size);

		// Create a buffer of the appropriate size and fill it with the info
		byte buffer[] = new byte[(int) size[0]];
		clGetDeviceInfo(device, paramName, buffer.length, Pointer.to(buffer),
				null);

		// Create a string from the buffer (excluding the trailing \0 byte)
		return new String(buffer, 0, buffer.length - 1);
	}

	/**
	 * Returns the value of the platform info parameter with the given name
	 * 
	 * @param platform
	 *            The platform
	 * @param paramName
	 *            The parameter name
	 * @return The value
	 */
	private static String getString(cl_platform_id platform, int paramName) {
		// Obtain the length of the string that will be queried
		long size[] = new long[1];
		clGetPlatformInfo(platform, paramName, 0, null, size);

		// Create a buffer of the appropriate size and fill it with the info
		byte buffer[] = new byte[(int) size[0]];
		clGetPlatformInfo(platform, paramName, buffer.length,
				Pointer.to(buffer), null);

		// Create a string from the buffer (excluding the trailing \0 byte)
		return new String(buffer, 0, buffer.length - 1);
	}

	/**
	 * Returns the value of the device info parameter with the given name
	 * 
	 * @param device
	 *            The device
	 * @param paramName
	 *            The parameter name
	 * @return The value
	 */
	private static long getSize(cl_device_id device, int paramName) {
		return getSizes(device, paramName, 1)[0];
	}

	/**
	 * Returns the values of the device info parameter with the given name
	 * 
	 * @param device
	 *            The device
	 * @param paramName
	 *            The parameter name
	 * @param numValues
	 *            The number of values
	 * @return The value
	 */
	private static long[] getSizes(cl_device_id device, int paramName,
			int numValues) {
		// The size of the returned data has to depend on
		// the size of a size_t, which is handled here
		ByteBuffer buffer = ByteBuffer.allocate(numValues * Sizeof.size_t)
				.order(ByteOrder.nativeOrder());
		clGetDeviceInfo(device, paramName, Sizeof.size_t * numValues,
				Pointer.to(buffer), null);
		long values[] = new long[numValues];
		if (Sizeof.size_t == 4) {
			for (int i = 0; i < numValues; i++) {
				values[i] = buffer.getInt(i * Sizeof.size_t);
			}
		} else {
			for (int i = 0; i < numValues; i++) {
				values[i] = buffer.getLong(i * Sizeof.size_t);
			}
		}
		return values;
	}

	public static long getCL_DEVICE_MAX_WORK_ITEM_SIZES(cl_device_id device) {
		long maxWorkGroupSize = getSize(device, CL_DEVICE_MAX_WORK_GROUP_SIZE);
		return maxWorkGroupSize;
	}

	public static long[] getCL_DEVICE_MAX_WORK_GROUP_SIZE(cl_device_id device) {
		long maxWorkItemSizes[] = getSizes(device,
				CL_DEVICE_MAX_WORK_ITEM_SIZES, 3);
		return maxWorkItemSizes;
	}
	
	public static boolean isProfilingEnabled(DeviceRecord dr){
		long queueProperties = getLong(dr.getDeviceID(), CL_DEVICE_QUEUE_PROPERTIES);
		if ((queueProperties & CL_QUEUE_PROFILING_ENABLE) != 0)
			return true;
		return false;
	}
	
	public static long getCL_DEVICE_TYPE(DeviceRecord dr){
	 return getLong(dr.getDeviceID(), CL_DEVICE_TYPE);
	
	}

}
