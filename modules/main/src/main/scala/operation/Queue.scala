// Copyright (c) 2016-2019 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package itac
package operation

import cats._
import cats.effect._
import cats.implicits._
import edu.gemini.tac.qengine.api.QueueEngine
import io.chrisdavenport.log4cats.Logger
import edu.gemini.tac.qengine.p1.QueueBand
import edu.gemini.tac.qengine.log.AcceptMessage
import edu.gemini.tac.qengine.log.RejectPartnerOverAllocation
import edu.gemini.tac.qengine.log.RejectNotBand3
import edu.gemini.tac.qengine.log.RejectNoTime
import java.nio.file.Path
import _root_.edu.gemini.tac.qengine.log.RejectCategoryOverAllocation
import edu.gemini.tac.qengine.log.RejectTarget
import edu.gemini.tac.qengine.log.RejectConditions
import edu.gemini.tac.qengine.util.Time

object Queue {

  /**
    * @param siteConfig path to site-specific configuration file, which can be absolute or relative
    *   (in which case it will be resolved relative to the workspace directory).
    */
  def apply[F[_]: Sync: Parallel](
    qe:             QueueEngine,
    siteConfig:     Path,
    rolloverReport: Option[Path]
  ): Operation[F] =
    new AbstractQueueOperation[F](qe, siteConfig, rolloverReport) {

      def run(ws: Workspace[F], log: Logger[F], b: Blocker): F[ExitCode] =
        ws.commonConfig.flatMap { cc =>
          computeQueue(ws).flatMap { case (ps, queueCalc) =>
            val log = queueCalc.proposalLog
            Sync[F].delay {

              val pids = log.proposalIds // proposals that were considered

              // println(s"${Console.BOLD}The following proposals were not considered due to site, mode, or lack of awarded time or observations.${Console.RESET}")
              // ps.filterNot(p => pids.contains(p.id)).foreach { p =>
              //   println(f"- ${p.id.reference} (${p.site.abbreviation}, ${p.mode.programId}%2s, ${p.ntac.awardedTime.toHours.value}%4.1fh ${p.ntac.partner.id}, ${p.obsList.length}%3d obs)")
              // }
              // println()

              QueueBand.Category.values.foreach { qc =>
                println(s"${Console.BOLD}The following proposals were rejected for $qc.${Console.RESET}")
                pids.foreach { pid =>
                  val p = ps.find(_.id == pid).get
                  log.get(pid, qc) match {
                    case None =>
                    case Some(AcceptMessage(_, _, _)) =>
                    case Some(m: RejectPartnerOverAllocation) => println(f"- ${pid.reference}%-20s ${p.piName.orEmpty}%-15s ${m.detail}")
                    case Some(m: RejectNotBand3) => println(f"- ${pid.reference}%-20s ${p.piName.orEmpty}%-15s ${m.detail}")
                    case Some(m: RejectNoTime) => println(f"- ${pid.reference}%-20s ${p.piName.orEmpty}%-15s ${m.detail}")
                    case Some(m: RejectCategoryOverAllocation) => println(f"- ${pid.reference}%-20s ${p.piName.orEmpty}%-15s ${m.detail}")
                    case Some(m: RejectTarget) => println(f"- ${pid.reference}%-20s ${p.piName.orEmpty}%-15s ${m.detail} -- ${ObservationDigest.digest(m.obs.p1Observation)}")
                    case Some(m: RejectConditions) => println(f"- ${pid.reference}%-20s ${p.piName.orEmpty}%-15s ${m.detail} -- ${ObservationDigest.digest(m.obs.p1Observation)}")
                    case Some(lm) => println(f"- ${pid.reference}%-20s ${p.piName.orEmpty}%-15s $lm")
                  }
                }
                println()
              }

              println(s"${Console.BOLD}RA/Conditions Bucket Allocations:${Console.RESET}")
              println(queueCalc.bucketsAllocation.raTablesANSI)
              println()

              QueueBand.values.foreach { qb =>
                val q = queueCalc.queue
                println(s"${Console.BOLD}The following proposals were accepted for Band ${qb.number}.${Console.RESET}")
                println(qb.number match {
                  case 1 => Console.YELLOW
                  case 2 => Console.GREEN
                  case 3 => Console.BLUE
                  case 4 => Console.RED
                })
                q.bandedQueue.get(qb).orEmpty.foreach { p =>
                  println(f"- ${p.id.reference}%-20s -> ${q.programId(p).get} ${p.piName.orEmpty}")
                }
                println(Console.RESET)
              }

              // Ok let's recategorize the results.
              val b12 = queueCalc.queue.bandedQueue(QueueBand.QBand1) ++
                        queueCalc.queue.bandedQueue(QueueBand.QBand2)

              val b3  = queueCalc.queue.bandedQueue(QueueBand.QBand3)
              // val b4  = queueCalc.queue.bandedQueue(QueueBand.QBand4)

              val pt = queueCalc.queue.queueTime

              cc.engine.partners.foreach { pa =>
                println(s"---- ${pa.fullName}")

                def band(t: Time): Int = {
                  val b1 = pt(QueueBand.QBand1, pa).percent(105)
                  val b2 = pt(QueueBand.QBand2, pa).percent(105)
                  // val b3 = pt(QueueBand.QBand3, pa)
                  if (t <= b1) 1 else if (t <= (b1 + b2)) 2 else 3
                }

                println(f"${pt(QueueBand.QBand1, pa).toHours.value}%5.1f ${pt(QueueBand.QBand2, pa).toHours.value}%5.1f ${pt(QueueBand.QBand3, pa).toHours.value}%5.1f")

                def color(t: Time): String =
                  band(t) match {
                    case 1 => Console.YELLOW
                    case 2 => Console.GREEN
                    case 3 => Console.BLUE
                    case 4 => Console.RED
                  }

                val t = b12.filter(_.ntac.partner == pa).foldLeft(Time.Zero) { (t, p) =>
                  val tʹ = t + p.time
                  println(f"• ${color(tʹ)}${p.id.reference}%-20s${Console.RESET} ${tʹ.toHours.value}%5.1f should be ${band(tʹ)} ~ is ${queueCalc.queue.positionOf(p).get.band.number}")
                  tʹ
                }

                b3.filter(_.ntac.partner == pa).foldLeft(t) { (t, p) =>
                  val tʹ = t + p.time
                  println(f"• ${color(tʹ)}${p.id.reference}%-20s${Console.RESET} ${tʹ.toHours.value}%5.1f should be ${band(tʹ)} ~ is ${queueCalc.queue.positionOf(p).get.band.number}")
                  tʹ
                }

                // Ok so semester resource needs to be parameterized by queue band category, and we need to re-do what we did yesterday. But for phase
                // 3 we need to create a new iterator since the post-1/2 iterator has kicked out everything that should be in band 3

              }

              ExitCode.Success

            }
          }
        }

  }

}


