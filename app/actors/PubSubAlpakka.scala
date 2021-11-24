package actors

import java.time.Instant
import java.util.Base64
import akka.actor.{ActorSystem, Cancellable}
import akka.stream.{ActorMaterializer, RestartSettings}
import akka.stream.alpakka.googlecloud.pubsub._
import akka.stream.alpakka.googlecloud.pubsub.scaladsl.GooglePubSub
import akka.stream.scaladsl.{Flow, FlowWithContext, RestartFlow, Sink, Source}
import akka.{Done, NotUsed}

import java.nio.charset.StandardCharsets
import javax.inject.{Inject, Singleton}
import scala.collection.immutable.Seq
import scala.concurrent.duration._
import scala.concurrent.{Future, Promise}

//https://doc.akka.io/docs/alpakka/current/google-cloud-pub-sub.html#
@Singleton
class PubSubAlpakka @Inject()(
  implicit val system: ActorSystem
) {

  val config = PubSubConfig()

  def publishMessage(topic: String, message: String): Future[Seq[Seq[String]]] = {
    val publishMessage =
      PublishMessage(new String(Base64.getEncoder.encode(message.getBytes)))

    val publishRequest = PublishRequest(Seq(publishMessage))
    val source: Source[PublishRequest, NotUsed] = Source.single(publishRequest)

    val publishFlow: Flow[PublishRequest, Seq[String], NotUsed] =
      GooglePubSub.publish(topic, config)

    val publishedMessageIds: Future[Seq[Seq[String]]] = source.via(publishFlow).runWith(Sink.seq)
    publishedMessageIds
  }

  def listenToMessages(subscription: String): Unit = {
    val subscriptionSource: Source[ReceivedMessage, Cancellable] =
      GooglePubSub.subscribe(subscription, config)

    val ackSink: Sink[AcknowledgeRequest, Future[Done]] =
      GooglePubSub.acknowledge(subscription, config)

    subscriptionSource
      .map { message =>
        val decodedMsg = message.message.data.map{ msg =>
          val decoded = Base64.getDecoder.decode(msg)
          new String(decoded, StandardCharsets.UTF_8)
        }.getOrElse("")
        println(
          s"""
             |>>> Message received: ${message.message.messageId}
             |    >>>> Data: ${decodedMsg}
             |    >>>> Attributes: ${message.message.attributes}
             |    >>>> Publish Time: ${message.message.publishTime}
             |>>> ACK Id: ${message.ackId}
             |""".stripMargin)
        message.ackId
      }
      .groupedWithin(3, 60.seconds)
      .map(AcknowledgeRequest.apply)
      .to(ackSink)
      .run()

  }
}