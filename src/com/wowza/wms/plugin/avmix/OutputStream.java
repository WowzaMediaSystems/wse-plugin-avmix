/*
 * This code and all components (c) Copyright 2006 - 2016, Wowza Media Systems, LLC.  All rights reserved.
 * This code is licensed pursuant to the Wowza Public License version 1.0, available at www.wowza.com/legal.
 */
package com.wowza.wms.plugin.avmix;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Queue;

import com.wowza.util.FLVUtils;
import com.wowza.wms.amf.AMFPacket;
import com.wowza.wms.application.IApplicationInstance;
import com.wowza.wms.logging.WMSLogger;
import com.wowza.wms.logging.WMSLoggerFactory;
import com.wowza.wms.logging.WMSLoggerIDs;
import com.wowza.wms.stream.IMediaStream;
import com.wowza.wms.stream.IMediaStreamMetaDataProvider;
import com.wowza.wms.stream.publish.Publisher;
import com.wowza.wms.vhost.IVHost;

public class OutputStream extends Thread
{
	class PacketComparator implements Comparator<AMFPacket>
	{

		@Override
		public int compare(AMFPacket thisPacket, AMFPacket otherPacket)
		{
			if (thisPacket == otherPacket)
				return 0;
			return thisPacket.getAbsTimecode() > otherPacket.getAbsTimecode() ? 1 : -1;
		}
	}

	public static final String CLASS_NAME = "OutputStream";

	private IApplicationInstance appInstance;
	private WMSLogger logger;
	private IMediaStream audioSource;
	private IMediaStream videoSource;
	private Publisher publisher;
	private Queue<AMFPacket> packets = new PriorityQueue<AMFPacket>(16, new PacketComparator());

	private String outputName;
	private volatile String audioName;
	private volatile String videoName;

	private long startTime = -1;
	private long delayOffset = -1;
	private long audioOffset = -1;
	private long videoOffset = -1;
	private long sortDelay = 10000l;
	private long flushInterval = 250;
	private long audioSeq = -1;
	private long videoSeq = -1;
	private long lastTC = -1;
	private long lastProcessedAudioTC = -1;
	private long lastProcessedVideoTC = -1;
	private long videoSwitchTimecode = -1;
	private long audioSwitchTimecode = -1;

	private boolean useOriginalTimecodes = false;
	private boolean addAudioData = true;
	private boolean addVideoData = true;
	private boolean isFirstAudio = true;
	private boolean isFirstVideo = true;
	private boolean foundFirstAudio = false;
	private boolean foundFirstVideo = false;
	private boolean waitForKeyframe = true;
	private boolean pendingVideoSwitch = false;

	private boolean pendingAudioSwitch = false;
	private boolean debugLog = false;
	private boolean doQuit;
	private boolean running = true;

	public OutputStream(IApplicationInstance appInstance, String outputName, long startTime, long sortDelay, boolean useOriginalTimecodes)
	{
		this.appInstance = appInstance;
		this.outputName = outputName;
		this.startTime = startTime;
		this.sortDelay = sortDelay;
		this.useOriginalTimecodes = useOriginalTimecodes;
		logger = WMSLoggerFactory.getLoggerObj(appInstance);

		debugLog = appInstance.getProperties().getPropertyBoolean("avMixDebugLog", debugLog);
		if (logger.isDebugEnabled())
			debugLog = true;
	}

