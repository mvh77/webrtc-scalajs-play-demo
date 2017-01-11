package actors

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.event.LoggingReceive
import shared.WebRtcProtocol.{Disconnect, JoinRoom, LeaveRoom, WebRtcMessage, _}
import upickle.default._

import scala.collection.immutable.HashMap

class RoomsActor extends Actor with ActorLogging {

  def receive = LoggingReceive {
    case join @ JoinRoom(_, room) =>
      context.child(room).getOrElse {
        context.actorOf(Props(new RoomActor(room)), room)
      }.tell(join, sender)
    case leave @ LeaveRoom(_, room) =>
      context.child(room).foreach(_.tell(leave, sender))
    case disc @ Disconnect(_) =>
      context.children.foreach(_.tell(disc, sender))
    case InRoomMessage(room, msg) =>
      context.child(room).foreach(_ ! msg)
  }
}

case class InRoomMessage(room: String, msg: WebRtcMessage)

class RoomActor(name: String) extends Actor with ActorLogging {

  var users: Map[User, ActorRef] = HashMap()

  override def receive: Receive = {
    case JoinRoom(user, _) =>
      users = users + (user -> sender)
      sender ! write(JoinSuccess(users.keys.filter(_ != user).toSeq))
      log.info(s"user $user joined room $name, room has occupants ${users.keySet}")
    case LeaveRoom(user, _) =>
      removeUser(user)
    case Disconnect(userId) =>
      users.find(_._1.id == userId).map(_._1).foreach(removeUser)
    case o @ Offer(source, target, _) =>
      log.info(s"room $name got offer from $source to $target")
      users.get(target).foreach(_ ! write(o))
    case a @ Answer(source, target, _) =>
      log.info(s"room $name got answer from $source to $target")
      users.get(target).foreach(_ ! write(a))
    case ice @ IceCandidate(source, target, _, _, _) =>
      log.info(s"room $name got ice candidate from $source to $target")
      users.get(target).foreach(_ ! write(ice))
  }

  private def removeUser(user: User): Unit = {
    users.values.foreach(ar => ar ! write(LeaveSuccess(user)))
    users = users - user
    log.info(s"user $user left room $name")
    if(users.isEmpty) {
      log.info(s"user $user last to leave room $name, stopping actor")
      context.stop(self)
    }
  }

}