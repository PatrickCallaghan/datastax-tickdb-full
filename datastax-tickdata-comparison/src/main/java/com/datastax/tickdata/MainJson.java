package com.datastax.tickdata;

import java.text.DecimalFormat;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datastax.demo.utils.PropertyHelper;
import com.datastax.demo.utils.Timer;
import com.datastax.tickdata.engine.TickGenerator;
import com.datastax.timeseries.model.TimeSeries;

public class MainJson {
	private static Logger logger = LoggerFactory.getLogger(MainJson.class);
	private AtomicLong jsonTotal = new AtomicLong(0);
	private AtomicLong tickTotal = new AtomicLong(0);
	
	private String pattern = "#,###,###.###";
	private DecimalFormat decimalFormat = new DecimalFormat(pattern);

	public MainJson() {

		String contactPointsStr = PropertyHelper.getProperty("contactPoints", "localhost");
		String noOfThreadsStr = PropertyHelper.getProperty("noOfThreads", "1");
		String noOfDaysStr = PropertyHelper.getProperty("noOfDays", "50");
		
		int noOfDays = Integer.parseInt(noOfDaysStr);
		DateTime startTime = new DateTime().minusDays(noOfDays - 1);
		
		logger.info("StartTime : " + startTime);
				
		TickDataJsonDao jsonDao = new TickDataJsonDao(contactPointsStr.split(","));
		
		int noOfThreads = Integer.parseInt(noOfThreadsStr);
		//Create shared queue 
		BlockingQueue<TimeSeries> jsonQueue = new ArrayBlockingQueue<TimeSeries>(100);
		
		//Executor for Threads
		ExecutorService jsonExecutor = Executors.newFixedThreadPool(noOfThreads);
		Timer timer = new Timer();
		timer.start();
			
		for (int i = 0; i < noOfThreads; i++) {
			
			jsonExecutor.execute(new TimeSeriesWriter(jsonDao, jsonQueue));
		}
		
		//Load the symbols
		DataLoader dataLoader = new DataLoader ();
		List<String> exchangeSymbols = dataLoader.getExchangeData().subList(0, 10);
		
		logger.info("No of symbols : " + exchangeSymbols.size());
		
		//Start the tick generator
		TickGenerator tickGenerator = new TickGenerator(exchangeSymbols, startTime);
		
		while (tickGenerator.hasNext()){
			TimeSeries next = tickGenerator.next();
			
			try {
				jsonQueue.put(next);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		timer.end();
		
		logger.info("Data Loading (" + decimalFormat.format(tickGenerator.getCount()) + " ticks) for json took " + jsonTotal.get()+ "ms (" + decimalFormat.format(new Double(tickGenerator.getCount()*1000)/(new Double(jsonTotal.get()).doubleValue())) + " a sec)");
		
		System.exit(0);
	}
	
	class TimeSeriesWriter implements Runnable {

		private TickDataJsonDao jsonDao;
		private BlockingQueue<TimeSeries> queue;

		public TimeSeriesWriter(TickDataJsonDao jsonDao, BlockingQueue<TimeSeries> queue) {
			logger.info("Created binary writer");
			
			this.jsonDao = jsonDao;			
			this.queue = queue;			
		}

		@Override
		public void run() {
			TimeSeries timeSeries;
			
			while(true){				
				timeSeries = queue.poll(); 
				
				if (timeSeries!=null){
					try {					
						Timer json = new Timer();						
						this.jsonDao.insertTimeSeries(timeSeries);
						json.end();
						jsonTotal.addAndGet(json.getTimeTakenMillis());
					} catch (Exception e) {
						e.printStackTrace();
					}
				}				
			}				
		}
	}
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		new MainJson();
	}
}
