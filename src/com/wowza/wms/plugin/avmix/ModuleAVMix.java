/*
 * This code and all components (c) Copyright 2006 - 2021, Wowza Media Systems, LLC.  All rights reserved.
 * This code is licensed pursuant to the Wowza Public License version 1.0, available at www.wowza.com/legal.
 */
package com.wowza.wms.plugin.avmix;

import com.wowza.util.StringUtils;
import com.wowza.wms.application.IApplicationInstance;
import com.wowza.wms.logging.WMSLogger;
import com.wowza.wms.logging.WMSLoggerFactory;
import com.wowza.wms.logging.WMSLoggerIDs;
import com.wowza.wms.mediacaster.IMediaCaster;
import com.wowza.wms.mediacaster.MediaCasterNotifyBase;
import com.wowza.wms.module.ModuleBase;
import com.wowza.wms.stream.IMediaStream;
import com.wowza.wms.stream.MediaStreamActionNotifyBase;

public class ModuleAVMix extends ModuleBase
{

	private class StreamListener extends MediaStreamActionNotifyBase
	{
		@Override
		public void onPublish(IMediaStream stream, String streamName, boolean isRecord, boolean isAppend)
		{
			if (appInstance.getMediaCasterStreams().getMediaCaster(streamName) != null)
				return;

			if (mixer != null)
				mixer.addStream(streamName);
		}

		@Override
		public void onUnPublish(IMediaStream stream, String streamName, boolean isRecord, boolean isAppend)
		{
			if (appInstance.getMediaCasterStreams().getMediaCaster(streamName) != null)
				return;

			if (mixer != null)
				mixer.removeStream(streamName);
		}
	}

	private class MediaCasterListener extends MediaCasterNotifyBase
	{
		@Override
		public void onStreamStart(IMediaCaster mediaCaster)
		{
			String streamName = mediaCaster.getStream().getName();

			if (mixer != null)
				mixer.addStream(streamName);
		}

		@Override
		public void onStreamStop(IMediaCaster mediaCaster)
		{
			String streamName = mediaCaster.getStream().getName();

			if (mixer != null)
				mixer.removeStream(streamName);
		}
	}

	public static final String MODULE_NAME = "ModuleAVMix";

	private WMSLogger logger;
	private boolean debugLog = false;
	private IApplicationInstance appInstance;
	private AVMixer mixer;
	private StreamListener streamListener = new StreamListener();
	private MediaCasterListener mediaCasterListener = new MediaCasterListener();

	public void onAppStart(IApplicationInstance appInstance)
	{
		logger = WMSLoggerFactory.getLoggerObj(appInstance);
		this.appInstance = appInstance;
		debugLog = appInstance.getProperties().getPropertyBoolean("avMixDebugLog", debugLog);
		if (logger.isDebugEnabled())
			debugLog = true;

		appInstance.addMediaCasterListener(mediaCasterListener);
		mixer = new AVMixer(appInstance);
		String config = appInstance.getProperties().getPropertyStr("avMixNames");
		if (!StringUtils.isEmpty(config))
		{
			loadConfig(config);
		}
		else
		{
			logger.info(MODULE_NAME + ".onAppStart No AVMixer config set in properties", WMSLoggerIDs.CAT_application, WMSLoggerIDs.EVT_comment);
		}

	}

	private void loadConfig(String config)
	{
		if (!StringUtils.isEmpty(config))
		{
			String[] streamNamesArray = config.split("\\|");
			for (String names : streamNamesArray)
			{
				String outputName = null;
				String videoName = null;
				String audioName = null;
				long sortDelay = appInstance.getProperties().getPropertyLong("avMixSortDelay", 10000l);
				boolean useOriginalTimecodes = appInstance.getProperties().getPropertyBoolean("avMixUseOriginalTimecodes", false);

				String[] namesArray = names.trim().split(",");
				for (String name : namesArray)
				{
					String[] parts = name.trim().split(":");
					if (parts.length == 2)
					{
						if (parts[0].trim().equalsIgnoreCase("outputName"))
							outputName = parts[1].trim();
						if (parts[0].trim().equalsIgnoreCase("videoName"))
							videoName = parts[1].trim();
						if (parts[0].trim().equalsIgnoreCase("audioName"))
							audioName = parts[1].trim();
						try
						{
							if (parts[0].trim().equalsIgnoreCase("sortDelay"))
								sortDelay = Long.parseLong(parts[1].trim());
						}
						catch (NumberFormatException e)
						{
							logger.warn(MODULE_NAME + ".loadConfig invalid valule for sortDelay [" + parts[1].trim() + "]", WMSLoggerIDs.CAT_application, WMSLoggerIDs.EVT_comment);
						}
						if (parts[0].trim().equalsIgnoreCase("useOriginalTimecodes"))
							useOriginalTimecodes = Boolean.parseBoolean(parts[1].trim());
					}
				}
				String err = "";
				String warn = "";
				while (true)
				{
					if (outputName == null)
					{
						err += "Output Name is empty.  Cannot create Output Stream.";
						break;
					}

					if (videoName == null)
					{
						warn = "Video Name not set for Output Stream: " + outputName + " ";
					}
					if (audioName == null)
					{
						warn += "Audio Name not set for Output Stream: " + outputName + " ";
					}
					break;
				}
				if (!err.equals(""))
				{
					logger.error(MODULE_NAME + ".loadConfig " + err + " [" + names + "]", WMSLoggerIDs.CAT_application, WMSLoggerIDs.EVT_comment);
				}
				else
				{
					if (!warn.equals(""))
					{
						logger.warn(MODULE_NAME + ".loadConfig " + warn + " [" + names + "]", WMSLoggerIDs.CAT_application, WMSLoggerIDs.EVT_comment);
					}

					String ret = mixer.addOrUpdateOutputStream(outputName, videoName, audioName, sortDelay, useOriginalTimecodes);
					logger.info(MODULE_NAME + ".loadConfig " + ret + " [" + names + "]", WMSLoggerIDs.CAT_application, WMSLoggerIDs.EVT_comment);
				}
			}
		}
	}

	public void onAppStop(IApplicationInstance appInstance)
	{
		if (mixer != null)
			mixer.close();
		mixer = null;
		appInstance.removeMediaCasterListener(mediaCasterListener);
	}

	public void onStreamCreate(IMediaStream stream)
	{
		stream.addClientListener(streamListener);
	}

	public void onStreamDestroy(IMediaStream stream)
	{
		stream.removeClientListener(streamListener);
	}

	public String addOrUpdateOutputStream(String outputName, String videoName, String audioName, long sortDelay, boolean useOriginalTimecodes)
	{
		return mixer.addOrUpdateOutputStream(outputName, videoName, audioName, sortDelay, useOriginalTimecodes);
	}

	public String setVideoSource(String outputName, String videoName)
	{
		return mixer.setVideoSource(outputName, videoName);
	}

	public String setAudioSource(String outputName, String audioName)
	{
		return mixer.setAudioSource(outputName, audioName);
	}

	public String removeOutputStream(String outputName)
	{
		return mixer.removeOutputStream(outputName);
	}

	public String[] getOutputNames()
	{
		return mixer.getConfiguredOutputStreams().keySet().toArray(new String[0]);
	}
}
