package models

import play.api.libs.json.{Json, OWrites, Reads}

object Model {

  case class PubSubModel(projectId: Option[String], topicId: Option[String], subscriptionId: Option[String], pushEndpoint: Option[String], ackDeadlineSeconds: Option[Int], message: Option[String])
  implicit lazy val pubSubModelReads: Reads[PubSubModel] = Json.reads[PubSubModel]
  implicit lazy val pubSubModelWrites: OWrites[PubSubModel] = Json.writes[PubSubModel]
}
