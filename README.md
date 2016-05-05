# AVMix

AVMix is a module for [Wowza Streaming Engine™ media server software](https://www.wowza.com/products/streaming-engine) that allows combining separate audio and video sources into a single stream.

## Prerequisites

Wowza Streaming Engine 4.0.0 or later is required.

## Usage

This module provides the following functionality:

* Configuration allows multiple streams to be configured separately.
* Any compatible live sources can be used for either video or audio. This feature isn't limited to a single audio source generated from a file.
* If one of the sources goes offline, the other source will continue. When both sources go offline, the output stream will shut down.
* Streams can be updated dynamically by using API methods.
* If source timecodes are synchronized, then the output stream can be set to synchronize; otherwise, the timecodes offset to a common base.
* Output stream can be delayed to allow for data surges in either source. This is ideal if the audio is a shoutcast source that includes a burst when it starts.

## API Reference

[Wowza Streaming Engine Server-Side API Reference](https://www.wowza.com/resources/WowzaStreamingEngine_ServerSideAPI.pdf)

[How to extend Wowza Streaming Engine using the Wowza IDE](https://www.wowza.com/forums/content.php?759-How-to-extend-Wowza-Streaming-Engine-using-the-Wowza-IDE)

Wowza Media Systems™ provides developers with a platform to create streaming applications and solutions. See [Wowza Developer Tools](https://www.wowza.com/resources/developers) to learn more about our APIs and SDK.

To use the compiled version of this module, see [How to mix audio and video from different live sources (ModuleAVMix)](https://www.wowza.com/forums/content.php?653-How-to-mix-audio-and-video-from-different-live-sources-(ModuleAVMix)#avMixNames).

ModuleAVMix is recommended in place of [ModuleAddAudioTrack](https://www.wowza.com/forums/content.php?590-How-to-add-an-audio-track-to-a-video-only-stream-(ModuleAddAudioTrack)) because it provides additional functionality.

## Contact

[Wowza Media Systems, LLC](https://www.wowza.com/contact)

## License

This code is distributed under the [Wowza Public License](https://github.com/WowzaMediaSystems/wse-plugin-avmix/blob/master/LICENSE.txt).
