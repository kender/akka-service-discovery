package me.enkode.akka.service.discovery.core

import java.time.Instant

import enumeratum.{Enum, EnumEntry}

import scala.concurrent.duration.FiniteDuration

sealed abstract class Scheme(val uriScheme: String) extends EnumEntry with Serializable

object Scheme extends Enum[Scheme] {
  override def values: Seq[Scheme] = findValues

  case object http extends Scheme("http")
  case object https extends Scheme("https")
}

case class Access(scheme: Scheme, host: Host, port: Int) {
  require(port > 0, "port must be postive")
  require(port <= math.pow(2, 16), "port must be a 16 bit unsigned integer")
}

sealed trait Status extends EnumEntry
object Status extends Enum[Status] {
  override def values: Seq[Status] = findValues

  case object ok extends Status
  case object unavailable extends Status
}

case class Service(serviceId: ServiceId)

case class Instance(instanceId: InstanceId, service: Service, access: Access)

sealed trait Report {
  def instance: Instance
  def when: Instant
  def status: Status
}

sealed trait HeartbeatMeta extends EnumEntry
object HeartbeatMeta extends Enum[HeartbeatMeta] {
  override def values: Seq[HeartbeatMeta] = findValues

  case object CpuLoad extends HeartbeatMeta
  case object MemoryLoad extends HeartbeatMeta

  type Value = Either[Double, String]
  type Values = Map[HeartbeatMeta, Value]
}

case class Heartbeat(
  instance: Instance,
  meta: HeartbeatMeta.Values,
  when: Instant = Instant.now(),
  status: Status = Status.ok
  ) extends Report {
  meta foreach {
    case (HeartbeatMeta.CpuLoad, Left(cpuLoad)) ⇒
      require(0.0 → 1.0 contains cpuLoad, s"cpu: $cpuLoad")

    case (HeartbeatMeta.MemoryLoad, Left(memoryLoad)) ⇒
      require(0.0 → 1.0 contains memoryLoad, s"mem: $memoryLoad")

    case (m, v) ⇒ throw new IllegalArgumentException(s"$m→$v is invalid")
  }
}

sealed trait ObservationMeta extends EnumEntry

object ObservationMeta extends Enum[ObservationMeta] {
  override def values: Seq[ObservationMeta] = findValues

  case object Latency extends ObservationMeta

  type Value = Either[Double, String]
  type Values = Map[ObservationMeta, Value]
}

case class Observation(
  instance: Instance,
  observedBy: Instance,
  meta: ObservationMeta.Values,
  when: Instant = Instant.now(),
  status: Status = Status.ok
  ) extends Report

case class Host(publicName: String, rack: Rack)
case object Host {
  val local = Host("localhost", Cloud.Local.LocalRack)
}


sealed trait Datacenter

sealed trait Rack {
  def datacenter: Datacenter
}

sealed trait Cloud extends EnumEntry

object Cloud extends Enum[Cloud] {

  override def values: Seq[Cloud] = findValues

  object AWS extends Cloud {
    sealed abstract class Region(id: String, location: String) extends EnumEntry with Datacenter {
      override def entryName: String = id
    }

    object Region extends Enum[Region] {
      val values = findValues
      case object usEast1 extends Region("us-east-1", "W. Virginia")
      case object usWest1 extends Region("us-west-1", "N. California")
      case object usWest2 extends Region("us-west-2", "Oregon")

      case object euWest1 extends Region("eu-west-1", "Ireland")
      case object euCentral1 extends Region("eu-central-1", "Frankfurt")

      case object apSoutheast1 extends Region("ap-southeast-1", "Singapore")
      case object apSoutheast2 extends Region("ap-southeast-2", "Sydney")
      case object apNortheast1 extends Region("ap-northeast-1", "Tokyo")

      case object saEast1 extends Region("sa-east-1", "São Paulo")
    }

    case class AZ(region: Region, az: Char) extends Rack {
      val datacenter: Datacenter = region
    }

    case object AZ {
      def fromId(id: String): AZ = {
        val az = id.last
        val region = id.take(id.length - 1)
        AZ(Region.lowerCaseNamesToValuesMap(region.toLowerCase), az)
      }
    }
  }

  object Local extends Cloud {
    case object LocalRegion extends Datacenter
    case object LocalRack extends Rack {
      override val datacenter: Datacenter = LocalRegion
    }
  }
}