	@Override
	public void run()
	{
		try
		{
			while (true)
			{
				synchronized(this)
				{
					if (doQuit)
					{
						if (debugLog)
							logger.info(CLASS_NAME + ".run(): " + "[" + appInstance.getContextStr() + "/" + outputName + " doQuit]", WMSLoggerIDs.CAT_application, WMSLoggerIDs.EVT_comment);
						running = false;
						break;
					}
				}

				processVideoSource();
				processAudioSource();
				processOutput();

				synchronized(this)
				{
					long now = System.currentTimeMillis();

					if (packets.isEmpty() && videoSource == null && audioSource == null)
					{
						if (sortDelay >= 0)
						{
							if (useOriginalTimecodes)
							{
								if (now - sortDelay > (lastTC == -1 ? startTime : lastTC + delayOffset))
								{
									if (debugLog)
										logger.info(CLASS_NAME + ".run(): " + "[" + appInstance.getContextStr() + "/" + outputName + " shutdown : " + packets.size() + " : " + (now - sortDelay) + " : " + (lastTC + delayOffset) + " : " + startTime + "]", WMSLoggerIDs.CAT_application,
												WMSLoggerIDs.EVT_comment);
									running = false;
									break;
								}
							}
							else if (now - sortDelay > (lastTC == -1 ? startTime : lastTC))
							{
								if (debugLog)
									logger.info(CLASS_NAME + ".run(): " + "[" + appInstance.getContextStr() + "/" + outputName + " shutdown : " + packets.size() + " : " + (now - sortDelay) + " : " + lastTC + " : " + startTime + "]", WMSLoggerIDs.CAT_application, WMSLoggerIDs.EVT_comment);
								running = false;
								break;
							}
						}
						else
						{
							if (debugLog)
								logger.info(CLASS_NAME + ".run(): " + "[" + appInstance.getContextStr() + "/" + outputName + " shutdown : empty packet list and no sources]", WMSLoggerIDs.CAT_application, WMSLoggerIDs.EVT_comment);
							running = false;
							break;
						}
					}
				}

				try
				{
					Thread.sleep(flushInterval);
				}
				catch (InterruptedException e)
				{
				}

			}
		}
		catch (Exception e)
		{
			logger.error(CLASS_NAME + ".run() Exception: " + e.getMessage(), e);
		}
		finally
		{
			if (publisher != null)
			{
				publisher.unpublish();
				publisher.close();
			}
			publisher = null;
			running = false;
		}
	}

