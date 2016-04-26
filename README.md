# AVMix

AVMix is a module for [Wowza Streaming Engine™ media server software](https://www.wowza.com/products/streaming-engine) allows combining separate audio and video sources into a single stream.

## Prerequisites

Wowza Streaming Engine 4.0.0 or later is required.

## Usage

This module is recommended in place of [ModuleAddAudioTrack](https://www.wowza.com/forums/content.php?590-How-to-add-an-audio-track-to-a-video-only-stream-(ModuleAddAudioTrack)) and provides extra functionality, including the following capabilities:

* Configuration allows multiple streams to be configured separately.
* Any compatible live sources can be used for either video or audio. This feature isn't limited to a single audio source generated from a file.
* If one of the sources goes offline, the other source will continue. When both sources go offline, the output stream will shut down.
* Streams can be updated dynamically by using API methods.
* If source timecodes are synchronized, then the output stream can be set to synchronize; otherwise, the timecodes offset to a common base.
* Output stream can be delayed to allow for data surges in either source. This is ideal if the audio is a shoutcast source that includes a burst when it starts.

## Configuration

Add the following Module Definition to your Application configuration. See [Configure modules](http://www.wowza.com/forums/content.php?625-How-to-get-started-as-a-Wowza-Streaming-Engine-Manager-administrator#configModules) for details.

Name | Description | Fully Qualified Class Name
-----|-------------|---------------------------  
ModuleAVMix | Mix audio and video sources. | com.wowza.wms.plugin.avmix.ModuleAVMix
*Need to confirm class name*

## Properties

Adjust the default settings by adding the following properties to your application. See [Configure properties](http://www.wowza.com/forums/content.php?625-How-to-get-started-as-a-Wowza-Streaming-Engine-Manager-administrator#configProperties) for details.

Path | Name | Type | Value | Notes
-----|------|------|-------|------
Root/Application | avMixNames | String | **outputName: stream1, videoName: myStream, audioName: audioStream | outputName: stream2, videoName: myOtherStream, audioName: audioStream2** | This property is used to configure each of the streams. See [How to configure avMixNames]https://www.wowza.com/forums/content.php?653-How-to-mix-audio-and-video-from-different-live-sources-(ModuleAVMix)#avMixNames for more information (default: **not set**).
Root/Application | avMixSortDelay | Integer | **10000** | This property is used as a milliseconds value that will delay the output stream and smooth out any data surges. **-1** disables completely. Can also be set at a per stream level (default: **10000**).
Root/Application | avMixUseOriginalTimecodes | Boolean | **false** | If both sources have synchronized timecodes, then setting this property to false will force the original timecodes to be used unchanged. *Warning:* unexpected results may occur if the sources aren't synchronized. Can also be set at a per stream level (default: **false**).
Root/Application | avMixDebugLog | Boolean | **true** | Enable extra debug logging (default: **false**).

**How to configure avMixNames**

The **avMixNames** property is a pipe-separated list of comma-separated name: **value pairs** ("name1: value1 | name2: value2) that defines each of the separate stream configurations. The following values can be used:

**outputName:** (required) The name of the output stream that will be used by the players.

**videoName:** (recommended) The name of the source that contains a video track. If this value isn't set, then the output stream won't have any video.

**audioName:** (recommended) The name of the source that contains an audio track. If this value isn't set, then the output won't have any audio.

**sortDelay:** (optional) Will override the avMixSortDelay property above.

**useOriginalTimecodes:** (optional) Will override the **avMixUseOriginalTimecodes** property above.

When the application is started, the configuration is read from the **avMixNames** property and each of the output streams is configured but not started. Starting one of the sources (either audio or video) will trigger the output stream to start. This stream will continue to run as long as one of the sources is running and then it will shut down automatically. If the sort delay is set, then the output startup, playback, and shutdown will be delayed by this amount of time.

JMX or a separate module can be used to adjust the stream configuration after it has been initially loaded. The following public methods are available from within the module.

**addOrUpdateOutputStream**(String **outputName**, String **videoName**, String **audioName**, long **sortDelay**, boolean **useOriginalTimecodes**) - Add a new configuration or update an existing one.

**setVideoSource**(String **outputName**, String **videoName**) - Update the video source for a configuration referenced by **outputName**. If the configuration doesn't exist, then it will be created.

**setVideoSource**(String **outputName**, String **videoName**) - Update the video source for a configuration referenced by **outputName**. If the configuration doesn't exist, then it will be created.

**setAudioSource**(String **outputName**, String **audioName**) - Update the audio source for a configuration referenced by **outputName**. If the configuration doesn't exist, then it will be created.

**removeOutputStream**(String **outputName**) - Shut down and remove a configuration referenced by **outputName**.

**getOutputNames**() - Get an array of currently configured output stream names.

## API Reference

[Wowza Streaming Engine Server-Side API Reference](https://www.wowza.com/resources/WowzaStreamingEngine_ServerSideAPI.pdf)

[How to extend Wowza Streaming Engine using the Wowza IDE](https://www.wowza.com/forums/content.php?759-How-to-extend-Wowza-Streaming-Engine-using-the-Wowza-IDE)

Wowza Media Systems™ provides developers with a platform to create streaming applications and solutions. See [Wowza Developer Tools](https://www.wowza.com/resources/developers) to learn more about our APIs and SDK.

To use the compiled version of this module, see [How to mix audio and video from different live sources (ModuleAVMix)](https://www.wowza.com/forums/content.php?653-How-to-mix-audio-and-video-from-different-live-sources-(ModuleAVMix)#avMixNames).

## Contact

[Wowza Media Systems, LLC](https://www.wowza.com/contact)

## License

This code is distributed under the [Wowza Public License](https://github.com/WowzaMediaSystems/[jar-file-name]/blob/master/LICENSE.txt).
