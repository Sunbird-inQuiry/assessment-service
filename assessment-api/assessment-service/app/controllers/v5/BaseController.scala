package controllers.v5

import akka.actor.ActorRef
import akka.pattern.Patterns
import org.sunbird.common.DateUtils
import org.sunbird.common.dto.{Response, ResponseHandler}
import org.sunbird.common.exception.ResponseCode
import org.sunbird.telemetry.logger.TelemetryManager
import play.api.mvc.{AbstractController, AnyContent, ControllerComponents, Request, Result}
import utils.JavaJsonUtils

import java.util.UUID
import collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}

abstract class BaseController(protected val cc: ControllerComponents)(implicit exec: ExecutionContext) extends AbstractController(cc) {

  def requestBody()(implicit request: Request[AnyContent]) = {
    val body = request.body.asJson.getOrElse("{}").toString
    JavaJsonUtils.deserialize[java.util.Map[String, Object]](body).getOrDefault("request", new java.util.HashMap()).asInstanceOf[java.util.Map[String, Object]]
  }

  def commonHeaders()(implicit request: Request[AnyContent]): java.util.Map[String, Object] = {
    val customHeaders = Map("x-channel-id" -> "channel", "X-Consumer-ID" -> "consumerId", "X-App-Id" -> "appId")
    customHeaders.map(ch => {
      val value = request.headers.get(ch._1)
      if (value.isDefined && !value.isEmpty) {
        collection.mutable.HashMap[String, Object](ch._2 -> value.get).asJava
      } else {
        collection.mutable.HashMap[String, Object]().asJava
      }
    }).reduce((a, b) => {
      a.putAll(b)
      return a
    })
  }

  def getRequest(input: java.util.Map[String, AnyRef], context: java.util.Map[String, AnyRef], operation: String): org.sunbird.common.dto.Request = {
    new org.sunbird.common.dto.Request(context, input, operation, null);
  }

  def getResult(apiId: String, actor: ActorRef, request: org.sunbird.common.dto.Request) : Future[Result] = {
    val future = Patterns.ask(actor, request, 30000) recoverWith {case e: Exception => Future(ResponseHandler.getErrorResponse(e))}
    future.map(f => {
      val result = f.asInstanceOf[Response]
      result.setId(apiId)
      result.setVer("5.0")
      setResponseEnvelope(result)
      val response = JavaJsonUtils.serialize(result);
      val publishApis = List("api.question.publish", "api.questionset.publish")
      if(publishApis.contains(apiId)) {
        val apiName = apiId match {
          case "api.question.publish" => "Question Publish V2 API"
          case "api.questionset.publish" => "QuestionSet Publish V2 API"
        }
        val params = Map("requestId" -> request.getContext().getOrDefault("requestId", "").asInstanceOf[String], "Response Code" -> result.getResponseCode.code()).asJava.asInstanceOf[java.util.Map[String, AnyRef]]
        TelemetryManager.info(s"EXIT:assessment: ${apiName} : Response Provided For Identifier ${request.getContext.getOrDefault("identifier", "").asInstanceOf[String]}.", params)
      }
      result.getResponseCode match {
        case ResponseCode.OK => Ok(response).as("application/json")
        case ResponseCode.CLIENT_ERROR => BadRequest(response).as("application/json")
        case ResponseCode.RESOURCE_NOT_FOUND => NotFound(response).as("application/json")
        case _ => play.api.mvc.Results.InternalServerError(response).as("application/json")
      }
    })
  }

  def setResponseEnvelope(response: Response) = {
    response.setTs(DateUtils.formatCurrentDate("yyyy-MM-dd'T'HH:mm:ss'Z'XXX"))
    response.getParams.setResmsgid(UUID.randomUUID().toString)
  }

  def setRequestContext(request:org.sunbird.common.dto.Request, version: String, objectType: String, schemaName: String): Unit = {
    var contextMap: java.util.Map[String, AnyRef] = new java.util.HashMap[String, AnyRef](){{
      put("graph_id", "domain")
      put("version" , version)
      put("objectType" , objectType)
      put("schemaName", schemaName)
    }};
    request.setObjectType(objectType);
    request.getContext().putAll(contextMap)
  }

  def getRequestHeader(headerkey: String, targetKey: String, defaultValue: String = UUID.randomUUID().toString)(implicit request: Request[AnyContent]): java.util.Map[String, AnyRef] = {
    val value = request.headers.get(headerkey)
    if (value.isDefined && !value.isEmpty) {
      collection.mutable.HashMap[String, Object](targetKey -> value.get).asJava
    } else {
      collection.mutable.HashMap[String, Object](targetKey -> defaultValue).asJava
    }
  }
}
