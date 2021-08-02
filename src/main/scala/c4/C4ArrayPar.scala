package c4

import spire.implicits.cfor

import scala.concurrent.Future
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.concurrent.ExecutionContext.Implicits.global
import scala.reflect.ClassTag
import C4ParUtils.{C4ExecutionContext, flattenBuffers, partitions}

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
      }.flatMap(flattenBuffers)

      Await.result(parallelized, Duration.Inf)
    }
  }

}