	private void processVideoSource()
	{
		if (debugLog)
			logger.info(CLASS_NAME + ".processVideoSource(): " + "[" + appInstance.getContextStr() + "/" + outputName + "]", WMSLoggerIDs.CAT_application, WMSLoggerIDs.EVT_comment);

		IMediaStream stream = appInstance.getStreams().getStream(videoName);
		if (stream == null)
		{
			videoSource = null;
			if (debugLog)
				logger.info(CLASS_NAME + ".processVideoSource(): " + "[" + appInstance.getContextStr() + "/" + outputName + " video source not running]", WMSLoggerIDs.CAT_application, WMSLoggerIDs.EVT_comment);
			return;
		}

		if (stream != videoSource)
		{
			if (videoSource != null)
				pendingVideoSwitch = true;
			if (pendingVideoSwitch && useOriginalTimecodes && waitForKeyframe)
			{
				AMFPacket newKey = stream.getLastKeyFrame();
				if (videoSwitchTimecode == -1)
				{
					if (newKey != null && lastProcessedVideoTC != -1 && newKey.getAbsTimecode() > lastProcessedVideoTC)
					{
						videoSwitchTimecode = newKey.getAbsTimecode();
						if (debugLog)
							logger.info(CLASS_NAME + ".processVideoSource(): " + "[" + appInstance.getContextStr() + "/" + outputName + " setting switchTimecode " + videoSwitchTimecode + "]", WMSLoggerIDs.CAT_application, WMSLoggerIDs.EVT_comment);
					}
					else
					{
						if (debugLog)
							logger.info(CLASS_NAME + ".processVideoSource(): " + "[" + appInstance.getContextStr() + "/" + outputName + " pending switch waiting for new stream to catch up " + (newKey != null ? newKey.getAbsTimecode() : 0) + ": " + lastProcessedVideoTC + "]",
									WMSLoggerIDs.CAT_application, WMSLoggerIDs.EVT_comment);
						return;
					}
				}
			}
			else
			{
				pendingVideoSwitch = false;
			}

			if (!pendingVideoSwitch)
			{
				videoSource = stream;
				videoOffset = -1;
				videoSeq = -1;
				foundFirstVideo = false;
				isFirstVideo = true;
				if (debugLog)
					logger.info(CLASS_NAME + ".processVideoSource(): " + "[" + appInstance.getContextStr() + "/" + outputName + " video source reset]", WMSLoggerIDs.CAT_application, WMSLoggerIDs.EVT_comment);
			}
		}

		List<AMFPacket> videoPackets = videoSource.getPlayPackets();
		if (videoPackets == null || videoPackets.isEmpty())
		{
			if (debugLog)
				logger.info(CLASS_NAME + ".processVideoSource(): " + "[" + appInstance.getContextStr() + "/" + outputName + " video source no packets]", WMSLoggerIDs.CAT_application, WMSLoggerIDs.EVT_comment);
			return;
		}

		long startSeq = videoPackets.get(0).getSeq();
		int startIdx = (videoSeq == -1 ? 0 : (int)(videoSeq - startSeq + 1));
		if (startIdx < 0)
			startIdx = 0;
		if (startIdx >= videoPackets.size())
			return;

		if (!foundFirstVideo)
		{
			for (int idx = videoPackets.size() - 1; idx >= 0; idx--)
			{
				AMFPacket packet = videoPackets.get(idx);
				if (!FLVUtils.isVideoKeyFrame(packet))
				{
					continue;
				}

				if (videoSwitchTimecode != -1 && packet.getAbsTimecode() != videoSwitchTimecode)
				{
					continue;
				}
				videoSwitchTimecode = -1;
				foundFirstVideo = true;
				startIdx = idx;

				if (useOriginalTimecodes)
				{
					if (delayOffset == -1)
						delayOffset = System.currentTimeMillis() - packet.getAbsTimecode();
				}
				else
				{
					videoOffset = System.currentTimeMillis() - packet.getAbsTimecode();
					if (videoOffset + packet.getAbsTimecode() <= lastProcessedVideoTC)
					{
						videoOffset = (lastProcessedVideoTC + packet.getTimecode()) - packet.getAbsTimecode();
					}
				}

				if (debugLog)
					logger.info(CLASS_NAME + ".processVideoSource(): " + "[" + appInstance.getContextStr() + "/" + outputName + " found video start. " + lastProcessedVideoTC + " : " + System.currentTimeMillis() + " : " + videoOffset + " : " + startIdx + "]", WMSLoggerIDs.CAT_application,
							WMSLoggerIDs.EVT_comment);
				break;
			}

			if (!foundFirstVideo)
				return;
		}

		for (int idx = startIdx; idx < videoPackets.size(); idx++)
		{
			AMFPacket packet = videoPackets.get(idx);
			int type = packet.getType();

			if (type == IVHost.CONTENTTYPE_AUDIO)
				continue;
			if ((type == IVHost.CONTENTTYPE_DATA0 || type == IVHost.CONTENTTYPE_DATA3) && !addVideoData)
				continue;

			if (videoSwitchTimecode != -1 && packet.getAbsTimecode() >= videoSwitchTimecode)
			{
				if (debugLog)
					logger.info(CLASS_NAME + ".processVideoSource(): " + "[" + appInstance.getContextStr() + "/" + outputName + " (packet.getAbsTimecode() >= switchTimecode) " + packet + "]", WMSLoggerIDs.CAT_application, WMSLoggerIDs.EVT_comment);
				videoSource = null;
				pendingVideoSwitch = false;
				break;
			}

			long newTimecode = useOriginalTimecodes ? packet.getAbsTimecode() : videoOffset + packet.getAbsTimecode();
			AMFPacket newPacket = new AMFPacket(type, 0, packet.getData());
			newPacket.setAbsTimecode(newTimecode);
			packets.add(newPacket);
			if (debugLog)
				logger.info(CLASS_NAME + ".processVideoSource(): " + "[" + appInstance.getContextStr() + "/" + outputName + " add video packet. " + packet + " : " + newPacket + "]", WMSLoggerIDs.CAT_application, WMSLoggerIDs.EVT_comment);

			if (isFirstVideo && type == IVHost.CONTENTTYPE_VIDEO)
			{
				AMFPacket configPacket = videoSource.getVideoCodecConfigPacket(packet.getAbsTimecode());
				if (configPacket != null)
				{
					newPacket = new AMFPacket(IVHost.CONTENTTYPE_VIDEO, 0, configPacket.getData());
					newPacket.setAbsTimecode(newTimecode);
					packets.add(newPacket);
					if (debugLog)
						logger.info(CLASS_NAME + ".processVideoSource(): " + "[" + appInstance.getContextStr() + "/" + outputName + " add video config packet. " + configPacket + " : " + newPacket + "]", WMSLoggerIDs.CAT_application, WMSLoggerIDs.EVT_comment);
				}

				if (addVideoData)
				{
					while (true)
					{
						IMediaStreamMetaDataProvider metaDataProvider = videoSource.getMetaDataProvider();
						if (metaDataProvider == null)
							break;

						List<AMFPacket> metaData = new ArrayList<AMFPacket>();

						metaDataProvider.onStreamStart(metaData, packet.getAbsTimecode());

						Iterator<AMFPacket> miter = metaData.iterator();
						while (miter.hasNext())
						{
							AMFPacket metaPacket = miter.next();
							if (metaPacket == null)
								continue;

							if (metaPacket.getSize() <= 0)
								continue;

							if (metaPacket.getData() == null)
								continue;

							newPacket = new AMFPacket(IVHost.CONTENTTYPE_DATA, 0, metaPacket.getData());
							newPacket.setAbsTimecode(newTimecode);
							packets.add(newPacket);
							if (debugLog)
								logger.info(CLASS_NAME + ".processVideoSource(): " + "[" + appInstance.getContextStr() + "/" + outputName + " add video metadata packet. " + metaPacket + " : " + newPacket + "]", WMSLoggerIDs.CAT_application, WMSLoggerIDs.EVT_comment);
						}
						break;
					}

				}
				isFirstVideo = false;
			}
			videoSeq = packet.getSeq();
			lastProcessedVideoTC = newTimecode;
		}

	}

