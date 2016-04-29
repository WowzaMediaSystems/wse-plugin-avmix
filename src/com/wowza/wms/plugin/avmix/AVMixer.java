/**
 * This code and all components (c) Copyright 2006 - 2016, Wowza Media Systems, LLC.  All rights reserved.
 * This code is licensed pursuant to the Wowza Public License version 1.0, available at www.wowza.com/legal.
 */
package com.wowza.wms.plugin.avmix;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import com.wowza.util.StringUtils;
import com.wowza.wms.application.IApplicationInstance;
import com.wowza.wms.logging.WMSLogger;
import com.wowza.wms.logging.WMSLoggerFactory;
import com.wowza.wms.logging.WMSLoggerIDs;

public class AVMixer
{

	private static final String CLASS_NAME = "AVMixer";

	private WMSLogger logger;
	private IApplicationInstance appInstance;

	private Map<String, OutputStream> outputStreams = new HashMap<String, OutputStream>();
	private Map<String, StreamInfo> streamNames = new HashMap<String, StreamInfo>();
	private Timer pendingShutdownChecker = null;

	private Object lock = new Object();

	private boolean debugLog = false;

	public AVMixer(IApplicationInstance appInstance)
	{
		this.appInstance = appInstance;
		logger = WMSLoggerFactory.getLoggerObj(appInstance);
		debugLog = appInstance.getProperties().getPropertyBoolean("avMixDebugLog", debugLog);
		if (logger.isDebugEnabled())
			debugLog = true;

		pendingShutdownChecker = new Timer(CLASS_NAME + ".PendingShutdownChecker [" + appInstance.getContextStr() + "]");
		pendingShutdownChecker.schedule(new TimerTask()
		{

			@Override
			public void run()
			{
				synchronized(lock)
				{
					Iterator<String> iter = outputStreams.keySet().iterator();
					while (iter.hasNext())
					{
						OutputStream outputStream = outputStreams.get(iter.next());
						if (!outputStream.isRunning())
						{
							if (debugLog)
								logger.info(CLASS_NAME + ".PendingShutdownChecker.run(): " + "[removing output stream " + outputStream.getName() + "]", WMSLoggerIDs.CAT_application, WMSLoggerIDs.EVT_comment);

							iter.remove();
						}
					}
				}
			}
		}, 1000, 1000);

		logger.info(CLASS_NAME + ".constructor: [" + appInstance.getContextStr() + "]", WMSLoggerIDs.CAT_application, WMSLoggerIDs.EVT_comment);
	}

	public String addOrUpdateOutputStream(String outputName, String videoName, String audioName, long sortDelay, boolean useOriginalTimecodes)
	{
		if (StringUtils.isEmpty(outputName))
			return "Output Name not set. ";

		String ret = "";

		if (StringUtils.isEmpty(videoName))
			videoName = null;
		if (StringUtils.isEmpty(audioName))
			audioName = null;

		synchronized(lock)
		{
			StreamInfo streamInfo = streamNames.get(outputName);
			if (streamInfo == null)
			{
				streamInfo = new StreamInfo();
				streamInfo.setOutputName(outputName);
				streamNames.put(outputName, streamInfo);
				ret += "Adding Output StreamInfo: " + outputName + ". ";
			}
			else
			{
				ret += "Updating Output Stream Info: " + outputName + ". ";
			}
			streamInfo.setVideoName(videoName);
			streamInfo.setAudioName(audioName);
			streamInfo.setSortDelay(sortDelay);
			streamInfo.setUseOriginalTimeCodes(useOriginalTimecodes);
		}
		ret += "Video Source set to " + videoName + ". Audio Source set to " + audioName + ". ";

		OutputStream outputStream;
		synchronized(lock)
		{
			outputStream = outputStreams.get(outputName);
		}
		if (outputStream != null && outputStream.isRunning())
		{
			outputStream.setVideoName(videoName);
			outputStream.setAudioName(audioName);
			ret += "Updating Output Stream: " + outputName + ". Video Source set to " + videoName + ". Audio Source set to " + audioName + ". ";
		}
		else
		{
			ret += startOutputStream(outputName, videoName, audioName, sortDelay, useOriginalTimecodes);
		}
		return ret;
	}

