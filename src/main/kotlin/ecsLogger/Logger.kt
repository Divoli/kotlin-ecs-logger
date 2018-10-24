package ecsLogger


import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.spi.ThrowableProxy
import ch.qos.logback.core.AppenderBase
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import net.andreinc.ansiscape.AnsiScape
import org.slf4j.LoggerFactory
import java.io.File
import java.text.SimpleDateFormat
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import java.lang.*;
import java.net.URL


val JSON_LOGGING = System.getenv("JSON_LOGGING")?: false == "true"

var asciiScape = AnsiScape()

val mapper = jacksonObjectMapper()

open class ECSData {
    // Cloud Info
    @JsonProperty("cloud.provider")
    var cloud_provider: String? = null
    @JsonProperty("cloud.availability_zone")
    var cloud_availability_zone: String? = null
    @JsonProperty("cloud.region")
    var cloud_region: String? = null
    @JsonProperty("cloud.instance.id")
    var cloud_instance_id: String? = null
    @JsonProperty("cloud.machine.type")
    var cloud_machine_type: String? = null
    @JsonProperty("cloud.instance.ip")
    var cloud_instance_ip: String? = null
    @JsonProperty("cloud.cluster")
    var cloud_cluster: String? = null

    // Container Info
    @JsonProperty("container.id")
    var container_id: String? = null
    @JsonProperty("container.image.name")
    var container_image_name: String? = null
    @JsonProperty("container.name")
    var container_name: String? = null

    // Service Info
    @JsonProperty("service.name")
    var service_name: String? = null
    @JsonProperty("service.version")
    var service_version: String? = null

}

@JsonIgnoreProperties(ignoreUnknown = true)
data class CloudInfo(
        @JsonProperty("availabilityZone")
        val availabilityZone: String,
        @JsonProperty("region")
        val region: String,
        @JsonProperty("instanceId")
        val instanceId: String,
        @JsonProperty("instanceType")
        val instanceType: String,
        @JsonProperty("privateIp")
        val privateIp: String

)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ECSInfo(
        @JsonProperty("MetadataFileStatus")
        val status: String,

        @JsonProperty("Cluster")
        val Cluster: String,
        @JsonProperty("ContainerID")
        val ContainerID: String,
        @JsonProperty("ImageID")
        val ImageID: String,
        @JsonProperty("ContainerName")
        val ContainerName: String,
        @JsonProperty("TaskDefinitionFamily")
        val TaskDefinitionFamily: String,
        @JsonProperty("TaskDefinitionRevision")
        val TaskDefinitionRevision: String
)

object ECSMeta {

    var data: ECSData

    init {
        println("init here")
        data = fetch()
    }

    fun format_data(ecs_info: ECSInfo?, cloud_info: CloudInfo?) : ECSData {
        val data = ECSData()

        if (cloud_info != null){
            data.cloud_provider = "ec2"
            data.cloud_availability_zone = cloud_info.availabilityZone
            data.cloud_region = cloud_info.region
            data.cloud_instance_id = cloud_info.instanceId
            data.cloud_machine_type = cloud_info.instanceType
            data.cloud_instance_ip = cloud_info.privateIp
        }

        if (ecs_info != null){
            data.cloud_cluster = ecs_info.Cluster
            data.container_id = ecs_info.ContainerID
            data.container_image_name = ecs_info.ImageID
            data.container_name = ecs_info.ContainerName
            data.service_name = ecs_info.TaskDefinitionFamily
            data.service_version = ecs_info.TaskDefinitionRevision
        }


        return data
    }
    fun fetch() : ECSData {

        val meta_file_path = System.getenv("ECS_CONTAINER_METADATA_FILE")

        // Not on EC2
        if (meta_file_path == null)
            return format_data(null, null)

        var ecs_info = mapper.readValue<ECSInfo>(File(meta_file_path))

        if (ecs_info.status != "READY"){
            Thread.sleep(1000)
            ecs_info = mapper.readValue<ECSInfo>(File(meta_file_path))
        }

        val cloud_info = mapper.readValue<CloudInfo>(URL("http://169.254.169.254/latest/dynamic/instance-identity/document"))
        // val cloud_info = mapper.readValue<CloudInfo>(File("/opt/cloud_test.json"))

        return format_data(ecs_info, cloud_info)

    }

}


