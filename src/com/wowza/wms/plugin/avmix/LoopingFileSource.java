/*
 * This code and all components (c) Copyright 2006 - 2026, Wowza Media Systems, LLC. All rights reserved.
 * This code is licensed pursuant to the Wowza Public License version 1.0, available at www.wowza.com/legal.
 */
package com.wowza.wms.plugin.avmix;

import com.wowza.wms.application.IApplicationInstance;
import com.wowza.wms.logging.WMSLogger;
import com.wowza.wms.logging.WMSLoggerFactory;
import com.wowza.wms.logging.WMSLoggerIDs;
import com.wowza.wms.stream.publish.Playlist;
import com.wowza.wms.stream.publish.Stream;

/**
 * Publishes a media file as a continuously looping simulated live stream within the application instance.
 * Uses a Playlist with repeat enabled so the file loops seamlessly with continuous timecodes.
 *
 * File name format follows Wowza convention, e.g. "mp4:myfile.mp4"
 */
public class LoopingFileSource
{
	public static final String CLASS_NAME = "LoopingFileSource";
	public static final String FILE_EXTENSION = ".mp4";

	private Stream stream;
	private final String streamName;
	private final WMSLogger logger;

	public LoopingFileSource(IApplicationInstance appInstance, String fileName)
	{
		this.streamName = fileName;
		this.logger = WMSLoggerFactory.getLoggerObj(appInstance);

		stream = Stream.createInstance(appInstance, streamName);
		Playlist playlist = new Playlist("filesource");
		playlist.setRepeat(true);
		playlist.addItem(fileName, 0, -1);
		playlist.open(stream);

		logger.info(CLASS_NAME + ": started [" + streamName + "] from file [" + fileName + "]", WMSLoggerIDs.CAT_application, WMSLoggerIDs.EVT_comment);
	}

	public String getStreamName()
	{
		return streamName;
	}

	public Stream getStream()
	{
		return stream;
	}

	public void close()
	{
		if (stream != null)
		{
			stream.close();
			stream = null;
			logger.info(CLASS_NAME + ": closed [" + streamName + "]", WMSLoggerIDs.CAT_application, WMSLoggerIDs.EVT_comment);
		}
	}
}