	private void processAudioSource()
	{
		if (debugLog)
			logger.info(CLASS_NAME + ".processAudioSource(): " + "[" + appInstance.getContextStr() + "/" + outputName + "]", WMSLoggerIDs.CAT_application, WMSLoggerIDs.EVT_comment);

		IMediaStream stream = appInstance.getStreams().getStream(audioName);
		if (stream == null)
		{
			audioSource = null;
			if (debugLog)
				logger.info(CLASS_NAME + ".processAudioSource(): " + "[" + appInstance.getContextStr() + "/" + outputName + " audio source not running]", WMSLoggerIDs.CAT_application, WMSLoggerIDs.EVT_comment);
			return;
		}

		if (stream != audioSource)
		{
			if (audioSource != null)
				pendingAudioSwitch = true;
			if (pendingAudioSwitch && useOriginalTimecodes)
			{
				long lastTimecode = stream.getAudioTC();
				if (audioSwitchTimecode == -1)
				{
					if (lastProcessedAudioTC != -1 && lastTimecode > lastProcessedAudioTC)
					{
						audioSwitchTimecode = lastTimecode;
						if (debugLog)
							logger.info(CLASS_NAME + ".processAudioSource(): " + "[" + appInstance.getContextStr() + "/" + outputName + " setting switchTimecode " + audioSwitchTimecode + "]", WMSLoggerIDs.CAT_application, WMSLoggerIDs.EVT_comment);
					}
					else
					{
						if (debugLog)
							logger.info(CLASS_NAME + ".processAudioSource(): " + "[" + appInstance.getContextStr() + "/" + outputName + " pending switch waiting for new stream to catch up " + lastTimecode + ": " + lastProcessedAudioTC + "]", WMSLoggerIDs.CAT_application, WMSLoggerIDs.EVT_comment);
						return;
					}
				}
			}
			else
			{
				pendingAudioSwitch = false;
			}

			if (!pendingAudioSwitch)
			{
				audioSource = stream;
				audioOffset = -1;
				audioSeq = -1;
				foundFirstAudio = false;
				isFirstAudio = true;
			}
		}

		List<AMFPacket> audioPackets = audioSource.getPlayPackets();
		if (audioPackets == null || audioPackets.isEmpty())
			return;

		long startSeq = audioPackets.get(0).getSeq();
		int startIdx = (audioSeq == -1 ? 0 : (int)(audioSeq - startSeq + 1));
		if (startIdx < 0)
			startIdx = 0;
		if (startIdx >= audioPackets.size())
			return;

		if (!foundFirstAudio)
		{
			for (int idx = audioPackets.size() - 1; idx >= 0; idx--)
			{
				AMFPacket packet = audioPackets.get(idx);
				if (!packet.isAudio())
				{
					continue;
				}

				if (audioSwitchTimecode != -1 && packet.getAbsTimecode() != audioSwitchTimecode)
				{
					continue;
				}

				audioSwitchTimecode = -1;
				foundFirstAudio = true;
				startIdx = idx;

				if (useOriginalTimecodes)
				{
					if (delayOffset == -1)
						delayOffset = System.currentTimeMillis() - packet.getAbsTimecode();
				}
				else
				{
					audioOffset = System.currentTimeMillis() - packet.getAbsTimecode();

					if (audioOffset + packet.getAbsTimecode() <= lastProcessedAudioTC)
					{
						audioOffset = (lastProcessedAudioTC + packet.getTimecode()) - packet.getAbsTimecode();
					}
				}

				if (debugLog)
					logger.info(CLASS_NAME + ".processAudioSource(): " + "[" + appInstance.getContextStr() + "/" + outputName + " found audio start. " + audioOffset + " : " + lastProcessedAudioTC + " : " + System.currentTimeMillis() + " : " + packet.getAbsTimecode() + "]",
							WMSLoggerIDs.CAT_application, WMSLoggerIDs.EVT_comment);
				break;
			}

			if (!foundFirstAudio)
				return;
		}

		for (int idx = startIdx; idx < audioPackets.size(); idx++)
		{
			AMFPacket packet = audioPackets.get(idx);
			int type = packet.getType();

			if (type == IVHost.CONTENTTYPE_VIDEO)
				continue;
			if ((type == IVHost.CONTENTTYPE_DATA0 || type == IVHost.CONTENTTYPE_DATA3) && !addAudioData)
				continue;

			if (audioSwitchTimecode != -1 && packet.getAbsTimecode() >= audioSwitchTimecode)
			{
				if (debugLog)
					logger.info(CLASS_NAME + ".processAudioSource(): " + "[" + appInstance.getContextStr() + "/" + outputName + " (packet.getAbsTimecode() >= switchTimecode) " + packet + "]", WMSLoggerIDs.CAT_application, WMSLoggerIDs.EVT_comment);
				audioSource = null;
				pendingAudioSwitch = false;
				break;
			}

			long newTimecode = useOriginalTimecodes ? packet.getAbsTimecode() : audioOffset + packet.getAbsTimecode();
			AMFPacket newPacket = new AMFPacket(type, 0, packet.getData());
			newPacket.setAbsTimecode(newTimecode);
			packets.add(newPacket);
			if (debugLog)
				logger.info(CLASS_NAME + ".processAudioSource(): " + "[" + appInstance.getContextStr() + "/" + outputName + " add audio packet. " + packet + " : " + newPacket + "]", WMSLoggerIDs.CAT_application, WMSLoggerIDs.EVT_comment);

			if (isFirstAudio && type == IVHost.CONTENTTYPE_AUDIO)
			{
				AMFPacket configPacket = audioSource.getAudioCodecConfigPacket(packet.getAbsTimecode());
				if (configPacket != null)
				{
					newPacket = new AMFPacket(IVHost.CONTENTTYPE_AUDIO, 0, configPacket.getData());
					newPacket.setAbsTimecode(newTimecode);
					packets.add(newPacket);
					if (debugLog)
						logger.info(CLASS_NAME + ".processAudioSource(): " + "[" + appInstance.getContextStr() + "/" + outputName + " add audio config packet. " + configPacket + " : " + newPacket + "]", WMSLoggerIDs.CAT_application, WMSLoggerIDs.EVT_comment);
				}

				if (addAudioData)
				{
					while (true)
					{
						IMediaStreamMetaDataProvider metaDataProvider = audioSource.getMetaDataProvider();
						if (metaDataProvider == null)
							break;

						List<AMFPacket> metaData = new ArrayList<AMFPacket>();

						metaDataProvider.onStreamStart(metaData, packet.getAbsTimecode());

						Iterator<AMFPacket> miter = metaData.iterator();
						while (miter.hasNext())
						{
							AMFPacket metaPacket = miter.next();
							if (metaPacket == null)
								continue;

							if (metaPacket.getSize() <= 0)
								continue;

							if (metaPacket.getData() == null)
								continue;

							newPacket = new AMFPacket(IVHost.CONTENTTYPE_DATA, 0, metaPacket.getData());
							newPacket.setAbsTimecode(newTimecode);
							packets.add(newPacket);
							if (debugLog)
								logger.info(CLASS_NAME + ".processAudioSource(): " + "[" + appInstance.getContextStr() + "/" + outputName + " add audio metadata packet. " + metaPacket + " : " + newPacket + "]", WMSLoggerIDs.CAT_application, WMSLoggerIDs.EVT_comment);
						}
						break;
					}

				}
				isFirstAudio = false;
			}
			audioSeq = packet.getSeq();
			lastProcessedAudioTC = newTimecode;
		}
	}

