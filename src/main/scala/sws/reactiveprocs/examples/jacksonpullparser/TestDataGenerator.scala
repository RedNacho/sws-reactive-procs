package sws.reactiveprocs.examples.jacksonpullparser

import java.io.File

import com.fasterxml.jackson.core.{JsonEncoding, JsonFactory}

/**
  * Created by simonwhite on 12/2/17.
  */
object TestDataGenerator {

  def apply(fileName: String) = {
    println("Generating loads of data, please wait...")

    val jsonGenerator = new JsonFactory().createGenerator(new File(fileName), JsonEncoding.UTF8)

    jsonGenerator.writeStartObject()
    jsonGenerator.writeStringField("garbage", "Here's some garbage")
    jsonGenerator.writeFieldName("data")
    jsonGenerator.writeStartArray()

    for (i <- 1 to 10000) {
      jsonGenerator.writeStartObject()
      jsonGenerator.writeStringField("garbage", "Here's some additional garbage")
      jsonGenerator.writeFieldName("data")
      jsonGenerator.writeStartArray()
      for (j <- 1 to 10000) {
        jsonGenerator.writeStartObject()
        jsonGenerator.writeStringField("value", s"$i-$j")
        jsonGenerator.writeEndObject()
      }
      jsonGenerator.writeEndArray()
      jsonGenerator.writeStringField("moreGarbage", "Here's even more garbage")
      jsonGenerator.writeEndObject()
    }

    jsonGenerator.writeEndArray()
    jsonGenerator.writeStringField("moreGarbage", "Here's yet more garbage")
    jsonGenerator.close()

    println("Data generated.")
  }

}
