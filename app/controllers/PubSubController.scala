package controllers

import actors.PubSubAlpakka
import com.google.api.core.ApiFuture
import com.google.cloud.pubsub.v1.{AckReplyConsumer, MessageReceiver, Publisher, Subscriber, SubscriptionAdminClient, TopicAdminClient}
import com.google.protobuf.ByteString
import com.google.pubsub.v1.{ProjectSubscriptionName, PubsubMessage, PushConfig, Subscription, TopicName}
import models.Model.PubSubModel

import javax.inject._
import play.api.mvc._

import java.util.concurrent.TimeUnit
import scala.concurrent.{ExecutionContext, Future, TimeoutException}

/**
 * This controller creates an `Action` to handle HTTP requests to the
 * application's home page.
 */
@Singleton
class PubSubController @Inject()(
  val controllerComponents: ControllerComponents,
  pubSubAlpakka: PubSubAlpakka
)(protected implicit val ec: ExecutionContext) extends BaseController {

  /**
   * Create an Action to render an HTML page.
   *
   * The configuration in the `routes` file means that this method
   * will be called when the application receives a `GET` request with
   * a path of `/`.
   */

  def ping() = Action { implicit request =>
    Ok("pong")
  }

  /**
   * https://github.com/googleapis/java-pubsub/blob/d0a02a9f320b254a3e4ef60bfff47a6bb3a2427f/samples/snippets/src/main/java/pubsub/CreateTopicExample.java
   *
   * @return
   */
  def createTopic() = Action.async { implicit request: Request[AnyContent] =>
    val json = request.body.asJson.get
    val model = json.as[PubSubModel]
    try {
      val topicAdminClient = TopicAdminClient.create()
      val topicName = TopicName.of(model.projectId.get, model.topicId.get)
      val topic = topicAdminClient.createTopic(topicName)
      Future.successful(Ok(topic.getName))
    } catch {
      case ex: Exception =>
        println(ex)
        Future.successful(BadRequest(ex.getMessage))
    }
  }

  def createPullSubscription() = Action.async { implicit request: Request[AnyContent] =>
    val json = request.body.asJson.get
    val model = json.as[PubSubModel]
    try {
      val subscriptionAdminClient = SubscriptionAdminClient.create()
      val topicName = TopicName.of(
        model.projectId.get,
        model.topicId.get
      )
      val subscriptionName = ProjectSubscriptionName.of(
        model.projectId.get,
        model.subscriptionId.get
      )
      val subscription: Subscription = subscriptionAdminClient.createSubscription(
        subscriptionName,
        topicName,
        PushConfig.getDefaultInstance,
        model.ackDeadlineSeconds.get
      )
      Future.successful(Ok(subscription.getName))
    } catch {
      case ex: Exception =>
        Future.successful(BadRequest(ex.getMessage))
    }
  }

  def createPushSubscriptionTopic() = Action.async { implicit request: Request[AnyContent] =>
    val json = request.body.asJson.get
    val model = json.as[PubSubModel]
    try {
      val subscriptionAdminClient = SubscriptionAdminClient.create()
      val topicName = TopicName.of(
        model.projectId.get,
        model.topicId.get
      )
      val subscriptionName = ProjectSubscriptionName.of(
        model.projectId.get,
        model.subscriptionId.get
      )
      val pushConfig = PushConfig.newBuilder().setPushEndpoint(
        model.pushEndpoint.get
      ).build()

      val subscription = subscriptionAdminClient.createSubscription(
        subscriptionName,
        topicName,
        pushConfig,
        model.ackDeadlineSeconds.get
      )
      Future.successful(Ok(subscription.getName))
    } catch {
      case ex: Exception =>
        Future.successful(BadRequest(ex.getMessage))
    }
  }

  /**
   * https://github.com/googleapis/java-pubsub/blob/d0a02a9f320b254a3e4ef60bfff47a6bb3a2427f/samples/snippets/src/main/java/pubsub/PublisherExample.java
   *
   * @return
   */
  def publishMessage() = Action.async { implicit request: Request[AnyContent] =>
    val json = request.body.asJson.get
    val model = json.as[PubSubModel]
    try {
      val topicName = TopicName.of(
        model.projectId.get,
        model.topicId.get
      )
      // Create a publisher instance with default settings bound to the topic
      val publisher = Publisher.newBuilder(topicName).build()
      val data = ByteString.copyFromUtf8(
        model.message.get
      )
      val pubsubMessage = PubsubMessage.newBuilder().setData(data).build()
      val messageIdFuture: ApiFuture[String] = publisher.publish(pubsubMessage)

      publisher.shutdown()
      publisher.awaitTermination(3, TimeUnit.SECONDS)

      Future.successful(Ok(s"Published message id: ${messageIdFuture.get()}"))
    } catch {
      case ex: Exception =>
        Future.successful(BadRequest(ex.getMessage))
    }
  }

  def receiveMessagesWithDeliveryAttemptsExample() = Action.async { implicit request: Request[AnyContent] =>
    val json = request.body.asJson.get
    val model = json.as[PubSubModel]

    val subscriptionName = ProjectSubscriptionName.of(
      model.projectId.get,
      model.subscriptionId.get
    )
    val receiver: MessageReceiver = new MessageReceiver() {
      override def receiveMessage(message: PubsubMessage, consumer: AckReplyConsumer): Unit = { // Handle incoming message, then ack the received message.
        println("Id: " + message.getMessageId)
        println("Data: " + message.getData.toStringUtf8)
        println("Delivery Attempt: " + Subscriber.getDeliveryAttempt(message))
        consumer.ack()
      }
    }

    var subscriber: Subscriber = null
    try {
      subscriber = Subscriber.newBuilder(subscriptionName, receiver).build()
      subscriber.startAsync().awaitRunning()
      printf("Listening for messages on %s:\n", subscriptionName.toString)
      subscriber.awaitTerminated(30, TimeUnit.SECONDS)
      Future.successful(Ok)
    } catch {
      case timeoutException: TimeoutException =>
        subscriber.stopAsync()
        Future.successful(BadRequest(timeoutException.getMessage))
    }
  }

  def sendMessageAlpakka() = Action.async { implicit request =>
    val json = request.body.asJson.get
    val model = json.as[PubSubModel]

    pubSubAlpakka.publishMessage(
      model.topicId.get,
      model.message.get
    ).map { result =>
      val id: Seq[String] = result.flatten
      Ok(id.head)
    }
  }

  def receiveMessageAlpakka() = Action { implicit request =>
    val json = request.body.asJson.get
    val model = json.as[PubSubModel]
    val future = pubSubAlpakka.listenToMessages(model.subscriptionId.get)

    Ok(s"Listening to messages on ${model.subscriptionId.get}")
  }
}
