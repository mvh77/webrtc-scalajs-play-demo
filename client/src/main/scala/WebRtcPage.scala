import io.udash._
import org.scalajs.dom.experimental.mediastream.{MediaDeviceKind, MediaDevicesInfo, MediaStream, MediaStreamConstraints}
import org.scalajs.dom.experimental.webrtc._
import org.scalajs.dom.html.Div
import org.scalajs.dom.raw.{MessageEvent, MouseEvent}
import org.scalajs.dom.{WebSocket, window}
import org.scalajs.jquery.jQuery
import shared.WebRtcProtocol._
import upickle.default._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.scalajs.js
import scala.scalajs.js.{Array, Dynamic, JSApp}
import scala.util.{Failure, Success}
import scalatags.JsDom.all._

object Conf {
  val constraints = MediaStreamConstraints(video = true, audio = true)
  val mediaDevices: MediaDevices = window.navigator.asInstanceOf[Dynamic].mediaDevices.asInstanceOf[MediaDevices]
  val audioOutputId: Future[Array[MediaDevicesInfo]] = mediaDevices.enumerateDevices().toFuture.map(_.filter(_.kind == MediaDeviceKind.audiooutput))
}

object WebRtcPage extends JSApp {

  private val users = SeqProperty[User]
  private var idToVideo = Map[User, VideoElement]()
  private var connections: Map[User, Connection] = Map()
  private var handleMessages: PartialFunction[WebRtcMessage, Unit] = {
    case msg => println(s"unhandled message $msg")
  }

  def main(): Unit = {
    println("initializing page...")
    val ws = new WebSocket(jQuery("body").data("ws-url").asInstanceOf[String])
    handleMessages = handleWsConnect(ws)
    ws.onmessage = (me: MessageEvent) => handleIncomingMessages(me, ws)
  }

  private def handleIncomingMessages(me: MessageEvent, ws: WebSocket): Unit = {
    val wsMsg = read[WebRtcMessage](me.data.toString)
    handleMessages(wsMsg)
  }

  private def handleWsConnect(ws: WebSocket): PartialFunction[WebRtcMessage, Unit] = {
    case ConnectSuccess(user) =>
      val rs = new RoomSelection(
        (room, nick, rs, ms) => {
          handleMessages = handleWebRtcNego(User(user, nick), ws, rs, ms)
          ws.send(write(JoinRoom(User(user, nick), room)))
        },
        (room, nick) => ws.send(write(LeaveRoom(User(user, nick), room)))
      )
      initView(ws, rs)
  }

  private def initView(ws: WebSocket, rs: RoomSelection): Unit = {
    val main = div(
      rs.output,
      produce(users) { (ids: Seq[User]) =>
        val cols = if (ids.size == 1) 1 else if(ids.size % 2 == 0 && ids.size < 6) 2 else 3
        ids.grouped(cols).map { row =>
          div(cls := "row",
            row.map(id => div(cls := s"col-md-${12 / cols}", idToVideo(id).content).render)
          ).render
        }.toSeq
      })
    jQuery("#root").append(main.render)
  }

  private def handleWebRtcNego(user: User, ws: WebSocket, rs: RoomSelection, ls: MediaStream): PartialFunction[WebRtcMessage, Unit] = {
    case JoinSuccess(others) =>
      println(s"got join success with others $others")
      idToVideo = idToVideo + (user -> new VideoElement(ls, user, true))
      users.append(user)
      rs.login()
      others.foreach { target =>
        addRemoteVideo(user, target, ws, ls).createOffer()
      }
    case LeaveSuccess(leaver) =>
      if(leaver == user) {
        rs.logout()
        idToVideo = Map()
        users.set(Seq())
      }
      else {
        idToVideo = idToVideo - leaver
        users.remove(leaver)
      }
    case offer: Offer =>
      connections.get(offer.source).foreach(_.close())
      addRemoteVideo(user, offer.source, ws, ls).receiveOffer(offer, Conf.constraints)
    case answer: Answer =>
      connections.get(answer.source).foreach(_.receiveAnswer(answer))
    case ice: IceCandidate =>
      connections.get(ice.source).foreach(_.receiveIceCandidate(ice))
  }

  private def addRemoteVideo(source: User, target: User, ws:  WebSocket, ls: MediaStream): Connection = {
    val connection = new Connection(source, target, ls, ws, stream => {
      idToVideo = idToVideo + (target -> new VideoElement(stream, target, false))
      users.append(target)
    })
    connections = connections + (target -> connection)
    connection
  }
}

private class RoomSelection(onJoin: (String, String, RoomSelection, MediaStream) => Unit, onLeave: (String, String) => Unit) {

  private val loggedProp = Property[Boolean]
  private val roomProp = Property[String]("")
  private val nickProp = Property[String]("")

  private val nickInput = TextInput.debounced(nickProp)(`type` := "text", cls := "form-control", placeholder := "Nickname").render
  private val roomInput = TextInput.debounced(roomProp)(`type` := "text", cls := "form-control", placeholder := "Room to join").render
  private val logoutButton = button(cls := "btn btn-default", `type` := "button", i(cls := "fa fa-sign-out", color.red), disabled := true).render

  private val loginButton = button(cls := "btn btn-default", `type` := "button", i(cls := "fa fa-sign-in", color.green), disabled := true, onclick := {() =>
    Conf.mediaDevices.getUserMedia(Conf.constraints).toFuture.onComplete {
      case Success(stream) =>
        onJoin(roomProp.get, nickProp.get, this, stream)
        println("successfully retrieved stream")
        logoutButton.onclick = {(me: MouseEvent) =>
          stream.getVideoTracks().foreach(_.stop())
          stream.getAudioTracks().foreach(_.stop())
          onLeave(roomProp.get, nickProp.get)
      }
      case Failure(ex) => println(s"error getting user media ${ex.toString}")
    }
  }).render

