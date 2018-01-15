/*
 * This code and all components (c) Copyright 2006 - 2018, Wowza Media Systems, LLC. All rights reserved.
 * This code is licensed pursuant to the Wowza Public License version 1.0, available at www.wowza.com/legal.
 */
package com.wowza.wms.plugin.avmix;

public class StreamInfo
{
	private String outputName;
	private String videoName;
	private String audioName;
	private long sortDelay = 10000l;
	private boolean useOriginalTimeCodes = false;

	public String getOutputName()
	{
		return outputName;
	}

	public void setOutputName(String outputName)
	{
		this.outputName = outputName;
	}

	public String getVideoName()
	{
		return videoName;
	}

	public void setVideoName(String videoName)
	{
		this.videoName = videoName;
	}

	public String getAudioName()
	{
		return audioName;
	}

	public void setAudioName(String audioName)
	{
		this.audioName = audioName;
	}

	public long getSortDelay()
	{
		return sortDelay;
	}

	public void setSortDelay(long sortDelay)
	{
		this.sortDelay = sortDelay;
	}

	public boolean isUseOriginalTimeCodes()
	{
		return useOriginalTimeCodes;
	}

	public void setUseOriginalTimeCodes(boolean useOriginalTimeCodes)
	{
		this.useOriginalTimeCodes = useOriginalTimeCodes;
	}
}
