package c4

import spire.implicits.cfor

import java.util.concurrent.Executors
import scala.concurrent.Future
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration.Duration
import scala.concurrent.ExecutionContext.Implicits.global
import scala.reflect.ClassTag

object C4ArrayPar {

  implicit class Ops[A: ClassTag](array: Array[A]) {

    def mapC4Par[B: ClassTag](f: A => B)(implicit ec: C4ExecutionContext): Array[B] = {
      val slices = partitions(array.length, ec.parallelism)

      val mapped = Array.ofDim[B](array.length)

      val parallelized: Future[List[Unit]] = Future.traverse(slices) { case (start, stop) =>
        Future {
          cfor(start)(_ < stop, _ + 1) { i =>
            mapped(i) = f(array(i))
          }
        }(ec.threadPool)
      }

      Await.result(parallelized, Duration.Inf)

      mapped
    }

    def filterC4Par(pred: A => Boolean)(implicit ec: C4ExecutionContext): Array[A] = {
      // Filtering is a lot trickier to parallelize than map because you don't know the size of the final
      // array until you've completed the filtering.
      // This means you can't pre-allocate the final array and fill straight into it.
      // Instead the array is divided into sections and each is filtered independently producing a buffer for each.
      // Once all the buffers are computed, we can work out the final array size and the starting position for each buffer.
      // This allows the final filling of the output array to be done in parallel.

      val slices = partitions(array.length, ec.parallelism)

      // Each slice yields an ArrayBuffer which get concatenated together

      val parallelized: Future[Array[A]] = Future.traverse(slices) { case (start, stop) =>
        Future {
          val buffer = ArrayBuffer.empty[A]
          cfor(start)(_ < stop, _ + 1) { i =>
            val element = array(i)
            if (pred(element))
              buffer.append(element)
          }
          buffer
        }(ec.threadPool)
      }.flatMap { buffers =>
        // Now that all the buffers are complete, we can compute the size of the final array and fill
        // it in parallel directly.

        // To fill each slice of the final array in parallel, you need to figure out where each slice starts
        // which requires adding the lengths of all the previous slices cumulatively.
        val seed: (Int, List[Int]) = (0, List.empty)
        val (totalLength, startingPositionsReversed) = buffers.foldLeft(seed) {
          case ((startingPosition, startingPositionsAcc), nextBuffer) =>
            val nextStartingPosition = startingPosition + nextBuffer.length
            (nextStartingPosition, startingPosition :: startingPositionsAcc)
        }

        val filtered = Array.ofDim[A](totalLength)

        Future.traverse(buffers.zip(startingPositionsReversed.reverse)) {
          case (buffer, start) =>
            Future {
              val sliceLength = buffer.length
              cfor(0)(_ < sliceLength, _ + 1) { i =>
                filtered(start + i) = buffer(i)
              }
            }(ec.threadPool)
        }.map { _ =>
          filtered
        }
      }

      Await.result(parallelized, Duration.Inf)
    }
  }

  /** C4 tasks are intended to be used only with fixed sized thread pools.
   * The standard execution context is too abstract and allows other implementations
   * e.g. fork-join ec's
   * This is a little wrapper that ensures an ec is a fixed sized thread pool
   * and also captures the cardinality of it to make it clear how many slices to divide
   * the array into.
   */
  class C4ExecutionContext private(val parallelism: Int, val threadPool: ExecutionContext)

  object C4ExecutionContext {
    // TODO - ensure parallelism is at least 1 - 0 doesn't make sense
    def apply(parallelism: Int): C4ExecutionContext = new C4ExecutionContext(
      parallelism,
      ExecutionContext.fromExecutor(Executors.newFixedThreadPool(parallelism))
    )
  }

  /** Divides an array range into different index slices to be operated on in parallel.
   *
   * Each pair returned is the start and stop range (stop is exclusive).
   *
   * Note that the range typically won't divide evenly into partitions.
   * Leftover elements are spread evenly across the front partitions.
   *
   * Example: array has 11 elements, parallelism is 3 leading to 3 slices:
   * (0, 4)
   * (4, 8)
   * (8, 11)   <-- slightly shorter
   */
  private[c4] def partitions(arrayLength: Int, numPartitions: Int): List[(Int, Int)] = {
    // TODO - need to specify and handle the case where the array length is less
    //  than the parallelism
    val normalSliceLength = arrayLength / numPartitions

    val leftOvers = arrayLength - normalSliceLength * numPartitions

    // Fold over the partitions keeping track of how many leftovers there are
    // and the closing position of the previous partition
    val seed: (Int, Int, List[(Int, Int)]) = (leftOvers, 0, List.empty)

    val (_, _, partitions) = (0 until numPartitions).foldLeft(seed) {
      case ((leftOvers, start, partitionsAcc), _) =>
        if (leftOvers > 0) {
          val stop = start + normalSliceLength + 1
          (leftOvers - 1, stop, (start, stop) :: partitionsAcc)
        } else {
          val stop = start + normalSliceLength
          (0, stop, (start, stop) :: partitionsAcc)
        }
    }

    partitions.reverse
  }
}