	private void processOutput()
	{
		if (debugLog)
			logger.info(CLASS_NAME + ".processOutput(): " + "[" + appInstance.getContextStr() + "/" + outputName + "]", WMSLoggerIDs.CAT_application, WMSLoggerIDs.EVT_comment);

		long now = System.currentTimeMillis();

		while (true)
		{
			AMFPacket packet = packets.peek();
			if (packet == null)
			{
				if (debugLog)
					logger.info(CLASS_NAME + ".processOutput(): " + "[" + appInstance.getContextStr() + "/" + outputName + " no packets available]", WMSLoggerIDs.CAT_application, WMSLoggerIDs.EVT_comment);
				break;
			}

			if (useOriginalTimecodes)
			{
				if (sortDelay >= 0 && packet.getAbsTimecode() + delayOffset > now - sortDelay)
				{
					if (debugLog)
						logger.info(CLASS_NAME + ".processOutput(): " + "[" + appInstance.getContextStr() + "/" + outputName + " no packets available. (packet.getAbsTimecode() + delayOffset > now - sortDelay) timecode: " + packet.getAbsTimecode() + ", delayOffset: " + delayOffset + ", now: " + now
								+ ", sortDelay: " + sortDelay + "]", WMSLoggerIDs.CAT_application, WMSLoggerIDs.EVT_comment);
					break;
				}
			}
			else if (sortDelay >= 0 && packet.getAbsTimecode() > now - sortDelay)
			{
				if (debugLog)
					logger.info(CLASS_NAME + ".processOutput(): " + "[" + appInstance.getContextStr() + "/" + outputName + " no packets available. (packet.getAbsTimecode() > now - sortDelay) timecode: " + packet.getAbsTimecode() + ", now: " + now + ", sortDelay: " + sortDelay + "]",
							WMSLoggerIDs.CAT_application, WMSLoggerIDs.EVT_comment);
				break;
			}

			long timecode = packet.getAbsTimecode();
			byte[] data = packet.getData();

			if (publisher == null)
			{
				if (!initPublisher())
					return;
			}

			switch (packet.getType())
			{
			case IVHost.CONTENTTYPE_AUDIO:
				publisher.addAudioData(data, timecode);
				if (debugLog)
					logger.info(CLASS_NAME + ".processOutput(): " + "[" + appInstance.getContextStr() + "/" + outputName + " add output audio packet. " + packet + "]", WMSLoggerIDs.CAT_application, WMSLoggerIDs.EVT_comment);
				break;

			case IVHost.CONTENTTYPE_VIDEO:
				publisher.addVideoData(data, timecode);
				if (debugLog)
					logger.info(CLASS_NAME + ".processOutput(): " + "[" + appInstance.getContextStr() + "/" + outputName + " add output video packet. " + packet + "]", WMSLoggerIDs.CAT_application, WMSLoggerIDs.EVT_comment);
				break;

			case IVHost.CONTENTTYPE_DATA0:
			case IVHost.CONTENTTYPE_DATA3:
				publisher.addDataData(data, timecode);
				if (debugLog)
					logger.info(CLASS_NAME + ".processOutput(): " + "[" + appInstance.getContextStr() + "/" + outputName + " add output data packet. " + packet + "]", WMSLoggerIDs.CAT_application, WMSLoggerIDs.EVT_comment);
				break;
			}
			lastTC = packet.getAbsTimecode();
			packets.remove();
		}
	}