	public String setVideoSource(String outputName, String videoName)
	{
		if (StringUtils.isEmpty(outputName))
			return "Output Name not set. ";

		if (StringUtils.isEmpty(videoName))
			videoName = null;

		String ret = "";
		StreamInfo streamInfo;
		synchronized(lock)
		{
			streamInfo = streamNames.get(outputName);
			if (streamInfo == null)
			{
				streamInfo = new StreamInfo();
				streamInfo.setOutputName(outputName);
				streamNames.put(outputName, streamInfo);
			}
			streamInfo.setVideoName(videoName);
		}
		ret = "Updating Output Stream Info: " + outputName + ". Video Source set to " + videoName + ". ";

		OutputStream outputStream;
		synchronized(lock)
		{
			outputStream = outputStreams.get(outputName);
		}

		if (outputStream != null && outputStream.isRunning())
		{
			outputStream.setVideoName(videoName);
		}
		else
		{
			ret += startOutputStream(outputName, videoName, streamInfo.getAudioName(), streamInfo.getSortDelay(), streamInfo.isUseOriginalTimeCodes());
		}
		return ret;
	}

	public String setAudioSource(String outputName, String audioName)
	{
		if (StringUtils.isEmpty(outputName))
			return "Output Name not set. ";

		if (StringUtils.isEmpty(audioName))
			audioName = null;

		String ret = "";
		StreamInfo streamInfo;
		synchronized(lock)
		{
			streamInfo = streamNames.get(outputName);
			if (streamInfo == null)
			{
				streamInfo = new StreamInfo();
				streamInfo.setOutputName(outputName);
				streamNames.put(outputName, streamInfo);
			}
			streamInfo.setAudioName(audioName);
		}
		ret = "Updating Output Stream Info: " + outputName + ". Audio Source set to " + audioName + ". ";

		OutputStream outputStream;
		synchronized(lock)
		{
			outputStream = outputStreams.get(outputName);
		}
		if (outputStream != null && outputStream.isRunning())
		{
			outputStream.setAudioName(audioName);
		}
		else
		{
			ret += startOutputStream(outputName, streamInfo.getVideoName(), audioName, streamInfo.getSortDelay(), streamInfo.isUseOriginalTimeCodes());
		}

		return ret;
	}

	public String removeOutputStream(String outputName)
	{

		if (StringUtils.isEmpty(outputName))
			return "Output Name not set";

		String ret = "";
		synchronized(lock)
		{
			streamNames.remove(outputName);
		}
		ret = "Removed Output Stream Info: " + outputName + ". ";

		ret += stopOutputStream(outputName, true);

		return ret;
	}

	public void addStream(String streamName)
	{
		synchronized(lock)
		{
			for (StreamInfo streamInfo : streamNames.values())
			{
				if (streamInfo.getVideoName().equals(streamName) || streamInfo.getAudioName().equals(streamName))
				{
					String ret = "";
					ret = startOutputStream(streamInfo.getOutputName(), streamInfo.getVideoName(), streamInfo.getAudioName(), streamInfo.getSortDelay(), streamInfo.isUseOriginalTimeCodes());
					logger.info(CLASS_NAME + ".addStream [" + ret + "]");
				}
			}
		}
	}

