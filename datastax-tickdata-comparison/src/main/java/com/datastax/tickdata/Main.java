package com.datastax.tickdata;

import java.text.DecimalFormat;
import java.util.ArrayList;
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
import com.datastax.tickdata.model.TickData;
import com.datastax.timeseries.model.TimeSeries;

public class Main {
	private static Logger logger = LoggerFactory.getLogger(Main.class);
	private AtomicLong binaryTotal = new AtomicLong(0);
	private AtomicLong tickTotal = new AtomicLong(0);

	private String pattern = "#,###,###.###";
	private DecimalFormat decimalFormat = new DecimalFormat(pattern);

	public Main() {

		String contactPointsStr = PropertyHelper.getProperty("contactPoints", "localhost");
		String noOfThreadsStr = PropertyHelper.getProperty("noOfThreads", "1");
		String noOfDaysStr = PropertyHelper.getProperty("noOfDays", "50");
		int noOfDays = Integer.parseInt(noOfDaysStr);
		DateTime startTime = new DateTime().minusDays(noOfDays - 1);

		logger.info("StartTime : " + startTime);

		TickDataBinaryDao binaryDao = new TickDataBinaryDao(contactPointsStr.split(","));
		TickDataDao dao = new TickDataDao(contactPointsStr.split(","));

		int noOfThreads = Integer.parseInt(noOfThreadsStr);
		// Create shared queue
		BlockingQueue<TimeSeries> queue = new ArrayBlockingQueue<TimeSeries>(100);

		// Executor for Threads
		ExecutorService executor = Executors.newFixedThreadPool(noOfThreads);
		Timer timer = new Timer();
		timer.start();


		// Load the symbols
		DataLoader dataLoader = new DataLoader();
		List<String> exchangeSymbols = dataLoader.getExchangeData().subList(0, 10);

		logger.info("No of symbols : " + exchangeSymbols.size());

		// Start the tick generator
		TickGenerator tickGenerator = new TickGenerator(exchangeSymbols, startTime);

		for (int i = 0; i < noOfThreads; i++) {

			executor.execute(new TimeSeriesTickWriter(dao, queue));
		}

		while (tickGenerator.hasNext()) {
			TimeSeries next = tickGenerator.next();

			try {
				queue.put(next);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		timer.end();

		
		while (!queue.isEmpty()){
			sleep(10);
		}
		
		logger.info("Data Loading (" + decimalFormat.format(tickGenerator.getCount()) + " ticks) for tick took "
				+ tickTotal.get() + "ms ("
				+ decimalFormat.format(
						new Double(tickGenerator.getCount() * 1000) / (new Double(tickTotal.get()).doubleValue()))
				+ " a sec)");

		System.exit(0);
	}

	class TimeSeriesTickWriter implements Runnable {

		private TickDataDao dao;
		private BlockingQueue<TimeSeries> queue;

		public TimeSeriesTickWriter(TickDataDao dao, BlockingQueue<TimeSeries> queue) {
			logger.info("Created tick writer");

			this.dao = dao;
			this.queue = queue;
		}

		@Override
		public void run() {
			TimeSeries timeSeriesBinary;

			while (true) {
				timeSeriesBinary = queue.poll();

				if (timeSeriesBinary != null) {
					try {
						List<TickData> tickDataList = createTimeSeriesList(timeSeriesBinary);

						Timer tick = new Timer();
						this.dao.insertTickData(tickDataList);
						tick.end();
						tickTotal.addAndGet(tick.getTimeTakenMillis());

					} catch (Exception e) {
						e.printStackTrace();
					}
				}				
			}
		}

		private List<TickData> createTimeSeriesList(TimeSeries timeSeriesBinary) {
			List<TickData> tickDataList = new ArrayList<TickData>();

			String symbol = timeSeriesBinary.getSymbol();

			long[] dates = timeSeriesBinary.getDates();
			double[] values = timeSeriesBinary.getValues();

			for (int i = 0; i < dates.length; i++) {

				tickDataList.add(new TickData(symbol, values[i], new DateTime(dates[i])));
			}

			logger.info("Writing TickData - " + symbol + " - " + tickDataList.size());

			return tickDataList;
		}
	}

	private void sleep(int seconds) {
		try {
			Thread.sleep(seconds * 1000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		new Main();
	}
}
