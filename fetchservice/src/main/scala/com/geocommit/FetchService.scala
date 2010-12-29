package com.geocommit;

import com.geocommit.source.Git;

import javax.servlet.http.{
    HttpServlet,
    HttpServletRequest,
    HttpServletResponse
}
import java.io.BufferedReader
import com.surftools.BeanstalkClientImpl.ClientImpl
import scala.collection.immutable.HashMap
import net.liftweb.json.JsonParser
import net.liftweb.json.JsonAST
import net.liftweb.json.JsonAST.{JValue, JObject, JNothing, JString, JField, JNull}
import net.liftweb.json.JsonDSL._


class FetchService extends HttpServlet {
    val beanstalk = new ClientImpl("localhost", 11300)

    implicit def string2ByteArray(s: String): Array[Byte] =
        s.getBytes("UTF-8")

    def readBufferedReader(reader: BufferedReader): StringBuffer = {
        val buffer = new StringBuffer
        var line = reader.readLine()
        while (line != null) {
            buffer.append(line)
            line = reader.readLine()
        }
        buffer
    }

    def getRequestBody(request: HttpServletRequest): String =
        readBufferedReader(request.getReader()).toString()

    def putJob(
        tube: String,
        priority: Long,
        delaySeconds: Int,
        timeToRun: Int,
        data: JObject
    ): Long = {
        beanstalk.useTube(tube)
        beanstalk.put(priority, delaySeconds, timeToRun, compact(JsonAST.render(data)))
    }

    def enqueueScanInit(
        request: JObject, response: HttpServletResponse
    ): JValue = {
        val result = (
            "job" -> putJob("scan-init", 100, 0, 600, request)
        )

        response.setStatus(HttpServletResponse.SC_CREATED)
        response.setContentType("application/geocommitjob+json")

        result
    }

    def lsremote(
        request: JObject, response: HttpServletResponse
    ): JValue = {
        (request \ "repository-url") match {
            case JField(_, JString(repo)) =>
                val git = new Git
                JObject(List(JField(
                    "refs/notes/geocommit",
                    JString(git.lsremote(repo, "refs/notes/geocommit"))
                )))
            case _ =>
                JNull
        }
    }

    override def doPost(
        request: HttpServletRequest, response: HttpServletResponse
    ) {
        JsonParser parse getRequestBody(request) match {
            case json: JObject =>
                response.getWriter.println(compact(JsonAST.render(
                    request.getPathInfo match {
                        case "/scan/init" =>
                            enqueueScanInit(json, response)
                        case "/lsremote" =>
                            lsremote(json, response)
                        case _ =>
                            response.setStatus(HttpServletResponse.SC_NOT_FOUND)
                            JNull
                    }
                )))
            case _ =>
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST)
        }

    }
}