	public void removeStream(String streamName)
	{
		synchronized(lock)
		{
			for (StreamInfo streamInfo : streamNames.values())
			{
				if (streamInfo.getVideoName().equals(streamName))
				{
					OutputStream outputStream = outputStreams.get(streamInfo.getOutputName());
					if (outputStream != null)
					{
						outputStream.setVideoName(null);
						logger.info(CLASS_NAME + ".removeStream [" + appInstance.getContextStr() + ": Video Source: " + streamName + " removed from " + streamInfo.getOutputName() + "]");
					}
				}
				if (streamInfo.getAudioName().equals(streamName))
				{
					OutputStream outputStream = outputStreams.get(streamInfo.getOutputName());
					if (outputStream != null)
					{
						outputStream.setAudioName(null);
						logger.info(CLASS_NAME + ".removeStream [" + appInstance.getContextStr() + ": Audio Source: " + streamName + " removed from " + streamInfo.getOutputName() + "]");
					}
				}
				if (streamInfo.getOutputName().equals(streamName))
				{
					outputStreams.remove(streamName);
					logger.info(CLASS_NAME + ".removeStream [" + appInstance.getContextStr() + ": OutputStream: " + streamName + " unPublished]");
				}
			}
		}
	}

	private String startOutputStream(String outputName, String videoName, String audioName, long sortDelay, boolean useOriginalTimecodes)
	{
		String ret = "";
		OutputStream outputStream = null;
		synchronized(lock)
		{
			outputStream = outputStreams.get(outputName);
		}
		if (outputStream != null && outputStream.isRunning())
		{
			outputStream.setVideoName(videoName);
			outputStream.setAudioName(audioName);
			ret = "Output Stream Updated: [" + appInstance.getContextStr() + "/" + outputName + " Video Source: " + videoName + ", Audio Source: " + audioName + "]. ";
		}
		else
		{
			synchronized(lock)
			{
				outputStream = new OutputStream(appInstance, outputName, System.currentTimeMillis(), sortDelay, useOriginalTimecodes);
				outputStream.setVideoName(videoName);
				outputStream.setAudioName(audioName);
				outputStream.setName("AVMixOutputStream: [" + appInstance.getContextStr() + "/" + outputName + "]");
				outputStream.setDaemon(true);
				outputStream.start();
				outputStreams.put(outputName, outputStream);
				ret = "Output Stream Started: [" + appInstance.getContextStr() + "/" + outputName + " Video Source: " + videoName + ", Audio Source: " + audioName + "]. ";
			}
		}

		return ret;
	}

	private String stopOutputStream(String outputName, boolean shuttingDown)
	{
		if (StringUtils.isEmpty(outputName))
			return "Output Name not set. ";
		String ret = "";
		OutputStream outputStream;
		synchronized(lock)
		{
			outputStream = outputStreams.get(outputName);
		}
		if (outputStream != null)
		{
			if (shuttingDown)
			{
				outputStream.close();
			}
			else
			{
				outputStream.setVideoName(null);
				outputStream.setAudioName(null);
			}
			ret = "Stopping Output Stream: " + appInstance.getContextStr() + "/" + outputName + ". Delayed: " + !shuttingDown;
		}
		else
		{
			ret = "Output Stream already stopped: " + appInstance.getContextStr() + "/" + outputName + ". ";
		}
		return ret;
	}

	public Map<String, OutputStream> getCurrentOutputStreams()
	{
		synchronized(lock)
		{
			Map<String, OutputStream> streams = new HashMap<String, OutputStream>();
			streams.putAll(outputStreams);
			return streams;
		}
	}

	public Map<String, StreamInfo> getConfiguredOutputStreams()
	{
		synchronized(lock)
		{
			Map<String, StreamInfo> streams = new HashMap<String, StreamInfo>();
			streams.putAll(streamNames);
			return streams;
		}
	}

	public void close()
	{
		List<String> names = new ArrayList<String>();
		synchronized(lock)
		{
			if (pendingShutdownChecker != null)
				pendingShutdownChecker.cancel();
			pendingShutdownChecker = null;

			names.addAll(outputStreams.keySet());
		}
		for (String name : names)
		{
			stopOutputStream(name, true);
		}
		outputStreams.clear();
	}

	@Override
	public void finalize()
	{
		close();
	}

}