	private boolean initPublisher()
	{
		IMediaStream stream = appInstance.getStreams().getStream(outputName);
		if (stream != null)
		{
			logger.error(CLASS_NAME + ".init(): " + "Cannot create Publisher. Stream already exists [" + appInstance.getContextStr() + "/" + outputName + "]", WMSLoggerIDs.CAT_application, WMSLoggerIDs.EVT_comment);
			return false;
		}

		publisher = Publisher.createInstance(appInstance);
		if (publisher != null)
		{
			publisher.publish(outputName);
			stream = publisher.getStream();
			stream.setMergeOnMetadata(appInstance.getProperties().getPropertyBoolean("avMixMergeMetadata", true));
			flushInterval = stream.getProperties().getPropertyLong("flushInterval", appInstance.getProperties().getPropertyLong("avMixflushInterval", flushInterval));
			logger.info(CLASS_NAME + ".init(): " + "Publisher created [" + appInstance.getContextStr() + "/" + outputName + "]", WMSLoggerIDs.CAT_application, WMSLoggerIDs.EVT_comment);
			return true;
		}
		logger.error(CLASS_NAME + ".init(): " + "Cannot create Publisher [" + appInstance.getContextStr() + "/" + outputName + "]", WMSLoggerIDs.CAT_application, WMSLoggerIDs.EVT_comment);
		return false;
	}

