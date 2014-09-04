package PerformanceEvaluation;

import java.util.ArrayList;
import java.util.List;

/**
 * A simple helper class for tracking cpu_events and printing
 * timing information for the execution of the commands that
 * are associated with the events.
 */
public class ExecutionStatisticsCPU
{
    /**
     * A single entry of the ExecutionStatistics
     */
    private static class Entry
    {
        private String name;
        private long duration = 0;
        

        Entry(String name, long duration)
        {
            this.name = name;
            this.duration = duration;
        }
        
        long getDuration()
        {
        	return duration;
        }

        void print()
        {
            System.out.println("Event "+name+": ");
            System.out.println("Time   : "+
                String.format("%8.3f", duration / 1e6)+" ms");
        }
        
        public String toString()
        {
        	StringBuilder x = new StringBuilder("");
        	x.append("Event "+name+" -> ");
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
     * @param duration duration of processing
     */
    public void addEntry(String name, long duration)
    {
        entries.add(new Entry(name, duration));
    }

    /**
     * Removes all entries
     */
    public void clear()
    {
        entries.clear();
    }


    /**
     * Print the statistics
     */
    public void print()
    {
        for (Entry entry : entries)
        {
            entry.print();
        }
    }
    
    public String toString()
    {
    	StringBuilder x = new StringBuilder("");
        for (Entry entry : entries)
        {
            x.append(entry.toString());
        }
        return x.toString();
    }
    
    public String toStringAggregate()
    {
    	StringBuilder x = new StringBuilder("");
    	x.append("CPU Events ( #"+this.entries.size()+" ) -> ");
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
