package actors

import javax.inject.{Inject, Named}

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.event.LoggingReceive
import shared.WebRtcProtocol.{ConnectSuccess, JoinRoom, LeaveRoom, WebRtcMessage}
import upickle.default.{read, write}

case class Create(user: String, responseTargetActor: ActorRef)

class WebSocketsActor @Inject() (@Named("roomsActor") roomsActor: ActorRef) extends Actor with ActorLogging {

  def receive = LoggingReceive {
    case Create(user, responseTargetActor) =>
      log.info(s"creating new websocket actor for user $user")
      // get or create the StockActor for the symbol and forward this message
      sender ! context.child(user).getOrElse {
        context.actorOf(Props(new WebSocketActor(user, responseTargetActor, roomsActor)), user)
      }
  }
}

class WebSocketActor (user: String, responseTargetActor: ActorRef, roomsActor: ActorRef) extends Actor with ActorLogging {

  override def preStart(): Unit = {
    responseTargetActor ! write(ConnectSuccess(user))
  }

  var currentRoom = ""

  override def receive: Receive = {
    case s: String => read[WebRtcMessage](s) match {
      case join @ JoinRoom(_, room) =>
        log.info(s"websocket for user $user got join message, forwarding to room $room")
        currentRoom = room
        roomsActor.tell(join, responseTargetActor)
      case leave @ LeaveRoom(_, room) =>
        log.info(s"websocket for user $user got leave message, forwarding to room $room")
        currentRoom = ""
        roomsActor.tell(leave, responseTargetActor)
      case rest if currentRoom.nonEmpty =>
        log.info(s"websocket for user $user got msg $rest")
        roomsActor.tell(InRoomMessage(currentRoom, rest), responseTargetActor)
    }
  }

}
