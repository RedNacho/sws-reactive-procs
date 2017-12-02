package sws.reactiveprocs.examples.jacksonpullparser

import com.fasterxml.jackson.core.{JsonParser, JsonToken}
import com.fasterxml.jackson.databind.{JsonNode, ObjectMapper}
import sws.reactiveprocs.ReactiveProcs.{Algorithm, Done}

import scala.annotation.tailrec
import scala.async.Async.{async, await}
import scala.concurrent.{ExecutionContext, Future}

case class ParseException(message: String = null, cause: Throwable = null) extends Exception(message, cause)

// Algorithm which uses the Jackson pull parser to read data procedurally.
// async/await doesn't allow functional constructs between async and await, so this code is very old-fashioned.
// But this is what I wanted to be able to do!
class JsonParsingAlgorithm(jsonParser: JsonParser, objectMapper: ObjectMapper)(implicit ec: ExecutionContext) extends Algorithm[JsonNode] {
  override def apply(yieldReturn: (JsonNode) => Future[Done.type], yieldBreak: () => Future[Done.type]): Future[Done.type] = {
    async {
      findDataArrayInObject(jsonParser, jsonParser.nextToken())

      var nextToken = jsonParser.nextToken()

      while (
        nextToken != JsonToken.END_ARRAY
      ) {
        findDataArrayInObject(jsonParser, nextToken)

        nextToken = jsonParser.nextToken()

        while (
          nextToken != JsonToken.END_ARRAY
        ) {
          if (nextToken != JsonToken.START_OBJECT) {
            throw ParseException("Expected end of array or a new object here.")
          }

          // Returns control to the calling code until the element is requested.
          await { yieldReturn(objectMapper.readTree[JsonNode](jsonParser)) }

          nextToken = jsonParser.nextToken()
        }

        findEndOfObject(jsonParser)

        nextToken = jsonParser.nextToken()
      }

      Done
    }
  }

  private def findDataArrayInObject(jsonParser: JsonParser, currentToken: JsonToken) = {
    if (currentToken != JsonToken.START_OBJECT) {
      throw ParseException("Expected start of JSON object here.")
    }

    findField(jsonParser, "data")

    if (jsonParser.nextToken() != JsonToken.START_ARRAY) {
      throw ParseException("Expected to find the start of an array.")
    }
  }

  @tailrec
  private def findField(jsonParser: JsonParser, fieldName: String): Done.type = {
    jsonParser.nextToken() match {
      case JsonToken.FIELD_NAME if jsonParser.getCurrentName == fieldName =>
        Done
      case JsonToken.FIELD_NAME =>
        jsonParser.nextToken()
        jsonParser.skipChildren()
        findField(jsonParser, fieldName)
      case _ =>
        throw ParseException(s"""Expected to find field names, including "$fieldName".""")
    }
  }

  @tailrec
  private def findEndOfObject(jsonParser: JsonParser): Done.type = {
    jsonParser.nextToken() match {
      case JsonToken.END_OBJECT =>
        Done
      case JsonToken.FIELD_NAME =>
        jsonParser.nextToken()
        jsonParser.skipChildren()
        findEndOfObject(jsonParser)
      case _ =>
        throw ParseException("Expected to find a field name or the end of the object.")
    }
  }
}