@JsonInclude(JsonInclude.Include.NON_NULL)
class LogInfo(
        val message: String,
        @JsonProperty("log.name")
        val log_name: String,
        @JsonProperty("log.level")
        val log_level: String


) : ECSData() {

    @JsonProperty("@timestamp")
    var timestamp: String

    @JsonProperty("exception.name")
    var exception_name: String? = null
    @JsonProperty("exception.message")
    var exception_msg: String? = null
    @JsonProperty("exception.traceback")
    var exception_traceback: ArrayList<String>? = null

    var data: Map<String, *> = emptyMap<String,String>()

    var tags = ArrayList<String>()

    init {

        timestamp = ZonedDateTime.now().toOffsetDateTime().toString()

        cloud_provider = ECSMeta.data.cloud_provider
        cloud_availability_zone = ECSMeta.data.cloud_availability_zone
        cloud_region = ECSMeta.data.cloud_region
        cloud_instance_id = ECSMeta.data.cloud_instance_id
        cloud_machine_type = ECSMeta.data.cloud_machine_type
        cloud_instance_ip = ECSMeta.data.cloud_instance_ip

        cloud_cluster = ECSMeta.data.cloud_cluster
        container_id = ECSMeta.data.container_id
        container_image_name = ECSMeta.data.container_image_name
        container_name = ECSMeta.data.container_name
        service_name = ECSMeta.data.service_name
        service_version = ECSMeta.data.service_version

        // Convenience
        if (ECSMeta.data.cloud_cluster != null){
            tags.add(ECSMeta.data.cloud_cluster as String)
        }

        if (ECSMeta.data.service_name != null){
            tags.add(ECSMeta.data.service_name as String)
        }

    }


}

class ECSLogAppender : AppenderBase<ILoggingEvent>() {

    override fun append(event: ILoggingEvent) {

        if (JSON_LOGGING == false){
            var color = "white"
            when(event.level){
                Level.INFO -> color = "green"
                Level.DEBUG -> color = "blue"
                Level.WARN -> color = "orange"
                Level.ERROR -> color = "red"
            }

            println(asciiScape.format(event.loggerName + " {"+ color + " " + event.level + "} " + event.formattedMessage))

        } else {

            val logInfo = LogInfo(
                    message = event.formattedMessage,
                    log_name = event.loggerName,
                    log_level = event.level.toString()
            )

            if (event.throwableProxy != null){
                logInfo.exception_name = event.throwableProxy.className
                logInfo.exception_msg = event.throwableProxy.message

                val stack = ArrayList<String>()

                for (i in 0..minOf(5, event.callerData.size-1)) {
                    val t = event.callerData[i]
                    if (t.fileName == null || t.lineNumber == -1)
                        break
                    stack.add("${t.lineNumber}:${t.className.split(".").lastOrNull()} ${t.methodName}")
                }

                logInfo.exception_traceback = stack

            } else if (event.argumentArray != null) {

                if (event.argumentArray.size > 0 && event.argumentArray[0] is Map<*, *>) {
                    @Suppress("UNCHECKED_CAST")
                    logInfo.data = event.argumentArray[0] as Map<String, *>
                }
            }
            // writerWithDefaultPrettyPrinter
            val output = mapper.writer().writeValueAsString(logInfo)

            println(output)

        }
    }
}

// todo: replace with unit test

object LoggerExample {
    @JvmStatic
    fun main(args: Array<String>) {

        val log = LoggerFactory.getLogger("home")

        log.info("hello")

        try {
            throw Exception("err")
        } catch(e: Exception){
            log.info("hmm", e)
        }

    }
}