	public String getAudioName()
	{
		return audioName;
	}

	public void setAudioName(String audioSourceName)
	{
		audioName = audioSourceName;
	}

	public String getVideoName()
	{
		return videoName;
	}

	public void setVideoName(String videoSourceName)
	{
		videoName = videoSourceName;
	}

	public String getOutputName()
	{
		return outputName;
	}

	public Publisher getPublisher()
	{
		return publisher;
	}

	public long getSortDelay()
	{
		return sortDelay;
	}

	public void setSortDelay(long sortDelay)
	{
		this.sortDelay = sortDelay;
	}

	public long getSleepTime()
	{
		return flushInterval;
	}

	public void setSleepTime(long sleepTime)
	{
		flushInterval = sleepTime;
	}

	public boolean isAddAudioData()
	{
		return addAudioData;
	}

	public void setAddAudioData(boolean addAudioData)
	{
		this.addAudioData = addAudioData;
	}

	public boolean isAddVideoData()
	{
		return addVideoData;
	}

	public void setAddVideoData(boolean addVideoData)
	{
		this.addVideoData = addVideoData;
	}

	public boolean isWaitForKeyframe()
	{
		return waitForKeyframe;
	}

	public void setWaitForKeyframe(boolean waitForKeyframe)
	{
		this.waitForKeyframe = waitForKeyframe;
	}

	public boolean isUseOriginalTimecodes()
	{
		return useOriginalTimecodes;
	}

	public void setUseOriginalTimecodes(boolean useOriginalTimecodes)
	{
		this.useOriginalTimecodes = useOriginalTimecodes;
	}

	public boolean isDebugLog()
	{
		return debugLog;
	}

	public void setDebugLog(boolean debugLog)
	{
		this.debugLog = debugLog;
	}

	public void close()
	{
		synchronized(this)
		{
			doQuit = true;
			interrupt();
		}
	}

	public boolean isRunning()
	{
		synchronized(this)
		{
			return running;
		}
	}
}
