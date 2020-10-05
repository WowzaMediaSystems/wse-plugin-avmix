# AVMix
The **ModuleAVMix** module for [Wowza Streaming Engine™ media server software](https://www.wowza.com/products/streaming-engine) enables you to combine separate audio and video sources into a single stream.

This repo includes a [compiled version](/lib/wse-plugin-avmix.jar).

## Prerequisites
Wowza Streaming Engine 4.0.0 or later is required.

## Usage
When the application is started, the **avMixNames** property values are used to configure the output stream. Starting either the audio or video source triggers the output stream to start. The stream continues to run as long as one of the sources is running. The stream will stop automatically when both sources are stopped. If the **sortDelay** property is set, then the output startup, playback, and shutdown are delayed by this amount of time.

This module provides the following functionality:

* Configuration allows multiple streams to be configured separately.
* Any compatible live sources can be used for either video or audio. This feature isn't limited to a single audio source generated from a file.
* If one of the sources goes offline, the other source will continue. When both sources go offline, the output stream will shut down.
* Streams can be updated dynamically by using API methods.
* If source timecodes are synchronized, then the output stream can be set to synchronize; otherwise, the timecodes offset to a common base.
* The output stream can be delayed to compensate for data surges in either source. This is ideal if the audio is a SHOUTcast source that includes a burst when it starts.

## More resources
To use the compiled version of this module, see [Mix audio and video from different live sources with a Wowza Streaming Engine Java module](https://www.wowza.com/docs/how-to-mix-audio-and-video-from-different-live-sources-moduleavmix).

We recommend that you use **ModuleAVMix** in place of [ModuleAddAudioTrack](https://www.wowza.com/docs/how-to-add-an-audio-track-to-a-video-only-stream-moduleaddaudiotrack) because it provides additional functionality.

[Wowza Streaming Engine Server-Side API Reference](https://www.wowza.com/resources/serverapi/)

[How to extend Wowza Streaming Engine using the Wowza IDE](https://www.wowza.com/docs/how-to-extend-wowza-streaming-engine-using-the-wowza-ide)

Wowza Media Systems™ provides developers with a platform to create streaming applications and solutions. See [Wowza Developer Tools](https://www.wowza.com/developer) to learn more about our APIs and SDK.

## Contact
[Wowza Media Systems, LLC](https://www.wowza.com/contact)

## License
This code is distributed under the [Wowza Public License](/LICENSE.txt).
