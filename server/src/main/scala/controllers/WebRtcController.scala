package controllers

import javax.inject.{Inject, Named, Singleton}

import actors.Create
import akka.NotUsed
import akka.actor.{ActorRef, ActorSystem}
import akka.event.Logging
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.stream.{Materializer, OverflowStrategy}
import akka.util.Timeout
import org.reactivestreams.Publisher
import play.api.mvc.{Action, Controller, RequestHeader, WebSocket}
import shared.WebRtcProtocol.Disconnect

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class WebRtcController @Inject()(@Named("webSocketsActor") webSocketsActor: ActorRef,
                                 @Named("roomsActor") roomsActor: ActorRef)
                                (implicit actorSystem: ActorSystem,
                                 mat: Materializer,
                                 ec: ExecutionContext,
                                 config: play.api.Configuration) extends Controller {

  // Use a direct reference to SLF4J
  private val logger = org.slf4j.LoggerFactory.getLogger("HomeController")

  // Home page that renders template
  def index = Action { implicit request =>
    Ok(views.html.index())
  }

  def ws: WebSocket = WebSocket.acceptOrResult{ request =>
    wsFutureFlow(request).map { flow =>
      Right(flow)
    }.recover {
      case e: Exception =>
        logger.error("Cannot create websocket", e)
        Left(InternalServerError("Cannot create websocket"))
    }
  }

  def wsFutureFlow(request: RequestHeader): Future[Flow[String, String, NotUsed]] = {
    // Creates a source to be materialized as an actor reference.
    val source: Source[String, ActorRef] = {
      // If you want to log on a flow, you have to use a logging adapter.
      // http://doc.akka.io/docs/akka/2.4.4/scala/logging.html#SLF4J
      val logging = Logging(actorSystem.eventStream, logger.getName)

      // Creating a source can be done through various means, but here we want
      // the source exposed as an actor so we can send it messages from other
      // actors.
      Source.actorRef[String](10, OverflowStrategy.dropTail).log("actorRefSource")(logging)
    }

    // Creates a sink to be materialized as a publisher.  Fanout is false as we only want
    // a single subscriber here.
    val sink: Sink[String, Publisher[String]] = Sink.asPublisher(fanout = false)

    // Connect the source and sink into a flow, telling it to keep the materialized values,
    // and then kicks the flow into existence.
    val (proxyActor, publisher) = source.toMat(sink)(Keep.both).run()

    val webSocketActorFuture = createWsActor(request.id.toString, proxyActor)

    webSocketActorFuture.map { wsActor =>
      // source is what comes in: browser ws events -> play -> publisher -> userActor
      // sink is what comes out:  userActor -> websocketOut -> play -> browser ws events
      val flow: Flow[String, String, NotUsed] = {
        val sink = Sink.actorRef(wsActor, akka.actor.Status.Success(()))
        val source = Source.fromPublisher(publisher)
        Flow.fromSinkAndSource(sink, source)
      }

      // Unhook the user actor when the websocket flow terminates
      // http://doc.akka.io/docs/akka/current/scala/stream/stages-overview.html#watchTermination
      flow.watchTermination() { (_, termination) =>
        termination.foreach { _ =>
          logger.info(s"Terminating actor $wsActor")
          roomsActor.tell(Disconnect(request.id.toString), proxyActor)
          actorSystem.stop(wsActor)
        }
        NotUsed
      }
    }
  }

  def createWsActor(name: String, responseTargetActor: ActorRef): Future[ActorRef] = {
    // Use guice assisted injection to instantiate and configure the child actor.
    import akka.pattern.ask
    import scala.concurrent.duration._

    val wsActorFuture = {
      implicit val timeout = Timeout(100.millis)
      (webSocketsActor ? Create(name, responseTargetActor)).mapTo[ActorRef]
    }
    wsActorFuture
  }
}