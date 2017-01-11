import org.scalajs.dom.experimental.mediastream.{MediaDevicesInfo, MediaStream, MediaStreamConstraints}
import org.scalajs.dom.raw.{Event, EventTarget}

import scala.scalajs.js

@js.native
trait MediaDevices extends EventTarget {

  /**
    * The event handler for the devicechange event. This event is
    * delivered to the MediaDevices object when a media input or
    * output device is attached to or removed from the user's computer.
    *
    * MDN
    */
  var ondevicechange: js.Function1[Event, Any] = js.native

  /**
    * Obtains an array of information about the media input and output devices
    * available on the system.
    *
    * MDN
    */
  def enumerateDevices(): js.Promise[js.Array[MediaDevicesInfo]] = js.native

  /**
    * Returns an object conforming to MediaTrackSupportedConstraints indicating
    * which constrainable properties are supported on the MediaStreamTrack
    * interface. See "Capabilities and constraints" in Media Capture and
    * Streams API (Media Streams) to learn more about constraints and how to use them.
    *
    * MDN
    */
  def getSupportedConstraints(): MediaTrackSupportedConstraints = js.native

  /**
    * With the user's permission through a prompt, turns on a camera or
    * screensharing and/or a microphone on the system and provides a
    * MediaStream containing a video track and/or an audio track with
    * the input.
    *
    * MDN
    */
  def getUserMedia(constraints: MediaStreamConstraints): js.Promise[MediaStream] = js.native

}

@js.native
trait MediaTrackSupportedConstraints extends js.Object {
  var width: Boolean = js.native
  var height: Boolean = js.native
  var aspectRatio: Boolean = js.native
  var frameRate: Boolean = js.native
  var facingMode: Boolean = js.native
  var volume: Boolean = js.native
  var sampleRate: Boolean = js.native
  var sampleSize: Boolean = js.native
  var echoCancellation: Boolean = js.native
  var latency: Boolean = js.native
  var channelCount: Boolean = js.native
  var deviceId: Boolean = js.native
  var groupId: Boolean = js.native
}
