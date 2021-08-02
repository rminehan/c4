package c4

import C4ParUtils.{C4ExecutionContext, flattenBuffers, partitions}
import spire.implicits.cfor

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.{Await, Future}
import scala.concurrent.duration.Duration
import scala.concurrent.ExecutionContext.Implicits.global
import scala.reflect.ClassTag

/** Represents a series of stages that have been fused together
 * to avoid the creation of intermediate collections.
 *
 * Here we're just describing a set of combinators, not executing them.
 *
 * You can build off existing fuses to create new ones using operations
 * like map and filter.
 *
 * TODO - there's no reason this concept has to be limited to Array's
 *  You can use it to transform any seq into another seq.
 *  This version is slightly optimized for arrays in that it uses a cfor loop
 *  which wouldn't make sense on other types
 */
case class Fuse[A, B: ClassTag](array: Array[A], transform: A => Option[B]) {
  def map[C: ClassTag](f: B => C): Fuse[A, C] = Fuse(array, (a: A) => transform(a).map(f))
  def filter(pred: B => Boolean): Fuse[A, B] = Fuse(array, (a: A) => transform(a).filter(pred))

  def boom: Array[B] = {
    val buffer = ArrayBuffer.empty[B]

    val length = array.length
    cfor(0)(_ < length, _ + 1) { i =>
      transform(array(i)).foreach { survivor =>
        buffer.append(survivor)
      }
    }

    buffer.toArray
  }

  // Tempted to call this kaBoom
  def parBoom(implicit ec: C4ExecutionContext): Array[B] = {
    val slices = partitions(array.length, ec.parallelism)

    // Similar to C4ArrayPar.Ops.filterC4Par - see that method for more explanation
    val parallelized: Future[Array[B]] = Future.traverse(slices) { case (start, stop) =>
      Future {
        val buffer = ArrayBuffer.empty[B]
        cfor(start)(_ < stop, _ + 1) { i =>
          transform(array(i)).foreach { survivor =>
            buffer.append(survivor)
          }
        }
        buffer
      }(ec.threadPool)
    }.flatMap(flattenBuffers)

    Await.result(parallelized, Duration.Inf)
  }
}

object Fuse {
  implicit class Ops[A: ClassTag](array: Array[A]) {
    def fuse: Fuse[A, A] = Fuse(array, a => Some(a))
  }
}
