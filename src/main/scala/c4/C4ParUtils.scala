package c4

import spire.implicits.cfor

import java.util.concurrent.Executors
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.reflect.ClassTag

object C4ParUtils {

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

  /** Combines all the buffers into a final array preserving the order.
   *
   * Each buffer is put into the final array in parallel using the ec passed.
   */
  private[c4] def flattenBuffers[A: ClassTag](buffers: List[ArrayBuffer[A]])(implicit ec: C4ExecutionContext): Future[Array[A]] = {

    // To fill each slice of the final array in parallel, you need to figure out where each slice starts
    // which requires adding the lengths of all the previous slices cumulatively.
    val (totalLength, startingPositionsReversed) = {
      val seed: (Int, List[Int]) = (0, List.empty)
      buffers.foldLeft(seed) {
        case ((startingPosition, startingPositionsAcc), nextBuffer) =>
          val nextStartingPosition = startingPosition + nextBuffer.length
          (nextStartingPosition, startingPosition :: startingPositionsAcc)
      }
    }

    val combined = Array.ofDim[A](totalLength)

    Future.traverse(buffers.zip(startingPositionsReversed.reverse)) {
      case (buffer, start) =>
        Future {
          val sliceLength = buffer.length
          cfor(0)(_ < sliceLength, _ + 1) { i =>
            combined(start + i) = buffer(i)
          }
        }(ec.threadPool)
    }.map { _ =>
      combined
    }
  }
}