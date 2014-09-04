package PerformanceEvaluation;

import java.util.ArrayList;
import java.util.List;

import org.jocl.*;

/**
 * A simple helper class for tracking cl_events and printing
 * timing information for the execution of the commands that
 * are associated with the events.
 */
public class ExecutionStatisticsCL
{
    /**
     * A single entry of the ExecutionStatistics
     */
    private static class Entry
    {
        private String name;
        private long submitTime[] = new long[1];
        private long queuedTime[] = new long[1];
        private long startTime[] = new long[1];
        private long endTime[] = new long[1];

        Entry(String name, cl_event event)
        {
            this.name = name;
            CL.clGetEventProfilingInfo(
                event, CL.CL_PROFILING_COMMAND_QUEUED,
                Sizeof.cl_ulong, Pointer.to(queuedTime), null);
            CL.clGetEventProfilingInfo(
                event, CL.CL_PROFILING_COMMAND_SUBMIT,
                Sizeof.cl_ulong, Pointer.to(submitTime), null);
            CL.clGetEventProfilingInfo(
                event, CL.CL_PROFILING_COMMAND_START,
                Sizeof.cl_ulong, Pointer.to(startTime), null);
            CL.clGetEventProfilingInfo(
                event, CL.CL_PROFILING_COMMAND_END,
                Sizeof.cl_ulong, Pointer.to(endTime), null);
        }

        void normalize(long baseTime)
        {
            submitTime[0] -= baseTime;
            queuedTime[0] -= baseTime;
            startTime[0] -= baseTime;
            endTime[0] -= baseTime;
        }

        long getQueuedTime()
        {
            return queuedTime[0];
        }
        
        long getDuration()
        {
        	return endTime[0]-startTime[0];
        }

        void print()
        {
            System.out.println("Event "+name+": ");
            System.out.println("Queued : "+
                String.format("%8.3f", queuedTime[0]/1e6)+" ms");
            System.out.println("Submit : "+
                String.format("%8.3f", submitTime[0]/1e6)+" ms");
            System.out.println("Start  : "+
                String.format("%8.3f", startTime[0]/1e6)+" ms");
            System.out.println("End    : "+
                String.format("%8.3f", endTime[0]/1e6)+" ms");

            long duration = endTime[0]-startTime[0];
            System.out.println("Time   : "+
                String.format("%8.3f", duration / 1e6)+" ms");
        }
        
        public String toString()
        {
        	StringBuilder x = new StringBuilder("");
        	x.append("Event "+name+" -> ");
        	x.append("Queued : "+
                String.format("%8.3f", queuedTime[0]/1e6)+" ms ; ");
        	x.append("Submit : "+
                String.format("%8.3f", submitTime[0]/1e6)+" ms ; ");
        	x.append("Start  : "+
                String.format("%8.3f", startTime[0]/1e6)+" ms ; ");
        	x.append("End    : "+
                String.format("%8.3f", endTime[0]/1e6)+" ms ; ");

            long duration = endTime[0]-startTime[0];
            x.append("Time   : "+
                String.format("%8.3f", duration / 1e6)+" ms ;\n");
            return x.toString();
        }
    }

    /**
     * The list of entries in this instance
     */
    private List<Entry> entries = new ArrayList<Entry>();

    /**
     * Adds the specified entry to this instance
     *
     * @param name A name for the event
     * @param event The event
     */
    public void addEntry(String name, cl_event event)
    {
        entries.add(new Entry(name, event));
    }

    /**
     * Removes all entries
     */
    public void clear()
    {
        entries.clear();
    }

    /**
     * Normalize the entries, so that the times are relative
     * to the time when the first event was queued
     */
    private void normalize()
    {
        long minQueuedTime = Long.MAX_VALUE;
        for (Entry entry : entries)
        {
            minQueuedTime = Math.min(minQueuedTime, entry.getQueuedTime());
        }
        for (Entry entry : entries)
        {
            entry.normalize(minQueuedTime);
        }
    }

    /**
     * Print the statistics
     */
    public void print()
    {
        normalize();
        for (Entry entry : entries)
        {
            entry.print();
        }
    }
    
    public String toString()
    {
    	StringBuilder x = new StringBuilder("");
    	normalize();
        for (Entry entry : entries)
        {
            x.append(entry.toString());
        }
        return x.toString();
    }
    
    public String toStringAggregate()
    {
    	StringBuilder x = new StringBuilder("");
    	normalize();
    	x.append("CL Events ( #"+this.entries.size()+" ) -> ");
    	long duration = 0;
    	for (Entry entry : entries)
        {
        	duration += entry.getDuration();
        }
    	x.append("Time   : "+
                String.format("%8.3f", duration / 1e6)+" ms ;");
        return x.toString();
    }


}
