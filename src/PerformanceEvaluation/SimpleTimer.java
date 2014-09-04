
package PerformanceEvaluation;

public class SimpleTimer {
	/* static environment */
	private static int progressive = 1;
	
	/* instance environment */
	private long t;
	private boolean started;
	private String timerName;
	private long timeSpent;
	
	/* CTOR */
	public SimpleTimer(String name) {
		timerName = name;
		started = false;
		timeSpent = 0;
	}
	
	/* CTOR */
	public SimpleTimer() {
		this("timer" + progressive++);
	}
	
	public void reset() {
		if(started)	{
			throw new RuntimeException("Cannot reset timer " + timerName + " while active.");
		} else {
			timeSpent = 0;
		}
	}
	
	public void start() {
		if(started) {
			throw new RuntimeException("The timer " + timerName + " has been already active.");
		} else {
			started = true;
			t = System.currentTimeMillis();
		}
	}
	
	public void stop() {
		if(started) {
			timeSpent += System.currentTimeMillis() - t;
			started = false;
		} else {
			System.err.println("The timer " + timerName + " is already off");
		}
	}
	
	public long getTimeCount() {
		if(started) {
			throw new RuntimeException("Timer " + timerName + " can't be observed while active.");
		} else {
			return timeSpent;
		}
	}
	
	public long getTimeCountAndReset() {
		long ret = getTimeCount();
		reset();
		return ret;
	}
	
	public String getTimerName() {
		return timerName;
	}
}