  loggedProp
    .combine(roomProp)((b, text) => b || text.isEmpty)
    .combine(nickProp)((b, text) => b || text.isEmpty)
    .listen(b => loginButton.disabled = b)

  loggedProp.listen{ b =>
    roomInput.disabled = b
    logoutButton.disabled = !b
  }

  lazy val output: Div = div(cls := "row",
    div(cls := "col-md-2",
      div(cls := "form-group", nickInput)
    ),
    div(cls := "col-md-4",
      div(cls := "input-group", roomInput, div(cls := "input-group-btn", loginButton, logoutButton))
    )
  ).render

  def login(): Unit = loggedProp.set(true)
  def logout(): Unit = loggedProp.set(false)

}

private class VideoElement(val stream: MediaStream, user: User, self: Boolean) {

  private val vid = video(cls := "embed-responsive-item").render
  vid.autoplay = true
  vid.muted = self
  vid.asInstanceOf[js.Dynamic].srcObject = stream
  vid.play()
  Conf.audioOutputId.onSuccess{case mdis => mdis.headOption.foreach(id => vid.asInstanceOf[js.Dynamic].setSinkId(id))}

  private val unmuted = Property[Option[Boolean]](stream.getAudioTracks().headOption.map(_.enabled))
  unmuted.listen(state => stream.getAudioTracks().headOption.foreach(_.enabled = state.get))
  unmuted.set(unmuted.get.map(_ => false))

  private val muteButton = button(cls := "btn btn-default", `type` := "button",
    onclick := {() => unmuted.set(unmuted.get.map(!_))},
    produce(unmuted) {
      case None => span(cls := "glyphicon glyphicon-volume-up").render
      case Some(true) if self => i(cls := "fa fa-microphone", color.green).render
      case Some(true) if !self => i(cls := "fa fa-volume-up", color.green).render
      case Some(false) if self => i(cls := "fa fa-microphone-slash", color.red).render
      case Some(false) if !self => i(cls := "fa fa-volume-off", color.red).render
    }
  )

  private val videoOn = Property[Boolean](true)
  videoOn.listen(state => stream.getVideoTracks().headOption.foreach(_.enabled = state))

  private val videoOnButton = button(cls := "btn btn-default", `type` := "button",
    onclick := {() => videoOn.set(!videoOn.get)},
    produce(videoOn){vo =>
      if(vo) span(cls := "fa fa-eye", color.green).render
      else span(cls := "fa fa-eye-slash", color.red).render
    }
  )

  def content: Div = {
    val d = div(
      div(cls := "embed-responsive embed-responsive-4by3", vid),
      div(cls := "caption",
        h3(user.nickname),
        p(div(cls := "btn-group", muteButton, videoOnButton))
      )
    ).render
    vid.play()
    d
  }
}

class Connection(val source: User, target: User, localStream: MediaStream, ws: WebSocket, onStream: MediaStream => Unit) {

  private val pc: RTCPeerConnection = newRTCPeerConnection().getOrElse(throw new RuntimeException("no rtc peer connection"))

  pc.addStream(localStream)
  pc.onicecandidate = (e: RTCPeerConnectionIceEvent) => {
    if (e != null) {
      val c = e.candidate
      if (c != null && c.candidate != null && c.candidate.nonEmpty) {
        println(s"got peer connection ice event from browser, sending to $target...")
        ws.send(write(IceCandidate(source, target, c.candidate, c.sdpMid, c.sdpMLineIndex)))
      }
    }
  }

  pc.onaddstream = (event: MediaStreamEvent) => {
    println("got media stream event from browser")
    onStream(event.stream)
  }

  def createOffer(): Unit = {
    pc.createOffer().toFuture.onComplete{
      case Success(description) =>
        pc.setLocalDescription(description)
        println(s"created offer, set it as local description, sending it to $target...")
        ws.send(write(Offer(source, target, description.sdp)))
      case Failure(ex) => println(s"error creating offer ${ex.toString}")
    }
  }

  def receiveOffer(offer: Offer, constraints: MediaStreamConstraints): Unit = {
    println(s"received offer from $target, setting as remote description")
    pc.setRemoteDescription(new RTCSessionDescription(RTCSessionDescriptionInit(RTCSdpType.offer, offer.sdp)))
    pc.createAnswer().toFuture.onComplete{
      case Success(description) =>
        pc.setLocalDescription(description)
        println(s"created answer, set it as local description, sending it to $target...")
        ws.send(write(Answer(source, target, description.sdp)))
      case Failure(ex) => println(s"error creating answer ${ex.toString}")
    }
  }

  def receiveAnswer(answer: Answer): Unit = {
    println(s"received answer from $target, setting as remote description")
    pc.setRemoteDescription(new RTCSessionDescription(RTCSessionDescriptionInit(RTCSdpType.answer, answer.sdp)))
  }

  def receiveIceCandidate(c: IceCandidate): Unit = {
    println(s"received ice candidate from $target, adding it to peer connection")
    pc.addIceCandidate(new RTCIceCandidate(RTCIceCandidateInit(c.candidate, c.sdpMid, c.sdpMLineIndex)))
  }

  def close(): Unit = pc.close()

  private def newRTCPeerConnection(configuration: js.UndefOr[RTCConfiguration] = js.undefined): Option[RTCPeerConnection] = {
    Seq("RTCPeerConnection", "webkitRTCPeerConnection")
      .collect{ case v if js.eval(s"typeof $v").asInstanceOf[String] != "undefined" => v }
      .headOption
      .map(v => js.eval(s"new $v($configuration)").asInstanceOf[RTCPeerConnection])
  }

}
